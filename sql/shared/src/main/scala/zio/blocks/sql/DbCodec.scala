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
import zio.blocks.schema.{As, Schema, SchemaError}
import zio.blocks.schema.derive.DerivationBuilder
import zio.blocks.schema.json.JsonCodec

/**
 * Maps a Scala value of type `A` to one or more database columns and back. A
 * `DbCodec` reads a value from a result row ([[DbResultReader]]), binds a value
 * to statement parameters ([[DbParamWriter]]), and turns a value into the
 * [[DbValue]]s that a [[Frag]] carries.
 *
 * `DbCodec.derived` derives a codec from a case class: one column per field,
 * named by [[SqlNameMapper.SnakeCase]] unless the field has `@Modifier.rename`.
 * A field whose type is a case class, a collection, a map or a sealed trait
 * with fields is stored as one JSON column; an enum of case objects is stored
 * as the case name. The built-in codecs for primitives, such as `DbCodec[Int]`,
 * have one column named `value`. `Option` and `Maybe` read SQL `NULL` as `None`
 * and `Maybe.absent`.
 *
 * {{{
 * case class Customer(id: Int, fullName: String, email: Option[String]) derives DbCodec
 *
 * val columns = DbCodec[Customer].columns // Vector(id, full_name, email)
 * val values  = DbCodec[Customer].toDbValues(Customer(1, "Ada Lovelace", None))
 * // Vector(DbInt(1), DbString(Ada Lovelace), DbNull)
 *
 * val xa = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
 * val rows = xa.connect {
 *   sql"SELECT 2 AS id, 'Alan Turing' AS full_name, NULL AS email".query[Customer]
 * }
 * // List(Customer(2,Alan Turing,None))
 * }}}
 *
 * A non-optional column that holds `NULL` reads as whatever the JDBC getter
 * returns, a known bug in 0.0.56: `0` for an `Int`, `false` for a `Boolean`,
 * `null` for a `String`. `BigDecimal` and a derived `Char` throw instead, and
 * so do `Option[BigDecimal]` and `Option[Char]`, another known bug.
 *
 * The positional `readValue` and `writeValue` take a 1-based `startIndex` (the
 * JDBC convention): the column at `startIndex` holds `columns(0)`. `Frag#query`
 * reads by position when the result's columns carry the codec's labels in codec
 * order from column 1, and by label otherwise, so a `SELECT` may list the
 * columns in another order as long as the labels match.
 *
 * @see
 *   [[https://zioblocks.com/reference/sql/db-codec/ DbCodec]]
 */
trait DbCodec[A] {

  /**
   * Returns the column names this codec reads and writes, in order. A record
   * field whose codec has several columns contributes them as
   * `<field>_<column>`.
   */
  def columns: IndexedSeq[String]

  /**
   * Reads a value from the current row, starting at the 1-based column
   * `startIndex`. The default looks up the labels of the next `columnCount`
   * columns and calls the label-based overload; the built-in codecs read by
   * position instead.
   */
  def readValue(reader: DbResultReader, startIndex: Int): A =
    readValue(reader, IndexedSeq.tabulate(columnCount)(offset => reader.columnLabel(startIndex + offset)))

  /**
   * Reads a value from the current row, taking each column by its label:
   * `columnLabels` has one label per entry of `columns`, in the same order.
   */
  def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): A

  /**
   * Binds `value` to the statement parameters starting at the 1-based index
   * `startIndex`, one parameter per column.
   */
  def writeValue(writer: DbParamWriter, startIndex: Int, value: A): Unit

  /** Returns the [[DbValue]]s that bind `value`, one per column. */
  def toDbValues(value: A): IndexedSeq[DbValue]

  /** Returns the number of columns, `columns.size`. */
  def columnCount: Int = columns.size

  /**
   * Returns a `DbCodec[B]` with the same columns, which reads through `read`
   * and writes through `write`. An exception that either function throws
   * reaches the caller unchanged. Use it for a wrapper type that needs no
   * `Schema`.
   *
   * {{{
   * final case class Sku(code: String)
   *
   * val skuCodec: DbCodec[Sku] = DbCodec[String].transform(Sku(_))(_.code)
   * val columns = skuCodec.columns                 // Vector(value)
   * val values  = skuCodec.toDbValues(Sku("MUG-1")) // Vector(DbString(MUG-1))
   * }}}
   */
  final def transform[B](read: A => B)(write: B => A): DbCodec[B] = {
    val self = this
    new DbCodec[B] {
      val columns: IndexedSeq[String]                                            = self.columns
      def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): B =
        read(self.readValue(reader, columnLabels))
      override def readValue(reader: DbResultReader, startIndex: Int): B =
        read(self.readValue(reader, startIndex))
      def writeValue(writer: DbParamWriter, startIndex: Int, value: B): Unit =
        self.writeValue(writer, startIndex, write(value))
      def toDbValues(value: B): IndexedSeq[DbValue] =
        self.toDbValues(write(value))
    }
  }

}

