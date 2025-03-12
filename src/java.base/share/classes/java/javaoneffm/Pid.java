package java.javaoneffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.io.IO.*;

/** ... */
public class Pid {

    /** ... */
    public Pid() {}

    /** ... */
    void main() {

        // Link
        Linker linker = Linker.nativeLinker();
        MemorySegment symbol = linker.defaultLookup().findOrThrow("getpid");
        @SuppressWarnings("restricted") // Outside Java's safetynet
        MethodHandle getPid = linker.downcallHandle(symbol, FunctionDescriptor.of(JAVA_INT));
        println(getPid); // MethodHandle()int

        // Invoke
        try {
            int pid = (int) getPid.invokeExact();
            println("pid = " + pid);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

}
