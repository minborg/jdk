package java.javaone.g;

import java.javaone.Logger;
import java.javaone.Product;
import java.javaone.User;
import java.util.List;

// +1 -1
final class OrderController {
    // Old:    private final Logger logger = Logger.create(OrderController.class);
    // Old:    private Logger logger;
    private final StableValue<Logger> logger = StableValue.of();

    Logger getLogger() {
        return logger.orElseSet( () -> Logger.create(OrderController.class) );
    }

    void submitOrder(User user, List<Product> products) {
        getLogger().info("order started");

        getLogger().info("order submitted");
    }
}