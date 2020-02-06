package edu.utah.hci.query;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import edu.utah.hci.it.SimpleBed;
import edu.utah.hci.query.tabix.TabixDataChunk;
import edu.utah.hci.query.tabix.TabixDataQuery;;

public class QueryRequest {

	//fields
	private QueryFilter queryFilter; 
	private Query query;
	private File inputFile = null; //might be null if using GET request
	private String[] bedRegions = null;
	private String[] vcfRegions = null;
	private DataSources dataSources;
	private QueryIndex queryIndex;
	private Set<String> availableIndexedChromosomes = null;
	private ArrayList<TabixDataChunk> tabixChunks = new ArrayList<TabixDataChunk>();
	private Iterator<TabixDataChunk> chunkIterator = null;
	private long numberRetrievedResults = 0;
	private int numberQueriesWithResults = 0;
	private long numberSavedResults = 0;
	private long time2Load = 0;
	public static final Pattern BED_COLON_DASH_PATTERN = Pattern.compile("(\\w+):([\\d\\,]+)-([\\d\\,]+)");
	public static final Pattern END_POSITION = Pattern.compile(".*END=(\\d+).*", Pattern.CASE_INSENSITIVE);
	private static final Logger lg = LogManager.getLogger(QueryRequest.class);
	
	//from run
	/*If not null then the query failed.*/
	private String errTxtForUser = null;
	/*Container of warning messages to set back to the user.*/
	private ArrayList<String> warningTxtForUser = new ArrayList<String>();
	/*Json objects with info elated to the query*/
	private JSONObject indexQueryStats = null;
	private JSONObject indexFileFilteringStats = null;
	private JSONObject dataRetrievalStats = null;

	/*Contains all the TabixQueries split by chromosome. Some may contain results.*/
	private HashMap<String, TabixDataQuery[]> chrTabixQueries = new HashMap<String, TabixDataQuery[]>();
	
	/*Contains a File pointer to a data source and the associated TabixQueries that intersect it.
	 * These are to be used in a tabix search.*/
	private HashMap<File, ArrayList<TabixDataQuery>> fileTabixQueries = new HashMap<File, ArrayList<TabixDataQuery>>();

	
	/**REST constructor.
	 * @param user */
	public QueryRequest(Query query, File inputFile, HashMap<String, String> options, User user) throws IOException {
		this.query = query;
		this.dataSources = query.getDataSources();
		this.queryIndex = query.getQueryIndex();
		this.inputFile = inputFile;
		availableIndexedChromosomes = queryIndex.getAvailableChromosomes();
		
		//create a new file filter and modify it with the input
		queryFilter = new QueryFilter(dataSources, user);

		//parse and set the options
		if (walkAndSetOptions(options)) {

			//find dataSource files that intersect regions in the input file bed or vcf, will return false if bed or vcf not found
			if (intersectRegionsWithIndex(inputFile)){
				
				//perform the index search
				indexQueryStats = queryIndex.queryFileIndex(this);
				
				//add in all the forceFetch datasets?
				if (queryFilter.isForceFetchData() && dataSources.getSkippedDataSourceNames().size() !=0) addInForceFetchDataSources();
					
				//filter which files to fetch data from base on the parent dir name, the actual file, the file extension 
				indexFileFilteringStats = queryFilter.filter(fileTabixQueries);
				
				
				//any left?
				if (fileTabixQueries.size()!=0){
					//fetch the underlying data (slow)? 
					if (queryFilter.isFetchData()) {
						makeTabixChunks();
						query.getQueryLoader().loadTabixQueriesWithData(this);
						
						//make json stats object
						makeDataRetrievalStats();
						
						//log the results
						lg.debug("Data retrieval stats:");
						lg.debug(dataRetrievalStats.toString(5));
					}
					//just indicate which file hit which region (fast)
					else loadTabixQueriesWithFileSources();
				}
				else lg.debug("No intersecting files after filtering");
			}
		}
	}
	
	/**Collects all the force fetch data sources and associates them with all of the tabix queries.*/
	private void addInForceFetchDataSources() {
		//collect all the TabixQueries
		ArrayList<TabixDataQuery> allQueries = new ArrayList<TabixDataQuery>();
		for (TabixDataQuery[] tq: chrTabixQueries.values()){
			for (TabixDataQuery t: tq) allQueries.add(t);
		}
		for (File f: dataSources.getForceFetchDataSources()) fileTabixQueries.put(f, allQueries);
	}

