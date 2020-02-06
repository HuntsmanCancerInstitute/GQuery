package edu.utah.hci.query;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

public class DataSources {

	//fields related to filtering files that intersect a particular set of regions
	private File dataDir = null;
	private TreeSet<File> availableDataFiles = new TreeSet<File>();
	private TreeSet<String> skippedDataSourceNames = null;
	private File[] forceFetchDataSources = null;
	private TreeMap<File, String> dataFileDisplayName = new TreeMap<File, String>();
	private TreeMap<String, File> displayNameDataFile = new TreeMap<String, File>();
	private static final Logger lg = LogManager.getLogger(DataSources.class);
	private static final String[] stringFileHeaderPatternStarts = {"^[#/<@].+", "^browser.+", "^track.+", "^color.+", "^url.+", "^Hugo_Symbol.+"};
	private Pattern[] fileHeaderStarts = null;
	
	//constructors
	public DataSources(File dataDir, TreeSet<String> skippedDataSourceNames){
		//set the dir with all the data files
		this.dataDir = dataDir;
		this.skippedDataSourceNames = skippedDataSourceNames;
		//create patterns for parsing file headers
		makeFileHeaderStarts();
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
	
	/**This creates the file : trimmed file name hash and loads the forceFetch File[] if any are present.*/
	public void trimFileNames(){
		Iterator<File> it = availableDataFiles.iterator();
		int indexOfRootParent = dataDir.toString().lastIndexOf("/") +1;
		ArrayList<File> forceFetch = new ArrayList<File>();
		while (it.hasNext()) {
			File f = it.next();
			//trim it and save
			String t = f.toString().substring(indexOfRootParent);
			dataFileDisplayName.put(f, t);
			displayNameDataFile.put(t,f);
			if (skippedDataSourceNames.contains(t)) forceFetch.add(f);
		}
		forceFetchDataSources = new File[forceFetch.size()];
		forceFetch.toArray(forceFetchDataSources);	
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

	public JSONObject getQueryOptions(User user) {
			ArrayList<JSONObject> al = new ArrayList<JSONObject>();
			ArrayList<Boolean> tf = new ArrayList<Boolean>();
			tf.add(true);
			tf.add(false);
			
			//fetchOptions
			JSONObject fo = new JSONObject();
			fo.put("name", "fetchOptions");
			fo.put("description", "Returns this list of options and filters, see https://github.com/HuntsmanCancerInstitute/Query for examples.");
			if (user != null) fo.put("user", user.getUserName());
			al.add(fo);
			
			//bed
			JSONObject bed = new JSONObject();
			bed.put("name", "bed");
			bed.put("description", "Region(s) of interest to query in region format, semicolon delimited. Commas and prepended 'chr' are ignored. ");
			ArrayList<String> bedEx = new ArrayList<String>();
			bedEx.add("chr21:145569-145594"); 
			bedEx.add("21:145,569-145,594"); 
			bedEx.add("21:145569-145594;22:4965784-4965881;X:8594-8599");
			bedEx.add("21\t11058198\t11058237\tMYCInt\t4.3\t-");
			bed.put("examples", bedEx);
			al.add(bed);
			
			//vcf
			JSONObject vcf = new JSONObject();
			vcf.put("name", "vcf");
			vcf.put("description", "Region(s) of interest to query in vcf format, semicolon delimited. Prepended 'chr' are ignored. Watch out "
					+ "for semicolons in the vcf INFO and FORMAT fields.  These will break the input parser.");
			ArrayList<String> vcfEx = new ArrayList<String>();
			vcfEx.add("chr20\t4162847\t.\tC\tT\t.\tPASS\t."); 
			vcfEx.add("20\t4163144\t.\tC\tA\t.\t.\t.;20\t4228734\t.\tC\tCCAAG\t.\tPASS\tAF=0.5"); 
			vcf.put("examples", vcfEx);
			al.add(vcf);

			//fetchData
			JSONObject fd = new JSONObject();
			fd.put("name", "fetchData");
			fd.put("description", "Pull records from disk (slow). Setting to 'force' and providing at least one regEx filter, enables "
					+ "access to the forceFetchDataSources. Use with a very restrictive regEx filter set, ideally on "
					+ "specific named file paths. Force turns the Query web app into a tabix data retrieval utility.");
			ArrayList<String> tff = new ArrayList<String>();
			tff.add("true"); tff.add("false"); tff.add("force");
			fd.put("options", tff);
			fd.put("defaultOption", false);
			al.add(fd);

			//matchVcf
			JSONObject mv = new JSONObject();
			mv.put("name", "matchVcf");
			mv.put("description", "For vcf input queries, require that vcf records match chr, pos, ref, and at least one alt. "
					+ "Will set 'fetchData' = true. Be sure to vt normalize and decompose_blocksub your vcf input, see https://github.com/atks/vt.");
			mv.put("options", tf);
			mv.put("defaultOption", false);
			al.add(mv);

			//includeHeaders
			JSONObject ih = new JSONObject();
			ih.put("name", "includeHeaders");
			ih.put("description", "Return the file headers associated with the intersecting datasets.");
			ih.put("options", tf);
			ih.put("defaultOption", false);
			al.add(ih);

			//regExAll matching
			JSONObject rx = new JSONObject();
			rx.put("name", "regExAll");
			rx.put("description", "Require records to belong to a file whose path matches all of these java regular expressions, "
					+ "semicolon delimited. Note, a .* is added to both ends of each regEx.");
			ArrayList<String> rxEx = new ArrayList<String>();
			rxEx.add("/B37/"); 
			rxEx.add("\\.vcf\\.gz"); 
			rx.put("examples", rxEx);
			al.add(rx);
			
			//or regex modifier
			JSONObject rxo = new JSONObject();
			rxo.put("name", "regExOne");
			rxo.put("description", "Require records to belong to a file whose path matches at least one of these java regular expressions, "
					+ "semicolon delimited. Note, a .* is added to both ends of each regEx.");
			ArrayList<String> rxoEx = new ArrayList<String>();
			rxoEx.add("\\.vcf\\.gz;\\.maf\\.txt\\.gz");  
			rxo.put("examples", rxoEx);
			al.add(rxo);
			
			//regExAllData matching
			JSONObject rxd = new JSONObject();
			rxd.put("name", "regExAllData");
			rxd.put("description", "Require each record to match all of the provided java regular expressions, "
					+ "semicolon delimited. Note, a .* is added to both ends of each regEx. Will set 'fetchData' = true. Case insensitive.");
			ArrayList<String> rxExD = new ArrayList<String>();
			rxExD.add("Pathogenic"); 
			rxExD.add("LOF"); 
			rxd.put("examples", rxExD);
			al.add(rxd);
			
			//regExAllData matching
			JSONObject rod = new JSONObject();
			rod.put("name", "regExOneData");
			rod.put("description", "Require each record to match at least one of the provided java regular expressions, "
					+ "semicolon delimited. Note, a .* is added to both ends of each regEx. Will set 'fetchData' = true. Case insensitive.");
			ArrayList<String> rxExx = new ArrayList<String>();
			rxExx.add("Pathogenic"); 
			rxExx.add("Uncertain;Benign"); 
			rod.put("examples", rxExx);
			al.add(rod);
			


			ArrayList<String>[] fs = fetchDataSources (user);
			
			//dataSources for lookup and retrieval
			JSONObject ds = new JSONObject();
			ds.put("name", "dataSources");
			ds.put("description", "Data sources available for interval tree intersection and tabix data retrieval.");
			ds.put("options", fs[0]);
			al.add(ds);
			
			//dataSources just available for retrieval with fetchData=force
			if (fs[1].size() != 0) {
				JSONObject fds = new JSONObject();
				fds.put("name", "forceFetchDataSources");
				fds.put("description", "Data sources only available for tabix data retrieval. No initial interval tree lookup is performed. To access, "
						+ "set fetchData=force and provide one or more regEx file path filters.");
				fds.put("options", fs[1]);
				al.add(fds);
			}

			JSONObject queryOptions = new JSONObject();
			queryOptions.put("queryOptionsAndFilters", al);
		
			return queryOptions;
	}
	
	/**Filters the dataSources based on the Users permitted regExOne patterns.*/
	private ArrayList<String>[] fetchDataSources(User user) {
		ArrayList<String> standard = new ArrayList<String>();
		ArrayList<String> skipped = new ArrayList<String>();
		try {
			//user null?
			if (user == null){
				for (String t : dataFileDisplayName.values()) {
					if (skippedDataSourceNames.contains(t) ) skipped.add(t);
					else standard.add(t);
				}
			}
			//nope so filter on the users patterns to make sure they can see it
			else {
				Pattern[] permitted = user.getRegExOne();
				Matcher mat;
				for (String t : dataFileDisplayName.values()) {
					String fullPath = displayNameDataFile.get(t).getCanonicalPath();
					for (Pattern p : permitted) {
						mat = p.matcher(fullPath);
						if (mat.matches()){
							if (skippedDataSourceNames.contains(t) ) skipped.add(t);
							else standard.add(t);
							break;
						}
					}
				}
			}
		} catch (IOException e) {
			lg.fatal("Problem fetching the canonical path for user file access\n"+Util.getStackTrace(e));
		}
		return new ArrayList[]{standard, skipped};
	}
	
	public void makeFileHeaderStarts() {
		fileHeaderStarts = new Pattern[stringFileHeaderPatternStarts.length];
		for (int i =0; i< stringFileHeaderPatternStarts.length; i++) fileHeaderStarts[i] = Pattern.compile(stringFileHeaderPatternStarts[i]); 
	}
	
	/**Extracts the file header as defined by the file header patterns. zip and gzip ok.
	 * @throws IOException */
	public JSONObject fetchFileHeader(File f) throws IOException {
		ArrayList<String> header = new ArrayList<String>();
		BufferedReader in = Util.fetchBufferedReader(f);
		String line;
		while ((line = in.readLine()) != null){
			//skip blanks
			line = line.trim();
			if (line.length()==0) continue;
			//scan patterns
			boolean found = false;
			for (Pattern p: fileHeaderStarts) {
				Matcher m = p.matcher(line);
				if (m.matches()) {
					header.add(line);
					found = true;
					break;
				}
			}
			if (found == false) break;
		}
		in.close();
		
		//make Json object
		JSONObject jo = new JSONObject();
		jo.put("source", dataFileDisplayName.get(f));
		jo.put("header", header);
		return jo;
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

	public TreeSet<String> getSkippedDataSourceNames() {
		return skippedDataSourceNames;
	}

	public TreeMap<String, File> getDisplayNameDataFile() {
		return displayNameDataFile;
	}

	public File[] getForceFetchDataSources() {
		return forceFetchDataSources;
	}
}
