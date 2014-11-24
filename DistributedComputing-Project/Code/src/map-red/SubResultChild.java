import rice.p2p.commonapi.NodeHandle;
import rice.p2p.scribe.Topic;
import java.io.*;
import java.util.*;

/* FIXME */
/**
 * Class used for storing the list of intermediate keys obtained from a mapper
 * node. After obtaining these lists from all the mapper nodes, these lists will
 * be combined to obtain the list of unique keys.
 */
class SubResultChild<K> {
	/* List of keys obtained from the mapper node */
	K key;

	/* Flag to keep track of the completion of input */
	boolean received;
	
	/**
	  * @param key
	  * Other members are populated on receiving the KeysReplyMessage
	  */
	public SubResultChild (K key) {
		this.received = false;
		this.key = key;
	}
	public K getKey () {
		return key;
	}
	public void setReceived () {
		this.received = true;
	}
	public boolean isReceived () {
		return received;
	}
	public String toString () {
		return "received: " + received;
	}
}
