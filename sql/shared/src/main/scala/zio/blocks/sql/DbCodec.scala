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
import zio.blocks.schema.{As, Schema}
import zio.blocks.schema.json.{JsonCodec => JsonSchemaCodec}

/**
 * Bidirectional codec between a Scala value `A` and one or more database
 * columns.
 *
 * Column ordering matches the `columns` [[IndexedSeq]]. The `startIndex`
 * parameter in positional `readValue` / `writeValue` is 1-based (JDBC
 * convention): column 1 corresponds to `columns(0)`. Query decoding prefers the
 * label-based overload so result column order can differ from codec order as
 * long as column labels still match.
 */
trait DbCodec[A] {
  def columns: IndexedSeq[String]
  def readValue(reader: DbResultReader, startIndex: Int): A =
    readValue(reader, IndexedSeq.tabulate(columnCount)(offset => reader.columnLabel(startIndex + offset)))
  def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): A
  def writeValue(writer: DbParamWriter, startIndex: Int, value: A): Unit
  def toDbValues(value: A): IndexedSeq[DbValue]
  def columnCount: Int = columns.size

  /**
   * Returns a new `DbCodec[B]` by mapping read values with `read` and write
   * values with `write`. Both functions are total; exceptions propagate as-is.
   * Use this to create codecs for opaque types and value wrappers without
   * defining a full `Schema`.
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
   * Auto-derives a [[DbCodec]][A] for Scala 3 opaque types by reusing the codec
   * of the underlying type. The opaque type companion must expose a public
   * `apply` method from the underlying type to the opaque type, so decode uses
   * the same validation/wrapping path as user code.
   *
   * For encoding, either define the opaque type as a subtype of the underlying
   * type:
   *
   * {{{
   * opaque type ProductId <: String = String
   * object ProductId { def apply(value: String): ProductId = value }
   * }}}
   *
   * or provide a public `unwrap` method:
   *
   * {{{
   * opaque type ProductId = String
   * object ProductId {
   *   def apply(value: String): ProductId = value
   *   def unwrap(value: ProductId): String = value
   * }
   * }}}
   *
   * This given lives in a super-trait so it has lower priority than `derived`
   * (requires `Schema`) and `dbCodecFromAs` (requires `As`), both of which are
   * defined in `object DbCodec` itself.
   */
  inline given derivedOpaque[A]: DbCodec[A] =
    ${ DbCodecOpaqueMacro.derivedOpaqueImpl[A] }
}

object DbCodec extends DbCodecOpaquePriority {
  private val SqlNullType = java.sql.Types.NULL

  private def decodeJsonb[A](input: String)(using jsonCodec: JsonSchemaCodec[A]): A =
    jsonCodec.decode(input) match {
      case Right(value) => value
      case Left(err)    => throw new RuntimeException(s"JSONB decode error: $err")
    }

  private def unexpectedNull(typeName: String): Nothing =
    throw new IllegalStateException(
      s"Encountered SQL NULL while decoding non-optional $typeName. Use Option[$typeName] or Maybe[$typeName] for nullable columns."
    )

  private def requireSingleColumnNullable[A](wrapperType: String, inner: DbCodec[A]): Unit =
    if (inner.columnCount != 1)
      throw new UnsupportedOperationException(
        s"DbCodec[$wrapperType] only supports single-column inner codecs (got ${inner.columnCount} columns)."
      )

  def apply[A](implicit codec: DbCodec[A]): DbCodec[A] = codec

  def jsonb[A](using jsonCodec: JsonSchemaCodec[A]): DbCodec[A] =
    DbCodec[String].transform[A](decodeJsonb[A])(value => jsonCodec.encodeToString(value))

  def jsonb[A](encode: A => String, decode: String => A): DbCodec[A] =
    DbCodec[String].transform[A](decode)(encode)

  def jsonbOption[A](using jsonCodec: JsonSchemaCodec[A]): DbCodec[Option[A]] =
    DbCodec[Option[String]].transform[Option[A]](
      _.map(str => decodeJsonb[A](str))
    )(
      _.map(value => jsonCodec.encodeToString(value))
    )

  def jsonbOption[A](encode: A => String, decode: String => A): DbCodec[Option[A]] =
    DbCodec[Option[String]].transform[Option[A]](
      _.map(str => decode(str))
    )(
      _.map(value => encode(value))
    )

  /**
   * Derives a `DbCodec[A]` using the default column name mapper.
   * Self-contained: derives `Schema[A]` internally, so no `Schema` needs to be
   * in scope. Enables the Scala 3 `derives` clause:
   * `case class User(...) derives DbCodec`
   */
  inline given derived[A]: DbCodec[A] =
    Schema.derived[A].deriving(DbCodecDeriver).derive

  inline given tupleCodec[T <: Tuple]: DbCodec[T] =
    ${ DbCodecTupleMacro.tupleCodecImpl[T] }

