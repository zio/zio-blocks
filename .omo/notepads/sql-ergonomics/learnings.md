`DbParam.fromDbCodec` lets `sql"..."` interpolation accept any type with only a `DbCodec[A]` in scope, including wrapper/opaque types that encode to a single column. The regression test covers `ProductId` and confirms the interpolated fragment renders one placeholder with `DbValue.DbString("abc")`.

`JdbcTransactor.postgresLayer`, `.sqliteLayer`, and `.h2Layer` are thin `ZLayer.fromFunction` helpers over `JdbcTransactor.fromDataSource`, so ZIO apps can expose `Transactor` from a JDBC `DataSource` without duplicating dialect wiring.

`Frag.sequence(frags: Frag*)` is a zero-separator helper built from `++`, so `Frag.sequence()` is `Frag.empty` and parameter order stays left-to-right across multiple fragments.

`sqlite-jdbc` 3.49.1.0's `getGeneratedKeys()` returns only the LAST rowid for a multi-row `VALUES (?, ?), (?, ?)` INSERT (one row in the ResultSet, not N). This is a fundamental SQLite limitation: `sqlite3_last_insert_rowid()` tracks only the most recently inserted row. Consequence: `Repo.insertMany` uses `SqlOps.update` (not `updateReturningKeys`) for the multi-row INSERT and returns IDs via `rows.map(getId)` — suitable when the caller supplies primary keys explicitly in the entities.

In JVM SQLite integration tests, override `connect` in `JdbcTransactor` to reuse the same `conn` across calls without closing it. The default `connect` implementation closes the `JdbcConnection` wrapper (and thereby the underlying `Connection`) after each call. For in-memory SQLite this would wipe the database; the override keeps the connection alive across multiple `tx.connect { ... }` blocks within the same test fixture.
