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
 * A-Small: queue-based batch migration worker.
 *
 * Dequeues IDs from a queue table, reads entities via Repo.findAll, applies
 * Migration[A,B], and writes to the target repo. Rows whose source entity no
 * longer exists are deleted from the target (ADR Decision 3). One transaction
 * per batch. Supports both in-place and shadow targets.
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
   * Prepare the target table. For ShadowTable strategy, creates the shadow
   * table via `TargetStrategyApplier.prepare`. When `captureTriggers` is
   * enabled, installs capture triggers on the source table. Safe to call
   * multiple times (idempotent after first success).
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
   * Finalize the migration. For ShadowTable strategy, performs the atomic swap
   * (shadow table replaces the live table). Requires init() first and refuses
   * to run while the queue still has pending items — draining is the caller's
   * responsibility.
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

  /** Processes one batch. Returns count of migrated rows. */
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
        // Shadow tables start empty, so rows are inserted fresh rather than updated.
        val migrated = if (entitiesV2.nonEmpty) writeRepo.insertBatch(entitiesV2)(using tx) else 0
        val deleted  = if (deletedIds.nonEmpty) writeRepo.deleteAll(sameIds(deletedIds))(using tx) else 0
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
