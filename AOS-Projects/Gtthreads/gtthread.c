#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <sched.h>
#include <sys/time.h>
#include <signal.h>
#include <string.h>
#include <ucontext.h>
#include <sys/types.h>
#include <unistd.h>
#include "gtthread.h"

#define MAX_NUM_THREADS 128

static int created_thr;
static volatile int curr_thread;
static int num_threads;

static gtthread_attr_t thr_table[MAX_NUM_THREADS];
static long interval;

void schedule_next_thread (int signal) {
	gtthread_t prev;
	sigset_t set;

        switch(signal) {
                case SIGVTALRM:
	        	sigemptyset(&set);
	        	sigaddset(&set, SIGVTALRM);
        		sigprocmask(SIG_BLOCK, &set, NULL);

                        prev = curr_thread;
			gtthread_t next = curr_thread;
			int i = 0;

			while (i < created_thr) {
				next = (next + 1) % created_thr;
				if (thr_table[next].valid && !thr_table[next].zombie && (thr_table[next].wait == -1)) {
					curr_thread = next;
					break;
				}
				i++;
			}
			//printf ("Scheduling new_thread = %d\n", curr_thread);

			sigprocmask(SIG_UNBLOCK, &set, NULL);
			if (curr_thread != prev) {
	                        if (swapcontext(&thr_table[prev].uc, &thr_table[curr_thread].uc) < 0) {
        	                        assert("schedule_next_thread: swapcontext failed");
                	                exit(1);
				}
                        }
                        break;
                default:
                        assert("schedule_next_thread: wrong signal");
                        exit(1);
        }
}

int timer_start() {
        struct sigaction sa;
        struct itimerval itv;

        itv.it_interval.tv_sec = itv.it_value.tv_sec = 0;
        itv.it_interval.tv_usec = itv.it_value.tv_usec = interval;

        memset(&sa, 0, sizeof(struct sigaction));
        sa.sa_flags = SA_RESTART;
        sigfillset(&sa.sa_mask);
        sa.sa_handler = schedule_next_thread;

        if (sigaction(SIGVTALRM, &sa, NULL) < 0) {
                assert("timer_start: SIGVTALRM failed");
                return SYS_ERR;
        }
        if (setitimer(ITIMER_VIRTUAL, &itv, NULL) < 0) {
                assert("timer_start: setitimer failed");
                return SYS_ERR;
        }

/*	gtthread_t old_thread = 0;
	curr_thread = 1;
	
	//printf ("Scheduling first thread = %d\n", curr_thread);
        if (swapcontext(&thr_table[old_thread].uc, &thr_table[curr_thread].uc) < 0) {
                assert("timer_start: swapcontext failed");
                return SYS_ERR;
        }*/
        return 0;
}

void gtthread_init(long period) {
	int i;

	created_thr = 1;
	curr_thread = 0;
	interval = period;
	num_threads = 1;

	for (i = 0; i < MAX_NUM_THREADS; i++) {
		thr_table[i].valid = 0;
		thr_table[i].zombie = 0;
		thr_table[i].cancel = 0;
		thr_table[i].wait = -1;
		thr_table[i].join = -1;
		thr_table[i].func = NULL;
		thr_table[i].arg = NULL;
		thr_table[i].ret = NULL;
		thr_table[i].status = NULL;
	}
	thr_table[curr_thread].valid = 1;
	timer_start();
}

void call_thread_function () {
	void *ret;
	if (thr_table[curr_thread].arg == NULL){
        	ret = (void*) thr_table[curr_thread].func(NULL);
        } else {
                ret = (void*) thr_table[curr_thread].func(thr_table[curr_thread].arg);
        }
	if (!thr_table[curr_thread].zombie) {
		gtthread_exit(ret);
	}
}

int gtthread_create(gtthread_t *thread,
                     void *(*start_routine)(void *),
                     void *arg)
{
	sigset_t set;
	sigemptyset(&set);
	sigaddset(&set, SIGVTALRM);
	sigprocmask(SIG_BLOCK, &set, NULL);
	
	gtthread_t new = created_thr; 
	if (new == MAX_NUM_THREADS) {
                return EAGAIN;
	}
	assert (new <= MAX_NUM_THREADS);
	created_thr++;
	num_threads++;
	sigprocmask(SIG_UNBLOCK, &set, NULL);
        
	if ((thr_table[new].stack = malloc(sizeof(char) * STACKSIZE)) == NULL) {
                assert("gtthread_create: malloc failed");
                return EAGAIN;
        }
	thr_table[new].valid = 1;
        thr_table[new].func = start_routine;
        thr_table[new].arg = arg;

        if (getcontext(&thr_table[new].uc) < 0) {
                assert("gtthread_create: getcontext failed");
                return SYS_ERR;
        }

        thr_table[new].uc.uc_stack.ss_sp = thr_table[new].stack;
        thr_table[new].uc.uc_stack.ss_size = STACKSIZE;
        thr_table[new].uc.uc_link = 0;
        makecontext(&thr_table[new].uc, call_thread_function, 0);

	*thread = new;
        return 0;
}

