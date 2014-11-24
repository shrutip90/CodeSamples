import java.io.*;
import java.util.*;

import rice.p2p.commonapi.*;
import rice.p2p.commonapi.Application;
import rice.p2p.commonapi.CancellableTask;
import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.Message;
import rice.pastry.messaging.*;
import rice.p2p.commonapi.NodeHandle;
import rice.p2p.commonapi.RouteMessage;
import rice.p2p.scribe.Scribe;
import rice.p2p.scribe.ScribeClient;
import rice.p2p.scribe.ScribeContent;
import rice.p2p.scribe.ScribeImpl;
import rice.p2p.scribe.Topic;
import rice.pastry.commonapi.PastryIdFactory;
import rice.environment.Environment;
import rice.pastry.*;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.standard.RandomNodeIdFactory;
import rice.pastry.transport.TransportPastryNodeFactory;

/**
 * Main mapper class provided in the user library. The user extends this library and
 * can override the 'map' and other functions in it. The class contains the state
 * related to the map operations and the messages communicated between the map-tree
 * root and the mapper nodes. It also contains functions to merge the intermediate
 * keys and proide the key-topic map to the mapper nodes.
 *
 * We do not provide a Combiner or Partitioner function presently.
 */
public class PastryMapper <K_IN, V_IN, K_OUT, V_OUT> {
	Context<K_OUT, V_OUT> cxt;
	private Class InputKeyType;
	private Class InputValueType;
	private Class OutputKeyType;
	private Class OutputValueType;

	ArrayList<KeysResultChild<K_OUT>> krc;
	ArrayList<SubResultChild<K_OUT>> sub;
	ArrayList<K_OUT> unionKeys;
	KeyTopicMap<K_OUT> ktm;
	FuncScribeContent jobMessage;

	public PastryMapper () {
		this.cxt = new Context<K_OUT, V_OUT>();
		this.krc = null;
		this.sub = null;
		this.ktm = null;
		this.unionKeys = null;
		this.jobMessage = null;
	}
	public void setPastryMapperClass () {	
	}

	public Class getInputKeyClass() {
		return this.InputKeyType;
	}
	public Class getInputValueClass() {
		return this.InputValueType;
	}
	public Class getOutputKeyClass() {
		return this.OutputKeyType;
	}
	public Class getOutputValueClass() {
		return this.OutputValueType;
	}
	public void setInputKeyClass(Class<K_IN> type) {
		this.InputKeyType = type;
	}
	public void setInputValueClass(Class<V_IN> type) {
		this.InputValueType = type;
	}
	public void setOutputKeyClass(Class<K_OUT> type) {
		this.OutputKeyType = type;
	}
	public void setOutputValueClass(Class<V_OUT> type) {
		this.OutputValueType = type;
	}

	/* FIXME: I should be an identity function */
	public void map(K_IN key, V_IN value, Context context) throws IOException, InterruptedException {
	}
	public Context<K_OUT, V_OUT> getContext() {
		return this.cxt;
	}
	public void setContext(Context<K_OUT, V_OUT> context) {
		this.cxt = context;
	}
	public void storeJobMessage (FuncScribeContent msg) {
		this.jobMessage = msg;
	}
	/* FIXME */
	public void setReducerClasses (PastryReducer reducer) {
		reducer.setKeyClass(this.OutputKeyType);
		reducer.setValueClass(this.OutputValueType);
	}
	public void initializeKRC (ArrayList<NodeHandle> nhs) {
		this.krc = new ArrayList<KeysResultChild<K_OUT>>();

   		for (NodeHandle nh : nhs) {
			this.krc.add(new KeysResultChild<K_OUT>(nh));
    		}
	}
	public boolean isKRCinitialized() {
		return (this.krc != null);	
	}

	public boolean setReceivedKRC (NodeHandle nh, ArrayList<K_OUT> keySet) {
		boolean finished = true;

		for (KeysResultChild<K_OUT> k : this.krc) {
			if (k.getNodeHandle().equals(nh)) {
				k.setKeySet(keySet);
				k.setReceived();
			}
			if (!k.isReceived()) {
				finished = false;
			}
		}
		return finished;
	}
	public int makeUnionKRC() {
		this.unionKeys = new ArrayList<K_OUT>();

		for (KeysResultChild<K_OUT> k : this.krc) {
			ArrayList<K_OUT> keys = k.getKeySet();
			for (K_OUT key : keys) {
				if (!this.unionKeys.contains(key)) {
					this.unionKeys.add (key);
				}
			}
		}
		return this.unionKeys.size();
	}

	/**
	 * Assigns one topic string per intermediate key.
	 * This function can be modified to partition the keys into R groups
	 * using a partitioning function, that can be user-defined, in order
	 * to limit the number of recuce node and the corresponding 
	 * intermediate trees.
	 */
	public KeyTopicMap<K_OUT> makeKTMmsg(int jobId) {
		int i = 0;
		KeyTopicMap<K_OUT> ktm_local = new KeyTopicMap<K_OUT>(jobId);

		for (K_OUT k : this.unionKeys) {
			ktm_local.insertKey (k, "key" + i);
			i++;
		}
		return ktm_local;
	}
	public KeyRootMessage getKeyRootMsg(int jobId) {
		KeyRootMessage<K_OUT> msg = new KeyRootMessage<K_OUT> (this.unionKeys, jobId);
		return msg;
	}
	public void printKTM (KeyTopicMap ktm) {
		HashMap<K_OUT, String> map = ktm.getMap();
		
		Iterator iterator = map.keySet().iterator(); 
		System.out.println ("Printing KTM"); 
        	while (iterator.hasNext()) {
			K_OUT k = (K_OUT)iterator.next();
			String topic = map.get(k);
			System.out.println ("Key: " + k + "Topic: " + topic);
		}
	}
	public void setKTM (KeyTopicMap ktm) {
		this.ktm = ktm;
	}
	public HashMap getKTM_HashMap () {
		if (this.ktm == null) {
			return null;
		} else {
			return this.ktm.getMap();
		}
	}
	public String getKTM_HashMapKey (Object key) {
		K_OUT k = (K_OUT) key;
		if (this.ktm == null) {
			return null;
		} else {
			return (String)((this.ktm.getMap()).get(k));
		}
	}
	public void initializeSUB () {
		this.sub = new ArrayList<SubResultChild<K_OUT>>();

   		for (K_OUT k: unionKeys) {
			this.sub.add(new SubResultChild<K_OUT>(k));
    		}
	}
	public boolean isSUBinitialized() {
		return (this.sub != null);	
	}

	public boolean setReceivedSUB(Object key) {
		K_OUT k = (K_OUT) key;
		boolean finished = true;

		for (SubResultChild r : this.sub) {
			if (r.getKey().equals(key)) {
				r.setReceived();
			}
			if (!r.isReceived()) {
				finished = false;
			}
		}
		return finished;
	}

	public ReduceInputMessage getRIMmsg (Object key, NodeHandle nh, Topic t, int jobId) {
		K_OUT k = (K_OUT) key;

		ReduceInputMessage<K_OUT, V_OUT> msg = null;
		ArrayList<V_OUT> empty = new ArrayList<V_OUT>();

		if (getContext().containsKey(k)) {
			msg = new ReduceInputMessage<K_OUT, V_OUT>(k, getContext().getValues(k), nh,
									t, jobId);
		} else {
			msg = new ReduceInputMessage<K_OUT, V_OUT>(k, empty, nh, t, jobId);
		}
		return msg;
	}
}
