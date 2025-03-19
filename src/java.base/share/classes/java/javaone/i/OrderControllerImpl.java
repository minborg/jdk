package java.javaone.i;


import java.javaone.Logger;
import java.javaone.OrderController;
import java.javaone.Product;
import java.javaone.User;
import java.util.List;
import java.util.function.Supplier;

// +1
final class OrderControllerImpl implements OrderController {

    private final Supplier<Logger> logger =
            StableValue.supplier( () -> Logger.create(OrderControllerImpl.class) );

    public OrderControllerImpl() {}

    @Override
    public void submitOrder(User user, List<Product> products) {
        logger.get().info("order started");
        // ...
        logger.get().info("order submitted");
    }
}
