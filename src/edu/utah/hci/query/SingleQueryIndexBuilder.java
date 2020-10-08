package edu.utah.hci.query;

import java.io.File;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**Builds a SingleQuery from a data dir with a .GQuery index.*/
public class SingleQueryIndexBuilder implements Runnable{

	//fields
	private boolean failed = false;
	private MasterQuery masterQuery;
	private int numCharToSkipForDataDir = 0;

	private File indexDir = null;

	
	private static final Logger lg = LogManager.getLogger(SingleQueryIndexBuilder.class);
	
	public SingleQueryIndexBuilder (MasterQuery masterQuery ) throws IOException{
		this.masterQuery = masterQuery;	
		numCharToSkipForDataDir = masterQuery.getNumCharToSkipForDataDir();
	}
	
	public void run() {	
		try {
			//get next File dir containing a gquery index, returns null if no more work
			while ((indexDir = masterQuery.getQueryIndexJob()) != null){ 
				String trunPath = indexDir.getParent().substring(numCharToSkipForDataDir)+"/";
				lg.info("Building : "+ trunPath);
				
				SingleQuery sq = new SingleQuery(indexDir, masterQuery);
				masterQuery.addAvailableIndexedChromosomes(sq.getQueryIndex().getChrTabixTree().keySet());
				if (sq.isInitialized() == false) throw new IOException("ERROR: failed to load "+indexDir);
				
				masterQuery.addQueryIndex(trunPath, sq, sq.getDataSources().getAvailableDataFiles().size());
			}

		} catch (Exception e) {
			failed = true;
			lg.error("Error: building a query index for  "+indexDir+"\n"+e.fillInStackTrace());
		} 
	}

	public boolean isFailed() {
		return failed;
	}
}
