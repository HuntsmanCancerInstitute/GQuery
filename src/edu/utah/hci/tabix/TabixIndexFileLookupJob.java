package edu.utah.hci.tabix;

import java.io.File;

public class TabixIndexFileLookupJob {
	
	private TabixDataQuery tabixDataQuery = null;
	private File indexFile = null;
	private File[] fileId2File = null;

	public TabixIndexFileLookupJob(File[] fileId2File, File indexFile, TabixDataQuery tabixDataQuery) {
		this.tabixDataQuery = tabixDataQuery;
		this.indexFile = indexFile;
		this.fileId2File = fileId2File;
	}

	public TabixDataQuery getTabixDataQuery() {
		return tabixDataQuery;
	}
	public File getIndexFile() {
		return indexFile;
	}
	public File[] getFileId2File() {
		return fileId2File;
	}

}
