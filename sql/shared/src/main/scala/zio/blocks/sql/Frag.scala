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

import scala.collection.mutable
import zio.blocks.chunk.Chunk
import zio.blocks.maybe.Maybe
import zio.blocks.streams.Stream
import zio.blocks.streams.internal.StreamError
import zio.blocks.streams.io.Reader

/**
 * A SQL fragment composed of literal text interleaved with typed parameter
 * values.
 *
 * `parts(i)` is a literal SQL string and `params(i)` is the value bound at the
 * `?` placeholder that follows it. The invariant is
 * `parts.length == params.length + 1`: the fragment begins and ends with a
 * literal segment (which may be empty).
 *
 * Fragments are assembled via `++` and rendered to a dialect-specific SQL
 * string with `sql`. Use the extension methods on [[Frag]] (e.g. `query`,
 * `queryOne`, `update`) to execute them against a live [[DbCon]].
 *
 * The `sql"..."` string interpolator is the primary way to construct fragments;
 * use [[Frag.literal]] for parameter-free SQL.
 */
final case class Frag(parts: IndexedSeq[String], params: IndexedSeq[DbValue]) {

  /**
   * Concatenates two fragments, merging the adjacent literal boundary so the
   * result remains a valid `Frag` (i.e. `parts.length == params.length + 1`).
   */
  def ++(other: Frag): Frag =
    if (parts.isEmpty) other
    else if (other.parts.isEmpty) this
    else {
      val mergedParts = parts.init ++ IndexedSeq(parts.last + other.parts.head) ++ other.parts.tail
      Frag(mergedParts, params ++ other.params)
    }

  /**
   * Renders the fragment to a SQL string using the given dialect's placeholder
   * syntax.
   *
   * The rendered SQL depends only on the fragment's literal `parts`, the number
   * of params, and the dialect — param *values* are bound separately — so the
   * result is memoized per shape in [[Frag.renderedSql]] and reused across
   * executions. Repeated execution of the same fragment therefore returns the
   * cached `String` instead of rebuilding it with a `StringBuilder` each time.
   */
  def sql(dialect: SqlDialect): String =
    Frag.synchronized {
      val shape = (parts, params.length, dialect)
      Frag.renderedSql.getOrElse(
        shape, {
          if (Frag.renderedSql.size >= Frag.RenderedSqlCacheMaxEntries) Frag.renderedSql.clear()
          val rendered = renderSql(dialect)
          Frag.renderedSql.update(shape, rendered)
          rendered
        }
      )
    }

  private def renderSql(dialect: SqlDialect): String = {
    val sb       = new StringBuilder
    var paramIdx = 1
    var i        = 0
    while (i < parts.length) {
      sb.append(parts(i))
      if (i < params.length) {
        sb.append(dialect.paramPlaceholder(paramIdx))
        paramIdx += 1
      }
      i += 1
    }
    sb.toString()
  }

  /**
   * The bound parameter values for this fragment.
   *
   * @return
   *   the indexed sequence of parameter values bound to `?` placeholders
   */
  def queryParams: IndexedSeq[DbValue] = params

  /**
   * Whether this fragment has no SQL text and no parameters.
   *
   * @return
   *   `true` if all literal parts are empty and there are no parameters
   */
  def isEmpty: Boolean = parts.forall(_.isEmpty) && params.isEmpty
}

object Frag {

  /**
   * Memoized rendered SQL, keyed by the fragment's shape — its literal `parts`,
   * param count, and dialect. The rendered SQL does not depend on param values
   * (they bind separately), so every execution of a fragment with the same
   * shape reuses the same `String` instead of rebuilding it with a
   * `StringBuilder`.
   *
   * A plain `HashMap` guarded by `synchronized` is used instead of a concurrent
   * map because `scala.collection.concurrent.TrieMap` cannot be linked by
   * Scala.js (its internal node classes do not exist on that platform). The
   * critical section is a single map lookup, so contention is negligible.
   *
   * The cache is bounded: workloads that build fragments from runtime strings
   * (per-tenant, per-request SQL) would otherwise retain every distinct shape
   * forever. On overflow the cache is cleared and hot shapes are re-rendered on
   * their next execution.
   */
  private val renderedSql: mutable.HashMap[(IndexedSeq[String], Int, SqlDialect), String] =
    mutable.HashMap.empty

