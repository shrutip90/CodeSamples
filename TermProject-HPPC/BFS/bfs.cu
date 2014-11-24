#include "driver.h"
#include "bfs.h"
#include "cuda_utils.h"
#include "assert.h"
#include <sys/time.h>
#include "omp.h"

#define BLOCK_SIZE 1024
#define FTS 4
#define TILE_SIZE 8
#define NUM_BANKS 16
#define LOG_NUM_BANKS 4
#define CONFLICT_FREE_OFFSET(n) \
    ((n) >> NUM_BANKS + (n) >> (2 * LOG_NUM_BANKS))

//#define DEBUG
//#define CUDADEBUG

#ifdef DEBUG
#define dbgprint1(a1)                           fprintf(stderr,a1)
#define dbgprint2(a1,a2)                        fprintf(stderr,a1,a2)
#define dbgprint3(a1,a2,a3)                     fprintf(stderr,a1,a2,a3)
#define dbgprint4(a1,a2,a3,a4)                  fprintf(stderr,a1,a2,a3,a4)
#define dbgprint5(a1,a2,a3,a4,a5)               fprintf(stderr,a1,a2,a3,a4,a5)
#define dbgprint6(a1,a2,a3,a4,a5,a6)            fprintf(stderr,a1,a2,a3,a4,a5,a6)
#define dbgprint7(a1,a2,a3,a4,a5,a6,a7)         fprintf(stderr,a1,a2,a3,a4,a5,a6,a7)
#define dbgprint8(a1,a2,a3,a4,a5,a6,a7,a8)      fprintf(stderr,a1,a2,a3,a4,a5,a6,a7,a8)

#else
#define dbgprint1(a1)
#define dbgprint2(a1,a2)
#define dbgprint3(a1,a2,a3)
#define dbgprint4(a1,a2,a3,a4)
#define dbgprint5(a1,a2,a3,a4,a5)
#define dbgprint6(a1,a2,a3,a4,a5,a6)
#define dbgprint7(a1,a2,a3,a4,a5,a6,a7)
#define dbgprint8(a1,a2,a3,a4,a5,a6,a7,a8)
#endif

#ifdef CUDADEBUG
#define cudadbgprint1(a1)                           printf(a1)
#define cudadbgprint2(a1,a2)                        printf(a1,a2)
#define cudadbgprint3(a1,a2,a3)                     printf(a1,a2,a3)
#define cudadbgprint4(a1,a2,a3,a4)                  printf(a1,a2,a3,a4)
#define cudadbgprint5(a1,a2,a3,a4,a5)               printf(a1,a2,a3,a4,a5)
#define cudadbgprint10(a1,a2,a3,a4,a5,a6,a7,a8,a9,a10)               printf(a1,a2,a3,a4,a5,a6,a7,a8,a9,a10)

#else
#define cudadbgprint1(a1)
#define cudadbgprint2(a1,a2)
#define cudadbgprint3(a1,a2,a3)
#define cudadbgprint4(a1,a2,a3,a4)
#define cudadbgprint5(a1,a2,a3,a4,a5)
#define cudadbgprint10(a1,a2,a3,a4,a5,a6,a7,a8,a9,a10)
#endif

void
printArray (dtype *A, int N) { 
	int i;

	for (i = 0; i < N ; i++) {
		dbgprint2("%u ", A[i]);
	}
	dbgprint1("\n");
}

void
printIntArray (int *A, int N) {
	int i;

	for (i = 0; i < N ; i++) {
		dbgprint2("%d ", A[i]);
	}
        dbgprint1("\n");
}

/* Each thread checks one edge of the edgelist and sets the depth of second vertex if the the first
 * vertex has a valid depth.
 * 'done' variable is a flag to indicate the completion of the search.
 */
__global__
void
BFSNaiveKernel (dtype *E, int *D, int current_depth, unsigned int num_edges, bool *done, int nBlocks)
{
	int tidx = ((blockIdx.y) * blockDim.x) + (blockIdx.x * nBlocks * blockDim.x) + threadIdx.x;

	if (tidx < num_edges) {
		dtype vfirst = E[2 * tidx];
		dtype vsecond = E[2 * tidx + 1];
		int dfirst = D[vfirst];
		int dsecond = D[vsecond];

		if ((dfirst == current_depth) && (dsecond == -1)) {
			D[vsecond] = dfirst + 1;
			*done = false;
		}
	}
}

