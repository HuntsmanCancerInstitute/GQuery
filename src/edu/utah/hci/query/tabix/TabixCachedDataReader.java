package edu.utah.hci.query.tabix;

import htsjdk.tribble.readers.TabixReader;

public class TabixCachedDataReader {
	
	//fields
	TabixReader reader;
	long lastCalled;
	
	public TabixCachedDataReader (TabixReader reader){
		this.reader = reader;
		lastCalled = System.currentTimeMillis();
	}
	
}
