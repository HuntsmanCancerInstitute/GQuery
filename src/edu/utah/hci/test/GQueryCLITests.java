package edu.utah.hci.test;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import edu.utah.hci.apps.GQueryCLI;
import edu.utah.hci.misc.Util;

import static org.junit.Assert.*;

/**Be sure to turn off authentication by modifying the web.xml and restarting tomcat.*/
public class GQueryCLITests {
	
	/**
	
	# Set up the indexed data dir, change the paths to match your install

	rm -rf /Users/u0028003/Code/GQuery/TestResources/TempFiles/Data
	cp -r /Users/u0028003/Code/GQuery/TestResources/Data  \
	   /Users/u0028003/Code/GQuery/TestResources/TempFiles/Data

	java -jar -Xmx10G /Users/u0028003/Code/GQuery/target/GQueryIndexer.jar \
	   -c /Users/u0028003/Code/GQuery/TestResources/b37Chr20-21ChromLen.bed \
	   -d /Users/u0028003/Code/GQuery/TestResources/TempFiles/Data \
	   -t /Users/u0028003/Code/GQuery/TestResources/Htslib_1.10.2/bin/

	# Final output: '0.1 Min to index 11 files containing 27,611 records'
	
	*/
	
	//Defile the paths to the test resources
	private File testResourceDir = new File (System.getProperty("user.dir")+"/TestResources");
	private File tempDir = new File (testResourceDir, "TempFiles");
	private File dataDir = new File (testResourceDir, "TempFiles/Data");
	private File jsonDir = new File (testResourceDir, "Json/GQueryCLI");
	
	@Test
	public void optionsTest() throws Exception{
		File newJsonOutput = new File(tempDir, "optionsTest.json");
		
		//Create cmd, -g /Users/u0028003/Code/GQuery/TestResources/TempFiles/Data -o -s optionsTest.json
		String[] cmd = {
				"-g", dataDir.toString(),
				"-s", newJsonOutput.toString(),
				"-o"
		};
		//Execute the search
		new GQueryCLI(cmd);
		
		//Compare json outputs, should be same line count
		File oldJsonOutput = new File (jsonDir, "optionsTest.json");
		long numNew = Util.countNonBlankLines(newJsonOutput);
		long numOld = Util.countNonBlankLines(oldJsonOutput);
		
		assertTrue(numNew+" line count new vs old "+numOld, numNew == numOld);	
	}
	
	@Test
	public void regionTest() throws Exception{
		File newJsonOutput = new File(tempDir, "regionTest.json");
		//Create cmd
		String[] cmd = {
				"-g", dataDir.toString(),
				"-s", newJsonOutput.toString(),
				"-r", "chr20:257584-257886;chr22:39441112-39441312"
		};
		//Execute the search
		new GQueryCLI(cmd);
		
		//Compare json outputs, should be same line count
		File oldJsonOutput = new File (jsonDir, "regionTest.json");
		long numNew = Util.countNonBlankLines(newJsonOutput);
		long numOld = Util.countNonBlankLines(oldJsonOutput);
		assertTrue(numNew+" line count new vs old "+numOld, numNew == numOld);	
		
		//load jsons
		JSONObject oldJo = Util.loadJsonFile(oldJsonOutput);
		JSONObject newJo = Util.loadJsonFile(newJsonOutput);
		
		//check fileIndexQueryStats
		HashMap<String, Integer> oldI = loadFileIndexQueryStats(oldJo.getJSONObject("fileIndexQueryStats"));
		HashMap<String, Integer> newI = loadFileIndexQueryStats(newJo.getJSONObject("fileIndexQueryStats"));
		assertTrue(oldI+ " <- old fileIndexQueryStats don't match new -> "+newI, compareHashMaps(oldI, newI));
		
		//check sources
		HashSet<String> oldSources = loadAllSources(oldJo.getJSONArray("queryResults"));
		HashSet<String> newSources = loadAllSources(newJo.getJSONArray("queryResults"));
		assertTrue(oldSources+ " <- old sources don't match new sources -> "+newSources, compareHashSets(oldSources, newSources));
	}
	
