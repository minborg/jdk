package jdk.internal.lang.stable;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.Stable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

record DenseLocks(@Stable long[] owners, Supplier<ConcurrentHashMap<Long, Lock>> locks) {

    static final Unsafe UNSAFE = Unsafe.getUnsafe();
    static final Supplier<ConcurrentHashMap<Long, Lock>> CHM_SUPPLIER = new Supplier<ConcurrentHashMap<Long, Lock>>() {
        @Override  public ConcurrentHashMap<Long, Lock> get() { return new ConcurrentHashMap<>(); }
    };

    private static final long AVAILABLE = 0;
    private static final long TOMB_STONE = -1;

    DenseLocks(int size) {
        this(new long[size], Supplier.ofLazy(CHM_SUPPLIER));
    }

    // Non-reentrant blocking lock.
    // Returns `true` to the winner, eventually `false` to everyone else
    boolean lock(int index) {
        final long threadId = Thread.currentThread().threadId();
        final long witness = UNSAFE.compareAndExchangeLong(owners, offsetFor(index), AVAILABLE, threadId);
        if (witness == 0) {
            // Todo: Find a less expensive construct than ReentrantLock
            // We got the lock
            final Lock underlyingLock = new ReentrantLock();
            locks.get().put(threadId, underlyingLock);
            underlyingLock.lock();
            return true;
        } else {
            if (witness == threadId) {
                throw new IllegalStateException("Recursive initialization of a stable value is illegal. Index: " + index);
            }

            Lock underlyingLock;
            long lockId = 0;
            while ((underlyingLock = locks.get().get(threadId)) == null && (lockId = threadIdVolatile(index)) != TOMB_STONE) {
                // Either the lock is not visible yet or someone raced before us
                Thread.onSpinWait();
            }
            if (lockId == TOMB_STONE) {
                // The lock is already unlocked so no need to wait
                return false;
            }
            // Suspend the current thread until the underlying lock is unlocked
            underlyingLock.lock();
            underlyingLock.unlock();
            return false;
        }
    }

    void unlock(int index) {
        final long threadId = Thread.currentThread().threadId();
        assert threadIdVolatile(index) == Thread.currentThread().threadId();
        UNSAFE.putLongVolatile(owners, offsetFor(index), TOMB_STONE);
        // The lock is not needed anymore
        locks.get().remove(threadId);
    }

    private long threadIdVolatile(int index) {
        final long offset = offsetFor(index);
        return UNSAFE.getLongVolatile(owners, offset);
    }

    private static long offsetFor(long index) {
        return Unsafe.ARRAY_LONG_BASE_OFFSET + Unsafe.ARRAY_LONG_INDEX_SCALE * index;
    }

}
