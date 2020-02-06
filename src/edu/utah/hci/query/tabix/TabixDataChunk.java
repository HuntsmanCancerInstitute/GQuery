package edu.utah.hci.query.tabix;

import java.io.File;
import java.util.ArrayList;

import edu.utah.hci.query.QueryRequest;

public class TabixDataChunk {
	
	private File tabixFile;
	private ArrayList<TabixDataQuery> queries;
	private QueryRequest queryRequest;
	
	public TabixDataChunk (File tabixFile, ArrayList<TabixDataQuery> queries, QueryRequest queryRequest){
		this.tabixFile = tabixFile;
		this.queries = queries;
		this.queryRequest = queryRequest;
	}

	public File getTabixFile() {
		return tabixFile;
	}

	public void setTabixFile(File tabixFile) {
		this.tabixFile = tabixFile;
	}

	public ArrayList<TabixDataQuery> getQueries() {
		return queries;
	}

	public void setQueries(ArrayList<TabixDataQuery> queries) {
		this.queries = queries;
	}

	public QueryRequest getQueryRequest() {
		return queryRequest;
	}

	public void setQueryRequest(QueryRequest queryRequest) {
		this.queryRequest = queryRequest;
	}
	
}
