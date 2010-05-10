/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
#include <stdlib.h>
#include <unistd.h>
#include <assert.h>
#include <alloca.h>

#include "isa.h"
#include "log.h"
#include "jni.h"
#include "threadLocals.h"
#include "teleProcess.h"
#include "teleNativeThread.h"

extern void gather_and_trace_threads(void);

static int trace = 0;         // set to non-zero to trace thread resumption/blocking
static int terminated = 0;    // target domain has terminated
static struct db_thread *threads_at_rest = NULL;  // cache of threads on return from resume
static int num_threads_at_rest;
static volatile int suspend_all_request = 0;

struct db_regs *checked_get_regs(char *f, int threadId) {
    struct db_regs *db_regs;
    db_regs = get_regs(threadId);
    if (db_regs == NULL) {
        log_println("guestvmXenNativeThread_%s: cannot get registers for thread %d", f, threadId);
        gather_and_trace_threads();
    }
    return db_regs;
}

JNIEXPORT jboolean JNICALL
Java_com_sun_max_tele_debug_guestvm_xen_GuestVMXenDBNativeChannel_nativeSuspendAll(JNIEnv *env, jclass c) {
	suspend_all_request = 1;
	return true;
}

JNIEXPORT jlong JNICALL
Java_com_sun_max_tele_debug_guestvm_xen_GuestVMXenDBNativeChannel_nativeSuspend(JNIEnv *env, jclass c, jint threadId) {
    suspend(threadId);
    return 1;
}

JNIEXPORT jboolean JNICALL
Java_com_sun_max_tele_debug_guestvm_xen_GuestVMXenDBNativeChannel_nativeSingleStep(JNIEnv *env, jclass c, jint threadId) {
    int rc = single_step(threadId);
    return rc == 0;
}


JNIEXPORT jint JNICALL
Java_com_sun_max_tele_debug_guestvm_xen_GuestVMXenDBNativeChannel_nativeSetInstructionPointer(JNIEnv *env, jclass c, jint threadId, jlong ip) {
    return set_ip(threadId, ip);
}

JNIEXPORT jboolean JNICALL
Java_com_sun_max_tele_debug_guestvm_xen_GuestVMXenDBNativeChannel_nativeReadRegisters(JNIEnv *env, jclass c, jlong threadId,
		jbyteArray integerRegisters, jint integerRegistersLength,
		jbyteArray floatingPointRegisters, jint floatingPointRegistersLength,
		jbyteArray stateRegisters, jint stateRegistersLength) {

    isa_CanonicalIntegerRegistersStruct canonicalIntegerRegisters;
    isa_CanonicalStateRegistersStruct canonicalStateRegisters;
    isa_CanonicalFloatingPointRegistersStruct canonicalFloatingPointRegisters;
    struct db_regs *db_regs;

    if (integerRegistersLength > sizeof(canonicalIntegerRegisters)) {
        log_println("buffer for integer register data is too large");
        return false;
    }

    if (stateRegistersLength > sizeof(canonicalStateRegisters)) {
        log_println("buffer for state register data is too large");
        return false;
    }

    if (floatingPointRegistersLength > sizeof(canonicalFloatingPointRegisters)) {
        log_println("buffer for floating point register data is too large");
        return false;
    }

    db_regs = checked_get_regs("nativeReadRegisters", threadId);
    if (db_regs == NULL) {
    	return false;
    }

	isa_canonicalizeTeleIntegerRegisters(db_regs, &canonicalIntegerRegisters);
	isa_canonicalizeTeleStateRegisters(db_regs, &canonicalStateRegisters);
	isa_canonicalizeTeleFloatingPointRegisters(db_regs, &canonicalFloatingPointRegisters);

    (*env)->SetByteArrayRegion(env, integerRegisters, 0, integerRegistersLength, (void *) &canonicalIntegerRegisters);
    (*env)->SetByteArrayRegion(env, stateRegisters, 0, stateRegistersLength, (void *) &canonicalStateRegisters);
    (*env)->SetByteArrayRegion(env, floatingPointRegisters, 0, floatingPointRegistersLength, (void *) &canonicalFloatingPointRegisters);
    return true;
}