  given optionCodec[A](using inner: DbCodec[A]): DbCodec[Option[A]] = new DbCodec[Option[A]] {
    requireSingleColumnNullable("Option[A]", inner)

    val columns: IndexedSeq[String] = inner.columns

    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): Option[A] = {
      val value = inner.readValue(reader, columnLabels)
      if (reader.wasNull) None else Some(value)
    }

    override def readValue(reader: DbResultReader, startIndex: Int): Option[A] = {
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

  given maybeCodec[A](using inner: DbCodec[A]): DbCodec[Maybe[A]] = new DbCodec[Maybe[A]] {
    requireSingleColumnNullable("Maybe[A]", inner)

    val columns: IndexedSeq[String] = inner.columns

    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): Maybe[A] = {
      val value = inner.readValue(reader, columnLabels)
      if (reader.wasNull) Maybe.absent else Maybe.present(value)
    }

    override def readValue(reader: DbResultReader, startIndex: Int): Maybe[A] = {
      val value = inner.readValue(reader, startIndex)
      if (reader.wasNull) Maybe.absent else Maybe.present(value)
    }

    def writeValue(writer: DbParamWriter, startIndex: Int, value: Maybe[A]): Unit =
      if (value.isAbsent) writer.setNull(startIndex, SqlNullType)
      else inner.writeValue(writer, startIndex, value.asInstanceOf[A])

    def toDbValues(value: Maybe[A]): IndexedSeq[DbValue] =
      if (value.isAbsent) IndexedSeq(DbValue.DbNull)
      else inner.toDbValues(value.asInstanceOf[A])
  }

  given intCodec: DbCodec[Int] = new DbCodec[Int] {
    val columns: IndexedSeq[String]                                              = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): Int = reader.getInt(columnLabels.head)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Int): Unit     = writer.setInt(startIndex, value)
    def toDbValues(value: Int): IndexedSeq[DbValue]                              = IndexedSeq(DbValue.DbInt(value))
  }

  given longCodec: DbCodec[Long] = new DbCodec[Long] {
    val columns: IndexedSeq[String]                                               = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): Long = reader.getLong(columnLabels.head)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Long): Unit     = writer.setLong(startIndex, value)
    def toDbValues(value: Long): IndexedSeq[DbValue]                              = IndexedSeq(DbValue.DbLong(value))
  }

  given stringCodec: DbCodec[String] = new DbCodec[String] {
    val columns: IndexedSeq[String]                                                 = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): String =
      reader.getString(columnLabels.head)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: String): Unit = writer.setString(startIndex, value)
    def toDbValues(value: String): IndexedSeq[DbValue]                          = IndexedSeq(DbValue.DbString(value))
  }

  given booleanCodec: DbCodec[Boolean] = new DbCodec[Boolean] {
    val columns: IndexedSeq[String]                                                  = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): Boolean =
      reader.getBoolean(columnLabels.head)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Boolean): Unit = writer.setBoolean(startIndex, value)
    def toDbValues(value: Boolean): IndexedSeq[DbValue]                          = IndexedSeq(DbValue.DbBoolean(value))
  }

  given doubleCodec: DbCodec[Double] = new DbCodec[Double] {
    val columns: IndexedSeq[String]                                                 = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): Double =
      reader.getDouble(columnLabels.head)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Double): Unit = writer.setDouble(startIndex, value)
    def toDbValues(value: Double): IndexedSeq[DbValue]                          = IndexedSeq(DbValue.DbDouble(value))
  }

  given floatCodec: DbCodec[Float] = new DbCodec[Float] {
    val columns: IndexedSeq[String]                                                = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): Float = reader.getFloat(columnLabels.head)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Float): Unit     = writer.setFloat(startIndex, value)
    def toDbValues(value: Float): IndexedSeq[DbValue]                              = IndexedSeq(DbValue.DbFloat(value))
  }

  given shortCodec: DbCodec[Short] = new DbCodec[Short] {
    val columns: IndexedSeq[String]                                                = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): Short = reader.getShort(columnLabels.head)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Short): Unit     = writer.setShort(startIndex, value)
    def toDbValues(value: Short): IndexedSeq[DbValue]                              = IndexedSeq(DbValue.DbShort(value))
  }

  given byteCodec: DbCodec[Byte] = new DbCodec[Byte] {
    val columns: IndexedSeq[String]                                               = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): Byte = reader.getByte(columnLabels.head)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Byte): Unit     = writer.setByte(startIndex, value)
    def toDbValues(value: Byte): IndexedSeq[DbValue]                              = IndexedSeq(DbValue.DbByte(value))
  }

  given bigDecimalCodec: DbCodec[BigDecimal] = new DbCodec[BigDecimal] {
    val columns: IndexedSeq[String]                                                     = IndexedSeq("value")
    def readValue(reader: DbResultReader, columnLabels: IndexedSeq[String]): BigDecimal = {
      val jbd = reader.getBigDecimal(columnLabels.head)
      if (jbd != null) scala.BigDecimal(jbd) else unexpectedNull("BigDecimal")
    }
    def writeValue(writer: DbParamWriter, startIndex: Int, value: BigDecimal): Unit =
      writer.setBigDecimal(startIndex, value.bigDecimal)
    def toDbValues(value: BigDecimal): IndexedSeq[DbValue] = IndexedSeq(DbValue.DbBigDecimal(value))
  }

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
   * Auto-derives a [[DbCodec]][B] from [[DbCodec]][A] and [[As]][A, B].
   *
   * This enables Scala 3 opaque types and ZIO Prelude Newtype/Subtype values to
   * receive a `DbCodec` for free when their underlying type already has one:
   *
   * {{{
   * opaque type ProductId = String
   * // provide As[String, ProductId] in the ProductId companion object
   * // DbCodec[ProductId] is then resolved automatically — no explicit given needed
   * }}}
   *
   * This given is lower priority than any explicit `given DbCodec[B]` or
   * `derived` because it lives in the companion object at the same level as
   * primitive codecs but is polymorphic (two type parameters), making it less
   * specific than any monomorphic given for a concrete type `B`.
   *
   * Use `DbCodec[A].transform` for types that do not have an `As` instance.
   */
  given dbCodecFromAs[A, B](using conv: As[A, B], base: DbCodec[A]): DbCodec[B] =
    base.transform(decoded =>
      conv.into(decoded) match {
        case Right(b) => b
        case Left(e)  => throw new IllegalStateException(s"DbCodec decode via As failed: $e")
      }
    )(value =>
      conv.from(value) match {
        case Right(a) => a
        case Left(e)  => throw new IllegalStateException(s"DbCodec encode via As failed: $e")
      }
    )
}

