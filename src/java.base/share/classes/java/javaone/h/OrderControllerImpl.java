package java.javaone.h;


import java.javaone.*;
import java.util.List;
import java.util.function.Supplier;

// +1
final class OrderController {

    private final Supplier<Logger> logger =
            StableValue.supplier( () -> Logger.create(OrderController.class) );

    void submitOrder(User user, List<Product> products) {
        logger.get().info("order started");

        logger.get().info("order submitted");
    }
}
