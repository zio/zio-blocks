package zio.blocks.schema.json

import zio.blocks.chunk.{Chunk, ChunkBuilder}
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

  implicit val stringEncoder: JsonEncoder[String] = new JsonEncoder[String] {
    def encode(s: String): Json = new Json.String(s)
  }

  implicit val booleanEncoder: JsonEncoder[Boolean] = new JsonEncoder[Boolean] {
    def encode(b: Boolean): Json = Json.Boolean(b)
  }

  implicit val intEncoder: JsonEncoder[Int] = new JsonEncoder[Int] {
    def encode(i: Int): Json = Json.Number(i)
  }

  implicit val longEncoder: JsonEncoder[Long] = new JsonEncoder[Long] {
    def encode(l: Long): Json = Json.Number(l)
  }

  implicit val floatEncoder: JsonEncoder[Float] = new JsonEncoder[Float] {
    def encode(f: Float): Json = Json.Number(f)
  }

  implicit val doubleEncoder: JsonEncoder[Double] = new JsonEncoder[Double] {
    def encode(d: Double): Json = Json.Number(d)
  }

  implicit val bigDecimalEncoder: JsonEncoder[BigDecimal] = new JsonEncoder[BigDecimal] {
    def encode(bd: BigDecimal): Json = Json.Number(bd)
  }

  implicit val bigIntEncoder: JsonEncoder[BigInt] = new JsonEncoder[BigInt] {
    def encode(bi: BigInt): Json = Json.Number(bi)
  }

  implicit val byteEncoder: JsonEncoder[Byte] = new JsonEncoder[Byte] {
    def encode(b: Byte): Json = Json.Number(b)
  }

  implicit val shortEncoder: JsonEncoder[Short] = new JsonEncoder[Short] {
    def encode(s: Short): Json = Json.Number(s)
  }

  implicit val charEncoder: JsonEncoder[Char] = new JsonEncoder[Char] {
    def encode(c: Char): Json = new Json.String(c.toString)
  }

  implicit val unitEncoder: JsonEncoder[Unit] = new JsonEncoder[Unit] {
    def encode(u: Unit): Json = Json.Null
  }

  implicit val nullEncoder: JsonEncoder[Null] = new JsonEncoder[Null] {
    def encode(n: Null): Json = Json.Null
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Json identity encoder
  // ─────────────────────────────────────────────────────────────────────────

  implicit val jsonEncoder: JsonEncoder[Json] = new JsonEncoder[Json] {
    def encode(json: Json): Json = json
  }

  implicit val jsonObjectEncoder: JsonEncoder[Json.Object] = new JsonEncoder[Json.Object] {
    def encode(obj: Json.Object): Json = obj
  }

  implicit val jsonArrayEncoder: JsonEncoder[Json.Array] = new JsonEncoder[Json.Array] {
    def encode(arr: Json.Array): Json = arr
  }

  implicit val jsonStringEncoder: JsonEncoder[Json.String] = new JsonEncoder[Json.String] {
    def encode(str: Json.String): Json = str
  }

  implicit val jsonNumberEncoder: JsonEncoder[Json.Number] = new JsonEncoder[Json.Number] {
    def encode(num: Json.Number): Json = num
  }

  implicit val jsonBooleanEncoder: JsonEncoder[Json.Boolean] = new JsonEncoder[Json.Boolean] {
    def encode(bool: Json.Boolean): Json = bool
  }

  implicit val jsonNullEncoder: JsonEncoder[Json.Null.type] = new JsonEncoder[Json.Null.type] {
    def encode(n: Json.Null.type): Json = n
  }
  // ─────────────────────────────────────────────────────────────────────────
  // Collection Encoders
  // ─────────────────────────────────────────────────────────────────────────

  implicit def optionEncoder[A](implicit encoder: JsonEncoder[A]): JsonEncoder[Option[A]] = new JsonEncoder[Option[A]] {
    def encode(opt: Option[A]): Json = opt match {
      case some: Some[_] => encoder.encode(some.value)
      case _             => Json.Null
    }
  }

  implicit def vectorEncoder[A](implicit encoder: JsonEncoder[A]): JsonEncoder[Vector[A]] = new JsonEncoder[Vector[A]] {
    def encode(vec: Vector[A]): Json = new Json.Array(Chunk.from(vec).map(encoder.encode))
  }

  implicit def listEncoder[A](implicit encoder: JsonEncoder[A]): JsonEncoder[List[A]] = new JsonEncoder[List[A]] {
    def encode(list: List[A]): Json = new Json.Array(Chunk.from(list).map(encoder.encode))
  }

  implicit def seqEncoder[A](implicit encoder: JsonEncoder[A]): JsonEncoder[Seq[A]] = new JsonEncoder[Seq[A]] {
    def encode(seq: Seq[A]): Json = new Json.Array(Chunk.from(seq).map(encoder.encode))
  }

  implicit def setEncoder[A](implicit encoder: JsonEncoder[A]): JsonEncoder[Set[A]] = new JsonEncoder[Set[A]] {
    def encode(set: Set[A]): Json = new Json.Array(Chunk.from(set).map(encoder.encode))
  }

  implicit def mapEncoder[V](implicit valueEncoder: JsonEncoder[V]): JsonEncoder[Map[String, V]] =
    new JsonEncoder[Map[String, V]] {
      def encode(map: Map[String, V]): Json =
        new Json.Object(
          map
            .foldLeft(ChunkBuilder.make[(String, Json)](map.size)) { (acc, kv) =>
              acc.addOne((kv._1, valueEncoder.encode(kv._2)))
            }
            .result()
        )
    }

  implicit def mapWithKeyableKeyEncoder[K, V](implicit
    keyKeyable: Keyable[K],
    valueEncoder: JsonEncoder[V]
  ): JsonEncoder[Map[K, V]] = new JsonEncoder[Map[K, V]] {
    def encode(map: Map[K, V]): Json =
      new Json.Object(
        map
          .foldLeft(ChunkBuilder.make[(String, Json)](map.size)) { (acc, kv) =>
            acc.addOne((keyKeyable.asKey(kv._1), valueEncoder.encode(kv._2)))
          }
          .result()
      )
  }

  implicit def arrayEncoder[A](implicit encoder: JsonEncoder[A]): JsonEncoder[Array[A]] =
    new JsonEncoder[Array[A]] {
      def encode(arr: Array[A]): Json = {
        val len   = arr.length
        val jsons = new Array[Json](len)
        var idx   = 0
        while (idx < len) {
          jsons(idx) = encoder.encode(arr(idx))
          idx += 1
        }
        new Json.Array(Chunk.fromArray(jsons))
      }
    }

  implicit def iterableEncoder[A](implicit encoder: JsonEncoder[A]): JsonEncoder[Iterable[A]] =
    new JsonEncoder[Iterable[A]] {
      def encode(iter: Iterable[A]): Json = new Json.Array(Chunk.from(iter.map(encoder.encode)))
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Tuple Encoders
  // ─────────────────────────────────────────────────────────────────────────

  implicit def tuple2Encoder[A, B](implicit
    encoderA: JsonEncoder[A],
    encoderB: JsonEncoder[B]
  ): JsonEncoder[(A, B)] = new JsonEncoder[(A, B)] {
    def encode(v: (A, B)): Json = new Json.Array(Chunk(encoderA.encode(v._1), encoderB.encode(v._2)))
  }

  implicit def tuple3Encoder[A, B, C](implicit
    encoderA: JsonEncoder[A],
    encoderB: JsonEncoder[B],
    encoderC: JsonEncoder[C]
  ): JsonEncoder[(A, B, C)] = new JsonEncoder[(A, B, C)] {
    def encode(v: (A, B, C)): Json =
      new Json.Array(Chunk(encoderA.encode(v._1), encoderB.encode(v._2), encoderC.encode(v._3)))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Either Encoder
  // ─────────────────────────────────────────────────────────────────────────

  implicit def eitherEncoder[L, R](implicit
    leftEncoder: JsonEncoder[L],
    rightEncoder: JsonEncoder[R]
  ): JsonEncoder[Either[L, R]] = new JsonEncoder[Either[L, R]] {
    def encode(e: Either[L, R]): Json = new Json.Object(Chunk.single(e match {
      case Left(l)  => ("Left", leftEncoder.encode(l))
      case Right(r) => ("Right", rightEncoder.encode(r))
    }))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Java Time Encoders
  // ─────────────────────────────────────────────────────────────────────────

  implicit val dayOfWeekEncoder: JsonEncoder[DayOfWeek] = new JsonEncoder[DayOfWeek] {
    def encode(dow: DayOfWeek): Json = new Json.String(dow.toString)
  }

  implicit val durationEncoder: JsonEncoder[Duration] = new JsonEncoder[Duration] {
    def encode(d: Duration): Json = new Json.String(d.toString)
  }

  implicit val instantEncoder: JsonEncoder[Instant] = new JsonEncoder[Instant] {
    def encode(i: Instant): Json = new Json.String(i.toString)
  }

  implicit val localDateEncoder: JsonEncoder[LocalDate] = new JsonEncoder[LocalDate] {
    def encode(ld: LocalDate): Json = new Json.String(ld.toString)
  }

  implicit val localTimeEncoder: JsonEncoder[LocalTime] = new JsonEncoder[LocalTime] {
    def encode(lt: LocalTime): Json = new Json.String(lt.toString)
  }

  implicit val localDateTimeEncoder: JsonEncoder[LocalDateTime] = new JsonEncoder[LocalDateTime] {
    def encode(ldt: LocalDateTime): Json = new Json.String(ldt.toString)
  }

  implicit val monthEncoder: JsonEncoder[Month] = new JsonEncoder[Month] {
    def encode(m: Month): Json = new Json.String(m.toString)
  }

  implicit val monthDayEncoder: JsonEncoder[MonthDay] = new JsonEncoder[MonthDay] {
    def encode(md: MonthDay): Json = new Json.String(md.toString)
  }

  implicit val offsetDateTimeEncoder: JsonEncoder[OffsetDateTime] = new JsonEncoder[OffsetDateTime] {
    def encode(c: OffsetDateTime): Json = new Json.String(c.toString)
  }

  implicit val offsetTimeEncoder: JsonEncoder[OffsetTime] = new JsonEncoder[OffsetTime] {
    def encode(ot: OffsetTime): Json = new Json.String(ot.toString)
  }

  implicit val periodEncoder: JsonEncoder[Period] = new JsonEncoder[Period] {
    def encode(p: Period): Json = new Json.String(p.toString)
  }

  implicit val yearEncoder: JsonEncoder[Year] = new JsonEncoder[Year] {
    def encode(y: Year): Json = new Json.String(y.toString)
  }

  implicit val yearMonthEncoder: JsonEncoder[YearMonth] = new JsonEncoder[YearMonth] {
    def encode(ym: YearMonth): Json = new Json.String(ym.toString)
  }

  implicit val zoneOffsetEncoder: JsonEncoder[ZoneOffset] = new JsonEncoder[ZoneOffset] {
    def encode(zo: ZoneOffset): Json = new Json.String(zo.toString)
  }

  implicit val zoneIdEncoder: JsonEncoder[ZoneId] = new JsonEncoder[ZoneId] {
    def encode(zi: ZoneId): Json = new Json.String(zi.toString)
  }

  implicit val zonedDateTimeEncoder: JsonEncoder[ZonedDateTime] = new JsonEncoder[ZonedDateTime] {
    def encode(zdt: ZonedDateTime): Json = new Json.String(zdt.toString)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Other Standard Types
  // ─────────────────────────────────────────────────────────────────────────

  implicit val uuidEncoder: JsonEncoder[UUID] = new JsonEncoder[UUID] {
    def encode(u: UUID): Json = new Json.String(u.toString)
  }

  implicit val currencyEncoder: JsonEncoder[Currency] = new JsonEncoder[Currency] {
    def encode(c: Currency): Json = new Json.String(c.toString)
  }

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
  implicit def fromSchema[A](implicit schema: Schema[A]): JsonEncoder[A] = new JsonEncoder[A] {
    private[this] val codec = schema.getInstance(JsonFormat)

    def encode(a: A): Json = Json.jsonCodec.decode(codec.encode(a)) match {
      case Right(json) => json
      case _           => Json.Null
    }
  }
}
