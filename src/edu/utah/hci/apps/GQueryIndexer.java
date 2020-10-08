package edu.utah.hci.apps;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.utah.hci.indexer.IndexRegion;
import edu.utah.hci.indexer.QueryIndexFileLoader;
import edu.utah.hci.it.SimpleBed;
import edu.utah.hci.misc.Gzipper;
import edu.utah.hci.misc.Util;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.tribble.index.tabix.TabixIndex;

public class GQueryIndexer {

	//user params
	private File dataDir;
	private File chrNameLength;
	private boolean verbose = true;
	private File bgzip = null;
	private File tabix = null;
	private int numberThreads = 0;

	//internal fields
	private String email = "bioinformaticscore@utah.edu";
	private String[] fileExtToIndex = {"vcf.gz", "bed.gz", "bedgraph.gz", "maf.txt.gz"};
	private static HashMap<String, int[]> extensionsStartStopSub = new HashMap<String, int[]>();
	public static final String INDEX_DIR_NAME = ".GQueryIndex";
	private TreeMap<String, Integer> chrLengths = null;
	private long totalRecordsProcessed = 0;
	private int totalFilesIndexed = 0;
	private ArrayList<File> dirsWithTbis = null;
	private int bpBlock = 250000000; //max size hg19 chr1 is 249,250,621
	
	//working obj per index dir
	private File[] workingDataFilesToParse= null;
	private HashMap<String, ArrayList<File>> workingChrFiles = new HashMap<String, ArrayList<File>>();
	private HashMap<File, Integer> workingFileId = new HashMap<File, Integer>();
	private Integer[] workingIds = null;
	private int toTruncatePoint = -1;
	private File workingIndexDir = null;
	private HashMap<String, FileInfo> workingPriorFileInfo = null;

	//per chr fields
	private ArrayList<IndexRegion>[] workingIndex = null; 
	private ArrayList<File> workingFilesToParse = new ArrayList<File>();
	private ArrayList<File> workingBedFilesToMerge = new ArrayList<File>();
	private int workingFilesToParseIndex = 0;
	private String workingChr = null;
	private int workingStartBp;
	private int workingStopBp;
	private long workingParsed = 0;
	private PrintWriter out = null;

	//constructor
	public GQueryIndexer(String[] args) {
		long startTime = System.currentTimeMillis();

		processArgs(args);
		parseDataSourceDirs();
		setKnownFileTypeSSS();
		parseChromLengthFile();
		
		//for each dir containing gz.tbi files
		for (File dir: dirsWithTbis) {
			Util.pl("\tIndexing "+dir);
			
			//load the workingDataFilesToParse
			parseDataSources(dir);
			
			//look for and if present load the prior index HashMaps
			loadPrior(dir);
			boolean buildIndex = true;
			
			if (workingPriorFileInfo != null) buildIndex = contrastPriorWithCurrent();
			else createFileIdHash();
			
			if (buildIndex) {
				//clear old and create new index dir
				Util.deleteDirectory(workingIndexDir);
				workingIndexDir.mkdir();
				
				createChrFiles();

				createFileIdArray();
				
				//for each chromosome
				for (String chr: chrLengths.keySet()) {
					workingChr = chr;
					parseChr();
				}

				bgzipAndTabixIndex();
				saveFileIds();
			}
			else Util.pl("\t\t\tUp to date");
		}


		String diffTime = Util.formatNumberOneFraction(((double)(System.currentTimeMillis() -startTime))/1000/60);
		String numParsed = NumberFormat.getNumberInstance(Locale.US).format(totalRecordsProcessed);
		String numFiles = NumberFormat.getNumberInstance(Locale.US).format(totalFilesIndexed);
		Util.pl("\n"+ diffTime+" Min to index "+numFiles+" files containing "+ numParsed +" records");
	}

	private void createFileIdArray() {
		int maxValue = 0;
		for (int i: workingFileId.values()) if (i>maxValue) maxValue = i;
		workingIds = new Integer[maxValue+1];
		for (Integer i: workingFileId.values()) workingIds[i] = i;
	}

	private void createFileIdHash() {
		workingFileId.clear();
		for (int i=0; i< workingDataFilesToParse.length; i++) {
			Integer id = new Integer(i);
			workingFileId.put(workingDataFilesToParse[i], id);
		}
	}
	
