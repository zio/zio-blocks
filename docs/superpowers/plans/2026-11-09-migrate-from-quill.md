# Plan: "Coming from Quill" (M-15)

## Problem

Quill users have a data-access layer built on quoted Scala queries that the compiler turns into SQL. They need to know what each Quill construct becomes in the ZIO Blocks `sql` module, and which ones have no counterpart.

## Goal

A migration guide, `docs/guides/migrate-from-quill.md`, modelled on `migrate-from-magnum-or-doobie.md` (page shape `one-program`), with one companion example, `sql-examples/src/main/scala/migratefromquill/CompleteExample.scala`. Domain: a clinic's patients and appointments (not books).

## Concept map (Quill → ZIO Blocks)

| Quill | ZIO Blocks |
| --- | --- |
| `SqliteJdbcContext(SnakeCase, ds)` | `JdbcTransactor.fromUrl(url, SqlDialect.SQLite)` |
| naming strategy `SnakeCase` | snake_case default, `@Modifier.rename`, `SqlNameMapper` |
| `query[A]`, `querySchema` | `Table.derived[A]`, `@Modifier.config("sql.table_name", ...)` |
| quoted query + `run` | `sql"..."` + `Frag#query`, or `SqlQuery` |
| `filter/sortBy/take/drop` | `SqlQuery#where/orderBy/limit/offset` |
| joins | `Rel` + `SqlQuery#join` |
| `lift(x)` | `$x` in `sql"..."` |
| `insertValue/updateValue/delete` | `Repo#insert/update/delete` |
| `returningGenerated` | `Frag#updateReturningKeys` |
| `liftQuery(xs).foreach(...)` | `Repo#insertBatch`, `Repo#insertAll` |
| `infix` / `sql"..."` raw SQL | `sql"..."`, `SqlLiteral` |
| `MappedEncoding` | `DbCodec#transform` |
| `ctx.transaction` | `transactor.transact` |
| `quill-jdbc-zio` layers | `zio-blocks-sql-zio`: `TransactorZIO.layer` |

## What has no equivalent (to say plainly)

- Quill builds SQL at compile time from quoted Scala and its compile-time validation; ZIO Blocks builds SQL at run time from `sql"..."` or `SqlQuery`, which take SQL strings and `Frag`s, not Scala predicates.
- `sql(table)` checks table and column names only, no types; the guide says exactly how that compares.
- Only PostgreSQL and SQLite dialects; no creator entities; `Table#createTable` emits no `PRIMARY KEY` (a known bug in 0.0.56).

## Sections

1. The Problem (Quill "before" code, plain fence, not compiled)
2. Prerequisites
3. Concept Map
4. The Clinic (shared domain, mdoc)
5. Contexts and Naming Strategies
6. Quoted Queries and `run`
7. Custom Table and Column Names
8. Inserts, Updates and Deletes
9. Generated Keys
10. Batch Actions
11. Joins
12. Raw SQL
13. Custom Column Types
14. Transactions
15. ZIO Integration
16. Compile-Time SQL: What Doesn't Carry Over
17. What You Gain and What's Different
18. Migration Checklist
19. Putting It Together, Running the Examples, Going Further

## Verification

Quill snippets: each compiled and run once with scala-cli against the real Quill artifact in a scratch directory outside the repo. ZIO Blocks code: mdoc on the page and `sbt "runMain migratefromquill.CompleteExample"` in `sql-examples`.
