package java.javaone.a;

import java.javaone.*;
import java.util.List;

/*
  _____     ____
 /      \  |  o |
|        |/ ___\|
|_________/
|_|_| |_|_|

*/
final class OrderControllerImpl implements OrderController {

    // Reads configuration, creates storage
    private final Logger logger = Logger.create(OrderControllerImpl.class);

    @Override
    public void submitOrder(User user, List<Product> products) {
        logger.info("order started");
        // ...
        logger.info("order submitted");
    }

}
