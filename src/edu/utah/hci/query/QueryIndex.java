package edu.utah.hci.query;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import edu.utah.hci.it.Interval1D;
import edu.utah.hci.it.IntervalST;
import edu.utah.hci.query.tabix.TabixDataQuery;
import edu.utah.hci.query.tabix.TabixIndexLoader;

/**Primary class for intersecting regions of interest against the index via an in memory interval tree or via threaded tabix index file look up.  The former is much faster and preferred if your server has the memory.*/
public class QueryIndex {

	private DataSources dataSources;
	private static final Logger lg = LogManager.getLogger(QueryIndex.class);
	
	//chr names and corresponding IntervalTree containing file ids that intersected each
	private HashMap<String, IntervalST<int[]>> chrTrees = null;
	private File[] fileId2File = null;
	
	//For tabix index lookup
	//chr tabix file
	private HashMap<String, File> chrTabixTree = null;
	private TabixIndexLoader[] tabixIndexLoaders = null;
	private HashSet<File> fileHits = new HashSet<File>();
	
	//constructor
	public QueryIndex(Query query) throws IOException{
		
		//load the various index objects
		File ids = new File(query.getIndexDir(), "fileIds.obj");
		File skipped = new File (query.getIndexDir(), "skippedSources.obj");
		if (skipped.exists() == false){
			String m = "ERROR: cannot find one or more of the required object files (fileIds.obj, fileHeaders.obj, skippedSources.obj) in your indexDir "+query.getIndexDir()+", aborting.";
			lg.fatal(m);
			throw new IOException (m);
		}
		
		//truncated file name with it's id
		lg.info("Loading serialized index objects, starting mem usage "+Util.memoryUsage());
		@SuppressWarnings("unchecked")
		HashMap<String, Integer> fileStringId = (HashMap<String, Integer>) Util.fetchObject(ids);
		
		//truncated file names for any data sources that were not actually indexed and require a fetchData=force query, might be empty
		@SuppressWarnings("unchecked")
		TreeSet<String> skippedDataSourceNames = (TreeSet<String>) Util.fetchObject(skipped);
		
		//build interval tree? chr names and corresponding IntervalTree containing file ids that intersected each
		if (query.isInMemoryIntervalTree()) buildIntervalTrees(query.getIndexDir());
		else {
			buildTabixSearchTree(query.getIndexDir());
			tabixIndexLoaders = new TabixIndexLoader[query.getNumberThreads()];
			for (int i=0; i< tabixIndexLoaders.length; i++) tabixIndexLoaders[i] = new TabixIndexLoader();
		}
		
		//create a container to hold all the File info for per request filtering
		dataSources = new DataSources(query.getDataDir(), skippedDataSourceNames);
		
		//for each file
		int max = findMaxIntValue (fileStringId);		
		fileId2File = new File[max+1];
		File dataDirParent = query.getDataDir().getParentFile();
		for (String path: fileStringId.keySet()){
			File f = new File (dataDirParent, path);
			//does it exist?
			if (f.exists() == false){
				String m = "Failed to find this data file despite presence in index, aborting! "+f;
				lg.fatal(m);
				throw new IOException (m);
			}
			int id = fileStringId.get(path);
			
			fileId2File[id] = f;
			dataSources.addFileToFilter(f);
		}
		
		//trim the FileNames
		dataSources.trimFileNames();
		
		//set in Query
		query.setDataSources(dataSources);
		
		lg.info("Memory usage: "+Util.memoryUsage());
	}
	
	/**Find the maxium int value in the hashmap*/
	public static int findMaxIntValue(HashMap<String, Integer> hash) {
		int max = 0;
		for (Integer i: hash.values()) {
			if (i> max) max = i;
		}
		return max;
	}
	
	/**This builds lookups using tabix instead of the in memory interval trees.*/
	private void buildTabixSearchTree(File indexDir) {
		lg.info("Building tabix lookup intersection tree:");
		try {
			File[] chrBed = Util.extractFiles(indexDir, ".qi.bed.gz");
			chrTabixTree = new HashMap<String, File>();
			
			//for each file
			for (File f: chrBed){
				//look for tbi's
				File tbi = new File(f.getParentFile(), f.getName()+".tbi");
				if (tbi.exists() == false) throw new IOException("Failed to find the tabix xxx.tbi index for "+f);
				
				//save chrom, tabix file
				String chr = f.getName().replace(".qi.bed.gz", "");
				chrTabixTree.put(chr, f);
				lg.info("\t"+chr);
			}

		} catch (IOException e) {
			e.printStackTrace();
			Util.printErrAndExit("\nERROR: problem bulding tabix file search tree from the bed files, aborting.\n");
		}
	}


