package jdk.internal.foreign.layout;

import jdk.internal.vm.annotation.ForceInline;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

/**
 * Common methods for group and sequence layout OfClass implementations.
 *
 * @param <T> carrier type
 */
public interface InternalCompositeLayoutOfClass<T> {
    Class<T> carrier();

    MethodHandle getter();

    MethodHandle setter();

    MethodHandle adaptedGetter();

    MethodHandle adaptedSetter();

    @SuppressWarnings("unchecked")
    @ForceInline
    default T get(MemorySegment segment, long offset) {
        try {
            return (T) adaptedGetter().invokeExact(segment, offset);
        } catch (NullPointerException |
                 IndexOutOfBoundsException |
                 WrongThreadException |
                 IllegalStateException |
                 IllegalArgumentException rethrow) {
            throw rethrow;
        } catch (Throwable e) {
            throw new RuntimeException("Unable to invoke getter() with " +
                    "segment=" + segment +
                    ", offset=" + offset, e);
        }
    }

    default void set(MemorySegment segment, long offset, T t) {
        try {
            adaptedSetter().invokeExact(segment, offset, (Object) t);
        } catch (IndexOutOfBoundsException |
                 WrongThreadException |
                 IllegalStateException |
                 IllegalArgumentException |
                 UnsupportedOperationException |
                 NullPointerException rethrow) {
            throw rethrow;
        } catch (Throwable e) {
            throw new RuntimeException("Unable to invoke setter() with " +
                    "segment=" + segment +
                    ", offset=" + offset +
                    ", t=" + t, e);
        }
    }

    static MethodHandle adaptGetter(MethodHandle getter) {
        return getter.asType(MethodType.methodType(Object.class, MemorySegment.class, long.class));
    }

    static MethodHandle adaptSetter(MethodHandle setter) {
        return setter.asType(MethodType.methodType(void.class, MemorySegment.class, long.class, Object.class));
    }

}
