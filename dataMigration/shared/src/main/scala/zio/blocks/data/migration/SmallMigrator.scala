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

import zio.blocks.schema.migration.Migration
import zio.blocks.sql.{DbCodec, DbCon, DbTx, Dialect, Repo, Transactor}

/**
 * The Small tier: converts the rows whose keys wait in a queue table, one batch
 * per [[processBatch]] call. Each batch runs in one transaction: it claims up
 * to `batchSize` keys from `queueTable`, reads those rows through `repoV1`,
 * converts them with `migration`, and writes them to the target that `target`
 * selects. A key whose source row no longer exists deletes the target row.
 *
 * The lifecycle is [[init]], then [[processBatch]] until `QueueTable.pending`
 * reports an empty queue, then [[complete]]. A batch whose keys all belong to
 * deleted rows returns `0` while other keys still wait, so loop on the pending
 * count, not on a `0` result. `init()` does not create the queue table; call
 * [[QueueTable.create]] first.
 *
 * {{{
 * import zio.blocks.schema._
 * import zio.blocks.schema.migration.Migration
 * import zio.blocks.sql.{zio => _, _}
 *
 * case class ProductV1(id: Long, name: String)
 * case class ProductV2(id: Long, name: String, stock: Int)
 * object ProductV1 { implicit val schema: Schema[ProductV1] = Schema.derived }
 * object ProductV2 { implicit val schema: Schema[ProductV2] = Schema.derived }
 *
 * val productsV1 = Repo.derived[ProductV1, Long]("products", "id", _.id)
 * val productsV2 = Repo.derived[ProductV2, Long]("products", "id", _.id)
 * val addStock: Migration[ProductV1, ProductV2] =
 *   Migration.newBuilder[ProductV1, ProductV2].addField(_.stock, SchemaExpr.literal(0)).build
 *
 * // `products` already has the new `stock` column, for example from a TinyMigrator
 * def backfill()(using transactor: Transactor, dialect: Dialect): Unit = {
 *   QueueTable.create[Long]("products_q", transactor)
 *   transactor.transact(QueueTable.enqueue("products_q", productsV1.all.map(_.id)))
 *
 *   val migrator =
 *     SmallMigrator[ProductV1, ProductV2, Long, Long](productsV1, productsV2, addStock, "products_q", 500, TargetStrategy.InPlace)
 *   migrator.init()
 *   while (transactor.connect(QueueTable.pending("products_q")) > 0) migrator.processBatch()
 *   migrator.complete()
 * }
 * }}}
 *
 * @param batchSize
 *   the most keys one batch claims; with `0` or less, `processBatch` claims
 *   nothing and returns `0`
 * @param captureTriggers
 *   whether `init()` installs triggers that queue every write to `repoV1`'s
 *   table; leave it off when `repoV1` and the target are the same table
 *
 * @see
 *   [[https://zioblocks.com/reference/data-migration/ Data Migration]]
 */