	@Test
	public void vcfTest() throws Exception{
		File newJsonOutput = new File(tempDir, "vcfTest.json");
		//Create cmd
		String[] cmd = {
				"-g", dataDir.toString(),
				"-s", newJsonOutput.toString(),
				"-v", "22_39358173_rs61743667_T_G"
		};
		//Execute the search
		new GQueryCLI(cmd);
		
		//Compare json outputs, should be same line count
		File oldJsonOutput = new File (jsonDir, "vcfTest.json");
		long numNew = Util.countNonBlankLines(newJsonOutput);
		long numOld = Util.countNonBlankLines(oldJsonOutput);
		assertTrue(numNew+" line count new vs old "+numOld, numNew == numOld);	
		
		//load jsons
		JSONObject oldJo = Util.loadJsonFile(oldJsonOutput);
		JSONObject newJo = Util.loadJsonFile(newJsonOutput);
		
		//check fileIndexQueryStats
		HashMap<String, Integer> oldI = loadFileIndexQueryStats(oldJo.getJSONObject("fileIndexQueryStats"));
		HashMap<String, Integer> newI = loadFileIndexQueryStats(newJo.getJSONObject("fileIndexQueryStats"));
		assertTrue(oldI+ " <- old fileIndexQueryStats don't match new -> "+newI, compareHashMaps(oldI, newI));
		
		//check sources
		HashSet<String> oldSources = loadAllSources(oldJo.getJSONArray("queryResults"));
		HashSet<String> newSources = loadAllSources(newJo.getJSONArray("queryResults"));
		assertTrue(oldSources+ " <- old sources don't match new sources -> "+newSources, compareHashSets(oldSources, newSources));

	}
	
	@Test
	public void vcfPadTest() throws Exception{
		File newJsonOutput = new File(tempDir, "vcfPadTest.json");
		//Create cmd
		String[] cmd = {
				"-g", dataDir.toString(),
				"-s", newJsonOutput.toString(),
				"-v", "22_39358173_rs61743667_T_G",
				"-a", "10000"
		};
		//Execute the search
		new GQueryCLI(cmd);
		
		//Compare json outputs, should be same line count
		File oldJsonOutput = new File (jsonDir, "vcfPadTest.json");
		long numNew = Util.countNonBlankLines(newJsonOutput);
		long numOld = Util.countNonBlankLines(oldJsonOutput);
		assertTrue(numNew+" line count new vs old "+numOld, numNew == numOld);	
		
		//load jsons
		JSONObject oldJo = Util.loadJsonFile(oldJsonOutput);
		JSONObject newJo = Util.loadJsonFile(newJsonOutput);
		
		//check fileIndexQueryStats
		HashMap<String, Integer> oldI = loadFileIndexQueryStats(oldJo.getJSONObject("fileIndexQueryStats"));
		HashMap<String, Integer> newI = loadFileIndexQueryStats(newJo.getJSONObject("fileIndexQueryStats"));
		assertTrue(oldI+ " <- old fileIndexQueryStats don't match new -> "+newI, compareHashMaps(oldI, newI));
		
		//check sources
		HashSet<String> oldSources = loadAllSources(oldJo.getJSONArray("queryResults"));
		HashSet<String> newSources = loadAllSources(newJo.getJSONArray("queryResults"));
		assertTrue(oldSources+ " <- old sources don't match new sources -> "+newSources, compareHashSets(oldSources, newSources));
	}
	
