package edu.utah.hci.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import edu.utah.hci.index.GQueryIndexer;
import edu.utah.hci.query.Util;
import static org.junit.Assert.*;

/**Be sure to turn off authentication by modifying the web.xml and restarting tomcat.*/
public class GQueryIndexerTests {
	
	/**Modify this url to match your running instance*/
	private String url = new String("http://localhost:8080/Query/search");
	
	//This resource dir should work provided you haven't moved the directories around
	private File testResourceDir = new File (System.getProperty("user.dir")+"/TestResources");
	private static final Logger lg = LogManager.getLogger(GQueryIndexerTests.class);
	
	@Test
	public void indexingTest() throws Exception{
		
			//Create a test index folder
			File testIndexFolder = new File(testResourceDir, "TestIndex");
			if (testIndexFolder.exists()) Util.deleteDirectory(testIndexFolder);
			testIndexFolder.mkdirs();
			assertTrue(testIndexFolder.exists());
			
			//Chrom File
			File chromFile = new File(testResourceDir, "b37Chr20-21ChromLen.bed");
			assertTrue(chromFile.canRead());
			
			//Data dir
			File dataDir = new File(testResourceDir, "Data");
			assertTrue(dataDir.exists());
			
			//Htslib
			File htslibDir = new File(testResourceDir, "Htslib_1.10.2/bin");
			assertTrue(htslibDir.exists());
			
			//Skip dir
			File skipDir = new File(dataDir, "/B37/GVCFs");
			assertTrue(skipDir.exists());
			
			//Create cmd
			String[] cmd = {
					"-c", chromFile.toString(),
					"-d", dataDir.toString(),
					"-t", htslibDir.toString(),
					"-i", testIndexFolder.toString(),
					"-s", skipDir.toString()
			};
			
			//Create a GQueryIndexer instance, this fires the indexing too
			GQueryIndexer gqi = new GQueryIndexer(cmd);
			
			//Correct number of data files?
			assertTrue(gqi.getDataFilesToParse().length == 11);
			
			//Correct number of parsed records?
			assertTrue(gqi.getTotalRecordsProcessed() == 26611);
			
			//Correct number of TestIndex files?
			File[] indexFiles = Util.extractFiles(testIndexFolder);
			assertTrue(indexFiles.length == 9);
			
			//Compare sorted bed files
			File realIndex = new File(testResourceDir,"Index");
			File[] oldBedGz = Util.extractFiles(realIndex, ".bed.gz");
			File[] newBedGz = Util.extractFiles(testIndexFolder, ".bed.gz");
			assertTrue(oldBedGz.length == 3);
			assertTrue(newBedGz.length == 3);
			for (int i=0; i< oldBedGz.length; i++) {
				String oldMd5 = md5CheckSum(oldBedGz[i]);
				String newMd5 = md5CheckSum(newBedGz[i]);
				//Util.pl(oldBedGz[i].getName()+" "+oldMd5);
				//Util.pl(newBedGz[i].getName()+" "+newMd5);
				assertTrue(oldMd5+" "+newMd5, oldMd5.equals(newMd5));
			}
			
			//cleanup
			Util.deleteDirectory(testIndexFolder);
			
	}
	
	public static String md5CheckSum(File f) {
		String md5 = "";
		try {
			InputStream is = new FileInputStream(f);
			md5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(is);
			is.close();
		} catch (Exception e) {
			e.printStackTrace();
		} 
		return md5;

	}


}
