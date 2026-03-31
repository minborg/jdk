/*
 * @test /nodynamiccopyright/
 * @enablePreview
 * @summary Smoke test for static and instance cached methods
 */
public class SmokeTest {

    static int static_init_count = 0;
    static int instance_init_count = 0;

    record Test(int x) {

        cached static int m_s() {
            static_init_count++;
            return 42;
        }

        cached int m_i() {
            instance_init_count++;
            return x + 42;
        }
    }

    public static void main(String[] args) {
        assertEquals(42, Test.m_s());
        assertEquals(42, Test.m_s());

        Test first = new Test(10);
        Test second = new Test(20);

        assertEquals(52, first.m_i());
        assertEquals(52, first.m_i());
        assertEquals(62, second.m_i());
        assertEquals(62, second.m_i());

        assertEquals(1, static_init_count);
        assertEquals(2, instance_init_count);
    }

    static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("expected " + expected + ", got " + actual);
        }
    }
}
