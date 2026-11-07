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

package zio.blocks.data.migration

import zio.blocks.sql.{DbCon, Dialect, Frag, Table as SqlTable}

/**
 * Creates and swaps shadow tables, which `TargetStrategy.ShadowTable` uses: the
 * migrator writes converted rows into `<table>_<suffix>`, and the swap then
 * renames `<table>` to `<table>_old_<suffix>` and the shadow table to
 * `<table>`. Table names and suffixes must be ASCII letters, digits and
 * underscores, not starting with a digit, or the methods throw
 * `IllegalArgumentException`.
 *
 * {{{
 * ShadowTable.swapPreview("customers", "v2")
 * // List(ALTER TABLE customers RENAME TO customers_old_v2, ALTER TABLE customers_v2 RENAME TO customers)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/data-migration/ Data Migration]]
 */
object ShadowTable {

  /**
   * Creates `<table>_<suffix>` with the columns of `table` as they are in the
   * database, and returns its name. On PostgreSQL it runs
   * `CREATE TABLE IF NOT EXISTS <shadow> (LIKE <table> INCLUDING ALL)`, so an
   * existing shadow table is left alone. When the migration changes columns,
   * the copy has the old ones (a known bug); create the shadow table yourself
   * first.
   *
   * @throws java.lang.UnsupportedOperationException
   *   on SQLite, which has no `CREATE TABLE ... LIKE`
   * @throws java.lang.IllegalArgumentException
   *   if `suffix` is not a plain identifier, or `dialect` targets another
   *   database than the connection
   */
  def create[E](table: SqlTable[E], suffix: String)(using con: DbCon, dialect: Dialect): String = {
    QueueTable.requireDialectMatch(con.dialect, dialect)
    val validated  = QueueTable.SqlId.validate("suffix", suffix)
    val shadowName = s"${table.name}_$validated"
    val ddl        = Frag.literal(dialect.createShadowTableDDL(shadowName, table.name))
    ddl.update
    shadowName
  }

  /**
   * Renames `tableName` to `<tableName>_old_<suffix>` and
   * `<tableName>_<suffix>` to `tableName`, and returns `(oldName, tableName)`.
   * The two renames are atomic when the connection is a transaction, as in
   * `Transactor#transact`. It works on PostgreSQL and SQLite. Drop the old
   * table yourself once the result is checked.
   */
  def swap(tableName: String, suffix: String)(using con: DbCon): (String, String) = {
    val tblValid   = QueueTable.SqlId.validate("table", tableName)
    val sfxValid   = QueueTable.SqlId.validate("suffix", suffix)
    val shadowName = s"${tblValid}_$sfxValid"
    val oldName    = s"${tblValid}_old_$sfxValid"

    // Rename live → old, shadow → live (within same transaction for atomicity)
    Frag.literal(s"ALTER TABLE $tblValid RENAME TO $oldName").update
    Frag.literal(s"ALTER TABLE $shadowName RENAME TO $tblValid").update

    (oldName, tableName)
  }

  // ---- Pure preview helpers (no connection) ----

  /**
   * Returns the statement that [[create]] runs, without running it; on SQLite,
   * which has none, a `-- ...` comment that says so.
   */
  def createPreview[E](table: SqlTable[E], suffix: String)(using dialect: Dialect): String = {
    val validated  = QueueTable.SqlId.validate("suffix", suffix)
    val shadowName = s"${table.name}_$validated"
    try dialect.createShadowTableDDL(shadowName, table.name)
    catch { case e: UnsupportedOperationException => s"-- ${e.getMessage}" }
  }

  /** Returns the two renames that [[swap]] runs, without running them. */
  def swapPreview(tableName: String, suffix: String): List[String] = {
    val tblValid   = QueueTable.SqlId.validate("table", tableName)
    val sfxValid   = QueueTable.SqlId.validate("suffix", suffix)
    val shadowName = s"${tblValid}_$sfxValid"
    val oldName    = s"${tblValid}_old_$sfxValid"
    List(
      s"ALTER TABLE $tblValid RENAME TO $oldName",
      s"ALTER TABLE $shadowName RENAME TO $tblValid"
    )
  }
}
