#ifndef __LIST_H
#define __LIST_H

#include<string>
#include <unistd.h>
#include<string.h>
using namespace std;
typedef struct contents_node* Node;
typedef struct contents_node
{
//	string* url_contents;
//	string* url;
	char *url;
	char* url_contents;
	Node next;
	Node prev;
	int listId;	/* For LRU Min */
} node;
typedef struct contents_list
{
	Node head;
	Node tail;
	size_t cache_size;
} contentsList;

class List{
  public:
	contentsList *list; 
//	string contents[100];
//	string urls[100];
//	int contents_count;

	List();
	~List();

	/* List Operations */
	Node getHead ();
	Node getTail ();
	size_t getSize();
	void printList(int listId);

	void list_insert(string url,string url_contents, int listId);

	int list_delete(struct contents_node* node);

	void list_moveToHead (Node node);

	void string_char(Node t, string s, string s1);
};
#endif