  /** Upper bound on [[renderedSql]] entries before it is cleared. */
  private val RenderedSqlCacheMaxEntries = 1024

  /**
   * Per-acquisition progress shared between a chunked query's row reader and
   * its release finalizer, so exactly one terminal log event is emitted no
   * matter how consumption ends.
   */
  private final class FinishMark(val start: Long) {
    val count: java.util.concurrent.atomic.AtomicInteger  = new java.util.concurrent.atomic.AtomicInteger(0)
    val logged: java.util.concurrent.atomic.AtomicBoolean = new java.util.concurrent.atomic.AtomicBoolean(false)
  }

  /** An empty fragment that contributes no SQL text and no parameters. */
  val empty: Frag = Frag(IndexedSeq(""), IndexedSeq.empty)

  /** Default chunk size for [[queryStream]] batch emission. */
  private val DefaultQueryChunkSize = 64

  /** Wraps a parameter-free SQL string as a fragment. */
  def literal(sqlStr: String): Frag = Frag(IndexedSeq(sqlStr), IndexedSeq.empty)

  /**
   * Concatenates multiple fragments with no separator between them. Returns
   * [[Frag.empty]] when called with no arguments.
   */
  def sequence(frags: Frag*): Frag = frags.foldLeft(Frag.empty)(_ ++ _)

  /**
   * Keyset pagination fragment for a single ordering column.
   *
   * Produces `WHERE <orderCol> > ? ORDER BY <orderCol> ASC LIMIT n` as a `Frag`
   * with one bound parameter (`lastValue`). The ordering column is validated as
   * a SQL identifier; when a `table` is supplied the column must also exist in
   * `table.columns`.
   *
   * Single column only — composite cursors are intentionally unsupported (v2).
   *
   * @param table
   *   the table whose columns are validated against
   * @param orderCol
   *   the column name used for ordering
   * @param lastValue
   *   the last value from the previous page
   * @param limit
   *   maximum number of rows to return
   * @return
   *   a `Frag` containing the keyset pagination clause
   * @throws IllegalArgumentException
   *   if `orderCol` is not a valid identifier, not in `table.columns` (when a
   *   table is supplied), or if `limit <= 0`
   */
  def keysetAfter(table: Table[_], orderCol: String, lastValue: DbValue, limit: Int): Frag = {
    require(limit > 0, s"Frag.keysetAfter: limit must be > 0, got $limit")
    val validated = SqlIdentifier.validate("column", orderCol)
    if (!table.columns.contains(validated))
      throw new IllegalArgumentException(
        s"Column '$validated' not found in table '${table.name}' columns: ${table.columns.mkString(", ")}"
      )
    Frag(
      IndexedSeq(s" WHERE $validated > ", s" ORDER BY $validated ASC LIMIT $limit"),
      IndexedSeq(lastValue)
    )
  }

  /**
   * Keyset pagination fragment without a table binding.
   *
   * Validates `orderCol` as a SQL identifier and produces
   * `WHERE <orderCol> > ? ORDER BY <orderCol> ASC LIMIT n`.
   *
   * @param orderCol
   *   the column name used for ordering
   * @param lastValue
   *   the last value from the previous page
   * @param limit
   *   maximum number of rows to return
   * @return
   *   a `Frag` containing the keyset pagination clause
   * @throws IllegalArgumentException
   *   if `orderCol` is not a valid identifier or `limit <= 0`
   */
  def keysetAfter(orderCol: String, lastValue: DbValue, limit: Int): Frag = {
    require(limit > 0, s"Frag.keysetAfter: limit must be > 0, got $limit")
    val validated = SqlIdentifier.validate("column", orderCol)
    Frag(
      IndexedSeq(s" WHERE $validated > ", s" ORDER BY $validated ASC LIMIT $limit"),
      IndexedSeq(lastValue)
    )
  }

