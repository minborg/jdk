package jdk.internal.lang.stable;

import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Supplier;

import static jdk.internal.lang.stable.StableUtil.*;

public final class StableSupplier<T> implements Supplier<T> {

    private static final long ELEMENT_OFFSET = offset("element");
    private static final long STATE_OFFSET = offset("state");
    private static final long MUTEX_OFFSET = offset("mutex");
    private static final long COMPUTE_INVOKE_OFFSET = offset("computeInvoke");

    @Stable
    private final Supplier<? extends T> supplier;
    @Stable
    private T element;
    @Stable
    private byte state;
    @Stable
    private Object mutex;
    @Stable
    private byte computeInvoke;

    public StableSupplier(Supplier<? extends T> supplier) {
        this.supplier = supplier;
    }

    @ForceInline
    @Override
    public T get() {
        T t = element;
        if (t != null) {
            return t;
        }
        return slowPath();
    }

    @DontInline
    private T slowPath() {
        @SuppressWarnings("unchecked")
        T t = (T)UNSAFE.getReferenceVolatile(this, ELEMENT_OFFSET);
        if (t != null) {
            return t;
        }
        Object mutex = acquireMutex();
        if (isMutexNeeded(mutex)) {
            synchronized (mutex) {
                return switch (state) {
                    case UNSET        -> {
                        if (!casComputeInvoked()) {
                            throw new StackOverflowError(supplier.toString());
                        }
                        try {
                            T element = supplier.get();
                            Objects.requireNonNull(element);
                            UNSAFE.storeStoreFence();
                            UNSAFE.putReferenceVolatile(this, ELEMENT_OFFSET, element);
                            UNSAFE.putIntVolatile(this,STATE_OFFSET, SET_NON_NULL);
                            UNSAFE.putReferenceVolatile(this, MUTEX_OFFSET, TOMBSTONE);
                            yield element;
                        } catch (Throwable th) {
                            UNSAFE.putReferenceVolatile(this, MUTEX_OFFSET, th.getClass());
                            UNSAFE.putIntVolatile(this, STATE_OFFSET, ERROR);
                            throw th;
                        }
                    }
                    case SET_NON_NULL -> element;
                    case ERROR        -> throw previousError(mutex);
                    default           -> throw shouldNotReachHere();
                };
            }
        }
        // If we already have a set value or an error, we do not need a mutex
        return switch (UNSAFE.getByteVolatile(this, STATE_OFFSET)) {
            case SET_NON_NULL -> {
                @SuppressWarnings("unchecked")
                T element = (T) UNSAFE.getReferenceVolatile(this, ELEMENT_OFFSET);
                yield element;
            }
            case ERROR        -> throw previousError(mutex);
            default           -> throw shouldNotReachHere();
        };

    }

    static InternalError shouldNotReachHere() {
        return new InternalError("Should not reach here");
    }


    static NoSuchElementException previousError(Object object) {
        String exceptionName = ((Class<?>)object).getName();
        return new NoSuchElementException(
                "A previous provider threw an exception of type " + exceptionName);
    }

    static boolean isMutexNeeded(Object mutex) {
        return mutex != TOMBSTONE && !(mutex instanceof Throwable);
    }

    private Object acquireMutex() {
        Object mutex = UNSAFE.getReferenceVolatile(this, MUTEX_OFFSET);
        if (mutex == null) {
            mutex = caeMutex();
        }
        return mutex;
    }

    private Object caeMutex() {
        final var created = new Object();
        final var witness = UNSAFE.compareAndExchangeReference(this, MUTEX_OFFSET, null, created);
        return witness == null ? created : witness;
    }

    private boolean casComputeInvoked() {
        return UNSAFE.compareAndSetByte(this, COMPUTE_INVOKE_OFFSET, NOT_INVOKED, INVOKED);
    }



    public static <T> StableSupplier<T> create(Supplier<? extends T> supplier) {
        return new StableSupplier<>(supplier);
    }

    private static long offset(String name) {
        return UNSAFE.objectFieldOffset(StableSupplier.class, name);
    }

}
