# Compile the code examples on the resource-management pages

Plain `scala` fences are never compiled by mdoc. This plan lists every plain fence on the six pages (line numbers on main `02856d333`) and what happens to it. Totals: 63 plain fences before, 48 after (wire 17 to 8, resource 19 to 18, scope 18 to 13; the other three pages unchanged). The five deliberate-error fences stay plain: `mdoc:fail` also compiles the earlier `compile-only` blocks of the page, whose repeated names clash.

Rules used: a bare signature listing (`def f[A](...): B` with no body, or a `trait` stub) stays plain; an sbt `libraryDependencies` line stays plain; a runnable example becomes `mdoc:compile-only` (self-contained) or `mdoc:silent` (later blocks reuse it); a deliberate compile error becomes `mdoc:fail` when the page allows it, else stays plain with the error named in prose.

## wire.md (17 plain)

| Line | Content                                  | Decision                                                                 |
| ---- | ---------------------------------------- | ------------------------------------------------------------------------ |
| 8    | `sealed trait Wire` signatures           | keep plain: signature listing                                            |
| 46   | Motivation: classes + manual wiring      | `mdoc:silent` (line 77 reuses the classes)                               |
| 77   | `Resource.from[App](Wire(Config))`       | `mdoc:compile-only`                                                      |
| 101  | sbt dependency                           | keep plain: build line                                                   |
| 107  | sbt `%%%` dependency                     | keep plain: build line                                                   |
| 121  | `Wire.shared[Database]` example          | `mdoc:compile-only`                                                      |
| 271  | diamond pattern                          | `mdoc:compile-only`                                                      |
| 315  | `trait Wire` isShared signature          | keep plain: signature                                                    |
| 324  | isShared/isUnique with `println`         | `mdoc` (real output replaces the `// true` comments)                     |
| 340  | `shared`/`unique` signature              | keep plain: signature                                                    |
| 349  | convert between strategies               | `mdoc` (real output)                                                     |
| 369  | `toResource` signature                   | keep plain: signature                                                    |
| 377  | `toResource` example                     | `mdoc:compile-only`                                                      |
| 399  | `trait Wire.Shared` make signature       | keep plain: signature (not valid Scala)                                  |
| 435  | macro derivation, all three features     | `mdoc:compile-only`                                                      |
| 469  | subtype conflict: error then fix         | error half: `mdoc:fail` if the page allows it, else plain; fix: compile-only |
| 484  | `Resource.from` integration              | `mdoc:compile-only`                                                      |

## resource.md (19 plain)

Lines 8, 73, 121, 308, 333, 356, 384, 430, 458, 490, 532, 551, 579, 609, 620, 671: signature listings, keep plain. Lines 38 and 44: sbt, keep plain. Line 712: `def serviceResource = Resource.from[Service](...)` is a fragment that needs `Config`, `Logger`, `Service`; convert to `mdoc:compile-only` with the three classes added.

## scope.md (18 plain)

| Line      | Content                                        | Decision                                                                   |
| --------- | ---------------------------------------------- | -------------------------------------------------------------------------- |
| 15, 288, 422, 473, 552 | signature listings              | keep plain                                                                 |
| 57        | sbt                                            | keep plain                                                                 |
| 105       | cross-scope `$` mismatch (does not compile)    | `mdoc:fail` if the page allows it, else plain                              |
| 121       | `lower` into a child scope                     | `mdoc:compile-only`, with `Database` defined                               |
| 137       | `$` with a non-literal (does not compile)      | `mdoc:fail` if possible, else plain                                        |
| 145       | hypothetical smuggling                         | keep plain: pseudocode ("hypothetical if the macro didn't require...")    |
| 164, 175  | returning a scoped value / closure             | `mdoc:fail` if possible, else plain                                        |
| 187       | correct `$(db)(_.query)`                       | `mdoc:compile-only`                                                        |
| 977       | scoped skeleton `// ... usage or error ...`    | keep plain: skeleton                                                       |
| 990, 1002, 1048, 1057 | fragments using an outer `db`      | keep plain: fragments of the skeleton at 977 (error lines and one-liners)  |

## finalization.md (5), finalizer.md (3), unscoped.md (1)

All are signature stubs (`def isEmpty: Boolean` with no body, `trait Finalizer`, `trait Unscoped[A]`). Keep plain.
