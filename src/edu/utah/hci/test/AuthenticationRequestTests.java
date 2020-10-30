package edu.utah.hci.test;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.Key;
import java.util.HashMap;
import java.util.HashSet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import edu.utah.hci.misc.Crypt;
import edu.utah.hci.misc.Util;

import static org.junit.Assert.*;

/**
 1) Be sure to turn on authentication by modifying the web.xml. 

2) Add the following to the tomcat-user.xml doc in the Eclipse Servers folder under Tomcat v9.0:
 <tomcat-users>
  <role rolename="queryUser"/>
  <user username="Obama" password="0bafcb708dd667a39316f7333c0c0b2e" roles="queryUser"/>
</tomcat-users>

3) Be sure the userGroup.txt file in the Eclipse WebContent/WEB-INF folder contains:
# Define groups and their associated regular expression(s) used to match file paths acceptable for returning to a given user.  If more than one regEx is provided, only one must match, not all.
GroupName	RegEx's, comma delimited, no spaces
Public	.+/Data/B37/BedData/.+,.+/Data/B37/TCGA/.+,.+/Data/B37/VCFData/.+
QueryAdmin	.+/Data/.+
Thor	.+/Data/B37/Thor/.+

4) # Define users and the group(s) they belong to in the same userGroup.txt file
# Required, define a user called 'Guest' and the groups they are allowed to access without an authentication key
UserName	Groups, comma delimited, no spaces, membership gains access to the file path RegEx's
Guest	Public
Admin	QueryAdmin
Obama	Public,Thor

5) Clean, build, restart tomcat

*/
public class AuthenticationRequestTests {
	
	/**Modify this url to match your running instance*/
	private String searchUrl = new String("http://localhost:8080/GQuery/search");
	private String fetchKeyUrl = new String("http://localhost:8080/GQuery/fetchKey");
	
	/**This resource dir should work provided you haven't moved the directories around*/
	private File testResourcesDir = new File (System.getProperty("user.dir")+"/TestResources");
	
	/**Path to the encrytionKey created by the QueryAuthorization service upon startup.  Note it is deleted upon shutdown.*/
	private File encryptionKeyFile = new File (System.getProperty("user.dir")+"/WebContent/WEB-INF/key.obj");

	private HashSet<String> rootKeysArchive = null;
	private HashSet<String> rootKeysResponse = null;
	private static final Logger lg = LogManager.getLogger(AuthenticationRequestTests.class);

	@Test
	public void obamaSearch() throws Exception {

		//fetch the key
		URL u = new URL(fetchKeyUrl);
		String keyValue = Util.loadDigestGet(u, "Obama", "thankYou");
		assertTrue("Failed to fetch an authentication token for Obama ", keyValue!=null);
		
		//decrypt it and split userName:timestamp
		Key key = (Key)Util.fetchObject(encryptionKeyFile);
		String nameTimestamp = Crypt.decrypt(keyValue, key);
		String[] nt = Util.COLON.split(nameTimestamp);
		assertTrue("Failed to split the authentication key on a colon "+keyValue, nt.length == 2);
		
		//check the time
		long timestamp = Long.parseLong(nt[1]);
		double diff = (double)(System.currentTimeMillis() - timestamp);
		assertTrue("Failed to return a timestamp < 10000 ms? "+diff, diff < 10000);
		
		//build the query, note this will encode the vals
		URIBuilder b = new URIBuilder(searchUrl);
		b.addParameter("bed", "chr22:39,281,892-39,515,517");
		b.addParameter("key", keyValue);
		
		//fetch json response
		JSONObject responseJo = Util.get(b);
		System.out.println("\nobamaSearch:\n"+responseJo.toString(1));
		assertEquals(200, responseJo.getInt("responseCode"));

		//load archived response to compare this new response to
		JSONObject archivedJo = Util.loadJsonFile(new File (testResourcesDir, "Json/GQueryAPI/b37ObamaSearch.json"));

		checkQueries(archivedJo, responseJo);
	}

	
	@Test
	public void guestSearch() throws URISyntaxException {
		//load archived response to compare this new response to
		JSONObject archivedJo = Util.loadJsonFile(new File (testResourcesDir, "Json/GQueryAPI/b37GuestSearch.json"));
		
		//build the query
		URIBuilder b = new URIBuilder(searchUrl);
		b.addParameter("bed", "chr22:39,281,892-39,515,517");
		
		//fetch json response
		JSONObject responseJo = Util.get(b);
		System.out.println("\nguestSearch:\n"+responseJo.toString(1));
		assertEquals(200, responseJo.getInt("responseCode"));

		checkQueries(archivedJo, responseJo);
	}
	
	@Test
	public void badUserRequest() throws Exception {
		//fetch the key
		URL u = new URL(fetchKeyUrl);
		String keyValue = Util.loadDigestGet(u, "Trump", "ILovePutin");
		assertTrue("A key value was returned but shouldn't have been "+keyValue, keyValue==null);
	}
	
