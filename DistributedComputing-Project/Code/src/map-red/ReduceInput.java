import rice.p2p.commonapi.NodeHandle;
import rice.p2p.scribe.Topic;
import java.io.*;
import java.util.*;

/**
 * Class used for storing the list of values obtained from a mapper node
 * for a particular key. This is a part of the HashMap rim stored in the
 * reducer that maintains the mapping from the key to a list of ReduceInput
 * objects.
 */
class ReduceInput<V> {
	/* NodeHandle of the mapper node that generated the values */
	NodeHandle nh;

	/* List of intermediate values to be input to the reducer */
	ArrayList<V> values;

	/* Flag to keep track of the completion of input */
	boolean received;

	/**
 	  * @param nh NodeHandle of the tree node that is expected to send
	  * the list of values.
	  * Other members are populated on receiving the ReduceInputMessage
	  */
	public ReduceInput (NodeHandle nh) {
		this.nh = nh;
		this.received = false;
		this.values = null;
	}
	public ArrayList<V> getValues () {
		return values;
	}
	public NodeHandle getNodeHandle () {
		return nh;
	}
	public boolean isReceived () {
		return received;
	}
	public void setValues (ArrayList<V> vals) {
		this.values = vals;
	}
	public void setReceived () {
		this.received = true;
	}
	public String toString () {
		return "NodeHandle: " + nh + ", received: " + received;
	}
}
