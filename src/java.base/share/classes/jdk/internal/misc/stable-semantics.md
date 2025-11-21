# Stable Memory Semantics


## Background

With the release of `LazyConstant`, the JDK got the ability to unify the best-of-two-worlds properties;
laziness and constant folding. This was achieved by exposing `LazyConstant` as a safe wrapper around
a `@Stable` field and additionally, by providing two new factories: `List::ofLazy` and `Map::ofLazy` allowing
the creation of lazy collections that still benefit from constant folding.

While this was great, we came across some culprits that became evident already in the first preview
of `StableValue`. `StableValue` offered both a functional and an imperative mode. The functional mode
worked mostly fine and was basically just ported to `LazyConstant` with some minor improvements
(such as allowing the collection of the computing function once the lazy constant was initialized).

However, the imperative part of `StableValue` was less of a success. Not only did it blur the more clean functional
view, but it also became evident that adding one or two extra levels of indirections had an adverse impact on both
performance and memory efficiency. For example, we tried to model lazy aspects of the Classfile API with
`StableValue` and failed.

What we were missing is a more low-level approach that would allow us to interact directly with fields
using stable semantics _without_ creating wrappers. Another drawback with solutions like `StableValue` is
that they do not allow direct stable access to flat memory such as `int` array or a `MemorySegment`.

## Stable Semantics

Stable semantics is an addition to the current existing memory access semantics we already have in the JVM:

 * Stable (new) (read only)
 * StableVolatile (new) (read only)
 * Plain
 * Opaque
 * Acquire/Release
 * Volatile

The fundamental primitives of the new stable semantics are implemented in `jdk.internal.misc.Unsafe` in the
shape of 18 intrinsic functions `Unsafe::`:

 * getReferenceStable
 * getBooleanStable
 * getByteStable
 * getShortStable
 * getCharStable
 * getIntStable
 * getLongStable
 * getFloatStable
 * getDoubleStable
 * getReferenceStableVolatile
 * getBooleanStableVolatile
 * getByteStableVolatile
 * getShortStableVolatile
 * getCharStableVolatile
 * getIntStableVolatile
 * getLongStableVolatile
 * getFloatStableVolatile
 * getDoubleStableVolatile

In the VM, there are corresponding changes (e.g., `typedef enum { Relaxed, Opaque, Volatile, Acquire, Release, Stable, Stable_Volatile } AccessKind;`)

Stable semantics works in the way that upon reading a memory location, the VM is free to _alide all further reads_,
and -- regardless of any potential updates by any thread (even by the same thread!) -- constant fold the value initially read.

Because of this, one could argue that _Stable semantics is even weeker that Plain semantics_. It is a sort of "weak" read.

While this might set of mental alarm bells, it should be noted that `Unsafe` methods are restricted to advanced
user and that there are already many ways to violate memory consistency and safety using `Unsafe`.

Evidently, we do not expect library maintainers and third-party maintainers to use `Unsafe` directly. Instead, there are
two new methods in `VarHandle::`:

 * getStable
 * getStableVolatile

While these accessors provide more safety, they are still oblivious to how the outcome of a stable access is used.
Just as with `getVolatile` et al., is is up to the developer to ensure proper use.

## The State of the Prototype

The current prototype is able to handle fields, arrays (e.g., an `int` array), and heap-based `MemorySegmnent`
instances allowing constant folding via stable semantics. We are working on providing an equivalent mechanism for
native memory access (e.g., a native `MemorySegment`).