void gtthread_clean(gtthread_t tid){
	thr_table[tid].valid = 0;
	thr_table[tid].zombie = 0;
	thr_table[tid].cancel = 0;
	thr_table[tid].wait = -1;
	thr_table[tid].join = -1;
	thr_table[tid].func = NULL;
	thr_table[tid].arg = NULL;
	thr_table[tid].ret = NULL;
	thr_table[tid].status = NULL;
	//free(thr_table[tid].stack);
	num_threads--;
}
int gtthread_join(gtthread_t thread, void **status) {
	int ret;

	sigset_t set;
        sigemptyset(&set);
        sigaddset(&set, SIGVTALRM);
        sigprocmask(SIG_BLOCK, &set, NULL);

	if (thr_table[thread].valid == 0){
		//No thread with the ID thread could be found
		ret = ESRCH;
	} else if (thr_table[thread].join != -1) {
		//Another thread is already waiting to join with this thread
		ret = EINVAL;
	} else if (thr_table[thread].wait == curr_thread) {
		//Deadlock detected (e.g., two threads tried to join with each other); or thread specifies the calling thread
		ret = EDEADLK;
	} else if (thr_table[curr_thread].wait != -1) {
		//thread already waiting for another thread
		//Don't think this can happen. But anyways.
		ret = EINVAL;
	} else {
		if (status) {
			*status = thr_table[thread].ret;
			thr_table[curr_thread].status = status;
		}
		if (thr_table[thread].zombie) {
			if (thr_table[thread].cancel) {
				if (status) {
					*status = (void *) PTHREAD_CANCELED;
				}
			}
			gtthread_clean(thread);	
		} else {
			thr_table[curr_thread].wait = thread;
			thr_table[thread].join = curr_thread;
		}

		sigprocmask(SIG_UNBLOCK, &set, NULL);
		if (kill(getpid(), SIGVTALRM) < 0) {
        	        assert("gtthread_join: kill-sigalrm failed");
                	return -1;
        	}
		ret = 0;
	}
	sigprocmask(SIG_UNBLOCK, &set, NULL);
	return ret;
}

void gtthread_exit(void *retval) {
	sigset_t set;
        sigemptyset(&set);
        sigaddset(&set, SIGVTALRM);
        sigprocmask(SIG_BLOCK, &set, NULL);

	if (num_threads <= 1){
		//only main thread is running
		sigprocmask(SIG_UNBLOCK, &set, NULL);
		exit(0);
	}
	if (retval) {
		thr_table[curr_thread].ret = retval;
	}
	thr_table[curr_thread].zombie = 1;

	if (thr_table[curr_thread].join != -1) {
		void **status = thr_table[thr_table[curr_thread].join].status;
		if (status) {
			*status = retval;
		}
		thr_table[thr_table[curr_thread].join].wait = -1;
		thr_table[curr_thread].join = -1;
		gtthread_clean(curr_thread);
	}
	sigprocmask(SIG_UNBLOCK, &set, NULL);
	if (kill(getpid(), SIGVTALRM) < 0) {
                assert("gtthread_exit: kill-sigalrm failed");
        }
}

//On success, sched_yield() returns 0.  On error, -1 is returned, and errno is set appropriately.
int gtthread_yield(void) {
	if (kill(getpid(), SIGVTALRM) < 0) {
		assert("gtthread_yield: kill-sigalrm failed");
                return -1;
       	}
	return 0;
}

int gtthread_equal(gtthread_t t1, gtthread_t t2) {
	if (t1 == t2) {
		return 1;
	}
	return 0;
}

int gtthread_cancel(gtthread_t thread) {
	sigset_t set;
        sigemptyset(&set);
        sigaddset(&set, SIGVTALRM);
        sigprocmask(SIG_BLOCK, &set, NULL);

	if (thr_table[thread].valid == 0){
                //No thread with the ID thread could be found.
                sigprocmask(SIG_UNBLOCK, &set, NULL);
		return ESRCH;
	}
	
	if (num_threads <= 1){
		//only main thread is running
		sigprocmask(SIG_UNBLOCK, &set, NULL);
		exit(0);
	}
	thr_table[thread].zombie = 1;
	thr_table[thread].cancel = 1;

	if (thr_table[thread].join != -1) {
		thr_table[thr_table[thread].join].wait = -1;
		thr_table[thread].join = -1;
	}
	sigprocmask(SIG_UNBLOCK, &set, NULL);
	return 0;
}

gtthread_t gtthread_self(void) {
	return curr_thread;
}

int gtthread_mutex_init(gtthread_mutex_t *mutex) {
	if (mutex == NULL) {
		return ENOMEM;
	}/* else if (mutex->effective) {
		return EBUSY;
	}*/
	mutex->effective = 1;
	mutex->owner = -1;
	mutex->locked = 0;
	return 0;
}
int gtthread_mutex_lock(gtthread_mutex_t *mutex) {
	if (!mutex->effective || mutex == NULL) {
		return EINVAL;
	} else if (mutex->locked && mutex->owner == curr_thread) {
		return EDEADLK;
	} else {
		sigset_t set;
	        sigemptyset(&set);
        	sigaddset(&set, SIGVTALRM);
	        sigprocmask(SIG_BLOCK, &set, NULL);

		while (mutex->locked) {
			sigprocmask(SIG_UNBLOCK, &set, NULL);
			gtthread_yield();
		}
		mutex->locked = 1;
		mutex->owner = curr_thread;
		sigprocmask(SIG_UNBLOCK, &set, NULL);
		return 0;
	}
}
int gtthread_mutex_unlock(gtthread_mutex_t *mutex) {
	if (!mutex->effective || mutex == NULL) {
		return EINVAL;
	} else if (mutex->owner != curr_thread) {
		return EPERM;
	} else if (mutex->locked == 0) {
		return SYS_ERR;
	} else {
		mutex->owner = -1;
		mutex->locked = 0;
	}	
	return 0;
}