  /**
   * Builds a VALUES clause for a multi-row INSERT. Renders as:
   * `(?, ?), (?, ?), ...` — one tuple per row, columns within each tuple
   * separated by `, `.
   *
   * @throws IllegalArgumentException
   *   if `rows` is empty (empty VALUES is invalid SQL)
   */
  def values[A](rows: Seq[A])(using codec: DbCodec[A]): Frag = {
    require(rows.nonEmpty, "Frag.values: rows must be non-empty")
    val rowFrags = rows.map { row =>
      val params = codec.toDbValues(row)
      val parts  = IndexedSeq("(") ++ IndexedSeq.fill(params.size - 1)(", ") :+ ")"
      Frag(parts, params)
    }
    rowFrags.reduceLeft((a, b) => a ++ Frag.literal(", ") ++ b)
  }

  extension (frag: Frag) {

    private def selectLabels[A](reader: DbResultReader, codec: DbCodec[A]): IndexedSeq[String] =
      codec.columns.zipWithIndex.map { case (expectedLabel, offset) =>
        if (reader.hasColumn(expectedLabel)) expectedLabel
        else reader.columnLabel(offset + 1)
      }

    /**
     * Resolves how each result row is decoded ONCE per statement rather than
     * once per row.
     *
     * When the result set exposes the codec's columns in codec order starting
     * at position 1 (the common case for `SELECT` matching the derived column
     * order), every row is decoded through the cheaper index-based
     * `readValue(reader, 1)` overload, which performs no per-row label lookup
     * and no label slicing. Otherwise the column labels are resolved once via
     * [[selectLabels]] and shared by every row through the label-based
     * overload.
     */
    private def rowDecoder[A](reader: DbResultReader, codec: DbCodec[A]): DbResultReader => A = {
      val columns = codec.columns
      var i       = 0
      while (i < columns.length && reader.hasColumn(columns(i)) && reader.columnLabel(i + 1) == columns(i)) i += 1
      if (i == columns.length) r => codec.readValue(r, 1)
      else {
        val labels = selectLabels(reader, codec)
        r => codec.readValue(r, labels)
      }
    }

    /** Executes this fragment as a SELECT and returns all decoded rows. */
    def query[A](using con: DbCon, codec: DbCodec[A]): List[A] = {
      val sqlStr = frag.sql(con.dialect)
      val start  = System.nanoTime()
      try {
        val ps = con.connection.prepareStatement(sqlStr)
        try {
          writeParams(ps.paramWriter, frag.queryParams)
          val rs = ps.executeQuery()
          try {
            val reader  = rs.reader
            val decode  = rowDecoder(reader, codec)
            val builder = List.newBuilder[A]
            var count   = 0
            while (rs.next()) {
              builder += decode(reader)
              count += 1
            }
            val duration = java.time.Duration.ofNanos(System.nanoTime() - start)
            con.logger.onSuccess(SqlLogger.SuccessEvent(sqlStr, frag.queryParams, duration, count))
            builder.result()
          } finally rs.close()
        } finally ps.close()
      } catch {
        case e: Throwable =>
          val duration = java.time.Duration.ofNanos(System.nanoTime() - start)
          con.logger.onError(SqlLogger.ErrorEvent(sqlStr, frag.queryParams, duration, e))
          throw e
      }
    }

