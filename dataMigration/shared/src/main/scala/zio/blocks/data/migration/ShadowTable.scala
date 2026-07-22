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
 * Helpers for creating and swapping shadow tables during large migrations.
 *
 * Pattern: CREATE TABLE users_v2 LIKE users; migrate into v2; swap atomically.
 */
object ShadowTable {

  /**
   * Creates a shadow table by copying structure from the source table. Uses the
   * dialect from `DbCon.dialect` to generate database-appropriate DDL —
   * `CREATE TABLE ... (LIKE ... INCLUDING ALL)` on PostgreSQL, or raises an
   * error on SQLite (which requires manual DDL).
   */
  def create[E](table: SqlTable[E], suffix: String)(using con: DbCon, dialect: Dialect): String = {
    val validated  = QueueTable.SqlId.validate("suffix", suffix)
    val shadowName = s"${table.name}_$validated"
    val ddl        = Frag.literal(dialect.createShadowTableDDL(shadowName, table.name))
    ddl.update
    shadowName
  }

  /**
   * Atomically swaps the live table with the shadow table.
   *
   * Postgres: renames live → live_old_suffix, shadow → live. Caller is
   * responsible for dropping the old table after verification.
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
}