/* Naive implementation of BFS using Edgelist representation of the graph. */
void
BFSNaive (dtype* E, unsigned int N, unsigned int num_edges, int* D, int* h_D)
{
	unsigned int nBlocks;
	int current_depth = 0;
	bool h_true = true, h_done = false, *d_done = NULL;

	CUDA_CHECK_ERROR (cudaMalloc ((void**) &d_done, sizeof (bool)));

	nBlocks = (num_edges + BLOCK_SIZE - 1) / (BLOCK_SIZE);
	nBlocks = sqrt(nBlocks);
	dim3 block(BLOCK_SIZE,1);
	dim3 grid(nBlocks,nBlocks);
        while(!h_done) {
		CUDA_CHECK_ERROR (cudaMemcpy(d_done, &h_true, sizeof(bool), cudaMemcpyHostToDevice));
                BFSNaiveKernel <<<grid,block>>>(E, D, current_depth, num_edges, d_done,nBlocks);
		current_depth++;
		CUDA_CHECK_ERROR (cudaMemcpy(&h_done, d_done, sizeof(bool), cudaMemcpyDeviceToHost));
        }
	CUDA_CHECK_ERROR (cudaFree (d_done));
}

/* Naive implementation of BFS using Edgelist representation of the graph.
 *
 * This method iterates multiple times over the whole edgelist setting the depths of the
 * vertices at the current level, till all the vertices has a valid depth.
 */
void
cudaBFS_Edgelist (dtype* h_E, unsigned int N, unsigned int num_edges, int *h_D)
{
	dtype *d_E;
	int *d_D;
	
	struct timeval start, end;
	gettimeofday(&start, NULL);

	h_D[0] = 0;
	CUDA_CHECK_ERROR (cudaMalloc ((void**) &d_E, (num_edges * 2) * sizeof (dtype)));
	CUDA_CHECK_ERROR (cudaMemcpy (d_E, h_E, (num_edges * 2) * sizeof (dtype), cudaMemcpyHostToDevice));

	CUDA_CHECK_ERROR (cudaMalloc ((void**) &d_D, N * sizeof (int)));
	CUDA_CHECK_ERROR (cudaMemcpy (d_D, h_D, N * sizeof (int), cudaMemcpyHostToDevice));
	
	BFSNaive (d_E, N, num_edges, d_D, h_D);

	gettimeofday(&end, NULL);

	CUDA_CHECK_ERROR (cudaMemcpy (h_D, d_D, N * sizeof (int), cudaMemcpyDeviceToHost));

	fprintf (stderr, "Time to execute GPU BFS Edgelist code: %ld usecs\n", ((end.tv_sec * 1000000 + end.tv_usec)
										  - (start.tv_sec * 1000000 + start.tv_usec)));
	CUDA_CHECK_ERROR (cudaFree (d_E));
	CUDA_CHECK_ERROR (cudaFree (d_D));
}

/* Input: F (Present frontier), N (Frontier size), R, R_size
 * Output: AllocationSize (Offsets of starting neighbours in next frontier)
 * 
 * Load the number of neighbours of each vertex 'v' in F, using R[v+1]-R[v]
 * into shared memory and output the inclusive prefix sum into Allocation Size
 * sum contains the last element after the prefix sum - this is used to
 * propagate the sum to other blocks for completing the prefix sum
 *
 * Each thread loads two elements of the input to prefix sum
 * Shared memory is two times the frontier size since we try to load the input
 * into conflict free indices in the shared memory to avoid shared memory bank
 * conflicts
 */
