#include <stdio.h>
#include <stdlib.h>
#include "queue.h"

Queue* initialize(Queue* q) {
	q->head = NULL;
	q->tail = NULL;
	q->size = 0;
	return q;
}
Queue* enqueue(Queue* q, Element e) {
	Node *d = (Node*) malloc (sizeof (Node));
	d->e = e;
	d->next = NULL;
	if (q->tail) {
		q->tail->next = d;
	}
	q->tail = d;
	
	if (q->size == 0) {
		q->head = q->tail;
	}
	q->size++;
	return q;
}
Queue* dequeue(Queue *q, Element* e) {
	Node *temp = q->head;
	*e = temp->e;
	q->head = q->head->next;
	free(temp);

	q->size--;
	if (q->size == 0) {
		q->tail = NULL;
	}
	return q;
}
int empty(Queue *q) {
	if (q->size == 0) {
		return 1;
	}
	return 0;
}
