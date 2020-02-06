package edu.utah.hci.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.json.JSONObject;

/**	 
 * Helper class to build a query and store the results.
 * The default boolean is false. 
 * @param fetchoptions - true or false
 * @param matchvcf - true or false
 * @param includeheaders - true or false
 * @param fetchdata - true or false
 * @param regexall - partial file path(s) where ALL must match for intersecting dataset to be returned, e.g. vcf.gz and Hg38/Avatar/Somatic and Lung
 * @param regexone - partial file path(s) where one of the provided must match for intersecting dataset to be returned, e.g. vcf.gz or Hg38/Avatar/Somatic
 * @param regexalldata - case-insensitive regex expressions that must ALL match a record line to be returned, e.g. Pathogenic and BRCA1 and splice
 * @param regexonedata - case-insensitive regex expressions where one must match a record line to be returned, e.g. pathogenic or likely-pathogenic or TP53
 * @param vcf - vcf records to intersect - this or bed required
 * @param bed - bed records to intersect - this or vcf required*/
public class UserQuery {

	private ArrayList<String> fetchOptions = null;
	private ArrayList<String> matchVcf = null;
	private ArrayList<String> includeHeaders = null;
	private ArrayList<String> fetchData = null;
	private ArrayList<String> regexAll = null;
	private ArrayList<String> regexOne = null;
	private ArrayList<String> regexAllData = null;
	private ArrayList<String> regexOneData = null;
	private ArrayList<String> bedRegions = null;
	private ArrayList<String> vcfRecords= null;
	private String error = null;
	private JSONObject results = null;

	public HashMap<String,String> fetchQueryOptions(){
		HashMap<String, List<String>> hm = new HashMap<String, List<String>>();
		if (fetchOptions != null) hm.put("fetchoptions", fetchOptions);
		if (matchVcf != null) hm.put("matchvcf", matchVcf);
		if (includeHeaders != null) hm.put("includeheaders", includeHeaders);
		if (fetchData != null) hm.put("fetchdata", fetchData);
		if (regexAll != null) hm.put("regexall", regexAll);
		if (regexOne != null) hm.put("regexone", regexOne);
		if (regexAllData != null) hm.put("regexalldata", regexAllData);
		if (regexOneData != null) hm.put("regexonedata", regexOneData);
		if (bedRegions != null) hm.put("bed", bedRegions);
		if (vcfRecords != null) hm.put("vcf", vcfRecords);

		//format the options
		return Util.loadGetQueryServiceOptions(hm);
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
	public UserQuery addRegexAll(String regex){
		if (regexAll == null) regexAll = new ArrayList<String>();
		regexAll.add(regex);
		return this;
	}
	public UserQuery addRegexOne(String regex){
		if (regexOne == null) regexOne = new ArrayList<String>();
		regexOne.add(regex);
		return this;
	}
	public UserQuery addRegexAllData(String regex){
		if (regexAllData == null) regexAllData = new ArrayList<String>();
		regexAllData.add(regex);
		return this;
	}
	public UserQuery addRegexOneData(String regex){
		if (regexOneData == null) regexOneData = new ArrayList<String>();
		regexOneData.add(regex);
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
	
	
}