	@Test
	public void vcfFileTest() throws Exception{
		File newJsonOutput = new File(tempDir, "vcfFileTest.json");
		//Create cmd
		String[] cmd = {
				"-g", dataDir.toString(),
				"-s", newJsonOutput.toString(),
				"-f", new File (testResourceDir, "b37Test.vcf").toString()
		};
		//Execute the search
		new GQueryCLI(cmd);
		
		//Compare json outputs, should be same line count
		File oldJsonOutput = new File (jsonDir, "vcfFileTest.json");
		long numNew = Util.countNonBlankLines(newJsonOutput);
		long numOld = Util.countNonBlankLines(oldJsonOutput);
		assertTrue(numNew+" line count new vs old "+numOld, numNew == numOld);	
		
		//load jsons
		JSONObject oldJo = Util.loadJsonFile(oldJsonOutput);
		JSONObject newJo = Util.loadJsonFile(newJsonOutput);
		
		//check fileIndexQueryStats
		HashMap<String, Integer> oldI = loadFileIndexQueryStats(oldJo.getJSONObject("fileIndexQueryStats"));
		HashMap<String, Integer> newI = loadFileIndexQueryStats(newJo.getJSONObject("fileIndexQueryStats"));
		assertTrue(oldI+ " <- old fileIndexQueryStats don't match new -> "+newI, compareHashMaps(oldI, newI));
		
		//check sources
		HashSet<String> oldSources = loadAllSources(oldJo.getJSONArray("queryResults"));
		HashSet<String> newSources = loadAllSources(newJo.getJSONArray("queryResults"));
		assertTrue(oldSources+ " <- old sources don't match new sources -> "+newSources, compareHashSets(oldSources, newSources));
	}
	
	@Test
	public void bedFileTest() throws Exception{
		File newJsonOutput = new File(tempDir, "bedFileTest.json");
		//Create cmd
		String[] cmd = {
				"-g", dataDir.toString(),
				"-s", newJsonOutput.toString(),
				"-f", new File (testResourceDir, "b37Test.bed").toString()
		};
		//Execute the search
		new GQueryCLI(cmd);
		
		//Compare json outputs, should be same line count
		File oldJsonOutput = new File (jsonDir, "bedFileTest.json");
		long numNew = Util.countNonBlankLines(newJsonOutput);
		long numOld = Util.countNonBlankLines(oldJsonOutput);
		assertTrue(numNew+" line count new vs old "+numOld, numNew == numOld);	
		
		//load jsons
		JSONObject oldJo = Util.loadJsonFile(oldJsonOutput);
		JSONObject newJo = Util.loadJsonFile(newJsonOutput);
		
		//check fileIndexQueryStats
		HashMap<String, Integer> oldI = loadFileIndexQueryStats(oldJo.getJSONObject("fileIndexQueryStats"));
		HashMap<String, Integer> newI = loadFileIndexQueryStats(newJo.getJSONObject("fileIndexQueryStats"));
		assertTrue(oldI+ " <- old fileIndexQueryStats don't match new -> "+newI, compareHashMaps(oldI, newI));
		
		//check sources
		HashSet<String> oldSources = loadAllSources(oldJo.getJSONArray("queryResults"));
		HashSet<String> newSources = loadAllSources(newJo.getJSONArray("queryResults"));
		assertTrue(oldSources+ " <- old sources don't match new sources -> "+newSources, compareHashSets(oldSources, newSources));
	}
	
	@Test
	public void vcfPathTest() throws Exception{
		File newJsonOutput = new File(tempDir, "vcfPathTest.json");
		//Create cmd
		String[] cmd = {
				"-g", dataDir.toString(),
				"-s", newJsonOutput.toString(),
				"-f", new File (testResourceDir, "b37Test.vcf").toString(),
				"-p", "B37/BedData;TCGA/AP"
		};
		//Execute the search
		new GQueryCLI(cmd);
		
		//Compare json outputs, should be same line count
		File oldJsonOutput = new File (jsonDir, "vcfPathTest.json");
		long numNew = Util.countNonBlankLines(newJsonOutput);
		long numOld = Util.countNonBlankLines(oldJsonOutput);
		assertTrue(numNew+" line count new vs old "+numOld, numNew == numOld);	
		
		//load jsons
		JSONObject oldJo = Util.loadJsonFile(oldJsonOutput);
		JSONObject newJo = Util.loadJsonFile(newJsonOutput);
		
		//check fileIndexQueryStats
		HashMap<String, Integer> oldI = loadFileIndexQueryStats(oldJo.getJSONObject("fileIndexQueryStats"));
		HashMap<String, Integer> newI = loadFileIndexQueryStats(newJo.getJSONObject("fileIndexQueryStats"));
		assertTrue(oldI+ " <- old fileIndexQueryStats don't match new -> "+newI, compareHashMaps(oldI, newI));
		
		//check sources
		HashSet<String> oldSources = loadAllSources(oldJo.getJSONArray("queryResults"));
		HashSet<String> newSources = loadAllSources(newJo.getJSONArray("queryResults"));
		assertTrue(oldSources+ " <- old sources don't match new sources -> "+newSources, compareHashSets(oldSources, newSources));
	}
	
