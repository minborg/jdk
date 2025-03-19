package java.javaone.h;

import java.javaone.*;
import java.javaone.repo.*;
import java.javaone.repo.UserImpl;
import java.util.List;
import java.util.function.Supplier;

// +1

/**
 * Test app
 */
public final class Application {
    static final Supplier<OrderController>   ORDERS   =
            StableValue.supplier(OrderControllerImpl::new);

    static final Supplier<ProductRepository> PRODUCTS =
            StableValue.supplier(ProductRepositoryImpl::new);

    static final Supplier<UserService>       USERS    =
            StableValue.supplier(UserServiceImpl::new);

    /**...*/
    public Application() {}

    /**
     * Demo app.
     * @param args ignored
     */
    public static void main(String[] args) {
        ORDERS.get().submitOrder(new UserImpl(), List.of(new ProductImpl()));
    }

}

