#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <omp.h>
#include <sys/time.h>

#define NATree		8	/* #children for each node in the arrival tree */
#define NWTree		4	/* #children for each node in the wakeup tree */
#define ITER		100	/* #iterations of the loop for experiment */

#define DEBUG

static int NUM_THREADS;

struct barrier_rec;
typedef struct barrier_rec BarrierRec;

struct barrier_rec {
	volatile bool	childNotReady[NATree];
	volatile bool	childSense[NWTree];
	bool		hasChild[NATree];
	volatile bool	sense;
	int		arrivalParent;
	int		wakeupParent;
} __attribute__((packed));

BarrierRec *BR;

void omp_MCS_barrier_init () {
	int i, j;

	BR = (BarrierRec*) malloc (sizeof (BarrierRec) * NUM_THREADS);
	/* Arrival tree initialization */
	for (i = 0; i < NUM_THREADS; i++) {
		BR[i].arrivalParent = (i - 1) / NATree;
		BR[i].sense = true;
		for (j = 0; j < NATree; j++) {
			if ((NATree * i + j + 1) < NUM_THREADS) {
				BR[i].childNotReady[j] = BR[i].hasChild[j] = true;
			} else {
				BR[i].childNotReady[j] = BR[i].hasChild[j] = false;
			}
		}
	}
	BR[0].arrivalParent = -1;

	/* Wakeup tree initialization */
	for (i = 0; i < NUM_THREADS; i++) {
                BR[i].wakeupParent = (i - 1) / NWTree;
		for (j = 0; j < NWTree; j++) {
                        BR[i].childSense[j] = false;
		}
	}
	BR[0].wakeupParent = -1;
}

void omp_MCS_barrier () {
	int tid = omp_get_thread_num();
	int i, parent = 0;

	for (i = 0; i < NATree; i++) {
		while (BR[tid].childNotReady[i]);
	}
	for (i = 0; i < NATree; i++) {
                BR[tid].childNotReady[i] = BR[tid].hasChild[i];
        }
	if (!tid) {
		for (i = 0; i < NWTree; i++) {
			BR[tid].childSense[i] = BR[tid].sense;
		}
	} else {
		parent = BR[tid].arrivalParent;
		BR[parent].childNotReady[(tid - 1) % NATree] = false;

		parent = BR[tid].wakeupParent;
		while (BR[parent].childSense[(tid - 1) % NWTree] != BR[tid].sense);

		for (i = 0; i < NWTree; i++) {
                        BR[tid].childSense[i] = BR[tid].sense;
                }
	}
	BR[tid].sense = !BR[tid].sense;
}

int main(int argc, char **argv) {
  	int thread_num = -1;
	NUM_THREADS = atoi(argv[1]);

        omp_set_dynamic(0);
        omp_set_num_threads(NUM_THREADS);
        
	omp_MCS_barrier_init();
#pragma omp parallel num_threads(NUM_THREADS) firstprivate(thread_num)
  {
	struct timeval start, stop;
	gettimeofday (&start, NULL);
	for (int i = 0; i < ITER; i++) {
	        omp_MCS_barrier ();
	}
	gettimeofday (&stop, NULL);
	long double elapsed_sec = (long double)(stop.tv_sec - start.tv_sec);
	long double elapsed_usec = (long double)(stop.tv_usec - start.tv_usec);
	thread_num = omp_get_thread_num();
	printf ("Thread %d: time elapsed = %Lg secs or %Lg usecs\n", thread_num, elapsed_sec + elapsed_usec*1e-6, elapsed_sec*1000000 + elapsed_usec);
  }

  return 0;
}