	@SuppressWarnings("unchecked")
	private void checkQueries(JSONObject archivedJo, JSONObject responseJo) {
		//load root key names, these are used to check if a particular key is present, dumb org.json !
		rootKeysArchive = new HashSet<String>();
		rootKeysArchive.addAll(archivedJo.keySet());
		rootKeysResponse = new HashSet<String>();
		rootKeysResponse.addAll(responseJo.keySet());
		
		//note can't check the url since the authentication key will be different
		checkQuerySettings(archivedJo, responseJo);
		checkIndexQueryStats(archivedJo, responseJo);
		checkDataRetrievalStats(archivedJo, responseJo);
		checkQueryResults(archivedJo, responseJo);
		checkFileHeaders(archivedJo, responseJo);
	}	
	
	private void checkFileHeaders(JSONObject archivedJo, JSONObject responseJo) {
		//obj present?
		boolean aPres = rootKeysArchive.contains("fileHeaders");
		boolean rPres = rootKeysResponse.contains("fileHeaders");
		if (aPres == false && rPres == false) return;
		assertTrue(aPres+" fileHeaders not present in both "+rPres, aPres == rPres);
		
		//get the arrays
		JSONArray aAr = archivedJo.getJSONArray("fileHeaders");
		JSONArray rAr = responseJo.getJSONArray("fileHeaders");
		
		//check the lengths
		int numA = aAr.length();
		int numR = rAr.length();
		assertTrue (numA+" lenFileHeaders!= "+numR, numA == numR);
		
		//load archive into hash
		HashMap<String, JSONObject> aHash = new HashMap<String, JSONObject>();
		for (int i=0; i< numA; i++) {
			JSONObject j = aAr.getJSONObject(i);
			aHash.put(j.getString("source"), j);
		}
			
		//check each
		for (int i=0; i< numA; i++) {
			JSONObject r = rAr.getJSONObject(i);
			JSONObject a = aHash.get(r.getString("source"));
			assertTrue("Failed to find a header source in the archive for "+r.getString("source"), a != null);
			if (a != null) checkString("header", a, r);
		}
		
	}
	
	private void checkQueryResults(JSONObject archivedJo, JSONObject responseJo) {
		//results present?
		boolean aPres = rootKeysArchive.contains("queryResults");
		boolean rPres = rootKeysResponse.contains("queryResults");
		if (aPres == false && rPres == false) return;
		assertTrue(aPres+" queryResults not present in both "+rPres, aPres == rPres);
		
		//get the arrays
		JSONArray aAr = archivedJo.getJSONArray("queryResults");
		JSONArray rAr = responseJo.getJSONArray("queryResults");
		
		//check the lengths
		int numA = aAr.length();
		int numR = rAr.length();
		assertTrue (numA+" lenQueryResults!= "+numR, numA == numR);
		
		//load archive into hash
		//something wonkie is going with the tabs thus converting to :::
		HashMap<String, JSONObject> archiveHash = new HashMap<String, JSONObject>();
		
		for (int i=0; i< numA; i++) {
			JSONObject aJo = aAr.getJSONObject(i);
			String inputArchive = aJo.getString("input");
			archiveHash.put(inputArchive, aJo);
		}
		
		//check each
		for (int i=0; i< numR; i++) {
			JSONObject rJo = rAr.getJSONObject(i);
			String inputResponse = rJo.getString("input");
			JSONObject a = archiveHash.get(inputResponse);
			assertTrue("Failed to find an input archive for "+rJo.getString("input") +"\n"+rJo, a != null);
			if (a != null){
				checkString("chr", a, rJo);
				checkInt("start", a, rJo);
				checkInt("stop", a, rJo);
				checkHits(a.getJSONArray("hits"), rJo.getJSONArray("hits"));
			}
		}
	}

	private void checkHits(JSONArray aJa, JSONArray rJa){
		//check length
		int numA = aJa.length();
		int numR = rJa.length();
		assertTrue (numA+" lenOfHits!= "+numR, numA == numR);
		//load archive values into hashmap
		HashMap<String, String> sMap = new HashMap<String, String>();
		for (int i=0; i< numA; i++) {
			JSONObject jo = aJa.getJSONObject(i);
			String key = jo.getString("source");
			String data = fetchData(jo); //might be null
			sMap.put(key, data);
			
		}
		//walk through response
		for (int i=0; i< numA; i++) {
			JSONObject jo = rJa.getJSONObject(i);
			String key = jo.getString("source");
			assertTrue ("Archive source doesn't contain response "+key+" in "+sMap.keySet(), sMap.containsKey(key));
			String dataRes = fetchData(jo);
			String dataArc = sMap.get(key);
			//both not null
			if (dataRes != null && dataArc != null ) assertTrue(dataArc+" data!= "+dataRes, dataArc.equals(dataRes));
			//one or other null
			if ((dataRes==null && dataArc!=null) || (dataRes!=null && dataArc==null)) fail(dataArc+" only one data is null "+dataRes);
			//both are null so skip
			
		}
	}
	
