package edu.utah.hci.apps;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataBodyPart;
import com.sun.jersey.multipart.FormDataMultiPart;
import com.sun.jersey.multipart.FormDataParam;
import com.sun.jersey.spi.resource.Singleton;

import edu.utah.hci.misc.Util;
import edu.utah.hci.query.QueryRequest;
import edu.utah.hci.query.User;

import java.security.Key;


@Path("/search")
@Singleton
public class QueryService implements ServletContextListener {

	//fields
	private static final Logger lg = LogManager.getLogger(QueryService.class);
	private static String helpUrl = null;
	private static File tempDir = null;
	private static Query query = null;
	private static HashMap<String, Pattern[]> userRegEx = null;
	private static File keyFile = null;
	private static Key key = null;
	private static int minPerSession = 0;
	private static boolean useIntervalTree = true;
	private static boolean initialized = true;
	private static boolean authorizing = false;

	@GET
	@Produces("application/json")
	public Response processGetRequest(@Context UriInfo ui){
		
		//check to see if the service is running
		if (initialized == false) return Response.status(500).entity("The query service failed initialization, contact admin "+helpUrl).build();
		
		//load options into a hash
		HashMap<String,String> options = Util.loadGetQueryServiceOptions(Util.lowercaseKeys(ui.getQueryParameters()));
		
		//Authenticating? Create a user
		User user = null;
		if (authorizing){
			//get the key, if this comes back null then something is wrong with the authorization
			if (fetchKey() == null) return Response.status(500).entity("The query service failed authorization initialization, contact admin "+helpUrl).build();
			
			//make a user and check
			user = new User(options.get("key"), this);
			if (user.isExpired()) return Response.status(401).entity("Your authentication key has expired, fetch another, if needed contact "+helpUrl).build();
			if (user.getErrorMessage() != null) return Response.status(400).entity("Problem parsing user info, contact "+helpUrl+"\n"+user.getErrorMessage()).build();
		}
		
		//just want options?
		String fo = options.get("fetchoptions");
		if (fo != null && fo.toLowerCase().startsWith("t")) return Response.status(200).entity(query.getDataSources().getQueryOptions(user).toString(3)).build();

		//any bed or vcf regions to query?
		if (options.get("bed") == null && options.get("vcf") == null) return Response.status(400).entity("Invalid request, missing query bed or vcf region(s)? in the input params "+options+", see "+helpUrl).build();

		return processRequest(options, null, user);
	}

	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces("application/json")
	public Response processPostRequest(
			@FormDataParam("file") InputStream uploadedInputStream,
			@FormDataParam("file") FormDataContentDisposition fileDetail,
			@FormDataParam("fetchOptions") String fetchOptions,
			@FormDataParam("fetchData") String fetchData,
			@FormDataParam("matchVcf") String matchVcf,
			@FormDataParam("includeHeaders") String includeHeaders,
			@FormDataParam("regExAll") List<FormDataBodyPart>  regExAll,
			@FormDataParam("regExOne") List<FormDataBodyPart>  regExOne,
			@FormDataParam("regExAllData") List<FormDataBodyPart>  regExAllData,
			@FormDataParam("regExOneData") List<FormDataBodyPart>  regExOneData,
			@FormDataParam("key") String key){

		//check to see if the service is running
		if (query == null) return Response.status(500).entity("\nThe query service failed initialization, contact admin "+helpUrl+ "\n").build();

		//Authenticating? Create a user
		User user = null;
		if (authorizing){
			//get the key, if this comes back null then something is wrong with the authorization
			if (fetchKey() == null) return Response.status(500).entity("The query service failed authorization initialization, contact admin "+helpUrl).build();

			//make a user and check
			user = new User(key, this);
			if (user.isExpired()) return Response.status(401).entity("Your authentication key has expired, fetch another, if needed contact "+helpUrl).build();
			if (user.getErrorMessage() != null) return Response.status(400).entity("Problem parsing user info, if needed contact "+helpUrl+"\n"+user.getErrorMessage()).build();
		}

		//just want the options?
		if (fetchOptions != null && fetchOptions.toLowerCase().startsWith("t") ) {
			return Response.status(200).entity(query.getDataSources().getQueryOptions(user).toString(1)).build();
		}
		
		// check if a file is provided
		if (uploadedInputStream == null || fileDetail == null || fileDetail.getFileName() == null || fileDetail.getFileName().trim().length() == 0) {
			return Response.status(400).entity("Invalid request, missing query file?, see "+helpUrl).build();
		}
		
		// attempt to save user file
		File userQueryFile = new File (tempDir, fileDetail.getFileName());
		if (Util.saveToFile(uploadedInputStream, userQueryFile) == false) return Response.status(500).entity("Failed to save your query file, contact admin, see "+helpUrl).build();
		
		//load options into a hash
		HashMap<String,String> options = Util.loadPostQueryServiceOptions(fetchData, matchVcf, includeHeaders, regExAll, regExOne, regExAllData, regExOneData);

		return processRequest(options, userQueryFile, user);
	}