	private boolean contrastPriorWithCurrent() {
		workingFileId.clear();
		int numOldDataSources = 0;
		int numNewDataSources = 0;
		int numNewDataSourcesDiffSize = 0;
		int numOldDataSourcesMissingInNew = 0;

		//walk working files to index
		HashSet<String> currentTrimmedDataSourceNames = new HashSet<String>();
		for (int i=0; i< workingDataFilesToParse.length; i++) {
			
			//add to fileId hash
			Integer id = new Integer(i);
			workingFileId.put(workingDataFilesToParse[i], id);
			
			//was it already parsed?
			String trimmedName = workingDataFilesToParse[i].toString().substring(toTruncatePoint);
			currentTrimmedDataSourceNames.add(trimmedName);
			FileInfo fi =  workingPriorFileInfo.get(trimmedName);
			

			if (fi == null) numNewDataSources++;
			else {
				//same size and mod date?
				if (workingDataFilesToParse[i].length() == fi.size && workingDataFilesToParse[i].lastModified() == fi.lastModified) numOldDataSources++;
				else numNewDataSourcesDiffSize++;
			}
		}
		
		//walk old Index and see which are missing in new and should be deleted
		for (String oldTN: workingPriorFileInfo.keySet()) {
			if (currentTrimmedDataSourceNames.contains(oldTN) == false) numOldDataSourcesMissingInNew++;
		}
		
		/*
		if (verbose) {
			//old datasets already parsed and in index
			Util.pl("\t\t\t"+numOldDataSources +" Datasets present in index");
			//entirely new datasets - to parse
			Util.pl("\t\t\t"+numNewDataSources +" New datasets to add to index");
			//new datasets with preexisting same named file but diff size or mod date
			Util.pl("\t\t\t"+numNewDataSourcesDiffSize +" New datasets to replace an existing data source");
			//old datasets to delete, either getting replace or were deleted from current datasource list
			Util.pl("\t\t\t"+numOldDataSourcesMissingInNew+" Datasets missing from index ");
		}*/
		
		//check if any work to do
		if (numNewDataSources==0 && numNewDataSourcesDiffSize==0 && numOldDataSourcesMissingInNew==0) return false;	
		return true;
	}

	@SuppressWarnings("unchecked")
	private void loadPrior(File dir) {
		try {
			workingPriorFileInfo = null;
			workingIndexDir = new File(dir, INDEX_DIR_NAME);
			if (workingIndexDir.exists() == false) return;
			
			//look for the fileInfo.txt 
			File info = new File(workingIndexDir, "fileInfo.txt.gz");
			if (info.exists() == false) return;
			loadFileInfo(info);
			
			File[] priorChromIndexFiles = Util.extractFiles(workingIndexDir, ".bed.gz");
			if (priorChromIndexFiles == null || priorChromIndexFiles.length == 0) throw new IOException("\nFailed to find your chrXXX.bed.gz index files in this index directory "+workingIndexDir+" Delete index dir and restart.");

		} catch (Exception e){
			e.printStackTrace();
			Util.printErrAndExit("\nERROR: opening prior index objects from "+dir);
		}
		
	}

	private void loadFileInfo(File info) throws IOException {
		workingPriorFileInfo = new HashMap<String, FileInfo>();
		BufferedReader in = Util.fetchBufferedReader(info);
		//skip first header line
		in.readLine();
		String line;
		String[] fields;
		while ((line = in.readLine()) != null) {
			//Id0 Size1 LastMod2 Name3
			fields = Util.TAB.split(line);
			workingPriorFileInfo.put(fields[3], new FileInfo(fields));
		}
		in.close();
	}
	
	private class FileInfo{
		int id;
		long size;
		long lastModified;
		
		private FileInfo (String[] fields) {
			//Id0 Size1 LastMod2 Name3
			id = Integer.parseInt(fields[0]);
			size = Long.parseLong(fields[1]);
			lastModified = Long.parseLong(fields[2]);
		}
	}

