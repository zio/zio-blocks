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

package zio.blocks.schema.yaml

import zio.blocks.chunk.Chunk
import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.codec.BinaryCodec
import zio.blocks.schema.json.{Json, JsonCodec}
import java.nio.ByteBuffer
import java.time._
import java.util.{Currency, UUID}
import scala.util.control.NonFatal

/**
 * Converts values of type `A` to and from YAML text. Derive one from a schema
 * with `Schema#derive(YamlFormat)`: it writes a record as a mapping with
 * kebab-case keys, a collection as a sequence, a map as a mapping, and a sealed
 * trait as a mapping with one key, the case name.
 *
 * Encode with `encodeToString` or `encode` (UTF-8 bytes), and decode with
 * `decode`, which returns `Left` with the path of the failing value instead of
 * throwing. `encodeValue` and `decodeValue` work on the [[Yaml]] tree; the text
 * methods always write with `YamlOptions.default`, so to choose
 * [[YamlOptions]], pass the tree from `encodeValue` to `YamlWriter.write`.
 *
 * {{{
 * import zio.blocks.schema._
 *
 * case class Deployment(app: String, replicas: Int, maxSurge: Option[Int])
 * object Deployment {
 *   implicit val schema: Schema[Deployment] = Schema.derived
 * }
 *
 * val codec: YamlCodec[Deployment] = Deployment.schema.derive(YamlFormat)
 *
 * codec.encodeToString(Deployment("checkout", 3, Some(1)))
 * // app: checkout
 * // replicas: 3
 * // max-surge: 1
 *
 * codec.decode("app: search\nreplicas: 2\n") // Right(Deployment(search,2,None))
 * }}}
 *
 * @tparam A
 *   the type this codec handles
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/yaml/ YAML]]
 */
abstract class YamlCodec[A] extends BinaryCodec[A] {

  /**
   * Decodes a value from a [[Yaml]] tree. Throws a [[YamlCodecError]] when the
   * tree does not match `A` (some `java.time` and enum codecs throw their own
   * exception instead); `decode` returns an `Either`.
   *
   * {{{
   * import zio.blocks.schema._
   *
   * case class Limit(requests: Int, perSeconds: Int)
   * object Limit {
   *   implicit val schema: Schema[Limit] = Schema.derived
   * }
   *
   * val tree = Yaml.Mapping.fromStringKeys(
   *   "requests"    -> Yaml.Scalar("100"),
   *   "per-seconds" -> Yaml.Scalar("60")
   * )
   *
   * Limit.schema.derive(YamlFormat).decodeValue(tree) // Limit(100,60)
   * }}}
   */
  def decodeValue(in: Yaml): A

  /**
   * Encodes a value as a [[Yaml]] tree, before any formatting. Pass the tree to
   * `YamlWriter.write` to write it with chosen [[YamlOptions]].
   *
   * {{{
   * import zio.blocks.schema._
   *
   * case class Route(path: String, methods: List[String])
   * object Route {
   *   implicit val schema: Schema[Route] = Schema.derived
   * }
   *
   * val tree: Yaml = Route.schema.derive(YamlFormat).encodeValue(Route("/orders", List("GET", "POST")))
   *
   * YamlWriter.write(tree, YamlOptions.flow) // {path: /orders, methods: [GET, POST]}
   * }}}
   */
  def encodeValue(x: A): Yaml

  /**
   * Decodes a value from the YAML text `s`, as `decode` does, but throws on
   * failure (a [[YamlCodecError]] in most cases) instead of returning `Left`.
   */
  def decodeKey(s: String): A = decodeUnsafe(s)

  /**
   * Returns the YAML text of a value, as `encodeToString` writes it. The
   * `yaml"..."` interpolator uses it for a value spliced into a key or into a
   * quoted string. The text is quoted when the writer would quote it (a
   * `String` codec returns `"true"`, with the quotes, for `true`), and spans
   * several lines for a record.
   */
  def encodeKey(x: A): String = encodeToString(x)