JNIEXPORT jint JNICALL
Java_com_sun_max_tele_debug_guestvm_xen_GuestVMXenDBNativeChannel_nativeReadByte(JNIEnv *env, jclass c, jlong address) {
    unsigned long long_val;
    unsigned long aligned_address = (address & (~7));
    int bit_offset = 8 * (address & 7);

    assert(sizeof(unsigned long) == 8);
    long_val = read_u64(aligned_address);

    return  (long_val >> bit_offset) & 0xFF;
}

JNIEXPORT jboolean JNICALL
Java_com_sun_max_tele_debug_guestvm_xen_GuestVMXenDBNativeChannel_nativeWriteByte(JNIEnv *env, jclass c, jlong address, jbyte jvalue) {
    unsigned long long_val;
    unsigned long aligned_address = (address & (~7));
    int bit_offset = 8 * (address & 7);
    unsigned long value = ((jvalue & 0xFFUL) << bit_offset);
    unsigned long mask =  (0xFFUL << bit_offset);


    assert(sizeof(unsigned long) == 8);
    /* read old 64bit value */
    long_val = read_u64(aligned_address);
    log_println(" >> Read %lx at %lx, bit offset = %d, value=%lx, mask=%lx",
            long_val, aligned_address, bit_offset, value, mask);
    /* clear the byte we are interested in */
    long_val &= ~mask;
    /* or it with the new value */
    long_val |= value;

    log_println(" >> Writing %lx at %lx, request for %x at %lx",
            long_val, aligned_address, value, address);
    write_u64(aligned_address, long_val);

    return 1;
}

JNIEXPORT jlong JNICALL
Java_com_sun_max_tele_debug_guestvm_xen_GuestVMXenDBNativeChannel_nativeReadInt(JNIEnv *env, jclass c, jlong address) {
    unsigned long long_val;
    unsigned long aligned_address = (address & (~7));
    unsigned long mask = (1UL << ((sizeof(unsigned int)) * 8)) - 1;
    int bit_offset = 8 * (address & 7);
    unsigned int value;

    assert(sizeof(unsigned long) == 8);
    long_val = read_u64(aligned_address);
    value = ((long_val >> bit_offset) & mask);
//    log_println(" >> Request to read from %lx, read from %lx, value %lx, returning %x\n", address, aligned_address, long_val, value);

    return (jlong) value;
}

JNIEXPORT jint JNICALL
Java_com_sun_max_tele_debug_guestvm_xen_GuestVMXenDBNativeChannel_nativeReadShort(JNIEnv *env, jclass c, jlong address) {
    unsigned long long_val;
    unsigned long aligned_address = (address & (~7));
    unsigned long mask = (1UL << ((sizeof(unsigned short)) * 8)) - 1;
    int bit_offset = 8 * (address & 7);
    unsigned short value;

    assert(sizeof(unsigned long) == 8);
    long_val = read_u64(aligned_address);
    value = ((long_val >> bit_offset) & mask);
//    log_println(" >> Request to read short from %lx, read from %lx, value %lx, returning %x\n", address, aligned_address, long_val, value);

    return (jint) value;
}

JNIEXPORT jboolean JNICALL
Java_com_sun_max_tele_debug_guestvm_xen_GuestVMXenDBNativeChannel_nativeAttach(JNIEnv *env, jclass c, jint domainId) {
    log_println("Calling do_attach on domId=%d", domainId);
    return db_attach(domainId);
}

JNIEXPORT jboolean JNICALL
Java_com_sun_max_tele_debug_guestvm_xen_GuestVMXenDBNativeChannel_nativeDetach(JNIEnv *env, jclass c) {
    return db_detach();
}

static ThreadState_t toThreadState(int state) {
    if (state & AUX1_FLAG) {
        return TS_MONITOR_WAIT;
    }
    if (state & AUX2_FLAG) {
        return TS_NOTIFY_WAIT;
    }
    if (state & JOIN_FLAG) {
        return TS_JOIN_WAIT;
    }
    if (state & SLEEP_FLAG) {
        return TS_SLEEPING;
    }
    if (state & WATCH_FLAG) {
    	return TS_WATCHPOINT;
    }
    // default
    return TS_SUSPENDED;
}

