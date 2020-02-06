package edu.utah.hci.query.tabix;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import edu.utah.hci.query.Util;
import htsjdk.tribble.readers.TabixReader;


/**For loading the intersection index based on the users regions of interest and pulling back file index numbers for those that intersect.  Threaded.*/
public class TabixIndexLoader implements Runnable{

	//fields
	private boolean failed = false;
	private TabixReader reader = null;
	private TabixDataQuery tq = null;
	
	private TabixDataQuery[] tabixDataQueries = null;
	private File tabixFile = null;
	
	private static final Logger lg = LogManager.getLogger(TabixIndexLoader.class);
	
	//Constructor
	public TabixIndexLoader () throws IOException{}
	
	public void setQueries(TabixDataQuery[] tabixDataQueries, File tabixIndex) {
		this.tabixDataQueries = tabixDataQueries;
		this.tabixFile = tabixIndex;
	}
	
	//Primary threaded run method
	public void run() {	
		try {
			//make reader, note all of these will be from the same chr so only need one reader
			reader = new TabixReader(tabixFile.toString());
			
			//for each query
			for (TabixDataQuery tdq: tabixDataQueries){
				tq = tdq;
				HashSet<Integer> fileIds = new HashSet<Integer>();
				
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
				//add hits to the query
				if (fileIds.size() !=0) tq.setIntersectingFileIds(fileIds);
			}

		} catch (Exception e) {
			failed = true;
			lg.error("Error: searching "+tabixFile+" for "+tq.getTabixCoordinates() +"\n"+e.toString()+"\n"+e.fillInStackTrace());
			System.err.println("\nTabixErrorHere\n"+e.getStackTrace()+"\n");
		} finally {
			reader.close();
		}
	}
	
	/**Use to try to fetch an iterator.*/
	private TabixReader.Iterator fetchIterator(){
		String coor = tq.getTabixCoordinates();
		TabixReader.Iterator it = null;
		//watch out for no retrieved data error from tabix
		try {
			it = reader.query(coor);
		} catch (ArrayIndexOutOfBoundsException e){
		}
		return it;
	}
	
	public String toString(){
		return "TabixIndexLoader:\n\t"+tabixFile.toString()+"\n\t"+tabixDataQueries.length;
	}
	public boolean isFailed() {
		return failed;
	}


}
