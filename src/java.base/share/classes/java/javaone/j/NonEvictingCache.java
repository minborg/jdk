package java.javaone.j;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** NonEvictingCache */
public final class NonEvictingCache {

    private static final Function<Integer, Double> SQRT_CACHE =
            StableValue.function(Set.of(1, 2, 4, 8, 16), i -> StrictMath.sqrt(i));

    private static final Map<Integer, Double> SQRT_MAP =
            StableValue.map(Set.of(1, 2, 4, 8, 16), i -> StrictMath.sqrt(i));

    /**...*/
    public NonEvictingCache() {}

    /**
     * Demo app.
     * @param args ignored
     */
    public static void main(String[] args) {
        // Both eligible for constant folding by the JIT -> 4
        double sqrt16F = SQRT_CACHE.apply(16);
        double sqrt16M = SQRT_MAP.get(16);
    }

}
