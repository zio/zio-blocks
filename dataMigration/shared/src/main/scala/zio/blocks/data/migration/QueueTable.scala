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

import zio.blocks.sql.{DbCodec, DbCon, DbTx, Dialect, Frag, SqlDialect, Transactor}
import zio.blocks.sql.Frag.*

/**
 * The queue table of a migration: the keys of the source rows that still need
 * converting. [[SmallMigrator]] and [[LargeMigrator]] claim keys from it in
 * batches. Keys enter it through [[enqueue]], called by the application for the
 * rows it writes or backfills, or through the capture triggers of
 * [[installTriggers]].
 *
 * The table has three columns: `id TEXT NOT NULL PRIMARY KEY`, the key as text;
 * `op`, `I`, `U` or `D` for the write that queued it; and `payload`, which the
 * PostgreSQL delete trigger fills with the deleted row as JSON. A key is queued
 * at most once. Keys are claimed in text order (`"10"` before `"9"`).
 *
 * On PostgreSQL, claims use `FOR UPDATE SKIP LOCKED`, so several workers can
 * share a queue. On SQLite, `JdbcTransactor` starts each `transact` block with
 * `BEGIN IMMEDIATE` and a 5-second busy timeout, so one worker claims at a
 * time. The methods check every table and column name they use (ASCII letters,
 * digits and underscores, not starting with a digit) and throw
 * `IllegalArgumentException` otherwise; `enqueue` with no keys checks nothing.
 *
 * {{{
 * import zio.blocks.sql.{zio => _, _}
 *
 * QueueTable.dequeueSQLTemplate("orders_q", 100)(using Dialect.Postgres)
 * // SELECT id FROM orders_q ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/data-migration/ Data Migration]]
 */
object QueueTable {

  /** The name of the queue table's key column, `id`. */
  final val QueueKeyColumn = "id"

  /**
   * Fails fast when the given generation `Dialect` targets a different database
   * than the live connection — mismatched SQL would otherwise fail deep inside
   * the driver with confusing errors.
   */
  private[migration] def requireDialectMatch(actual: SqlDialect, dialect: Dialect): Unit =
    require(
      dialect.sqlDialect == actual,
      s"Dialect mismatch: SQL was generated for ${dialect.sqlDialect} but the connection reports $actual"
    )

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
   * Creates the queue table `tableName` with `CREATE TABLE IF NOT EXISTS`, in
   * its own transaction, so a second call does nothing. It installs no trigger;
   * see [[installTriggers]].
   *
   * @throws java.lang.IllegalArgumentException
   *   if `tableName` is not a plain identifier, the key codec has more than one
   *   column, or `dialect` targets another database than the connection
   */
  def create[ID](tableName: String, transactor: Transactor)(using codec: DbCodec[ID], dialect: Dialect): Unit = {
    require(codec.columns.length == 1, "QueueTable only supports single-column ID codecs")
    val validated = SqlId.validate("table", tableName)
    val colName   = SqlId.validate("column", QueueKeyColumn)
    transactor.transact { (tx: DbTx) ?=>
      requireDialectMatch(tx.dialect, dialect)
      Frag.literal(dialect.createQueueTableDDL(validated, colName)).update
    }
  }

  /**
   * Installs triggers on `sourceTable` that queue the key in `sourceIdColumn`
   * of every inserted, updated or deleted row, in the writer's own transaction.
   * An insert or update leaves a key that is already queued alone; a delete
   * sets its `op` to `D`. The application then writes as usual.
   *
   * Calling it again does nothing new: SQLite uses `CREATE TRIGGER IF NOT
   * EXISTS`, and PostgreSQL uses `CREATE OR REPLACE` (PostgreSQL 14 or later).
   * The queue table must exist first, or every write to `sourceTable` fails. Do
   * not install triggers on the table that the migrator writes to: its own
   * writes would queue the converted keys again, without end.
   *
   * @throws java.lang.IllegalArgumentException
   *   if a name is not a plain identifier, the key codec has more than one
   *   column, or `dialect` targets another database than the connection
   */
  def installTriggers[ID](queueTable: String, sourceTable: String, sourceIdColumn: String)(using
    tx: DbTx,
    dialect: Dialect,
    codec: DbCodec[ID]
  ): Unit = {
    require(codec.columns.length == 1, "QueueTable only supports single-column ID codecs")
    requireDialectMatch(tx.dialect, dialect)
    val q      = SqlId.validate("queue table", queueTable)
    val s      = SqlId.validate("source table", sourceTable)
    val srcCol = SqlId.validate("source id column", sourceIdColumn)
    val keyCol = SqlId.validate("column", QueueKeyColumn)
    val ddl    = dialect.createTriggerDDL(q, s, srcCol, keyCol)
    ddl.foreach(stmt => Frag.literal(stmt).update)
  }

