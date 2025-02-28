package java.javaone.j;

import java.util.function.Supplier;

public interface Lazy<T> extends Supplier<T> {

    static <T> Lazy<T> of(Supplier<? extends T> original) {
        final Supplier<T> delegate = StableValue.supplier(original);
        return new Lazy<T>() {
            @Override
            public T get() {
                return delegate.get();
            }
        };
    }

}
