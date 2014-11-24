import java.io.*;
import java.util.*;

class ImageName {
	String name;

	public ImageName () {
		name = null;
	}
	public ImageName (String name) {
		this.name = name;
	}
	public String toString () {
		return name;
	}
}
class TextName {
	String name;

	public TextName () {
		name = null;
	}
	public TextName (String name) {
		this.name = name;
	}
	public String toString () {
		return name;
	}
}
class Text {
	byte[] bytes;

	public Text (TextName fileName) {
		File file = new File(fileName.toString()) ;
		try {
            		bytes = new byte[(int)(file.length())];
			System.out.println("File length = " + (int)(file.length()));

            		FileInputStream fis = new FileInputStream(file);
			
            		int count = 0;
            		count = fis.read(bytes);
			if (count <= 0 || count != file.length()) {
				System.out.println ("Didn't read file properly!!!!!!!!!!!!");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public Text (String fileContent) {
		bytes = fileContent.getBytes();
	}

	public byte[] getBytes () {
		return bytes;
	}
	public int getLength() {
		return bytes.length;
	}
	public String toString() {
		return new String(bytes);
	}
}
class Pair<K, V> implements Serializable {
	K key;
	V value;
	public Pair (K key, V val) {
		this.key = key;
		this.value = val;
	}
	public K getKey() {
		return key;
	}
	public V getValue() {
		return value;
	}
	public String toString () {
		return ("Key: " + key + ", Value: " + value);
	}
}
class Context<K, V> {
	ArrayList<Pair<K,V>> KV_List;
	HashMap<K, ArrayList<V>> KV_Map;

	public Context() {
		KV_List = new ArrayList<Pair<K,V>>();	
		KV_Map = new HashMap<K, ArrayList<V>>();	
	}
	public void write (K key, V val) {
		Pair<K, V> p = new Pair<K, V>(key, val);
		KV_List.add (p);
	}
	public void printContext () {
		for (Pair<K, V> p : KV_List) {
			System.out.println (p);			
		}
	}
	public void printPartitions () {
		Iterator iterator = (KV_Map.keySet()).iterator();  
       
    		while (iterator.hasNext()) {  
       			K key = (K)iterator.next();  
       			ArrayList<V> value = KV_Map.get(key);  
       
       			System.out.println("Key: " + key + ", Values: ");
			for (V val: value) {
				System.out.print (val + ", ");
			}
    		}  
	}
	public int partition () {
		for (Pair<K, V> p : KV_List) {
			if (KV_Map.containsKey (p.getKey())) {
				KV_Map.get(p.getKey()).add(p.getValue());
			} else {
				KV_Map.put(p.getKey(), new ArrayList<V>());
				KV_Map.get(p.getKey()).add(p.getValue());
			}
		}
		return KV_Map.size();
	}
	public int getNumPartitions () {
		return KV_Map.size();
	}
	public boolean equalKeys (K key1, K key2) {
		return key1.equals(key2);
	}
	public Set<K> getKeySet () {
		return KV_Map.keySet();
	}
	public ArrayList<V> getValues (K key) {
		return KV_Map.get(key);
	}
	public ArrayList<Pair<K,V>> getKVlist () {
		return KV_List;
	}
	public boolean containsKey (Object key) {
		return getKeySet().contains((K)key);
	}
}
