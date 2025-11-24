package java;

import jdk.internal.vm.annotation.Stable;

/** A */
public final class MainStableField {

    /** A */
    public MainStableField() {}

    @Stable
    private final int value = 1;

    void main() throws InterruptedException {
        int sum = 0;
        for (int i = 0; i < 1_000_000; i++) {
            sum += payload();
        }
        IO.println(sum);
        Thread.sleep(1_000);
        IO.println("Done");
    }

    int payload() {
        return value;
    }

}
