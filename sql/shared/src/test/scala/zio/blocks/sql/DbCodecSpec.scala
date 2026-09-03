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

import zio.test._
import zio.blocks.maybe.Maybe
import zio.blocks.schema._
import zio.blocks.schema.json.JsonCodec
import zio.blocks.schema.json.JsonCodecDeriver
import zio.blocks.typeid.TypeId

object DbCodecSpec extends ZIOSpecDefault {

  case class SimpleRecord(name: String, age: Int)
  object SimpleRecord {
    implicit val schema: Schema[SimpleRecord] = Schema.derived
  }

  case class WithOption(id: Int, nickname: Option[String])
  object WithOption {
    implicit val schema: Schema[WithOption] = Schema.derived
  }

  case class WithMaybe(id: Int, nickname: Maybe[String])
  object WithMaybe {
    implicit val schema: Schema[WithMaybe] = Schema.derived
  }

  case class WithTransient(
    name: String,
    @Modifier.transient() hidden: Int = 0
  )
  object WithTransient {
    implicit val schema: Schema[WithTransient] = Schema.derived
  }

  case class WithRename(
    @Modifier.rename("user_name") name: String,
    age: Int
  )
  object WithRename {
    implicit val schema: Schema[WithRename] = Schema.derived
  }

  case class AllPrimitives(
    b: Boolean,
    by: Byte,
    s: Short,
    i: Int,
    l: Long,
    f: Float,
    d: Double,
    str: String
  )
  object AllPrimitives {
    implicit val schema: Schema[AllPrimitives] = Schema.derived
  }

  case class CamelCaseFields(firstName: String, lastName: String)
  object CamelCaseFields {
    implicit val schema: Schema[CamelCaseFields] = Schema.derived
  }

  enum Color {
    case Red, Green, Blue
  }
  object Color {
    implicit val schema: Schema[Color] = Schema.derived
  }

  enum Status {
    case Active, Inactive, Pending
  }
  object Status {
    implicit val schema: Schema[Status] = Schema.derived
  }

  enum BonusType {
    @Modifier.rename("CENT")
    case Cent
    @Modifier.rename("PCT")
    case Percentage
  }
  object BonusType {
    implicit val schema: Schema[BonusType] = Schema.derived
  }

  case class WithEnum(id: Int, color: Color)
  object WithEnum {
    implicit val schema: Schema[WithEnum] = Schema.derived
  }

  case class WithOptionalEnum(id: Int, status: Option[Status])
  object WithOptionalEnum {
    implicit val schema: Schema[WithOptionalEnum] = Schema.derived
  }

  case class JsonPayload(message: String, count: Int)
  object JsonPayload {
    implicit val schema: Schema[JsonPayload]       = Schema.derived
    implicit val jsonCodec: JsonCodec[JsonPayload] =
      schema.deriving(JsonCodecDeriver).derive
  }

  case class WithListField(id: Int, tags: List[String])
  object WithListField {
    implicit val schema: Schema[WithListField] = Schema.derived
  }

  case class WithNestedList(name: String, items: List[JsonPayload])
  object WithNestedList {
    implicit val schema: Schema[WithNestedList] = Schema.derived
  }

  case class WithSimpleRecordList(id: Int, items: List[SimpleRecord])
  object WithSimpleRecordList {
    implicit val schema: Schema[WithSimpleRecordList] = Schema.derived
  }

  case class Tags(values: List[String])
  object Tags {
    implicit val schema: Schema[Tags] = Schema.derived
  }

  case class WithCustomTags(id: Int, tags: Tags)
  object WithCustomTags {
    implicit val schema: Schema[WithCustomTags] = Schema.derived
  }

  case class CartLine(title: String, quantity: Int)
  object CartLine {
    implicit val schema: Schema[CartLine] = Schema.derived
  }

  case class WithCustomLines(id: Int, lines: List[CartLine])
  object WithCustomLines {
    implicit val schema: Schema[WithCustomLines] = Schema.derived
  }

  case class WithChar(c: Char)
  object WithChar {
    implicit val schema: Schema[WithChar] = Schema.derived
  }

  /**
   * Row-varying fake reader: every positional getter answers from the current
   * `row` (1-based), so two simulated rows decode to different values. Label
   * getters throw — any codec routing through labels on the positional path
   * fails loudly — and `columnLabel` counts calls (each one hits result-set
   * metadata on a real JDBC reader).
   */
  private final class RowReader extends DbResultReader {
    var row: Int                                   = 1
    var columnLabelCalls: Int                      = 0
    private def unsupported(name: String): Nothing =
      throw new UnsupportedOperationException(s"positional path must not call $name")
    def getInt(index: Int): Int                                  = row * 100 + index
    def getInt(label: String): Int                               = unsupported("getInt(label)")
    def getLong(index: Int): Long                                = row * 100L + index
    def getLong(label: String): Long                             = unsupported("getLong(label)")
    def getDouble(index: Int): Double                            = row * 100.0 + index
    def getDouble(label: String): Double                         = unsupported("getDouble(label)")
    def getFloat(index: Int): Float                              = (row * 100 + index).toFloat
    def getFloat(label: String): Float                           = unsupported("getFloat(label)")
    def getBoolean(index: Int): Boolean                          = ((row + index) & 1) == 0
    def getBoolean(label: String): Boolean                       = unsupported("getBoolean(label)")
    def getString(index: Int): String                            = s"r${row}c$index"
    def getString(label: String): String                         = unsupported("getString(label)")
    def getBigDecimal(index: Int): java.math.BigDecimal          = java.math.BigDecimal.valueOf(row * 100L + index)
    def getBigDecimal(label: String): java.math.BigDecimal       = unsupported("getBigDecimal(label)")
    def getBytes(index: Int): Array[Byte]                        = unsupported("getBytes(index)")
    def getBytes(label: String): Array[Byte]                     = unsupported("getBytes(label)")
    def getShort(index: Int): Short                              = (row * 100 + index).toShort
    def getShort(label: String): Short                           = unsupported("getShort(label)")
    def getByte(index: Int): Byte                                = (row * 100 + index).toByte
    def getByte(label: String): Byte                             = unsupported("getByte(label)")
    def getLocalDate(index: Int): java.time.LocalDate            = unsupported("getLocalDate(index)")
    def getLocalDate(label: String): java.time.LocalDate         = unsupported("getLocalDate(label)")
    def getLocalDateTime(index: Int): java.time.LocalDateTime    = unsupported("getLocalDateTime(index)")
    def getLocalDateTime(label: String): java.time.LocalDateTime = unsupported("getLocalDateTime(label)")
    def getLocalTime(index: Int): java.time.LocalTime            = unsupported("getLocalTime(index)")
    def getLocalTime(label: String): java.time.LocalTime         = unsupported("getLocalTime(label)")
    def getInstant(index: Int): java.time.Instant                = unsupported("getInstant(index)")
    def getInstant(label: String): java.time.Instant             = unsupported("getInstant(label)")
    def getDuration(index: Int): java.time.Duration              = unsupported("getDuration(index)")
    def getDuration(label: String): java.time.Duration           = unsupported("getDuration(label)")
    def getUUID(index: Int): java.util.UUID                      = unsupported("getUUID(index)")
    def getUUID(label: String): java.util.UUID                   = unsupported("getUUID(label)")
    def columnLabel(index: Int): String                          = {
      columnLabelCalls += 1
      s"c$index"
    }
    def hasColumn(label: String): Boolean = true
    def wasNull: Boolean                  = false
  }