  /**
   * Throws a [[YamlCodecError]] whose path starts with `span`. When `error` is
   * already a `YamlCodecError`, adds `span` to the front of its `spans` and
   * rethrows the same instance; otherwise throws a new one with `error`'s
   * message.
   *
   * @throws zio.blocks.schema.yaml.YamlCodecError
   *   always
   */
  def error(span: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: YamlCodecError =>
      e.spans = new ::(span, e.spans)
      throw e
    case _ => throw new YamlCodecError(new ::(span, Nil), error.getMessage)
  }

  /**
   * Throws a [[YamlCodecError]] whose path starts with `span1` then `span2`,
   * reusing `error` when it is already a `YamlCodecError`, as the one-span
   * overload does.
   *
   * @throws zio.blocks.schema.yaml.YamlCodecError
   *   always
   */
  def error(span1: DynamicOptic.Node, span2: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: YamlCodecError =>
      e.spans = new ::(span1, new ::(span2, e.spans))
      throw e
    case _ => throw new YamlCodecError(new ::(span1, new ::(span2, Nil)), error.getMessage)
  }

  /**
   * Throws a [[YamlCodecError]] with the given message and an empty path.
   *
   * @throws zio.blocks.schema.yaml.YamlCodecError
   *   always
   */
  def error(message: String): Nothing = throw new YamlCodecError(Nil, message)

  /**
   * Decodes a value from the remaining bytes of `input`, read as UTF-8 YAML
   * text; consumes all of them.
   */
  override def decode(input: ByteBuffer): Either[SchemaError, A] = {
    val bytes = new Array[Byte](input.remaining())
    input.get(bytes)
    decode(bytes)
  }

  /**
   * Writes the UTF-8 YAML text of `value` into `output`.
   *
   * @throws java.nio.BufferOverflowException
   *   when the text does not fit in the remaining space of `output`
   */
  override def encode(value: A, output: ByteBuffer): Unit = output.put(encode(value))

  /**
   * Decodes a value from UTF-8 YAML bytes, as `decode` on a `String` does.
   *
   * {{{
   * import zio.blocks.schema._
   *
   * case class Feature(key: String, enabled: Boolean)
   * object Feature {
   *   implicit val schema: Schema[Feature] = Schema.derived
   * }
   *
   * val bytes = "key: dark-mode\nenabled: true\n".getBytes("UTF-8")
   *
   * Feature.schema.derive(YamlFormat).decode(bytes) // Right(Feature(dark-mode,true))
   * }}}
   */
  def decode(input: Array[Byte]): Either[SchemaError, A] =
    try new Right(decodeValue(YamlReader.readFromBytes(input)))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  /**
   * Encodes a value as the UTF-8 bytes of its YAML text, written with
   * `YamlOptions.default`.
   *
   * {{{
   * import zio.blocks.schema._
   *
   * case class Reading(sensor: String, celsius: Double)
   * object Reading {
   *   implicit val schema: Schema[Reading] = Schema.derived
   * }
   *
   * val bytes: Array[Byte] = Reading.schema.derive(YamlFormat).encode(Reading("t-101", 21.5))
   *
   * new String(bytes, "UTF-8")
   * // sensor: t-101
   * // celsius: 21.5
   * }}}
   */
  def encode(value: A): Array[Byte] = YamlWriter.writeToBytes(encodeValue(value))

