package jdk.internal.lang.stable;

import jdk.internal.lang.StableArray;
import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Stream;

public record MemoizedFunction<T, R>(Function<? super T, ? extends R> original,
                                     StableArray<T> keys,
                                     IntFunction<R> wrapped) implements Function<T, R> {

    @ForceInline
    @Override
    public R apply(T t) {
        int i = probe(keys, t);
        if (i < 0) {
            throw new NoSuchElementException(t.toString());
        }
        return wrapped.apply(i);
    }

    public static <T, R> Function<T, R> of(Set<T> inputs,
                                           Function<T, R> original) {
            if (inputs.isEmpty()) {
                return new Function<T, R>() {
                    @Override
                    public R apply(T t) {
                        throw new NoSuchElementException();
                    }
                };
            }
            int size = inputs.size();

            // Todo: Consider having a larger array
            int len = 2 * size;
            len = (len + 1) & ~1; // ensure table is even length

            StableArray<T> keys = StableArray.of(len);

            for (T key : inputs) {
                T k = Objects.requireNonNull(key);
                int idx = probe(keys, k);
                if (idx >= 0) {
                    throw new IllegalArgumentException("duplicate key: " + k);
                } else {
                    int dest = -(idx + 1);
                    keys.setOrThrow(dest, k);
                }
            }

            // IntFunction<K> mappedOriginal = i -> original.apply(keys.getOrThrow(i));

            return new MemoizedFunction<>(
                    original,
                    keys,
                    MemoizedIntFunction.memoizedIntFunction(len, i -> original.apply(keys.getOrThrow(i)))
            );

    }

    private static <T> int probe(StableArray<T> keys, Object pk) {
        int idx = Math.floorMod(pk.hashCode(), keys.length());
        // Linear probing
        while (true) {
            T ek = keys.getOrNull(idx);
            if (ek == null) {
                return -idx - 1;
            } else if (pk.equals(ek)) {
                return idx;
            } else if ((idx += 1) == keys.length()) {
                idx = 0;
            }
        }
    }

}
