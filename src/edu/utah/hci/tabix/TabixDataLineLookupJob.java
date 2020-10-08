package edu.utah.hci.tabix;

import java.io.File;

public class TabixDataLineLookupJob {
	
	private File dataFile = null;
	private TabixDataQuery tabixDataQuery = null;

	public TabixDataLineLookupJob(File dataFile, TabixDataQuery tabixDataQuery) {
		this.dataFile = dataFile;
		this.tabixDataQuery = tabixDataQuery;
	}

	public File getDataFile() {
		return dataFile;
	}

	public TabixDataQuery getTabixDataQuery() {
		return tabixDataQuery;
	}

}