  /**
   * Label-driven fake reader backed by a value map plus the result set's column
   * order. Positional getters resolve through `resultOrder`, label getters
   * through `byLabel` — a codec taking the wrong path still decodes, but
   * misalignment assertions (columnLabel/hasColumn) steer record codecs onto
   * the intended path.
   */
  private final class LabelReader(
    byLabel: Map[String, Any],
    resultOrder: IndexedSeq[String]
  ) extends DbResultReader {
    private def at(index: Int): Any                              = byLabel(resultOrder(index - 1))
    private def atLabel(label: String): Any                      = byLabel(label)
    def getInt(index: Int): Int                                  = at(index).asInstanceOf[Int]
    def getInt(label: String): Int                               = atLabel(label).asInstanceOf[Int]
    def getLong(index: Int): Long                                = at(index).asInstanceOf[Long]
    def getLong(label: String): Long                             = atLabel(label).asInstanceOf[Long]
    def getDouble(index: Int): Double                            = at(index).asInstanceOf[Double]
    def getDouble(label: String): Double                         = atLabel(label).asInstanceOf[Double]
    def getFloat(index: Int): Float                              = at(index).asInstanceOf[Float]
    def getFloat(label: String): Float                           = atLabel(label).asInstanceOf[Float]
    def getBoolean(index: Int): Boolean                          = at(index).asInstanceOf[Boolean]
    def getBoolean(label: String): Boolean                       = atLabel(label).asInstanceOf[Boolean]
    def getString(index: Int): String                            = at(index).asInstanceOf[String]
    def getString(label: String): String                         = atLabel(label).asInstanceOf[String]
    def getBigDecimal(index: Int): java.math.BigDecimal          = at(index).asInstanceOf[java.math.BigDecimal]
    def getBigDecimal(label: String): java.math.BigDecimal       = atLabel(label).asInstanceOf[java.math.BigDecimal]
    def getBytes(index: Int): Array[Byte]                        = at(index).asInstanceOf[Array[Byte]]
    def getBytes(label: String): Array[Byte]                     = atLabel(label).asInstanceOf[Array[Byte]]
    def getShort(index: Int): Short                              = at(index).asInstanceOf[Short]
    def getShort(label: String): Short                           = atLabel(label).asInstanceOf[Short]
    def getByte(index: Int): Byte                                = at(index).asInstanceOf[Byte]
    def getByte(label: String): Byte                             = atLabel(label).asInstanceOf[Byte]
    def getLocalDate(index: Int): java.time.LocalDate            = at(index).asInstanceOf[java.time.LocalDate]
    def getLocalDate(label: String): java.time.LocalDate         = atLabel(label).asInstanceOf[java.time.LocalDate]
    def getLocalDateTime(index: Int): java.time.LocalDateTime    = at(index).asInstanceOf[java.time.LocalDateTime]
    def getLocalDateTime(label: String): java.time.LocalDateTime =
      atLabel(label).asInstanceOf[java.time.LocalDateTime]
    def getLocalTime(index: Int): java.time.LocalTime    = at(index).asInstanceOf[java.time.LocalTime]
    def getLocalTime(label: String): java.time.LocalTime = atLabel(label).asInstanceOf[java.time.LocalTime]
    def getInstant(index: Int): java.time.Instant        = at(index).asInstanceOf[java.time.Instant]
    def getInstant(label: String): java.time.Instant     = atLabel(label).asInstanceOf[java.time.Instant]
    def getDuration(index: Int): java.time.Duration      = at(index).asInstanceOf[java.time.Duration]
    def getDuration(label: String): java.time.Duration   = atLabel(label).asInstanceOf[java.time.Duration]
    def getUUID(index: Int): java.util.UUID              = at(index).asInstanceOf[java.util.UUID]
    def getUUID(label: String): java.util.UUID           = atLabel(label).asInstanceOf[java.util.UUID]
    def columnLabel(index: Int): String                  = resultOrder(index - 1)
    def hasColumn(label: String): Boolean                = byLabel.contains(label)
    def wasNull: Boolean                                 = false
  }

  /** Fixed-string reader for char edge cases (null/empty/single-char). */
  private final class ConstStringReader(value: String) extends DbResultReader {
    private def unsupported(name: String): Nothing =
      throw new UnsupportedOperationException(s"char path must not call $name")
    def getInt(index: Int): Int                                  = unsupported("getInt(index)")
    def getInt(label: String): Int                               = unsupported("getInt(label)")
    def getLong(index: Int): Long                                = unsupported("getLong(index)")
    def getLong(label: String): Long                             = unsupported("getLong(label)")
    def getDouble(index: Int): Double                            = unsupported("getDouble(index)")
    def getDouble(label: String): Double                         = unsupported("getDouble(label)")
    def getFloat(index: Int): Float                              = unsupported("getFloat(index)")
    def getFloat(label: String): Float                           = unsupported("getFloat(label)")
    def getBoolean(index: Int): Boolean                          = unsupported("getBoolean(index)")
    def getBoolean(label: String): Boolean                       = unsupported("getBoolean(label)")
    def getString(index: Int): String                            = value
    def getString(label: String): String                         = value
    def getBigDecimal(index: Int): java.math.BigDecimal          = unsupported("getBigDecimal(index)")
    def getBigDecimal(label: String): java.math.BigDecimal       = unsupported("getBigDecimal(label)")
    def getBytes(index: Int): Array[Byte]                        = unsupported("getBytes(index)")
    def getBytes(label: String): Array[Byte]                     = unsupported("getBytes(label)")
    def getShort(index: Int): Short                              = unsupported("getShort(index)")
    def getShort(label: String): Short                           = unsupported("getShort(label)")
    def getByte(index: Int): Byte                                = unsupported("getByte(index)")
    def getByte(label: String): Byte                             = unsupported("getByte(label)")
    def getLocalDate(index: Int): java.time.LocalDate            = unsupported("getLocalDate(index)")
    def getLocalDate(label: String): java.time.LocalDate         = unsupported("getLocalDate(label)")
    def getLocalDateTime(index: Int): java.time.LocalDateTime    = unsupported("getLocalDateTime(index)")
    def getLocalDateTime(label: String): java.time.LocalDateTime = unsupported("getLocalDateTime(label)")
    def getLocalTime(index: Int): java.time.LocalTime            = unsupported("getLocalTime(index)")
    def getLocalTime(label: String): java.time.LocalTime         = unsupported("getLocalTime(label)")
    def getInstant(index: Int): java.time.Instant                = unsupported("getInstant(index)")
    def getInstant(label: String): java.time.Instant             = unsupported("getInstant(label)")
    def getDuration(index: Int): java.time.Duration              = unsupported("getDuration(index)")
    def getDuration(label: String): java.time.Duration           = unsupported("getDuration(label)")
    def getUUID(index: Int): java.util.UUID                      = unsupported("getUUID(index)")
    def getUUID(label: String): java.util.UUID                   = unsupported("getUUID(label)")
    def columnLabel(index: Int): String                          = "c"
    def hasColumn(label: String): Boolean                        = label == "c"
    def wasNull: Boolean                                         = false
  }

