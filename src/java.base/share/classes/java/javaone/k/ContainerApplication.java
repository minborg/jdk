package java.javaone.k;

import java.javaone.*;
import java.javaone.repo.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

// +1

/**
 * Test app
 */
public final class ContainerApplication {

    record Provider<T>(Class<T> type, Supplier<? extends T> factory) {}

    static final StableContainer COMPONENTS = StableContainer.of(
            new Provider<>(OrderController.class, OrderControllerImpl::new),
            new Provider<>(ProductRepository.class, ProductRepositoryImpl::new),
            new Provider<>(UserService.class, UserServiceImpl::new)
    );

    // Could also be via ServiceLoader, reflection,
    // permitted class, dependency injection etc.
    // See SharedSecrets

    /**...*/
    public ContainerApplication() {}

    /**
     * Demo app.
     */
    void main() {
        // Eligible for constant folding by the JIT
        COMPONENTS.get(OrderController.class)
                // Devirtualizable
                .submitOrder(new UserImpl(), List.of(new ProductImpl()));
    }

    @FunctionalInterface
    interface StableContainer {

        <T> T get(Class<T> type);

        record StableContainerImpl(Map<Class<?>, Supplier<?>> delegate) implements StableContainer {
            @Override
            public <T> T get(Class<T> type) {
                return type.cast(
                        delegate.get(type) // Supplier<T>  -- Stable
                                .get());   // T
            }
        }

        static StableContainer of(Provider<?>... providers) {
            Map<Class<?>, Supplier<?>> map = Arrays.stream(providers)
                    .collect(Collectors.toUnmodifiableMap(
                            Provider::type,
                            p -> StableValue.supplier(p.factory())));

           return new StableContainerImpl(map);
        }

    }

}

