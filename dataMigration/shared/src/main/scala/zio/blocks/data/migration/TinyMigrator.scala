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
 * The Tiny tier: schema statements (DDL) that run at application startup, in
 * one transaction. Extend it, put the statements in [[run]], and call
 * [[migrate]] before the application serves requests. It converts no rows; use
 * [[SmallMigrator]] or [[LargeMigrator]] for that.
 *
 * If `run` throws, `migrate` rolls the transaction back and rethrows the
 * exception. On databases whose DDL is transactional, such as PostgreSQL and
 * SQLite, the rollback also undoes the statements that already ran. The class
 * keeps no record of having run, so write statements that are safe to repeat,
 * such as `CREATE TABLE IF NOT EXISTS`.
 *
 * {{{
 * import zio.blocks.sql.{zio => _, _}
 *
 * class CreateInvoices(transactor: Transactor) extends TinyMigrator(transactor) {
 *   def run()(using tx: DbTx): Unit = {
 *     Ddl.createTable("invoices", IndexedSeq(
 *       ColumnDef("id", "BIGINT", nullable = false),
 *       ColumnDef("total_cents", "BIGINT", nullable = false)
 *     )).update
 *   }
 * }
 *
 * def migrateAtStartup(transactor: Transactor): Unit = new CreateInvoices(transactor).migrate()
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/data-migration/ Data Migration]]
 */
abstract class TinyMigrator(transactor: Transactor) {

  /**
   * The schema statements to run, inside the transaction that [[migrate]]
   * opens.
   */
  def run()(using tx: DbTx): Unit

  /**
   * Runs [[run]] in one `Transactor#transact` block, which commits when it
   * returns and rolls back when it throws.
   */
  final def migrate(): Unit =
    transactor.transact((tx: DbTx) ?=> run()(using tx))
}
