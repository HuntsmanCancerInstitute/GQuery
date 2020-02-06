package edu.utah.hci.query;

import java.io.File;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/** Tool for intersecting regions with a collection of vcf and bed data. */
public class Query {

	//fields
	private File dataDir = null;
	private File indexDir = null;
	private int numberQueriesInChunk = 1000;
	private int numberThreads = 0;
	private boolean inMemoryIntervalTree = true;

	//internal
	private static final Logger lg = LogManager.getLogger(Query.class);
	private DataSources dataSources;
	private QueryIndex queryIndex;
	private QueryLoader queryLoader;
	private boolean initialized = false;

	//constructors	
	public Query (File dataDir, File indexDir, int numQueriesInChunk, boolean inMemoryIntervalTree) {
		try {
			long startTime = System.currentTimeMillis();
			this.dataDir = dataDir;
			this.indexDir = indexDir;
			this.numberQueriesInChunk = numQueriesInChunk;
			this.inMemoryIntervalTree = inMemoryIntervalTree;
			
			//threads to use
			int numAvail = Runtime.getRuntime().availableProcessors();
			if (numberThreads < 1) numberThreads =  numAvail - 1;
			lg.info(numAvail +" Available processors, using "+numberThreads+" threaded loaders");
			
			//make object that loads the interval trees and can handle tree queries
			queryIndex = new QueryIndex(this);
			
			//make the object to load the results of the queryIndex with the actual data
			queryLoader = new QueryLoader(this);

			//print some stats on building the engine
			lg.info(dataSources.getAvailableDataFiles().size()+" Data sources available for searching");
			lg.info(dataSources.getQueryOptions(null).toString(3));
			String diffTime = Util.formatNumberOneFraction(((double)(System.currentTimeMillis() -startTime))/1000.0/60.0);
			lg.info(diffTime+" min to build, Memory - "+Util.memoryUsage());
			
			initialized = true;
		} catch (Exception e) {
			lg.fatal("ERROR: Problem with Query\n"+Util.getStackTrace(e));
		}
	}

	public int getNumberThreads() {
		return numberThreads;
	}
	public int getNumberQueriesInChunk() {
		return numberQueriesInChunk;
	}
	public DataSources getDataSources() {
		return dataSources;
	}
	public void setDataSources(DataSources dataSources) {
		this.dataSources = dataSources;
	}
	public QueryIndex getQueryIndex() {
		return queryIndex;
	}
	public QueryLoader getQueryLoader() {
		return queryLoader;
	}
	public boolean isInitialized() {
		return initialized;
	}
	public File getDataDir() {
		return dataDir;
	}
	public File getIndexDir() {
		return indexDir;
	}
	public boolean isInMemoryIntervalTree() {
		return inMemoryIntervalTree;
	}



}






