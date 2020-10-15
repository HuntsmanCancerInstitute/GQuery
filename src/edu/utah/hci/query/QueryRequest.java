package edu.utah.hci.query;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import edu.utah.hci.it.SimpleBed;
import edu.utah.hci.misc.Util;
import edu.utah.hci.tabix.TabixDataLineLoader;
import edu.utah.hci.tabix.TabixDataQuery;
import edu.utah.hci.tabix.TabixFileRegionIntersector;;

public class QueryRequest {

	//fields
	private QueryFilter queryFilter; 
	private MasterQuery masterQuery;
	private File inputFile = null; //might be null if using GET request
	private String[] bedRegions = null;
	private String[] vcfRegions = null;
	private int bpPadding = 0;
	public static final Pattern BED_COLON_DASH_PATTERN = Pattern.compile("(\\w+):([\\d\\,]+)-([\\d\\,]+)");
	public static final Pattern END_POSITION = Pattern.compile(".*END=(\\d+).*", Pattern.CASE_INSENSITIVE);
	private static final Logger lg = LogManager.getLogger(QueryRequest.class);
	
	//from run
	/*If not null then the query failed.*/
	private String errTxtForUser = null;
	/*Container of warning messages to set back to the user.*/
	private ArrayList<String> warningTxtForUser = new ArrayList<String>();
	/*Json objects with info elated to the query*/
	private JSONObject queryOptions;
	private JSONObject indexQueryStats = null;
	private JSONObject indexFileFilteringStats = null;
	private JSONObject dataRetrievalStats = null;

	/*Contains all the user region TabixQueries split by chromosome. Will be loaded with results.*/
	private HashMap<String, TabixDataQuery[]> chrTabixQueries = new HashMap<String, TabixDataQuery[]>();
	
	/*Contains a File pointer to a data source and the associated TabixQueries that intersect it.
	 * These are to be used in a tabix search.*/
	private HashMap<File, ArrayList<TabixDataQuery>> fileTabixQueries = new HashMap<File, ArrayList<TabixDataQuery>>();

	
	/**REST constructor.
	 * @param user */
	public QueryRequest(MasterQuery masterQuery, File inputFile, HashMap<String, String> options, User user) throws IOException {
		this.masterQuery = masterQuery;
		this.inputFile = inputFile;
		
		//create a new file filter and modify it with the input, this parses the user options including the bed/ vcf regions to search
		queryFilter = new QueryFilter(user, this, options);
		errTxtForUser = queryFilter.getErrTxtForUser();
		if (errTxtForUser != null) return;
		
		//do they want the queryOptions?
		if (queryFilter.isFetchOptions() == true) {
			queryOptions = getQueryOptions(queryFilter, masterQuery.getDataDir());
			return;
		}
		
		//set bpPadding
		bpPadding = queryFilter.getBpPadding();
		
		//load any bed/vcf file if provided and convert them to tabix chrom index queries
		if (createChrTabixQuery(inputFile) == false) return;
		
		//which query indexes do they and can they actually see
		ArrayList<SingleQuery> toSearch = queryFilter.fetchQueriesToSearch();
		if (toSearch == null) {
			warningTxtForUser.add("No indexes were found to match your directory path regex. Nothing to search!");
			return;
		}
		
		TabixFileRegionIntersector mtfri = new TabixFileRegionIntersector(this, toSearch);
		indexQueryStats = mtfri.getIndexQueryStats();
		
		//filter which files to fetch data, this has already been User regEx filtered
		indexFileFilteringStats = queryFilter.filterFiles(fileTabixQueries);
		
		//any left?
		if (fileTabixQueries.size()!=0){
			
			//do they want to fetch the underlying data (slow)? 
			if (queryFilter.isFetchData()) {
				
				TabixDataLineLoader mtdll = new TabixDataLineLoader(this);
				
				//make json stats object
				makeDataRetrievalStats(mtdll);
				
				
				//log the results
				//lg.debug("Data retrieval stats:");
				//lg.debug(dataRetrievalStats.toString(5));
			}
			//just indicate which file hit which region (fast)
			else loadTabixQueriesWithFileSources();
		}
		else lg.debug("No intersecting files after filtering");

	}

