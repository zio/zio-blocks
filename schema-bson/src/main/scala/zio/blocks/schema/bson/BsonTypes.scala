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

package zio.blocks.schema.bson

import org.bson.{
  BsonArray,
  BsonBoolean,
  BsonDocument,
  BsonDouble,
  BsonInt32,
  BsonInt64,
  BsonReader,
  BsonString,
  BsonValue,
  BsonWriter
}
import org.bson.types.{Decimal128, ObjectId}
import zio.blocks.chunk.Chunk
import zio.blocks.schema.json.Json
import java.math.{BigDecimal => JBigDecimal, BigInteger => JBigInteger}
import java.time._
import java.util.{Currency, UUID}
import scala.util.control.NoStackTrace
import scala.jdk.CollectionConverters._

/**
 * One step of the path to a decoding error: a document field or an array index.
 * A [[BsonDecoder.Error]] holds the steps innermost first.
 */
sealed trait BsonTrace

/** The two kinds of [[BsonTrace]] step, and the renderer of a path. */
object BsonTrace {

  /**
   * Renders a path, outermost step first, as `.items[0].qty`. `trace` is
   * innermost first, the order a decoder builds it in; an empty trace renders
   * as the empty string.
   */
  def render(trace: List[BsonTrace]): String =
    trace.reverse.map {
      case Field(name) => s".$name"
      case Array(idx)  => s"[$idx]"
    }.mkString

  /** A step into the document field `name`, rendered as `.name`. */
  case class Field(name: String) extends BsonTrace

  /** A step into the array element at index `idx`, rendered as `[idx]`. */
  case class Array(idx: Int) extends BsonTrace
}

/**
 * Writes values of type `A` as BSON, either to a `BsonValue` with `toBsonValue`
 * or to a driver `BsonWriter` with `encode`. A derived [[BsonCodec]] holds one;
 * build one for a new type from an existing encoder with `contramap`.
 *
 * {{{
 * case class Sku(code: String)
 *
 * val skuEncoder: BsonEncoder[Sku] = BsonCodec.string.encoder.contramap(_.code)
 * val value = skuEncoder.toBsonValue(Sku("mug-02")) // BsonString{value='mug-02'}
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/bson/ BSON Codec Module]]
 */
trait BsonEncoder[A] { self =>

  /**
   * Returns an encoder for `B` that converts each value with `f` and encodes
   * the result with this encoder.
   */
  final def contramap[B](f: B => A): BsonEncoder[B] = new BsonEncoder[B] {
    override def isAbsent(value: B): Boolean = self.isAbsent(f(value))

    def encode(writer: BsonWriter, value: B, ctx: BsonEncoder.EncoderContext): Unit =
      self.encode(writer, f(value), ctx)

    def toBsonValue(value: B): BsonValue = self.toBsonValue(f(value))
  }

  /**
   * Returns whether a field holding `value` may be left out of its document. It
   * is `false` unless an encoder overrides it, and the derived codecs never
   * call it: they write every field that is not `Modifier.transient`.
   */
  def isAbsent(value: A): Boolean = false

  /**
   * Writes `value` at the current position of `writer`. A value that encodes as
   * a document, such as a record, a sealed trait with the default
   * [[BsonSchemaCodec.SumTypeHandling]] or a map with `String` keys, can be the
   * top-level value of an `org.bson.BsonDocumentWriter`. Any other value must
   * be written where the writer expects a value, such as after a field name the
   * caller has written or inside an array. The derived codecs ignore `ctx`.
   */
  def encode(writer: BsonWriter, value: A, ctx: BsonEncoder.EncoderContext): Unit

  /**
   * Returns `value` as a `BsonValue`, such as a `BsonDocument` for a record;
   * call `asDocument()` on it to get a document to insert into MongoDB.
   */
  def toBsonValue(value: A): BsonValue
}

/** The encoder context and two built-in encoders. */
object BsonEncoder {

  /**
   * Options passed to `encode`. The derived codecs never read
   * `inlineNextObject`.
   */
  case class EncoderContext(inlineNextObject: Boolean = false)

  /** The default [[EncoderContext]]. */
  object EncoderContext {

    /** An [[EncoderContext]] with `inlineNextObject = false`. */
    val default: EncoderContext = EncoderContext()
  }

  /**
   * Writes a `java.math.BigInteger` as a BSON `STRING` of its decimal digits,
   * so values of any size fit.
   */
  val bigInteger: BsonEncoder[JBigInteger] = new BsonEncoder[JBigInteger] {
    def encode(writer: BsonWriter, value: JBigInteger, ctx: EncoderContext): Unit =
      writer.writeString(value.toString)

    def toBsonValue(value: JBigInteger): BsonValue =
      new org.bson.BsonString(value.toString)
  }

  /** Writes a `BsonValue` as it is, with the driver's `BsonValueCodec`. */
  val bsonValueEncoder: BsonEncoder[BsonValue] = new BsonEncoder[BsonValue] {
    def encode(writer: BsonWriter, value: BsonValue, ctx: EncoderContext): Unit = {
      val codec = new org.bson.codecs.BsonValueCodec()
      codec.encode(writer, value, org.bson.codecs.EncoderContext.builder().build())
    }

    def toBsonValue(value: BsonValue): BsonValue = value
  }
}

