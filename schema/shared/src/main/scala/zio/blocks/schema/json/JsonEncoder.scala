package zio.blocks.schema.json

import zio.blocks.schema.Schema

import java.time._
import java.util.{Currency, UUID}

/**
 * A typeclass for encoding a value of type `A` into a `Json` value.
 *
 * JsonEncoder instances are used to convert Scala types into JSON values.
 *
 * The priority order for encoders is:
 *   1. Explicitly provided JsonEncoder instances
 *   2. Schema-derived encoders (lower priority via implicit resolution)
 */
trait JsonEncoder[A] { self =>

  /**
   * Encodes a value of type A into a Json value.
   */
  def encode(a: A): Json

  /**
   * Contramaps the encoder using the given function.
   */
  def contramap[B](f: B => A): JsonEncoder[B] = new JsonEncoder[B] {
    def encode(b: B): Json = self.encode(f(b))
  }
}

object JsonEncoder {

  /**
   * Summons a JsonEncoder instance for type A.
   */
  def apply[A](implicit encoder: JsonEncoder[A]): JsonEncoder[A] = encoder

  /**
   * Creates a JsonEncoder from a function.
   */
  def instance[A](f: A => Json): JsonEncoder[A] = new JsonEncoder[A] {
    def encode(a: A): Json = f(a)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Primitive Encoders
  // ─────────────────────────────────────────────────────────────────────────

  implicit val stringEncoder: JsonEncoder[String] = instance(Json.String(_))

  implicit val booleanEncoder: JsonEncoder[Boolean] = instance(Json.Boolean(_))

  implicit val intEncoder: JsonEncoder[Int] = instance(i => Json.Number(BigDecimal(i)))

  implicit val longEncoder: JsonEncoder[Long] = instance(l => Json.Number(BigDecimal(l)))

  implicit val floatEncoder: JsonEncoder[Float] = instance(f => Json.Number(BigDecimal(f.toDouble)))

  implicit val doubleEncoder: JsonEncoder[Double] = instance(d => Json.Number(BigDecimal(d)))

  implicit val bigDecimalEncoder: JsonEncoder[BigDecimal] = instance(Json.Number(_))

  implicit val bigIntEncoder: JsonEncoder[BigInt] = instance(bi => Json.Number(BigDecimal(bi)))

  implicit val byteEncoder: JsonEncoder[Byte] = instance(b => Json.Number(BigDecimal(b.toInt)))

  implicit val shortEncoder: JsonEncoder[Short] = instance(s => Json.Number(BigDecimal(s.toInt)))

  implicit val charEncoder: JsonEncoder[Char] = instance(c => Json.String(c.toString))

  implicit val unitEncoder: JsonEncoder[Unit] = instance(_ => Json.Null)

  // ─────────────────────────────────────────────────────────────────────────
  // Json identity encoder
  // ─────────────────────────────────────────────────────────────────────────

  implicit val jsonEncoder: JsonEncoder[Json] = instance(identity)

  // ─────────────────────────────────────────────────────────────────────────
  // Collection Encoders
  // ─────────────────────────────────────────────────────────────────────────

  implicit def optionEncoder[A](implicit encoder: JsonEncoder[A]): JsonEncoder[Option[A]] = instance {
    case Some(a) => encoder.encode(a)
    case None    => Json.Null
  }

  implicit def vectorEncoder[A](implicit encoder: JsonEncoder[A]): JsonEncoder[Vector[A]] = instance { vec =>
    Json.Array(vec.map(encoder.encode))
  }

  implicit def listEncoder[A](implicit encoder: JsonEncoder[A]): JsonEncoder[List[A]] = instance { list =>
    Json.Array(list.map(encoder.encode).toVector)
  }

  implicit def seqEncoder[A](implicit encoder: JsonEncoder[A]): JsonEncoder[Seq[A]] = instance { seq =>
    Json.Array(seq.map(encoder.encode).toVector)
  }

  implicit def setEncoder[A](implicit encoder: JsonEncoder[A]): JsonEncoder[Set[A]] = instance { set =>
    Json.Array(set.map(encoder.encode).toVector)
  }

  implicit def mapEncoder[V](implicit valueEncoder: JsonEncoder[V]): JsonEncoder[Map[String, V]] = instance { map =>
    Json.Object(map.map { case (k, v) => (k, valueEncoder.encode(v)) }.toVector)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Tuple Encoders
  // ─────────────────────────────────────────────────────────────────────────

  implicit def tuple2Encoder[A, B](implicit
    encoderA: JsonEncoder[A],
    encoderB: JsonEncoder[B]
  ): JsonEncoder[(A, B)] = instance { case (a, b) =>
    Json.Array(Vector(encoderA.encode(a), encoderB.encode(b)))
  }

  implicit def tuple3Encoder[A, B, C](implicit
    encoderA: JsonEncoder[A],
    encoderB: JsonEncoder[B],
    encoderC: JsonEncoder[C]
  ): JsonEncoder[(A, B, C)] = instance { case (a, b, c) =>
    Json.Array(Vector(encoderA.encode(a), encoderB.encode(b), encoderC.encode(c)))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Either Encoder
  // ─────────────────────────────────────────────────────────────────────────

  implicit def eitherEncoder[L, R](implicit
    leftEncoder: JsonEncoder[L],
    rightEncoder: JsonEncoder[R]
  ): JsonEncoder[Either[L, R]] = instance {
    case Left(l)  => Json.Object(Vector(("Left", leftEncoder.encode(l))))
    case Right(r) => Json.Object(Vector(("Right", rightEncoder.encode(r))))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Java Time Encoders
  // ─────────────────────────────────────────────────────────────────────────

  implicit val instantEncoder: JsonEncoder[Instant]               = instance(i => Json.String(i.toString))
  implicit val localDateEncoder: JsonEncoder[LocalDate]           = instance(d => Json.String(d.toString))
  implicit val localTimeEncoder: JsonEncoder[LocalTime]           = instance(t => Json.String(t.toString))
  implicit val localDateTimeEncoder: JsonEncoder[LocalDateTime]   = instance(dt => Json.String(dt.toString))
  implicit val offsetDateTimeEncoder: JsonEncoder[OffsetDateTime] = instance(dt => Json.String(dt.toString))
  implicit val offsetTimeEncoder: JsonEncoder[OffsetTime]         = instance(t => Json.String(t.toString))
  implicit val zonedDateTimeEncoder: JsonEncoder[ZonedDateTime]   = instance(dt => Json.String(dt.toString))
  implicit val durationEncoder: JsonEncoder[Duration]             = instance(d => Json.String(d.toString))
  implicit val periodEncoder: JsonEncoder[Period]                 = instance(p => Json.String(p.toString))
  implicit val yearEncoder: JsonEncoder[Year]                     = instance(y => Json.String(y.toString))
  implicit val yearMonthEncoder: JsonEncoder[YearMonth]           = instance(ym => Json.String(ym.toString))
  implicit val monthDayEncoder: JsonEncoder[MonthDay]             = instance(md => Json.String(md.toString))
  implicit val zoneIdEncoder: JsonEncoder[ZoneId]                 = instance(z => Json.String(z.toString))
  implicit val zoneOffsetEncoder: JsonEncoder[ZoneOffset]         = instance(z => Json.String(z.toString))
  implicit val dayOfWeekEncoder: JsonEncoder[DayOfWeek]           = instance(d => Json.String(d.toString))
  implicit val monthEncoder: JsonEncoder[Month]                   = instance(m => Json.String(m.toString))

  // ─────────────────────────────────────────────────────────────────────────
  // Other Standard Types
  // ─────────────────────────────────────────────────────────────────────────

  implicit val uuidEncoder: JsonEncoder[UUID]         = instance(u => Json.String(u.toString))
  implicit val currencyEncoder: JsonEncoder[Currency] = instance(c => Json.String(c.toString))

  // ─────────────────────────────────────────────────────────────────────────
  // Schema-derived Encoder (lower priority)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Derives a JsonEncoder from a Schema. This has lower priority than explicit
   * JsonEncoder instances due to the implicit parameter.
   *
   * This encoder uses the JsonBinaryCodec derived from the Schema to encode the
   * value to bytes, then parses it back to Json.
   */
  implicit def fromSchema[A](implicit schema: Schema[A]): JsonEncoder[A] = instance { a =>
    val codec = schema.derive(JsonBinaryCodecDeriver)
    val bytes = codec.encode(a)
    // Parse the bytes back to Json - this is safe since we just encoded it
    Json.parse(bytes).getOrElse(Json.Null)
  }
}
