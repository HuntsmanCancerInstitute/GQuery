# GQuery
GQuery is a software tool for rapidly querying large numbers of bgzip compressed, tabix indexed genomic data files e.g. vcf, maf, bed, bedGraph, etc. from multiple species with different genome builds without the need to develop, debug, and maintain custom file parsers. Search for particular germline or somatic mutations, copy number changes, differentially expressed genes, epigenetic marks, anything that can be associated with genomic coordinates.  Just point the GQuery indexer at a collection of tabix indexed files and then run either the GQuery command line app or the web API to search them.  GQuery is built using a fast, multi-threaded, genomic range search engine with extensive junit testing.

**The GQuery package includes three Java applications:**
<ol>
<li>GQuery Indexer - a command line tool for building chromosome indexes that link genomic coordinates with the data files that contain intersecting records.</li> 
<li>GQuery CLI - a command line tool for executing queries locally on GQuery indexed data directories.</li>
<li>GQuery API - a RESTful web  API service for executing queries on remote servers for authenticated user groups.</li>
</ol>

Each query triggers an intersection of each user's regions of interest against the GQuery chromosome indexes to identify data files that contain intersecting records. Regular expression filters are provided to limit which directory paths are searched and which file types are returned.  Often this is all that is needed for a basic query.  If requested, a second tabix search is used to fetch the actual intersecting records from the data files. These records can also be filtered using regular expressions.  

