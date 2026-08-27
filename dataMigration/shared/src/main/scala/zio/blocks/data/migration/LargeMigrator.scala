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
 * B-Large: incremental worker that processes migration queue until empty.
 * Supports pause/resume and progress reporting.
 *
 * The lifecycle methods (init, fence, drain, complete) are internally
 * consistent but are expected to be driven from a single thread; pause and
 * resume are safe to call from other threads while run() or drain() is in
 * flight.
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

  /** Whether the migrator is currently paused (checked inside run()). */
  def isPaused: Boolean = _paused

  /** Pause the migrator loop. In-flight batches complete before exit. */
  def pause(): Unit = _paused = true

  /** Resume processing after a pause. */
  def resume(): Unit = _paused = false

  private var writeRepo: Repo[B, ID2]        = repoV2
  @volatile private var initialized: Boolean = false
  @volatile private var state: State         = State.Initialized

  /**
   * Prepare the target table. For ShadowTable strategy, creates the shadow
   * table via `TargetStrategyApplier.prepare`. When `captureTriggers` is
   * enabled, installs capture triggers on the source table. Safe to call
   * multiple times (idempotent after first success).
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
   * Signal cutover: no more producers will write to the old table. After
   * fence(), drain() will process remaining items and verify emptiness via
   * COUNT query before allowing complete().
   *
   * The application MUST ensure that producers stop writing to the old table
   * before calling fence(). Writes arriving after fence() may be lost or
   * require a separate reconciliation.
   */
  def fence(): Unit = synchronized {
    require(initialized, "init() must be called before fence()")
    state match {
      case State.Initialized => state = State.Fenced
      case _                 => throw new IllegalStateException(s"cannot fence() in state $state")
    }
  }

  /**
   * Drain remaining queue items until pendingCount confirms the queue is truly
   * empty. Uses a separate COUNT query for verification, not just the dequeue
   * result. If batches come back empty while items remain pending for more than
   * MaxEmptyDrainRounds consecutive rounds (e.g. a stuck concurrent worker
   * holding row locks), drain() fails loudly instead of spinning.
   *
   * @return
   *   total items processed during drain
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
   * Finalize the migration by swapping the shadow table. Requires Drained state
   * (fence() + drain() completed successfully). After complete(), the shadow
   * table becomes the live table and the old table is renamed to
   * `{table}_old_{suffix}`.
   */
  def complete(): Unit = synchronized {
    require(state == State.Drained, s"must drain() before complete(); current state: $state")
    transactor.transact { (tx: DbTx) ?=>
      TargetStrategyApplier.finalizeTarget(repoV2.table.name, target)
    }
    state = State.Completed
  }

  /** Returns queue size estimate (pending items). */
  def pendingCount: Long =
    transactor.connect((tx: DbCon) ?=> QueueTable.pending(queueTable)(using tx))

  /**
   * Runs worker loop until queue is empty or paused. Returns total migrated.
   * Does not auto-complete; caller must use fence/drain/complete protocol for
   * safe cutover.
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
   * Reinterpret dequeued V1 IDs as V2 IDs using the type equality evidence
   * supplied at construction time. Safe because the constructor requires
   * `ID1 =:= ID2` (see ADR Decision 7).
   */
  private def sameIds(ids: List[ID1]): List[ID2] =
    ev.substituteCo[List](ids)
}
