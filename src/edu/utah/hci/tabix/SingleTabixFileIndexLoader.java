package edu.utah.hci.tabix;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.utah.hci.misc.Util;
import htsjdk.tribble.readers.TabixReader;

public class SingleTabixFileIndexLoader implements Runnable{

	//fields
	private boolean failed = false;
	private TabixFileRegionIntersector intersector;
	private TabixIndexFileLookupJob tc = null;
	private File tabixFile = new File("");
	private TabixReader reader = null;
	private HashSet<Integer> fileIds = new HashSet<Integer>();
	private TabixDataQuery tq = null;
	private HashSet<String> numberLookupJobsWithFileHits = new HashSet<String>();
	
	private static final Logger lg = LogManager.getLogger(SingleTabixFileIndexLoader.class);
	

	
	public SingleTabixFileIndexLoader (TabixFileRegionIntersector intersector ) throws IOException{
		this.intersector = intersector;	
	}
	
	public void run() {	
		try {
			//get next TabixIndexFileLookupJob, returns null if no more work
			while ((tc = intersector.getTabixLookupJob()) != null){ 
				tq = tc.getTabixDataQuery();
				
				//Different index file? Create new TabixReader?
				File indexFile = tc.getIndexFile();
				if (indexFile.equals(tabixFile) == false) {
					tabixFile = indexFile;
					if (reader != null) reader.close();
					reader = new TabixReader(tabixFile.toString());
				}

				fileIds.clear();
				
				//fetch iterator, returns null if nothing present
				TabixReader.Iterator it = fetchIterator();
				if (it == null) continue;

				//parse hits
				String[] intString = null;
				String hitString = null;
				while ((hitString = it.next()) != null) {
					String[] t = Util.TAB.split(hitString);
					intString = Util.COMMA.split(t[3]);
					for (String fileId : intString) fileIds.add(Integer.parseInt(fileId));
				}
				//add hits to the MultiTabixDataQuery
				int numFileIds = fileIds.size();

				if (numFileIds !=0) {
					File[] fileIndex = tc.getFileId2File();
					File[] files = new File[numFileIds];
					int counter =0;
					for (Integer idToFetch: fileIds) files[counter++] = fileIndex[idToFetch];
					tq.addIntersectingFiles(files);
					numberLookupJobsWithFileHits.add(tq.getInput());
				}
			}

			//update the query stats for all the processed jobs
			intersector.updateQueryStats(numberLookupJobsWithFileHits);

		} catch (Exception e) {
			failed = true;
			lg.error("Error: searching tabix file index "+tabixFile+" for "+tq.getTabixCoordinates() +"\n"+e.toString()+"\n"+e.fillInStackTrace());
			System.err.println("\nTabix File Index Loader Error Here\n"+e.getStackTrace()+"\n");
		} finally {
			//close the last reader
			if (reader != null) reader.close();
		}
	}
	
	/**Use to try to fetch an iterator.*/
	private TabixReader.Iterator fetchIterator(){
		String coor = tq.getTabixCoordinates();
		TabixReader.Iterator it = null;
		//watch out for no retrieved data error from tabix
		try {
			it = reader.query(coor);
		} catch (ArrayIndexOutOfBoundsException e){}
		return it;
	}

	public boolean isFailed() {
		return failed;
	}
}
