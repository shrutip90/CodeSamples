#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/mman.h>
#include <fcntl.h>

#include <assert.h>
#include <unistd.h>
#include <errno.h>
#include <map>
#include <iostream>
#include <string>

#include "rvm.h"

#ifdef NO_ASSERT
#define ASSERT(x, y)

#else
#define ASSERT(x, y)			assert (x && y)
#endif

#ifdef DEBUG
#define dbgprint1(a1)			printf(a1)
#define dbgprint2(a1,a2)		printf(a1,a2)
#define dbgprint3(a1,a2,a3)		printf(a1,a2,a3)
#define dbgprint4(a1,a2,a3,a4)		printf(a1,a2,a3,a4)
#define dbgprint5(a1,a2,a3,a4,a5)	printf(a1,a2,a3,a4,a5)
#define dbgprint6(a1,a2,a3,a4,a5,a6)	printf(a1,a2,a3,a4,a5,a6)
#else

#define dbgprint1(a1)
#define dbgprint2(a1,a2)
#define dbgprint3(a1,a2,a3)
#define dbgprint4(a1,a2,a3,a4)
#define dbgprint5(a1,a2,a3,a4,a5)
#define dbgprint6(a1,a2,a3,a4,a5,a6)

#endif

void rvm_truncate_seg(char* seg_fname, char *seg_rname);
using namespace std;
std::map <std::string, rvm_t> dir_map;
std::map <trans_t, transaction> trans_map;

static int trans_counter = 0;

#ifdef DEBUG
void print_dir_map () {
	std::map <std::string, rvm_t>::iterator it;

	dbgprint1 ("Printing in-mem rvms.........\n");
	for (it = dir_map.begin(); it != dir_map.end(); it++) {
		cout << it->first;
		dbgprint2 ("%p\n", it->second);
	}
}
#else
void print_dir_map () {
}
#endif

rvm_t rvm_init(const char *directory) {
	string dname (directory);
	rvm_t r = (rvm_t) malloc (sizeof (rvm_s));
	ASSERT(r, "rvm_init: Malloc error\n");
	int res = mkdir (directory, 0777);
	if (res) {
		if (errno == EEXIST) {
			dbgprint1 ("rvm_init: Directory already exists\n");
			print_dir_map ();

			if (dir_map[dname]) {
				dbgprint1 ("rvm_init: Returning in-mem dir\n");
				return dir_map[dname];
			}
		} else {
			ASSERT (0, "rvm_init: Error creating directory\n");
		}
	}

	r->dir = (char *) malloc (sizeof (char) * (strlen (directory) + 1));
	ASSERT(r->dir, "rvm_init: Malloc error\n");

	strncpy (r->dir, directory, sizeof (char) * (strlen (directory) + 1));
	r->seg_file_name = (char *) malloc (sizeof (char) * 
						(strlen (directory) + 20));
	ASSERT(r->seg_file_name, "rvm_init: Malloc error\n");

	strncpy (r->seg_file_name, directory, sizeof (char) * strlen (directory));
	strncat (r->seg_file_name, "/.segmentsSA", sizeof (char) * 11);

	r->seg_map = new map<string, seg_t *>();
	r->seg_data_map = new map<void*, seg_t *>();

	dir_map[dname] = r;
	print_dir_map ();

	/*r->num_trans = 0;
	r->trans = NULL;*/
	return r;
}

#ifdef DEBUG
void print_rvm_segs (rvm_t rvm) {
	std::map <std::string, seg_t*>::iterator it;
	seg_t *seg;

	dbgprint1 ("Printing in-mem segs.........\n");
	for (it = (*(rvm->seg_map)).begin(); it != (*(rvm->seg_map)).end(); it++) {
		cout << it->first << endl;
		dbgprint2 ("%p\n", it->second);
	}
}
#else
void print_rvm_segs (rvm_t rvm) {
}
#endif

