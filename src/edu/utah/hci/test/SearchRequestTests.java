package edu.utah.hci.test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import edu.utah.hci.misc.Util;

import static org.junit.Assert.*;

/**Be sure to turn off authentication by modifying the web.xml and restarting tomcat.
 * Run this test suite twice, with and without interval tree lookup by modifying the WebContent/WEB-INF/web.xml 'useIntervalTree' param.*/
public class SearchRequestTests {
	
	/**Modify this url to match your running instance*/
	private String url = new String("http://localhost:8080/GQuery/search");
	
	//This resource dir should work provided you haven't moved the directories around
	private File testResourcesDir = new File (System.getProperty("user.dir")+"/TestResources");
	private File testBedFile = new File(testResourcesDir, "b37Test.bed");
	private HashSet<String> rootKeysArchive = null;
	private HashSet<String> rootKeysResponse = null;
	private static final Logger lg = LogManager.getLogger(SearchRequestTests.class);
	
	@Test
	public void fetchOptionsTest() throws URISyntaxException{
			//load archived response to compare this new response to
			JSONObject archivedJo = Util.loadJsonFile(new File (testResourcesDir, "Json/queryOptionsAndFilters.json"));
			//build the query
			URIBuilder b = new URIBuilder(url);
			b.addParameter("fetchOptions", "true");
			//fetch json response
			JSONObject responseJo = Util.get(b);
			System.out.println("\nfetchOptionsTest:\n"+responseJo.toString(1));
			assertEquals(200, responseJo.getInt("responseCode"));
			//pull arrays and compare
			HashMap<String, JSONObject> resMap = Util.buildHashOnKey("name", responseJo.getJSONArray("queryOptionsAndFilters"));
			HashMap<String, JSONObject> arcMap = Util.buildHashOnKey("name", archivedJo.getJSONArray("queryOptionsAndFilters"));
			assertTrue(arcMap.size()+" lenOptions!= "+resMap.size(), resMap.size() == arcMap.size());
			//fetchData
			JSONArray resJa = (JSONArray) resMap.get("fetchData").get("options");
			JSONArray arcJa = (JSONArray) arcMap.get("fetchData").get("options");
			compareJsonStringArrays(resJa, arcJa);	
			//dataSources
			resJa = (JSONArray) resMap.get("dataSources").get("options");
			arcJa = (JSONArray) arcMap.get("dataSources").get("options");
			compareJsonStringArrays(resJa, arcJa);
			//forceFetchDataSources
			resJa = (JSONArray) resMap.get("forceFetchDataSources").get("options");
			arcJa = (JSONArray) arcMap.get("forceFetchDataSources").get("options");
			compareJsonStringArrays(resJa, arcJa);
			//matchVcf
			resJa = (JSONArray) resMap.get("matchVcf").get("options");
			arcJa = (JSONArray) arcMap.get("matchVcf").get("options");
			assertTrue(resJa.length() == arcJa.length());
			//includeHeaders
			resJa = (JSONArray) resMap.get("includeHeaders").get("options");
			arcJa = (JSONArray) arcMap.get("includeHeaders").get("options");
			assertTrue(resJa.length() == arcJa.length());
	}

	@Test
	public void intersectVcfRegionsMinimal() throws URISyntaxException {
		//load archived response to compare this new response to
		JSONObject archivedJo = Util.loadJsonFile(new File (testResourcesDir, "Json/b37IntersectVcfRegionsMatchVcf.json"));
		
		//build the query
		URIBuilder b = new URIBuilder(url);
		b.addParameter("vcf", "chr20\t4162847\t.\tC\tT\t.\tPASS\t.");
		b.addParameter("vcf", "20\t4163144\t.\tC\tA\t.\t.\t.;20\t4228734\t.\tC\tCCAAG\t.\tPASS\tAF=0.5");
		b.addParameter("matchVcf", "true");
		b.addParameter("fetchData", "true");
		//fetch json response
		JSONObject responseJo = Util.get(b);
		System.out.println("\nintersectVcfRegionsMinimal:\n"+responseJo.toString(1));
		assertEquals(200, responseJo.getInt("responseCode"));

		checkQueries(archivedJo, responseJo);
	}
	
