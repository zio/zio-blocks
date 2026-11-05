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

package zio.blocks.sql

import java.time.Duration

/**
 * Receives an event for the statements that [[Frag]] and [[Repo]] operations
 * run: `onSuccess` with the row count, or `onError` with the exception. The
 * callbacks run on the thread that ran the statement, after it finished, so
 * keep them fast. Pass one as the third argument of the `JdbcTransactor` or
 * `TransactorZIO` constructor; the default is `SqlLogger.noop`.
 *
 * {{{
 * val seen = scala.collection.mutable.ListBuffer.empty[String]
 * val logger = new SqlLogger {
 *   def onSuccess(event: SqlLogger.SuccessEvent): Unit = seen += "ok " + event.rowCount + ": " + event.sql
 *   def onError(event: SqlLogger.ErrorEvent): Unit     = seen += "error: " + event.sql
 * }
 * val xa = new JdbcTransactor(() => java.sql.DriverManager.getConnection("jdbc:sqlite::memory:"), SqlDialect.SQLite, logger)
 * xa.connect {
 *   sql"CREATE TABLE wish (item TEXT)".update
 *   val item = "lamp"
 *   sql"INSERT INTO wish VALUES (\$item)".update
 *   scala.util.Try(sql"SELECT price FROM wish".query[Double])
 * }
 * // seen: ListBuffer(ok 0: CREATE TABLE wish (item TEXT), ok 1: INSERT INTO wish VALUES (?), error: SELECT price FROM wish)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/sql/sql-logger/ SqlLogger]]
 */
trait SqlLogger {

  /** Called after a statement succeeds. */
  def onSuccess(event: SqlLogger.SuccessEvent): Unit

  /** Called after a statement fails. */
  def onError(event: SqlLogger.ErrorEvent): Unit
}

/** The event types of [[SqlLogger]] and its no-op instance. */
object SqlLogger {

  /**
   * A statement that succeeded: its SQL text with `?` placeholders, the bound
   * values, how long it took, and `rowCount`, which is the number of rows read
   * for a query and the number changed for an update.
   */
  final case class SuccessEvent(
    sql: String,
    params: IndexedSeq[DbValue],
    duration: Duration,
    rowCount: Int
  )

  /**
   * A statement that failed: its SQL text, the bound values, how long it ran
   * before failing, and the exception.
   */
  final case class ErrorEvent(
    sql: String,
    params: IndexedSeq[DbValue],
    duration: Duration,
    error: Throwable
  )

  /** A logger that ignores every event; the default of `JdbcTransactor`. */
  val noop: SqlLogger = new SqlLogger {
    def onSuccess(event: SuccessEvent): Unit = ()
    def onError(event: ErrorEvent): Unit     = ()
  }
}
