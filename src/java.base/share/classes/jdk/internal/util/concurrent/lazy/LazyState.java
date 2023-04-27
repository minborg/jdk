package jdk.internal.util.concurrent.lazy;

import java.util.concurrent.lazy.LazyArray;
import java.util.concurrent.lazy.LazyValue;

/**
 * The State indicates the current state of a Lazy instance.
 * <p>
 * The following values are supported:
 * <ul>
 *     <li><a id="empty"><b>{@link LazyState#UNBOUND}</b></a>
 *     <p> No value is bound and is not being constructed (initial non-final state).</p></li>
 *     <li><a id="constructing"><b>{@link LazyState#CONSTRUCTING}</b></a>
 *     <p> A value is being constructed but the value is not yet bound (transient state).</p></li>
 *     <li><a id="present"><b>{@link LazyState#BOUND}</b></a>
 *     <p> A value is present and is available via an accessor (final state).</p></li>
 * </ul>
 */
public enum LazyState {

    /**
     * Indicates no value is bound and is not being constructed.
     * This is the initial <em>transient state</em>.
     */
    UNBOUND,

    /**
     * Indicates a value is being constructed but is not yet bound.
     * This is a <em>transient state</em>.
     */
    CONSTRUCTING,

    /**
     * Indicates a value is bound.
     * This is a <em>final state</em>.
     */
    BOUND;

    /**
     * {@return if this state is final (e.g. can no longer change)}.
     */
    static boolean isFinal(LazyState state) {
        return state == BOUND;
    }
}
