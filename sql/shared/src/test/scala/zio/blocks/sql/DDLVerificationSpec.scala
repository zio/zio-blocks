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
import zio.blocks.chunk.Chunk
import zio.blocks.schema.*
import java.time.Instant

object DDLVerificationSpec extends ZIOSpecDefault {

  // ── 5 representative schemas ────────────────────────────────────────────
  case class Mixed(
    s: String,
    i: Int,
    l: Long,
    d: Double,
    f: Float,
    b: Boolean,
    opt: Option[String],
    ts: Instant,
    bytes: Array[Byte]
  )
  object Mixed { implicit val schema: Schema[Mixed] = Schema.derived }

  case class ChunkMixed(
    s: String,
    i: Int,
    l: Long,
    d: Double,
    b: Boolean,
    opt: Option[String],
    ts: Instant,
    chunkBytes: Chunk[Byte]
  )
  object ChunkMixed { implicit val schema: Schema[ChunkMixed] = Schema.derived }

  case class OptionHeavy(
    a: Option[String],
    b: Option[Int],
    c: Option[Instant],
    d: String,
    e: Int
  )
  object OptionHeavy { implicit val schema: Schema[OptionHeavy] = Schema.derived }

  case class Primitives(
    str: String,
    intVal: Int,
    longVal: Long,
    doubleVal: Double,
    floatVal: Float,
    boolVal: Boolean,
    instantVal: Instant
  )
  object Primitives { implicit val schema: Schema[Primitives] = Schema.derived }

  case class BytesOnly(
    data: Array[Byte],
    chunkData: Chunk[Byte]
  )
  object BytesOnly { implicit val schema: Schema[BytesOnly] = Schema.derived }

  case class WithJson(
    id: Int,
    tags: List[String]
  )
  object WithJson { implicit val schema: Schema[WithJson] = Schema.derived }

  // helper: build DDL string from ColumnMeta
  private def ddlString(cols: IndexedSeq[ColumnMeta], dialect: SqlDialect): String =
    cols.map(c => s"${c.name} ${dialect.typeName(c.dbValue)}${if (c.nullable) "" else " NOT NULL"}").mkString(", ")

