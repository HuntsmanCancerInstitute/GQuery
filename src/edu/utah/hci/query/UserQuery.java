package edu.utah.hci.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.json.JSONObject;

import edu.utah.hci.misc.Util;

/**	 
 * Helper class to build a query and store the results.
 * The default boolean is false. 
 * @param fetchOptions - true or false
 * @param matchVcf - true or false
 * @param includeHeaders - true or false
 * @param fetchData - true or false
 * @param regExDirPath - for matching truncated dir paths, just from the user specified Data/xxx/xxx dir on down, remember each is surrounded with .*XXXXXX.*
 * @param regExFileName - for matching file names
 * @param regExDataLine - for matching record data lines
 * @param regExDataLineExclude - for excluding record data lines
 * @param matchAllDirPathRegEx - boolean indicating whether all regExDirPaths must match 
 * @param matchAllFileNameRegEx - boolean indicating whether all regExFileNames must match
 * @param matchAllDataRegEx - boolean indicating whether all regExDataLine lines must match
 * @param vcf - vcf records to intersect - this or bed required
 * @param bed - bed records to intersect - this or vcf required*/
public class UserQuery {

	private ArrayList<String> fetchOptions = null;
	private boolean fetchOptionsFlag = false;
	private ArrayList<String> matchVcf = null;
	private ArrayList<String> includeHeaders = null;
	private ArrayList<String> fetchData = null;
	
	private ArrayList<String> regExDirPath = null;
	private ArrayList<String> regExFileName = null;
	private ArrayList<String> regExDataLine = null;
	private ArrayList<String> regExDataLineExclude = null;
	
	private ArrayList<String> matchAllDirPathRegEx = null;
	private ArrayList<String> matchAllFileNameRegEx = null;
	private ArrayList<String> matchAllDataLineRegEx = null;
	
	private ArrayList<String> bedRegions = null;
	private ArrayList<String> vcfRecords= null;
	private ArrayList<String> bpPadding= null;
	private String error = null;
	private JSONObject results = null;

	public HashMap<String,String> fetchQueryOptions(){
		
		HashMap<String, List<String>> hm = new HashMap<String, List<String>>();
		if (fetchOptions != null) hm.put("fetchOptions", fetchOptions);
		if (matchVcf != null) hm.put("matchVcf", matchVcf);
		if (includeHeaders != null) hm.put("includeHeaders", includeHeaders);
		if (fetchData != null) hm.put("fetchData", fetchData);
		if (bpPadding != null) hm.put("bpPadding", bpPadding);
		
		if (regExDirPath != null) hm.put("regExDirPath", regExDirPath);
		if (regExFileName != null) hm.put("regExFileName", regExFileName);
		if (regExDataLine != null) hm.put("regExDataLine", regExDataLine);
		if (regExDataLineExclude != null) hm.put("regExDataLineExclude", regExDataLineExclude);
		
		if (matchAllDirPathRegEx != null) hm.put("matchAllDirPathRegEx", matchAllDirPathRegEx);
		if (matchAllFileNameRegEx != null) hm.put("matchAllFileNameRegEx", matchAllFileNameRegEx);
		if (matchAllDataLineRegEx != null) hm.put("matchAllDataLineRegEx", matchAllDataLineRegEx);

		if (bedRegions != null) hm.put("bed", bedRegions);
		if (vcfRecords != null) hm.put("vcf", vcfRecords);

		//format the options
		return Util.loadGetMultiQueryServiceOptions(hm);
	}
	
	public void clearResultsRegionsRecords(){
		error = null;
		results = null;
		if (bedRegions != null) bedRegions.clear();
		if (vcfRecords != null) vcfRecords.clear();
	}
	
	public UserQuery fetchOptions(){
		fetchOptions = new ArrayList<String>();
		fetchOptions.add("true");
		fetchOptionsFlag = true;
		return this;
	}
	public UserQuery fetchData(){
		fetchData = new ArrayList<String>();
		fetchData.add("true");
		return this;
	}
	public UserQuery matchVcf(){
		matchVcf = new ArrayList<String>();
		matchVcf.add("true");
		return this;
	}
	public UserQuery includeHeaders(){
		includeHeaders = new ArrayList<String>();
		includeHeaders.add("true");
		return this;
	}
	
	public UserQuery matchAllDirPathRegEx(){
		matchAllDirPathRegEx = new ArrayList<String>();
		matchAllDirPathRegEx.add("true");
		return this;
	}
	public UserQuery matchAllFileNameRegEx(){
		matchAllFileNameRegEx = new ArrayList<String>();
		matchAllFileNameRegEx.add("true");	
		return this;
	}
	public UserQuery matchAllDataLineRegEx(){
		matchAllDataLineRegEx = new ArrayList<String>();
		matchAllDataLineRegEx.add("true");
		return this;
	}
	
	public UserQuery addRegExDirPath(String regEx){
		if (regExDirPath == null) regExDirPath = new ArrayList<String>();
		regExDirPath.add(regEx);
		return this;
	}
	public UserQuery addRegExFileName(String regEx){
		if (regExFileName == null) regExFileName = new ArrayList<String>();
		regExFileName.add(regEx);
		return this;
	}
	public UserQuery addRegExDataLine(String regEx){
		if (regExDataLine == null) regExDataLine = new ArrayList<String>();
		regExDataLine.add(regEx);
		return this;
	}
	public UserQuery addRegExDataLineExclude(String regEx){
		if (regExDataLineExclude == null) regExDataLineExclude = new ArrayList<String>();
		regExDataLineExclude.add(regEx);
		return this;
	}
	
	public UserQuery addVcfRecord(String vcf){
		if (vcfRecords == null) vcfRecords = new ArrayList<String>();
		vcfRecords.add(vcf);
		return this;
	}
	public UserQuery addBedRegion(String bed){
		if (bedRegions == null) bedRegions = new ArrayList<String>();
		bedRegions.add(bed);
		return this;
	}
	public UserQuery addBpPadding(String bps){
		if (bpPadding == null) bpPadding = new ArrayList<String>();
		bpPadding.add(bps);
		return this;
	}
	public String getError() {
		return error;
	}
	public void setError(String error) {
		this.error = error;
	}
	public JSONObject getResults() {
		return results;
	}
	public void setResults(JSONObject results) {
		this.results = results;
	}

	public boolean isFetchOptionsFlag() {
		return fetchOptionsFlag;
	}
	
}