  /**
   * Reads `input` with `YamlReader` and decodes the tree. Returns `Left` with a
   * [[zio.blocks.schema.SchemaError]] that names the path of the failing value;
   * an exception from the reader or from `decodeValue` is caught and returned
   * the same way.
   *
   * The reader is lenient: it reports few syntax errors and stops at the first
   * line it cannot read, dropping the rest without an error (a known bug), so a
   * malformed document can decode from its first lines or fail with a
   * `Missing required field` error.
   *
   * {{{
   * import zio.blocks.schema._
   *
   * case class Pool(name: String, size: Int)
   * object Pool {
   *   implicit val schema: Schema[Pool] = Schema.derived
   * }
   *
   * val codec = Pool.schema.derive(YamlFormat)
   *
   * codec.decode("name: primary\nsize: 10\n")  // Right(Pool(primary,10))
   * codec.decode("name: primary\nsize: ten\n") // Left(zio.blocks.schema.SchemaError: Invalid int value: ten at: .size)
   * }}}
   */
  def decode(input: String): Either[SchemaError, A] =
    try new Right(decodeValue(YamlReader.read(input)))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  /**
   * Encodes a value as YAML text, written with `YamlOptions.default`. A
   * record's field names are written in kebab-case.
   *
   * {{{
   * import zio.blocks.schema._
   *
   * case class UserProfile(userId: Long, displayName: String, emailVerified: Boolean)
   * object UserProfile {
   *   implicit val schema: Schema[UserProfile] = Schema.derived
   * }
   *
   * UserProfile.schema.derive(YamlFormat).encodeToString(UserProfile(42L, "Ada", emailVerified = true))
   * // user-id: 42
   * // display-name: Ada
   * // email-verified: true
   * }}}
   */
  def encodeToString(value: A): String = YamlWriter.write(encodeValue(value))

  /** Reads and decodes `input`, throwing on failure. */
  private[yaml] def decodeUnsafe(input: String): A = decodeValue(YamlReader.read(input))

  private[this] def toError(error: Throwable): SchemaError = new SchemaError(
    new ::(
      new ExpectationMismatch(
        error match {
          case e: YamlCodecError =>
            var list  = e.spans
            val array = new Array[DynamicOptic.Node](list.size)
            var idx   = 0
            while (list ne Nil) {
              array(idx) = list.head
              idx += 1
              list = list.tail
            }
            new DynamicOptic(Chunk.fromArray(array))
          case _ => DynamicOptic.root
        }, {
          var msg = error.getMessage
          if (msg eq null) msg = s"${error.getClass.getName}: (no message)"
          msg
        }
      ),
      Nil
    )
  )
}

/**
 * The [[YamlCodec]] instances for primitive types, which [[YamlCodecDeriver]]
 * uses for the primitive parts of a schema. The numeric codecs trim the text
 * before parsing it.
 */
object YamlCodec {

  /**
   * Encodes `()` as `null`; decodes `NullValue` or a scalar `null`, `~` or
   * empty text.
   */
  val unitCodec: YamlCodec[Unit] = new YamlCodec[Unit] {
    def decodeValue(yaml: Yaml): Unit = yaml match {
      case Yaml.NullValue                                            => ()
      case Yaml.Scalar(v, _) if v == "null" || v == "~" || v.isEmpty => ()
      case _                                                         => error("Expected null value for Unit")
    }
    def encodeValue(x: Unit): Yaml = Yaml.NullValue
  }

  /**
   * Encodes a `Boolean` as `true` or `false` with the `Bool` tag. Decodes only
   * lowercase `true` and `false`; `True`, which YAML 1.2 allows, fails (a known
   * bug).
   */
  val booleanCodec: YamlCodec[Boolean] = new YamlCodec[Boolean] {
    def decodeValue(yaml: Yaml): Boolean = yaml match {
      case Yaml.Scalar(v, _) =>
        v.trim match {
          case "true"  => true
          case "false" => false
          case _       => error(s"Invalid boolean value: $v")
        }
      case _ => error("Expected scalar for boolean")
    }
    def encodeValue(x: Boolean): Yaml = Yaml.Scalar(x.toString, tag = Some(YamlTag.Bool))
  }
  val byteCodec: YamlCodec[Byte] = new YamlCodec[Byte] {
    def decodeValue(yaml: Yaml): Byte = yaml match {
      case Yaml.Scalar(v, _) =>
        try v.trim.toByte
        catch {
          case _: NumberFormatException => error(s"Invalid byte value: $v")
        }
      case _ => error("Expected scalar for byte")
    }
    def encodeValue(x: Byte): Yaml = Yaml.Scalar(x.toString, tag = Some(YamlTag.Int))
  }
  val shortCodec: YamlCodec[Short] = new YamlCodec[Short] {
    def decodeValue(yaml: Yaml): Short = yaml match {
      case Yaml.Scalar(v, _) =>
        try v.trim.toShort
        catch {
          case _: NumberFormatException => error(s"Invalid short value: $v")
        }
      case _ => error("Expected scalar for short")
    }
    def encodeValue(x: Short): Yaml = Yaml.Scalar(x.toString, tag = Some(YamlTag.Int))
  }
  val intCodec: YamlCodec[Int] = new YamlCodec[Int] {
    def decodeValue(yaml: Yaml): Int = yaml match {
      case Yaml.Scalar(v, _) =>
        try v.trim.toInt
        catch {
          case _: NumberFormatException => error(s"Invalid int value: $v")
        }
      case _ => error("Expected scalar for int")
    }
    def encodeValue(x: Int): Yaml = Yaml.Scalar(x.toString, tag = Some(YamlTag.Int))
  }
  val longCodec: YamlCodec[Long] = new YamlCodec[Long] {
    def decodeValue(yaml: Yaml): Long = yaml match {
      case Yaml.Scalar(v, _) =>
        try v.trim.toLong
        catch {
          case _: NumberFormatException => error(s"Invalid long value: $v")
        }
      case _ => error("Expected scalar for long")
    }
    def encodeValue(x: Long): Yaml = Yaml.Scalar(x.toString, tag = Some(YamlTag.Int))
  }

