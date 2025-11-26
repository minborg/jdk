package java.lang;

import jdk.internal.invoke.MhUtil;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.util.NoSuchElementException;

// In java.lang so trusted fields

/**
 * A
 * @param <T> T
 */
public sealed interface ReadBiasedHolder<T> {

    /**
     * A
     * @return the type
     */
    Class<T> type();

    /**
     * A
     * @return the held value
     */
    T get();

    /**
     * Sets the value
     * @param value to set
     */
    void set(T value);

    /**
     * Impl
     * @param type t
     * @param mutableCallSite mcs
     * @param dynamicInvoker di
     * @param <T> T
     */
    record ReadBiasedHolderImpl<T>(
            Class<T> type,
            MutableCallSite mutableCallSite,
            MethodHandle dynamicInvoker
    ) implements ReadBiasedHolder<T> {

        @SuppressWarnings("unchecked")
        @Override
        public T get() {
            try {
                return (T) dynamicInvoker.invoke();
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }

        }

        @Override
        public void set(T value) {
            set0(MethodHandles.constant(type(), value));
        }

        private void set0(MethodHandle methodHandle) {
            mutableCallSite.setTarget(methodHandle);
        }

        private void init() {
            set0(ReadBiasedHolderImpl.NO_SUCH_ELEMENT_EXCEPTION.asType(MethodType.methodType(type)));
        }

        private static final MethodHandle NO_SUCH_ELEMENT_EXCEPTION =
                MhUtil.findStatic(MethodHandles.lookup(), "noSuchElementException", MethodType.methodType(Object.class));

        static Object noSuchElementException() {
            throw new NoSuchElementException("No value set");
        }
    }

    /**
     * A
     * @param type t
     * @return r
     * @param <T> T
     */
    static <T> ReadBiasedHolderImpl<T> of(Class<T> type) {
        final MutableCallSite mutableCallSite = new MutableCallSite(MethodType.methodType(type));
        var holder =  new ReadBiasedHolderImpl<>(type, mutableCallSite, mutableCallSite.dynamicInvoker());
        // holder.init();
        return holder;
    }


}
