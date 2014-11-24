#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <omp.h>
#include <sys/time.h>

#define ITER            1000000
#define DEBUG

static int NUM_THREADS;

int main(int argc, char **argv) {
  	int thread_num = -1;
	NUM_THREADS = atoi(argv[1]);

        omp_set_dynamic(0);
        omp_set_num_threads(NUM_THREADS);
        
#pragma omp parallel num_threads(NUM_THREADS) firstprivate(thread_num)
  {
	struct timeval start, stop;
        gettimeofday (&start, NULL);
        for (int i = 0; i < ITER; i++) {
#pragma omp barrier
        }
        gettimeofday (&stop, NULL);
        long double elapsed_sec = (long double)(stop.tv_sec - start.tv_sec);
        long double elapsed_usec = (long double)(stop.tv_usec - start.tv_usec);
        thread_num = omp_get_thread_num();
        printf ("Thread %d: time elapsed = %Lg secs or %Lg usecs\n", thread_num, elapsed_sec + elapsed_usec*1e-6, elapsed_sec*1000000 + elapsed_usec);
  }

  return 0;
}
