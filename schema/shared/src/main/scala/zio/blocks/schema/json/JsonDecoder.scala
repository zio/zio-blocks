package zio.blocks.schema.json

import zio.blocks.schema.Schema

import java.time.{
  DayOfWeek,
  Duration,
  Instant,
  LocalDate,
  LocalDateTime,
  LocalTime,
  Month,
  MonthDay,
  OffsetDateTime,
  OffsetTime,
  Period,
  Year,
  YearMonth,
  ZonedDateTime,
  ZoneId,
  ZoneOffset
}
import java.util.{Currency, UUID}
import scala.util.control.NonFatal

/**
 * A typeclass for decoding a `Json` value into a value of type `A`.
 *
 * JsonDecoder instances are used by `Json#as[A]` and `JsonSelection#as[A]`
 * methods to decode JSON values into Scala types.
 *
 * The priority order for decoders is:
 *   1. Explicitly provided JsonDecoder instances
 *   2. Schema-derived decoders (lower priority via implicit resolution)
 */
trait JsonDecoder[A] { self =>

  /**
   * Decodes a Json value into a value of type A.
   */
  def decode(json: Json): Either[JsonError, A]

  /**
   * Maps the decoded value using the given function.
   */
  def map[B](f: A => B): JsonDecoder[B] =
    (json: Json) => self.decode(json).map(f)

  /**
   * FlatMaps the decoded value using the given function.
   */
  def flatMap[B](f: A => Either[JsonError, B]): JsonDecoder[B] =
    (json: Json) => self.decode(json).flatMap(f)

  /**
   * Returns an alternative decoder that is tried if this decoder fails.
   */
  def orElse(that: => JsonDecoder[A]): JsonDecoder[A] = (json: Json) =>
    self.decode(json) match {
      case Left(_) => that.decode(json)
      case right   => right
    }
}

object JsonDecoder {

  /**
   * Summons a JsonDecoder instance for type A.
   */
  def apply[A](implicit decoder: JsonDecoder[A]): JsonDecoder[A] = decoder

  /**
   * Creates a JsonDecoder from a function.
   */
  def instance[A](f: Json => Either[JsonError, A]): JsonDecoder[A] =
    (json: Json) => f(json)

  // ─────────────────────────────────────────────────────────────────────────
  // Primitive Decoders
  // ─────────────────────────────────────────────────────────────────────────

  implicit val stringDecoder: JsonDecoder[String] = instance { json =>
    json.stringValue match {
      case Some(s) => Right(s)
      case None    => Left(JsonError("Expected string"))
    }
  }

  implicit val booleanDecoder: JsonDecoder[Boolean] = instance { json =>
    json.booleanValue match {
      case Some(b) => Right(b)
      case None    => Left(JsonError("Expected boolean"))
    }
  }

  implicit val intDecoder: JsonDecoder[Int] = instance { json =>
    json.numberValue match {
      case Some(n) if n.isValidInt => Right(n.toInt)
      case Some(n)                 => Left(JsonError(s"Number $n is not a valid Int"))
      case None                    => Left(JsonError("Expected number"))
    }
  }

  implicit val longDecoder: JsonDecoder[Long] = instance { json =>
    json.numberValue match {
      case Some(n) if n.isValidLong => Right(n.toLong)
      case Some(n)                  => Left(JsonError(s"Number $n is not a valid Long"))
      case None                     => Left(JsonError("Expected number"))
    }
  }

  implicit val floatDecoder: JsonDecoder[Float] = instance { json =>
    json.numberValue match {
      case Some(n) => Right(n.toFloat)
      case None    => Left(JsonError("Expected number"))
    }
  }

  implicit val doubleDecoder: JsonDecoder[Double] = instance { json =>
    json.numberValue match {
      case Some(n) => Right(n.toDouble)
      case None    => Left(JsonError("Expected number"))
    }
  }

  implicit val bigDecimalDecoder: JsonDecoder[BigDecimal] = instance { json =>
    json.numberValue match {
      case Some(n) => Right(n)
      case None    => Left(JsonError("Expected number"))
    }
  }

  implicit val bigIntDecoder: JsonDecoder[BigInt] = instance { json =>
    json.numberValue match {
      case Some(n) if n.isWhole => Right(n.toBigInt)
      case Some(n)              => Left(JsonError(s"Number $n is not a valid BigInt"))
      case None                 => Left(JsonError("Expected number"))
    }
  }

