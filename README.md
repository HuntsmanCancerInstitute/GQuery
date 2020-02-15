# GQuery API
A RESTful webapp API service for rapidly querying large numbers (>10K) of tabix indexed genomic data files, e.g. vcf, gvcf, maf, bed, bedGraph, etc. from multiple species with different genome builds.

A GET or POST request triggers a two step search.  User's regions of interest (ROI) are intersected against an in-memory, interval tree data structure to identify genomic data files that contain intersecting records. Intersection times are typically < 1 second for 100's of ROI against 1Ks of data files.  Two regular expression filters are provided to limit what file paths are returned.  Often this is all that is needed for basic genomic queries.  Upon request, a high performance, threaded tabix fetch request can be executed to return the actual intersecting records along with the file headers for downstream processing. These records can be filtered using regular expressions.

This approach of an in memory interval tree region file look up combine with tabix retrieval solves the random range query problem. In our benchmarking tests, it significantly out performs both relational database (MySQL) and NoSQL (MongoDB) approaches. Moreover, use of the widely adopted, bgzip compressed/ tabix indexed file format eliminates the need to duplicate the data source content.

This web app is built using the Java Jersy JAX RESTful API framework. It is JUnit tested and deployed on Apache Tomcat for optimized performance. Results are returned in JSON.

---
# Example Usage with Test Resource Files

### Fetch Options
This API call returns the available options and data sources 

<pre>http://localhost:8080/GQuery/search?fetchOptions=true</pre>

https://github.com/HuntsmanCancerInstitute/GQuery/blob/master/TestResources/Json/queryOptionsAndFilters.json

These include:

Feature | Options | Description
------------ | ------------- | -------------
bed | chr21:145,569-145,594; 21\\t11058198\\t11058237 | Region(s) of interest to query in region format, semicolon delimited. Commas and prepended 'chr' are ignored.
vcf | chr20\\t4162847\\t.\\tC\\tT\\t.\\tPASS\\t. | Region(s) of interest to query in vcf format, semicolon delimited. Prepended 'chr' are ignored. Watch out for semicolons in the vcf INFO and FORMAT fields.
regExAll | /B37/; \\.vcf\\.gz | Require intersecting records to belong to a file whose path matches all of these java regular expressions, semicolon delimited. Note, a .\* is added to both ends of each regEx.
regExOne | TCGA; ICGC | Require intersecting records to belong to a file whose path matches at least one of these java regular expressions, semicolon delimited. Note, a .\* is added to both ends of each regEx.
regExAllData | Pathogenic;Frameshift | Require records to match all of the regexs. Case insensitive.
regExOneData | Pathogenic;LOF;HIGH | Require records to match at least one of the regexs. Case insensitive.
includeHeaders | true, false | Return the file headers associated with the intersecting datasets.
matchVcf | true, false | For vcf queries, require that intersecting vcf records in the data dir match chr, pos, ref, and at least one alt. Will set fetchData=true. Be sure to vt normalize and decompose_blocksub your vcf input, see https://github.com/atks/vt.
fetchData | true, false, force | Pull the intersecting records from disk (slow). Setting to 'force' and providing at least one regEx filter, enables access to the forceFetchDataSources. Use with a very restrictive regEx filter set, ideally on specific named file paths. Force turns the GQuery web app into a tabix data retrieval utility.
key | token | Encrypted userName:timeStamp from the GQuery/fetchKey service. Required for accessing restricted resources when query authentication is enabled.  Skip for guest access. 

### Region search against all datasets

<pre>http://localhost:8080/GQuery/search?bed=20:4162827-4162867</pre>

https://github.com/HuntsmanCancerInstitute/GQuery/blob/master/TestResources/Json/QueryExamples/simpleBed.json

Note, 20:4162827-4162867, 20:4,162,827-4,162,867, chr20:4162827-4162867 are all permitted.

### Region search and fetch data

<pre>http://localhost:8080/GQuery/search?bed=20:4,162,827-4,162,867&ampfetchData=true</pre>

https://github.com/HuntsmanCancerInstitute/GQuery/blob/master/TestResources/Json/QueryExamples/fetchData.json

### Region search of only the TCGA datasets

<pre>http://localhost:8080/GQuery/search?bed=20:4,162,827-4,162,867&ampregExAll=/TCGA/</pre>

https://github.com/HuntsmanCancerInstitute/GQuery/blob/master/TestResources/Json/QueryExamples/selectProjects.json

### Region search of maf and bed files from the B37 genome build

<pre>http://localhost:8080/GQuery/search?bed=20:4,162,827-4,162,867&ampregExAll=/B37/&ampregExOne=maf\.txt\.gz;bed\.gz</pre>

