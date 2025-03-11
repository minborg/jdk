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
final class OrderControllerImpl implements OrderController {

    public static Logger getLogger() {

        final class Holder {
            private static final Logger LOGGER = Logger.create(OrderControllerImpl.class);
        }

        return Holder.LOGGER;
    }

    @Override
    public void submitOrder(User user, List<Product> products) {
        getLogger().info("order started");
        // ...
        getLogger().info("order submitted");
    }
}