	public void makeDataRetrievalStats(){
		dataRetrievalStats = new JSONObject();
		dataRetrievalStats.put("millSecToLoadRecords", time2Load);
		dataRetrievalStats.put("queriesWithTabixRecords", numberQueriesWithResults);
		dataRetrievalStats.put("recordsRetrieved", numberRetrievedResults);
		dataRetrievalStats.put("recordsReturnedToUser", numberSavedResults);
	}
	
	
	private boolean walkAndSetOptions(HashMap<String, String> options) {
		//walk through each option, forcing key to be case insensitive
		for (String key: options.keySet()){
			
			//fetchData?
			if (key.equals("fetchdata")){
				String bool = options.get("fetchdata").toLowerCase();
				//true
				if (bool.startsWith("t")) queryFilter.setFetchData(true);
				//force fetching on all data sources
				else if (bool.startsWith("fo")){
					queryFilter.setFetchData(true);
					queryFilter.setForceFetchData(true);
				}
				else queryFilter.setFetchData(false);
			}
			//match vcf?
			else if (key.equals("matchvcf")){
				String bool = options.get("matchvcf").toLowerCase();
				if (bool.startsWith("t")) queryFilter.setMatchVcf(true);
				else queryFilter.setMatchVcf(false);
			}
			//include headers?
			else if (key.equals("includeheaders")){
				String bool = options.get("includeheaders").toLowerCase();
				if (bool.startsWith("t")) queryFilter.setIncludeHeaders(true);
				else queryFilter.setIncludeHeaders(false);
			}
			
			//filter with regular expressions where all must match?
			else if (key.equals("regexall")){
				String[] rgs = Util.SEMI_COLON.split(options.get("regexall"));
				Pattern[] ps = new Pattern[rgs.length];
				for (int i=0; i< rgs.length; i++) ps[i] = Pattern.compile(".*"+rgs[i]+".*");
				queryFilter.setFilterOnRegEx(true);
				queryFilter.setRegExAll(ps);
			}
			
			//filter data lines with regular expressions where all patterns must match?
			else if (key.equals("regexalldata")){
				String[] rgs = Util.SEMI_COLON.split(options.get("regexalldata"));
				Pattern[] ps = new Pattern[rgs.length];
				for (int i=0; i< rgs.length; i++) ps[i] = Pattern.compile(".*"+rgs[i]+".*", Pattern.CASE_INSENSITIVE);
				queryFilter.setFilterOnRegEx(true);
				queryFilter.setRegExAllData(ps);
			}
			
			//filter data lines with regular expressions where all patterns must match?
			else if (key.equals("regexonedata")){
				String[] rgs = Util.SEMI_COLON.split(options.get("regexonedata"));
				Pattern[] ps = new Pattern[rgs.length];
				for (int i=0; i< rgs.length; i++) ps[i] = Pattern.compile(".*"+rgs[i]+".*", Pattern.CASE_INSENSITIVE);
				queryFilter.setFilterOnRegEx(true);
				queryFilter.setRegExOneData(ps);
			}
			
			//filter with regular expressions where all must match?
			else if (key.equals("regexone")){
				String[] rgs = Util.SEMI_COLON.split(options.get("regexone"));
				Pattern[] ps = new Pattern[rgs.length];
				for (int i=0; i< rgs.length; i++) ps[i] = Pattern.compile(".*"+rgs[i]+".*");
				queryFilter.setFilterOnRegEx(true);
				queryFilter.setRegExOne(ps);
			}
			
			//any incomming bed or vcf regions from a GET request?  not present with POST
			else if (key.equals("bed")) {
				bedRegions = Util.SEMI_COLON.split(options.get("bed"));
			}
			else if (key.equals("vcf")) vcfRegions = Util.SEMI_COLON.split(options.get("vcf"));
			
			//authentication token, ignore
			else if (key.equals("key")){}
			
			//something odd coming in, throw error
			else {
				errTxtForUser= "Unrecognized cmd -> "+ key+"="+options.get(key);
				lg.error("Unrecognized cmd -> "+ key+"="+options.get(key));
				return false;
			}
		}
		
		//are they indicating matchVcf or filtering by data regexes? if so then fetchData so we can examine the ref alt etc but indicate they don't want the data hits returned just the number
		if (queryFilter.isMatchVcf() || queryFilter.getRegExAllData() != null || queryFilter.getRegExOneData() != null){
			if (queryFilter.isFetchData() == false){
				queryFilter.setExcludeDataFromResults(true);
				queryFilter.setFetchData(true);
			}
		}
		
		//have they set fetchData to force? Require that they have provided at least one regex
		if (queryFilter.isForceFetchData() == true){
			if (queryFilter.filterOnRegEx == false){
				errTxtForUser= "please provide one or more regEx filters to limit the data paths that are tabix queried. This is required when setting fetchData=force";
				lg.debug(errTxtForUser);
				return false;
			}
		}
		
		//check for both bed and vcf
		//bed present?
		if (bedRegions != null && vcfRegions != null){
			errTxtForUser= "Please provide either bed regions or vcf regions, not both.";
			lg.debug(errTxtForUser);
			return false;
		}
		return true;
	}