	public void buildIntervalTrees(File indexDir){
		lg.info("Building interval trees (chr #intervals):");
		try {
			File[] chrBed = Util.extractFiles(indexDir, ".qi.bed.gz");
			chrTrees = new HashMap<String, IntervalST<int[]>>();
			HashMap<String, int[]> idsFileIndex = new HashMap<String, int[]>();
			//for each file
			for (File f: chrBed){
				BufferedReader in = Util.fetchBufferedReader(f);
				IntervalST<int[]> st = new IntervalST<int[]>();
				String line;
				while ((line = in.readLine())!= null){
					String[] t = Util.TAB.split(line);
					int start = Integer.parseInt(t[1]);
					int stop = Integer.parseInt(t[2]);
					int[] ids = idsFileIndex.get(t[3]);
					if (ids == null){
						ids = stringToInts(t[3], Util.COMMA);
						idsFileIndex.put(t[3], ids);
					}
					//the end is included in IntervalST so sub 1 from end
					st.put(new Interval1D(start, stop-1), ids);
				}
				in.close();

				//save tree
				String chr = f.getName().replace(".qi.bed.gz", "");
				chrTrees.put(chr, st);
				lg.info("\t"+chr+"\t"+st.size()+"\t"+Util.memoryUsage());
			}

		} catch (IOException e) {
			e.printStackTrace();
			Util.printErrAndExit("\nERROR: problem bulding interval trees from the bed files, aborting.\n");
		}
	}
	
	/**Given a String of ints delimited by something, will parse or return null.*/
	public static int[] stringToInts(String s, Pattern pat){
		String[] tokens = pat.split(s);
		int[] num = new int[tokens.length];
		try {
			for (int i=0; i< tokens.length; i++){
				num[i] = Integer.parseInt(tokens[i]);
			}
			return num;
		} catch (Exception e){
			return null;
		}
	}

	
	public JSONObject queryFileIndex(QueryRequest qr) throws IOException{
		if (chrTrees == null) return queryFileIndexViaTabix(qr);
		else return queryFileIndexViaIntervalTrees(qr);
	}
	
	/**This finds intersecting files to a list of user queries via an in memory interval tree lookup. Preferred approach if your server has the memory.*/
	public JSONObject queryFileIndexViaIntervalTrees(QueryRequest qr) {
		long startTime = System.currentTimeMillis();
		long numProcessedQueries = 0;
		long numSkippedQueries = 0;
		long numQueriesWithHits = 0;
		long numberHits = 0;

		HashMap<File, ArrayList<TabixDataQuery>> fileTabixQueries = qr.getFileTabixQueries();
		HashMap<String, TabixDataQuery[]> chrTabixQueries = qr.getChrTabixQueries();

		//for each chromosome of regions to query
		for (String chr: chrTabixQueries.keySet()){

			//check that chr exists in the trees
			if (chrTrees.containsKey(chr) == false){
				int numSkipped = chrTabixQueries.get(chr).length;
				String warning = "No records for chromosome '"+chr+"' are present in the index. Skipping "+numSkipped+" user query region(s).";
				lg.warn(warning);
				qr.getWarningTxtForUser().add(warning);
				numSkippedQueries += numSkipped; 
			}

			else {
				//pull the interval tree
				IntervalST<int[]> index = chrTrees.get(chr);
				
				//pull the regions to intersect
				TabixDataQuery[] regions = chrTabixQueries.get(chr);
				numProcessedQueries += regions.length;
				
				//for each region
				for (TabixDataQuery tq: regions){

					//search the interval tree, end is included so sub 1
					Iterable<Interval1D> it = index.searchAll(new Interval1D(tq.getStart(), tq.getStop()-1));
					for (Interval1D x : it) {
						int[] ids = index.get(x);
						//add Files based on indexes in the interval tree, use hash to collapse
						for (int i=0; i< ids.length; i++) fileHits.add(fileId2File[ids[i]]);
					}
					
					//any intersecting files?
					if (fileHits.size() !=0) {
						numberHits += fileHits.size();
						numQueriesWithHits++;
						addHits(fileHits, fileTabixQueries, tq);
						fileHits.clear();
					}
				}
			}
		}

		long diffTime = System.currentTimeMillis() -startTime;
		
		//make json object
		JSONObject stats = new JSONObject();
		stats.put("searchedUserQueries", numProcessedQueries);
		stats.put("skippedUserQueries", numSkippedQueries);
		stats.put("queriesWithIndexHits", numQueriesWithHits);
		stats.put("totalIndexHits", numberHits);
		stats.put("millSecForIndexSearch", diffTime);
		return stats;
	}
	
