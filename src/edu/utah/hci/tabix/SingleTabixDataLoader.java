package edu.utah.hci.tabix;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.utah.hci.misc.Util;
import edu.utah.hci.query.QueryFilter;
import htsjdk.tribble.readers.TabixReader;

public class SingleTabixDataLoader implements Runnable{

	//fields
	private boolean failed = false;
	private TabixDataLineLoader tabixDataLineLoader;
	private TabixDataLineLookupJob tc = null;
	private TabixDataQuery tq = null;
	private File tabixFile = new File("");
	private TabixReader reader = null;
	private Set<String> chromosomes = null;
	
	private Pattern[] regExDataLine = null;
	private Pattern[] regExDataLineExclude = null;
	private boolean matchAllDataLineRegEx = false;
	private boolean matchVcf = false;
	
	private HashSet<String> queriesWithData = new HashSet<String>();
	private HashSet<String> queriesWithDataThatPassRegEx = new HashSet<String>();
	
	private static final Logger lg = LogManager.getLogger(SingleTabixDataLoader.class);
	

	
	public SingleTabixDataLoader (TabixDataLineLoader tabixDataLineLoader, QueryFilter queryFilter ) throws IOException{
		this.tabixDataLineLoader = tabixDataLineLoader;
		
		//data line filter params
		regExDataLine = queryFilter.getRegExDataLine(); //may be null
		regExDataLineExclude = queryFilter.getRegExDataLineExclude();
		matchAllDataLineRegEx = queryFilter.isMatchAllDataLineRegEx();
		matchVcf = queryFilter.isMatchVcf();
	}
	
	public void run() {	
		try {
			//get next TabixLookupJob, returns null if no more work
			while ((tc = tabixDataLineLoader.getTabixLookupJob()) != null){ 
				
				//Different data file? Create new TabixReader?
				File dataFile = tc.getDataFile();
				if (dataFile.equals(tabixFile) == false) {
					tabixFile = dataFile;
					if (reader != null) reader.close();
					reader = new TabixReader(tabixFile.toString());
					chromosomes = reader.getChromosomes();
				}

				//get the TabixQuery and attempt to get an iterator
				tq = tc.getTabixDataQuery();
				TabixReader.Iterator it = fetchIterator();
				if (it == null) continue;


				String hit = null;
				boolean dataFound = false;
				ArrayList<String> al = new ArrayList<String>();
				
				//VCF? check that the vcf pos, ref, alt is the same, only one of the alts need match
				if (matchVcf && tabixFile.getName().endsWith(".vcf.gz")){
					while ((hit = it.next()) != null) {
						dataFound = true;
						//pass data line regex?
						if (passRegExData(hit)){
							String[] t = Util.TAB.split(hit);
							//pass vcf match?
							if (tq.compareVcf(t[1], t[3], Util.COMMA.split(t[4]))) al.add(hit);
						}
					}
				}
				//nope add everything
				else {					
					while ((hit = it.next()) != null) {
						dataFound = true;
						//pass data line regex?
						if (passRegExData(hit))al.add(hit);
					}
				}
				if (dataFound) queriesWithData.add(tq.getInterbaseCoordinates());
				
				// anything to save?
				if (al.size()!=0) {
					tq.addResults(tabixFile, al);
					queriesWithDataThatPassRegEx.add(tq.getInterbaseCoordinates());
				}
			}

			//close the last reader
			if (reader != null) reader.close();

			//update the query stats for all the processed jobs
			tabixDataLineLoader.updateQueryStats(queriesWithData, queriesWithDataThatPassRegEx);

		} catch (IOException e) {
			failed = true;
			lg.error("Error: searching "+tabixFile+" for "+tq.getTabixCoordinates() +"\n"+e.toString()+"\n"+e.fillInStackTrace());
		}
	}
	
	/**Examines a data line to see if it passes the data regExDataLine filters*/
	private boolean passRegExData(String hit) {
		//any filters?
		if (regExDataLine == null && regExDataLineExclude == null) return true;

		Matcher mat;
		
		//any data line regex?
		if (regExDataLine != null) {
			//check that all pass?
			if (matchAllDataLineRegEx) {
				for (Pattern pat: regExDataLine){
					mat = pat.matcher(hit);
					if (mat.matches() == false) return false;
				}
			}
			//check that one or more matches
			else {
				boolean pass = false;
				for (Pattern pat: regExDataLine){
					mat = pat.matcher(hit);
					if (mat.matches()) {
						pass = true;
						break;
					}
				}
				if (pass == false) return false;
			}
		}
		
		//any data line regex exclude
		if (regExDataLineExclude == null) return true;
		
		//if any match, return false
		for (Pattern pat: regExDataLineExclude){
			mat = pat.matcher(hit);
			if (mat.matches()) return false;
		}
		return true;
	}

	/**Use to try to fetch an iterator without then with 'chr' prepended to the coordinates.
	 * Returns null if not found*/
	private TabixReader.Iterator fetchIterator(){
		//look for the chromosome
		String chr = null;
		if (chromosomes.contains(tq.getChr())) chr = tq.getChr();
		if (chromosomes.contains("chr"+tq.getChr()))chr = "chr"+tq.getChr();
		if (chr == null) return null;
		return reader.query(chr, tq.getStart(), tq.getStop());
	}

	public boolean isFailed() {
		return failed;
	}
}