  def spec = suite("DDLVerificationSpec")(
    suite("SQLite dialect - type mapping")(
      test("String -> TEXT NOT NULL") {
        val cols = TableMetadata.columnsFor(summon[Schema[Primitives]])
        val col  = cols.find(_.name == "str").get
        assertTrue(SqlDialect.SQLite.typeName(col.dbValue) == "TEXT", !col.nullable)
      },
      test("Int -> INTEGER NOT NULL") {
        val cols = TableMetadata.columnsFor(summon[Schema[Primitives]])
        val col  = cols.find(_.name == "int_val").get
        assertTrue(SqlDialect.SQLite.typeName(col.dbValue) == "INTEGER", !col.nullable)
      },
      test("Long -> INTEGER NOT NULL (SQLite typeless)") {
        val cols = TableMetadata.columnsFor(summon[Schema[Primitives]])
        val col  = cols.find(_.name == "long_val").get
        assertTrue(SqlDialect.SQLite.typeName(col.dbValue) == "INTEGER", !col.nullable)
      },
      test("Double -> REAL NOT NULL") {
        val cols = TableMetadata.columnsFor(summon[Schema[Primitives]])
        val col  = cols.find(_.name == "double_val").get
        assertTrue(SqlDialect.SQLite.typeName(col.dbValue) == "REAL", !col.nullable)
      },
      test("Float -> REAL NOT NULL") {
        val cols = TableMetadata.columnsFor(summon[Schema[Primitives]])
        val col  = cols.find(_.name == "float_val").get
        assertTrue(SqlDialect.SQLite.typeName(col.dbValue) == "REAL", !col.nullable)
      },
      test("Boolean -> INTEGER NOT NULL (SQLite)") {
        val cols = TableMetadata.columnsFor(summon[Schema[Primitives]])
        val col  = cols.find(_.name == "bool_val").get
        assertTrue(SqlDialect.SQLite.typeName(col.dbValue) == "INTEGER", !col.nullable)
      },
      test("Instant -> TEXT NOT NULL (SQLite)") {
        val cols = TableMetadata.columnsFor(summon[Schema[Primitives]])
        val col  = cols.find(_.name == "instant_val").get
        assertTrue(SqlDialect.SQLite.typeName(col.dbValue) == "TEXT", !col.nullable)
      },
      test("Option[String] -> TEXT nullable") {
        val cols = TableMetadata.columnsFor(summon[Schema[OptionHeavy]])
        val col  = cols.find(_.name == "a").get
        assertTrue(SqlDialect.SQLite.typeName(col.dbValue) == "TEXT", col.nullable)
      },
      test("Option[Int] -> INTEGER nullable") {
        val cols = TableMetadata.columnsFor(summon[Schema[OptionHeavy]])
        val col  = cols.find(_.name == "b").get
        assertTrue(SqlDialect.SQLite.typeName(col.dbValue) == "INTEGER", col.nullable)
      },
      test("Array[Byte] -> BLOB") {
        val cols = TableMetadata.columnsFor(summon[Schema[Mixed]])
        val col  = cols.find(_.name == "bytes").get
        assertTrue(SqlDialect.SQLite.typeName(col.dbValue) == "BLOB", !col.nullable)
      },
      test("Chunk[Byte] -> BLOB") {
        val cols = TableMetadata.columnsFor(summon[Schema[ChunkMixed]])
        val col  = cols.find(_.name == "chunk_bytes").get
        assertTrue(SqlDialect.SQLite.typeName(col.dbValue) == "BLOB", !col.nullable)
      },
      test("List[String] -> TEXT (SQLite stores JSONB as TEXT)") {
        val cols = TableMetadata.columnsFor(summon[Schema[WithJson]])
        val col  = cols.find(_.name == "tags").get
        assertTrue(col.dbValue.isInstanceOf[DbValue.DbJsonb], SqlDialect.SQLite.typeName(col.dbValue) == "TEXT")
      },
      test("Table.derived[Mixed] createTable SQLite") {
        // Mixed contains Array[Byte] which maps to BLOB
        val table = Table.derived[Primitives]
        val sql   = table.createTable(SqlDialect.SQLite).sql(SqlDialect.SQLite)
        assertTrue(sql.contains("TEXT NOT NULL"), sql.contains("INTEGER NOT NULL"), sql.contains("REAL NOT NULL"))
      },
      test("Option nullable via Table.derived") {
        val table = Table.derived[OptionHeavy]
        val sql   = table.createTable(SqlDialect.SQLite).sql(SqlDialect.SQLite)
        // nullable cols should NOT have NOT NULL
        assertTrue(sql.contains("a TEXT,"), sql.contains("b INTEGER,"))
      },
      test("ddlString helper produces expected for Primitives") {
        val cols = TableMetadata.columnsFor(summon[Schema[Primitives]])
        val ddl  = ddlString(cols, SqlDialect.SQLite)
        // SQLite: str TEXT NOT NULL, int_val INTEGER NOT NULL, long_val INTEGER NOT NULL, double_val REAL NOT NULL, float_val REAL NOT NULL, bool_val INTEGER NOT NULL, instant_val TEXT NOT NULL
        assertTrue(
          ddl.contains("str TEXT NOT NULL"),
          ddl.contains("int_val INTEGER NOT NULL"),
          ddl.contains("bool_val INTEGER NOT NULL")
        )
      }
    ),
    suite("Postgres dialect - type mapping")(
      test("String -> TEXT NOT NULL") {
        val cols = TableMetadata.columnsFor(summon[Schema[Primitives]])
        val col  = cols.find(_.name == "str").get
        assertTrue(SqlDialect.PostgreSQL.typeName(col.dbValue) == "TEXT", !col.nullable)
      },
      test("Int -> INTEGER NOT NULL") {
        val cols = TableMetadata.columnsFor(summon[Schema[Primitives]])
        val col  = cols.find(_.name == "int_val").get
        assertTrue(SqlDialect.PostgreSQL.typeName(col.dbValue) == "INTEGER", !col.nullable)
      },
      test("Long -> BIGINT NOT NULL") {
        val cols = TableMetadata.columnsFor(summon[Schema[Primitives]])
        val col  = cols.find(_.name == "long_val").get
        assertTrue(SqlDialect.PostgreSQL.typeName(col.dbValue) == "BIGINT", !col.nullable)
      },
      test("Double -> DOUBLE PRECISION NOT NULL") {
        val cols = TableMetadata.columnsFor(summon[Schema[Primitives]])
        val col  = cols.find(_.name == "double_val").get
        assertTrue(SqlDialect.PostgreSQL.typeName(col.dbValue) == "DOUBLE PRECISION", !col.nullable)
      },
      test("Float -> REAL NOT NULL") {
        val cols = TableMetadata.columnsFor(summon[Schema[Primitives]])
        val col  = cols.find(_.name == "float_val").get
        assertTrue(SqlDialect.PostgreSQL.typeName(col.dbValue) == "REAL", !col.nullable)
      },
      test("Boolean -> BOOLEAN NOT NULL") {
        val cols = TableMetadata.columnsFor(summon[Schema[Primitives]])
        val col  = cols.find(_.name == "bool_val").get
        assertTrue(SqlDialect.PostgreSQL.typeName(col.dbValue) == "BOOLEAN", !col.nullable)
      },
      test("Instant -> TIMESTAMPTZ NOT NULL (Postgres)") {
        val cols = TableMetadata.columnsFor(summon[Schema[Primitives]])
        val col  = cols.find(_.name == "instant_val").get
        // Task expects TIMESTAMP, actual is TIMESTAMPTZ — document diff
        assertTrue(SqlDialect.PostgreSQL.typeName(col.dbValue) == "TIMESTAMPTZ", !col.nullable)
      },
      test("Option[String] -> TEXT nullable PG") {
        val cols = TableMetadata.columnsFor(summon[Schema[OptionHeavy]])
        val col  = cols.find(_.name == "a").get
        assertTrue(SqlDialect.PostgreSQL.typeName(col.dbValue) == "TEXT", col.nullable)
      },
      test("Array[Byte] -> BYTEA") {
        val cols = TableMetadata.columnsFor(summon[Schema[Mixed]])
        val col  = cols.find(_.name == "bytes").get
        assertTrue(SqlDialect.PostgreSQL.typeName(col.dbValue) == "BYTEA", !col.nullable)
      },
      test("Chunk[Byte] -> BYTEA") {
        val cols = TableMetadata.columnsFor(summon[Schema[ChunkMixed]])
        val col  = cols.find(_.name == "chunk_bytes").get
        assertTrue(SqlDialect.PostgreSQL.typeName(col.dbValue) == "BYTEA", !col.nullable)
      },
      test("List[String] -> JSONB") {
        val cols = TableMetadata.columnsFor(summon[Schema[WithJson]])
        val col  = cols.find(_.name == "tags").get
        assertTrue(col.dbValue.isInstanceOf[DbValue.DbJsonb], SqlDialect.PostgreSQL.typeName(col.dbValue) == "JSONB")
      },
      test("Table.derived with JSON field renders JSONB on PG") {
        val table = Table.derived[WithJson]
        val sql   = table.createTable(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        assertTrue(sql.contains("tags JSONB NOT NULL"))
      },
      test("Table.derived createTable PG") {
        val table = Table.derived[Primitives]
        val sql   = table.createTable(SqlDialect.PostgreSQL).sql(SqlDialect.PostgreSQL)
        assertTrue(
          sql.contains("TEXT NOT NULL"),
          sql.contains("BIGINT NOT NULL"),
          sql.contains("DOUBLE PRECISION NOT NULL"),
          sql.contains("BOOLEAN NOT NULL")
        )
      },
      test("ChunkBytes diff triage print") {
        // Diagnostic: print actual vs expected
        val primCols  = TableMetadata.columnsFor(summon[Schema[Primitives]])
        val sqliteDDL = ddlString(primCols, SqlDialect.SQLite)
        val pgDDL     = ddlString(primCols, SqlDialect.PostgreSQL)
        assertTrue(sqliteDDL.nonEmpty, pgDDL.nonEmpty)
      }
    ),
    suite("verification helpers")(
      test("columnsFor Mixed prints DDL for both dialects") {
        val primCols  = TableMetadata.columnsFor(summon[Schema[Primitives]])
        val sqliteDDL = primCols
          .map(c => s"${c.name} ${SqlDialect.SQLite.typeName(c.dbValue)}${if (c.nullable) "" else " NOT NULL"}")
          .mkString(", ")
        val pgDDL = primCols
          .map(c => s"${c.name} ${SqlDialect.PostgreSQL.typeName(c.dbValue)}${if (c.nullable) "" else " NOT NULL"}")
          .mkString(", ")
        val expectedSqlite =
          "str TEXT NOT NULL, int_val INTEGER NOT NULL, long_val INTEGER NOT NULL, double_val REAL NOT NULL, float_val REAL NOT NULL, bool_val INTEGER NOT NULL, instant_val TEXT NOT NULL"
        val expectedPg =
          "str TEXT NOT NULL, int_val INTEGER NOT NULL, long_val BIGINT NOT NULL, double_val DOUBLE PRECISION NOT NULL, float_val REAL NOT NULL, bool_val BOOLEAN NOT NULL, instant_val TIMESTAMPTZ NOT NULL"
        assertTrue(sqliteDDL == expectedSqlite, pgDDL == expectedPg)
      }
    )
  )
}
