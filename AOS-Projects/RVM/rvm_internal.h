#ifndef __LIBRVM_INTERNAL_H_
#define __LIBRVM_INTERNAL_H_

#include <map>
#include <string>

#define SEG_NAME_LEN 80

/* For restoring contents of a segment */
typedef struct undo_log {
	void *data;
} undo_log_t;

typedef struct range {
	int offset;
	int size;
} range_t;

typedef struct seg_hdr {
	char *name;
	int size;
	bool mapped;
	char *rname;
	char *fname;	/* File name with the directory path */

	undo_log_t *ulog;
	int num_ranges;
	range_t **ranges;
} __attribute__((packed, aligned(4))) seg_hdr_t;

typedef struct seg {
	seg_hdr_t hdr;
	void *data;	/* User will be given a pointer to this in-memory data.
			 * So, always keep this the last in seg data structure,
			 * so that any overflow by the user will result in a segfault.
			 */
} __attribute__((packed, aligned(4))) seg_t;

struct trans;
typedef int trans_t;
typedef struct trans* transaction;

struct rvm;
typedef struct rvm* rvm_t;

typedef struct trans {
	trans_t id;
	rvm_t rvm;
	int numsegs;
	seg_t **segs;
} trans_s;

typedef struct redo_log {
	trans_t *trns;
} redo_log_t;

typedef struct rvm {
	int rid;
	char *dir;
	char *seg_file_name;
	std::map <std::string, seg_t*>* seg_map;
	std::map <void*, seg_t*>* seg_data_map;
} rvm_s;

extern std::map <trans_t, transaction> trans_map;
extern std::map <std::string, rvm_t> dir_map;
#endif
