import rice.p2p.commonapi.NodeHandle;
import rice.p2p.scribe.Topic;
import java.io.*;
import java.util.*;

/**
 * Class used for storing the list of intermediate keys obtained from a mapper
 * node. After obtaining these lists from all the mapper nodes, these lists will
 * be combined to obtain the list of unique keys.
 */
class KeysResultChild<K> {
	/* NodeHandle of the mapper node that sent the values */
	NodeHandle nh;

	/* List of keys obtained from the mapper node */
	ArrayList<K> keySet;

	/* Flag to keep track of the completion of input */
	boolean received;
	
	/**
	  * @param nh NodeHandle of the mapper node that sent the values
	  * Other members are populated on receiving the KeysReplyMessage
	  */
	public KeysResultChild (NodeHandle nh) {
		this.nh = nh;
		this.received = false;
		this.keySet = null;
	}
	public ArrayList<K> getKeySet () {
		return keySet;
	}
	public NodeHandle getNodeHandle () {
		return nh;
	}
	public void setKeySet (ArrayList<K> kset) {
		this.keySet = kset;
	}
	public void setReceived () {
		this.received = true;
	}
	public boolean isReceived () {
		return received;
	}
	public String toString () {
		return "NodeHandle: " + nh + ", received: " + received;
	}
}
