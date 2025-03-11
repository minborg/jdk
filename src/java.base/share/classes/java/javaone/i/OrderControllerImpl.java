package java.javaone.i;


import java.javaone.Logger;
import java.javaone.OrderController;
import java.javaone.Product;
import java.javaone.User;
import java.util.List;
import java.util.function.Supplier;

// +1
final class OrderControllerImpl implements OrderController {

    private final int id;
    private final Supplier<Logger> logger =
            StableValue.supplier( () -> Logger.create(OrderControllerImpl.class) );

    public OrderControllerImpl(int id) {
        this.id = id;
    }

    @Override
    public void submitOrder(User user, List<Product> products) {
        logger.get().info("order started via " + id);
        // ...
        logger.get().info("order submitted via " + id);
    }
}
