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
import zio.blocks.streams.io.Reader

/**
 * A piece of SQL: literal text with typed parameter values kept apart from it.
 * Build one with the `sql"..."` interpolator, join fragments with `++`, and run
 * the result with the extension methods in the companion (`query`, `queryOne`,
 * `update`, `queryStream`) inside `Transactor#connect` or
 * `Transactor#transact`. Each interpolated value becomes a `?` placeholder and
 * is bound through JDBC when the fragment runs, so it never enters the SQL
 * text. The exceptions are an interpolated [[SqlLiteral]], which is spliced
 * into the text as written, and an interpolated `Frag`, whose literal parts
 * join the text while its parameters stay bound.
 *
 * `parts(i)` is literal SQL and `params(i)` is the value bound at the `?` that
 * follows it, so a well-formed fragment has `parts.length == params.length + 1`
 * (the first and last parts may be empty).
 *
 * {{{
 * val minTotal = 50
 * val frag     = sql"SELECT id, total FROM orders WHERE total >= \$minTotal" ++ Frag.literal(" ORDER BY total DESC")
 *
 * frag.sql(SqlDialect.PostgreSQL) // SELECT id, total FROM orders WHERE total >= ? ORDER BY total DESC
 * frag.params                     // Vector(DbInt(50))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/sql/frag/ Frag]]
 */
final case class Frag(parts: IndexedSeq[String], params: IndexedSeq[DbValue]) {

  /**
   * Appends `other` to this fragment. The last literal part of this fragment
   * and the first of `other` join into one, so the result keeps
   * `parts.length == params.length + 1`. No space is added between them.
   *
   * {{{
   * def search(city: Option[String]): Frag = {
   *   val filter = city match {
   *     case Some(c) => sql" WHERE city = \$c"
   *     case None    => Frag.empty
   *   }
   *   sql"SELECT name FROM customer" ++ filter
   * }
   *
   * search(Some("Oslo")).sql(SqlDialect.SQLite) // SELECT name FROM customer WHERE city = ?
   * search(None).sql(SqlDialect.SQLite)         // SELECT name FROM customer
   * }}}
   */
  def ++(other: Frag): Frag =
    if (parts.isEmpty) other
    else if (other.parts.isEmpty) this
    else {
      val mergedParts = parts.init ++ IndexedSeq(parts.last + other.parts.head) ++ other.parts.tail
      Frag(mergedParts, params ++ other.params)
    }

  /**
   * Returns the SQL text this fragment sends to the database: the literal parts
   * with one `dialect.paramPlaceholder` between each pair. Both built-in
   * dialects, `SqlDialect.SQLite` and `SqlDialect.PostgreSQL`, write `?`.
   *
   * The text depends only on the literal parts, the number of parameters and
   * the dialect, not on the parameter values, so it is cached by those three
   * and reused by later calls; the cache is cleared once it holds 1024 entries.
   *
   * {{{
   * val sku = "KB-01"
   * sql"SELECT price FROM product WHERE sku = \$sku".sql(SqlDialect.PostgreSQL) // SELECT price FROM product WHERE sku = ?
   * }}}
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

  /** Returns the values bound to the `?` placeholders, in order: `params`. */
  def queryParams: IndexedSeq[DbValue] = params

  /**
   * Returns `true` when this fragment has no parameters and every literal part
   * is the empty string, as for [[Frag.empty]].
   */
  def isEmpty: Boolean = parts.forall(_.isEmpty) && params.isEmpty
}

/**
 * Constructors for [[Frag]] (`empty`, `literal`, `sequence`, `values`,
 * `keysetAfter`) and the extension methods that run a fragment against a
 * [[DbCon]].
 *
 * @see
 *   [[https://zioblocks.com/reference/sql/frag/ Frag]]
 */
object Frag {

  /**
   * Rendered SQL keyed by literal parts, parameter count and dialect, guarded
   * by `Frag.synchronized` and cleared when it reaches
   * `RenderedSqlCacheMaxEntries`, so fragments built from runtime strings
   * cannot grow it without bound.
   */
  private val renderedSql: mutable.HashMap[(IndexedSeq[String], Int, SqlDialect), String] =
    mutable.HashMap.empty

  /** Number of `renderedSql` entries at which the cache is cleared. */
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

