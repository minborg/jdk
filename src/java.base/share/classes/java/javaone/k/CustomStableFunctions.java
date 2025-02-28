package java.javaone.k;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**..*/
public final class CustomStableFunctions {
    /**..*/
    public CustomStableFunctions() {}

    // Why only stable Supplier, IntSupplier and Function?

    static <T> Predicate<T> stablePredicate(Set<? extends T> inputs,
                                            Predicate<? super T> original) {
        return StableValue.<T, Boolean>function(inputs, original::test)::apply;
    }

    // More-than-one-arity functions are harder

    // Entry<L, R>
    record Pair<L, R>(L left, R right){}

    // Somewhat simplified for brevity (e.g. super/extends removed & no dedup)

    record StableBiFunction<T, U, R>(
            Map<T, Map<U, StableValue<R>>> delegate,
            BiFunction<T, U, R> original
    ) implements BiFunction<T, U, R> {

        @Override
        public R apply(T t, U u) {
            final StableValue<R> stableValue;
            try {
                stableValue = Objects.requireNonNull(delegate.get(t).get(u));
            } catch (NullPointerException _) {
                throw new IllegalArgumentException(t.toString() + ", " + u.toString());
            }
            return stableValue.orElseSet(() -> original.apply(t, u));
        }

        static <T, U, R> BiFunction<T, U, R> of(Set<Pair<T, U>> inputs,
                                                BiFunction<T, U, R> original) {
            Map<T, Map<U, StableValue<R>>> delegate = Map.copyOf(inputs.stream()
                    .collect(Collectors.groupingBy(Pair::left,
                            Collectors.toUnmodifiableMap(Pair::right,
                                    _ -> StableValue.of()))));
            return new StableBiFunction<>(delegate, original);
        }

    }

    /**...*/
    void main() {
        Predicate<Integer> even = stablePredicate(Set.of(1, 2), i -> i % 2 == 0);
        // true
        System.out.println("even.getClass().isHidden() = " + even.getClass().isHidden());

        BiFunction<Integer, Integer, Integer> max = StableBiFunction.of(Set.of(new Pair<>(1, 1)), Integer::max);
        // true
        System.out.println("max.getClass().isRecord() = " + max.getClass().isRecord());
    }

}
