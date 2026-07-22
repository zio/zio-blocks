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

import zio.blocks.maybe.Maybe

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
   */
  def sql(dialect: SqlDialect): String = {
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

  def queryParams: IndexedSeq[DbValue] = params

  def isEmpty: Boolean = parts.forall(_.isEmpty) && params.isEmpty
}

object Frag {

  /** An empty fragment that contributes no SQL text and no parameters. */
  val empty: Frag = Frag(IndexedSeq(""), IndexedSeq.empty)

  /** Wraps a parameter-free SQL string as a fragment. */
  def literal(sqlStr: String): Frag = Frag(IndexedSeq(sqlStr), IndexedSeq.empty)

  /**
   * Concatenates multiple fragments with no separator between them. Returns
   * [[Frag.empty]] when called with no arguments.
   */
  def sequence(frags: Frag*): Frag = frags.foldLeft(Frag.empty)(_ ++ _)

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
            val builder = List.newBuilder[A]
            var count   = 0
            while (rs.next()) {
              builder += codec.readValue(reader, selectLabels(reader, codec))
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
            val result =
              if (rs.next()) Maybe(codec.readValue(rs.reader, selectLabels(rs.reader, codec))) else Maybe.absent
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
            val builder = List.newBuilder[A]
            var count   = 0
            while (count < limit && rs.next()) {
              builder += codec.readValue(reader, selectLabels(reader, codec))
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