	@SuppressWarnings("deprecation")
	@Test
	public void intersectBedFileRegionsAll() throws URISyntaxException, FileNotFoundException, UnsupportedEncodingException {
		//load archived response to compare this new response to
		JSONObject archivedJo = Util.loadJsonFile(new File (testResourcesDir, "Json/b37IntersectBedFileRegionsAll.json"));
		
		MultipartEntity entity = new MultipartEntity();
		entity.addPart("fetchData", new StringBody("true"));
		entity.addPart("includeHeaders", new StringBody("true"));

		//fetch json response
		JSONObject responseJo = Util.post(testBedFile, url, entity);

		assertTrue("Post returned an error", responseJo != null);
		System.out.println("\nintersectBedFileRegionsAll:\n"+responseJo.toString(1));
		assertEquals(200, responseJo.getInt("responseCode"));

		checkQueries(archivedJo, responseJo);
	}
	
	@Test
	public void intersectBedRegionsAll() throws URISyntaxException {
		//load archived response to compare this new response to
		JSONObject archivedJo = Util.loadJsonFile(new File (testResourcesDir, "Json/b37IntersectBedRegionsAll.json"));
		
		//build the query
		URIBuilder b = new URIBuilder(url);
		b.addParameter("bed", "20:4,162,827-4,162,867;21\t11058198\t11058237\ttestX\t4.3\t-");
		b.addParameter("bed", "20\t3893036\t3898261\tjustName");
		b.addParameter("fetchData", "true");
		b.addParameter("includeHeaders", "true");
		//fetch json response
		JSONObject responseJo = Util.get(b);
		System.out.println("\nintersectBedRegionsAll:\n"+responseJo.toString(1));
		assertEquals(200, responseJo.getInt("responseCode"));

		checkQueries(archivedJo, responseJo);
	}
	
	@Test
	public void intersectBedForceFetchData() throws URISyntaxException {
		//load archived response to compare this new response to 
		JSONObject archivedJo = Util.loadJsonFile(new File (testResourcesDir, "Json/b37IntersectBedForceFetchData.json"));
		
		//build the query
		URIBuilder b = new URIBuilder(url);
		b.addParameter("bed", "chr22:16,051,925-16,051,971");
		b.addParameter("fetchData", "force");
		b.addParameter("regExOne", "Data/B37/GVCFs/wgSeq_chr22\\.g\\.vcf\\.gz;Data/B37/BedData/b37EnsGenes_ExonsChr20-21\\.bed\\.gz");
		
		//fetch json response
		JSONObject responseJo = Util.get(b);
		System.out.println("\nintersectBedForceFetchData:\n"+responseJo.toString(1));
		assertEquals(200, responseJo.getInt("responseCode"));
		
		checkQueries(archivedJo, responseJo);
	}
	
	@Test
	public void intersectBedRegionsMinimal() throws URISyntaxException {
		//load archived response to compare this new response to
		JSONObject archivedJo = Util.loadJsonFile(new File (testResourcesDir, "Json/b37IntersectBedRegionsMinimal.json"));
		
		//build the query
		URIBuilder b = new URIBuilder(url);
		b.addParameter("bed", "20\t4162827\t4162867;21\t11058198\t11058237\ttestX\t4.3\t-");
		b.addParameter("bed", "chr20\t3893036\t3898261\tjustName");
		//fetch json response
		JSONObject responseJo = Util.get(b);
		System.out.println("\nintersectBedRegionsMinimal:\n"+responseJo.toString(1));
		assertEquals(200, responseJo.getInt("responseCode"));
		
		checkQueries(archivedJo, responseJo);
	}
	
	@Test
	public void intersectBedRegionsChr() throws URISyntaxException {
		//show that chr is ignored at both the user entry and tabix query level on an hg19 tabix indexed bed file
		//load archived response to compare this new response to
		JSONObject archivedJo = Util.loadJsonFile(new File (testResourcesDir, "Json/hg19IntersectBedRegionsChr.json"));
		
		//build the query
		URIBuilder b = new URIBuilder(url);
		b.addParameter("bed", "22:25,973,988-27,015,054");
		b.addParameter("regExAll", "/Hg19/");
		b.addParameter("fetchData", "true");
		//fetch json response
		JSONObject responseJo = Util.get(b);
		System.out.println("\nintersectBedRegionsChr:\n"+responseJo.toString(1));
		assertEquals(200, responseJo.getInt("responseCode"));
		
		checkQueries(archivedJo, responseJo);
	}
	
