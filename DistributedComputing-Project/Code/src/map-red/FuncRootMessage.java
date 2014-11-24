import rice.p2p.commonapi.NodeHandle;
import rice.p2p.commonapi.Message;
import java.io.*;
import java.util.*;

/* 
 * Message used to pass the user's map-reduce functions to the
 * roots of the map and reduce trees. The nodehandles of the
 * members of the map tree (nhs) is also kept in this message
 * as the list is required for the reduce nodes to wait for
 * the complete input, since the implementation is a synchronous
 * map-reduce implementation.
 */
class FuncRootMessage implements Message {
	/* JAR file containing the user MapReduce classes */
  	String fileName;

	/* JAR file contents */
	byte[] fileBytes;

	/* List of nodes that are members of the map scribe tree */
	ArrayList<NodeHandle> nhs;

	/* Job id for the map-reduce job */
	int jobNum;

	public FuncRootMessage () {
		this.fileBytes = null;
		this.fileName = null;
		this.nhs = null;
		this.jobNum = 0;
	}
	
	/**
   	  * The user supplied JAR file is read and the byte array is
	  * populated with the contents.
 	  * 
 	  * @param fileName User JAR file name
	  * @param nhs NodeHandles of the map scribe tree members
	  * @param jobNum Job ID of the map-reduce job
	  */
	public FuncRootMessage (String filePath, String fileName, ArrayList<NodeHandle> nhs,
				int jobNum) {
		this.fileName = fileName;

		File file, destination;
		file = new File(filePath + fileName) ;
		
		try {
            		this.fileBytes = new byte[(int)(file.length())];
			System.out.println("File length = " + (int)(file.length()));

            		FileInputStream jis = new FileInputStream(file);
			
            		int count = 0;
            		count = jis.read(fileBytes);
			if (count <= 0 || count != file.length()) {
				System.out.println ("Didn't read file properly!!!!!!!!!!!!");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		this.nhs = nhs;
		this.jobNum = jobNum;
	}
	public byte[] getFileBytes () {
		return fileBytes;
	}
	public String getFileName () {
		return fileName;
	}
	public int getJobNum () {
		return jobNum;
	}
	public ArrayList<NodeHandle> getNodeHandles () {
		return nhs;
	}
	public int getPriority() {
      		return MAX_PRIORITY;
    	}
}
