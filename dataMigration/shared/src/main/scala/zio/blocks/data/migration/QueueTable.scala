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
 * Helpers for Postgres queue tables used by migration workers.
 *
 * Queue tables store IDs of aggregates pending migration. Workers dequeue
 * batches via `SELECT ... FOR UPDATE SKIP LOCKED`.
 */
object QueueTable {

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
   * Creates a queue table (id, op, payload) and installs capture triggers on the source table.
   * Uses the dialect from `DbCon.dialect` to generate database-appropriate DDL.
   *
   * @param tableName
   *   Name of the queue table (validated as SQL identifier)
   * @param sourceTable
   *   Name of the source table to attach triggers to
   * @param sourceIdColumn
   *   Name of the ID column in the source table
   * @param transactor
   *   Transactor for executing DDL
   */
  def create[ID](tableName: String, transactor: Transactor)(using codec: DbCodec[ID], dialect: Dialect): Unit = {
    require(codec.columns.length == 1, "QueueTable only supports single-column ID codecs")
    val validated = SqlId.validate("table", tableName)
    val colName   = SqlId.validate("column", codec.columns.headOption.getOrElse("id"))
    transactor.connect { (con: DbCon) ?=>
      Frag.literal(dialect.createQueueTableDDL(validated, colName)).update
    }
  }

  /**
   * Enqueues IDs into the queue table.
   */
  def enqueue[ID](tableName: String, ids: Seq[ID])(using con: DbCon, codec: DbCodec[ID]): Unit = {
    if (ids.isEmpty) return
    val validated  = SqlId.validate("table", tableName)
    val colName    = SqlId.validate("column", codec.columns.headOption.getOrElse("id"))
    val valuesFrag = Frag.values(ids)
    val frag       = Frag.literal(s"INSERT INTO $validated ($colName) VALUES ") ++ valuesFrag ++ Frag.literal(
      " ON CONFLICT DO NOTHING"
    )
    frag.update
  }

  /**
   * Dequeues up to `batchSize` IDs. Uses the dialect from `tx.dialect` to
   * generate database-appropriate locking — `FOR UPDATE SKIP LOCKED` on
   * PostgreSQL, plain `LIMIT` on SQLite (which serializes writes via BEGIN
   * IMMEDIATE).
   */
  def dequeue[ID](tableName: String, batchSize: Int)(using tx: DbTx, codec: DbCodec[ID], dialect: Dialect): List[ID] = {
    require(batchSize > 0, "batchSize must be positive")
    val validated = SqlId.validate("table", tableName)
    val colName   = SqlId.validate("column", codec.columns.headOption.getOrElse("id"))
    val frag      = Frag.literal(dialect.dequeueSQL(validated, colName, batchSize))
    val rows      = frag.query[ID]
    // Delete dequeued rows (parameterized)
    if (rows.nonEmpty) {
      val params = rows.flatMap(r => codec.toDbValues(r)).toIndexedSeq
      val parts  =
        IndexedSeq(s"DELETE FROM $validated WHERE $colName IN (") ++ IndexedSeq.fill(params.size - 1)(", ") :+ ")"
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
