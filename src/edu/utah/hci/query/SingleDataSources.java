package edu.utah.hci.query;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SingleDataSources {

	//fields related to filtering files that intersect a particular set of regions
	private File dataDir = null;
	private TreeSet<File> availableDataFiles = new TreeSet<File>();
	private TreeMap<File, String> dataFileDisplayName = new TreeMap<File, String>();
	private TreeMap<String, File> displayNameDataFile = new TreeMap<String, File>();
	private static final Logger lg = LogManager.getLogger(SingleDataSources.class);

	
	//constructors
	public SingleDataSources(File dataDir){
		//set the dir with all the data files
		this.dataDir = dataDir;

	}
	
	//methods
	void addFileToFilter(File file) throws IOException {
		//add to data sources
		availableDataFiles.add(file);
	}
	
	/**Returns all the data files, full path.*/
	public String fetchDataFiles(String delimiter){
		Iterator<File> it = availableDataFiles.iterator();
		return buildStringFromIterator(it, delimiter);
	}
	
	/**This creates the file : trimmed file name hash.*/
	public void trimFileNames(){
		Iterator<File> it = availableDataFiles.iterator();
		int indexOfRootParent = dataDir.toString().lastIndexOf("/") +1;
		while (it.hasNext()) {
			File f = it.next();
			//trim it and save
			String t = f.toString().substring(indexOfRootParent);
			dataFileDisplayName.put(f, t);
			displayNameDataFile.put(t,f);
		}	
	}
	
	/**Returns all the relative path data files, full path.*/
	public String fetchDataFilesRelative(String delimiter){
		Collection<String> x = dataFileDisplayName.values();
		Iterator<String> it = x.iterator();
		StringBuilder sb = new StringBuilder(it.next());
		while(it.hasNext()){
			sb.append(delimiter);
			sb.append(it.next());
		}
		return sb.toString();
	}

	
	public static String buildStringFromIterator(Iterator<File> it, String delimiter){
		StringBuilder sb = new StringBuilder();
		sb.append(it.next().toString());
		while (it.hasNext()){
			sb.append(delimiter);
			sb.append(it.next().toString());
		}
		return sb.toString();
	}

	//getters and setters
	public TreeSet<File> getAvailableDataFiles() {
		return availableDataFiles;
	}
	public void setAvailableDataFiles(TreeSet<File> availableDataFiles) {
		this.availableDataFiles = availableDataFiles;
	}

	public TreeMap<File, String> getDataFileDisplayName() {
		return dataFileDisplayName;
	}
	public void setDataFileDisplayName(TreeMap<File, String> dataFileDisplayName) {
		this.dataFileDisplayName = dataFileDisplayName;
	}
	public File getDataDir() {
		return dataDir;
	}
	public TreeMap<String, File> getDisplayNameDataFile() {
		return displayNameDataFile;
	}
}
