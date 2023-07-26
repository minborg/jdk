package jdk.internal.util.concurrent.constant;

import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.constant.Constant;
import java.util.function.Supplier;

public final class StandardConstant<V> implements Constant<V> {

    // Allows access to the "value" field with arbitrary memory semantics
    private static final VarHandle VALUE_HANDLE;

    // Allows access to the "state" field with arbitrary memory semantics
    private static final VarHandle STATE_HANDLE;

    /**
     * This field holds the bound value.
     * If != null, a value is bound, otherwise the state field needs to be consulted.
     */
    @Stable
    private V value;

    byte state;

    private StandardConstant() {}

    private StandardConstant(V value) {
        this.value = value;
        this.state = (value == null)
                ? ConstantUtil.NULL
                : ConstantUtil.NON_NULL;
    }

    @ForceInline
    @Override
    public boolean isBinding() {
        return stateVolatile() == ConstantUtil.BINDING;
    }

    @ForceInline
    @Override
    public boolean isUnbound() {
        return stateVolatile() == ConstantUtil.UNBOUND;
    }

    @ForceInline
    @Override
    public boolean isBound() {
        // Try normal memory semantics first
        return value != null || ConstantUtil.isBound(stateVolatile());
    }

    @ForceInline
    @Override
    public boolean isError() {
        return stateVolatile() == ConstantUtil.BIND_ERROR;
    }

    @ForceInline
    @Override
    public V get() {
        // Try normal memory semantics first
        V v = value;
        if (v != null) {
            return v;
        }
        if (state == ConstantUtil.NULL) {
            return null;
        }
        return slowPath(null, null, true);
    }

    @ForceInline
    @Override
    public V orElse(V other) {
        // Try normal memory semantics first
        V v = value;
        if (v != null) {
            return v;
        }
        if (state == ConstantUtil.NULL) {
            return null;
        }
        return slowPath(null, other, false);
    }

    @ForceInline
    @Override
    public <X extends Throwable> V orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        V v = orElse(null);
        if (v == null) {
            throw exceptionSupplier.get();
        }
        return v;
    }

    @Override
    public synchronized void bind(V value) {
        if (state != ConstantUtil.UNBOUND) {
            ConstantUtil.throwAlreadyBound(ConstantUtil.toState(state));
        }
        if (value == null) {
            state = ConstantUtil.NULL;
        } else {
            this.value = value;
            state = ConstantUtil.NON_NULL;
        }
    }

    @Override
    public V computeIfUnbound(Supplier<? extends V> supplier) {
        Objects.requireNonNull(supplier);
        // Try normal memory semantics first
        V v = value;
        if (v != null) {
            return v;
        }
        if (state == ConstantUtil.NULL) {
            return null;
        }
        return slowPath(supplier, null, true);
    }

    private synchronized V slowPath(Supplier<? extends V> supplier,
                                    V other,
                                    boolean rethrow) {

        // Under synchronization, visibility and atomicy is guaranteed for both
        // the fields "value" and "auxiliary" as they are only changed within this block.
        V v = value;
        if (v != null) {
            return v;
        }
        return switch (state) {
            case ConstantUtil.NULL -> null;
            case ConstantUtil.BINDING ->
                    throw new StackOverflowError("Circular provider detected");
            case ConstantUtil.BIND_ERROR ->
                    throw new NoSuchElementException("A previous provider threw an exception");
            default -> bindValue(supplier, rethrow, other);
        };
    }

    private V bindValue(Supplier<? extends V> supplier,
                        boolean rethrow,
                        V other) {
        setStateVolatile(ConstantUtil.BINDING);
        try {
            V v = supplier.get();
            if (v == null) {
                setStateVolatile(ConstantUtil.BINDING);
            } else {
                casValue(v);
                setStateVolatile(ConstantUtil.NON_NULL);
            }
            return v;
        } catch (Throwable e) {
            setStateVolatile(ConstantUtil.BIND_ERROR);
            if (e instanceof java.lang.Error err) {
                // Always rethrow errors
                throw err;
            }
            if (rethrow) {
                throw new NoSuchElementException(e);
            }
            return other;
        }
    }

    @Override
    public String toString() {
        String v = switch (stateVolatile()) {
            case ConstantUtil.UNBOUND  -> ".unbound";
            case ConstantUtil.BINDING  -> ".binding";
            case ConstantUtil.NULL -> "null";
            case ConstantUtil.NON_NULL -> "[" + valueVolatile().toString() + "]";
            case ConstantUtil.BIND_ERROR -> ".error";
            default -> ".INTERNAL_ERROR";
        };
        return "StandardConstant" + v;
    }

    private void casValue(Object o) {
        if (!VALUE_HANDLE.compareAndSet(this, null, o)) {
            throw new InternalError();
        }
    }

    @SuppressWarnings("unchecked")
    private V valueVolatile() {
        return (V) VALUE_HANDLE.getVolatile(this);
    }

    private void setStateVolatile(byte state) {
        STATE_HANDLE.setVolatile(this, state);
    }

    private byte stateVolatile() {
        return (byte) STATE_HANDLE.getVolatile(this);
    }

    static  {
        try {
            var lookup = MethodHandles.lookup();
            VALUE_HANDLE = lookup
                    .findVarHandle(StandardConstant.class, "value", Object.class);
            STATE_HANDLE = lookup
                    .findVarHandle(StandardConstant.class, "state", byte.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static <V> StandardConstant<V> ofUnbound() {
        return new StandardConstant<>();
    }

    public static <V> StandardConstant<V> of(V value) {
        return new StandardConstant<>(value);
    }

}
