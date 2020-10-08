package edu.utah.hci.apps;

import java.io.File;
import java.security.Key;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.utah.hci.misc.Crypt;
import edu.utah.hci.misc.Util;

/**Returns an encrypted token with the username:timestamp for use by the QueryService
 * Puts it either in the header under Authorization http://localhost:8080/Query/fetchKey or 
 * in the body http://localhost:8080/Query/fetchKey?body 
 * Protect this service with digest authentication or https! Anyone with these tokens can 
 * access particular resources.
 */
@Path("/fetchKey")
public class QueryAuthorization implements ServletContextListener {

	//fields
	private static final Logger lg = LogManager.getLogger(QueryAuthorization.class);
	private static String helpUrl = null;
	private static Key key = null;
	private static File keyFile = null;
	private static HashMap<String, Pattern[]> userPatterns = null;
	private static boolean initialized = false;
	private static boolean authorizing = false;
	private static boolean tokenInHeader = true;

	@GET
	@Produces("text/plain")
	public Response processGetRequest(@Context HttpHeaders headers, @Context UriInfo ui){
		if (initialized == false) return Response.status(500).entity("\nThe Query Authorization service failed initialization, contact admin "+helpUrl+ "\n").build();
		if (authorizing == false) return Response.status(503).entity("\nThe Query Authorization service is not available, no userGroup.txt file provided, contact the admin if you believe this an issue, "+helpUrl+ "\n").build();

Util.pl("AuthoRequestRecieved ");

		//get username
		List<String> authHeaders = headers.getRequestHeader(HttpHeaders.AUTHORIZATION);
		String userName = parseUserNameFromDigestAuthentication(authHeaders);
		if (userName == null) {
			String message ="Failed to parse the user name from the http digest authorization header "+authHeaders; 
			lg.error(message);
			return Response.status(500).entity(message+", contact "+helpUrl).build();
		}

		//is the user in the userPatterns?
		if (userPatterns.containsKey(userName) == false) {
			String message ="Failed to find the userName '"+userName+"' in the userGroup.txt file"; 
			lg.error(message);
			return Response.status(500).entity(message+", contact "+helpUrl).build();
		}

		//build and encrypt the userName:timestamp
		String token = userName+":"+System.currentTimeMillis();
		String encryptedToken = Crypt.encrypt(token, key);
		if (encryptedToken == null) {
			String message ="Failed to encrypt the token for '"+userName+"'"; 
			lg.error(message);
			return Response.status(500).entity(message+", contact "+helpUrl).build();
		}

		//put in the return text or the authorization header 
		if (tokenInHeader == false) return Response.status(200).entity(encryptedToken).build();
		
		//String encodedKey = URLEncoder.encode(encryptedToken, "UTF-8");
		
		
		//build and send the response putting the encrypted token in the authorization header, these are kept in a secured memory compartment by most apps with restricted access
		return Response.status(200).
				entity("Encrypted token placed in the "+HttpHeaders.AUTHORIZATION+ " header.").
				header(HttpHeaders.AUTHORIZATION, encryptedToken).build();

	}

	public void contextDestroyed(ServletContextEvent arg) {
		lg.info("STOPPING QueryAuthorization Service...");
		if (keyFile != null && keyFile.exists()) keyFile.delete();
	}

	public void contextInitialized(ServletContextEvent arg) {
		lg.info("INITIALIZING QueryAuthorization Service...");

		//pull from web.xml
		ServletContext sc = arg.getServletContext();
		helpUrl = Util.fetchStringParam(sc, "helpUrl");
		lg.info("helpUrl: "+helpUrl);

		//authorizationEnabled?
		String ae = Util.fetchStringParam(sc, "authorizationEnabled");
		if (ae == null){
			lg.fatal("QueryAuthorizaiton FAILED initialization, aborting");
			return;
		}
		lg.info("authorizationEnabled: "+ae);
		//no authorization
		if (ae.toLowerCase().startsWith("f")) {
			lg.info("AUTHENTICATION is NOT required to access any resource!");
			return;
		}


		//in the body or header?
		String tokenLocation = Util.fetchStringParam(sc, "tokenLocation");
		if (tokenLocation != null && tokenLocation.toLowerCase().startsWith("b")) tokenInHeader = false;
		lg.info("tokenInHeader: "+tokenInHeader);

		//fetch user group txt file
		File userGroup = Util.fetchFile(sc, "userGroup", false);
		userPatterns = Util.parseUserGroupRegExPatterns(userGroup);
		if (userPatterns == null) {
			lg.fatal("QueryAuthorizaiton FAILED initialization: problem encountered when parsing the user groups file "+userGroup);
			return;
		}

		//print out users and their file access patterns
		StringBuilder sb = new StringBuilder("\n\nUserName\tPermittedFileAccessPattern(s)\n");
		for (String name: userPatterns.keySet()){
			sb.append(name);
			sb.append("\t");
			for (Pattern p: userPatterns.get(name)) {
				sb.append(p.pattern());
				sb.append(" ");
			}
			sb.append("\n");
		}
		lg.info(sb.toString());

		//create new encryption key, delete on exit
		keyFile = new File(userGroup.getParentFile(), "key.obj");
		keyFile.deleteOnExit();
		key = Crypt.generateAndSaveNewKey(keyFile);
		if (key == null){
			lg.fatal("QueryAuthorizaiton FAILED initialization");
			return;
		}

		//good to go
		authorizing = true;

		//made it so
		initialized = true;
		lg.info("QueryAuthorization is initialized and authenticating...");

	}

	private static final Pattern un = Pattern.compile(".+username=\"(\\w+)\",.+");
	/**Parses digest authentication user name info.*/
	public static String parseUserNameFromDigestAuthentication(List<String> authHeaders){
		if (authHeaders == null || authHeaders.size() == 0) {
			lg.debug("Authorization headers are null or empty");
			return null;
		}

		//Digest username="Dave", realm="QueryAPI", nonce="10....."

		Matcher mat = un.matcher(authHeaders.get(0));
		if (mat.matches()) return mat.group(1);
		else {
			lg.debug("Failed to match? "+un + " in "+authHeaders.get(0));
			return null;
		}
	}

}