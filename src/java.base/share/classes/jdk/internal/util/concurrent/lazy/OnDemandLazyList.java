package jdk.internal.util.concurrent.lazy;

import jdk.internal.ValueBased;
import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.AbstractList;
import java.util.List;
import java.util.concurrent.lazy.LazyValue;
import java.util.function.IntFunction;

@ValueBased
public class OnDemandLazyList<V>
        extends AbstractList<LazyValue<V>>
        implements List<LazyValue<V>> {

    private static final VarHandle ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(LazyValue[].class);

    private final IntFunction<? extends V> provider;
    @Stable
    private LazyValue<V>[] values;

    @SuppressWarnings("unchecked")
    private OnDemandLazyList(int size, IntFunction<? extends V> provider) {
        this.provider = provider;
        this.values = (LazyValue<V>[]) new LazyValue<?>[size];
    }

    @Override
    public int size() {
        return values.length;
    }

    @SuppressWarnings("unchecked")
    @Override
    public LazyValue<V> get(int index) {
        LazyValue<V> v = values[index];
        if (v != null) {
            return v;
        }
        v = ListElementLazyValue.create(index, provider);
        if (!ARRAY_HANDLE.compareAndSet(values, index, null, v)) {
            v = (LazyValue<V>) ARRAY_HANDLE.getVolatile(values, index);
        }
        return v;
    }

    public static <V> List<LazyValue<V>> create(int size, IntFunction<? extends V> provider) {
        return new OnDemandLazyList<>(size, provider);
    }

}
