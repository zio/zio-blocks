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
 * Metadata for a single column in a `CREATE TABLE` statement.
 *
 * @param name
 *   The column name as it appears in SQL.
 * @param sqlType
 *   The SQL type string (e.g. `"INTEGER"`, `"TEXT"`).
 * @param nullable
 *   Whether the column allows SQL NULL.
 */
final case class ColumnDef(name: String, sqlType: String, nullable: Boolean)

/** Helpers for generating DDL fragments (`CREATE TABLE`, `DROP TABLE`). */
object Ddl {

  /**
   * Produces a `CREATE TABLE IF NOT EXISTS <tableName> (...)` fragment. Each
   * column is rendered as `name sqlType [NOT NULL]`.
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

  /** Produces a `DROP TABLE IF EXISTS <tableName>` fragment. */
  def dropTable(tableName: String): Frag =
    Frag.literal(s"DROP TABLE IF EXISTS ${SqlIdentifier.validate("table", tableName)}")
}
