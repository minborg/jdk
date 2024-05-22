package jdk.internal.lang.stable;

import jdk.internal.lang.StableArray;
import jdk.internal.vm.annotation.ForceInline;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;

public record MemoizedFunction<T, R>(Function<? super T, ? extends R> original,
                                     StableArray<T> keys,
                                     IntFunction<R> wrapped) implements Function<T, R> {

    @ForceInline
    @Override
    public R apply(T t) {
        final int i = probe(keys, t);
        if (i < 0) {
            throw new NoSuchElementException(t.toString());
        }
        return wrapped.apply(i);
    }

    public static <T, R> Function<T, R> of(Set<T> inputs,
                                           Function<T, R> original) {
            if (inputs.isEmpty()) {
                return EmptyMemoizedFunction.instance();
            }
            final int size = inputs.size();
            final int len = 2 * size;
            final StableArray<T> keys = StableArray.of(len);

            for (T key : inputs) {
                final T k = Objects.requireNonNull(key);
                final int idx = probe(keys, k);
                if (idx >= 0) {
                    throw new IllegalArgumentException("duplicate key: " + k);
                } else {
                    final int dest = -(idx + 1);
                    keys.setOrThrow(dest, k);
                }
            }

            return new MemoizedFunction<>(
                    original,
                    keys,
                    MemoizedIntFunction.memoizedIntFunction(len, i -> original.apply(keys.orElseThrow(i)))
            );

    }

    private static <T> int probe(StableArray<T> keys, Object pk) {
        int idx = Math.floorMod(pk.hashCode(), keys.length());
        // Linear probing
        while (true) {
            T ek = keys.orElseNull(idx);
            if (ek == null) {
                return -idx - 1;
            } else if (pk.equals(ek)) {
                return idx;
            } else if ((idx += 1) == keys.length()) {
                idx = 0;
            }
        }
    }

    private static class EmptyMemoizedFunction<T,R> implements Function<T, R> {
        static final Function<?, ?> INSTANCE = new EmptyMemoizedFunction<>();
        @Override public R apply(T t) { throw new NoSuchElementException(); }
        @Override public String toString() { return "Empty"; }
        @SuppressWarnings("unchecked") static <T, R> Function<T, R> instance() {
            return (Function<T, R>) INSTANCE;
        }
    }


}
