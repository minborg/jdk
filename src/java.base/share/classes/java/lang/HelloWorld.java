package java.lang;

import java.lang.foreign.Arena;

import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * H
 */
public class HelloWorld {

    /** C */
    public HelloWorld() {}


    /** m */
    static void main() {
        IO.println("HelloWorld");
        try (Arena a = Arena.ofConfined()) {
            a.allocate(JAVA_INT);
        }
    }

}