private[sql] trait DbCodecOpaquePriority {

  /**
   * Builds a [[DbCodec]] for a Scala 3 opaque type from the codec of its
   * underlying type. Reading calls the companion's public
   * `apply(underlying): A`, so any check in `apply` runs on every row. Writing
   * uses the value as is when the opaque type is declared as a subtype of its
   * underlying type (`opaque type Sku <: String = String`), and otherwise calls
   * the companion's public `unwrap(a: A): underlying`. A missing companion,
   * `apply` or `unwrap` is a compile error.
   *
   * Summoning `DbCodec[Sku]` without a given in scope resolves to
   * `DbCodec.derived`, not to this method, a known bug in 0.0.56: `derived`
   * reads the underlying value directly and never calls `apply`. Declaring the
   * given yourself fixes `query[Sku]`, but a `Sku` field of a derived row type
   * (`derives DbCodec`, `Table.derived`, `Repo.derived`) still skips `apply`.
   * With the given declared:
   *
   * {{{
   * import scala.util.Try
   *
   * object Ids {
   *   opaque type Sku <: String = String
   *   object Sku {
   *     def apply(value: String): Sku = {
   *       require(value.startsWith("SKU-"), "not a SKU: " + value)
   *       value
   *     }
   *   }
   * }
   * import Ids.Sku
   *
   * given DbCodec[Sku] = DbCodec.derivedOpaque[Sku]
   *
   * val xa = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
   * val ok  = xa.connect(sql"SELECT 'SKU-7' AS value".query[Sku]) // List(SKU-7)
   * val bad = Try(xa.connect(sql"SELECT 'mug' AS value".query[Sku]))
   * // Failure(java.lang.IllegalArgumentException: requirement failed: not a SKU: mug)
   * }}}
   *
   * An opaque type that is not a subtype of its underlying type needs `unwrap`:
   *
   * {{{
   * object Units {
   *   opaque type Quantity = Int
   *   object Quantity {
   *     def apply(value: Int): Quantity    = value
   *     def unwrap(value: Quantity): Int   = value
   *   }
   * }
   * import Units.Quantity
   *
   * given DbCodec[Quantity] = DbCodec.derivedOpaque[Quantity]
   *
   * val values = DbCodec[Quantity].toDbValues(Quantity(3)) // Vector(DbInt(3))
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/sql/db-codec/ DbCodec]]
   */
  inline given derivedOpaque[A]: DbCodec[A] =
    ${ DbCodecOpaqueMacro.derivedOpaqueImpl[A] }
}

/**
 * Derivation, the built-in codecs for primitives, `Option`, `Maybe` and tuples,
 * and constructors for JSON columns.
 */
object DbCodec extends DbCodecOpaquePriority {
  // Matches java.sql.Types.NULL (0); kept local so shared sources stay free of java.sql.
  private val SqlNullType = 0

  private def decodeJsonb[A](input: String)(using jsonCodec: JsonCodec[A]): A =
    decodeJsonbEither[A](input) match {
      case Right(value) => value
      case Left(err)    => throw new RuntimeException(s"JSONB decode error: $err")
    }

  /** Decodes a JSON column value, returning the decode error as a `Left`. */
  private[sql] def decodeJsonbEither[A](input: String)(using jsonCodec: JsonCodec[A]): Either[SchemaError, A] =
    jsonCodec.decode(input)

  /** Converts a decoded `A` with `conv.into`, used by [[dbCodecFromAs]]. */
  private[sql] def decodeViaAs[A, B](conv: As[A, B], decoded: A): Either[SchemaError, B] =
    conv.into(decoded)

  /** Converts a `B` back with `conv.from`, used by [[dbCodecFromAs]]. */
  private[sql] def encodeViaAs[A, B](conv: As[A, B], value: B): Either[SchemaError, A] =
    conv.from(value)

  private def unexpectedNull(typeName: String): Nothing =
    throw new IllegalStateException(
      s"Encountered SQL NULL while decoding non-optional $typeName. Use Option[$typeName] or Maybe[$typeName] for nullable columns."
    )

