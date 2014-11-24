CC = mpic++
<<<<<<< HEAD
CFLAGS =
COPTFLAGS =
LDFLAGS =

OMPFLAGS = -fopenmp

default:
	@echo "================================================================"
	@echo "To build, use:"
	@echo "  make combined		# For Combined OpenMP MPI barrier"
	@echo ""
	@echo "To clean this subdirectory (remove object files"
	@echo "and other junk), use:"
	@echo "  make clean"
	@echo "================================================================"

# Combined Barrier
combined: combinedBarrier.o
	$(CC) $(COPTFLAGS) $(OMPFLAGS) -o $@ $^

combinedBarrier.o: combinedBarrier.c
	$(CC) $(COPTFLAGS) $(OMPFLAGS) -o $@ -c $<

clean:
	rm -f core *.o *~ combined
# eof
=======
CFLAGS = -fopenmp
EXECUTABLES = combined

all:
	$(CC) $(CFLAGS) combinedBarrier.c -o combined

clean:
	@rm $(EXECUTABLES)
>>>>>>> f0cbea85d7f4bc77724c9da65159fc5537652377