  /**
   * Encodes a `Float` as JSON does, with the `Float` tag, such as `1.5` or
   * `1.0E10`. NaN and infinite values throw a `JsonCodecError`, and the YAML
   * forms `.nan` and `.inf` do not decode (a known bug).
   */
  val floatCodec: YamlCodec[Float] = new YamlCodec[Float] {
    def decodeValue(yaml: Yaml): Float = yaml match {
      case Yaml.Scalar(v, _) =>
        try v.trim.toFloat
        catch {
          case _: NumberFormatException => error(s"Invalid float value: $v")
        }
      case _ => error("Expected scalar for float")
    }
    def encodeValue(x: Float): Yaml = Yaml.Scalar(JsonCodec.floatCodec.encodeToString(x), tag = new Some(YamlTag.Float))
  }

  /**
   * Encodes a `Double` as JSON does, with the `Float` tag, such as `0.1` or
   * `1.0E10`. NaN and infinite values throw a `JsonCodecError`, and the YAML
   * forms `.nan` and `.inf` do not decode (a known bug).
   */
  val doubleCodec: YamlCodec[Double] = new YamlCodec[Double] {
    def decodeValue(yaml: Yaml): Double = yaml match {
      case Yaml.Scalar(v, _) =>
        try v.trim.toDouble
        catch {
          case _: NumberFormatException => error(s"Invalid double value: $v")
        }
      case _ => error("Expected scalar for double")
    }
    def encodeValue(x: Double): Yaml =
      Yaml.Scalar(JsonCodec.doubleCodec.encodeToString(x), tag = new Some(YamlTag.Float))
  }

  /**
   * Encodes a `Char` as a one-character scalar; decodes only a scalar of
   * exactly one character.
   */
  val charCodec: YamlCodec[Char] = new YamlCodec[Char] {
    def decodeValue(yaml: Yaml): Char = yaml match {
      case Yaml.Scalar(v, _) =>
        if (v.length == 1) v.charAt(0)
        else error(s"Expected single character, got: $v")
      case _ => error("Expected scalar for char")
    }
    def encodeValue(x: Char): Yaml = Yaml.Scalar(x.toString)
  }

  /**
   * Encodes a `String` as an untagged scalar, which the writer quotes when
   * needed. Decodes any scalar's text, and decodes `NullValue` (an empty value,
   * `null` or `~` in the text) as `""` instead of failing (a known bug).
   */
  val stringCodec: YamlCodec[String] = new YamlCodec[String] {
    def decodeValue(yaml: Yaml): String = yaml match {
      case Yaml.Scalar(v, _) => v
      case Yaml.NullValue    => ""
      case _                 => error("Expected scalar for string")
    }
    def encodeValue(x: String): Yaml = Yaml.Scalar(x)
  }

