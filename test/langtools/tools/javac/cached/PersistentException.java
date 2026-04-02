import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/*
 * @test /nodynamiccopyright/
 * @enablePreview
 * @summary Test of persistent exception handling with cached methods
 */
public final class PersistentException {

    public interface Either<L, R> {

        Optional<L> left();

        Optional<R> right();

        public static <L, R> Either<L, R> ofLeft(L left) {
            return new Either<L, R>() {
                @Override
                public Optional<L> left() {
                    return Optional.of(left);
                }

                @Override
                public Optional<R> right() {
                    return Optional.empty();
                }
            };
        }

        public static <L, R> Either<L, R> ofRight(R right) {
            return new Either<L, R>() {
                @Override
                public Optional<L> left() {
                    return Optional.empty();
                }

                @Override
                public Optional<R> right() {
                    return Optional.of(right);
                }
            };
        }

    }

    static int yey_count = 0;
    static int ney_count = 0;

    public static void main(String[] args) {
        checkYey();
        checkYey();
        checkNey();
        checkNey();
    }

    static void checkYey() {
        var yey = yey();
        assertEquals(42, yey.left().orElseThrow());
        assertTrue(yey.right().isEmpty());
        assertEquals(1, yey_count);
    }

    static void checkNey() {
        var ney = ney();
        assertTrue(ney.left().isEmpty());
        assertEquals(ArithmeticException.class, ney.right().orElseThrow().getClass());
        assertEquals(1, ney_count);
    }

    static cached Either<Integer, Throwable> yey() {
        yey_count++;
        return PersistentException.Either.ofLeft(42);
    }

    static cached Either<Integer, Throwable> ney() {
        ney_count++;
        try {
            // Fails
            return Either.ofLeft(10 / 0);
        } catch (Throwable t) {
            return Either.ofRight(t);
        }
    }

    static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected " + expected + ", got " + actual);
        }
    }

    static void assertTrue(boolean actual) {
        if (!actual) {
            throw new AssertionError("expected true, got " + actual);
        }
    }

}
