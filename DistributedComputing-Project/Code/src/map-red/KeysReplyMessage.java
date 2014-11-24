import rice.p2p.commonapi.NodeHandle;
import rice.p2p.commonapi.Message;
import java.io.*;
import java.util.*;

/**
 * Message used for sending the results of each map task from
 * the mapper nodes to the map tree root.
 */
class KeysReplyMessage implements Message {
	/* List of keys obtained from the map task at a particular map node */
	ArrayList keySet;

	/* NodeHandle of the map node that performed the map task */
	NodeHandle nh;

	/* Job ID of the map-reduce job */
	int jobId;

	public KeysReplyMessage () {
		this.keySet = null;
		this.nh = null;
		this.jobId = 0;
	}
	/**
 	  * @param kset Set of keys generated from the map task
	  * @param nh NodeHandle of the map node
	  * @param jobId Job ID of the map-reduce job
	  */
	public KeysReplyMessage (Set kset, NodeHandle nh, int jobId) {
		this.keySet = new ArrayList(kset);
		this.nh = nh;
		this.jobId = jobId;
	}
	public ArrayList getKeySet () {
		return keySet;
	}
	public NodeHandle getNodeHandle () {
		return nh;
	}
	public int getJobId () {
		return jobId;
	}
	public int getPriority() {
      		return MAX_PRIORITY;
    	}
}
