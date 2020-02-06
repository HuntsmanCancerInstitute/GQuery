package edu.utah.hci.query.tabix;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;
import org.json.JSONObject;
import edu.utah.hci.it.SimpleBed;
import edu.utah.hci.query.Util;


public class TabixDataQuery {
		
	//fields
	private String chr;
	private int start; //interbase	
	private int stop; //interbase
	private String input = null; //user input region unmodified
	
	//just for vcf, need pos since the start and stop might be padded
	private String pos = null;
	private String ref = null;
	private String[] alts = null;
	
	private HashMap<File, ArrayList<String>> sourceResults = new HashMap<File, ArrayList<String>>();
	private TreeMap<File, String> dataFileDisplayName = null;
	
	//from tabix intersecting file id approach, will be nulled after initial search
	private HashSet<Integer> intersectingFileIds = null;
	
	//constructors
	public TabixDataQuery(SimpleBed bed, TreeMap<File, String> dataFileDisplayName) {
		chr = bed.getChr();
		start = bed.getStart();
		stop = bed.getStop();
		input = bed.getName();
		this.dataFileDisplayName = dataFileDisplayName;
	}
	
	/**Good to call if working with vcf data so that an exact match can be made*/
	public void parseVcf(){
		String[] t = Util.TAB.split(input);
		pos = t[1];
		ref = t[3];
		alts = Util.COMMA.split(t[4]);
	}
	
	/**Comparses these vcf args against the TQ for exact matching, note only one of the alts must match, not all.*/
	public boolean compareVcf(String otherPos, String otherRef, String[] otherAlts){
		if (pos.equals(otherPos) == false) return false;
		if (ref.equals(otherRef) == false) return false;
		//one of the alts needs to match
		for (String thisAlt: alts){
			for (String thatAlt: otherAlts){
				if (thisAlt.equals(thatAlt)) return true;
			}
		}
		return false;
	}

	/*Need to synchronize this since multiple threads could be adding results simultaneously.*/
	public synchronized void addResults(File source, ArrayList<String> results){
		ArrayList<String> al = sourceResults.get(source);
		if (al == null) {
			al = new ArrayList<String>();
			sourceResults.put(source, al);
		}
		al.addAll(results);
	}

	public String getTabixCoordinates() {
		return chr+":"+(start+1)+"-"+stop;
	}
	
	public String getInterbaseCoordinates() {
		return chr+":"+start+"-"+stop;
	}
	
	public JSONObject getResults(boolean returnData){
		JSONObject jo = new JSONObject();
		jo.put("chr", chr);
		jo.put("start", start);
		jo.put("stop", stop);
		if (input.length()!=0) jo.put("input", input);
		jo.put("numberHits", sourceResults.size());
		if (sourceResults.size()!=0){
			jo.put("hits", getHits(returnData));
		}
		return jo;
	}
	
	private ArrayList<JSONObject> getHits(boolean returnData){
		ArrayList<JSONObject> hits = new ArrayList<JSONObject>();
		//for each file
		for (File source: sourceResults.keySet()){
			String trimmedName = dataFileDisplayName.get(source);
			JSONObject hit = new JSONObject();
			hit.put("source", trimmedName);
			//any data loaded?
			if (returnData) {
				ArrayList<String> records = sourceResults.get(source);
				if (records != null && records.size()!= 0) hit.put("data", records);
			}
			hits.add(hit);
		}
		return hits;
	}
	
	public static String getInterbaseCoordinates(ArrayList<TabixDataQuery> al){
		StringBuilder sb = new StringBuilder();
		sb.append(al.get(0).getInterbaseCoordinates());
		for (int i=1; i< al.size(); i++){
			sb.append(",");
			sb.append(al.get(i).getInterbaseCoordinates());
		}
		return sb.toString();
	}

	public HashMap<File, ArrayList<String>> getSourceResults() {
		return sourceResults;
	}
	public String getChr() {
		return chr;
	}
	public int getStart() {
		return start;
	}
	public int getStop() {
		return stop;
	}
	public void setIntersectingFileIds(HashSet<Integer> fileIds) {
		intersectingFileIds = fileIds;
	}
	public HashSet<Integer> getIntersectingFileIds() {
		return intersectingFileIds;
	}
}
