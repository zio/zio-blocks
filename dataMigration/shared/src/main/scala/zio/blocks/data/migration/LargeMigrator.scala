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
import zio.blocks.sql.*

/**
 * The Large tier: a worker that converts queued rows while the application
 * keeps writing, then cuts over to the new table. Each batch runs in one
 * transaction and converts rows as [[SmallMigrator]] does; on PostgreSQL,
 * several workers can share a queue, since claims use `SKIP LOCKED`.
 *
 * The lifecycle is [[init]], [[run]] while the application writes, then the
 * cutover: stop the writers, [[fence]], [[drain]], [[complete]]. `init()` does
 * not create the queue table; call [[QueueTable.create]] first. Call the
 * lifecycle methods from one thread; [[pause]] and [[resume]] may come from
 * another thread while `run()` or `drain()` is working.
 *
 * Two known bugs affect the cutover: `run()` can return with keys still queued
 * (see [[run]]), and `complete()` does not check the queue, so check
 * [[pendingCount]] right before it.
 *
 * {{{
 * import zio.blocks.sql.{zio => _, _}
 *
 * // Every writer to the source table has stopped before this point
 * def cutOver[A, B](migrator: LargeMigrator[A, B, Long, Long]): Unit = {
 *   migrator.fence()
 *   migrator.drain()
 *   require(migrator.pendingCount == 0, "a write reached the queue after drain(); stop all writers first")
 *   migrator.complete()
 * }
 * }}}
 *
 * @param batchSize
 *   the most keys one batch claims; [[run]] and [[drain]] throw
 *   `IllegalArgumentException` when it is `0` or less
 * @param captureTriggers
 *   whether `init()` installs triggers that queue every write to `repoV1`'s
 *   table; leave it off when `repoV1` and the target are the same table
 *
 * @see
 *   [[https://zioblocks.com/reference/data-migration/ Data Migration]]
 */
