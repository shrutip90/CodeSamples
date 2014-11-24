import rice.p2p.commonapi.NodeHandle;
import rice.p2p.scribe.ScribeContent;
import java.io.*;
import java.util.*;

public class KeyTopicMap<K> implements ScribeContent {
  
  	/* Job ID of the map-reduce job */
  	int jobId;

  	HashMap<K, String> topicMap;

	/* Default constructor */
  	public KeyTopicMap() {
    		this.jobId = 0;
    		this.topicMap = new HashMap<K, String>();
  	}
	/**
	  * @param jobId Job ID of the map-reduce job
	  */
  	public KeyTopicMap(int jobId) {
    		this.jobId = jobId;
    		this.topicMap = new HashMap<K, String>();
  	}
  
	public void insertKey (K key, String topic) {
    		if (!this.topicMap.containsKey(key)) {
			this.topicMap.put(key, topic);
    		}
 	}
  	public Set<K> getKeys () {
    		return topicMap.keySet();
  	}
  	public HashMap getMap () {
    		return topicMap;
 	}
	public int getJobId () {
		return jobId;
	}
  	public String toString() {
    		return "KeyTopicMap job# "+jobId;
  	}  
}
