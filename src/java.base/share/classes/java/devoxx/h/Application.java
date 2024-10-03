package java.devoxx.h;

import java.devoxx.*;
import java.util.List;
import java.util.function.Supplier;

// +1

/**
 * Test app
 */
public final class Application {
    static final Supplier<OrderController>   ORDERS   = StableValue.ofSupplier(OrderController::new);
    static final Supplier<ProductRepository> PRODUCTS = StableValue.ofSupplier(ProductRepository::new);
    static final Supplier<UserService>       USERS    = StableValue.ofSupplier(UserService::new);

    /** Ctor */
    public Application() {}

    /**
     * Demo app.
     * @param args ignored
     */
    public static void main(String[] args) {
        ORDERS.get().submitOrder(new User(), List.of(new Product()));
    }

}

