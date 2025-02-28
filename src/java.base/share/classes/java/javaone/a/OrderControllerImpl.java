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
final class OrderController {

    // Reads configuration, creates storage
    private final Logger logger = Logger.create(OrderController.class);

    void submitOrder(User user, List<Product> products) {
        logger.info("order started");

        logger.info("order submitted");
    }

}
