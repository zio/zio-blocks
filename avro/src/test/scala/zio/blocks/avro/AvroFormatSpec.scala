package zio.blocks.avro

import org.apache.avro.generic.{GenericDatumReader, GenericDatumWriter}
import org.apache.avro.io.{DecoderFactory, EncoderFactory}
import zio.blocks.schema.Schema
import zio.test.Assertion._
import zio.test._
import zio.ZIO
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util
import java.util.UUID
import scala.collection.immutable.ArraySeq

object AvroFormatSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("AvroFormatSpec")(
    suite("primitives")(
      test("Unit") {
        roundTrip((), 0)
      },
      test("Boolean") {
        roundTrip(true, 1)
      },
      test("Byte") {
        roundTrip(123: Byte, 2)
      },
      test("Short") {
        roundTrip(12345: Short, 3)
      },
      test("Int") {
        roundTrip(1234567890, 5)
      },
      test("Long") {
        roundTrip(1234567890123456789L, 9)
      },
      test("Float") {
        roundTrip(42.0f, 4)
      },
      test("Double") {
        roundTrip(42.0, 8)
      },
      test("Char") {
        roundTrip('a', 2)
      },
      test("String") {
        roundTrip("Hello", 6)
      },
      test("BigInt") {
        roundTrip(BigInt("9" * 20), 10)
      },
      test("BigDecimal") {
        roundTrip(BigDecimal("9." + "9" * 20 + "E+12345"), 15)
      },
      test("DayOfWeek") {
        roundTrip(java.time.DayOfWeek.WEDNESDAY, 1)
      },
      test("Duration") {
        roundTrip(java.time.Duration.ofNanos(1234567890123456789L), 9)
      },
      test("Instant") {
        roundTrip(java.time.Instant.parse("2025-07-18T08:29:13.121409459Z"), 9)
      },
      test("LocalDate") {
        roundTrip(java.time.LocalDate.parse("2025-07-18"), 4)
      },
      test("LocalDateTime") {
        roundTrip(java.time.LocalDateTime.parse("2025-07-18T08:29:13.121409459"), 11)
      },
      test("LocalTime") {
        roundTrip(java.time.LocalTime.parse("08:29:13.121409459"), 7)
      },
      test("Month") {
        roundTrip(java.time.Month.of(12), 1)
      },
      test("MonthDay") {
        roundTrip(java.time.MonthDay.of(12, 31), 2)
      },
      test("OffsetDateTime") {
        roundTrip(java.time.OffsetDateTime.parse("2025-07-18T08:29:13.121409459-07:00"), 14)
      },
      test("OffsetTime") {
        roundTrip(java.time.OffsetTime.parse("08:29:13.121409459-07:00"), 10)
      },
      test("Period") {
        roundTrip(java.time.Period.of(1, 12, 31), 3)
      },
      test("Year") {
        roundTrip(java.time.Year.of(2025), 2)
      },
      test("YearMonth") {
        roundTrip(java.time.YearMonth.of(2025, 7), 3)
      },
      test("ZoneId") {
        roundTrip(java.time.ZoneId.of("UTC"), 4)
      },
      test("ZoneOffset") {
        roundTrip(java.time.ZoneOffset.ofTotalSeconds(0), 1)
      },
      test("ZonedDateTime") {
        roundTrip(java.time.ZonedDateTime.parse("2025-07-18T08:29:13.121409459+02:00[Europe/Warsaw]"), 27)
      },
      test("Currency") {
        roundTrip(java.util.Currency.getInstance("USD"), 3)
      },
      test("UUID") {
        roundTrip(UUID.randomUUID(), 16)
      }
    ),
    suite("records")(
      test("simple record") {
        roundTrip(Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"), 22)
      },
      test("nested record") {
        roundTrip(
          Record2(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
          ),
          44
        )
      },
      test("recursive record") {
        roundTrip(Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))), 8)
      }
    ),
    suite("sequences")(
      test("primitive values") {
        implicit val arrayOfUnitSchema: Schema[Array[Unit]]       = Schema.derived
        implicit val arrayOfBooleanSchema: Schema[Array[Boolean]] = Schema.derived
        implicit val arrayOfByteSchema: Schema[Array[Byte]]       = Schema.derived
        implicit val arrayOfShortSchema: Schema[Array[Short]]     = Schema.derived
        implicit val arrayOfCharSchema: Schema[Array[Char]]       = Schema.derived

        roundTrip(Array[Unit]((), (), ()), 2) &&
        roundTrip(Array[Boolean](true, false, true), 5) &&
        roundTrip(Array[Byte](1: Byte, 2: Byte, 3: Byte), 5) &&
        roundTrip(Array[Short](1: Short, 2: Short, 3: Short), 5) &&
        roundTrip(Array('1', '2', '3'), 5) &&
        roundTrip(List(1, 2, 3), 5) &&
        roundTrip(ArraySeq(1L, 2L, 3L), 5) &&
        roundTrip(Set(1.0f, 2.0f, 3.0f), 14) &&
        roundTrip(Vector(1.0, 2.0, 3.0), 26) &&
        roundTrip(List("1", "2", "3"), 8) &&
        roundTrip(List(BigInt(1), BigInt(2), BigInt(3)), 8) &&
        roundTrip(List(BigDecimal(1.0), BigDecimal(2.0), BigDecimal(3.0)), 17) &&
        roundTrip(List(java.time.LocalDate.of(2025, 1, 1), java.time.LocalDate.of(2025, 1, 2)), 10) &&
        roundTrip(List(new java.util.UUID(1L, 1L), new java.util.UUID(2L, 2L), new java.util.UUID(3L, 3L)), 50)
      },
      test("complex values") {
        roundTrip(
          List(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
          ),
          46
        )
      },
      test("recursive values") {
        roundTrip(
          List(
            Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))),
            Recursive(4, List(Recursive(5, List(Recursive(6, Nil)))))
          ),
          18
        )
      }
    ),
    suite("maps")(
      test("string keys and primitive values") {
        roundTrip(Map("VVV" -> (), "WWW" -> ()), 10) &&
        roundTrip(Map("VVV" -> true, "WWW" -> false), 12) &&
        roundTrip(Map("VVV" -> (1: Byte), "WWW" -> (2: Byte)), 12) &&
        roundTrip(Map("VVV" -> (1: Short), "WWW" -> (2: Short)), 12) &&
        roundTrip(Map("VVV" -> '1', "WWW" -> '2'), 12) &&
        roundTrip(Map("VVV" -> 1, "WWW" -> 2), 12) &&
        roundTrip(Map("VVV" -> 1L, "WWW" -> 2L), 12) &&
        roundTrip(Map("VVV" -> 1.0f, "WWW" -> 2.0f), 18) &&
        roundTrip(Map("VVV" -> 1.0, "WWW" -> 2.0), 26) &&
        roundTrip(Map("VVV" -> "1", "WWW" -> "2"), 14) &&
        roundTrip(Map("VVV" -> BigInt(1), "WWW" -> BigInt(2)), 14) &&
        roundTrip(Map("VVV" -> BigDecimal(1.0), "WWW" -> BigDecimal(2.0)), 20) &&
        roundTrip(Map("VVV" -> java.time.LocalDate.of(2025, 1, 1), "WWW" -> java.time.LocalDate.of(2025, 1, 2)), 18) &&
        roundTrip(Map("VVV" -> new java.util.UUID(1L, 1L), "WWW" -> new java.util.UUID(2L, 2L)), 42)
      },
      test("string keys and complex values") {
        roundTrip(
          Map(
            "VVV" -> Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            "WWW" -> Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
          ),
          54
        )
      },
      test("string keys and recursive values") {
        roundTrip(
          Map(
            "VVV" -> Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))),
            "WWW" -> Recursive(4, List(Recursive(5, List(Recursive(6, Nil)))))
          ),
          26
        )
      },
      test("non string key map") {
        ZIO.attempt {
          roundTrip(Map(1 -> 1L, 2 -> 2L), 10)
        }.flip
          .map(e => assert(e.getMessage)(equalTo("Expected string keys only")))
      }
    ),
    suite("enums")(
      test("conatant value enum") {
        roundTrip[TrafficLight](TrafficLight.Green, 1) &&
        roundTrip[TrafficLight](TrafficLight.Yellow, 1) &&
        roundTrip[TrafficLight](TrafficLight.Red, 1)
      },
      test("option") {
        roundTrip(Option(42), 2) &&
        roundTrip[Option[Int]](None, 1)
      },
      test("either") {
        roundTrip[Either[String, Int]](Right(42), 2) &&
        roundTrip[Either[String, Int]](Left("VVV"), 5)
      }
    )
  )

  def roundTrip[A: Schema](value: A, expectedLength: Int): TestResult = {
    val schema          = Schema[A]
    val encodedBySchema = encodeToByteArray(out => schema.encode(AvroFormat)(out)(value))
    val avroSchema      = AvroSchemaCodec.toAvroSchema(schema)
    val reader          = new GenericDatumReader[A](avroSchema)
    val datum           = reader.read(null.asInstanceOf[A], DecoderFactory.get().binaryDecoder(encodedBySchema, null))
    val writer          = new GenericDatumWriter[Any](avroSchema)
    val encodedByAvro   = new ByteArrayOutputStream(1024)
    val binaryEncoder   = EncoderFactory.get().directBinaryEncoder(encodedByAvro, null)
    writer.write(datum, binaryEncoder)
    /*
    val valueStr =
      if (value.isInstanceOf[Array[?]]) value.asInstanceOf[Array[?]].toList.toString
      else value.toString
    println(valueStr + "\n" + datum + "\n" + HexUtils.hexDump(encodedBySchema) + "\n" + HexUtils.hexDump(encodedByAvro.toByteArray) + "\n")
     */
    assert(encodedBySchema.length)(equalTo(expectedLength)) &&
    assert(schema.decode(AvroFormat)(toHeapByteBuffer(encodedBySchema)))(isRight(equalTo(value))) &&
    assert(schema.decode(AvroFormat)(toDirectByteBuffer(encodedBySchema)))(isRight(equalTo(value))) &&
    assert(util.Arrays.compare(encodedBySchema, encodedByAvro.toByteArray))(equalTo(0))
  }

  def encodeToByteArray(f: ByteBuffer => Unit): Array[Byte] = {
    val byteBuffer = ByteBuffer.allocate(1024)
    f(byteBuffer)
    util.Arrays.copyOf(byteBuffer.array, byteBuffer.position)
  }

  def toHeapByteBuffer(bs: Array[Byte]): ByteBuffer = ByteBuffer.wrap(bs)

  def toDirectByteBuffer(bs: Array[Byte]): ByteBuffer =
    ByteBuffer.allocateDirect(1024).put(bs).position(0).limit(bs.length)

  case class Record1(
    bl: Boolean,
    b: Byte,
    sh: Short,
    i: Int,
    l: Long,
    f: Float,
    d: Double,
    c: Char,
    s: String
  )

  object Record1 {
    implicit val schema: Schema[Record1] = Schema.derived
  }

  case class Record2(
    r1_1: Record1,
    r1_2: Record1
  )

  object Record2 {
    implicit val schema: Schema[Record2] = Schema.derived
  }

  case class Recursive(i: Int, ln: List[Recursive])

  object Recursive {
    implicit val schema: Schema[Recursive] = Schema.derived
  }

  sealed trait TrafficLight

  object TrafficLight {
    implicit val schema: Schema[TrafficLight] = Schema.derived[TrafficLight]

    case object Red extends TrafficLight

    case object Yellow extends TrafficLight

    case object Green extends TrafficLight
  }

  implicit val eitherSchema: Schema[Either[String, Int]] = Schema.derived[Either[String, Int]]
}
