#include "Hash.h"
using namespace std;

HashTable::HashTable()
{
}
HashTable::~HashTable()
{
}

void HashTable::hash_insert(string url, string url_contents, Node h) {
	hash_table[url] = h;
#ifdef DEBUG
	printf("inserted into hash table\n") ;
#endif
}

void HashTable::hash_delete(string url) {
	hash_table.erase(url);
}

contents_node* HashTable::hash_lookup(string url) {
	if(hash_table.find(url) == hash_table.end()) {
		return NULL;
	}
	else {
#ifdef DEBUG
		printf("found in cache\n");
#endif
		return hash_table[url];
	}
}
