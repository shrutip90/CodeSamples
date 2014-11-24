#### GTThread Library Makefile

CFLAGS  = -Wall -pedantic
LFLAGS  =
CC      = gcc
RM      = /bin/rm -rf
AR      = ar rc
RANLIB  = ranlib

LIBRARY = gtthread.a

SRC = gtthread.c philosopher.c

LIB_OBJ = gtthread.o
phil_OBJ = philosopher.o


# pattern rule for object files
%.o: %.c
	$(CC) -c $(CFLAGS) $< -o $@

all: $(LIBRARY) phil

$(LIBRARY): $(LIB_OBJ)
	$(AR) $(LIBRARY) $(LIB_OBJ)
	$(RANLIB) $(LIBRARY)

phil: $(phil_OBJ)
	$(CC) $(CFLAGS) $^ -o $@ $(LIBRARY)

clean:
	$(RM) $(LIBRARY) $(LIB_OBJ) $(phil_OBJ)

.PHONY: depend
depend:
	$(CFLAGS) -- $(LIB_SRC)  2>/dev/null