	public void makeDataRetrievalStats(TabixDataLineLoader mtdll){
		dataRetrievalStats = new JSONObject();
		dataRetrievalStats.put("millSecToLoadRecords", mtdll.getMsTimeToComplete());
		dataRetrievalStats.put("numberDataLookupJobs", mtdll.getNumberLookupJobs());
		dataRetrievalStats.put("numberQueries", mtdll.getNumberQueries());
		dataRetrievalStats.put("numberQueriesWithData", mtdll.getNumberQueriesWithData());
		dataRetrievalStats.put("numberQueriesWithDataThatPassDataLineRegEx", mtdll.getNumberQueriesWithDataThatPassRegEx());
	}
	


	/**This takes a bed file, parses each region into a TabixQuery object and identifies which file data sources contain data that intersects it.*/
	public boolean createChrTabixQuery(File inputFile) throws IOException {

		//is the input present, thus POST request?
		if (inputFile != null){
			String name = inputFile.getName();
			if (name.endsWith(".bed.gz") || name.endsWith(".bed")){
				
				//load file into String[]
				loadBedRegionsAsStringArray(inputFile);
				
				//convert them to TabixQuery 
				chrTabixQueries = parseBedRegions();
			}
			else if (name.endsWith(".vcf.gz") || name.endsWith(".vcf"))chrTabixQueries = convertVcfToTabixQuery(inputFile);  
			else {
				errTxtForUser = "Input file must be either bed or vcf (.gz/.zip OK) format";
				lg.debug(errTxtForUser);
				return false;
			}
		}
		
		//nope, GET request with already loaded regions
		else {
			if (vcfRegions != null) chrTabixQueries = parseVcfRegions();
			else if (bedRegions != null) chrTabixQueries = parseBedRegions();
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
		else if (masterQuery.getAvailableIndexedChromosomes().contains(t[0]) == false){
			String message = "WARNING: Failed to find a chromosome for '"+t[0]+ "' excluding from search -> "+bedLine;
			warningTxtForUser.add(message);
			lg.debug(message);
		}
		
		//parse it!
		else {
			//remove any potential commas
			t[1] = Util.COMMA.matcher(t[1]).replaceAll("");
			t[2] = Util.COMMA.matcher(t[2]).replaceAll("");
			int start = Integer.parseInt(t[1])- bpPadding;
			int stop = Integer.parseInt(t[2])+ bpPadding;
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
		else if (masterQuery.getAvailableIndexedChromosomes().contains(t[0]) == false){
			warningTxtForUser.add("WARNING: Failed to find a chromosome for '"+t[0]+ "' excluding from search -> "+vcfLine);
			lg.debug(warningTxtForUser);
		}
		
		//parse it!
		else {
			//fetch start and stop for effected bps.
			int[] startStop = fetchEffectedBps(t);
			if (startStop != null) {
				startStop[0] = startStop[0]- bpPadding;
				startStop[1] = startStop[1]+ bpPadding;
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
				tq[i] = new TabixDataQuery(b[i]);
				if (parseVcf) tq[i].parseVcf();
			}
			tqs.put(chr, tq);
		}
		return tqs;
	}

	public JSONObject getJsonResults() {
		JSONObject jo = new JSONObject();
		jo.put("querySettings", queryFilter.getCurrentSettings(inputFile));
		if (queryOptions != null) jo.put("queryOptions", queryOptions);
		else {
			if (indexQueryStats != null) jo.put("fileIndexQueryStats", indexQueryStats);
			if (indexFileFilteringStats != null) jo.put("fileIndexFilteringStats", indexFileFilteringStats);
			if (dataRetrievalStats != null) jo.put("dataRetrievalStats", dataRetrievalStats);
			if (chrTabixQueries != null) appendQueryResults(jo);
			if (queryFilter.includeHeaders() && fileTabixQueries.size()!=0) jo.put("dataHitFileHeaders", getFileHeaders());
		}
		if (warningTxtForUser.size() !=0) jo.put("warningMessages", warningTxtForUser);
		return jo;
	}
	
	private ArrayList<JSONObject> getFileHeaders() {
		try {
			ArrayList<JSONObject> al = new ArrayList<JSONObject>();
			for (File f: fileTabixQueries.keySet()) al.add(masterQuery.fetchFileHeader(f));
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
				if (tqs[i].getSourceResults().size() !=0) jo.append("queryResults", tqs[i].getResults(returnData, masterQuery.getNumCharToSkipForDataDir()));
			}	
		}
	}
	
	/**Set inputs to null to just get options without the data sources.*/
	public static JSONObject getQueryOptions(QueryFilter mqf, File rootDataDir) {
		ArrayList<JSONObject> al = new ArrayList<JSONObject>();
		ArrayList<Boolean> tf = new ArrayList<Boolean>();
		tf.add(true);
		tf.add(false);
		
		//fetchOptions
		JSONObject fo = new JSONObject();
		fo.put("name", "fetchOptions");
		fo.put("description", "Returns this list of options and filters, see https://github.com/HuntsmanCancerInstitute/Query for examples.");
		al.add(fo);
		
		//bed
		JSONObject bed = new JSONObject();
		bed.put("name", "bed");
		bed.put("description", "Region(s) of interest to query in bed region format, https://genome.ucsc.edu/FAQ/FAQformat.html#format1, semicolon delimited. Commas and prepended 'chr' are ignored. ");
		ArrayList<String> bedEx = new ArrayList<String>();
		bedEx.add("chr21:145569-145594"); 
		bedEx.add("21:145,569-145,594"); 
		bedEx.add("21:145569-145594;22:4965784-4965881;X:8594-8599");
		bedEx.add("21\t11058198\t11058237\tMYCInt\t4.3\t-");
		bed.put("examples", bedEx);
		al.add(bed);
		
		//vcf
		JSONObject vcf = new JSONObject();
		vcf.put("name", "vcf");
		vcf.put("description", "Region(s) of interest to query in vcf format, http://samtools.github.io/hts-specs/VCFv4.3.pdf, semicolon delimited. Prepended 'chr' are ignored. Watch out "
				+ "for semicolons in the vcf INFO and FORMAT fields.  Replace the INFO column with a '.' and delete the remainder, otherwise semi-colons will break the input parser.");
		ArrayList<String> vcfEx = new ArrayList<String>();
		vcfEx.add("chr20\t4162847\t.\tC\tT\t.\tPASS\t."); 
		vcfEx.add("20\t4163144\t.\tC\tA\t.\t.\t.;20\t4228734\t.\tC\tCCAAG\t.\tPASS\tAF=0.5"); 
		vcf.put("examples", vcfEx);
		al.add(vcf);

		//fetchData
		JSONObject fd = new JSONObject();
		fd.put("name", "fetchData");
		fd.put("description", "Pull records from disk (slow). First develop and appropriate restrictive regEx filter set, then fetchData.");
		ArrayList<String> tff = new ArrayList<String>();
		tff.add("true"); tff.add("false");
		fd.put("options", tff);
		fd.put("defaultOption", false);
		al.add(fd);
		
		//bpPadding
		JSONObject pad = new JSONObject();
		pad.put("name", "bpPadding");
		pad.put("description", "Pad each vcf or bed region +/- bpPadding value.");
		pad.put("defaultOption", 0);
		al.add(pad);

		//matchVcf
		JSONObject mv = new JSONObject();
		mv.put("name", "matchVcf");
		mv.put("description", "For vcf input queries, require that vcf records match chr, pos, ref, and at least one alt. "
				+ "Will set 'fetchData' = true. Be sure to vt normalize and decompose_blocksub your vcf input, see https://github.com/atks/vt.");
		mv.put("options", tf);
		mv.put("defaultOption", false);
		al.add(mv);

		//includeHeaders
		JSONObject ih = new JSONObject();
		ih.put("name", "includeHeaders");
		ih.put("description", "Return the file headers associated with the intersecting datasets.");
		ih.put("options", tf);
		ih.put("defaultOption", false);
		al.add(ih);
		
		//regExDirPath
		JSONObject rx = new JSONObject();
		rx.put("name", "regExDirPath");
		rx.put("description", "Require records to belong to a file whose file path matches these java regular expressions, "
				+ "semicolon delimited. Note, a .* is added to both ends of each regEx.");
		ArrayList<String> rxEx = new ArrayList<String>();
		rxEx.add("/B37/"); 
		rxEx.add("\\.vcf\\.gz"); 
		rx.put("examples", rxEx);
		al.add(rx);
		
		//regExFileName
		JSONObject rxo = new JSONObject();
		rxo.put("name", "regExFileName");
		rxo.put("description", "Require records to belong to a file whose name matches these java regular expressions, "
				+ "semicolon delimited. Note, a .* is added to both ends of each regEx.");
		ArrayList<String> rxoEx = new ArrayList<String>();
		rxoEx.add("\\.vcf\\.gz;\\.maf\\.txt\\.gz");  
		rxo.put("examples", rxoEx);
		al.add(rxo);
		
		//regExDataLine
		JSONObject rxd = new JSONObject();
		rxd.put("name", "regExDataLine");
		rxd.put("description", "Require each record data line to match these java regular expressions, "
				+ "semicolon delimited. Note, a .* is added to both ends of each regEx. Will set 'fetchData' = true. Case insensitive.");
		ArrayList<String> rxExD = new ArrayList<String>();
		rxExD.add("Pathogenic"); 
		rxExD.add("LOF"); 
		rxd.put("examples", rxExD);
		al.add(rxd);
		
		//matchAllDirPathRegEx
		JSONObject madpr = new JSONObject();
		madpr.put("name", "matchAllDirPathRegEx");
		madpr.put("description", "Require that all regExDirPath expressions match.");
		madpr.put("options", tf);
		madpr.put("defaultOption", false);
		al.add(madpr);
		
		//matchAllFileNameRegEx
		JSONObject mafnr = new JSONObject();
		mafnr.put("name", "matchAllFileNameRegEx");
		mafnr.put("description", "Require that all regExFileName expressions match.");
		mafnr.put("options", tf);
		mafnr.put("defaultOption", false);
		al.add(mafnr);
		
		//matchAllDataLineRegEx
		JSONObject madlr = new JSONObject();
		madlr.put("name", "matchAllDataLineRegEx");
		madlr.put("description", "Require that all regExDataLine expressions match.");
		madlr.put("options", tf);
		madlr.put("defaultOption", false);
		al.add(madlr);

		//data sources, only return when a user was built
		if (mqf != null && mqf.getRegExDirPathUser() != null) {
			ArrayList<String> fs = fetchDataSources (mqf.getTruncFilePathsUserCanSee(), rootDataDir);
		
			//dataSources for lookup and retrieval
			JSONObject ds = new JSONObject();
			ds.put("name", "dataSources");
			ds.put("userName", mqf.getUserName());
			ds.put("userDirPathRegEx", mqf.getRegExDirPathUser());
			ds.put("description", "Data sources available for searching by the user. Design regExDirPath and regExFileName expressions to match particular sets of these.");
			ds.put("searchableFiles", fs);
			al.add(ds);
		}
		
		JSONObject queryOptions = new JSONObject();
		queryOptions.put("queryOptionsAndFilters", al);
	
		return queryOptions;
}

	/**Filters the dataSources based on the Users permitted regExOne patterns.*/
	private static ArrayList<String> fetchDataSources(ArrayList<String> trucFilePathsUserCanSee, File rootDataDir) {
		ArrayList<String> filePaths = new ArrayList<String>();
			File parent = rootDataDir.getParentFile();
			//for each DataSource
			for (String ds: trucFilePathsUserCanSee) {
				File dir = new File (parent, ds);			
				ArrayList<File> files = Util.fetchFiles(dir, ".gz");
				for (File f: files) {
					
					filePaths.add(ds+f.getName());
				}
				
			}
		return filePaths;
	}


	
	public HashMap<String, TabixDataQuery[]> getChrTabixQueries() {
		return chrTabixQueries;
	}
	public HashMap<File, ArrayList<TabixDataQuery>> getFileTabixQueries() {
		return fileTabixQueries;
	}
	public String getErrTxtForUser() {
		return errTxtForUser;
	}
	public ArrayList<String> getWarningTxtForUser() {
		return warningTxtForUser;
	}

	public QueryFilter getQueryFilter() {
		return queryFilter;
	}

	public MasterQuery getMasterQuery() {
		return masterQuery;
	}

	public String[] getBedRegions() {
		return bedRegions;
	}

	public void setBedRegions(String[] bedRegions) {
		this.bedRegions = bedRegions;
	}

	public String[] getVcfRegions() {
		return vcfRegions;
	}

	public void setVcfRegions(String[] vcfRegions) {
		this.vcfRegions = vcfRegions;
	}

}