/**
 * Reads values of type `A` from BSON, either from a `BsonValue` with
 * `fromBsonValue` or from a driver `BsonReader` with `decode`. A failure is a
 * [[BsonDecoder.Error]] that carries the path to the bad value. A derived
 * [[BsonCodec]] holds one; adapt one with `map` or `mapOrFail`.
 *
 * Only a [[BsonDecoder.Error]] becomes a `Left`. A known bug lets other
 * exceptions escape: a string that does not parse as a `UUID`, `Currency`,
 * `ZoneId` or `java.time` value, or a `DayOfWeek` or `Month` that is not an
 * upper-case name, throws its parser's exception (such as
 * `IllegalArgumentException`, or [[zio.blocks.schema.SchemaError]] for
 * `Instant`). Through `decode`, a primitive or record value of the wrong BSON
 * type throws `org.bson.BsonInvalidOperationException` instead of returning
 * `Left`, and a `Char` string longer than one character throws
 * `RuntimeException`.
 *
 * {{{
 * import org.bson.BsonDocument
 * import zio.blocks.schema._
 *
 * case class LineItem(sku: String, qty: Int)
 * object LineItem {
 *   implicit val schema: Schema[LineItem] = Schema.derived
 * }
 *
 * val decoder = LineItem.schema.derive(BsonCodecDeriver).decoder
 * val ok      = decoder.fromBsonValue(BsonDocument.parse("""{"sku": "mug", "qty": 2}"""))
 * // Right(LineItem(mug,2))
 * val bad = decoder.fromBsonValue(BsonDocument.parse("""{"sku": "mug", "qty": "two"}"""))
 * val why = bad.left.map(_.render) // Left(.qty: Expected INT32 but got STRING)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/bson/ BSON Codec Module]]
 */
trait BsonDecoder[A] { self =>

  /**
   * Reads one value at the current position of `reader` and returns it, or the
   * [[BsonDecoder.Error]] that stopped it. A record reads a whole document, so
   * it can start at the top of an `org.bson.BsonDocumentReader`. Other
   * exceptions are thrown, not returned (a known bug; see [[BsonDecoder]]).
   */
  def decode(reader: BsonReader): Either[BsonDecoder.Error, A] =
    try Right(decodeUnsafe(reader, Nil, BsonDecoder.BsonDecoderContext.default))
    catch {
      case e: BsonDecoder.Error => Left(e)
    }

  /**
   * Converts `value`, such as a `BsonDocument` read from MongoDB, and returns
   * the result or the [[BsonDecoder.Error]] that stopped it. A string that its
   * parser rejects throws instead (a known bug; see [[BsonDecoder]]).
   */
  def fromBsonValue(value: BsonValue): Either[BsonDecoder.Error, A] =
    try Right(fromBsonValueUnsafe(value, Nil, BsonDecoder.BsonDecoderContext.default))
    catch {
      case e: BsonDecoder.Error => Left(e)
    }

  /**
   * Returns the value for a field missing from its document, or throws
   * [[BsonDecoder.Error]] with the message `missing`, which this default does.
   * The derived codecs never call it: they use the field's default value or
   * fail with `Missing required field: <name>`.
   */
  def decodeMissingUnsafe(trace: List[BsonTrace]): A =
    throw BsonDecoder.Error(trace, "missing")

  /**
   * Reads one value from `reader`, or throws [[BsonDecoder.Error]] with `trace`
   * (the path so far, innermost first) as its path. Implement it in a custom
   * decoder; call `decode` otherwise.
   */
  def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A

  /**
   * Converts `value`, or throws [[BsonDecoder.Error]] with `trace` (the path so
   * far, innermost first) as its path. Implement it in a custom decoder; call
   * `fromBsonValue` otherwise.
   */
  def fromBsonValueUnsafe(value: BsonValue, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A

  /** Returns a decoder that decodes with this one and converts with `f`. */
  def map[B](f: A => B): BsonDecoder[B] = new BsonDecoder[B] {
    override def decodeMissingUnsafe(trace: List[BsonTrace]): B = f(self.decodeMissingUnsafe(trace))

    def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): B = f(
      self.decodeUnsafe(reader, trace, ctx)
    )

    def fromBsonValueUnsafe(value: BsonValue, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): B = f(
      self.fromBsonValueUnsafe(value, trace, ctx)
    )
  }

  /**
   * Returns a decoder that decodes with this one and converts with `f`. A
   * `Left(message)` from `f` fails decoding with a [[BsonDecoder.Error]] that
   * has `message` and the current path.
   *
   * {{{
   * import org.bson.BsonInt32
   *
   * val quantity = BsonCodec.int.decoder.mapOrFail { n =>
   *   if (n > 0) Right(n) else Left("quantity must be positive")
   * }
   * val result = quantity.fromBsonValue(new BsonInt32(0)).left.map(_.message)
   * // Left(quantity must be positive)
   * }}}
   */
  def mapOrFail[B](f: A => Either[String, B]): BsonDecoder[B] = new BsonDecoder[B] {
    override def decodeMissingUnsafe(trace: List[BsonTrace]): B = f(self.decodeMissingUnsafe(trace)) match {
      case Left(err)    => throw BsonDecoder.Error(trace, err)
      case Right(value) => value
    }

    def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): B =
      f(self.decodeUnsafe(reader, trace, ctx)) match {
        case Left(msg)    => throw BsonDecoder.Error(trace, msg)
        case Right(value) => value
      }