This approach of searching genomic coordinate indexes for intersecting data files combine with tabix data record retrieval is an excellent way to address the random range query problem. In our benchmarking tests, it significantly out performs both relational database (MySQL) and NoSQL (MongoDB) approaches. Moreover, use of the widely adopted, bgzip compressed, tabix indexed file format (<https://www.htslib.org/doc/tabix.html>) eliminates the need to duplicate the data source content or create and maintain custom db data file importers.  If you can tabix index it, you can search it.

Getting up and going with GQuery is a **simple three step process:** download the latest jar files, build the chromosome data file indexes, and execute queries using the CLI.  

For those looking to provide search capability via a web application, deploy the GQuery RESTful web API.  This is especially useful when searching needs to be restricted to subsets of the data for particular user groups, e.g. patient, IRB restricted, or unpublished project data.

---
## Step 1: Download the Jar Files
Go to <https://github.com/HuntsmanCancerInstitute/GQuery/releases> and download the latest xxx.jar files.  These are self contained. No other libraries are required.  Open a command line terminal.  Type 'java -version'. If needed, install java 1.8 or higher, <https://www.java.com/en/download/>. In a command line terminal, launch the Indexer and CLI without options to pull the help menus, e.g. 

<pre>java -jar pathToJars/GQueryIndexer.jar; java -jar pathToJars/GQueryCLI.jar</pre>


---
## Step 2: Build the Chromosome Indexes

The second step with GQuery is to build the chromosome indexes with the GQueryIndexer application. It is multi-threaded and junit tested.

Give some thought to how to best structure the base Data directory for your group.  If you are working with multiple species and genome builds then create a sub directory named with the build for easy directory path regular expression matching (e.g. Data/B37/, Data/Hg38, Data/MM10, etc.). Likewise create directories for each major project (e.g. Data/Hg38/TCGA, Data/Hg38/AVATAR, Data/Hg38/Clinical/Foundation) and particular data types (e.g. Data/Hg38/AVATAR/Germline, Data/Hg38/AVATAR/Somatic/Vcf, AVATAR/Somatic/ReadCoverage, Data/Hg38/AVATAR/Somatic/Cnv). 

Keep in mind that a .GQuery chromosome index is created in each directory that contains xxx.gz.tbi files.  Thus the most optimal indexing strategy is to soft link or copy over 100's to 1000's of files into the same directory. The worst strategy is to have many directories with just a few data files. Lastly, directory path regular expressions are used by GQuery to both restrict  what a user can search and to speed up the searching, so create a directory structure in the way that best meets your needs. 

<pre>
java -jar -Xmx30G ~/YourPathTo/GQueryIndexer.jar

**************************************************************************************
**                               GQuery Indexer: Oct 2020                           **
**************************************************************************************
GQI builds index files for GQuery by recursing through a base data directory looking
for directories containing bgzip compressed and tabix indexed genomic data files, e.g. 
xxx.gz and xxx.gz.tbi . A GQuery index is created for each directory, thus place or
link 100 or more related files in the same directory, e.g. Data/Hg38/Somatic/Vcfs/
and Data/Hg38/Somatic/Cnvs/ . This app is threaded for simultaneous file loading
and requires >30G RAM to run on large data collections. Lastly, the indexer will only
re index an existing index if the data files have changed. Thus, run it nightly to
keep the indexes up to date.

Required Params:
-c A bed file of chromosomes and their lengths (e.g. chr21 0 48129895) to use to 
     building the intersection index. Exclude those you don't want to index. For
     multiple builds and species, add all, duplicates will be collapsed taking the
     maximum length. Any 'chr' prefixes are ignored when indexing and searching.
-d A base data directory containing sub directories with tabix indexed data
     files. Known file types include xx.vcf.gz, xx.bed.gz, xx.bedGraph.gz, and 
     xx.maf.txt.gz. Others will be parsed using info from the xx.gz.tbi index. See
     https://github.com/samtools/htslib . For bed files don't use the -p option,
     use '-0 -s 1 -b 2 -e 3'. For vcf files, vt normalize and decompose_blocksub,
     see http://genome.sph.umich.edu/wiki/Vt.
-t Full path directory containing the compiled bgzip and tabix executables. See
     https://github.com/samtools/htslib

Optional Params:
-q Quiet output, no per record warnings.
-b BP block to process, defaults to 250000000. Reduce if out of memory issues occur.
-n Number cores to use, defaults to all

Example for generating the test index using the GitHub GQuery/TestResources files
see https://github.com/HuntsmanCancerInstitute/GQuery

d=/pathToYourLocalGitHubInstalled/GQuery/TestResources
java -jar -Xmx115G GQueryIndexer.jar -c $d/b37Chr20-21ChromLen.bed -d $d/Data
-t $d/Htslib_1.10.2/bin/ 

**************************************************************************************
</pre>


---
## Step 3: Run Local Queries with the Command Line Interface

Run queries locally using the GQueryCLI application. It is multi-threaded and junit tested. Results are returned in JSON. Specify one or more regions of interest in bed region or vcf format.  Use the path and file name regular expressions to speed up and limit what files are searched.  

For example, if you're only interested in Germline mutations and your base data dir is organized into Data/Germline and Data/Somatic files, use a dir path regex '-p Data/Germline' to only search the germline data.  

Likewise, if you are only interested in actual vcf variants, specify a file name regex '-n vcf.gz' in addition to a dir path regex.

Lastly, fetching the actual intersecting data records from each file can be a computationally intensive process so only use the '-d' fetch data option after you have narrowed down your search with path and file name regexes.  In many cases it's not needed, for example if you're only interested in identifying patients with a BRCA1 mutation, then skip the '-d' option and just parse the 'source' names from the JSON output.

<pre>
java -jar -Xmx30G ~/YourPathTo/GQueryCLI.jar

**************************************************************************************
**                     GQuery Command Line Interface: Oct 2020                      **
**************************************************************************************
GQueryCLI executes queries on GQuery indexed genomic data. First run the GQueryIndexer
application on directories containing thousands of tabix indexed genomic data files.

Required Arguments:
-g A data directory containing GQuery indexed data files. See the GQueryIndexer app.

     And one of the following. (Any ; ? Surrond with quotes, e.g. 'xxx;xxx') 

-o Fetch detailed query options and available data sources. Use the later to create
     java regex expressions to focus and speed up queries.
-r Regions to use in searching, no spaces, semi-colon separated, chr and , ignored 
     e.g. 'chr17:43,042,295-43,127,364;chr13:32313480-32401672' 
-v Vcf records to use in searching, replace tabs with _ ,no spaces, ; separated, 
     e.g. 'chr17_43063930_Foundation71_C_T'
-f File of bed or vcf records to use in searching, xxx.bed or xxx.vcf, .zip/.gz OK


Optional Arguments:
-p Directory path regex(s) to select particular directory paths to search,
     semi-colon delimited, no spaces, 'quote multiple regexes', case sensitive,
     each rx is surrounded with .* so no need to add these 
     e.g. '/Somatic/Avatar/;/Somatic/Tempus/' 
-n File name regex(s) to select particular file names to search, ditto
     e.g. '.vcf.gz;.maf.txt.gz' 
-l Data record regex(s) to select particular data lines to return, ditto 
     e.g. 'HIGH;Pathogenic;Likely_pathogenic'
-e Data record regex(s) to exclude particular data lines from returning, ditto 
     e.g. 'Benign;Likely_benign'
-u User specific directory path regex(s) to limit searching to approved datasets,
     e.g. '.*/Tempus/.*;.*/ARUP/.*', defaults to '.+'
-w User name, defaults to CLI

-P Match all DirPathRegExs, defaults to just one
-N Match all FileNameRegExs
-L Match all DataLineRegEx

-d Fetch data, defaults to just returning the data source file paths
-j Include data source headers
-m Match vcf record's CHROM POS REF ALT 
-a Pad each vcf or bed region +/- this bp value

-s Save the json output to this file
-i Run an interactive session to enable sequential queries
-c Number processors to use, defaults to all
-h Print this help menu

Examples:
# Pull data source paths and detailed options:
   java -jar -Xmx20G ~/YourPathTo/GQueryCLI.jar -g ~/GQueryIndexedData/ -o

# Execute a query for BRCA1 and BRCA2 pathogenic germline vcf variants
   java -jar -Xmx20G ~/YourPathTo/GQueryCLI.jar -g ~/GQueryIndexedData/ -d -n .vcf.gz
   -p '/Germline/;/Hg38/' -P -l '=Pathogenic;=Likely_pathogenic' -s results.json
   -r 'chr17:43,042,295-43,127,364;chr13:32313480-32401672'

**************************************************************************************
</pre>




---
## Step 4 (Optional): Run Queries using the Web API

If needed, GQueries may be executing on remote servers using a web API. It is built using the Java Jersey JAX RESTful API framework. It is JUnit tested and deployed on Apache Tomcat for optimized performance. Results are returned in JSON. Key token based digest authentication may be enabled to restrict what user may search.  

### Fetch Options
This API call returns the available options and data sources 

<pre>http://localhost:8080/GQuery/search?fetchOptions=true</pre>

These include:

Feature | Example | Description
------------ | ------------- | -------------
bed | bed=chr21:145,569-145,594;21\\t11058198\\t11058237 | Region(s) of interest to query in region format, semicolon delimited. Commas and prepended 'chr' are ignored.
vcf | vcf=chr20\\t4162847\\t.\\tC\\tT\\t.\\tPASS\\t. | Region(s) of interest to query in vcf format, semicolon delimited. Prepended 'chr' are ignored. Remove any semicolons in the vcf INFO and FORMAT fields.
regExDirPath | regExDirPath=/TCGA/;ICGC | Require intersecting records to belong to a file whose path matches at least one of these java regular expressions, semicolon delimited. Note, a .* is added to both ends of each regEx.
regExFileName | regExFileName=/B37/;\\.vcf\\.gz | Require records to belong to a file whose name matches these java regular expressions, semicolon delimited. Note, a .* is added to both ends of each regEx.
regExDataLine | regExDataLine=Pathogenic;LOF;HIGH | Require each record data line to match these java regular expressions, semicolon delimited. Note, a .* is added to both ends of each regEx. Will set 'fetchData' = true.
regExDataLineExclude | regExDataLineExclude=Benign;FailsQC | Exclude record data lines that match any of these java regular expressions, semicolon delimited. Note, a .* is added to both ends of each regEx. Will set 'fetchData' = true.
matchAllDirPathRegEx | matchAllDirPathRegEx=true | Require all regExDirPath match.
matchAllFileNameRegEx | matchAllFileNameRegEx=true | Require all regExFileName match.
matchAllDataLineRegEx | matchAllDataLineRegEx=true | Require all regExDataLine match.
bpPadding | bpPadding=1000 | Pad each vcf or bed region +/- bpPadding value.
includeHeaders | includeHeaders=true | Return the file headers associated with the intersecting datasets.
matchVcf | matchVcf=true | For vcf queries, require intersecting vcf records match chr, pos, ref, and at least one alt. Will set fetchData=true. Be sure to vt normalize your vcf input, see https://github.com/atks/vt.
fetchData | fetchData=true | Pull records from disk (slow). First develop an appropriate restrictive regEx filter set, then fetchData.
key | key=tqGT3e4EG%2B1Jnnw%2FRPA9k | Encrypted userName:timeStamp from the GQuery/fetchKey service. Required for accessing restricted resources when query authentication is enabled.  Not required for guest access. 


### Region search against all datasets

<pre>http://localhost:8080/GQuery/search?bed=20:4162827-4162867</pre>

Note, 20:4162827-4162867, 20:4,162,827-4,162,867, chr20:4162827-4162867 are all permitted.

### Region search and fetch data

<pre>http://localhost:8080/GQuery/search?bed=20:4,162,827-4,162,867&ampfetchData=true</pre>

### Region search of only the TCGA datasets

<pre>http://localhost:8080/GQuery/search?bed=20:4,162,827-4,162,867&ampregExDirPath=/TCGA/</pre>

### Region search of maf and bed files from the B37 genome build

<pre>http://localhost:8080/GQuery/search?bed=20:4,162,827-4,162,867&ampregExDirPath=/B37/&ampregExFileName=maf\.txt\.gz;bed\.gz</pre>

### Search for vcf records that match chr,pos,ref, and one alt, include file headers

Note, you will likely need to encode the tabs by replacing them with %09 if pasting the URL directly in a web browser

<pre>20\t4163144\t.\tC\tA\t.\t.\t. -> 20%094163144%09.%09C%09A%09.%09.%09.</pre>

<pre>http://localhost:8080/GQuery/search?vcf=20%094163144%09.%09C%09A%09.%09.%09.&ampmatchVcf=true&ampregExFileName=vcf\.gz&ampincludeHeaders=true</pre>


## Installing the GQuery Web App
See also <https://github.com/HuntsmanCancerInstitute/GQuery/blob/master/Misc/queryNotes.txt>

### Install Tomcat 7 on a large linux server (>12 cores, > 30G RAM)
e.g. <https://www.digitalocean.com/community/tutorials/how-to-install-apache-tomcat-7-on-centos-7-via-yum>

Be sure to increase the Xmx and MaxPermSize params to > 30G RAM to avoid out of memory errors. Tomcat 8 likely works but hasn't been tested.

### Modify the latest GQuery-XX.war  

Download, unzip, and modify the following config files to match your environment<br>
<https://github.com/HuntsmanCancerInstitute/GQuery/releases>

<pre>unzip -q GQuery-XX.war </pre>

WEB-INF/web.xml, set the correct paths and help url
```xml
<param-name>path2DataDir</param-name>
<param-name>tempDir</param-name>
<param-name>helpUrl</param-name>
```

Edit the WEB-INF/classes/log4j2.properties file, tell GQuery where to write internal messages, good to watch for errors and issues.

Update the war
<pre>zip -ru GQuery-XX.war META-INF WEB-INF</pre>

### Deploy
Move the updated war file into the tomcat/webapps/ dir. If needed, restart tomcat.<br>
Examine the log4j log file for startup and test issues. Loading of the interval trees can take several minutes.<br>
Test the server: *http://IPAddressOfMyBigServer:8080/GQuery-XX/search?fetchOptions=true* <br>

---
## Configuring GQuery for token based digest authentication

### Enable digest authentication in tomcat, see the WEB-INF/web.xml doc for an example, details:
<https://techannotation.wordpress.com/2012/07/02/tomcat-digestauthentication/>

Generate passwords:
apache-tomcat-7.xxx/bin/digest.sh -a md5 Obama:GQuery:thankYou	
Obama:GQuery:thankYou:15e22d1122b938d84b2920e246a2e095

Modify the apache-tomcat-7.xxx/conf/tomcat-users.xml file:
```xml
<tomcat-users>
  <role rolename="queryUser"/>
  <user username="Admin" password="cd982408c54028850cae3fa4df580929" roles="queryUser"/>
  <user username="Obama" password="15e22d1122b938d84b2920e246a2e095" roles="queryUser"/>
</tomcat-users>
```

### Create a tab delimited userGroup.txt file and place it someplace secure and readable by tomcat, e.g. WEB-INF/userGroup.txt:
<pre>
# Define groups and their associated regular expression(s) used to match file paths acceptable for returning to a given user.  If more than one regEx is provided, only one must match, not all.
GroupName	RegEx's, comma delimited, no spaces
Public	.+/Data/B37/BedData/.+,.+/Data/B37/TCGA/.+,.+/Data/B37/VCFData/.+
QueryAdmin	.+/Data/.+
Thor	.+/Data/B37/Thor/.+

# Define users and the group(s) they belong to
# Required, define a user called 'Guest' and the groups they are allowed to access without an authentication key
UserName	Groups, comma delimited, no spaces, membership gains access to the file path RegEx's
Guest	Public
Admin	QueryAdmin
Obama	Public,Thor
</pre>

### Enable GQuery authentication by modifying the WEB-INF/web.xml doc:
```xml
  <context-param>
    <description>Boolean indicating whether authorization is enabled.  If true, complete the userGroup param.</description>
    <param-name>authorizationEnabled</param-name>
    <param-value>true</param-value>
  </context-param>
  <context-param>
    <description>If using authorization, provide a full path to the userGroup file. This parent dir will also be used to store the encryption key so keep it secure.</description>
    <param-name>userGroup</param-name>
    <param-value>/Users/u0028003/Code/GQuery/WebContent/WEB-INF/userGroup.txt</param-value>
  </context-param>
    <context-param>
    <description>If using authorization, place QueryAuthorization token in Response 'body' or Authorization 'header' (more secure)</description>
    <param-name>tokenLocation</param-name>
    <param-value>body</param-value>
  </context-param>
  <context-param>
    <description>If using authorization, indicate how many minutes an issued token remains active.</description>
    <param-name>minPerSession</param-name>
    <param-value>360</param-value>
  </context-param>
```

### Start-up tomcat and look in the log4j output for any error messages
Note, if authorizationEnable is set to true, several checks are performed.  If any fail, the entire QueryService is disabled.

### Request a key token from the digest protected service, these expire after the WEB-INF/web.xml defined time:
<pre>http://localhost:8080/GQuery/fetchKey</pre>

Provide the userName and pw.  The key is returned in the body, or if so configured, in the WEB-INFO/web.xml doc under the protected Authorization header. 

Test with curl:
<pre>curl --user Obama:thankYou --digest http://localhost:8080/GQuery/fetchKey -D header.txt</pre>

### Perform a search with the key token
If pasting in a browser, be sure to encode the token:

<pre>http://localhost:8080/GQuery/search?bed=chr22:39281892-39515517&key=tqGT3e4EG%2B1Jnnw%2FRPA9kg%3D%3D%3A62VfVs2UbvrOVCk%2BRL2Dr%2B09NND9DXlP1SDQPUh%2B6bs%3D</pre>

For guest access skip the key value argument:

<pre>http://localhost:8080/GQuery/search?bed=chr22:39281892-39515517</pre>