  /** Labels wrapper counting `slice` calls to lock the F2b hoist. */
  private final class CountingLabels(underlying: IndexedSeq[String]) extends IndexedSeq[String] {
    var sliceCalls: Int                                           = 0
    def apply(i: Int): String                                     = underlying(i)
    def length: Int                                               = underlying.length
    override def iterator: Iterator[String]                       = underlying.iterator
    override def slice(from: Int, until: Int): IndexedSeq[String] = {
      sliceCalls += 1
      underlying.slice(from, until)
    }
  }

  sealed trait Shape
  object Shape {
    case class Circle(radius: Double)     extends Shape
    case class Rect(w: Double, h: Double) extends Shape

    implicit val schema: Schema[Shape] = Schema.derived
  }

  case class InlineAddress(street: String, city: String)
  object InlineAddress {
    implicit val schema: Schema[InlineAddress] = Schema.derived
  }

  case class PersonWithInline(
    @Modifier.config("sql.inline", "true") address: InlineAddress,
    name: String
  )
  object PersonWithInline {
    implicit val schema: Schema[PersonWithInline] = Schema.derived
  }

  case class PersonWithoutInline(
    address: InlineAddress,
    name: String
  )
  object PersonWithoutInline {
    implicit val schema: Schema[PersonWithoutInline] = Schema.derived
  }

  case class MixedInline(
    @Modifier.config("sql.inline", "true") address: InlineAddress,
    tags: List[String],
    name: String
  )
  object MixedInline {
    implicit val schema: Schema[MixedInline] = Schema.derived
  }

  case class NestedInner(x: Int, y: Int)
  object NestedInner {
    implicit val schema: Schema[NestedInner] = Schema.derived
  }

  case class NestedOuter(
    @Modifier.config("sql.inline", "true") position: NestedInner,
    @Modifier.config("sql.inline", "true") size: NestedInner,
    label: String
  )
  object NestedOuter {
    implicit val schema: Schema[NestedOuter] = Schema.derived
  }

  @Modifier.config("sql.inline_fields", "true")
  case class AutoInlineOrder(id: Long, shipping: InlineAddress, billing: InlineAddress)
  object AutoInlineOrder {
    implicit val schema: Schema[AutoInlineOrder] = Schema.derived
  }

  private final class SingleStringColumnReader(label: String, value: String) extends DbResultReader {
    private var lastWasNull = false

    private def stringValue: String = {
      lastWasNull = value == null
      value
    }

    private def unsupported[A](method: String): A =
      throw new UnsupportedOperationException(s"SingleStringColumnReader does not support $method")

    def getInt(index: Int): Int                = unsupported("getInt(index)")
    def getInt(label: String): Int             = unsupported("getInt(label)")
    def getLong(index: Int): Long              = unsupported("getLong(index)")
    def getLong(label: String): Long           = unsupported("getLong(label)")
    def getDouble(index: Int): Double          = unsupported("getDouble(index)")
    def getDouble(label: String): Double       = unsupported("getDouble(label)")
    def getFloat(index: Int): Float            = unsupported("getFloat(index)")
    def getFloat(label: String): Float         = unsupported("getFloat(label)")
    def getBoolean(index: Int): Boolean        = unsupported("getBoolean(index)")
    def getBoolean(label: String): Boolean     = unsupported("getBoolean(label)")
    def getString(index: Int): String          = if (index == 1) stringValue else unsupported("getString(index)")
    def getString(columnLabel: String): String =
      if (columnLabel == label) stringValue else unsupported("getString(label)")
    def getBigDecimal(index: Int): java.math.BigDecimal          = unsupported("getBigDecimal(index)")
    def getBigDecimal(label: String): java.math.BigDecimal       = unsupported("getBigDecimal(label)")
    def getBytes(index: Int): Array[Byte]                        = unsupported("getBytes(index)")
    def getBytes(label: String): Array[Byte]                     = unsupported("getBytes(label)")
    def getShort(index: Int): Short                              = unsupported("getShort(index)")
    def getShort(label: String): Short                           = unsupported("getShort(label)")
    def getByte(index: Int): Byte                                = unsupported("getByte(index)")
    def getByte(label: String): Byte                             = unsupported("getByte(label)")
    def getLocalDate(index: Int): java.time.LocalDate            = unsupported("getLocalDate(index)")
    def getLocalDate(label: String): java.time.LocalDate         = unsupported("getLocalDate(label)")
    def getLocalDateTime(index: Int): java.time.LocalDateTime    = unsupported("getLocalDateTime(index)")
    def getLocalDateTime(label: String): java.time.LocalDateTime = unsupported("getLocalDateTime(label)")
    def getLocalTime(index: Int): java.time.LocalTime            = unsupported("getLocalTime(index)")
    def getLocalTime(label: String): java.time.LocalTime         = unsupported("getLocalTime(label)")
    def getInstant(index: Int): java.time.Instant                = unsupported("getInstant(index)")
    def getInstant(label: String): java.time.Instant             = unsupported("getInstant(label)")
    def getDuration(index: Int): java.time.Duration              = unsupported("getDuration(index)")
    def getDuration(label: String): java.time.Duration           = unsupported("getDuration(label)")
    def getUUID(index: Int): java.util.UUID                      = unsupported("getUUID(index)")
    def getUUID(label: String): java.util.UUID                   = unsupported("getUUID(label)")
    def columnLabel(index: Int): String                          = if (index == 1) label else unsupported("columnLabel")
    def hasColumn(columnLabel: String): Boolean                  = columnLabel == label
    def wasNull: Boolean                                         = lastWasNull
  }

  private final class RecordReader(valuesByLabel: Map[String, Any]) extends DbResultReader {
    private var lastWasNull = false

    private def valueFor(label: String): Any = {
      val value = valuesByLabel.getOrElse(
        label,
        throw new UnsupportedOperationException(s"RecordReader has no value for $label")
      )
      lastWasNull = value == null
      value
    }

    private def valueFor(index: Int): Any = valueFor(columnLabel(index))

    private def unsupported[A](method: String): A =
      throw new UnsupportedOperationException(s"RecordReader does not support $method")

    def getInt(index: Int): Int = valueFor(index).asInstanceOf[Int]

    def getInt(label: String): Int = valueFor(label).asInstanceOf[Int]

