package java.javaoneffm;

import java.lang.foreign.FunctionDescriptor;
import java.util.function.IntSupplier;

import static java.io.IO.println;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/** ... */
public class Pid2 {

    /** ... */
    public Pid2() {}

    /** ... */
    void main() {
        IntSupplier pid = LinkerUtil.link(IntSupplier.class, "getpid", FunctionDescriptor.of(JAVA_INT));
        println("pid = " + pid.getAsInt());
    }

}