	/**This finds intersecting files to a list of user queries via tabix indexed file lookup. This is the alternate approach to using an in memory interval tree.*/
	public JSONObject queryFileIndexViaTabix(QueryRequest qr) throws IOException {
		long startTime = System.currentTimeMillis();
		long numProcessedQueries = 0;
		long numSkippedQueries = 0;
		long numQueriesWithHits = 0;
		long numberHits = 0;

		HashMap<File, ArrayList<TabixDataQuery>> fileTabixQueries = qr.getFileTabixQueries();
		HashMap<String, TabixDataQuery[]> chrTabixQueries = qr.getChrTabixQueries();
		//for each chromosome of regions to query
		for (String chr: chrTabixQueries.keySet()){

			//check that chr exists in the trees
			if (chrTabixTree.containsKey(chr) == false){
				int numSkipped = chrTabixQueries.get(chr).length;
				String warning = "No records for chromosome '"+chr+"' are present in the index. Skipping "+numSkipped+" user query region(s).";
				lg.warn(warning);
				qr.getWarningTxtForUser().add(warning);
				numSkippedQueries += numSkipped; 
			}

			else {
				File tabixIndex = chrTabixTree.get(chr);
				
				//pull the regions to intersect
				TabixDataQuery[] regions = chrTabixQueries.get(chr);
				numProcessedQueries += regions.length;
				
				//chunk em based on # loaders
				TabixDataQuery[][] chunk = chunk(regions, tabixIndexLoaders.length);
				
				//make thread pool
				ExecutorService executor = Executors.newFixedThreadPool(chunk.length);
			
				for (int i=0; i< chunk.length; i++){	
					tabixIndexLoaders[i].setQueries(chunk[i], tabixIndex);
					executor.execute(tabixIndexLoaders[i]);
				}
				executor.shutdown();

				//spins here until the executer is terminated, e.g. all threads complete
				while (!executor.isTerminated()) {}

				//check loaders 
				for (int i=0; i< chunk.length; i++){
					if (tabixIndexLoaders[i].isFailed()) throw new IOException("ERROR: TabixIndexLoader issue! \n"+tabixIndexLoaders[i]);
				}
				
				//for each region
				for (TabixDataQuery tq: regions){
					HashSet<Integer> fileIds = tq.getIntersectingFileIds();
					
					//any intersecting files?
					if (fileIds != null){
						int numIntersectingFiles = fileIds.size();
						numberHits += numIntersectingFiles;
						numQueriesWithHits++;
						addHitsViaFileIds(fileIds, fileTabixQueries, tq);
					}
					//null the hash to minimize size
					tq.setIntersectingFileIds(null);
				}
			}
		}

		long diffTime = System.currentTimeMillis() -startTime;
		
		//make json object
		JSONObject stats = new JSONObject();
		stats.put("searchedUserQueries", numProcessedQueries);
		stats.put("skippedUserQueries", numSkippedQueries);
		stats.put("queriesWithIndexHits", numQueriesWithHits);
		stats.put("totalIndexHits", numberHits);
		stats.put("millSecForIndexSearch", diffTime);
		return stats;
	}
	
	/**Adds the TabixQuery to an ArrayList associated with a file resource to fetch the data from.*/
	private void addHitsViaFileIds(HashSet<Integer> hits, HashMap<File, ArrayList<TabixDataQuery>> toQuery, TabixDataQuery tq) {
		for (Integer fileId: hits){
			File fHit = this.fileId2File[fileId];
			ArrayList<TabixDataQuery> al = toQuery.get(fHit);
			if (al == null){
				al = new ArrayList<TabixDataQuery>();
				toQuery.put(fHit, al);
			}
			al.add(tq);
		}
	}

	/**Adds the TabixQuery to an ArrayList associated with a file resource to fetch the data from.*/
	private static void addHits(HashSet<File> hits, HashMap<File, ArrayList<TabixDataQuery>> toQuery, TabixDataQuery tq) {
		for (File fHit: hits){
			ArrayList<TabixDataQuery> al = toQuery.get(fHit);
			if (al == null){
				al = new ArrayList<TabixDataQuery>();
				toQuery.put(fHit, al);
			}
			al.add(tq);
		}
	}

	
	public HashMap<String, IntervalST<int[]>> getChrTrees() {
		return chrTrees;
	}
	
	public Set<String> getAvailableChromosomes(){
		if (chrTrees != null) return chrTrees.keySet();
		else return this.chrTabixTree.keySet();
	}
	
	/**Splits an TabixDataQuery[] into chunks containing the minNumEach. Any remainder is evenly distributed over the prior.
	 * Note this is by reference, the array is not copied. */
	public static TabixDataQuery[][] chunk (TabixDataQuery[] s, int minNumEach){
		//watch out for cases where the min can't be met
		int numChunks = s.length/minNumEach;
		if (numChunks == 0) return new TabixDataQuery[][]{s};
		
		double numLeftOver = (double)s.length % (double)minNumEach;
		
		int[] numInEach = new int[numChunks];
		for (int i=0; i< numChunks; i++) numInEach[i] = minNumEach;
		
		while (numLeftOver > 0){
			for (int i=0; i< numChunks; i++) {
				numInEach[i]++;
				numLeftOver--;
				if (numLeftOver == 0) break;
			}
		}
		//build chunk array
		TabixDataQuery[][] chunks = new TabixDataQuery[numChunks][];
		int index = 0;
		//for each chunk
		for (int i=0; i< numChunks; i++){
			//create container and fill it
			TabixDataQuery[] sub = new TabixDataQuery[numInEach[i]];
			for (int j=0; j< sub.length; j++) sub[j] = s[index++];
			chunks[i] = sub;
		}
		return chunks;
	}

}