    def getLong(index: Int): Long = unsupported("getLong(index)")

    def getLong(label: String): Long = unsupported("getLong(label)")

    def getDouble(index: Int): Double = unsupported("getDouble(index)")

    def getDouble(label: String): Double = unsupported("getDouble(label)")

    def getFloat(index: Int): Float = unsupported("getFloat(index)")

    def getFloat(label: String): Float = unsupported("getFloat(label)")

    def getBoolean(index: Int): Boolean = unsupported("getBoolean(index)")

    def getBoolean(label: String): Boolean = unsupported("getBoolean(label)")

    def getString(index: Int): String = valueFor(index).asInstanceOf[String]

    def getString(columnLabel: String): String = valueFor(columnLabel).asInstanceOf[String]

    def getBigDecimal(index: Int): java.math.BigDecimal = unsupported("getBigDecimal(index)")

    def getBigDecimal(label: String): java.math.BigDecimal = unsupported("getBigDecimal(label)")

    def getBytes(index: Int): Array[Byte] = unsupported("getBytes(index)")

    def getBytes(label: String): Array[Byte] = unsupported("getBytes(label)")

    def getShort(index: Int): Short = unsupported("getShort(index)")

    def getShort(label: String): Short = unsupported("getShort(label)")

    def getByte(index: Int): Byte = unsupported("getByte(index)")

    def getByte(label: String): Byte = unsupported("getByte(label)")

    def getLocalDate(index: Int): java.time.LocalDate = unsupported("getLocalDate(index)")

    def getLocalDate(label: String): java.time.LocalDate = unsupported("getLocalDate(label)")

    def getLocalDateTime(index: Int): java.time.LocalDateTime = unsupported("getLocalDateTime(index)")

    def getLocalDateTime(label: String): java.time.LocalDateTime = unsupported("getLocalDateTime(label)")

    def getLocalTime(index: Int): java.time.LocalTime = unsupported("getLocalTime(index)")

    def getLocalTime(label: String): java.time.LocalTime = unsupported("getLocalTime(label)")

    def getInstant(index: Int): java.time.Instant = unsupported("getInstant(index)")

    def getInstant(label: String): java.time.Instant = unsupported("getInstant(label)")

    def getDuration(index: Int): java.time.Duration = unsupported("getDuration(index)")

    def getDuration(label: String): java.time.Duration = unsupported("getDuration(label)")

    def getUUID(index: Int): java.util.UUID = unsupported("getUUID(index)")

    def getUUID(label: String): java.util.UUID = unsupported("getUUID(label)")

    def columnLabel(index: Int): String =
      valuesByLabel.keys.toIndexedSeq.lift(index - 1).getOrElse(unsupported("columnLabel"))

    def hasColumn(columnLabel: String): Boolean = valuesByLabel.contains(columnLabel)

    def wasNull: Boolean = lastWasNull
  }

  private def deriveCodec[A](implicit s: Schema[A]): DbCodec[A] =
    s.deriving(DbCodecDeriver).derive

  private def deriveCodecWithMapper[A](
    mapper: SqlNameMapper
  )(implicit s: Schema[A]): DbCodec[A] =
    s.deriving(DbCodecDeriver.withColumnNameMapper(mapper)).derive

  private val tagsJsonCodec = Tags.schema.deriving(JsonCodecDeriver).derive

  private val tagsStringCodec: DbCodec[Tags] =
    DbCodec[String].transform(json =>
      tagsJsonCodec.decode(json) match {
        case Right(value) => value
        case Left(err)    => throw new IllegalArgumentException(err.toString)
      }
    )(value => tagsJsonCodec.encodeToString(value))

  private val cartLinesJsonCodec = Schema[List[CartLine]].deriving(JsonCodecDeriver).derive

  private val linesStringCodec: DbCodec[List[CartLine]] =
    DbCodec[String].transform(json =>
      cartLinesJsonCodec.decode(json) match {
        case Right(value) => value
        case Left(err)    => throw new IllegalArgumentException(err.toString)
      }
    )(value => cartLinesJsonCodec.encodeToString(value))