https://github.com/HuntsmanCancerInstitute/GQuery/blob/master/TestResources/Json/QueryExamples/dataTypeFilter.json

### Search for vcf records that match chr,pos,ref, and one alt, include file headers

Note, you will likely need to encode the tabs by replacing them with %09 if pasting the URL directly in a web browser

<pre>20\t4163144\t.\tC\tA\t.\t.\t. -> 20%094163144%09.%09C%09A%09.%09.%09.</pre>

<pre>http://localhost:8080/GQuery/search?vcf=20%094163144%09.%09C%09A%09.%09.%09.&ampmatchVcf=true&ampregExAll=vcf\.gz&ampincludeHeaders=true</pre>

https://github.com/HuntsmanCancerInstitute/GQuery/blob/master/TestResources/Json/QueryExamples/vcfMatch.json

---
# Building the file system genome index

The first step in getting GQuery up and going with your data is to build the requisit interval tree and data file objects using the USeq QueryIndexer app, see https://github.com/HuntsmanCancerInstitute/USeq

<pre>
> java -jar ~/YourPathTo/GQueryIndexer_0.1.jar

**************************************************************************************
**                               GQuery Indexer: Jan 2019                           **
**************************************************************************************
Builds index files for GQuery by recursing through a data directory looking for bgzip
compressed and tabix indexed genomic data files (e.g. vcf, bed, maf, and custom).
Interval trees are built containing regions that overlap the data sources.
These are used by the GQuery REST service to rapidly identify which data files contain
records that overlap user's ROI. This app is threaded for simultanious file loading
and requires >30G RAM to run on large data collections so use a big analysis server.
Note, relative file paths are saved. So long as the structure of the Data Directory is
preserved, the GQueryIndexer and GQuery REST service don't need to run on the same
file system. Lastly, the indexer will only re index an existing index if the data
files have changed. Thus, run nightly to keep up to date.

Required Params:
-c A bed file of chromosomes and their lengths (e.g. chr21 0 48129895) to use to 
     building the intersection index. Exclude those you don't want to index. For
     multiple builds and species, add all, duplicates will be collapsed taking the
     maximum length. Any 'chr' prefixes are ignored when indexing and searching.
-d A data directory containing bgzipped and tabix indexed data files. Known file
     types include xxx.vcf.gz, xxx.bed.gz, xxx.bedGraph.gz, and xxx.maf.txt.gz. Others
     will be parsed using info from the xxx.gz.tbi index. See
     https://github.com/samtools/htslib . For bed files don't use the -p option,
     use '-0 -s 1 -b 2 -e 3'. For vcf files, vt normalize and decompose_blocksub,
     see http://genome.sph.umich.edu/wiki/Vt. Files may be hard linked but not soft.
-t Full path directory containing the compiled bgzip and tabix executables. See
     https://github.com/samtools/htslib
-i A directory in which to save the index files

Optional Params:
-s One or more directory paths, comma delimited no spaces, to skip when building
     interval trees but make available for data source record retrieval. Useful for
     whole genome gVCFs and read coverage files that cover large genomic regions.
-q Quiet output, no per record warnings.
-b BP block to process, defaults to 250000000. Reduce if out of memory issues occur.
-n Number cores to use, defaults to all

Example for generating the test index using the GitHub GQuery/TestResources files
see https://github.com/HuntsmanCancerInstitute/GQuery

d=/pathToYourLocalGitHubInstalled/GQuery/TestResources
java -jar -Xmx115G GQueryIndexer_0.1.jar -c $d/b37Chr20-21ChromLen.bed -d $d/Data
-i $d/Index -t ~/BioApps/HTSlib/1.10.2/bin/ -s $d/Data/Public/B37/GVCFs 

**************************************************************************************</pre>

Give some thought to how to best structure the data directory.  If you are supporting multiple species/ genome builds then create a sub directory named with the build for easy regular expression matching (e.g. MyDataDir/B37/ , MyDataDir/MM10 , etc.). Likewise for big projects, multi institute programs, and different data source releases, (e.g. /TCGA/, /CvDC/, /GATA4_KO/RNASeq/, /GATA4_KO/ChIPSeq/, etc.).  The two regEx filters used by the GQuery API (all patterns must match and/ or only one must match) are both simple and very effective at selecting just the data sources the user requests, provided the directory structure is well organized. 

Note the TestResource example in the cmd line menu:

