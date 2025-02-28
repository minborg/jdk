package java.javaone.d;

import java.javaone.*;
import java.util.List;

/* -1
  _____     ____
 /      \  |  o |
|        |/ ___\|
|_________/
|_|_| |_|_|

*/
final class OrderController {

    private volatile Logger logger;

    public Logger getLogger() {
        Logger v = logger;
        if (v == null) {
            synchronized (this) {
                v = logger;
                if (v == null) {
                    logger = v = Logger.create(OrderController.class);
                }
            }
        }
        return v;
    }

    void submitOrder(User user, List<Product> products) {
        getLogger().info("order started");

        getLogger().info("order submitted");
    }

}