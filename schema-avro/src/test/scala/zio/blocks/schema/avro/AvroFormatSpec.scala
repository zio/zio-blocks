package zio.blocks.schema.avro

import org.apache.avro.{Schema => AvroSchema}
import org.apache.avro.io.{BinaryDecoder, BinaryEncoder}
import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.avro.AvroTestUtils._
import zio.blocks.schema.binding.Binding
import zio.blocks.typeid.{Owner, TypeId}
import zio.test._

import java.time._
import java.util.{Currency, UUID}
import scala.collection.immutable.ArraySeq

object AvroFormatSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("AvroFormatSpec")(
    suite("primitives")(
      test("Unit") {
        avroSchema[Unit]("\"null\"") &&
        roundTrip((), 0)
      },
      test("Boolean") {
        avroSchema[Boolean]("\"boolean\"") &&
        roundTrip(true, 1) &&
        roundTrip(false, 1) &&
        decodeError[Boolean](Array.empty[Byte], "Unexpected end of input at: .")
      },
      test("Byte") {
        val intCodec = Schema[Int].derive(AvroFormat)
        avroSchema[Byte]("\"int\"") &&
        roundTrip(1: Byte, 1) &&
        roundTrip(Byte.MinValue, 2) &&
        roundTrip(Byte.MaxValue, 2) &&
        decodeError[Byte](intCodec.encode(Byte.MinValue - 1), "Expected Byte at: .") &&
        decodeError[Byte](intCodec.encode(Byte.MaxValue + 1), "Expected Byte at: .") &&
        decodeError[Byte](Array.empty[Byte], "Unexpected end of input at: .")
      },
      test("Short") {
        val intCodec = Schema[Int].derive(AvroFormat)
        avroSchema[Short]("\"int\"") &&
        roundTrip(1: Short, 1) &&
        roundTrip(Short.MinValue, 3) &&
        roundTrip(Short.MaxValue, 3) &&
        decodeError[Short](intCodec.encode(Short.MinValue - 1), "Expected Short at: .") &&
        decodeError[Short](intCodec.encode(Short.MaxValue + 1), "Expected Short at: .") &&
        decodeError[Short](Array.empty[Byte], "Unexpected end of input at: .")
      },
      test("Int") {
        val intCodec = Schema[Int].derive(AvroFormat)
        val bytes    = intCodec.encode(Int.MaxValue)
        bytes(4) = 0xff.toByte
        avroSchema[Int]("\"int\"") &&
        roundTrip(1, 1) &&
        roundTrip(Int.MinValue, 5) &&
        roundTrip(Int.MaxValue, 5) &&
        decodeError(bytes, intCodec, "Invalid int encoding at: .") &&
        decodeError(Array.empty[Byte], intCodec, "Unexpected end of input at: .")
      },
      test("Long") {
        val longCodec = Schema[Long].derive(AvroFormat)
        val bytes     = longCodec.encode(Long.MaxValue)
        bytes(9) = 0xff.toByte
        avroSchema[Long]("\"long\"") &&
        roundTrip(1L, 1) &&
        roundTrip(Long.MinValue, 10) &&
        roundTrip(Long.MaxValue, 10) &&
        decodeError(bytes, longCodec, "Invalid long encoding at: .") &&
        decodeError(Array.empty[Byte], longCodec, "Unexpected end of input at: .")
      },
      test("Float") {
        avroSchema[Float]("\"float\"") &&
        roundTrip(42.0f, 4) &&
        roundTrip(Float.MinValue, 4) &&
        roundTrip(Float.MaxValue, 4) &&
        decodeError[Float](new Array[Byte](3), "Unexpected end of input at: .")
      },
      test("Double") {
        avroSchema[Double]("\"double\"") &&
        roundTrip(42.0, 8) &&
        roundTrip(Double.MinValue, 8) &&
        roundTrip(Double.MaxValue, 8) &&
        decodeError[Double](new Array[Byte](7), "Unexpected end of input at: .")
      },
      test("Char") {
        val intCodec = Schema[Int].derive(AvroFormat)
        avroSchema[Char]("\"int\"") &&
        roundTrip('7', 1) &&
        roundTrip(Char.MinValue, 1) &&
        roundTrip(Char.MaxValue, 3) &&
        decodeError[Char](intCodec.encode(Char.MinValue - 1), "Expected Char at: .") &&
        decodeError[Char](intCodec.encode(Char.MaxValue + 1), "Expected Char at: .")
      },
      test("String") {
        avroSchema[String]("\"string\"") &&
        roundTrip("Hello", 6) &&
        roundTrip("★\uD83C\uDFB8\uD83C\uDFA7⋆｡ °⋆", 24) &&
        decodeError[String](Array.empty[Byte], "Unexpected end of input at: .") &&
        decodeError[String](Array[Byte](100, 42, 42, 42), "Unexpected end of input at: .")
      },
      test("BigInt") {
        avroSchema[BigInt]("\"bytes\"") &&
        roundTrip(BigInt("9" * 20), 10)
      },
      test("BigDecimal") {
        avroSchema[BigDecimal](
          "{\"type\":\"record\",\"name\":\"BigDecimal\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"mantissa\",\"type\":\"bytes\"},{\"name\":\"scale\",\"type\":\"int\"},{\"name\":\"precision\",\"type\":\"int\"},{\"name\":\"roundingMode\",\"type\":\"int\"}]}"
        ) &&
        roundTrip(BigDecimal("9." + "9" * 20 + "E+12345"), 15)
      },
      test("DayOfWeek") {
        avroSchema[java.time.DayOfWeek]("\"int\"") &&
        roundTrip(java.time.DayOfWeek.WEDNESDAY, 1)
      },
      test("Duration") {
        avroSchema[java.time.Duration](
          "{\"type\":\"record\",\"name\":\"Duration\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"seconds\",\"type\":\"long\"},{\"name\":\"nano\",\"type\":\"int\"}]}"
        ) &&
        roundTrip(java.time.Duration.ofNanos(1234567890123456789L), 9)
      },
      test("Instant") {
        avroSchema[java.time.Instant](
          "{\"type\":\"record\",\"name\":\"Instant\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"epochSecond\",\"type\":\"long\"},{\"name\":\"nano\",\"type\":\"int\"}]}"
        ) &&
        roundTrip(java.time.Instant.parse("2025-07-18T08:29:13.121409459Z"), 9)
      },
      test("LocalDate") {
        avroSchema[java.time.LocalDate](
          "{\"type\":\"record\",\"name\":\"LocalDate\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"}]}"
        ) &&
        roundTrip(java.time.LocalDate.parse("2025-07-18"), 4)
      },
      test("LocalDateTime") {
        avroSchema[java.time.LocalDateTime](
          "{\"type\":\"record\",\"name\":\"LocalDateTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"},{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"}]}"
        ) &&
        roundTrip(java.time.LocalDateTime.parse("2025-07-18T08:29:13.121409459"), 11)
      },
      test("LocalTime") {
        avroSchema[java.time.LocalTime](
          "{\"type\":\"record\",\"name\":\"LocalTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"}]}"
        ) &&
        roundTrip(java.time.LocalTime.parse("08:29:13.121409459"), 7)
      },
      test("Month") {
        avroSchema[java.time.Month]("\"int\"") &&
        roundTrip(java.time.Month.of(12), 1)
      },
      test("MonthDay") {
        avroSchema[java.time.MonthDay](
          "{\"type\":\"record\",\"name\":\"MonthDay\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"}]}"
        ) &&
        roundTrip(java.time.MonthDay.of(12, 31), 2)
      },
      test("OffsetDateTime") {
        avroSchema[java.time.OffsetDateTime](
          "{\"type\":\"record\",\"name\":\"OffsetDateTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"},{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"},{\"name\":\"offset\",\"type\":\"int\"}]}"
        ) &&
        roundTrip(java.time.OffsetDateTime.parse("2025-07-18T08:29:13.121409459-07:00"), 14)
      },
      test("OffsetTime") {
        avroSchema[java.time.OffsetTime](
          "{\"type\":\"record\",\"name\":\"OffsetTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"},{\"name\":\"offset\",\"type\":\"int\"}]}"
        ) &&
        roundTrip(java.time.OffsetTime.parse("08:29:13.121409459-07:00"), 10)
      },
      test("Period") {
        avroSchema[java.time.Period](
          "{\"type\":\"record\",\"name\":\"Period\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"years\",\"type\":\"int\"},{\"name\":\"months\",\"type\":\"int\"},{\"name\":\"days\",\"type\":\"int\"}]}"
        ) &&
        roundTrip(java.time.Period.of(1, 12, 31), 3)
      },
      test("Year") {
        avroSchema[java.time.Year]("\"int\"") &&
        roundTrip(java.time.Year.of(2025), 2)
      },
      test("YearMonth") {
        avroSchema[java.time.YearMonth](
          "{\"type\":\"record\",\"name\":\"YearMonth\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"}]}"
        ) &&
        roundTrip(java.time.YearMonth.of(2025, 7), 3)
      },
      test("ZoneId") {
        avroSchema[java.time.ZoneId]("\"string\"") &&
        roundTrip(java.time.ZoneId.of("UTC"), 4)
      },
      test("ZoneOffset") {
        avroSchema[java.time.ZoneOffset]("\"int\"") &&
        roundTrip(java.time.ZoneOffset.ofTotalSeconds(3600), 2)
      },
      test("ZonedDateTime") {
        avroSchema[java.time.ZonedDateTime](
          "{\"type\":\"record\",\"name\":\"ZonedDateTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"},{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"},{\"name\":\"offset\",\"type\":\"int\"},{\"name\":\"zone\",\"type\":\"string\"}]}"
        ) &&
        roundTrip(java.time.ZonedDateTime.parse("2025-07-18T08:29:13.121409459+02:00[Europe/Warsaw]"), 27)
      },
      test("Currency") {
        avroSchema[Currency](
          "{\"type\":\"fixed\",\"name\":\"Currency\",\"namespace\":\"java.util\",\"size\":3}"
        ) &&
        roundTrip(Currency.getInstance("USD"), 3)
      },
      test("UUID") {
        avroSchema[UUID](
          "{\"type\":\"fixed\",\"name\":\"UUID\",\"namespace\":\"java.util\",\"size\":16}"
        ) &&
        roundTrip(UUID.randomUUID(), 16)
      }
    ),
    suite("records")(
      test("simple record") {
        avroSchema[Record1](
          "{\"type\":\"record\",\"name\":\"Record1\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"bl\",\"type\":\"boolean\"},{\"name\":\"b\",\"type\":\"int\"},{\"name\":\"sh\",\"type\":\"int\"},{\"name\":\"i\",\"type\":\"int\"},{\"name\":\"l\",\"type\":\"long\"},{\"name\":\"f\",\"type\":\"float\"},{\"name\":\"d\",\"type\":\"double\"},{\"name\":\"c\",\"type\":\"int\"},{\"name\":\"s\",\"type\":\"string\"}]}"
        ) &&
        roundTrip(Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"), 22) &&
        decodeError[Record1](Array.empty[Byte], "Unexpected end of input at: .bl") &&
        decodeError[Record1](Array[Byte](100, 42, 42, 42), "Unexpected end of input at: .l")
      },
      test("nested record") {
        avroSchema[Record2](
          "{\"type\":\"record\",\"name\":\"Record2\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"r1_1\",\"type\":{\"type\":\"record\",\"name\":\"Record1\",\"fields\":[{\"name\":\"bl\",\"type\":\"boolean\"},{\"name\":\"b\",\"type\":\"int\"},{\"name\":\"sh\",\"type\":\"int\"},{\"name\":\"i\",\"type\":\"int\"},{\"name\":\"l\",\"type\":\"long\"},{\"name\":\"f\",\"type\":\"float\"},{\"name\":\"d\",\"type\":\"double\"},{\"name\":\"c\",\"type\":\"int\"},{\"name\":\"s\",\"type\":\"string\"}]}},{\"name\":\"r1_2\",\"type\":{\"type\":\"record\",\"name\":\"Record1_1\",\"fields\":[{\"name\":\"bl\",\"type\":\"boolean\"},{\"name\":\"b\",\"type\":\"int\"},{\"name\":\"sh\",\"type\":\"int\"},{\"name\":\"i\",\"type\":\"int\"},{\"name\":\"l\",\"type\":\"long\"},{\"name\":\"f\",\"type\":\"float\"},{\"name\":\"d\",\"type\":\"double\"},{\"name\":\"c\",\"type\":\"int\"},{\"name\":\"s\",\"type\":\"string\"}]}}]}"
        ) &&
        roundTrip(
          Record2(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
          ),
          44
        )
      },
      test("recursive record") {
        avroSchema[Recursive](
          "{\"type\":\"record\",\"name\":\"Recursive\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"i\",\"type\":\"int\"},{\"name\":\"ln\",\"type\":{\"type\":\"array\",\"items\":\"Recursive\"}}]}"
        ) &&
        roundTrip(Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))), 8)
      },
      test("record with unit and variant fields") {
        avroSchema[Record4](
          "{\"type\":\"record\",\"name\":\"Record4\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"hidden\",\"type\":\"null\"},{\"name\":\"optKey\",\"type\":[{\"type\":\"record\",\"name\":\"None\",\"namespace\":\"scala\",\"fields\":[]},{\"type\":\"record\",\"name\":\"Some\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"value\",\"type\":\"string\"}]}]}]}"
        ) &&
        roundTrip(Record4((), Some("VVV")), 5) &&
        roundTrip(Record4((), None), 1)
      },
      test("record with a custom codec for primitives injected by optic") {
        val codec = Record1.schema
          .deriving(AvroFormat.deriver)
          .instance(
            Record1.i,
            new AvroBinaryCodec[Int](AvroBinaryCodec.intType) {
              val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.STRING)

              def decodeUnsafe(decoder: BinaryDecoder): Int = java.lang.Integer.valueOf(decoder.readString())

              def encode(value: Int, encoder: BinaryEncoder): Unit = encoder.writeString(value.toString)
            }
          )
          .derive
        avroSchema(
          "{\"type\":\"record\",\"name\":\"Record1\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"bl\",\"type\":\"boolean\"},{\"name\":\"b\",\"type\":\"int\"},{\"name\":\"sh\",\"type\":\"int\"},{\"name\":\"i\",\"type\":\"string\"},{\"name\":\"l\",\"type\":\"long\"},{\"name\":\"f\",\"type\":\"float\"},{\"name\":\"d\",\"type\":\"double\"},{\"name\":\"c\",\"type\":\"int\"},{\"name\":\"s\",\"type\":\"string\"}]}",
          codec
        ) &&
        roundTrip(Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"), 23, codec)
      },
      test("record with a custom codec for primitives injected by type name") {
        val codec = Record1.schema
          .deriving(AvroFormat.deriver)
          .instance(
            TypeId.int,
            new AvroBinaryCodec[Int](AvroBinaryCodec.intType) {
              val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.STRING)

              def decodeUnsafe(decoder: BinaryDecoder): Int = java.lang.Integer.valueOf(decoder.readString())

              def encode(value: Int, encoder: BinaryEncoder): Unit = encoder.writeString(value.toString)
            }
          )
          .derive
        avroSchema(
          "{\"type\":\"record\",\"name\":\"Record1\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"bl\",\"type\":\"boolean\"},{\"name\":\"b\",\"type\":\"int\"},{\"name\":\"sh\",\"type\":\"int\"},{\"name\":\"i\",\"type\":\"string\"},{\"name\":\"l\",\"type\":\"long\"},{\"name\":\"f\",\"type\":\"float\"},{\"name\":\"d\",\"type\":\"double\"},{\"name\":\"c\",\"type\":\"int\"},{\"name\":\"s\",\"type\":\"string\"}]}",
          codec
        ) &&
        roundTrip(Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"), 23, codec)
      },
      test("record with a custom codec for unit injected by optic") {
        val codec = Record4.schema
          .deriving(AvroFormat.deriver)
          .instance(
            Record4.hidden,
            new AvroBinaryCodec[Unit](AvroBinaryCodec.unitType) {
              val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.STRING)

              def decodeUnsafe(decoder: BinaryDecoder): Unit = decoder.readString()

              def encode(value: Unit, encoder: BinaryEncoder): Unit = encoder.writeString("WWW")
            }
          )
          .derive
        avroSchema(
          "{\"type\":\"record\",\"name\":\"Record4\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"hidden\",\"type\":\"string\"},{\"name\":\"optKey\",\"type\":[{\"type\":\"record\",\"name\":\"None\",\"namespace\":\"scala\",\"fields\":[]},{\"type\":\"record\",\"name\":\"Some\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"value\",\"type\":\"string\"}]}]}]}",
          codec
        ) &&
        roundTrip(Record4((), Some("VVV")), 9, codec)
      },
      test("record with a custom codec for None injected by optic") {
        val codec = Record4.schema
          .deriving(AvroFormat.deriver)
          .instance(
            Record4.optKey_None,
            new AvroBinaryCodec[None.type](AvroBinaryCodec.unitType) {
              val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.STRING)

              def decodeUnsafe(decoder: BinaryDecoder): None.type = {
                val _ = decoder.readString()
                None
              }

              def encode(value: None.type, encoder: BinaryEncoder): Unit = encoder.writeString("WWW")
            }
          )
          .derive
        avroSchema(
          "{\"type\":\"record\",\"name\":\"Record4\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"hidden\",\"type\":\"null\"},{\"name\":\"optKey\",\"type\":[\"string\",{\"type\":\"record\",\"name\":\"Some\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"value\",\"type\":\"string\"}]}]}]}",
          codec
        ) &&
        roundTrip(Record4((), Some("VVV")), 5, codec) &&
        roundTrip(Record4((), None), 5, codec)
      },
      test("record with a custom codec for nested record injected by optic") {
        val codec1 = new AvroBinaryCodec[Record1]() {
          private val codec = Record1.schema.derive(AvroFormat)

          val avroSchema: AvroSchema =
            AvroSchema.createUnion(AvroSchema.create(AvroSchema.Type.NULL), codec.avroSchema)

          def decodeUnsafe(decoder: BinaryDecoder): Record1 = {
            val idx = decoder.readInt()
            if (idx == 0) null
            else codec.decodeUnsafe(decoder)
          }

          def encode(value: Record1, encoder: BinaryEncoder): Unit =
            if (value eq null) encoder.writeInt(0)
            else {
              encoder.writeInt(1)
              codec.encode(value, encoder)
            }
        }
        val codec2 = Record2.schema
          .deriving(AvroFormat.deriver)
          .instance(Record2.r1_1, codec1)
          .instance(Record2.r1_2, codec1)
          .derive
        avroSchema(
          "{\"type\":\"record\",\"name\":\"Record2\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"r1_1\",\"type\":[\"null\",{\"type\":\"record\",\"name\":\"Record1\",\"fields\":[{\"name\":\"bl\",\"type\":\"boolean\"},{\"name\":\"b\",\"type\":\"int\"},{\"name\":\"sh\",\"type\":\"int\"},{\"name\":\"i\",\"type\":\"int\"},{\"name\":\"l\",\"type\":\"long\"},{\"name\":\"f\",\"type\":\"float\"},{\"name\":\"d\",\"type\":\"double\"},{\"name\":\"c\",\"type\":\"int\"},{\"name\":\"s\",\"type\":\"string\"}]}]},{\"name\":\"r1_2\",\"type\":[\"null\",\"Record1\"]}]}",
          codec2
        ) &&
        roundTrip(
          Record2(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(false, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "WWW")
          ),
          46,
          codec2
        ) &&
        roundTrip(Record2(null, null), 2, codec2)
      },
      test("record with a custom codec for nested primitives injected by optic") {
        val codec = Record2.schema
          .deriving(AvroFormat.deriver)
          .instance(
            TypeId.int,
            new AvroBinaryCodec[Int](AvroBinaryCodec.intType) {
              val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.STRING)

              def decodeUnsafe(decoder: BinaryDecoder): Int = java.lang.Integer.valueOf(decoder.readString())

              def encode(value: Int, encoder: BinaryEncoder): Unit = encoder.writeString(value.toString)
            }
          )
          .instance(
            Record2.r1_2_i,
            new AvroBinaryCodec[Int](AvroBinaryCodec.intType) {
              val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.DOUBLE)

              def decodeUnsafe(decoder: BinaryDecoder): Int = decoder.readDouble().toInt

              def encode(value: Int, encoder: BinaryEncoder): Unit = encoder.writeDouble(value.toDouble)
            }
          )
          .derive
        avroSchema(
          "{\"type\":\"record\",\"name\":\"Record2\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"r1_1\",\"type\":{\"type\":\"record\",\"name\":\"Record1\",\"fields\":[{\"name\":\"bl\",\"type\":\"boolean\"},{\"name\":\"b\",\"type\":\"int\"},{\"name\":\"sh\",\"type\":\"int\"},{\"name\":\"i\",\"type\":\"string\"},{\"name\":\"l\",\"type\":\"long\"},{\"name\":\"f\",\"type\":\"float\"},{\"name\":\"d\",\"type\":\"double\"},{\"name\":\"c\",\"type\":\"int\"},{\"name\":\"s\",\"type\":\"string\"}]}},{\"name\":\"r1_2\",\"type\":{\"type\":\"record\",\"name\":\"Record1_1\",\"fields\":[{\"name\":\"bl\",\"type\":\"boolean\"},{\"name\":\"b\",\"type\":\"int\"},{\"name\":\"sh\",\"type\":\"int\"},{\"name\":\"i\",\"type\":\"double\"},{\"name\":\"l\",\"type\":\"long\"},{\"name\":\"f\",\"type\":\"float\"},{\"name\":\"d\",\"type\":\"double\"},{\"name\":\"c\",\"type\":\"int\"},{\"name\":\"s\",\"type\":\"string\"}]}}]}",
          codec
        ) &&
        roundTrip(
          Record2(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(false, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "WWW")
          ),
          52,
          codec
        )
      },
      test("record with a custom codec for nested record injected by type name") {
        val codec = Record2.schema
          .deriving(AvroFormat.deriver)
          .instance(
            Record1.schema.reflect.typeId,
            new AvroBinaryCodec[Record1]() {
              private val codec = Record1.schema.derive(AvroFormat)

              val avroSchema: AvroSchema =
                AvroSchema.createUnion(AvroSchema.create(AvroSchema.Type.NULL), codec.avroSchema)

              def decodeUnsafe(decoder: BinaryDecoder): Record1 = {
                val idx = decoder.readInt()
                if (idx == 0) null
                else codec.decodeUnsafe(decoder)
              }

              def encode(value: Record1, encoder: BinaryEncoder): Unit =
                if (value eq null) encoder.writeInt(0)
                else {
                  encoder.writeInt(1)
                  codec.encode(value, encoder)
                }
            }
          )
          .derive
        avroSchema(
          "{\"type\":\"record\",\"name\":\"Record2\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"r1_1\",\"type\":[\"null\",{\"type\":\"record\",\"name\":\"Record1\",\"fields\":[{\"name\":\"bl\",\"type\":\"boolean\"},{\"name\":\"b\",\"type\":\"int\"},{\"name\":\"sh\",\"type\":\"int\"},{\"name\":\"i\",\"type\":\"int\"},{\"name\":\"l\",\"type\":\"long\"},{\"name\":\"f\",\"type\":\"float\"},{\"name\":\"d\",\"type\":\"double\"},{\"name\":\"c\",\"type\":\"int\"},{\"name\":\"s\",\"type\":\"string\"}]}]},{\"name\":\"r1_2\",\"type\":[\"null\",\"Record1\"]}]}",
          codec
        ) &&
        roundTrip(
          Record2(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
          ),
          46,
          codec
        ) &&
        roundTrip(Record2(null, null), 2, codec)
      },
      test("recursive record with a custom codec") {
        val codec = Recursive.schema
          .deriving(AvroFormat.deriver)
          .instance(
            Recursive.i,
            new AvroBinaryCodec[Int](AvroBinaryCodec.intType) {
              val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.STRING)

              def decodeUnsafe(decoder: BinaryDecoder): Int = java.lang.Integer.valueOf(decoder.readString())

              def encode(value: Int, encoder: BinaryEncoder): Unit = encoder.writeString(value.toString)
            }
          )
          .derive
        avroSchema[Recursive](
          "{\"type\":\"record\",\"name\":\"Recursive\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"i\",\"type\":\"string\"},{\"name\":\"ln\",\"type\":{\"type\":\"array\",\"items\":\"Recursive\"}}]}",
          codec
        ) &&
        roundTrip(Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))), 11, codec)
      }
    ),
    suite("sequences")(
      test("primitive values") {
        implicit val arrayOfUnitSchema: Schema[Array[Unit]]         = Schema.derived
        implicit val arrayOfBooleanSchema: Schema[Array[Boolean]]   = Schema.derived
        implicit val arrayOfByteSchema: Schema[Array[Byte]]         = Schema.derived
        implicit val arrayOfShortSchema: Schema[Array[Short]]       = Schema.derived
        implicit val arrayOfCharSchema: Schema[Array[Char]]         = Schema.derived
        implicit val arrayOfFloatSchema: Schema[Array[Float]]       = Schema.derived
        implicit val arrayOfIntSchema: Schema[Array[Int]]           = Schema.derived
        implicit val arrayOfDoubleSchema: Schema[Array[Double]]     = Schema.derived
        implicit val arrayOfLongSchema: Schema[Array[Long]]         = Schema.derived
        implicit val arraySeqOfFloatSchema: Schema[ArraySeq[Float]] = Schema.derived

        avroSchema[Array[Unit]]("{\"type\":\"array\",\"items\":\"null\"}") &&
        roundTrip(Array[Unit]((), (), ()), 2) &&
        avroSchema[Array[Boolean]]("{\"type\":\"array\",\"items\":\"boolean\"}") &&
        roundTrip(Array[Boolean](true, false, true), 5) &&
        avroSchema[Array[Byte]]("{\"type\":\"array\",\"items\":\"int\"}") &&
        roundTrip(Array[Byte](1: Byte, 2: Byte, 3: Byte), 5) &&
        avroSchema[Array[Short]]("{\"type\":\"array\",\"items\":\"int\"}") &&
        roundTrip(Array[Short](1: Short, 2: Short, 3: Short), 5) &&
        avroSchema[Array[Char]]("{\"type\":\"array\",\"items\":\"int\"}") &&
        roundTrip(Array('1', '2', '3'), 5) &&
        avroSchema[Array[Float]]("{\"type\":\"array\",\"items\":\"float\"}") &&
        roundTrip(Array[Float](1.0f, 2.0f, 3.0f), 14) &&
        avroSchema[Array[Int]]("{\"type\":\"array\",\"items\":\"int\"}") &&
        roundTrip(Array[Int](1, 2, 3), 5) &&
        avroSchema[Array[Double]]("{\"type\":\"array\",\"items\":\"double\"}") &&
        roundTrip(Array[Double](1.0, 2.0, 3.0), 26) &&
        avroSchema[Array[Long]]("{\"type\":\"array\",\"items\":\"long\"}") &&
        roundTrip(Array[Long](1, 2, 3), 5) &&
        avroSchema[List[Int]]("{\"type\":\"array\",\"items\":\"int\"}") &&
        roundTrip((1 to 100).toList, 140) &&
        avroSchema[Set[Long]]("{\"type\":\"array\",\"items\":\"long\"}") &&
        roundTrip(Set(1L, 2L, 3L), 5) &&
        avroSchema[ArraySeq[Float]]("{\"type\":\"array\",\"items\":\"float\"}") &&
        roundTrip(ArraySeq(1.0f, 2.0f, 3.0f), 14) &&
        avroSchema[Vector[Double]]("{\"type\":\"array\",\"items\":\"double\"}") &&
        roundTrip(Vector(1.0, 2.0, 3.0), 26) &&
        avroSchema[List[String]]("{\"type\":\"array\",\"items\":\"string\"}") &&
        roundTrip(List("1", "2", "3"), 8) &&
        avroSchema[List[BigInt]]("{\"type\":\"array\",\"items\":\"bytes\"}") &&
        roundTrip(List(BigInt(1), BigInt(2), BigInt(3)), 8) &&
        avroSchema[List[BigDecimal]](
          "{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"BigDecimal\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"mantissa\",\"type\":\"bytes\"},{\"name\":\"scale\",\"type\":\"int\"},{\"name\":\"precision\",\"type\":\"int\"},{\"name\":\"roundingMode\",\"type\":\"int\"}]}}"
        ) &&
        roundTrip(List(BigDecimal(1.0), BigDecimal(2.0), BigDecimal(3.0)), 17) &&
        avroSchema[List[java.time.LocalDate]](
          "{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"LocalDate\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"}]}}"
        ) &&
        roundTrip(List(java.time.LocalDate.of(2025, 1, 1), java.time.LocalDate.of(2025, 1, 2)), 10) &&
        avroSchema[List[java.util.UUID]](
          "{\"type\":\"array\",\"items\":{\"type\":\"fixed\",\"name\":\"UUID\",\"namespace\":\"java.util\",\"size\":16}}"
        ) &&
        roundTrip((1 to 32).map(x => new java.util.UUID(x, x)).toList, 514) &&
        decodeError[List[Int]](Array.empty[Byte], "Unexpected end of input at: .") &&
        decodeError[List[Int]](Array[Byte](100, 42, 42, 42), "Unexpected end of input at: .at(3)") &&
        decodeError[List[Int]](Array(0x01.toByte), "Expected positive collection part size, got -1 at: .") &&
        decodeError[List[Int]](
          Array(0xfe.toByte, 0xff.toByte, 0xff.toByte, 0xff.toByte, 0x0f.toByte),
          "Expected collection size not greater than 2147483639, got 2147483647 at: ."
        ) &&
        roundTrip(List.empty[Boolean], 1) &&
        roundTrip(List.empty[Byte], 1) &&
        roundTrip(List.empty[Short], 1) &&
        roundTrip(List.empty[Char], 1) &&
        roundTrip(List.empty[Int], 1) &&
        roundTrip(List.empty[Long], 1) &&
        roundTrip(List.empty[Float], 1) &&
        roundTrip(List.empty[Double], 1) &&
        decodeError[List[Boolean]](Array(0x01.toByte), "Expected positive collection part size, got -1 at: .") &&
        decodeError[List[Byte]](Array(0x01.toByte), "Expected positive collection part size, got -1 at: .") &&
        decodeError[List[Short]](Array(0x01.toByte), "Expected positive collection part size, got -1 at: .") &&
        decodeError[List[Char]](Array(0x01.toByte), "Expected positive collection part size, got -1 at: .") &&
        decodeError[List[Long]](Array(0x01.toByte), "Expected positive collection part size, got -1 at: .") &&
        decodeError[List[Float]](Array(0x01.toByte), "Expected positive collection part size, got -1 at: .") &&
        decodeError[List[Double]](Array(0x01.toByte), "Expected positive collection part size, got -1 at: .") &&
        decodeError[Array[Boolean]](Array(0x01.toByte), "Expected positive collection part size, got -1 at: .") &&
        decodeError[Array[Byte]](Array(0x01.toByte), "Expected positive collection part size, got -1 at: .") &&
        decodeError[Array[Short]](Array(0x01.toByte), "Expected positive collection part size, got -1 at: .") &&
        decodeError[Array[Char]](Array(0x01.toByte), "Expected positive collection part size, got -1 at: .") &&
        decodeError[Array[Int]](Array(0x01.toByte), "Expected positive collection part size, got -1 at: .") &&
        decodeError[Array[Long]](Array(0x01.toByte), "Expected positive collection part size, got -1 at: .") &&
        decodeError[Array[Float]](Array(0x01.toByte), "Expected positive collection part size, got -1 at: .") &&
        decodeError[Array[Double]](Array(0x01.toByte), "Expected positive collection part size, got -1 at: .") &&
        decodeError[Array[Boolean]](
          Array(0xfe.toByte, 0xff.toByte, 0xff.toByte, 0xff.toByte, 0x0f.toByte),
          "Expected collection size not greater than 2147483639, got 2147483647 at: ."
        ) &&
        decodeError[Array[Byte]](
          Array(0xfe.toByte, 0xff.toByte, 0xff.toByte, 0xff.toByte, 0x0f.toByte),
          "Expected collection size not greater than 2147483639, got 2147483647 at: ."
        ) &&
        decodeError[Array[Short]](
          Array(0xfe.toByte, 0xff.toByte, 0xff.toByte, 0xff.toByte, 0x0f.toByte),
          "Expected collection size not greater than 2147483639, got 2147483647 at: ."
        ) &&
        decodeError[Array[Char]](
          Array(0xfe.toByte, 0xff.toByte, 0xff.toByte, 0xff.toByte, 0x0f.toByte),
          "Expected collection size not greater than 2147483639, got 2147483647 at: ."
        ) &&
        decodeError[Array[Int]](
          Array(0xfe.toByte, 0xff.toByte, 0xff.toByte, 0xff.toByte, 0x0f.toByte),
          "Expected collection size not greater than 2147483639, got 2147483647 at: ."
        ) &&
        decodeError[Array[Long]](
          Array(0xfe.toByte, 0xff.toByte, 0xff.toByte, 0xff.toByte, 0x0f.toByte),
          "Expected collection size not greater than 2147483639, got 2147483647 at: ."
        ) &&
        decodeError[Array[Float]](
          Array(0xfe.toByte, 0xff.toByte, 0xff.toByte, 0xff.toByte, 0x0f.toByte),
          "Expected collection size not greater than 2147483639, got 2147483647 at: ."
        ) &&
        decodeError[Array[Double]](
          Array(0xfe.toByte, 0xff.toByte, 0xff.toByte, 0xff.toByte, 0x0f.toByte),
          "Expected collection size not greater than 2147483639, got 2147483647 at: ."
        ) &&
        roundTrip(Array.empty[Boolean], 1) &&
        roundTrip(Array.empty[Byte], 1) &&
        roundTrip(Array.empty[Short], 1) &&
        roundTrip(Array.empty[Char], 1) &&
        roundTrip(Array.empty[Int], 1) &&
        roundTrip(Array.empty[Long], 1) &&
        roundTrip(Array.empty[Float], 1) &&
        roundTrip(Array.empty[Double], 1)
      },
      test("complex values") {
        avroSchema[List[Record1]](
          "{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"Record1\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"bl\",\"type\":\"boolean\"},{\"name\":\"b\",\"type\":\"int\"},{\"name\":\"sh\",\"type\":\"int\"},{\"name\":\"i\",\"type\":\"int\"},{\"name\":\"l\",\"type\":\"long\"},{\"name\":\"f\",\"type\":\"float\"},{\"name\":\"d\",\"type\":\"double\"},{\"name\":\"c\",\"type\":\"int\"},{\"name\":\"s\",\"type\":\"string\"}]}}"
        ) &&
        roundTrip(
          List(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
          ),
          46
        )
      },
      test("recursive values") {
        avroSchema[List[Recursive]](
          "{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"Recursive\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"i\",\"type\":\"int\"},{\"name\":\"ln\",\"type\":{\"type\":\"array\",\"items\":\"Recursive\"}}]}}"
        ) &&
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
        avroSchema[Map[String, Unit]]("{\"type\":\"map\",\"values\":\"null\"}") &&
        roundTrip(Map("VVV" -> (), "WWW" -> ()), 10) &&
        avroSchema[Map[String, Boolean]]("{\"type\":\"map\",\"values\":\"boolean\"}") &&
        roundTrip(Map("VVV" -> true, "WWW" -> false), 12) &&
        avroSchema[Map[String, Byte]]("{\"type\":\"map\",\"values\":\"int\"}") &&
        roundTrip(Map("VVV" -> (1: Byte), "WWW" -> (2: Byte)), 12) &&
        avroSchema[Map[String, Short]]("{\"type\":\"map\",\"values\":\"int\"}") &&
        roundTrip(Map("VVV" -> (1: Short), "WWW" -> (2: Short)), 12) &&
        avroSchema[Map[String, Char]]("{\"type\":\"map\",\"values\":\"int\"}") &&
        roundTrip(Map("VVV" -> '1', "WWW" -> '2'), 12) &&
        avroSchema[Map[String, Int]]("{\"type\":\"map\",\"values\":\"int\"}") &&
        roundTrip(Map("VVV" -> 1, "WWW" -> 2), 12) &&
        avroSchema[Map[String, Long]]("{\"type\":\"map\",\"values\":\"long\"}") &&
        roundTrip(Map("VVV" -> 1L, "WWW" -> 2L), 12) &&
        avroSchema[Map[String, Float]]("{\"type\":\"map\",\"values\":\"float\"}") &&
        roundTrip(Map("VVV" -> 1.0f, "WWW" -> 2.0f), 18) &&
        avroSchema[Map[String, Double]]("{\"type\":\"map\",\"values\":\"double\"}") &&
        roundTrip(Map("VVV" -> 1.0, "WWW" -> 2.0), 26) &&
        avroSchema[Map[String, String]]("{\"type\":\"map\",\"values\":\"string\"}") &&
        roundTrip(Map("VVV" -> "1", "WWW" -> "2"), 14) &&
        avroSchema[Map[String, BigInt]]("{\"type\":\"map\",\"values\":\"bytes\"}") &&
        roundTrip(Map("VVV" -> BigInt(1), "WWW" -> BigInt(2)), 14) &&
        roundTrip(Map("VVV" -> BigDecimal(1.0), "WWW" -> BigDecimal(2.0)), 20) &&
        roundTrip(Map("VVV" -> java.time.LocalDate.of(2025, 1, 1), "WWW" -> java.time.LocalDate.of(2025, 1, 2)), 18) &&
        roundTrip(Map("VVV" -> new java.util.UUID(1L, 1L), "WWW" -> new java.util.UUID(2L, 2L)), 42) &&
        decodeError[Map[String, Int]](Array.empty[Byte], "Unexpected end of input at: .") &&
        decodeError[Map[String, Int]](Array[Byte](100), "Unexpected end of input at: .at(0)") &&
        decodeError[Map[String, Int]](Array(0x01.toByte), "Expected positive map part size, got -1 at: .") &&
        decodeError[Map[String, Int]](
          Array(0xfe.toByte, 0xff.toByte, 0xff.toByte, 0xff.toByte, 0x0f.toByte),
          "Expected map size not greater than 2147483639, got 2147483647 at: ."
        )
      },
      test("string keys and complex values") {
        avroSchema[Map[String, Record1]](
          "{\"type\":\"map\",\"values\":{\"type\":\"record\",\"name\":\"Record1\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"bl\",\"type\":\"boolean\"},{\"name\":\"b\",\"type\":\"int\"},{\"name\":\"sh\",\"type\":\"int\"},{\"name\":\"i\",\"type\":\"int\"},{\"name\":\"l\",\"type\":\"long\"},{\"name\":\"f\",\"type\":\"float\"},{\"name\":\"d\",\"type\":\"double\"},{\"name\":\"c\",\"type\":\"int\"},{\"name\":\"s\",\"type\":\"string\"}]}}"
        ) &&
        roundTrip(
          Map(
            "VVV" -> Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            "WWW" -> Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
          ),
          54
        )
      },
      test("string keys and recursive values") {
        avroSchema[Map[String, Recursive]](
          "{\"type\":\"map\",\"values\":{\"type\":\"record\",\"name\":\"Recursive\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"i\",\"type\":\"int\"},{\"name\":\"ln\",\"type\":{\"type\":\"array\",\"items\":\"Recursive\"}}]}}"
        ) &&
        roundTrip(
          Map(
            "VVV" -> Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))),
            "WWW" -> Recursive(4, List(Recursive(5, List(Recursive(6, Nil)))))
          ),
          26
        )
      },
      test("non string key map") {
        avroSchema[Map[Int, Long]](
          "{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"Tuple2\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"_1\",\"type\":\"int\"},{\"name\":\"_2\",\"type\":\"long\"}]}}"
        ) &&
        roundTrip(Map(1 -> 1L, 2 -> 2L), 6) &&
        decodeError[Map[Int, Long]](Array.empty[Byte], "Unexpected end of input at: .") &&
        decodeError[Map[Int, Long]](Array[Byte](100), "Unexpected end of input at: .at(0)") &&
        decodeError[Map[Int, Long]](Array(0x01.toByte), "Expected positive map part size, got -1 at: .") &&
        decodeError[Map[Int, Long]](
          Array(0xfe.toByte, 0xff.toByte, 0xff.toByte, 0xff.toByte, 0x0f.toByte),
          "Expected map size not greater than 2147483639, got 2147483647 at: ."
        ) &&
        decodeError[Map[Int, Long]](Array[Byte](2, 2, 0xff.toByte), "Unexpected end of input at: .atKey(1)")
      },
      test("non string key with recursive values") {
        avroSchema[Map[Recursive, Int]](
          "{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"Tuple2\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"_1\",\"type\":{\"type\":\"record\",\"name\":\"Recursive\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"i\",\"type\":\"int\"},{\"name\":\"ln\",\"type\":{\"type\":\"array\",\"items\":\"Recursive\"}}]}},{\"name\":\"_2\",\"type\":\"int\"}]}}"
        ) &&
        roundTrip(
          Map(
            Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))) -> 1,
            Recursive(4, List(Recursive(5, List(Recursive(6, Nil))))) -> 2
          ),
          20
        )
      },
      test("nested maps") {
        avroSchema[Map[String, Map[Int, Long]]](
          "{\"type\":\"map\",\"values\":{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"Tuple2\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"_1\",\"type\":\"int\"},{\"name\":\"_2\",\"type\":\"long\"}]}}}"
        ) &&
        roundTrip(Map("VVV" -> Map(1 -> 1L, 2 -> 2L)), 12) &&
        avroSchema[Map[Map[Int, Long], String]](
          "{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"Tuple2_1\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"_1\",\"type\":{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"Tuple2\",\"fields\":[{\"name\":\"_1\",\"type\":\"int\"},{\"name\":\"_2\",\"type\":\"long\"}]}}},{\"name\":\"_2\",\"type\":\"string\"}]}}"
        ) &&
        roundTrip(Map(Map(1 -> 1L, 2 -> 2L) -> "WWW"), 12)
      }
    ),
    suite("variants")(
      test("constant values") {
        avroSchema[TrafficLight](
          "[{\"type\":\"record\",\"name\":\"Red\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatSpec.TrafficLight\",\"fields\":[]},{\"type\":\"record\",\"name\":\"Yellow\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatSpec.TrafficLight\",\"fields\":[]},{\"type\":\"record\",\"name\":\"Green\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatSpec.TrafficLight\",\"fields\":[]}]"
        ) &&
        roundTrip[TrafficLight](TrafficLight.Green, 1) &&
        roundTrip[TrafficLight](TrafficLight.Yellow, 1) &&
        roundTrip[TrafficLight](TrafficLight.Red, 1)
        decodeError[TrafficLight](Array[Byte](6), "Expected enum index from 0 to 2, got 3 at: .")
      },
      test("option") {
        avroSchema[Option[Int]](
          "[{\"type\":\"record\",\"name\":\"None\",\"namespace\":\"scala\",\"fields\":[]},{\"type\":\"record\",\"name\":\"Some\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]}]"
        ) &&
        roundTrip(Option(42), 2) &&
        roundTrip[Option[Int]](None, 1) &&
        decodeError[Option[Int]](Array[Byte](4), "Expected enum index from 0 to 1, got 2 at: .") &&
        decodeError[Option[Int]](Array[Byte](2, 0xff.toByte), "Unexpected end of input at: .when[Some].value")
      },
      test("either") {
        avroSchema[Either[String, Int]](
          "[{\"type\":\"record\",\"name\":\"Left\",\"namespace\":\"scala.util\",\"fields\":[{\"name\":\"value\",\"type\":\"string\"}]},{\"type\":\"record\",\"name\":\"Right\",\"namespace\":\"scala.util\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]}]"
        ) &&
        roundTrip[Either[String, Int]](Right(42), 2) &&
        roundTrip[Either[String, Int]](Left("VVV"), 5)
      }
    ),
    suite("wrapper")(
      test("top-level") {
        val emailCodec = Schema[Email].derive(AvroFormat)
        val bytes      = emailCodec.encode(Email("test@gmail.com"))
        bytes(5) = 42
        val testUuid = new UUID(123456789L, 987654321L)
        avroSchema[UserId]("\"long\"") &&
        roundTrip[UserId](UserId(1234567890123456789L), 9) &&
        avroSchema[Email]("\"string\"") &&
        roundTrip[Email](Email("john@gmail.com"), 15) &&
        avroSchema[TransactionId](
          "{\"type\":\"fixed\",\"name\":\"UUID\",\"namespace\":\"java.util\",\"size\":16}"
        ) &&
        roundTrip[TransactionId](TransactionId(testUuid), 16) &&
        decodeError[Email](bytes, "Expected Email at: .") &&
        decodeError[Email](Array[Byte](100), "Unexpected end of input at: .wrapped")
      },
      test("as a record field") {
        avroSchema[Record3](
          "{\"type\":\"record\",\"name\":\"Record3\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"userId\",\"type\":\"long\"},{\"name\":\"email\",\"type\":\"string\"}]}"
        ) &&
        roundTrip[Record3](Record3(UserId(1234567890123456789L), Email("backup@gmail.com")), 26)
      }
    ),
    suite("dynamic value")(
      test("top-level") {
        avroSchema[DynamicValue](
          "{\"type\":\"record\",\"name\":\"DynamicValue\",\"namespace\":\"zio.blocks.schema\",\"fields\":[{\"name\":\"value\",\"type\":[{\"type\":\"record\",\"name\":\"Primitive\",\"namespace\":\"zio.blocks.schema.DynamicValue\",\"fields\":[{\"name\":\"value\",\"type\":[{\"type\":\"record\",\"name\":\"Unit\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[]},{\"type\":\"record\",\"name\":\"Boolean\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"boolean\"}]},{\"type\":\"record\",\"name\":\"Byte\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]},{\"type\":\"record\",\"name\":\"Short\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]},{\"type\":\"record\",\"name\":\"Int\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]},{\"type\":\"record\",\"name\":\"Long\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"long\"}]},{\"type\":\"record\",\"name\":\"Float\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"float\"}]},{\"type\":\"record\",\"name\":\"Double\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"double\"}]},{\"type\":\"record\",\"name\":\"Char\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]},{\"type\":\"record\",\"name\":\"String\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"string\"}]},{\"type\":\"record\",\"name\":\"BigInt\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"bytes\"}]},{\"type\":\"record\",\"name\":\"BigDecimal\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"BigDecimal\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"mantissa\",\"type\":\"bytes\"},{\"name\":\"scale\",\"type\":\"int\"},{\"name\":\"precision\",\"type\":\"int\"},{\"name\":\"roundingMode\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"DayOfWeek\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]},{\"type\":\"record\",\"name\":\"Duration\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"Duration\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"seconds\",\"type\":\"long\"},{\"name\":\"nano\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"Instant\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"Instant\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"epochSecond\",\"type\":\"long\"},{\"name\":\"nano\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"LocalDate\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"LocalDate\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"LocalDateTime\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"LocalDateTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"},{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"LocalTime\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"LocalTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"Month\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]},{\"type\":\"record\",\"name\":\"MonthDay\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"MonthDay\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"OffsetDateTime\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"OffsetDateTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"},{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"},{\"name\":\"offset\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"OffsetTime\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"OffsetTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"},{\"name\":\"offset\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"Period\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"Period\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"years\",\"type\":\"int\"},{\"name\":\"months\",\"type\":\"int\"},{\"name\":\"days\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"Year\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]},{\"type\":\"record\",\"name\":\"YearMonth\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"YearMonth\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"ZoneId\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"string\"}]},{\"type\":\"record\",\"name\":\"ZoneOffset\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]},{\"type\":\"record\",\"name\":\"ZonedDateTime\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"ZonedDateTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"},{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"},{\"name\":\"offset\",\"type\":\"int\"},{\"name\":\"zone\",\"type\":\"string\"}]}}]},{\"type\":\"record\",\"name\":\"Currency\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"fixed\",\"name\":\"Currency\",\"namespace\":\"java.util\",\"size\":3}}]},{\"type\":\"record\",\"name\":\"UUID\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"fixed\",\"name\":\"UUID\",\"namespace\":\"java.util\",\"size\":16}}]}]}]},{\"type\":\"record\",\"name\":\"Record\",\"namespace\":\"zio.blocks.schema.DynamicValue\",\"fields\":[{\"name\":\"fields\",\"type\":{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"Field\",\"namespace\":\"zio.blocks.schema.internal\",\"fields\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"value\",\"type\":\"zio.blocks.schema.DynamicValue\"}]}}}]},{\"type\":\"record\",\"name\":\"Variant\",\"namespace\":\"zio.blocks.schema.DynamicValue\",\"fields\":[{\"name\":\"caseName\",\"type\":\"string\"},{\"name\":\"value\",\"type\":\"zio.blocks.schema.DynamicValue\"}]},{\"type\":\"record\",\"name\":\"Sequence\",\"namespace\":\"zio.blocks.schema.DynamicValue\",\"fields\":[{\"name\":\"elements\",\"type\":{\"type\":\"array\",\"items\":\"zio.blocks.schema.DynamicValue\"}}]},{\"type\":\"record\",\"name\":\"Map\",\"namespace\":\"zio.blocks.schema.DynamicValue\",\"fields\":[{\"name\":\"entries\",\"type\":{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"Entry\",\"namespace\":\"zio.blocks.schema.internal\",\"fields\":[{\"name\":\"key\",\"type\":\"zio.blocks.schema.DynamicValue\"},{\"name\":\"value\",\"type\":\"zio.blocks.schema.DynamicValue\"}]}}}]},{\"type\":\"record\",\"name\":\"Null\",\"namespace\":\"zio.blocks.schema.DynamicValue\",\"fields\":[]}]}]}"
        ) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Unit), 2) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Boolean(true)), 3) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Byte(1: Byte)), 3) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Short(1: Short)), 3) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Int(1)), 3) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Long(1L)), 3) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Float(1.0f)), 6) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Double(1.0)), 10) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Char('1')), 3) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.String("VVV")), 6) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.BigInt(123)), 4) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.BigDecimal(123.45)), 8) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY)), 3) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Duration(Duration.ofSeconds(60))), 4) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Instant(Instant.EPOCH)), 4) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.LocalDate(LocalDate.MAX)), 9) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.LocalDateTime(LocalDateTime.MAX)), 17) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.LocalTime(LocalTime.MAX)), 10) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Month(Month.MAY)), 3) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.MonthDay(MonthDay.of(Month.MAY, 1))), 4) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.OffsetDateTime(OffsetDateTime.MAX)), 20) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.OffsetTime(OffsetTime.MAX)), 13) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Period(Period.ofDays(1))), 5) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Year(Year.of(2025))), 4) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.YearMonth(YearMonth.of(2025, 1))), 5) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.ZoneId(ZoneId.of("UTC"))), 6) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.ZoneOffset(ZoneOffset.MAX)), 5) &&
        roundTrip[DynamicValue](
          DynamicValue.Primitive(
            PrimitiveValue.ZonedDateTime(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC")))
          ),
          15
        ) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Currency(Currency.getInstance("USD"))), 5) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.UUID(UUID.randomUUID())), 18) &&
        roundTrip[DynamicValue](
          DynamicValue.Record(
            Chunk(
              ("i", DynamicValue.Primitive(PrimitiveValue.Int(1))),
              ("s", DynamicValue.Primitive(PrimitiveValue.String("VVV")))
            )
          ),
          16
        ) &&
        roundTrip[DynamicValue](DynamicValue.Variant("Int", DynamicValue.Primitive(PrimitiveValue.Int(1))), 8) &&
        roundTrip[DynamicValue](
          DynamicValue.Sequence(
            Chunk(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.String("VVV"))
            )
          ),
          12
        ) &&
        roundTrip[DynamicValue](
          DynamicValue.Map(
            Chunk(
              (DynamicValue.Primitive(PrimitiveValue.Long(1L)), DynamicValue.Primitive(PrimitiveValue.Int(1))),
              (DynamicValue.Primitive(PrimitiveValue.Long(2L)), DynamicValue.Primitive(PrimitiveValue.String("VVV")))
            )
          ),
          18
        ) &&
        roundTrip[DynamicValue](DynamicValue.Null, 1) &&
        decodeError[DynamicValue](Array[Byte](12), "Expected enum index from 0 to 5, got 6 at: .") &&
        decodeError[DynamicValue](Array.empty[Byte], "Unexpected end of input at: .") &&
        decodeError[DynamicValue](Array[Byte](0), "Unexpected end of input at: .when[Primitive]") &&
        decodeError[DynamicValue](
          Array[Byte](0, 60),
          "Expected enum index from 0 to 29, got 30 at: .when[Primitive]"
        ) &&
        decodeError[DynamicValue](
          Array[Byte](0, 8, 0xff.toByte),
          "Unexpected end of input at: .when[Primitive].value"
        ) &&
        decodeError[DynamicValue](Array[Byte](2), "Unexpected end of input at: .when[Record].fields") &&
        decodeError[DynamicValue](
          Array[Byte](2, 1),
          "Expected positive collection part size, got -1 at: .when[Record].fields"
        ) &&
        decodeError[DynamicValue](
          Array[Byte](2, 2, 2),
          "Unexpected end of input at: .when[Record].fields.at(0)._1"
        ) &&
        decodeError[DynamicValue](
          Array[Byte](2, 2, 0, 12),
          "Expected enum index from 0 to 5, got 6 at: .when[Record].fields.at(0)._2"
        ) &&
        decodeError[DynamicValue](
          Array[Byte](2, 0xfe.toByte, 0xff.toByte, 0xff.toByte, 0xff.toByte, 0x0f.toByte),
          "Expected collection size not greater than 2147483639, got 2147483647 at: .when[Record].fields"
        ) &&
        decodeError[DynamicValue](Array[Byte](4, 2), "Unexpected end of input at: .when[Variant].caseName") &&
        decodeError[DynamicValue](
          Array[Byte](4, 0, 12),
          "Expected enum index from 0 to 5, got 6 at: .when[Variant].value"
        ) &&
        decodeError[DynamicValue](
          Array[Byte](6, 1),
          "Expected positive collection part size, got -1 at: .when[Sequence].elements"
        ) &&
        decodeError[DynamicValue](
          Array[Byte](6, 0xfe.toByte, 0xff.toByte, 0xff.toByte, 0xff.toByte, 0x0f.toByte),
          "Expected collection size not greater than 2147483639, got 2147483647 at: .when[Sequence].elements"
        ) &&
        decodeError[DynamicValue](
          Array[Byte](6, 2, 12),
          "Expected enum index from 0 to 5, got 6 at: .when[Sequence].elements.at(0)"
        ) &&
        decodeError[DynamicValue](
          Array[Byte](8, 1),
          "Expected positive collection part size, got -1 at: .when[Map].entries"
        ) &&
        decodeError[DynamicValue](
          Array[Byte](8, 0xfe.toByte, 0xff.toByte, 0xff.toByte, 0xff.toByte, 0x0f.toByte),
          "Expected collection size not greater than 2147483639, got 2147483647 at: .when[Map].entries"
        ) &&
        decodeError[DynamicValue](
          Array[Byte](8, 2, 12),
          "Expected enum index from 0 to 5, got 6 at: .when[Map].entries.at(0)._1"
        ) &&
        decodeError[DynamicValue](
          Array[Byte](8, 2, 0, 0, 12),
          "Expected enum index from 0 to 5, got 6 at: .when[Map].entries.at(0)._2"
        )
      },
      test("as record field values") {
        val value = Dynamic(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Map(
            Chunk(
              (DynamicValue.Primitive(PrimitiveValue.Long(1L)), DynamicValue.Primitive(PrimitiveValue.Int(1))),
              (DynamicValue.Primitive(PrimitiveValue.Long(2L)), DynamicValue.Primitive(PrimitiveValue.String("VVV")))
            )
          )
        )
        avroSchema[Dynamic](
          "{\"type\":\"record\",\"name\":\"Dynamic\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"primitive\",\"type\":{\"type\":\"record\",\"name\":\"DynamicValue\",\"namespace\":\"zio.blocks.schema\",\"fields\":[{\"name\":\"value\",\"type\":[{\"type\":\"record\",\"name\":\"Primitive\",\"namespace\":\"zio.blocks.schema.DynamicValue\",\"fields\":[{\"name\":\"value\",\"type\":[{\"type\":\"record\",\"name\":\"Unit\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[]},{\"type\":\"record\",\"name\":\"Boolean\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"boolean\"}]},{\"type\":\"record\",\"name\":\"Byte\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]},{\"type\":\"record\",\"name\":\"Short\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]},{\"type\":\"record\",\"name\":\"Int\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]},{\"type\":\"record\",\"name\":\"Long\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"long\"}]},{\"type\":\"record\",\"name\":\"Float\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"float\"}]},{\"type\":\"record\",\"name\":\"Double\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"double\"}]},{\"type\":\"record\",\"name\":\"Char\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]},{\"type\":\"record\",\"name\":\"String\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"string\"}]},{\"type\":\"record\",\"name\":\"BigInt\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"bytes\"}]},{\"type\":\"record\",\"name\":\"BigDecimal\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"BigDecimal\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"mantissa\",\"type\":\"bytes\"},{\"name\":\"scale\",\"type\":\"int\"},{\"name\":\"precision\",\"type\":\"int\"},{\"name\":\"roundingMode\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"DayOfWeek\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]},{\"type\":\"record\",\"name\":\"Duration\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"Duration\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"seconds\",\"type\":\"long\"},{\"name\":\"nano\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"Instant\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"Instant\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"epochSecond\",\"type\":\"long\"},{\"name\":\"nano\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"LocalDate\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"LocalDate\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"LocalDateTime\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"LocalDateTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"},{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"LocalTime\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"LocalTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"Month\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]},{\"type\":\"record\",\"name\":\"MonthDay\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"MonthDay\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"OffsetDateTime\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"OffsetDateTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"},{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"},{\"name\":\"offset\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"OffsetTime\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"OffsetTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"},{\"name\":\"offset\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"Period\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"Period\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"years\",\"type\":\"int\"},{\"name\":\"months\",\"type\":\"int\"},{\"name\":\"days\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"Year\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]},{\"type\":\"record\",\"name\":\"YearMonth\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"YearMonth\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"ZoneId\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"string\"}]},{\"type\":\"record\",\"name\":\"ZoneOffset\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]},{\"type\":\"record\",\"name\":\"ZonedDateTime\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"ZonedDateTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"},{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"},{\"name\":\"offset\",\"type\":\"int\"},{\"name\":\"zone\",\"type\":\"string\"}]}}]},{\"type\":\"record\",\"name\":\"Currency\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"fixed\",\"name\":\"Currency\",\"namespace\":\"java.util\",\"size\":3}}]},{\"type\":\"record\",\"name\":\"UUID\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"fixed\",\"name\":\"UUID\",\"namespace\":\"java.util\",\"size\":16}}]}]}]},{\"type\":\"record\",\"name\":\"Record\",\"namespace\":\"zio.blocks.schema.DynamicValue\",\"fields\":[{\"name\":\"fields\",\"type\":{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"Field\",\"namespace\":\"zio.blocks.schema.internal\",\"fields\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"value\",\"type\":\"zio.blocks.schema.DynamicValue\"}]}}}]},{\"type\":\"record\",\"name\":\"Variant\",\"namespace\":\"zio.blocks.schema.DynamicValue\",\"fields\":[{\"name\":\"caseName\",\"type\":\"string\"},{\"name\":\"value\",\"type\":\"zio.blocks.schema.DynamicValue\"}]},{\"type\":\"record\",\"name\":\"Sequence\",\"namespace\":\"zio.blocks.schema.DynamicValue\",\"fields\":[{\"name\":\"elements\",\"type\":{\"type\":\"array\",\"items\":\"zio.blocks.schema.DynamicValue\"}}]},{\"type\":\"record\",\"name\":\"Map\",\"namespace\":\"zio.blocks.schema.DynamicValue\",\"fields\":[{\"name\":\"entries\",\"type\":{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"Entry\",\"namespace\":\"zio.blocks.schema.internal\",\"fields\":[{\"name\":\"key\",\"type\":\"zio.blocks.schema.DynamicValue\"},{\"name\":\"value\",\"type\":\"zio.blocks.schema.DynamicValue\"}]}}}]},{\"type\":\"record\",\"name\":\"Null\",\"namespace\":\"zio.blocks.schema.DynamicValue\",\"fields\":[]}]}]}},{\"name\":\"map\",\"type\":\"zio.blocks.schema.DynamicValue\"}]}"
        ) &&
        roundTrip[Dynamic](value, 21)
      },
      test("as record field values with custom codecs injected by optic") {
        val value = Dynamic(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Map(
            Chunk(
              (DynamicValue.Primitive(PrimitiveValue.Long(1L)), DynamicValue.Primitive(PrimitiveValue.Int(1))),
              (DynamicValue.Primitive(PrimitiveValue.Long(2L)), DynamicValue.Primitive(PrimitiveValue.String("VVV")))
            )
          )
        )
        val codec1 = new AvroBinaryCodec[DynamicValue]() {
          private val codec = Schema[DynamicValue].derive(AvroFormat)

          val avroSchema: AvroSchema =
            AvroSchema.createUnion(AvroSchema.create(AvroSchema.Type.NULL), codec.avroSchema)

          def decodeUnsafe(decoder: BinaryDecoder): DynamicValue = {
            val idx = decoder.readInt()
            if (idx == 0) null
            else codec.decodeUnsafe(decoder)
          }

          def encode(value: DynamicValue, encoder: BinaryEncoder): Unit =
            if (value eq null) encoder.writeInt(0)
            else {
              encoder.writeInt(1)
              codec.encode(value, encoder)
            }
        }
        val codec2 = Schema[Dynamic]
          .deriving(AvroFormat.deriver)
          .instance(Dynamic.primitive, codec1)
          .instance(Dynamic.map, codec1)
          .derive
        avroSchema(
          "{\"type\":\"record\",\"name\":\"Dynamic\",\"namespace\":\"zio.blocks.schema.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"primitive\",\"type\":[\"null\",{\"type\":\"record\",\"name\":\"DynamicValue\",\"namespace\":\"zio.blocks.schema\",\"fields\":[{\"name\":\"value\",\"type\":[{\"type\":\"record\",\"name\":\"Primitive\",\"namespace\":\"zio.blocks.schema.DynamicValue\",\"fields\":[{\"name\":\"value\",\"type\":[{\"type\":\"record\",\"name\":\"Unit\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[]},{\"type\":\"record\",\"name\":\"Boolean\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"boolean\"}]},{\"type\":\"record\",\"name\":\"Byte\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]},{\"type\":\"record\",\"name\":\"Short\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]},{\"type\":\"record\",\"name\":\"Int\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]},{\"type\":\"record\",\"name\":\"Long\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"long\"}]},{\"type\":\"record\",\"name\":\"Float\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"float\"}]},{\"type\":\"record\",\"name\":\"Double\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"double\"}]},{\"type\":\"record\",\"name\":\"Char\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]},{\"type\":\"record\",\"name\":\"String\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"string\"}]},{\"type\":\"record\",\"name\":\"BigInt\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"bytes\"}]},{\"type\":\"record\",\"name\":\"BigDecimal\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"BigDecimal\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"mantissa\",\"type\":\"bytes\"},{\"name\":\"scale\",\"type\":\"int\"},{\"name\":\"precision\",\"type\":\"int\"},{\"name\":\"roundingMode\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"DayOfWeek\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]},{\"type\":\"record\",\"name\":\"Duration\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"Duration\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"seconds\",\"type\":\"long\"},{\"name\":\"nano\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"Instant\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"Instant\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"epochSecond\",\"type\":\"long\"},{\"name\":\"nano\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"LocalDate\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"LocalDate\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"LocalDateTime\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"LocalDateTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"},{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"LocalTime\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"LocalTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"Month\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]},{\"type\":\"record\",\"name\":\"MonthDay\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"MonthDay\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"OffsetDateTime\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"OffsetDateTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"},{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"},{\"name\":\"offset\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"OffsetTime\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"OffsetTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"},{\"name\":\"offset\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"Period\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"Period\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"years\",\"type\":\"int\"},{\"name\":\"months\",\"type\":\"int\"},{\"name\":\"days\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"Year\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]},{\"type\":\"record\",\"name\":\"YearMonth\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"YearMonth\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"}]}}]},{\"type\":\"record\",\"name\":\"ZoneId\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"string\"}]},{\"type\":\"record\",\"name\":\"ZoneOffset\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]},{\"type\":\"record\",\"name\":\"ZonedDateTime\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"record\",\"name\":\"ZonedDateTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"},{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"},{\"name\":\"offset\",\"type\":\"int\"},{\"name\":\"zone\",\"type\":\"string\"}]}}]},{\"type\":\"record\",\"name\":\"Currency\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"fixed\",\"name\":\"Currency\",\"namespace\":\"java.util\",\"size\":3}}]},{\"type\":\"record\",\"name\":\"UUID\",\"namespace\":\"zio.blocks.schema.PrimitiveValue\",\"fields\":[{\"name\":\"value\",\"type\":{\"type\":\"fixed\",\"name\":\"UUID\",\"namespace\":\"java.util\",\"size\":16}}]}]}]},{\"type\":\"record\",\"name\":\"Record\",\"namespace\":\"zio.blocks.schema.DynamicValue\",\"fields\":[{\"name\":\"fields\",\"type\":{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"Field\",\"namespace\":\"zio.blocks.schema.internal\",\"fields\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"value\",\"type\":\"zio.blocks.schema.DynamicValue\"}]}}}]},{\"type\":\"record\",\"name\":\"Variant\",\"namespace\":\"zio.blocks.schema.DynamicValue\",\"fields\":[{\"name\":\"caseName\",\"type\":\"string\"},{\"name\":\"value\",\"type\":\"zio.blocks.schema.DynamicValue\"}]},{\"type\":\"record\",\"name\":\"Sequence\",\"namespace\":\"zio.blocks.schema.DynamicValue\",\"fields\":[{\"name\":\"elements\",\"type\":{\"type\":\"array\",\"items\":\"zio.blocks.schema.DynamicValue\"}}]},{\"type\":\"record\",\"name\":\"Map\",\"namespace\":\"zio.blocks.schema.DynamicValue\",\"fields\":[{\"name\":\"entries\",\"type\":{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"Entry\",\"namespace\":\"zio.blocks.schema.internal\",\"fields\":[{\"name\":\"key\",\"type\":\"zio.blocks.schema.DynamicValue\"},{\"name\":\"value\",\"type\":\"zio.blocks.schema.DynamicValue\"}]}}}]},{\"type\":\"record\",\"name\":\"Null\",\"namespace\":\"zio.blocks.schema.DynamicValue\",\"fields\":[]}]}]}]},{\"name\":\"map\",\"type\":[\"null\",\"zio.blocks.schema.DynamicValue\"]}]}",
          codec2
        ) &&
        roundTrip[Dynamic](value, 23, codec2) &&
        roundTrip[Dynamic](Dynamic(null, null), 2, codec2)
      }
    )
  )

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

  object Record1 extends CompanionOptics[Record1] {
    implicit val schema: Schema[Record1] = Schema.derived

    val i: Lens[Record1, Int] = $(_.i)
  }

  case class Record2(
    r1_1: Record1,
    r1_2: Record1
  )

  object Record2 extends CompanionOptics[Record2] {
    implicit val schema: Schema[Record2] = Schema.derived

    val r1_1: Lens[Record2, Record1] = $(_.r1_1)
    val r1_2: Lens[Record2, Record1] = $(_.r1_2)
    val r1_1_i: Lens[Record2, Int]   = $(_.r1_1.i)
    val r1_2_i: Lens[Record2, Int]   = $(_.r1_2.i)
  }

  case class Recursive(i: Int, ln: List[Recursive])

  object Recursive extends CompanionOptics[Recursive] {
    implicit val schema: Schema[Recursive]   = Schema.derived
    val i: Lens[Recursive, Int]              = $(_.i)
    val ln: Lens[Recursive, List[Recursive]] = $(_.ln)
  }

  sealed trait TrafficLight

  object TrafficLight {
    implicit val schema: Schema[TrafficLight] = Schema.derived

    case object Red extends TrafficLight

    case object Yellow extends TrafficLight

    case object Green extends TrafficLight
  }

  implicit val eitherSchema: Schema[Either[String, Int]] = Schema.derived

  case class UserId(value: Long)

  object UserId {
    implicit val typeId: TypeId[UserId] =
      TypeId.nominal[UserId]("UserId", Owner.fromPackagePath("zio.blocks.schema.avro").term("AvroFormatSpec"))
    implicit val schema: Schema[UserId] =
      Schema[Long].transform[UserId](x => new UserId(x), _.value)
  }

  case class Email(value: String)

  object Email {
    private[this] val EmailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".r

    implicit val schema: Schema[Email] = new Schema(
      new Reflect.Wrapper[Binding, Email, String](
        Schema[String].reflect,
        TypeId.nominal[Email]("Email", Owner.fromPackagePath("zio.blocks.avro").term("AvroFormatSpec")),
        Binding.Wrapper(
          {
            case x @ EmailRegex(_*) => new Email(x)
            case _                  => throw SchemaError.validationFailed("Expected Email")
          },
          (e: Email) => e.value
        )
      )
    )
  }

  case class TransactionId(value: UUID)

  object TransactionId {
    implicit val typeId: TypeId[TransactionId] =
      TypeId
        .nominal[TransactionId]("TransactionId", Owner.fromPackagePath("zio.blocks.schema.avro").term("AvroFormatSpec"))
    implicit val schema: Schema[TransactionId] =
      Schema[UUID].transform[TransactionId](x => new TransactionId(x), _.value)
  }

  case class Record3(userId: UserId, email: Email)

  object Record3 {
    implicit val schema: Schema[Record3] = Schema.derived
  }

  case class Record4(hidden: Unit, optKey: Option[String])

  object Record4 extends CompanionOptics[Record4] {
    implicit val schema: Schema[Record4] = Schema.derived

    val hidden: Lens[Record4, Unit]               = $(_.hidden)
    val optKey: Lens[Record4, Option[String]]     = $(_.optKey)
    val optKey_None: Optional[Record4, None.type] = $(_.optKey.when[None.type])
  }

  case class Dynamic(primitive: DynamicValue, map: DynamicValue)

  object Dynamic extends CompanionOptics[Dynamic] {
    implicit val schema: Schema[Dynamic] = Schema.derived

    val primitive: Lens[Dynamic, DynamicValue] = $(_.primitive)
    val map: Lens[Dynamic, DynamicValue]       = $(_.map)
  }
}
