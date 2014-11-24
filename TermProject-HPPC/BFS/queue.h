#ifndef __QUEUE_H
#define __QUEUE_H

#include <stdlib.h>
#include <stdio.h>
struct node;

typedef int Element;
typedef struct node {
        Element e;
        struct node* next;
} Node;
typedef struct queue {
        Node *head;
        Node *tail;
        int size;
} Queue;

extern Queue* initialize(Queue* q);
extern Queue* enqueue(Queue* q, Element e);
extern Queue* dequeue(Queue *q, Element* e);
extern int empty(Queue *q);
#endif