__global__ void prefixscan (int *AllocationSize, const dtype *F, int N, int tbSize, dtype *R, int R_size, int *sum)
{
	__shared__ int temp[4 * BLOCK_SIZE];
	int bid = blockIdx.x;
	int tid = threadIdx.x;
	int nthreads = (N + 1) /2;
	int nblocks = (nthreads + tbSize - 1) / tbSize;
	int id = 2 * blockIdx.x * blockDim.x + threadIdx.x;
	int offset = 1;
	int n = 0;

	if (bid == nblocks - 1) {
		n = N - ((nblocks - 1) * 2 * tbSize);
	} else {
		n = 2 * tbSize;
	}
	__syncthreads();
	int ai_in = id;
	int bi_in = id + (n/2);
	int ai = tid;
	int bi = tid + (n/2);
	int bankOffsetA = CONFLICT_FREE_OFFSET(ai);
	int bankOffsetB = CONFLICT_FREE_OFFSET(bi);
	int d;
	
	if (bi < n) {
		__syncthreads();
		if (ai < (n/2)) {
			dtype idx = F[ai_in];
			temp[ai + bankOffsetA] = R[idx + 1] - R[idx];
		}
		dtype idx = F[bi_in];
		temp[bi + bankOffsetB] = R[idx + 1] - R[idx];

		for (d = n >> 1; d > 0; d >>= 1) { 
			__syncthreads();
  			if (tid < d) {
				int ai = offset*(2*tid+1)-1;
				int bi = offset*(2*tid+2)-1;
				ai += CONFLICT_FREE_OFFSET(ai);
				bi += CONFLICT_FREE_OFFSET(bi);
	
				if (bi < n) {
					temp[bi] += temp[ai];
				}
	                }
			offset *= 2;
		}
  
		for (d = 1; d <= (n/2); d *= 2) {
     			offset >>= 1;
			int i = n >> 1, j = d*2;
			while (j <= i) {
				i >>= 1;
			}
	   		 __syncthreads();
   			 if (tid < i) {
     				 int ai = offset*(2*tid+2)-1;
     				 int bi = offset*(2*tid+3)-1;
	     			 ai += CONFLICT_FREE_OFFSET(ai);
      				 bi += CONFLICT_FREE_OFFSET(bi); 
				 if (bi < n) {
	        			 temp[bi] += temp[ai];
			 	}
     			}
		}
		__syncthreads();
		if (ai < (n/2)) {
			AllocationSize[ai_in] = temp[ai + bankOffsetA];
		}
		if (bi < n) {
			AllocationSize[bi_in] = temp[bi + bankOffsetB];
		}
		if (tid == 0) {
                        sum[bid] = temp[n - 1 + CONFLICT_FREE_OFFSET(n-1)];
                }
	}
}

/* Helper function for prefixscan2
 * Prefix sum on the last elements of the blocks obtained using prefixscan
 * or maskPrefixscan
 */
__global__ void sumPrefix (int *sum, int n) {
	for (int i = 1; i < n; i++) {
		sum[i] += sum[i-1];
	}
}

/* Complete the prefix sum by adding 'sum' element corr to prev block to
 * all the elements of a block
 */
__global__ void prefixscan2 (int *g_odata, int N, int tbSize, int *sum)
{
	int bid = blockIdx.x;
	int id = 2 * (blockIdx.x * blockDim.x + threadIdx.x);
	int s = (bid == 0) ? 0 : sum[bid-1];

	if (id < N) {
		g_odata[id] += s;
		if (id + 1 < N)
			g_odata[id + 1] += s;
	}
}

/* Inclusive prefix scan
 * Input: g_idata (input vector), N (input size), tbSize (thread block size)
 * Output: g_odata (output vector after inclusive prefix sum)
 *
 * Each thread loads two elements of the input to prefix sum.
 * Shared memory is two times the frontier size since we try to load the input
 * into conflict free indices in the shared memory to avoid shared memory bank
 * conflicts. 'sum' contains the last element after the prefix sum - this is used to
 * propagate the sum to other blocks for completing the prefix sum.
 *
 * Similar to prefixscan but this is generic while prefixscan was specific to AllocationSize of the
 * frontier as it load the number of neighbors before performing the scan.
 */
