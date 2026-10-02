# LazyCaches: API decisions and tradeoffs

**Decision record for Java architects — updated 1 October 2026 — branch `prototype/lazy`.**

This document records the design developed in this discussion and embodied in the current specifications of `java.util.LazyCaches` and its field-updater extension in `java.lang.invoke.LazyUpdaters` in this branch. It summarizes the decisions and their consequences; the source Javadoc remains the normative contract for this prototype. “Adopted” means adopted in this branch, not approved for the Java platform or committed to a release.

The starting point was the work associated with [JDK-8336664](https://bugs.openjdk.org/browse/JDK-8336664), including `LazyConstant` and the `List.ofLazy`, `Map.ofLazy`, and `Set.ofLazy` factories, followed by Brian Goetz's email reproduced in the discussion. The email supplied the overall direction. Defects in the subsequent naming, typing, failure, memory, and collection decisions described here should be blamed on Per only. The pros and cons below are architectural assessments, not quotations or evidence of wider consensus.

**The architectural direction**

The adopted design is a non-generic, final utility class, `java.util.LazyCaches`, with generic factories that own their lazy storage. It packages common lazy-initialization and memoization use cases around the shape of access and the coordination/failure policy. The computing function and policy are supplied at construction, keeping later access small and familiar. Caller-owned field or array-element storage is handled by the separate `java.lang.invoke.LazyUpdaters` API.

Two principles from Brian's proposal drive the design. First, laziness has several legitimate meanings: initialization with irreversible side effects needs different coordination from a repeatable calculation. Second, ordinary users should not need to understand VM stability machinery to use the API correctly. Constant folding remains an optimization opportunity underneath a usable library abstraction.

Grouping the storage-owning factories in `LazyCaches` gives them a common vocabulary and one place to learn their differences. Its cost is a relatively substantial shared specification: the familiar return types do not express all the lazy semantics. Separating the field updater keeps its more demanding storage protocol out of that surface, at the cost of another API to learn. The design deliberately covers selected useful combinations rather than every combination of coordinates, storage, failure policy, and memory ordering.

The central choices for `LazyCaches` are standard `Supplier`/`Function`/collection interfaces, storage owned by the returned object, two modes named for what happens once, and acquire/release publication. `LazyUpdaters` supplies the separate, lower-level choice of caller-owned field or array-element storage, with explicit lookup authority for named fields. The main costs accepted are semantics that must be learned from factory contracts, duplicate work or permanent failure depending on mode, and extra obligations when clients supply storage to an updater.

**How the design evolved**

| Stage | Step taken | Step not taken |
| --- | --- | --- |
| API organization | Followed Brian's final refinement toward one factory class and familiar access interfaces; named the storage-owning utility `LazyCaches` after earlier revisions called it `Lazy`. | Did not expose the earlier family of `OfAbsolute`, `OfRelative`, `OfIndexed`, and `OfIndexedRelative` interfaces. |
| Factory usability | Initially separated context access into `ofContext` and replaced the branch's `BoundFunction` with `Function`; subsequently named both scalar factory shapes `ofScalar`. | Did not retain `of` / `ofContext` as public factory names or a public custom context-access interface. The shared `ofScalar` name accepts some method-reference overload ambiguity. |
| Policy naming | Evolved the proposal's `InitMode` and the branch's `ONCE` / `RACE` terminology into `LazyCaches.Mode.COMPUTE_ONCE` / `PUBLISH_ONCE`; renamed updater `CAS` / `SYNCHRONIZED` to `PUBLISH_ONCE` / `SERIALIZED` to describe their guarantees. | Did not make the public mode names depend on a particular atomic instruction or locking mechanism, or offer independent retry, exclusion, and fencing switches. |
| Mode typing | Kept `LazyCaches.Mode` as a two-value enum and gave `LazyUpdaters.Mode` its own three-value enum. | Did not couple the APIs through inherited mode values or expose updater-only modes to `LazyCaches` factories. |
| Updater mode selection | Required `Mode` as the first argument of every `LazyUpdaters` factory and listed `PUBLISH_ONCE` first as the usual choice for repeatable computations. | Did not keep implicit-`PUBLISH_ONCE` overloads; the extra argument makes the policy explicit but lengthens common calls. |
| Memory contract | Replaced the earlier volatile-access guarantee with acquire/release publication, including recorded failures. | Did not promise a total ordering across different lazy values or extra synchronization from repeated access. |
| Field access | Explored a named-field `Lazy.ofField` with an exact field-type token and explicit `Lookup`, then moved caller-owned storage to `LazyUpdaters.ofLocation`. That factory takes a string field selector and checks the named field's type against the functional-interface result. | Did not retain caller-owned field storage in `LazyCaches`, bypass access checks, or keep the later bytecode-cracked accessor-lambda scheme. |
| Array access | Extended `LazyUpdaters` from one- and two-index access to any positive index count supported by the array rank and method arity. `ofLocation` selects an instance or static array field by name and applies indices in their declared order; `ofArray` takes the array itself as a storage argument, with no selector string or caller lookup. | Did not add an array factory to `LazyCaches`, infer arbitrary array expressions from lambdas, or promise index permutation. |
| Computation timing | Retained declaration-site factories that store one computation in the updater, and added use-site overloads that take a computation as the updater's first argument on each access. | Did not require a callback stored by the updater when different accesses need different computations. The caller must keep computations interchangeable for a shared location. |
| Contract completion | Added `ofSet` and specified collection views, entries, traversal failures, streams, callback retention, composition, and execution context. | Did not treat ordinary interface defaults as sufficient to define these interactions. |
| Implementation | After the API reviews, brought implementations into line and added focused and general collection regressions. | Did not remove the earlier lazy APIs, settle their migration policy, or establish performance claims from benchmarks in this work. |

Brian's final sketch used `BoundSupplier` and `InitMode`, still overloaded `of`, and did not include `ofSet`. The current design therefore follows that direction with substantive later refinements.

**Current API surface**

The following is a signature synopsis of the `LazyCaches` factories; bodies, annotations, and Javadoc are omitted. Field- and array-element-backed access is available separately through `LazyUpdaters`, not through `LazyCaches`.

```java
public static <T> Supplier<T> ofScalar(Supplier<? extends T> computingFunction);
public static <T> Supplier<T> ofScalar(Mode mode,
                                      Supplier<? extends T> computingFunction);

public static <C, T> Function<C, T> ofScalar(
        Mode mode, Function<? super C, ? extends T> computingFunction);

public static <T> List<T> ofList(
        Mode mode, int size, IntFunction<? extends T> computingFunction);
public static <K, V> Map<K, V> ofMap(
        Mode mode, Set<? extends K> keys,
        Function<? super K, ? extends V> computingFunction);
public static <E> Set<E> ofSet(
        Mode mode, Set<? extends E> elementCandidates,
        Predicate<? super E> computingFunction);
```

The nested public types are the enum `Mode`, with `COMPUTE_ONCE` and `PUBLISH_ONCE`, and the final error class `InitializationError`, with a public `InitializationError(String message)` constructor.

| Factory | Unit of retained state | Access | Policy selection |
| --- | --- | --- | --- |
| `ofScalar(Supplier)` / `ofScalar(Mode, Supplier)` | One value per returned supplier | `get()` | Explicit mode, or `COMPUTE_ONCE` by default |
| `ofScalar(Mode, Function)` | One value per returned function, across all contexts | `apply(context)` | Explicit mode |
| `ofList` | One value per index | `get(index)` and ordinary list operations | One explicit mode, applied independently per element |
| `ofMap` | One value per copied key | `get(key)` and ordinary map operations | One explicit mode, applied independently per mapped value |
| `ofSet` | One Boolean membership result per copied candidate | `contains(candidate)` and ordinary set operations | One explicit mode, applied independently per candidate |

Among `LazyCaches` factories, only `ofScalar(Supplier)` supplies a default mode. `LazyCaches` has no field, segment, or array factory and no indexed-context shape. A list provides indexed access together with size, views, iteration, and streams; the price is a collection contract rather than a minimal coordinate interface. This is a surface-area choice, not a demonstrated claim of identical performance to an array. `LazyUpdaters.ofLocation` can instead update an element reached through a named array field, while `LazyUpdaters.ofArray` updates an element of an array supplied directly to the updater.

**Standard interfaces, overload resolution, and inference**

Returning `Supplier` and `Function` avoids introducing new access interfaces. Replacing `BoundFunction<C,T>.get(C)` with `Function<C,T>.apply(C)` improves interoperability and gives users ordinary composition. The corresponding cost is semantic: `Function` alone does not tell the reader where storage lives or whether distinct arguments receive distinct results.

The `ofScalar(Mode, Function)` overload owns **one slot**, not a cache indexed by context. For example:

```java
Function<String, Integer> length =
        LazyCaches.ofScalar(LazyCaches.Mode.PUBLISH_ONCE, String::length);

length.apply("abc");    // Installs 3.
length.apply("hello");  // Returns the retained 3.
```

An instance-derived property normally needs its own `ofScalar(Mode, Function)` holder per instance. A static holder shared across instances would share one value. Supplying the context at access time permits a noncapturing callback; it does not eliminate the holder or guarantee allocation-free lambda execution. A null context is rejected even after initialization.

The shared `ofScalar` name makes the scalar category visible but again overloads supplier-shaped and context-shaped computations. Explicit zero-argument and one-argument lambdas generally distinguish them; overloaded or inexact method references—such as references to constructors with several arities—can make overload selection ambiguous. A destination result type does not generally rescue that selection. The earlier `ofContext` name avoided this ambiguity and made the access shape explicit, at the cost of another method name.

The remaining `var` issue is ordinary inference: Java cannot infer `String` merely from `s -> s.length()` when no context type is otherwise supplied. A typed destination, an explicitly typed lambda, a suitable method reference, or explicit type arguments supplies that information. For example:

```java
var length = LazyCaches.ofScalar(LazyCaches.Mode.PUBLISH_ONCE,
                                (String s) -> s.length());
```

An additional `Class<C>` argument for the context-shaped overload was explored as an inference aid but was not adopted. It would make `var` examples easier to type, while adding an argument and another factory form despite there being no field type to validate in this case. This is a considered alternative, not a permanent rejection of any future convenience overload.

Composition retains the ordinary [`Function` ordering](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/function/Function.html#compose(java.util.function.Function)): `compose` runs the preceding function on every application, and `andThen` runs the following function after every successful lazy access. Their results are not automatically cached. Composition itself records no additional initialization failure; nested lazy accesses still follow their own failure rules. This avoids silently changing familiar composition semantics, but requires callers to place expensive work inside the intended lazy boundary.

**Two coordinated policies, named for their guarantees**

| Dimension | `COMPUTE_ONCE` | `PUBLISH_ONCE` |
| --- | --- | --- |
| Intended use | Initialization, including side effects that must not repeat | Memoization where repeated computation is acceptable |
| Invocation count per slot | At most once; zero if never needed | Competing invocations and retries after failure are permitted |
| Competing access | Waits for the one invocation | May compute another candidate |
| Successful access | Returns the retained result | Returns the installed winner, including when its own candidate loses |
| Failure | Permanent | Propagated to that caller; not retained |
| Principal benefit | Prevents duplicate initialization and repeated side effects | Avoids requiring all callers to wait for a designated initializing computation |
| Principal cost | Waiting, permanent failure, and possible dependency deadlock | Duplicate work, discarded results, and side effects that may repeat |

`COMPUTE_ONCE` states what is limited; `PUBLISH_ONCE` states what is committed. They are more informative than `ONCE` / `RACE` and avoid encoding a particular instruction such as CAS. “Once” does not require eager execution. The installed result is consistent across successful accesses to one slot, but the identity or contents of the winning candidate need not be predictable across executions.

Initialization versus memoization is an intended-use distinction, not a static-versus-instance restriction. Either mode can serve either location where the chosen factory supports it.

The two `LazyCaches` policies bundle exclusion and failure handling. The benefit is a small choice set; the cost is that users cannot request, for example, serialized initialization with retry or concurrent computation with sticky failure. `PUBLISH_ONCE` is not a promise that the API or computing function is lock-free or wait-free. Discarded results are not closed or otherwise disposed of by `LazyCaches`.

**Mode types and the caller-owned-storage updater**

`LazyCaches.Mode` and `LazyUpdaters.Mode` are unrelated enums. `LazyCaches.Mode` retains `COMPUTE_ONCE` and `PUBLISH_ONCE`. `LazyUpdaters.Mode` offers `PLAIN`, `PUBLISH_ONCE`, and `SERIALIZED` for caller-owned field and array-element storage. Both `LazyUpdaters.ofLocation` and `LazyUpdaters.ofArray` accept these three modes. A mode from either enum cannot be passed to a factory expecting the other; each API keeps a vocabulary suited to its guarantees, and both retain enum conveniences such as `values()` and `switch` expressions.

The updater's `PUBLISH_ONCE` replaces the earlier name `CAS`: it names the one installed value rather than the operation used to install it. `SERIALIZED` replaces `SYNCHRONIZED`: it promises one computation at a time per storage location without requiring a particular locking implementation. Unlike `LazyCaches.Mode.COMPUTE_ONCE`, `SERIALIZED` allows another access to retry after failure.

The `ofLocation` factories identify an instance or static field by string. An instance-field selector is the field's simple name, with its owner inferred from the updater interface's concrete receiver type; a static-field selector is the owner's binary name followed by a dot and the field name. One or more `int` parameters following the receiver, or comprising all storage parameters for a static array field, select an array element through that field. The separate `ofArray` factories need no selector: their first storage parameter is the array and the following one or more `int` parameters select an element. In a use-site interface, the computing-function parameter comes before these storage parameters. The index count must not exceed the array rank and remains subject to ordinary Java and JVM method-parameter limits. The interface fixes that count: `int...` erases to one `int[]` parameter and cannot stand in for several index parameters. Every updater factory requires a `Mode` argument. All updater modes reserve the selected location's Java default value to mean uninitialized and reject a computation that produces that value.

| `LazyUpdaters.Mode` | Access and coordination | Failure | Principal benefit and cost |
| --- | --- | --- | --- |
| `PLAIN` | Plain read and write, without CAS or exclusion; each computing caller returns its own result | Propagated directly; retry remains possible | Least coordination, at the cost of possible overwrites, stale reads, and no safe-publication guarantee; plain `long`/`double` access may tear |
| `PUBLISH_ONCE` | Acquire observation and atomic release installation of a non-default value; competing candidates race and successful callers return the installed winner | Propagated directly; retry remains possible | No per-receiver coordination state, at the cost of duplicate work and discarded candidates |
| `SERIALIZED` | Acquire read, release write, and one computing thread at a time for the same field and receiver (or static field), or physical array element, across updater objects | Propagated directly without being retained; a later or waiting caller may retry | Avoids concurrent computations, at the cost of waiting and auxiliary coordination state |

`SERIALIZED` shares coordination state across updater objects for a field and tracks instance receivers by weak identity. For arrays of any rank, its key is the identity of the final container array and the final index. The preceding indices only traverse outer dimensions. Two outer arrays or owner fields that refer to the same selected container array therefore coordinate access to the same element, as do direct-array and field-backed updaters that reach it. If a computation throws or returns the reserved default value, the location remains uninitialized; the next caller to acquire that state may compute again. The state must remain shared after failure so waiting and newly arriving callers cannot compute concurrently. After success, the location retains the result and the coordination state may be discarded. Cleared receiver or container-array entries are removed on a later uninitialized access, so their metadata may remain until then or until class unloading.

Updaters sharing a physical storage location must all use the same mode and should have interchangeable computing functions. For a use-site updater, this also applies to callbacks supplied on different calls to the same updater. The location must not be reset or written directly while they are in use; compatible same-mode updaters may share it. `PLAIN` is appropriate only when callers accept its weaker outcomes or provide their own synchronization. It is deliberately available at the updater layer, where the caller explicitly controls storage, without weakening the guarantees of the high-level `LazyCaches` factories.

**Failure policy: preserve the Error category without retaining the original failure**

The discussion explicitly chose to propagate an initial `Error` directly and keep its failure permanent in `COMPUTE_ONCE`. Subsequent observations remain in the `Error` hierarchy, using the dedicated `LazyCaches.InitializationError` rather than `NoSuchElementException`.

| Computation outcome | Access executing the computation in `COMPUTE_ONCE` | Access observing its recorded failure in `COMPUTE_ONCE` | Failing access in `PUBLISH_ONCE` |
| --- | --- | --- | --- |
| Throws an `Error` | The same error, unchanged | A new `InitializationError`, no cause; original error class name when available | The same error, unchanged; retry remains possible |
| Throws another throwable | `NoSuchElementException` with that throwable as cause | `NoSuchElementException`, no cause; original throwable class name | The original throwable; retry remains possible |
| Returns null | Computation failure represented by a `NullPointerException`, wrapped as above | The recorded permanent failure | Direct `NullPointerException`; retry remains possible |

Waiting callers count as subsequent accesses. The failure state does not retain the original throwable. A later `InitializationError` reports an earlier failure, not a fresh occurrence of the original error condition.

The benefit over wrapping everything in `NoSuchElementException` is that ordinary `catch (Exception)` handling does not accidentally absorb subsequent observations of an `Error`. Keeping only failure information also limits retention of exception stacks, application objects, and class loaders through the failure state. The costs are a new public error type and reduced diagnostics on later access: the original cause, stack, and exact error subtype are no longer available there. `NoSuchElementException` also has a second meaning in traversal, so a lazy failure must not be interpreted as end-of-iteration.

The Javadoc cites [JLS 12.4.2](https://docs.oracle.com/javase/specs/jls/se26/html/jls-12.html#jls-12.4.2). The analogy is deliberately limited: class initialization propagates an initial `Error` unchanged and later reports erroneous initialization with `NoClassDefFoundError`; `LazyCaches` uses its own failure types and storage rules. It does not import the whole class-initialization protocol.

Same-thread recursive access to an uninitialized `COMPUTE_ONCE` slot throws `IllegalStateException`. The outer computation may catch it and succeed; it becomes sticky only if it escapes that computation. Recursive `PUBLISH_ONCE` computation need not be detected. Cross-thread dependencies can deadlock in `COMPUTE_ONCE`. No general cycle detector was added.

In `PUBLISH_ONCE`, a failing computation still throws even if another thread has installed a value in the meantime. Substituting the winner for that failure would hide an outcome of user code; the chosen rule preserves exception propagation at the cost of some accesses failing after storage has become successfully initialized.

**Memory consistency: publication, without repeated volatile synchronization**

The adopted guarantee for `LazyCaches` is release publication and acquire observation, for both successful values and permanent `COMPUTE_ONCE` failures. Actions before publication happen-before actions after an access observing that publication, including actions in a handler for a recorded failure. The updater's `PUBLISH_ONCE` and `SERIALIZED` modes publish successful values with release effects and observe them with acquire effects. `PLAIN` makes no such publication guarantee, and `SERIALIZED` does not retain failures.

| Alternative | Benefit | Cost / status                                                                                                                                      |
| --- | --- |----------------------------------------------------------------------------------------------------------------------------------------------------|
| Volatile-access contract | Stronger ordering, including a total order of volatile accesses | Stronger than the selected publication requirement; adds obligations to repeated access (e.g., prevents @Stable access). Replaced in this branch. |
| Acquire/release publication | Preserves visibility of initialization while allowing later reads to be elided when publication is preserved | Does not guarantee additional ordering or visibility for subsequent mutations, or total ordering across different lazy values. Adopted.            |
| Plain access without publication guarantees | Potentially weaker access costs | Requires a weaker user model or external synchronization. Excluded from `LazyCaches` factories; available as `LazyUpdaters.Mode.PLAIN` for field and array-element updates. |

The distinction for discarded computations was corrected during review:

- A successful access whose own candidate loses **does acquire the winner's publication**.
- That does **not publish the losing computation's own actions to other readers** through the lazy slot.
- Actions performed by a failed `PUBLISH_ONCE` computation are likewise not guaranteed to be published to other threads through that slot.

An atomic operation is not automatically sufficient for both directions. In particular, [`compareAndExchangeRelease`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/invoke/VarHandle.html#compareAndExchangeRelease(java.lang.Object...)) reads its witness with plain semantics. The updated implementations acquire-read the installed value after a losing release exchange. This is an implementation technique satisfying the contract, not an instruction sequence required of every implementation.

The revised contract permits constant folding and elision of later value or failure reads only while preserving the applicable publication guarantee. It does not promise folding on every VM, equate all final-field chains with trusted fields, or make subsequent mutation of a returned object thread-safe. Lazily obtaining an array also does not make its elements lazy. Internal volatile operations remain permissible; changing the public contract did not mean removing every volatile field from the implementation.

**Caller-owned field and array-element storage: a separate updater API**

Brian's email identified an allocation-sensitive case: many cheap, short-lived objects whose caches are usually never read. Using a field already in each receiver avoids allocating a lazy holder per receiver. The earlier named-field `Lazy.ofField` overload explored that approach, but is no longer part of the storage-owning utility class, now `LazyCaches`. The separate `LazyUpdaters` API uses caller-owned fields and array elements. Its `Class<T>` argument identifies the functional interface implemented by the returned updater; it is not a token for the stored value type. A declaration-site factory also requires the computing function to implement `T`. A use-site factory instead requires the updater's first method parameter to be a functional interface for the computation.

The adopted factories have these shapes (with generic bounds and Javadoc omitted):

```java
static <T> T ofLocation(Mode mode, Lookup lookup, Class<T> type,
                        String fieldSelector, T computingFunction);
static <T> T ofLocation(Mode mode, Lookup lookup, Class<T> type,
                        String fieldSelector);

static <T> T ofArray(Mode mode, Class<T> type, T computingFunction);
static <T> T ofArray(Mode mode, Class<T> type);
```

Requiring `Mode` leaves four updater factories rather than eight and makes the coordination choice visible at every declaration. `PUBLISH_ONCE` is listed first in `LazyUpdaters.Mode` and is the usual choice when concurrent duplicate computations and discarded candidates are acceptable. Enum order helps discovery but does not supply a Java default. The cost is repeating `Mode.PUBLISH_ONCE` in common calls and requiring clients to choose a policy before creating an updater. Removing the no-mode overloads also breaks sources that used them.

Annotating `LazyUpdaters` at class level with `@PreviewFeature` makes every factory a preview entry point. Previously the no-mode overloads were unannotated while the mode-taking forms were preview. Requiring preview opt-in for all updater factory calls is intentional while this API's surface and contract are being shaped.

An overload with a `computingFunction` argument binds one computation when the updater is created; one without it takes a computation at each access. In the latter form the updater's first method parameter is a functional interface whose single abstract method accepts the updater's remaining storage arguments in order and produces a compatible value. The factory checks both method shapes and the selected storage type. A callback may return a subtype of reference storage or use boxing, unboxing, or widening primitive conversion; a broad reference result is checked when computed. The callback parameter is not a storage coordinate. `ofLocation` names a field, optionally indexing into an array held by that field; `ofArray` operates on an array supplied as the first storage argument. `ofArray` is deliberately array-specific. An ordinary receiver type does not identify which of its fields to update without a selector. Other intrinsic storage carriers, such as atomic holders, would need their own explicit contract if added later.

| Storage selected | Selector and storage-argument shape | Consequence |
| --- | --- | --- |
| Instance field | Simple field name, such as `"radius"`; receiver followed by no indices | The receiver supplies storage for each invocation; a concrete SAM receiver type or fixed generic specialization identifies its owner |
| Static field | Binary owner name, dot, and field name, such as `"example.Answer.value"`; no storage arguments | The named class supplies one shared location; the static use-site interfaces cover `int`, `long`, and reference values |
| Element reached through an instance array field | Simple array-field name; concrete owner followed by one or more `int` indices, up to array rank | The element, not the array-reference field, is the storage location |
| Element reached through a static array field | Qualified static array-field name; one or more `int` indices, up to array rank, and no receiver | The selected element is the storage location; standard `IntUnaryOperator` or `IntBinaryOperator` can fit one- or two-index declaration-site cases |
| Element of a direct array | No selector; concrete array followed by one or more `int` indices, up to array rank | The updater may serve many arrays of the same type |

For use-site computation, prepend one callback parameter to the storage arguments in this table. The callback receives those same storage arguments. The named field or selected array element remains the location whose default value signals that computation is needed.

Nine pre-canned accessor interfaces in `LazyUpdaters` cover common use-site shapes. The six primitive interfaces retain their fixed `int` or `long` results: `IntArrayAccessor` and `LongArrayAccessor` access one-dimensional arrays, `IntFieldAccessor<R>` and `LongFieldAccessor<R>` access instance fields, and `StaticIntFieldAccessor` and `StaticLongFieldAccessor` access static fields. The three reference-valued counterparts are `ReferenceArrayAccessor<E>` for an element of a directly supplied `E[]`, `ReferenceFieldAccessor<R,V>` for an instance field, and `StaticReferenceFieldAccessor<V>` for a static field. Each accessor's outer method is named `getOrCompute`; the nested array `Computer` methods remain `apply`. Their callbacks likewise receive the storage coordinates, if any, and run only when the selected location has its reserved default value. A custom callback-first interface remains available for other primitive types or index shapes.

Unlike the non-generic primitive array and static-field accessors, each reference accessor needs a concrete, non-generic subinterface as its factory `Class` token, for example `interface Names extends ReferenceArrayAccessor<String> { }`, `interface Title extends ReferenceFieldAccessor<Document,String> { }`, or `interface Caption extends StaticReferenceFieldAccessor<String> { }`. This fixes the array element type, receiver and value type, or static value type that a raw generic token cannot supply. The selected reference field's existing value must be compatible with the accessor result; a newly computed value must also be storable in that field. A parameterized accessor value type must exactly match the field's declared generic type; the factory does not resolve type variables inherited through a generic field owner. A raw or broader result is checked against the field's erased storage type before publication, but Java cannot check its generic arguments at runtime. As with other reference locations, `null` is reserved for the uninitialized state and cannot be a successful computed result.

The field selector names one field; it is not a Java expression or a path through several fields. For a nested static owner, the selector uses its binary name, such as `example.Outer$Inner.value`. A field declared directly by the named owner or receiver type takes precedence. Otherwise, the nearest superclass declaration is selected if no interface declares the same name; with no superclass declaration, exactly one interface declaration is accepted. A superclass/interface conflict or multiple interface declarations are rejected as ambiguous. The search considers declarations even when a field is not inherited in Java source; the supplied lookup must still authorize access. A static caller can name the declaring class or interface to resolve such a conflict. Field access is authorized through the named owner or receiver type, allowing an accessible public subclass to expose an inherited public field from an inaccessible superclass. The `Lookup` resolves a static owner name using its lookup class's loader and authorizes access. Thus identical selector text can designate different classes under different loaders, but it cannot select an arbitrary class of the same name independently of the supplied lookup. A separate `Class<?> owner` token would make that identity explicit at the call site, at the cost of another argument or factory shape.

For an instance field, the receiver is normally inferred from the updater interface's **concrete erased receiver parameter type**. `Function<Point, Double>` does not provide this type to the factory: its `apply` method erases the parameter to `Object`. More generally, a raw generic interface token such as `Function.class` or `Supplier.class` is rejected at factory creation with an `IllegalArgumentException` explaining the erased type variable and how to declare a non-generic interface with concrete method types. A concrete subinterface of an arbitrary generic accessor may still inherit an `Object` receiver in its erased method; in that case the client must declare an independent interface with a concrete receiver parameter. The pre-canned generic interfaces are a deliberate exception: a non-generic subinterface such as `CounterAccessor extends IntFieldAccessor<Counter>` fixes the owner, while the reference forms above fix their element or field-value types as well. The raw `IntFieldAccessor.class`, `LongFieldAccessor.class`, and reference-accessor base tokens leave required types unresolved and receive a diagnostic showing the necessary specialization. The field name is validated against the inferred receiver type, and the selected field type is checked against the updater's result type. A static field needs no receiver, so a non-generic zero-argument interface such as `IntSupplier` remains useful for declaration-site computation.

```java
class Point {
    private final double x, y;
    private Double radius;                 // null means not initialized

    Point(double x, double y) { this.x = x; this.y = y; }
    interface Radius { Double getOrCompute(Point point); }

    private static final Radius LAZY_RADIUS = LazyUpdaters.ofLocation(
            LazyUpdaters.Mode.PUBLISH_ONCE, MethodHandles.lookup(),
            Radius.class, "radius",
            point -> Math.hypot(point.x, point.y));

    double radius() { return LAZY_RADIUS.getOrCompute(this); }
}
```

The boxed `Double` permits a computed positive zero: `null`, rather than `+0.0`, is the reserved uninitialized value. For a primitive `double` field, positive zero could not be stored as a computed result.

The pre-canned instance accessor gives the computation its own interface. A concrete subinterface fixes the receiver type, while each access supplies a callback before that receiver:

```java
class Counter {
    private int seed = 41;
    private int cached;                    // zero means not initialized

    interface CounterAccessor extends LazyUpdaters.IntFieldAccessor<Counter> { }

    private static final CounterAccessor LAZY = LazyUpdaters.ofLocation(
            LazyUpdaters.Mode.SERIALIZED, MethodHandles.lookup(),
            CounterAccessor.class, "cached");

    int value() { return LAZY.getOrCompute(counter -> counter.seed + 1, this); }
}
```

Here `LAZY` retains no computation. The lambda has a `Counter` parameter and runs only when `cached` is zero. In `SERIALIZED` mode, the first successful callback for a location supplies its value; a later access receives that value without invoking its own callback. After a callback fails, a later access may try its own callback. A caller can instead declare a custom updater interface with its own `Computer` and access method for an instance field of another value type.

A static selector includes the owner's binary name. An index-only interface can reach an element through a static array field without passing the array at each invocation (imports omitted):

```java
package example;

class Answer {
    private static int value;
    private static final IntSupplier LAZY_VALUE = LazyUpdaters.ofLocation(
            LazyUpdaters.Mode.PUBLISH_ONCE, MethodHandles.lookup(),
            IntSupplier.class,
            "example.Answer.value", () -> 42);
}

class Holder {
    private static final int[] ints = new int[8];
    private static final int[][] matrix = { new int[4], new int[4] };
    private static final int[][][] volume = new int[2][3][4];

    interface Voxel { int getOrCompute(int plane, int row, int column); }

    private static final IntUnaryOperator LAZY_INTS = LazyUpdaters.ofLocation(
            LazyUpdaters.Mode.PUBLISH_ONCE, MethodHandles.lookup(),
            IntUnaryOperator.class,
            "example.Holder.ints", index -> index + 1);

    private static final IntBinaryOperator LAZY_CELLS = LazyUpdaters.ofLocation(
            LazyUpdaters.Mode.SERIALIZED, MethodHandles.lookup(),
            IntBinaryOperator.class, "example.Holder.matrix",
            (row, column) -> 1 + row * 4 + column);

    private static final Voxel LAZY_VOXELS = LazyUpdaters.ofLocation(
            LazyUpdaters.Mode.PUBLISH_ONCE, MethodHandles.lookup(),
            Voxel.class, "example.Holder.volume",
            (plane, row, column) -> 1 + plane * 12 + row * 4 + column);
}
```

`LAZY_INTS.applyAsInt(3)` selects `ints[3]`; `LAZY_CELLS.applyAsInt(1, 2)` selects `matrix[1][2]`; `LAZY_VOXELS.getOrCompute(1, 2, 3)` selects `volume[1][2][3]`. The `final` static fields are paths to mutable elements; the fields themselves are not updated. For any rank, index arguments follow array dimensions from outermost to innermost. There is no accessor expression in which to swap, repeat, or transform them.

The pre-canned static field accessors accept `IntSupplier`, `LongSupplier`, or `Supplier<V>` callbacks for `int`, `long`, or reference fields. These callbacks take no parameters because there is no receiver or index to pass through:

```java
class UseSiteAnswer {
    private static int value;

    private static final LazyUpdaters.StaticIntFieldAccessor ACCESS =
            LazyUpdaters.ofLocation(LazyUpdaters.Mode.PUBLISH_ONCE,
                    MethodHandles.lookup(),
                    LazyUpdaters.StaticIntFieldAccessor.class,
                    UseSiteAnswer.class.getName() + ".value");

    static int answer() { return ACCESS.getOrCompute(() -> 42); }
}
```

The earlier `Counter.CounterAccessor` example fixes the instance owner through its `IntFieldAccessor<Counter>` specialization. Supplying the raw generic base type instead leaves the owner unknown and fails at factory creation.

For an instance array field, `ofLocation` takes the field's simple name and the interface includes its owner. For a direct array, `ofArray` takes no selector and the interface includes the array itself:

```java
class Names {
    private final String[] entries = new String[8];
    interface Entry { String getOrCompute(Names owner, int index); }
    private static final Entry LAZY_ENTRY = LazyUpdaters.ofLocation(
            LazyUpdaters.Mode.PUBLISH_ONCE, MethodHandles.lookup(),
            Entry.class, "entries",
            (owner, index) -> "entry-" + index);
}

class Values {
    private final int[] entries = new int[8];
    interface Entry { int getOrCompute(int[] array, int index); }
    private static final Entry LAZY_ENTRY = LazyUpdaters.ofArray(
            LazyUpdaters.Mode.PUBLISH_ONCE, Entry.class,
            (array, index) -> index + 1);

    int entry(int index) { return LAZY_ENTRY.getOrCompute(entries, index); }
}
```

The use-site form makes the callback first and keeps the array and index together. A call with the built-in `int[]` accessor is:

```java
int[] entries = new int[8];
LazyUpdaters.IntArrayAccessor access = LazyUpdaters.ofArray(
        LazyUpdaters.Mode.PUBLISH_ONCE,
        LazyUpdaters.IntArrayAccessor.class);
int value = access.getOrCompute((array, index) -> array.length + index + 1,
                              entries, 0);
```

The callback receives the array and index, with no unused updater parameter. A cached nonzero element is returned without calling it. In `PUBLISH_ONCE` mode, callbacks can race and successful accesses return the installed winner; a losing candidate's result is discarded. A one-index reference array can use `ReferenceArrayAccessor<E>` through a concrete subinterface; other primitive element types or multiple indices can use a custom callback-first interface.

The direct-array shape also permits an array-valued element. For `int[][][]`, one index selects an `int[][]` plane, two select an `int[]` row, and three select an `int` cell:

```java
class Volume {
    interface Plane { int[][] getOrCompute(int[][][] array, int plane); }
    interface Row { int[] getOrCompute(int[][][] array, int plane, int row); }
    interface Cell { int getOrCompute(int[][][] array, int plane, int row, int column); }

    private static final Plane PLANES = LazyUpdaters.ofArray(
            LazyUpdaters.Mode.PUBLISH_ONCE, Plane.class,
            (array, plane) -> new int[3][]);
    private static final Row ROWS = LazyUpdaters.ofArray(
            LazyUpdaters.Mode.PUBLISH_ONCE, Row.class,
            (array, plane, row) -> new int[4]);
    private static final Cell CELLS = LazyUpdaters.ofArray(
            LazyUpdaters.Mode.PUBLISH_ONCE, Cell.class,
            (array, plane, row, column) ->
                    1 + plane * 12 + row * 4 + column);

    private final int[][][] volume = new int[2][][];

    int cell(int plane, int row, int column) {
        PLANES.getOrCompute(volume, plane);
        ROWS.getOrCompute(volume, plane, row);
        return CELLS.getOrCompute(volume, plane, row, column);
    }
}
```

On a new `Volume`, the outer calls install a plane and row into `null` slots before the cell updater traverses them. An intermediate array reference must be non-null for a deeper selection; a null reference or an invalid index fails before computing. Each updater selects a distinct physical element and uses that element's default value as its uninitialized sentinel. If `CELLS` used `SERIALIZED` mode, the final container array and final index would determine coordination: the `int[]` row and `column`. All forms rely on a concrete array parameter in the interface. A generic interface erased to `Object` cannot reveal the direct array type merely through `Class<T>`.

For `ofLocation`, the lookup is an access capability, not a bypass: it authorizes the updater interface and selected field, and lookup-imposed receiver restrictions remain in force. The capability can be delegated through helpers, whereas a `@CallerSensitive` scheme would tie authority to invocation context. Its cost is an extra argument at each `ofLocation` call. This follows the established [lookup capability model](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/invoke/MethodHandles.Lookup.html). Direct `ofArray` access needs no field resolution or caller-supplied lookup. Its concrete array parameter and index parameters identify the element; it does not check caller access to the updater interface or array class. Adapter generation remains an internal implementation detail.

Both factories keep the existing updater modes and storage protocol. The selected mutable field or physical array element uses its Java default value as the uninitialized sentinel: `null`, `false`, integral zero, or positive floating-point zero. A computation returning that value throws `IllegalArgumentException`, and a preexisting non-default value is returned without computation. A selected field cannot be `final`, but a final array-reference field can lead to mutable elements. A null array along a path or invalid index fails before computing. A null-restricted reference field is rejected at factory creation; a null-restricted reference array element cannot supply the required `null` sentinel and is rejected on access. An array-reference path is resolved once per invocation. The caller must safely publish any receiver, array, or preinitialized value and must not reset a selected location or change intermediate array references while its updater is in use.

The modes retain their distinct tradeoffs: `PLAIN` permits racy plain writes without safe publication, `PUBLISH_ONCE` uses acquire observation and atomic release publication to retain one non-default winner, and `SERIALIZED` runs at most one computation at a time per physical location, with retry after failure. Across either factory, aliases of the same selected container array and index designate one location and share `SERIALIZED` coordination. Updaters sharing a location must use the same mode and should have interchangeable computing functions, including callbacks provided to a use-site updater on separate calls. Neither factory eagerly adds a value holder to every receiver or array element; `SERIALIZED` may allocate auxiliary weakly keyed coordination state on access. Generic functional-interface parameterizations are erased at `Class<T>`, so some result compatibility cannot be proved at factory creation; an incompatible computed value may fail at a later cast or array store.

**Path not taken: cracking accessor lambdas**

An earlier revision supplied a second lambda to select storage, for example `point -> point.radius`, `index -> Holder.ints[index]`, or `(array, row, column) -> array[row][column]`. This made the selector type-checked by the Java compiler, supported IDE rename tools, and avoided string field names. It also let a single `ofLocation` factory cover fields and direct arrays. The prototype inspected generated lambda implementation bytecode without executing the accessor.

That advantage came with a complex and fragile contract. The implementation had to recover the lambda's implementation method, inspect bytecode and constant-pool references, recognize array-load opcodes and method-handle forwarding shapes, and then recheck lookup authority. Captures, method references, transformed indices, and several compiler or hidden-class shapes needed exclusions or special cases; custom class loaders complicated access to implementation bytes. Even simple source expressions depended on the exact bytecode shape emitted by the compiler. The mechanism also allowed reversed index arguments, adding a semantic choice whose only expression was the selector lambda. Those costs outweighed the convenience of a type-checked field expression for this prototype.

| Storage-selection choice | Pros | Cons / decision |
| --- | --- | --- |
| Named field with `String` and `Lookup` | Simple selector grammar; no inspection of compiler-generated accessor code; explicit access authority | Field names are checked at factory creation rather than compilation, and static names resolve in the lookup's loader. Adopted for `ofLocation`. |
| Direct array identified by a concrete SAM parameter | No selector string or storage-selection callback; coordinates have one order | Requires a separate `ofArray` name and a concrete erased array parameter. Adopted for direct arrays. |
| Cracked accessor lambda | Compiler type-checks the storage expression and IDE renames can update it | Depends on generated bytecode and rejects several otherwise ordinary lambda shapes. Dropped. |

The current design uses a string only where a field genuinely needs a name and uses `ofArray` where the concrete SAM storage parameters and indices already identify a direct array element. This removes accessor bytecode inspection from the public contract and implementation path. The computation remains a normal lambda or method reference; it may capture state. A declaration-site updater retains it, while a use-site updater receives it on each access. The design gives up compile-time checking and automatic refactoring of field names; a wrong selector is detected when the factory is created. A more general string-free overload for arbitrary receivers was not adopted because a receiver type alone cannot identify which of its fields is the desired storage location.

**Collections: retain ordinary shapes, specify the lazy boundary**

The collection factories create unmodifiable collections. Every factory invocation supplies independent initially uninitialized storage; this does not require distinct result objects if callbacks return the same object. Views and map entries share their originating storage. Empty-instance reuse is permitted when construction has size zero, no keys, or no candidates; this permission does not turn a nonempty-candidate set that later filters to empty into shared lazy state.

| Shape | What is fixed or copied eagerly | What is computed lazily | Main benefit / cost |
| --- | --- | --- | --- |
| List | Size and index domain | One element per index | Useful indexed interface and `RandomAccess` views; element-inspecting operations can trigger work or fail |
| Map | Copied key set | One value per key | Keys and size can be used without evaluating values; ordinary value-oriented operations can evaluate them |
| Set | Copied candidate set | One membership decision per candidate, retaining both true and false | Supports lazy filtering with stable membership; size and hash require all membership statuses |

`ofSet` was added in this branch beyond Brian's final sketch. It computes membership of existing candidates, not new element objects or an unbounded stream.

List sublists and reversed views share original slots and compute with the original indices. Looking up an absent map key or an object outside a set's candidate domain does not invoke its computing function. An out-of-range list access fails before computation.

Map computations receive the **retained key from construction**, even when queried with a different but equal object. Set predicates receive the retained candidate on the same basis. The copies are shallow: the key/candidate objects themselves are retained, not cloned. This removes dependence on which equal query object arrives first. Its cost is that information present only on a query object is intentionally not an input to the cached computation. Input sets must use equality-compatible membership; changing key/candidate equality or hash codes while retained is outside the contract. Map and set iteration order is unspecified.

Copying a lazy input set can evaluate **the input's memberships** and propagate its failures. Construction does not thereby compute the output map's values or the output set's memberships. This distinction preserves snapshot semantics while making clear that accepting a lazy input is not a promise of a computation-free factory call.

The following stronger collection choices were made:

| Decision | Pros | Cons |
| --- | --- | --- |
| Null results are computation failures; null keys/candidates are rejected during construction | Clear distinction between uninitialized storage and a retained result | Absent computed values need an explicit representation such as `Optional` |
| Unsupported mutations always throw, even for no-ops, without invoking computing functions or supplied mutation callbacks | Predictable unmodifiability; `sort`, `replaceAll`, `removeIf`, and map updates cannot accidentally perform work | More overrides than permissive default methods; no “successful” empty mutation |
| Entries remain valid after traversal and share the original per-key storage | Entries can safely escape an iterator; later access observes the same result or permanent failure | Retaining one entry can retain the map and other unresolved state |
| Returned suppliers, functions, and collections are not serializable, including empty collections | Avoids defining serialization of callbacks, incomplete state, and sticky failures | Applications must choose their own materialized or domain-specific representation |
| Scalar `equals`, `hashCode`, and `toString` do not compute; collection content operations may compute | Keeps scalar diagnostics harmless while retaining ordinary collection meanings | Collection logging, hashing, equality, or copying can run user code and fail |

Obtaining map entries or their keys does not itself compute mapped values. Reading an entry's value does; `Map.Entry.copyOf` therefore may compute or report a failure while producing its detached copy. Unmodifiable collections can still contain mutable values. In particular, a lazy map's structural hash must reflect current value hashes rather than be permanently cached merely because its storage locations never change.

**Traversal and streams: failures do not silently remove data**

When the traversal itself encounters a lazy computation failure, the pending value or candidate is not consumed. A failed list-iterator `next` or `previous` leaves its cursor unchanged. Set iterators may advance past candidates already known to be absent, but keep a failing membership pending. The rules cover bulk traversal, `hasNext`/`hasPrevious`, and spliterators; a failed source access in `tryAdvance` does not invoke its action. A split that fails because of lazy computation must preserve the original's remaining coverage.

The benefit is that retry in `PUBLISH_ONCE` cannot silently skip data, while `COMPUTE_ONCE` repeatedly reports the same slot's recorded failure. The cost is extra traversal state and implementations beyond ordinary defaults. This is not transactional rollback: earlier values, memberships, and failures remain retained, and elements already delivered remain consumed.

Failures thrown by a traversal action follow the ordinary interface's continuation rules, including when that action makes its own failing lazy access. Such a failure must not be mistaken for failure of the traversal's own source access. More generally, user callbacks outside the computing function do not themselves create an additional initialization failure; lazy accesses inside them retain their own rules.

Creating streams or spliterators and querying their metadata do not compute values or report stored computation failures. Source spliterators, including those produced by splitting, report `NONNULL` and `IMMUTABLE`; lists and views additionally report `ORDERED`, `SIZED`, and `SUBSIZED`. Map key/entry views report `DISTINCT`, `SIZED`, and `SUBSIZED`; value views report the latter two. Lazy sets report `DISTINCT` and need not report an exact size before it is known without evaluation.

The design does not promise minimal evaluation by every traversal, split, or parallel operation. Short-circuiting can compute more than the final result consumes. Conversely, a sized `count()` can avoid element computation altogether, even when some element has a recorded failure. This preserves stream optimization freedom. A terminal operation such as `count()` is not evidence that every lazy value has been evaluated; validation must explicitly traverse or access the relevant values. The current implementation's non-computing range splits are stronger than the minimum public guarantee and are not a new API commitment.

**Execution, retention, and lifetime**

Computations run synchronously on the accessing thread. `COMPUTE_ONCE` coordinates one slot; a shared collection callback may still execute concurrently for different slots. Waiting is uninterruptible and preserves an interrupt already present or received during the wait, on normal or abrupt completion. A computing function's own interruption handling is not constrained by that waiting rule. Cancellation, timeouts, and asynchronous execution are not provided.

This keeps access and exception propagation direct and avoids an executor policy. It also means that a blocking computation blocks its caller, and potentially other callers waiting for its result. Neither mode provides a general liveness guarantee.

`LazyCaches` factories strongly retain the computing function while any slot remains unresolved, and release the invocation reference when all slots have either a result or permanent failure. Mixed successful and failed slots count as terminal. A view's lifetime is assessed against the whole original storage, including slots outside that view. Declaration-site `LazyUpdaters.ofLocation` and `ofArray` updaters retain their computing functions for their lifetimes because new receivers or array elements can always arrive. Use-site updaters do not retain a computation between accesses; a callback supplied to an access can still be held while that invocation is in progress.

This rule bounds retention after completed initialization while ensuring that unresolved slots remain computable. It cannot guarantee garbage collection: values, keys, candidates, in-flight computations, and other application references may still retain the callback. `LazyCaches.ofScalar(Mode, Function)` does not otherwise retain its context, and the `LazyUpdaters` factories do not otherwise retain receivers or arrays; callbacks or results can themselves contain references. The updater's `SERIALIZED` mode uses weak receiver or selected-container-array keys for its auxiliary coordination state.

**Records and Valhalla: supported patterns and limits**

The discussion tested the derived-property use case for `record Point(int x, int y)`. That exact record cannot declare extra instance fields for `radius` and `phi`; see [JLS 8.10.2](https://docs.oracle.com/javase/specs/jls/se26/html/jls-8.html#jls-8.10.2). A static `ofScalar(Mode, Function)` would incorrectly share one slot across points. An ordinary class can declare per-instance holder or updater storage instead. Adding holders as record components would change its representation and generated equality; no transparent record cache facility or new language feature was implemented.

For the Valhalla value-class model examined here, a final field can reference a mutable lazy holder, and `ofScalar(Mode, Function)` with a noncapturing callback avoids capturing incompletely initialized `this`. A value-class instance field cannot itself serve as mutable storage for `LazyUpdaters.ofLocation` because value-class instance fields are final. A final field referencing a mutable array could in principle supply lazy array elements through `ofLocation` or pass that array directly to `ofArray`, subject to the value-class and array rules; this combination has not been validated here. The important cost is equality: holder or array references participate in value-object `==` and inherited `Object.equals`, so objects with otherwise equal application data may compare unequal. Custom `equals`/`hashCode` can define logical equality but cannot change `==`. These points concern the Valhalla model, whose [draft specification](https://cr.openjdk.org/~dlsmith/jep401/latest/) remains a separate language-development effort.

The benefit is a workable library pattern without adding language machinery. The costs are holder storage and possible equality consequences. The API does not make all capturing lambdas illegal, guarantee allocation-free value-class caching, or supply an indexed-relative collection shape whose ordinary `List.get` accepts an owning value object.

**Alternatives outside the adopted scope**

The distinctions in this table matter: absence from the current surface is not proof of permanent rejection.

| Alternative | Status in this work | Reason or unresolved tradeoff |
| --- | --- | --- |
| Lazy fields / cached methods as new language features | Discussed in Brian's background; not implemented here | Static and instance use cases do not divide cleanly; records and value classes add separate constraints |
| Public stability annotation or general stable VarHandle/MethodHandle toolkit | Not exposed by this API | The high-level entry point targets common usage; possible lower-level facilities remain separate work |
| Full coordinate family beyond array indices | Present in earlier sketches; the updater layer supports any feasible array rank, optionally through one owner field or a static array field, but no general indexed-relative coordinate type | A smaller API loses non-array context-plus-index use cases and arbitrary coordinate transformations |
| Context class-token overload | Explored, not adopted | Better inference cues versus extra signature surface |
| Further primitive specializations | Pre-canned use-site `int` and `long` accessors cover one-dimensional arrays, instance fields, and static fields; reference counterparts now cover those same shapes, but other primitive types are not provided here | Custom updater and callback interfaces remain available, while a larger built-in family would increase the API surface |
| Null as a successful lazy result | Not offered | Non-null retained values simplify the contract; absence needs another representation |
| Reset, invalidation, eviction, or per-argument memoization | Not provided | These require a lifecycle beyond one retained result per storage location |
| Explicit initialization-state query or non-computing peek | Not provided | Standard return interfaces expose access, not a new lifecycle protocol |
| Serialized retry in `LazyCaches` factories | Not provided; `LazyUpdaters.Mode.SERIALIZED` offers it for field and array-element storage | Requires waiting and auxiliary coordination state |
| Independent policy flags | Not provided | The design deliberately stops short of the full policy/storage product |
| `COMPUTE_ONCE` for caller-owned updaters | Not provided by `LazyUpdaters` | At-most-once computation with permanent failure would require sticky failure state in addition to the selected field or array element |
| Segment-backed `Lazy.ofSegment` | Experimental overloads removed from the earlier `Lazy` prototype | A segment-backed factory would need its own storage and lifetime contract |
| Cancellation, timeouts, executor-backed evaluation | Not provided | No scheduling or task-management policy is added to lazy access |
| Removal or deprecation of the earlier lazy APIs | Not performed | Migration and compatibility policy require a separate platform decision |

**Implementation and validation status**

The implementation work followed the specification reviews. It updated scalar, field, and collection publication; retained stable storage opportunities; added failure-preserving traversal and views; preserved original storage through map entries and key views; corrected equality exception propagation; and preserved publication through cached set size/hash results. Existing legacy factory behavior was kept on its existing paths.

The source layout keeps `java.util.LazyCaches` and `java.lang.invoke.LazyUpdaters` as public API facades that validate arguments and delegate to internal implementations. Scalar and collection behavior lives in `jdk.internal.util` (`LazySupport` and `LazyCollections`); field selection, updater coordination, and functional-interface adapters live in `jdk.internal.invoke` (`LazyUpdaterSupport` and `LazyUpdaterFactory`). The existing `List.ofLazy`, `Map.ofLazy`, and `Set.ofLazy` factories also use the internal collection implementation. Generated hidden classes may still implement updater interfaces; that adapter mechanism is separate from the dropped accessor-bytecode inspection. The caller-supplied lookup for `ofLocation` governs access to the selected field; `ofArray` has no caller-supplied lookup. One- and two-index updaters retain specialized call paths. Higher-rank updaters collect their indices into an `int[]` before traversal, with a potential per-access allocation cost.

The historical validation results below record earlier revisions under their then-current `Lazy` name and `java/util/Lazy` test path. Even the final rename-stage results precede the string-selector and `ofArray` redesign. They describe what was checked at each stage, not validation of the current updater contract.

The earlier validation sequence built `java.base` and passed 13 targeted jtreg test descriptions, comprising 821 successful JUnit invocations plus non-JUnit checks. Coverage included the new Lazy suites, legacy lazy-list/map/set tests, a negative compilation test, lookup/module tests, and MOAT collection checks extended to both coordinated modes. One new test file initially needed a generic assertion typing correction and then passed on a focused rerun; the combined results were all passing. Those recorded results predate the mode-interface and three-mode updater extension and do not validate that extension.

The preceding mode-interface variant was validated against the built JDK: the `java.base` and JDK API Javadoc builds passed; all 12 Lazy jtreg descriptions passed (376 JUnit executions), including compile-fail checks for the updater-only mode; and both `LazyUpdaters` jtreg descriptions passed (82 JUnit executions across normal and interpreter runs). The earlier MOAT run also passed; it was not repeated for that variant. Those results do not validate the independent enums or `SERIALIZED` retry behavior introduced by this revision.

The internal-package migration passed a clean JDK `images` build. On that build, 17 focused jtreg descriptions passed (904 JUnit executions), covering the independent modes, updater retry and field access, lazy factories and collections, and legacy list/map/set behavior. The throwable class-unloading and MOAT collection checks also passed as two additional jtreg descriptions.

After removal of the `Lazy.ofField` and `Lazy.ofSegment` overloads and their tests, and the updater-mode renaming, the pre-array revision passed the `java.base-java` and JDK API Javadoc builds. All 11 targeted jtreg descriptions passed: eight under `java/util/Lazy` and three under `java/lang/invoke/LazyUpdaters`, including 408 JUnit invocations. These runs establish the tested behavior, not a proof of all memory-model executions, measured allocation savings, or a performance comparison across VMs. The outstanding platform decisions remain adoption and release sequencing, the relationship to the earlier APIs, and whether workload evidence warrants any additional shapes or specializations.

For the earlier lambda-selector array-element extension, the `java.base-java` and JDK API Javadoc builds passed. All three `LazyUpdaters` jtreg descriptions passed, including 104 JUnit executions across normal and interpreter runs. Those tests covered one- and two-index direct and owner-field accessors, swapped indices, every primitive array-load opcode and reference elements, the three updater modes, aliasing of inner arrays, reserved-value retries, bounds and null failures, and lookup checks. The `java/util/Lazy` tests were not rerun for that extension, which did not change the `Lazy` implementation.

In that earlier lambda-selector design, index-only static-array access was folded into `ofLocation` and an earlier, different `ofArray` factory was removed. The updated JDK image and JDK API Javadoc build passed. The three focused `LazyUpdaters` jtreg descriptions (`OfStaticArrayLocationTest`, `OfLocationTest`, and `FindVarHandleTest`) passed with 130 JUnit invocations across normal and interpreter runs. They covered static one- and two-index accessors, reversed indices, the default and explicit modes, lookup checks, and the existing receiver-based forms. The present `ofArray` factory has different parameters and semantics.

For the `LazyCaches` and `ofScalar` rename, the JDK image and JDK API Javadoc builds passed. All eight jtreg descriptions under `test/jdk/java/util/Lazy` passed (326 JUnit invocations), as did the MOAT collection test and the `LazyUpdaters` mode-mismatch compile-fail test. The test directory retains its historical `Lazy` name; its source now exercises `LazyCaches`. These results also precede the current selector redesign.

For the initial string-selector and direct-array design, before `ofArray` dropped its lookup parameter, the `java.base-java`, full JDK `images`, and JDK API Javadoc builds passed. All four focused `LazyUpdaters` jtreg descriptions and the updated `LazyDemo` passed, including 101 JUnit invocations across normal and interpreter runs and the mode-mismatch compile-fail check. The tests cover named instance and static fields, direct and field-backed arrays, row-valued selections, natural index order, the three modes, lookup access, inherited fields reached through public subclasses and interfaces, ambiguous-name rejection, and distinct static fields with the same binary name in separate class loaders. An initial static-array test expected an access error from `publicLookup`, but that lookup could not load the application class; the test was corrected to use a restricted lookup in the same loader and passed on rerun. These results validate the focused behavior, not performance claims or all possible memory-model executions.

After removing the lookup parameter from `ofArray`, the `java.base-java` and JDK API Javadoc builds passed. All four focused `LazyUpdaters` jtreg descriptions passed, including 101 JUnit executions across normal and interpreter runs; a test-only naming cleanup was followed by another successful `OfLocationTest` run. The suite now covers a direct array with a private functional interface and a private element type, while retaining `ofLocation` lookup-access tests. These checks do not establish performance or all possible memory-model executions.

For the arbitrary-rank extension, the `java.base-java` and JDK API Javadoc builds passed. All four focused `LazyUpdaters` jtreg descriptions passed, including 115 JUnit executions across normal and interpreter runs. New tests cover three- and four-index direct access, three-index instance and static array fields, higher-rank partial indexing, aliases, failures before computation, retries after a reserved result, and rejection when the index count exceeds array rank. These are representative ranks, not an exhaustive test of every JVM-legal method arity or memory-model execution.

Before adding the reference pre-canned interfaces, the declaration-site and use-site design passed the JDK API Javadoc build and all five `LazyUpdaters` jtreg descriptions, including 153 JUnit executions across normal and interpreter runs. The use-site tests covered direct and field-backed arrays, static and instance fields, the six pre-canned `int` and `long` shapes, all three modes, shared physical-element coordination, generic receiver specialization, callback validation, reference subtype results, primitive widening, and diagnostics for erased generic interface tokens and storage coordinates. This validates the exercised behavior at that stage, not the subsequently added reference interfaces, performance, or every memory-model execution.

With the three reference pre-canned interfaces, the `java.base-java` and JDK API Javadoc builds passed. All five `LazyUpdaters` jtreg descriptions passed, including 167 JUnit executions across normal and interpreter runs. The added tests cover all three updater modes for direct reference arrays and static and instance reference fields, cached and reserved-null results, array covariance, raw generic-token diagnostics, incompatible field types, and matching or mismatching parameterized field types through direct and indirect specializations, including wildcard bounds. These tests do not establish performance or every memory-model execution.

After requiring an explicit updater `Mode` and moving `@PreviewFeature` to the `LazyUpdaters` class, the `java.base-java` and JDK API Javadoc builds passed. All five `LazyUpdaters` jtreg descriptions and `LazyDemo` passed, comprising 169 JUnit executions across normal and interpreter runs. A client compilation using the built JDK rejected `LazyUpdaters` without preview enabled and succeeded with `--enable-preview`. These checks validate the migrated call sites and preview boundary, not source or binary compatibility with the removed overloads.

For the current documentation reconciliation, the JDK API Javadoc build passed, followed by all five `LazyUpdaters` and eight `LazyCaches` jtreg descriptions (494 JUnit executions). All 13 examples in the two class Javadocs were compiled individually and exercised with the built preview JDK. A separate value-class probe confirmed the documented rejection timing for null-restricted reference fields and array elements in all three updater modes. These checks establish that the examples are usable and the focused regressions pass; they do not prove every memory-model execution or measure performance.
