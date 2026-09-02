/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
        assertTrue(
          frag.sql(SqlDialect.PostgreSQL) ==
            "CREATE TABLE IF NOT EXISTS users (\n  id INTEGER NOT NULL,\n  name TEXT NOT NULL,\n  email TEXT\n)"
        )
      },
      test("nullable columns omit NOT NULL") {
        val columns = IndexedSeq(ColumnDef("bio", "TEXT", true))
        assertTrue(
          Ddl.createTable("profiles", columns).sql(SqlDialect.PostgreSQL) ==
            "CREATE TABLE IF NOT EXISTS profiles (\n  bio TEXT\n)"
        )
      },
      test("non-nullable columns include NOT NULL") {
        val columns = IndexedSeq(ColumnDef("id", "INTEGER", false))
        assertTrue(
          Ddl.createTable("items", columns).sql(SqlDialect.PostgreSQL) ==
            "CREATE TABLE IF NOT EXISTS items (\n  id INTEGER NOT NULL\n)"
        )
      },
      test("multiple columns are comma-separated") {
        val columns = IndexedSeq(
          ColumnDef("a", "INTEGER", false),
          ColumnDef("b", "TEXT", false),
          ColumnDef("c", "REAL", true)
        )
        assertTrue(
          Ddl.createTable("t", columns).sql(SqlDialect.PostgreSQL) ==
            "CREATE TABLE IF NOT EXISTS t (\n  a INTEGER NOT NULL,\n  b TEXT NOT NULL,\n  c REAL\n)"
        )
      },
      test("PostgreSQL type names in column definitions") {
        val columns = IndexedSeq(
          ColumnDef("active", "BOOLEAN", false),
          ColumnDef("data", "BYTEA", false),
          ColumnDef("id", "UUID", false)
        )
        assertTrue(
          Ddl.createTable("pg_table", columns).sql(SqlDialect.PostgreSQL) ==
            "CREATE TABLE IF NOT EXISTS pg_table (\n  active BOOLEAN NOT NULL,\n  data BYTEA NOT NULL,\n  id UUID NOT NULL\n)"
        )
      },
      test("SQLite type names in column definitions") {
        val columns = IndexedSeq(
          ColumnDef("active", "INTEGER", false),
          ColumnDef("data", "BLOB", false),
          ColumnDef("id", "TEXT", false)
        )
        assertTrue(
          Ddl.createTable("sqlite_table", columns).sql(SqlDialect.SQLite) ==
            "CREATE TABLE IF NOT EXISTS sqlite_table (\n  active INTEGER NOT NULL,\n  data BLOB NOT NULL,\n  id TEXT NOT NULL\n)"
        )
      },
      test("result is a parameterless Frag") {
        val columns = IndexedSeq(ColumnDef("x", "INTEGER", false))
        val frag    = Ddl.createTable("t", columns)
        assertTrue(frag.params.isEmpty)
      },
      test("rejects invalid table identifiers") {
        val columns = IndexedSeq(ColumnDef("id", "INTEGER", false))
        val error   = try {
          Ddl.createTable("users; DROP TABLE users", columns)
          throw new AssertionError("Expected IllegalArgumentException")
        } catch {
          case e: IllegalArgumentException => e
        }
        assertTrue(error.getMessage.contains("Invalid SQL table identifier"))
      },
      test("rejects invalid column identifiers") {
        val error = try {
          Ddl.createTable("users", IndexedSeq(ColumnDef("name desc", "TEXT", false)))
          throw new AssertionError("Expected IllegalArgumentException")
        } catch {
          case e: IllegalArgumentException => e
        }
        assertTrue(error.getMessage.contains("Invalid SQL column identifier"))
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
      },
      test("rejects invalid drop-table identifiers") {
        val error = try {
          Ddl.dropTable("orders cascade")
          throw new AssertionError("Expected IllegalArgumentException")
        } catch {
          case e: IllegalArgumentException => e
        }
        assertTrue(error.getMessage.contains("Invalid SQL table identifier"))
      }
    )
  )
}