__global__ void maskPrefixscan (int *g_odata, int *g_idata, int N, int tbSize, int *sum)
{
	__shared__ int temp[4 * BLOCK_SIZE];
        int tid = threadIdx.x;
        int bid = blockIdx.x;
        int nthreads = (N + 1) /2;
        int nblocks = (nthreads + tbSize - 1) / tbSize;
        int id = 2 * blockIdx.x * blockDim.x + threadIdx.x;
        int offset = 1;
        int n = 0;

        if (bid == nblocks - 1) {
                n = N - ((nblocks - 1) * 2 * tbSize);
        } else {
                n = 2 * tbSize;
        }

	int ai_in = id;
	int bi_in = id + (n/2);
	int ai = tid;
	int bi = tid + (n/2);
	int bankOffsetA = CONFLICT_FREE_OFFSET(ai);
	int bankOffsetB = CONFLICT_FREE_OFFSET(bi);
	int d;
	
	if (bi < n) {
		if (ai < (n/2)) {
        	        temp[ai + bankOffsetA] = g_idata[ai_in];
	        }
		temp[bi + bankOffsetB] = g_idata[bi_in];

		for (d = n >> 1; d > 32; d >>= 1) { 
			__syncthreads();
  			if (tid < d) {
				int ai = offset*(2*tid+1)-1;
				int bi = offset*(2*tid+2)-1;
				ai += CONFLICT_FREE_OFFSET(ai);
				bi += CONFLICT_FREE_OFFSET(bi);
	
				if (bi < n) {
					temp[bi] += temp[ai];
				}
                	}
			offset *= 2;
		}

  		if (tid < 32) {
			int ai = offset*(2*tid+1)-1; int bi = offset*(2*tid+2)-1;
			ai += CONFLICT_FREE_OFFSET(ai);	bi += CONFLICT_FREE_OFFSET(bi);
			if (bi < n) temp[bi] += temp[ai];
               	}
		offset *= 2;
  		if (tid < 16) {
			int ai = offset*(2*tid+1)-1; int bi = offset*(2*tid+2)-1;
			ai += CONFLICT_FREE_OFFSET(ai);	bi += CONFLICT_FREE_OFFSET(bi);
			if (bi < n) temp[bi] += temp[ai];
               	}
		offset *= 2;
  		if (tid < 8) {
			int ai = offset*(2*tid+1)-1; int bi = offset*(2*tid+2)-1;
			ai += CONFLICT_FREE_OFFSET(ai);	bi += CONFLICT_FREE_OFFSET(bi);
			if (bi < n) temp[bi] += temp[ai];
               	}
		offset *= 2;
  		if (tid < 4) {
			int ai = offset*(2*tid+1)-1; int bi = offset*(2*tid+2)-1;
			ai += CONFLICT_FREE_OFFSET(ai);	bi += CONFLICT_FREE_OFFSET(bi);
			if (bi < n) temp[bi] += temp[ai];
               	}
		offset *= 2;
  		if (tid < 2) {
			int ai = offset*(2*tid+1)-1; int bi = offset*(2*tid+2)-1;
			ai += CONFLICT_FREE_OFFSET(ai);	bi += CONFLICT_FREE_OFFSET(bi);
			if (bi < n) temp[bi] += temp[ai];
               	}
		offset *= 2;
  		if (tid < 1) {
			int ai = offset*(2*tid+1)-1; int bi = offset*(2*tid+2)-1;
			ai += CONFLICT_FREE_OFFSET(ai);	bi += CONFLICT_FREE_OFFSET(bi);
			if (bi < n) temp[bi] += temp[ai];
               	}
		offset *= 2;

		for (d = 1; d <= (n/2); d *= 2) {
     			offset >>= 1;
			int i = n >> 1, j = d*2;
			while (j <= i) {
				i >>= 1;
			}
	   		 __syncthreads();
   			 if (tid < i) {
     				 int ai = offset*(2*tid+2)-1;
     				 int bi = offset*(2*tid+3)-1;
	     			 ai += CONFLICT_FREE_OFFSET(ai);
      				 bi += CONFLICT_FREE_OFFSET(bi); 
				 if (bi < n) {
	        			 temp[bi] += temp[ai];
			 	}
	     		}
		}
		__syncthreads();

		if (ai < (n/2)) {
			g_odata[ai_in] = temp[ai + bankOffsetA];
		}
		if (bi < n) {
			g_odata[bi_in] = temp[bi + bankOffsetB];
		}
		if (tid == 0) {
                        sum[bid] = temp[n - 1 + CONFLICT_FREE_OFFSET(n-1)];
                }
	}
}