  /**
   * The fragment with no SQL text and no parameters. Appending it with `++`
   * changes nothing, which makes it the "no clause" case of an optional filter.
   *
   * {{{
   * val onlyInStock = false
   * val frag        = sql"SELECT sku FROM product" ++ (if (onlyInStock) sql" WHERE stock > 0" else Frag.empty)
   *
   * frag.sql(SqlDialect.SQLite) // SELECT sku FROM product
   * }}}
   */
  val empty: Frag = Frag(IndexedSeq(""), IndexedSeq.empty)

  /** Rows per chunk that `queryStream` asks `queryChunked` for. */
  private val DefaultQueryChunkSize = 64

  /**
   * Wraps SQL text that has no parameters as a fragment. The text is used as
   * written, so never pass it a value that comes from a user; interpolate such
   * values with `sql"..."` instead.
   *
   * {{{
   * val frag = sql"SELECT name FROM supplier" ++ Frag.literal(" ORDER BY name")
   *
   * frag.sql(SqlDialect.SQLite) // SELECT name FROM supplier ORDER BY name
   * }}}
   */
  def literal(sqlStr: String): Frag = Frag(IndexedSeq(sqlStr), IndexedSeq.empty)

  /**
   * Joins the fragments with `++`, adding no separator between them. Returns
   * [[Frag.empty]] when called with no arguments.
   *
   * {{{
   * val country = "PE"
   * val frag = Frag.sequence(
   *   sql"SELECT id FROM warehouse",
   *   sql" WHERE country = \$country",
   *   Frag.literal(" ORDER BY id")
   * )
   *
   * frag.sql(SqlDialect.SQLite) // SELECT id FROM warehouse WHERE country = ? ORDER BY id
   * frag.params                 // Vector(DbString(PE))
   * }}}
   */
  def sequence(frags: Frag*): Frag = frags.foldLeft(Frag.empty)(_ ++ _)

  /**
   * Returns the clause that fetches the page after `lastValue` in keyset
   * pagination: ` WHERE <orderCol> > ? ORDER BY <orderCol> ASC LIMIT <limit>`,
   * with `lastValue` bound to the `?` and a leading space, ready to append to a
   * `SELECT`. This overload also checks that `orderCol` is one of
   * `table.columns`.
   *
   * {{{
   * import zio.blocks.schema.Schema
   *
   * case class Product(id: Int, name: String)
   * object Product { implicit val schema: Schema[Product] = Schema.derived }
   *
   * val products = Table.derived[Product]
   * val page     = sql"SELECT id, name FROM product" ++ Frag.keysetAfter(products, "id", DbValue.DbInt(40), 20)
   *
   * page.sql(SqlDialect.SQLite) // SELECT id, name FROM product WHERE id > ? ORDER BY id ASC LIMIT 20
   * }}}
   *
   * The column is written without quotes, so a column whose name is an SQL
   * keyword, such as `order`, passes the checks and then fails when the
   * statement runs: a known bug in 0.0.56. It orders by one column only.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `orderCol` is not made of ASCII letters, digits and underscores
   *   (starting with a letter or underscore), is not one of `table.columns`, or
   *   if `limit` is not positive
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
   * Returns the keyset-pagination clause
   * ` WHERE <orderCol> > ? ORDER BY <orderCol> ASC LIMIT <limit>`, with
   * `lastValue` bound to the `?`. Unlike the overload that takes a [[Table]],
   * it does not check that the column exists.
   *
   * {{{
   * val lastSeen = DbValue.DbInt(40)
   * val page     = sql"SELECT id, name FROM product" ++ Frag.keysetAfter("id", lastSeen, 20)
   *
   * page.sql(SqlDialect.SQLite) // SELECT id, name FROM product WHERE id > ? ORDER BY id ASC LIMIT 20
   * page.params                 // Vector(DbInt(40))
   * }}}
   *
   * The column is written without quotes, so a keyword column such as `order`
   * fails when the statement runs: a known bug in 0.0.56.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `orderCol` is not made of ASCII letters, digits and underscores
   *   (starting with a letter or underscore), or if `limit` is not positive
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
   * Returns the row list of a multi-row `INSERT ... VALUES`: one `(?, ?, ...)`
   * group per row, separated by `, `, with each row's values from
   * `codec.toDbValues` bound in order.
   *
   * {{{
   * import zio.blocks.schema.Schema
   *
   * case class OrderLine(orderId: Int, sku: String, qty: Int)
   * object OrderLine { implicit val schema: Schema[OrderLine] = Schema.derived }
   *
   * val insert = Frag.literal("INSERT INTO order_line (order_id, sku, qty) VALUES ") ++
   *   Frag.values(List(OrderLine(7, "KB-01", 1), OrderLine(7, "MS-02", 2)))
   *
   * insert.sql(SqlDialect.SQLite) // INSERT INTO order_line (order_id, sku, qty) VALUES (?, ?, ?), (?, ?, ?)
   * }}}
   *
   * @throws java.lang.IllegalArgumentException
   *   if `rows` is empty
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

    /**
     * For each codec column, the column's own label when the result set has it,
     * else the label at the codec column's position.
     */
    private def selectLabels[A](reader: DbResultReader, codec: DbCodec[A]): IndexedSeq[String] =
      codec.columns.zipWithIndex.map { case (expectedLabel, offset) =>
        if (reader.hasColumn(expectedLabel)) expectedLabel
        else reader.columnLabel(offset + 1)
      }

