What Linux platform do you use?
-----------------------------------------
I have used the Killerbee machine at College of Computing for the development and testing.

How the preemptive scheduler is implemented?
-------------------------------------------------
The preemptive scheduler is implemented using the signal SIGVTALRM. On encountering the signal, the scheduler picks up a different thread to run
and does the context switch. The signal is scheduled to be triggered periodically based on the time period specified in the gtthread_init function.
The signal is also triggered by the functions gtthread_yield, gtthread_join and gtthread_exit when the thread wants to preempt itself due to completion,
in case of gtthread_exit or because it needs to wait for another thread, in case of gtthread_join and to yield the processor to another thread, in case of
gtthread_yield.
The signalling is disabled in certain parts of the code to allow for correct manipulation of the data-structures used for storing thread metadata. It is
also disabled in gtthread_mutex_lock for implementing the locking without using atomic operations.

How to compile your library and run your program.
--------------------------------------------------------
To compile, give the command "make".
To run, give the command "./phil".

How you prevent deadlocks in your Dining Philosophers solution.
---------------------------------------------------------------------
There is a mutex allocated for each fork and another global mutex in the program. The threads for each philosopher spend a random amount of time in thinking
and then acquire the forks by first locking the global mutex and then locking the left fork and right fork, then unlock the global mutex. This way whoever obtains
the global lock would be able to eat at some point. This ensures that only one philosopher will be able to pick up the forks at a time. So there is no 
possibility for deadlocks. But this solution is very inefficient, as a philosopher who obtains the global lock would have to wait for his neighbours to finish
eating if they holding one of the forks.

Any thoughts you have on the project, including things that work especially well or which don't work.
------------------------------------------------------------------------------------------------------------
I had also implemented the thread library using circular queues for storing the metatdata, after my initial implementation using static array. But the queue
implementation didn't seem to provide any added advantage except for the dynamic memory allocation and the array based implementation was much simpler in terms
of searching the data-structure for threads to be joined or cancelled. Though I contemplated for long, whether I should do use the queue based or array based
implementation, I finally decided to keep the array-based one as the project allowed us to restrict the maximum number of threads. In a real implementation though,
I would definitely use the queue based implementation.
