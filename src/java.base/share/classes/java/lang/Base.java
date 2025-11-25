package java.lang;

import jdk.internal.misc.Unsafe;

/** To be removed */
public abstract class Base {

    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    Base() {}

    /** main method
     * @throws InterruptedException e */
    public void main() throws InterruptedException {
        IO.println("Starting " + this.getClass().getSimpleName());
        int sum = 0;
        for (int i = 0; i < 10_000_000; i++) {
            sum += payload();
        }
        Thread.sleep(1_000);
        IO.println("The sum is " + String.format("%,d", sum));
    }

    abstract int payload();

}
