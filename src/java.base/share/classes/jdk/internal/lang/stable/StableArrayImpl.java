package jdk.internal.lang.stable;

import jdk.internal.lang.StableArray;
import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static jdk.internal.lang.stable.StableUtil.UNSAFE;
import static jdk.internal.lang.stable.StableUtil.objectOffset;

/**
 * Ultra-thin stable value wrapper that will not constant-fold null values.
 *
 * @param <T> type to hold
 */
public final class StableArrayImpl<T> implements StableArray<T> {

    @Stable
    private final T[] elements;

    @SuppressWarnings("unchecked")
    private StableArrayImpl(int length) {
        elements =  (T[]) new Object[length];
    }

    public boolean trySet(int index, T value) {
        // Explicitly check the index as we are performing unsafe operations later on
        Objects.checkIndex(index, elements.length);
        // Prevent reordering under plain read semantics
        UNSAFE.storeStoreFence();
        return UNSAFE.compareAndSetReference(elements, objectOffset(index), null, value);
    }

    @ForceInline
    public T orElseThrow(int index) {
        // Implicit array bounds check
        final T e = elements[index];
        if (e != null) {
            return e;
        }
        return getOrThrowSlowPath(index);
    }

    @DontInline
    private T getOrThrowSlowPath(int index) {
        @SuppressWarnings("unchecked")
        final T e = (T) UNSAFE.getReferenceVolatile(elements, objectOffset(index));
        if (e != null) {
            return e;
        }
        throw new NoSuchElementException();
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    public T orElseNull(int index) {
        // Implicit array bounds check
        final T e = elements[index];
        if (e != null) {
            return e;
        }
        return (T) UNSAFE.getReferenceVolatile(elements, objectOffset(index));
    }

    @Override
    public int length() {
        return elements.length;
    }

    @Override
    public int hashCode() {
        return IntStream.range(0, length())
                .mapToObj(this::orElseNull)
                .mapToInt(Objects::hashCode)
                .reduce(1, (a, e) -> 31 * a + e);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof StableArrayImpl<?> other &&
                length() == other.length() &&
                IntStream.range(0, length())
                        .allMatch(i -> Objects.equals(orElseNull(i), other.orElseNull(i)));
    }

    @Override
    public String toString() {
        return "StableArray[" +
                IntStream.range(0, length())
                        .mapToObj(this::orElseNull)
                        .map(Objects::toString)
                        .collect(Collectors.joining(", ")) +
                ']';
    }

    // Factory
    public static <T> StableArrayImpl<T> of(int length) {
        return new StableArrayImpl<>(length);
    }

}
