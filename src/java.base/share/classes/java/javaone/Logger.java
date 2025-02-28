package java.devoxx;

import java.time.Instant;

/**
 * A simple logger.
 *
 * @param clazz   for which a logger is associated to
 * @param enabled iff true, outputs values
 */
public record Logger(Class<?> clazz, boolean enabled) {

    /**
     * Logs on `info` level
     * @param msg to output
     */
    public void info(String msg) {
        if (enabled) {
            System.out.println(Instant.now() + " INFO  " + clazz.getName() + "::" + msg);
        }
    }

    /**
     * Logs on `error` level
     * @param msg to output
     */
    public void error(String msg) {
        if (enabled) {
            System.err.println(Instant.now() + " ERROR " + clazz.getName() + "::" + msg);
        }
    }

    /**
     * {@return a new logger}
     * @param clazz to associate
     */
    public static Logger create(Class<?> clazz) {
        return new Logger(clazz, true);
    }

    /**
     * {@return a new logger}
     * @param clazz    to associate
     * @param enabled  if output is enabled
     */
    public static Logger create(Class<?> clazz, boolean enabled) {
        return new Logger(clazz, enabled);
    }

}
