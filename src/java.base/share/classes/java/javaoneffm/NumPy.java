package java.javaoneffm;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.invoke.VarHandle;

import static java.io.IO.println;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;

/** ... */
public class NumPy {

    /** ... */
    public NumPy() {}

    static final MemoryLayout FLOAT_MATRIX_3x4 = MemoryLayout.sequenceLayout(
            3,
            MemoryLayout.sequenceLayout(4, JAVA_FLOAT)
    );

    void main() {
        println("FLOAT_MATRIX_3x4.byteSize() = "
                + FLOAT_MATRIX_3x4.byteSize()); // 3 * 4 * 4 = 48

        VarHandle elementHandle = FLOAT_MATRIX_3x4.varHandle(
                MemoryLayout.PathElement.sequenceElement(),
                MemoryLayout.PathElement.sequenceElement());

        println("elementHandle = " + elementHandle); // (MemorySegment, long, long, long)float

        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocate(FLOAT_MATRIX_3x4);
            elementHandle.set(segment, 0, 2, 3, 42);
            float e = (float)elementHandle.get(segment, 0, 2, 3);
            println("e = " + e); // e = 42.0
            // Throws java.lang.IndexOutOfBoundsException: Index 100 out of bounds for length 3
            elementHandle.set(segment, 0, 100, 1, 13);
        }

    }

}