	/**This takes a bed file, parses each region into a TabixQuery object and identifies which file data sources contain data that intersects it.*/
	public boolean intersectRegionsWithIndex(File inputFile) throws IOException {

		//is the input present, thus POST request?
		if (inputFile != null){
			String name = inputFile.getName();
			if (name.endsWith(".bed.gz") || name.endsWith(".bed")){
				
				//load file into String[]
				loadBedRegionsAsStringArray(inputFile);
				
				//convert them to TabixQuery 
				chrTabixQueries = parseBedRegions();
			}

			else if (name.endsWith(".vcf.gz") || name.endsWith(".vcf")){
				chrTabixQueries = convertVcfToTabixQuery(inputFile);  
			}

			else {
				errTxtForUser = "Input file must be either bed or vcf (.gz/.zip OK) format";
				lg.debug(errTxtForUser);
				return false;
			}
		}
		
		//nope, GET request with already loaded regions
		else {
			if (vcfRegions != null) chrTabixQueries = parseVcfRegions();
			else if (bedRegions != null) {
				chrTabixQueries = parseBedRegions();
			}
		}	
		
		//any queries?
		if (chrTabixQueries != null && chrTabixQueries.size() != 0) return true;
		return false;
	}
	
	private void loadBedRegionsAsStringArray(File bedFile) throws IOException {
		BufferedReader in = Util.fetchBufferedReader(bedFile);
		String line;
		ArrayList<String> bedAl = new ArrayList<String>();
		while ((line = in.readLine()) != null){
			line=line.trim();
			if (line.startsWith("#") == false && line.length()!=0) bedAl.add(line);
		}
		//clean up and stat incrementing
		in.close();

		bedRegions = new String[bedAl.size()];
		bedAl.toArray(bedRegions);
	}

	private HashMap<String, TabixDataQuery[]> parseBedRegions() throws IOException {
		ArrayList<SimpleBed> bedAl = new ArrayList<SimpleBed>();
		for (String line : bedRegions){
			SimpleBed bed = makeBedFromBedRecord(line);			
			if (bed!= null) bedAl.add(bed);
		}
		//covert to TQ?
		if (bedAl.size() == 0) return null;
		return convertBedAL2TabixQuerys(bedAl, false);
	}

	private HashMap<String, TabixDataQuery[]> parseVcfRegions() throws IOException {
		ArrayList<SimpleBed> bedAl = new ArrayList<SimpleBed>();
		for (String line : vcfRegions){
			SimpleBed bed = makeBedFromVcfRecord(line);
			if (bed!= null) bedAl.add(bed);
		}
		//covert to TQ
		if (bedAl.size() == 0) return null;
		return convertBedAL2TabixQuerys(bedAl, true);
	}
	
	private void loadTabixQueriesWithFileSources() {
		//for each data source file
		for (File tabixFile: fileTabixQueries.keySet()){
			//pull queries that intersect it
			ArrayList<TabixDataQuery> toFetch = fileTabixQueries.get(tabixFile);
			//for each query, add the data source
			for (TabixDataQuery tq: toFetch) tq.getSourceResults().put(tabixFile, null);
		}
	}
	
	public HashMap<String, TabixDataQuery[]> convertVcfToTabixQuery(File vcf) throws IOException{
		BufferedReader in = Util.fetchBufferedReader(vcf);
		String line;
		ArrayList<SimpleBed> bedAl = new ArrayList<SimpleBed>();
		while ((line = in.readLine()) != null){
			if (line.startsWith("#") == false){
				SimpleBed bed = makeBedFromVcfRecord(line);
				if (bed!= null) bedAl.add(bed);
			}
		}
		//clean up and stat incrementing
		in.close();
		//covert to TQ
		if (bedAl.size() == 0) return null;
		return convertBedAL2TabixQuerys(bedAl, true);
	}
	
