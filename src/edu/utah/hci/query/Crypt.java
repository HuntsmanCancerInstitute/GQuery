package edu.utah.hci.query;

import java.io.File;
import java.security.Key;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**Static helper methods for symmetric encryption.*/
public class Crypt {
	private static final Logger lg = LogManager.getLogger(Crypt.class);
	
	/**Example usage.*/
	public static void main( String [] args ) throws Exception {
		//can be saved as a serialized object
		Key key = Crypt.generateSymmetricKey();

		String plaintext = "My secret";
		System.out.println( plaintext );

		//encrypt it
		byte[] iv = Crypt.generateIV();
		String ciphertext = Crypt.encrypt(iv, plaintext, key );
		System.out.println( ciphertext );

		//decrypt with key
		String decrypted = Crypt.decrypt( ciphertext, key );
		System.out.println( decrypted );
	}
	
	public static String encrypt( byte [] iv, String plaintext, Key key ) throws Exception {
		byte [] decrypted = plaintext.getBytes();
		byte [] encrypted = encrypt( iv, decrypted, key );
		StringBuilder ciphertext = new StringBuilder();
		ciphertext.append( Base64.encodeBase64String( iv ) );
		ciphertext.append( ":" );
		ciphertext.append( Base64.encodeBase64String( encrypted ) );
		return ciphertext.toString();
	}
	
	public static String encrypt(String plaintext, Key key) {
		try {
			return encrypt(Crypt.generateIV(), plaintext, key);
		} catch (Exception e) {
			String st = Util.getStackTrace(e);
			lg.error("Problem encountered when attempting to encrypt the plain text.");
			lg.error(st);
		}
		return null;
	}

	public static String decrypt( String ciphertext, Key key ) throws Exception {
		String [] parts = ciphertext.split( ":" );
		byte [] iv = Base64.decodeBase64( parts[0] );
		byte [] encrypted = Base64.decodeBase64( parts[1] );
		byte [] decrypted = decrypt( iv, encrypted, key);
		return new String( decrypted );
	}
	public static byte [] generateIV() {
		SecureRandom random = new SecureRandom();
		byte [] iv = new byte [16];
		random.nextBytes( iv );
		return iv;
	}
	public static Key generateSymmetricKey() throws Exception {
		KeyGenerator generator = KeyGenerator.getInstance( "AES" );
		SecretKey key = generator.generateKey();
		return key;
	}
	public static byte [] encrypt( byte [] iv, byte [] plaintext, Key key ) throws Exception {
		Cipher cipher = Cipher.getInstance( key.getAlgorithm() + "/CBC/PKCS5Padding" );
		cipher.init( Cipher.ENCRYPT_MODE, key, new IvParameterSpec( iv ) );
		return cipher.doFinal( plaintext );
	}
	public static byte [] decrypt( byte [] iv, byte [] ciphertext, Key key ) throws Exception {
		Cipher cipher = Cipher.getInstance( key.getAlgorithm() + "/CBC/PKCS5Padding" );
		cipher.init( Cipher.DECRYPT_MODE, key, new IvParameterSpec( iv ) );
		return cipher.doFinal( ciphertext );
	}

	/**Creates a new key and saves it as a serialized object.*/
	public static Key generateAndSaveNewKey(File keyFile) {
		if (keyFile.exists()) keyFile.delete();
		Key key = null;
		try {
			key = Crypt.generateSymmetricKey();
			if (Util.saveObject(keyFile, key) == false) throw new Exception();
		} catch (Exception e) {
			if (keyFile != null && keyFile.exists()) keyFile.delete();
			key = null;
			String st = Util.getStackTrace(e);
			lg.error("Problem encountered when attempting to create and save the encryption key");
			lg.error(st);
		}
		return key;
	}
}
