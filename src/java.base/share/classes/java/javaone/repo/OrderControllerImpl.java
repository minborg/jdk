package java.javaone.repo;


import java.javaone.Logger;
import java.javaone.OrderController;
import java.javaone.Product;
import java.javaone.User;
import java.util.List;
import java.util.function.Supplier;

/**...*/
public final class OrderControllerImpl implements OrderController {

    /**...*/
    public OrderControllerImpl() {}

    private final Supplier<Logger> logger =
            StableValue.supplier( () -> Logger.create(OrderControllerImpl.class) );

    @Override
    public void submitOrder(User user, List<Product> products) {
        logger.get().info("order started");

        logger.get().info("order submitted");
    }
}
