# GQuery
GQuery is a software platform that enables rapid querying of large numbers (>10K) of bgzip compressed, tabix indexed genomic data files, e.g. vcf, gvcf, maf, bed, bedGraph, etc. from multiple species with different genome builds without the need to write custom file parsers.

It includes three Java applications:

GQueryIndexer - a command line tool for building chromosome indexes that associate genomic coordinates with data files that contain intersecting records. 

GQuery CLI - a command line tool for executing queries locally on GQuery indexed data directories.

GQuery API - a RESTful web application API service for executing queries on remote servers.

Each query triggers an initial intersection of each user's regions of interest against the GQuery chromosome indexes to identify data files that contain intersecting records. Regular expression filters are provided to limit which directory paths are searched and which file types are returned.  Often this is all that is needed for a basic query.  If requested, a second tabix search is used to fetch the actual intersecting records from the data files. These records can also be filtered using regular expressions.  

This approach of searching genomic coordinate indexes for intersecting data files combine with tabix data record retrieval is an excellent approach to address the random range query problem. In our benchmarking tests, it significantly out performs both relational database (MySQL) and NoSQL (MongoDB) approaches. Moreover, use of the widely adopted, bgzip compressed, tabix indexed file format (https://www.htslib.org/doc/tabix.html) eliminates the need to duplicate the data source content and requirement to create and maintain custom db data file importers that can accommodate a large set of disparate file formats and format flavors.  If you can tabix index it, you can search it.

Getting up and going with GQuery is a simple three step process: download the latest jar files, build the chromosome data file indexes, and execute queries using the CLI.  

For those looking to provide search capability via a web application, deploy the GQuery RESTful web API.  This is especially useful when searching needs to be restricted to subsets of the data, e.g. IRB approved, unpublished project data.



---
# Step 1: Build the Chromosome Data File Indexes with the GQueryIndexer

The first step in getting GQuery up and going with your data is to build the chromosome genome indexes with the GQueryIndexer application. It is multi-threaded and junit tested.

Give some thought to how to best structure the base Data directory for your group.  If you are working with multiple species and genome builds then create a sub directory named with the build for easy directory path regular expression matching (e.g. Data/B37/, Data/Hg38, Data/MM10, etc.). Likewise create directories for each major project (e.g. Data/Hg38/TCGA, Data/Hg38/AVATAR, Data/Hg38/Clinical/Foundation) and particular data types (e.g. Data/Hg38/AVATAR/Germline, Data/Hg38/AVATAR/Somatic/Vcf, Data/Hg38/AVATAR/Somatic/Cnv). Keep in mind that a chromosome genome index is created in each directory that contains xxx.gz.tbi files.  Thus the most optimal indexing strategy is to soft link or copy over 100's of files into the same directory. The worst strategy is to have many directories with just a few data files. Lastly, directory path regular expressions are used by GQuery to both restrict  what a user can search and to speed up the searching so structure the base Data directory in a way that best meets your needs. 

<pre>
> java -jar -Xmx30G ~/YourPathTo/GQueryIndexer.jar

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

**************************************************************************************</pre>






---
# Step 2: Run Local Queries with the GQuery Command Line Interface

Run queries locally using the GQueryCLI application. It is multi-threaded and junit tested. Results are returned in JSON. Specify one or more regions of interest in bed or vcf format.  Use the path and file name regular expressions to speed up and limit what files are searched.  

For example, if you're only interested in Germline mutations and your base data dir is organized into Data/Germline and Data/Somatic files, use a dir path regex '-p Data/Germline' to only search the germline data.  

Likewise, if you are only interested in actual vcf variants, specify a file name regex '-n vcf.gz'

Lastly, fetching the actual intersecting data records from each file can be a computationally intensive process so only use the '-d' fetch data option after you've narrowed down your search with path and file name regexes.  In many cases it's not needed, for example if you're only interested in identifying patients with a BRCA1 mutation, then skip the '-d' option and just parse the 'source' names from the JSON output.

<pre>
> java -jar -Xmx30G ~/YourPathTo/GQueryCLI.jar

**************************************************************************************
**                     GQuery Command Line Interface 0.1: Oct 2020                  **
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
# Example Usage with Test Resource Files

This web app is built using the Java Jersey JAX RESTful API framework. It is JUnit tested and deployed on Apache Tomcat for optimized performance. Results are returned in JSON.

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



# Installing the GQuery Web App
### See also https://github.com/HuntsmanCancerInstitute/GQuery/blob/master/Misc/queryNotes.txt

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

WEB-INF/classes/log4j2.properties, tell GQuery where to write internal messages, good to watch for errors and issues.

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












