package java.javaone.c;


import java.javaone.*;
import java.util.List;

/* -1
  _____     ____
 /      \  |  o |
|        |/ ___\| x 2
|_________/
|_|_| |_|_|

*/
final class OrderControllerImpl implements OrderController {
    private Logger logger;

    synchronized Logger getLogger() {
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