    /**
     * Chooses the row decoder once per statement: by position from column 1
     * when the result set's first labels equal the codec's columns in order,
     * otherwise by the labels `selectLabels` resolves.
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

    /**
     * Runs this fragment as a query and returns every row, decoded with
     * `codec`. Each of the codec's columns is read by name when the result set
     * has a column of that name, otherwise from the column at the same
     * position. Reports one event to the connection's [[SqlLogger]] and
     * rethrows any failure.
     *
     * {{{
     * import zio.blocks.schema.Schema
     *
     * case class Customer(id: Int, name: String)
     * object Customer { implicit val schema: Schema[Customer] = Schema.derived }
     *
     * val xa = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
     * val customers = xa.connect {
     *   sql"CREATE TABLE customer (id INTEGER, name TEXT)".update
     *   sql"INSERT INTO customer VALUES (1, 'Ada'), (2, 'Linus')".update
     *   sql"SELECT id, name FROM customer ORDER BY id".query[Customer]
     * }
     * // List(Customer(1,Ada), Customer(2,Linus))
     * }}}
     *
     * @see
     *   [[https://zioblocks.com/reference/sql/frag/ Frag]]
     */
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

    /**
     * Runs this fragment as a query and returns its first row, or
     * `Maybe.absent` when there is none. Rows after the first are not read.
     *
     * {{{
     * val xa = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
     * val (found, missing) = xa.connect {
     *   sql"CREATE TABLE product (sku TEXT, price REAL)".update
     *   sql"INSERT INTO product VALUES ('KB-01', 49.5)".update
     *   val sku   = "KB-01"
     *   val other = "MS-02"
     *   (
     *     sql"SELECT price FROM product WHERE sku = \$sku".queryOne[Double].toOption,
     *     sql"SELECT price FROM product WHERE sku = \$other".queryOne[Double].toOption
     *   )
     * }
     * // (Some(49.5),None)
     * }}}
     */
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

