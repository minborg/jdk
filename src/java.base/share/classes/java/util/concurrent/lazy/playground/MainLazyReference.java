package java.util.concurrent.lazy.playground;

import jdk.internal.foreign.abi.aarch64.CallArranger;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.util.Objects;
import java.util.concurrent.lazy.Lazy;
import java.util.concurrent.lazy.LazyReference;
import java.util.function.Supplier;

/**
 * Test
 */
public final class MainLazyReference {

    private static final Bar FOO = new Bar();

    private static class Bar{};

    private static final Supplier<Integer> LAZY = Lazy.of(() -> 1);
    private final Supplier<Integer> lazy = Lazy.of(() -> 1);

    /**
     * A
     */
    public MainLazyReference() {
    }

    /**
     * A
     *
     * @param args a
     * @throws InterruptedException i
     */
    public static void main(String[] args) throws InterruptedException {

        // How LazyReference.get() is called
        var instance = MemoryLayout.structLayout(ValueLayout.ADDRESS.withName("instance"));
        var ret = MemoryLayout.structLayout(ValueLayout.ADDRESS.withName("value"));
        var funcDesc = FunctionDescriptor.of(ret, instance);
        var bindings = CallArranger.MACOS.getBindings(funcDesc.toMethodType(), funcDesc, true);
        System.out.println(bindings.callingSequence().asString());

        for (int i = 0; i < 5; i++) {
            System.out.println("stat()");
            System.out.println("stat() = " + stat());
            System.out.println("inst()");
            System.out.println("new MainLazyReference().inst() = " + new MainLazyReference().inst());
            System.out.println("arr()");
            System.out.println("new MainLazyReference().arr() = " + new MainLazyReference().arr());
            Thread.sleep(1000);
        }
    }

    private static int stat() {
        int sum = 0;
        for (int i = 0; i < 2_000_000; i++) { // 0x1E8480
            sum += getLazy();
        }
        return sum;
    }

    private static int getLazy() {
        return LAZY.get();
    }

    private int inst() {
        int sum = 0;
        for (int i = 0; i < 2_000_000; i++) { // 0x1E8480
            sum += lazy.get();
        }
        return sum;
    }

    private int arr() {
        int sum = 0;
        for (int i = 0; i < 2_000_000; i++) { // 0x1E8480
            sum += Objects.checkIndex(i, 2_000_000);
        }
        return sum;
    }


}
