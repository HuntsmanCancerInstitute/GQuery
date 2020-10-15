package edu.utah.hci.query;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import edu.utah.hci.misc.Util;
import edu.utah.hci.tabix.TabixDataQuery;

import java.util.Iterator;
//import java.util.TreeMap;

public class QueryFilter {

	/**One or more case-insensitive patterns that a file path or data line will be scored against to be included in the output.*/
	private Pattern[] regExDirPath;
	private Pattern[] regExFileName;
	private Pattern[] regExDataLine;
	private Pattern[] regExDataLineExclude;
	private boolean matchAllDirPathRegEx = false;
	private boolean matchAllFileNameRegEx = false;
	private boolean matchAllDataLineRegEx = false;
	private int numCharToSkipForDataDir = -1;
	private QueryRequest queryRequest;
	
	private Pattern[] regExDirPathUser = null;
	private String userName = null;
	boolean filterOnRegEx = false;

	/**This hash is loaded with the File data sources to return. */
	private TreeSet<File> dataFilesToReturn = new TreeSet<File>();

	private boolean matchVcf = false;
	private boolean fetchData = false;
	private boolean fetchOptions = false;
	private boolean excludeDataFromResults = false;
	private boolean includeHeaders = false;
	private int bpPadding = 0;
	private HashMap<String, SingleQuery> dirTNameQueryIndexes = null;
	private ArrayList<String> truncFilePathsUserCanSee = null;
	
	/*If not null then the query failed.*/
	private String errTxtForUser = null;
	

	private static final Logger lg = LogManager.getLogger(QueryFilter.class); 

	//constructors
	public QueryFilter(User user, QueryRequest queryRequest, HashMap<String, String> options) throws IOException{
		this.queryRequest = queryRequest;
		dirTNameQueryIndexes = queryRequest.getMasterQuery().getQueryIndexes();
		if (user != null) {
			userName = user.getUserName();
			regExDirPathUser = user.getRegExOne();
		}
		truncFilePathsUserCanSee = fetchTruncFilePathsUserCanSee();
		setOptions(options);
		numCharToSkipForDataDir = queryRequest.getMasterQuery().getNumCharToSkipForDataDir();
	}
	
