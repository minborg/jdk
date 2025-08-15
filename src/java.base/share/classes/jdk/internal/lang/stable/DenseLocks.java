package jdk.internal.lang.stable;

import jdk.internal.misc.Unsafe;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A class holding a plurality of non-reentrant blocking locks. The objective is to
 * keep data as dense as possible.
 *
 * @param flags for holding lock bits
 * @param locks for holding additional transient State for indices during computation
 */
record DenseLocks(byte[] flags, ConcurrentHashMap<Integer, State> locks) {

    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    record State(long threadId, Lock lock) {}

    // Only two bits are used per component in the `flags` array. The transition from:
    //   AVAILABLE -> ACQUIRED   sets bit 0
    //   ACQUIRED  -> AVAILABLE  clears bit 0  Used to back out from setting contents (e.g., Exception)
    //   ACQUIRED  -> TOMB_STONE sets bit 1    The contents has been set
    private static final byte AVAILABLE =  0x00;
    private static final byte ACQUIRED =   AVAILABLE | 0b0000_0001;  // 0x01
    private static final byte TOMB_STONE = ACQUIRED  | 0b0000_0010;  // 0x03

    // Todo: Consider using a sharded linked list instead of a CHM
    DenseLocks(int size) {
        this(new byte[size], new ConcurrentHashMap<>(4));
    }


    // Returns `true` to the winning thread, eventually `false` to everyone else.
    boolean lock(int index) {
        final long currentTid = Thread.currentThread().threadId();
        final byte witness = caeFlags(index, AVAILABLE, ACQUIRED);
        if (witness == AVAILABLE) {
            // We got the lock!
            // Todo: Find a less expensive construct than ReentrantLock
            final Lock underlyingLock = new ReentrantLock();
            // Lock the lock before we announce it in `locks`
            underlyingLock.lock();
            // Announce the particulars of this locking event
            locks.put(index, new State(currentTid, underlyingLock));
            return true;
        } else {
            // The lock was already acquired, either by another thread or by the current thread
            State state;
            // Wait for states to be visible
            while ((state = locks.get(index)) == null && flags(index) != TOMB_STONE) {
                // Either the lock is not visible yet or someone raced before us
                Thread.onSpinWait();
            }
            // Recheck the flag as the state might be available even though there is
            // a `TOMB_STONE`.
            if (flags(index) == TOMB_STONE) {
                // The lock was already unlocked so no need to wait
                return false;
            }
            assert state != null; // This should never happen as a `TOMB_STONE` is set
                                  // before the `state` is removed in `unlock()`
            if (state.threadId() == currentTid) {
                // Disallow reentrant invocation
                throw new IllegalStateException("Recursive initialization of a stable value is illegal. Index: " + index);
            }
            // Suspend the current thread until the underlying lock is unlocked
            final Lock lock = state.lock();
            lock.lock();
            lock.unlock();
            return false;
        }
    }

    void rollBack(int index) {
        unlock0(index, AVAILABLE);
    }

    void unlock(int index) {
        unlock0(index, TOMB_STONE);
    }

    void unlock0(int index, byte newFlags) {
        final long threadId = Thread.currentThread().threadId();
        assert locks.get(index).threadId() == threadId; // Make sure we own the lock
        // Announce that operations for this index are completed
        // This has to be done before the state is removed
        flags(index, newFlags);
        // The state is not needed anymore
        State state = locks.remove(index);
        // Release the lock
        state.lock().unlock();
    }

    // Support methods for Unsafe access

    private byte caeFlags(int index, byte expected, byte value) {
        return UNSAFE.compareAndExchangeByte(flags, offsetFor(index), expected, value);
    }

    private long flags(int index) {
        return UNSAFE.getByteVolatile(flags, offsetFor(index));
    }

    private void flags(int index, byte value) {
        UNSAFE.putByteVolatile(flags, offsetFor(index), value);
    }

    private static long offsetFor(long index) {
        return Unsafe.ARRAY_BYTE_BASE_OFFSET + Unsafe.ARRAY_BYTE_INDEX_SCALE * index;
    }

}
