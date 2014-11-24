#include "tournament.h"
int n, rank;
int rounds;
struct timeval barrier_s, barrier_e;
void barrier(struct node* n);
void execute(struct node* n)
{
	//do something and then call barrier//
//	printf("inside execute\n");
#if 0
	float a[102][102];
	float b[102][102];
	float c[102][102];

	int i = 0, j=0, k=0;
	for(i=0;i<102;i++)
		for(j=0;j<102;j++)
		{
			a[i][j]=rand();
			b[i][j]=rand();
			b[i][j]=0;
		}
	for(i = 0 ;i< 102; i++)
	{
		for(j=0;j<102;j++)
		{
			for(k=0; k<102; k++)
			{
				c[i][j]+=c[i][k]*c[k][j];
			}
		}
	}
	barrier(n);
	printf("after barrier1 rank =%d\n", rank);
	gettimeofday(&breakout,NULL);
	if(rank == 0)
		printf("barrier time = %lu\n", (barrier_t.tv_sec*1000000+barrier_t.tv_usec));
	printf("rank %d breakout time %lu\n", rank,(breakout.tv_sec*1000000+breakout.tv_usec));

	for(i = 0 ;i< 102; i++)
	{
		for(j=0;j<102;j++)
		{
			for(k=0; k<102; k++)
			{
				c[i][j]+=c[i][k]*c[k][j];
			}
		}
	}
//	barrier(n);
//	printf("after barrier2 rank =%d\n", rank);
#endif

	int t = 0;
	gettimeofday(&barrier_s,NULL);
	for(t=0; t<TIMES;t++)
	{
		barrier(n);
	}
	gettimeofday(&barrier_e,NULL);
}
void print_time(struct timeval *s, struct timeval *e)
{
	long elapsed_sec = (e->tv_sec - s->tv_sec);
	long elapsed_msec = (e->tv_usec - s->tv_usec);
	if(s->tv_usec > e->tv_usec)
	{
		elapsed_msec = 1000000 - s->tv_usec + e->tv_usec;
		elapsed_sec-=1;
	}
	printf("Rank %d:total elapsed time for %d barriers: %ld:%ld\n",rank,TIMES,elapsed_sec,elapsed_msec);
	//double avg = (double)elapsed_time/(double)TIMES;
	//printf("Rank %d:average time per barrier: %lf\n",rank, avg);
}
void init(int *num, int *rounds)
{
	int r = *rounds;
	int n = *num;
	struct node eachRound[r+1];
	//initialize values for role, opponent, local_spin
	int i = 0;
	for( i = 0; i<=r; i++)
	{
		eachRound[i].local_spin = 1;
		if(i == 0)
			eachRound[i].role=DROPOUT;
		else if(i>0 && (rank==0) && (int)pow(2,i)>=n)
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

		if(eachRound[i].role == LOSER)
			eachRound[i].opponent = rank -(int)pow(2,i-1);
		else if(eachRound[i].role == WINNER || eachRound[i].role == CHAMPION)
			eachRound[i].opponent = rank + (int)pow(2,i-1);
		else
			eachRound[i].opponent = WASTE;
	}
#if 0
	for(i = 0;i <=r ;i++)
	{
		printf("Processor %d eachRound[%d].role=%d and eachRound[%d].opponent =%d\n", rank, i, (int)eachRound[i].role,i, (int)eachRound[i].opponent); 
	}
#endif
	execute(&eachRound);

	print_time(&barrier_s, &barrier_e);
	//barrier(&eachRound);
		
}
void barrier(struct node* n)
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
				count = 0;
				break;
			case BYE:
				break;
			case CHAMPION:
		//		printf("in champion %d case waiting for %d in round %d\n", rank,n[round].opponent,round);
				MPI_Recv(&buff,1,MPI_INT,n[round].opponent,1,MPI_COMM_WORLD,&status);
		//		printf("winner %d recevied msg from loser %d in round %d\n", rank, n[round].opponent,round);
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
				break;
			case BYE:
				break;
			case DROPOUT:
				count = 1;

		}
	}

}
int main(int argc, char *argv[])
{


	MPI_Init (&argc, &argv);      /* starts MPI */
	MPI_Comm_rank (MPI_COMM_WORLD, &rank);        /* get current process id */
	MPI_Comm_size (MPI_COMM_WORLD, &n);        /* get number of processes */
	rounds = (int)(log(n)/log(2));
	if(rank == 0)
	{
		printf("rounds = %d\n", rounds);
		printf("n = %d\n", n);
	}
	init(&n, &rounds);
	MPI_Finalize();
	return 0;
}