	private boolean setOptions(HashMap<String, String> options) {
		//walk through each option, forcing key to be case insensitive
		for (String key: options.keySet()){
			String lcKey = key.toLowerCase();
			
			//fetchOptions?
			if (lcKey.equals("fetchoptions")){
				String bool = options.get(key).toLowerCase();
				//true
				if (bool.startsWith("t")) {
					fetchOptions = true;
					return true;
				}
				else fetchOptions = false;
			}
			//fetchData?
			else if (lcKey.equals("fetchdata")){
				String bool = options.get(key).toLowerCase();
				//true
				if (bool.startsWith("t")) fetchData = true;
				else fetchData = false;
			}
			//match vcf?
			else if (lcKey.equals("matchvcf")){
				String bool = options.get(key).toLowerCase();
				if (bool.startsWith("t")) matchVcf = true;
				else matchVcf = false;
			}
			//include headers?
			else if (lcKey.equals("includeheaders")){
				String bool = options.get(key).toLowerCase();
				if (bool.startsWith("t")) includeHeaders = true;
				else  includeHeaders = false;
			}
			//filter files based on dir path
			else if (lcKey.equals("regexdirpath")){
				String[] rgs = Util.SEMI_COLON.split(options.get(key));
				Pattern[] ps = new Pattern[rgs.length];
				for (int i=0; i< rgs.length; i++) ps[i] = Pattern.compile(".*"+rgs[i]+".*");
				filterOnRegEx = true;
				regExDirPath = ps;
			}

			//filter files based on name? 
			else if (lcKey.equals("regexfilename")){
				String[] rgs = Util.SEMI_COLON.split(options.get(key));
				Pattern[] ps = new Pattern[rgs.length];
				for (int i=0; i< rgs.length; i++) ps[i] = Pattern.compile(".*"+rgs[i]+".*");
				filterOnRegEx = true;
				regExFileName = ps;
			}

			//filter data lines with regular expressions?
			else if (lcKey.equals("regexdataline")){
				String[] rgs = Util.SEMI_COLON.split(options.get(key));
				Pattern[] ps = new Pattern[rgs.length];
				for (int i=0; i< rgs.length; i++) ps[i] = Pattern.compile(".*"+rgs[i]+".*");
				filterOnRegEx = true;
				regExDataLine = ps;
			}
			//filter data lines with regular expressions?
			else if (lcKey.equals("regexdatalineexclude")){
				String[] rgs = Util.SEMI_COLON.split(options.get(key));
				Pattern[] ps = new Pattern[rgs.length];
				for (int i=0; i< rgs.length; i++) ps[i] = Pattern.compile(".*"+rgs[i]+".*");
				filterOnRegEx = true;
				regExDataLineExclude = ps;
			}
			// must all of the Dir regexes match?
			else if (lcKey.equals("matchalldirpathregex")){
				String bool = options.get(key).toLowerCase();
				if (bool.startsWith("t")) matchAllDirPathRegEx = true;
				else matchAllDirPathRegEx = false;
			}
			// must all of the file name regexes match?
			else if (lcKey.equals("matchallfilenameregex")){
				String bool = options.get(key).toLowerCase();
				if (bool.startsWith("t")) matchAllFileNameRegEx = true;
				else matchAllFileNameRegEx = false;
			}
			// must all of the data line regexes match?
			else if (lcKey.equals("matchalldatalineregex")){
				String bool = options.get(key).toLowerCase();
				if (bool.startsWith("t")) matchAllDataLineRegEx = true;
				else matchAllDataLineRegEx = false;
			}

			//any incomming bed or vcf regions from a GET request?  not present with POST
			else if (lcKey.equals("bed")) queryRequest.setBedRegions(Util.SEMI_COLON.split(options.get("bed")));
			else if (lcKey.equals("vcf")) queryRequest.setVcfRegions(Util.SEMI_COLON.split(options.get("vcf")));
			
			//bpPadding
			else if (lcKey.equals("bppadding")){
				String s = options.get(key);
				bpPadding = Integer.parseInt(s);
			}
			
			//authentication token, ignore
			else if (lcKey.equals("key")){}

			//something odd coming in, throw error
			else {
				errTxtForUser= "Unrecognized cmd -> "+ key+"="+options.get(key);
				lg.error("Unrecognized cmd -> "+ key+"="+options.get(key));
				return false;
			}
		}
		
		//do they want to filter file paths or names?
		if (regExFileName != null || regExDirPath != null) filterOnRegEx = true;

		//are they indicating matchVcf or filtering by data regexes? if so then fetchData so we can examine the ref alt etc but indicate they don't want the data hits returned just the number
		if (matchVcf || regExDataLine != null){
			if (fetchData == false){
				excludeDataFromResults = true;
				fetchData = true;
			}
		}

		//check for both bed and vcf
		//bed present?
		if (queryRequest.getBedRegions() != null && queryRequest.getVcfRegions() != null){
			errTxtForUser= "Please provide either bed regions or vcf regions, not both.";
			lg.debug(errTxtForUser);
			return false;
		}
		return true;
	}

	
	public ArrayList<String>  fetchTruncFilePathsUserCanSee () throws IOException{
		ArrayList<String> canSee = new ArrayList<String>();
		
		if (regExDirPathUser == null) canSee.addAll(dirTNameQueryIndexes.keySet());
		else {
			//for each trunc file path
			for (String tp: dirTNameQueryIndexes.keySet()) {
				for (Pattern p: regExDirPathUser){
					if (p.matcher(tp).matches()){						
						canSee.add(tp);
						break;
					}
				}
			}
		}
		lg.debug("User can see: "+canSee.toString());
		if (canSee.size() == 0) {
			lg.error("User cannot see any of the indexed data directories?!");
			throw new IOException("User cannot see any of the indexed data directories?! ");
		}
		return canSee;
	}
	
