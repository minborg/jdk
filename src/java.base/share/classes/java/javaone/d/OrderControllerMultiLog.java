package java.javaone.d;

import java.javaone.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.stream.Stream;

/* -1
  _____     ____       .-.
 /      \  |  o |     (o o) boo!
|        |/ ___\|     | O \
|_________/            \   \
|_|_| |_|_|             `~~~'

*/
final class OrderControllerMultiLog {

    private static final VarHandle LOGGERS_HANDLE =
            MethodHandles.arrayElementVarHandle(Logger[].class);

    private final Object[] mutexes;
    private final Logger[] loggers;

    public OrderControllerMultiLog(int size) {
        this.mutexes = Stream.generate(Object::new).limit(size).toArray();
        this.loggers = new Logger[size];
    }

    public Logger getLogger(int index) {
        // Volatile semantics is needed here to guarantee we only
        // see fully initialized element objects
        Logger v = (Logger) LOGGERS_HANDLE.getVolatile(loggers, index);
        if (v == null) {
            // Use distinct mutex objects for each index
            synchronized (mutexes[index]) {
                // Plain read semantics suffice here as updates to an element
                // always takes place under the same mutex object as for this read
                v = loggers[index];
                if (v == null) {
                    // Volatile semantics needed here to establish a
                    // happens-before relation with future volatile reads
                    LOGGERS_HANDLE.setVolatile(loggers, index,
                            v = Logger.create(OrderControllerImpl.class));
                }
            }
        }
        return v;
    }

    void submitOrder(User user, List<Product> products) {
        getLogger(1).info("order started");
        // ...
        getLogger(1).info("order submitted");
    }

}