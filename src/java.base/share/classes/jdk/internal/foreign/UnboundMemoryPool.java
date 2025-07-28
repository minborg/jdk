package jdk.internal.foreign;

import jdk.internal.vm.annotation.ForceInline;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryPool;
import java.lang.foreign.MemorySegment;
import java.lang.ref.Reference;

public final class UnboundMemoryPool implements MemoryPool {

    private final SegmentFifo segmentFifo;
    private final Runnable closeAction;

    public UnboundMemoryPool(boolean concurrent) {
        final Arena underlyingArena = Arena.ofAuto();
        this.segmentFifo = concurrent
                ? new SegmentFifo.OfConcurrent(underlyingArena, Arena::allocate)
                : new SegmentFifo.OfNonConcurrent(underlyingArena, Arena::allocate);
        this.closeAction = new CloseAction(underlyingArena);
    }

    @ForceInline
    @Override
    public Arena get() {
        return new UnboudMemoryPoolArena(segmentFifo, closeAction);
    }

    @Override
    public String toString() {
        return "UnboundMemoryPool(segmentFifo=" + segmentFifo + ")";
    }

    // Todo: Improve the situation for "from" operations
    private record UnboudMemoryPoolArena(SegmentFifo segmentFifo,
                                         Runnable closeAction,
                                         ArenaImpl arena,
                                         UnboundSegmentStack segments) implements Arena {

        @ForceInline
        private UnboudMemoryPoolArena(SegmentFifo segmentFifo,
                                      Runnable closeAction) {
            this(segmentFifo, closeAction, (ArenaImpl) Arena.ofConfined(), new UnboundSegmentStack());
            memorySession().addCloseAction(closeAction);
        }

        @ForceInline
        @Override
        public MemorySegment allocate(long byteSize, long byteAlignment) {
            return allocate0(byteSize, byteAlignment)
                    // Todo: explore faster fill (backing is always a power of two)
                    .fill((byte) 0);
        }

        @SuppressWarnings("restricted")
        @ForceInline
        public MemorySegment allocate0(long byteSize, long byteAlignment) {
            Utils.checkAllocationSizeAndAlign(byteSize, byteAlignment);
            memorySession().checkValidState();
            final MemorySegment raw = segmentFifo.take(byteSize, byteAlignment);
            segments.push(raw);
            final long address = raw.address();
            return raw.asSlice(Utils.alignUp(address, byteAlignment) - address, byteSize)
                    .reinterpret(arena, null);
        }


        @Override
        public MemorySegment.Scope scope() {
            return arena.scope();
        }

        @ForceInline
        @Override
        public void close() {
            arena.close();
            for (MemorySegment segment : segments) {
                segmentFifo.release(segment);
            }
        }

        @ForceInline
        private MemorySessionImpl memorySession() {
            return ((MemorySessionImpl) arena.scope());
        }

    }

    private record CloseAction(Arena arena) implements Runnable {
        @Override
        public void run() {
            Reference.reachabilityFence(arena);
        }
    }

}