	@Test
	public void vcfAllPathTest() throws Exception{
		File newJsonOutput = new File(tempDir, "vcfAllPathTest.json");
		//Create cmd
		String[] cmd = {
				"-g", dataDir.toString(),
				"-s", newJsonOutput.toString(),
				"-f", new File (testResourceDir, "b37Test.vcf").toString(),
				"-p", "B37/;TCGA/AP",
				"-P"
		};
		//Execute the search
		new GQueryCLI(cmd);
		
		//Compare json outputs, should be same line count
		File oldJsonOutput = new File (jsonDir, "vcfAllPathTest.json");
		long numNew = Util.countNonBlankLines(newJsonOutput);
		long numOld = Util.countNonBlankLines(oldJsonOutput);
		assertTrue(numNew+" line count new vs old "+numOld, numNew == numOld);	
		
		//load jsons
		JSONObject oldJo = Util.loadJsonFile(oldJsonOutput);
		JSONObject newJo = Util.loadJsonFile(newJsonOutput);
		
		//check fileIndexQueryStats
		HashMap<String, Integer> oldI = loadFileIndexQueryStats(oldJo.getJSONObject("fileIndexQueryStats"));
		HashMap<String, Integer> newI = loadFileIndexQueryStats(newJo.getJSONObject("fileIndexQueryStats"));
		assertTrue(oldI+ " <- old fileIndexQueryStats don't match new -> "+newI, compareHashMaps(oldI, newI));
		
		//check sources
		HashSet<String> oldSources = loadAllSources(oldJo.getJSONArray("queryResults"));
		HashSet<String> newSources = loadAllSources(newJo.getJSONArray("queryResults"));
		assertTrue(oldSources+ " <- old sources don't match new sources -> "+newSources, compareHashSets(oldSources, newSources));
	}
	
	@Test
	public void vcfNameTest() throws Exception{
		File newJsonOutput = new File(tempDir, "vcfNameTest.json");
		//Create cmd
		String[] cmd = {
				"-g", dataDir.toString(),
				"-s", newJsonOutput.toString(),
				"-f", new File (testResourceDir, "b37Test.vcf").toString(),
				"-n", ".vcf.gz;.maf.txt.gz"
		};
		//Execute the search
		new GQueryCLI(cmd);
		
		//Compare json outputs, should be same line count
		File oldJsonOutput = new File (jsonDir, "vcfNameTest.json");
		long numNew = Util.countNonBlankLines(newJsonOutput);
		long numOld = Util.countNonBlankLines(oldJsonOutput);
		assertTrue(numNew+" line count new vs old "+numOld, numNew == numOld);	
		
		//load jsons
		JSONObject oldJo = Util.loadJsonFile(oldJsonOutput);
		JSONObject newJo = Util.loadJsonFile(newJsonOutput);
		
		//check fileIndexQueryStats
		HashMap<String, Integer> oldI = loadFileIndexQueryStats(oldJo.getJSONObject("fileIndexQueryStats"));
		HashMap<String, Integer> newI = loadFileIndexQueryStats(newJo.getJSONObject("fileIndexQueryStats"));
		assertTrue(oldI+ " <- old fileIndexQueryStats don't match new -> "+newI, compareHashMaps(oldI, newI));
		
		//check sources
		HashSet<String> oldSources = loadAllSources(oldJo.getJSONArray("queryResults"));
		HashSet<String> newSources = loadAllSources(newJo.getJSONArray("queryResults"));
		assertTrue(oldSources+ " <- old sources don't match new sources -> "+newSources, compareHashSets(oldSources, newSources));
	}
	
