package java.lang.foreign;

import jdk.internal.foreign.BufferStack;
import jdk.internal.foreign.StackMemoryPool;
import jdk.internal.foreign.ThreadLocalMemoryPool;
import jdk.internal.foreign.UnboundMemoryPool;

/**
 * A memory pool ...
 *
 * @implNote All implementations of the MemoryPool interface are thread safe.
 * Todo: Properties
 * // confined or shared *use* (e.g. even though memory is always confined, #threads using)
 * // Bound or unbound
 * // Global or per-thread
 * // Zeroing or not
 */
public sealed interface MemoryPool
        permits BufferStack.StackedMemoryPool, StackMemoryPool, StackMemoryPool.PerPlatformThread, ThreadLocalMemoryPool, UnboundMemoryPool {

    /**
     * {@return a new {@linkplain Arena} that will try to use recycled memory in order
     *          to satisfy {@linkplain Arena#allocate(long, long) allocation} requests}
     * <p>
     * When the returned {@linkplain Arena} is closed, its underlying memory may
     * be recycled within the memory pool.
     * <p>
     * The returned Arena is {@linkplain Arena#ofConfined() confined} to the thread
     * that created it.
     */
    Arena get();

    /**
     * {@return a new bound memory pool of a certain {@code size} in byte
     *          that can be used by any thread}
     * <p>
     * The returned unbound memory pool will allocate memory on demand and will always
     * recycle any and all memory returned to the pool upon {@linkplain Arena#close()}.
     * <p>
     * Memory segments {@linkplain Arena#allocate(long, long) allocated} via
     * {@linkplain Arena arenas} obtained from the returned arena pool
     * are zero-initialized.
     * <p>
     * Recycled memory in the pool will be returned to the operating system,
     * automatically, by the garbage collector at the earliest once all
     * arenas emanating from the pool have been closed and the allocating thread has died.
     *
     * @param size the size in bytes the memory pool shall hold per thread
     * @throws IllegalArgumentException if the provided {@code size} is negative
     */
    static MemoryPool ofStack(long size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
/*        return BufferStack.StackedMemoryPool.of(size);*/
        return StackMemoryPool.of(size);
    }

    /**
     * {@return a new unbound memory pool that can only be used by the thread that
     *          created the memory pool}
     * <p>
     * The returned unbound memory pool will allocate memory on demand and will always
     * recycle any and all memory returned to the pool upon {@linkplain Arena#close()}.
     * <p>
     * Memory segments {@linkplain Arena#allocate(long, long) allocated} via
     * {@linkplain Arena arenas} obtained from the returned arena pool
     * are zero-initialized.
     * <p>
     * Recycled memory in the pool will be returned to the operating system,
     * automatically, by the garbage collector at the earliest once all
     * arenas emanating from the pool have been closed and the returned memory pool
     * is no longer not strongly reachable.
     */
    static MemoryPool ofUnbound() {
        return UnboundMemoryPool.of(UnboundMemoryPool.FifoType.NON_CONCURRENT);
    }

    /**
     * {@return a new unbound memory pool that can be used by any thread}
     * <p>
     * The returned unbound memory pool will allocate memory on demand and will always
     * recycle any and all memory returned to the pool upon {@linkplain Arena#close()}.
     * <p>
     * Memory segments {@linkplain Arena#allocate(long, long) allocated} via
     * {@linkplain Arena arenas} obtained from the returned arena pool
     * are zero-initialized.
     * <p>
     * Recycled memory in the pool will be returned to the operating system,
     * automatically, by the garbage collector at the earliest once all
     * arenas emanating from the pool have been closed and the returned memory pool
     * is no longer not strongly reachable.
     * <p>
     * This memory pool is memory efficient across threads but susceptible to
     * thread contention.
     */
    static MemoryPool ofConcurrentUnbound() {
        return UnboundMemoryPool.of(UnboundMemoryPool.FifoType.CONCURRENT);
    }

    /**
     * {@return a new unbound memory pool that can be used by any thread}
     * <p>
     * The returned unbound memory pool will allocate memory on demand and will always
     * recycle any and all memory returned to the pool upon {@linkplain Arena#close()}.
     * <p>
     * Memory segments {@linkplain Arena#allocate(long, long) allocated} via
     * {@linkplain Arena arenas} obtained from the returned arena pool
     * are zero-initialized.
     * <p>
     * Recycled memory in the pool will be returned to the operating system,
     * automatically, by the garbage collector at the earliest once all
     * arenas emanating from the pool have been closed and the allocating thread has died.
     * <p>
     * This memory pool is less memory efficient but totally immune to thread contention.
     */
    static MemoryPool ofThreadLocal() {
        return ThreadLocalMemoryPool.of();
    }

}
