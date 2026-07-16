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

import zio.blocks.sql.*

/**
 * A-Tiny: startup migration that runs schema DDL within a single transaction.
 *
 * Usage:
 * {{{
 * class MyStartupMigrations(transactor: Transactor) extends TinyMigrator(transactor) {
 *   def run()(using tx: DbTx): Unit = {
 *     Ddl.createTable("users", IndexedSeq(
 *       ColumnDef("id", "BIGSERIAL", nullable = false),
 *       ColumnDef("email", "TEXT", nullable = false)
 *     )).update
 *   }
 * }
 * }}}
 */
abstract class TinyMigrator(transactor: Transactor) {
  /** Override to define DDL operations. Runs inside transactor.transact. */
  def run()(using tx: DbTx): Unit

  /** Executes the migration at startup. Wraps run() in transactor.transact. */
  final def migrate(): Unit =
    transactor.transact { (tx: DbTx) ?=> run()(using tx) }
}
