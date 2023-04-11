package java.util.concurrent.lazy.playground;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.lazy.Lazy;
import java.util.concurrent.lazy.LazyReferenceArray;
import java.util.function.Function;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.*;

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

        var mapper = Lazy.ofMapper(
                List.of("Foo", "Bar", "Baz"),
                s -> s.repeat(3));

        System.out.println("mapper.apply(\"A\") = " + mapper.apply("A"));
        System.out.println("mapper.apply(\"Bar\") = " + mapper.apply("Bar"));

        var m2 = new LazyMapper<>(List.of(
                new LazyKeyMapper<>("Foo", n -> n + " is the first name used in examples"),
                new LazyKeyMapper<>("Bar", n -> n + " is anther name"),
                new LazyKeyMapper<>("Baz", n -> n + " is rarely used")
        ));

        System.out.println("m2.apply(\"A\").orElse(\"Dunno\") = " + m2.apply("A").orElse("Dunno"));
        System.out.println("m2.apply(\"Bar\") = " + m2.apply("Bar"));

    }

    void a() {
        var pm = LazyReferenceArray.KeyMapper.polynomialMapper("A", "B", "C");
        System.out.println(pm);

        var rnd = new Random(42);
        var strings = IntStream.range(0, 10)
                .mapToObj(i -> randomString(rnd))
                .toArray(String[]::new);
        Arrays.stream(strings)
                .forEach(System.out::println);
        var pm2 = LazyReferenceArray.KeyMapper.polynomialMapper(strings);
        System.out.println(pm2);
    }

    private static String randomString(Random random) {
        int len = 2 + random.nextInt(5);
        return IntStream.range(0, len)
                .mapToObj(i -> (char) ('A' + random.nextInt('z' - 'A')))
                .map(Objects::toString)
                .collect(joining());
    }

    private DemoMapObject() {
    }
}
