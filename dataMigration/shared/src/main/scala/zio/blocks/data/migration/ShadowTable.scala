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

import zio.blocks.sql.{DbCon, Frag, Table as SqlTable}

/**
 * Helpers for creating and swapping shadow tables during large migrations.
 *
 * Pattern: CREATE TABLE users_v2 LIKE users; migrate into v2; swap atomically.
 */
object ShadowTable {

  /**
   * Creates a shadow table by copying structure from the source table.
   * Uses `CREATE TABLE ... LIKE` (Postgres) or equivalent.
   */
  def create[E](table: SqlTable[E], suffix: String)(using con: DbCon): String = {
    val shadowName = s"${table.name}_$suffix"
    val ddl        = Frag.literal(s"CREATE TABLE $shadowName (LIKE ${table.name} INCLUDING ALL)")
    ddl.update
    shadowName
  }

  /**
   * Atomically swaps the live table with the shadow table.
   *
   * Postgres: renames live → live_old_suffix, shadow → live.
   * Caller is responsible for dropping the old table after verification.
   */
  def swap(tableName: String, suffix: String)(using con: DbCon): (String, String) = {
    val shadowName = s"${tableName}_$suffix"
    val oldName    = s"${tableName}_old_$suffix"

    // Rename live → old, shadow → live (within same transaction for atomicity)
    Frag.literal(s"ALTER TABLE $tableName RENAME TO $oldName").update
    Frag.literal(s"ALTER TABLE $shadowName RENAME TO $tableName").update

    (oldName, tableName)
  }
}
