package edu.utah.hci.query;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import edu.utah.hci.query.tabix.TabixDataQuery;
import java.util.Iterator;
import java.util.TreeMap;

public class QueryFilter {

	private DataSources dataSources;

	/**One or more patterns that a file path or data line will be scored against to be included in the output. 
	 * Set the orRegexFilters to false to require all to match or true to only require one.*/
	private Pattern[] regExAll = null;
	private Pattern[] regExAllData = null;
	private Pattern[] regExOne = null;
	private Pattern[] regExOneData = null;
	private Pattern[] regExOneUser = null;
	private String userName = null;
	boolean filterOnRegEx = false;

	/**This hash is loaded with the File data sources to return. */
	private TreeSet<File> dataFilesToReturn = new TreeSet<File>();

	private boolean matchVcf = false;
	private boolean fetchData = false;
	private boolean forceFetchData = false;
	private boolean excludeDataFromResults = false;
	private boolean includeHeaders = false;
	private int bpPadding = 0;
	private int numCharToSkipForDataDir = 0;

	private static final Logger lg = LogManager.getLogger(QueryFilter.class); 

	//constructors
	public QueryFilter(DataSources dataSources, User user){
		this.dataSources = dataSources;
		numCharToSkipForDataDir = dataSources.getDataDir().getParentFile().toString().length()+1;
		if (user != null) {
			userName = user.getUserName();
			regExOneUser = user.getRegExOne();
			filterOnRegEx = true;
		}
	}

	public JSONObject getCurrentSettings(File userFile){
		JSONObject jo = new JSONObject();
		if (userFile!= null) jo.put("inputFile", userFile.getName());
		jo.put("matchVcf", matchVcf);
		//don't return data?
		if (excludeDataFromResults) jo.put("fetchData", false);
		else jo.put("fetchData", fetchData);
		jo.put("forceFetchData", forceFetchData);
		jo.put("includeHeaders", includeHeaders);
		if (filterOnRegEx) {
			if (regExAll != null) {
				ArrayList<String> pat = new ArrayList<String>();
				for (Pattern p: regExAll) pat.add(p.toString());
				jo.put("regExAll", pat);
			}
			if (regExAllData != null) {
				ArrayList<String> pat = new ArrayList<String>();
				for (Pattern p: regExAllData) pat.add(p.toString());
				jo.put("regExAllData", pat);
			}
			if (regExOne != null) {
				ArrayList<String> patOne = new ArrayList<String>();
				for (Pattern p: regExOne) patOne.add(p.toString());
				jo.put("regExOne", patOne);
			}
			if (regExOneData != null) {
				ArrayList<String> patOne = new ArrayList<String>();
				for (Pattern p: regExOneData) patOne.add(p.toString());
				jo.put("regExOneData", patOne);
			}
			if (regExOneUser != null) {
				ArrayList<String> patOne = new ArrayList<String>();
				for (Pattern p: regExOneUser) patOne.add(p.toString());
				jo.put("regExOneUser", patOne);
			}
		}
		if (userName != null){
			jo.put("userName", userName);
		}
		return jo;
	}

	public void addArray(JSONObject jo, String key, @SuppressWarnings("rawtypes") Iterator it){
		ArrayList<String> sb = new ArrayList<String>();
		while (it.hasNext()) sb.add(it.next().toString());
		jo.put(key, sb);
	}

	public JSONObject filter(HashMap<File, ArrayList<TabixDataQuery>> fileTabixQueries){
		int numFiles = fileTabixQueries.size();
		int numToKeep = numFiles;
		boolean fileFiltering = false;

		//filter?
		if (filterOnRegEx){
			fileFiltering = true;
			ArrayList<File> toKeep = new ArrayList<File>();		
			//for each file
			
			for (File f: fileTabixQueries.keySet()){
				
				boolean addIt = true;
				String fPath = f.toString();
				String tPath = fPath.substring(numCharToSkipForDataDir);
				//look at all
				if (regExAll !=null){
					for (Pattern p: regExAll){
						if (p.matcher(tPath).matches() == false){						
							addIt = false;
							break;
						}
					}
				}
				//look at one
				if (addIt && regExOne != null){
					addIt = false;				
					for (Pattern p: regExOne){
						if (p.matcher(tPath).matches()){						
							addIt = true;
							break;
						}
					}
				}
				//look at user one, note these patterns match the whole path not truncated path
				if (addIt && regExOneUser != null){
					addIt = false;
					for (Pattern p: regExOneUser){
						if (p.matcher(fPath).matches()){						
							addIt = true;
							break;
						}
					}
				}
				if (addIt) toKeep.add(f);
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
			//log stats
			lg.debug("Index File Filtering Stats:");
			lg.debug(stats.toString(3));
			return stats;
		}

		else return null;
	}


	public void addTrunkName(JSONObject jo, String key, Iterator<File> it){
		TreeMap<File, String> dataFileDisplayName = dataSources.getDataFileDisplayName();
		ArrayList<String> sb = new ArrayList<String>();
		while (it.hasNext()) {
			File f = it.next();
			sb.add(dataFileDisplayName.get(f));
		}
		jo.put(key, sb);
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
	public boolean isForceFetchData() {
		return forceFetchData;
	}
	public void setForceFetchData(boolean forceFetchData) {
		this.forceFetchData = forceFetchData;
	}
	public boolean isFilterOnRegEx() {
		return filterOnRegEx;
	}
	public void setFilterOnRegEx(boolean filterOnRegEx) {
		this.filterOnRegEx = filterOnRegEx;
	}

	public Pattern[] getRegExAll() {
		return regExAll;
	}

	public void setRegExAll(Pattern[] regExAll) {
		this.regExAll = regExAll;
	}

	public Pattern[] getRegExOne() {
		return regExOne;
	}

	public void setRegExOne(Pattern[] regExOne) {
		this.regExOne = regExOne;
	}

	public Pattern[] getRegExAllData() {
		return regExAllData;
	}

	public void setRegExAllData(Pattern[] regExAllData) {
		this.regExAllData = regExAllData;
	}

	public Pattern[] getRegExOneData() {
		return regExOneData;
	}

	public void setRegExOneData(Pattern[] regExOneData) {
		this.regExOneData = regExOneData;
	}

	public boolean isExcludeDataFromResults() {
		return excludeDataFromResults;
	}

	public void setExcludeDataFromResults(boolean excludeDataFromResults) {
		this.excludeDataFromResults = excludeDataFromResults;
	}
}