    def fromBsonValueUnsafe(value: BsonValue, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): B =
      f(self.fromBsonValueUnsafe(value, trace, ctx)) match {
        case Left(msg)    => throw BsonDecoder.Error(trace, msg)
        case Right(value) => value
      }
  }
}

/** The decoder context, the decoding error and a built-in decoder. */
object BsonDecoder {

  /**
   * Options passed to the unsafe decoding methods. `ignoreExtraField` names one
   * document field a record decoder skips even when it rejects unknown fields;
   * a [[BsonSchemaCodec.SumTypeHandling.DiscriminatorField]] codec sets it to
   * its discriminator field.
   */
  case class BsonDecoderContext(ignoreExtraField: Option[String] = None)

  /** The default [[BsonDecoderContext]]. */
  object BsonDecoderContext {

    /** A [[BsonDecoderContext]] that names no field to skip. */
    val default: BsonDecoderContext = BsonDecoderContext()
  }

  /**
   * A decoding failure: what went wrong in `message`, such as
   * `Expected INT32 but got STRING` or `Missing required field: total`, and
   * where in `trace`, innermost step first. Its `getMessage` is
   * `Path: .items[0].qty, error: ...`; for an error at the top level the path
   * is empty. It has no stack trace.
   */
  case class Error(trace: List[BsonTrace], message: String)
      extends RuntimeException(
        s"Path: ${BsonTrace.render(trace)}, error: $message"
      )
      with NoStackTrace {

    /** Returns the path and the message, as `.items[0].qty: Expected ...`. */
    def render: String = s"${BsonTrace.render(trace)}: $message"
  }

  /**
   * Reads a `java.math.BigInteger` from a BSON `STRING` of decimal digits.
   * Through `fromBsonValue`, another BSON type fails with
   * `Expected STRING but got <type>`.
   */
  val bigInteger: BsonDecoder[JBigInteger] = new BsonDecoder[JBigInteger] {
    def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoderContext): JBigInteger =
      new JBigInteger(reader.readString())

    def fromBsonValueUnsafe(value: BsonValue, trace: List[BsonTrace], ctx: BsonDecoderContext): JBigInteger =
      if (value.isString) new JBigInteger(value.asString().getValue)
      else throw Error(trace, s"Expected STRING but got ${value.getBsonType}")
  }
}

/**
 * A [[BsonEncoder]] and a [[BsonDecoder]] for the same type: what
 * `Schema#derive(BsonCodecDeriver)` and [[BsonSchemaCodec]] return. Use it to
 * turn case classes into `BsonDocument`s for the MongoDB Java driver and back.
 * It does not extend `zio.blocks.schema.codec.Codec`.
 *
 * A derived codec writes a case class as a document with one field per case
 * class field, in declaration order, named after the field or its
 * `Modifier.rename`. It leaves out `Modifier.transient` fields, and on decode
 * fills a missing field with its default value or fails with
 * `Missing required field: <name>`. A collection becomes an array and a map
 * with `String` keys a document. A sealed trait is stored as
 * [[BsonSchemaCodec.SumTypeHandling]] says, and so is `Option`, as a
 * `{"Some": {"value": ...}}` or `{"None": {}}` sub-document rather than the
 * value or `null` (a known bug). The companion's codecs list the BSON type of
 * each primitive; `java.time` values, `UUID`, `BigInt` and `Char` are strings.
 *
 * {{{
 * import org.bson.types.ObjectId
 * import zio.blocks.schema._
 *
 * case class LineItem(sku: String, qty: Int)
 * object LineItem {
 *   implicit val schema: Schema[LineItem] = Schema.derived
 * }
 *
 * case class Order(@Modifier.rename("_id") id: ObjectId, total: BigDecimal, items: List[LineItem])
 * object Order {
 *   import ObjectIdSupport.objectIdSchema
 *   implicit val schema: Schema[Order] = Schema.derived
 * }
 *
 * val codec: BsonCodec[Order] = Order.schema.derive(BsonCodecDeriver)
 * val order    = Order(new ObjectId("6720f1a2b3c4d5e6f7a8b9c0"), BigDecimal("59.90"), List(LineItem("mug", 2)))
 * val document = codec.encoder.toBsonValue(order).asDocument()
 * val json     = document.toJson
 * // {"_id": {"\$oid": "6720f1a2b3c4d5e6f7a8b9c0"}, "total": {"\$numberDecimal": "59.90"}, "items": [{"sku": "mug", "qty": 2}]}
 * val back = codec.decoder.fromBsonValue(document)
 * // Right(Order(6720f1a2b3c4d5e6f7a8b9c0,59.90,List(LineItem(mug,2))))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/bson/ BSON Codec Module]]
 */
