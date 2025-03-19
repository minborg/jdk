package java.javaone.k;

/**..*/
public final class TrustUtil {
    private TrustUtil() {}

    /*
    JIT Constant folding eligibility - Ensuring the Chain of trust

    1) The first "anchor" must be a VM constant (i.e. a `static final` field)

    static final A = ...

    2) All fields in a chain must be a constant or in

    JDK 25     : an instance field that is declared `final` AND (
                 - the holding class is a `record` or a hidden class OR
                 - the holding class is an internal JDK class and the field is annotated with `@Stable` )

    JDK 25 + N : an instance field is `final`.

    Note: Method references are implemented using hidden classes.
    Note: Immutable collections like Set.of(), Map.of(), Collectors.toUnmodifiableX, etc.
          are backed by `@Stable` fields.

    Examples for JDK 25 where
      - `b` though `f` are `final` instance fields declared in a `record` or a hidden class
      - `x` is a `final` instance field in a normal or anonymous class)

    A.b.c.d.e.f    Trusted
    A.b.C.d.e.f    Trusted
    A.b.x.d.e.f    Not trusted

    */

    static boolean isShallowlyTrustedInJdk25(Class<?> type) {
        return type.isRecord() || type.isHidden();
    }

}