	public SimpleBed makeBedFromBedRecord(String bedLine) throws IOException{
		String[] t = Util.TAB.split(bedLine);
		
		//nothing split? try looking for X:1234-12345 form
		if (t.length == 1) t= splitColonDashBed(bedLine);
		
		//trim chr
		if (t[0].startsWith("chr")) t[0]= t[0].substring(3);
		
		//check bed
		String error = Util.checkBed(t);
		if (error != null){
			String message = "WARNING: a problem was found with this bed record,  excluding from search -> "+bedLine+" message: "+error;
			warningTxtForUser.add(message);
			lg.debug(message);
		}
		
		//check chrom
		else if (availableIndexedChromosomes.contains(t[0]) == false){
			String message = "WARNING: Failed to find a chromosome for '"+t[0]+ "' excluding from search -> "+bedLine;
			warningTxtForUser.add(message);
			lg.debug(message);
		}
		
		//parse it!
		else {
			//remove any potential commas
			t[1] = Util.COMMA.matcher(t[1]).replaceAll("");
			t[2] = Util.COMMA.matcher(t[2]).replaceAll("");
			int start = Integer.parseInt(t[1]);
			int stop = Integer.parseInt(t[2]);
			if (start < 0) start = 0; 	
			return new SimpleBed(t[0], start, stop, bedLine);
			
		}
		return null;
	}

	
	private String[] splitColonDashBed(String bedLine) {
		Matcher mat = BED_COLON_DASH_PATTERN.matcher(bedLine);
		if (mat.matches()) return new String[]{mat.group(1),mat.group(2), mat.group(3)};
		return new String[]{bedLine};
	}

	public SimpleBed makeBedFromVcfRecord(String vcfLine) throws IOException{
		String[] t = Util.TAB.split(vcfLine);
		//trim chr
		if (t[0].startsWith("chr")) t[0]= t[0].substring(3);
		
		//check vcf
		String error = Util.checkVcf(t);
		if (error != null){
			warningTxtForUser.add("WARNING: a problem was found with this vcf record,  excluding from search -> "+vcfLine+" message: "+error);
			lg.debug(warningTxtForUser);
		}
		
		//check chrom
		else if (availableIndexedChromosomes.contains(t[0]) == false){
			warningTxtForUser.add("WARNING: Failed to find a chromosome for '"+t[0]+ "' excluding from search -> "+vcfLine);
			lg.debug(warningTxtForUser);
		}
		
		//parse it!
		else {
			//fetch start and stop for effected bps.
			int[] startStop = fetchEffectedBps(t);
			if (startStop != null) {
				if (startStop[0] < 0) startStop[0] = 0;
				return new SimpleBed(t[0], startStop[0], startStop[1], vcfLine);
			}
			else {
				warningTxtForUser.add("WARNING: Problem parsing effected start stop positions from this vcf, excluding from search -> "+vcfLine);
				lg.debug(warningTxtForUser);
			}
		}
		return null;
	}
	
	/**Returns the interbase start stop region of effected bps for simple SNV and INDELs. 
	 * SNV=iPos,iPos+LenRef; INS=iPos,iPos+lenRef+1; DEL=iPos+1,iPos+lenRef; iPos=pos-1.
	 * For multi alts, returns min begin and max end of all combinations.
	 * For alts with < indicating a CNV or trans, attempts to parse the END=number from the INFO column. */
	public int[] fetchEffectedBps(String[] vcf) throws NumberFormatException{
		
		/*NOTE, any changes here, please update the USeq QueryIndexer app too*/
		
		//CHROM	POS	ID	REF	ALT	
		//  0    1   2   3   4  
		//put into interbase coordinates
		int iPos = Integer.parseInt(vcf[1]) - 1;
		String ref= vcf[3];
		String alt= vcf[4];

		//any commas/ multi alts?
		if (alt.contains(",") == false) return fetchEffectedSingleAlt(iPos, ref, alt, vcf, true);

		//OK commas present, thus multi alts, these need to be tested for max effect
		//There is complexity with multi alts, best to deconvolute and left justify! 
		String[] alts = Util.COMMA.split(alt);
		int begin = Integer.MAX_VALUE;
		int end = -1;
		for (int i=0; i< alts.length; i++){
			int[] ss = fetchEffectedSingleAlt(iPos, ref, alts[i], vcf, false);
			//skip it?
			if (ss == null) continue;
			if(ss[0] < begin) begin = ss[0];
			if(ss[1]> end) end = ss[1];
		}
		if (begin == Integer.MAX_VALUE) {
			String em = "ERROR: Failed to parse an END=number position, skipping if this is the only alt -> "+Util.stringArrayToString(vcf, "\t");
			lg.debug(em);
			warningTxtForUser.add(em);
			return null;
		}
		return new int[]{begin, end};
	}

