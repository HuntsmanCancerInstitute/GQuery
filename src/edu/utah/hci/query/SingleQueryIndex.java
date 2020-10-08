package edu.utah.hci.query;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.utah.hci.misc.Util;

/**Class for holding info related to a single query index.*/
public class SingleQueryIndex {

	private SingleDataSources dataSources;
	private File[] fileId2File = null;
	private HashMap<String, File> chrTabixTree = null;
	private static final Logger lg = LogManager.getLogger(SingleQueryIndex.class);
	
	//constructor
	public SingleQueryIndex(SingleQuery query) throws IOException{
		
		//load the file id hash, truncated file name with it's id
		HashMap<String, Integer> fileStringId = loadFileInfo(query);
		
		buildTabixSearchTree(query.getIndexDir());
		
		//create a container to hold all the File info for per request filtering
		dataSources = new SingleDataSources(query.getDataDir());
		
		//for each file
		int max = findMaxIntValue (fileStringId);		
		fileId2File = new File[max+1];
		File dataDirParent = query.getMasterQuery().getDataDir().getParentFile();	
		
		for (String path: fileStringId.keySet()){		
			
			File f = new File (dataDirParent, path);
			//does it exist?
			if (f.exists() == false){
				String m = "Failed to find this data file despite presence in index, aborting! "+f;
				lg.fatal(m);
				throw new IOException (m);
			}
			int id = fileStringId.get(path);
			
			fileId2File[id] = f;
			dataSources.addFileToFilter(f);
		}
		
		//trim the FileNames
		dataSources.trimFileNames();
		
		//set in Query
		query.setDataSources(dataSources);
	}
	
	private HashMap<String, Integer> loadFileInfo(SingleQuery query) throws IOException {
		File info = new File(query.getIndexDir(), "fileInfo.txt.gz");
		if (info.exists() == false){
			String m = "ERROR: cannot find the fileInfo.txt.gz file in the indexDir "+query.getIndexDir()+", aborting.";
			lg.fatal(m);
			throw new IOException (m);
		}
		
		HashMap<String, Integer> fileStringId = new HashMap<String, Integer>();
		
		BufferedReader in = Util.fetchBufferedReader(info);
		//skip first header line
		in.readLine();
		String line;
		String[] fields;
		while ((line = in.readLine()) != null) {
			//Id0 Size1 LastMod2 Name3
			fields = Util.TAB.split(line);
			fileStringId.put(fields[3], Integer.parseInt(fields[0]));
		}
		in.close();
		
		return fileStringId;
	}

	/**Find the maxium int value in the hashmap*/
	public static int findMaxIntValue(HashMap<String, Integer> hash) {
		int max = 0;
		for (Integer i: hash.values()) {
			if (i> max) max = i;
		}
		return max;
	}
	
	/**This builds lookups using tabix .*/
	private void buildTabixSearchTree(File indexDir) {
		try {
			File[] chrBed = Util.extractFiles(indexDir, ".qi.bed.gz");
			chrTabixTree = new HashMap<String, File>();
			//for each file
			for (File f: chrBed){
				//look for tbi's
				File tbi = new File(f.getParentFile(), f.getName()+".tbi");
				if (tbi.exists() == false) throw new IOException("Failed to find the tabix xxx.tbi index for "+f);
				//save chrom, tabix file
				String chr = f.getName().replace(".qi.bed.gz", "");
				chrTabixTree.put(chr, f);
			}
		} catch (IOException e) {
			e.printStackTrace();
			Util.printErrAndExit("\nERROR: problem bulding tabix file search tree from the bed files, aborting.\n");
		}
	}

	/**Given a String of ints delimited by something, will parse or return null.*/
	public static int[] stringToInts(String s, Pattern pat){
		String[] tokens = pat.split(s);
		int[] num = new int[tokens.length];
		try {
			for (int i=0; i< tokens.length; i++){
				num[i] = Integer.parseInt(tokens[i]);
			}
			return num;
		} catch (Exception e){
			return null;
		}
	}

	public HashMap<String, File> getChrTabixTree() {
		return chrTabixTree;
	}

	public File[] getFileId2File() {
		return fileId2File;
	}

}
