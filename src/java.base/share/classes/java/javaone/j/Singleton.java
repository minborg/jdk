package java.javaone.j;

import java.util.function.IntFunction;
import java.util.function.Supplier;

/** Singleton */
public final class Singleton {

    /**...*/
    public Singleton() {}

    /** This is our custom class we want to guard */
    public static class MySingleton{
        /**...*/
        public MySingleton() {}

        // Logic
    }

    private static final Supplier<MySingleton> SINGLETON_SUPPLIER =
            StableValue.supplier(MySingleton::new);

    /**
     * {@return the one and only}
     */
    public static MySingleton getInstance() {
        return SINGLETON_SUPPLIER.get();
    }

    private static final IntFunction<MySingleton> TRINGLETON_SUPPLIER =
            StableValue.intFunction(3, _ -> new MySingleton());

    /**
     * {@return the one of the three "tringletons"}
     * @param index for selecting element
     * @throws IllegalArgumentException if index not in [0, 2]
     */
    public static MySingleton getInstance(int index) {
        return TRINGLETON_SUPPLIER.apply(index);
    }

}
