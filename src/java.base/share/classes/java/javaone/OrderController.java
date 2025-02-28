package java.javaone;

import java.util.List;

/** An order controller. */
public interface OrderController {
    /**...
     *
     * @param user who places the order
     * @param products to order
     *
     * */
    void submitOrder(User user, List<Product> products);
}
