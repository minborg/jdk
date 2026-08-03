# Third-party lazy use-site analysis

This directory contains the evidence and proposed document revision for the chapter “Lazy use-sites in third-party code”. The study applies the document's decision graph to 116 scalar-cache protocols in ten prominent Java projects, using source revisions checked out on 31 July 2026.

## Deliverables

- `lazy_initialization_and_memoization_v4.html` — a copy of the supplied v3 document with the proposed chapter inserted after the JDK analysis.
- `third_party_lazy_sites.csv` — the row-level inventory, with pinned commit, source location, grouped site count, classification, static status, `LazyConstant` compatibility, and rationale.
- `LazyCandidateScanner.java` — the recall-oriented AST scanner used to identify candidates. It intentionally over-reports; manual source review determines every classification and compatibility result.

## Method

The unit of analysis is one scalar-cache publication protocol: one published object, even when it is a fully populated array, list, or map. Independent cached accessors count separately. Fields initialized together as one transaction count once.

The sample includes production Java sources and excludes tests, benchmarks, generated sources, examples, per-key caches, eager constants, and copied compatibility implementations. It is balanced at no more than 15 protocols per project, stratified across packages and mechanisms. Consequently, the results describe the semantic variety and applicability found in third-party code; they are not an exhaustive ecosystem census or a population-frequency estimate.

Each site follows the document's decision graph:

1. If every initializer attempt cannot safely run more than once, classify it as lazy initialization.
2. Otherwise, if callers require one established value, classify it as strong memoization.
3. Otherwise, classify it as weak memoization.

For strong and weak memoization, `lazy_constant_compatible_memo_sites` counts sites whose initializer does not fail in ordinary operation or whose failure is deterministic from immutable input. A zero means sticky failure could change behavior because a later retry might succeed. Catastrophic failures such as `OutOfMemoryError` are excluded, consistently with the source document.

## Aggregate result

| Project | Sites | Lazy initialization | Strong memoization | Weak memoization | Memo compatible with `LazyConstant` | Static lazy sites |
|---|---:|---:|---:|---:|---:|---:|
| Guava | 15 | 0 | 3 | 12 | 13 / 15 | 0 / 0 |
| Jackson Databind | 15 | 8 | 0 | 7 | 7 / 7 | 0 / 8 |
| Caffeine | 15 | 0 | 0 | 15 | 15 / 15 | 0 / 0 |
| SLF4J API | 5 | 1 | 1 | 3 | 3 / 4 | 1 / 1 |
| Logback | 13 | 8 | 1 | 4 | 5 / 5 | 0 / 8 |
| JUnit 5 | 7 | 1 | 0 | 6 | 1 / 6 | 0 / 1 |
| Mockito | 8 | 1 | 0 | 7 | 6 / 7 | 1 / 1 |
| RxJava | 8 | 4 | 0 | 4 | 4 / 4 | 3 / 4 |
| Netty | 15 | 3 | 0 | 12 | 11 / 12 | 0 / 3 |
| Spring Framework | 15 | 9 | 0 | 6 | 4 / 6 | 1 / 9 |
| **Total** | **116** | **35** | **5** | **76** | **69 / 81** | **6 / 35** |

## Re-running candidate discovery

Compile the scanner with the JDK under study, then pass it a checked-out repository root:

```shell
javac LazyCandidateScanner.java
java LazyCandidateScanner /path/to/repository > candidates.tsv
```

The TSV is a review queue, not a classification. Reproducibility of the final evidence comes from the pinned commits and source locations in the CSV.
