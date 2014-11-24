import rice.p2p.commonapi.NodeHandle;
import rice.p2p.commonapi.Message;
import rice.p2p.scribe.Topic;
import java.io.*;
import java.util.*;

/**
 * Message used for sending the results of map task corresponding to a particular
 * key from the mapper nodes to the reducer node the corresponds to the key.
 */
class ReduceInputMessage<K, V> implements Message {
	/* Intermediate key corresponding to the list of values being sent.
	 * This is required as a single reducer node may be responsible for
	 * multiple intermediate keys.
	 */
	K key;

	/* List of intermediate values obtained from a map task */
	ArrayList<V> values;

	/* NodeHandle of the mapper node */
	NodeHandle nh;

	/* Topic of the intermediate-reduce tree */
	Topic t;

  	/* Job ID of the map-reduce job */
  	int jobId;

	public ReduceInputMessage () {
		this.key = null;
		this.values = null;
		this.nh = null;
		this.t = null;
		this.jobId = 0;
	}
	/**
 	  * @param key Intermediate key
	  * @param values Intermediate values for the key
	  * @param nh NodeHandle of mapper node
	  * @param t Topic of the intermediate-reduce tree
	  * @param jobId Job ID of the map-reduce job
	  */
	public ReduceInputMessage (K key, ArrayList<V> values, NodeHandle nh, Topic t, int jobId) {
		this.key = key;
		this.values = values;
		this.nh = nh;
		this.t = t;
		this.jobId = jobId;
	}
	public ArrayList<V> getValues () {
		return values;
	}
	public K getKey () {
		return key;
	}
	public NodeHandle getNodeHandle () {
		return nh;
	}
	public Topic getTopic () {
		return t;
	}
	public int getJobId () {
		return jobId;
	}
	public int getPriority() {
      		return MAX_PRIORITY;
    	}
}
