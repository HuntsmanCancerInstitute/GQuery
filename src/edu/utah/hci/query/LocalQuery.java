package edu.utah.hci.query;

import java.io.File;
import java.util.HashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

/**Object for directly querying a Query API Data/ Index/ via the file system.*/
public class LocalQuery {

	private int numQueriesPerChunk = 1000;
	private static final Logger lg = LogManager.getLogger(LocalQuery.class);
	private Query query = null;

	/*Constructor*/
	public LocalQuery(File path2DataDir, File path2IndexDir, boolean useIntervalTree){
		//build a reusable query object
		query = new Query (path2DataDir, path2IndexDir, numQueriesPerChunk, useIntervalTree);

		if (query.isInitialized() == false) {
			lg.error("ERROR: failed to initialize Query, aborting.");
			System.exit(1);
		}
	}


	public void query(UserQuery userQuery) {

		HashMap<String,String> options = userQuery.fetchQueryOptions();

		try{
			//just wants options?
			String fo = options.get("fetchoptions");
			if (fo != null && fo.toLowerCase().startsWith("t")) userQuery.setResults(fetchOptions());

			else {
				//make a single use QueryRequest object
				QueryRequest qr = new QueryRequest(query, null, options, null);
				//any errors?
				if (qr.getErrTxtForUser() != null) {
					String error = "Invalid request, "+qr.getErrTxtForUser();
					lg.error(error);
					userQuery.setError(error);
				}
				//set results
				else userQuery.setResults(qr.getJsonResults());
			}

		} catch (Exception e){
			String st = "Issue encountered with QueryRequest "+options+"\n"+Util.getStackTrace(e);
			lg.error(st);
			userQuery.setError(st);
		}
	}

	public JSONObject fetchOptions(){
		return query.getDataSources().getQueryOptions(null);
	}


	public static void main(String[] args) {
		if (args.length == 0) System.out.println("For testing, provide: path2DataDir path2IndexDir");
		else {
			LocalQuery lq = new LocalQuery(new File(args[0]), new File(args[1]), false);

			UserQuery uq = new UserQuery();
			uq.fetchOptions();
			lq.query(uq);
			System.out.println("\nFETCHING OPTIONS: \n"+ uq.getResults().toString(3));

			//add a bed region to intersect, need to reset 
			uq = new UserQuery();
			uq.addBedRegion("chr17:43042295-43127483");
			lq.query(uq);
			System.out.println("\nBED REGION SEARCH: \n"+ uq.getResults().toString(3));

			//restrict to vcf files and fetch data
			uq.addRegexAll("vcf.gz").fetchData();

			//filter for records with CLNSIG annotation
			uq.addRegexAllData("CLNSIG");
			lq.query(uq);
			
			System.out.println("\nBED REGION pulling VCF Records: \n"+ uq.getResults().toString(3));

		}
	}

}
