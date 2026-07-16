/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless otherwise indicated, this file is licensed under the Apache 2.0 license.
 * See the LICENSE file in the project root for more information.
 */

package zio.blocks.data.migration

import zio.blocks.sql.{DbCon, Table}

/**
 * Applies a TargetStrategy, providing the actual table name to write to
 * and handling shadow table lifecycle (create, write, swap).
 */
object TargetStrategyApplier {

  /**
   * Resolves the effective target table name based on strategy.
   */
  def resolveTableName[E](table: Table[E], strategy: TargetStrategy): String =
    strategy match {
      case TargetStrategy.InPlace        => table.name
      case TargetStrategy.ShadowTable(n) => n
    }

  /**
   * Prepares the target: for shadow tables, creates the shadow table.
   * Returns the table name to write to.
   */
  def prepare[E](table: Table[E], strategy: TargetStrategy)(using con: DbCon): String =
    strategy match {
      case TargetStrategy.InPlace =>
        table.name
      case TargetStrategy.ShadowTable(suffix) =>
        // Create shadow table with the given suffix
        ShadowTable.create(table, suffix)
    }

  /**
   * Finalizes the target: for shadow tables, performs the atomic swap.
   * For in-place, no-op.
   * Returns (oldTableName, newTableName) for shadow, or (tableName, tableName) for in-place.
   */
  def finalize(tableName: String, strategy: TargetStrategy)(using con: DbCon): (String, String) =
    strategy match {
      case TargetStrategy.InPlace =>
        (tableName, tableName)
      case TargetStrategy.ShadowTable(suffix) =>
        ShadowTable.swap(tableName, suffix)
    }
}
