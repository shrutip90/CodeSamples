#ifndef __CACHE_H
#define __CACHE_H

#include <string>
#include <unistd.h>
#include <iostream>
#include "Hash.h"

using namespace std;
namespace proxy {

#define AVG_PAGE_SIZE		250*1024
#define NUM_LRU_MIN_LISTS	4
#define LRU_MIN_S		AVG_PAGE_SIZE

typedef struct cacheStats {
	int accesses;
	int hits;
	int misses;
	double hit_rate;
	double miss_rate;
} CacheStats;

typedef enum {RANDOM, FIFO, LRU_MIN} ReplacementPolicy;

class Cache {
  public:
	int MAX_CACHE_SIZE;
	ReplacementPolicy policy;
	HashTable Cache_HashTable;
	List Cache_List;
	CacheStats stats;

	size_t S;
	List LRUMinLists[NUM_LRU_MIN_LISTS];

	Cache ();
	~Cache ();
	
	void setMaxSize(int size);
	void setCachePolicy(int pol);
	size_t getSize();
	bool isFull(size_t s);
	void printStats();

	/* Search for the URL contents in the cache */
	const std::string getContents (const std::string url);

	/* Add the contents read from the server to the cache */
	void addContents (const std::string url,const std::string url_contents);

	/* Compute avg access time for the accesses that happenend */
	void completeStats ();
	
	/* */
	int cache_replace(const string url_contents);

	int random_replace(const string url_contents);
	int fifo_replace(const string url_contents);
	int lru_min_replace(const string url_contents);
};


} // namespace
#endif
