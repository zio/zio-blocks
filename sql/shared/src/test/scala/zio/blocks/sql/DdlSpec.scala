package zio.blocks.sql

import zio.test.*

object DdlSpec extends ZIOSpecDefault {
  def spec = suite("DdlSpec")(
    suite("createTable")(
      test("generates CREATE TABLE IF NOT EXISTS with columns") {
        val columns = IndexedSeq(
          ColumnDef("id", "INTEGER", false),
          ColumnDef("name", "TEXT", false),
          ColumnDef("email", "TEXT", true)
        )
        val frag = Ddl.createTable("users", columns)
        val sql  = frag.sql(SqlDialect.PostgreSQL)
        assertTrue(
          sql.contains("CREATE TABLE IF NOT EXISTS users"),
          sql.contains("id INTEGER NOT NULL"),
          sql.contains("name TEXT NOT NULL"),
          sql.contains("email TEXT"),
          !sql.contains("email TEXT NOT NULL")
        )
      },
      test("nullable columns omit NOT NULL") {
        val columns = IndexedSeq(ColumnDef("bio", "TEXT", true))
        val sql     = Ddl.createTable("profiles", columns).sql(SqlDialect.PostgreSQL)
        assertTrue(
          sql.contains("bio TEXT"),
          !sql.contains("NOT NULL")
        )
      },
      test("non-nullable columns include NOT NULL") {
        val columns = IndexedSeq(ColumnDef("id", "INTEGER", false))
        val sql     = Ddl.createTable("items", columns).sql(SqlDialect.PostgreSQL)
        assertTrue(sql.contains("id INTEGER NOT NULL"))
      },
      test("multiple columns are comma-separated") {
        val columns = IndexedSeq(
          ColumnDef("a", "INTEGER", false),
          ColumnDef("b", "TEXT", false),
          ColumnDef("c", "REAL", true)
        )
        val sql = Ddl.createTable("t", columns).sql(SqlDialect.PostgreSQL)
        assertTrue(
          sql.contains("a INTEGER NOT NULL,\n"),
          sql.contains("b TEXT NOT NULL,\n"),
          sql.contains("c REAL\n")
        )
      },
      test("PostgreSQL type names in column definitions") {
        val columns = IndexedSeq(
          ColumnDef("active", "BOOLEAN", false),
          ColumnDef("data", "BYTEA", false),
          ColumnDef("id", "UUID", false)
        )
        val sql = Ddl.createTable("pg_table", columns).sql(SqlDialect.PostgreSQL)
        assertTrue(
          sql.contains("active BOOLEAN NOT NULL"),
          sql.contains("data BYTEA NOT NULL"),
          sql.contains("id UUID NOT NULL")
        )
      },
      test("SQLite type names in column definitions") {
        val columns = IndexedSeq(
          ColumnDef("active", "INTEGER", false),
          ColumnDef("data", "BLOB", false),
          ColumnDef("id", "TEXT", false)
        )
        val sql = Ddl.createTable("sqlite_table", columns).sql(SqlDialect.SQLite)
        assertTrue(
          sql.contains("active INTEGER NOT NULL"),
          sql.contains("data BLOB NOT NULL"),
          sql.contains("id TEXT NOT NULL")
        )
      },
      test("result is a parameterless Frag") {
        val columns = IndexedSeq(ColumnDef("x", "INTEGER", false))
        val frag    = Ddl.createTable("t", columns)
        assertTrue(frag.params.isEmpty)
      }
    ),
    suite("dropTable")(
      test("generates DROP TABLE IF EXISTS") {
        val frag = Ddl.dropTable("users")
        assertTrue(frag.sql(SqlDialect.PostgreSQL) == "DROP TABLE IF EXISTS users")
      },
      test("works with any table name") {
        assertTrue(
          Ddl.dropTable("orders").sql(SqlDialect.SQLite) == "DROP TABLE IF EXISTS orders"
        )
      },
      test("result is a parameterless Frag") {
        val frag = Ddl.dropTable("t")
        assertTrue(frag.params.isEmpty)
      }
    )
  )
}
