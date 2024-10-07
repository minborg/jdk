package java.devoxx.g;

import java.devoxx.ProductRepository;
import java.devoxx.UserService;

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
        return ORDERS.computeIfUnset(OrderController::new);
    }

    public static ProductRepository products() {
        return PRODUCTS.computeIfUnset(ProductRepository::new);
    }

    public static UserService users() {
        return USERS.computeIfUnset(UserService::new);
    }
}