  /**
   * Queues `ids` in one `INSERT`, skipping keys that are already queued. Each
   * key is written as `CAST(? AS TEXT)`, since the key column is text. An empty
   * `ids` does nothing and checks nothing, not even `tableName`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `ids` is not empty and `tableName` is not a plain identifier, or the
   *   key codec has more than one column
   */
  def enqueue[ID](tableName: String, ids: Seq[ID])(using con: DbCon, codec: DbCodec[ID]): Unit = {
    if (ids.isEmpty) return
    require(codec.columns.length == 1, "QueueTable only supports single-column ID codecs")
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
   * Claims up to `batchSize` keys, in text order, deletes them from the queue
   * table and returns them, in the caller's transaction: if the transaction
   * rolls back, the keys stay queued. PostgreSQL skips rows that another
   * transaction has locked; SQLite relies on `transact` holding the write lock
   * (`BEGIN IMMEDIATE`), so use one consumer.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `batchSize` is `0` or less, `tableName` is not a plain identifier, the
   *   key codec has more than one column, or `dialect` targets another database
   *   than the connection
   */
  def dequeue[ID](tableName: String, batchSize: Int)(using tx: DbTx, codec: DbCodec[ID], dialect: Dialect): List[ID] = {
    require(batchSize > 0, "batchSize must be positive")
    require(codec.columns.length == 1, "QueueTable only supports single-column ID codecs")
    requireDialectMatch(tx.dialect, dialect)
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
   * Returns the number of keys in the queue table. It throws the driver's
   * exception if the table does not exist.
   */
  def pending(tableName: String)(using con: DbCon): Long = {
    val validated = SqlId.validate("table", tableName)
    val frag      = Frag.literal(s"SELECT COUNT(*) FROM $validated")
    frag.queryOne[Long].getOrElse(0L)
  }

  // ---- Pure preview helpers (no connection) ----

  /**
   * Returns the queue table's `CREATE TABLE IF NOT EXISTS` statement, without
   * running it.
   */
  def queueTableDDL(tableName: String)(using dialect: Dialect): String = {
    val validated = SqlId.validate("table", tableName)
    val colName   = SqlId.validate("column", QueueKeyColumn)
    dialect.createQueueTableDDL(validated, colName)
  }

  /**
   * Returns the statements that [[installTriggers]] runs, without running them:
   * three `CREATE TRIGGER IF NOT EXISTS` on SQLite, a function and a trigger on
   * PostgreSQL.
   */
  def triggerDDLs(queueTable: String, sourceTable: String, sourceIdColumn: String)(using
    dialect: Dialect
  ): List[String] = {
    val q      = SqlId.validate("queue table", queueTable)
    val s      = SqlId.validate("source table", sourceTable)
    val srcCol = SqlId.validate("source id column", sourceIdColumn)
    val keyCol = SqlId.validate("column", QueueKeyColumn)
    dialect.createTriggerDDL(q, s, srcCol, keyCol)
  }

  /**
   * Returns the `SELECT` that [[dequeue]] runs to claim keys, without running
   * it.
   */
  def dequeueSQLTemplate(tableName: String, batchSize: Int)(using dialect: Dialect): String = {
    val validated = SqlId.validate("table", tableName)
    val colName   = SqlId.validate("column", QueueKeyColumn)
    dialect.dequeueSQL(validated, colName, batchSize)
  }

  /** Returns [[queueTableDDL]] as a `Frag`, to run with `.update`. */
  def ddl(tableName: String)(using dialect: Dialect): Frag =
    Frag.literal(queueTableDDL(tableName))
}