/**
 * Reads column values from a database result set.
 *
 * All `index` parameters are 1-based (JDBC convention). Label-based access is
 * available for order-independent decoding. After any `get*` call, use
 * `wasNull` to check whether the value was SQL NULL.
 */
trait DbResultReader {
  def getInt(index: Int): Int
  def getInt(label: String): Int
  def getLong(index: Int): Long
  def getLong(label: String): Long
  def getDouble(index: Int): Double
  def getDouble(label: String): Double
  def getFloat(index: Int): Float
  def getFloat(label: String): Float
  def getBoolean(index: Int): Boolean
  def getBoolean(label: String): Boolean
  def getString(index: Int): String
  def getString(label: String): String
  def getBigDecimal(index: Int): java.math.BigDecimal
  def getBigDecimal(label: String): java.math.BigDecimal
  def getBytes(index: Int): Array[Byte]
  def getBytes(label: String): Array[Byte]
  def getShort(index: Int): Short
  def getShort(label: String): Short
  def getByte(index: Int): Byte
  def getByte(label: String): Byte
  def getLocalDate(index: Int): java.time.LocalDate
  def getLocalDate(label: String): java.time.LocalDate
  def getLocalDateTime(index: Int): java.time.LocalDateTime
  def getLocalDateTime(label: String): java.time.LocalDateTime
  def getLocalTime(index: Int): java.time.LocalTime
  def getLocalTime(label: String): java.time.LocalTime
  def getInstant(index: Int): java.time.Instant
  def getInstant(label: String): java.time.Instant
  def getDuration(index: Int): java.time.Duration
  def getDuration(label: String): java.time.Duration
  def getUUID(index: Int): java.util.UUID
  def getUUID(label: String): java.util.UUID
  def getArray(index: Int): java.sql.Array =
    throw new UnsupportedOperationException("DbResultReader does not support getArray(index)")
  def getArray(label: String): java.sql.Array =
    throw new UnsupportedOperationException("DbResultReader does not support getArray(label)")
  def columnLabel(index: Int): String
  def hasColumn(label: String): Boolean
  def wasNull: Boolean
}

/**
 * Writes parameter values to a prepared statement.
 *
 * All `index` parameters are 1-based (JDBC convention). Use `setNull` to write
 * SQL NULL for a given parameter index.
 */
trait DbParamWriter {
  def setInt(index: Int, value: Int): Unit
  def setLong(index: Int, value: Long): Unit
  def setDouble(index: Int, value: Double): Unit
  def setFloat(index: Int, value: Float): Unit
  def setBoolean(index: Int, value: Boolean): Unit
  def setString(index: Int, value: String): Unit
  def setBigDecimal(index: Int, value: java.math.BigDecimal): Unit
  def setBytes(index: Int, value: Array[Byte]): Unit
  def setShort(index: Int, value: Short): Unit
  def setByte(index: Int, value: Byte): Unit
  def setLocalDate(index: Int, value: java.time.LocalDate): Unit
  def setLocalDateTime(index: Int, value: java.time.LocalDateTime): Unit
  def setLocalTime(index: Int, value: java.time.LocalTime): Unit
  def setInstant(index: Int, value: java.time.Instant): Unit
  def setDuration(index: Int, value: java.time.Duration): Unit
  def setUUID(index: Int, value: java.util.UUID): Unit
  def setNull(index: Int, sqlType: Int): Unit
  def setArray(index: Int, elementType: String, elements: IndexedSeq[Any]): Unit
}
