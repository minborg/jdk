package java.devoxx.a;

import java.devoxx.*;


/*
  _____     ____
 /      \  |  o |
|        |/ ___\|  x 6
|_________/
|_|_| |_|_|

*/

final class Application {
        static final OrderController   ORDERS = new OrderController();
        static final ProductRepository PRODUCTS = new ProductRepository();
        static final UserService       USERS    = new UserService();
}