/* Input: F (Present Frontier), f_size (Frontier Size), AllocationSize (Offsets of starting neighbours in next frontier), R, C
 * Output: F_next (Next Frontier)
 * 
 * Serial Gathering of vertices for the next frontier
 * Each thread expands the neighbors of one vertex in F
 */
__global__ void getNextFrontier (const dtype* F, dtype* AllocationSize, dtype *R, dtype *C, dtype* F_next, int f_size) {
	int idx = blockIdx.x * blockDim.x + threadIdx.x;
        dtype offset = 0;

        if (idx < f_size) {
                if (idx > 0) {
                        offset = AllocationSize[idx - 1];
                }
                dtype vertex = F[idx];
                dtype c_start = R[vertex];
                dtype c_end = R[vertex + 1];

                for (dtype i = c_start; i < c_end; i++) {
                        F_next[offset] = C[i];
                        offset++;
                }
        }
}

/* NOT USED PRESENTLY
 * Fine grained version of getNextFrontier: Serial gathering expands one vertex per thread which
 * results in unbalanced amounts of work done by each thread in a block. To balance this among
 * all the threads of a block, each thread tries to process FTS number of output elements. So, only
 * some threads of the block expand the required number of vertices and load the column indices into
 * shared memory. Each thread then uses these column indices to expand FTS vertices.
 * Binary search is used to compute the start vertex for each block to expand.
 */
__global__ void getNextFrontier2 (const dtype* F, dtype* AllocationSize, dtype *R, dtype *C, dtype* F_next, int f_size, int fnext_size) {
        int idx = blockIdx.x * blockDim.x + threadIdx.x;
        int bid = blockIdx.x;
        int tid = threadIdx.x;
        __shared__ int Col[FTS * BLOCK_SIZE];
        dtype offset = 0;
        __shared__ int start;

        if (tid == 0) {
                int size = FTS * BLOCK_SIZE * bid;
                int low = 0, high = f_size - 1, mid;
                while (low < high) {
                        mid = (low + high) >> 1;
                        if (size <= AllocationSize[mid]) {
                                high = mid;
                        } else {
                                low = mid + 1;
                        }
                }
                start = (AllocationSize[high] == size) ? (high + 1) : high;
        }
        __syncthreads();
        if (start + tid < f_size) {
                offset = (start + tid > 0) ? AllocationSize[start + tid - 1] : 0;

                if (offset < FTS * BLOCK_SIZE * (bid + 1)) {
                        dtype vertex = F[start + tid];
                        int c_off = (tid == 0 && bid > 0) ? (FTS * BLOCK_SIZE * bid - offset) : 0;
                        dtype c_start = R[vertex] + c_off;
                        dtype c_end = R[vertex + 1];

                        for (int i = 0; (i < (c_end - c_start)) && (offset + i + c_off < FTS * BLOCK_SIZE * (bid + 1)); i++) {
                                Col[i + offset - FTS * BLOCK_SIZE * bid + c_off] = c_start + i;
                        }
                }
        }
        __syncthreads();

        for (int i = 0; i < FTS; i++) {
                if (idx * FTS + i < fnext_size) {
                        F_next[idx * FTS + i] = C[Col[tid * FTS + i]];
                }
        }
}

/* NOT USED PRESENTLY
 * Helper function for getNextFrontier3
 *
 * Compute the start indices in the next frontier that should be processed by each block in
 * getNextFrontier3
 */
__global__ void getStartIndices (dtype *AllocationSize, uint32_t f_size, uint32_t *sindices) {
        uint32_t count = 0;

        for (uint32_t i = 0; i < f_size; i++) {
                if (AllocationSize[i] > count * FTS * BLOCK_SIZE) {
                        sindices[count++] = i--;
                }
        }
}

/* NOT USED PRESENTLY
 * Another Fine grained version of getNextFrontier: Similar to getNextFrontier2, but uses precomputed start
 * indices for the blocks using getStartIndices instead of using the binary search.
 *
 * Each thread expands FTS elements of the next frontier.
 */
