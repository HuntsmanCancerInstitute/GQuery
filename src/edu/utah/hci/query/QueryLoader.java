package edu.utah.hci.query;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import edu.utah.hci.query.tabix.TabixDataChunk;
import edu.utah.hci.query.tabix.TabixDataLoader;
import edu.utah.hci.query.tabix.TabixDataQuery;
import htsjdk.tribble.readers.TabixReader;

public class QueryLoader {
	
	private Query query;
	
	/*Container for active TabixReaders*/
	private HashMap<File, TabixReader> tabixReaders = new HashMap<File, TabixReader>();
	private static final Logger lg = LogManager.getLogger(QueryLoader.class);
	private QueryRequest currentQueryRequest;
	
	public QueryLoader(Query query) {
		this.query = query;
	}

	/**Synchronized method for getting or setting a TabixReader.
	 * This is a primitive cache of readers, only one kept per file. Would be better to time these out and permit many readers for big files, e.g. dbSNP
	 * @throws IOException */
	public synchronized TabixReader getSetTabixReader(File tabixFile, TabixReader reader) throws IOException {
		//do they want an active reader?
		if (reader == null) {
			//does it exist?
			if (tabixReaders.containsKey(tabixFile)) return tabixReaders.remove(tabixFile);
			else return new TabixReader(tabixFile.toString());
		}
		//nope, want to put one back, do so if it doesn't exist
		else {
			//save it?
			if (tabixReaders.containsKey(tabixFile) == false) {
				tabixReaders.put(tabixFile, reader);
			}
			//nope, close it
			else reader.close();
			return null;
		}
	}

	/**Method for freeing up the file handles.*/
	public void closeTabixReaders(){
		for (TabixReader tr: tabixReaders.values()) tr.close();
	}
	
	/**This is the method the TabixLoaders use to pull a chunk of data to process.
	 * @return a TabixChunk or null when nothing is left to do. This shuts down the TabixLoader.*/
	public synchronized TabixDataChunk getChunk() throws IOException {
		//at some point will want to queue up many QueryRequest and keep the loaders busy
		//for now all just processing one QueryRequest at a time
		return currentQueryRequest.getChunk();
	}

	/**This takes QueryRequests and loads their TabixQueries with data. */
	public void loadTabixQueriesWithData(QueryRequest qr) throws IOException {
		long startTime = System.currentTimeMillis();
		currentQueryRequest = qr;
		
		//try to make a loader for each chunk
		int numToMake= qr.getTabixChunks().size();
		if (numToMake > query.getNumberThreads()) numToMake = query.getNumberThreads();
		TabixDataLoader[] loader = new TabixDataLoader[numToMake];
		ExecutorService executor = Executors.newFixedThreadPool(numToMake);
		for (int i=0; i< loader.length; i++){
			loader[i] = new TabixDataLoader(this);
			executor.execute(loader[i]);
		}
		executor.shutdown();

		//spins here until the executer is terminated, e.g. all threads complete
		while (!executor.isTerminated()) {}

		//check loaders 
		for (TabixDataLoader c: loader) {
			if (c.isFailed()) throw new IOException("ERROR: TabixLoader issue! \n"+c);
		}
		
		//clean up sources for matchVcf?
		//a hit in the index might not pull any data after filtering for vcf matching, thus need to toss the source hit
		if (currentQueryRequest.getFilter().isMatchVcf()){
			for (TabixDataQuery[] tq : qr.getChrTabixQueries().values()){
				for (int i=0; i< tq.length; i++){
					HashMap<File, ArrayList<String>> hits = tq[i].getSourceResults();
					ArrayList<File> toRemove = new ArrayList<File>();
					for (File f: hits.keySet()){
						int size = hits.get(f).size();
						if (size == 0) toRemove.add(f);
					}
					if (toRemove.size() !=0){
						for (File f: toRemove) hits.remove(f);
					}
				}
			}
		}
		qr.setTime2Load(System.currentTimeMillis() -startTime);
	}

}
