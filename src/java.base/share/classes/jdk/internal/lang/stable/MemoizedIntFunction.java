package jdk.internal.lang.stable;

import jdk.internal.lang.StableArray;
import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;

import java.util.NoSuchElementException;
import java.util.function.IntFunction;
import java.util.stream.Stream;

public record MemoizedIntFunction<R>(IntFunction<? extends R> original,
                                     StableArray<R> values,
                                     StableArray<ProviderResult> results,
                                     Object[] mutexes) implements IntFunction<R> {

    @ForceInline
    @Override
    public R apply(int i) {
        final R t = values.orElseNull(i);
        if (t != null) {
            return t;
        }
        if (results.orElseNull(i) instanceof ProviderResult.Null) {
            return null;
        }
        return getSlowPath(i);
    }

    @DontInline
    private R getSlowPath(int i) {
        synchronized (mutexes[i]) {
            return switch (results.orElseNull(i)) {
                case ProviderResult.NonNull _  -> values.orElseNull(i);
                case ProviderResult.Null _     -> null;
                case ProviderResult.Error<?> e -> throw new NoSuchElementException(e.throwableClass().getName());
                case null -> {
                    try {
                        R t = original.apply(i);
                        if (t != null) {
                            values.setOrThrow(i, t);
                            results.setOrThrow(i, ProviderResult.NonNull.INSTANCE);
                        } else {
                            results.setOrThrow(i, ProviderResult.Null.INSTANCE);
                        }
                        yield t;
                    } catch (Throwable th) {
                        results.setOrThrow(i, new ProviderResult.Error<>(th.getClass()));
                        throw th;
                    }
                }
            };
        }
    }

    public static <R> IntFunction<R> memoizedIntFunction(int length,
                                                         IntFunction<? extends R> original) {
        return new MemoizedIntFunction<>(
                original,
                StableArray.of(length),
                StableArray.of(length),
                Stream.generate(Object::new).limit(length).toArray()
        );
    }

}