	/**Given a users access restrictions and provided regExDirPath s, returns the SingleQuerys to search.
	 * If none were found returns null.*/
	public ArrayList<SingleQuery> fetchQueriesToSearch (){
		ArrayList<SingleQuery> toSearch = new ArrayList<SingleQuery>();
		//filter on dir path?
		if (regExDirPath == null) {
			//add all they can see
			for (String s: truncFilePathsUserCanSee) toSearch.add(dirTNameQueryIndexes.get(s));
		}
		else {
			//for each truncated file path they can see
			for (String s: truncFilePathsUserCanSee) {
				if (matchAllDirPathRegEx == false) {
					//for each reg ex dir path
					for (Pattern p: regExDirPath){
						if (p.matcher(s).matches()) {
							toSearch.add(dirTNameQueryIndexes.get(s));
							break;
						}
					}
				}
				else {
					boolean addIt = true;
					//for each reg ex dir path
					for (Pattern p: regExDirPath){
						if (p.matcher(s).matches() == false) {
							addIt = false;
							break;
						}
					}
					if (addIt) toSearch.add(dirTNameQueryIndexes.get(s));
					
				}
			}
		}
		if (toSearch.size() == 0) return null;
		return toSearch;
	}

	public JSONObject getCurrentSettings(File userFile){
		JSONObject jo = new JSONObject();
		if (userFile!= null) jo.put("inputFile", userFile.getName());
		jo.put("fetchOptions", fetchOptions);
		jo.put("matchVcf", matchVcf);
		//don't return data?
		if (excludeDataFromResults) jo.put("fetchData", false);
		else jo.put("fetchData", fetchData);
		jo.put("includeHeaders", includeHeaders);
		if (regExDirPath != null) {
			jo.put("matchAllDirPathRegEx", matchAllDirPathRegEx);
			ArrayList<String> pat = new ArrayList<String>();
			for (Pattern p: regExDirPath) pat.add(p.toString());
			jo.put("regExDirPath", pat);
		}
		if (regExFileName != null) {
			jo.put("matchAllFileNameRegEx", matchAllFileNameRegEx);
			ArrayList<String> pat = new ArrayList<String>();
			for (Pattern p: regExFileName) pat.add(p.toString());
			jo.put("regExFileName", pat);
		}
		if (regExDataLine != null) {
			jo.put("matchAllDataLineRegEx", matchAllDataLineRegEx);
			ArrayList<String> pat = new ArrayList<String>();
			for (Pattern p: regExDataLine) pat.add(p.toString());
			jo.put("regExDataLine", pat);
		}
		if (regExDataLineExclude != null) {
			ArrayList<String> pat = new ArrayList<String>();
			for (Pattern p: regExDataLineExclude) pat.add(p.toString());
			jo.put("regExDataLineExclude", pat);
		}
		if (regExDirPathUser != null) {
			ArrayList<String> patOne = new ArrayList<String>();
			for (Pattern p: regExDirPathUser) patOne.add(p.toString());
			jo.put("userDirPathRegEx", patOne);
		}
		if (userName != null)jo.put("userName", userName);
		
		if (bpPadding != 0)jo.put("bpPadding", bpPadding);
		
		//regions
		ArrayList<String> bedVcfRegions = new ArrayList<String>();
		if (queryRequest.getBedRegions() != null) for (String bed: queryRequest.getBedRegions()) bedVcfRegions.add(bed);
		if (queryRequest.getVcfRegions() != null) for (String vcf: queryRequest.getVcfRegions()) bedVcfRegions.add(vcf);
		if (bedVcfRegions.size() !=0) {
			jo.put("numberQueries", bedVcfRegions.size());
			jo.put("queries", bedVcfRegions);
		}
		return jo;
	}

	public void addArray(JSONObject jo, String key, @SuppressWarnings("rawtypes") Iterator it){
		ArrayList<String> sb = new ArrayList<String>();
		while (it.hasNext()) sb.add(it.next().toString());
		jo.put(key, sb);
	}