final case class BsonCodec[A](encoder: BsonEncoder[A], decoder: BsonDecoder[A]) {

  /**
   * Returns a codec for `B` that converts with `to` after decoding and with
   * `from` before encoding.
   */
  def transform[B](to: A => B)(from: B => A): BsonCodec[B] =
    BsonCodec(encoder.contramap(from), decoder.map(to))

  /**
   * Returns a codec for `B` that converts with `to` after decoding, where a
   * `Left(message)` fails decoding with a [[BsonDecoder.Error]], and with
   * `from` before encoding.
   *
   * {{{
   * import org.bson.BsonInt32
   *
   * case class Quantity(value: Int)
   *
   * val quantity: BsonCodec[Quantity] = BsonCodec.int.transformOrFail { n =>
   *   if (n > 0) Right(Quantity(n)) else Left("quantity must be positive")
   * }(_.value)
   * val stored = quantity.encoder.toBsonValue(Quantity(3)) // BsonInt32{value=3}
   * val read   = quantity.decoder.fromBsonValue(new BsonInt32(-1)).left.map(_.message)
   * // Left(quantity must be positive)
   * }}}
   */
  def transformOrFail[B](to: A => Either[String, B])(from: B => A): BsonCodec[B] =
    BsonCodec(encoder.contramap(from), decoder.mapOrFail(to))
}

/**
 * The built-in codecs a derived codec uses for primitive types, each named with
 * the BSON type it writes. Through `fromBsonValue`, a value of another BSON
 * type fails with `Expected <TYPE> but got <TYPE>`.
 */
object BsonCodec {
  private def primitive[A](
    writeValue: (BsonWriter, A) => Unit,
    toBson: A => BsonValue,
    readValue: BsonReader => A,
    fromBson: (BsonValue, List[BsonTrace]) => A
  ): BsonCodec[A] = BsonCodec(
    new BsonEncoder[A] {
      def encode(writer: BsonWriter, value: A, ctx: BsonEncoder.EncoderContext): Unit =
        writeValue(writer, value)
      def toBsonValue(value: A): BsonValue = toBson(value)
    },
    new BsonDecoder[A] {
      def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A =
        readValue(reader)
      def fromBsonValueUnsafe(value: BsonValue, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): A =
        fromBson(value, trace)
    }
  )

  private def stringBased[A](
    toString: A => String,
    fromString: String => A
  ): BsonCodec[A] = primitive[A](
    (w, v) => w.writeString(toString(v)),
    v => new org.bson.BsonString(toString(v)),
    r => fromString(r.readString()),
    (v, trace) =>
      if (v.isString) fromString(v.asString().getValue)
      else throw BsonDecoder.Error(trace, s"Expected STRING but got ${v.getBsonType}")
  )

  /** Writes a `Boolean` as BSON `BOOLEAN`. */
  val boolean: BsonCodec[Boolean] = primitive[Boolean](
    (w, v) => w.writeBoolean(v),
    v => new org.bson.BsonBoolean(v),
    r => r.readBoolean(),
    (v, trace) =>
      if (v.isBoolean) v.asBoolean().getValue
      else throw BsonDecoder.Error(trace, s"Expected BOOLEAN but got ${v.getBsonType}")
  )

  /**
   * Writes a `Byte` as BSON `INT32`. Decoding keeps the low 8 bits of the
   * `INT32` without a range check, so `300` decodes as `44` (a known bug).
   */
  val byte: BsonCodec[Byte] = primitive[Byte](
    (w, v) => w.writeInt32(v.toInt),
    v => new org.bson.BsonInt32(v.toInt),
    r => r.readInt32().toByte,
    (v, trace) =>
      if (v.isInt32) v.asInt32().getValue.toByte
      else throw BsonDecoder.Error(trace, s"Expected INT32 but got ${v.getBsonType}")
  )

  /**
   * Writes a `Short` as BSON `INT32`. Decoding keeps the low 16 bits of the
   * `INT32` without a range check, so `70000` decodes as `4464` (a known bug).
   */
  val short: BsonCodec[Short] = primitive[Short](
    (w, v) => w.writeInt32(v.toInt),
    v => new org.bson.BsonInt32(v.toInt),
    r => r.readInt32().toShort,
    (v, trace) =>
      if (v.isInt32) v.asInt32().getValue.toShort
      else throw BsonDecoder.Error(trace, s"Expected INT32 but got ${v.getBsonType}")
  )

  /** Writes an `Int` as BSON `INT32`. */
  val int: BsonCodec[Int] = primitive[Int](
    (w, v) => w.writeInt32(v),
    v => new org.bson.BsonInt32(v),
    r => r.readInt32(),
    (v, trace) =>
      if (v.isInt32) v.asInt32().getValue
      else throw BsonDecoder.Error(trace, s"Expected INT32 but got ${v.getBsonType}")
  )

