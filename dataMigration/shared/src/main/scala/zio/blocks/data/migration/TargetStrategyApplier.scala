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
 * Applies a TargetStrategy, providing the actual table name to write to and
 * handling shadow table lifecycle (create, write, swap).
 */
object TargetStrategyApplier {

  /**
   * Resolves the effective target table name based on strategy.
   */
  def resolveTableName[E](table: Table[E], strategy: TargetStrategy): String =
    strategy match {
      case TargetStrategy.InPlace        => table.name
      case TargetStrategy.ShadowTable(n) => table.name + "_" + QueueTable.SqlId.validate("suffix", n)
    }

  /**
   * Prepares the target: for shadow tables, creates the shadow table. Returns
   * the table name to write to.
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
   * Finalizes the target: for shadow tables, performs the atomic swap. For
   * in-place, no-op. Returns (oldTableName, newTableName) for shadow, or
   * (tableName, tableName) for in-place.
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
   * Pure shadow-create DDL for the given dialect, if strategy is ShadowTable.
   */
  def preparePreview[E](table: Table[E], strategy: TargetStrategy)(using dialect: Dialect): Option[String] =
    strategy match {
      case TargetStrategy.InPlace             => None
      case TargetStrategy.ShadowTable(suffix) =>
        val shadowName = s"${table.name}_${QueueTable.SqlId.validate("suffix", suffix)}"
        try Some(dialect.createShadowTableDDL(shadowName, table.name))
        catch { case e: UnsupportedOperationException => Some(s"-- ${e.getMessage}") }
    }

  /** Pure finalize (swap/rename) statements for the given strategy. */
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