__global__ void getNextFrontier3 (const dtype* F, dtype* AllocationSize, dtype *R, dtype *C, dtype* F_next, uint32_t f_size, uint32_t fnext_size, uint32_t *sindices, uint32_t nBlocks) {
        uint32_t idx = blockIdx.x * blockDim.x + threadIdx.x;
        uint32_t bid = blockIdx.x;
        uint32_t tid = threadIdx.x;
        __shared__ uint16_t Col[FTS * BLOCK_SIZE];
        dtype offset = 0;
        uint32_t start = sindices[bid];

        if ((start + tid) < f_size) {
                offset = (start + tid > 0) ? AllocationSize[start + tid - 1] : 0;

                if (offset < FTS * BLOCK_SIZE * (bid + 1)) {
                        dtype vertex = F[start + tid];
                        uint32_t c_off = (tid == 0 && bid > 0) ? (FTS * BLOCK_SIZE * bid - offset) : 0;
                        dtype c_start = R[vertex] + c_off;
                        dtype c_end = R[vertex + 1];
                        for (uint32_t i = 0; (i < (c_end - c_start)) && (offset + i + c_off < FTS * BLOCK_SIZE * (bid + 1)); i++) {
                                Col[i + offset - FTS * BLOCK_SIZE * bid + c_off] = c_start + i;
                        }
                }
        }
        __syncthreads();

        for (uint32_t i = 0; i < FTS; i++) {
                if (idx * FTS + i < fnext_size) {
                        F_next[idx * FTS + i] = C[Col[tid * FTS + i]];
                }
        }
}

/* Input: F (Present Frontier), fsize (Frontier Size), D (depth of vertices)
 * Output: mask (flags indicating if the vertex is newly discovered)
 * 
 * Check the depth of vertices gathered and set the mask to 0 or 1
 * This implicitly eliminates the duplicates as the duplicates would correspond to same index in mask
 * Each thread checks one vertex in F
 */
__global__ void generateMask (dtype *F, int fsize, int *mask, int *D) {
	int idx = blockIdx.x * blockDim.x + threadIdx.x;

	if (idx < fsize) {
		dtype vertex = F[idx];
		mask[vertex] = (D[vertex] == -1) ? 1 : 0;
	}
}

/* Input: mask, mask_scan (Prefix sum of mask), mask_size
 * Output: F_compact (Compact frontier after filtering)
 *
 * Each thread processes one vertex or element of mask and sets the vertex number in F_compact
 * using the position obtained using mask_scan
 */
__global__ void compact (int *mask, int *mask_scan, dtype *F_compact, int mask_size) {
	int idx = blockIdx.x * blockDim.x + threadIdx.x;

	if (idx < mask_size) {
		if (mask[idx] == 1) {
			if (idx == 0) {
				F_compact[0] = idx;
			} else {
				int index = mask_scan[idx - 1];
				F_compact[index] = idx;
			}
		}
	}
}

/*
 * Input: mask (Flag indicating newly discovered vertices), N (number of nodes)
 * Output: F_compact (Compact frontier), fcompact_size (Size of compact frontier)
 *
 * Sequential filtering of the frontier, when the size of filter is below the threshold
 * Single thread examines the mask and sets vertex numbers in F_compact
 */
__global__ void compactSeq (int *mask, int N, dtype *F_compact, int *fcompact_size) {
	int current = 0;
	for (int idx = 0; idx < N; idx++) {
		if (mask[idx] == 1) {
			F_compact[current++] = idx;
		}
	}
	*fcompact_size = current;
}

/*
 * Input: mask (Flags indicating newly discovered vertices), D (depth vector), N (number of nodes),
 * 	  depth (current level of BFS)
 * Output: d_done (flag indicating last iteration of BFS)
 *
 * Each thread checks the mask and sets the depth of the corr. vertex
 * If any of the vertices has an invalid depth, then sets d_done to false to indicate that the
 * search should continue to the next level.
 */
__global__ void setDepth (int* mask, int *D, int N, int depth, bool *d_done) {
	int idx = blockIdx.x * blockDim.x + threadIdx.x;

	if (idx < N) {
		if (mask[idx] == 1) {
			D[idx] = depth;
		} else if (D[idx] == -1) {
			*d_done = false;
		}
	}
}

/* The optimized BFS version based on the reference papaer by Duane Merill
 *
 * This does a frontier based search by using the CSR representation of the graph.
 * The search proceeds using various steps for gathering the neighbors of the vertices in the
 * present frontier and processing them to obtain unique newly discovered vertices.
 */