	@Test
	public void intersectBedRegionsMinimalFileTypeFilter() throws URISyntaxException {
		//load archived response to compare this new response to
		JSONObject archivedJo = Util.loadJsonFile(new File (testResourcesDir, "Json/b37IntersectBedRegionsMinimalFileTypeFilter.json"));
		
		//build the query
		URIBuilder b = new URIBuilder(url);
		b.addParameter("bed", "20\t4162827\t4162867;21\t11058198\t11058237\ttestX\t4.3\t-");
		b.addParameter("bed", "20\t3893036\t3898261\tjustName");
		b.addParameter("regExOne", "\\.vcf\\.gz;\\.bedGraph\\.gz");
		//fetch json response
		JSONObject responseJo = Util.get(b);
		System.out.println("\nintersectBedRegionsMinimalFileTypeFilter:\n"+responseJo.toString(1));
		assertEquals(200, responseJo.getInt("responseCode"));

		checkQueries(archivedJo, responseJo);
	}
	
	@Test
	public void intersectBedRegionsMinimalDataPathsFilter() throws URISyntaxException {
		//load archived response to compare this new response to
		JSONObject archivedJo = Util.loadJsonFile(new File (testResourcesDir, "Json/b37IntersectBedRegionsMinimalDataPathsFilter.json"));
		
		//build the query
		URIBuilder b = new URIBuilder(url);
		b.addParameter("bed", "20\t4162827\t4162867;21\t11058198\t11058237\ttestX\t4.3\t-");
		b.addParameter("bed", "20\t3893036\t3898261\tjustName");
		b.addParameter("regExOne", "Data/B37/TCGA/AP/");
		b.addParameter("regExOne", "Data/B37/BedData/");
		//fetch json response
		JSONObject responseJo = Util.get(b);
		System.out.println("\nintersectBedRegionsMinimalDataPathsFilter:\n"+responseJo.toString(1));
		assertEquals(200, responseJo.getInt("responseCode"));

		checkQueries(archivedJo, responseJo);
	}
	
	@Test
	public void intersectBedRegionsMinimalDataSourcesFilter() throws URISyntaxException {
		//load archived response to compare this new response to
		JSONObject archivedJo = Util.loadJsonFile(new File (testResourcesDir, "Json/b37IntersectBedRegionsMinimalDataSourcesFilter.json"));
		
		//build the query
		URIBuilder b = new URIBuilder(url);
		b.addParameter("bed", "20\t4162827\t4162867;21\t11058198\t11058237\ttestX\t4.3\t-");
		b.addParameter("bed", "20\t3893036\t3898261\tjustName");
		b.addParameter("regExOne", "Data/B37/TCGA/AP/AP_Test\\.maf\\.txt\\.gz");
		b.addParameter("regExOne", "Data/B37/BedData/chr20-21_Exome_UniObRC\\.bedGraph\\.gz");
		//fetch json response
		JSONObject responseJo = Util.get(b);
		System.out.println("\nintersectBedRegionsMinimalDataSourcesFilter:\n"+responseJo.toString(1));
		assertEquals(200, responseJo.getInt("responseCode"));
		checkQueries(archivedJo, responseJo);
	}
	
	@Test
	public void intersectBedRegionsMinimalGenomeBuildFilter() throws URISyntaxException {
		//load archived response to compare this new response to
		JSONObject archivedJo = Util.loadJsonFile(new File (testResourcesDir, "Json/b38IntersectBedRegionsMinimalGenomeBuildFilter.json"));
		//build the query
		URIBuilder b = new URIBuilder(url);
		b.addParameter("bed", "21:31660839-31664437");
		b.addParameter("regExAll", "/B38/");
		//fetch json response
		JSONObject responseJo = Util.get(b);
		System.out.println("\nintersectBedRegionsMinimalGenomeBuildFilter:\n"+responseJo.toString(1));
		assertEquals(200, responseJo.getInt("responseCode"));
		checkQueries(archivedJo, responseJo);
	}
	
