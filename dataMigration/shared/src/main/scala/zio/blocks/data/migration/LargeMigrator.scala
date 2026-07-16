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
final class LargeMigrator[A, B, ID](
  repoV1: Repo[A, ID],
  repoV2: Repo[B, ID],
  migration: Migration[A, B],
  queueTable: String,
  batchSize: Int,
  target: TargetStrategy
)(using transactor: Transactor, codecId: DbCodec[ID]) {

  @volatile private var _paused: Boolean = false
  
  def isPaused: Boolean = _paused
  def pause(): Unit = _paused = true
  def resume(): Unit = _paused = false

  /** Returns queue size estimate (pending items). */
  def pendingCount: Long =
    transactor.connect { (tx: DbCon) ?=> QueueTable.pending(queueTable)(using tx) }

  /** Runs worker loop until queue is empty or paused. Returns total migrated. */
  def run(): Int = {
    var total = 0
    var keepGoing = true
    while (keepGoing) {
      if (_paused) return total
      val batch = transactor.transact { (tx: DbTx) ?=>
        val ids = QueueTable.dequeue[ID](queueTable, batchSize)(using tx, codecId)
        if (ids.isEmpty) 0
        else {
          val entitiesV1 = repoV1.findAll(ids)(using tx)
          val entitiesV2 = entitiesV1.map { entity =>
            migration.apply(entity) match {
              case Right(v2) => v2
              case Left(err) => throw new RuntimeException(s"Migration failed: $err")
            }
          }
          target match {
            case TargetStrategy.InPlace | TargetStrategy.ShadowTable(_) =>
              repoV2.insertBatch(entitiesV2)(using tx)
          }
        }
      }
      total += batch
      if (batch == 0) keepGoing = false
    }
    total
  }
}
