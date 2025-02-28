package java.devoxx.i;

import java.devoxx.*;
import java.util.List;
import java.util.function.Supplier;

final class Application {
    private static final int POOL_SIZE = 16;

    static final List<OrderController>       ORDER_POOL = StableValue.ofList(POOL_SIZE, OrderController::new);
    static final Supplier<ProductRepository> PRODUCTS   = StableValue.ofSupplier(ProductRepository::new);
    static final Supplier<UserService>       USERS      = StableValue.ofSupplier(UserService::new);

    public static OrderController orders() {
        long index = Thread.currentThread().threadId() % POOL_SIZE;
        return ORDER_POOL.get((int) index);
    }

}