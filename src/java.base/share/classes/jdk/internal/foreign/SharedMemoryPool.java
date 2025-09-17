package jdk.internal.foreign;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryPool;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.ref.Reference;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntBinaryOperator;

// Todo: explore an underlying shared arena

public record SharedMemoryPool(long byteSize,
                               long byteAlignment,
                               Arena underlying,
                               Runnable closeAction,
                               Stack<SlicingAllocator> stack) implements MemoryPool {

    @Override
    public Arena get() {
        SlicingAllocator allocator = stack.pop();
        if (allocator == null) {
            allocator = new SlicingAllocator(underlying.allocate(byteSize, byteAlignment));
        } else {
            allocator.resetTo(0);
        }
        final Arena arena = Arena.ofConfined();
        ((MemorySessionImpl) arena.scope()).addCloseAction(closeAction);
        return new PooledArena(arena, allocator, stack);
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public String toString() {
        return "SharedMemoryPool[byteSize=" + byteSize + ", byteAlignment=" + byteAlignment + "]";
    }


    private record PooledArena(Arena arena,
                               SlicingAllocator allocator,
                               Stack<SlicingAllocator> stack)
            implements Arena, SegmentAllocator.NonZeroable {

        @ForceInline
        @Override
        public MemorySegment allocate(long byteSize, long byteAlignment) {
            return allocate0(byteSize, byteAlignment, true);
        }

        @ForceInline
        @Override
        public MemorySegment.Scope scope() {
            return arena.scope();
        }

        @ForceInline
        @Override
        public void close() {
            arena.close();
            stack.push(allocator);
        }

        @ForceInline
        @Override
        public MemorySegment allocateNonZeroing(long byteSize, long byteAlignment) {
            return allocate0(byteSize, byteAlignment, false);
        }

        @SuppressWarnings("restricted")
        @ForceInline
        private MemorySegment allocate0(long byteSize, long byteAlignment, boolean init) {
            if (allocator.canAllocate(byteSize, byteAlignment)) {
                final MemorySegment segment = allocator
                        .allocate(byteSize, byteAlignment)
                        .reinterpret(arena, null);
                return init
                        ? segment.fill((byte) 0)
                        : segment;
            }
            throw new OutOfMemoryError("Unable to allocate from " + allocator);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

    }

    public static MemoryPool of(long byteSize, long byteAlignment, int maxBiasedThreads) {
        final Arena underlying = Arena.ofAuto();
        record CloseAction(Arena arena) implements Runnable {
            @Override
            public void run() {
                Reference.reachabilityFence(arena);
            }
        }
        final Stack<SlicingAllocator> stack = (maxBiasedThreads == 0)
                ? new ArrayStack<>()
                : new BiasedArrayStack<>(maxBiasedThreads);
        return new SharedMemoryPool(byteSize, byteAlignment, underlying, new CloseAction(underlying), stack);
    }

    private static sealed class LinkedStack<T> implements Stack<T> {

        static final Unsafe UNSAFE = Unsafe.getUnsafe();
        private static final long FIRST_OFFSET = UNSAFE.objectFieldOffset(LinkedStack.class, "first");

        // Updated reflectively
        private volatile Node<T> first;

        record Node<T>(T t, Node<T> next) { }

        @ForceInline
        public void push(T t) {
            Node<T> snapshot;
            Node<T> candidate;
            do {
                snapshot = first;
                candidate = new Node<>(t, snapshot);
            } while (!UNSAFE.compareAndSetReference(this, FIRST_OFFSET, snapshot, candidate));
        }

        @ForceInline
        public T pop() {
            Node<T> candidate;
            do {
                candidate = first;
                if (candidate == null) {
                    return null;
                }
            } while (!UNSAFE.compareAndSetReference(this, FIRST_OFFSET, candidate, candidate.next()));
            return candidate.t();
        }

    }

    private static final class BiasedLinkedStack<T> extends LinkedStack<T> {

        @Stable
        private final int maxBiasedThreads;
        @Stable
        // Components are accessed reflectively
        private final long[] biasedTids;
        @Stable
        // This array can be accessed using plain memory semantics
        private final Node<T>[] biasedFirsts;

        @SuppressWarnings("unchecked")
        public BiasedLinkedStack(int maxBiasedThreads) {
            this.maxBiasedThreads = maxBiasedThreads;
            this.biasedTids = new long[maxBiasedThreads];
            this.biasedFirsts = (Node<T>[]) new Node<?>[maxBiasedThreads];
        }

        @ForceInline
        public void push(T t) {
            final long tid = Thread.currentThread().threadId();
            final int bucket = bucket(tid);
            if (biasedTids[bucket] == 0) {
                // The first thread for this bucket will acquire biased performance
                UNSAFE.compareAndSetLong(biasedTids, Unsafe.ARRAY_LONG_BASE_OFFSET + Unsafe.ARRAY_LONG_INDEX_SCALE * (long) bucket, 0, tid);
                // The current thread will see this update even using plain semantics if it succeeds
            }
            if (biasedTids[bucket] == tid) {
                // Preferential treatment
                biasedFirsts[bucket] = new Node<>(t, biasedFirsts[bucket]);
            } else {
                super.push(t);
            }
        }

        @ForceInline
        public T pop() {
            final long tid = Thread.currentThread().threadId();
            final int bucket = bucket(tid);
            if (biasedTids[bucket] == tid) {
                // Preferential treatment
                var bf = biasedFirsts[bucket];
                if (bf == null) {
                    return null;
                }
                biasedFirsts[bucket] = bf.next();
                return bf.t();
            }
            return super.pop();
        }

        @ForceInline
        private int bucket(long tid) {
            return (int) ((tid % maxBiasedThreads)) & 0x7FFF_FFFF;
        }

    }

    // This is slightly faster but is bound to a preset size
    private static sealed class ArrayStack<T> implements Stack<T> {

        static final Unsafe UNSAFE = Unsafe.getUnsafe();
        static final int SIZE = 128;

        @SuppressWarnings("unchecked")
        private final T[] elements = (T[]) new Object[SIZE];

        private final AtomicInteger index = new AtomicInteger();

        private static final IntBinaryOperator INCREMENT_THROWING_AT_SIZE = new IntBinaryOperator() {
            @Override public int applyAsInt(int e, int unused) {
                if (++e >= SIZE) {
                    throw new IndexOutOfBoundsException();
                }
                return e;
            }
        };

        @ForceInline
        public void push(T t) {
            final int i = index.getAndAccumulate(1, INCREMENT_THROWING_AT_SIZE);
            UNSAFE.putReferenceVolatile(elements, Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * (long) i, t);
        }

        private static final IntBinaryOperator DECREMENT_STOPPING_AT_ZERO = new IntBinaryOperator() {
            @Override public int applyAsInt(int e, int unused) {
                return Math.max(--e, 0);
            }
        };

        @SuppressWarnings("unchecked")
        @ForceInline
        public T pop() {
            final int i = index.getAndAccumulate(-1, DECREMENT_STOPPING_AT_ZERO);
            return i <= 0
                    ? null
                    : (T) UNSAFE.getReferenceVolatile(elements, Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * (long) (i - 1));
        }

    }

    // This is slightly faster but is bound to a preset size
    private static final class BiasedArrayStack<T> extends ArrayStack<T> {

        @Stable
        private final int maxBiasedThreads;
        @Stable
        // Components are accessed reflectively
        private final long[] biasedTids;
        @Stable
        // This array can be accessed using plain memory semantics
        private final T[][] biasedArrays;
        private final int[] indices;

        @SuppressWarnings("unchecked")
        public BiasedArrayStack(int maxBiasedThreads) {
            this.maxBiasedThreads = maxBiasedThreads;
            this.biasedTids = new long[maxBiasedThreads];
            this.biasedArrays = (T[][]) Array.newInstance(Object.class, maxBiasedThreads, SIZE);
            this.indices = new int[maxBiasedThreads];
        }

        @ForceInline
        public void push(T t) {
            final long tid = Thread.currentThread().threadId();
            final int bucket = bucket(tid);
            if (biasedTids[bucket] == 0) {
                // The first thread for this bucket will acquire biased performance
                UNSAFE.compareAndSetLong(biasedTids, Unsafe.ARRAY_LONG_BASE_OFFSET + Unsafe.ARRAY_LONG_INDEX_SCALE * (long) bucket, 0, tid);
                // The current thread will see this update even using plain semantics if it succeeds
            }
            if (biasedTids[bucket] == tid) {
                // Preferential treatment
                final T[] elements = biasedArrays[bucket];
                elements[indices[bucket]++] = t;
            } else {
                super.push(t);
            }
        }

        @ForceInline
        public T pop() {
            final long tid = Thread.currentThread().threadId();
            final int bucket = bucket(tid);
            if (biasedTids[bucket] == tid) {
                // Preferential treatment
                final T[] elements = biasedArrays[bucket];
                final int index = indices[bucket];
                if (index <= 0) {
                    return null;
                } else {
                    return elements[--indices[bucket]];
                }
            }
            return super.pop();
        }

        @ForceInline
        private int bucket(long tid) {
            return (int) ((tid % maxBiasedThreads)) & 0x7FFF_FFFF;
        }

    }

    interface Stack<T> {
        void push(T t);
        T pop();
    }

}