	public void parseChr(){
		try {
			workingFilesToParse.clear();
			workingFilesToParse.addAll(workingChrFiles.get(workingChr));
			workingBedFilesToMerge.clear();
			
			//any work to do?
			if (workingFilesToParse.size() == 0 ) return;
			
			//start io
			File queryIndexFile = new File(workingIndexDir, workingChr+".qi.bed");
			out = new PrintWriter(new FileWriter((queryIndexFile)));
			
			//for each block
			int chromLength = chrLengths.get(workingChr)+2;
			for (int i=0; i<chromLength; i+=bpBlock) {
				workingStartBp = i;
				workingStopBp = workingStartBp+bpBlock;
				if (workingStopBp > chromLength) workingStopBp = chromLength;
				Util.p("\t\t\t"+workingChr+":"+workingStartBp+"-"+workingStopBp);
				
				//prep for new chr seg
				workingIndex = new ArrayList[workingStopBp-workingStartBp+1];
				workingParsed = 0;
				parseChrBlock();
			}

			out.close();

		} catch (IOException e){
			e.printStackTrace();
			Util.printErrAndExit("\nFATAL error with loading "+workingChr+", aborting.");
		}
	}


	private void parseChrBlock() throws IOException {
		
		//load new datasets?
		if (workingFilesToParse.size() !=0) {
			workingFilesToParseIndex = 0;
			//try to make a loader for each file
			int numToMake= workingFilesToParse.size();
			if (numToMake > numberThreads) numToMake = numberThreads;			
			QueryIndexFileLoader[] loader = new QueryIndexFileLoader[numToMake];			
			ExecutorService executor = Executors.newFixedThreadPool(numToMake);
			for (int i=0; i< loader.length; i++){
				loader[i] = new QueryIndexFileLoader(this, workingChr, workingStartBp, workingStopBp);
				executor.execute(loader[i]);
			}
			executor.shutdown();

			//spins here until the executer is terminated, e.g. all threads complete
			while (!executor.isTerminated()) {}

			//check loaders 
			for (QueryIndexFileLoader c: loader) {
				if (c.isFailed()) throw new IOException("ERROR: File Loader issue! \n"+c);
			}
		}
		

		//save the index for this chrom block
		if (workingParsed !=0) saveWorkingChrBlock();

		//stats
		Util.pl("\t"+workingParsed);
		totalRecordsProcessed+= workingParsed;
	}
	
	public synchronized void addRegions (ArrayList<IndexRegion> regions) {
		for (IndexRegion region: regions){
			//add start
			int sIndex = region.start;
			if (workingIndex[sIndex] == null) workingIndex[sIndex] = new ArrayList<IndexRegion>();
			workingIndex[sIndex].add(region);

			//add stop 
			sIndex = region.stop;
			if (workingIndex[sIndex] == null) workingIndex[sIndex] = new ArrayList<IndexRegion>();
			workingIndex[sIndex].add(region);
		}
		workingParsed+= regions.size();
		regions.clear();
	}

	/**Gets a file to parse containing records that match a particular chr. Thread safe.*/
	public synchronized File getFileToParse(){
		if (workingFilesToParseIndex < workingFilesToParse.size()) return workingFilesToParse.get(workingFilesToParseIndex++);
		return null;
	}

