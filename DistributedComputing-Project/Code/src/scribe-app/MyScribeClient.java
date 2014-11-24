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
import rice.p2p.scribe.messaging.*;

import rice.environment.Environment;
import rice.pastry.*;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.standard.RandomNodeIdFactory;
import rice.pastry.transport.TransportPastryNodeFactory;

import java.io.*;
import java.util.*;
import java.lang.reflect.Method;

/*
 * Every node that stores the application data subscribes to the map topic.
 * The nodes also subscribe to some intermediate topics and a reduce topic during
 * the execution of the map-reduce job. The trees would be cleaned up at the end
 * of the job.
 *
 * The nodes are assumed to contain all the input data to the map task already.
 * This allows for processing the data locally without the need for any data
 * movement.
 *
 * We also assume that the data is not replicated on the nodes. If the nodes
 * contain replicated data, the code needs to be modified accordingly to avoid
 * duplicate computations / results and to shift tasks to replicated nodes in
 * case of failures.
 *
 * We currently assume that intermediate key-value pairs produced by the map
 * function on each node will fit in memory. Similar assumptions are made for
 * the final output key-value pairs. The model can be easily extended to handle
 * larger inputs by writing these to disk.
 */
public class MyScribeClient implements ScribeClient, Application {

  /* The message sequence number.  Will be incremented after each send. */
  int seqNum = 0;
  
  /* Handle to a scribe impl */
  Scribe myScribe;
  
  /* Map and reduce topics */
  Topic mapTopic;
  Topic reduceTopic;

  /* The Endpoint represents the underlieing node. */
  protected Endpoint endpoint;

  PastryNode node;
  int nodeNum;

  Hashtable<Integer,Job> jobMap;
  static int jobNum = 0;
  
  /**
   * The constructor for this scribe client.  It will construct the ScribeApplication.
   *
   * @param node the PastryNode
   * @param nodeNum Current node number in the list of nodes, to identify the input file
   * 		    directory corresponding to the node. This will not be required in an
   *		    actual distributed scenario where the node will have only its data
   * 		    directory local to itself.
   */
  public MyScribeClient(Node node, int nodeNum) {
    	this.endpoint = node.buildEndpoint(this, "myinstance");

    	myScribe = new ScribeImpl(node,"myScribeInstance");

	/**
	 * All the nodes are aware of the map and reduce topics to keep things simple.
	 * Note that they do not need to subscribe though.
	 */
    	mapTopic = new Topic(new PastryIdFactory(node.getEnvironment()), "example topic");
    	reduceTopic = new Topic(new PastryIdFactory(node.getEnvironment()), "reduce");

    	System.out.println("mapTopic = "+mapTopic);

    	this.nodeNum = nodeNum + 1;
    	this.node = (PastryNode) node;

	jobMap = new Hashtable<Integer, Job>();	

    	endpoint.register();
  }

  public void subscribe() {
   	myScribe.subscribe(mapTopic, this); 
  }

  public void logMessage (String msg) {
	System.out.println (msg);
  }
  
