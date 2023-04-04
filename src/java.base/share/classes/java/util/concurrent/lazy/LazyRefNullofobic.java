package java.util.concurrent.lazy;

import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * A
 *
 * @param <V> Type
 */
public final class LazyRefNullofobic<V>
        implements Supplier<V> {

    static final VarHandle VALUE_VH = valueHandle();

    private final Semaphore semaphore = new Semaphore(1);

    private Supplier<? extends V> supplier;

    @Stable
    private Object value;

    /**
     * Constructor
     *
     * @param supplier to use
     */
    private LazyRefNullofobic(Supplier<? extends V> supplier) {
        this.supplier = supplier;
    }

    @SuppressWarnings("unchecked")
    @Override
    public V get() {
        V v = (V) value;
        if (v != null) {
            return v;
        }
        semaphore.acquireUninterruptibly();
        try {
            //v = getAcquire();
            v = (V) value;
            if (v == null) {
                if (supplier == null) {
                    throw new IllegalArgumentException("No pre-set supplier specified.");
                }
                try {
                    v = supplier.get();
                    if (v == null) {
                        throw new NullPointerException("The supplier returned null: " + supplier);
                    }
                    setRelease(v);
                } catch (Exception e) {
                    setRelease(e);
                    throw e;
                } finally {
                    supplier = null;
                }
            }
        } finally {
            semaphore.release();
        }
        return v;
    }

    /**
     * {@return The {@link Lazy.State } of this Lazy}.
     * <p>
     * The value is a snapshot of the current State.
     * No attempt is made to compute a value if it is not already present.
     * <p>
     * If the returned State is either {@link Lazy.State#PRESENT} or
     * {@link Lazy.State#ERROR}, it is guaranteed the state will
     * never change in the future.
     * <p>
     * This method can be used to act on a value if it is present:
     * {@snippet lang = java:
     *     if (lazy.state() == State.PRESENT) {
     *         V value = lazy.get();
     *         // perform action on the value
     *     }
     *}
     */
    public Lazy.State state() {
        if (value instanceof Exception) {
            return Lazy.State.ERROR;
        }
        // Use ReenterantLock to query Lazy.State.CONSTRUCTING. This also enables @jdk.internal.ValueBased
        if (semaphore.availablePermits() == 0) {
            return Lazy.State.CONSTRUCTING;
        }
        if (value == null) {
            semaphore.acquireUninterruptibly();
            try {
                if (value instanceof Exception) {
                    return Lazy.State.ERROR;
                }
                if (value == null) {
                    return Lazy.State.EMPTY;
                }
                return Lazy.State.PRESENT; // Redundant
            } finally {
                semaphore.release();
            }
        }
        return Lazy.State.PRESENT;
/*        return switch (value) {
            case Exception e -> Lazy.State.ERROR;
            case null -> {
                synchronized (this) {
                    yield switch (value) {
                        case Exception e -> Lazy.State.ERROR;
                        case null -> Lazy.State.EMPTY;
                        default -> Lazy.State.PRESENT;
                    };
                }
            }
            default -> Lazy.State.PRESENT;
        };*/
    }

    V getAcquire() {
        return (V) VALUE_VH.getAcquire(this);
    }

    void setRelease(Object value) {
        VALUE_VH.setRelease(this, value);
    }

    private static VarHandle valueHandle() {
        try {
            return MethodHandles.lookup()
                    .findVarHandle(LazyRefNullofobic.class, "value", Object.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * A
     * @param supplier s
     * @return s
     * @param <V> type
     */
    public static <V> Supplier<V> of(Supplier<? extends V> supplier) {
        return new LazyRefNullofobic<>(supplier);
    }

}
