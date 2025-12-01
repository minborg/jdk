package java.lang.reflect;

import jdk.internal.ValueBased;
import jdk.internal.invoke.MhUtil;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.util.NoSuchElementException;

// In `jdk.lang.reflect` so trusted fields

/**
 * A read biased value is a holder of contents that are rarely updated.
 * <p>
 * TBW
 *
 * @param <T> the type of the contents
 */
@ValueBased
public final /* value */ class ReadBiasedValue<T> {

    private final Class<T> type;
    private final MutableCallSite mutableCallSite;
    private final MethodHandle dynamicInvoker;
    private final MethodHandle dynamicInvokerAsObject;

    private ReadBiasedValue(Class<T> type,
                            MutableCallSite mutableCallSite,
                            MethodHandle dynamicInvoker,
                            MethodHandle dynamicInvokerAsObject) {
        this.type = type;
        this.mutableCallSite = mutableCallSite;
        this.dynamicInvoker = dynamicInvoker;
        this.dynamicInvokerAsObject = dynamicInvokerAsObject;
    }

    /**
     * {@return the contents}
     *
     * @throws IllegalStateException if no contents has been set
     */
    public T get() {
        try {
            return type.cast(dynamicInvokerAsObject.invokeExact());
        } catch (IllegalStateException ise) {
            // Contents uninitialized
            throw ise;
        } catch (Throwable t) {
            throw new InternalError(t);
        }
    }

    /**
     * {@return the type of the contents}
     */
    public Class<T> type() {
        return type;
    }

    /**
     * Sets the contents to the provided {@code value}
     *
     * @param value the type of the value
     * @throws ClassCastException if the class of the provided value
     *         prevents it from being set as contents
     */
    public void set(T value) {
        setHandle(MethodHandles.constant(type(), type.cast(value)));
    }

    /**
     * Indirectly sets the contents to the provided {@code methodHandle} where the
     * {@code methodHandle} returns the contents.
     *
     * @param methodHandle that returns the contents
     * @throws IllegalArgumentException if the provided method handle takes parameters
     * @throws ClassCastException if the class of the return type of the provided
     *         method handle prevents it from being used as contents
     */
    public void setHandle(MethodHandle methodHandle) {
        final MethodType mhType = methodHandle.type();
        if (mhType.parameterCount() > 0) {
            throw new IllegalArgumentException("The provided MethodHandle should not have parameters: " + methodHandle);
        }
        if (!type.isAssignableFrom(mhType.returnType())) {
            throw new ClassCastException("Illegal return type." + mhType.returnType() + " is not a subclass of " + type);
        }
        mutableCallSite.setTarget(methodHandle);
    }

    /**
     * {@return a method handle that returns the contents of this read biased value}
     * <p>
     *  TBW - Can use sharp primitive shapes
     */
    public MethodHandle getHandle() {
        return dynamicInvoker;
    }

    /**
     * {@return a new read biased value of the provided {@code type}}
     *
     * @param type a class that represent the type of the contents
     * @param <T> type of the contents.
     */
    public static <T> ReadBiasedValue<T> of(Class<T> type) {
        final MethodType methodType = MethodType.methodType(type);
        final MutableCallSite mutableCallSite = new MutableCallSite(methodType);
        final MethodHandle dynamicInvoker = mutableCallSite.dynamicInvoker();
        final MethodHandle dynamicInvokerAsObject = dynamicInvoker.asType(MethodType.methodType(Object.class));
        return new ReadBiasedValue<>(type, mutableCallSite, dynamicInvoker, dynamicInvokerAsObject);
    }

}
