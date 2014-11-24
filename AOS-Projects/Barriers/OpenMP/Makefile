CC = g++
CFLAGS =
COPTFLAGS = -Wall -pedantic -O3 -g
LDFLAGS =

OMPFLAGS = -fopenmp

default:
	@echo "================================================================"
	@echo "To build, use:"
	@echo "  make senseReverse-omp		# For Sense Reversing barrier"
	@echo "  make mcs-omp			# For MCS Barrier"
	@echo "  make regular-omp		# For Regular OMP Barrier"
	@echo ""
	@echo "To clean this subdirectory (remove object files"
	@echo "and other junk), use:"
	@echo "  make clean"
	@echo "================================================================"

# Sense Barrier for OpenMP
senseReverse-omp: senseReverse--omp.o
	$(CC) $(COPTFLAGS) $(OMPFLAGS) -o $@ $^

senseReverse--omp.o: senseReverse.c
	$(CC) $(COPTFLAGS) $(OMPFLAGS) -o $@ -c $<

# MCS Barrier for OpenMP
mcs-omp: mcs--omp.o
	$(CC) $(COPTFLAGS) $(OMPFLAGS) -o $@ $^

mcs--omp.o: MCS.c
	$(CC) $(COPTFLAGS) $(OMPFLAGS) -o $@ -c $<

# Regular Barrier for OpenMP
regular-omp: omp--barrier.o
	$(CC) $(COPTFLAGS) $(OMPFLAGS) -o $@ $^
omp--barrier.o: omp_barrier.c
	$(CC) $(COPTFLAGS) $(OMPFLAGS) -o $@ -c $<

clean:
	rm -f core *.o *~ mcs-omp senseReverse-omp regular-omp
# eof
