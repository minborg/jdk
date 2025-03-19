package java.javaoneffm;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.util.function.IntSupplier;

import static java.io.IO.println;
import static java.lang.foreign.ValueLayout.*;

/** ... */
public class Dispatcher {

    /** ... */
    public Dispatcher() {}

    /** ... */
    @FunctionalInterface
    public interface StrLen {

        /**
         * {@return the length of the C string in the provided {@code segment}}
         * @param segment where the C string is located
         */
        long strlen(MemorySegment segment);
    }

    interface SystemCallDispatcher {
        int pid();
        long strlen(MemorySegment segment);
    }

    record SystemCallDispatcherImpl(IntSupplier pidFunction,
                                    StrLen strlenFunction) implements SystemCallDispatcher {
        @Override
        public int pid() {
            return pidFunction.getAsInt();
        }

        @Override
        public long strlen(MemorySegment segment) {
            return strlenFunction.strlen(segment);
        }
    }

    // With the Stable Value API, the binding could be lazy.
    static final SystemCallDispatcher SYSTEM_CALL_DISPATCHER = new SystemCallDispatcherImpl(
            LinkerUtil.link(IntSupplier.class, "getpid", FunctionDescriptor.of(JAVA_INT)),
            LinkerUtil.link(StrLen.class, "strlen", FunctionDescriptor.of(JAVA_LONG, ADDRESS)));


    /** ... */
    void main() {
        int pid = SYSTEM_CALL_DISPATCHER.pid();
        println("pid = " + pid);

        String text = "JavaOne!";
        MemorySegment cText = Arena.ofAuto().allocateFrom(text);
        long len = SYSTEM_CALL_DISPATCHER.strlen(cText);
        println("The length of the text '" + text + "' is " + len);
    }

}
