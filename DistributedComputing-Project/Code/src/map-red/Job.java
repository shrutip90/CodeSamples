import java.io.*;
import java.util.*;
import java.lang.reflect.Method;

public class Job {
	public PastryMapper mapper;
	public PastryReducer reducer;
	public MapRedStore store;
	int jobId;

	public Job() {
		this.mapper = null;
		this.reducer = null;
		this.store = new MapRedStore();
		this.jobId = 0;
	}
	public Job (int jobId) {
		this.mapper = null;
		this.reducer = null;
		this.store = new MapRedStore();
		this.jobId = jobId;
	}
}
