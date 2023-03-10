package java.util.concurrent.lazy;

/**
 * Test
 */
public class MainCondyLong {

    private static final CondyLong CONDY_LONG = new CondyLong(() -> 42);
    private static final CondyLong CONDY_LONG2 = CondyLong.of(() -> 42);

    private static final long CONDY_VALUE = new CondyLong(() -> 42).getAsLong();

    /**
     * A
     */
    public MainCondyLong() {
    }

    /**
     * A
     * @param args a
     */
    public static void main(String[] args) {
        CondyLong condyLong = CondyLong.of(() -> 42);
        System.out.println(condyLong);
        System.out.println(CONDY_LONG);
        System.out.println(CONDY_LONG2);
        System.out.println(CONDY_VALUE);
    }

}
