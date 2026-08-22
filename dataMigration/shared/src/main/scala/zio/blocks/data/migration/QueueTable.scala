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

import zio.blocks.sql.{DbCodec, DbCon, DbTx, Dialect, Frag, Transactor}
import zio.blocks.sql.Frag.*

/**
 * Helpers for migration queue tables used by migration workers, dialect-driven
 * to support both PostgreSQL and SQLite.
 *
 * Queue tables store IDs of aggregates pending migration. Workers dequeue
 * batches via `SELECT ... FOR UPDATE SKIP LOCKED` on PostgreSQL; SQLite uses a
 * plain `LIMIT` since it serializes writes.
 */
object QueueTable {

  /**
   * Fixed queue key column name, matching the documented `(id, op, payload)`
   * schema.
   */
  final val QueueKeyColumn = "id"

  private[migration] object SqlId {
    private val Identifier                            = raw"[A-Za-z_][A-Za-z0-9_]*".r
    def validate(kind: String, value: String): String =
      value match {
        case Identifier() => value
        case _            =>
          throw new IllegalArgumentException(
            s"Invalid SQL $kind identifier '$value'. Only ASCII letters, digits, and underscores are supported, and the first character must be a letter or underscore."
          )
      }
  }

  /**
   * Creates a queue table with columns (id, op, payload) for tracking dirty
   * keys. Uses the given `dialect` to generate database-appropriate DDL.
   *
   * Note: trigger installation is a separate step handled by the dialect's
   * `createTriggerDDL`; this method only creates the queue table itself.
   *
   * @param tableName
   *   Name of the queue table (validated as a bare SQL identifier)
   * @param transactor
   *   Transactor for executing DDL
   */
  def create[ID](tableName: String, transactor: Transactor)(using codec: DbCodec[ID], dialect: Dialect): Unit = {
    require(codec.columns.length == 1, "QueueTable only supports single-column ID codecs")
    val validated = SqlId.validate("table", tableName)
    val colName   = SqlId.validate("column", QueueKeyColumn)
    transactor.transact { (tx: DbTx) ?=>
      Frag.literal(dialect.createQueueTableDDL(validated, colName)).update
    }
  }

  /**
   * Installs capture triggers on `sourceTable` so that every INSERT, UPDATE,
   * and DELETE upserts the affected key into the queue table within the
   * writer's own transaction. This is the automatic counterpart to manual
   * `enqueue`: once installed, producers write normally and dirty keys are
   * captured without application-code changes.
   *
   * The queue key column is the fixed `id` column (see `QueueKeyColumn`) — use
   * the same codec for `create`/`enqueue`. `sourceIdColumn` names the primary
   * key column on the source table and may differ from it.
   *
   * Idempotent: safe to call again on restart (SQLite uses CREATE TRIGGER IF
   * NOT EXISTS; PostgreSQL uses CREATE OR REPLACE TRIGGER, which requires PG
   * 14+).
   *
   * Do NOT enable capture on the same physical table the migrator writes to:
   * the worker's own writes would re-enqueue processed keys forever.
   */
  def installTriggers[ID](queueTable: String, sourceTable: String, sourceIdColumn: String)(using
    tx: DbTx,
    dialect: Dialect,
    codec: DbCodec[ID]
  ): Unit = {
    require(codec.columns.length == 1, "QueueTable only supports single-column ID codecs")
    val q      = SqlId.validate("queue table", queueTable)
    val s      = SqlId.validate("source table", sourceTable)
    val srcCol = SqlId.validate("source id column", sourceIdColumn)
    val keyCol = SqlId.validate("column", QueueKeyColumn)
    val ddl    = dialect.createTriggerDDL(q, s, srcCol, keyCol)
    ddl.foreach(stmt => Frag.literal(stmt).update)
  }

  /**
   * Enqueues IDs into the queue table.
   *
   * The queue table stores IDs in a TEXT column for portability across ID
   * types. On PostgreSQL, numeric ID parameters are wrapped in
   * `CAST(... AS TEXT)` so that they work against the TEXT column without an
   * implicit-cast error.
   */
  def enqueue[ID](tableName: String, ids: Seq[ID])(using con: DbCon, codec: DbCodec[ID]): Unit = {
    if (ids.isEmpty) return
    val validated = SqlId.validate("table", tableName)
    val colName   = SqlId.validate("column", QueueKeyColumn)
    val rowFrags  = ids.map { id =>
      val params = codec.toDbValues(id)
      val parts  = IndexedSeq("(CAST(") ++ IndexedSeq.fill(params.size - 1)(" AS TEXT), CAST(") :+ " AS TEXT))"
      Frag(parts, params)
    }
    val valuesFrag = rowFrags.reduceLeft((a, b) => a ++ Frag.literal(", ") ++ b)
    val frag       = Frag.literal(s"INSERT INTO $validated ($colName) VALUES ") ++ valuesFrag ++ Frag.literal(
      " ON CONFLICT DO NOTHING"
    )
    frag.update
  }

  /**
   * Dequeues up to `batchSize` IDs. Uses the given `dialect` to generate
   * database-appropriate locking — `FOR UPDATE SKIP LOCKED` on PostgreSQL,
   * plain `LIMIT` on SQLite (which serializes writes via BEGIN IMMEDIATE).
   */
  def dequeue[ID](tableName: String, batchSize: Int)(using tx: DbTx, codec: DbCodec[ID], dialect: Dialect): List[ID] = {
    require(batchSize > 0, "batchSize must be positive")
    val validated = SqlId.validate("table", tableName)
    val colName   = SqlId.validate("column", QueueKeyColumn)
    val frag      = Frag.literal(dialect.dequeueSQL(validated, colName, batchSize))
    val rows      = frag.query[ID]
    // Delete dequeued rows with CAST(... AS TEXT) for numeric-ID/PG compatibility
    if (rows.nonEmpty) {
      val params = rows.flatMap(r => codec.toDbValues(r)).toIndexedSeq
      val parts  = IndexedSeq(s"DELETE FROM $validated WHERE $colName IN (CAST(") ++
        IndexedSeq.fill(params.size - 1)(" AS TEXT), CAST(") :+ " AS TEXT))"
      Frag(parts, params).update
    }
    rows
  }

  /**
   * Returns count of pending IDs in the queue.
   */
  def pending(tableName: String)(using con: DbCon): Long = {
    val validated = SqlId.validate("table", tableName)
    val frag      = Frag.literal(s"SELECT COUNT(*) FROM $validated")
    frag.queryOne[Long].getOrElse(0L)
  }
}
