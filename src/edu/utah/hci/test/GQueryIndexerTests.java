package edu.utah.hci.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;
import edu.utah.hci.apps.GQueryIndexer;
import edu.utah.hci.misc.Util;
import static org.junit.Assert.*;

/**Be sure to turn off authentication by modifying the web.xml and restarting tomcat.*/
public class GQueryIndexerTests {
	
	//This resource dir should work provided you haven't moved the directories around
	private File testResourceDir = new File (System.getProperty("user.dir")+"/TestResources");
	private static final Logger lg = LogManager.getLogger(GQueryIndexerTests.class);
	
	@Test
	public void indexingTest() throws Exception{

		//Data dir
		File dataDir = new File(testResourceDir, "Data");
		assertTrue(dataDir.exists());

		//Create a test index folder
		File testIndexFolder = new File(testResourceDir, "TestIndexDeleteMe");
		if (testIndexFolder.exists()) Util.deleteDirectory(testIndexFolder);
		testIndexFolder.mkdirs();
		assertTrue(testIndexFolder.exists());


		Util.copyDirectoryRecursive(dataDir, testIndexFolder, null);


		//Chrom File
		File chromFile = new File(testResourceDir, "b37Chr20-21ChromLen.bed");
		assertTrue(chromFile.canRead());



		//Htslib
		File htslibDir = new File(testResourceDir, "Htslib_1.10.2/bin");
		assertTrue(htslibDir.exists());


		//Create cmd
		String[] cmd = {
				"-c", chromFile.toString(),
				"-d", testIndexFolder.toString(),
				"-t", htslibDir.toString()
		};

		//Create a GQueryIndexer instance, this fires the indexing too
		GQueryIndexer gqi = new GQueryIndexer(cmd);

		//Correct number of data files?
		assertTrue(gqi.getTotalFilesIndexed() == 11);

		//Correct number of parsed records?
		assertTrue(gqi.getTotalRecordsProcessed() == 27611);

		//check one of the dirs
		File gi = new File(testResourceDir, "TestIndexDeleteMe/Hg19/.GQueryIndex");
		File[] gif = Util.extractFiles(gi);
		assertTrue(gif.length == 7);

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
