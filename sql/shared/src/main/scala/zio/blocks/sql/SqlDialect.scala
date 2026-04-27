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

sealed trait SqlDialect {
  def name: String
  def typeName(dbValue: DbValue): String
  def paramPlaceholder(index: Int): String
}

object SqlDialect {
  case object PostgreSQL extends SqlDialect {
    val name: String = "PostgreSQL"

    def typeName(dbValue: DbValue): String = dbValue match {
      case DbValue.DbNull             => "NULL"
      case _: DbValue.DbInt           => "INTEGER"
      case _: DbValue.DbLong          => "BIGINT"
      case _: DbValue.DbDouble        => "DOUBLE PRECISION"
      case _: DbValue.DbFloat         => "REAL"
      case _: DbValue.DbBoolean       => "BOOLEAN"
      case _: DbValue.DbString        => "TEXT"
      case _: DbValue.DbBigDecimal    => "NUMERIC"
      case _: DbValue.DbBytes         => "BYTEA"
      case _: DbValue.DbShort         => "SMALLINT"
      case _: DbValue.DbByte          => "SMALLINT"
      case _: DbValue.DbChar          => "CHAR(1)"
      case _: DbValue.DbLocalDate     => "DATE"
      case _: DbValue.DbLocalDateTime => "TIMESTAMP"
      case _: DbValue.DbLocalTime     => "TIME"
      case _: DbValue.DbInstant       => "TIMESTAMPTZ"
      case _: DbValue.DbDuration      => "INTERVAL"
      case _: DbValue.DbUUID          => "UUID"
    }

    def paramPlaceholder(index: Int): String = s"$$$index"
  }

  case object SQLite extends SqlDialect {
    val name: String = "SQLite"

    def typeName(dbValue: DbValue): String = dbValue match {
      case DbValue.DbNull             => "NULL"
      case _: DbValue.DbInt           => "INTEGER"
      case _: DbValue.DbLong          => "INTEGER"
      case _: DbValue.DbDouble        => "REAL"
      case _: DbValue.DbFloat         => "REAL"
      case _: DbValue.DbBoolean       => "INTEGER"
      case _: DbValue.DbString        => "TEXT"
      case _: DbValue.DbBigDecimal    => "TEXT"
      case _: DbValue.DbBytes         => "BLOB"
      case _: DbValue.DbShort         => "INTEGER"
      case _: DbValue.DbByte          => "INTEGER"
      case _: DbValue.DbChar          => "TEXT"
      case _: DbValue.DbLocalDate     => "TEXT"
      case _: DbValue.DbLocalDateTime => "TEXT"
      case _: DbValue.DbLocalTime     => "TEXT"
      case _: DbValue.DbInstant       => "TEXT"
      case _: DbValue.DbDuration      => "TEXT"
      case _: DbValue.DbUUID          => "TEXT"
    }

    def paramPlaceholder(index: Int): String = "?"
  }
}
