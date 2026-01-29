package zio.blocks.schema.json

import zio.blocks.schema.{Schema, SchemaError}

import java.time._
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
  def decode(json: Json): Either[SchemaError, A]

  /**
   * Maps the decoded value using the given function.
   */
  def map[B](f: A => B): JsonDecoder[B] = (json: Json) => self.decode(json).map(f)

  /**
   * FlatMaps the decoded value using the given function.
   */
  def flatMap[B](f: A => Either[SchemaError, B]): JsonDecoder[B] = (json: Json) => self.decode(json).flatMap(f)

  /**
   * Returns an alternative decoder that is tried if this decoder fails.
   */
  def orElse(that: => JsonDecoder[A]): JsonDecoder[A] = (json: Json) =>
    self.decode(json) match {
      case _: Left[_, _] => that.decode(json)
      case right         => right
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
  def instance[A](f: Json => Either[SchemaError, A]): JsonDecoder[A] = (json: Json) => f(json)

  // ─────────────────────────────────────────────────────────────────────────
  // Primitive Decoders
  // ─────────────────────────────────────────────────────────────────────────

  implicit val stringDecoder: JsonDecoder[String] = new JsonDecoder[String] {
    def decode(json: Json): Either[SchemaError, String] = json match {
      case str: Json.String => new Right(str.value)
      case _                => new Left(SchemaError("Expected String"))
    }
  }

  implicit val booleanDecoder: JsonDecoder[Boolean] = new JsonDecoder[Boolean] {
    def decode(json: Json): Either[SchemaError, Boolean] = json match {
      case bool: Json.Boolean => new Right(bool.value)
      case _                  => new Left(SchemaError("Expected Boolean"))
    }
  }

  implicit val intDecoder: JsonDecoder[Int] = new JsonDecoder[Int] {
    def decode(json: Json): Either[SchemaError, Int] = json match {
      case num: Json.Number =>
        val n = num.value
        try new Right(n.toInt)
        catch {
          case err if NonFatal(err) => new Left(SchemaError(s"Number $n is not a valid Int"))
        }
      case _ => new Left(SchemaError("Expected Number"))
    }
  }

  implicit val longDecoder: JsonDecoder[Long] = new JsonDecoder[Long] {
    def decode(json: Json): Either[SchemaError, Long] = json match {
      case num: Json.Number =>
        val n = num.value
        try new Right(n.toLong)
        catch {
          case err if NonFatal(err) => new Left(SchemaError(s"Number $n is not a valid Long"))
        }
      case _ => new Left(SchemaError("Expected Number"))
    }
  }

  implicit val floatDecoder: JsonDecoder[Float] = new JsonDecoder[Float] {
    def decode(json: Json): Either[SchemaError, Float] = {
      json match {
        case num: Json.Number =>
          try return new Right(num.value.toFloat)
          catch {
            case err if NonFatal(err) =>
          }
        case _ =>
      }
      new Left(SchemaError("Expected Number"))
    }
  }

  implicit val doubleDecoder: JsonDecoder[Double] = new JsonDecoder[Double] {
    def decode(json: Json): Either[SchemaError, Double] = {
      json match {
        case num: Json.Number =>
          try return new Right(num.value.toDouble)
          catch {
            case err if NonFatal(err) =>
          }
        case _ =>
      }
      new Left(SchemaError("Expected Number"))
    }
  }

  implicit val bigDecimalDecoder: JsonDecoder[BigDecimal] = new JsonDecoder[BigDecimal] {
    def decode(json: Json): Either[SchemaError, BigDecimal] = {
      json match {
        case num: Json.Number =>
          try return new Right(BigDecimal(num.value))
          catch {
            case err if NonFatal(err) =>
          }
        case _ =>
      }
      new Left(SchemaError("Expected Number"))
    }
  }

  implicit val bigIntDecoder: JsonDecoder[BigInt] = new JsonDecoder[BigInt] {
    def decode(json: Json): Either[SchemaError, BigInt] = json match {
      case num: Json.Number =>
        val n = num.value
        try new Right(BigInt(n))
        catch {
          case err if NonFatal(err) => new Left(SchemaError(s"Number $n is not a valid BigInt"))
        }
      case _ => new Left(SchemaError("Expected Number"))
    }
  }

  implicit val byteDecoder: JsonDecoder[Byte] = new JsonDecoder[Byte] {
    def decode(json: Json): Either[SchemaError, Byte] = json match {
      case num: Json.Number =>
        val n = num.value
        try new Right(n.toByte)
        catch {
          case err if NonFatal(err) => new Left(SchemaError(s"Number $n is not a valid Byte"))
        }
      case _ => new Left(SchemaError("Expected Number"))
    }
  }

  implicit val shortDecoder: JsonDecoder[Short] = new JsonDecoder[Short] {
    def decode(json: Json): Either[SchemaError, Short] = json match {
      case num: Json.Number =>
        val n = num.value
        try new Right(n.toShort)
        catch {
          case err if NonFatal(err) => new Left(SchemaError(s"Number $n is not a valid Short"))
        }
      case _ => new Left(SchemaError("Expected Number"))
    }
  }

  implicit val charDecoder: JsonDecoder[Char] = new JsonDecoder[Char] {
    def decode(json: Json): Either[SchemaError, Char] = json match {
      case str: Json.String =>
        val s = str.value
        if (s.length == 1) new Right(s.charAt(0))
        else new Left(SchemaError(s"String '$s' is not a single character"))
      case _ => new Left(SchemaError("Expected String"))
    }
  }

  implicit val unitDecoder: JsonDecoder[Unit] = new JsonDecoder[Unit] {
    def decode(json: Json): Either[SchemaError, Unit] = json match {
      case _: Json.Null.type => new Right(())
      case _                 => new Left(SchemaError("Expected Null"))
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Json identity decoder
  // ─────────────────────────────────────────────────────────────────────────

  implicit val jsonDecoder: JsonDecoder[Json] = new JsonDecoder[Json] {
    def decode(json: Json): Either[SchemaError, Json] = new Right(json)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Collection Decoders
  // ─────────────────────────────────────────────────────────────────────────

  implicit def optionDecoder[A](implicit decoder: JsonDecoder[A]): JsonDecoder[Option[A]] = new JsonDecoder[Option[A]] {
    def decode(json: Json): Either[SchemaError, Option[A]] = json match {
      case _: Json.Null.type => new Right(None)
      case _                 =>
        decoder.decode(json) match {
          case Right(v)    => new Right(new Some(v))
          case Left(error) => new Left(error.atCase("Some"))
        }
    }
  }

  implicit def vectorDecoder[A](implicit decoder: JsonDecoder[A]): JsonDecoder[Vector[A]] = new JsonDecoder[Vector[A]] {
    def decode(json: Json): Either[SchemaError, Vector[A]] = json match {
      case arr: Json.Array =>
        val builder = Vector.newBuilder[A]
        val elems   = arr.value
        val len     = elems.length
        var idx     = 0
        while (idx < len) {
          decoder.decode(elems(idx)) match {
            case Right(a)    => builder.addOne(a)
            case Left(error) => return new Left(error.atIndex(idx))
          }
          idx += 1
        }
        new Right(builder.result())
      case _ => new Left(SchemaError("Expected Array"))
    }
  }

  implicit def listDecoder[A](implicit decoder: JsonDecoder[A]): JsonDecoder[List[A]] = new JsonDecoder[List[A]] {
    def decode(json: Json): Either[SchemaError, List[A]] = json match {
      case arr: Json.Array =>
        val builder = List.newBuilder[A]
        val elems   = arr.value
        val len     = elems.length
        var idx     = 0
        while (idx < len) {
          decoder.decode(elems(idx)) match {
            case Right(a)    => builder.addOne(a)
            case Left(error) => return new Left(error.atIndex(idx))
          }
          idx += 1
        }
        new Right(builder.result())
      case _ => new Left(SchemaError("Expected Array"))
    }
  }

  implicit def seqDecoder[A](implicit decoder: JsonDecoder[A]): JsonDecoder[Seq[A]] = new JsonDecoder[Seq[A]] {
    def decode(json: Json): Either[SchemaError, Seq[A]] = json match {
      case arr: Json.Array =>
        val builder = Seq.newBuilder[A]
        val elems   = arr.value
        val len     = elems.length
        var idx     = 0
        while (idx < len) {
          decoder.decode(elems(idx)) match {
            case Right(a)    => builder.addOne(a)
            case Left(error) => return new Left(error.atIndex(idx))
          }
          idx += 1
        }
        new Right(builder.result())
      case _ => new Left(SchemaError("Expected Array"))
    }
  }

  implicit def setDecoder[A](implicit decoder: JsonDecoder[A]): JsonDecoder[Set[A]] = new JsonDecoder[Set[A]] {
    def decode(json: Json): Either[SchemaError, Set[A]] = json match {
      case arr: Json.Array =>
        val builder = Set.newBuilder[A]
        val elems   = arr.value
        val len     = elems.length
        var idx     = 0
        while (idx < len) {
          decoder.decode(elems(idx)) match {
            case Right(a)    => builder.addOne(a)
            case Left(error) => return new Left(error.atIndex(idx))
          }
          idx += 1
        }
        new Right(builder.result())
      case _ => new Left(SchemaError("Expected Array"))
    }
  }

  implicit def mapDecoder[V](implicit valueDecoder: JsonDecoder[V]): JsonDecoder[Map[String, V]] =
    new JsonDecoder[Map[String, V]] {
      def decode(json: Json): Either[SchemaError, Map[String, V]] = json match {
        case obj: Json.Object =>
          val builder = Map.newBuilder[String, V]
          val fields  = obj.value
          val len     = fields.length
          var idx     = 0
          while (idx < len) {
            val (key, value) = fields(idx)
            valueDecoder.decode(value) match {
              case Right(a)    => builder.addOne((key, a))
              case Left(error) => return new Left(error.atField(key))
            }
            idx += 1
          }
          new Right(builder.result())
        case _ => new Left(SchemaError("Expected Object"))
      }
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Tuple Decoders
  // ─────────────────────────────────────────────────────────────────────────

  implicit def tuple2Decoder[A, B](implicit
    decoderA: JsonDecoder[A],
    decoderB: JsonDecoder[B]
  ): JsonDecoder[(A, B)] = new JsonDecoder[(A, B)] {
    def decode(json: Json): Either[SchemaError, (A, B)] = json match {
      case arr: Json.Array =>
        val elems = arr.value
        val len   = elems.length
        if (len == 2) {
          val a = decoderA.decode(elems(0)) match {
            case Right(a)    => a
            case Left(error) => return new Left(error.atIndex(0))
          }
          val b = decoderB.decode(elems(1)) match {
            case Right(b)    => b
            case Left(error) => return new Left(error.atIndex(1))
          }
          new Right((a, b))
        } else new Left(SchemaError(s"Expected Array of 2 elements, got $len"))
      case _ => new Left(SchemaError("Expected Array"))
    }
  }

  implicit def tuple3Decoder[A, B, C](implicit
    decoderA: JsonDecoder[A],
    decoderB: JsonDecoder[B],
    decoderC: JsonDecoder[C]
  ): JsonDecoder[(A, B, C)] = new JsonDecoder[(A, B, C)] {
    def decode(json: Json): Either[SchemaError, (A, B, C)] = json match {
      case arr: Json.Array =>
        val elems = arr.value
        val len   = elems.length
        if (len == 3) {
          val a = decoderA.decode(elems(0)) match {
            case Right(a)    => a
            case Left(error) => return new Left(error.atIndex(0))
          }
          val b = decoderB.decode(elems(1)) match {
            case Right(b)    => b
            case Left(error) => return new Left(error.atIndex(1))
          }
          val c = decoderC.decode(elems(2)) match {
            case Right(c)    => c
            case Left(error) => return new Left(error.atIndex(2))
          }
          new Right((a, b, c))
        } else new Left(SchemaError(s"Expected array of 3 elements, got $len"))
      case _ => new Left(SchemaError("Expected Array"))
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Either Decoder
  // ─────────────────────────────────────────────────────────────────────────

  implicit def eitherDecoder[L, R](implicit
    leftDecoder: JsonDecoder[L],
    rightDecoder: JsonDecoder[R]
  ): JsonDecoder[Either[L, R]] = new JsonDecoder[Either[L, R]] {
    def decode(json: Json): Either[SchemaError, Either[L, R]] = json match {
      case obj: Json.Object =>
        val fields = obj.value
        fields.headOption match {
          case Some(("Left", value)) =>
            new Right(new Left(leftDecoder.decode(value) match {
              case Right(l)    => l
              case Left(error) => return new Left(error.atCase("Left"))
            }))
          case Some(("Right", value)) =>
            new Right(rightDecoder.decode(value) match {
              case Left(error) => return new Left(error.atCase("Right"))
              case r           => r.asInstanceOf[Either[L, R]]
            })
          case _ => new Left(SchemaError("Expected Object with 'Left' or 'Right' key"))
        }
      case _ => new Left(SchemaError("Expected Object"))
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Java Time Decoders
  // ─────────────────────────────────────────────────────────────────────────

  private def parseString[A](name: String)(parse: String => A): JsonDecoder[A] = new JsonDecoder[A] {
    def decode(json: Json): Either[SchemaError, A] = json match {
      case str: Json.String =>
        val s = str.value
        try new Right(parse(s))
        catch {
          case err if NonFatal(err) => new Left(SchemaError(s"Invalid $name: ${err.getMessage}"))
        }
      case _ => new Left(SchemaError(s"Expected String for $name"))
    }
  }

  implicit val dayOfWeekDecoder: JsonDecoder[DayOfWeek] = parseString("DayOfWeek")(DayOfWeek.valueOf)

  implicit val durationDecoder: JsonDecoder[Duration] = parseString("Duration")(Duration.parse)

  implicit val instantDecoder: JsonDecoder[Instant] = parseString("Instant")(Instant.parse)

  implicit val localDateDecoder: JsonDecoder[LocalDate] = parseString("LocalDate")(LocalDate.parse)

  implicit val localTimeDecoder: JsonDecoder[LocalTime] = parseString("LocalTime")(LocalTime.parse)

  implicit val localDateTimeDecoder: JsonDecoder[LocalDateTime] = parseString("LocalDateTime")(LocalDateTime.parse)

  implicit val monthDecoder: JsonDecoder[Month] = parseString("Month")(Month.valueOf)

  implicit val monthDayDecoder: JsonDecoder[MonthDay] = parseString("MonthDay")(MonthDay.parse)

  implicit val offsetDateTimeDecoder: JsonDecoder[OffsetDateTime] = parseString("OffsetDateTime")(OffsetDateTime.parse)

  implicit val offsetTimeDecoder: JsonDecoder[OffsetTime] = parseString("OffsetTime")(OffsetTime.parse)

  implicit val periodDecoder: JsonDecoder[Period] = parseString("Period")(Period.parse)

  implicit val yearDecoder: JsonDecoder[Year] = parseString("Year")(Year.parse)

  implicit val yearMonthDecoder: JsonDecoder[YearMonth] = parseString("YearMonth")(YearMonth.parse)

  implicit val zoneOffsetDecoder: JsonDecoder[ZoneOffset] = parseString("ZoneOffset")(ZoneOffset.of)

  implicit val zoneIdDecoder: JsonDecoder[ZoneId] = parseString("ZoneId")(ZoneId.of)

  implicit val zonedDateTimeDecoder: JsonDecoder[ZonedDateTime] = parseString("ZonedDateTime")(ZonedDateTime.parse)

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
  implicit def fromSchema[A](implicit schema: Schema[A]): JsonDecoder[A] = new JsonDecoder[A] {
    private[this] val codec = schema.derive(JsonBinaryCodecDeriver)

    def decode(json: Json): Either[SchemaError, A] = codec.decode(json.printBytes) match {
      case r: Right[_, _] => r.asInstanceOf[Either[SchemaError, A]]
      case Left(error)    => new Left(error)
    }
  }
}
