package edu.utah.hci.apps;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Random;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import edu.utah.hci.misc.Util;
import edu.utah.hci.query.MasterQuery;
import edu.utah.hci.query.QueryRequest;
import edu.utah.hci.query.User;
import edu.utah.hci.query.UserQuery;

/**Object for directly querying a Query API Data dir via the file system. All data sets are visible.*/
public class GQueryCLI {

	private static final Logger lg = LogManager.getLogger(GQueryCLI.class);
	private MasterQuery masterQuery = null;
	private File gQueryIndexDir = null;
	private int numberProcessors = -1;
	private File outputFile = null;
	private File bedVcfFile = null;
	private User user = null;
	private UserQuery userQuery = null;
	private boolean printMenu = false;
	private boolean interactive = false;
	private String argsString = null;
	private String userName = "CLI";
	private String[] userRegEx = new String[] {".+"};

	/*Constructor*/
	public GQueryCLI(String[] args) {

		try {
			Configurator.setAllLevels(LogManager.getRootLogger().getName(), Level.ERROR);

			//process the args to pull the gQueryIndexDir
			userQuery = processArgs(args, true);

			//build a reusable query object
			if (masterQuery == null) {
				Util.pl("Building the MasterQuery engine...");
				masterQuery = new MasterQuery (gQueryIndexDir);
				Util.pl("\t"+masterQuery.getBuildInfo());
				if (masterQuery.isInitialized() == false) {
					lg.error("ERROR: failed to initialize MasterQuery, aborting.");
					System.exit(1);
				}
			}

			//execute the query
			query(userQuery);

			//is it interactive?
			if (interactive) runInteractiveSession();
				
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	private void runInteractiveSession() throws IOException {
		Scanner s = new Scanner(System.in);
		while (true) {     
			//remove the -g xxx, -i, -c
			if (argsString.contains("-g ")) {
				String[] a = Util.WHITESPACE.split(argsString);
				StringBuilder sb = new StringBuilder();
				for (int i=0; i< a.length; i++) {
					if (a[i].equals("-g")) i++;
					else if (a[i].equals("-i") || a[i].equals("-c")) continue;
					else {
						sb.append(a[i]);
						sb.append(" ");
					}
				}
				argsString = sb.toString();
			}

			Util.pl("\nEnter another query, hit return for the menu, or type 'exit', prior relevant args:\n"+argsString);
			String input = s.nextLine().trim();
			String lcInput = input.toLowerCase();

			//empty or -h for the menu?
			if (input.contains("-h") || input.length() == 0) printDocs();

			else if (lcInput.contains("exit") || lcInput.contains("quit")) {
				s.close();
				Random r = new Random();
				Util.pl("\n"+goodbye[r.nextInt(goodbye.length)]+ "\n");
				return;
			}
			else {
				//remove any ' or " from the new arg line
				input = Util.QUOTE_SINGLE.matcher(input).replaceAll("");
				input = Util.QUOTE_DOUBLE.matcher(input).replaceAll("");
				userQuery = processArgs(Util.WHITESPACE.split(input), false);
				if (userQuery != null) query(userQuery);
			}
		}
	}


	public void query(UserQuery userQuery) throws IOException {

		HashMap<String,String> options = userQuery.fetchQueryOptions();

		//make a single use QueryRequest object
		QueryRequest qr = new QueryRequest(masterQuery, bedVcfFile, options, user);
		//any errors?
		if (qr.getErrTxtForUser() != null || qr.getWarningTxtForUser().size()!=0) {
			if (qr.getErrTxtForUser() !=null) Util.printErrAndExit("\nInvalid request, "+qr.getErrTxtForUser()+ ",aborting.");
			if (qr.getWarningTxtForUser().size() !=0) Util.printErrAndExit("\n"+qr.getWarningTxtForUser()+", aborting.");
		}
		//set results
		else {
			userQuery.setResults(qr.getJsonResults());
			if (outputFile != null) {
				Util.pl("Query Results written to "+outputFile);
				Util.writeString(userQuery.getResults().toString(3), outputFile);
			}
			else Util.pl("\nQuery Results:\n"+userQuery.getResults().toString(3));
		}
	}


	public static void main(String[] args)  {
		if (args.length ==0){
			printDocs();
			System.exit(0);
		}
		new GQueryCLI (args);
	}
	
	/**This method will process each argument and assign new varibles*/
	public UserQuery processArgs(String[] args, boolean printArgs){
		//did they just hit return in an interactive session?
		if (args.length == 0 || args[0].equals("")) return null;
		
		Pattern pat = Pattern.compile("-[a-zA-Z]");
		UserQuery uq = new UserQuery();
		argsString = Util.stringArrayToString(args, " ").trim();
		if (printArgs) Util.pl("\nGQueryCLI Arguments: "+ argsString +"\n");
		
		bedVcfFile = null;
		outputFile = null;
		String[] regions = null;
		String[] vcfRegions = null;
		printMenu = false;
		String userRx = null;
		
		
		for (int i = 0; i<args.length; i++){
			Matcher mat = pat.matcher(args[i]);
			if (mat.matches()){				
				char test = args[i].charAt(1);
				try{
					switch (test){
					case 'g': gQueryIndexDir = new File(args[++i]).getCanonicalFile(); break;
					case 'o': uq.fetchOptions(); break;	
					case 'f': bedVcfFile = new File(args[++i]); break;
					case 'r': regions = Util.SEMI_COLON.split(args[++i]); break;
					case 'v': vcfRegions = Util.SEMI_COLON.split(args[++i]); break;
					
					case 'p': for (String s: Util.SEMI_COLON.split(args[++i])) uq.addRegExDirPath(s); break;
					case 'n': for (String s: Util.SEMI_COLON.split(args[++i])) uq.addRegExFileName(s); break;
					case 'l': for (String s: Util.SEMI_COLON.split(args[++i])) uq.addRegExDataLine(s); break;
					case 'e': for (String s: Util.SEMI_COLON.split(args[++i])) uq.addRegExDataLineExclude(s); break;
					case 'u': userRx = args[++i]; break;
					case 'w': userName = args[++i]; break;
					
					case 'P': uq.matchAllDirPathRegEx(); break;
					case 'N': uq.matchAllFileNameRegEx(); break;
					case 'L': uq.matchAllDataLineRegEx(); break;
					
					case 'd': uq.fetchData(); break;
					case 'j': uq.includeHeaders(); break;
					case 'm': uq.matchVcf(); break;
					case 'a': uq.addBpPadding(args[++i]); break;
					
					case 's': outputFile = new File(args[++i]); break;
					case 'c': numberProcessors = Integer.parseInt(args[++i]); break;
					case 'h': printMenu = true; break;
					case 'i': interactive = true; break;
					
					default: Util.printErrAndExit("\nProblem, unknown option! " + mat.group()+" Use -h to print help menu.");
					}
				}
				catch (Exception e){
					Util.printErrAndExit("\nSorry, something doesn't look right with this parameter: -"+test+"\n");
				}
			}
			else Util.printErrAndExit("\nSorry, something doesn't look right with these arguments: "+argsString+"\n");
		}
		if (printMenu) {
			printDocs();
			System.exit(0);
		}
		//gQueryIndex?, only set once
		if (gQueryIndexDir == null || gQueryIndexDir.exists() == false) {
			Util.printErrAndExit("\nERROR: please provide a GQuery indexed genomic data directory -g, aborting. Does '"+gQueryIndexDir+"' exits?\n");
			System.exit(1);
		}
		//processors to use, only set once
		if (numberProcessors < 0) {
			int numAvail = Runtime.getRuntime().availableProcessors();
			if (numberProcessors < 1 || numberProcessors > numAvail) numberProcessors =  numAvail - 1;
		}
		//must have one of the following
		if (regions != null || uq.isFetchOptionsFlag() || bedVcfFile != null || vcfRegions != null ) {
			if (regions != null) for (String s: regions) uq.addBedRegion(s);
			else if (vcfRegions != null) {
				for (String s: vcfRegions) {
					s=Util.UNDERSCORE.matcher(s).replaceAll("\t");
					uq.addVcfRecord(s);
				}
			}
		}
		else Util.printErrAndExit("\nERROR: please complete one of the following arguments: -o -r -f or -v\n");
		
		//make a user
		if (userRx !=null) {
			userRegEx = Util.SEMI_COLON.split(userRx);
			for (int i=0; i< userRegEx.length; i++) userRegEx[i] = ".*"+userRegEx[i]+".*";
		}
		user = new User(userName, userRegEx);	
		return uq;
	}
	
	public static void printDocs(){
		Util.pl("\n" +
				"**************************************************************************************\n" +
				"**                      GQuery Command Line Interface : Oct 2020                    **\n" +
				"**************************************************************************************\n" +
				"GQueryCLI executes queries on GQuery indexed genomic data. First run the GQueryIndexer\n"+
				"application on a base data directory containing sub directories with thousands of\n"+
				"tabix indexed genomic data files.\n"+

				"\nRequired Arguments:\n"+
				"-g A data directory containing GQuery indexed data files. See the GQueryIndexer app.\n\n"+
				
				"     And one of the following. (Any ; ? Surrond with quotes, e.g. 'xxx;xxx') \n\n"+
				
				"-o Fetch detailed query options and available data sources. Use the later to create\n"+
				"     java regex expressions to focus and speed up queries.\n"+
				"-r Regions to use in searching, no spaces, semi-colon separated, chr and , ignored \n"+
				"     e.g. 'chr17:43,042,295-43,127,364;chr13:32313480-32401672' \n"+
				"-v Vcf records to use in searching, replace tabs with _ ,no spaces, ; separated, \n"+
				"     e.g. 'chr17_43063930_Foundation71_C_T'\n"+
				"-f File of bed or vcf records to use in searching, xxx.bed or xxx.vcf, .zip/.gz OK\n\n"+

				"\nOptional Arguments:\n"+
				"-p Directory path regex(s) to select particular directory paths to search,\n"+
				"     semi-colon delimited, no spaces, 'quote multiple regexes', case sensitive,\n"+
				"     each rx is surrounded with .* so no need to add these \n"+
				"     e.g. '/Somatic/Avatar/;/Somatic/Tempus/' \n"+
				"-n File name regex(s) to select particular file names to search, ditto\n"+ 
				"     e.g. '.vcf.gz;.maf.txt.gz' \n"+
				"-l Data record regex(s) to select particular data lines to return, ditto \n" + 
				"     e.g. 'HIGH;Pathogenic;Likely_pathogenic'\n"+
				"-e Data record regex(s) to exclude particular data lines from returning, ditto \n" + 
				"     e.g. 'Benign;Likely_benign'\n"+
				"-u User specific directory path regex(s) to limit searching to approved datasets,\n"+
				"     e.g. '.*/Tempus/.*;.*/ARUP/.*', defaults to '.+'\n"+
				"-w User name, defaults to CLI\n\n"+

				"-P Match all DirPathRegExs, defaults to just one\n"+
				"-N Match all FileNameRegExs\n"+
				"-L Match all DataLineRegEx\n\n"+

				"-d Fetch data, defaults to just returning the data source file paths\n"+
				"-j Include data source headers\n"+
				"-m Match vcf record's CHROM POS REF ALT \n"+
				"-a Pad each vcf or bed region +/- this bp value\n\n"+
				
				"-s Save the json output to this file\n"+
				"-i Run an interactive session to enable sequential queries\n"+
				"-c Number processors to use, defaults to all\n"+
				"-h Print this help menu\n"+

				"\nExamples:\n"+
				"# Pull data source paths and detailed options:\n"+
				"   java -jar -Xmx20G ~/YourPathTo/GQueryCLI.jar -g ~/GQueryIndexedData/ -o\n\n"+
				
				"# Execute a query for BRCA1 and BRCA2 pathogenic germline vcf variants\n"+
				"   java -jar -Xmx20G ~/YourPathTo/GQueryCLI.jar -g ~/GQueryIndexedData/ -d -n .vcf.gz\n"+
				"   -p '/Germline/;/Hg38/' -P -l '=Pathogenic;=Likely_pathogenic' -s results.json\n"+
				"   -r 'chr17:43,042,295-43,127,364;chr13:32313480-32401672'\n\n"+

				"**************************************************************************************\n");
	}
	
	public static final String[] goodbye = {"hágoónee' (Navajo)", "Totsiens (Afrikaans)", "Ma'a as-salaama (Arabic)", "Bidāẏa (Bengali)", "Zdravo! (Bosnian)", "Joigin (Cantonese)", "Donadagohvi (Cherokee)", 
			"Doviđenja (Croatian)", "Sbohem (Czech)", "Farvel (Danish)", "Tot ziens (Dutch)", "Nägemist! (Estonian)", "Näkemiin (Finnish)", "Au Revoir (French)", "Auf Wiedersehen (German)", 
			"Yasou (Greek)", "Aloha (Hawaiian)", "L'hitraot (Hebrew)", "Namaste (Hindi)", "Viszlát! (Hungarian)", "Vertu sæll! (Icelandic)", "Sampai Jumpa (Indonesian)", "Slan (Irish)", 
			"Arrivederci (Italian)", "Sayōnara (Japanese)", "Annyeong (Korean)", "Vale (Latin)", "Uz redzēšanos! (Latvian)", "Atsiprasau (Lithuanian)", "Zài jiàn (Mandarin)", "Namaste (Nepalese)", 
			"Ha det bra (Norwegian)", "Khodaa haafez (Persian)", "Żegnaj (Polish)", "Adeus (Portuguese)", "Alweda (Punjabi)", "La revedere (Romanian)", "Do svidaniya (Russian)", "Zdravo! (Serbian)", 
			"Dovidenia! (Slovak)", "Nasvidenje (Slovene)", "Adios (Spanish)", "Adjö (Swedish)", "Poitu varein (Tamil)", "Laa Gòn (Thai)", "Görüşürüz! (Turkish)", "Do pobachennia! (Ukrainian)", 
			"Khuda hafiz (Urdu)", "Tạm biệt (Vietnamese)", "Hwyl fawr (Welsh)", "Hamba kahle (Zulu)",};

}
