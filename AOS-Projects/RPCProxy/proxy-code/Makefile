CC = g++
INCLUDE = /usr/local/include/thrift
LIBS = /usr/local/lib
CFLAGS = -g -I $(INCLUDE)

SDIR = src
ODIR = obj
GDIR = gen-cpp

GOBJ = $(GDIR)/proxy_constants.o $(GDIR)/proxy_types.o $(GDIR)/RPCproxy.o 

EXECUTABLES = client server

all: client server

client: $(GOBJ) $(ODIR)/Client.o
	$(CC) -L $(LIBS) -o $@ $^ -lthrift

server: $(GOBJ) $(ODIR)/Server.o $(ODIR)/Cache.o $(ODIR)/List.o $(ODIR)/Hash.o
	$(CC) -L $(LIBS) -o $@ $^ -lthrift -lcurl

$(GDIR)/%.o: $(GDIR)/%.cpp
	$(CC) $(CFLAGS) -o $@ -c $<

$(ODIR)/%.o: $(SDIR)/%.cpp
	$(CC) $(CFLAGS) -o $@ -c $<

clean:
	rm -f $(GDIR)/*.o
	rm -f $(ODIR)/*.o
	rm -f $(EXECUTABLES)
