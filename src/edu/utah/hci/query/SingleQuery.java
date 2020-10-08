package edu.utah.hci.query;

import java.io.File;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.utah.hci.misc.Util;

/** Tool for intersecting regions with a collection of vcf and bed data. */
public class SingleQuery {

	//fields
	private File dataDir = null;
	private File indexDir = null;
	private MasterQuery masterQuery = null;

	//internal
	private static final Logger lg = LogManager.getLogger(SingleQuery.class);
	private SingleDataSources dataSources;
	private SingleQueryIndex queryIndex;
	private boolean initialized = false;

	//constructors	
	public SingleQuery (File indexDir, MasterQuery masterQuery) {
		try {
			this.dataDir = indexDir.getParentFile();
			this.indexDir = indexDir;
			this.masterQuery = masterQuery;
			
			//make object that loads the interval trees and can handle tree queries
			queryIndex = new SingleQueryIndex(this);
			
			initialized = true;
		} catch (Exception e) {
			lg.fatal("ERROR: Problem with Query\n"+Util.getStackTrace(e));
		}
	}

	public SingleDataSources getDataSources() {
		return dataSources;
	}
	public void setDataSources(SingleDataSources dataSources) {
		this.dataSources = dataSources;
	}
	public SingleQueryIndex getQueryIndex() {
		return queryIndex;
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
	public MasterQuery getMasterQuery() {
		return masterQuery;
	}



}






