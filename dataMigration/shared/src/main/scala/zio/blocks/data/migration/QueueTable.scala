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

import zio.blocks.sql.{DbCodec, DbCon, Frag, Transactor}
import zio.blocks.sql.Frag.*

/**
 * Helpers for Postgres queue tables used by migration workers.
 *
 * Queue tables store IDs of aggregates pending migration. Workers dequeue
 * batches via `SELECT ... FOR UPDATE SKIP LOCKED`.
 */
object QueueTable {

  private object SqlId {
    private val Identifier = raw"[A-Za-z_][A-Za-z0-9_]*".r
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
   * Creates a queue table with a single ID column.
   *
   * @param tableName
   *   Name of the queue table (validated as SQL identifier)
   * @param transactor
   *   Transactor for executing DDL
   */
  def create[ID](tableName: String, transactor: Transactor)(using codec: DbCodec[ID]): Unit = {
    val validated = SqlId.validate("table", tableName)
    val colName   = SqlId.validate("column", codec.columns.headOption.getOrElse("id"))
    val ddl       = Frag.literal(s"CREATE TABLE IF NOT EXISTS $validated (\n  $colName TEXT NOT NULL\n)")
    transactor.connect { (con: DbCon) ?=> ddl.update(using con) }
  }

  /**
   * Enqueues IDs into the queue table.
   */
  def enqueue[ID](tableName: String, ids: Seq[ID])(using con: DbCon, codec: DbCodec[ID]): Unit = {
    if (ids.isEmpty) return
    val validated = SqlId.validate("table", tableName)
    val colName   = SqlId.validate("column", codec.columns.headOption.getOrElse("id"))
    val valuesFrag = Frag.values(ids)
    val frag       = Frag.literal(s"INSERT INTO $validated ($colName) VALUES ") ++ valuesFrag ++ Frag.literal(" ON CONFLICT DO NOTHING")
    frag.update
  }

  /**
   * Dequeues up to `batchSize` IDs using SKIP LOCKED.
   */
  def dequeue[ID](tableName: String, batchSize: Int)(using con: DbCon, codec: DbCodec[ID]): List[ID] = {
    val validated = SqlId.validate("table", tableName)
    val colName   = SqlId.validate("column", codec.columns.headOption.getOrElse("id"))
    val frag      = Frag.literal(s"SELECT $colName FROM $validated ORDER BY $colName FOR UPDATE SKIP LOCKED LIMIT $batchSize")
    val rows      = frag.query[ID]
    // Delete dequeued rows (parameterized)
    if (rows.nonEmpty) {
      val rowFrags = rows.map { r =>
        val params = codec.toDbValues(r)
        val parts  = IndexedSeq("(") ++ IndexedSeq.fill(params.size - 1)(", ") :+ ")"
        Frag(parts, params)
      }
      val inFrag = rowFrags.reduceLeft((a, b) => a ++ Frag.literal(", ") ++ b)
      (Frag.literal(s"DELETE FROM $validated WHERE $colName IN ") ++ inFrag).update
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
