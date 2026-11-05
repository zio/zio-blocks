# sql and sql-zio Scaladoc Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to run the tasks. Checkbox (`- [ ]`) steps track progress. The process is the `scaladoc-rollout` skill.

**Spec:** `docs/superpowers/specs/2026-11-04-sql-scaladoc-design.md`

## In plain English

**The problem.** The `sql` module talks to databases: you write a query with `sql"..."`, run it inside a transaction, and map rows to case classes. Only 31% of its public members have comments (50% in `sql-zio`, its ZIO adapter), nobody has checked that those comments are true, and the 13 code examples in comments are never compiled. The build's example checker can't even take the module: the checker also builds on Scala 2.13, and `sql` is Scala 3 only.

**The goal.** Every public member has a true, short comment. The 12 types a user meets first have an overview, compiled examples and a link to their reference page. CI compiles every example on Scala 3.9 and 3.10.

**The steps.**
1. **Tooling.** Add a second checker project, `scaladoc-check-scala3`, that builds on Scala 3 only and compiles the `sql` and `sql-zio` examples. It shares its settings with the existing checker. Give `sql` examples an import that hides the `zio` sub-package (a known shadowing bug), with a unit test. Update the style guide and AGENTS.md.
2. **Connections, transactions and fragments (Opus).** A strong-model agent documents `Frag`, `sql"..."`, `Transactor`, `JdbcTransactor`, `DbCon`, `DbTx`, `TransactorZIO` and their neighbours, makes their examples compile, and checks every existing comment in those files against the code.
3. **Mapping and queries (Opus, alongside step 2).** A second agent does the same for `DbCodec`, `Table`, `Repo`, `SqlQuery`, `Rel` and their neighbours. It waits for step 2 before running sbt, because only one sbt build may run at a time.
4. **Gaps (Sonnet).** A cheaper-model agent writes the short comments still missing.
5. **Review.** Two fresh agents test every changed claim against the code and the published 0.0.56 library with SQLite; the authors fix what they find; a last agent re-checks only the fixes.
6. **Ship.** One pull request. Bugs are not fixed here: each is written up with a reproducer and sent to the docs-backlog coordinator.

## Global Constraints

Full text: `.superpowers/sdd/2026-11-04-sql-scaladoc/global-constraints.md` (skill template with `sql` filled in, plus the sql-specific rules).

## Task 0 (controller): tooling

**Files:** `build.sbt` (shared checker settings, `scaladocCheckedScala3Modules`, `scaladoc-check-scala3`, `testJVMScala3Command`, root aggregate, the checker comment), `project/ScaladocSnippets.scala` (`moduleImport` exception for `sql`), `scaladoc-check/src/test/scala/ScaladocSnippetsSpec.scala` (one test), `docs/dev/scaladoc-style.md` (module list and the Scala-3 checker command), `AGENTS.md` (approved diff).

- [ ] Step 1: write the build and extractor changes and the test.
- [ ] Step 2: run `scaladoc-check/test` on 3.9.0, 3.10.0, 2.13.18 (green) and `scaladoc-check-scala3/test` on 3.9.0 (expected to fail only on the 13 existing blocks).
- [ ] Step 3: commit (`build: compile sql and sql-zio Scaladoc examples in scaladoc-check-scala3`), then `docs: ...` for the style guide and AGENTS.md.

## Task 1 (Opus): connections, transactions and fragments