  private def requireSingleColumnNullable[A](wrapperType: String, inner: DbCodec[A]): Unit =
    if (inner.columnCount != 1)
      throw new UnsupportedOperationException(
        s"DbCodec[$wrapperType] only supports single-column inner codecs (got ${inner.columnCount} columns)."
      )

  /** Returns the `DbCodec[A]` in implicit scope. */
  def apply[A](implicit codec: DbCodec[A]): DbCodec[A] = codec

  /**
   * Returns a `DerivationBuilder` for `A`'s derived schema, so a codec for one
   * field or type can be replaced (`DerivationBuilder#instance`) before
   * `DerivationBuilder#derive` builds the [[DbCodec]].
   *
   * @see
   *   [[https://zioblocks.com/reference/sql/db-codec/ DbCodec]]
   */
  inline def builder[A]: DerivationBuilder[DbCodec, A] =
    Schema.derived[A].deriving(DbCodecDeriver)

  /**
   * Derives a [[DbCodec]] like `derived`, after `configure` has replaced the
   * codecs of some fields or types on the [[builder]].
   *
   * {{{
   * import zio.blocks.typeid.TypeId
   *
   * case class Product(sku: String, tags: List[String])
   *
   * val tagsCodec: DbCodec[List[String]] =
   *   DbCodec[String].transform(_.split(",").toList)(_.mkString(","))
   * val productCodec = DbCodec.derivedWith[Product](
   *   _.instance(TypeId.of[Product], "tags", tagsCodec)
   * )
   * val values = productCodec.toDbValues(Product("MUG-1", List("kitchen", "gift")))
   * // Vector(DbString(MUG-1), DbString(kitchen,gift))
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/sql/db-codec/ DbCodec]]
   */
  inline def derivedWith[A](
    configure: DerivationBuilder[DbCodec, A] => DerivationBuilder[DbCodec, A]
  ): DbCodec[A] =
    configure(builder[A]).derive

  /**
   * Returns a one-column codec that stores `A` as JSON text through
   * `jsonCodec`, bound with `setString`. Reading text that `jsonCodec` can't
   * decode throws `RuntimeException("JSONB decode error: ...")`. On PostgreSQL,
   * the driver sends the text as `varchar`, which a `jsonb` column rejects
   * unless the JDBC URL has `stringtype=unspecified`, a known bug in 0.0.56.
   *
   * {{{
   * import zio.blocks.schema.Schema
   * import zio.blocks.schema.json.JsonCodec
   *
   * case class Address(street: String, city: String)
   * object Address { implicit val schema: Schema[Address] = Schema.derived }
   *
   * given JsonCodec[Address] = Address.schema.jsonCodec
   * val values = DbCodec.jsonb[Address].toDbValues(Address("Main St", "Paris"))
   * // Vector(DbString({"street":"Main St","city":"Paris"}))
   * }}}
   */
  def jsonb[A](using jsonCodec: JsonCodec[A]): DbCodec[A] =
    DbCodec[String].transform[A](decodeJsonb[A])(value => jsonCodec.encodeToString(value))

  /**
   * Returns a one-column codec that stores `A` as the text `encode` returns and
   * reads it back with `decode`.
   */
  def jsonb[A](encode: A => String, decode: String => A): DbCodec[A] =
    DbCodec[String].transform[A](decode)(encode)

  /**
   * Returns the nullable form of `jsonb`: `None` is stored as SQL `NULL`, and
   * `NULL` reads as `None`.
   */
  def jsonbOption[A](using jsonCodec: JsonCodec[A]): DbCodec[Option[A]] =
    DbCodec[Option[String]].transform[Option[A]](
      _.map(str => decodeJsonb[A](str))
    )(
      _.map(value => jsonCodec.encodeToString(value))
    )

  /**
   * Returns the nullable form of `jsonb(encode, decode)`: `None` is stored as
   * SQL `NULL`, and `NULL` reads as `None`.
   */
  def jsonbOption[A](encode: A => String, decode: String => A): DbCodec[Option[A]] =
    DbCodec[Option[String]].transform[Option[A]](
      _.map(str => decode(str))
    )(
      _.map(value => encode(value))
    )

  /**
   * Derives a `DbCodec[A]` with [[DbCodecDeriver]], which names columns with
   * [[SqlNameMapper.SnakeCase]]. It derives `Schema[A]` itself with
   * `Schema.derived`, so `A` needs no `Schema` in scope, and it backs the
   * `derives DbCodec` clause.
   *
   * {{{
   * case class OrderLine(orderId: Int, productSku: String, quantity: Int) derives DbCodec
   *
   * val columns = DbCodec[OrderLine].columns // Vector(order_id, product_sku, quantity)
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/sql/db-codec/ DbCodec]]
   */
  inline given derived[A]: DbCodec[A] =
    Schema.derived[A].deriving(DbCodecDeriver).derive

