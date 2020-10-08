package edu.utah.hci.indexer;

public class IndexRegion implements Comparable<IndexRegion> {
	public int start;
	public int stop;
	public Integer fileId;
	
	public IndexRegion(int start, int stop, Integer fileId){
		this.start = start;
		this.stop = stop;
		this.fileId = fileId;
	}
	
	public String toString(){
		return start+"-"+stop+":"+fileId;
	}
	
	/**Sorts by start base, then by stop, then fileId, smaller to larger for all.*/
	public int compareTo(IndexRegion se){
		if (start<se.start) return -1;
		if (start>se.start) return 1;
		//same start, sort by stop, smaller to larger
		if (stop<se.stop) return -1;
		if (stop>se.stop) return 1;
		//same stop, sort by fileId[0], smaller to larger
		if (fileId<se.fileId) return -1;
		if (fileId>se.fileId) return 1;
		return 0;
	}
}