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
 */
final class LargeMigrator[A, B, ID1, ID2](
  repoV1: Repo[A, ID1],
  repoV2: Repo[B, ID2],
  migration: Migration[A, B],
  queueTable: String,
  batchSize: Int,
  target: TargetStrategy
)(using transactor: Transactor, codecId: DbCodec[ID1], dialect: Dialect) {

  enum State derives CanEqual {
    case Initialized, Fenced, Drained, Completed
  }

  @volatile private var _paused: Boolean = false

  def isPaused: Boolean = _paused
  def pause(): Unit     = _paused = true
  def resume(): Unit    = _paused = false

  private var writeRepo: Repo[B, ID2] = repoV2
  private var initialized: Boolean    = false
  private var state: State            = State.Initialized

  def init(): Unit = {
    if (initialized) return
    transactor.transact { (tx: DbTx) ?=>
      val resolvedName = TargetStrategyApplier.prepare(repoV2.table, target)(using tx, dialect)
      if (resolvedName != repoV2.table.name) {
        import zio.blocks.sql.{Table => SqlTable}
        val shadowTable = SqlTable(resolvedName, repoV2.table.codec, repoV2.table.columnsMeta)
        writeRepo = Repo(shadowTable, repoV2.idColumn, repoV2.idCodec, repoV2.getId)
      }
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
  def fence(): Unit = {
    require(initialized, "init() must be called before fence()")
    state match {
      case State.Initialized => state = State.Fenced
      case _                 => throw new IllegalStateException(s"cannot fence() in state $state")
    }
  }

  /**
   * Drain remaining queue items until pendingCount confirms the queue is truly
   * empty. Uses a separate COUNT query for verification, not just the dequeue
   * result.
   *
   * @return
   *   total items processed during drain
   */
  def drain(): Int = {
    require(state == State.Fenced, s"must fence() before drain(); current state: $state")
    var total     = 0
    var keepGoing = true
    while (keepGoing && !_paused) {
      val batch = transactor.transact { (tx: DbTx) ?=>
        val ids = QueueTable.dequeue[ID1](queueTable, batchSize)(using tx, codecId, dialect)
        if (ids.isEmpty) 0
        else processBatch(ids)(using tx)
      }
      total += batch
      if (pendingCount == 0) keepGoing = false
    }
    if (!_paused) state = State.Drained
    total
  }

  /**
   * Finalize the migration by swapping the shadow table. Requires Drained state
   * (fence() + drain() completed successfully). After complete(), the shadow
   * table becomes the live table and the old table is renamed to
   * `{table}_old_{suffix}`.
   */
  def complete(): Unit = {
    require(state == State.Drained, s"must drain() before complete(); current state: $state")
    transactor.transact { (tx: DbTx) ?=>
      TargetStrategyApplier.finalize(repoV2.table.name, target)
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
    target match {
      case TargetStrategy.InPlace =>
        entitiesV2.foldLeft(0) { (count, e) =>
          count + repoV2.update(e)(using tx)
        }
      case TargetStrategy.ShadowTable(_) =>
        val migrated   = writeRepo.insertBatch(entitiesV2)(using tx)
        val foundIds   = entitiesV1.map(repoV1.getId).toSet
        val deletedIds = ids.filterNot(foundIds.contains)
        val deleted    = if (deletedIds.nonEmpty) writeRepo.deleteAll(deletedIds.asInstanceOf[List[ID2]])(using tx) else 0
        migrated + deleted
    }
  }
}
