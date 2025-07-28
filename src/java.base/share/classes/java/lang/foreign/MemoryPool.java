package java.lang.foreign;

import jdk.internal.foreign.UnboundMemoryPool;

/**
 * A memory pool ...
 *
 * @implNote All implementations of the MemoryPool interface are thread safe.
 * Todo: Properties
 * // confined or shared *use* (e.g. even though memory is always confined, #threads using)
 * // Bound or unbound
 * // Global or per-thread
 */
public sealed interface MemoryPool
        permits UnboundMemoryPool {

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
     * arenas emanating from the pool have been closed.
     */
    static MemoryPool ofConcurrentUnbound() {
        return new UnboundMemoryPool(true);
    }

    /**
     * {@return a new unbound memory pool that can only be used by a single thread}
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
     * arenas emanating from the pool have been closed.
     */
    static MemoryPool ofUnbound() {
        return new UnboundMemoryPool(false);
    }

}