    /** Executes this fragment as a SELECT and returns the first row, if any. */
    def queryOne[A](using con: DbCon, codec: DbCodec[A]): Maybe[A] = {
      val sqlStr = frag.sql(con.dialect)
      val start  = System.nanoTime()
      try {
        val ps = con.connection.prepareStatement(sqlStr)
        try {
          writeParams(ps.paramWriter, frag.queryParams)
          val rs = ps.executeQuery()
          try {
            val reader = rs.reader
            val decode = rowDecoder(reader, codec)
            val result =
              if (rs.next()) Maybe(decode(reader)) else Maybe.absent
            val count    = if (result.isPresent) 1 else 0
            val duration = java.time.Duration.ofNanos(System.nanoTime() - start)
            con.logger.onSuccess(SqlLogger.SuccessEvent(sqlStr, frag.queryParams, duration, count))
            result
          } finally rs.close()
        } finally ps.close()
      } catch {
        case e: Throwable =>
          val duration = java.time.Duration.ofNanos(System.nanoTime() - start)
          con.logger.onError(SqlLogger.ErrorEvent(sqlStr, frag.queryParams, duration, e))
          throw e
      }
    }

    /** Executes this fragment as a SELECT with a row limit. */
    def queryLimit[A](limit: Int)(using con: DbCon, codec: DbCodec[A]): List[A] = {
      val sqlStr = frag.sql(con.dialect)
      val start  = System.nanoTime()
      try {
        val ps = con.connection.prepareStatement(sqlStr)
        try {
          writeParams(ps.paramWriter, frag.queryParams)
          val rs = ps.executeQuery()
          try {
            val reader  = rs.reader
            val decode  = rowDecoder(reader, codec)
            val builder = List.newBuilder[A]
            var count   = 0
            while (count < limit && rs.next()) {
              builder += decode(reader)
              count += 1
            }
            val duration = java.time.Duration.ofNanos(System.nanoTime() - start)
            con.logger.onSuccess(SqlLogger.SuccessEvent(sqlStr, frag.queryParams, duration, count))
            builder.result()
          } finally rs.close()
        } finally ps.close()
      } catch {
        case e: Throwable =>
          val duration = java.time.Duration.ofNanos(System.nanoTime() - start)
          con.logger.onError(SqlLogger.ErrorEvent(sqlStr, frag.queryParams, duration, e))
          throw e
      }
    }

    /**
     * Executes this fragment as a SELECT and returns the decoded rows as a
     * stream of `Chunk`s, each holding up to [[DefaultQueryChunkSize]] rows.
     *
     * Equivalent to `queryChunked[A](DefaultQueryChunkSize)`.
     *
     * @example
     *   {{{
     * val chunks: Stream[Throwable, Chunk[User]] =
     *   sql"SELECT id, name FROM users".queryStream[User]
     * val total = chunks.runFold(0)((n, chunk) => n + chunk.size)
     *   }}}
     *
     * @tparam A
     *   the row type, decoded through `codec`'s column mapping
     * @param con
     *   the connection context providing the connection, dialect, and logger
     * @param codec
     *   decoder for the expected row shape
     * @return
     *   a lazy stream of decoded row chunks; see [[queryChunked]] for the
     *   acquisition lifetime and error behavior
     */
    def queryStream[A](using con: DbCon, codec: DbCodec[A]): Stream[Throwable, Chunk[A]] =
      queryChunked[A](DefaultQueryChunkSize)