  /**
   * Combines the codecs of a tuple's elements. Column `i` is named `_i`, or
   * `_i_<column>` for an element with several columns.
   *
   * Decoding a joined row as a tuple of row types goes by column label, so when
   * two of them have a column with the same name, such as `id`, every element
   * reads the first column with that name, a known bug in 0.0.56.
   *
   * {{{
   * val columns = DbCodec[(Int, String)].columns // Vector(_1, _2)
   * }}}
   */
  inline given tupleCodec[T <: Tuple]: DbCodec[T] =
    ${ DbCodecTupleMacro.tupleCodecImpl[T] }

  /**
   * Reads SQL `NULL` as `None` and writes `None` as `NULL`; otherwise defers to
   * `inner`. Throws `UnsupportedOperationException` when `inner` does not have
   * exactly one column.
   *
   * With the JDBC backend, `inner` reads the column before the `NULL` is seen,
   * so an inner codec that rejects `NULL` throws: `Option[BigDecimal]`, and an
   * `Option` of an enum or of a `Schema#transform` newtype, fail on `NULL`, a
   * known bug in 0.0.56.
   */
  given optionCodec[A](using inner: DbCodec[A]): DbCodec[Option[A]] = new DbCodec[Option[A]] {
    requireSingleColumnNullable("Option[A]", inner)

    val columns: IndexedSeq[String] = inner.columns

    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): Option[A] =
      if (reader.isNull(columnLabels.head)) None
      else {
        val value = inner.readValue(reader, columnLabels)
        // Post-read fallback: readers without a null bitmap (default isNull)
        // preserve the historical decode-then-wasNull behavior.
        if (reader.wasNull) None else Some(value)
      }

    override def readValue(reader: DbResultReader, startIndex: Int): Option[A] =
      if (reader.isNull(startIndex)) None
      else {
        val value = inner.readValue(reader, startIndex)
        if (reader.wasNull) None else Some(value)
      }

    def writeValue(writer: DbParamWriter, startIndex: Int, value: Option[A]): Unit =
      value match {
        case Some(v) => inner.writeValue(writer, startIndex, v)
        case None    => writer.setNull(startIndex, SqlNullType)
      }