void *rvm_map(rvm_t rvm, const char *segname, int size_to_create) {
	seg_t *seg;

	/* Check if segment already exists */
	FILE *df = fopen (rvm->seg_file_name, "a+");
	char *name = (char *) malloc (sizeof (char) * SEG_NAME_LEN);
	ASSERT(name, "rvm_map: Malloc error\n");

	bool seg_exists = false;
	dbgprint1 ("Scanning directory..................\n");
	while (fscanf (df, "%s\n", name) != EOF) {
		dbgprint2 ("Seg %s\n", name);
		if (!strcmp (name, segname)) {
			dbgprint2 ("Found a match with Seg %s\n", name);
			print_rvm_segs(rvm);

			string sname(segname);
			seg = (*(rvm->seg_map))[sname];
			if (seg == NULL) {
				dbgprint1 ("Seg exits\n");
				seg_exists = true;
				break;
			}
			ASSERT(!(seg->hdr.mapped), "Cannot map previously mapped segment\n");
			if (seg->hdr.size >= size_to_create) {
				seg->hdr.mapped= true;
				fclose (df);
				return seg->data;
			} else {
				seg->data = realloc (seg->data, size_to_create);
				seg->hdr.size = size_to_create;
				seg->hdr.mapped= true;
				fclose (df);
				return seg->data;
			}
		}
	}

	/* Create a new segment */
	dbgprint1 ("Creating new segment\n");
	seg = (seg_t *) malloc (sizeof (seg_t));
	ASSERT(seg, "rvm_map: Malloc error\n");

	seg->hdr.name = (char *) malloc (sizeof (char) * (strlen(segname) + 1));
	ASSERT(seg->hdr.name, "rvm_map: Malloc error\n");
	strncpy (seg->hdr.name, segname, sizeof (char) * (strlen(segname) + 1));
	
	seg->data = malloc (size_to_create);
	ASSERT(seg->data, "rvm_map: Malloc error\n");

	(*(rvm->seg_data_map))[seg->data] = seg;
	seg->hdr.size = size_to_create;
	seg->hdr.mapped = true;

	string sname(segname);
	(*(rvm->seg_map))[sname] = seg;
	print_rvm_segs(rvm);

	seg->hdr.fname = (char *) malloc (sizeof (char) * 
					(strlen(rvm->dir) + strlen(segname) + 2));
	ASSERT(seg->hdr.fname, "rvm_map: Malloc error\n");
	strncpy (seg->hdr.fname, rvm->dir, sizeof (char) * strlen(rvm->dir));
	strncat (seg->hdr.fname, "/", sizeof (char));
	strncat (seg->hdr.fname, seg->hdr.name, sizeof (char) * 
						(strlen(seg->hdr.name) + 1));

	seg->hdr.rname = (char *) malloc (sizeof (char) * 
					(strlen(rvm->dir) + strlen(segname) + 20));
	strncat (seg->hdr.rname, seg->hdr.fname, sizeof (char) * 
						strlen(seg->hdr.fname));
	strncat (seg->hdr.rname, "_redo", sizeof (char) * 6);

	seg->hdr.num_ranges = 0;
	seg->hdr.ranges = NULL;
	seg->hdr.ulog = NULL;

	struct stat buf;
	if (seg_exists && !stat(seg->hdr.rname, &buf)) {
		rvm_truncate_log (rvm);
	}
	FILE *sf = fopen (seg->hdr.fname, "a+");
	char c; int off = 0;

	char *segdata = (char *)seg->data;
	if (seg_exists) {
		while ((c = fgetc (sf)) != EOF) {
			segdata[off++] = c;
		}
	}
	fclose(sf);

	if (!seg_exists) {
		fprintf (df, "%s\n", segname);
	}
	dbgprint4 ("segname = %s, seg_fname = %s, seg_rname = %s\n", segname, seg->hdr.fname, seg->hdr.rname);
	fflush (df);
	fclose (df);
	return seg->data;
}

void rvm_unmap(rvm_t rvm, void *segbase) {
	seg_t *seg;
	seg = (*(rvm->seg_data_map))[segbase];
	ASSERT(seg, " NULL segment\n");
	ASSERT(seg->hdr.mapped, "Cannot unmap unmapped segment\n");
	rvm_truncate_seg(seg->hdr.fname, seg->hdr.rname);
	seg->hdr.mapped = false;	
}

void rvm_destroy(rvm_t rvm, const char *segname) {
	string sname (segname);

	seg_t *seg;
	if (rvm->seg_map->find(sname) == rvm->seg_map->end()) {
		return;
	} else {
		seg = (*(rvm->seg_map))[sname];
	}
	ASSERT(seg->hdr.mapped, "Cannot destroy mapped segment\n");
	/* Delete the segment file the directory */
	/* FIXME: Remove segment name in the directoy */
	remove(seg->hdr.fname);

	rvm->seg_map->erase(sname);
	rvm->seg_data_map->erase(seg->data);
	free(seg->hdr.fname);
	free(seg->hdr.name);
	free(seg->hdr.rname);
	free(seg->hdr.fname);
	free(seg->data);
	if(seg->hdr.ulog)
		free(seg->hdr.ulog);
	for ( int i = 0; i < seg->hdr.num_ranges; i++)
	{
		free(seg->hdr.ranges[i]);
	}
	if(seg->hdr.ranges)
		free(seg->hdr.ranges);
	free(seg);
}