	public int[] fetchEffectedSingleAlt(int iPos, String ref, String alt, String[] vcf, boolean singleAlt) throws NumberFormatException{
		
		/*NOTE, any changes here, please update the USeq QueryIndexer app too*/
		
		int begin = -1;
		int end = -1;
		int lenRef = ref.length();
		int lenAlt = alt.length();

		//watch out for < in the alt indicative of a CNV, structural var, or gvcf block
		if (alt.contains("<")){
			//CHROM	POS	ID	REF	ALT QUAL FILTER INFO	
			//  0    1   2   3   4   5      6     7
			Matcher mat = END_POSITION.matcher(vcf[7]);
			if (mat.matches()) end = Integer.parseInt(mat.group(1));
			else {
				//only warn if there are no other alts
				if (singleAlt){
					String em = "ERROR: found a < or . containing alt yet failed to parse an END=number position, skipping if this is the only alt -> "+Util.stringArrayToString(vcf, "\t");
					lg.debug(em);
					warningTxtForUser.add(em);
				}
				return null;
			}
			begin = iPos;
		}

		//single or multi adjacent snp? return just the changed bps,  GC->AT or G->A or G->.
		else if (lenRef == lenAlt) {
			begin = iPos;
			end = iPos+ lenRef;
		}
		//ins? return the bases on either side of the insertion GC->GATTA or G->ATTA
		else if (lenAlt > lenRef) {
			begin = iPos;
			end = iPos+ lenRef +1;
			if (ref.charAt(0) != alt.charAt(0)) {
				String em = "ERROR: Odd INS vcf record, the first base in the ref and alt should be the same, use vt to normalize your variants, skipping -> "+Util.stringArrayToString(vcf, "\t");
				lg.debug(em);
				warningTxtForUser.add(em);
				return null;
			}

		}
		//del? return the uneffected bp and those that are deleted to match tabix's behaviour AT->A, ATTCG->ACC
		else if (lenRef > lenAlt) {
			begin = iPos;
			end = iPos + lenRef;
			if (ref.charAt(0) != alt.charAt(0)) {
				String em = "ERROR: Odd DEL vcf record, the first base in the ref and alt should be the same, use vt to normalize your variants, skipping -> "+Util.stringArrayToString(vcf, "\t");
				lg.debug(em);
				warningTxtForUser.add(em);
				return null;
			}
		}
		//odd, shouldn't hit this
		else {
			String em = "ERROR: Contact admin! Odd vcf record, can't parse effected bps, skipping -> "+Util.stringArrayToString(vcf, "\t");
			lg.debug(em);
			warningTxtForUser.add(em);
			return null;
		}

		return new int[]{begin, end};
	}

	
	public HashMap<String, TabixDataQuery[]> convertBedAL2TabixQuerys(ArrayList<SimpleBed> bedAl, boolean parseVcf){
		HashMap<String, TabixDataQuery[]> tqs = new HashMap<String, TabixDataQuery[]>();
		SimpleBed[] bed = new SimpleBed[bedAl.size()];
		bedAl.toArray(bed);
		Arrays.sort(bed);	
		HashMap<String, SimpleBed[]> bedHash = SimpleBed.splitBedByChrom(bed);
		for (String chr: bedHash.keySet()){
			SimpleBed[] b = bedHash.get(chr);		
			TabixDataQuery[] tq = new TabixDataQuery[b.length];
			for (int i=0; i< b.length; i++) {
				tq[i] = new TabixDataQuery(b[i], dataSources.getDataFileDisplayName());
				if (parseVcf) tq[i].parseVcf();
			}
			tqs.put(chr, tq);
		}
		return tqs;
	}

