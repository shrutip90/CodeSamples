#ifndef __HASH_H
#define __HASH_H

#include <string>
#include <stdlib.h>
#include <stdio.h>
#include <map>
#include "List.h"

using namespace std;
/* typedef struct hash_node
{
	string url;
	struct contents_node *p;
	struct hash_node *next;
}hash_node;*/


class HashTable : public List{
  public:
	map<string,contents_node*>hash_table; 		//hash map for storing
	map<string,contents_node*>::iterator ht_i;	//iterator for the map
	HashTable();
	~HashTable();
	/* Hash Table operations */

	void hash_insert(string url, string url_contents, Node h);

	void hash_delete(string url);

	contents_node* hash_lookup(string url);
};

#endif
