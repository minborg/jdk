package jdk.internal.reflect;

import jdk.internal.ValueBased;
import jdk.internal.invoke.MhUtil;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.util.NoSuchElementException;

// In `jdk.internal.reflect` so trusted fields

@ValueBased
public final class ReadBiasedHolder<T> {

    private final Class<T> type;
    private final MutableCallSite mutableCallSite;
    private final MethodHandle dynamicInvoker;

    private ReadBiasedHolder(Class<T> type,
                             MutableCallSite mutableCallSite,
                             MethodHandle dynamicInvoker) {
        this.type = type;
        this.mutableCallSite = mutableCallSite;
        this.dynamicInvoker = dynamicInvoker;
    }

    /**
     * R
     * @return t
     */
    @SuppressWarnings("unchecked")
    public T get() {
        try {
            return (T) dynamicInvoker.invokeExact();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * A
     * @return the type
     */
    public Class<T> type() {
        return type;
    }

    /**
     * Set
     * @param value v
     */
    public void set(T value) {
        set(MethodHandles.constant(type(), value));
    }

    public void set(MethodHandle methodHandle) {
        mutableCallSite.setTarget(methodHandle);
    }

    private void init() {
        set(NO_SUCH_ELEMENT_EXCEPTION.asType(MethodType.methodType(type)));
    }

    private static final MethodHandle NO_SUCH_ELEMENT_EXCEPTION =
            MhUtil.findStatic(MethodHandles.lookup(), "noSuchElementException", MethodType.methodType(Object.class));

    static Object noSuchElementException() {
        throw new NoSuchElementException("No value set");
    }

    /**
     * A
     * @param type t
     * @return r
     * @param <T> T
     */
    public static <T> ReadBiasedHolder<T> of(Class<T> type) {
        final MethodType methodType = MethodType.methodType(type);
        final MutableCallSite mutableCallSite = new MutableCallSite(methodType);
        var holder =  new ReadBiasedHolder<>(type, mutableCallSite, mutableCallSite.dynamicInvoker().asType(MethodType.methodType(Object.class)));
        // holder.init();
        return holder;
    }


}
