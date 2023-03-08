package java.util.concurrent.lazy;

import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

abstract class AbstractLazy<P> implements Lazy {

    // Maintain a separate private copy of the array.
    private static final State[] STATES = State.values();

    private P presetProvider;

    @Stable
    private int state;

    // We are using a separate flag for "constructing" as we want
    // "state" to be @Stable and only make at most one change to "state".
    private boolean constructing;

    protected AbstractLazy(P presetProvider) {
        this.presetProvider = presetProvider;
    }

    private static final VarHandle STATE_VH;

    static {
        try {
            STATE_VH = MethodHandles.lookup()
                    .findVarHandle(AbstractLazy.class, "state", int.class);
            // .withInvokeExactBehavior(); // Improve performance?
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public State state() {
        State state = STATES[stateValuePlain()];
        if (state.isFinal()) {
            return state;
        }
        synchronized (this) {
            int stateValuePlain = stateValuePlain();
            if (constructing && stateValuePlain == State.EMPTY.ordinal()) {
                return State.CONSTRUCTING;
            }
            return STATES[stateValuePlain];
        }
    }

    @Override
    public final String toString() {
        return getClass().getSimpleName() + "[" + switch (state()) {
            case EMPTY -> State.EMPTY;
            case CONSTRUCTING -> State.CONSTRUCTING;
            case PRESENT -> renderValue();
            case ERROR -> renderError();
        } + "]";
    }

    protected abstract String renderValue();

    protected String renderError() {
        return State.ERROR.toString();
    }

    void constructing(boolean constructing) {
        this.constructing = constructing;
    }

    protected boolean isPlain(State state) {
        return stateValuePlain() == state.ordinal();
    }

    protected boolean is(State state) {
        if (state.isFinal() && isPlain(state)) {
            return true;
        }
        synchronized (this) {
            return isPlain(state);
        }
    }

    // Faster than isPlain(State.Present)
    protected boolean isPresentPlain() {
        return stateValuePlain() == State.PRESENT.ordinal();
    }

    protected boolean isPresent() {
        if (isPresentPlain()) {
            return true;
        }
        synchronized (this) {
            return isPresentPlain();
        }

    }

    protected int stateValuePlain() {
        return (int) STATE_VH.get(this);
    }

    protected void stateValueRelease(State newState) {
        STATE_VH.setRelease(this, newState.ordinal());
    }

    protected void forgetPresetProvided() {
        // Stops preventing the provider from being collected once it has been
        // used (if initially set).
        this.presetProvider = null;
    }

    protected P presetProvider() {
        return presetProvider;
    }

}
