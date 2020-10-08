package edu.utah.hci.query;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import edu.utah.hci.apps.GQueryIndexer;
import edu.utah.hci.misc.Util;

/**Container for all of the individual Query objects.*/
public class MasterQuery {

	//fields
	private File dataDir;
	private HashMap<String, SingleQuery> queryIndexes = new HashMap<String, SingleQuery>();
	private HashSet<String> availableIndexedChromosomes = new HashSet<String>();
	private int numCharToSkipForDataDir;
	private static final String[] stringFileHeaderPatternStarts = {"^[#/<@].+", "^browser.+", "^track.+", "^color.+", "^url.+", "^Hugo_Symbol.+"};
	private Pattern[] fileHeaderStarts = null;
	private int numberThreads = 0;
	private boolean initialized = false;
	private static final Logger lg = LogManager.getLogger(MasterQuery.class);
	private String buildInfo = null;
	
	//for building the SingleQuerys
	private long msTimeToComplete = 0;
	private ArrayList<File> queryIndexDirs = null;
	private int numberQueryBuildJobs = 0;
	private int numberIndexedFiles = 0;
	private int currentQueryBuildJobIndex = 0;
	
	//constructor
	public MasterQuery (File dataDir) throws IOException {
		long start = System.currentTimeMillis();
		this.dataDir = dataDir;
		
		//how much to trim
		numCharToSkipForDataDir = dataDir.getParentFile().toString().length()+1;
		
		//create patterns for parsing file headers
		makeFileHeaderStarts();
		
		//threads to use
		int numAvail = Runtime.getRuntime().availableProcessors();
		if (numberThreads < 1) numberThreads =  numAvail - 1;
		lg.info(numAvail +" Available processors, using "+numberThreads+" query threads");
		
		buildQueries();
		
		msTimeToComplete = System.currentTimeMillis() - start;
		buildInfo = msTimeToComplete +" ms to register "+numberQueryBuildJobs+" query indexes with "+numberIndexedFiles+ " data files";
		lg.info(buildInfo);
	}


	private void buildQueries() throws IOException {
		//find all of the QueryIndexes built by the indexer
		queryIndexDirs = Util.fetchNamedDirectoriesRecursively(dataDir, GQueryIndexer.INDEX_DIR_NAME);
		numberQueryBuildJobs = queryIndexDirs.size();
		if (numberQueryBuildJobs == 0) throw new IOException ("ERROR: failed to find any "+GQueryIndexer.INDEX_DIR_NAME+" directories in "+dataDir+" . Has this been MultiGQueryIndexed?");

		//create threaded loaders
		int numLoaders = this.numberThreads;
		if (numberQueryBuildJobs < numLoaders) numLoaders = numberQueryBuildJobs;
		SingleQueryIndexBuilder[] loader = new SingleQueryIndexBuilder[numLoaders];
		ExecutorService executor = Executors.newFixedThreadPool(numLoaders);
		for (int i=0; i< loader.length; i++){
			loader[i] = new SingleQueryIndexBuilder(this);
			executor.execute(loader[i]);
		}
		executor.shutdown();
		//spins here until the executer is terminated, e.g. all threads complete
		while (!executor.isTerminated()) {}
		//check loaders 
		for (SingleQueryIndexBuilder c: loader) {
			if (c.isFailed()) throw new IOException("ERROR: SingleTabixQueryBuilder issue! \n"+c);
		}
		initialized = true;
	}
	
	/**Provides a Query Index Dir or returns null*/
	public synchronized File getQueryIndexJob() {
		if (currentQueryBuildJobIndex < numberQueryBuildJobs) return queryIndexDirs.get(currentQueryBuildJobIndex++);
		return null;
	}
	
	/**Provides a Query Index Dir or returns null*/
	public synchronized void addQueryIndex(String truncDataPath, SingleQuery singleQuery, int numberDataSources) {
		queryIndexes.put(truncDataPath, singleQuery);
		this.numberIndexedFiles+= numberDataSources;
	}
	
	/**Provides a Query Index Dir or returns null*/
	public synchronized void addAvailableIndexedChromosomes(Set<String> chroms) {
		availableIndexedChromosomes.addAll(chroms);
	}

	public int getNumCharToSkipForDataDir() {
		return numCharToSkipForDataDir;
	}

	/**@return truncated file path: SingleQuery index*/
	public HashMap<String, SingleQuery> getQueryIndexes() {
		return queryIndexes;
	}
	
	public void makeFileHeaderStarts() {
		fileHeaderStarts = new Pattern[stringFileHeaderPatternStarts.length];
		for (int i =0; i< stringFileHeaderPatternStarts.length; i++) fileHeaderStarts[i] = Pattern.compile(stringFileHeaderPatternStarts[i]); 
	}
	
	/**Extracts the file header as defined by the file header patterns. zip and gzip ok.
	 * @throws IOException */
	public JSONObject fetchFileHeader(File f) throws IOException {
		ArrayList<String> header = new ArrayList<String>();
		BufferedReader in = Util.fetchBufferedReader(f);
		String line;
		while ((line = in.readLine()) != null){
			//skip blanks
			line = line.trim();
			if (line.length()==0) continue;
			//scan patterns
			boolean found = false;
			for (Pattern p: fileHeaderStarts) {
				Matcher m = p.matcher(line);
				if (m.matches()) {
					header.add(line);
					found = true;
					break;
				}
			}
			if (found == false) break;
		}
		in.close();
		
		//make Json object
		JSONObject jo = new JSONObject();
		jo.put("source", f.toString().substring(numCharToSkipForDataDir));
		jo.put("header", header);
		return jo;
	}

	public HashSet<String> getAvailableIndexedChromosomes() {
		return availableIndexedChromosomes;
	}
	public boolean isInitialized() {
		return initialized;
	}

	public File getDataDir() {
		return dataDir;
	}

	public int getNumberThreads() {
		return numberThreads;
	}


	public String getBuildInfo() {
		return buildInfo;
	}
	
}