JNIEXPORT jboolean JNICALL
Java_com_sun_max_tele_debug_guestvm_xen_GuestVMXenDBNativeChannel_nativeGatherThreads(JNIEnv *env, jclass c, jobject teleDomain, jobject threadSeq, jlong threadLocalsList, jlong primordialThreadLocals) {
    struct db_thread *threads;
    int num_threads;
    threads = gather_threads(&num_threads);
    int i;
    for (i=0; i<num_threads; i++) {
        ThreadLocals threadLocals = (ThreadLocals) alloca(threadLocalsAreaSize());
        NativeThreadLocalsStruct nativeThreadLocalsStruct;
        struct db_regs *db_regs = checked_get_regs("nativeGatherThreads", threads[i].id);
        ProcessHandle ph = NULL;
        threadLocals = teleProcess_findThreadLocals(ph, threadLocalsList, primordialThreadLocals, db_regs->rsp, threadLocals, &nativeThreadLocalsStruct);
        teleProcess_jniGatherThread(env, teleDomain, threadSeq, threads[i].id, toThreadState(threads[i].flags), db_regs->rip, threadLocals);
    }
    free(threads);

    return 0;
}

int is_state(int state, int flag) {
	return state & flag ? 1 : 0;
}

int is_th_state(struct db_thread *thread, int flag) {
	return is_state(thread->flags, flag);
}

void trace_thread(struct db_thread *thread) {
    int state = thread->flags;
    if (trace) {
        log_println("thread %d, ra %d, r %d, dying %d, rds %d, ds %d, mw %d, nw %d, jw %d, sl %d, wp %d",
            thread->id, is_state(state, RUNNABLE_FLAG), is_state(state, RUNNING_FLAG),
            is_state(state, DYING_FLAG), is_state(state, REQ_DEBUG_SUSPEND_FLAG),
            is_state(state, DEBUG_SUSPEND_FLAG), is_state(state, AUX1_FLAG),
            is_state(state, AUX2_FLAG), is_state(state, JOIN_FLAG), is_state(state, SLEEP_FLAG), is_state(state, WATCH_FLAG));
    }
}

void trace_threads(struct db_thread *threads, int num_threads) {
    int i;
    for(i=0; i<num_threads; i++) {
        trace_thread(&threads[i]);
    }
}

void gather_and_trace_threads(void) {
    struct db_thread *threads;
    int num_threads;
    if (terminated) return;
    threads = gather_threads(&num_threads);
    trace_threads(threads, num_threads);
    free(threads);
}

JNIEXPORT jboolean JNICALL
Java_com_sun_max_tele_debug_guestvm_xen_GuestVMXenDBNativeChannel_nativeResume(JNIEnv *env, jobject domain) {
    unsigned long sleep_time = 0;
    struct db_thread *threads;
    int num_threads, i;

    if (trace) log_println("resuming all runnable threads");
    if (threads_at_rest != NULL) free(threads_at_rest);
    resume_all();
    /* Poll waiting for the thread to block or we get a suspendAll request, sleep for a short while to give domain chance to do something */
    usleep(500);
    while(suspend_all_request == 0) {
        if (trace) log_println("waiting for a thread to block");
        threads = gather_threads(&num_threads);
        if (threads == NULL) {
            // target domain has explicitly terminated
            // send signoff
            db_signoff();
            terminated = 1;
            return 1;
        }
        trace_threads(threads, num_threads);

        for (i=0; i<num_threads; i++) {
            if (is_th_state(&threads[i], DEBUG_SUSPEND_FLAG)) {
            	suspend_all_request = 1;
                break;
            }
        }
        free(threads);
        if (suspend_all_request == 0) {
            sleep_time += 2000;  // usecs
            usleep(sleep_time);
        }
    }

// At this point at least one thread is debug_suspend'ed or we
// got a suspendAll request. Now suspend any other runnable threads.
// N.B. This is not an atomic operation and threads
// may become runnable, e.g., if a sleep expires
// or a driver thread is woken by an interrupt.
// However, those threads will debug_suspend themselves in that case.

    suspend_all_request = 0;
    if (trace) log_println("suspending all threads");
    suspend_all();
    threads = gather_threads(&num_threads);
    trace_threads(threads, num_threads);
    threads_at_rest = threads;
    num_threads_at_rest = num_threads;
    return 0;
}

