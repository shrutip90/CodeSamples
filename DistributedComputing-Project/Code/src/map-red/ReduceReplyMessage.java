import rice.p2p.commonapi.NodeHandle;
import rice.p2p.commonapi.Message;
import java.io.*;
import java.util.*;

/**
 * Message used to pass the key-value pairs obtained from the reduce
 * functions for each intermediate key, to the reduce topic root node.
 * One such message is passed to the reduce root after each reduce
 * function invocation.
 */
class ReduceReplyMessage<K,V> implements Message {
	/* Input Key used for the reduce function */
	K key;

	/* List of key-value pairs obtained from the reduce function */
	ArrayList<Pair<K, V>> KV_List;

  	/* Job ID of the map-reduce job */
  	int jobId;

	/* Default constructor */
	public ReduceReplyMessage () {
		this.key = null;
		this.KV_List = null;
		this.jobId = 0;
	}
	/**
 	  * @param key Intermediate key
	  * @param list List of key-value pairs obtained from the reduce function
	  * @param jobId Job ID of the map-reduce job
	  */
	public ReduceReplyMessage (K key, ArrayList<Pair<K,V>> list, int jobId) {
		this.key = key;
		this.KV_List = list;
		this.jobId = jobId;
	}
	public ArrayList<Pair<K,V>> getKVlist() {
		return KV_List;
	}
	public K getKey () {
		return key;
	}
	public int getJobId () {
		return jobId;
	}
	public int getPriority() {
      		return MAX_PRIORITY;
    	}
}
