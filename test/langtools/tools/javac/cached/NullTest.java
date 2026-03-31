/*
 * @test /nodynamiccopyright/
 * @enablePreview
 * @summary Smoke test for cached methods returning null
 */
public class NullTest {

    static int static_init_count = 0;
    static int instance_init_count = 0;

    record Test(int x) {

        cached static String m_s() {
            static_init_count++;
            return null;
        }

        cached String m_i() {
            instance_init_count++;
            return null;
        }
    }

    public static void main(String[] args) {
        assertNull(Test.m_s());
        assertNull(Test.m_s());

        Test first = new Test(10);
        Test second = new Test(20);

        assertNull(first.m_i());
        assertNull(first.m_i());
        assertNull(second.m_i());
        assertNull(second.m_i());

        assertEquals(1, static_init_count);
        assertEquals(2, instance_init_count);
    }

    static void assertNull(Object value) {
        if (value != null) {
            throw new AssertionError("expected null, got " + value);
        }
    }

    static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("expected " + expected + ", got " + actual);
        }
    }
}