    def toDbValues(value: Option[A]): IndexedSeq[DbValue] =
      value match {
        case Some(v) => inner.toDbValues(v)
        case None    => IndexedSeq(DbValue.DbNull)
      }
  }

  /**
   * Reads SQL `NULL` as `Maybe.absent` and writes it as `NULL`, like
   * [[optionCodec]], with the same single-column rule and the same known bug.
   */
  given maybeCodec[A](using inner: DbCodec[A]): DbCodec[Maybe[A]] = new DbCodec[Maybe[A]] {
    requireSingleColumnNullable("Maybe[A]", inner)

    val columns: IndexedSeq[String] = inner.columns

    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): Maybe[A] =
      if (reader.isNull(columnLabels.head)) Maybe.absent
      else {
        val value = inner.readValue(reader, columnLabels)
        if (reader.wasNull) Maybe.absent else Maybe.unsafeWrap(value)
      }

    override def readValue(reader: DbResultReader, startIndex: Int): Maybe[A] =
      if (reader.isNull(startIndex)) Maybe.absent
      else {
        val value = inner.readValue(reader, startIndex)
        if (reader.wasNull) Maybe.absent else Maybe.unsafeWrap(value)
      }

    def writeValue(writer: DbParamWriter, startIndex: Int, value: Maybe[A]): Unit = {
      val v = Maybe.unsafeGet(value)
      if (v == null) writer.setNull(startIndex, SqlNullType)
      else inner.writeValue(writer, startIndex, v.asInstanceOf[A])
    }

    def toDbValues(value: Maybe[A]): IndexedSeq[DbValue] = {
      val v = Maybe.unsafeGet(value)
      if (v == null) IndexedSeq(DbValue.DbNull)
      else inner.toDbValues(v.asInstanceOf[A])
    }
  }

  /** The codec for `Int`: one column named `value`. SQL `NULL` reads as `0`. */
  given intCodec: DbCodec[Int] = new DbCodec[Int] {
    val columns: IndexedSeq[String]                                              = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): Int = reader.getInt(columnLabels.head)
    override def readValue(reader: DbResultReader, startIndex: Int): Int         = reader.getInt(startIndex)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Int): Unit     = writer.setInt(startIndex, value)
    def toDbValues(value: Int): IndexedSeq[DbValue]                              = IndexedSeq(DbValue.DbInt(value))
  }

  /**
   * The codec for `Long`: one column named `value`. SQL `NULL` reads as `0`.
   */
  given longCodec: DbCodec[Long] = new DbCodec[Long] {
    val columns: IndexedSeq[String]                                               = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): Long = reader.getLong(columnLabels.head)
    override def readValue(reader: DbResultReader, startIndex: Int): Long         = reader.getLong(startIndex)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Long): Unit     = writer.setLong(startIndex, value)
    def toDbValues(value: Long): IndexedSeq[DbValue]                              = IndexedSeq(DbValue.DbLong(value))
  }

  /**
   * The codec for `String`: one column named `value`. SQL `NULL` reads as
   * `null`.
   */
  given stringCodec: DbCodec[String] = new DbCodec[String] {
    val columns: IndexedSeq[String]                                                 = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): String =
      reader.getString(columnLabels.head)
    override def readValue(reader: DbResultReader, startIndex: Int): String =
      reader.getString(startIndex)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: String): Unit = writer.setString(startIndex, value)
    def toDbValues(value: String): IndexedSeq[DbValue]                          = IndexedSeq(DbValue.DbString(value))
  }

  /**
   * The codec for `Boolean`: one column named `value`. SQL `NULL` reads as
   * `false`.
   */
  given booleanCodec: DbCodec[Boolean] = new DbCodec[Boolean] {
    val columns: IndexedSeq[String]                                                  = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): Boolean =
      reader.getBoolean(columnLabels.head)
    override def readValue(reader: DbResultReader, startIndex: Int): Boolean =
      reader.getBoolean(startIndex)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Boolean): Unit = writer.setBoolean(startIndex, value)
    def toDbValues(value: Boolean): IndexedSeq[DbValue]                          = IndexedSeq(DbValue.DbBoolean(value))
  }

  /**
   * The codec for `Double`: one column named `value`. SQL `NULL` reads as
   * `0.0`.
   */
  given doubleCodec: DbCodec[Double] = new DbCodec[Double] {
    val columns: IndexedSeq[String]                                                 = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): Double =
      reader.getDouble(columnLabels.head)
    override def readValue(reader: DbResultReader, startIndex: Int): Double =
      reader.getDouble(startIndex)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Double): Unit = writer.setDouble(startIndex, value)
    def toDbValues(value: Double): IndexedSeq[DbValue]                          = IndexedSeq(DbValue.DbDouble(value))
  }

  /**
   * The codec for `Float`: one column named `value`. SQL `NULL` reads as `0.0`.
   */
  given floatCodec: DbCodec[Float] = new DbCodec[Float] {
    val columns: IndexedSeq[String]                                                = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): Float = reader.getFloat(columnLabels.head)
    override def readValue(reader: DbResultReader, startIndex: Int): Float         = reader.getFloat(startIndex)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Float): Unit     = writer.setFloat(startIndex, value)
    def toDbValues(value: Float): IndexedSeq[DbValue]                              = IndexedSeq(DbValue.DbFloat(value))
  }

  /**
   * The codec for `Short`: one column named `value`. SQL `NULL` reads as `0`.
   */
  given shortCodec: DbCodec[Short] = new DbCodec[Short] {
    val columns: IndexedSeq[String]                                                = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): Short = reader.getShort(columnLabels.head)
    override def readValue(reader: DbResultReader, startIndex: Int): Short         = reader.getShort(startIndex)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Short): Unit     = writer.setShort(startIndex, value)
    def toDbValues(value: Short): IndexedSeq[DbValue]                              = IndexedSeq(DbValue.DbShort(value))
  }

  /**
   * The codec for `Byte`: one column named `value`. SQL `NULL` reads as `0`.
   */
  given byteCodec: DbCodec[Byte] = new DbCodec[Byte] {
    val columns: IndexedSeq[String]                                               = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): Byte = reader.getByte(columnLabels.head)
    override def readValue(reader: DbResultReader, startIndex: Int): Byte         = reader.getByte(startIndex)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Byte): Unit     = writer.setByte(startIndex, value)
    def toDbValues(value: Byte): IndexedSeq[DbValue]                              = IndexedSeq(DbValue.DbByte(value))
  }

  /**
   * The codec for `BigDecimal`: one column named `value`. Reading SQL `NULL`
   * throws, and so does reading it into `Option[BigDecimal]`, a known bug in
   * 0.0.56.
   */
  given bigDecimalCodec: DbCodec[BigDecimal] = new DbCodec[BigDecimal] {
    val columns: IndexedSeq[String]                                                     = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): BigDecimal = {
      val jbd = reader.getBigDecimal(columnLabels.head)
      if (jbd != null) scala.BigDecimal(jbd) else unexpectedNull("BigDecimal")
    }
    override def readValue(reader: DbResultReader, startIndex: Int): BigDecimal = {
      val jbd = reader.getBigDecimal(startIndex)
      if (jbd != null) scala.BigDecimal(jbd) else unexpectedNull("BigDecimal")
    }
    def writeValue(writer: DbParamWriter, startIndex: Int, value: BigDecimal): Unit =
      writer.setBigDecimal(startIndex, value.bigDecimal)
    def toDbValues(value: BigDecimal): IndexedSeq[DbValue] = IndexedSeq(DbValue.DbBigDecimal(value))
  }

  /**
   * The codec for `java.time.Instant`: one column named `value`. The JDBC
   * backend binds it as a UTC timestamp. On SQLite, a `TEXT` column, which
   * `Table#createTable` declares for an `Instant`, can't be read back, a known
   * bug in 0.0.56.
   */
  given instantCodec: DbCodec[java.time.Instant] = new DbCodec[java.time.Instant] {
    val columns: IndexedSeq[String]                                                            = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): java.time.Instant =
      reader.getInstant(columnLabels.head)
    override def readValue(reader: DbResultReader, startIndex: Int): java.time.Instant =
      reader.getInstant(startIndex)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: java.time.Instant): Unit =
      writer.setInstant(startIndex, value)
    def toDbValues(value: java.time.Instant): IndexedSeq[DbValue] =
      IndexedSeq(DbValue.DbInstant(value))
  }

  /**
   * Builds a `DbCodec[B]` from a `DbCodec[A]` and an `As[A, B]`: reading
   * converts with `As#into`, writing with `As#from`, and a `Left` from either
   * throws `IllegalStateException`.
   *
   * Summoning `DbCodec[B]` resolves to `derived` whenever `derived` can derive
   * `B`, as it can for an opaque type, so this given is skipped and the `As`
   * checks don't run, a known bug in 0.0.56. Declaring the given yourself fixes
   * `query[B]`, but a `B` field of a derived row type still skips the `As`.
   * With the given declared:
   *
   * {{{
   * import scala.util.Try
   * import zio.blocks.schema.{As, SchemaError}
   *
   * object Codes {
   *   opaque type Coupon = String
   *   object Coupon {
   *     given As[String, Coupon] = new As[String, Coupon] {
   *       def into(s: String): Either[SchemaError, Coupon] =
   *         if (s.startsWith("C-")) Right(s) else Left(SchemaError("not a coupon: " + s))
   *       def from(c: Coupon): Either[SchemaError, String] = Right(c)
   *     }
   *   }
   * }
   * import Codes.Coupon
   *
   * given DbCodec[Coupon] = DbCodec.dbCodecFromAs[String, Coupon]
   *
   * val xa  = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
   * val bad = Try(xa.connect(sql"SELECT 'SPRING' AS value".query[Coupon]))
   * // Failure(java.lang.IllegalStateException: DbCodec decode via As failed:
   * //   zio.blocks.schema.SchemaError: not a coupon: SPRING)
   * }}}
   *
   * For a type without an `As`, use [[DbCodec#transform]].
   *
   * @see
   *   [[https://zioblocks.com/reference/sql/db-codec/ DbCodec]]
   */
  given dbCodecFromAs[A, B](using conv: As[A, B], base: DbCodec[A]): DbCodec[B] =
    base.transform(decoded =>
      decodeViaAs(conv, decoded) match {
        case Right(b) => b
        case Left(e)  => throw new IllegalStateException(s"DbCodec decode via As failed: $e")
      }
    )(value =>
      encodeViaAs(conv, value) match {
        case Right(a) => a
        case Left(e)  => throw new IllegalStateException(s"DbCodec encode via As failed: $e")
      }
    )
}

