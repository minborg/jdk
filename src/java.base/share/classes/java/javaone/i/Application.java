package java.javaone.i;

import java.javaone.*;
import java.javaone.repo.*;
import java.util.List;
import java.util.function.Supplier;

final class Application {
    private static final int POOL_SIZE = 16;

    static final List<OrderController>       ORDER_POOL =
            StableValue.list(POOL_SIZE,_ -> new OrderControllerImpl());

    static final Supplier<ProductRepository> PRODUCTS   =
            StableValue.supplier(ProductRepositoryImpl::new);

    static final Supplier<UserService>       USERS      =
            StableValue.supplier(UserServiceImpl::new);

    public static OrderController orders() {
        long index = Thread.currentThread().threadId() % POOL_SIZE;
        return ORDER_POOL.get((int) index);
    }

}