  /**
   * Encodes a `BigInt` as an untagged scalar, which the writer quotes, so other
   * YAML readers see a string (a known bug). Decodes quoted or plain digits.
   */
  val bigIntCodec: YamlCodec[BigInt] = new YamlCodec[BigInt] {
    def decodeValue(yaml: Yaml): BigInt = yaml match {
      case Yaml.Scalar(v, _) =>
        try BigInt(v.trim)
        catch {
          case _: NumberFormatException => error(s"Invalid BigInt value: $v")
        }
      case _ => error("Expected scalar for BigInt")
    }
    def encodeValue(x: BigInt): Yaml = Yaml.Scalar(JsonCodec.bigIntCodec.encodeToString(x))
  }

  /**
   * Encodes a `BigDecimal` as an untagged scalar, which the writer quotes, so
   * other YAML readers see a string (a known bug). Decodes quoted or plain
   * numbers.
   */
  val bigDecimalCodec: YamlCodec[BigDecimal] = new YamlCodec[BigDecimal] {
    def decodeValue(yaml: Yaml): BigDecimal = yaml match {
      case Yaml.Scalar(v, _) =>
        try BigDecimal(v.trim)
        catch {
          case _: NumberFormatException => error(s"Invalid BigDecimal value: $v")
        }
      case _ => error("Expected scalar for BigDecimal")
    }
    def encodeValue(x: BigDecimal): Yaml = Yaml.Scalar(JsonCodec.bigDecimalCodec.encodeToString(x))
  }
  val dayOfWeekCodec: YamlCodec[DayOfWeek] = new YamlCodec[DayOfWeek] {
    def decodeValue(yaml: Yaml): DayOfWeek = yaml match {
      case Yaml.Scalar(v, _) => DayOfWeek.valueOf(v.trim.toUpperCase)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: DayOfWeek): Yaml = Yaml.Scalar(x.toString)
  }

