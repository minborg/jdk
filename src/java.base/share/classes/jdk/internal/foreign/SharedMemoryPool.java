package jdk.internal.foreign;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryPool;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.ref.Reference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntBinaryOperator;
import java.util.function.IntFunction;

// Todo: explore an underlying shared arena

public record SharedMemoryPool(long byteSize,
                               long byteAlignment,
                               Arena underlying,
                               Runnable closeAction,
                               LinkedStack<SlicingAllocator> stack) implements MemoryPool {

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
                               LinkedStack<SlicingAllocator> stack)
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

    public static MemoryPool of(long byteSize, long byteAlignment) {
        final Arena underlying = Arena.ofAuto();
        record CloseAction(Arena arena) implements Runnable {
            @Override
            public void run() {
                Reference.reachabilityFence(arena);
            }
        }
        return new SharedMemoryPool(byteSize, byteAlignment, underlying, new CloseAction(underlying), new LinkedStack<>());
    }

    private static final class LinkedStack<T> {

        private static final Unsafe UNSAFE = Unsafe.getUnsafe();
        private static final long FIRST_OFFSET = UNSAFE.objectFieldOffset(LinkedStack.class, "first");
        private static final long BIASED_TID_OFFSET = UNSAFE.objectFieldOffset(LinkedStack.class, "biasedTid");

        // Updated reflectively
        private volatile Node<T> first;

        @Stable
        private long biasedTid;
        // Updated reflectively
        private T biasedT;
        // Plain memory semantics
        private Node<T> biasedFirst;

        record Node<T>(T t, Node<T> next) { }

        @ForceInline
        void push(T t) {
            final long tid = Thread.currentThread().threadId();
            if (biasedTid == 0) {
                // The first thread will acquire biased performance
                UNSAFE.compareAndSetLong(this, BIASED_TID_OFFSET, 0, tid);
                // The current thread will see this update if it succeeds
            }
            if (biasedTid == tid) {
                // Preferential treatment
                biasedFirst = new Node<>(t, biasedFirst);
            } else {
                Node<T> snapshot;
                Node<T> candidate;
                do {
                    snapshot = first;
                    candidate = new Node<>(t, snapshot);
                } while (!UNSAFE.compareAndSetReference(this, FIRST_OFFSET, snapshot, candidate));
            }
        }

        @ForceInline
        T pop() {
            final long tid = Thread.currentThread().threadId();
            if (biasedTid == tid) {
                // Preferential treatment
                var bf = biasedFirst;
                if (bf == null) {
                    return null;
                }
                biasedFirst = bf.next();
                return bf.t();
            }
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

    // This is slightly faster but is bound to a preset size
    private static final class ArrayStack<T> {

        private static final Unsafe UNSAFE = Unsafe.getUnsafe();
        private static final int SIZE = 128;

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
        void push(T t) {
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
        T pop() {
            final int i = index.getAndAccumulate(-1, DECREMENT_STOPPING_AT_ZERO);
            return i <= 0
                    ? null
                    : (T) UNSAFE.getReferenceVolatile(elements, Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * (long) (i - 1));
        }

    }

}

