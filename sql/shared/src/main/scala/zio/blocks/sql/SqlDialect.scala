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

/**
 * The SQL database a [[Transactor]] talks to: PostgreSQL or SQLite. A [[Frag]]
 * renders its placeholders for a dialect, and [[Table#createTable]] takes
 * column types from it.
 *
 * {{{
 * val pg     = SqlDialect.PostgreSQL.typeName(DbValue.DbLong(0L))   // BIGINT
 * val sqlite = SqlDialect.SQLite.typeName(DbValue.DbLong(0L))       // INTEGER
 * val dec    = SqlDialect.SQLite.typeName(DbValue.DbBigDecimal(0)) // TEXT
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/sql/sql-dialect/ SqlDialect]]
 */
sealed trait SqlDialect {

  /** The display name: `PostgreSQL` or `SQLite`. */
  def name: String

  /**
   * Returns the column type for values like `dbValue`, such as `BIGINT` for a
   * [[DbValue.DbLong]] on PostgreSQL.
   */
  def typeName(dbValue: DbValue): String

  /**
   * Returns the placeholder for the 1-based parameter `index`: `?` in both
   * dialects.
   */
  def paramPlaceholder(index: Int): String
}

/** The supported dialects. */
object SqlDialect {

  /**
   * PostgreSQL: `Instant` is `TIMESTAMPTZ`, `BigDecimal` is `NUMERIC`,
   * `Boolean` is `BOOLEAN`, and an array is `TEXT[]`.
   */
  case object PostgreSQL extends SqlDialect {

    /** The dialect name. */
    val name: String = "PostgreSQL"

    /** Returns this dialect's column type for `dbValue`. */
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
      case _: DbValue.DbArray         => "TEXT[]"
    }

    /** Returns `?`. */
    def paramPlaceholder(index: Int): String = "?"
  }

  /**
   * SQLite: integers and `Boolean` are `INTEGER`, `Double` and `Float` are
   * `REAL`, byte arrays are `BLOB`, and the other value types, including
   * `BigDecimal`, `UUID` and the `java.time` types, are `TEXT`.
   */
  case object SQLite extends SqlDialect {

    /** The dialect name. */
    val name: String = "SQLite"

    /** Returns this dialect's column type for `dbValue`. */
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
      case _: DbValue.DbArray         => "TEXT"
    }

    /** Returns `?`. */
    def paramPlaceholder(index: Int): String = "?"
  }
}
