package java.util.concurrent.lazy.playground;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentScope;
import java.lang.invoke.VarHandle;
import java.util.concurrent.lazy.LazyLong;
import java.util.stream.LongStream;

import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.util.Objects.requireNonNull;

/**
 * DemoLazyLong
 */
public final class DemoLazyLong {

    /**
     * Main
     *
     * @param args from command line
     */
    public static void main(String[] args) {
        Measurement measurement = new Measurement(MemorySegment.ofArray(new long[]{1L, 2L, 3L}));

        System.out.println("measurement.valueAt(1) = " + measurement.valueAt(1));
        System.out.println("measurement.sum() = " + measurement.sum());
    }

    static final class Measurement {

        private static final MemoryLayout LAYOUT = MemoryLayout.sequenceLayout(JAVA_LONG);
        private static final VarHandle HANDLE = LAYOUT.varHandle(MemoryLayout.PathElement.sequenceElement());

        private final MemorySegment segment;
        private final LazyLong sum = LazyLong.of(this::sum0);

        public Measurement(MemorySegment segment) {
            // Defensive read-only copy
            this.segment = MemorySegment.allocateNative(segment.byteSize(), SegmentScope.auto())
                    .copyFrom(segment)
                    .asReadOnly();
        }

        long valueAt(long index) {
            return (long) HANDLE.get(segment, index);
        }

        long sum() {
            return sum.getAsLong();
        }

        private long sum0() {
            return LongStream.range(0, segment.byteSize() / Long.BYTES)
                    .map(this::valueAt)
                    .sum();
        }

    }


    private DemoLazyLong() {
    }
}
