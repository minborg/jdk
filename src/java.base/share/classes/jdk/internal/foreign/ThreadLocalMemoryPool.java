package jdk.internal.foreign;

import jdk.internal.ValueBased;
import jdk.internal.misc.CarrierThread;
import jdk.internal.misc.CarrierThreadLocal;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryPool;

@ValueBased
public final class ThreadLocalMemoryPool implements MemoryPool {

    private final CarrierThreadLocal<UnboundMemoryPool> tl;

    private ThreadLocalMemoryPool() {
        this.tl = new CarrierThreadLocal<>() {
            @Override
            protected UnboundMemoryPool initialValue() {
                // If we are a virtual thread, we need concurrency as the virtual thread
                // can be unmounted from the carrier thread at any time.
                //final boolean concurrent = JLA.currentCarrierThread() instanceof CarrierThread;
                final boolean concurrent = CarrierThread.currentThread().isVirtual();
                return UnboundMemoryPool.of(concurrent
                        ? UnboundMemoryPool.FifoType.CONCURRENT
                        : UnboundMemoryPool.FifoType.THREAD_LOCAL);
            }
        };
    }

    @ForceInline
    @Override
    public Arena get() {
        return tl.get()
                .get();
    }

    public static MemoryPool of() {
        return new ThreadLocalMemoryPool();
    }

}