/**
 * Reads column values from the current row of a result set. A [[DbCodec]] reads
 * through it, and the JDBC backend supplies one for each result set.
 *
 * Every `index` is 1-based (the JDBC convention). The label overloads read a
 * column by name, so a codec can decode columns in any order. After a `get*`
 * call, `wasNull` tells whether that value was SQL `NULL`.
 *
 * @see
 *   [[https://zioblocks.com/reference/sql/db-result-reader/ DbResultReader]]
 */
trait DbResultReader {

  /** Reads the 1-based column `index` of the current row as an `Int`. */
  def getInt(index: Int): Int

  /** Reads the column labelled `label` in the current row as an `Int`. */
  def getInt(label: String): Int

  /** Reads the 1-based column `index` of the current row as a `Long`. */
  def getLong(index: Int): Long

  /** Reads the column labelled `label` in the current row as a `Long`. */
  def getLong(label: String): Long

  /** Reads the 1-based column `index` of the current row as a `Double`. */
  def getDouble(index: Int): Double

  /** Reads the column labelled `label` in the current row as a `Double`. */
  def getDouble(label: String): Double

  /** Reads the 1-based column `index` of the current row as a `Float`. */
  def getFloat(index: Int): Float

  /** Reads the column labelled `label` in the current row as a `Float`. */
  def getFloat(label: String): Float

