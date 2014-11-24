#include <thrift/concurrency/ThreadManager.h>
#include <thrift/concurrency/PosixThreadFactory.h>
#include <thrift/protocol/TBinaryProtocol.h>
#include <thrift/server/TSimpleServer.h>
#include <thrift/server/TThreadPoolServer.h>
#include <thrift/server/TThreadedServer.h>
#include <thrift/transport/TServerSocket.h>
#include <thrift/transport/TTransportUtils.h>

#include <iostream>
#include <stdexcept>
#include <sstream>
#include <string>

#include <curl/curl.h>
#include "../gen-cpp/RPCproxy.h"
#include "Cache.h"

using namespace std;
using namespace apache::thrift;
using namespace apache::thrift::protocol;
using namespace apache::thrift::transport;
using namespace apache::thrift::server;

using namespace proxy;
using namespace boost;

Cache Cache_obj;
int no_cache = 0;

static size_t WriteCallback(void *contents, size_t size, size_t nmemb, void *userp) {
	((std::string*)userp)->append((char*)contents, size * nmemb);
	return size * nmemb;
}

string ifInCache(string url) {
	string ret;
	ret = Cache_obj.getContents(url);
	return ret;
}
void addContents(string url, string url_contents) {
	Cache_obj.addContents( url,url_contents);
}
class RPCproxyHandler : public RPCproxyIf{
	public:
	RPCproxyHandler() {}
	
	void ping() {
		printf("ping()\n");
	}
	void getURL(std::string& _return, const std::string& url) {
		// printf("getURL\n");
		// cout<<"url "<<url<<endl;
		CURL *curl;
		CURLcode res;
		string readBuffer;
		string ret = "not";
		if (!no_cache) {
			ret = ifInCache(url);
		}
		if(ret == "not") {
	
			/* Get curl handle. Each thread needs unique handle. */
			curl = curl_easy_init();

			if(NULL != curl) {
				/* Set the URL for the operation. */
				curl_easy_setopt(curl, CURLOPT_URL, url.c_str());
				curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);
				curl_easy_setopt(curl, CURLOPT_TIMEOUT_MS, 10000);
				/* "WriteCallback" is called with returned data. */
				curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, WriteCallback);

				/* userp parameter passed to write_data. */
				curl_easy_setopt(curl, CURLOPT_WRITEDATA, &readBuffer);

				/* Perform the query. */
				res = curl_easy_perform(curl);

				/* Check the return value and do whatever. */
				if(res != CURLE_OK) {
					fprintf(stderr, "curl_easy_perform() failed: %s\n", curl_easy_strerror(res));
				} else {
					if (!no_cache) {
						addContents(url,readBuffer);
					}
					_return = readBuffer;
				}
				curl_easy_cleanup(curl);
			} else {
				fprintf(stderr, "Error: could not get CURL handle.\n");
				exit(EXIT_FAILURE);
			}
		} else {
			//printf("Fetched from cache\n");
			_return = ret;
		}
	}
	void getCacheStats() {
    		printf("getCacheStats\n");
		Cache_obj.printStats();
  	}
};

int main(int argc, char **argv) {
	if (argc != 2 && argc != 4) {
		cout << "Usage: ./server <port-number> [<max-cache-size> <cache-policy-num>]" << endl << endl;
		cout << "Give the following parameters if cache needs to be enabled:" << endl;
		cout << " - max-cache-size: in bytes" << endl;
		cout << " - cache-policy-num: to be given as: " << endl;
		cout << "\t0 : RANDOM policy" << endl;
		cout << "\t1 : FIFO policy" << endl;
		cout << "\t2 : LRU-MIN policy" << endl;
		return 0;
  	}
	shared_ptr<TProtocolFactory> protocolFactory(new TBinaryProtocolFactory());
	shared_ptr<RPCproxyHandler> handler(new RPCproxyHandler());
	shared_ptr<TProcessor> processor(new RPCproxyProcessor(handler));
	shared_ptr<TServerTransport> serverTransport(new TServerSocket
								(atoi(argv[1])));
	shared_ptr<TTransportFactory> transportFactory(new TBufferedTransportFactory());

	TSimpleServer server(processor,
			serverTransport,
			transportFactory,
			protocolFactory);

	printf("Starting the server...\n");
	if (argc == 2) {
		no_cache = 1;
	} else {
		int size = atoi(argv[2]);
		int pol = atoi(argv[3]);

		Cache_obj.setMaxSize(size);
		Cache_obj.setCachePolicy(pol);
	}

	server.serve();
	printf("done.\n");
	return 0;
}
