#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <string.h>
#include <math.h>
#include <sys/time.h>
#include "queue.h"

#include "driver.h"
//#define DEBUG
//#define CUDADEBUG

#define INF 0xffffffff
#define ITER 5
#define DENSITY 25
#define MAX_DEGREE 80

#ifdef DEBUG
#define dbgprint1(a1)                           fprintf(stderr,a1)
#define dbgprint2(a1,a2)                        fprintf(stderr,a1,a2)
#define dbgprint3(a1,a2,a3)                     fprintf(stderr,a1,a2,a3)
#define dbgprint4(a1,a2,a3,a4)                  fprintf(stderr,a1,a2,a3,a4)
#define dbgprint5(a1,a2,a3,a4,a5)               fprintf(stderr,a1,a2,a3,a4,a5)
#define dbgprint6(a1,a2,a3,a4,a5,a6)            fprintf(stderr,a1,a2,a3,a4,a5,a6)
#define dbgprint7(a1,a2,a3,a4,a5,a6,a7)         fprintf(stderr,a1,a2,a3,a4,a5,a6,a7)
#define dbgprint8(a1,a2,a3,a4,a5,a6,a7,a8)      fprintf(stderr,a1,a2,a3,a4,a5,a6,a7,a8)

#else
#define dbgprint1(a1)
#define dbgprint2(a1,a2)
#define dbgprint3(a1,a2,a3)
#define dbgprint4(a1,a2,a3,a4)
#define dbgprint5(a1,a2,a3,a4,a5)
#define dbgprint6(a1,a2,a3,a4,a5,a6)
#define dbgprint7(a1,a2,a3,a4,a5,a6,a7)
#define dbgprint8(a1,a2,a3,a4,a5,a6,a7,a8)
#endif

#ifdef CUDADEBUG
#define cudadbgprint1(a1)                           printf(a1)
#define cudadbgprint2(a1,a2)                        printf(a1,a2)
#define cudadbgprint3(a1,a2,a3)                     printf(a1,a2,a3)
#define cudadbgprint4(a1,a2,a3,a4)                  printf(a1,a2,a3,a4)
#define cudadbgprint5(a1,a2,a3,a4,a5)               printf(a1,a2,a3,a4,a5)

#else
#define cudadbgprint1(a1)
#define cudadbgprint2(a1,a2)
#define cudadbgprint3(a1,a2,a3)
#define cudadbgprint4(a1,a2,a3,a4)
#define cudadbgprint5(a1,a2,a3,a4,a5)
#endif

int
compareAns(dtype* reference, dtype* data, const unsigned int len) {
	unsigned int i;
	
	for(i = 0; i < len; ++i) {
		if (reference[i] != data[i]) {
			return 0;
		}
	}
	return 1;
}

void
cpuBFS(dtype* C, dtype* R, unsigned int N, int* D)
{
	int i, j, k;
	if (N == 0) {
		return;
	}
	Queue* q = (Queue*)malloc (sizeof (Queue));
	q = initialize(q);
	
	D[0] = 0;
	q = enqueue(q, 0);
	while (!empty(q)) {
		Element e;
		q = dequeue(q, &e);
		for (j = R[e]; j < R[e+1]; j++) {
			k = C[j];
			if (D[k] == -1) {
				D[k] = D[e] + 1;
				q = enqueue(q, k);
			}
		}
	}
	free(q);
}

void
parseArgs (int argc, char** argv, unsigned int *N)
{
	if(argc < 2) {
		fprintf (stderr, "usage: %s <N>\n", argv[0]);
		exit (EXIT_FAILURE);
	} else {
		*N = atoi (argv[1]);
	}
}

