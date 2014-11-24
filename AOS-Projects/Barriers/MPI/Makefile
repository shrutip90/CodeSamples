CC = mpicc
EXECUTABLES = tournament dissemination

all:
	$(CC) $(CFLAGS) tournament_non_power2.c -o tournament
	$(CC) $(CFLAGS) dissemination.c -o dissemination

clean:
	@rm $(EXECUTABLES)
