import java.io.*;
import java.util.*;
import rice.p2p.commonapi.NodeHandle;

/**
 * Temporary storage object for storing the results from the map tasks, at
 * the reducer nodes, in case the reducer nodes do not receive the job functions
 * by the time of arrival of the results.
 *
 * FIXME: These objects can be discarded in case of job termination due to any reason.
 * They can also be discarded after a timeout if the reducer does not get initialized
 * by then.
 */
public class MapRedStore {
	ArrayList<NodeHandle> rnhs;
	HashMap<Object, ArrayList<ReduceInput<Object>>> rim;

	public MapRedStore () {
		this.rnhs = null;
		this.rim = null;
	}
	public void setRNHs (ArrayList<NodeHandle> nhs) {
		this.rnhs = nhs;
		boolean flag = false;

		if (isRIMInitialized()) {
			ArrayList<Object> keys = new ArrayList<Object> (this.getReceivedKeysRIM());

			for (Object key: keys) {
				ArrayList<ReduceInput<Object>> rd = this.rim.get(key);

				/* FIXME: Double loop can be avoided */
				for (NodeHandle nh : this.rnhs) {
					flag = false;
					for (ReduceInput rdi : rd) {
						if (rdi.getNodeHandle().equals(nh)) {
							flag = true;
							break;
						}
					}
					if (!flag) {
						rd.add(new ReduceInput<Object>(nh));
					}
    				}
			}
		}
	}
	public boolean isRIMInitialized() {
		return (this.rim != null);
	}
	public void initializeRIM() {
		this.rim = new HashMap<Object, ArrayList<ReduceInput<Object>>>();
	}
	public void putRIMKey (Object k) {
    		ArrayList<ReduceInput<Object>> rd = new ArrayList<ReduceInput<Object>>();

		if (this.rnhs != null) {
	   		for (NodeHandle nh : this.rnhs) {
				rd.add(new ReduceInput<Object>(nh));
    			}
		}
		this.rim.put (k, rd);
	}
	public boolean RIMcontainsKey (Object key) {
		return this.rim.containsKey(key);
	}
	public boolean setReceivedRIMKey (Object k, NodeHandle nh, ArrayList values) {
		boolean finished = true;
		ArrayList<ReduceInput<Object>> rd = this.rim.get(k);

		if (this.rnhs == null) {
			ReduceInput rdi = new ReduceInput<Object>(nh);
			rdi.setValues(values);
			rdi.setReceived();
			rd.add(rdi);
			return false;
		}

		for (ReduceInput rdi : rd) {
			if (rdi.getNodeHandle().equals(nh)) {
				rdi.setValues(values);
				rdi.setReceived();
			}
			if (!rdi.isReceived()) {
				finished = false;
			}
		}
		return finished;
	}
	public Set getReceivedKeysRIM () {
		return this.rim.keySet();
	}
	public boolean receivedFullRIM (Object k) {
		boolean finished = true;
		ArrayList<ReduceInput<Object>> rd = this.rim.get(k);

		for (ReduceInput rdi : rd) {
			if (!rdi.isReceived()) {
				finished = false;
			}
		}
		return finished;
	}
	
	/* FIXME */
	public void setReducerInput (Object k, PastryReducer reducer) {
		ArrayList<ReduceInput<Object>> rd = this.rim.get(k);

		reducer.putInputKey (k);
		for (ReduceInput rdi : rd) {
			reducer.addInputValues (k, rdi.getValues());
		}
	}
}