  def spec: Spec[TestEnvironment, Any] = suite("DbCodecSpec")(
    suite("primitive derivation")(
      test("Int codec has single column") {
        val codec = deriveCodec[Int]
        assertTrue(
          codec.columns.size == 1,
          codec.columnCount == 1
        )
      },
      test("String codec has single column") {
        val codec = deriveCodec[String]
        assertTrue(codec.columns.size == 1)
      },
      test("Boolean codec has single column") {
        val codec = deriveCodec[Boolean]
        assertTrue(codec.columns.size == 1)
      },
      test("Long codec has single column") {
        val codec = deriveCodec[Long]
        assertTrue(codec.columns.size == 1)
      },
      test("Int toDbValues produces DbInt") {
        val codec  = deriveCodec[Int]
        val values = codec.toDbValues(42)
        assertTrue(values == IndexedSeq(DbValue.DbInt(42)))
      },
      test("String toDbValues produces DbString") {
        val codec  = deriveCodec[String]
        val values = codec.toDbValues("hello")
        assertTrue(values == IndexedSeq(DbValue.DbString("hello")))
      },
      test("Boolean toDbValues produces DbBoolean") {
        val codec  = deriveCodec[Boolean]
        val values = codec.toDbValues(true)
        assertTrue(values == IndexedSeq(DbValue.DbBoolean(true)))
      },
      test("Unit codec has zero columns") {
        val codec = deriveCodec[Unit]
        assertTrue(
          codec.columns.isEmpty,
          codec.columnCount == 0,
          codec.toDbValues(()) == IndexedSeq.empty
        )
      },
      test("BigDecimal codec fails loudly on SQL NULL") {
        val codec  = deriveCodec[BigDecimal]
        val reader = new DbResultReader {
          def getInt(index: Int): Int                                  = 0
          def getInt(label: String): Int                               = 0
          def getLong(index: Int): Long                                = 0L
          def getLong(label: String): Long                             = 0L
          def getDouble(index: Int): Double                            = 0d
          def getDouble(label: String): Double                         = 0d
          def getFloat(index: Int): Float                              = 0f
          def getFloat(label: String): Float                           = 0f
          def getBoolean(index: Int): Boolean                          = false
          def getBoolean(label: String): Boolean                       = false
          def getString(index: Int): String                            = null
          def getString(label: String): String                         = null
          def getBigDecimal(index: Int): java.math.BigDecimal          = null
          def getBigDecimal(label: String): java.math.BigDecimal       = null
          def getBytes(index: Int): Array[Byte]                        = null
          def getBytes(label: String): Array[Byte]                     = null
          def getShort(index: Int): Short                              = 0
          def getShort(label: String): Short                           = 0
          def getByte(index: Int): Byte                                = 0
          def getByte(label: String): Byte                             = 0
          def getLocalDate(index: Int): java.time.LocalDate            = null
          def getLocalDate(label: String): java.time.LocalDate         = null
          def getLocalDateTime(index: Int): java.time.LocalDateTime    = null
          def getLocalDateTime(label: String): java.time.LocalDateTime = null
          def getLocalTime(index: Int): java.time.LocalTime            = null
          def getLocalTime(label: String): java.time.LocalTime         = null
          def getInstant(index: Int): java.time.Instant                = null
          def getInstant(label: String): java.time.Instant             = null
          def getDuration(index: Int): java.time.Duration              = null
          def getDuration(label: String): java.time.Duration           = null
          def getUUID(index: Int): java.util.UUID                      = null
          def getUUID(label: String): java.util.UUID                   = null
          def columnLabel(index: Int): String                          = "value"
          def hasColumn(label: String): Boolean                        = label == "value"
          def wasNull: Boolean                                         = true
        }
        val error = try {
          codec.readValue(reader, IndexedSeq("value"))
          throw new AssertionError("Expected IllegalStateException")
        } catch {
          case e: IllegalStateException => e
        }
        assertTrue(error.getMessage.contains("Encountered SQL NULL while decoding non-optional BigDecimal"))
      }
    ),
    suite("record derivation")(
      test("simple case class columns match field names") {
        val codec = deriveCodec[SimpleRecord]
        assertTrue(
          codec.columns == IndexedSeq("name", "age"),
          codec.columnCount == 2
        )
      },
      test("simple case class toDbValues") {
        val codec  = deriveCodec[SimpleRecord]
        val values = codec.toDbValues(SimpleRecord("Alice", 30))
        assertTrue(
          values == IndexedSeq(DbValue.DbString("Alice"), DbValue.DbInt(30))
        )
      },
      test("all primitives record has correct column count") {
        val codec = deriveCodec[AllPrimitives]
        assertTrue(codec.columnCount == 8)
      },
      test("all primitives record toDbValues") {
        val codec = deriveCodec[AllPrimitives]
        val value = AllPrimitives(
          b = true,
          by = 1,
          s = 2,
          i = 3,
          l = 4L,
          f = 5.0f,
          d = 6.0,
          str = "hello"
        )
        val dbValues = codec.toDbValues(value)
        assertTrue(
          dbValues == IndexedSeq(
            DbValue.DbBoolean(true),
            DbValue.DbByte(1),
            DbValue.DbShort(2),
            DbValue.DbInt(3),
            DbValue.DbLong(4L),
            DbValue.DbFloat(5.0f),
            DbValue.DbDouble(6.0),
            DbValue.DbString("hello")
          )
        )
      },
      test("record with List field uses JSONB for the list") {
        val codec = deriveCodec[WithListField]
        assertTrue(
          codec.columns == IndexedSeq("id", "tags"),
          codec.columnCount == 2
        )
      },
      test("record with List field round-trips JSON array through JSONB fallback") {
        val codec = deriveCodec[List[SimpleRecord]]
        val value = codec.readValue(
          new SingleStringColumnReader(
            "value",
            """[{"name":"apple","age":1},{"name":"pear","age":2}]"""
          ),
          IndexedSeq("value")
        )
        assertTrue(
          value == List(SimpleRecord("apple", 1), SimpleRecord("pear", 2))
        )
      },
      test("derivedWith honors field-level DbCodec override for record-backed JSON column") {
        val codec = DbCodec.derivedWith[WithCustomTags](_.instance(TypeId.of[WithCustomTags], "tags", tagsStringCodec))
        val value = codec.readValue(
          new RecordReader(
            Map(
              "id"   -> 1,
              "tags" -> "{\"values\":[\"Organic\",\"Vegan\"]}"
            )
          ),
          IndexedSeq("id", "tags")
        )
        assertTrue(
          value == WithCustomTags(1, Tags(List("Organic", "Vegan"))),
          codec.toDbValues(WithCustomTags(2, Tags(List("Regional")))) ==
            IndexedSeq(DbValue.DbInt(2), DbValue.DbString("{\"values\":[\"Regional\"]}"))
        )
      },
      test("derivedWith honors field-level DbCodec override for sequence-backed JSON column") {
        val codec =
          DbCodec.derivedWith[WithCustomLines](_.instance(TypeId.of[WithCustomLines], "lines", linesStringCodec))
        val value = codec.readValue(
          new RecordReader(
            Map(
              "id"    -> 7,
              "lines" -> "[{\"title\":\"apple\",\"quantity\":2}]"
            )
          ),
          IndexedSeq("id", "lines")
        )
        assertTrue(
          value == WithCustomLines(7, List(CartLine("apple", 2))),
          codec.toDbValues(WithCustomLines(8, List(CartLine("pear", 3)))) ==
            IndexedSeq(DbValue.DbInt(8), DbValue.DbString("[{\"title\":\"pear\",\"quantity\":3}]"))
        )
      }
    ),
    suite("option handling")(
      test("Option field is nullable column") {
        val codec = deriveCodec[WithOption]
        assertTrue(
          codec.columns == IndexedSeq("id", "nickname"),
          codec.columnCount == 2
        )
      },
      test("Option Some produces inner value") {
        val codec  = deriveCodec[WithOption]
        val values = codec.toDbValues(WithOption(1, Some("nick")))
        assertTrue(
          values == IndexedSeq(DbValue.DbInt(1), DbValue.DbString("nick"))
        )
      },
      test("Option None produces DbNull") {
        val codec  = deriveCodec[WithOption]
        val values = codec.toDbValues(WithOption(1, None))
        assertTrue(
          values == IndexedSeq(DbValue.DbInt(1), DbValue.DbNull)
        )
      },
      test("Maybe field is nullable column") {
        val codec = deriveCodec[WithMaybe]
        assertTrue(
          codec.columns == IndexedSeq("id", "nickname"),
          codec.columnCount == 2
        )
      },
      test("Maybe present produces inner value") {
        val codec  = deriveCodec[WithMaybe]
        val values = codec.toDbValues(WithMaybe(1, Maybe.present("nick")))
        assertTrue(
          values == IndexedSeq(DbValue.DbInt(1), DbValue.DbString("nick"))
        )
      },
      test("Maybe absent produces DbNull") {
        val codec  = deriveCodec[WithMaybe]
        val values = codec.toDbValues(WithMaybe(1, Maybe.absent))
        assertTrue(
          values == IndexedSeq(DbValue.DbInt(1), DbValue.DbNull)
        )
      }
    ),
    suite("enum/sealed trait handling")(
      test("simple enum produces single String column") {
        val codec = deriveCodec[Color]
        assertTrue(
          codec.columns == IndexedSeq("value"),
          codec.columnCount == 1
        )
      },
      test("enum toDbValues produces variant name as DbString") {
        val codec = deriveCodec[Color]
        assertTrue(
          codec.toDbValues(Color.Red) == IndexedSeq(DbValue.DbString("Red")),
          codec.toDbValues(Color.Green) == IndexedSeq(DbValue.DbString("Green")),
          codec.toDbValues(Color.Blue) == IndexedSeq(DbValue.DbString("Blue"))
        )
      },
      test("enum in record uses snake_case column name") {
        val codec = deriveCodec[WithEnum]
        assertTrue(
          codec.columns == IndexedSeq("id", "color"),
          codec.columnCount == 2
        )
      },
      test("enum in record toDbValues") {
        val codec  = deriveCodec[WithEnum]
        val values = codec.toDbValues(WithEnum(1, Color.Blue))
        assertTrue(
          values == IndexedSeq(DbValue.DbInt(1), DbValue.DbString("Blue"))
        )
      },
      test("optional enum with Some produces string value") {
        val codec  = deriveCodec[WithOptionalEnum]
        val values = codec.toDbValues(WithOptionalEnum(1, Some(Status.Active)))
        assertTrue(
          values == IndexedSeq(DbValue.DbInt(1), DbValue.DbString("Active"))
        )
      },
      test("optional enum with None produces DbNull") {
        val codec  = deriveCodec[WithOptionalEnum]
        val values = codec.toDbValues(WithOptionalEnum(1, None))
        assertTrue(
          values == IndexedSeq(DbValue.DbInt(1), DbValue.DbNull)
        )
      },
      test("all enum variants round-trip via toDbValues") {
        val codec = deriveCodec[Status]
        assertTrue(
          codec.toDbValues(Status.Active) == IndexedSeq(DbValue.DbString("Active")),
          codec.toDbValues(Status.Inactive) == IndexedSeq(DbValue.DbString("Inactive")),
          codec.toDbValues(Status.Pending) == IndexedSeq(DbValue.DbString("Pending"))
        )
      },
      test("enum with @rename uses custom names in toDbValues") {
        val codec = deriveCodec[BonusType]
        assertTrue(
          codec.toDbValues(BonusType.Cent) == IndexedSeq(DbValue.DbString("CENT")),
          codec.toDbValues(BonusType.Percentage) == IndexedSeq(DbValue.DbString("PCT"))
        )
      },
      test("enum with @rename reads custom names from DB") {
        val codec = deriveCodec[BonusType]
        assertTrue(
          codec.readValue(new SingleStringColumnReader("value", "CENT"), IndexedSeq("value")) == BonusType.Cent,
          codec.readValue(new SingleStringColumnReader("value", "PCT"), IndexedSeq("value")) == BonusType.Percentage
        )
      }
    ),
    suite("modifier handling")(
      test("transient field excluded from columns") {
        val codec = deriveCodec[WithTransient]
        assertTrue(
          codec.columns == IndexedSeq("name"),
          codec.columnCount == 1
        )
      },
      test("transient field excluded from toDbValues") {
        val codec  = deriveCodec[WithTransient]
        val values = codec.toDbValues(WithTransient("Alice", 42))
        assertTrue(values == IndexedSeq(DbValue.DbString("Alice")))
      },
      test("rename modifier uses custom name") {
        val codec = deriveCodec[WithRename]
        assertTrue(
          codec.columns == IndexedSeq("user_name", "age")
        )
      }
    ),
    suite("column name mapping")(
      test("default SnakeCase mapper: camelCase fields become snake_case columns") {
        val codec = deriveCodec[CamelCaseFields]
        assertTrue(
          codec.columns == IndexedSeq("first_name", "last_name"),
          codec.columnCount == 2
        )
      },
      test("Modifier.rename overrides SnakeCase mapper") {
        val codec = deriveCodec[WithRename]
        assertTrue(
          codec.columns == IndexedSeq("user_name", "age")
        )
      },
      test("Identity mapper preserves camelCase field names") {
        val codec = CamelCaseFields.schema.deriving(DbCodecDeriver.withColumnNameMapper(SqlNameMapper.Identity)).derive
        assertTrue(
          codec.columns == IndexedSeq("firstName", "lastName"),
          codec.columnCount == 2
        )
      },
      test("Custom mapper applies custom function") {
        val upperMapper = SqlNameMapper.Custom(_.toUpperCase)
        val codec       = CamelCaseFields.schema.deriving(DbCodecDeriver.withColumnNameMapper(upperMapper)).derive
        assertTrue(
          codec.columns == IndexedSeq("FIRSTNAME", "LASTNAME")
        )
      }
    ),
    suite("JSONB fallback for non-primitive types")(
      test("List[Int] produces single JSONB column") {
        val codec = deriveCodec[List[Int]]
        assertTrue(
          codec.columns == IndexedSeq("value"),
          codec.columnCount == 1
        )
      },
      test("List[Int] serializes as JSON string") {
        val codec  = deriveCodec[List[Int]]
        val values = codec.toDbValues(List(1, 2, 3))
        assertTrue(values == IndexedSeq(DbValue.DbString("[1,2,3]")))
      },
      test("record with List field derives successfully") {
        val codec = deriveCodec[WithListField]
        assertTrue(
          codec.columns == IndexedSeq("id", "tags"),
          codec.columnCount == 2
        )
      },
      test("record with List field serializes list as JSONB") {
        val codec  = deriveCodec[WithListField]
        val values = codec.toDbValues(WithListField(1, List("a", "b")))
        assertTrue(
          values == IndexedSeq(DbValue.DbInt(1), DbValue.DbString("[\"a\",\"b\"]"))
        )
      },
      test("record with nested List[Record] serializes as JSONB") {
        val codec  = deriveCodec[WithNestedList]
        val values = codec.toDbValues(WithNestedList("test", List(JsonPayload("hi", 1))))
        assertTrue(
          values(0) == DbValue.DbString("test"),
          values(1).isInstanceOf[DbValue.DbString]
        )
      },
      test("record with List[SimpleRecord] field round-trips JSON array through JSONB fallback") {
        val codec   = deriveCodec[WithSimpleRecordList]
        val payload = WithSimpleRecordList(1, List(SimpleRecord("apple", 1), SimpleRecord("pear", 2)))
        val values  = codec.toDbValues(payload)
        val reader  = new RecordReader(
          Map(
            "id"    -> 1,
            "items" -> values(1).asInstanceOf[DbValue.DbString].value
          )
        )

        assertTrue(
          values == IndexedSeq(
            DbValue.DbInt(1),
            DbValue.DbString("""[{"name":"apple","age":1},{"name":"pear","age":2}]""")
          ),
          codec.readValue(reader, IndexedSeq("id", "items")) == payload
        )
      },
      test("Map[String,Int] produces single JSONB column") {
        val codec = deriveCodec[scala.collection.immutable.Map[String, Int]]
        assertTrue(
          codec.columns == IndexedSeq("value"),
          codec.columnCount == 1
        )
      },
      test("complex sealed trait serializes as JSONB") {
        val codec = deriveCodec[Shape]
        assertTrue(
          codec.columns == IndexedSeq("value"),
          codec.columnCount == 1
        )
      },
      test("complex sealed trait toDbValues produces JSON") {
        val codec  = deriveCodec[Shape]
        val values = codec.toDbValues(Shape.Circle(5.0))
        assertTrue(values.size == 1, values(0).isInstanceOf[DbValue.DbString])
      }
    ),
    suite("jsonb helpers")(
      test("jsonb writes JSON strings via DbString") {
        val codec = DbCodec.jsonb[JsonPayload]
        assertTrue(
          codec.columns == IndexedSeq("value"),
          codec.toDbValues(JsonPayload("hello", 2)) == IndexedSeq(
            DbValue.DbString("{\"message\":\"hello\",\"count\":2}")
          )
        )
      },
      test("jsonb overload writes and reads via provided functions") {
        val codec = DbCodec.jsonb[JsonPayload](
          value => s"${value.message}:${value.count}",
          input =>
            input.split(":", 2).toList match {
              case message :: count :: Nil => JsonPayload(message, count.toInt)
              case _                       => throw new RuntimeException(s"bad test payload: $input")
            }
        )
        assertTrue(
          codec.toDbValues(JsonPayload("hello", 2)) == IndexedSeq(DbValue.DbString("hello:2"))
        )
      },
      test("jsonbOption encodes Some and None") {
        val codec = DbCodec.jsonbOption[JsonPayload]
        assertTrue(
          codec.toDbValues(Some(JsonPayload("hello", 2))) == IndexedSeq(
            DbValue.DbString("{\"message\":\"hello\",\"count\":2}")
          ),
          codec.toDbValues(None) == IndexedSeq(DbValue.DbNull)
        )
      },
      test("jsonbOption overload encodes Some and None via provided functions") {
        val codec = DbCodec.jsonbOption[JsonPayload](
          value => s"${value.message}:${value.count}",
          input =>
            input.split(":", 2).toList match {
              case message :: count :: Nil => JsonPayload(message, count.toInt)
              case _                       => throw new RuntimeException(s"bad test payload: $input")
            }
        )
        assertTrue(
          codec.toDbValues(Some(JsonPayload("hello", 2))) == IndexedSeq(DbValue.DbString("hello:2")),
          codec.toDbValues(None) == IndexedSeq(DbValue.DbNull)
        )
      },
      test("jsonb decodes JSON strings from reader") {
        val codec  = DbCodec.jsonb[JsonPayload]
        val reader = new DbResultReader {
          def getInt(index: Int): Int                                  = 0
          def getInt(label: String): Int                               = 0
          def getLong(index: Int): Long                                = 0L
          def getLong(label: String): Long                             = 0L
          def getDouble(index: Int): Double                            = 0d
          def getDouble(label: String): Double                         = 0d
          def getFloat(index: Int): Float                              = 0f
          def getFloat(label: String): Float                           = 0f
          def getBoolean(index: Int): Boolean                          = false
          def getBoolean(label: String): Boolean                       = false
          def getString(index: Int): String                            = "{\"message\":\"hello\",\"count\":2}"
          def getString(label: String): String                         = "{\"message\":\"hello\",\"count\":2}"
          def getBigDecimal(index: Int): java.math.BigDecimal          = null
          def getBigDecimal(label: String): java.math.BigDecimal       = null
          def getBytes(index: Int): Array[Byte]                        = null
          def getBytes(label: String): Array[Byte]                     = null
          def getShort(index: Int): Short                              = 0
          def getShort(label: String): Short                           = 0
          def getByte(index: Int): Byte                                = 0
          def getByte(label: String): Byte                             = 0
          def getLocalDate(index: Int): java.time.LocalDate            = null
          def getLocalDate(label: String): java.time.LocalDate         = null
          def getLocalDateTime(index: Int): java.time.LocalDateTime    = null
          def getLocalDateTime(label: String): java.time.LocalDateTime = null
          def getLocalTime(index: Int): java.time.LocalTime            = null
          def getLocalTime(label: String): java.time.LocalTime         = null
          def getInstant(index: Int): java.time.Instant                = null
          def getInstant(label: String): java.time.Instant             = null
          def getDuration(index: Int): java.time.Duration              = null
          def getDuration(label: String): java.time.Duration           = null
          def getUUID(index: Int): java.util.UUID                      = null
          def getUUID(label: String): java.util.UUID                   = null
          def columnLabel(index: Int): String                          = "value"
          def hasColumn(label: String): Boolean                        = label == "value"
          def wasNull: Boolean                                         = false
        }

        assertTrue(codec.readValue(reader, IndexedSeq("value")) == JsonPayload("hello", 2))
      }
    ),
    suite("inline field flattening")(
      test("inline record field expands to prefixed columns") {
        val codec = deriveCodec[PersonWithInline]
        assertTrue(
          codec.columns == IndexedSeq("address_street", "address_city", "name"),
          codec.columnCount == 3
        )
      },
      test("inline record field toDbValues produces flat values") {
        val codec  = deriveCodec[PersonWithInline]
        val values = codec.toDbValues(PersonWithInline(InlineAddress("Main St", "NYC"), "Alice"))
        assertTrue(
          values == IndexedSeq(
            DbValue.DbString("Main St"),
            DbValue.DbString("NYC"),
            DbValue.DbString("Alice")
          )
        )
      },
      test("non-inline record field produces single JSONB column") {
        val codec = deriveCodec[PersonWithoutInline]
        assertTrue(
          codec.columns == IndexedSeq("address", "name"),
          codec.columnCount == 2
        )
      },
      test("non-inline record field toDbValues produces JSON string") {
        val codec  = deriveCodec[PersonWithoutInline]
        val values = codec.toDbValues(PersonWithoutInline(InlineAddress("Main St", "NYC"), "Alice"))
        assertTrue(
          values.size == 2,
          values(0).isInstanceOf[DbValue.DbString],
          values(1) == DbValue.DbString("Alice")
        )
      },
      test("mixed inline record + non-record fields") {
        val codec = deriveCodec[MixedInline]
        assertTrue(
          codec.columns == IndexedSeq("address_street", "address_city", "tags", "name"),
          codec.columnCount == 4
        )
      },
      test("mixed inline toDbValues") {
        val codec  = deriveCodec[MixedInline]
        val values = codec.toDbValues(MixedInline(InlineAddress("Elm St", "LA"), List("a", "b"), "Bob"))
        assertTrue(
          values(0) == DbValue.DbString("Elm St"),
          values(1) == DbValue.DbString("LA"),
          values(2).isInstanceOf[DbValue.DbString],
          values(3) == DbValue.DbString("Bob")
        )
      },
      test("multiple inline fields with same nested type") {
        val codec = deriveCodec[NestedOuter]
        assertTrue(
          codec.columns == IndexedSeq("position_x", "position_y", "size_x", "size_y", "label"),
          codec.columnCount == 5
        )
      },
      test("multiple inline fields toDbValues") {
        val codec  = deriveCodec[NestedOuter]
        val values = codec.toDbValues(NestedOuter(NestedInner(1, 2), NestedInner(10, 20), "rect"))
        assertTrue(
          values == IndexedSeq(
            DbValue.DbInt(1),
            DbValue.DbInt(2),
            DbValue.DbInt(10),
            DbValue.DbInt(20),
            DbValue.DbString("rect")
          )
        )
      },
      test("type-level inline_fields inlines all record fields") {
        val codec = deriveCodec[AutoInlineOrder]
        assertTrue(
          codec.columns == IndexedSeq(
            "id",
            "shipping_street",
            "shipping_city",
            "billing_street",
            "billing_city"
          ),
          codec.columnCount == 5
        )
      },
      test("type-level inline_fields toDbValues") {
        val codec  = deriveCodec[AutoInlineOrder]
        val values =
          codec.toDbValues(AutoInlineOrder(42L, InlineAddress("Ship St", "NYC"), InlineAddress("Bill St", "LA")))
        assertTrue(
          values == IndexedSeq(
            DbValue.DbLong(42L),
            DbValue.DbString("Ship St"),
            DbValue.DbString("NYC"),
            DbValue.DbString("Bill St"),
            DbValue.DbString("LA")
          )
        )
      },
      test("maybeCodec handles Present(null) without CCE (unsafeGet null check)") {
        val mcodec = summon[DbCodec[Maybe[String]]]
        val values = mcodec.toDbValues(Maybe.present(null))
        assertTrue(values == IndexedSeq(DbValue.DbNull))
      }
    ),
    suite("positional decode avoids per-row metadata (F2)")(
      test("all single-column primitives decode 2 rows with zero columnLabel calls") {
        val reader = new RowReader
        // Row 1 at indexes 1-9
        val (i1, l1, s1, b1, d1, f1, sh1, by1, bd1) = (
          DbCodec.intCodec.readValue(reader, 1),
          DbCodec.longCodec.readValue(reader, 2),
          DbCodec.stringCodec.readValue(reader, 3),
          DbCodec.booleanCodec.readValue(reader, 4),
          DbCodec.doubleCodec.readValue(reader, 5),
          DbCodec.floatCodec.readValue(reader, 6),
          DbCodec.shortCodec.readValue(reader, 7),
          DbCodec.byteCodec.readValue(reader, 8),
          DbCodec.bigDecimalCodec.readValue(reader, 9)
        )
        reader.row = 2
        // Row 2 at the same indexes
        val (i2, l2, s2, b2, d2, f2, sh2, by2, bd2) = (
          DbCodec.intCodec.readValue(reader, 1),
          DbCodec.longCodec.readValue(reader, 2),
          DbCodec.stringCodec.readValue(reader, 3),
          DbCodec.booleanCodec.readValue(reader, 4),
          DbCodec.doubleCodec.readValue(reader, 5),
          DbCodec.floatCodec.readValue(reader, 6),
          DbCodec.shortCodec.readValue(reader, 7),
          DbCodec.byteCodec.readValue(reader, 8),
          DbCodec.bigDecimalCodec.readValue(reader, 9)
        )
        assertTrue(
          (i1, l1, s1, b1, d1, f1, sh1, by1) == (101, 102L, "r1c3", false, 105.0, 106.0f, 107.toShort, 108.toByte),
          bd1.toString == "109",
          (i2, l2, s2, b2, d2, f2, sh2, by2) == (201, 202L, "r2c3", true, 205.0, 206.0f, 207.toShort, 208.toByte),
          bd2.toString == "209",
          reader.columnLabelCalls == 0
        )
      }
    ),
    suite("record label-path slice hoist (F2b)")(
      test("reordered labels slice once per statement, not once per field per row") {
        val codec  = deriveCodec[SimpleRecord]
        val labels = new CountingLabels(IndexedSeq("name", "age"))
        // Result set exposes a different first column, forcing the codec onto
        // the label fallback path for every row.
        val reader = new LabelReader(Map[String, Any]("name" -> "Alice", "age" -> 3), IndexedSeq("other", "age"))
        val first  = codec.readValue(reader, labels)
        val second = codec.readValue(reader, labels)
        assertTrue(
          first == SimpleRecord("Alice", 3),
          second == SimpleRecord("Alice", 3),
          labels.sliceCalls == 2
        )
      }
    ),
    suite("char decoding rejects null and empty (F5)")(
      test("SQL NULL char fails fast on both overloads") {
        val codec = deriveCodec[WithChar]
        // Two labels forces the record codec onto the label fallback path,
        // exercising the char codec's label overload directly; the single
        // label takes the aligned positional path.
        val viaLabels   = scala.util.Try(codec.readValue(new ConstStringReader(null), IndexedSeq("c", "extra")))
        val viaPosition = scala.util.Try(codec.readValue(new ConstStringReader(null), 1))
        assertTrue(
          viaLabels.isFailure,
          viaLabels.failed.get.isInstanceOf[IllegalStateException],
          viaLabels.failed.get.getMessage.contains("NULL"),
          viaPosition.isFailure,
          viaPosition.failed.get.isInstanceOf[IllegalStateException]
        )
      },
      test("empty string char fails fast on both overloads") {
        val codec       = deriveCodec[WithChar]
        val viaLabels   = scala.util.Try(codec.readValue(new ConstStringReader(""), IndexedSeq("c", "extra")))
        val viaPosition = scala.util.Try(codec.readValue(new ConstStringReader(""), 1))
        assertTrue(
          viaLabels.isFailure,
          viaLabels.failed.get.isInstanceOf[IllegalStateException],
          viaLabels.failed.get.getMessage.contains("empty"),
          viaPosition.isFailure,
          viaPosition.failed.get.isInstanceOf[IllegalStateException]
        )
      },
      test("single-char string still decodes") {
        val codec  = deriveCodec[WithChar]
        val reader = new LabelReader(Map[String, Any]("c" -> "Z"), IndexedSeq("c"))
        assertTrue(codec.readValue(reader, IndexedSeq("c")) == WithChar('Z'))
      }
    ),
    suite("As Either helpers (F10)")(
      test("decodeViaAs/encodeViaAs preserve success and failure as values") {
        val conv = new As[String, Int] {
          def into(s: String): Either[SchemaError, Int] =
            scala.util.Try(s.toInt).toEither.left.map(_ => SchemaError(s"not an int: $s"))
          def from(i: Int): Either[SchemaError, String] = Right(i.toString)
        }
        assertTrue(
          DbCodec.decodeViaAs(conv, "42") == Right(42),
          DbCodec.decodeViaAs(conv, "nope").isLeft,
          DbCodec.encodeViaAs(conv, 7) == Right("7")
        )
      },
      test("decodeJsonbEither round-trips success and surfaces bad JSON as Left") {
        implicit val jsonCodec: JsonCodec[JsonPayload] = JsonPayload.jsonCodec
        assertTrue(
          DbCodec.decodeJsonbEither[JsonPayload]("{\"message\":\"hello\",\"count\":2}") ==
            Right(JsonPayload("hello", 2)),
          DbCodec.decodeJsonbEither[JsonPayload]("not json").isLeft
        )
      }
    )
  )
}
