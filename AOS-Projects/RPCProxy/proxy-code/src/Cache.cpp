#include "Cache.h"
#include <assert.h>
//#define LRU_MIN_DEBUG

using namespace proxy;

Cache::Cache () {
	MAX_CACHE_SIZE = 5 * 512 * 1024;
	policy = RANDOM;
	S = LRU_MIN_S;

	stats.accesses = 0;
	stats.misses = 0;
	stats.hits = 0;
	stats.hit_rate = 0.0;
	stats.miss_rate = 0.0;

#ifdef LRU_MIN_DEBUG
	int k = 1;
	cout << "Initialized LRU_MIN with S = "<< S << "and size of lists: ";
	for (int i = 0; i < NUM_LRU_MIN_LISTS - 1; i++) {
		cout << "More than " << S / k << ", ";
		k *= 2;
	}
	k /= 2;
	cout << "Less than " << S / k << endl;
#endif
}

Cache::~Cache() {
}

void Cache::setMaxSize(int size) {
	cout << "Setting cache size to " << size << endl;
	MAX_CACHE_SIZE = size;
}
void Cache::setCachePolicy(int pol) {
	cout << "Setting cache policy to ";
	switch (pol) {
		case 0: policy = RANDOM;
			cout << "RANDOM" << endl;
			break;
		case 1: policy = FIFO;
			cout << "FIFO" << endl;
			break;
		case 2: policy = LRU_MIN;
			cout << "LRU-MIN" << endl;
			break;
		default: cout << "None" << endl;
			cout << "Incorrect cache policy. Exiting." << endl;
			exit(0);
	}
}
void Cache::printStats() {
	completeStats();
	cout << "Accesses: " << stats.accesses << endl;
	cout << "Hits: " << stats.hits << endl;
	cout << "Misses: " << stats.misses << endl;
	cout << "Hit Rate: " << stats.hit_rate << endl;
	cout << "Miss Rate: " << stats.miss_rate << endl;
}
size_t Cache::getSize() {
	if (policy == RANDOM || policy == FIFO) {
		return Cache_List.getSize();
	} else {
		size_t size = 0;
		for (int i = 0; i < NUM_LRU_MIN_LISTS; i++) {
			size += LRUMinLists[i].getSize();
		}
		return size;
	}
}
bool Cache::isFull(size_t s) {
	size_t size = getSize();
	if(size + s >= MAX_CACHE_SIZE) {
		//printf("Cache full, size required = %d\n", size + s);
		return true;
	} else {
		//printf("Cache not full, size required = %d\n", size +s);
		return false;
	}
}

/* Search for the URL contents in the cache */
 const std::string Cache::getContents (const std::string url) {
	stats.accesses++;

	Node temp = Cache_HashTable.hash_lookup(url);	
	if(temp != NULL) {
		stats.hits++;
#ifdef DEBUG
		cout<<"URL CONTENTS\n"<< *(temp->url_contents)<<endl;
		string url_contents = *(temp->url_contents);
		cout<<"url ="<<url;
		cout<<"temp="<< temp <<" url_contents "<<temp->url_contents<<endl;;
		cout << *(temp->url_contents)<<endl;
		printf("size of url_Contents = %lld\n", sizeof(url_contents));
		printf("length of url_Contents = %lld\n", url_contents.length());
#endif
		string url_t(temp->url_contents);

		if (policy == LRU_MIN) {
			int id = temp->listId;
			string cont(temp->url_contents);

			
			Cache_HashTable.hash_delete(url);
			LRUMinLists[id].list_moveToHead(temp);
			Cache_HashTable.hash_insert(url, cont, 
							LRUMinLists[id].getHead());
#ifdef LRU_MIN_DEBUG
			cout << "Found in LRU Min list" << id << endl;
			for (int i = 0; i < NUM_LRU_MIN_LISTS; i++) {
				LRUMinLists[i].printList(i);
			}
#endif
		}
		return url_t;
	} else {
		stats.misses++;
		string ret = "not";
		return ret;
	}
}

/* Add the contents read from the server to the cache */
void Cache::addContents (const std::string url,const std::string url_contents) {
#ifdef DEBUG
	printf("inside addContents\n");
#endif
	size_t size = url_contents.size();
	if (isFull (size)) {
		int content_iter = cache_replace(url_contents);
		
		/* cache_replace should delete the node in the list and 
		 * the map entry
		 */
		if (content_iter == -1) {
			//cout << "Wont fit in cache" << endl;
			return;
		}
	}
	if (policy == RANDOM || policy == FIFO) {
		Cache_List.list_insert (url, url_contents, 0);
		Cache_HashTable.hash_insert(url, url_contents, Cache_List.getHead());
	} else {
		int i, k = 1;
		for (i = 0; i < NUM_LRU_MIN_LISTS - 1; i++) {
			if (size > (S / k)) {
				break;
			}
			k *= 2;
		}
#ifdef LRU_MIN_DEBUG
		cout << "addContents:: content size = " << size << ", S = "<< S;
		cout << ", k = " << k << ", List id = " << i << endl;
#endif
		LRUMinLists[i].list_insert (url, url_contents, i);
		Cache_HashTable.hash_insert(url, url_contents, 
							LRUMinLists[i].getHead());
	}
}