  implicit val byteDecoder: JsonDecoder[Byte] = instance { json =>
    json.numberValue match {
      case Some(n) if n.isValidByte => Right(n.toByte)
      case Some(n)                  => Left(JsonError(s"Number $n is not a valid Byte"))
      case None                     => Left(JsonError("Expected number"))
    }
  }

  implicit val shortDecoder: JsonDecoder[Short] = instance { json =>
    json.numberValue match {
      case Some(n) if n.isValidShort => Right(n.toShort)
      case Some(n)                   => Left(JsonError(s"Number $n is not a valid Short"))
      case None                      => Left(JsonError("Expected number"))
    }
  }

  implicit val charDecoder: JsonDecoder[Char] = instance { json =>
    json.stringValue match {
      case Some(s) if s.length == 1 => Right(s.charAt(0))
      case Some(s)                  => Left(JsonError(s"String '$s' is not a single character"))
      case None                     => Left(JsonError("Expected string"))
    }
  }

  implicit val unitDecoder: JsonDecoder[Unit] = instance { json =>
    if (json.isNull) Right(())
    else Left(JsonError("Expected null"))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Json identity decoder
  // ─────────────────────────────────────────────────────────────────────────

  implicit val jsonDecoder: JsonDecoder[Json] = instance(json => Right(json))

  // ─────────────────────────────────────────────────────────────────────────
  // Collection Decoders
  // ─────────────────────────────────────────────────────────────────────────

  implicit def optionDecoder[A](implicit decoder: JsonDecoder[A]): JsonDecoder[Option[A]] = instance { json =>
    if (json.isNull) Right(None)
    else decoder.decode(json).map(Some(_))
  }

  implicit def vectorDecoder[A](implicit decoder: JsonDecoder[A]): JsonDecoder[Vector[A]] = instance { json =>
    if (!json.isArray) Left(JsonError("Expected array"))
    else {
      val elems = json.elements
      elems.zipWithIndex.foldLeft[Either[JsonError, Vector[A]]](Right(Vector.empty)) {
        case (Right(acc), (elem, idx)) =>
          decoder.decode(elem) match {
            case Right(a)    => Right(acc :+ a)
            case Left(error) => Left(error.atIndex(idx))
          }
        case (left, _) => left
      }
    }
  }

  implicit def listDecoder[A](implicit decoder: JsonDecoder[A]): JsonDecoder[List[A]] =
    vectorDecoder[A].map(_.toList)

  implicit def seqDecoder[A](implicit decoder: JsonDecoder[A]): JsonDecoder[Seq[A]] =
    vectorDecoder[A].map(_.toSeq)

  implicit def setDecoder[A](implicit decoder: JsonDecoder[A]): JsonDecoder[Set[A]] =
    vectorDecoder[A].map(_.toSet)

  implicit def mapDecoder[V](implicit valueDecoder: JsonDecoder[V]): JsonDecoder[Map[String, V]] = instance { json =>
    if (!json.isObject) Left(JsonError("Expected object"))
    else {
      val fields = json.fields
      fields.foldLeft[Either[JsonError, Map[String, V]]](Right(Map.empty)) {
        case (Right(acc), (key, value)) =>
          valueDecoder.decode(value) match {
            case Right(v)    => Right(acc + (key -> v))
            case Left(error) => Left(error.atField(key))
          }
        case (left, _) => left
      }
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Tuple Decoders
  // ─────────────────────────────────────────────────────────────────────────

  implicit def tuple2Decoder[A, B](implicit
    decoderA: JsonDecoder[A],
    decoderB: JsonDecoder[B]
  ): JsonDecoder[(A, B)] = instance { json =>
    if (!json.isArray) Left(JsonError("Expected array"))
    else {
      val elems = json.elements
      if (elems.length == 2)
        for {
          a <- decoderA.decode(elems(0)).left.map(_.atIndex(0))
          b <- decoderB.decode(elems(1)).left.map(_.atIndex(1))
        } yield (a, b)
      else Left(JsonError(s"Expected array of 2 elements, got ${elems.length}"))
    }
  }

  implicit def tuple3Decoder[A, B, C](implicit
    decoderA: JsonDecoder[A],
    decoderB: JsonDecoder[B],
    decoderC: JsonDecoder[C]
  ): JsonDecoder[(A, B, C)] = instance { json =>
    if (!json.isArray) Left(JsonError("Expected array"))
    else {
      val elems = json.elements
      if (elems.length == 3)
        for {
          a <- decoderA.decode(elems(0)).left.map(_.atIndex(0))
          b <- decoderB.decode(elems(1)).left.map(_.atIndex(1))
          c <- decoderC.decode(elems(2)).left.map(_.atIndex(2))
        } yield (a, b, c)
      else Left(JsonError(s"Expected array of 3 elements, got ${elems.length}"))
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Either Decoder
  // ─────────────────────────────────────────────────────────────────────────

  implicit def eitherDecoder[L, R](implicit
    leftDecoder: JsonDecoder[L],
    rightDecoder: JsonDecoder[R]
  ): JsonDecoder[Either[L, R]] = instance { json =>
    if (!json.isObject) Left(JsonError("Expected object"))
    else {
      val fields = json.fields
      fields.headOption match {
        case Some(("Left", value)) =>
          leftDecoder.decode(value).map(scala.Left(_)).left.map(_.atField("Left"))
        case Some(("Right", value)) =>
          rightDecoder.decode(value).map(scala.Right(_)).left.map(_.atField("Right"))
        case _ =>
          Left(JsonError("Expected object with 'Left' or 'Right' key"))
      }
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Java Time Decoders
  // ─────────────────────────────────────────────────────────────────────────

  private def parseString[A](name: String)(parse: String => A): JsonDecoder[A] = instance { json =>
    json.stringValue match {
      case Some(s) =>
        try Right(parse(s))
        catch {
          case NonFatal(e) => Left(JsonError(s"Invalid $name: ${e.getMessage}"))
        }
      case None => Left(JsonError(s"Expected string for $name"))
    }
  }

  implicit val instantDecoder: JsonDecoder[Instant]               = parseString("Instant")(Instant.parse)
  implicit val localDateDecoder: JsonDecoder[LocalDate]           = parseString("LocalDate")(LocalDate.parse)
  implicit val localTimeDecoder: JsonDecoder[LocalTime]           = parseString("LocalTime")(LocalTime.parse)
  implicit val localDateTimeDecoder: JsonDecoder[LocalDateTime]   = parseString("LocalDateTime")(LocalDateTime.parse)
  implicit val offsetDateTimeDecoder: JsonDecoder[OffsetDateTime] = parseString("OffsetDateTime")(OffsetDateTime.parse)
  implicit val offsetTimeDecoder: JsonDecoder[OffsetTime]         = parseString("OffsetTime")(OffsetTime.parse)
  implicit val zonedDateTimeDecoder: JsonDecoder[ZonedDateTime]   = parseString("ZonedDateTime")(ZonedDateTime.parse)
  implicit val durationDecoder: JsonDecoder[Duration]             = parseString("Duration")(Duration.parse)
  implicit val periodDecoder: JsonDecoder[Period]                 = parseString("Period")(Period.parse)
  implicit val yearDecoder: JsonDecoder[Year]                     = parseString("Year")(Year.parse)
  implicit val yearMonthDecoder: JsonDecoder[YearMonth]           = parseString("YearMonth")(YearMonth.parse)
  implicit val monthDayDecoder: JsonDecoder[MonthDay]             = parseString("MonthDay")(MonthDay.parse)
  implicit val zoneIdDecoder: JsonDecoder[ZoneId]                 = parseString("ZoneId")(ZoneId.of)
  implicit val zoneOffsetDecoder: JsonDecoder[ZoneOffset]         = parseString("ZoneOffset")(ZoneOffset.of)
  implicit val dayOfWeekDecoder: JsonDecoder[DayOfWeek]           = parseString("DayOfWeek")(DayOfWeek.valueOf)
  implicit val monthDecoder: JsonDecoder[Month]                   = parseString("Month")(Month.valueOf)

  // ─────────────────────────────────────────────────────────────────────────
  // Other Standard Types
  // ─────────────────────────────────────────────────────────────────────────

  implicit val uuidDecoder: JsonDecoder[UUID]         = parseString("UUID")(UUID.fromString)
  implicit val currencyDecoder: JsonDecoder[Currency] = parseString("Currency")(Currency.getInstance)

  // ─────────────────────────────────────────────────────────────────────────
  // Schema-derived Decoder (lower priority)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Derives a JsonDecoder from a Schema. This has lower priority than explicit
   * JsonDecoder instances due to the implicit parameter.
   *
   * This decoder parses the JSON to bytes, then uses the JsonBinaryCodec
   * derived from the Schema to decode the value.
   */
  implicit def fromSchema[A](implicit schema: Schema[A]): JsonDecoder[A] = instance { json =>
    val bytes = json.encodeToBytes
    val codec = schema.derive(JsonBinaryCodecDeriver)
    codec.decode(bytes) match {
      case Right(value) => Right(value)
      case Left(err)    => Left(JsonError(err.message))
    }
  }
}
