package edu.utah.hci.query;


import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.utah.hci.apps.QueryService;
import edu.utah.hci.misc.Crypt;
import edu.utah.hci.misc.Util;

/**Decrypts and parses a user name and whether their session has expired.*/
public class User {

	private static final Logger lg = LogManager.getLogger(User.class);
	private String userName = null;
	private boolean expired = false;
	private Pattern[] regExOne = null;
	private String errorMessage = null;

	public User (String encryptedKey, QueryService queryService){
		try {
			//any token?
			if (encryptedKey == null) {
				regExOne = QueryService.getUserRegEx().get("Guest");
				userName = "Guest";
				return;
			}

			//decrypt it and split userName:timestamp
			String nameTimestamp = Crypt.decrypt(encryptedKey, QueryService.fetchKey());
			String[] nt = Util.COLON.split(nameTimestamp);
			if (nt.length != 2) throw new Exception("Failed to split key on colon?");
			userName = nt[0];
			long timestamp = Long.parseLong(nt[1]);

			//expired?
			double diff = (double)(System.currentTimeMillis() - timestamp);
			int min = (int)Math.round(diff/1000.0/60.0);
			if (min <= QueryService.getMinPerSession()) {
				expired = false;
				//attempt to parse
				regExOne = QueryService.getUserRegEx().get(userName);
				if (regExOne == null) throw new Exception("Failed to find regEx patterns for the given user?!");
			}
			else expired = true;

		} catch (Exception e){
			String st = Util.getStackTrace(e);
			errorMessage = "Problem with instantiating a User "+userName+" "+encryptedKey+"\n"+st;
			lg.error(errorMessage);
		}
	}
	
	public User (String userName, String[] userDirPathRegExs) {
		this.userName = userName;
		regExOne = new Pattern[userDirPathRegExs.length];
		for (int i=0; i< regExOne.length; i++) regExOne[i] = Pattern.compile(userDirPathRegExs[i]);
	}

	public String getUserName() {
		return userName;
	}
	public boolean isExpired() {
		return expired;
	}
	public Pattern[] getRegExOne() {
		return regExOne;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

}