void
BFSMerill (dtype* C, dtype* R, unsigned int N, unsigned int num_edges, int* D)
{
	unsigned int nBlocks, nThreads, tbSize;
	dtype *AllocationSize, *F_next, *F_compact;
	dtype initial_F = 0;
	int f_size = 1, *d_fcompact_size, fcompact_size = 1, fnext_size;
	int current_depth = 0, mask_threshold = 512;
	int *mask, *mask_scan, *sum;
	bool h_true = true, h_done = false, *d_done = NULL;

        CUDA_CHECK_ERROR (cudaMalloc ((void**) &d_done, sizeof (bool)));

	CUDA_CHECK_ERROR (cudaMalloc ((void**) &F_compact, sizeof (dtype)));
	CUDA_CHECK_ERROR (cudaMalloc ((void**) &d_fcompact_size, sizeof (int)));
	CUDA_CHECK_ERROR (cudaMemcpy (F_compact, &initial_F, sizeof (dtype), cudaMemcpyHostToDevice));

        while(!h_done) {
		CUDA_CHECK_ERROR (cudaMemcpy(d_done, &h_true, sizeof(bool), cudaMemcpyHostToDevice));
		current_depth++;

		/* Allocate memory required for vector of index offsets into next frontier */
		f_size = fcompact_size;
		CUDA_CHECK_ERROR (cudaMalloc ((void**) &AllocationSize, f_size * sizeof (dtype)));

		/* Get index offsets into the next frontier for writing neighbors */
		nThreads = (f_size + 1) / 2;
		tbSize = BLOCK_SIZE;
		nBlocks = (nThreads + tbSize - 1) / tbSize;
		CUDA_CHECK_ERROR (cudaMalloc ((void**) &sum, nBlocks * sizeof (int)));
                prefixscan <<<nBlocks, tbSize>>>((int *)AllocationSize, F_compact, f_size, tbSize, R, N, sum);

		sumPrefix <<<1,1>>> (sum, nBlocks);
		prefixscan2 <<<nBlocks, tbSize>>> ((int *)AllocationSize, f_size, tbSize, sum);
		CUDA_CHECK_ERROR (cudaFree(sum));

		CUDA_CHECK_ERROR (cudaMemcpy(&fnext_size, &AllocationSize[f_size-1], sizeof(int), cudaMemcpyDeviceToHost));

		/* Allocate array for next frontier */
		CUDA_CHECK_ERROR (cudaMalloc ((void**) &F_next, fnext_size * sizeof (dtype)));

		/* Gather the neighbors to form next frontier */
		nThreads = f_size;
                tbSize = BLOCK_SIZE;
                nBlocks = (nThreads + tbSize - 1) / tbSize;
		getNextFrontier <<<nBlocks, tbSize>>> (F_compact, AllocationSize, R, C, F_next, f_size);
		CUDA_CHECK_ERROR (cudaFree (AllocationSize));
		CUDA_CHECK_ERROR (cudaFree ((void*)F_compact));

		/* Mask to indicate newly discivered neighbors in the gathered frontier */
		CUDA_CHECK_ERROR (cudaMalloc ((void**) &mask, N * sizeof (int)));
		CUDA_CHECK_ERROR (cudaMemset(mask, 0, N * sizeof (int)));
			
		nThreads = fnext_size;
        	tbSize = BLOCK_SIZE;
                nBlocks = (nThreads + tbSize - 1) / tbSize;
		generateMask<<<nBlocks, tbSize>>> (F_next, fnext_size, mask, D);
		CUDA_CHECK_ERROR (cudaFree (F_next));
		
		/* Set the depth of the new vertices at the current level of processing */
		nThreads = N;
		tbSize = BLOCK_SIZE;
		nBlocks = (nThreads + tbSize - 1) / tbSize;
		setDepth <<<nBlocks, tbSize>>> (mask, D, N, current_depth, d_done);
		
		if (fnext_size < mask_threshold) {
			/* Gather the compact frontier sequentially */
			CUDA_CHECK_ERROR (cudaMalloc ((void**) &F_compact, fnext_size * sizeof (dtype)));

                        compactSeq <<<1, 1>>> (mask, N, F_compact, d_fcompact_size);
			CUDA_CHECK_ERROR (cudaMemcpy(&fcompact_size, d_fcompact_size, sizeof(int), cudaMemcpyDeviceToHost));
			if (!fcompact_size) {
				CUDA_CHECK_ERROR (cudaFree (F_compact));
			}
		} else {
			CUDA_CHECK_ERROR (cudaMalloc ((void**) &mask_scan, N * sizeof (int)));
			
			/* Prefix scan on mask to get the offsets into the compact frontier for next level*/
			nThreads = (N + 1)/2;
	                tbSize = BLOCK_SIZE;
        	        nBlocks = (nThreads + tbSize - 1) / tbSize;
			CUDA_CHECK_ERROR (cudaMalloc ((void**) &sum, nBlocks * sizeof (int)));
			maskPrefixscan <<<nBlocks, tbSize>>> (mask_scan, mask, N, tbSize, sum);

			sumPrefix <<<1,1>>> (sum, nBlocks);
	                prefixscan2 <<<1, tbSize>>> (mask_scan, N, tbSize, sum);
			CUDA_CHECK_ERROR (cudaFree (sum));
			CUDA_CHECK_ERROR (cudaMemcpy(&fcompact_size, &mask_scan[N-1], sizeof(int), cudaMemcpyDeviceToHost));

			if (fcompact_size) {
				CUDA_CHECK_ERROR (cudaMalloc ((void**) &F_compact, fcompact_size * sizeof (dtype)));

				/* Kernel to compact the frontier after filtering */
				nThreads = N;
        	        	tbSize = BLOCK_SIZE;
                		nBlocks = (nThreads + tbSize - 1) / tbSize;
				compact <<<nBlocks, tbSize>>> (mask, mask_scan, F_compact, N);
			}
			CUDA_CHECK_ERROR (cudaFree (mask));
			CUDA_CHECK_ERROR (cudaFree (mask_scan));
		}
		CUDA_CHECK_ERROR (cudaMemcpy(&h_done, d_done, sizeof(bool), cudaMemcpyDeviceToHost));
        }
	CUDA_CHECK_ERROR (cudaFree (d_fcompact_size));
	CUDA_CHECK_ERROR (cudaFree (d_done));
}

