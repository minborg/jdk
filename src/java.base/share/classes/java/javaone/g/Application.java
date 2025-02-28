package java.javaone.g;

import java.javaone.OrderController;
import java.javaone.ProductRepository;
import java.javaone.UserService;
import java.javaone.repo.ProductRepositoryImpl;
import java.javaone.repo.UserServiceImpl;

// -1 + 1
final class Application {
    /* Old
    static final OrderController   ORDERS   = new OrderController();
    static final ProductRepository PRODUCTS = new ProductRepository();
    static final UserService       USERS    = new UserService(); */

    /* New */
    static final StableValue<OrderController>   ORDERS   = StableValue.of();
    static final StableValue<ProductRepository> PRODUCTS = StableValue.of();
    static final StableValue<UserService>       USERS    = StableValue.of();

    public static OrderController orders() {
        return ORDERS.orElseSet(OrderControllerImpl::new);
    }

    public static ProductRepository products() {
        return PRODUCTS.orElseSet(ProductRepositoryImpl::new);
    }

    public static UserService users() {
        return USERS.orElseSet(UserServiceImpl::new);
    }
}
