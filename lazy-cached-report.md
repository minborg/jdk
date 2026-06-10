# Lazy Constant, Lazy Collection, and Cached Method Analysis

2026-06-10, Per Minborg

## Executive Summary

An extensive analysis of the applicability of the semantic categories
`LazyConstant` and Lazy Collections (library APIs), and Cached Methods
(a proposed language feature), was made using the corpus of:

 * The JDK itself [[1]](lazy-cached-candidates-report.md), and
 * Prominent (*) open-source libraries [[2]](openlib-lazy-cached-candidates-report.md).

The two analyses can be summarized in this table:

| Scope                  | LazyConstant | Lazy Collections | Cached Methods |
|------------------------|-------------:|-----------------:|---------------:|
| JDK                    |           65 |               13 |             95 |
| Open-source libraries  |           44 |              135 |             64 |
| **Grand total**        |      **109** |          **148** |        **159** |

_Table 1, showing the distribution of the semantic categories across use sites_

The same distribution is shown in the following graph:

```text
JDK
LazyConstant       | #############                    65
Lazy Collections   | ###                              13
Cached Methods     | ###################              95

Open-source libraries
LazyConstant       | #########                        44
Lazy Collections   | ###########################      135
Cached Methods     | #############                    64

Grand total
LazyConstant       | ######################           109
Lazy Collections   | ##############################   148
Cached Methods     | ################################ 159
```
_Graph 1, showing the distribution of the semantic categories across use sites_

The analyses suggest that all three semantic categories are real and recurring.
Under the current design assumption, this supports retaining `LazyConstant` and
lazy collections as library APIs, while treating Cached Methods as the distinct
language feature for racy scalar memoization. In aggregate the categories are of
similar magnitude, although their distribution differs substantially by corpus.

(*) The open-source corpus covered 15 libraries, including JavaFX, Guava, and Kafka.
See [[2]](openlib-lazy-cached-candidates-report.md) for details.

## Key Findings

`LazyConstant` fits sites where at-most-once initialization is semantically important:
provider discovery, security/native state, global singletons, holder-class idioms, stable
property snapshots, and resource-backed constants. The JDK has many strong examples in
`java.base`, `java.desktop`, `java.management`, `java.rmi`, `java.xml`, and
crypto/security modules.
Open-source examples include JavaFX CSS converter holders, Hadoop/YARN metrics singletons,
Tomcat/Netty holders, and Lucene analyzer resources.

Lazy Collection/Table fits keyed or indexed lazy state. This category is small but
important in the JDK, with examples like method-handle tables, `VarHandle` tables, vector
operator tables, and locale/provider caches. It is much larger in open-source libraries:
Hibernate metadata caches, Hadoop LoadingCaches, Kafka broker/client/session maps, Guava
collection views, Spark KV/network caches, Jetty/Log4j registries, and Vaadin
feature/resource maps.

Cached Method fits pure or effectively pure derived values where duplicate computation is
acceptable and exceptions should retry. Strong examples include JDK MethodType, reflection
metadata, path/string/hash caches, OpenMBean metadata, HotSpot memoized primitive
utilities, Guava @LazyInit derived views, JavaFX hash-code caches, JUnit reflective
selectors, and Spark BestEffortLazyVal, which closely matches the proposed racy-CAS
semantics.

## Notable Differences

The JDK report is scalar-heavy: 95 cached-method candidates and 65 LazyConstant
candidates, with only 13 lazy collection/table candidates.

The open-source report is collection-heavy: 135 lazy collection/table candidates, more
than `LazyConstant` and Cached Method combined. This suggests external frameworks rely
heavily on keyed lazy state and cache tables, while the JDK has more pure scalar
memoization and singleton/provider initialization.

## Design Implications

The reports support keeping the concepts semantically separate:

 * `LazyConstant`: at-most-once, stable initialization, no duplicate side effects.
 * Lazy collections/tables: keyed or indexed laziness that scalar cached methods should not
try to model.
 * Cached Methods: racy memoization, possible duplicate computation, exception retry.

## Note

Many apparent candidates were excluded because they involve invalidation, lifecycle reset,
mutable configuration, native handles, security/session caches, public setters, eviction,
or cleanup requirements.

## References

[[1]](lazy-cached-candidates-report.md) JDK Analysis

[[2]](openlib-lazy-cached-candidates-report.md) Open-source Analysis