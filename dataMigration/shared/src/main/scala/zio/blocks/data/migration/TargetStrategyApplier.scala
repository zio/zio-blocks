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

import zio.blocks.sql.{DbCon, Dialect, Table}

/**
 * Carries out a [[TargetStrategy]] for a table: names the table to write to,
 * creates the shadow table and swaps it in. [[SmallMigrator]] and
 * [[LargeMigrator]] call it; `TargetStrategy.InPlace` makes each step a no-op.
 */
object TargetStrategyApplier {

  /**
   * Returns the table that converted rows go to: `table.name` for `InPlace`,
   * `<table>_<suffix>` for `ShadowTable(suffix)`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if the suffix is not a plain identifier
   */
  def resolveTableName[E](table: Table[E], strategy: TargetStrategy): String =
    strategy match {
      case TargetStrategy.InPlace        => table.name
      case TargetStrategy.ShadowTable(n) => table.name + "_" + QueueTable.SqlId.validate("suffix", n)
    }

  /**
   * Prepares the target and returns the table to write to: `table.name` for
   * `InPlace`, and for `ShadowTable` the shadow table that
   * [[ShadowTable.create]] creates.
   */
  def prepare[E](table: Table[E], strategy: TargetStrategy)(using con: DbCon, dialect: Dialect): String =
    strategy match {
      case TargetStrategy.InPlace =>
        table.name
      case TargetStrategy.ShadowTable(suffix) =>
        // Create shadow table with the given suffix
        ShadowTable.create(table, suffix)
    }

  /**
   * Finishes the target: for `ShadowTable`, runs [[ShadowTable.swap]] and
   * returns `(oldName, tableName)`; for `InPlace`, does nothing and returns
   * `(tableName, tableName)`.
   */
  def finalizeTarget(tableName: String, strategy: TargetStrategy)(using con: DbCon): (String, String) =
    strategy match {
      case TargetStrategy.InPlace =>
        (tableName, tableName)
      case TargetStrategy.ShadowTable(suffix) =>
        ShadowTable.swap(tableName, suffix)
    }

  // ---- Pure preview helpers (no connection) ----

  /**
   * Returns the statement that [[prepare]] runs, without running it: `None` for
   * `InPlace`, and for `ShadowTable` the shadow table's `CREATE TABLE`, or on
   * SQLite a `-- ...` comment.
   */
  def preparePreview[E](table: Table[E], strategy: TargetStrategy)(using dialect: Dialect): Option[String] =
    strategy match {
      case TargetStrategy.InPlace             => None
      case TargetStrategy.ShadowTable(suffix) =>
        val shadowName = s"${table.name}_${QueueTable.SqlId.validate("suffix", suffix)}"
        try Some(dialect.createShadowTableDDL(shadowName, table.name))
        catch { case e: UnsupportedOperationException => Some(s"-- ${e.getMessage}") }
    }

  /**
   * Returns the statements that [[finalizeTarget]] runs, without running them:
   * none for `InPlace`, the two renames for `ShadowTable`.
   */
  def finalizePreview(tableName: String, strategy: TargetStrategy): List[String] =
    strategy match {
      case TargetStrategy.InPlace             => Nil
      case TargetStrategy.ShadowTable(suffix) =>
        val tbl        = QueueTable.SqlId.validate("table", tableName)
        val sfx        = QueueTable.SqlId.validate("suffix", suffix)
        val shadowName = s"${tbl}_$sfx"
        val oldName    = s"${tbl}_old_$sfx"
        List(
          s"ALTER TABLE $tbl RENAME TO $oldName",
          s"ALTER TABLE $shadowName RENAME TO $tbl"
        )
    }
}
