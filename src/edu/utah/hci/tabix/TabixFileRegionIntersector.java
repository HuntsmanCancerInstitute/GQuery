package edu.utah.hci.tabix;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

import edu.utah.hci.query.QueryRequest;
import edu.utah.hci.query.SingleQuery;

/**Class for coordinating the fetch of data from tabix files*/
public class TabixFileRegionIntersector {
	
	//fields
	private HashMap<File, ArrayList<TabixDataQuery>> fileTabixQueries = null;
	private ArrayList<TabixIndexFileLookupJob> lookupJobs = new ArrayList<TabixIndexFileLookupJob>();
	private int currentLookupJobIndex = 0;
	private int numberLookupJobs = 0;
	private int numberQueries = 0;
	private HashSet<String> lookupJobsWithFileHits = new HashSet<String>();
	private long msTimeToComplete = 0;

	public TabixFileRegionIntersector(QueryRequest queryRequest, ArrayList<SingleQuery> toSearch) throws IOException {
		
		long start = System.currentTimeMillis();
		
		// this is the master object to load with files that intersect the user queries
		fileTabixQueries = queryRequest.getFileTabixQueries();
		
		
		//Contains all the user region TabixQueries split by chromosome. Will be loaded with results.
		HashMap<String, TabixDataQuery[]> chrTabixQueries = queryRequest.getChrTabixQueries();
		
		//create lookup jobs
		//for each chrom of user TabixQueries
		
		for (String chr: chrTabixQueries.keySet()) {
			TabixDataQuery[] regions = chrTabixQueries.get(chr);
			numberQueries+= regions.length;
			
			//for each SingleQuery
			for (SingleQuery sq: toSearch) {	
				//does the SingleQuery have the chr they want to search? If so create LookupJobs
				File fToSearch = sq.getQueryIndex().getChrTabixTree().get(chr);
				if (fToSearch != null) {
					for (TabixDataQuery mtdq: regions) lookupJobs.add(new TabixIndexFileLookupJob(sq.getQueryIndex().getFileId2File(), fToSearch, mtdq));
				}
			}
		}

		numberLookupJobs = lookupJobs.size();		
		
		//create threaded loaders
		int numLoaders = queryRequest.getMasterQuery().getNumberThreads();
		if (numberLookupJobs < numLoaders) numLoaders = numberLookupJobs;
		SingleTabixFileIndexLoader[] loader = new SingleTabixFileIndexLoader[numLoaders];
		ExecutorService executor = Executors.newFixedThreadPool(numLoaders);
		for (int i=0; i< loader.length; i++){
			loader[i] = new SingleTabixFileIndexLoader(this);
			executor.execute(loader[i]);
		}
		executor.shutdown();
		//spins here until the executer is terminated, e.g. all threads complete
		while (!executor.isTerminated()) {}
		//check loaders 
		for (SingleTabixFileIndexLoader c: loader) {
			if (c.isFailed()) throw new IOException("ERROR: SingleTabixFileIndexLoader issue! \n"+c);
		}
		
		
		
		//walk all of the user queries to see if they have a file hit, then add them to the master
		for (String chr: chrTabixQueries.keySet()) {
			for (TabixDataQuery mtdq: chrTabixQueries.get(chr)) {
				HashSet<File> intFiles = mtdq.getIntersectingFiles();
				//any intersecting files?
				if (intFiles.size() != 0) addHitsViaFiles(intFiles, mtdq);
			}
		}
		
		
		
		msTimeToComplete = System.currentTimeMillis() - start;
	}
	
	/**Adds the TabixQuery to an ArrayList associated with a file resource to fetch the data from.*/
	private void addHitsViaFiles(HashSet<File> fileHits, TabixDataQuery tq) {
		for (File fHit: fileHits){
			ArrayList<TabixDataQuery> al = fileTabixQueries.get(fHit);
			if (al == null){
				al = new ArrayList<TabixDataQuery>();
				fileTabixQueries.put(fHit, al);
			}
			al.add(tq);
		}
	}

	/**Provides a single lookup job or returns null*/
	public synchronized TabixIndexFileLookupJob getTabixLookupJob() {
		if (currentLookupJobIndex < numberLookupJobs) return lookupJobs.get(currentLookupJobIndex++);
		return null;
	}
	
	public synchronized void updateQueryStats(HashSet<String> hits) {
		lookupJobsWithFileHits.addAll(hits);
	}

	public JSONObject getIndexQueryStats() {
		//make json object
		JSONObject stats = new JSONObject();
		stats.put("numberQueries", numberQueries);
		stats.put("numberQueriesThatIntersectDataFilesPreFiltering", lookupJobsWithFileHits.size());
		stats.put("numberIndexLookupJobs", numberLookupJobs);
		stats.put("millSecForFileIntersectionSearch", msTimeToComplete);
		return stats;

	}

}
