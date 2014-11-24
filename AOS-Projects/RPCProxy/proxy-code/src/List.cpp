#include "List.h"
#include<stdlib.h>
#include<stdio.h>
#include<iostream>
#include <assert.h>
#include <string.h>

using namespace std;
List::List() {
	list = (contentsList*) malloc (sizeof (contentsList));
	//contents_count = 0;
	
	if(list != NULL) {
		list->head = NULL;
		list->tail = NULL;
		list->cache_size = 0;
	} else {
		printf("List()::malloc error\n");
		exit(0);
	}
}

List::~List() {
	if (list) {
		Node temp, next;
		for (temp = list->head; temp != NULL; temp = next) {
			next = temp->next;
			free(temp->url_contents);
			free(temp->url);
			free(temp);
		}
		free(list);
	}
}

Node List::getHead () {
	return list->head;
}
Node List::getTail () {
	return list->tail;
}
size_t List::getSize() {
	return list->cache_size;
}

void List::printList (int listId) {
	Node temp;

	cout << "Printing list " << listId << endl;
	for (temp = list->head; temp != NULL; temp = temp->next) {
		printf ("%s:%d, ", temp->url, strlen(temp->url_contents));
	}
	cout << endl;
}
void List::string_char(Node t, string s, string s1)
{
	t->url_contents = (char*)malloc(s.length()*sizeof(char)+1);
	t->url = (char*)malloc(s1.length()*sizeof(char)+1);
	if(t->url_contents !=NULL)
	{
		strncpy(t->url_contents,s.c_str(), s.length()+1);
	}
	else
	{
		printf("string to char malloc error\n");
		return;
	}
	if(t->url !=NULL)
	{
		strncpy(t->url,s1.c_str(), s1.length()+1);
	}
	else
	{
		printf("string to char malloc error\n");
		return;
	}
}
void List::list_insert (string url, string url_contents, int listId) {
	Node temp = (Node) malloc (sizeof (node));

	if(temp!=NULL) {
		/*
		if(content_iter == -1) {
			contents[contents_count] = url_contents;
			urls[contents_count] = url;
			temp->url = &urls[contents_count];
			temp->url_contents = &contents[contents_count++];
			list->cache_size += (contents[contents_count-1].size());
		} else {
			contents[content_iter] = url_contents;
			urls[content_iter] = url;
			temp->url = &urls[content_iter];
			temp->url_contents = &contents[content_iter];
			list->cache_size += (contents[content_iter].size());
			
		}*/
		string_char(temp,url_contents,url);
		list->cache_size += url_contents.size();
		//cout<<"url_contents\n"<<*(temp->url_contents)<<endl;
#ifdef DEBUG
		cout<<*(temp->url_contents)<<endl;
			
		printf("max string size = %lld\n", (temp->url_contents)->max_size());
		printf("size of url_contents = %lld\n",sizeof(*(temp->url_contents)));
		printf("inside temp not null\n");
#endif
		temp->next=NULL;
		temp->prev=NULL;
		temp->listId = listId;
	} else {
		printf("malloc error\n");
		exit(0);
	}
	
	if(list->head == NULL) {
		list->head = temp;
		list->tail = temp;
	} else {
		list->head->prev = temp;
		temp->next = list->head;
		list->head = temp;
	}
	//printf("Inserted into list\n");

}
int List::list_delete(struct contents_node* node)
{
//	unsigned int index = (node->url_contents - contents);
#ifdef DEBUG
	printf("list_delete::index of = %u\n", index);
#endif
	if(list->head == node && list->head == node) {
		list->head = NULL;
		list->tail = NULL;
	} else if(list->head == node) {
#ifdef DEBUG
		printf("list_delete::head = node\n");
#endif
		list->head = node->next;
		node->next->prev = NULL;
	} else if(list->tail == node) {
#ifdef DEBUG
		printf("list_delete::tail = node\n");
#endif
		node->prev->next = NULL;
		list->tail = node->prev;
	} else {
#ifdef DEBUG
		printf("list_delete::inside else\n");
#endif
		node->prev->next = node->next;
		node->next->prev = node->prev;
	}
	
//	list->cache_size -= (contents[index].size());
	string temp(node->url_contents);
	list->cache_size -= temp.size();
//	printf("inside delete\n");
	free (node->url_contents);
	free(node->url);
	free(node);
	return 0;
}

void List::list_moveToHead (Node node) {
	assert (list->head != NULL);
	if (list->head == node) {
		return;
	}
	node->prev->next = node->next;
	if (node == list->tail) {
		list->tail = node->prev;
	} else {
		node->next->prev = node->prev;
	}

	list->head->prev = node;
	node->next = list->head;
	node->prev = NULL;
	list->head = node;
}