	@Test
	public void intersectBedRegionsMinimalDataRecordAllFilter() throws URISyntaxException {
		//load archived response to compare this new response to
		JSONObject archivedJo = Util.loadJsonFile(new File (testResourcesDir, "Json/b37IntersectBedRegionsMinimalDataRecordAllFilter.json"));
		//build the query
		URIBuilder b = new URIBuilder(url);
		b.addParameter("bed", "20:4,162,827-4,162,867");
		b.addParameter("regExAllData", "SMOX;Illumina");
		b.addParameter("fetchData", "true");
		//fetch json response
		JSONObject responseJo = Util.get(b);
		System.out.println("\nintersectBedRegionsMinimalDataRecordAllFilter:\n"+responseJo.toString(1));
		assertEquals(200, responseJo.getInt("responseCode"));
		checkQueries(archivedJo, responseJo);
	}
	
	@Test
	public void intersectBedRegionsMinimalDataRecordOneFilter() throws URISyntaxException {
		//load archived response to compare this new response to
		JSONObject archivedJo = Util.loadJsonFile(new File (testResourcesDir, "Json/b37IntersectBedRegionsMinimalDataRecordOneFilter.json"));
		//build the query
		URIBuilder b = new URIBuilder(url);
		b.addParameter("bed", "20:4,162,827-4,162,867");
		b.addParameter("regExOneData", "4162828;4163495");
		b.addParameter("fetchData", "true");
		//fetch json response
		JSONObject responseJo = Util.get(b);
		System.out.println("\nintersectBedRegionsMinimalDataRecordOneFilter:\n"+responseJo.toString(1));
		assertEquals(200, responseJo.getInt("responseCode"));

		checkQueries(archivedJo, responseJo);
	}
	
	@SuppressWarnings("unchecked")
	private void checkQueries(JSONObject archivedJo, JSONObject responseJo) {
		//load root key names, these are used to check if a particular key is present, dumb org.json !
		rootKeysArchive = new HashSet<String>();
		rootKeysArchive.addAll(archivedJo.keySet());
		rootKeysResponse = new HashSet<String>();
		rootKeysResponse.addAll(responseJo.keySet());
		
		checkUrls(archivedJo, responseJo);
		checkQuerySettings(archivedJo, responseJo);
		checkIndexQueryStats(archivedJo, responseJo);
		checkDataRetrievalStats(archivedJo, responseJo);
		checkQueryResults(archivedJo, responseJo);
		checkFileHeaders(archivedJo, responseJo);
	}	
	
	private void checkUrls(JSONObject archivedJo, JSONObject responseJo) {
		//obj present?
		boolean aPres = rootKeysArchive.contains("url");
		boolean rPres = rootKeysResponse.contains("url");
		if (aPres == false && rPres == false) return;
		assertTrue(aPres+" url not present in both "+rPres, aPres == rPres);
		
		//get the value
		String aAr = archivedJo.getString("url");
		String rAr = responseJo.getString("url");
		assertTrue (aAr+" url!= "+rAr, aAr.equals(rAr));
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
		boolean aPres = rootKeysArchive.contains("indexQueryStats");
		boolean rPres = rootKeysResponse.contains("indexQueryStats");
		if (aPres == false && rPres == false) return;
		assertTrue(aPres+" indexQueryStats not present in both "+rPres, aPres == rPres);
		
		JSONObject a = archivedJo.getJSONObject("indexQueryStats");
		JSONObject r = responseJo.getJSONObject("indexQueryStats");
		checkInt("skippedUserQueries", a, r);
		checkInt("searchedUserQueries", a, r);
		checkInt("totalIndexHits", a, r);
		checkInt("queriesWithIndexHits", a, r);
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
		checkNullThenString("fetchData", keysArchive, keysResponse, a, r);
		checkNullThenString("includeHeaders", keysArchive, keysResponse, a, r);
		checkNullThenString("regExOne", keysArchive, keysResponse, a, r);
		checkNullThenString("regExAll", keysArchive, keysResponse, a, r);	
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
		checkInt("recordsRetrieved", a, r); 
		checkInt("recordsReturnedToUser", a, r);
		checkInt("queriesWithTabixRecords", a, r);
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
	
	/**Compares two JSONArray of String.*/
	private void compareJsonStringArrays(JSONArray resJa, JSONArray arcJa) {
		assertTrue(resJa.length() == arcJa.length());
		int size = resJa.length();
		for (int i=0; i< size; i++) {
			String r = resJa.getString(i);
			String a = arcJa.getString(i);
			assertTrue(r +" != "+a, r.equals(a));
		}
	}

}
