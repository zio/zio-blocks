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
import zio.blocks.sql.{DbCodec, DbCon, DbTx, Repo, Transactor}

/**
 * A-Small: queue-based batch migration worker.
 *
 * Dequeues IDs from a queue table, reads entities via Repo.findAll,
 * applies Migration[A,B], writes via Repo.insertBatch.
 * One transaction per batch. Supports both in-place and shadow targets.
 */
final class SmallMigrator[A, B, ID](
  repoV1: Repo[A, ID],
  repoV2: Repo[B, ID],
  migration: Migration[A, B],
  queueTable: String,
  batchSize: Int,
  target: TargetStrategy
)(using transactor: Transactor, codecId: DbCodec[ID]) {

  private var writeRepo: Repo[B, ID] = repoV2

  def init(): Unit = {
    transactor.connect { (con: DbCon) ?=> 
      val resolvedName = TargetStrategyApplier.prepare(repoV2.table, target)
      if (resolvedName != repoV2.table.name) {
        import zio.blocks.sql.{Table => SqlTable}
        val shadowTable = SqlTable(resolvedName, repoV2.table.codec, repoV2.table.columnsMeta)
        writeRepo = Repo(shadowTable, repoV2.idColumn, repoV2.idCodec, repoV2.getId)
      }
    }
  }

  def complete(): Unit = {
    transactor.connect { (con: DbCon) ?=> 
      TargetStrategyApplier.finalize(repoV2.table.name, target)
    }
  }

  /** Processes one batch. Returns count of migrated rows. */
  def processBatch(): Int = {
    if (batchSize <= 0) return 0
    transactor.transact { (tx: DbTx) ?=> 
      // 1. Dequeue up to batchSize IDs from queue table
      val ids = QueueTable.dequeue[ID](queueTable, batchSize)(using tx, summon[DbCodec[ID]])
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
            entitiesV2.foreach(e => repoV2.update(e)(using tx))
            entitiesV2.size
          case TargetStrategy.ShadowTable(_) =>
            writeRepo.insertBatch(entitiesV2)(using tx)
        }
      }
    }
  }
}
