package java.javaone.l;


import java.javaone.Logger;
import java.javaone.OrderController;
import java.javaone.Product;
import java.javaone.User;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

// Java 25
public record OrderControllerImpl(Supplier<Logger> logger)
        implements OrderController {

    @Override
    public void submitOrder(User user, List<Product> products) {
        logger.get().info("order started");
        // ...
        logger.get().info("order submitted");
    }

    public static OrderController create() {
        return new OrderControllerImpl(
                () -> Logger.create(OrderControllerImpl.class));
    }

}
