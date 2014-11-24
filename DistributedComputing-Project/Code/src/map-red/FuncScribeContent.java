import rice.p2p.commonapi.NodeHandle;
import rice.p2p.scribe.ScribeContent;
import java.io.*;
import java.util.*;

public class FuncScribeContent implements ScribeContent {
	/* The source of this content. */
  	NodeHandle from;
  
  	/* Job ID of the map-reduce job */
  	int jobNum;
  	byte[] fileBytes;
  	String fileName;
  	ArrayList<NodeHandle> nhs;
  
  	/*
   	 * @param from Who sent the message.
  	 * @param jobNum Job ID of the map-reduce job
  	 */
  	public FuncScribeContent(NodeHandle from, int jobNum, String fileName, byte[] fileBytes) {
    		this.from = from;
	    	this.jobNum = jobNum;
		this.fileName = fileName;
		this.fileBytes = fileBytes;
		this.nhs = null;
  	}
  	public void setNodeHandles (ArrayList<NodeHandle> nhs) {
		this.nhs = nhs;
  	}
  	public String toString() {
    		return "FuncScribeContent job#"+jobNum+" from "+from;
  	}  
}
