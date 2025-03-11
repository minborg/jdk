package java.javaone.f;

import java.javaone.*;
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

  _____     ____
 /      \  |  o |
|        |/ ___\|
|_________/
|_|_| |_|_|
 */

final class OrderControllerImpl implements OrderController {

    private final Map<Class<?>, Logger> logger = new ConcurrentHashMap<>();;

    public Logger getLogger() {
        return logger.computeIfAbsent(OrderControllerImpl.class, Logger::create);
    }

    @Override
    public void submitOrder(User user, List<Product> products) {
        getLogger().info("order started");
        // ...
        getLogger().info("order submitted");
    }

}