final class SmallMigrator[A, B, ID1, ID2](
  repoV1: Repo[A, ID1],
  repoV2: Repo[B, ID2],
  migration: Migration[A, B],
  queueTable: String,
  batchSize: Int,
  target: TargetStrategy,
  captureTriggers: Boolean = false
)(using transactor: Transactor, codecId: DbCodec[ID1], dialect: Dialect, ev: ID1 =:= ID2) {

  private var writeRepo: Repo[B, ID2]        = repoV2
  @volatile private var initialized: Boolean = false
  @volatile private var completed: Boolean   = false

  /**
   * Prepares the target in one transaction: for `TargetStrategy.ShadowTable` it
   * creates the shadow table (a known bug gives it the target table's current
   * columns; on SQLite it throws `UnsupportedOperationException`), and with
   * `captureTriggers` it installs the capture triggers. Later calls do nothing.
   * It does not create the queue table, and with `captureTriggers` every write
   * to the source table fails until the queue table exists (a known bug).
   *
   * @throws java.lang.IllegalArgumentException
   *   if called after [[complete]]
   */
  def init(): Unit = {
    require(!completed, "cannot init() after complete(); create a new migrator")
    if (initialized) return
    transactor.transact { (tx: DbTx) ?=>
      val resolvedName = TargetStrategyApplier.prepare(repoV2.table, target)(using tx, dialect)
      if (resolvedName != repoV2.table.name) {
        import zio.blocks.sql.{Table => SqlTable}
        val shadowTable = SqlTable(resolvedName, repoV2.table.codec, repoV2.table.columnsMeta)
        writeRepo = Repo(shadowTable, repoV2.idColumn, repoV2.idCodec, repoV2.getId)
      }
      if (captureTriggers) QueueTable.installTriggers[ID1](queueTable, repoV1.table.name, repoV1.idColumn)
    }
    initialized = true
  }

  /**
   * Finishes the migration. For `TargetStrategy.ShadowTable` it renames the
   * table to `<table>_old_<suffix>` and the shadow table to `<table>`, in one
   * transaction; for `TargetStrategy.InPlace` it changes no table. The old
   * table, the queue table and any triggers stay; drop them yourself.
   *
   * @throws java.lang.IllegalArgumentException
   *   if [[init]] has not run, if `complete` already ran, or if the queue table
   *   still holds keys
   */
  def complete(): Unit = {
    require(initialized, "init() must be called before complete()")
    require(!completed, "complete() must not be called twice; create a new migrator")
    val pending = transactor.connect((tx: DbCon) ?=> QueueTable.pending(queueTable)(using tx))
    require(pending == 0, s"cannot complete(): $pending item(s) still pending in queue '$queueTable'")
    transactor.transact { (tx: DbTx) ?=>
      TargetStrategyApplier.finalizeTarget(repoV2.table.name, target)
    }
    completed = true
  }

  /**
   * Returns the statements of the migration for the `Dialect` in scope, without
   * opening a connection: the queue table's `CREATE TABLE`, the shadow table's
   * `CREATE TABLE` (a `-- ...` comment on SQLite), the trigger statements when
   * `captureTriggers` is set, the claiming `SELECT`, and the two renames of a
   * shadow-table swap. `init()` runs neither the queue table's statement nor
   * the renames.
   */
  def previewSql(): Vector[String] = {
    val buf = Vector.newBuilder[String]
    // queue DDL
    buf += QueueTable.queueTableDDL(queueTable)
    // shadow create (if any)
    TargetStrategyApplier.preparePreview(repoV2.table, target).foreach(buf += _)
    // triggers
    if (captureTriggers) buf ++= QueueTable.triggerDDLs(queueTable, repoV1.table.name, repoV1.idColumn)
    // dequeue template
    buf += QueueTable.dequeueSQLTemplate(queueTable, batchSize)
    // finalize (rename) preview for shadow
    buf ++= TargetStrategyApplier.finalizePreview(repoV2.table.name, target)
    buf.result()
  }

  /**
   * Converts one batch in one transaction and returns the number of rows it
   * wrote or deleted in the target, `0` when the queue is empty. A conversion
   * failure throws `RuntimeException` (`Migration failed: ...`) and rolls the
   * batch back, so its keys stay queued.
   *
   * @throws java.lang.IllegalArgumentException
   *   if [[init]] has not run or [[complete]] has
   */
  def processBatch(): Int = {
    require(initialized, "init() must be called before processBatch()")
    require(!completed, "cannot processBatch() after complete(); create a new migrator")
    if (batchSize <= 0) return 0
    transactor.transact { (tx: DbTx) ?=>
      val ids = QueueTable.dequeue[ID1](queueTable, batchSize)(using tx, codecId, dialect)
      if (ids.isEmpty) 0
      else writeBatch(ids)(using tx)
    }
  }

  private def writeBatch(ids: List[ID1])(using tx: DbTx): Int = {
    val entitiesV1 = repoV1.findAll(ids)(using tx)
    val entitiesV2 = entitiesV1.map { entity =>
      migration.apply(entity) match {
        case Right(v2) => v2
        case Left(err) => throw new RuntimeException(s"Migration failed: $err")
      }
    }
    val foundIds   = entitiesV1.map(repoV1.getId).toSet
    val deletedIds = ids.filterNot(foundIds.contains)
    target match {
      case TargetStrategy.InPlace =>
        val updated = entitiesV2.foldLeft(0) { (count, e) =>
          count + repoV2.update(e)(using tx)
        }
        val deleted = if (deletedIds.nonEmpty) repoV2.deleteAll(sameIds(deletedIds))(using tx) else 0
        updated + deleted
      case TargetStrategy.ShadowTable(_) =>
        // Upsert semantics: capture triggers can re-enqueue an already-migrated
        // row (source UPDATE during the migration window), so a plain insert
        // would hit the shadow table's primary key. Update first, insert if absent.
        val migrated = entitiesV2.foldLeft(0) { (count, e) =>
          val updated = writeRepo.update(e)(using tx)
          if (updated > 0) count + updated
          else count + writeRepo.insert(e)(using tx)
        }
        val deleted = if (deletedIds.nonEmpty) writeRepo.deleteAll(sameIds(deletedIds))(using tx) else 0
        migrated + deleted
    }
  }

  /**
   * Reinterprets the claimed V1 keys as V2 keys, through the `ID1 =:= ID2`
   * evidence that the constructor requires.
   */
  private def sameIds(ids: List[ID1]): List[ID2] =
    ev.substituteCo[List](ids)
}
