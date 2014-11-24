import rice.p2p.commonapi.NodeHandle;
import rice.p2p.commonapi.Message;
import java.io.*;
import java.util.*;

/* FIXME */
/**
 * Message used for sending the results of each map task from
 * the mapper nodes to the map tree root.
 */
class SubscribedReduceMessage implements Message {
	/* List of keys obtained from the map task at a particular map node */
	Object key;

  	/* Job ID of the map-reduce job */
  	int jobId;

	public SubscribedReduceMessage () {
		this.key = null;
		this.jobId = 0;
	}
	/**
 	  * @param kset Set of keys generated from the map task
	  * @param nh NodeHandle of the map node
	  * @param jobId Job ID of the map-reduce job
	  */
	public SubscribedReduceMessage (Object key, int jobId) {
		this.key = key;
		this.jobId = jobId;
	}
	public Object getKey () {
		return key;
	}
	public int getJobId () {
		return jobId;
	}
	public int getPriority() {
      		return MAX_PRIORITY;
    	}
}