    /**
     * Executes this fragment as a SELECT and returns the decoded rows as a
     * stream of `Chunk`s, each holding up to `chunkSize` rows.
     *
     * The statement and result set are acquired lazily on the first pull and
     * released when the stream is closed or fully drained (via
     * `Stream.fromAcquireRelease`). Rows are batched by the streams layer's
     * `Reader.readN` inside `Stream.chunked` rather than a manual
     * `List.newBuilder` loop. SQL failures surface as typed errors (`Left`)
     * from terminal operations such as `runCollect`.
     *
     * Because acquisition is lazy, the stream must be consumed within the scope
     * that provides the `DbCon`: leaving `Transactor.connect`/`transact` closes
     * the captured connection, and pulling afterwards fails.
     *
     * @example
     *   {{{
     * Transactor.connect(transactor) {
     *   sql"SELECT id, name FROM users"
     *     .queryChunked[User](500)
     *     .runFold(0)((acc, chunk) => acc + chunk.size)
     * }
     *   }}}
     *
     * @tparam A
     *   the row type, decoded through `codec`'s column mapping
     * @param chunkSize
     *   maximum number of rows per emitted `Chunk`; must be `>= 1`
     * @param con
     *   the connection context providing the connection, dialect, and logger
     * @param codec
     *   decoder for the expected row shape
     * @return
     *   a lazy stream of decoded row chunks; terminal operations answer
     *   `Left(error)` on SQL failure
     * @throws IllegalArgumentException
     *   if `chunkSize` is less than 1
     */
    def queryChunked[A](chunkSize: Int)(using con: DbCon, codec: DbCodec[A]): Stream[Throwable, Chunk[A]] = {
      require(chunkSize >= 1, s"queryChunked: chunkSize must be >= 1, got $chunkSize")
      val sqlStr = frag.sql(con.dialect)
      Stream.fromAcquireRelease(
        acquire = {
          // Timed from acquisition, not construction: re-running or delaying
          // the stream must not inherit a stale duration.
          val start                   = System.nanoTime()
          var ps: DbPreparedStatement = null
          try {
            ps = con.connection.prepareStatement(sqlStr)
            writeParams(ps.paramWriter, frag.queryParams)
            val rs = ps.executeQuery()
            (ps, rs, FinishMark(start))
          } catch {
            case e: Throwable =>
              // release does not run when acquire fails.
              if (ps ne null)
                try ps.close()
                catch { case _: Throwable => () }
              val duration = java.time.Duration.ofNanos(System.nanoTime() - start)
              con.logger.onError(SqlLogger.ErrorEvent(sqlStr, frag.queryParams, duration, e))
              throw new StreamError(e)
          }
        },
        release = { case (ps, rs, mark) =>
          try rs.close()
          finally ps.close()
          // A consumer that closes early must still produce exactly one
          // terminal log event; natural exhaustion and errors mark the same
          // flag first, so this never double-logs.
          if (mark.logged.compareAndSet(false, true)) {
            val duration = java.time.Duration.ofNanos(System.nanoTime() - mark.start)
            con.logger.onSuccess(SqlLogger.SuccessEvent(sqlStr, frag.queryParams, duration, mark.count.get))
          }
        }
      ) { case (_, rs, mark) =>
        val reader = rs.reader
        val decode = rowDecoder(reader, codec)
        Stream
          .fromReader[Throwable, A](new Reader[A] {
            private var done                    = false
            def isClosed: Boolean               = done
            def read[A1 >: A](sentinel: A1): A1 =
              if (done) sentinel
              else
                try {
                  if (rs.next()) { mark.count.incrementAndGet(); decode(reader).asInstanceOf[A1] }
                  else {
                    done = true
                    if (mark.logged.compareAndSet(false, true)) {
                      val duration = java.time.Duration.ofNanos(System.nanoTime() - mark.start)
                      con.logger.onSuccess(
                        SqlLogger.SuccessEvent(sqlStr, frag.queryParams, duration, mark.count.get)
                      )
                    }
                    sentinel
                  }
                } catch {
                  case e: Throwable =>
                    done = true
                    if (mark.logged.compareAndSet(false, true)) {
                      val duration = java.time.Duration.ofNanos(System.nanoTime() - mark.start)
                      con.logger.onError(SqlLogger.ErrorEvent(sqlStr, frag.queryParams, duration, e))
                    }
                    throw new StreamError(e)
                }
            def close(): Unit = done = true
          })
          .chunked(chunkSize)
      }
    }

