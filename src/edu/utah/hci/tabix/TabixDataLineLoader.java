package edu.utah.hci.tabix;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import edu.utah.hci.query.QueryRequest;

/**Class for coordinating the fetch of data from tabix files*/
public class TabixDataLineLoader {
	
	//fields
	private ArrayList<TabixDataLineLookupJob> lookupJobs = new ArrayList<TabixDataLineLookupJob>();
	private int currentLookupJobIndex = 0;
	private HashSet<String>  queries = new HashSet<String>();
	private HashSet<String> queriesWithData = new HashSet<String>();
	private HashSet<String> queriesWithDataThatPassRegEx = new HashSet<String>();
	private long msTimeToComplete = 0;
	private int numberLookupJobs = 0;

	public TabixDataLineLoader(QueryRequest queryRequest) throws IOException {
		
		long start = System.currentTimeMillis();
		
		//create lookup jobs
		HashMap<File, ArrayList<TabixDataQuery>> fileTabixQueries = queryRequest.getFileTabixQueries();
		for (File f: fileTabixQueries.keySet()) {
			ArrayList<TabixDataQuery> al = fileTabixQueries.get(f);
			for (TabixDataQuery dq: al) {
				lookupJobs.add(new TabixDataLineLookupJob(f, dq));
				queries.add(dq.getInterbaseCoordinates());
			}
		}
		numberLookupJobs = lookupJobs.size();
		
		//create threaded loaders
		int numLoaders = queryRequest.getMasterQuery().getNumberThreads();
		if (numberLookupJobs < numLoaders) numLoaders = numberLookupJobs;
		
		SingleTabixDataLoader[] loader = new SingleTabixDataLoader[numLoaders];
		ExecutorService executor = Executors.newFixedThreadPool(numLoaders);
		
		for (int i=0; i< loader.length; i++){
			loader[i] = new SingleTabixDataLoader(this, queryRequest.getQueryFilter());
			executor.execute(loader[i]);
		}
		executor.shutdown();

		//spins here until the executer is terminated, e.g. all threads complete
		while (!executor.isTerminated()) {}

		//check loaders 
		for (SingleTabixDataLoader c: loader) {
			if (c.isFailed()) throw new IOException("ERROR: TabixLoader issue! \n"+c);
		}
		
		msTimeToComplete = System.currentTimeMillis() - start;
	}

	/**Provides a single lookup job or returns null*/
	public synchronized TabixDataLineLookupJob getTabixLookupJob() {
		if (currentLookupJobIndex < numberLookupJobs) return lookupJobs.get(currentLookupJobIndex++);
		return null;
	}
	
	public synchronized void updateQueryStats(HashSet<String> queriesWithData, HashSet<String> queriesWithDataThatPassRegEx ) {
		this.queriesWithData.addAll(queriesWithData);
		this.queriesWithDataThatPassRegEx.addAll(queriesWithDataThatPassRegEx);
		
	}
	public int getNumberLookupJobs() {
		return numberLookupJobs;
	}
	public long getMsTimeToComplete() {
		return msTimeToComplete;
	}

	public int getNumberQueries() {
		return queries.size();
	}

	public int getNumberQueriesWithData() {
		return queriesWithData.size();
	}

	public int getNumberQueriesWithDataThatPassRegEx() {
		return queriesWithDataThatPassRegEx.size();
	}

}
