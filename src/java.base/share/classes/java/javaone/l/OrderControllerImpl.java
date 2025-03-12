package java.javaone.l;


import java.javaone.Logger;
import java.javaone.OrderController;
import java.javaone.Product;
import java.javaone.User;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** JDK 25
 * @param logger to use
 * */
public record OrderControllerImpl(Supplier<Logger> logger)
        implements OrderController {

    @Override
    public void submitOrder(User user, List<Product> products) {
        logger.get().info("order started");
        // ...
        logger.get().info("order submitted");
    }

    /** {@return a new OrderController} */
    public static OrderController create() {
        return new OrderControllerImpl(
                () -> Logger.create(OrderControllerImpl.class));
    }

}
