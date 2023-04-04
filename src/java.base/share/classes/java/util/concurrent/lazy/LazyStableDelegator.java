package java.util.concurrent.lazy;

import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.Supplier;

/**
 * A
 * @param <T> Type
 */
public final class LazyStableDelegator<T>
        implements Supplier<T> {

    private static final VarHandle DELEGATOR_HANDLE = evaluatedSupplierHandle();

    private boolean initiated;

    // Changed twice but maybe that is ok?
    private Supplier<T> delegator;

    /**
     * Constructor
     * @param initialSupplier supplier
     */
    public LazyStableDelegator(Supplier<T> initialSupplier) {
        this.delegator = initialSupplier;
    }

    public T get() {
        return delegator.get();

/*        return initialized
                ? evaluatedSupplier.get()
                : tryOriginal();*/
    }

    private synchronized T tryOriginal() {
        if (!initiated) {
            try {
                T value = delegator.get();
                //initialized = true;
                // Alt 1
                DELEGATOR_HANDLE.setRelease(this, new EvaluatedSupplier<>(value));
                //DELEGATOR_HANDLE.setRelease(this, (Supplier<T>)() -> value);

                // Alt 2
                // DELEGATOR_HANDLE.set(this, new EvaluatedSupplier<>(value));
                // VarHandle.fullFence();
            } finally {
                initiated = true;
            }
        }
        return delegator.get();
    }

    private static final class EvaluatedSupplier<T>
            implements Supplier<T> {
        private final T value;

        public EvaluatedSupplier(T value) {
            this.value = value;
        }

        @Override
        public T get() {
            return value;
        }
    }

    private static VarHandle evaluatedSupplierHandle() {
        try {
            return MethodHandles.lookup()
                    .findVarHandle(LazyStableDelegator.class, "delegator", Supplier.class);
            // .withInvokeExactBehavior(); // Make sure no boxing is made?
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

}
