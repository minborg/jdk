package java.javaone.a;

import java.javaone.*;
import java.javaone.repo.ProductRepositoryImpl;
import java.javaone.repo.UserServiceImpl;


/*
  _____     ____
 /      \  |  o |
|        |/ ___\|  x 6
|_________/
|_|_| |_|_|

*/

final class Application {
        static final OrderController   ORDERS   = new OrderControllerImpl();
        static final ProductRepository PRODUCTS = new ProductRepositoryImpl();
        static final UserService       USERS    = new UserServiceImpl();
}
