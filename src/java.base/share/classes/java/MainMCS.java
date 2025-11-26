package java;

import jdk.internal.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;

/**
 * A
 */
public final class MainMCS {

    /**
     * A
     */
    public MainMCS() {
    }

    private static final Unsafe U = Unsafe.getUnsafe();
    private static final long FIELD_OFFSET = U.objectFieldOffset(MainMCS.Holder.class, "value");

    private static int extractValueStable(Holder holder) {
        return U.getIntStable(holder, FIELD_OFFSET);
    }

    static final Holder HOLDER = new Holder(1);

    private static final MethodType METHOD_TYPE = MethodType.methodType(Holder.class);
    private static final MutableCallSite MUTABLE_CALL_SITE = new MutableCallSite(METHOD_TYPE);
    private static final MethodHandle DYNAMIC_INVOKER = MUTABLE_CALL_SITE.dynamicInvoker();

    static final class Holder {
        int value;

        public Holder(int value) {
            this.value = value;
        }
    }

    private static final MethodHandle MCS_INVOKER;
/*    private static final MethodHandle DIRECT_INVOKER;*/

    static {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            MethodHandle valueExtractor = lookup.findStatic(MainMCS.class, "extractValueStable", MethodType.methodType(int.class, Holder.class));
            MCS_INVOKER = MethodHandles.filterReturnValue(DYNAMIC_INVOKER, valueExtractor);

/*
            MethodHandle staticGetter = lookup.findStaticGetter(MainMCS.class, "HOLDER", Holder.class);
            DIRECT_INVOKER = MethodHandles.filterReturnValue(staticGetter, valueExtractor);
*/

        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }


    void main() throws InterruptedException {
        MUTABLE_CALL_SITE.setTarget(MethodHandles.constant(Holder.class, HOLDER));
        int sum = 0;
        for (int i = 0; i < 1_000_000; i++) {
            sum += payload();
        }
        IO.println(sum);
        Thread.sleep(1_000);
        IO.println("Done");
    }

    int payload() {
        try {
             return (int) MCS_INVOKER.invokeExact();
            // return (int) DIRECT_INVOKER.invokeExact();
            // return U.getIntStable(HOLDER, FIELD_OFFSET);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

    }

}
