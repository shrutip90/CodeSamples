#include <stdio.h>
#include <unistd.h>
#include <sys/time.h>
#include <iostream>
#include <fstream>
#include <string>
#include <stdlib.h>
#include <boost/random.hpp>

#include <thrift/protocol/TBinaryProtocol.h>
#include <thrift/transport/TSocket.h>
#include <thrift/transport/TTransportUtils.h>

#include "../gen-cpp/RPCproxy.h"

#define NUM_URLS 125	/* Maximum number of URLs to be read from the input */
#define NROLLS 625	/* Total number of URL requests to be issues */

int workload = 0;

using namespace std;
using namespace apache::thrift;
using namespace apache::thrift::protocol;
using namespace apache::thrift::transport;

using namespace proxy;
using namespace boost;

int main(int argc, char** argv) {
  if (argc != 5) {
	cout << "Usage: ./client <ip-address> <port-number> <url-file> <workload-option>" << endl << endl;
	cout << "Choose Workload option as:" << endl;
	cout << "\t0: Uniform Distribution" << endl;
	cout << "\t1: Exponential Distribution" << endl;

	cout << endl;
	return 0;
  }
  workload = atoi(argv[4]);
  if (workload != 0 && workload != 1) {
  	cout << "Incorrect workload " << workload << endl;
	return 0;
  }
  shared_ptr<TTransport> socket(new TSocket(argv[1], atoi (argv[2])));
  shared_ptr<TTransport> transport(new TBufferedTransport(socket));
  shared_ptr<TProtocol> protocol(new TBinaryProtocol(transport));
  RPCproxyClient client(protocol);

  string contents, url;
  string urls[NUM_URLS];

  int num_urls = 0;
 
  try {
    transport->open();

    client.ping();
    printf("ping()\n");
    
    ofstream f("out.html");
    if(!f.is_open()) {
	cout << "Unable to open output file. Exiting."<<endl;
	transport->close();
	exit(0);
    }

    /*if (argc > 1) {
	url = argv[1];
	client.getURL(contents, url);
	printf("size of contents = %u\n",contents.size());

	f << contents;
    	printf("CONTENTS of url written to out.html\n");
	f.close();
	transport->close();
	return 0;
    }*/

    FILE *urlfile = fopen (argv[3], "r");
    char *tempurl = (char*) malloc (sizeof (char) * 80);
    
    int i = 0; string line;
    if (urlfile) {
      while ( i < NUM_URLS && (fscanf (urlfile, "%[^\n]\n", tempurl) != EOF)) {
	string s(tempurl);
        urls[i++] = s;
      }
      num_urls = i;
    } else {
      cout << "Unable to open input file" << endl;
      transport->close();
      exit(0);
    }
    fclose(urlfile);

	struct timeval start, stop;
	long double timer = 0.0;

	/* Generating workload */
	if (num_urls > 0) {
		const int nrolls=NROLLS;  // number of experiments
  		const int nintervals = num_urls; // number of intervals
	if (workload == 0) {
		boost::mt19937 rng(1000u); 
		boost::uniform_real<>dist(0,num_urls);
 		boost::variate_generator< boost::mt19937&, boost::uniform_real<> > rndm(rng, dist) ;
  

  		for (int i = 0; i < nrolls; ++i) {
			double number = rndm();
    			if (number < 1.0) {
				url = urls[int(nintervals*number)];
				cout << "URL = " << url << endl;

				gettimeofday(&start, NULL);
				client.getURL(contents, url);
				gettimeofday(&stop, NULL);

				long double secs = (long double)(stop.tv_sec - start.tv_sec);
				long double usecs = (long double)(stop.tv_usec - start.tv_usec);
				timer += secs + usecs * (1e-6);
				//printf("size of contents = %u\n",contents.size());

				f << contents;
    				printf("%d: timer = %Lg\n", i, timer);
			} else {
				i--;
			}
  		}
	}



	if (workload == 1) {
		boost::mt19937 rng(1000u);
  		boost::variate_generator< boost::mt19937&, boost::exponential_distribution<> > rndm(rng, boost::exponential_distribution<>()) ;

  		for (int i = 0; i < nrolls; ++i) {
			double number = rndm();
    			if (number < 1.0) {
				url = urls[int(nintervals*number)];
				cout << "URL = " << url << endl;

				gettimeofday(&start, NULL);
				client.getURL(contents, url);
				gettimeofday(&stop, NULL);

				long double secs = (long double)(stop.tv_sec - start.tv_sec);
				long double usecs = (long double)(stop.tv_usec - start.tv_usec);
				timer += secs + usecs * (1e-6);
				//printf("size of contents = %u\n",contents.size());

				f << contents;
    				printf("%d: timer = %Lg\n", i, timer);
			} else {
				i--;
			}
  		}
	}
		client.getCacheStats();
      	}
      	f.close();
	transport->close();
  } catch (TException &tx) {
    printf("ERROR: %s\n", tx.what());
  }

}
