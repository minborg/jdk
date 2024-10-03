package java.devoxx.i;


import java.devoxx.Logger;
import java.devoxx.Product;
import java.devoxx.User;
import java.util.List;
import java.util.function.Supplier;

// +1
final class OrderController {

    private final int id;
    private final Supplier<Logger> logger =
            StableValue.ofSupplier( () -> Logger.create(OrderController.class) );

    public OrderController(int id) {
        this.id = id;
    }

    void submitOrder(User user, List<Product> products) {
        logger.get().info("order started via " + id);

        logger.get().info("order submitted via " + id);
    }
}
