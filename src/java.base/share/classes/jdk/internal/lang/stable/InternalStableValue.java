package jdk.internal.lang.stable;

import jdk.internal.vm.annotation.ForceInline;

import java.lang.StableValue;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

// Wrapper interface to allow internal implementations that are not public
public non-sealed interface InternalStableValue<T> extends StableValue<T> {

    T orElseSet(int input, FunctionHolder<?> functionHolder);

    T orElseSet(Object key, FunctionHolder<?> functionHolder);

    Object contentsPlain();

    Object contentsAcquire();

    boolean set(T newValue);

    @SuppressWarnings("unchecked")
    default T orElseSetSlowPath(final Object mutex,
                                final Object input,
                                final Object function) {
        preventReentry(mutex);
        synchronized (mutex) {
            final Object t = contentsPlain();  // Plain semantics suffice here
            if (t == null) {
                final T newValue;
                if (function instanceof FunctionHolder<?> functionHolder) {
                    final Object u = functionHolder.function();
                    newValue = eval(input, u);
                    // Reduce the counter and if it reaches zero, clear the reference
                    // to the underlying holder.
                    functionHolder.countDown();
                } else {
                    newValue = eval(input, function);
                }
                // The mutex is not reentrant so we know newValue should be returned
                set(newValue);
                return newValue;
            }
            return (T) t;
        }
    }

    @SuppressWarnings("unchecked")
    private T eval(Object input, Object u) {
        T v = switch (u) {
            case Supplier<?> sup -> (T) sup.get();
            case IntFunction<?> iFun -> (T) iFun.apply((int) input);
            case Function<?, ?> fun ->
                    ((Function<Object, T>) fun).apply(input);
            default -> throw new InternalError("cannot reach here");
        };
        Objects.requireNonNull(v);
        return v;
    }


    default void preventReentry(Object mutex) {
        // This method is not annotated with @ForceInline as it is always called
        // in a slow path.
        if (Thread.holdsLock(mutex)) {
            throw new IllegalStateException("Recursive initialization of a stable value is illegal");
        }
    }

}
