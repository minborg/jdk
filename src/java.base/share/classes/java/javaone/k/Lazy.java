package java.javaone.k;

import java.util.function.Supplier;

@FunctionalInterface
interface Lazy<T> extends Supplier<T> {

    static <T> Lazy<T> of(Supplier<? extends T> original) {
        return StableValue.supplier(original)::get;
    }

}
