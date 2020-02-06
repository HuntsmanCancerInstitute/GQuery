package edu.utah.hci.query.tabix;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import edu.utah.hci.query.QueryLoader;
import edu.utah.hci.query.QueryRequest;
import edu.utah.hci.query.Util;
import htsjdk.tribble.readers.TabixReader;

public class TabixDataLoader implements Runnable{

	//fields
	private boolean failed = false;
	private QueryLoader queryLoader;
	
	//working fields
	private TabixReader reader = null;
	private Set<String> chromosomes = null;
	private File tabixFile = null;
	private TabixDataQuery tq = null;
	private TabixDataChunk tc = null;
	private Pattern[] regExAllData = null;
	private Pattern[] regExOneData = null;
	private ArrayList<TabixDataQuery> toQuery;
	private long numberRetrievedResults = 0;
	private int numberQueriesWithResults = 0;
	private long numberSavedResults = 0;
	private boolean matchVcf = false;
	
	private static final Logger lg = LogManager.getLogger(TabixDataLoader.class);
	
	public TabixDataLoader (QueryLoader queryLoader) throws IOException{
		this.queryLoader = queryLoader;
	}
	
	public void run() {	
		try {
			//get next chunk of work
			while ((tc = queryLoader.getChunk()) != null){ 
				loadWorkChunk();
				
				int size = toQuery.size();
				for (int i=0; i< size; i++){
					tq = toQuery.get(i);
					//fetch iterator, returns null if no chr
					TabixReader.Iterator it = fetchIterator();
					if (it == null) continue;
					
					String hit;

					ArrayList<String> al = new ArrayList<String>();
					int numRes = 0;
					//check that the vcf pos, ref, alt is the same, only one of the alts need match
					if (matchVcf && tabixFile.getName().endsWith(".vcf.gz")){
						while ((hit = it.next()) != null) {
							//pass data line regex?
							if (passRegExData(hit)){
								String[] t = Util.TAB.split(hit);
								//pass vcf match?
								if (tq.compareVcf(t[1], t[3], Util.COMMA.split(t[4]))) {
									al.add(hit);
									numberSavedResults++;
								}
							}
							numRes++;
						}
					}
					//nope add everything
					else {					
						while ((hit = it.next()) != null) {
							//pass data line regex?
							if (passRegExData(hit)){ 
								al.add(hit);
								numberSavedResults++;
							}
							numRes++;
						}
					}
					// added in check
					if (al.size()!=0) tq.addResults(tabixFile, al);
					
					//stats
					if (numRes != 0) {
						numberQueriesWithResults++;
						numberRetrievedResults += numRes;
					}
					//good to know if a query was created yet no results came back, this should be rare, comes form how tabix defines the foot print and how Query does it.
					else lg.debug("\tNo data from "+tabixFile+" for region "+tq.getInterbaseCoordinates()+" -> converted tbx query-> "+tq.getTabixCoordinates() );
				}
				
				//update the QueryRequest
				updateQueryStats();
			}	
			//return the reader
			if (tabixFile != null) queryLoader.getSetTabixReader(tabixFile, reader);
		} catch (IOException e) {
			failed = true;
			lg.error("Error: searching "+tabixFile+" for "+tq.getTabixCoordinates() +"\n"+e.toString()+"\n"+e.fillInStackTrace());
		}
	}
	
	/**Examines a data line to see if it passes the data regex filters*/
	private boolean passRegExData(String hit) {
		Matcher mat;
		//check the all?
		if (regExAllData != null) {
			for (Pattern pat: regExAllData){
				mat = pat.matcher(hit);
				if (mat.matches() == false) return false;
			}
		}
		
		//OK all looks good so check the one
		if (regExOneData != null){
			for (Pattern pat: regExOneData){
				mat = pat.matcher(hit);
				if (mat.matches()) return true;
			}
			//nope none matched
			return false;
		}
		
		return true;
	}

	/**Use to try to fetch an iterator without then with chr prepended to the coordinates.
	 * Returns null if not found*/
	private TabixReader.Iterator fetchIterator(){
		//look for the chromosome
		String chr = null;
		if (chromosomes.contains(tq.getChr())) chr = tq.getChr();
		if (chromosomes.contains("chr"+tq.getChr()))chr = "chr"+tq.getChr();
		if (chr == null) return null;
		return reader.query(chr, tq.getStart(), tq.getStop());
	}
	
	/*This loads a chunk of work to do and deals with the readers, returning the old or fetching a new one.*/
	private void loadWorkChunk() throws IOException {
		//check reader
		if (reader != null){
			//fetch new?
			if (tabixFile.equals(tc.getTabixFile()) == false){
				//return old
				queryLoader.getSetTabixReader(tabixFile, reader);
				//get new
				tabixFile = tc.getTabixFile();
				reader = queryLoader.getSetTabixReader(tabixFile, null);
			}
		}
		else {
			tabixFile = tc.getTabixFile();
			reader = queryLoader.getSetTabixReader(tabixFile, null);
		}
		//set working objects
		chromosomes = reader.getChromosomes();
		toQuery = tc.getQueries();
		matchVcf = tc.getQueryRequest().getFilter().isMatchVcf();
		regExAllData = tc.getQueryRequest().getFilter().getRegExAllData();
		regExOneData = tc.getQueryRequest().getFilter().getRegExOneData();
		
		//reset counters
		numberRetrievedResults = 0;
		numberQueriesWithResults = 0;
		numberSavedResults = 0;
	}
	
	private void updateQueryStats() {
		QueryRequest qr = tc.getQueryRequest();
		qr.incrementNumQueriesWithResults(numberQueriesWithResults);
		qr.incrementNumRetrievedResults(numberRetrievedResults);
		qr.incrementNumSavedResults(numberSavedResults);
	}

	public String toString(){
		return "TabixLoader:\n\t"+tabixFile.toString()+"\n\t"+toQuery.size();
	}
	public boolean isFailed() {
		return failed;
	}
}