  /**
   * Part of the Application interface.  Will receive pastry messages and
   * directly routed messages for a particular pastry destination node.
   */
  public void deliver(Id id, Message message) {
     	if (message instanceof FuncRootMessage) {
		/**
		 * Message details:
		 * ******************************
		 * Sent By: Any node that performs jobStart
		 * Received at: Map and reduce topic root nodes
		 *
		 * Map root and reduce root receive FuncRootMessage with the user
		 * supplied jar with map and reduce functions.
		 */
    		ArrayList<NodeHandle> nhs = ((FuncRootMessage)message).getNodeHandles();

		String fileName = ((FuncRootMessage)message).getFileName();
		File destination = new File (fileName);
		byte[] fileBytes = null;
		int jobId = ((FuncRootMessage)message).getJobNum();
    		
		logMessage("Node "+endpoint.getLocalNodeHandle()+" received FuncRootMessage " +
			   " with job "+jobId);
		
		try {
            		fileBytes = ((FuncRootMessage)message).getFileBytes();
	    		FileOutputStream fos = new FileOutputStream(destination);
			
	    		fos.write(fileBytes, 0, fileBytes.length);
            		fos.flush();
	    		fos.close();
		
            		UnzipJar uj = new UnzipJar();
	    		uj.unzipJar (".", fileName);

			Job j = null;
			if (!jobMap.containsKey(jobId)) {
				j = new Job(jobId);
				jobMap.put(jobId, j);
				logMessage("New Job created at map or reduce root with jobId: " + jobId);
			} else {
				j = jobMap.get(jobId);
			}

			FuncScribeContent jobMessage = new FuncScribeContent(
					endpoint.getLocalNodeHandle(), jobId, fileName, fileBytes);

	    		/* Initialize the j.mapper and j.reducer objects for the root nodes */
	    		if (isRoot(mapTopic)) {
				Class cls = Class.forName("Map");
				Object obj = cls.newInstance();
		
				j.mapper = (PastryMapper)obj;
    				j.mapper.initializeKRC(nhs);
				
				/* Multicast the functions to the j.mapper nodes. */
    				myScribe.publish(mapTopic, jobMessage);

				/**
				 * Reducer nodes subscribe to the reduce topic only after the 
				 * map phase finishes. The code will then be published on the 
				 * reduce tree by the root of the map topic. The map 
				 * topic root will store the jobMessage until then.
				 */
				jobMessage.setNodeHandles (nhs);
				j.mapper.storeJobMessage (jobMessage);
	    		} else if (isRoot(reduceTopic)) {
				Class cls = Class.forName("Reduce");
				Object obj = cls.newInstance();
		
				j.reducer = (PastryReducer)obj;
	    		} else {
				String err = "ERROR: Received FuncRootMessage at " +
					     "neither map-root nor reduce-root";
				logMessage (err);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

    	} else if (message instanceof KeysReplyMessage) {
		/**
		 * Message details:
		 * ******************************
		 * Sent By: Mapper nodes
		 * Received at: Map topic root
		 *
		 * Mapper nodes after completing the map task, send the results to the
		 * map tree root in this message.
		 */
      		try {
			if (!isRoot(mapTopic)) {
				String err = "ERROR: Received KeysReplyMessage at non-map-tree root";
				logMessage (err);
			}
			int jobId = ((KeysReplyMessage)message).getJobId();

			if (!jobMap.containsKey(jobId)) {
				logMessage ("ERROR: KeysReplyMessage for unknown jobId: " + jobId);
			}
			Job j = jobMap.get(jobId);

			/**
			 * Mapper and KRC structures at the map-tree root should have been
			 * initialized on receiving FuncRootMessage at job start.
			 */
			if (j.mapper == null) {
				logMessage ("ERROR: KeysReplyMessage: Mapper not initialized "+ 					this.endpoint.getLocalNodeHandle() + " jobId: " + jobId);
			}
			if (!j.mapper.isKRCinitialized()) {
				logMessage ("ERROR: Map root KRC not initialized" + 						this.endpoint.getLocalNodeHandle() + " jobId: " + jobId);
			}
			NodeHandle nh = ((KeysReplyMessage)message).getNodeHandle();

			boolean finished = j.mapper.setReceivedKRC (nh,
							((KeysReplyMessage)message).getKeySet());
			logMessage ("Map-root received intermediate keys from NH = " + nh);
			
			if (finished) {
				/**
				 * This is the last j.mapper node. We now combine all the sets
				 * of keys received and generate the set of unique keys.
				 */
				logMessage ("Map root finished receiving intermediate keys");

				int numKeys = j.mapper.makeUnionKRC();
				logMessage ("makeUnionKRC: Total number of keys: " + numKeys);

				KeyTopicMap ktMap = j.mapper.makeKTMmsg(jobId);
			
				/**
				 * The set of unique keys is sent to the reduce tree root in
				 * order for it to know when the job completes (on receiving
				 * msgs from j.reducer nodes for all these unique keys).
				 */
				KeyRootMessage msg = j.mapper.getKeyRootMsg(jobId);
				NodeHandle reduceRoot = ((ScribeImpl)myScribe).getRoot(reduceTopic);
				endpoint.route (null, msg, reduceRoot);

				j.mapper.initializeSUB();
	
				/**
				 * A map with unique keys and corresponding topics is multicasted
				 * to all the j.mapper nodes, for them to subscribe to the j.reducer
				 * nodes and send them the results.
				 */
				
				logMessage ("PUBLISHING KEYTOPICMAP........................" + 
							this.endpoint.getLocalNodeHandle());
				myScribe.publish(mapTopic, ktMap);
			}
      		} catch (Exception e) {
			e.printStackTrace();
      		}
	} else if (message instanceof SubscribedReduceMessage) {
		/**
		 * Message details:
		 * ******************************
		 * Sent By: Reducer nodes
		 * Received at: Map topic root
		 *
		 * The j.reducer nodes send this message to the map-tree root after
		 * subscribing to the reduce topic.
		 */
		if (!isRoot(mapTopic)) {
			logMessage ("ERROR: Received SubscribedReduceMessage at " + 
					    "non-map-tree root");
		}
		int jobId = ((SubscribedReduceMessage)message).getJobId();

		if (!jobMap.containsKey(jobId)) {
			logMessage ("ERROR: SubscribedReduceMessage for unknown jobId: " + jobId);
		}
		Job j = jobMap.get(jobId);

		if (!j.mapper.isSUBinitialized()) {
			logMessage ("ERROR: Map root SUB not initialized");
		}
		Object key = ((SubscribedReduceMessage)message).getKey();

		boolean finished = j.mapper.setReceivedSUB (key);
		logMessage ("Reducer node for Key = " + key + " subscribed to reduce topic");
		
		if (finished) {
			/**
			 * All the reducer nodes have finished subscribing to the reduce topic.
			 * Now we publish the code with the user-defined reduce functions.
			 */
			logMessage ("All reducer nodes finished subscribing to reduce topic");
			logMessage ("Publishing code package to reduce topic");
			myScribe.publish(reduceTopic, j.mapper.jobMessage);
		}
	} else if (message instanceof ReduceInputMessage) {
		/**
		 * Message details:
		 * ******************************
		 * Sent By: Mapper nodes
		 * Received at: Reducer node (root for an intermediate key)
		 *
		 * This message containes the values for an intermediate key, produced
		 * from the map task. The j.reducer node stores them until it gets all
		 * the values for a key and the reduce function itself.
		 */
      		try {
			logMessage ("ReduceInputMessage received");
			
			int jobId = ((ReduceInputMessage)message).getJobId();

			Job j = jobMap.get(jobId);
			if (!jobMap.containsKey(jobId)) {
				logMessage ("ReduceInputMessage for unknown jobId: " + jobId);
				j = new Job(jobId);
				jobMap.put (jobId, j);
			} else {
				j = jobMap.get(jobId);
			}

			Class keyClass = j.mapper.getOutputKeyClass();
			Class valClass = j.mapper.getOutputValueClass();

			Object k = (Object)((ReduceInputMessage)message).getKey();

			if (!j.store.isRIMInitialized()) {
				j.store.initializeRIM();

				myScribe.subscribe(reduceTopic, this);
				node.getEnvironment().getTimeSource().sleep(5000);
			}
			if (!j.store.RIMcontainsKey(k)) {
    				j.store.putRIMKey(k);

				/**
				 * Send msg to the map-tree root informing about the
				 * subsciption to reduce topic. When map-tree root receives
				 * a msg from all the j.reducer nodes, it multicasts a msg
				 * with the code for the reduce function.
				 */
				SubscribedReduceMessage msg = new SubscribedReduceMessage(k, jobId);
				
				NodeHandle root = ((ScribeImpl)myScribe).getRoot(mapTopic);
				endpoint.route (null, msg, root);
			}

			NodeHandle nh = ((ReduceInputMessage)message).getNodeHandle();
	
			boolean finished = j.store.setReceivedRIMKey (k, nh,
						 ((ReduceInputMessage)message).getValues());
			
			/**
			 * The code package might not have arrived even after all the values
			 * have arrived. So, we need to check for both. In such a case, the
			 * keys will be checked and processed on receiving the code package.
			 */
			if (finished && (j.reducer != null)) {
	    			logMessage ("Starting reduce");
				Class cls = Class.forName("Reduce");
				Object obj = (Object) j.reducer;

				if (!j.reducer.isInitializedIMap()) {
					j.reducer.initializeIMap();
				}

				j.mapper.setReducerClasses (j.reducer);
				j.store.setReducerInput (k, j.reducer);

				Class noparams[] = {};

				Class keyCls = j.reducer.getKeyClass();
				Class valueCls = j.reducer.getValueClass();

				Context cxt = j.reducer.getNewContext();
		
				ArrayList vals = j.reducer.getValuesByKey (k);
				if (vals == null) {
					logMessage ("PastryReducer/getValuesByKey ERROR: " + 
						    "No key in iMap");
				}
				
				logMessage ("REDUCE function input");
				for (Object v :  vals) {
					logMessage (v.toString());
				}

				/* REDUCE function invocation */
				Method method = cls.getDeclaredMethod("reduce", keyCls, Iterable.class,
										Context.class);
				method.invoke(obj, k, vals, cxt);

				/* TESTME */
				j.reducer.setContext(cxt);			
				ReduceReplyMessage msg = j.reducer.getRMsg (k, cxt.getKVlist(),
							 this.endpoint.getLocalNodeHandle(), jobId);

				/**
				 * Even if the current node is the root of the mapTopic,
				 * currently it sends a message to itself, to keep things simple.
				 */
				NodeHandle root = ((ScribeImpl)myScribe).getRoot(reduceTopic);
				endpoint.route (null, msg, root);
    			}
      		} catch (Exception e) {
			e.printStackTrace();
      		}
    	} else if (message instanceof KeyRootMessage) {
		/**
		 * Message details:
		 * ******************************
		 * Sent By: Map topic root
		 * Received at: Reduce topic root
		 *
		 * This message contains the list for of unique intermediate keys.
		 * This is used for waiting on the keys at the reduce-topic root in
		 * order to know the point of job completion and to write the output.
		 */
		if (!isRoot(reduceTopic)) {
			logMessage ("ERROR: Received KeyRootMessage at " + 
					    "non-reduce-tree root");
		}
		logMessage ("KeyRootMessage received");
		int jobId = ((KeyRootMessage)message).getJobId();

		if (!jobMap.containsKey(jobId)) {
			logMessage ("ERROR: KeyRootMessage for unknown jobId: " + jobId);
		}
		Job j = jobMap.get(jobId);

      		try {
			if (j.reducer == null) {
				logMessage ("ERROR: KeyRootMessage: reducer not initialized");
			}

			if (!j.reducer.isRRCinitialized()) {
				j.reducer.setIKeys(((KeyRootMessage)message).getKeys());
    				j.reducer.initializeRRC();

				if (j.reducer.receivedFullRRC()) {
					j.reducer.writeOutput();
					logMessage ("KeyRootMessage: JOB DONE\n" +
					    	    "Output written to ReduceOutput.txt");
					System.out.println ("KeyRootMessage: JOB DONE\n" +
					    	    	    "Output written to ReduceOutput.txt");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
      		}
    	} else if (message instanceof ReduceReplyMessage) {		
		/**
		 * Message details:
		 * ******************************
		 * Sent By: Reducer nodes
		 * Received at: Reduce topic root
		 *
		 * This message contains the list for of key-value pairs generated by
		 * the reduce function. The reduce topic root stores these values until
		 * it gets the values from all the j.reducer nodes.
		 */
		if (!isRoot(reduceTopic)) {
			logMessage ("ERROR: ReduceReplyMessage received at"
								+ " non-reduce-tree root");
		}
		logMessage ("ReduceRelyMessage received at reduce-tree root");
		
		int jobId = ((ReduceReplyMessage)message).getJobId();

		if (!jobMap.containsKey(jobId)) {
			logMessage ("ERROR: ReduceReplyMessage for unknown jobId: " + jobId);
		}
		Job j = jobMap.get(jobId);

      		try {
			if (!j.reducer.isRRCinitialized()) {
				j.reducer.initializeRRC();
			}
			Object key = ((ReduceReplyMessage)message).getKey();

			boolean finished = j.reducer.setReceivedRRC (key,
						 ((ReduceReplyMessage)message).getKVlist());
			logMessage ("ReduceReplyMessage received for Key = " + key);
			
			if (finished) {
				// this is the last child
				logMessage ("Reduce-tree root received reducer output for all keys");
				j.reducer.writeOutput();
				logMessage ("ReduceReplyMessage: JOB DONE\n" +
					    "Output written to ReduceOutput.txt");
				System.out.println ("ReduceReplyMessage: JOB DONE\n" +
					    	    "Output written to ReduceOutput.txt");
			}
      		} catch (Exception e) {
			e.printStackTrace();
      		}
    	} else if (message instanceof PublishContent) {
      		sendMulticast();
    	}
  }
  
  /* Sends a dummy multicast message. */
  public void sendMulticast() {
    	logMessage("Node "+endpoint.getLocalNodeHandle()+" broadcasting "+seqNum);
    	MyScribeContent myMessage = new MyScribeContent(endpoint.getLocalNodeHandle(), seqNum);
    	myScribe.publish(mapTopic, myMessage); 
    	seqNum++;
  }

  /* Sends the multicast message with the function to execute. */
  public void jobStart(String jarFilePath, String jarFileName, ArrayList<NodeHandle> nhs) {
	jobNum++;
    	logMessage("Node "+endpoint.getLocalNodeHandle()+" broadcasting job "+jobNum);

    	/* Send the map-reduce job to root of map tree and reduce tree*/
    	FuncRootMessage msg = new FuncRootMessage (jarFilePath, jarFileName, nhs, jobNum);

    	NodeHandle root = ((ScribeImpl)myScribe).getRoot(mapTopic);
    	endpoint.route (null, msg, root);
    
    	/* Send the map-reduce job to root of map tree */
    	root = ((ScribeImpl)myScribe).getRoot(reduceTopic);
    	endpoint.route (null, msg, root);
    	seqNum++;
  }

  /* Called whenever we receive a published message. */
  public void deliver(Topic topic, ScribeContent content) {
    	logMessage("MyScribeClient.deliver("+topic+","+content+")");

    	if (content instanceof FuncScribeContent) {
		/* Both map and reduce tree members unzip the code received */
    		logMessage("Received FuncScribeContent");

		int jobId = ((FuncScribeContent)content).jobNum;

		String fileName = ((FuncScribeContent)content).fileName;
		File destination = new File (fileName);
		
		try {
            		byte[] fileBytes = ((FuncScribeContent)content).fileBytes;
	    		FileOutputStream fos = new FileOutputStream(destination);
			
	    		fos.write(fileBytes, 0, fileBytes.length);
            		fos.flush();
	    		fos.close();
		
            		UnzipJar uj = new UnzipJar();
	    		uj.unzipJar (".", fileName);
		} catch (Exception e) {
			e.printStackTrace();
		}

		/* map tree members execute the map functions */
		if (topic.equals(mapTopic)) {
            		try{
				Job j = null;
				if (!jobMap.containsKey(jobId)) {
					j = new Job (jobId);
					/* TESTME */
					jobMap.put (jobId, j);
					logMessage ("New job created at mapper node with jobId: " + jobId);
				} else {
					j = jobMap.get(jobId);
				}

				Class maincls = Class.forName("FaceRecog");
				Object mainobj = maincls.newInstance();

				Class cls = Class.forName("Map");
				Class noparams[] = {};

				if (j.mapper == null) {
					Object obj = cls.newInstance();
					j.mapper = (PastryMapper)obj;
				}
				Object obj = (PastryMapper) j.mapper;

				Method method;
				method = maincls.getDeclaredMethod("setup", cls);
				method.invoke(mainobj, obj);

				Class inKeyCls = ((PastryMapper)obj).getInputKeyClass();
				Class inValueCls = ((PastryMapper)obj).getInputValueClass();
				KeysReplyMessage msg = null;

    				if (inKeyCls == TextName.class && inValueCls == Text.class) {
					BufferedReader br = new BufferedReader(
							    new FileReader("res/node" + 
									nodeNum + "/files"));
					String line;
					String txtFile;

					Context cxt = ((PastryMapper)obj).getContext();
					
					method = cls.getDeclaredMethod("map", inKeyCls, 
									inValueCls, Context.class);

					while ((line = br.readLine()) != null) {
						logMessage ("Mapper Reading file " + line);
						txtFile = "res/node" + nodeNum + "/" + line;
				
						TextName name = new TextName (txtFile);
    						Text t = new Text(name);
						
						method.invoke(obj, name, t, cxt);
					}
					cxt.partition();
			
					/* TESTME */
					((PastryMapper)obj).setContext(cxt);

					Class outKeyCls = ((PastryMapper)obj).getOutputKeyClass();
			
					msg = new KeysReplyMessage (cxt.getKeySet(),
							 this.endpoint.getLocalNodeHandle(), jobId);
    				} else if (inKeyCls == ImageName.class && inValueCls == String.class) {
					BufferedReader br = new BufferedReader(
							    new FileReader("res/node" + 
									nodeNum + "/images"));
					BufferedReader br2 = new BufferedReader(
							    new FileReader("res/node" + 
									nodeNum + "/location"));
					String line;
					String imgFile;

					Context cxt = ((PastryMapper)obj).getContext();
					String location;
					if ((location = br2.readLine()) == null) {
						new Exception("Location Data incorrect").
										printStackTrace();
					}					
					method = cls.getDeclaredMethod("map", inKeyCls, 
									inValueCls, Context.class);

					while ((line = br.readLine()) != null) {
						logMessage ("Mapper Reading file " + line + " at " +
								this.endpoint.getLocalNodeHandle());
						imgFile = "res/node" + nodeNum + "/" + line;
				
						ImageName name = new ImageName (imgFile);

						method.invoke(obj, name, location, cxt);
					}
					cxt.printContext();
					cxt.partition();
			
					/* TESTME */
					((PastryMapper)obj).setContext(cxt);

					Class outKeyCls = ((PastryMapper)obj).getOutputKeyClass();
			
					msg = new KeysReplyMessage (cxt.getKeySet(),
							 this.endpoint.getLocalNodeHandle(), jobId);
    				} else {
					logMessage ("Non-text input for mapper");
    				}

				/**
				 * Even if the current node is the root of the mapTopic,
				 * currently it sends a message to itself, to keep things simple.
				 */
				NodeHandle root = ((ScribeImpl)myScribe).getRoot(mapTopic);
				endpoint.route (null, msg, root);
	    		} catch (Exception ex) {
				ex.printStackTrace();
	    		}

		} else if (topic.equals(reduceTopic)) {
	    		/* reduce tree members wait for the reduce inputs */
			logMessage ("REDUCE tree: Received Job Message");
            		ArrayList<NodeHandle> nhs = ((FuncScribeContent)content).nhs;
	    		if (nhs == null) {
				logMessage ("ERROR: NodeHandles NULL in reduce roots");
	    		}

         		try {
				Job j = null;
				if (!jobMap.containsKey(jobId)) {
					j = new Job (jobId);
					/* TESTME */
					jobMap.put (jobId, j);
					logMessage ("New job created at reducer node with jobId: " + jobId);
				} else {
					j = jobMap.get(jobId);
				}

				Class cls = Class.forName("Reduce");
				if (j.reducer == null) {
					Object obj = cls.newInstance();
					j.reducer = (PastryReducer) obj;
	   			}
				j.store.setRNHs(nhs);
				Object obj = (PastryReducer) j.reducer;

				ArrayList<Object> keys = new ArrayList<Object>(j.store.
									getReceivedKeysRIM());

				for (Object k : keys) {
		    			boolean finished = j.store.receivedFullRIM(k);
	
            				if (finished) {
						if (!j.reducer.isInitializedIMap()) {
							j.reducer.initializeIMap();
						}
						j.mapper.setReducerClasses (j.reducer);
						j.store.setReducerInput (k, j.reducer);

						Class noparams[] = {};
						Class keyCls = j.reducer.getKeyClass();
						Class valueCls = j.reducer.getValueClass();

						Context cxt = j.reducer.getNewContext();
		
						ArrayList vals = j.reducer.getValuesByKey (k);
						logMessage ("REDUCE funtion input");
						for (Object v :  vals) {
							logMessage (v.toString());
						}
						Method method = cls.getDeclaredMethod("reduce", keyCls, 
								Iterable.class, Context.class);

						method.invoke(obj, k, vals, cxt);
						cxt.printContext();

						j.reducer.setContext(cxt);			
						ReduceReplyMessage msg = j.reducer.getRMsg (k,
							 cxt.getKVlist(),
							 this.endpoint.getLocalNodeHandle(), jobId);

						/**
						 * Even if the current node is the root of the
						 * mapTopic, currently it sends a message to
						 * itself, to keep things simple.
						 */
						NodeHandle root = ((ScribeImpl)myScribe).
										getRoot(reduceTopic);
						endpoint.route (null, msg, root);
					}
	        		}
			} catch (Exception ex) {
	 			ex.printStackTrace();
	    		}
		} else {
			logMessage ("ERROR: Received FuncScribeContent at non-mapper and " +
				    "non-reducer node");
		}

	} else if (content instanceof KeyTopicMap) {
		logMessage ("Received KeyTopicMap at" + this.endpoint.getLocalNodeHandle());
		
		int jobId = ((KeyTopicMap)content).getJobId();

		if (!jobMap.containsKey(jobId)) {
			logMessage ("ERROR: KeyTopicMap for unknown jobId: " + jobId);
		}
		Job j = jobMap.get(jobId);

		Set keySet = j.mapper.getContext().getKeySet();
		Class keyClass = j.mapper.getOutputKeyClass();
		Class valClass = j.mapper.getOutputValueClass();

		j.mapper.setKTM (((KeyTopicMap)content));
		HashMap hmap = j.mapper.getKTM_HashMap();
		if (hmap == null) {
			logMessage ("ERROR: getKTM_HashMap: KTM is NULL");
		}
		ArrayList<Topic> topics = new ArrayList<Topic>();

		Iterator iterator = hmap.keySet().iterator();
		int counter = 0;
        	while (iterator.hasNext()) {
       			Object k = (Object) iterator.next();
			String topicStr = j.mapper.getKTM_HashMapKey(k);
			if (topicStr == null) {
				logMessage ("ERROR: getKTM_HashMapKey: KTM is NULL");
			}

			Topic t = new Topic(new PastryIdFactory(node.getEnvironment()), topicStr);
			myScribe.subscribe(t, this); 
			topics.add(t);
			counter++;
		}
		try {
		    	node.getEnvironment().getTimeSource().sleep(5000);
		} catch (Exception e) {
			e.printStackTrace();
		}
	    	logMessage ("Mapper node subscribed to reducer topics");

	    	ListIterator<Topic> it = topics.listIterator();
		iterator = hmap.keySet().iterator();
            	
		while (iterator.hasNext()) {
			Object k = (Object) iterator.next();
			if (!it.hasNext()) {
				logMessage ("ERROR: Topic List incomplete");
				new Exception("Topic List incomplete").printStackTrace();
			}
			Topic t = it.next();

			NodeHandle root = ((ScribeImpl)myScribe).getRoot(t);
			logMessage ("Sending ReduceInputMessage to Reducer root = " + root);
	
			ReduceInputMessage msg = j.mapper.getRIMmsg(k, 
						this.endpoint.getLocalNodeHandle(), t, jobId);
			endpoint.route (null, msg, root);
	    	}
    	} else if (((MyScribeContent)content).from == null) {
      		new Exception("Unknown sender").printStackTrace();
    	}
  }

  /* Sends a dummy anycast message. */
  public void sendAnycast() {
    	logMessage("Node "+endpoint.getLocalNodeHandle()+" anycasting "+seqNum);
    	MyScribeContent myMessage = new MyScribeContent(endpoint.getLocalNodeHandle(), seqNum);
    	myScribe.anycast(mapTopic, myMessage); 
    	seqNum++;
  }
  
  /**
   * Called when we receive an anycast.  If we return
   * false, it will be delivered elsewhere.  Returning true
   * stops the message here.
   */
  public boolean anycast(Topic topic, ScribeContent content) {
    	boolean returnValue = myScribe.getEnvironment().getRandomSource().nextInt(3) == 0;
    	logMessage("MyScribeClient.anycast("+topic+","+content+"):"+returnValue);
    	return returnValue;
  }

  public void childAdded(Topic topic, NodeHandle child) {
	//logMessage("MyScribeClient.childAdded("+topic+","+child+")");
  }

  public void childRemoved(Topic topic, NodeHandle child) {
	//logMessage("MyScribeClient.childRemoved("+topic+","+child+")");
  }

  public void subscribeFailed(Topic topic) {
	//logMessage("MyScribeClient.childFailed("+topic+")");
  }

  public boolean forward(RouteMessage message) {
    	return true;
  }


  public void update(NodeHandle handle, boolean joined) {
    
  }

  class PublishContent implements Message {
    	public int getPriority() {
      		return MAX_PRIORITY;
    	}
  }

  
  /************ Some passthrough accessors for the myScribe *************/
  public boolean isRoot(Topic t) {
    	return myScribe.isRoot(t);
  }
  
  public NodeHandle getParent(Topic t) {
    	return ((ScribeImpl)myScribe).getParent(t);
  }
  
  public NodeHandle[] getChildren(Topic t) {
    	return myScribe.getChildren(t); 
  }
  
  public boolean myRoot() {
    	return myScribe.isRoot(mapTopic);
  }
  
  public NodeHandle myParent() {
    	return ((ScribeImpl)myScribe).getParent(mapTopic);
  }
  
  public NodeHandle[] myChildren() {
    	return myScribe.getChildren(mapTopic); 
  }
}
