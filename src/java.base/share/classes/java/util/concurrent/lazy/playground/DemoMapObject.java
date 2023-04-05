package java.util.concurrent.lazy.playground;

import java.util.Random;
import java.util.concurrent.lazy.Lazy;
import java.util.concurrent.lazy.LazyReferenceArray;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A demo of how to cache computation.
 */
public final class DemoMapObject {

    /**
     * Main
     *
     * @param args args
     */
    public static void main(String[] args) {
        var pm = LazyReferenceArray.KeyMapper.polynomialMapper("A", "B", "C");
        System.out.println(pm);

        var rnd = new Random(42);
        var pm2 = LazyReferenceArray.KeyMapper.polynomialMapper(IntStream.range(0, 10).mapToObj(i -> randomString(rnd)).toArray());
        System.out.println(pm2);
    }

    private static String randomString(Random random) {
        int len = 2 + random.nextInt(5);
        return IntStream.range(0, len)
                .mapToObj(i -> "a" + random.nextInt('z' - 'a'))
                .collect(Collectors.joining());
    }

    private DemoMapObject() {
    }
}