    /**
     * Executes this fragment as an INSERT/UPDATE/DELETE and returns the
     * affected row count.
     */
    def update(using con: DbCon): Int = {
      val sqlStr = frag.sql(con.dialect)
      val start  = System.nanoTime()
      try {
        val ps = con.connection.prepareStatement(sqlStr)
        try {
          writeParams(ps.paramWriter, frag.queryParams)
          val count    = ps.executeUpdate()
          val duration = java.time.Duration.ofNanos(System.nanoTime() - start)
          con.logger.onSuccess(SqlLogger.SuccessEvent(sqlStr, frag.queryParams, duration, count))
          count
        } finally ps.close()
      } catch {
        case e: Throwable =>
          val duration = java.time.Duration.ofNanos(System.nanoTime() - start)
          con.logger.onError(SqlLogger.ErrorEvent(sqlStr, frag.queryParams, duration, e))
          throw e
      }
    }

    /** Executes this fragment as an INSERT and returns auto-generated keys. */
    def updateReturningKeys[A](using con: DbCon, codec: DbCodec[A]): List[A] = {
      val sqlStr = frag.sql(con.dialect)
      val start  = System.nanoTime()
      try {
        val ps = con.connection.prepareStatementReturningKeys(sqlStr)
        try {
          writeParams(ps.paramWriter, frag.queryParams)
          val rs = ps.executeUpdateReturningKeys()
          try {
            val reader  = rs.reader
            val builder = List.newBuilder[A]
            var count   = 0
            while (rs.next()) {
              builder += codec.readValue(reader, 1)
              count += 1
            }
            val results  = builder.result()
            val duration = java.time.Duration.ofNanos(System.nanoTime() - start)
            con.logger.onSuccess(SqlLogger.SuccessEvent(sqlStr, frag.queryParams, duration, count))
            results
          } finally rs.close()
        } finally ps.close()
      } catch {
        case e: Throwable =>
          val duration = java.time.Duration.ofNanos(System.nanoTime() - start)
          con.logger.onError(SqlLogger.ErrorEvent(sqlStr, frag.queryParams, duration, e))
          throw e
      }
    }
  }

  private val SqlNullType = 0

  /** Writes parameter values to a prepared statement. */
  private[sql] def writeParams(writer: DbParamWriter, params: IndexedSeq[DbValue]): Unit = {
    var i = 0
    while (i < params.length) {
      val idx = i + 1
      params(i) match {
        case DbValue.DbNull             => writer.setNull(idx, SqlNullType)
        case DbValue.DbInt(v)           => writer.setInt(idx, v)
        case DbValue.DbLong(v)          => writer.setLong(idx, v)
        case DbValue.DbDouble(v)        => writer.setDouble(idx, v)
        case DbValue.DbFloat(v)         => writer.setFloat(idx, v)
        case DbValue.DbBoolean(v)       => writer.setBoolean(idx, v)
        case DbValue.DbString(v)        => writer.setString(idx, v)
        case DbValue.DbBigDecimal(v)    => writer.setBigDecimal(idx, v.bigDecimal)
        case DbValue.DbBytes(v)         => writer.setBytes(idx, v)
        case DbValue.DbShort(v)         => writer.setShort(idx, v)
        case DbValue.DbByte(v)          => writer.setByte(idx, v)
        case DbValue.DbChar(v)          => writer.setString(idx, v.toString)
        case DbValue.DbLocalDate(v)     => writer.setLocalDate(idx, v)
        case DbValue.DbLocalDateTime(v) => writer.setLocalDateTime(idx, v)
        case DbValue.DbLocalTime(v)     => writer.setLocalTime(idx, v)
        case DbValue.DbInstant(v)       => writer.setInstant(idx, v)
        case DbValue.DbDuration(v)      => writer.setDuration(idx, v)
        case DbValue.DbUUID(v)          => writer.setUUID(idx, v)
        case DbValue.DbArray(t, elems)  => writer.setArray(idx, t, elems)
      }
      i += 1
    }
  }
}