	public JSONObject getJsonResults() {
		JSONObject jo = new JSONObject();
		jo.put("querySettings", queryFilter.getCurrentSettings(inputFile));
		if (indexQueryStats != null) jo.put("indexQueryStats", indexQueryStats);
		if (indexFileFilteringStats != null) jo.put("indexFileFilteringStats", indexFileFilteringStats);
		if (dataRetrievalStats != null) jo.put("dataRetrievalStats", dataRetrievalStats);
		if (chrTabixQueries != null) appendQueryResults(jo);
		if (queryFilter.includeHeaders() && fileTabixQueries.size()!=0) jo.put("fileHeaders", getFileHeaders());
		if (warningTxtForUser.size() !=0) jo.put("warningMessages", warningTxtForUser);
		return jo;
	}
	
	private ArrayList<JSONObject> getFileHeaders() {
		try {
		ArrayList<JSONObject> al = new ArrayList<JSONObject>();
		for (File f: fileTabixQueries.keySet()){
			JSONObject jo = dataSources.fetchFileHeader(f);
			al.add(jo);
		}
		return al;
		} catch (Exception e) {
			errTxtForUser= "Problem fetching file headers for -> "+ fileTabixQueries.keySet()+" contact admin! ";
			lg.error(errTxtForUser+"\n"+Util.getStackTrace(e));
		}
		return null;
	}

	private void appendQueryResults(JSONObject jo){
		//do they want to return vcf hit data, sometimes no, just the numberOfHits
		boolean returnData =true;
		if (queryFilter.isExcludeDataFromResults()) returnData = false;
		
		//for each chromosome of user queries
		for (String chr: chrTabixQueries.keySet()){
			TabixDataQuery[] tqs = chrTabixQueries.get(chr);
			//for each query that has a hit
			for (int i=0; i< tqs.length; i++){
				if (tqs[i].getSourceResults().size() !=0) jo.append("queryResults", tqs[i].getResults(returnData));
			}	
		}
	}

	/**Returns the next chunk or null.*/
	public TabixDataChunk getChunk(){
		if (chunkIterator.hasNext()) return chunkIterator.next();
		return null;
	}

	private void makeTabixChunks() {
		int numberQueriesInChunk = query.getNumberQueriesInChunk();

		//walk through files
		for (File f: fileTabixQueries.keySet()){
			ArrayList<TabixDataQuery> al = fileTabixQueries.get(f);
			int numTQs = al.size();			

			if (numTQs <= numberQueriesInChunk) tabixChunks.add( new TabixDataChunk(f, al, this));
			else {
				//walk it
				ArrayList<TabixDataQuery> tqSub = new ArrayList<TabixDataQuery>();
				for (TabixDataQuery tq : al){
					tqSub.add(tq);
					//hit max?
					if (tqSub.size() == numberQueriesInChunk) {
						tabixChunks.add(new TabixDataChunk(f, tqSub, this));
						tqSub = new ArrayList<TabixDataQuery>();
					}
				}
				//add last?
				if (tqSub.size() !=0) tabixChunks.add(new TabixDataChunk(f, tqSub, this));
			}
		}

		//create iterator for Loaders to pull from
		chunkIterator = tabixChunks.iterator();

		lg.debug(tabixChunks.size()+"\tQuery chunks created");
	}
	
	public synchronized void incrementNumRetrievedResults(long n){
		numberRetrievedResults += n;
	}
	public synchronized void incrementNumQueriesWithResults(int n){
		numberQueriesWithResults += n;
	}
	public synchronized void incrementNumSavedResults(long n){
		numberSavedResults += n;
	}
	public QueryFilter getFilter() {
		return queryFilter;
	}
	public Query getQuery() {
		return query;
	}
	public ArrayList<TabixDataChunk> getTabixChunks() {
		return tabixChunks;
	}
	public Iterator<TabixDataChunk> getChunkIterator() {
		return chunkIterator;
	}
	public HashMap<String, TabixDataQuery[]> getChrTabixQueries() {
		return chrTabixQueries;
	}
	public HashMap<File, ArrayList<TabixDataQuery>> getFileTabixQueries() {
		return fileTabixQueries;
	}
	public long getNumberRetrievedResults() {
		return numberRetrievedResults;
	}
	public int getNumberQueriesWithResults() {
		return numberQueriesWithResults;
	}
	public long getNumberSavedResults() {
		return numberSavedResults;
	}
	public void setTime2Load(long l) {
		time2Load = l;
	}
	public String getErrTxtForUser() {
		return errTxtForUser;
	}
	public ArrayList<String> getWarningTxtForUser() {
		return warningTxtForUser;
	}

}
