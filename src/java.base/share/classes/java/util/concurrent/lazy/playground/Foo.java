package java.util.concurrent.lazy.playground;

/**
 * Foo
 */
public class Foo {

    /**
     * Foo
     */
    public Foo() {
    }

    static class Holder_0 {
        private static final Bar LAZY = new Bar();
    }

    /**
     * {@return the Bar}.
     */
    public Bar get() {
        return Holder_0.LAZY;
    }

}
