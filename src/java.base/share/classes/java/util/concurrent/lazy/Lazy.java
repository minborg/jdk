package java.util.concurrent.lazy;

/**
 * This interface provides common methods and classes for all
 * Lazy class variants.
 * <p>
 * Array variants do not implement the inteface but are using the
 * State class.
 */
public interface Lazy {

    /**
     * {@return The {@link State} of this Lazy}.
     * <p>
     * The value is a snapshot of the current State.
     * No attempt is made to compute a value if it is not already present.
     * <p>
     * If the returned State is either {@link State#PRESENT} or
     * {@link State#ERROR}, it is guaranteed the state will
     * never change in the future.
     * <p>
     * This method can be used to act on a value if it is present:
     * {@snippet lang = java:
     *     if (lazy.state() == State.PRESENT) {
     *         T value = lazy.get();
     *         // perform action on the value
     *     }
     *}
     */
    State state();

    /**
     * The State indicates the current state of a Lazy instance:
     * <ul>
     *     <li><a id="empty"><b>EMPTY</b></a>
     *     <p> No value is present (initial state).</p></li>
     *     <li><a id="constructing"><b>CONSTRUCTING</b></a>
     *     <p> A value is being constructed but the value is not yet available (transient state).</p></li>
     *     <li><a id="present"><b>PRESENT</b></a>
     *     <p> A value is present and is available via an accessor (final state).</p></li>
     *     <li><a id="error"><b>ERROR</b></a>
     *     <p> The construction of tha value failed and a value will never be present (final state).
     *     The error is available via an accessor for some implementations.</p></li>
     * </ul>
     */
    enum State {
        /**
         * Indicates a value is not present and is not about to be constructed.
         */
        EMPTY,  // ABSENT?
        /**
         * Indicates a value is being constructed but is not yet available.
         */
        CONSTRUCTING, // Todo: Consider dropping this state
        /**
         * Indicates a value is present. This is a <em>final state</em>.
         */
        PRESENT,
        /**
         * Indicates an error has occured during construction of the value. This is a <em>final state</em>.
         */
        ERROR;

        /**
         * {@return if this state is final (e.g. can never change)}.
         */
        static boolean isFinal(State state) {
            return state == PRESENT ||
                    state == ERROR;
        }
    }

}
