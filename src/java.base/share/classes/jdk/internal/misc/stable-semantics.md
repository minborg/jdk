# Stable Memory Semantics


## Background

With the introduction of [JEP 526: Lazy Constants](https://openjdk.org/jeps/526) integrated in JDK 26, the JDK gained
a way to combine laziness with constant folding. `LazyConstant` is exposed as a safe wrapper around a `@Stable` field, and new factories
(`List::ofLazy` and `Map::ofLazy`) enable creating lazy collections that still benefit from constant folding.

While this was a big step forward, some issues surfaced as early as the first preview of `StableValue`.
`StableValue` provided both functional and imperative modes. The functional mode worked well and was largely
carried over to `LazyConstant` with incremental improvements (for example, allowing the collection of
the computing function once the lazy constant is initialized).

The imperative mode, however, was less successful. It obscured the simpler functional model and introduced
one or two layers of indirection that hurt performance and memory efficiency. For example, attempts to model
lazy aspects of the Classfile API with `StableValue` were not successful.

What’s missing is a lower-level mechanism that enables direct interaction with fields using stable semantics
without introducing wrappers. Additionally, wrapper-based approaches like `StableValue` lacks the ability
to access flat memory, such as `int` arrays or `MemorySegment` instances.

## Stable Semantics

Stable semantics add two new read-only access modes to the JVM’s existing memory access model:

 * (new) Stable (exists only for read operations)
 * (new) StableVolatile (exists only for read operations)
 * Plain
 * Opaque
 * Acquire/Release
 * Volatile

The fundamental primitives for stable semantics are provided in `jdk.internal.misc.Unsafe` via 18 intrinsics:

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


Semantically, when reading a memory location with stable access, the VM may elide all subsequent reads and, regardless of intervening updates by any thread (including the updating thread!),
constant-fold the initially observed value. In that sense, Stable semantics can be viewed as even weaker than Plain semantics — a deliberately “weak” read.

While this can raise concerns, it is important to note that `Unsafe` is already limited to advanced users, and there are many existing ways to violate
memory consistency and safety with `Unsafe`. We do not expect library or third-party maintainers to use `Unsafe` directly. Instead, two new methods are provided on `VarHandle`:

 * getStable
 * getStableVolatile

These accessors are safer to use but, like `getVolatile` and related operations, they do not enforce correct usage.
It remains the developer’s responsibility to apply them appropriately.

## The State of the Prototype

The current prototype supports primitive and `Object` fields, arrays (e.g., `int[]`), and heap-backed `MemorySegment` instances,
enabling constant folding via stable semantics. Work is underway to provide equivalent support for
native memory (e.g., native MemorySegment).

In addition, stable semantics (via `getStable` and `getStableVolatile`) are now available on the following atomic classes:

 * AtomicBoolean
 * AtomicInteger
 * AtomicIntegerArray
 * AtomicLong
 * AtomicLongArray
 * AtomicReference
 * AtomicReferenceArray

## Generated Assembler

Field access of a normal `private int value;` field via stable semantics (field set to 1):
```
                                                            ; - java.MainField::payload@-1 (line 29)
  0x0000000118e66e9a:   mov    $0x1,%eax

```
Array access of a normal `private int[] values;` component via stable semantics (component at index 0 set to 1):
```
Array:
                                                            ; - java.MainArray::payload@-1 (line 26)
  0x0000000117cb7e9a:   mov    $0x1,%eax
```

### Benchmarks

Here is a benchmark that loops over a 16-element `int` array and computes the sum of the elements:

```
Benchmark                                Mode  Cnt  Score   Error  Units
StableArrayBenchmark.sum                 avgt    6  2.531 ± 0.583  ns/op
StableArrayBenchmark.sumUnsafeStable     avgt    6  0.393 ± 0.080  ns/op <- The entire loop is constant-folded
StableArrayBenchmark.sumVarHandleStable  avgt    6  0.360 ± 0.046  ns/op
```