int
initGraph (dtype* A, unsigned int N, int density, int Pdegree)
{
	int i, j, num_edges = 0;
	double Prange = ((double)(100.0 - density) / 100.0);
	int connect = 0;
	int max_degree = (Pdegree * N / 100);
	int degree;

        for (i = 0; i < N; i++) {
		connect = 0;
		degree = 0;
                for (j = 0; j < N; j++){
                        if (i == j){
                                A[i * N + j] = 1;
                                continue;
                        }
			if (degree == max_degree) {
				A[i * N + j] = 0;
				continue;
			}
                        double pr = drand48();
			//printf ("pr = %lf, Prange = %lf\n", pr, Prange);
			if (pr > Prange) {
                        	A[i * N + j] = 1;
				degree++;
				connect = 1;
				num_edges++;
			} else {
				A[i * N + j] = 0;
			}
                }
		if (!connect) {
			int pr = rand();
			j = pr % N;
			if (j == i) {
				A[i * N + j + 1] = 1;
			} else {
				A[i * N + j] = 1;
			}
			num_edges++;
		}
        }
	return num_edges;
}
void
generateCR (dtype *G, unsigned int N, dtype *C, dtype *R) {
	int i, j, cur = 0;

        for (i = 0; i < N; i++) {
		R[i] = cur;
                for (j = 0; j < N; j++){
			if ((G[i * N + j] == 1) && (i != j)) {
				C[cur] = j;
				cur++;	
			}
		}
	}
	R[i] = cur;
}
void
generateEdgeList (dtype *G, unsigned int N, dtype *E) {
	int i, j, cur = 0;

	for (i = 0; i < N; i++) {
		for (j = 0; j < N; j++){
                        if (G[i * N + j] == 1 && i != j) {
				E[cur++] = i;
				E[cur++] = j;
			}
		}
	}
}
int main (int argc, char** argv)
{
	/* declare variables */
	dtype *G, *C, *R, *E;
	int *h_D, *d_D1, *d_D2;

	unsigned int N, OPT;
	int i, j, num_edges;

	srand (time(NULL));

	/* read arguments */
	N = 0;
	parseArgs (argc, argv, &N);
	assert ((N > 0));

	/* declare and initialize data */
	G = (dtype*) malloc (N * N * sizeof (dtype));
	R = (dtype*) malloc ((N + 1) * sizeof (dtype));
	
	num_edges = initGraph (G, N, DENSITY, MAX_DEGREE);
	//printf ("Num edges = %d\n", num_edges);
	C = (dtype*) malloc (num_edges * sizeof (dtype));

	generateCR (G, N, C, R);
	
	E = (dtype*) malloc (num_edges * 2 * sizeof (dtype));
	generateEdgeList (G, N, E);

	h_D = (int *) malloc (N * sizeof (int));
	d_D1 = (int *) malloc (N * sizeof (int));
	d_D2 = (int *) malloc (N * sizeof (int));

	struct timeval tv_start, tv_stop;

	dbgprint1 ("Adjecency list:\n");
	for (i = 0; i < N; i++) {
		printArray (G + i*N, N);
	}
	dbgprint1 ("C:\n");
	printArray (C, num_edges);
	dbgprint1 ("R:\n");
	printArray (R, N + 1);
	dbgprint1 ("E:\n");
	printArray (E, num_edges * 2);

	for (i = 0; i < ITER; i++) {
		for (j = 0; j < N; j++) {
			d_D2[j] = d_D1[j] = h_D[j] = -1;
		}
		/* do GPU BFS */
		cudaBFS_Edgelist (E, N, num_edges, d_D1);
		cudaBFS_Merill (C, R, N, num_edges, d_D2);
		
		dbgprint1 ("GPU result Edgelist:\n");
		printIntArray (d_D1, N);
		dbgprint1 ("GPU result Edgelist:\n");
		printIntArray (d_D2, N);
		
		/* compare answers */
		gettimeofday(&tv_start, NULL);	
			cpuBFS (C, R, N, h_D);
		gettimeofday(&tv_stop, NULL);
		fprintf (stderr, "Time to execute CPU BFS code: %ld usecs\n", ((tv_stop.tv_sec * 1000000 + tv_stop.tv_usec) - 
												(tv_start.tv_sec * 1000000 + tv_start.tv_usec)));
		dbgprint1 ("CPU result:\n");
		printArray (h_D, N);

		if(compareAns(h_D, d_D1, N)) {
			fprintf (stderr, "Correct answer BFS_Edgelist\n\n");
		} else {
			fprintf (stderr, "Incorrect answer BFS_Edgelist\n\n");
		}
		if(compareAns(h_D, d_D2, N)) {
			fprintf (stderr, "Correct answer BFS_Merill\n\n");
		} else {
			fprintf (stderr, "Incorrect answer BFS_Merill\n\n");
		}
	}
	free (G);
	free (C);
	free (R);
	free (E);
	free (h_D);
	free (d_D1);
	free (d_D2);
	
	return 0;
}
