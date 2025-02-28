package java.javaone.e;

import java.javaone.*;
import java.util.List;

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

    public static Logger getLogger() {

        final class Holder {
            private static final Logger LOGGER = Logger.create(OrderController.class);
        }

        return Holder.LOGGER;
    }

    void submitOrder(User user, List<Product> products) {
        getLogger().info("order started");

        getLogger().info("order submitted");
    }
}
