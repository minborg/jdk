/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.internal.lang.stable;

import jdk.internal.misc.Unsafe;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static jdk.internal.lang.stable.DenseLocks.BackoffStrategy.*;

/**
 * A class holding a plurality of non-reentrant blocking locks. The objective is to keep
 * data as dense as possible.
 *
 */
final class DenseLocks {

    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private static final int BITS_PER_FLAG = 2; // Must be a power of two
    private static final int FLAGS_PER_BYTE = Byte.SIZE / BITS_PER_FLAG;
    private static final int BIT_MASK = ~-(BITS_PER_FLAG << 1); // 0b0000_0011
    private static final int ACQUIRE_BIT = 0;
    private static final int TOMB_STONE_BIT = 1;

    private static final byte AVAILABLE = 0;
    private static final byte ACQUIRED_MASK = 1 << ACQUIRE_BIT;
    private static final byte TOMB_STONE_MASK = 1 << TOMB_STONE_BIT;

    private final byte[] flags;
    private Map<Integer, Long> threadIds;
    private final AtomicInteger counter;

    DenseLocks(int size) {
        this.flags = new byte[(size + FLAGS_PER_BYTE - 1) / FLAGS_PER_BYTE];
        threadIds = new ConcurrentHashMap<>();
        this.counter = new AtomicInteger(size);
    }

    // Only two bits are used per component in the `flags` array. The valid transitions are:
    //   AVAILABLE -> ACQUIRED   sets bit 0
    //   ACQUIRED  -> AVAILABLE  clears bit 0  Used to back out from setting contents (e.g., Exception)
    //   ACQUIRED  -> TOMB_STONE sets bit 1    The contents has been set. The lock is spent
    //
    // Hence the allowed states are:
    //                            The lock is available and up for grabs.
    //   ACQUIRED                 The lock is actively acquired but not spent.
    //   ACQUIRED | TOMB_STONE    The lock was previously acquired but is now spent.


    // Returns `true` to the winning thread, eventually `false` to everyone else.
    boolean lock(int index) {
        boolean l = lock0(index);
        if (l) {
            threadIds.put(index, Thread.currentThread().threadId());
            if (counter.decrementAndGet() == 0) {
                // We do not need the map anymore
                threadIds = null;
            }
        }
        return l;
    }

    boolean lock0(int index) {
        byte witness = caeFlag(index, ACQUIRE_BIT);
        if (witness == AVAILABLE) {
            // We got the lock!
            // System.out.println("Got the lock!");
            return true;
        }
        if ((witness & TOMB_STONE_MASK) != 0) {
            //System.out.println("Spent!");
            // The lock will never be available anymore so, we give up
            return false;
        }
        // We need to wait for an update to the flags by the holding thread
        final BackoffStrategy backoffStrategy =
                busyWaiting(1_000);
/*        final BackoffStrategy backoffStrategy =
                busyWaiting(1_000)
                        .andThen(yielding(10_000))
                        .andThen(progressivelySleeping(TimeUnit.MICROSECONDS.toNanos(1)));*/
        // Wait for state to change while trying to acquire the lock
        for (; ; witness = caeFlag(index, ACQUIRE_BIT)) {
            //System.out.println("witness = " + witness);
            //System.out.flush();
            if (witness == AVAILABLE) {
                return true;
            }
            if ((witness & TOMB_STONE_MASK) != 0) {
                return false;
            }
            // Keep on trying ...
            if (!backoffStrategy.backoff()) {
                throw new RuntimeException("Que?");
            }
        }
    }

    void preventLockReentry(int index) {
        if (flag(index, ACQUIRED_MASK)) {
            if (threadIds.get(index) == Thread.currentThread().threadId()) {
                // Disallow reentrant invocation
                throw new IllegalStateException("Recursive initialization of a stable value is illegal. Index: " + index);
            }
        }
    }

    void rollBack(int index) {
        unlock0(index, true);
    }

    void unlock(int index) {
        unlock0(index, false);
        threadIds.remove(index);
    }

