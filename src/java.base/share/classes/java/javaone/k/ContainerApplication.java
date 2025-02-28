package java.javaone.k;

import java.javaone.Product;
import java.javaone.ProductRepository;
import java.javaone.User;
import java.javaone.UserService;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

// +1

/**
 * Test app
 */
public final class Application {

    record Provider<T>(Class<T> componentType, Supplier<? extends T> factory) {}

    static final Container COMPONENTS = Container.of(
            new Provider<>(OrderController.class, OrderController::new),
            new Provider<>(ProductRepository.class, ProductRepository::new),
            new Provider<>(UserService.class, UserService::new)
    );

    /**...*/
    public Application() {}

    /**
     * Demo app.
     * @param args ignored
     */
    public static void main(String[] args) {
        COMPONENTS.get(OrderController.class) // Constant folded by the JIT
                .submitOrder(new User(), List.of(new Product()));
    }

    @FunctionalInterface
    interface Container {

        <T> T get(Class<T> type);

        static Container of(Provider<?>... providers) {
           var map = Arrays.stream(providers)
                   .collect(Collectors.toUnmodifiableMap(
                           Provider::componentType,
                           p -> StableValue.supplier(p.factory())));
           return new Container() {
               @Override
               public <T> T get(Class<T> type) {
                   return type.cast(
                           map.get(type)    // Supplier<T>
                                   .get()); // T
               }
           };
        }

    }

}

