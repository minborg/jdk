package java.javaone.b;

import java.javaone.*;
import java.util.List;

/* -1, -8
  _____     ____
 /      \  |  o |
|        |/ ___\|
|_________/
|_|_| |_|_|

*/
final class OrderControllerImpl implements OrderController {

    // No constant folding
    private Logger logger;

    Logger getLogger() {
        if (logger == null) {
            logger = Logger.create(OrderControllerImpl.class);
        }
        return logger;
    }

    @Override
    public void submitOrder(User user, List<Product> products) {
        getLogger().info("order started");
        // ...
        getLogger().info("order submitted");
    }
}

