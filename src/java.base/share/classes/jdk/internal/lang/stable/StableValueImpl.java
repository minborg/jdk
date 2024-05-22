package jdk.internal.lang.stable;

import jdk.internal.lang.StableValue;
import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.NoSuchElementException;
import java.util.Objects;

import static jdk.internal.lang.stable.StableUtil.UNSAFE;

/**
 * Ultra-thin stable value wrapper that will not constant-fold null values.
 *
 * @param <T> type to hold
 */
public final class StableValueImpl<T> implements StableValue<T> {

    private static final long ELEMENT_OFFSET =
            UNSAFE.objectFieldOffset(StableValueImpl.class, "element");

    @Stable
    private T element;

    private StableValueImpl() {}

    public boolean trySet(T value) {
        // Prevent reordering under plain read semantics
        UNSAFE.storeStoreFence();
        return UNSAFE.compareAndSetReference(this, ELEMENT_OFFSET, null, value);
    }

    @ForceInline
    public T orElseThrow() {
        final T e = element;
        if (e != null) {
            return e;
        }
        return getOrThrowSlowPath();
    }

    @DontInline
    private T getOrThrowSlowPath() {
        @SuppressWarnings("unchecked")
        final T e = (T) UNSAFE.getReferenceVolatile(this, ELEMENT_OFFSET);
        if (e != null) {
            return e;
        }
        throw new NoSuchElementException();
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    public T orElseNull() {
        final T e = element;
        if (e != null) {
            return e;
        }
        return (T) UNSAFE.getReferenceVolatile(this, ELEMENT_OFFSET);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(orElseNull());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof StableValueImpl<?> other &&
                Objects.equals(orElseNull(), other.orElseNull());
    }

    @Override
    public String toString() {
        return "StableValue[" + orElseNull() + ']';
    }


    // Factory
    public static <T> StableValueImpl<T> of() {
        return new StableValueImpl<>();
    }

}