	/**Filter files to return based on this MQF, these have already been restricted to what they can see*/
	public JSONObject filterFiles(HashMap<File, ArrayList<TabixDataQuery>> fileTabixQueries){

		int numFiles = fileTabixQueries.size();
		int numToKeep = numFiles;
		boolean fileFiltering = false;

		//filter?
		if (filterOnRegEx){
			
			fileFiltering = true;
			ArrayList<File> toKeep = new ArrayList<File>();	
			
			//for each file
			for (File f: fileTabixQueries.keySet()){
				boolean addItPath = true;
				boolean addItName = true;
				String tPath = f.toString().substring(numCharToSkipForDataDir);
				
				//filter on file path?
				if (regExDirPath != null) {
					for (Pattern p: regExDirPath){
						addItPath = false;
						boolean match = p.matcher(tPath).matches();
						//just need one?
						if (matchAllDirPathRegEx == false && match == true) {
							addItPath = true;
							break;
						}
						//need all and it doesn't match
						else if (matchAllDirPathRegEx == true) {
							if (match == false) {
								addItPath = false;
								break;
							}
							else addItPath = true;
						}
					}
				}
				
				//filter on file name?
				if (addItPath == true && regExFileName != null) {
					for (Pattern p: regExFileName){
						addItName = false;
						boolean match = p.matcher(f.getName()).matches();
						//just need one?
						if (matchAllFileNameRegEx == false && match == true) {
							addItName = true;
							break;
						}
						//need all and it doesn't match
						else if (matchAllFileNameRegEx == true) {
							if (match == false) {
								addItName = false;
								break;
							}
							else addItName = true;
						}
					}
				}
				if (addItPath == true && addItName == true) toKeep.add(f);
			}
			
			//OK toss those not in toKeep
			fileTabixQueries.keySet().retainAll(toKeep);
			numToKeep = toKeep.size();
		}

		//filtering on?
		if (fileFiltering){
			//Make json object to track filtering
			JSONObject stats = new JSONObject();
			stats.put("filesPreFiltering", numFiles);
			stats.put("filesPostFiltering", numToKeep);
			return stats;
		}

		else return null;
	}

	//getters and setters
	public TreeSet<File> getDataFilesToReturn() {
		return dataFilesToReturn;
	}
	public void setDataFilesToReturn(TreeSet<File> dataFilesToReturn) {
		this.dataFilesToReturn = dataFilesToReturn;
	}
	public boolean isMatchVcf() {
		return matchVcf;
	}
	public void setMatchVcf(boolean matchVcf) {
		this.matchVcf = matchVcf;
	}
	public boolean isFetchData() {
		return fetchData;
	}
	public void setFetchData(boolean fetchData) {
		this.fetchData = fetchData;
	}
	public int getBpPadding() {
		return bpPadding;
	}
	public void setBpPadding(int bpPadding) {
		this.bpPadding = bpPadding;
	}
	public boolean includeHeaders() {
		return includeHeaders;
	}
	public void setIncludeHeaders(boolean b){
		includeHeaders = b;
	}
	public boolean isFilterOnRegEx() {
		return filterOnRegEx;
	}
	public void setFilterOnRegEx(boolean filterOnRegEx) {
		this.filterOnRegEx = filterOnRegEx;
	}

	public boolean isExcludeDataFromResults() {
		return excludeDataFromResults;
	}

	public void setExcludeDataFromResults(boolean excludeDataFromResults) {
		this.excludeDataFromResults = excludeDataFromResults;
	}

	public String getErrTxtForUser() {
		return errTxtForUser;
	}

	public Pattern[] getRegExDataLine() {
		return regExDataLine;
	}

	public boolean isMatchAllDataLineRegEx() {
		return matchAllDataLineRegEx;
	}

	public ArrayList<String> getTruncFilePathsUserCanSee() {
		return truncFilePathsUserCanSee;
	}

	public Pattern[] getRegExDirPathUser() {
		return regExDirPathUser;
	}

	public String getUserName() {
		return userName;
	}

	public boolean isFetchOptions() {
		return fetchOptions;
	}

	public void setFetchOptions(boolean fetchOptions) {
		this.fetchOptions = fetchOptions;
	}

	public Pattern[] getRegExDataLineExclude() {
		return regExDataLineExclude;
	}
}