	@Test
	public void userTest() throws Exception{
		File newJsonOutput = new File(tempDir, "userTest.json");
		//Create cmd
		String[] cmd = {
				"-g", dataDir.toString(),
				"-s", newJsonOutput.toString(),
				"-f", new File (testResourceDir, "b37Test.vcf").toString(),
				"-u", "B37/BedData;TCGA/AP",
				"-w", "TestUser"
		};
		//Execute the search
		new GQueryCLI(cmd);
		
		//Compare json outputs, should be same line count, should produce the same output as the path test
		File oldJsonOutput = new File (jsonDir, "userTest.json");
		long numNew = Util.countNonBlankLines(newJsonOutput);
		long numOld = Util.countNonBlankLines(oldJsonOutput);
		assertTrue(numNew+" line count new vs old "+numOld, numNew == numOld);	
		
		//load jsons
		JSONObject oldJo = Util.loadJsonFile(oldJsonOutput);
		JSONObject newJo = Util.loadJsonFile(newJsonOutput);
		
		//check fileIndexQueryStats
		HashMap<String, Integer> oldI = loadFileIndexQueryStats(oldJo.getJSONObject("fileIndexQueryStats"));
		HashMap<String, Integer> newI = loadFileIndexQueryStats(newJo.getJSONObject("fileIndexQueryStats"));
		assertTrue(oldI+ " <- old fileIndexQueryStats don't match new -> "+newI, compareHashMaps(oldI, newI));
		
		//check sources
		HashSet<String> oldSources = loadAllSources(oldJo.getJSONArray("queryResults"));
		HashSet<String> newSources = loadAllSources(newJo.getJSONArray("queryResults"));
		assertTrue(oldSources+ " <- old sources don't match new sources -> "+newSources, compareHashSets(oldSources, newSources));
	}

	@Test
	public void vcfDataTest() throws Exception{
		File newJsonOutput = new File(tempDir, "vcfDataTest.json");
		//Create cmd
		String[] cmd = {
				"-g", dataDir.toString(),
				"-s", newJsonOutput.toString(),
				"-v", "22_39358173_rs61743667_T_G",
				"-a", "10000",
				"-d"
		};
		//Execute the search
		new GQueryCLI(cmd);
		
		//Compare json outputs, should be same line count
		File oldJsonOutput = new File (jsonDir, "vcfDataTest.json");
		long numNew = Util.countNonBlankLines(newJsonOutput);
		long numOld = Util.countNonBlankLines(oldJsonOutput);
		assertTrue(numNew+" line count new vs old "+numOld, numNew == numOld);	
		
		//load jsons
		JSONObject oldJo = Util.loadJsonFile(oldJsonOutput);
		JSONObject newJo = Util.loadJsonFile(newJsonOutput);
		
		//check fileIndexQueryStats
		HashMap<String, Integer> oldI = loadFileIndexQueryStats(oldJo.getJSONObject("fileIndexQueryStats"));
		HashMap<String, Integer> newI = loadFileIndexQueryStats(newJo.getJSONObject("fileIndexQueryStats"));
		assertTrue(oldI+ " <- old fileIndexQueryStats don't match new -> "+newI, compareHashMaps(oldI, newI));
		
		//check sources
		HashSet<String> oldSources = loadAllSources(oldJo.getJSONArray("queryResults"));
		HashSet<String> newSources = loadAllSources(newJo.getJSONArray("queryResults"));
		assertTrue(oldSources+ " <- old sources don't match new sources -> "+newSources, compareHashSets(oldSources, newSources));
		
		//check data
		HashSet<String> oldData = loadAllSources(oldJo.getJSONArray("queryResults"));
		HashSet<String> newData = loadAllSources(newJo.getJSONArray("queryResults"));
		assertTrue(oldSources+ " <- old data records don't match new data -> "+newSources, compareHashSets(oldData, newData));
	}
	