JNIEXPORT jlong JNICALL
Java_com_sun_max_tele_debug_guestvm_xen_GuestVMXenDBNativeChannel_nativeGetBootHeapStart(JNIEnv *env, jclass c) {
    return app_specific1(0);
}

JNIEXPORT jint JNICALL
Java_com_sun_max_tele_debug_guestvm_xen_GuestVMXenDBNativeChannel_nativeSetTransportDebugLevel(JNIEnv *env, jclass c, jint level) {
    return db_debug(level);
}

JNIEXPORT jint JNICALL
Java_com_sun_max_tele_debug_guestvm_xen_GuestVMXenDBNativeChannel_nativeReadBytes(JNIEnv *env, jclass c, jlong src, jobject dst, jboolean isDirectByteBuffer, jint dstOffset, jint length) {
    return teleProcess_read(NULL, env, c, src, dst, isDirectByteBuffer, dstOffset, length);
}

JNIEXPORT jint JNICALL
Java_com_sun_max_tele_debug_guestvm_xen_GuestVMXenDBNativeChannel_nativeWriteBytes(JNIEnv *env, jclass c, jlong dst, jobject src, jboolean isDirectByteBuffer, jint srcOffset, jint length) {
    return teleProcess_write(NULL, env, c, dst, src, isDirectByteBuffer, srcOffset, length);
}

JNIEXPORT jint JNICALL
Java_com_sun_max_tele_debug_guestvm_xen_GuestVMXenDBNativeChannel_nativeMaxByteBufferSize(JNIEnv *env, jclass c) {
	return multibytebuffersize();
}

JNIEXPORT jboolean JNICALL
Java_com_sun_max_tele_debug_guestvm_xen_GuestVMXenDBNativeChannel_nativeActivateWatchpoint(JNIEnv *env, jclass c, jlong address, jlong size, jboolean after, jboolean read, jboolean write, jboolean exec) {
    int kind = 0;
    if (after) kind |= AFTER_W;
    if (read) kind |= READ_W;
    if (write) kind |= WRITE_W;
    if (exec) kind |= EXEC_W;
	if (!after) return false;
    return activate_watchpoint(address, size, kind);
}

JNIEXPORT jboolean JNICALL
Java_com_sun_max_tele_debug_guestvm_xen_GuestVMXenDBNativeChannel_nativeDeactivateWatchpoint(JNIEnv *env, jclass c, jlong address, jlong size) {
	return deactivate_watchpoint(address, size);
}

static int get_wp_thread() {
    int i;
    for (i = 0; i < num_threads_at_rest; i++) {
    	if (is_th_state(&threads_at_rest[i], WATCH_FLAG)) {
    		return threads_at_rest[i].id;
    	}
    }
    return -1;
}

JNIEXPORT jlong JNICALL
Java_com_sun_max_tele_debug_guestvm_xen_GuestVMXenDBNativeChannel_nativeReadWatchpointAddress(JNIEnv *env, jclass c) {
	int thread_id = get_wp_thread();
	int kind;
	if (thread_id < 0) {
		log_println("readWatchpointAddress: no thread at watchpoint");
		return 0;
	}
	return watchpoint_info(thread_id, &kind);
}

JNIEXPORT jint JNICALL
Java_com_sun_max_tele_debug_guestvm_xen_GuestVMXenDBNativeChannel_nativeReadWatchpointAccessCode(JNIEnv *env, jclass c) {
	int thread_id = get_wp_thread();
	int kind;
	if (thread_id < 0) {
		log_println("readWatchpointAccessCode: no thread at watchpoint");
		return 0;
	}
	watchpoint_info(thread_id, &kind);
	return kind & ~AFTER_W;

}

void teleProcess_initialize(void)
{
    log_println("teleProcess_initialize for guestvmXen");
}