    void unlock0(int index, boolean clear) {
        if (clear) {
            clearFlags(index);
        } else {
            caeFlag(index, TOMB_STONE_BIT);
        }
    }

    // Support methods for Unsafe access

    private byte caeFlag(int index, int bit) {
        final byte b = UNSAFE.getAndBitwiseOrByte(flags, offsetFor(index), maskFor(index, bit));
        return maskOutFlags(index, b);
    }

    private byte flags(int index) {
        final byte b = UNSAFE.getByteVolatile(flags, offsetFor(index));
        return maskOutFlags(index, b);
    }

    private boolean flag(int index, int mask) {
        final byte b = UNSAFE.getByteVolatile(flags, offsetFor(index));
        return (maskOutFlags(index, b) & mask) == mask;
    }

    private void clearFlags(int index) {
        System.out.println("index = " + index);
        final long offset = offsetFor(index);
        System.out.println("offset = " + offset);
        final int shifts = shiftsFor(index);
        System.out.println("shifts = " + shifts);
        // Clear the flags without touching flags for other indices
        UNSAFE.getAndBitwiseAndByte(flags, offset, (byte) (BIT_MASK << shifts));

        final byte b = UNSAFE.getByteVolatile(flags, offsetFor(index));
        System.out.println("b = " + b);
        System.out.println("flags(index) = " + flags(index));
    }

    private static long offsetFor(int index) {
        return Unsafe.ARRAY_BYTE_BASE_OFFSET + (long) Unsafe.ARRAY_BYTE_INDEX_SCALE * index / FLAGS_PER_BYTE;
    }

    private static byte maskFor(int index, int bit) {
        assert bit >= 0 && bit < BITS_PER_FLAG : bit;
        return (byte) (1 << (shiftsFor(index) + bit));
    }

    private static int shiftsFor(int index) {
        return (index % Byte.SIZE) * BITS_PER_FLAG;
    }

    private static byte maskOutFlags(int index, byte b) {
        return (byte) ((b >>> shiftsFor(index)) & BIT_MASK);
    }

    public byte[] flags() {
        return flags;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (DenseLocks) obj;
        return Objects.equals(this.flags, that.flags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flags);
    }

    @Override
    public String toString() {
        return "DenseLocks[" +
                "flags=" + flags + ']';
    }


    // Todo: Replace this with something more efficient once the strategy has
    //       been determined in detail.
    @FunctionalInterface
    interface BackoffStrategy {

        /**
         * {@return waits and then returns {@code true} if more waiting is required,
         * otherwise returns {@code false}}
         */
        boolean backoff();

        default BackoffStrategy andThen(BackoffStrategy next) {
            return new BackoffStrategy() {
                @Override
                public boolean backoff() {
                    return BackoffStrategy.this.backoff() || next.backoff();
                }
            };
        }

        static BackoffStrategy busyWaiting(int iterations) {
            return new OfBusyWait(iterations);
        }

        static BackoffStrategy yielding(int iterations) {
            return new OfYield(iterations);
        }

        static BackoffStrategy progressivelySleeping(long initialSleepNs) {
            return new OfProgressiveSleep(initialSleepNs);
        }

        final class OfBusyWait implements BackoffStrategy {
            int iteration;

            OfBusyWait(int iterations) {
                this.iteration = iterations;
            }

            @Override
            public boolean backoff() {
                if (iteration-- != 0) {
                    Thread.onSpinWait();
                    return true;
                } else {
                    return false;
                }
            }
        }

        final class OfYield implements BackoffStrategy {
            int iteration;

            OfYield(int iteration) {
                this.iteration = iteration;
            }

            @Override
            public boolean backoff() {
                if (iteration-- != 0) {
                    Thread.yield();
                    return true;
                } else {
                    return false;
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
            public boolean backoff() {
                LockSupport.parkNanos(sleepTimeNs);
                sleepTimeNs = Math.min(MAX_SLEEP_TIME_NS, sleepTimeNs << 1);
                // Repeat ad infinitum
                return true;
            }
        }

    }

}
