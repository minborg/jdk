package java.devoxx.f;

import java.devoxx.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/* -1
     _.-- ,.--.
   .'   .'    /
   | @       |'..--------._
  /      \._/              '.
 /  .-.-                     \
(  /    \                     \
 \\      '.                  | #
  \\       \   -.           /
   :\       |    )._____.'   \
    "       |   /  \  |  \    )
            |   |./'  :__ \.-'
            '--'
 */

final class OrderController {

    private final Map<Class<?>, Logger> logger = new ConcurrentHashMap<>();;

    public Logger getLogger() {
        return logger.computeIfAbsent(OrderController.class, Logger::create);
    }

    void submitOrder(User user, List<Product> products) {
        getLogger().info("order started");

        getLogger().info("order submitted");
    }

}