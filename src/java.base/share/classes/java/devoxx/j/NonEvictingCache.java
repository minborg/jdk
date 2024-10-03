package java.devoxx.j;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** NonEvictingCache */
public final class NonEvictingCache {

    private static final Function<Integer, Double> SQRT_CACHE =
            StableValue.ofFunction(Set.of(1, 2, 4, 8, 16), i -> Math.sqrt(i));

    private static final Map<Integer, Double> SQRT_MAP =
            StableValue.ofMap(Set.of(1, 2, 4, 8, 16), i -> Math.sqrt(i));

    /** Ctor */ public NonEvictingCache() {}

    /**
     * Demo app.
     * @param args ignored
     */
    public static void main(String[] args) {
        double sqrt16F = SQRT_CACHE.apply(16); // Constant folded -> 4
        double sqrt16M = SQRT_MAP.get(16);       // Constant folded -> 4


    }


}
