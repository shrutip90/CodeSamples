#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>
#include "gtthread.h"

#define N 5
#define TIMEPERIOD 1000
#define THINKING_PART 5
#define EATING_PART 3

gtthread_mutex_t g_mutex;
gtthread_mutex_t fork_mutex[N];

void think (int p) {
	int i, factor;
	volatile int j;

	printf("Philosopher #%d is thinking\n", p);
	fflush (stdout);

	factor = 1 + 2 % THINKING_PART;
	for (i = 0; i < 100000000 * factor; i++) {
		j += (int) i*i;
  	}    
}

void eat (int p) {
	int i, factor;
	volatile int j;

	printf("Philosopher #%d is eating\n", p);
	fflush (stdout);
	
	factor = 1 + 2 % EATING_PART;
	for (i = 0; i < 100000000 * factor; i++) {
		j += (int) i*i;
	}
}

void get_forks (int p) {
  	printf("Philosopher #%d is hungry\n", p);
	fflush (stdout);
	gtthread_mutex_lock(&g_mutex);
	printf("Philosopher #%d tries to acquire left chopstick\n", p);
	gtthread_mutex_lock(&(fork_mutex[p]));
	printf("Philosopher #%d tries to acquire right chopstick\n", p);
	gtthread_mutex_lock(&(fork_mutex[(p + N - 1) % N]));
	gtthread_mutex_unlock(&g_mutex);
}

void put_forks (int p) {
	printf("Philosopher #%d releases left chopstick\n", p);
	gtthread_mutex_unlock(&(fork_mutex[p]));
	printf("Philosopher #%d releases right chopstick\n", p);
	gtthread_mutex_unlock(&(fork_mutex[(p + N - 1) % N]));
}

void philosopher (int p) {
	while (1) {
		think(p);
		get_forks(p);
    		eat(p);
   		put_forks(p);
  	}
}

int main (int argc, char **argv) {
	int i;

	gtthread_init(1000);
	gtthread_mutex_init(&g_mutex);
  
	for (i = 0; i < N ; i++){
		gtthread_mutex_init(&(fork_mutex[i]));
  	}
	gtthread_t th[N];
	for (i = 0; i < N ; i++){
		gtthread_create (&th[i], philosopher, i);
  	}
	for (i = 0; i < N ; i++){
		gtthread_join (th[i], NULL);	
	}
	return 0;
}