	@Test
	public void vcfFilteredDataTest() throws Exception{
		File newJsonOutput = new File(tempDir, "vcfFilteredDataTest.json");
		//Create cmd
		String[] cmd = {
				"-g", dataDir.toString(),
				"-s", newJsonOutput.toString(),
				"-v", "22_39358173_rs61743667_T_G",
				"-a", "10000",
				"-d", "-m",
				"-l", "PASS;FOXOG", "-L"
		};
		//Execute the search
		new GQueryCLI(cmd);
		
		//Compare json outputs, should be same line count
		File oldJsonOutput = new File (jsonDir, "vcfFilteredDataTest.json");
		long numNew = Util.countNonBlankLines(newJsonOutput);
		long numOld = Util.countNonBlankLines(oldJsonOutput);
		assertTrue(numNew+" line count new vs old "+numOld, numNew == numOld);	
		
		//load jsons
		JSONObject oldJo = Util.loadJsonFile(oldJsonOutput);
		JSONObject newJo = Util.loadJsonFile(newJsonOutput);
		
		//check fileIndexQueryStats
		HashMap<String, Integer> oldI = loadFileIndexQueryStats(oldJo.getJSONObject("fileIndexQueryStats"));
		HashMap<String, Integer> newI = loadFileIndexQueryStats(newJo.getJSONObject("fileIndexQueryStats"));
		assertTrue(oldI+ " <- old fileIndexQueryStats don't match new -> "+newI, compareHashMaps(oldI, newI));
		
		//check sources
		HashSet<String> oldSources = loadAllSources(oldJo.getJSONArray("queryResults"));
		HashSet<String> newSources = loadAllSources(newJo.getJSONArray("queryResults"));
		assertTrue(oldSources+ " <- old sources don't match new sources -> "+newSources, compareHashSets(oldSources, newSources));
		
		//check data
		HashSet<String> oldData = loadAllSources(oldJo.getJSONArray("queryResults"));
		HashSet<String> newData = loadAllSources(newJo.getJSONArray("queryResults"));
		assertTrue(oldSources+ " <- old data records don't match new data -> "+newSources, compareHashSets(oldData, newData));
	}
	
	
	
	
	
	
	
	
	
	
	public static boolean compareHashSets(HashSet<String> a, HashSet<String> b) {
		if (a.size() != b.size()) return false;
		for (String s: a) if (b.contains(s) == false) return false;
		return true;
	}
	
	public static boolean compareHashMaps(HashMap<String, Integer> a, HashMap<String, Integer> b) {
		if (a.size() != b.size()) return false;
		for (String s: a.keySet()) {
			Integer old = a.get(s);
			Integer n = b.get(s);
			if (old != n) return false;
		}
		return true;
	}

	public static HashSet<String> loadAllSources (JSONArray queryResults){
		HashSet<String> sources = new HashSet<String>();
		int numRes = queryResults.length();
		for (int i=0; i< numRes; i++) {
			JSONObject qr = (JSONObject)queryResults.get(i);
			JSONArray hits = qr.getJSONArray("hits");
			int numHits = hits.length();
			for (int j=0; j< numHits; j++) {
				JSONObject source = (JSONObject)hits.get(j);
				sources.add(source.get("source").toString());
			}
		}
		return sources;
	}
	
	public static HashSet<String> loadAllData (JSONArray queryResults){
		HashSet<String> data = new HashSet<String>();
		int numRes = queryResults.length();
		for (int i=0; i< numRes; i++) {
			JSONObject qr = (JSONObject)queryResults.get(i);
			JSONArray hits = qr.getJSONArray("hits");
			int numHits = hits.length();
			for (int j=0; j< numHits; j++) {
				JSONObject source = (JSONObject)hits.get(j);
				data.add(source.get("data").toString());
			}
		}
		return data;
	}
	
	public static HashMap<String, Integer> loadFileIndexQueryStats (JSONObject jo){
		HashMap<String, Integer> data = new HashMap<String, Integer>();
		data.put("numberQueriesThatIntersectDataFilesPreFiltering", jo.getInt("numberQueriesThatIntersectDataFilesPreFiltering"));
		data.put("numberQueries", jo.getInt("numberQueries"));
		data.put("numberIndexLookupJobs", jo.getInt("numberIndexLookupJobs"));
		return data;
	}

}