final class LargeMigrator[A, B, ID1, ID2](
  repoV1: Repo[A, ID1],
  repoV2: Repo[B, ID2],
  migration: Migration[A, B],
  queueTable: String,
  batchSize: Int,
  target: TargetStrategy,
  captureTriggers: Boolean = false
)(using transactor: Transactor, codecId: DbCodec[ID1], dialect: Dialect, ev: ID1 =:= ID2) {

  private val MaxEmptyDrainRounds = 100

  private enum State derives CanEqual {
    case Initialized, Fenced, Drained, Completed
  }

  @volatile private var _paused: Boolean = false

  /** Whether [[pause]] was called and [[resume]] has not been since. */
  def isPaused: Boolean = _paused

  /**
   * Asks [[run]] and [[drain]] to return after the batch they are working on; a
   * later `run()` returns at once, after [[init]], until [[resume]].
   */
  def pause(): Unit = _paused = true

  /**
   * Clears the pause, so the next [[run]] or [[drain]] processes batches again.
   */
  def resume(): Unit = _paused = false

  private var writeRepo: Repo[B, ID2]        = repoV2
  @volatile private var initialized: Boolean = false
  @volatile private var state: State         = State.Initialized

  /**
   * Prepares the target in one transaction: for `TargetStrategy.ShadowTable` it
   * creates the shadow table (a known bug gives it the target table's current
   * columns; on SQLite it throws `UnsupportedOperationException`), and with
   * `captureTriggers` it installs the capture triggers. Later calls do nothing,
   * also after [[complete]]. It does not create the queue table, and with
   * `captureTriggers` every write to the source table fails until the queue
   * table exists (a known bug). The triggers queue only writes made after
   * `init()`, so queue the existing keys afterwards.
   */
  def init(): Unit = {
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
   * Starts the cutover by moving the migrator from its initial state to the
   * fenced state, which [[drain]] requires. It changes nothing in the database
   * and blocks no write: stop every writer to the source table before calling
   * it. A key queued after [[drain]] returns is not converted.
   *
   * @throws java.lang.IllegalArgumentException
   *   if [[init]] has not run
   * @throws java.lang.IllegalStateException
   *   if the migrator is already fenced, drained or completed
   */
  def fence(): Unit = synchronized {
    require(initialized, "init() must be called before fence()")
    state match {
      case State.Initialized => state = State.Fenced
      case _                 => throw new IllegalStateException(s"cannot fence() in state $state")
    }
  }

  /**
   * Converts batches until [[pendingCount]] finds the queue empty, then moves
   * the migrator to the drained state that [[complete]] requires, and returns
   * the number of rows written or deleted. After a [[pause]] it returns early
   * and the migrator stays fenced, so `drain()` can run again.
   *
   * @throws java.lang.IllegalArgumentException
   *   if the migrator is not fenced, or `batchSize` is `0` or less
   * @throws java.lang.IllegalStateException
   *   if 100 batches in a row convert nothing while keys stay queued, for
   *   example because another worker holds their rows locked
   */
  def drain(): Int = {
    require(state == State.Fenced, s"must fence() before drain(); current state: $state")
    var total       = 0
    var emptyRounds = 0
    var drained     = false
    while (!drained && !_paused) {
      val batch = transactor.transact { (tx: DbTx) ?=>
        val ids = QueueTable.dequeue[ID1](queueTable, batchSize)(using tx, codecId, dialect)
        if (ids.isEmpty) 0
        else processBatch(ids)(using tx)
      }
      total += batch
      if (pendingCount == 0) drained = true
      else if (batch == 0) {
        emptyRounds += 1
        if (emptyRounds >= MaxEmptyDrainRounds)
          throw new IllegalStateException(
            s"Queue '$queueTable' did not drain after $MaxEmptyDrainRounds consecutive empty batches " +
              s"(${pendingCount} item(s) pending); a concurrent worker may be stuck"
          )
      } else emptyRounds = 0
    }
    if (!_paused && drained) synchronized { state = State.Drained }
    total
  }

  /**
   * Finishes the migration. For `TargetStrategy.ShadowTable` it renames the
   * table to `<table>_old_<suffix>` and the shadow table to `<table>`, in one
   * transaction; for `TargetStrategy.InPlace` it changes no table. The old
   * table, the queue table and any triggers stay; drop them yourself.
   *
   * It does not check the queue, so a key queued after [[drain]] returned is
   * left behind and its write is missing from the new table; this is a known
   * bug. Check that [[pendingCount]] is `0` right before calling it.
   *
   * @throws java.lang.IllegalArgumentException
   *   if the migrator is not drained
   */
  def complete(): Unit = synchronized {
    require(state == State.Drained, s"must drain() before complete(); current state: $state")
    transactor.transact { (tx: DbTx) ?=>
      TargetStrategyApplier.finalizeTarget(repoV2.table.name, target)
    }
    state = State.Completed
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
    buf += QueueTable.queueTableDDL(queueTable)
    TargetStrategyApplier.preparePreview(repoV2.table, target).foreach(buf += _)
    if (captureTriggers) buf ++= QueueTable.triggerDDLs(queueTable, repoV1.table.name, repoV1.idColumn)
    buf += QueueTable.dequeueSQLTemplate(queueTable, batchSize)
    buf ++= TargetStrategyApplier.finalizePreview(repoV2.table.name, target)
    buf.result()
  }

  /**
   * Returns the number of keys in the queue table, counted on a new connection.
   * Keys that another worker has claimed in an open transaction still count.
   */
  def pendingCount: Long =
    transactor.connect((tx: DbCon) ?=> QueueTable.pending(queueTable)(using tx))

  /**
   * Calls [[init]], then converts batches until a batch writes nothing or the
   * migrator is paused, and returns the number of rows written or deleted. It
   * does not cut over; use [[fence]], [[drain]] and [[complete]] for that.
   *
   * A batch whose keys all belong to rows deleted after they were queued writes
   * nothing, so `run()` returns while later keys are still queued; this is a
   * known bug. [[drain]] counts the queue instead.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `batchSize` is `0` or less, or [[complete]] has run
   */
  def run(): Int = {
    require(batchSize > 0, "batchSize must be positive")
    require(state != State.Completed, "cannot run() after complete(); create a new migrator")
    init()
    var total     = 0
    var keepGoing = true
    while (keepGoing) {
      if (_paused) return total
      val batch = transactor.transact { (tx: DbTx) ?=>
        val ids = QueueTable.dequeue[ID1](queueTable, batchSize)(using tx, codecId, dialect)
        if (ids.isEmpty) 0
        else processBatch(ids)(using tx)
      }
      total += batch
      if (batch == 0) keepGoing = false
    }
    total
  }

  private def processBatch(ids: List[ID1])(using tx: DbTx): Int = {
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
