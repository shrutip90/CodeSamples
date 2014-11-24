namespace cpp proxy

service RPCproxy {

	void ping(),
	string getURL(1:string url),
	void getCacheStats()

}
