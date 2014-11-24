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

public class PastryReducer <K_IN, V_IN, K_OUT, V_OUT> {
	Context<K_OUT, V_OUT> cxt;
	private Class KeyType;
	private Class ValueType;

	HashMap<K_IN, ArrayList<V_IN>> iMap;
	ReduceReplyMessage<K_OUT, V_OUT> rMsg;
	ArrayList<ReduceResultChild<K_OUT, V_OUT>> rrc;
	ArrayList<K_OUT> iKeys;

	public PastryReducer () {
		this.cxt = new Context<K_OUT, V_OUT>();
		this.iMap = null;
		this.rMsg = null;
		this.rrc = null;
		this.iKeys = null;
	}

	/* FIXME: I should be an identity function */
	public void reduce(K_IN key, Iterable<V_IN> values, Context context) 
						throws IOException, InterruptedException {
	}
	public Class getKeyClass() {
		return this.KeyType;
	}
	public Class getValueClass() {
		return this.ValueType;
	}
	public void setKeyClass(Class<K_IN> type) {
		this.KeyType = type;
	}
	public void setValueClass(Class<V_IN> type) {
		this.ValueType = type;
	}
	public Context<K_OUT, V_OUT> getContext() {
		return this.cxt;
	}
	public Context<K_OUT, V_OUT> getNewContext() {
		return (new Context<K_OUT, V_OUT>());
	}
	public void setContext(Context<K_OUT, V_OUT> context) {
		this.cxt = context;
	}
	public boolean isInitializedIMap () {
		return (this.iMap != null);
	}
	public void initializeIMap () {
		this.iMap = new HashMap<K_IN, ArrayList<V_IN>>();
	}
	public void putInputKey (Object key) {
		if (!this.iMap.containsKey((K_IN)key)) {
			this.iMap.put((K_IN)key, new ArrayList<V_IN>());
		}
	}
	public void addInputValues (Object key, ArrayList<V_IN> vals) {
		if (!this.iMap.containsKey((K_IN)key)) {
			new Exception ("PastryReducer/addInputValues ERROR: No key in iMap").
										printStackTrace();
		} else {
			(this.iMap.get((K_IN) key)).addAll (vals);
		}
	}
	public ArrayList getValuesByKey (Object key) {
		if (!this.iMap.containsKey((K_IN)key)) {
			return null;
		} else {
			return (this.iMap.get((K_IN) key));
		}
	}
	public ReduceReplyMessage getRMsg (Object key, ArrayList<Pair<K_OUT,V_OUT>> list,
									 NodeHandle nh, int jobId) {
		return (new ReduceReplyMessage((K_OUT)key, list, jobId));
	}
	public void setIKeys (ArrayList<K_OUT> keyList) {
		this.iKeys = keyList;

		if (isRRCinitialized()) {
			for (K_OUT key: keyList) {
				if (!this.rrc.contains(key)) {
					this.rrc.add (new ReduceResultChild<K_OUT, V_OUT>(key));
				}
			}
		}
	}
	public void initializeRRC () {
		this.rrc = new ArrayList<ReduceResultChild<K_OUT, V_OUT>>();

		if (this.iKeys != null) {
			for (K_OUT key: iKeys) {
				this.rrc.add (new ReduceResultChild<K_OUT, V_OUT>(key));
			}
		}
	}
	public boolean isRRCinitialized() {
		return (this.rrc != null);	
	}

	public boolean setReceivedRRC (Object key, ArrayList<Pair<K_OUT, V_OUT>> list) {
		boolean finished = true;
		K_OUT k = (K_OUT) key;

		if (this.iKeys == null) {
			ReduceResultChild<K_OUT, V_OUT> r = new ReduceResultChild<K_OUT, V_OUT>(k);
			r.setKVlist(list);
			r.setReceived();
			this.rrc.add (r);
			return false;
		}
		for (ReduceResultChild<K_OUT, V_OUT> r : rrc) {
			if (r.getKey().equals(key)) {
				r.setKVlist(list);
				r.setReceived();
			}
			if (!r.isReceived()) {
				finished = false;
			}
		}
		return finished;
	}
	public boolean receivedFullRRC () {
		boolean finished = true;

		for (ReduceResultChild<K_OUT, V_OUT> k : rrc) {
			if (!k.isReceived()) {
				finished = false;
			}
		}
		return finished;
	}
	public void writeOutput () {
		File destination = new File ("ReduceOutput.txt");
		
		try {
	    		FileOutputStream fos = new FileOutputStream(destination);
			Writer writer = new BufferedWriter(new OutputStreamWriter(fos));

			for (ReduceResultChild<K_OUT, V_OUT> k : rrc) {
				ArrayList<Pair<K_OUT, V_OUT>> list = k.getKVlist();

				for (Pair<K_OUT, V_OUT> p : list) {
		          		String key = p.getKey().toString();
		          		String val = p.getValue().toString();
	    				writer.write("<" + key + ", " + val + ">\n");
				}
			}
            		writer.flush();
	    		writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