	private Response processRequest(HashMap<String, String> options, File userQueryFile, User user) {
		try{
			//make a QueryRequest
			QueryRequest qr = new QueryRequest(query, userQueryFile, options, user);
			if (userQueryFile != null) userQueryFile.delete();

			//any errors?
			if (qr.getErrTxtForUser() != null) return Response.status(400).entity("Invalid request, "+qr.getErrTxtForUser()+", contact "+helpUrl+ " if needed").build();

			//return results
			else return Response.status(200).entity(qr.getJsonResults().toString(1)).build();

		} catch (Exception e){
			String st = Util.getStackTrace(e);
			lg.error("Issue encountered with QueryRequest "+userQueryFile+" "+options);
			lg.error(st);
			return Response.status(500).entity("Issue encountered with QueryRequest "+userQueryFile+" "+options +" , contact admin "+helpUrl+ "\n"+st).build();
		}
	}



	public void contextDestroyed(ServletContextEvent arg) {
		lg.info("STOPPING the QueryService...");
		if (query != null) query.getQueryLoader().closeTabixReaders();
	}

	public void contextInitialized(ServletContextEvent arg) {
		lg.info("INITIALIZING the QueryService...");
		
		System.out.println("\nINITIALIZING the QueryService, see log4j output for details.\n");

		//pull from web.xml, these will all write a fatal log4j error and return null if not found
		ServletContext sc = arg.getServletContext();
		tempDir = Util.fetchFile(sc, "tempDir", true);
		if (tempDir != null) tempDir.mkdirs();
		helpUrl = Util.fetchStringParam(sc, "helpUrl");
		useIntervalTree = Util.fetchStringParam(sc, "useIntervalTree").toLowerCase().startsWith("t");
		File path2DataDir = Util.fetchFile(sc, "path2DataDir", true);
		File path2IndexDir = Util.fetchFile(sc, "path2IndexDir", true);
		int numQueriesPerChunk = Util.fetchIntParam(sc, "numQueriesPerChunk");
		String ae = Util.fetchStringParam(sc, "authorizationEnabled");
		
		lg.info("tempDir: "+tempDir);
		lg.info("helpUrl: "+helpUrl);
		lg.info("path2DataDir: "+path2DataDir);
		lg.info("path2IndexDir: "+path2IndexDir);
		lg.info("numQueriesPerChunk: "+numQueriesPerChunk);
		lg.info("useIntervalTree: "+useIntervalTree);
		lg.info("memory: "+Util.memoryUsage());
		lg.info("authorizationEnabled: "+ae);

		if (path2DataDir == null || path2IndexDir == null || tempDir == null || numQueriesPerChunk < 1 || ae == null){
			lg.fatal("ERROR: failed to parse the required params from the web.xml doc, aborting.");
			initialized = false;
			authorizing = false;
			return;
		}

		//clean out temp dir?
		if (lg.isDebugEnabled() == false) for (File f: Util.fetchAllFilesRecursively(tempDir)) f.delete();

		//authorizationEnabled?
		File userGroupFile = Util.fetchFile(sc, "userGroup", false);
		if (ae.toLowerCase().startsWith("f")) {
			lg.info("AUTHENTICATION is NOT required to access any resource!");
			authorizing = false;
			initialized = true;
		}

		else { 
			if (userGroupFile== null) {
				lg.fatal("ERROR: failed to parse the userGroupFile defined in the web.xml doc, aborting. "+userGroupFile);
				initialized = false;
				return;
			}

			//pull user: patterns hash 
			userRegEx = Util.parseUserGroupRegExPatterns(userGroupFile);

			//fetch expiration time
			minPerSession = Util.fetchIntParam(sc, "minPerSession");
			
			//make key file, this might be old or non existent until the QueryAuthorization service is running so don't check it just yet
			keyFile = new File(userGroupFile.getParentFile(), "key.obj");

			//check
			if (minPerSession < 1 || userRegEx == null || userRegEx.get("Guest") == null){
				if (minPerSession <1) lg.fatal("ERROR: failed to initialize QueryService authorization, minPerSession not set in web.xml -> "+minPerSession);
				if (userRegEx == null) lg.fatal("ERROR: failed to initialize QueryService authorization, unable to load userRegEx hash");
				if (userRegEx.get("Guest") == null) lg.fatal("ERROR: failed to initialize QueryService authorization, unable to find a 'Guest' user, please define in the userGroup file -> "+userGroupFile);
				authorizing = false;
				initialized = false;
				return;
			}

			lg.info("minPerSession: "+minPerSession);
			lg.info("userGroupFile: "+userGroupFile);
			lg.info("keyFile: "+keyFile);
			
			authorizing = true;
			initialized = true;
		}
		
		//make query object?
		if (initialized) {
			query = new Query (path2DataDir, path2IndexDir, numQueriesPerChunk, useIntervalTree);
			if (query.isInitialized() == false) {
				lg.fatal("ERROR: failed to initialize Query, aborting.");
				query = null;
				initialized = false;
			}
		}
	}

	/**Loads key serialized object.  If it fails it shuts down the service.*/
	public static Key fetchKey(){
		//does it already exist?
		if (key != null) return key;
		//load it
		key = (Key)Util.fetchObject(keyFile);
		//check
		if (key == null){
			authorizing = false;
			initialized = false;
		}
		return key;
	}



	public static int getMinPerSession() {
		return minPerSession;
	}

	public static HashMap<String, Pattern[]> getUserRegEx() {
		return userRegEx;
	}
}