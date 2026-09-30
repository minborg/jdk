Hi Brian,

Following our observation that the bring-your-own-field case is sort of an outlier,
Maurizio, Jorn and I have been exploring whether it would fit better as a general
lazy updater, separate from the factories that own their storage.

A useful observation, from Maurizio, is that the computing function and the returned
lazy accessor have the same signature and can therefore be represented by the same
functional interface. We can accept an instance of a functional interface and return
another instance of that interface, adding the field access and lazy publication logic
around the computation.

The current API sketch in the branch is:
``` java
public static <T> T ofField(
    MethodHandles.Lookup lookup,
    Class<T> type,
    String fieldName,
    T computingFunction)
```

Here, `T` is the functional-interface type, rather than the type of the cached value. For example:

```java

final class Point {
    // Preserve the Point type argument in a concrete interface
    private interface ToDoubleFunctionPoint  extends ToDoubleFunction<Point> {}

    private static final ToDoubleFunctionPoint LAZY_RADIUS =
            Lazy.ofField(
                    MethodHandles.lookup(),
                    ToDoubleFunctionPoint.class,
                    "radius",
                    p -> Math.hypot(p.x, p.y));

    private static final ToDoubleFunctionPoint LAZY_PHI =
            Lazy.ofField(
                    MethodHandles.lookup(),
                    ToDoubleFunctionPoint.class,
                    "phi",
                    p -> Math.atan2(p.y, p.x));

    private final double x, y;
    private double radius, phi;

    Point(double x, double y) {
        this.x = x;
        this.y = y;
    }

    double radius() {
        return LAZY_RADIUS.applyAsDouble(this);
    }

    double phi() {
        return LAZY_PHI.applyAsDouble(this);
    }
}
```

The same interface describes both accessors; the field name and computation distinguish
them. The handlers are shared, while each receiver supplies its own storage. This
preserves the motivation for `ofField`: avoiding a mandatory lazy holder and a capturing
computation for each receiver.

My initial sketch placed the computation in a private interface method. Maurizio’s
formulation removes that convention entirely: the computation is an ordinary lambda, and
the interface describes only the access shape.

This also gives us primitive signatures without adding a factory for every primitive
specialization. The same approach could accommodate an array field through a shape such
as `(Polygon, int) -> Point`, where the extra argument identifies the element index. We
would define the supported shapes and validate their correspondence to the field when
constructing the handler.

The explicit interface token serves two purposes. It identifies the intended functional
interface unambiguously, and it supplies the signature information needed by the factory.

In the example, the small subinterface records the binding to `Point`. A generic type-token
abstraction could remove that declaration, but would introduce another API concept; I
would initially keep the explicit interface only.

Jorn pointed out that this is close to a method-handle combinator, with functional
interfaces providing the user-facing form. I think that suggests a possible home in
something like `java.lang.invoke.LazyUpdater`, with method-handle machinery underneath.

The examples currently live in `Lazy` for exploration; their placement need not be the
final API decision.

The `Lookup` makes the access authority explicit, including access to private fields. I
would keep that separate from field ownership: the receiver type can identify the owner
for simple shapes, while inherited fields and specialized receiver types need a precise
resolution policy, possibly an explicit owner parameter.

There are still important semantic questions. In particular, expressing a primitive shape
does not solve how its uninitialized state is represented: zero is a valid result for both
properties above. There are several ways to address this:

* Use wrapped values (this would of course change the field type and add a second layer of
  indirection, pushing us towards the field-owning lazy constructs)
 * Use wider storage units (e.g., use a `long` for an `int` value and stick in a high bit
   when computed and then do an `(int)` cast at the accessor).

Both the above could also provide stability for default values.

I would initially exclude separate state fields or side tables, while allowing
initialization state to be encoded in the chosen storage representation. Unfortunately,
`long` and `double` cannot be widened.

In order to make things "safer", we could add an annotation `@ReflectionOnly` that
prevents regular access to fields and that is enforced by `javac`.

There is an additional interesting observation to be made here, there is a _third_
potential usage of the interface shape: a lambda with shape T could (eventually) provide a
more type safe way of expressing the targeted storage. For example, replacing the
field-name string "radius" with `p -> p.radius`. Babylon could make that practical, but
it introduces a separate dependency and validation. I would treat it as a possible
convenience layer for the future, over the updater mechanism. Combining `@ReflectionOnly`
with field-selector lambdas would require a specific rule distinguishing field
identification from ordinary access.

Interestingly, a similar concept can be adapted for memory segments, so that we can express
lazy computation of native memory for arbitrary memory layouts. I think we can come back
to that later on but seems to indicate, this path is more general than perhaps initially
thought. I will not go into the details here though other than showing this short snippet:

```java
    private static final SequenceLayout POINTS = MemoryLayout.sequenceLayout(8,
        MemoryLayout.structLayout(
                JAVA_DOUBLE.withName("x"),
                JAVA_DOUBLE.withName("y")
        ));

    @FunctionalInterface
    static interface CoordinateAtIndex{
        double coordinate(MemorySegment segment, long index);
    }

    // 0, 2, 4, 6, ...
    private static final CoordinateAtIndex LAZY_X =
            ofSegment(CoordinateAtIndex.class, (segment, index) -> index * 2,
                    POINTS, MemoryLayout.PathElement.sequenceElement(), MemoryLayout.PathElement.groupElement("x"));
```

The new factories are still API sketches, not completed implementations or performance
tested. Does this direction address the awkwardness of `ofField`: a general non-owning
updater underneath the storage-owning Lazy factories, with the functional interface
expressing its access shape?

This also brings us close to cached methods. I appreciate your earlier observation that
the static and instance cases pull in different directions. Is the non-owning
instance-cache case narrow enough to revisit a language solution, or would you prefer to
explore this updater as the library abstraction?

Best,
Per