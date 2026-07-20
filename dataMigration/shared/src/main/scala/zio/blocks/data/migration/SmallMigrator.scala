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
import zio.blocks.sql.{DbCodec, DbTx, Dialect, Repo, Transactor}

/**
 * A-Small: queue-based batch migration worker.
 *
 * Dequeues IDs from a queue table, reads entities via Repo.findAll, applies
 * Migration[A,B], writes via Repo.insertBatch. One transaction per batch.
 * Supports both in-place and shadow targets.
 */
final class SmallMigrator[A, B, ID1, ID2](
  repoV1: Repo[A, ID1],
  repoV2: Repo[B, ID2],
  migration: Migration[A, B],
  queueTable: String,
  batchSize: Int,
  target: TargetStrategy
)(using transactor: Transactor, codecId: DbCodec[ID1], dialect: Dialect) {

  private var writeRepo: Repo[B, ID2] = repoV2
  private var initialized: Boolean    = false

  /**
   * Prepare the target table. For ShadowTable strategy, creates the shadow
   * table via `TargetStrategyApplier.prepare`. Safe to call multiple times
   * (idempotent after first success).
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
    }
    initialized = true
  }

  /**
   * Finalize the migration. For ShadowTable strategy, performs the atomic
   * swap (shadow table replaces the live table). Requires init() first.
   */
  def complete(): Unit = {
    require(initialized, "init() must be called before complete()")
    transactor.transact { (tx: DbTx) ?=>
      TargetStrategyApplier.finalize(repoV2.table.name, target)
    }
  }

  /** Processes one batch. Returns count of migrated rows. */
  def processBatch(): Int = {
    require(initialized, "init() must be called before processBatch()")
    if (batchSize <= 0) return 0
    transactor.transact { (tx: DbTx) ?=>
      // 1. Dequeue up to batchSize IDs from queue table
      val ids = QueueTable.dequeue[ID1](queueTable, batchSize)(using tx, codecId, dialect)
      if (ids.isEmpty) 0
      else {
        // 2. Find entities by ID using Repo.findAll
        val entitiesV1 = repoV1.findAll(ids)(using tx)

        // 3. Apply Migration[A,B] to each entity
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
            val deleted    =
              if (deletedIds.nonEmpty) writeRepo.deleteAll(sameIds(deletedIds))(using tx) else 0
            migrated + deleted
        }
      }
    }
  }

  /** IDs dequeued from the queue (ID1) are the same logical identifiers used in
    * the target table (ID2) — migration does not change the ID type. */
  private def sameIds(ids: List[ID1]): List[ID2] =
    ids.asInstanceOf[List[ID2]]
}
