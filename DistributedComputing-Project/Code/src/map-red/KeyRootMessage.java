import rice.p2p.commonapi.Message;
import java.util.*;

/* Message used to send the list of unique keys obtained across all
 * the map tasks in-order to inform the reduce tree root about the
 * number of messages it needs to wait for.
 */
class KeyRootMessage<K> implements Message {
	/* List of all unique intermediate keys */
	ArrayList<K> keys;
	
	/* Job ID of the map-reduce job */
  	int jobId;

	/* Default constructor */
	public KeyRootMessage () {
		this.keys = null;
		this.jobId = 0;
	}
	/**
 	  * @param keys List of unique intermediate keys
	  * @param jobId Job ID of the map-reduce job
	  */
	public KeyRootMessage (ArrayList<K> keys, int jobId) {
		this.keys = keys;
		this.jobId = jobId;
	}
	public ArrayList<K> getKeys () {
		return keys;
	}
	public int getJobId () {
		return jobId;
	}
	public int getPriority() {
      		return MAX_PRIORITY;
    	}
}
