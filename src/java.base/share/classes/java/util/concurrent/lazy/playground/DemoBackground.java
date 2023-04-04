package java.util.concurrent.lazy.playground;

import java.util.concurrent.lazy.Lazy;
import java.util.concurrent.lazy.LazyReference;

/**
 * Demobackground: computing a value in the background
 */
public final class DemoBackground {

    private DemoBackground() {}

    /**
     * Main method
     *
     * @param args from command line
     * @throws InterruptedException if something happens...
     */
    public static void main(String[] args) throws InterruptedException {
        LazyReference<Foo> lazy = Lazy.<Foo>builder()
                .withSupplier(Foo::new)
                .withEarliestEvaluation(Lazy.Evaluation.CREATION_BACKGROUND)
                .build();

        Thread.sleep(1000);

        System.out.println("lazy.get() = " + lazy.get());
    }

    private static final class Foo {

        public Foo() {
            System.out.println("Constructor invoked by " + Thread.currentThread());
        }
    }

}
