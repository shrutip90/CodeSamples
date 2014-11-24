#include "dissemination.h"
int n, rank;
struct timeval barrier_s, barrier_e;
void dissemination_barrier(int n, int rounds)
{
	int i = 0;
	int sbuff[1];
	int rbuff[1];
	MPI_Status status;
	for(i=0; i< rounds; i++)
	{
		int send_id = (rank + (int)pow(2,i))%n;
		int recv_id;
		if ( rank - pow(2,i) >= 0)
		       recv_id = (rank - (int)pow(2,i))%n;
		else
			recv_id = rank - (int)pow(2,i) + n;

//	        printf("send_id of rank %d in round %d = %d\n", rank,i,send_id);	
//	        printf("recv_id of rank %d in round %d= %d\n", rank,i,recv_id);	
		MPI_Send(&sbuff,1,MPI_INT,send_id,i,MPI_COMM_WORLD);

		MPI_Recv(&rbuff,1,MPI_INT,recv_id,i,MPI_COMM_WORLD,&status);
	}
	
}

void execute(int n, int r)
{
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
	dissemination_barrier(n,r);

	gettimeofday(&breakout,NULL);
	printf("timestamp after barrier %d %lu\n",rank, (breakout.tv_sec*1000000+breakout.tv_usec));
#endif
	int t = 0;
	gettimeofday(&barrier_s,NULL);
	for(t=0; t<TIMES;t++)
	{
		dissemination_barrier(n,r);
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
int main(int argc, char *argv[])
{


	MPI_Init (&argc, &argv);      /* starts MPI */
	MPI_Comm_rank (MPI_COMM_WORLD, &rank);        /* get current process id */
	MPI_Comm_size (MPI_COMM_WORLD, &n);        /* get number of processes */
	int rounds = ceil(log(n)/log(2));
	if(rank == 0)
	{
		printf("rounds = %d\n", rounds);
		printf("n = %d\n", n);
	}
	//init(&n, &rounds);
	execute(n,rounds);
	print_time(&barrier_s, &barrier_e);
	MPI_Finalize();
	return 0;
}