    /**
     * Runs this fragment as a query and returns at most its first `limit` rows.
     * The SQL is sent unchanged, so the database still produces every row; add
     * a `LIMIT` to the SQL to stop it there. A `limit` of 0 or less returns an
     * empty list.
     */
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
     * Runs this fragment as a query and returns its rows as a lazy stream of
     * chunks of up to 64 rows: `queryChunked(64)`. Run the stream before the
     * `Transactor#connect` or `Transactor#transact` block that supplied `con`
     * returns; after that the connection is closed and the stream fails with
     * `Left` of the driver's closed-connection error (`java.sql.SQLException:
     * database connection closed` on SQLite).
     *
     * {{{
     * val xa = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
     * val total = xa.connect {
     *   sql"CREATE TABLE order_line (qty INTEGER)".update
     *   sql"INSERT INTO order_line VALUES (2), (1), (4)".update
     *   sql"SELECT qty FROM order_line".queryStream[Int].runFold(0)((sum, chunk) => sum + chunk.sum)
     * }
     * // Right(7)
     * }}}
     *
     * There is no way to keep the connection open past the block, so the stream
     * can't be handed to a consumer that runs it later: a known bug in 0.0.56.
     * Errors behave as for `queryChunked`.
     *
     * @see
     *   [[https://zioblocks.com/reference/sql/frag/ Frag]]
     */
    def queryStream[A](using con: DbCon, codec: DbCodec[A]): Stream[Throwable, Chunk[A]] =
      queryChunked[A](DefaultQueryChunkSize)

    /**
     * Runs this fragment as a query and returns its rows as a lazy stream of
     * chunks of up to `chunkSize` rows, decoding rows only as the stream is
     * pulled. The library sets no JDBC fetch size, so the driver may still load
     * the whole result set (the PostgreSQL driver does by default). The
     * statement runs when the stream is first pulled, and the statement and
     * result set close when the stream ends or is closed. Run the stream inside
     * the `Transactor#connect` or `Transactor#transact` block that supplied
     * `con`: the connection closes when that block returns.
     *
     * {{{
     * val xa = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
     * val sizes = xa.connect {
     *   sql"CREATE TABLE visit (page TEXT)".update
     *   sql"INSERT INTO visit VALUES ('/'), ('/cart'), ('/'), ('/help'), ('/cart')".update
     *   sql"SELECT page FROM visit".queryChunked[String](2).map(_.size).runCollect
     * }
     * // Right(Chunk(2,2,1))
     * }}}
     *
     * A failing statement or a row that fails to decode ends the stream with
     * `Left(error)` from terminal operations such as `runCollect`, and the
     * stream reports one event to the connection's [[SqlLogger]]. When the
     * result set has fewer columns than the row type needs, the terminal
     * operation throws instead and the logger records a success: a known bug in
     * 0.0.56.
     *
     * @throws java.lang.IllegalArgumentException
     *   when called with a `chunkSize` below 1, before any stream is built
     * @see
     *   [[https://zioblocks.com/reference/sql/frag/ Frag]]
     */
    def queryChunked[A](chunkSize: Int)(using con: DbCon, codec: DbCodec[A]): Stream[Throwable, Chunk[A]] = {
      require(chunkSize >= 1, s"queryChunked: chunkSize must be >= 1, got $chunkSize")
      val sqlStr = frag.sql(con.dialect)
      Stream.attempt {
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
            throw e
        }
      }.flatMap { resource =>
        Stream.fromAcquireRelease(
          acquire = resource,
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
            .fromReader[Throwable, A](new Reader.SyncReader[A] {
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
                      failSource(e)
                  }
              def close(): Unit = done = true
            })
            .chunked(chunkSize)
        }
      }
    }

    /**
     * Runs this fragment as a statement that returns no rows (`INSERT`,
     * `UPDATE`, `DELETE`, or DDL such as `CREATE TABLE`) and returns the number
     * of rows it changed, as JDBC's `executeUpdate` reports it (0 for DDL).
     *
     * {{{
     * val xa = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
     * val shipped = xa.connect {
     *   sql"CREATE TABLE orders (id INTEGER, status TEXT)".update
     *   sql"INSERT INTO orders VALUES (1, 'paid'), (2, 'paid'), (3, 'new')".update
     *   val from = "paid"
     *   val to   = "shipped"
     *   sql"UPDATE orders SET status = \$to WHERE status = \$from".update
     * }
     * // 2
     * }}}
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

    /**
     * Runs this fragment as an `INSERT` and returns the keys the driver reports
     * through JDBC's `getGeneratedKeys`, each row decoded with `codec` from
     * column 1 on. The PostgreSQL driver returns one row per inserted row; the
     * SQLite driver returns only the key of the last inserted row, so a
     * three-row `INSERT` gives one key.
     *
     * {{{
     * val xa = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
     * val keys = xa.connect {
     *   sql"CREATE TABLE coupon (id INTEGER PRIMARY KEY, code TEXT)".update
     *   val code = "WELCOME10"
     *   sql"INSERT INTO coupon (code) VALUES (\$code)".updateReturningKeys[Int]
     * }
     * // List(1)
     * }}}
     */
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

  /** The SQL type passed to `setNull` for `DbNull`: `java.sql.Types.NULL`. */
  private val SqlNullType = 0

  /** Binds `params` to the statement from JDBC position 1 on. */
  private[sql] def writeParams(writer: DbParamWriter, params: IndexedSeq[DbValue]): Unit =
    writeParams(writer, params, 1)

  /**
   * Writes parameter values starting at 1-based JDBC position `startIndex`.
   * Lets batch writers emit two adjacent values ranges with two calls instead
   * of concatenating them into one temporary collection per row.
   */
  private[sql] def writeParams(writer: DbParamWriter, params: IndexedSeq[DbValue], startIndex: Int): Unit = {
    var i = 0
    while (i < params.length) {
      val idx = startIndex + i
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
