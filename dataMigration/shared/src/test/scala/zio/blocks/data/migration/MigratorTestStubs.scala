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
import zio.blocks.sql._

/**
 * Shared `SmallMigrator` test fixtures: one body-executing counting transactor
 * plus the repos and migration stub every spec builds, so the stub cannot drift
 * between the guard specs and the concurrency spec.
 */
object MigratorTestStubs {

  /**
   * Transactor stub that counts preparation transactions AND runs their bodies,
   * so `init()` exercises its real path (`TargetStrategyApplier.prepare` plus
   * the shadow-table swap bookkeeping) instead of skipping it.
   *
   * Honesty note: the body runs with a null connection. That is sound only for
   * the `InPlace` strategy without capture triggers, whose preparation never
   * touches the connection (pure name resolution). What this stub does NOT
   * prove is database-level idempotence under real races: concurrent
   * preparation against a live database rests on the idempotent trigger DDL
   * (`CREATE TRIGGER IF NOT EXISTS` on SQLite, `CREATE OR REPLACE TRIGGER` on
   * PostgreSQL — see `QueueTable.installTriggers` and `SmallMigrator.init`).
   */
  final class CountingTransactor extends Transactor {
    val transactCalls                          = new java.util.concurrent.atomic.AtomicInteger(0)
    override def connect[A](f: DbCon ?=> A): A = 0L.asInstanceOf[A]
    override def transact[A](f: DbTx ?=> A): A = {
      transactCalls.incrementAndGet()
      given noConnection: DbTx = null.asInstanceOf[DbTx]
      f
    }
    override def transact[A](isolation: TransactionIsolation, readOnly: Boolean)(f: DbTx ?=> A): A =
      transact(f)
  }

  private val idColumns = IndexedSeq(ColumnMeta("id", DbValue.DbInt(0), nullable = false))
  val v1Repo            =
    Repo(Table[Int]("users_v1", DbCodec.intCodec, idColumns), "id", DbCodec.intCodec, identity)
  val v2Repo =
    Repo(Table[Int]("users_v2", DbCodec.intCodec, idColumns), "id", DbCodec.intCodec, identity)

  // Never invoked against a real database: stubs either skip transaction bodies
  // entirely or run only the connection-free `InPlace` preparation path.
  val unusedMigration: Migration[Int, Int] = null.asInstanceOf[Migration[Int, Int]]

  def newInPlaceMigrator(tx: Transactor)(using Dialect): SmallMigrator[Int, Int, Int, Int] =
    SmallMigrator[Int, Int, Int, Int](
      repoV1 = v1Repo,
      repoV2 = v2Repo,
      migration = unusedMigration,
      queueTable = "q",
      batchSize = 10,
      target = TargetStrategy.InPlace
    )(using tx, DbCodec.intCodec)
}
