package edu.utah.hci.misc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.servlet.ServletContext;
import javax.ws.rs.core.MultivaluedMap;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.impl.auth.DigestScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import com.sun.jersey.multipart.FormDataBodyPart;

/**Static helper methods.*/
public class Util {
	
	private static final Logger lg = LogManager.getLogger(Util.class);
	public static final Pattern NON_GATCN = Pattern.compile("[^GATCN]", Pattern.CASE_INSENSITIVE);
	public static final Pattern STRAND = Pattern.compile("[+-\\.]");
	public static final Pattern COMMA = Pattern.compile(",");
	public static final Pattern SEMI_COLON = Pattern.compile(";");
	public static final Pattern WHITESPACE = Pattern.compile("\\s+");
	public static final Pattern TAB = Pattern.compile("\t");
	public static final Pattern COLON = Pattern.compile(":");
	public static final Pattern UNDERSCORE = Pattern.compile("_");
	public static final Pattern QUOTE_SINGLE = Pattern.compile("'");
	public static final Pattern QUOTE_DOUBLE = Pattern.compile("\"");
	
	
	/**Attempts to delete a directory and it's contents.*/
	public static void deleteDirectory(File dir){
		if (dir == null || dir.exists() == false) return;
		if (dir.isDirectory()) for (File f: dir.listFiles()) deleteDirectory(f);
		dir.delete();
		if (dir.exists()) deleteDirectoryViaCmdLine(dir);
	}

