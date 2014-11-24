#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <omp.h>
#include "tournament.h"

#define NATree		8	/* #children for each node in the arrival tree */
#define NWTree		4	/* #children for each node in the wakeup tree */
#define ITER            100000	/* #iterations of the loop for experiment */

int num, rank;
int extra;
int rounds;
struct node *eachRound;
void tournament_barrier(struct node* n);
void print_time(struct timeval* s, timeval* e);

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
		tournament_barrier(eachRound);

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

void tournament_init(int *num, int *rounds)
{
	int r = *rounds;
	int n = *num;
	eachRound = (struct node*)malloc((r+1)*sizeof(struct node));
	
	//initialize values for role, opponent, local_spin
	int i = 0;
	for( i = 0; i<=r; i++)
	{
		eachRound[i].local_spin = 1;
		if(i == 0)
			eachRound[i].role=DROPOUT;
		//else if(i>0 && (rank==0) && (int)pow(2,i)>=n)
		else if(i==r && (rank==0))
		{
		//	printf("champion else\n");
			eachRound[i].role=CHAMPION;
		}
		else if(i>0 && (rank % (int)pow(2,i)==0) && (rank+pow(2,i-1)<n) && (pow(2,i)<n))
			eachRound[i].role=WINNER;
		else if(i>0 && (rank % (int)pow(2,i) == (int)pow(2,i-1)))
			eachRound[i].role=LOSER;
		else
			eachRound[i].role=BYE;
		if ( n &(n-1))
			 if(i==r && rank == extra)
				eachRound[i].role = LOSER;

		if(eachRound[i].role == LOSER)
			eachRound[i].opponent = rank -(int)pow(2,i-1);
		else if(eachRound[i].role == WINNER || eachRound[i].role == CHAMPION)
			eachRound[i].opponent = rank + (int)pow(2,i-1);
		else
			eachRound[i].opponent = WASTE;
		if ( n&(n-1))
		if(i == r && rank == extra)
			eachRound[i].opponent = 0;
	}
#if 0
	for(i = 0;i <=r ;i++)
	{
		printf("Processor %d eachRound[%d].role=%d and eachRound[%d].opponent =%d\n", rank, i, (int)eachRound[i].role,i, (int)eachRound[i].opponent); 
	}
#endif
}
void tournament_barrier(struct node* n)
{

	int round = 1;
	MPI_Status status;
	int buff[1];
	int sbuff[1];
	int rbuff[1];
	int count = 1;
	while(count ==1)
	{
		switch(n[round].role)
		{
			case WINNER:
				MPI_Recv(&buff,1,MPI_INT,n[round].opponent,1,MPI_COMM_WORLD,&status);
		//		printf("winner %d recevied msg from loser %d in round %d\n", rank, n[round].opponent,round);
				break;
			case LOSER:
				MPI_Send(&sbuff,1,MPI_INT,n[round].opponent,1,MPI_COMM_WORLD);
		//		printf("loser %d sent msg to winner %d in round %d\n", rank, n[round].opponent,round);
		//		printf("loser %d waiting for wake up msg from %d in round %d\n", rank, n[round].opponent, round);
				MPI_Recv(&rbuff,1,MPI_INT,n[round].opponent,2,MPI_COMM_WORLD,&status);
				n[round].local_spin=0;
				count = 0;
				break;
			case BYE:
				//count = 0;
				break;
			case CHAMPION:
		//		printf("in champion %d case waiting for %d in round %d\n", rank,n[round].opponent,round);
				MPI_Recv(&buff,1,MPI_INT,n[round].opponent,1,MPI_COMM_WORLD,&status);
		//		printf("winner %d recevied msg from loser %d in round %d\n", rank, n[round].opponent,round);
				if(num &(num-1))				
					MPI_Recv(&buff,1,MPI_INT,extra,1,MPI_COMM_WORLD,&status);
				
		//		printf("winner %d recevied msg from loser %d in round %d\n", rank, extra,round);
				count = 0;
				break;
		}
		round++;
	}
	while(count == 0)
	{
		round--;
		switch(n[round].role)
		{
			case LOSER:
				break;
			case WINNER:
		//		printf("winner %d sending wake up msg to %d in round %d\n", rank, n[round].opponent, round);
				MPI_Send(&sbuff,1,MPI_INT,n[round].opponent,2,MPI_COMM_WORLD);
				break;
			case CHAMPION:
		//		printf("champion %d sending wake up msg to %d in round %d\n", rank, n[round].opponent, round);
				MPI_Send(&sbuff,1,MPI_INT,n[round].opponent,2,MPI_COMM_WORLD);
				if(num &(num-1))
					if(round == rounds)
						MPI_Send(&sbuff,1,MPI_INT,extra,2,MPI_COMM_WORLD);
				break;
			case BYE:
				break;
			case DROPOUT:
				count = 1;

		}
	}
}

int main(int argc, char **argv) {
	MPI_Init (&argc, &argv);			/* starts MPI */
	MPI_Comm_rank (MPI_COMM_WORLD, &rank);        	/* get current process id */
	MPI_Comm_size (MPI_COMM_WORLD, &num);        	/* get number of processes */
	rounds = (int)(log(num)/log(2));
	int b = (int) (log(num)/log(2));	
	extra = (int) pow(2,b);

	/*if(rank == 0)
	{
		printf("rounds = %d\n", rounds);
		printf("n = %d\n", num);
		if(num & (num-1))
		{
			printf("number of processors is not a power of 2: extra = %d\n",extra);
		
		}
	}*/
	tournament_init(&num, &rounds);
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
  MPI_Finalize();

  return 0;
}