	@SuppressWarnings("unchecked")
	private String fetchData(JSONObject jo) {
		//does it exits?
		HashSet<String> keys = new HashSet<String>();
		keys.addAll(jo.keySet());
		if (keys.contains("data")){
			JSONArray ja = jo.getJSONArray("data");
			return ja.toString();
		}
		return null;
	}

	/**Helper method looking at query stats*/
	private void checkIndexQueryStats(JSONObject archivedJo, JSONObject responseJo) {
		//obj present?
		boolean aPres = rootKeysArchive.contains("fileIndexQueryStats");
		boolean rPres = rootKeysResponse.contains("fileIndexQueryStats");
		if (aPres == false && rPres == false) return;
		assertTrue(aPres+" fileIndexQueryStats not present in both "+rPres, aPres == rPres);
		
		JSONObject a = archivedJo.getJSONObject("fileIndexQueryStats");
		JSONObject r = responseJo.getJSONObject("fileIndexQueryStats");
		checkInt("numberQueriesThatIntersectDataFilesPreFiltering", a, r);
		checkInt("numberQueries", a, r);
		checkInt("numberIndexLookupJobs", a, r);
	}
	
	/**Helper method looking at query stats*/
	@SuppressWarnings("unchecked")
	private void checkQuerySettings(JSONObject archivedJo, JSONObject responseJo) {
		//obj present?
		boolean aPres = rootKeysArchive.contains("querySettings");
		boolean rPres = rootKeysResponse.contains("querySettings");
		if (aPres == false && rPres == false) return;
		assertTrue(aPres+" querySettings not present in both "+rPres, aPres == rPres);
		
		JSONObject a = archivedJo.getJSONObject("querySettings");
		JSONObject r = responseJo.getJSONObject("querySettings");
		
		HashSet<String> keysArchive = new HashSet<String>();
		keysArchive.addAll(a.keySet());
		HashSet<String> keysResponse = new HashSet<String>();
		keysResponse.addAll(r.keySet());
		
		checkNullThenString("matchVcf", keysArchive, keysResponse, a, r);
		checkNullThenString("bpPadding", keysArchive, keysResponse, a, r);
		checkNullThenString("fetchData", keysArchive, keysResponse, a, r);
		checkNullThenString("includeHeaders", keysArchive, keysResponse, a, r);
		checkNullThenString("regExDirPath", keysArchive, keysResponse, a, r);
		checkNullThenString("regExFileName", keysArchive, keysResponse, a, r);	
		checkNullThenString("regExDataLine", keysArchive, keysResponse, a, r);
		checkNullThenString("regExDataLineExclude", keysArchive, keysResponse, a, r);
		checkNullThenString("matchAllDirPathRegEx", keysArchive, keysResponse, a, r);
		checkNullThenString("matchAllFileNameRegEx", keysArchive, keysResponse, a, r);	
		checkNullThenString("matchAllDataLineRegEx", keysArchive, keysResponse, a, r);
	}

	
	private void checkNullThenString(String key, HashSet<String> keysArchive, HashSet<String> keysResponse, JSONObject a, JSONObject r) {
		//obj present?
		boolean aPres = keysArchive.contains(key);
		boolean rPres = keysResponse.contains(key);
		if (aPres == false && rPres == false) return;
		assertTrue(aPres+" "+key+" not present in both "+rPres, aPres == rPres);
		checkString(key, a, r);
	}

	/**Helper method looking at data retrieval stats*/
	private void checkDataRetrievalStats(JSONObject archivedJo, JSONObject responseJo) {
		//obj present?
		boolean aPres = rootKeysArchive.contains("dataRetrievalStats");
		boolean rPres = rootKeysResponse.contains("dataRetrievalStats");
		if (aPres == false && rPres == false) return;
		assertTrue(aPres+" dataRetrievalStats not present in both "+rPres, aPres == rPres);
		
		JSONObject a = archivedJo.getJSONObject("dataRetrievalStats");
		JSONObject r = responseJo.getJSONObject("dataRetrievalStats");
		checkInt("numberQueriesWithDataThatPassDataLineRegEx", a, r);
		checkInt("numberDataLookupJobs", a, r); 
		checkInt("numberQueriesWithData", a, r);
		checkInt("numberQueries", a, r);
	}

	/**Helper method*/
	private void checkInt(String key, JSONObject a, JSONObject r) {
		int aInt = a.getInt(key);
		int rInt = r.getInt(key);
		assertTrue(aInt +" != "+rInt, aInt == rInt);
	}
	
	private void checkString(String key, JSONObject a, JSONObject r) {
		String aInt = a.get(key).toString();
		String rInt = r.get(key).toString();
		assertTrue(aInt +" != "+rInt, aInt.equals(rInt));
	}

}