  /** Reads the 1-based column `index` of the current row as a `Boolean`. */
  def getBoolean(index: Int): Boolean

  /** Reads the column labelled `label` in the current row as a `Boolean`. */
  def getBoolean(label: String): Boolean

  /** Reads the 1-based column `index` of the current row as a `String`. */
  def getString(index: Int): String

  /** Reads the column labelled `label` in the current row as a `String`. */
  def getString(label: String): String

  /**
   * Reads the 1-based column `index` of the current row as a
   * `java.math.BigDecimal`.
   */
  def getBigDecimal(index: Int): java.math.BigDecimal

  /**
   * Reads the column labelled `label` in the current row as a
   * `java.math.BigDecimal`.
   */
  def getBigDecimal(label: String): java.math.BigDecimal

  /** Reads the 1-based column `index` of the current row as a byte array. */
  def getBytes(index: Int): Array[Byte]

  /** Reads the column labelled `label` in the current row as a byte array. */
  def getBytes(label: String): Array[Byte]

  /** Reads the 1-based column `index` of the current row as a `Short`. */
  def getShort(index: Int): Short

  /** Reads the column labelled `label` in the current row as a `Short`. */
  def getShort(label: String): Short

  /** Reads the 1-based column `index` of the current row as a `Byte`. */
  def getByte(index: Int): Byte

  /** Reads the column labelled `label` in the current row as a `Byte`. */
  def getByte(label: String): Byte

  /** Reads the 1-based column `index` of the current row as a `LocalDate`. */
  def getLocalDate(index: Int): java.time.LocalDate

  /** Reads the column labelled `label` in the current row as a `LocalDate`. */
  def getLocalDate(label: String): java.time.LocalDate

  /**
   * Reads the 1-based column `index` of the current row as a `LocalDateTime`.
   */
  def getLocalDateTime(index: Int): java.time.LocalDateTime

  /**
   * Reads the column labelled `label` in the current row as a `LocalDateTime`.
   */
  def getLocalDateTime(label: String): java.time.LocalDateTime

  /** Reads the 1-based column `index` of the current row as a `LocalTime`. */
  def getLocalTime(index: Int): java.time.LocalTime

  /** Reads the column labelled `label` in the current row as a `LocalTime`. */
  def getLocalTime(label: String): java.time.LocalTime

  /** Reads the 1-based column `index` of the current row as an `Instant`. */
  def getInstant(index: Int): java.time.Instant

  /** Reads the column labelled `label` in the current row as an `Instant`. */
  def getInstant(label: String): java.time.Instant

  /** Reads the 1-based column `index` of the current row as a `Duration`. */
  def getDuration(index: Int): java.time.Duration

  /** Reads the column labelled `label` in the current row as a `Duration`. */
  def getDuration(label: String): java.time.Duration

  /** Reads the 1-based column `index` of the current row as a `UUID`. */
  def getUUID(index: Int): java.util.UUID

  /** Reads the column labelled `label` in the current row as a `UUID`. */
  def getUUID(label: String): java.util.UUID

  /**
   * Reads the array column at the given 1-based `index` as a flat
   * `Array[String]`.
   *
   * @return
   *   the column's elements; a SQL `NULL` array decodes to `null`
   * @throws UnsupportedOperationException
   *   by default — only backends with native array support override this
   */
  def getArray(index: Int): Array[String] =
    throw new UnsupportedOperationException("DbResultReader does not support getArray(index)")

  /**
   * Reads the array column named `label` as a flat `Array[String]`.
   *
   * @return
   *   the column's elements; a SQL `NULL` array decodes to `null`
   * @throws UnsupportedOperationException
   *   by default — only backends with native array support override this
   */
  def getArray(label: String): Array[String] =
    throw new UnsupportedOperationException("DbResultReader does not support getArray(label)")

