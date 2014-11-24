import java.io.IOException;
import java.net.*;
import java.util.*;

import rice.environment.Environment;
import rice.p2p.commonapi.NodeHandle;
import rice.pastry.*;
import rice.pastry.socket.SocketPastryNodeFactory;
import rice.pastry.standard.RandomNodeIdFactory;
import rice.pastry.transport.TransportPastryNodeFactory;

public class Main {
	/* this will keep track of all Scribe applications */
  	Vector<MyScribeClient> apps = new Vector<MyScribeClient>();

  	/* NodeHandles to keep track of the nodes that subscribe to map topic */
  	ArrayList<NodeHandle> nhs = new ArrayList<NodeHandle>();

  	/**
	 * This constructor launches numNodes PastryNodes. They will bootstrap to an
   	 * existing ring if one exists at the specified location, otherwise it will
  	 * start a new ring.
  	 * 
  	 * @param bindport the local port to bind to
  	 * @param bootaddress the IP:port of the node to boot from
  	 * @param numNodes the number of nodes to create in this JVM
 	 * @param env the Environment
   	 */
  	public Main (int bindport, InetSocketAddress bootaddress,
      				int numNodes, Environment env) throws Exception {
    
    		// Generate the NodeIds Randomly
    		NodeIdFactory nidFactory = new RandomNodeIdFactory(env);

    		// construct the PastryNodeFactory to use rice.pastry.socket
    		PastryNodeFactory factory = new SocketPastryNodeFactory(nidFactory, bindport, env);

    		for (int curNode = 0; curNode < numNodes; curNode++) {
      			PastryNode node = factory.newNode();
      
      			MyScribeClient app = new MyScribeClient(node, curNode);
      			apps.add(app);
      
      			node.boot(bootaddress);
      
      			// the node may require sending several messages to fully boot into the ring
      			synchronized(node) {
        			while(!node.isReady() && !node.joinFailed()) {
          			    node.wait(500);
          
          			    // abort if can't join
          			    if (node.joinFailed()) {
            				throw new IOException("Could not join the FreePastry ring." +
							      "Reason:"+node.joinFailedReason()); 
          			    }
        			}       
      			}
			System.out.println("Finished creating new node: " + node);
    		}

    		// subscribe the nodes to map topic
    		Iterator<MyScribeClient> i = apps.iterator();
    		MyScribeClient app = (MyScribeClient) i.next();
    		app.subscribe();
    		nhs.add (app.endpoint.getLocalNodeHandle());

    		while (i.hasNext()) {
      			app = (MyScribeClient) i.next();
	      		app.subscribe();
      			nhs.add (app.endpoint.getLocalNodeHandle());
    		}

    		// print the tree for map topic
    		env.getTimeSource().sleep(5000);
    		printTree(apps);

    		// start the map-reduce job
    		app.jobStart("src/jars/", "App.jar", nhs);
	}

	/**
	  * Note that this function only works because we have global knowledge. Doing
	  * this in an actual distributed environment will take some more work.
	  * 
	  * @param apps Vector of the applicatoins.
	  */
	public static void printTree(Vector<MyScribeClient> apps) {
    		// build a hashtable of the apps, keyed by nodehandle
    		Hashtable<NodeHandle, MyScribeClient> appTable = new Hashtable<NodeHandle,
										MyScribeClient>();
    		Iterator<MyScribeClient> i = apps.iterator();
    
		while (i.hasNext()) {
      			MyScribeClient app = (MyScribeClient) i.next();
      			appTable.put(app.endpoint.getLocalNodeHandle(), app);
    		}
    		NodeHandle seed = ((MyScribeClient) apps.get(0)).endpoint.getLocalNodeHandle();

    		// get the root
    		NodeHandle root = getRoot(seed, appTable);

    		// print the tree from the root down
    		recursivelyPrintChildren(root, 0, appTable);
	}

	/**
	  * Recursively crawl up the tree to find the root.
	  */
	public static NodeHandle getRoot(NodeHandle seed, Hashtable<NodeHandle,MyScribeClient>appTable) {
	    	MyScribeClient app = (MyScribeClient) appTable.get(seed);
    
		if (app.myRoot())
      			return seed;
    		NodeHandle nextSeed = app.myParent();

    		return getRoot(nextSeed, appTable);
	}

	/**
	  * Print's self, then children.
	  */
	public static void recursivelyPrintChildren(NodeHandle curNode, int recursionDepth,
					    Hashtable<NodeHandle, MyScribeClient> appTable) {
    		// print self at appropriate tab level
    		String s = "";
    
		for (int numTabs = 0; numTabs < recursionDepth; numTabs++) {
      			s += "  ";
    		}
    		s += curNode.getId().toString();
    		System.out.println(s);

    		// recursively print all children
    		MyScribeClient app = (MyScribeClient) appTable.get(curNode);
    		NodeHandle[] children = app.myChildren();
    
		for (int curChild = 0; curChild < children.length; curChild++) {
      			recursivelyPrintChildren(children[curChild], recursionDepth + 1, appTable);
    		}
	}

	/**
	  * Usage: java [-cp FreePastry- <version>.jar]
	  * Main localbindport bootIP bootPort numNodes
	  * example java Main 9001 pokey.cs.almamater.edu 9001
	  */
	public static void main(String[] args) throws Exception {
    		// Loads pastry configurations
	    	Environment env = new Environment();

    		// disable the UPnP setting (in case you are testing this on a NATted LAN)
    		env.getParameters().setString("nat_search_policy","never");
	    	env.getParameters().setInt("pastry_socket_writer_max_queue_length", 10000);
    
    		try {
      			// the port to use locally
      			int bindport = Integer.parseInt(args[0]);

      			// build the bootaddress from the command line args
      			InetAddress bootaddr = InetAddress.getByName(args[1]);
      			int bootport = Integer.parseInt(args[2]);
      			InetSocketAddress bootaddress = new InetSocketAddress(bootaddr, bootport);
	
      			// the port to use locally
      			int numNodes = Integer.parseInt(args[3]);
	
      			// launch our node!
			System.out.println ("Initializing Main....................");
      			Main dt = new Main(bindport, bootaddress, numNodes, env);
	
    		} catch (Exception e) {
      			System.out.println("Usage:");
      			System.out.println("java [-cp FreePastry-<version>.jar]" +
					   "Main localbindport bootIP bootPort numNodes");
      			System.out.println("example java Main 9001 pokey.cs.almamater.edu 9001 10");
      			throw e;
    		}
	}
}