	public static void deleteDirectoryViaCmdLine(File dir){
		try {
			executeViaProcessBuilder(new String[]{"rm","-rf",dir.getCanonicalPath()}, false);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**Extracts the full path file names of all the files and directories in a given directory. If a file is given it is
	 * returned as the File[0].
	 * Skips files starting with a '.'*/
	public static File[] extractFiles(File directory){
		try{
			directory = directory.getCanonicalFile();
			File[] files = null;	
			String[] fileNames;
			if (directory.isDirectory()){
				fileNames = directory.list();
				int num = fileNames.length;
				ArrayList<File> al = new ArrayList<File>();
				String path = directory.getCanonicalPath();
				for (int i=0; i< num; i++)  {
					if (fileNames[i].startsWith(".") == false) al.add(new File(path, fileNames[i])); 
				}
				//convert arraylist to file[]
				if (al.size() != 0){
					files = new File[al.size()];
					al.toArray(files);
				}
			}
			if (files == null){
				files = new File[1];
				files[0] = directory;
			}
			Arrays.sort(files);
			return files;
		}catch(IOException e){
			System.out.println("Problem extractFiles() "+directory);
			e.printStackTrace();
			return null;
		}
	}
	
	/**Writes a String to disk. */
	public static boolean writeString(String data, File file) {
		try {
			PrintWriter out = new PrintWriter(new FileWriter(file));
			out.print(data);
			out.close();
			return true;
		} catch (IOException e) {
			System.out.println("Problem writing String to disk!");
			e.printStackTrace();
			return false;
		}
	}
	
	/**Uses ProcessBuilder to execute a cmd, combines standard error and standard out into one and returns their output.*/
	public static String[] executeViaProcessBuilder(String[] command, boolean printToStandardOut){
		ArrayList<String> al = new ArrayList<String>();
		try {
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.redirectErrorStream(true);
			Process proc = pb.start();

			BufferedReader data = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String line;
			while ((line = data.readLine()) != null){
				al.add(line);
				if (printToStandardOut) System.out.println(line);
			}
			data.close();
		} catch (Exception e) {
			System.err.println("Problem executing -> "+stringArrayToString(command," "));
			e.printStackTrace();
			return null;
		}
		String[] res = new String[al.size()];
		al.toArray(res);
		return res;
	}
	
	/**Returns a String separated by commas for each bin.*/
	public static String stringArrayToString(String[] s, String separator){
		if (s==null) return "";
		int len = s.length;
		if (len==1) return s[0];
		if (len==0) return "";
		StringBuffer sb = new StringBuffer(s[0]);
		for (int i=1; i<len; i++){
			sb.append(separator);
			sb.append(s[i]);
		}
		return sb.toString();
	}
	
	/**Fetches files that don't start with a '.' from a directory recursing through sub directories.*/
	public static ArrayList<File> fetchAllFilesRecursively (File directory){
		ArrayList<File> files = new ArrayList<File>(); 
		File[] list = directory.listFiles();
		if (list != null){
			for (int i=0; i< list.length; i++){
				if (list[i].isDirectory()) {
					ArrayList<File> al = fetchAllFilesRecursively (list[i]);
					int size = al.size();
					for (int x=0; x< size; x++){
						File test = al.get(x);
						if (test.getName().startsWith(".") == false) files.add(test);
					}
				}
				else if (list[i].getName().startsWith(".") == false) files.add(list[i]);				
			}
		}
		return files;
	}
	
	/**Fetches all files with a given extension in a directory recursing through sub directories.
	 * Will return a file if a file is given with the appropriate extension, or null.*/
	public static File[] fetchFilesRecursively (File directory, String extension){
		if (directory.isDirectory() == false){
			return extractFiles(directory, extension);
		}
		ArrayList<File> al = fetchAllFilesRecursively (directory, extension);
		File[] files = new File[al.size()];
		al.toArray(files);
		return files;
	}
	
	/**Fetches all files with a given extension in a directory recursing through sub directories.*/
	public static ArrayList<File> fetchAllFilesRecursively (File directory, String extension){
		ArrayList<File> files = new ArrayList<File>(); 
		File[] list = directory.listFiles();
		for (int i=0; i< list.length; i++){
			if (list[i].isDirectory()) {
				ArrayList<File> al = fetchAllFilesRecursively (list[i], extension);
				files.addAll(al);
			}
			else{
				if (list[i].getName().endsWith(extension)) files.add(list[i]);
			}
		}
		return files;
	}
	
	/**Fetches all files with a given extension in a directory recursing through sub directories.*/
	public static ArrayList<File> fetchFiles (File directory, String extension){
		ArrayList<File> files = new ArrayList<File>(); 
		File[] list = directory.listFiles();
		for (int i=0; i< list.length; i++){
			if (list[i].getName().endsWith(extension) && list[i].isFile()) files.add(list[i]);
		}
		return files;
	}
	
	/**Fetches directories recursively that end in the extension.*/
	public static ArrayList<File> fetchDirectoriesRecursively (File directory, String extension){
		ArrayList<File> dirs = new ArrayList<File>(); 
		File[] list = directory.listFiles();
		if (list != null){
			for (int i=0; i< list.length; i++){
				if (list[i].isDirectory()) {
					if (list[i].getName().endsWith(extension)) dirs.add(list[i]);
					dirs.addAll(fetchDirectoriesRecursively(list[i], extension));
				}				
			}
		}
		return dirs;
	}
	
	/**Fetches directories recursively that end in the extension.*/
	public static ArrayList<File> fetchNamedDirectoriesRecursively (File directory, String name){
		ArrayList<File> dirs = new ArrayList<File>(); 
		File[] list = directory.listFiles();
		if (list != null){
			for (int i=0; i< list.length; i++){
				if (list[i].isDirectory()) {
					if (list[i].getName().equals(name)) dirs.add(list[i]);
					dirs.addAll(fetchNamedDirectoriesRecursively(list[i], name));
				}				
			}
		}
		return dirs;
	}
	
	/**Fetches all directories recursively*/
	public static ArrayList<File> fetchDirectoriesRecursively (File directory){
		ArrayList<File> dirs = new ArrayList<File>(); 
		File[] list = directory.listFiles();
		if (list != null){
			for (int i=0; i< list.length; i++){
				if (list[i].isDirectory()) {
					dirs.add(list[i]);
					dirs.addAll(fetchDirectoriesRecursively(list[i]));
				}				
			}
		}
		return dirs;
	}
	
	/**Converts a double ddd.dddddddd to sss.s */
	public static String formatNumberOneFraction(double num){
		NumberFormat f = NumberFormat.getNumberInstance();
		f.setMaximumFractionDigits(1);
		return f.format(num);
	}
	
	/**Prints message to screen, then exits.*/
	public static void printErrAndExit (String message){
		System.err.println (message);
		System.exit(1);
	}
	/**Prints message to screen, then exits.*/
	public static void printExit (String message){
		System.out.println (message);
		System.exit(0);
	}
	
	public static void pl(Object obj){
		System.out.println(obj.toString());
	}
	
	public static void p(Object obj){
		System.out.print(obj.toString());
	}
	
	/**Extracts the full path file names of all the files in a given directory with a given extension (ie txt or .txt).
	 * If the dirFile is a file and ends with the extension then it returns a File[] with File[0] the
	 * given directory. Returns null if nothing found. Case insensitive.*/
	public static File[] extractFiles(File dirOrFile, String extension){
		if (dirOrFile == null || dirOrFile.exists() == false) return null;
		File[] files = null;
		Pattern p = Pattern.compile(".*"+extension+"$", Pattern.CASE_INSENSITIVE);
		Matcher m;
		if (dirOrFile.isDirectory()){
			files = dirOrFile.listFiles();
			int num = files.length;
			ArrayList<File> chromFiles = new ArrayList<File>();
			for (int i=0; i< num; i++)  {
				m= p.matcher(files[i].getName());
				if (m.matches()) chromFiles.add(files[i]);
			}
			files = new File[chromFiles.size()];
			chromFiles.toArray(files);
		}
		else{
			m= p.matcher(dirOrFile.getName());
			if (m.matches()) {
				files=new File[1];
				files[0]= dirOrFile;
			}
		}
		if (files != null) Arrays.sort(files);
		return files;
	}
	
	/**Prints a int[] to System.out*/
	public static void printArray(int[] array){
		if (array==null){
			System.out.println("null");
			return;
		}
		int len = array.length;
		for (int i=0; i<len; i++) System.out.print(array[i]+"\t");
		System.out.println();
	}
	
	public static File[] returnFilesWithTabix(File[] tabixFiles) {
		ArrayList<File> goodFiles = new ArrayList<File>();
		for (File tb: tabixFiles){
			File index = new File (tb.toString()+".tbi");
			if (index.exists()) goodFiles.add(tb);
			else lg.warn("WARNING: failed to find a tabix index for "+tb.toString()+", skipping!");
		}
		//any files?
		if (goodFiles.size()==0) return null;
		File[] toReturn = new File[goodFiles.size()];
		goodFiles.toArray(toReturn);
		return toReturn;
	}

	public static boolean saveObject(File file, Object ob) {
		try {
			ObjectOutputStream out =
				new ObjectOutputStream(new FileOutputStream(file));
			out.writeObject(ob);
			out.close();
			return true;
		} catch (Exception e) {
			System.out.println("There appears to be a problem with saving this file: "+ file);
			e.printStackTrace();
		}
		return false;
	}
	
	/**Fetches an Object stored as a serialized file.
	 * Can be zip/gz compressed too.*/
	public static Object fetchObject(File file) {
		Object a = null;
		try {
			ObjectInputStream in;
			ZipFile zf = null;
			if (file.getName().endsWith(".zip")){
				zf = new ZipFile(file);
				ZipEntry ze = (ZipEntry) zf.entries().nextElement();
				in = new ObjectInputStream( zf.getInputStream(ze));
			}
			else if (file.getName().endsWith(".gz")) {
				in = new ObjectInputStream(new GZIPInputStream(new FileInputStream(file)));
			}
			else in = new ObjectInputStream(new FileInputStream(file));
			a = in.readObject();
			in.close();
			if (zf!=null) zf.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Problem fetchObject() "+file);
		}
		return a;
	}
	
	public static boolean saveToFile(InputStream inStream, File target) {
		OutputStream out = null;
		int read = 0;
		byte[] bytes = new byte[1024];
		try {
			out = new FileOutputStream(target);
			while ((read = inStream.read(bytes)) != -1) out.write(bytes, 0, read);
			out.flush();
			out.close();
			return true;
		} catch (Exception e) {
			lg.error("ERROR: failed to save userQueryFile "+target+"\n"+e.toString()+"\n"+e.fillInStackTrace());
		}
		return false;
	}
	
	public static File fetchFile(ServletContext sc, String param, boolean fatalError){
		String s = sc.getInitParameter(param);
		String e = null;
		if (s == null) e = "Failed to parse from the web.xml a param for '"+param + "'";
		else {
			File f = new File(s);
			if (f.exists() == false) e = "Failed to find the '"+param+"' file -> "+s;
			else return f;
		}
		if (fatalError) lg.fatal(e);
		else lg.warn(e);
		return null;
	}
	
	
	
	/**Returns the standard stack trace in String form.*/
	public static String getStackTrace(Exception e){
		StringBuilder sb = new StringBuilder(e.toString());
		for (StackTraceElement ste: e.getStackTrace()) {
			sb.append("\n\tat ");
			sb.append(ste.toString());
		}
		return sb.toString();
	}
	
	/**Returns null if OK or an error message*/
	public static String checkVcf(String[] vcf){
		//CHROM	POS	ID	REF	ALT	
		//  0    1   2   3   4 
		//corr number
		if (vcf.length < 5) return "Too few fields.";
		//parse integer from pos?
		try {
			Integer.parseInt(vcf[1]);
		} catch (Exception e){
			return "Failed to parse an integer from the POS field.";
		}
		//GATCN in REF
		Matcher mat = NON_GATCN.matcher(vcf[3]);
		if (mat.find()) return "Found a non GATC character in the REF field.";
		//all ok
		return null;
	}
	
	/**Returns the amount of memory being used.*/
	public static String memoryUsage(){
		System.gc();
		Runtime rt = Runtime.getRuntime();
		System.gc();
		long availableMB = rt.totalMemory() / 1024 / 1024;
		long usedMB = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
		return "Avail: "+availableMB+"MB, Used: "+usedMB+"MB";
	}
	
	/**Fires a request for the url against a Digest protected realm with the provided user name and plain text password.
	 * Returns null if a the status code isn't 200. Otherwise the body contents.*/
	public static String loadDigestGet(URL url, String userName, String password) throws IOException {
		HttpHost targetHost = new HttpHost(url.getHost(), url.getPort(), url.getProtocol());
		CloseableHttpClient httpClient = HttpClients.createDefault();
		HttpClientContext context = HttpClientContext.create();
		CredentialsProvider credsProvider = new BasicCredentialsProvider();
		credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(userName, password));
		AuthCache authCache = new BasicAuthCache();
		DigestScheme digestScheme = new DigestScheme();
		authCache.put(targetHost, digestScheme);
		context.setCredentialsProvider(credsProvider);
		context.setAuthCache(authCache);
		HttpGet httpget = new HttpGet(url.getPath());

		CloseableHttpResponse response = httpClient.execute(targetHost, httpget, context);
		int statusCode = response.getStatusLine().getStatusCode();
		String res = EntityUtils.toString(response.getEntity());
		response.close();

		if (statusCode == 200) return res;
		else {
			lg.error("Problem loading DigestGet for "+url+" user: "+userName);
			lg.error("StatusLine: "+response.getStatusLine().toString());
			lg.error("Entity: "+res);
			return null;
		}
	}

	/**Executes a get request, returns a json object for info from server or null if something with this method failed.*/
	public static JSONObject get(URIBuilder b){
		HttpURLConnection con = null;
		BufferedReader in = null;
		try {
			//send the request
			URL q = b.build().toURL();
			con = (HttpURLConnection) q.openConnection();
			con.setRequestMethod("GET");
			con.setConnectTimeout(1000000); //17min

			//read results
			int responseCode = con.getResponseCode();

			//good response?
			if (responseCode == 200) {
				//parse json
				JSONTokener t = new JSONTokener(con.getInputStream());
				JSONObject root = new JSONObject(t);
				root.put("responseCode", responseCode);
				root.put("url", b.toString());
				return root;
			}
			//nope!
			else {
				//read error
				in = new BufferedReader(new InputStreamReader(con.getErrorStream()));
				String inputLine;
				StringBuilder sb = new StringBuilder();
				while ((inputLine = in.readLine()) != null) {
					sb.append(inputLine);
					sb.append("\n");
				}
				//return the error
				JSONObject j = new JSONObject();
				j.put("responseCode", responseCode);
				j.put("errorMessage", sb.toString());
				j.put("url", b.toString());
				return j;
			}
		} catch (Exception e){
			e.printStackTrace();
		} finally{
			if (con != null) con.disconnect();
			if (in != null){
				try {
					in.close();
				} catch (IOException e) {}
			}
		}
		return null;
	}
	
	@SuppressWarnings("deprecation")
	public static JSONObject post(File regionsFile, String url, MultipartEntity entity) {
		FileInputStream fis = null;
		DefaultHttpClient httpclient = null;
		try {
			
			//add file to entity
			fis = new FileInputStream(regionsFile);
			entity.addPart("file", new InputStreamBody(fis, regionsFile.getName()));
			
			//create connection and execute post
			httpclient = new DefaultHttpClient(new BasicHttpParams());
			HttpPost httppost = new HttpPost(url);
			httppost.setEntity(entity);
			HttpResponse response = httpclient.execute(httppost);
			
			//pull the response
			int statusCode = response.getStatusLine().getStatusCode();
			HttpEntity responseEntity = response.getEntity();
			String responseString = EntityUtils.toString(responseEntity, "UTF-8");
			if (statusCode !=200) {
				lg.error("Response Code : " + statusCode);
				lg.error("Message : "+responseString);
				return null;
			}
			
			JSONObject jo = new JSONObject(responseString);
			jo.put("responseCode", statusCode);
			jo.put("regionsFile", regionsFile.toString());
			return jo;
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (httpclient !=null) httpclient.close();
				if (fis != null) fis.close();
			} catch (IOException e) {}
		}
		return null;
	}

	
	/**Loads a json or json.gz file*/
	public static JSONObject loadJsonFile (File jsonFile){
		BufferedReader in = null;
		JSONObject root = null;
		try {
			in = Util.fetchBufferedReader(jsonFile);

			JSONTokener t = new JSONTokener(in);
			root = new JSONObject(t);

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (in != null)
				try {
					in.close();
				} catch (IOException e) {}
		}
		return root;
	}
	