/* Host function for calling the optimized BFS version based on the reference paper by Duane Merill.*/
void
cudaBFS_Merill (dtype* h_C, dtype* h_R, unsigned int N, unsigned int num_edges, int* h_D)
{
	dtype *d_C, *d_R;
	int *d_D;
	
	struct timeval start, end;
	gettimeofday(&start, NULL);

	h_D[0] = 0;
	CUDA_CHECK_ERROR (cudaMalloc ((void**) &d_C, num_edges * sizeof (dtype)));
	CUDA_CHECK_ERROR (cudaMemcpy (d_C, h_C, num_edges * sizeof (dtype), cudaMemcpyHostToDevice));

	CUDA_CHECK_ERROR (cudaMalloc ((void**) &d_R, (N + 1) * sizeof (dtype)));
	CUDA_CHECK_ERROR (cudaMemcpy (d_R, h_R, (N + 1) * sizeof (dtype), cudaMemcpyHostToDevice));

	CUDA_CHECK_ERROR (cudaMalloc ((void**) &d_D, N * sizeof (int)));
	CUDA_CHECK_ERROR (cudaMemcpy (d_D, h_D, N * sizeof (int), cudaMemcpyHostToDevice));
	
	BFSMerill (d_C, d_R, N, num_edges, d_D);

	gettimeofday(&end, NULL);

	CUDA_CHECK_ERROR (cudaMemcpy (h_D, d_D, N * sizeof (int), cudaMemcpyDeviceToHost));

	fprintf (stderr, "Time to execute GPU BFS Merill code: %ld usecs\n", ((end.tv_sec * 1000000 + end.tv_usec)
										  - (start.tv_sec * 1000000 + start.tv_usec)));
	CUDA_CHECK_ERROR (cudaFree (d_C));
	CUDA_CHECK_ERROR (cudaFree (d_R));
	CUDA_CHECK_ERROR (cudaFree (d_D));
}