int Cache::cache_replace (const string url_contents) {
	/* return 0 or -1. -1 if first page doesnt fit in cache */
	int index;

	if (policy == RANDOM) {
		index = random_replace(url_contents);
	} else if (policy == FIFO) {
		index = fifo_replace(url_contents);
	} else if (policy == LRU_MIN) {
		index = lru_min_replace(url_contents);
	} else {
		assert (!"cache_replace::Incorrect policy");
	}
	return index;
}

int Cache::random_replace(const string url_contents)
{
	if(Cache_List.getHead() == NULL) {
		/* List is empty and the first url is greater then max_cache_size */
		return -1;
	}
	if(url_contents.size() > MAX_CACHE_SIZE)
		return -1;
	Cache_HashTable.ht_i = Cache_HashTable.hash_table.begin();
	
	if(Cache_HashTable.hash_table.size() == 0)
		return -1;

	int r = rand()%(Cache_HashTable.hash_table.size());
#ifdef DEBUG
	printf("r = %d\n",r);
#endif
	advance(Cache_HashTable.ht_i, r);
	string url_replace = Cache_HashTable.ht_i->first;

	//cout<<"replace url="<<url_replace<<endl;
	contents_node* temp = Cache_HashTable.ht_i->second;
	string cont(temp->url_contents);
	int replace_size = cont.size();

	if(replace_size + (MAX_CACHE_SIZE - Cache_List.list->cache_size) <url_contents.size()) {

		int index = Cache_List.list_delete(temp);
		Cache_HashTable.hash_delete(url_replace);
		while(replace_size + (MAX_CACHE_SIZE - Cache_List.getSize()) < url_contents.size())
		{
			//printf("inside while\n");
			if(Cache_HashTable.hash_table.size()==0)
				return -1;
			r = rand()%(Cache_HashTable.hash_table.size());
			Cache_HashTable.ht_i = Cache_HashTable.hash_table.begin();
			advance(Cache_HashTable.ht_i,r);
			url_replace = Cache_HashTable.ht_i->first;

			//cout<<"replace url="<<url_replace<<endl;
			Node temp = Cache_HashTable.ht_i->second;

			//printf("after getting temp node\n");
			string cont1(temp->url_contents);
			//printf("after getting temp contents\n");
			replace_size = cont1.size();
			Cache_List.list_delete(temp);
			//printf("after delete\n");
			Cache_HashTable.hash_delete(url_replace);
			//printf("after second delete\n");
			
		}
		return 0;
	
	} else {
		int index = Cache_List.list_delete(temp);
		Cache_HashTable.hash_delete(url_replace);
		return index;
	}

}

int Cache::fifo_replace(const string url_contents) {
	if(Cache_List.getHead() == NULL) {
		/* List is empty and the first url is greater then max_cache_size */
		return -1;
	}
	if(url_contents.size() > MAX_CACHE_SIZE)
		return -1;
	
	string url_replace((Cache_List.getTail())->url);

	//cout << "Candidate url for replacement =" << url_replace << endl;
	string cont((Cache_List.getTail())->url_contents);
	int replace_size = cont.size();

	if(replace_size + (MAX_CACHE_SIZE - Cache_List.getSize()) < url_contents.size())
	{
		Cache_List.list_delete(Cache_List.getTail());
		Cache_HashTable.hash_delete(url_replace);
	while(replace_size + (MAX_CACHE_SIZE - Cache_List.getSize()) < 	url_contents.size() && Cache_List.getHead()!=NULL) {
		string url_replace((Cache_List.getTail())->url);
		//cout << "Candidate url for replacement =" << url_replace << endl;
		string cont((Cache_List.getTail())->url_contents);
		replace_size = cont.size();
		Cache_List.list_delete(Cache_List.getTail());
		Cache_HashTable.hash_delete(url_replace);
	}
	return 0;
	}
	else
	{
		int index = Cache_List.list_delete(Cache_List.getTail());
		Cache_HashTable.hash_delete(url_replace);
		return 0;
	}
}

int Cache::lru_min_replace(const string url_contents) {
	int i;

	if (url_contents.size() > MAX_CACHE_SIZE) {
		return -1;
	}

#ifdef LRU_MIN_DEBUG
	for (i = 0; i < NUM_LRU_MIN_LISTS; i++) {
		LRUMinLists[i].printList(i);
	}
#endif

	for (i = 0; i < NUM_LRU_MIN_LISTS; i++) {
		while (LRUMinLists[i].getHead() != NULL) {
			if (isFull (url_contents.size())) {
				string url_replace((LRUMinLists[i].getTail())->url);
#ifdef LRU_MIN_DEBUG
				cout << "evicting url " << url_replace << endl;
#endif
				LRUMinLists[i].list_delete(LRUMinLists[i].getTail());
				Cache_HashTable.hash_delete(url_replace);
			} else {
				return 0;
			}
		}
	}
	if (isFull (url_contents.size())) {
		return -1;
	} else {
		return 0;
	}
}

/* Compute avg access time for the accesses that happenend */
void Cache::completeStats () {
	stats.hit_rate = (double)stats.accesses ? ((double)stats.hits / (double)stats.accesses) : 0;
	stats.miss_rate = (double)stats.accesses ? ((double)stats.misses / (double)stats.accesses) : 0;
}