	/**Uses the value of the key as the key in a hashmap. If null in the JSONObject, silently skips it.*/
	public static HashMap<String, JSONObject> buildHashOnKey(String key, JSONArray ja){
		int size = ja.length();
		HashMap<String, JSONObject> hm = new HashMap<String, JSONObject>(size);
		for (int i=0; i< size; i++){
			JSONObject jo = ja.getJSONObject(i);
			String k = jo.getString(key);
			if (k!=null) hm.put(k, jo);
		}
		return hm;
	}
	
	/**Counts the number of lines in a file skipping blanks.*/
	public static long countNonBlankLines(File file){
		long num =0;
		try {
			BufferedReader in = new BufferedReader(new FileReader(file));
			String line;
			while ((line = in.readLine()) !=null) {
				line = line.trim();
				if (line.length() !=0) num++;
			}
			in.close();
		}
		catch (IOException e){
			System.out.println("\nProblem counting the number of lines in the file: "+file);
			e.printStackTrace();
		}
		return num;
	}
	
	/**Returns a gz zip or straight file reader on the file based on it's extension. Be sure to close it!
	 * @author davidnix*/
	public static BufferedReader fetchBufferedReader( File txtFile) throws IOException{
		BufferedReader in;
		String name = txtFile.getName().toLowerCase();
		if (name.endsWith(".zip")) {
			ZipFile zf = new ZipFile(txtFile);
			ZipEntry ze = (ZipEntry) zf.entries().nextElement();
			in = new BufferedReader(new InputStreamReader(zf.getInputStream(ze)));
		}
		else if (name.endsWith(".gz")) {
			in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(txtFile))));
		}
		else in = new BufferedReader (new FileReader (txtFile));
		return in;
	}
	
	/**Returns null if OK or an error message*/
	public static String checkBed(String[] bed){
		//CHROM	Start Stop name score strand	
		//  0    1     2     3    4      5
		//corr number
		int num = bed.length;
		if (bed.length < 3) return "Too few fields.";
		//parse integer from start and stop?
		int start = 0;
		int stop = 0;
		try {
			//remove any potential commas in the number
			String cStart = COMMA.matcher(bed[1]).replaceAll("");
			String cStop = COMMA.matcher(bed[2]).replaceAll("");
			start = Integer.parseInt(cStart);
			stop = Integer.parseInt(cStop);
		} catch (Exception e){
			return "Failed to parse integers from the start or stop fields.";
		}
		//stop > start
		if (stop < start) return "Stop must be greater than the start.";
			
		//check score?
		if (num >= 5){
			try {
				Double.parseDouble(bed[4]);
			} catch (Exception e){
				return "Failed to parse a double from the score field.";
			}
		}
		//check the strand
		if (num >=6){
			Matcher mat = STRAND.matcher(bed[5]);
			if (mat.matches() == false) return "Failed to parse a +, -, or . from the strand field.";
		}
		//all ok
		return null;
	}

	
	public static File saveUserBedVcfUserInput(HashMap<String, String> options, File tempDir) {
		File toSave = null;
		String[] lines;
		if (options.containsKey("bed")) {
			lines = SEMI_COLON.split(options.get("bed"));
			String uuid = UUID.randomUUID().toString();
			toSave = new File(tempDir, uuid+".bed");
		}
		else if (options.containsKey("vcf")) {
			String[] noH = SEMI_COLON.split(options.get("vcf"));
			lines = new String[noH.length+1];
			lines[0] = "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO";
			int j=0;
			for (int i=1; i< lines.length; i++) lines[i] = noH[j++];
			String uuid = UUID.randomUUID().toString();
			toSave = new File(tempDir, uuid+".vcf");
		}
		else return null;
		
		//write it out
		PrintWriter out = null;
		try {
			 out = new PrintWriter( new FileWriter(toSave));
			 for (String s: lines) out.println(s);
			 return toSave;
		} catch (IOException e) {
			lg.error("Error writing out users GET request regions "+options+" "+toSave);
			lg.error(Util.getStackTrace(e));
			return null;
		} finally {
			if (out != null) out.close();
		}
	}
	
	/**Copies a given directory and it's contents to the destination directory.
	 * Use a extension (e.g. "class$|properties$") to limit the files copied over or set to null for all.*/
	public static boolean copyDirectoryRecursive (File sourceDir, File destDir, String extension){
		Pattern pat = null;
		if(extension != null) pat = Pattern.compile(extension);
		if (destDir.exists() == false) destDir.mkdir();
		//for each file in source copy to destDir		
		File[] files = extractFiles(sourceDir);
		for (int i=0; i< files.length; i++){
			if (files[i].isDirectory()) {
				copyDirectoryRecursive(files[i], new File (destDir, files[i].getName()), extension);
			}
			else {
				if (pat != null){
					Matcher mat = pat.matcher(files[i].getName());
					if (mat.find()){
						File copied = new File (destDir, files[i].getName());					
						if (copyViaFileChannel(files[i], copied) == false ) return false;
					}
				}
				else {
					File copied = new File (destDir, files[i].getName());					
					if (copyViaFileChannel(files[i], copied) == false ) return false;
				}
			}
		}
		return true;
	}
	
	/** Fast & simple file copy. From GForman http://www.experts-exchange.com/M_500026.html
	 * Hit an odd bug with a "Size exceeds Integer.MAX_VALUE" error when copying a vcf file. -Nix.*/
	public static boolean copyViaFileChannel(File source, File dest){
		FileChannel in = null, out = null;
		try {
			in = new FileInputStream(source).getChannel();
			out = new FileOutputStream(dest).getChannel();
			long size = in.size();
			MappedByteBuffer buf = in.map(FileChannel.MapMode.READ_ONLY, 0, size);
			out.write(buf);
			in.close();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	/**Processes the GET params*/
	public static HashMap<String, String> loadGetMultiQueryServiceOptions(HashMap<String, List<String>> lc) {
		lg.debug("Preparsed user GET options: "+lc);
		HashMap<String,String> options = new HashMap<String,String>();
		addTrueFalse("fetchData", lc, options);
		addTrueFalse("matchVcf", lc, options);
		addTrueFalse("includeHeaders", lc, options);
		
		addTrueFalse("matchAllDirPathRegEx", lc, options);
		addTrueFalse("matchAllFileNameRegEx", lc, options);
		addTrueFalse("matchAllDataLineRegEx", lc, options);

		addConcat("regExDirPath", lc, options);
		addConcat("regExFileName", lc, options);
		addConcat("regExDataLine", lc, options);
		addConcat("regExDataLineExclude", lc, options);
		
		addConcat("vcf", lc, options);
		addConcat("key", lc, options);
		addConcat("bed", lc, options);
		addConcat("fetchOptions", lc, options);
		addConcat("vcf", lc, options);
		addConcat("bpPadding", lc, options);
		
		lg.debug("Incoming user GET options: "+options);
		return options;
	}
	
	/**Concats the List<String>, if it exist with a semicolon and puts it into the options.*/
	private static void addConcat(String key, HashMap<String, List<String>> lcInput, HashMap<String, String> options) {
		List<String> val = lcInput.get(key);
		if (val == null || val.size() ==0) return;
		StringBuilder sb = new StringBuilder(val.get(0));
		int size = val.size();
		for (int i=1; i< size; i++){
			sb.append(";");
			sb.append(val.get(i));
		}
		options.put(key, sb.toString());
		
	}

	/**Adds a true or false, if it exist, into the options.*/
	private static void addTrueFalse(String key, HashMap<String, List<String>> lcInput, HashMap<String, String> op2Return) {
		List<String> val = lcInput.get(key);
		if (val == null || val.size() ==0) return;
		String o = val.get(0).toLowerCase();
		if (o.startsWith("t")) op2Return.put(key, "true");
		else if (o.startsWith("f")) op2Return.put(key, "false");
		//do nothing, leave at default
	}

	/**Converts the keys to lower case.*/
	public static HashMap<String, List<String>> convertKeys(MultivaluedMap<String, String> queryParams){
		HashMap<String, List<String>> options = new HashMap<String, List<String>>();
		for (String key : queryParams.keySet()) options.put(key, queryParams.get(key));
		return options;
	}
	
	/**Processes the POST params*/
	public static HashMap<String, String> loadPostQueryServiceOptions(
			String fetchOptions, String fetchData, String matchVcf, String includeHeaders, String bpPadding, 
			List<FormDataBodyPart> regExDirPath, List<FormDataBodyPart> regExFileName, List<FormDataBodyPart> regExDataLine, List<FormDataBodyPart> regExDataLineExclude, 
			String matchAllDirPathRegEx, String matchAllFileNameRegEx, String matchAllDataLineRegEx) {
		
		HashMap<String,String> options = new HashMap<String,String>();
		if (notEmpty(fetchOptions)) options.put("fetchOptions", fetchOptions);
		if (notEmpty(fetchData)) options.put("fetchData", fetchData);
		if (notEmpty(matchVcf)) options.put("matchVcf", matchVcf);
		if (notEmpty(includeHeaders)) options.put("includeHeaders", includeHeaders);
		if (notEmpty(bpPadding)) options.put("bpPadding", bpPadding);
		
		if (regExDirPath!=null && regExDirPath.size()!=0) {
			String rgx = concat(regExDirPath);
			if (rgx!=null) options.put("regExDirPath", rgx);
		}
		if (regExFileName!=null && regExFileName.size()!=0) {
			String rgx = concat(regExFileName);
			if (rgx!=null) options.put("regExFileName", rgx);
		}
		if (regExDataLine!=null && regExDataLine.size()!=0) {
			String rgx = concat(regExDataLine);
			if (rgx!=null) options.put("regExDataLine", rgx);
		}
		if (regExDataLineExclude!=null && regExDataLineExclude.size()!=0) {
			String rgx = concat(regExDataLineExclude);
			if (rgx!=null) options.put("regExDataLineExclude", rgx);
		}
		
		if (notEmpty(matchAllDirPathRegEx)) options.put("matchAllDirPathRegEx", matchAllDirPathRegEx);
		if (notEmpty(matchAllFileNameRegEx)) options.put("matchAllFileNameRegEx", matchAllFileNameRegEx);
		if (notEmpty(matchAllDataLineRegEx)) options.put("matchAllDataLineRegEx", matchAllDataLineRegEx);
		
		lg.debug("Incoming user POST options: "+options);
		return options;
	}
	
	/**Concatenates a List of FormDataBodyPart with semicolons.*/
	public static String concat(List<FormDataBodyPart> l) {
		StringBuilder sb = new StringBuilder();
		int num = l.size();
		for (int x = 0; x< num; x++){
			String v = l.get(x).getValue().trim();
			if (v.length()!=0){
				sb.append(v);
				sb.append(";");
			}
		}
		if (sb.length()==0) return null;
		String toReturn = sb.toString();
		return toReturn.substring(0, toReturn.length()-1);
	}

	/**Checks to see if null, zero length after trimming, or contains the String "null"*/
	public static boolean notEmpty(String param){
		if (param == null) return false;
		if (param.trim().length()==0 ) return false;
		if (param.toLowerCase().contains("null")) return false;
		return true;
	}
	
	public static String fetchStringParam(ServletContext sc, String param){
		String s = sc.getInitParameter(param);
		if (s == null) lg.fatal("ERROR: Failed to parse from the web.xml a param for '"+param + "'");
		return s;
	}
	
	public static int fetchIntParam(ServletContext sc, String param){
		String s = sc.getInitParameter(param);
		if (s == null) lg.fatal("ERROR: Failed to parse from the web.xml a param for '"+param + "'");
		else {
			try {
				int val = Integer.parseInt(s);
				return val;
			} catch (NumberFormatException e) {
				lg.fatal("ERROR: Failed to parse an integer from the web.xml for '"+param + "' "+s);
			}
		}
		return -1;
	}

	/**Parses the following two column, tab delimited file:
	 
	#GroupName	RegEx's, comma delimited, no spaces, where one or more need to match a file's path before returning result from that file to the user
	Public	.+/Data/Public/.+
	Restricted	.+/Data/Restricted/.+
	CvDC	.+/Data/Restricted/CvDC/.+
	PCGC	.+/Data/Restricted/PCGC/.+
	B2BShared	.+/Data/Restricted/CvDC/B2BShared/.+,.+/Data/Restricted/PCGC/B2BShared/.+
			
	#UserName	Groups, comma delimited, no spaces, membership gains access to the file path RegEx's
	JohnDoe	Public
	JoeYost	Public,CvDC,B2BShared
	PeteWhite	Public,PCGC,B2BShared
	B2BAdmin	Public,CvDC,PCGC
	QueryAdmin	Public,Restricted
		
	 * */
	public static HashMap<String, Pattern[]> parseUserGroupRegExPatterns(File userGroupFile) {
		try {
			//parse out the groups and users
			BufferedReader in = fetchBufferedReader(userGroupFile);
			HashMap<String, String> groups = new HashMap<String, String>();
			HashMap<String, String> users = new HashMap<String, String>();
			HashMap<String, String> inUse = null;
			String[] t = null;
			String line = null;
			while ((line = in.readLine()) != null){
				line = line.trim();
				if (line.startsWith("#") || line.length() == 0) continue;
				if (line.startsWith("GroupName")) inUse = groups;
				else if (line.startsWith("UserName")) inUse = users;
				else {
					t = WHITESPACE.split(line);
					if (t.length != 2) throw new IOException ("Failed to parse two columns from "+line);
					inUse.put(t[0], t[1]);
				}
			}
			in.close();
			
			//make hash of group: pattern
			HashMap<String, Pattern[]> groupPat = new HashMap<String, Pattern[]>();
			for (String groupName: groups.keySet()){
				//already exists?
				if (groupPat.containsKey(groupName)) throw new IOException ("This group name is listed twice "+groupName);
				String[] stringPat = COMMA.split(groups.get(groupName));
				Pattern[] patterns = new Pattern[stringPat.length];
				for (int i=0; i< stringPat.length; i++) patterns[i] = Pattern.compile(stringPat[i]);
				groupPat.put(groupName, patterns);
			}
			
			HashMap<String, Pattern[]> userPatterns = new HashMap<String, Pattern[]>();
			//for each user
			for (String userName: users.keySet()){
				//already exists?
				if (userPatterns.containsKey(userName)) throw new IOException ("This user name is listed twice "+userName);
				
				//fetch user groups
				String[] usrGrps = COMMA.split(users.get(userName));
				
				//make hash to collapse any repeat entries
				LinkedHashSet<Pattern> pats = new LinkedHashSet<Pattern>();
				//for each user group
				for (String grpName: usrGrps){
					//fetch the group patterns
					Pattern[] grpPat = groupPat.get(grpName);
					//does it exist?
					if (grpPat == null) throw new IOException ("Failed to find this group "+grpName+ " indicated for user "+userName);
					for (Pattern p: grpPat) pats.add(p);
				}
				
				//convert to []
				Pattern[] uPat = new Pattern[pats.size()];
				pats.toArray(uPat);
				userPatterns.put(userName, uPat);
			}
			
			return userPatterns;
			
		} catch (IOException e) {
			lg.fatal("Error reading user group file "+ userGroupFile);
			lg.fatal(Util.getStackTrace(e));
		}
		return null;
	}
	
	public static void main(String[] args){
		File test = new File("/Users/u0028003/Code/Query/WebContent/WEB-INF/userGroup.txt");
		HashMap<String, Pattern[]> userPatterns = parseUserGroupRegExPatterns(test);
		for (String name: userPatterns.keySet()){
			System.out.println("\n"+name);
			for (Pattern p: userPatterns.get(name)) System.out.println("\t"+p.pattern());
		}
	}

}
