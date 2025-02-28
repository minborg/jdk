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
final class OrderController {
    private Logger logger;

    synchronized Logger getLogger() {
        if (logger == null) {
            logger = Logger.create(OrderController.class);
        }
        return logger;
    }

    void submitOrder(User user, List<Product> products) {
        getLogger().info("order started");

        getLogger().info("order submitted");
    }
}
