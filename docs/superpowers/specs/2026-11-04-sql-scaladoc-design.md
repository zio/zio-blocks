# sql and sql-zio Scaladoc design

Follows `docs/superpowers/specs/2026-11-04-typeid-scaladoc-design.md` and the `scaladoc-rollout` process.

## Problem

The `sql` module has comments on 127 of 405 public API members (31%): package `zio.blocks.sql` 119 of 369, `zio.blocks.sql.query` 8 of 36. `sql-zio` has 7 of 14 (50%). Nobody has checked that the existing comments are true; the `chunk`, `scope`, `config` and `typeid` rollouts found that 19%, 31%, 36% and 24% of inherited comments were wrong or imprecise. Thirteen `{{{` blocks in nine files are never compiled, and none of them compiles on its own (they use an undefined `transactor`, `User`, `url` or `dialect`, or are fragments).

The example checker cannot take these modules as it is. `scaladoc-check` builds on Scala 2.13.18, 3.9.0 and 3.10.0, but `sql` and `sql-zio` are built for 3.9.0 and 3.10.0 only. And when `sql-zio` is on the classpath, `import zio.blocks.sql._` brings the package `zio.blocks.sql.zio` into scope as `zio`, so a later `import zio.blocks.schema._` fails with `value blocks is not a member of zio.blocks.sql.zio` (probed against 0.0.56; a known bug in `library-issues.md`). `import zio.blocks.sql.{zio => _, _}` compiles.

## Goal

Every public member of `sql` and `sql-zio` has a true, short, useful comment. The tier-1 types have an overview, compiled examples and a reference-page link. CI compiles every example on Scala 3.9.0 and 3.10.0. `--drift` stays empty.

## Decisions (Milad, 2026-11-04)

- **Checker: a generic Scala-3-only project, `scaladoc-check-scala3`.** It builds on 3.9.0 and 3.10.0 only and compiles the examples of the modules in a new list, `scaladocCheckedScala3Modules` (`sql.jvm`, `sql-zio`). It shares its settings with `scaladoc-check` through one function, so nothing is copied; the extractor unit tests stay in `scaladoc-check`. Later Scala-3-only modules (`projection`, `htmx`, `datastar`) join by one list entry. CI runs it in `testJVMScala3Command`; the root aggregate lists it.
- **Wrapper import for `sql`:** `ScaladocSnippets.moduleImport` returns `zio.blocks.sql.{zio => _, _}` for the `sql` module, so `sql` examples compile as for a user without `sql-zio`. `sql-zio` keeps the default `zio.blocks.sql.zio._`; its examples put their own `import zio.blocks.sql._` after every other `zio` import, or use `_root_.zio`.
- **No markers.** The checker skips the whole module on 2.13, so examples need no `// scala-3-only`; Scala 3 syntax (`given`, `using`, `summon`, opaque types) is fine, braces are still required.
- **Tier 1 (12 types):** `Frag` and the `sql"..."` interpolator, `Transactor`, `JdbcTransactor`, `DbCon`, `DbTx`, `DbCodec`, `Table`, `Repo`, `SqlQuery`, `Rel`, `TransactorZIO`. Every other public member is tier 2 (with `@see` to its reference page when one exists) or tier 3.
- **AGENTS.md:** two downstream rows (`sql*`, `sql-zio`) and one appended paragraph in the Scaladoc bullet (diff approved as shown).
- **One PR.**

## Layout

- `sql/shared/src/main/scala/zio/blocks/sql/` (28 files) and `.../sql/query/` (6), `sql/jvm/src/main/scala/zio/blocks/sql/` (5: `JdbcConnection`, `JdbcParamWriter`, `JdbcResultReader`, `JdbcTransactor`, `PgCodec`). `sql/js/` has no main sources, so there are no jvm/js copies to keep identical.
- `sql-zio/src/main/scala/zio/blocks/sql/zio/` (`TransactorZIO`, `JdbcTransactorLayers`).
- Packages `zio.blocks.sql`, `zio.blocks.sql.query`, `zio.blocks.sql.zio`. Both modules are on `/api`.
- Reference pages: `docs/reference/sql/` (23 pages, `https://zioblocks.com/reference/sql/<page>/`).
- `main` differs from `v0.0.56` only in two comment lines of `Frag.scala`, so every probe runs against the 0.0.56 release.
- No `sql` test reads source text, so a comment change cannot fail the module's own tests.
- `JoinsDumpExample` is a test fixture published in the main jar by mistake (a known bug); its comment says so briefly.

## Tasks

- **Tooling (controller):** the new project, the shared settings function, the `moduleImport` exception and its extractor test, the CI command and aggregate, the style guide's module list, AGENTS.md.
- **Task 1 (Opus), "connections, transactions and fragments":** `Frag`, `SqlInterpolator` (`sql"..."`, `DbParam`), `SqlLiteral`, `Transactor`, `JdbcTransactor`, `DbCon`, `DbTx`, `DbConnection`, `TransactionIsolation`, `SqlLogger`, the jvm `Jdbc*` files, `PgCodec`, `SqlIdentifierChecker`, `SqlIdentifier`, `SqlMacros`, `SqlValidator`, and both `sql-zio` files. Tier 1 there; make the blocks there compile; fact-check every existing comment in those files.
- **Task 2 (Opus), "mapping, schema and queries":** `DbCodec`, `DbCodecDeriver`, `DbCodecOpaqueMacro`, `DbCodecTupleMacro`, `DbValue`, `Table`, `TableMetadata`, `Repo`, `Upsert`, `Ddl`, `Dump`, `Dialect`, `SqlDialect`, `SqlNameMapper`, `JoinsDumpExample`, and `query/*`. Same work. Runs alongside Task 1 but runs sbt only after Task 1 hands back.
- **Task 3 (Sonnet):** tier-2 and tier-3 gaps the coverage report still lists after Tasks 1 and 2.
- **Review:** two fresh-context Opus reviewers on the Task 1 and Task 2 file groups (each re-runs its share of bug reproducers and samples untouched comments), one fix round, a Sonnet re-review of the fix diff.

## Gates

1. `++3.9.0; scaladoc-check-scala3/test; ++3.10.0; scaladoc-check-scala3/test`, and `++3.9.0; scaladoc-check/test; ++3.10.0; scaladoc-check/test; ++2.13.18; scaladoc-check/test` (tooling changed).
2. `sqlJVM/doc`, `sqlJS/doc` and `sql-zio/doc` on 3.9.0 and 3.10.0: no new warnings against the base.
3. `python3 -I .claude/scripts/scaladoc-coverage.py sql` (405/405) and `sql-zio` (14/14); `--drift` empty for both.
4. `comments-only.py` against the base for `sql` and `sql-zio`.
5. `++2.13.18; check; headerCheckAll; ++3.9.0; check; headerCheckAll`; `scalafmtSbt`/`scalafmtAll` for `build.sbt`.

## Out of scope

Library code changes, reference-page rewrites (errors go to `docs-issues.md` entries via the coordinator), `docs/dev/` files other than the style guide's module list, other modules.