	private void createChrFiles() {
		//load hash to hold files with a particular chr
		workingChrFiles.clear();
		for (String chr: chrLengths.keySet()) workingChrFiles.put(chr, new ArrayList<File>());

		try {
			//for each file
			for (File f: workingDataFilesToParse){
				//make an index
				File i = new File (f+".tbi");
				TabixIndex ti = new TabixIndex(i);
				//for each chromosome
				for (String chr: chrLengths.keySet()){
					if (ti.containsChromosome(chr) || ti.containsChromosome("chr"+chr)) workingChrFiles.get(chr).add(f);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			Util.printErrAndExit("\nERROR: problem with testing indexes for particular chroms\n");
		}
	}
	
	public void saveWorkingChrBlock(){

		HashSet<IndexRegion> openRegions = new HashSet<IndexRegion>();
		int startPos = -1;
		ArrayList<RegionToPrint> toSave = new ArrayList<RegionToPrint>();

		for (int i=0; i< workingIndex.length; i++){
			if (workingIndex[i]== null) continue;

			//first in block?
			if (openRegions.size() == 0){
				openRegions.addAll(workingIndex[i]);
				startPos = i;
				if (toSave.size() != 0) saveRegions(toSave, out);
			}

			//must be adding a new so need to save old
			else {
				toSave.add(new RegionToPrint(startPos, i, fetchIds(openRegions)));
				startPos = i;

				//go through each of the current index regions
				for (IndexRegion ir: workingIndex[i]){
					//already in open then this must be an end so remove it
					if (openRegions.contains(ir)) openRegions.remove(ir);
					//not present so this is a beginning, add it
					else openRegions.add(ir);
				}
			}
		}
		//save last?
		if (toSave.size() != 0) saveRegions(toSave, out);
	}
	
	private void saveRegions(ArrayList<RegionToPrint> al, PrintWriter out){
		//set first
		RegionToPrint rtp = al.get(0);
		
		//walk looking for same ids in following regions and merge em
		int num = al.size();
		for (int i=1; i< num; i++){
			RegionToPrint next = al.get(i);
			//same file id's
			if (next.ids.equals(rtp.ids)) rtp.stop = next.stop;
			else {
				StringBuilder sb = new StringBuilder(workingChr);
				rtp.appendInfo(sb, workingStartBp);
				out.println(sb);
				rtp = next;
			}
		}
		//print last
		StringBuilder sb = new StringBuilder(workingChr);
		rtp.appendInfo(sb, workingStartBp);
		out.println(sb);
		al.clear();
		
	}

	private class RegionToPrint{
		int start;
		int stop;
		String ids;
		
		public RegionToPrint(int start, int stop, String ids){
			this.start = start;
			this.stop = stop;
			this.ids = ids;
		}

		public void appendInfo(StringBuilder sb, int blockStartPosition) {
			sb.append("\t");
			sb.append(start+blockStartPosition);
			sb.append("\t");
			sb.append(stop+blockStartPosition);
			sb.append("\t");
			sb.append(ids);
		}
	}
	
	
	private static String fetchIds(HashSet<IndexRegion> al) {
		//just one?
		if (al.size() == 1) return new Integer (al.iterator().next().fileId).toString();
		
		//hash ids and sort, might have dups
		TreeSet<Integer> idsTS = new TreeSet<Integer>();
		Iterator<IndexRegion> it = al.iterator();
		while (it.hasNext())idsTS.add(it.next().fileId);
	
		//create string
		Iterator<Integer>iit = idsTS.iterator();
		StringBuilder sb = new StringBuilder(iit.next().toString());
		while (iit.hasNext()){
			sb.append(",");
			sb.append(iit.next().toString());
		}
		
		return sb.toString();
	}

	public void bgzipAndTabixIndex() {
		File[] beds = Util.extractFiles(workingIndexDir, ".qi.bed");
		for (File bed: beds){
			//compress with bgzip, this will replace any existing compressed file and delete the uncompressed
			String[] cmd = { bgzip.toString(), "-f", "--threads", numberThreads+"", bed.toString()};
			String[] output = Util.executeViaProcessBuilder(cmd, false);
			File compBed = new File (bed+".gz");
			if (output.length != 0 || bed.exists() == true || compBed.exists() == false){
				Util.printErrAndExit("\nERROR: Failed to bgzip compress "+bed+"\nMessage: "+Util.stringArrayToString(output, "\n"));
			}

			//tabix
			//must use -0 --sequence 1 --begin 2 --end 3; -p bed doesn't work with java tabix!!!!
			cmd = new String[]{ tabix.toString(), "-0", "--sequence", "1", "--begin", "2", "--end", "3", compBed.toString()};

			output = Util.executeViaProcessBuilder(cmd, false);
			File indexBed = new File (compBed+".tbi");
			if (output.length != 0 || indexBed.exists() == false){
				Util.printErrAndExit("\nERROR: Failed to tabix index "+compBed+"\nMessage: "+Util.stringArrayToString(output, "\n"));
			}
		}
	}

	public static void main(String[] args) {
		if (args.length ==0){
			printDocs();
			System.exit(0);
		}
		new GQueryIndexer (args);
	}	

	/**This method will process each argument and assign new varibles*/
	public void processArgs(String[] args){
		Pattern pat = Pattern.compile("-[a-z]");
		File tabixBinDirectory = null;
		Util.pl("\nGQuery Indexer Arguments: "+ Util.stringArrayToString(args, " ") +"\n");
		for (int i = 0; i<args.length; i++){
			String lcArg = args[i].toLowerCase();
			Matcher mat = pat.matcher(lcArg);
			if (mat.matches()){
				char test = args[i].charAt(1);
				try{
					switch (test){
					case 'c': chrNameLength = new File(args[++i]); break;
					case 'd': dataDir = new File(args[++i]).getCanonicalFile(); break;
					case 'q': verbose = false; break;
					case 't': tabixBinDirectory = new File(args[++i]); break;
					case 'n': numberThreads = Integer.parseInt(args[++i]); break;
					case 'b': bpBlock = Integer.parseInt(args[++i]); break;
					default: Util.printErrAndExit("\nProblem, unknown option! " + mat.group());
					}
				}
				catch (Exception e){
					Util.printErrAndExit("\nSorry, something doesn't look right with this parameter: -"+test+"\n");
				}
			}
		}
		//pull tabix and bgzip executables
		if (tabixBinDirectory == null) Util.printExit("\nError: please point to the dir containing the tabix and bgzip HTSlib executibles (e.g. /Users/Clinton/BioApps/HTSlib/1.3/bin/ )\n");
		bgzip = new File (tabixBinDirectory, "bgzip");
		tabix = new File (tabixBinDirectory, "tabix");
		if (bgzip.canExecute() == false || tabix.canExecute() == false) Util.printExit("\nCannot find or execute bgzip or tabix executables from "+bgzip+" "+tabix);

		if (chrNameLength == null || chrNameLength.exists() == false) Util.printErrAndExit("\nError: please provide a bed file of chromosome and their max lengths to index. e.g. X 0 155270560\n" );
		if (dataDir == null || dataDir.isDirectory() == false) Util.printErrAndExit("\nERROR: please provide a directory containing gzipped and tabix indexed bed, vcf, maf.txt, and bedGraph files to index." );

		//threads to use
		int numAvail = Runtime.getRuntime().availableProcessors();
		if (numberThreads < 1 || numberThreads > numAvail) numberThreads =  numAvail - 1;
		Util.pl(numAvail +" available processors, using "+numberThreads);
		
		toTruncatePoint = dataDir.getParentFile().toString().length()+1;
	}	

	private void parseChromLengthFile() {
		SimpleBed[] sb = SimpleBed.parseFile(chrNameLength);
		Arrays.sort(sb);
		HashMap<String, SimpleBed[]> chrLen = SimpleBed.splitBedByChrom(sb);
		chrLengths = new TreeMap<String, Integer>();
		for (String chr: chrLen.keySet()){
			//find max
			SimpleBed[] regions = chrLen.get(chr);
			int max = -1;
			for (SimpleBed r: regions){
				if (r.getStop()> max) max = r.getStop();
			}
			//drop chr
			if (chr.startsWith("chr")) chr = chr.substring(3);
			//any already present?
			Integer priorLen = chrLengths.get(chr);
			if (priorLen == null || priorLen.intValue() < max) chrLengths.put(chr, max);
		}
	}

	private void setKnownFileTypeSSS() {
		//0 index start stop column info for "bed.gz", "bedGraph.gz", "maf.txt.gz"
		//the last is the number to subtract from the start to convert to interbase
		//note vcf.gz is it's own beast and handled separately
		extensionsStartStopSub.put("bed.gz", new int[]{1,2,0});
		extensionsStartStopSub.put("bedgraph.gz", new int[]{1,2,0});
		extensionsStartStopSub.put("maf.txt.gz", new int[]{5,6,1});
	}
	
	public synchronized int[] getSSS(File f) {
		int[] startStopSubtract = null;
		try {
			//pull known
			String name = f.getName().toLowerCase();
			if (name.endsWith(".bed.gz")) startStopSubtract = extensionsStartStopSub.get("bed.gz");
			else if (name.endsWith(".maf.txt.gz")) startStopSubtract =  extensionsStartStopSub.get("maf.txt.gz");
			else if (name.endsWith(".bedgraph.gz")) startStopSubtract =  extensionsStartStopSub.get("bedgraph.gz");
			
			//pull from tbi
			//I'm suspicious of the flags field so the subtract may be wrong.  Best to manually set for each file type.
			else {
				File index = new File (f.getPath()+".tbi");
				TabixIndex ti = new TabixIndex(index);
				TabixFormat tf = ti.getFormatSpec();
				int startIndex = tf.startPositionColumn- 1;
				int stopIndex = tf.endPositionColumn- 1;
				int sub = 1;
				if (tf.flags == 65536) sub = 0;
				startStopSubtract = new int[]{startIndex, stopIndex, sub};
				//Util.pl("New data type! ");
				//Util.printArray(startStopSubtract);
			}
		} catch (IOException e) {
			e.printStackTrace();
			Util.printErrAndExit("Error loading tbi index, aborting.");
		}
		return startStopSubtract;
	}

	
	private void saveFileIds(){
		try {

			//find all the parsed files and their id
			TreeMap<Integer, File> parsedFiles = new TreeMap<Integer, File>();
			for (ArrayList<File> al: workingChrFiles.values()) {
				//for each file
				for (File f: al) {
					Integer id = workingFileId.get(f);
					if (id == null) throw new Exception("\nFailed to find an id for "+f);
					if (parsedFiles.containsKey(id) == false) parsedFiles.put(id, f);
				}
			}

			//save with truncated paths
			int toSkip = dataDir.getParentFile().toString().length()+1;
			Gzipper out = new Gzipper(new File (workingIndexDir, "fileInfo.txt.gz"));
			out.println("#Id\tSize\tLastMod\tRelPath");
			
			for (Integer id: parsedFiles.keySet()){
				File f = parsedFiles.get(id);
				
				//trim the name
				String trimmedName = f.getPath().substring(toSkip);
				
				//save id size lastModified and trimmed name
				out.println(id+"\t"+f.length()+"\t"+f.lastModified()+"\t"+trimmedName);
			}
			out.close();

		} catch (Exception e){
			e.printStackTrace();
			Util.printErrAndExit("\nERROR: saving file objects, aborting\n");
		}
	}
	
	private void parseDataSourceDirs(){
		Util.pl("\nSearching for directories containing tabixed data sources...");
		dirsWithTbis = new ArrayList<File>();
		
		ArrayList<File> dirs = Util.fetchDirectoriesRecursively(dataDir);
		dirs.add(dataDir);

		//for each look inside for tbi indexes
		for (File dir: dirs) {
			//skip index dirs
			if (dir.getName().equals(INDEX_DIR_NAME)) continue;
			
			File[] list = dir.listFiles();
			for (int i=0; i< list.length; i++){
				if (list[i].getName().endsWith(".gz.tbi")) {
					dirsWithTbis.add(dir);
					break;
				}
			}
		}
		if (dirsWithTbis.size() == 0) Util.printErrAndExit("\nERROR: No directories were found with xxx.gz.tbi files inside "+dataDir+" Aborting!");
	}
	
	private void parseDataSources(File dir){
			ArrayList<File> goodDataSources = new ArrayList<File>();
			ArrayList<File> tbiMissingDataSources = new ArrayList<File>();
			ArrayList<File> unrecognizedDataSource = new ArrayList<File>();

			//find .tbi files and check
			File[] list = dir.listFiles();
			for (File f: list){
				if (f.getName().endsWith(".gz.tbi") == false) continue;
				String path = f.getPath();

				//look for data file
				File df = new File(path.substring(0, path.length()-4));
				if (df.exists() == false){
					tbiMissingDataSources.add(f);
					continue;
				}
				
				//is it a known type
				boolean recognized = false;
				for (String knownExt: fileExtToIndex){
					if (df.getName().toLowerCase().endsWith(knownExt)){
						recognized = true;
						break;
					}
				}
				if (recognized) goodDataSources.add(df); 
				else unrecognizedDataSource.add(df);
			}

			int numFiles = goodDataSources.size() + unrecognizedDataSource.size();

			//print messages
			if (numFiles == 0) Util.printErrAndExit("\nERROR: failed to find any data sources for tabix indexed files in "+dir+" Aborting.");
			Util.pl("\t\t"+goodDataSources.size()+" Data sources with known formats ("+ Util.stringArrayToString(fileExtToIndex, ", ")+")");
			if (tbiMissingDataSources.size() !=0){
				Util.pl("\t\t"+tbiMissingDataSources.size()+" WARNING: The data source file(s) for the following tbi index(s) could not be found, skipping:");
				for (File f: tbiMissingDataSources) Util.pl("\t\t\t"+f.getPath());
			}
			if (unrecognizedDataSource.size() !=0){
				Util.pl("\t\t"+unrecognizedDataSource.size()+" WARNING: Data sources with unknown format(s). The format of the "
						+ "following files will be set using info from the tabix index and may be incorrect. Contact "+email+" to add.");
				for (File f: unrecognizedDataSource) Util.pl("\t\t\t"+f.getPath());
			}
			
			//make final set
			goodDataSources.addAll(unrecognizedDataSource);
			workingDataFilesToParse = new File[goodDataSources.size()];
			goodDataSources.toArray(workingDataFilesToParse);
			Arrays.sort(workingDataFilesToParse);
			totalFilesIndexed+= workingDataFilesToParse.length;
	}

	public static File[] returnFilesWithTabix(File[] tabixFiles) {
		ArrayList<File> goodFiles = new ArrayList<File>();
		for (File tb: tabixFiles){
			File index = new File (tb.toString()+".tbi");
			if (index.exists()) goodFiles.add(tb);
		}
		//any files?
		if (goodFiles.size()==0) return null;
		File[] toReturn = new File[goodFiles.size()];
		goodFiles.toArray(toReturn);
		return toReturn;
	}


	public static void printDocs(){
		Util.pl("\n" +
				"**************************************************************************************\n" +
				"**                               GQuery Indexer: Feb 2020                           **\n" +
				"**************************************************************************************\n" +
				"GQI builds index files for GQuery by recursing through a root data directory looking for\n"+
				"directories containing bgzip compressed and tabix indexed genomic data files, e.g. \n"+
				"xxx.gz and xxx.gz.tbi . A GQuery index is created for each directory, thus place or\n"+
				"link 100 or more related files in the same directory, e.g. Data/Hg38/Somatic/Vcfs/\n"+
				"and Data/Hg38/Somatic/Cnvs/ . This app is threaded for simultanious file loading\n"+
				"and requires >30G RAM to run on large data collections. Lastly, the indexer will only\n"+
				"re index an existing index if the data files have changed. Thus, run it nightly to\n"+
				"keep the indexes up to date.\n"+

				"\nRequired Params:\n"+
				"-c A bed file of chromosomes and their lengths (e.g. chr21 0 48129895) to use to \n"+
				"     building the intersection index. Exclude those you don't want to index. For\n"+
				"     multiple builds and species, add all, duplicates will be collapsed taking the\n"+
				"     maximum length. Any 'chr' prefixes are ignored when indexing and searching.\n"+
				"-d A data directory containing bgzipped and tabix indexed data files. Known file\n"+
				"     types include xxx.vcf.gz, xxx.bed.gz, xxx.bedGraph.gz, and xxx.maf.txt.gz. Others\n"+
				"     will be parsed using info from the xxx.gz.tbi index. See\n"+
				"     https://github.com/samtools/htslib . For bed files don't use the -p option,\n"+
				"     use '-0 -s 1 -b 2 -e 3'. For vcf files, vt normalize and decompose_blocksub,\n"+
				"     see http://genome.sph.umich.edu/wiki/Vt.\n"+
				"-t Full path directory containing the compiled bgzip and tabix executables. See\n" +
				"     https://github.com/samtools/htslib\n"+

				"\nOptional Params:\n"+
				"-q Quiet output, no per record warnings.\n"+
				"-b BP block to process, defaults to 250000000. Reduce if out of memory issues occur.\n"+
				"-n Number cores to use, defaults to all\n"+

				"\nExample for generating the test index using the GitHub GQuery/TestResources files\n"+
				"see https://github.com/HuntsmanCancerInstitute/GQuery\n\n"+
				
				"d=/pathToYourLocalGitHubInstalled/GQuery/TestResources\n"+
				"java -jar -Xmx115G GQueryIndexer_0.1.jar -c $d/b37Chr20-21ChromLen.bed -d $d/Data\n"+
				"-t $d/Htslib_1.10.2/bin/ \n\n" +

				"**************************************************************************************\n");
	}

	public int getWorkingIndexLength() {
		return workingIndex.length;
	}

	public boolean isVerbose() {
		return verbose;
	}

	public HashMap<File, Integer> getFileId() {
		return workingFileId;
	}

	public Integer[] getIds() {
		return workingIds;
	}

	public long getTotalRecordsProcessed() {
		return totalRecordsProcessed;
	}

}