trans_t rvm_begin_trans(rvm_t rvm, int numsegs, void **segbases) {
	transaction t;
	std::map <trans_t, transaction>::iterator it;
	undo_log_t *ulog;
	int i, j;
	seg_t* seg;

	for (it = trans_map.begin(); it != trans_map.end(); it++) {
		t = it->second;
		ASSERT(t, "NULL transaction\n");
		for (i = 0; i < numsegs; i++) {
			seg = (*(rvm->seg_data_map))[segbases[i]];
		
			for (j = 0; j < t->numsegs; j++) {
				if (seg == t->segs[j]) {
					dbgprint1 ("returning -1\n");
					return -1;
				}
			}
		}
	}

	t = (transaction) malloc (sizeof (trans_s));

	t->id = trans_counter++;
	t->segs = (seg_t **) malloc (sizeof (seg_t *) * numsegs);

	for (i = 0; i < numsegs; i++) {
		seg = (*(rvm->seg_data_map))[segbases[i]];
		ASSERT(seg->hdr.mapped, "Cannot begin transaction on unmapped segment\n");
		seg->hdr.ulog = (undo_log_t *) malloc (sizeof (undo_log_t));
		ulog = seg->hdr.ulog;
		ulog->data = malloc (seg->hdr.size);
		memcpy (ulog->data, segbases[i], seg->hdr.size);
		t->segs[i] = seg;
	}
	t->numsegs = numsegs;

	t->rvm = rvm;
	trans_map[t->id] = t;
	return t->id;
}
void rvm_about_to_modify(trans_t t, void *segbase, int offset, int size) {
	dbgprint2 ("rvm_about_to_modify: Searching for trans %d\n", t);
	transaction tid = trans_map[t];
	ASSERT(tid, "rvm_about_to_modify: NULL transaction\n");

	int i;
	rvm_t rvm = tid->rvm;
	seg_t *seg = (*(rvm->seg_data_map))[segbase];
	ASSERT(seg->hdr.mapped, "rvm_about_to_modify: Cannot modify unmapped segment\n");

	for (i = 0; i < tid->numsegs; i++) {
		if (seg == tid->segs[i]) {
			break;
		}
	}
	if (i == tid->numsegs) {
		ASSERT (0, "rvm_about_to_modify: Cannot modify segments that are not specified \
									in rvm_begin_trans\n");
	}
	ASSERT ((offset + size) <= seg->hdr.size, "rvm_about_to_modify: range out of bounds of \
									the segment\n");
	range_t *rng = (range_t *) malloc (sizeof (range_t));
	rng->offset = offset;
	rng->size = size;

	dbgprint3 ("rvm_about_to_modify: offset = %d, size = %d\n", offset, size);

	seg->hdr.num_ranges++;
	seg->hdr.ranges = (range_t**) realloc (seg->hdr.ranges, sizeof (range_t *) *
							seg->hdr.num_ranges);
	seg->hdr.ranges [seg->hdr.num_ranges - 1] = rng;
}
void rvm_commit_trans(trans_t t) {
	transaction tid = trans_map[t];
	int i, j, off, size;
	int rf;
	seg_t *seg;
	char *segdata;
	char c = '\n';
	dbgprint2 ("t->numsegs = %d\n", tid->numsegs);
	for (i = 0; i < tid->numsegs; i++) {
		seg = tid->segs[i];
		ASSERT(seg->hdr.mapped, "Cannot commit unmapped segment\n");

		if ((rf = open (seg->hdr.rname, O_CREAT | O_WRONLY, S_IRWXU)) < 0) {
			dbgprint1 ("rvm_commit_trans: can't open redo log file\n");
		}
	
		segdata = (char *) seg->data;
		for (j = 0; j < seg->hdr.num_ranges; j++) {
			off = seg->hdr.ranges[j]->offset;
			size = seg->hdr.ranges[j]->size;

			dbgprint4 ("rvm_commit_trans: offset = %d, size = %d, data = %s\n", off, size, &segdata[off]);
			write (rf, &off, sizeof (off));
			write (rf, &c, sizeof (c));
			write (rf, &size, sizeof (size));
			write (rf, &c, sizeof (c));
			write (rf, &segdata[off], sizeof (char) * size);
		} 
		fsync (rf);
		close (rf);
		free(seg->hdr.ulog->data);
		free(seg->hdr.ulog);
		seg->hdr.ulog = NULL;	
	}
	tid->numsegs = 0;	
	free(tid->segs);
	free(tid);
}
void rvm_abort_trans(trans_t t) {
	transaction tid = trans_map[t];
	int i, j;
	seg_t *seg;
	undo_log_t *ulog;

	for (i = 0; i < tid->numsegs; i++) {
		seg = tid->segs[i];
		ASSERT(seg->hdr.mapped, "Cannot abort unmapped segment\n");
		ulog = seg->hdr.ulog;
		memcpy (seg->data, ulog->data, seg->hdr.size);
		free(ulog->data);
		
		free(ulog);

		seg->hdr.ulog = NULL;
		for (j = 0; j < seg->hdr.num_ranges; j++) {
			free(seg->hdr.ranges[j]);
		}
		free(seg->hdr.ranges);
		seg->hdr.ranges = NULL;

		seg->hdr.num_ranges = 0;
	}
	tid->numsegs = 0;
	free(tid->segs);
	free(tid);
}
void rvm_truncate_seg(char *seg_fname, char *seg_rname)
{
	int rf, sf, off, size;
	char *buf = NULL;
	char c;
	off_t initial, final,record_size;
	struct stat statbuf;
	
	if ((rf = open (seg_rname, O_RDWR, S_IRWXU)) < 0) {
		return;
	}
	if ((sf = open (seg_fname, O_RDWR, S_IRWXU)) < 0) {
		ASSERT (0, "rvm_truncate_log: can't create seg file for writing\n");
	}

	while (1) {
		initial = lseek (rf, 0, SEEK_SET);
			if (read (rf, &off, sizeof (int)) == 0) {
			break;
		}
		read (rf, &c, sizeof (char));
		read (rf, &size, sizeof (int));
		read (rf, &c, sizeof (char));
			buf = (char *) malloc (sizeof (char) * size);
		int num = read (rf, buf, sizeof (char) * size);
		dbgprint5 ("rvm_truncate_log: offset = %d, size = %d, readbuf %s, num = %d\n", 
										off, size, buf, num);

		off_t ret;
		if ((ret = lseek (sf, off, SEEK_SET)) == -1) {
			ASSERT (0, "rvm_truncate_log: lseek error %d\n");
		}
		write (sf, buf, sizeof (char) * size);
		free (buf);

		/* find size of redo log file */
		if (fstat (rf ,&statbuf) < 0) {
			ASSERT (0, "rvm_truncate_log: fstat error\n");
		}
		if ((final = lseek (rf, 0, SEEK_CUR)) == -1) {
			ASSERT (0, "rvm_truncate_log: lseek error %d\n");
		}
		record_size = final - initial;

		buf = (char *) malloc (sizeof (char) * statbuf.st_size - record_size);
		read (rf, buf, statbuf.st_size - record_size);
		
		if (lseek (rf, 0, SEEK_SET) == -1) {
			ASSERT (0, "rvm_truncate_log: lseek error %d\n");
		}
		write (rf, buf, statbuf.st_size - record_size);
		free (buf);
		ftruncate (rf, statbuf.st_size - record_size);
	}
	remove(seg_rname);
	fsync (sf);
	fsync (rf);
	close (sf);
	close (sf);
}
void rvm_truncate_log(rvm_t rvm) {
	FILE *df = fopen (rvm->seg_file_name, "r");
	if (df == NULL) {
		dbgprint1 ("no segnames file\n");
		return;
	}
	char *name = (char *) malloc (sizeof (char) * SEG_NAME_LEN);
	ASSERT(name, "rvm_truncate_log: Malloc error\n");
	char *seg_fname = NULL, *seg_rname = NULL;

	dbgprint1 ("Truncate reading directory..................\n");
	while (fscanf (df, "%s\n", name) != EOF) {
		dbgprint2 ("Seg %s\n", name);
		seg_fname = (char *) malloc (sizeof (char) * 
				(strlen(rvm->dir) + strlen(name) + 2));
		ASSERT(seg_fname, "rvm_truncate_log: Malloc error\n");

		strncpy (seg_fname, rvm->dir, sizeof (char) * strlen (rvm->dir));
		strncat (seg_fname, "/", sizeof (char));
		strncat (seg_fname, name, sizeof (char) * (strlen(name) + 1));

		seg_rname = (char *) malloc (sizeof (char) * (strlen(rvm->dir) + strlen(name) + 20));
		strncat (seg_rname, seg_fname, sizeof (char) * strlen(seg_fname));
		strncat (seg_rname, "_redo", sizeof (char) * 6);
		rvm_truncate_seg(seg_fname, seg_rname);
	}

}