  /** Returns the label of the 1-based column `index`. */
  def columnLabel(index: Int): String

  /** Returns `true` if the result has a column labelled `label`. */
  def hasColumn(label: String): Boolean

  /** Returns `true` if the last value read was SQL `NULL`. */
  def wasNull: Boolean

  /**
   * Returns `true` if the column at the given 1-based `index` contains SQL NULL
   * in the current row.
   *
   * Only backends that carry an out-of-band null bitmap can answer reliably
   * before the column is decoded (a wire-protocol backend reads NULL flags from
   * the row header). The JDBC backend records `wasNull` as each getter runs, so
   * its answer is only meaningful after that column has been read; before the
   * first read it reports `false`. The default reports `false`, which preserves
   * the historical behavior: Option/Maybe codecs then fall back to the
   * post-read `wasNull` check and decode the inner value as before.
   *
   * @param index
   *   the 1-based column position
   * @return
   *   `true` if the backend knows the column is NULL; `false` when it is
   *   non-null or not yet known — callers must treat `false` as "unknown",
   *   never as a confirmed non-null
   */
  def isNull(index: Int): Boolean = false

  /**
   * Label-based variant of [[isNull]].
   *
   * @param label
   *   the result-set column label
   * @return
   *   `true` if the backend knows the named column is NULL; `false` when it is
   *   non-null or not yet known (see [[isNull(index:Int)]] for the per-backend
   *   reliability contract)
   */
  def isNull(label: String): Boolean = false
}

/**
 * Binds parameter values to a prepared statement. A [[DbCodec]] writes through
 * it, and the JDBC backend supplies one for each prepared statement.
 *
 * Every `index` is 1-based (the JDBC convention). `setNull` binds SQL `NULL`.
 *
 * @see
 *   [[https://zioblocks.com/reference/sql/db-param-writer/ DbParamWriter]]
 */
trait DbParamWriter {

  /** Binds an `Int` to the 1-based parameter `index`. */
  def setInt(index: Int, value: Int): Unit

  /** Binds a `Long` to the 1-based parameter `index`. */
  def setLong(index: Int, value: Long): Unit

  /** Binds a `Double` to the 1-based parameter `index`. */
  def setDouble(index: Int, value: Double): Unit

  /** Binds a `Float` to the 1-based parameter `index`. */
  def setFloat(index: Int, value: Float): Unit

  /** Binds a `Boolean` to the 1-based parameter `index`. */
  def setBoolean(index: Int, value: Boolean): Unit

  /** Binds a `String` to the 1-based parameter `index`. */
  def setString(index: Int, value: String): Unit

  /** Binds a `java.math.BigDecimal` to the 1-based parameter `index`. */
  def setBigDecimal(index: Int, value: java.math.BigDecimal): Unit

  /** Binds a byte array to the 1-based parameter `index`. */
  def setBytes(index: Int, value: Array[Byte]): Unit

  /** Binds a `Short` to the 1-based parameter `index`. */
  def setShort(index: Int, value: Short): Unit

  /** Binds a `Byte` to the 1-based parameter `index`. */
  def setByte(index: Int, value: Byte): Unit

  /** Binds a `LocalDate` to the 1-based parameter `index`. */
  def setLocalDate(index: Int, value: java.time.LocalDate): Unit

  /** Binds a `LocalDateTime` to the 1-based parameter `index`. */
  def setLocalDateTime(index: Int, value: java.time.LocalDateTime): Unit

  /** Binds a `LocalTime` to the 1-based parameter `index`. */
  def setLocalTime(index: Int, value: java.time.LocalTime): Unit

  /** Binds an `Instant` to the 1-based parameter `index`. */
  def setInstant(index: Int, value: java.time.Instant): Unit

  /** Binds a `Duration` to the 1-based parameter `index`. */
  def setDuration(index: Int, value: java.time.Duration): Unit

  /** Binds a `UUID` to the 1-based parameter `index`. */
  def setUUID(index: Int, value: java.util.UUID): Unit

  /**
   * Binds SQL `NULL` to the 1-based parameter `index`; `sqlType` is a
   * `java.sql.Types` code.
   */
  def setNull(index: Int, sqlType: Int): Unit

  /**
   * Binds `elements` as an SQL array whose elements have the type
   * `elementType`.
   */
  def setArray(index: Int, elementType: String, elements: IndexedSeq[Any]): Unit
}
