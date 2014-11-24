import rice.p2p.commonapi.NodeHandle;
import java.io.*;
import java.util.*;

/**
 * Class used for storing the list of key-value pairs obtained from a reducer
 * node. The root of the reduce tree maintains these objects until all the
 * reducer nodes complete and used them to write the key-value pairs to the
 * final output file.
 */
class ReduceResultChild<K, V> {
	/* Input Key used for the reduce function */
	K key;

	/* List of key-value pairs obtained from the reduce function */
	ArrayList<Pair<K,V>> KV_List;
 
	/* Flag to keep track of the completion of input */
	boolean received;
	
	/**
 	  * @param key Key corresponding to the key-value pairs output from
	  * the reduce function.
	  * Other members are populated on receiving the ReduceReplyMessage
	  */
	public ReduceResultChild (K key) {
		this.key = key;
		this.received = false;
		this.KV_List = null;
	}
	public ArrayList<Pair<K,V>> getKVlist () {
		return KV_List;
	}
	public K getKey () {
		return key;
	}
	public boolean isReceived () {
		return received;
	}
	public void setKVlist (ArrayList<Pair<K,V>> list) {
		this.KV_List = list;
	}
	public void setReceived () {
		this.received = true;
	}
	public String toString () {
		return "Key: " + key + ", received: " + received;
	}
}