  /**
   * Writes a `Long` as BSON `INT64`. Decoding accepts only `INT64`, not
   * `INT32`.
   */
  val long: BsonCodec[Long] = primitive[Long](
    (w, v) => w.writeInt64(v),
    v => new org.bson.BsonInt64(v),
    r => r.readInt64(),
    (v, trace) =>
      if (v.isInt64) v.asInt64().getValue
      else throw BsonDecoder.Error(trace, s"Expected INT64 but got ${v.getBsonType}")
  )

  /**
   * Writes a `Float` as BSON `DOUBLE`, widened, so `1.1f` is stored as
   * `1.100000023841858`.
   */
  val float: BsonCodec[Float] = primitive[Float](
    (w, v) => w.writeDouble(v.toDouble),
    v => new org.bson.BsonDouble(v.toDouble),
    r => r.readDouble().toFloat,
    (v, trace) =>
      if (v.isDouble) v.asDouble().getValue.toFloat
      else throw BsonDecoder.Error(trace, s"Expected DOUBLE but got ${v.getBsonType}")
  )

  /**
   * Writes a `Double` as BSON `DOUBLE`. Decoding accepts only `DOUBLE`, not
   * `INT32`.
   */
  val double: BsonCodec[Double] = primitive[Double](
    (w, v) => w.writeDouble(v),
    v => new org.bson.BsonDouble(v),
    r => r.readDouble(),
    (v, trace) =>
      if (v.isDouble) v.asDouble().getValue
      else throw BsonDecoder.Error(trace, s"Expected DOUBLE but got ${v.getBsonType}")
  )

  /**
   * Writes a `Char` as a one-character BSON `STRING`. Decoding a longer string
   * fails, with a [[BsonDecoder.Error]] through `fromBsonValue` and a
   * `RuntimeException` through `decode` (a known bug).
   */
  val char: BsonCodec[Char] = primitive[Char](
    (w, v) => w.writeString(v.toString),
    v => new org.bson.BsonString(v.toString),
    r => {
      val s = r.readString()
      if (s.length == 1) s.charAt(0)
      else throw new RuntimeException(s"Expected single character string but got: $s")
    },
    (v, trace) =>
      if (v.isString) {
        val s = v.asString().getValue
        if (s.length == 1) s.charAt(0)
        else throw BsonDecoder.Error(trace, s"Expected single character string but got: $s")
      } else throw BsonDecoder.Error(trace, s"Expected STRING but got ${v.getBsonType}")
  )

  /** Writes a `String` as BSON `STRING`. */
  val string: BsonCodec[String] = primitive[String](
    (w, v) => w.writeString(v),
    v => new org.bson.BsonString(v),
    r => r.readString(),
    (v, trace) =>
      if (v.isString) v.asString().getValue
      else throw BsonDecoder.Error(trace, s"Expected STRING but got ${v.getBsonType}")
  )

  /**
   * Writes a `java.math.BigDecimal` as BSON `DECIMAL128`, keeping its scale.
   * Encoding a value that `DECIMAL128` cannot hold exactly, such as one with
   * more than 34 significant digits, throws `NumberFormatException`.
   */
  val bigDecimal: BsonCodec[JBigDecimal] = primitive[JBigDecimal](
    (w, v) => w.writeDecimal128(new Decimal128(v)),
    v => new org.bson.BsonDecimal128(new Decimal128(v)),
    r => r.readDecimal128().bigDecimalValue(),
    (v, trace) =>
      if (v.isDecimal128) v.asDecimal128().getValue.bigDecimalValue()
      else throw BsonDecoder.Error(trace, s"Expected DECIMAL128 but got ${v.getBsonType}")
  )

  /**
   * Writes a `UUID` as a BSON `STRING` in its standard form, not as BSON
   * binary.
   */
  val uuid: BsonCodec[UUID] = stringBased[UUID](
    _.toString,
    UUID.fromString
  )

  /**
   * Writes an `org.bson.types.ObjectId` as BSON `OBJECT_ID`, the type MongoDB
   * uses for `_id`. Decoding accepts only `OBJECT_ID`, not its hex string. A
   * derived codec uses it for a field whose schema is
   * [[ObjectIdSupport.objectIdSchema]].
   */
  val objectId: BsonCodec[ObjectId] = primitive[ObjectId](
    (w, v) => w.writeObjectId(v),
    v => new org.bson.BsonObjectId(v),
    r => r.readObjectId(),
    (v, trace) =>
      if (v.isObjectId) v.asObjectId().getValue
      else throw BsonDecoder.Error(trace, s"Expected OBJECT_ID but got ${v.getBsonType}")
  )

