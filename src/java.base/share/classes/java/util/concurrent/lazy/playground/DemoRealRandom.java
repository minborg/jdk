package java.util.concurrent.lazy.playground;

import java.lang.foreign.MemorySegment;
import java.util.HexFormat;
import java.util.concurrent.lazy.Lazy;
import java.util.concurrent.lazy.LazyReference;
import java.util.random.RandomGenerator;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

/**
 * DemoRealRandom: computing an expensive value
 */
public final class DemoRealRandom {

    private static final LazyReference<MemorySegment> PER_JVM_RANDOM_BYTES =
            Lazy.of(DemoRealRandom::makeRealRandomBytes);
    /**
     * {@return A memory segment with per-JVM random bytes}.
     */
    public static MemorySegment randomBytes() {
        return PER_JVM_RANDOM_BYTES.get();
    }

    private static final MemorySegment makeRealRandomBytes() {
        // Gather enthropy from lava lamps and atomic decay devices
        Entropy entropy = gatherEntropy();
        // Make random memory segment
        return entropy.toSegment(64);
    }

    /**
     * Main method
     *
     * @param args from command line
     */
    public static void main(String[] args) {
        var segment = randomBytes();

        var hex = HexFormat.ofDelimiter(", ")
                .formatHex(segment.toArray(JAVA_BYTE));

        System.out.println(hex);
    }

    private static Entropy gatherEntropy() {
        return new Entropy() {
        };
    }

    private interface Entropy {
        // Fake randoms...
        default MemorySegment toSegment(int length) {
            return MemorySegment.ofArray(RandomGenerator.getDefault().ints()
                    .limit(length)
                    .toArray());
        }
    }

    private DemoRealRandom() {
    }


}
