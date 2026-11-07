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
 * One column of a `CREATE TABLE` statement built by [[Ddl.createTable]].
 *
 * @param name
 *   the column name, written into the SQL without quotes
 * @param sqlType
 *   the SQL type, written as is, such as `"INTEGER"` or `"TEXT"`
 * @param nullable
 *   `false` adds `NOT NULL`
 * @see
 *   [[https://zioblocks.com/reference/sql/ddl/ Ddl]]
 */
final case class ColumnDef(name: String, sqlType: String, nullable: Boolean)

/**
 * Builds `CREATE TABLE` and `DROP TABLE` statements as [[Frag]]s with no
 * parameters. [[Table#createTable]] and [[Table#dropTable]] call it.
 *
 * Names are checked as SQL identifiers (`IllegalArgumentException` otherwise)
 * but not quoted, unlike the statements [[Repo]] and [[Upsert]] build, so a
 * table or column named by an SQL keyword, such as `order`, gives invalid SQL,
 * a known bug in 0.0.56.
 *
 * {{{
 * val ddl = Ddl.createTable(
 *   "coupon",
 *   IndexedSeq(
 *     ColumnDef("code", "TEXT", nullable = false),
 *     ColumnDef("percent_off", "INTEGER", nullable = true)
 *   )
 * )
 * val sqlText = ddl.sql(SqlDialect.SQLite)
 * // CREATE TABLE IF NOT EXISTS coupon (
 * //   code TEXT NOT NULL,
 * //   percent_off INTEGER
 * // )
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/sql/ddl/ Ddl]]
 */
object Ddl {

  /**
   * Returns `CREATE TABLE IF NOT EXISTS <tableName> (...)`, one line per column
   * rendered as `name sqlType`, followed by `NOT NULL` unless the column is
   * nullable. It declares no key or other constraint.
   */
  def createTable(tableName: String, columns: IndexedSeq[ColumnDef]): Frag = {
    val validatedTable = SqlIdentifier.validate("table", tableName)
    val colDefs        = columns.map { col =>
      val validatedColumn = SqlIdentifier.validate("column", col.name)
      val nullStr         = if (col.nullable) "" else " NOT NULL"
      s"  $validatedColumn ${col.sqlType}$nullStr"
    }
    Frag.literal(s"CREATE TABLE IF NOT EXISTS $validatedTable (\n${colDefs.mkString(",\n")}\n)")
  }

  /** Returns `DROP TABLE IF EXISTS <tableName>`. */
  def dropTable(tableName: String): Frag =
    Frag.literal(s"DROP TABLE IF EXISTS ${SqlIdentifier.validate("table", tableName)}")
}
