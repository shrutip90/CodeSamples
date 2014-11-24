CC = gcc 
NVCC = nvcc
CUDA_PATH = /opt/cuda-4.2/cuda
CFLAGS = -L$(CUDA_PATH)/lib64 -lcudart -fopenmp -Igomp
NVCCFLAGS= -arch=compute_20 -code=sm_20 -I$(CUDA_SDK_PATH)/C/common/inc -g -G -Xcompiler -fopenmp
COPTFLAGS = -O3 -g
LDFLAGS =


bfs_CUSRCS = bfs.cu 
bfs_CSRCS = driver.c queue.c
bfs_CUOBJS = $(bfs_CUSRCS:.cu=.o__cu)
bfs_COBJS = $(bfs_CSRCS:.c=.o__c)

bfs: $(bfs_CUOBJS) $(bfs_COBJS)
	$(CC) $(CFLAGS) $^ -o $@ 


%.o__c: %.c
	$(CC) -o $@ -c $<

%.o__cu: %.cu
	$(NVCC) $(NVCCFLAGS) -o $@ -c $< -DBS=512 -Xptxas -v

clean:
	rm -f core *.o__cu *.o__c *~ bfs

# eof