  /**
   * Writes a [[zio.blocks.schema.json.Json]] value as the matching BSON value:
   * an object as a document, an array as an array, and `null` as `NULL`. A
   * number without fractional digits is an `INT32`, an `INT64` or a
   * `DECIMAL128`, the first that holds it; any other number is a `DOUBLE` when
   * a `Double` holds it exactly, as `2.5`, and a `DECIMAL128` otherwise, as
   * `0.1`. An object with a repeated key throws `IllegalArgumentException`.
   * Decoding reads documents, arrays, strings, booleans, numbers and `NULL`,
   * and fails on other BSON types, such as `DATE_TIME`, with
   * `Unsupported BSON type for Json: <TYPE>`.
   */
  val json: BsonCodec[Json] = BsonCodec(
    new BsonEncoder[Json] {
      def encode(writer: BsonWriter, value: Json, ctx: BsonEncoder.EncoderContext): Unit =
        BsonEncoder.bsonValueEncoder.encode(writer, jsonToBsonValue(value), ctx)

      def toBsonValue(value: Json): BsonValue = jsonToBsonValue(value)
    },
    new BsonDecoder[Json] {
      private val bsonValueCodec = new org.bson.codecs.BsonValueCodec()
      private val decoderContext = org.bson.codecs.DecoderContext.builder().build()

      def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): Json =
        bsonValueToJson(bsonValueCodec.decode(reader, decoderContext), trace)

      def fromBsonValueUnsafe(value: BsonValue, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): Json =
        bsonValueToJson(value, trace)
    }
  )

  private def jsonToBsonValue(value: Json): BsonValue = value match {
    case obj: Json.Object =>
      val document = new BsonDocument()
      obj.value.foreach { case (key, json) =>
        if (document.containsKey(key))
          throw new IllegalArgumentException(
            s"Json object contains duplicate field '$key', which cannot be represented as a BSON document"
          )
        document.put(key, jsonToBsonValue(json))
      }
      document
    case array: Json.Array =>
      val bsonArray = new BsonArray()
      array.value.foreach(json => bsonArray.add(jsonToBsonValue(json)))
      bsonArray
    case string: Json.String   => new BsonString(string.value)
    case boolean: Json.Boolean => new BsonBoolean(boolean.value)
    case number: Json.Number   => jsonNumberToBsonValue(number.value)
    case Json.Null             => org.bson.BsonNull.VALUE
  }

  private def jsonNumberToBsonValue(value: BigDecimal): BsonValue = {
    val scale = value.bigDecimal.scale
    if (scale <= 0 && value.isValidInt) new BsonInt32(value.toInt)
    else if (scale <= 0 && value.isValidLong) new BsonInt64(value.toLong)
    else if (scale <= 0) jsonNumberToDecimal128(value)
    else {
      val double = value.toDouble
      if (
        java.lang.Double.isFinite(double) &&
        new JBigDecimal(double).compareTo(value.bigDecimal) == 0
      ) new BsonDouble(double)
      else jsonNumberToDecimal128(value)
    }
  }

  private def jsonNumberToDecimal128(value: BigDecimal): BsonValue =
    try new org.bson.BsonDecimal128(new Decimal128(value.bigDecimal))
    catch {
      case error: NumberFormatException =>
        throw new IllegalArgumentException(s"Json number cannot be represented exactly as BSON: $value", error)
    }

  private def bsonValueToJson(value: BsonValue, trace: List[BsonTrace]): Json =
    value.getBsonType match {
      case org.bson.BsonType.DOCUMENT =>
        val fields = scala.collection.mutable.ArrayBuffer.empty[(String, Json)]
        val iter   = value.asDocument().entrySet().iterator()
        while (iter.hasNext) {
          val entry = iter.next()
          fields += entry.getKey -> bsonValueToJson(entry.getValue, BsonTrace.Field(entry.getKey) :: trace)
        }
        new Json.Object(Chunk.fromIterable(fields))
      case org.bson.BsonType.ARRAY =>
        val elements = scala.collection.mutable.ArrayBuffer.empty[Json]
        val iter     = value.asArray().iterator()
        var idx      = 0
        while (iter.hasNext) {
          elements += bsonValueToJson(iter.next(), BsonTrace.Array(idx) :: trace)
          idx += 1
        }
        new Json.Array(Chunk.fromIterable(elements))
      case org.bson.BsonType.STRING  => Json.String(value.asString().getValue)
      case org.bson.BsonType.BOOLEAN => Json.Boolean(value.asBoolean().getValue)
      case org.bson.BsonType.INT32   => Json.Number(value.asInt32().getValue)
      case org.bson.BsonType.INT64   => Json.Number(value.asInt64().getValue)
      case org.bson.BsonType.DOUBLE  =>
        val double = value.asDouble().getValue
        if (java.lang.Double.isFinite(double)) {
          val decimal = new JBigDecimal(double)
          Json.Number(BigDecimal(if (decimal.scale <= 0) decimal.setScale(1) else decimal))
        } else throw BsonDecoder.Error(trace, s"Unsupported non-finite DOUBLE for Json: $double")
      case org.bson.BsonType.DECIMAL128 =>
        val decimal = value.asDecimal128().getValue
        if (decimal.isNaN || decimal.isInfinite)
          throw BsonDecoder.Error(trace, s"Unsupported non-finite DECIMAL128 for Json: $decimal")
        else if (decimal.compareTo(Decimal128.NEGATIVE_ZERO) == 0) Json.Number(0)
        else Json.Number(BigDecimal(decimal.bigDecimalValue()))
      case org.bson.BsonType.NULL => Json.Null
      case other                  => throw BsonDecoder.Error(trace, s"Unsupported BSON type for Json: $other")
    }

  /**
   * Writes a `DayOfWeek` as a BSON `STRING` such as `FRIDAY`. Decoding accepts
   * only that upper-case name; another string throws `IllegalArgumentException`
   * instead of returning `Left` (a known bug).
   */
  val dayOfWeek: BsonCodec[DayOfWeek] = stringBased[DayOfWeek](
    _.toString,
    DayOfWeek.valueOf
  )

  /** Writes a `Duration` as an ISO-8601 BSON `STRING`, such as `PT1H30M`. */
  val duration: BsonCodec[Duration] = stringBased[Duration](
    Json.durationRawCodec.encodeToString,
    x =>
      Json.durationRawCodec.decode(x) match {
        case Right(v)  => v
        case Left(err) => throw err
      }
  )

  /**
   * Writes an `Instant` as an ISO-8601 BSON `STRING`, such as
   * `2026-11-05T10:15:30Z`, not as a BSON `DATE_TIME`.
   */
  val instant: BsonCodec[Instant] = stringBased[Instant](
    Json.instantRawCodec.encodeToString,
    x =>
      Json.instantRawCodec.decode(x) match {
        case Right(v)  => v
        case Left(err) => throw err
      }
  )

  /**
   * Writes a `LocalDate` as an ISO-8601 BSON `STRING`, such as `2026-11-05`.
   */
  val localDate: BsonCodec[LocalDate] = stringBased[LocalDate](
    Json.localDateRawCodec.encodeToString,
    x =>
      Json.localDateRawCodec.decode(x) match {
        case Right(v)  => v
        case Left(err) => throw err
      }
  )

  /** Writes a `LocalDateTime` as an ISO-8601 BSON `STRING`. */
  val localDateTime: BsonCodec[LocalDateTime] = stringBased[LocalDateTime](
    Json.localDateTimeRawCodec.encodeToString,
    x =>
      Json.localDateTimeRawCodec.decode(x) match {
        case Right(v)  => v
        case Left(err) => throw err
      }
  )

  /** Writes a `LocalTime` as an ISO-8601 BSON `STRING`. */
  val localTime: BsonCodec[LocalTime] = stringBased[LocalTime](
    Json.localTimeRawCodec.encodeToString,
    x =>
      Json.localTimeRawCodec.decode(x) match {
        case Right(v)  => v
        case Left(err) => throw err
      }
  )

  /**
   * Writes a `Month` as a BSON `STRING` such as `NOVEMBER`. Decoding accepts
   * only that upper-case name; another string throws `IllegalArgumentException`
   * instead of returning `Left` (a known bug).
   */
  val month: BsonCodec[Month] = stringBased[Month](
    _.toString,
    Month.valueOf
  )

  /** Writes a `MonthDay` as an ISO-8601 BSON `STRING`, such as `--11-05`. */
  val monthDay: BsonCodec[MonthDay] = stringBased[MonthDay](
    Json.monthDayRawCodec.encodeToString,
    x =>
      Json.monthDayRawCodec.decode(x) match {
        case Right(v)  => v
        case Left(err) => throw err
      }
  )

  /** Writes an `OffsetDateTime` as an ISO-8601 BSON `STRING`. */
  val offsetDateTime: BsonCodec[OffsetDateTime] = stringBased[OffsetDateTime](
    Json.offsetDateTimeRawCodec.encodeToString,
    x =>
      Json.offsetDateTimeRawCodec.decode(x) match {
        case Right(v)  => v
        case Left(err) => throw err
      }
  )

  /** Writes an `OffsetTime` as an ISO-8601 BSON `STRING`. */
  val offsetTime: BsonCodec[OffsetTime] = stringBased[OffsetTime](
    Json.offsetTimeRawCodec.encodeToString,
    x =>
      Json.offsetTimeRawCodec.decode(x) match {
        case Right(v)  => v
        case Left(err) => throw err
      }
  )

  /** Writes a `Period` as an ISO-8601 BSON `STRING`, such as `P1M`. */
  val period: BsonCodec[Period] = stringBased[Period](
    Json.periodRawCodec.encodeToString,
    x =>
      Json.periodRawCodec.decode(x) match {
        case Right(v)  => v
        case Left(err) => throw err
      }
  )

  /** Writes a `Year` as a BSON `STRING`, such as `2026`. */
  val year: BsonCodec[Year] = stringBased[Year](
    _.toString,
    Year.parse
  )

  /** Writes a `YearMonth` as a BSON `STRING`, such as `2026-11`. */
  val yearMonth: BsonCodec[YearMonth] = stringBased[YearMonth](
    _.toString,
    YearMonth.parse
  )

  /** Writes a `ZoneId` as a BSON `STRING`, such as `Europe/Berlin`. */
  val zoneId: BsonCodec[ZoneId] = stringBased[ZoneId](
    _.toString,
    ZoneId.of
  )

  /** Writes a `ZoneOffset` as a BSON `STRING`, such as `+01:00`. */
  val zoneOffset: BsonCodec[ZoneOffset] = stringBased[ZoneOffset](
    _.toString,
    ZoneOffset.of
  )

  /** Writes a `ZonedDateTime` as an ISO-8601 BSON `STRING`. */
  val zonedDateTime: BsonCodec[ZonedDateTime] = stringBased[ZonedDateTime](
    Json.zonedDateTimeRawCodec.encodeToString,
    x =>
      Json.zonedDateTimeRawCodec.decode(x) match {
        case Right(v)  => v
        case Left(err) => throw err
      }
  )

  /**
   * Writes a `Currency` as a BSON `STRING` holding its ISO 4217 code, such as
   * `EUR`.
   */
  val currency: BsonCodec[Currency] = stringBased[Currency](
    _.getCurrencyCode,
    Currency.getInstance
  )

  /**
   * Writes `()` as an empty BSON document. Decoding through `fromBsonValue`
   * accepts any value; through `decode` it reads and skips a document.
   */
  val unit: BsonCodec[Unit] = BsonCodec(
    new BsonEncoder[Unit] {
      def encode(writer: BsonWriter, value: Unit, ctx: BsonEncoder.EncoderContext): Unit = {
        writer.writeStartDocument()
        writer.writeEndDocument()
      }
      def toBsonValue(value: Unit): BsonValue = new BsonDocument()
    },
    new BsonDecoder[Unit] {
      def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): Unit = {
        reader.readStartDocument()
        while (reader.readBsonType() != org.bson.BsonType.END_OF_DOCUMENT) {
          reader.readName()
          reader.skipValue()
        }
        reader.readEndDocument()
      }
      def fromBsonValueUnsafe(
        value: BsonValue,
        trace: List[BsonTrace],
        ctx: BsonDecoder.BsonDecoderContext
      ): Unit = ()
    }
  )

  /**
   * Writes a `BigInt` as a BSON `STRING` of its decimal digits, so values of
   * any size fit.
   */
  val scalaBigInt: BsonCodec[BigInt] = BsonCodec(
    new BsonEncoder[BigInt] {
      def encode(writer: BsonWriter, value: BigInt, ctx: BsonEncoder.EncoderContext): Unit =
        BsonEncoder.bigInteger.encode(writer, value.bigInteger, ctx)
      def toBsonValue(value: BigInt): BsonValue = BsonEncoder.bigInteger.toBsonValue(value.bigInteger)
    },
    new BsonDecoder[BigInt] {
      def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): BigInt =
        BigInt(BsonDecoder.bigInteger.decodeUnsafe(reader, trace, ctx))
      def fromBsonValueUnsafe(
        value: BsonValue,
        trace: List[BsonTrace],
        ctx: BsonDecoder.BsonDecoderContext
      ): BigInt = BigInt(BsonDecoder.bigInteger.fromBsonValueUnsafe(value, trace, ctx))
    }
  )

  /**
   * Writes a `BigDecimal` as BSON `DECIMAL128`; see [[BsonCodec.bigDecimal]]
   * for the limits.
   */
  val scalaBigDecimal: BsonCodec[BigDecimal] = BsonCodec(
    new BsonEncoder[BigDecimal] {
      def encode(writer: BsonWriter, value: BigDecimal, ctx: BsonEncoder.EncoderContext): Unit =
        bigDecimal.encoder.encode(writer, value.bigDecimal, ctx)
      def toBsonValue(value: BigDecimal): BsonValue = bigDecimal.encoder.toBsonValue(value.bigDecimal)
    },
    new BsonDecoder[BigDecimal] {
      def decodeUnsafe(reader: BsonReader, trace: List[BsonTrace], ctx: BsonDecoder.BsonDecoderContext): BigDecimal =
        BigDecimal(bigDecimal.decoder.decodeUnsafe(reader, trace, ctx))
      def fromBsonValueUnsafe(
        value: BsonValue,
        trace: List[BsonTrace],
        ctx: BsonDecoder.BsonDecoderContext
      ): BigDecimal = BigDecimal(bigDecimal.decoder.fromBsonValueUnsafe(value, trace, ctx))
    }
  )
}

