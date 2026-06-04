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
import java.time.{Duration, Instant, LocalDate, LocalDateTime, LocalTime}
import java.util.UUID

object SqlDialectSpec extends ZIOSpecDefault {
  def spec = suite("SqlDialectSpec")(
    suite("PostgreSQL")(
      test("name is PostgreSQL") {
        assertTrue(SqlDialect.PostgreSQL.name == "PostgreSQL")
      },
      test("DbNull -> NULL") {
        assertTrue(SqlDialect.PostgreSQL.typeName(DbValue.DbNull) == "NULL")
      },
      test("DbInt -> INTEGER") {
        assertTrue(SqlDialect.PostgreSQL.typeName(DbValue.DbInt(1)) == "INTEGER")
      },
      test("DbLong -> BIGINT") {
        assertTrue(SqlDialect.PostgreSQL.typeName(DbValue.DbLong(1L)) == "BIGINT")
      },
      test("DbDouble -> DOUBLE PRECISION") {
        assertTrue(
          SqlDialect.PostgreSQL.typeName(DbValue.DbDouble(1.0)) == "DOUBLE PRECISION"
        )
      },
      test("DbFloat -> REAL") {
        assertTrue(SqlDialect.PostgreSQL.typeName(DbValue.DbFloat(1.0f)) == "REAL")
      },
      test("DbBoolean -> BOOLEAN") {
        assertTrue(SqlDialect.PostgreSQL.typeName(DbValue.DbBoolean(true)) == "BOOLEAN")
      },
      test("DbString -> TEXT") {
        assertTrue(SqlDialect.PostgreSQL.typeName(DbValue.DbString("x")) == "TEXT")
      },
      test("DbBigDecimal -> NUMERIC") {
        assertTrue(
          SqlDialect.PostgreSQL.typeName(DbValue.DbBigDecimal(scala.BigDecimal("1"))) == "NUMERIC"
        )
      },
      test("DbBytes -> BYTEA") {
        assertTrue(
          SqlDialect.PostgreSQL.typeName(DbValue.DbBytes(Array[Byte](1))) == "BYTEA"
        )
      },
      test("DbShort -> SMALLINT") {
        assertTrue(
          SqlDialect.PostgreSQL.typeName(DbValue.DbShort(1.toShort)) == "SMALLINT"
        )
      },
      test("DbByte -> SMALLINT") {
        assertTrue(SqlDialect.PostgreSQL.typeName(DbValue.DbByte(1.toByte)) == "SMALLINT")
      },
      test("DbChar -> CHAR(1)") {
        assertTrue(SqlDialect.PostgreSQL.typeName(DbValue.DbChar('A')) == "CHAR(1)")
      },
      test("DbLocalDate -> DATE") {
        assertTrue(
          SqlDialect.PostgreSQL.typeName(DbValue.DbLocalDate(LocalDate.now())) == "DATE"
        )
      },
      test("DbLocalDateTime -> TIMESTAMP") {
        assertTrue(
          SqlDialect.PostgreSQL.typeName(
            DbValue.DbLocalDateTime(LocalDateTime.now())
          ) == "TIMESTAMP"
        )
      },
      test("DbLocalTime -> TIME") {
        assertTrue(
          SqlDialect.PostgreSQL.typeName(DbValue.DbLocalTime(LocalTime.now())) == "TIME"
        )
      },
      test("DbInstant -> TIMESTAMPTZ") {
        assertTrue(
          SqlDialect.PostgreSQL.typeName(DbValue.DbInstant(Instant.now())) == "TIMESTAMPTZ"
        )
      },
      test("DbDuration -> INTERVAL") {
        assertTrue(
          SqlDialect.PostgreSQL.typeName(DbValue.DbDuration(Duration.ofHours(1))) == "INTERVAL"
        )
      },
      test("DbUUID -> UUID") {
        assertTrue(
          SqlDialect.PostgreSQL.typeName(
            DbValue.DbUUID(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
          ) == "UUID"
        )
      },
      test("paramPlaceholder(1) -> $1") {
        assertTrue(SqlDialect.PostgreSQL.paramPlaceholder(1) == "$1")
      },
      test("paramPlaceholder(2) -> $2") {
        assertTrue(SqlDialect.PostgreSQL.paramPlaceholder(2) == "$2")
      },
      test("paramPlaceholder(42) -> $42") {
        assertTrue(SqlDialect.PostgreSQL.paramPlaceholder(42) == "$42")
      }
    ),
    suite("SQLite")(
      test("name is SQLite") {
        assertTrue(SqlDialect.SQLite.name == "SQLite")
      },
      test("DbNull -> NULL") {
        assertTrue(SqlDialect.SQLite.typeName(DbValue.DbNull) == "NULL")
      },
      test("DbInt -> INTEGER") {
        assertTrue(SqlDialect.SQLite.typeName(DbValue.DbInt(1)) == "INTEGER")
      },
      test("DbLong -> INTEGER") {
        assertTrue(SqlDialect.SQLite.typeName(DbValue.DbLong(1L)) == "INTEGER")
      },
      test("DbDouble -> REAL") {
        assertTrue(SqlDialect.SQLite.typeName(DbValue.DbDouble(1.0)) == "REAL")
      },
      test("DbFloat -> REAL") {
        assertTrue(SqlDialect.SQLite.typeName(DbValue.DbFloat(1.0f)) == "REAL")
      },
      test("DbBoolean -> INTEGER") {
        assertTrue(SqlDialect.SQLite.typeName(DbValue.DbBoolean(true)) == "INTEGER")
      },
      test("DbString -> TEXT") {
        assertTrue(SqlDialect.SQLite.typeName(DbValue.DbString("x")) == "TEXT")
      },
      test("DbBigDecimal -> TEXT") {
        assertTrue(
          SqlDialect.SQLite.typeName(DbValue.DbBigDecimal(scala.BigDecimal("1"))) == "TEXT"
        )
      },
      test("DbBytes -> BLOB") {
        assertTrue(SqlDialect.SQLite.typeName(DbValue.DbBytes(Array[Byte](1))) == "BLOB")
      },
      test("DbShort -> INTEGER") {
        assertTrue(
          SqlDialect.SQLite.typeName(DbValue.DbShort(1.toShort)) == "INTEGER"
        )
      },
      test("DbByte -> INTEGER") {
        assertTrue(SqlDialect.SQLite.typeName(DbValue.DbByte(1.toByte)) == "INTEGER")
      },
      test("DbChar -> TEXT") {
        assertTrue(SqlDialect.SQLite.typeName(DbValue.DbChar('A')) == "TEXT")
      },
      test("DbLocalDate -> TEXT") {
        assertTrue(
          SqlDialect.SQLite.typeName(DbValue.DbLocalDate(LocalDate.now())) == "TEXT"
        )
      },
      test("DbLocalDateTime -> TEXT") {
        assertTrue(
          SqlDialect.SQLite.typeName(
            DbValue.DbLocalDateTime(LocalDateTime.now())
          ) == "TEXT"
        )
      },
      test("DbLocalTime -> TEXT") {
        assertTrue(SqlDialect.SQLite.typeName(DbValue.DbLocalTime(LocalTime.now())) == "TEXT")
      },
      test("DbInstant -> TEXT") {
        assertTrue(
          SqlDialect.SQLite.typeName(DbValue.DbInstant(Instant.now())) == "TEXT"
        )
      },
      test("DbDuration -> TEXT") {
        assertTrue(
          SqlDialect.SQLite.typeName(DbValue.DbDuration(Duration.ofHours(1))) == "TEXT"
        )
      },
      test("DbUUID -> TEXT") {
        assertTrue(
          SqlDialect.SQLite.typeName(DbValue.DbUUID(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))) == "TEXT"
        )
      },
      test("paramPlaceholder(1) -> ?") {
        assertTrue(SqlDialect.SQLite.paramPlaceholder(1) == "?")
      },
      test("paramPlaceholder(42) -> ?") {
        assertTrue(SqlDialect.SQLite.paramPlaceholder(42) == "?")
      }
    )
  )
}
