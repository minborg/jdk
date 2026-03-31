/*
 * @test /nodynamiccopyright/
 * @enablePreview
 * @summary Smoke test for cached methods with exceptions
 */
public class ExceptionTest {

    static int static_init_count = 0;
    static int instance_init_count = 0;

    record Test(int x) {

        cached static int m_s() {
            static_init_count++;
            if (static_init_count == 1) {
                throw new RuntimeException("static");
            }
            return 42;
        }

        cached int m_i() {
            instance_init_count++;
            if (instance_init_count == 1) {
                throw new RuntimeException("instance");
            }
            return x + 42;
        }
    }

    public static void main(String[] args) {
        assertThrows("static", () -> Test.m_s());
        assertEquals(42, Test.m_s());
        assertEquals(42, Test.m_s());

        Test test = new Test(10);

        assertThrows("instance", () -> test.m_i());
        assertEquals(52, test.m_i());
        assertEquals(52, test.m_i());

        assertEquals(2, static_init_count);
        assertEquals(2, instance_init_count);
    }

    interface ThrowingRunnable {
        void run();
    }

    static void assertThrows(String expectedMessage, ThrowingRunnable action) {
        try {
            action.run();
            throw new AssertionError("expected exception");
        } catch (RuntimeException ex) {
            if (!expectedMessage.equals(ex.getMessage())) {
                throw new AssertionError("expected " + expectedMessage + ", got " + ex.getMessage());
            }
        }
    }

    static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("expected " + expected + ", got " + actual);
        }
    }
}
