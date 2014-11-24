#include<stdio.h>
#include "mpi.h"
#include<math.h>
#include<sys/time.h>
#include<time.h>
#define TIMES 100000
#define WINNER 0
#define LOSER 1
#define BYE 2
#define CHAMPION 3
#define DROPOUT 4
#define WASTE -1
struct node
{
	short role;
	short opponent;
	short local_spin;
};

extern void init();

