package edu.utah.hci.it;

import java.io.BufferedReader;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import edu.utah.hci.misc.Util;

public class SimpleBed implements Comparable<SimpleBed> {
	
	//fields
	private String chr;
	private int start;
	private int stop;
	private String name;
	
	//constructor
	public SimpleBed (String chr, int start, int stop, String name) {
		this.chr = chr;
		this.start = start;
		this.stop = stop;
		this.name = name;
	}
	
	/**Sorts by chromsome, start position, length (smallest to largest).*/
	public int compareTo(SimpleBed otherCoor){
		//sort by chromosome
		int compare = otherCoor.chr.compareTo(chr);
		if (compare !=0) return compare * -1;;
		//sort by start position
		if (start<otherCoor.start) return -1;
		if (start>otherCoor.start) return 1;
		// if same start, sort by length, smaller to larger
		int len = stop-start;
		int otherLen = otherCoor.stop-otherCoor.start;
		if (len<otherLen) return -1;
		if (len>otherLen) return 1;
		return 0;
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(chr); sb.append(Util.TAB);
		sb.append(start); sb.append(Util.TAB);
		sb.append(stop); sb.append(Util.TAB);
		sb.append(name);
		return sb.toString();
	}
	
	/**Splits the Bed[] by chrom, does not add strand onto the chrom name.
	 * Be sure to sort before calling! */
	public static HashMap<String, SimpleBed[]> splitBedByChrom(SimpleBed[] sortedBed) {
		HashMap<String, SimpleBed[]> chromRegions = new HashMap<String, SimpleBed[]>();
		ArrayList<SimpleBed> recordsAL = new ArrayList<SimpleBed>();
		//set first
		String oldChrom = sortedBed[0].getChr();
		recordsAL.add(sortedBed[0]);
		//for each region
		for (int i=1; i< sortedBed.length; i++){
			String testChrom = sortedBed[i].getChr();
			//is it the same chrom?
			if (oldChrom.equals(testChrom) == false){
				//close old
				SimpleBed[] v = new SimpleBed[recordsAL.size()];
				recordsAL.toArray(v);
				recordsAL.clear();
				chromRegions.put(oldChrom, v);
				oldChrom = testChrom;
				if (chromRegions.containsKey(oldChrom)) {
					System.err.println("ERROR: problem with spliting SimpleBed by chrom, looks like the array wasn't sorted!\n");
					return null;
				}
			}
			//save info
			recordsAL.add(sortedBed[i]);
		}
		//set last
		SimpleBed[] v = new SimpleBed[recordsAL.size()];
		recordsAL.toArray(v);
		chromRegions.put(oldChrom, v);
		return chromRegions;
	}
	
	/**Parses a tab delimited bed file: chrom, start, stop, text. Does NOT sort final result.*/
	public static SimpleBed[] parseFile(File bedFile){
		SimpleBed[] bed =null;
		String line = null;
		try{
			BufferedReader in = Util.fetchBufferedReader(bedFile);
			String[] tokens;
			ArrayList<SimpleBed> al = new ArrayList<SimpleBed>();
			//chrom, start, stop, text
			while ((line = in.readLine()) !=null) {
				if (line.length() ==0 || line.startsWith("#")) continue;
				tokens = Util.TAB.split(line);
				if (tokens.length < 3) continue;
				al.add(new SimpleBed(tokens[0], Integer.parseInt(tokens[1]), Integer.parseInt(tokens[2]), tokens[2]));
			}
			bed = new SimpleBed[al.size()];
			al.toArray(bed);
		}catch (Exception e){
			e.printStackTrace();
			Util.pl("Bad bed line? -> "+line+" from "+bedFile);
		}
		return bed;
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

	public String getName() {
		return name;
	}
}
