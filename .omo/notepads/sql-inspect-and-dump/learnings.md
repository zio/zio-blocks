## Task 5: Docs — Inspecting SQL + Compile-time SQL Dumps

The `sql` module exposes two query builder APIs at different abstraction levels:
- `zio.blocks.sql.SqlQuery`: legacy builder with `explain(dialect)`, `statement(dialect)`, `toFrag(dialect)` methods
- `zio.blocks.sql.query.SqlQuery`: newer query IR with `sql(dialect)`, `toFrag(dialect)` methods (uses `Rel` for joins, `Frag` for filters)

`explain(dialect)` renders SQL with `?N` parameter placeholders and a trailing comment with parameter types. `statement(dialect)` returns a structured `SqlStatement` with decomposed source, joins, filters, groupBy, orderBy, limit, offset fields.

`Dump` is an inline macro object that emits `.sql` files at compile time when `-Dzib.sql.dumpDir` is set. Three entry points: `dumpTable` (DDL), `dump` (legacy query builder), `dumpQuery` (query IR). Files are named `<owner>-<dialect>.sql` with content-hash skip (no write if identical). Owner name is derived from the enclosing symbol at the call site.

`previewSql()` is available on `SmallMigrator` and `LargeMigrator` in the `data-migration` module. Returns `Vector[String]` of all SQL statements (DDL, triggers, templates) without opening a database connection.

The docs project (`zio-blocks-docs`) depends on `sql.jvm` so all SQL types are available for mdoc compilation. However, mdoc has a pre-existing failure unrelated to our changes — the output files are generated correctly before the process exits with non-zero code.

No dedicated "transactions guide" exists in the docs. Transaction-related content lives in reference pages (`transactor.md`, `db-tx.md`, `transactor-zio.md`). The inspecting SQL and dumps sections fit naturally in the `query-dsl-sql.md` guide since they are query-related features.
