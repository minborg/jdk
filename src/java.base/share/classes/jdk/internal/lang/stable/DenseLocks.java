package jdk.internal.lang.stable;

import jdk.internal.misc.Unsafe;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
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

    private static final int BITS_PER_FLAG = 2; // Must be a power of two
    private static final int FLAGS_PER_BYTE = Byte.SIZE / BITS_PER_FLAG;
    private static final int BIT_MASK = ~-(BITS_PER_FLAG << 1); // 0b0000_0011
    private static final int ACQUIRE_BIT = 0;
    private static final int TOMB_STONE_BIT = 1;

    private static final byte AVAILABLE = 0;
    private static final byte ACQUIRED = 1 << ACQUIRE_BIT;
    private static final byte TOMB_STONE = 1 << TOMB_STONE_BIT;

    // Only two bits are used per component in the `flags` array. The transition from:
    //   AVAILABLE -> ACQUIRED   sets bit 0
    //   ACQUIRED  -> AVAILABLE  clears bit 0  Used to back out from setting contents (e.g., Exception)
    //   ACQUIRED  -> TOMB_STONE sets bit 1    The contents has been set
/*    private static final byte AVAILABLE =  0x00;
    private static final byte ACQUIRED =   AVAILABLE | 0b0000_0001;  // 0x01
    private static final byte TOMB_STONE = ACQUIRED  | 0b0000_0010;  // 0x03*/

    // Todo: Consider using a sharded linked list instead of a CHM
    DenseLocks(int size) {
        this(new byte[(size + FLAGS_PER_BYTE - 1) / FLAGS_PER_BYTE], new ConcurrentHashMap<>(4));
    }

    // Returns `true` to the winning thread, eventually `false` to everyone else.
    boolean lock(int index) {
        final long currentTid = Thread.currentThread().threadId();
        final byte witness = caeFlag(index, ACQUIRE_BIT);
        if (witness == ACQUIRED) {
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
            while ((state = locks.get(index)) == null && flag(index, TOMB_STONE)) {
                // Either the lock is not visible yet or someone raced before us
                Thread.onSpinWait();
            }
            // Recheck the flag as the state might be available even though there is
            // a `TOMB_STONE`.
            if ((flag(index, TOMB_STONE))) {
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
        unlock0(index, true);
    }

    void unlock(int index) {
        unlock0(index, false);
    }

    void unlock0(int index, boolean clear) {
        final long threadId = Thread.currentThread().threadId();
        assert locks.get(index).threadId() == threadId; // Make sure we own the lock
        // This has to be done before the state is removed
        if (clear) {
            clearFlags(index);
        } else {
            caeFlag(index, TOMB_STONE_BIT);
        }
        // The state is not needed anymore
        State state = locks.remove(index);
        // Release the lock
        state.lock().unlock();
    }

    // Support methods for Unsafe access

    private byte caeFlag(int index, int bit) {
        final byte b = UNSAFE.getAndBitwiseOrByte(flags, offsetFor(index), maskFor(index, bit));
        return maskOutFlags(index, b);
    }

/*    private long flags(int index) {
        final byte b = UNSAFE.getByteVolatile(flags, offsetFor(index));
        return maskOutFlags(index, b);
    }*/

    private boolean flag(int index, int mask) {
        final byte b = UNSAFE.getByteVolatile(flags, offsetFor(index));
        return (maskOutFlags(index, b) & mask) == mask;
    }

    private void clearFlags(int index) {
        final long offset = offsetFor(index);
        final int shifts = shiftsFor(index);
        // Clear the flags without touching flags for other indices
        UNSAFE.getAndBitwiseAndByte(flags, offset, (byte) (BIT_MASK << shifts));
    }

    private static long offsetFor(int index) {
        return Unsafe.ARRAY_BYTE_BASE_OFFSET + (long) Unsafe.ARRAY_BYTE_INDEX_SCALE * index / FLAGS_PER_BYTE;
    }

    private static byte maskFor(int index, int bit) {
        assert bit > 0 && bit < BITS_PER_FLAG;
        return (byte) (1 << (shiftsFor(index) + bit));
    }

    private static int shiftsFor(int index) {
        return (index % Byte.SIZE) * BITS_PER_FLAG;
    }

    private static byte maskOutFlags(int index, byte b) {
        return (byte) ((b >>> shiftsFor(index)) & BIT_MASK);
    }

    @FunctionalInterface
    interface BackoffStrategy {

        void backoff();

        default BackoffStrategy andThen(BackoffStrategy next) {
            return new BackoffStrategy() {
                @Override
                public void backoff() {
                    backoff();
                    next.backoff();
                }
            };
        }

        static BackoffStrategy ofBusy(int iterations) {
            return new OfBusy(iterations);
        }

        static BackoffStrategy ofYield(int iterations) {
            return new OfYield(iterations);
        }

        static BackoffStrategy ofProgressiveSleep(long initialSleepNs) {
            return new OfProgressiveSleep(initialSleepNs);
        }

        final class OfBusy implements BackoffStrategy {
            int iteration;

            OfBusy(int iterations) {
                this.iteration = iterations;
            }

            @Override
            public void backoff() {
                if (iteration-- != 0)
                    Thread.onSpinWait();
            }
        }

        final class OfYield implements BackoffStrategy {
            int iteration;

            OfYield(int iteration) {
                this.iteration = iteration;
            }

            @Override
            public void backoff() {
                if (iteration-- != 0) {
                    Thread.yield();
                }
            }
        }

        // Truncated exponential backoff
        final class OfProgressiveSleep implements BackoffStrategy {
            private static final long MAX_SLEEP_TIME_NS = TimeUnit.MILLISECONDS.toNanos(7);
            long sleepTimeNs;

            OfProgressiveSleep(long sleepTimeNs) {
                this.sleepTimeNs = sleepTimeNs;
            }

            @Override
            public void backoff() {
                LockSupport.parkNanos(sleepTimeNs);
                sleepTimeNs = Math.min(MAX_SLEEP_TIME_NS, sleepTimeNs << 1);
            }
        }

    }

}
