package java.util.concurrent.lazy;

/**
 * The State indicates the current state of a Lazy instance.
 * <p>
 * The following values are supported:
 * <ul>
 *     <li><a id="empty"><b>{@link LazyState#EMPTY}</b></a>
 *     <p> No value is present (initial non-final state).</p></li>
 *     <li><a id="constructing"><b>{@link LazyState#CONSTRUCTING}</b></a>
 *     <p> A value is being constructed but the value is not yet available (transient state).</p></li>
 *     <li><a id="present"><b>{@link LazyState#PRESENT}</b></a>
 *     <p> A value is present and is available via an accessor (final state).</p></li>
 *     <li><a id="error"><b>{@link LazyState#ERROR}</b></a>
 *     <p> The construction of tha value failed and a value will never be present (final state).
 *     The error is available via either the {@link Lazy#exception()} or
 *     the {@link LazyArray#exception(int)} accessor.</p></li>
 * </ul>
 */
public enum LazyState {
    /**
     * Indicates a value is not present and is not about to be constructed.
     */
    EMPTY,  // ABSENT?
    /**
     * Indicates a value is being constructed but is not yet available.
     */
    CONSTRUCTING,
    /**
     * Indicates a value is present. This is a <em>final state</em>.
     */
    PRESENT,
    /**
     * Indicates an error has occured during construction of the value. This is a <em>final state</em>.
     */
    ERROR;

    /**
     * {@return if this state is final (e.g. can no longer change)}.
     */
    static boolean isFinal(LazyState state) {
        return state == PRESENT ||
                state == ERROR;
    }
}