**Files:** `sql/shared/.../sql/{Frag,SqlInterpolator,SqlLiteral,Transactor,DbCon,DbTx,DbConnection,TransactionIsolation,SqlLogger,SqlIdentifierChecker,SqlIdentifier,SqlMacros,SqlValidator}.scala`, `sql/jvm/.../sql/*.scala` (5), `sql-zio/.../sql/zio/*.scala` (2).
**Tier 1 and key methods:** `Frag` (`sql`, `query`, `queryOne`/`queryOption` as present, `update`, `queryStream`, `queryChunked`, `++`, `Frag.const`/`empty`/join helpers as present, `keysetAfter`), `sql"..."` interpolator and `DbParam`, `Transactor` (`connect`, `transact`, isolation overloads), `JdbcTransactor` (construction: `fromUrl`, data source, logger), `DbCon`, `DbTx` (nested transactions, `transactNested`), `TransactorZIO` (`connect`/`transact` as effects, layers, `JdbcTransactor` layer extensions).
**Reference pages:** `frag.md`, `transactor.md`, `transactor-zio.md`, `db-con.md`, `db-tx.md`, `db-connection.md`, `db-param.md`, `sql-logger.md`, `index.md`.
**Known issues to reflect** (coordinator copy `/home/milad/sources/scala/zio-blocks-worktrees/tutorials-and-guides-lists/docs/dev/library-issues.md`): `queryStream` throws on too few columns (l.2366); `transactZIO` commits when its body throws while being built (2483); `zio` package shadowing (2560); `Frag.keysetAfter` unquoted column (2600); no way to keep a connection open past `connect`, `queryStream` (14067); `sql"..."` doesn't process escapes (14152); `List`/`Set` interpolated binds as one JSON string (14205); newtype with private constructor as a parameter (12043); identifier check suggests a keyword (12333); PostgreSQL `jsonb` insert needs `stringtype` (11975, for `PgCodec`).

- [ ] Step 1: research (source, tests, reference pages), then make the blocks in these files compile.
- [ ] Step 2: tier 1, then fact-check table of every existing comment in these files (member, claim, kind, evidence).
- [ ] Step 3: gates, one commit per file group, report and issues file.

## Task 2 (Opus): mapping, schema and queries

**Files:** `sql/shared/.../sql/{DbCodec,DbCodecDeriver,DbCodecOpaqueMacro,DbCodecTupleMacro,DbValue,Table,TableMetadata,Repo,Upsert,Ddl,Dump,Dialect,SqlDialect,SqlNameMapper,JoinsDumpExample}.scala`, `sql/shared/.../sql/query/*.scala` (6).
**Tier 1 and key methods:** `DbCodec` (derivation, `columns`, read/write, opaque and `As`-based codecs, tuple codecs), `Table` (`derived`, `name`, `columns`, `createTable`/DDL helpers as present), `Repo` (`derived`, `insert`, `findById`, `findAll`, `update`, `delete`, `upsert` as present), `SqlQuery` (`from`, `where`, `join`, `orderBy`, `groupBy`, `limit`, `offset`, rendering, running), `Rel` (`manyToOne`, `oneToMany`).
**Reference pages:** `db-codec.md`, `db-codec-deriver.md`, `db-value.md`, `db-result-reader.md`, `db-param-writer.md`, `table.md`, `table-metadata.md`, `repo.md`, `ddl.md`, `dump.md`, `sql-dialect.md`, `sql-name-mapper.md`, `sql-query.md`, `rel.md`.
**Known issues to reflect:** tuple rows read repeated column names from the first table (2655); `Dump.dumpQuery` drops `?` and unnamed dumps (2719); `Table.derived`/`Repo.derived` flatten a nested field (5626); `Option` field of a transform newtype or enum fails on NULL (5686); SQLite `Instant` column can't be read back (5745); `Option[BigDecimal]`/`Option[Char]` throw on NULL (9007); `Rel` ignores `@Modifier.rename` (9098); `SqlQuery#offset` without `limit` (9143); `Dump.dumpTable` ignores rename (9189); `Rel` accepts any selector (9259); `SqlQuery#where` joins with `AND` without parentheses (9345); `orderBy`/`groupBy` only name `FROM`-table columns (11187); `Option` field of a nested case class rejected (12082); `JoinsDumpExample` published in the jar (14532).

- [ ] Step 1: research, then make the blocks in these files compile (after Task 0).
- [ ] Step 2: tier 1, then fact-check table of every existing comment in these files.
- [ ] Step 3: gates (only after the controller says Task 1 has finished with sbt), one commit per file group, report and issues file.

## Task 3 (Sonnet): tier 2 and 3 gaps

- [ ] Step 1: list every member the coverage script still reports undocumented.
- [ ] Step 2: write comments; probe anything behavioural; no absolute words unless verified.
- [ ] Step 3: gates, commit, report.

## Task 4: review and ship

- [ ] Two Opus fact-check reviews (fresh context, Task 1 files + tooling; Task 2 files), fix round (authors resumed), Sonnet scoped re-review.
- [ ] Merge origin/main, re-run the gates, push `khajavi sql-scaladoc`, open the PR, watch CI to green.
- [ ] Report coverage before/after, the bug entries, the §13f text and the metric to the coordinator.
