#ifdef __cplusplus
extern "C" {
#endif

void printArray (dtype *A, int N);
void printIntArray (int *A, int N);
void initCudaArray (dtype **d_A, dtype *h_A, unsigned int N);
void cudaBFS_Edgelist (dtype* h_E, unsigned int N, unsigned int num_edges, int *h_D);
void cudaBFS_Merill (dtype* h_C, dtype* h_R, unsigned int N, unsigned int num_edges, int* h_D);

#ifdef __cplusplus
}
#endif