/**
 * Short constructors for BSON values, for building documents by hand, as in
 * tests:
 * `BsonBuilder.doc("sku" -> BsonBuilder.str("mug"), "qty" -> BsonBuilder.int32(2))`.
 */
object BsonBuilder {

  /**
   * Returns a `BsonDocument` with `fields` in order; a repeated key keeps its
   * last value.
   */
  def doc(fields: (String, BsonValue)*): BsonDocument = {
    val document = new BsonDocument()
    fields.foreach { case (key, value) => document.append(key, value) }
    document
  }

  /** Returns a `BsonString` holding `value`. */
  def str(value: String): BsonString = new BsonString(value)

  /** Returns a `BsonInt32` holding `value`. */
  def int32(value: Int): BsonInt32 = new BsonInt32(value)

  /** Returns a `BsonInt64` holding `value`. */
  def int64(value: Long): BsonInt64 = new BsonInt64(value)

  /** Returns a `BsonDouble` holding `value`. */
  def double(value: Double): BsonDouble = new BsonDouble(value)

  /** Returns a `BsonBoolean` holding `value`. */
  def boolean(value: Boolean): BsonBoolean = new BsonBoolean(value)

  /** Returns a `BsonArray` holding `values` in order. */
  def array(values: BsonValue*): BsonArray = new BsonArray(values.asJava)
}