  val durationCodec: YamlCodec[Duration] = new YamlCodec[Duration] {
    def decodeValue(yaml: Yaml): Duration = yaml match {
      case Yaml.Scalar(v, _) => Json.durationRawCodec.decodeUnsafe(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: Duration): Yaml = Yaml.Scalar(Json.durationRawCodec.encodeToString(x))
  }
  val instantCodec: YamlCodec[Instant] = new YamlCodec[Instant] {
    def decodeValue(yaml: Yaml): Instant = yaml match {
      case Yaml.Scalar(v, _) => Json.instantRawCodec.decodeUnsafe(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: Instant): Yaml = Yaml.Scalar(Json.instantRawCodec.encodeToString(x))
  }
  val localDateCodec: YamlCodec[LocalDate] = new YamlCodec[LocalDate] {
    def decodeValue(yaml: Yaml): LocalDate = yaml match {
      case Yaml.Scalar(v, _) => Json.localDateRawCodec.decodeUnsafe(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: LocalDate): Yaml = Yaml.Scalar(Json.localDateRawCodec.encodeToString(x))
  }
  val localDateTimeCodec: YamlCodec[LocalDateTime] = new YamlCodec[LocalDateTime] {
    def decodeValue(yaml: Yaml): LocalDateTime = yaml match {
      case Yaml.Scalar(v, _) => Json.localDateTimeRawCodec.decodeUnsafe(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: LocalDateTime): Yaml = Yaml.Scalar(Json.localDateTimeRawCodec.encodeToString(x))
  }
  val localTimeCodec: YamlCodec[LocalTime] = new YamlCodec[LocalTime] {
    def decodeValue(yaml: Yaml): LocalTime = yaml match {
      case Yaml.Scalar(v, _) => Json.localTimeRawCodec.decodeUnsafe(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: LocalTime): Yaml = Yaml.Scalar(Json.localTimeRawCodec.encodeToString(x))
  }
  val monthCodec: YamlCodec[Month] = new YamlCodec[Month] {
    def decodeValue(yaml: Yaml): Month = yaml match {
      case Yaml.Scalar(v, _) => Month.valueOf(v.trim.toUpperCase)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: Month): Yaml = Yaml.Scalar(x.toString)
  }
  val monthDayCodec: YamlCodec[MonthDay] = new YamlCodec[MonthDay] {
    def decodeValue(yaml: Yaml): MonthDay = yaml match {
      case Yaml.Scalar(v, _) => Json.monthDayRawCodec.decodeUnsafe(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: MonthDay): Yaml = Yaml.Scalar(Json.monthDayRawCodec.encodeToString(x))
  }
  val offsetDateTimeCodec: YamlCodec[OffsetDateTime] = new YamlCodec[OffsetDateTime] {
    def decodeValue(yaml: Yaml): OffsetDateTime = yaml match {
      case Yaml.Scalar(v, _) => Json.offsetDateTimeRawCodec.decodeUnsafe(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: OffsetDateTime): Yaml = Yaml.Scalar(Json.offsetDateTimeRawCodec.encodeToString(x))
  }
  val offsetTimeCodec: YamlCodec[OffsetTime] = new YamlCodec[OffsetTime] {
    def decodeValue(yaml: Yaml): OffsetTime = yaml match {
      case Yaml.Scalar(v, _) => Json.offsetTimeRawCodec.decodeUnsafe(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: OffsetTime): Yaml = Yaml.Scalar(Json.offsetTimeRawCodec.encodeToString(x))
  }
  val periodCodec: YamlCodec[Period] = new YamlCodec[Period] {
    def decodeValue(yaml: Yaml): Period = yaml match {
      case Yaml.Scalar(v, _) => Json.periodRawCodec.decodeUnsafe(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: Period): Yaml = Yaml.Scalar(Json.periodRawCodec.encodeToString(x))
  }
  val yearCodec: YamlCodec[Year] = new YamlCodec[Year] {
    def decodeValue(yaml: Yaml): Year = yaml match {
      case Yaml.Scalar(v, _) => Year.parse(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: Year): Yaml = Yaml.Scalar(x.toString)
  }
  val yearMonthCodec: YamlCodec[YearMonth] = new YamlCodec[YearMonth] {
    def decodeValue(yaml: Yaml): YearMonth = yaml match {
      case Yaml.Scalar(v, _) => YearMonth.parse(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: YearMonth): Yaml = Yaml.Scalar(x.toString)
  }
  val zoneIdCodec: YamlCodec[ZoneId] = new YamlCodec[ZoneId] {
    def decodeValue(yaml: Yaml): ZoneId = yaml match {
      case Yaml.Scalar(v, _) => ZoneId.of(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: ZoneId): Yaml = Yaml.Scalar(x.toString)
  }
  val zoneOffsetCodec: YamlCodec[ZoneOffset] = new YamlCodec[ZoneOffset] {
    def decodeValue(yaml: Yaml): ZoneOffset = yaml match {
      case Yaml.Scalar(v, _) => ZoneOffset.of(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: ZoneOffset): Yaml = Yaml.Scalar(x.toString)
  }
  val zonedDateTimeCodec: YamlCodec[ZonedDateTime] = new YamlCodec[ZonedDateTime] {
    def decodeValue(yaml: Yaml): ZonedDateTime = yaml match {
      case Yaml.Scalar(v, _) => Json.zonedDateTimeRawCodec.decodeUnsafe(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: ZonedDateTime): Yaml = Yaml.Scalar(Json.zonedDateTimeRawCodec.encodeToString(x))
  }
  val currencyCodec: YamlCodec[Currency] = new YamlCodec[Currency] {
    def decodeValue(yaml: Yaml): Currency = yaml match {
      case Yaml.Scalar(v, _) => Currency.getInstance(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: Currency): Yaml = Yaml.Scalar(x.getCurrencyCode)
  }
  val uuidCodec: YamlCodec[UUID] = new YamlCodec[UUID] {
    def decodeValue(yaml: Yaml): UUID = yaml match {
      case Yaml.Scalar(v, _) => UUID.fromString(v.trim)
      case _                 => error("Expected scalar value")
    }

    def encodeValue(x: UUID): Yaml = Yaml.Scalar(x.toString)
  }
}