<pre>
java  -jar -Xmx8G ~/Code/GQuery/target/GQueryIndexer_0.1.jar -c ~/Code/GQuery/TestResources/b37Chr20-21ChromLen.bed -d ~/Code/GQuery/TestResources/Data -i ~/Code/GQuery/TestResources/NewIndex -t ~/Code/GQuery/TestResources/Htslib_1.10.2/bin/ -s ~/Code/GQuery/TestResources/Data/B37/GVCFs

GQuery Indexer Arguments: -c /Users/u0028003/Code/GQuery/TestResources/b37Chr20-21ChromLen.bed -d /Users/u0028003/Code/GQuery/TestResources/Data -i /Users/u0028003/Code/GQuery/TestResources/NewIndex -t /Users/u0028003/Code/GQuery/TestResources/Htslib_1.10.2/bin/ -s /Users/u0028003/Code/GQuery/TestResources/Data/B37/GVCFs

8 available processors, using 7

Searching for tabix indexes and bgzipped data sources...
	11 Data sources with known formats (vcf.gz, bed.gz, bedgraph.gz, maf.txt.gz)

Creating file id hash...

Checking tbi files for chr content...

Indexing records by chr...
	ChrBlock	#Parsed
	20:0-63025522	16555
	21:0-48129897	8911
	22:0-51304568	1145

Compressing master index...

Saving file objects...

0 Min to parse ~26611 records and build the query index
</pre>

Java version 1.8 works. It takes ~ 1hr to index 10K files with 100M records on a 23 core machine.

---
# Installing the GQuery Web App

### Install Tomcat 7 on a large linux server (>12 cores, > 30G RAM)
e.g. https://www.digitalocean.com/community/tutorials/how-to-install-apache-tomcat-7-on-centos-7-via-yum

Be sure to increase the Xmx and MaxPermSize params to > 30G RAM to avoid out of memory errors. Tomcat 8 likely works but hasn't been tested.

### Modify the latest GQuery-XX.war  

Download, unzip, and modify the following config files to match your environment<br>
https://github.com/HuntsmanCancerInstitute/GQuery/releases

<pre>unzip -q GQuery-XX.war </pre>

WEB-INF/web.xml, set the correct paths and help url
```xml
<param-name>path2DataDir</param-name>
<param-name>path2IndexDir</param-name>
<param-name>tempDir</param-name>
<param-name>helpUrl</param-name>
```

WEB-INF/classes/log4j2.properties, tell GQuery where to write internal messages, good to watch for errors and issues
<pre>log4j2.appender.file.File</pre>

Update the war
<pre>zip -ru GQuery-XX.war META-INF WEB-INF</pre>

### Deploy
Move all the index files generated by the GQueryIndexer app into the path2IndexDir.<br>
Move the updated war file into the tomcat/webapps/ dir. If needed, restart tomcat.<br>
Examine the log4j log file for startup and test issues. Loading of the interval trees can take several minutes.<br>
Test the server: *http://IPAddressOfMyBigServer:8080/GQuery-XX/search?fetchOptions=true* <br>

---
# Configuring GQuery for token based digest authentication

### Enable digest authentication in tomcat, see the WEB-INF/web.xml doc for an example, details:
https://techannotation.wordpress.com/2012/07/02/tomcat-digestauthentication/

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

### Create a tab delimited userGroup.txt file and place it someplace secure and readable by tomcat, e.g. the WEB-INFO folder:
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

### Start-up tomcat and look in the log4j2 output for any error messages
Note, if authorizationEnable is set to true, several checks are performed.  If any fail, the entire QueryService is disabled.

### Request a key token from the digest protected service, these expire after the WEB-INF/web.xml defined time:
http://localhost:8080/GQuery/fetchKey

Provide the userName and pw.  The key is returned in the body, or if so configured, in the WEB-INFO/web.xml doc under the protected Authorization header. 

Test with curl:
<pre>
curl --user Obama:thankYou --digest http://localhost:8080/GQuery/fetchKey -D header.txt
</pre>

### Perform a search with the key token
If pasting in a browser, be sure to encode the token:

<pre>http://localhost:8080/GQuery/search?bed=chr22:39281892-39515517&key=tqGT3e4EG%2B1Jnnw%2FRPA9kg%3D%3D%3A62VfVs2UbvrOVCk%2BRL2Dr%2B09NND9DXlP1SDQPUh%2B6bs%3D</pre>

https://raw.githubusercontent.com/HuntsmanCancerInstitute/GQuery/master/TestResources/Json/b37ObamaSearch.json

For guest access skip the key value argments:

<pre>http://localhost:8080/GQuery/search?bed=chr22:39281892-39515517</pre>

https://raw.githubusercontent.com/HuntsmanCancerInstitute/GQuery/master/TestResources/Json/b37GuestSearch.json












