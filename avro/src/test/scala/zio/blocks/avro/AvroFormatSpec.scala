package zio.blocks.avro

import org.apache.avro.io.{BinaryDecoder, BinaryEncoder}
import org.apache.avro.{Schema => AvroSchema}
import zio.blocks.schema._
import zio.blocks.avro.AvroTestUtils._
import zio.blocks.schema.binding.Binding
import zio.test._
import java.util.UUID
import scala.collection.immutable.ArraySeq
import scala.util.control.NonFatal

object AvroFormatSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("AvroFormatSpec")(
    suite("primitives")(
      test("Unit") {
        avroSchema[Unit]("\"null\"") &&
        roundTrip((), 0)
      },
      test("Boolean") {
        avroSchema[Boolean]("\"boolean\"") &&
        roundTrip(true, 1) &&
        roundTrip(false, 1)
      },
      test("Boolean (decode error)") {
        val booleanCodec = Schema[Boolean].derive(AvroFormat.deriver)
        decodeError(Array.empty[Byte], booleanCodec, "Unexpected end of input")
      },
      test("Byte") {
        avroSchema[Byte]("\"int\"") &&
        roundTrip(1: Byte, 1) &&
        roundTrip(Byte.MinValue, 2) &&
        roundTrip(Byte.MaxValue, 2)
      },
      test("Byte (decode error)") {
        val intCodec  = Schema[Int].derive(AvroFormat.deriver)
        val byteCodec = Schema[Byte].derive(AvroFormat.deriver)
        decodeError(intCodec.encode(Byte.MinValue - 1), byteCodec, "Expected Byte") &&
        decodeError(intCodec.encode(Byte.MaxValue + 1), byteCodec, "Expected Byte") &&
        decodeError(Array.empty[Byte], byteCodec, "Unexpected end of input")
      },
      test("Short") {
        avroSchema[Short]("\"int\"") &&
        roundTrip(1: Short, 1) &&
        roundTrip(Short.MinValue, 3) &&
        roundTrip(Short.MaxValue, 3)
      },
      test("Short (decode error)") {
        val intCodec   = Schema[Int].derive(AvroFormat.deriver)
        val shortCodec = Schema[Short].derive(AvroFormat.deriver)
        decodeError(intCodec.encode(Short.MinValue - 1), shortCodec, "Expected Short") &&
        decodeError(intCodec.encode(Short.MaxValue + 1), shortCodec, "Expected Short") &&
        decodeError(Array.empty[Byte], shortCodec, "Unexpected end of input")
      },
      test("Int") {
        avroSchema[Int]("\"int\"") &&
        roundTrip(1, 1) &&
        roundTrip(Int.MinValue, 5) &&
        roundTrip(Int.MaxValue, 5)
      },
      test("Int (decode error)") {
        val intCodec = Schema[Int].derive(AvroFormat.deriver)
        val bytes    = intCodec.encode(Int.MaxValue)
        bytes(4) = 0xff.toByte
        decodeError(bytes, intCodec, "Invalid int encoding") &&
        decodeError(Array.empty[Byte], intCodec, "Unexpected end of input")
      },
      test("Long") {
        avroSchema[Long]("\"long\"") &&
        roundTrip(1L, 1) &&
        roundTrip(Long.MinValue, 10) &&
        roundTrip(Long.MaxValue, 10)
      },
      test("Long (decode error)") {
        val longCodec = Schema[Long].derive(AvroFormat.deriver)
        val bytes     = longCodec.encode(Long.MaxValue)
        bytes(9) = 0xff.toByte
        decodeError(bytes, longCodec, "Invalid long encoding") &&
        decodeError(Array.empty[Byte], longCodec, "Unexpected end of input")
      },
      test("Float") {
        avroSchema[Float]("\"float\"") &&
        roundTrip(42.0f, 4) &&
        roundTrip(Float.MinValue, 4) &&
        roundTrip(Float.MaxValue, 4)
      },
      test("Float (decode error)") {
        val floatCodec = Schema[Float].derive(AvroFormat.deriver)
        decodeError(new Array[Byte](3), floatCodec, "Unexpected end of input")
      },
      test("Double") {
        avroSchema[Double]("\"double\"") &&
        roundTrip(42.0, 8) &&
        roundTrip(Double.MinValue, 8) &&
        roundTrip(Double.MaxValue, 8)
      },
      test("Double (decode error)") {
        val floatCodec = Schema[Double].derive(AvroFormat.deriver)
        decodeError(new Array[Byte](7), floatCodec, "Unexpected end of input")
      },
      test("Char") {
        avroSchema[Char]("\"int\"") &&
        roundTrip('7', 1) &&
        roundTrip(Char.MinValue, 1) &&
        roundTrip(Char.MaxValue, 3)
      },
      test("Char (decode error)") {
        val intCodec  = Schema[Int].derive(AvroFormat.deriver)
        val charCodec = Schema[Char].derive(AvroFormat.deriver)
        decodeError(intCodec.encode(Char.MinValue - 1), charCodec, "Expected Char") &&
        decodeError(intCodec.encode(Char.MaxValue + 1), charCodec, "Expected Char")
      },
      test("String") {
        avroSchema[String]("\"string\"") &&
        roundTrip("Hello", 6) &&
        roundTrip("★\uD83C\uDFB8\uD83C\uDFA7⋆｡ °⋆", 24)
      },
      test("String (decode error)") {
        val stringCodec = Schema[String].derive(AvroFormat.deriver)
        decodeError(Array.empty[Byte], stringCodec, "Unexpected end of input") &&
        decodeError(Array[Byte](100, 42, 42, 42), stringCodec, "Unexpected end of input")
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
          "{\"type\":\"record\",\"name\":\"Duration\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"seconds\",\"type\":\"long\"},{\"name\":\"nanos\",\"type\":\"int\"}]}"
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
          "{\"type\":\"record\",\"name\":\"OffsetDateTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"},{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"},{\"name\":\"offsetSecond\",\"type\":\"int\"}]}"
        ) &&
        roundTrip(java.time.OffsetDateTime.parse("2025-07-18T08:29:13.121409459-07:00"), 14)
      },
      test("OffsetTime") {
        avroSchema[java.time.OffsetTime](
          "{\"type\":\"record\",\"name\":\"OffsetTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"},{\"name\":\"offsetSecond\",\"type\":\"int\"}]}"
        ) &&
        roundTrip(java.time.OffsetTime.parse("08:29:13.121409459-07:00"), 10)
      },
      test("Period") {
        avroSchema[java.time.Period](
          "{\"type\":\"record\",\"name\":\"Period\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"years\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"days\",\"type\":\"int\"}]}"
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
          "{\"type\":\"record\",\"name\":\"ZonedDateTime\",\"namespace\":\"java.time\",\"fields\":[{\"name\":\"year\",\"type\":\"int\"},{\"name\":\"month\",\"type\":\"int\"},{\"name\":\"day\",\"type\":\"int\"},{\"name\":\"hour\",\"type\":\"int\"},{\"name\":\"minute\",\"type\":\"int\"},{\"name\":\"second\",\"type\":\"int\"},{\"name\":\"nano\",\"type\":\"int\"},{\"name\":\"offsetSecond\",\"type\":\"int\"},{\"name\":\"zoneId\",\"type\":\"string\"}]}"
        ) &&
        roundTrip(java.time.ZonedDateTime.parse("2025-07-18T08:29:13.121409459+02:00[Europe/Warsaw]"), 27)
      },
      test("Currency") {
        avroSchema[java.util.Currency](
          "{\"type\":\"fixed\",\"name\":\"Currency\",\"namespace\":\"java.util\",\"size\":3}"
        ) &&
        roundTrip(java.util.Currency.getInstance("USD"), 3)
      },
      test("UUID") {
        avroSchema[java.util.UUID](
          "{\"type\":\"fixed\",\"name\":\"UUID\",\"namespace\":\"java.util\",\"size\":16}"
        ) &&
        roundTrip(UUID.randomUUID(), 16)
      }
    ),
    suite("records")(
      test("simple record") {
        avroSchema[Record1](
          "{\"type\":\"record\",\"name\":\"Record1\",\"namespace\":\"zio.blocks.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"bl\",\"type\":\"boolean\"},{\"name\":\"b\",\"type\":\"int\"},{\"name\":\"sh\",\"type\":\"int\"},{\"name\":\"i\",\"type\":\"int\"},{\"name\":\"l\",\"type\":\"long\"},{\"name\":\"f\",\"type\":\"float\"},{\"name\":\"d\",\"type\":\"double\"},{\"name\":\"c\",\"type\":\"int\"},{\"name\":\"s\",\"type\":\"string\"}]}"
        ) &&
        roundTrip(Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"), 22)
      },
      test("simple record (decode error)") {
        val record1Codec = Schema[Record1].derive(AvroFormat.deriver)
        decodeError(
          Array.empty[Byte],
          record1Codec,
          SchemaError.expectationMismatch(List(DynamicOptic.Node.Field("bl")), "Unexpected end of input")
        ) &&
        decodeError(
          Array[Byte](100, 42, 42, 42),
          record1Codec,
          SchemaError.expectationMismatch(List(DynamicOptic.Node.Field("l")), "Unexpected end of input")
        )
      },
      test("nested record") {
        avroSchema[Record2](
          "{\"type\":\"record\",\"name\":\"Record2\",\"namespace\":\"zio.blocks.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"r1_1\",\"type\":{\"type\":\"record\",\"name\":\"Record1\",\"fields\":[{\"name\":\"bl\",\"type\":\"boolean\"},{\"name\":\"b\",\"type\":\"int\"},{\"name\":\"sh\",\"type\":\"int\"},{\"name\":\"i\",\"type\":\"int\"},{\"name\":\"l\",\"type\":\"long\"},{\"name\":\"f\",\"type\":\"float\"},{\"name\":\"d\",\"type\":\"double\"},{\"name\":\"c\",\"type\":\"int\"},{\"name\":\"s\",\"type\":\"string\"}]}},{\"name\":\"r1_2\",\"type\":\"Record1\"}]}"
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
          "{\"type\":\"record\",\"name\":\"Recursive\",\"namespace\":\"zio.blocks.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"i\",\"type\":\"int\"},{\"name\":\"ln\",\"type\":{\"type\":\"array\",\"items\":\"Recursive\"}}]}"
        ) &&
        roundTrip(Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))), 8)
      },
      test("record with unit and variant fields") {
        avroSchema[Record4](
          "{\"type\":\"record\",\"name\":\"Record4\",\"namespace\":\"zio.blocks.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"hidden\",\"type\":\"null\"},{\"name\":\"optKey\",\"type\":[{\"type\":\"record\",\"name\":\"None\",\"namespace\":\"scala\",\"fields\":[]},{\"type\":\"record\",\"name\":\"Some_10c51065\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"value\",\"type\":\"string\"}]}]}]}"
        ) &&
        roundTrip(Record4((), Some("VVV")), 5) &&
        roundTrip(Record4((), None), 1)
      },
      test("record with a custom codec for primitives injected by optic") {
        val codec: AvroBinaryCodec[Record1] = Record1.schema
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
          "{\"type\":\"record\",\"name\":\"Record1\",\"namespace\":\"zio.blocks.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"bl\",\"type\":\"boolean\"},{\"name\":\"b\",\"type\":\"int\"},{\"name\":\"sh\",\"type\":\"int\"},{\"name\":\"i\",\"type\":\"string\"},{\"name\":\"l\",\"type\":\"long\"},{\"name\":\"f\",\"type\":\"float\"},{\"name\":\"d\",\"type\":\"double\"},{\"name\":\"c\",\"type\":\"int\"},{\"name\":\"s\",\"type\":\"string\"}]}",
          codec
        ) &&
        roundTrip(Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"), 23, codec)
      },
      test("record with a custom codec for primitives injected by type name") {
        val codec: AvroBinaryCodec[Record1] = Record1.schema
          .deriving(AvroFormat.deriver)
          .instance(
            TypeName.int,
            new AvroBinaryCodec[Int](AvroBinaryCodec.intType) {
              val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.STRING)

              def decodeUnsafe(decoder: BinaryDecoder): Int = java.lang.Integer.valueOf(decoder.readString())

              def encode(value: Int, encoder: BinaryEncoder): Unit = encoder.writeString(value.toString)
            }
          )
          .derive
        avroSchema(
          "{\"type\":\"record\",\"name\":\"Record1\",\"namespace\":\"zio.blocks.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"bl\",\"type\":\"boolean\"},{\"name\":\"b\",\"type\":\"int\"},{\"name\":\"sh\",\"type\":\"int\"},{\"name\":\"i\",\"type\":\"string\"},{\"name\":\"l\",\"type\":\"long\"},{\"name\":\"f\",\"type\":\"float\"},{\"name\":\"d\",\"type\":\"double\"},{\"name\":\"c\",\"type\":\"int\"},{\"name\":\"s\",\"type\":\"string\"}]}",
          codec
        ) &&
        roundTrip(Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"), 23, codec)
      },
      test("record with a custom codec for unit injected by optic") {
        val codec: AvroBinaryCodec[Record4] = Record4.schema
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
          "{\"type\":\"record\",\"name\":\"Record4\",\"namespace\":\"zio.blocks.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"hidden\",\"type\":\"string\"},{\"name\":\"optKey\",\"type\":[{\"type\":\"record\",\"name\":\"None\",\"namespace\":\"scala\",\"fields\":[]},{\"type\":\"record\",\"name\":\"Some_10c51065\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"value\",\"type\":\"string\"}]}]}]}",
          codec
        ) &&
        roundTrip(Record4((), Some("VVV")), 9, codec)
      },
      test("record with a custom codec for None injected by optic") {
        val codec: AvroBinaryCodec[Record4] = Record4.schema
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
          "{\"type\":\"record\",\"name\":\"Record4\",\"namespace\":\"zio.blocks.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"hidden\",\"type\":\"null\"},{\"name\":\"optKey\",\"type\":[\"string\",{\"type\":\"record\",\"name\":\"Some_10c51065\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"value\",\"type\":\"string\"}]}]}]}",
          codec
        ) &&
        roundTrip(Record4((), Some("VVV")), 5, codec) &&
        roundTrip(Record4((), None), 5, codec)
      },
      test("record with a custom codec for nested record injected by optic") {
        val codec: AvroBinaryCodec[Record2] = Record2.schema
          .deriving(AvroFormat.deriver)
          .instance(
            Record2.r1_1,
            new AvroBinaryCodec[Record1]() {
              private val codec = Record1.schema.derive(AvroFormat.deriver)

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
          .instance(
            Record2.r1_2,
            new AvroBinaryCodec[Record1]() {
              private val codec = Record1.schema.derive(AvroFormat.deriver)

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
        roundTrip(
          Record2(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(false, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "WWW")
          ),
          46,
          codec
        ) &&
        roundTrip(Record2(null, null), 2, codec)
      },
      test("record with a custom codec for nested primitives injected by optic") {
        val codec: AvroBinaryCodec[Record2] = Record2.schema
          .deriving(AvroFormat.deriver)
          .instance(
            TypeName.int,
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
        val codec: AvroBinaryCodec[Record2] = Record2.schema
          .deriving(AvroFormat.deriver)
          .instance(
            Record1.schema.reflect.typeName,
            new AvroBinaryCodec[Record1]() {
              private val codec = Record1.schema.derive(AvroFormat.deriver)

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
        lazy val codec: AvroBinaryCodec[Recursive] = Recursive.schema
          .deriving(AvroFormat.deriver)
          .instance(
            Recursive.ln,
            new AvroBinaryCodec[List[Recursive]]() {
              val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.STRING) // FIXME: use proper Avro schema

              def decodeUnsafe(decoder: BinaryDecoder): List[Recursive] = {
                val builder = List.newBuilder[Recursive]
                val size    = decoder.readInt()
                var idx     = 0
                while (idx < size) {
                  val _ =
                    try decoder.readString()
                    catch {
                      case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                    }
                  val v =
                    try codec.decodeUnsafe(decoder)
                    catch {
                      case error if NonFatal(error) => decodeError(new DynamicOptic.Node.AtIndex(idx), error)
                    }
                  builder.addOne(v)
                  idx += 1
                }
                val _ = decoder.readInt()
                builder.result()
              }

              def encode(value: List[Recursive], encoder: BinaryEncoder): Unit = {
                encoder.writeInt(value.size)
                val it  = value.iterator
                var idx = 0
                while (it.hasNext) {
                  encoder.writeString(idx.toString)
                  codec.encode(it.next(), encoder)
                  idx += 1
                }
                encoder.writeInt(0)
              }
            }
          )
          .derive
        shortRoundTrip(Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))), 13, codec)
      }
    ),
    suite("sequences")(
      test("primitive values") {
        implicit val arrayOfUnitSchema: Schema[Array[Unit]]       = Schema.derived
        implicit val arrayOfBooleanSchema: Schema[Array[Boolean]] = Schema.derived
        implicit val arrayOfByteSchema: Schema[Array[Byte]]       = Schema.derived
        implicit val arrayOfShortSchema: Schema[Array[Short]]     = Schema.derived
        implicit val arrayOfCharSchema: Schema[Array[Char]]       = Schema.derived

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
        avroSchema[List[Int]]("{\"type\":\"array\",\"items\":\"int\"}") &&
        roundTrip(List(1, 2, 3), 5) &&
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
        roundTrip(List(new java.util.UUID(1L, 1L), new java.util.UUID(2L, 2L), new java.util.UUID(3L, 3L)), 50)
      },
      test("primitive values (decode error)") {
        val intListCodec = Schema[List[Int]].derive(AvroFormat.deriver)
        decodeError(Array.empty[Byte], intListCodec, "Unexpected end of input") &&
        decodeError(
          Array[Byte](100, 42, 42, 42),
          intListCodec,
          SchemaError.expectationMismatch(List(DynamicOptic.Node.AtIndex(3)), "Unexpected end of input")
        ) &&
        decodeError(Array(0x01.toByte), intListCodec, "Expected positive collection part size, got -1") &&
        decodeError(
          Array(0xfe.toByte, 0xff.toByte, 0xff.toByte, 0xff.toByte, 0x0f.toByte),
          intListCodec,
          "Expected collection size not greater than 2147483639, got 2147483647"
        )
      },
      test("complex values") {
        avroSchema[List[Record1]](
          "{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"Record1\",\"namespace\":\"zio.blocks.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"bl\",\"type\":\"boolean\"},{\"name\":\"b\",\"type\":\"int\"},{\"name\":\"sh\",\"type\":\"int\"},{\"name\":\"i\",\"type\":\"int\"},{\"name\":\"l\",\"type\":\"long\"},{\"name\":\"f\",\"type\":\"float\"},{\"name\":\"d\",\"type\":\"double\"},{\"name\":\"c\",\"type\":\"int\"},{\"name\":\"s\",\"type\":\"string\"}]}}"
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
          "{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"Recursive\",\"namespace\":\"zio.blocks.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"i\",\"type\":\"int\"},{\"name\":\"ln\",\"type\":{\"type\":\"array\",\"items\":\"Recursive\"}}]}}"
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
        roundTrip(Map("VVV" -> new java.util.UUID(1L, 1L), "WWW" -> new java.util.UUID(2L, 2L)), 42)
      },
      test("string keys and primitive values (decode error)") {
        val stringToIntMapCodec = Schema[Map[String, Int]].derive(AvroFormat.deriver)
        decodeError(Array.empty[Byte], stringToIntMapCodec, "Unexpected end of input") &&
        decodeError(
          Array[Byte](100),
          stringToIntMapCodec,
          SchemaError.expectationMismatch(List(DynamicOptic.Node.AtIndex(0)), "Unexpected end of input")
        ) &&
        decodeError(Array(0x01.toByte), stringToIntMapCodec, "Expected positive map part size, got -1") &&
        decodeError(
          Array(0xfe.toByte, 0xff.toByte, 0xff.toByte, 0xff.toByte, 0x0f.toByte),
          stringToIntMapCodec,
          "Expected map size not greater than 2147483639, got 2147483647"
        )
      },
      test("string keys and complex values") {
        avroSchema[Map[String, Record1]](
          "{\"type\":\"map\",\"values\":{\"type\":\"record\",\"name\":\"Record1\",\"namespace\":\"zio.blocks.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"bl\",\"type\":\"boolean\"},{\"name\":\"b\",\"type\":\"int\"},{\"name\":\"sh\",\"type\":\"int\"},{\"name\":\"i\",\"type\":\"int\"},{\"name\":\"l\",\"type\":\"long\"},{\"name\":\"f\",\"type\":\"float\"},{\"name\":\"d\",\"type\":\"double\"},{\"name\":\"c\",\"type\":\"int\"},{\"name\":\"s\",\"type\":\"string\"}]}}"
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
          "{\"type\":\"map\",\"values\":{\"type\":\"record\",\"name\":\"Recursive\",\"namespace\":\"zio.blocks.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"i\",\"type\":\"int\"},{\"name\":\"ln\",\"type\":{\"type\":\"array\",\"items\":\"Recursive\"}}]}}"
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
          "{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"Tuple2_86f12e54\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"_1\",\"type\":\"int\"},{\"name\":\"_2\",\"type\":\"long\"}]}}"
        ) &&
        roundTrip(Map(1 -> 1L, 2 -> 2L), 6)
      },
      test("non string key map (decode error)") {
        val intToLongMapCodec = Schema[Map[Int, Long]].derive(AvroFormat.deriver)
        decodeError(Array.empty[Byte], intToLongMapCodec, "Unexpected end of input") &&
        decodeError(
          Array[Byte](100),
          intToLongMapCodec,
          SchemaError.expectationMismatch(List(DynamicOptic.Node.AtIndex(0)), "Unexpected end of input")
        ) &&
        decodeError(Array(0x01.toByte), intToLongMapCodec, "Expected positive map part size, got -1") &&
        decodeError(
          Array(0xfe.toByte, 0xff.toByte, 0xff.toByte, 0xff.toByte, 0x0f.toByte),
          intToLongMapCodec,
          "Expected map size not greater than 2147483639, got 2147483647"
        )
      },
      test("non string key with recursive values") {
        avroSchema[Map[Recursive, Int]](
          "{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"Tuple2_7185acfb\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"_1\",\"type\":{\"type\":\"record\",\"name\":\"Recursive\",\"namespace\":\"zio.blocks.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"i\",\"type\":\"int\"},{\"name\":\"ln\",\"type\":{\"type\":\"array\",\"items\":\"Recursive\"}}]}},{\"name\":\"_2\",\"type\":\"int\"}]}}"
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
          "{\"type\":\"map\",\"values\":{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"Tuple2_86f12e54\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"_1\",\"type\":\"int\"},{\"name\":\"_2\",\"type\":\"long\"}]}}}"
        ) &&
        roundTrip(Map("VVV" -> Map(1 -> 1L, 2 -> 2L)), 12) &&
        avroSchema[Map[Map[Int, Long], String]](
          "{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"Tuple2_70fbca0d\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"_1\",\"type\":{\"type\":\"array\",\"items\":{\"type\":\"record\",\"name\":\"Tuple2_86f12e54\",\"fields\":[{\"name\":\"_1\",\"type\":\"int\"},{\"name\":\"_2\",\"type\":\"long\"}]}}},{\"name\":\"_2\",\"type\":\"string\"}]}}"
        ) &&
        roundTrip(Map(Map(1 -> 1L, 2 -> 2L) -> "WWW"), 12)
      }
    ),
    suite("enums")(
      test("constant values") {
        avroSchema[TrafficLight](
          "[{\"type\":\"record\",\"name\":\"Red\",\"namespace\":\"zio.blocks.avro.AvroFormatSpec.TrafficLight\",\"fields\":[]},{\"type\":\"record\",\"name\":\"Yellow\",\"namespace\":\"zio.blocks.avro.AvroFormatSpec.TrafficLight\",\"fields\":[]},{\"type\":\"record\",\"name\":\"Green\",\"namespace\":\"zio.blocks.avro.AvroFormatSpec.TrafficLight\",\"fields\":[]}]"
        ) &&
        roundTrip[TrafficLight](TrafficLight.Green, 1) &&
        roundTrip[TrafficLight](TrafficLight.Yellow, 1) &&
        roundTrip[TrafficLight](TrafficLight.Red, 1)
      },
      test("constant values (decode error)") {
        val trafficLightCodec = Schema[TrafficLight].derive(AvroFormat.deriver)
        val bytes             = trafficLightCodec.encode(TrafficLight.Red)
        bytes(0) = 42
        decodeError(bytes, trafficLightCodec, "Expected enum index from 0 to 2, got 21")
      },
      test("option") {
        avroSchema[Option[Int]](
          "[{\"type\":\"record\",\"name\":\"None\",\"namespace\":\"scala\",\"fields\":[]},{\"type\":\"record\",\"name\":\"Some_ccb86a7f\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]}]"
        ) &&
        roundTrip(Option(42), 2) &&
        roundTrip[Option[Int]](None, 1)
      },
      test("option (decode error)") {
        val intOptionCodec = Schema[Option[Int]].derive(AvroFormat.deriver)
        val bytes          = intOptionCodec.encode(Some(1))
        bytes(0) = 42
        decodeError(bytes, intOptionCodec, "Expected enum index from 0 to 1, got 21")
      },
      test("either") {
        avroSchema[Either[String, Int]](
          "[{\"type\":\"record\",\"name\":\"Left_e90cad65\",\"namespace\":\"scala.util\",\"fields\":[{\"name\":\"value\",\"type\":\"string\"}]},{\"type\":\"record\",\"name\":\"Right_e90cad65\",\"namespace\":\"scala.util\",\"fields\":[{\"name\":\"value\",\"type\":\"int\"}]}]"
        ) &&
        roundTrip[Either[String, Int]](Right(42), 2) &&
        roundTrip[Either[String, Int]](Left("VVV"), 5)
      }
    ),
    suite("wrapper")(
      test("top-level") {
        avroSchema[UserId]("\"long\"") &&
        roundTrip[UserId](UserId(1234567890123456789L), 9) &&
        avroSchema[Email]("\"string\"") &&
        roundTrip[Email](Email("john@gmail.com"), 15)
      },
      test("top-level (decode error)") {
        val emailCodec = Schema[Email].derive(AvroFormat.deriver)
        val bytes      = emailCodec.encode(Email("test@gmail.com"))
        bytes(5) = 42
        decodeError(bytes, emailCodec, "Expected Email")
      },
      test("as a record field") {
        avroSchema[Record3](
          "{\"type\":\"record\",\"name\":\"Record3\",\"namespace\":\"zio.blocks.avro.AvroFormatSpec\",\"fields\":[{\"name\":\"userId\",\"type\":\"long\"},{\"name\":\"email\",\"type\":\"string\"}]}"
        ) &&
        roundTrip[Record3](Record3(UserId(1234567890123456789L), Email("backup@gmail.com")), 26)
      }
    ),
    suite("dynamic value")(
      test("top-level") {
        shortRoundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Int(1)), 3) &&
        shortRoundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.String("VVV")), 6) &&
        shortRoundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.UUID(UUID.randomUUID())), 18) &&
        shortRoundTrip[DynamicValue](
          DynamicValue.Record(
            Vector(
              ("i", DynamicValue.Primitive(PrimitiveValue.Int(1))),
              ("s", DynamicValue.Primitive(PrimitiveValue.String("VVV")))
            )
          ),
          16
        ) &&
        shortRoundTrip[DynamicValue](DynamicValue.Variant("Int", DynamicValue.Primitive(PrimitiveValue.Int(1))), 8) &&
        shortRoundTrip[DynamicValue](
          DynamicValue.Sequence(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.String("VVV"))
            )
          ),
          12
        ) &&
        shortRoundTrip[DynamicValue](
          DynamicValue.Map(
            Vector(
              (DynamicValue.Primitive(PrimitiveValue.Long(1L)), DynamicValue.Primitive(PrimitiveValue.Int(1))),
              (DynamicValue.Primitive(PrimitiveValue.Long(2L)), DynamicValue.Primitive(PrimitiveValue.String("VVV")))
            )
          ),
          18
        )
      },
      test("top-level (decode error)") {
        val dynamicValueCodec = Schema[DynamicValue].derive(AvroFormat.deriver)
        val bytes             = dynamicValueCodec.encode(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        bytes(0) = 42
        decodeError(bytes, dynamicValueCodec, "Expected enum index from 0 to 4, got 21") &&
        decodeError(Array.empty[Byte], dynamicValueCodec, "Unexpected end of input") &&
        decodeError(
          Array[Byte](2),
          dynamicValueCodec,
          SchemaError.expectationMismatch(
            List(DynamicOptic.Node.Field("fields"), DynamicOptic.Node.Case("Record")),
            "Unexpected end of input"
          )
        ) &&
        decodeError(
          Array[Byte](2, 0x01.toByte),
          dynamicValueCodec,
          SchemaError.expectationMismatch(
            List(DynamicOptic.Node.Field("fields"), DynamicOptic.Node.Case("Record")),
            "Expected positive collection part size, got -1"
          )
        ) &&
        decodeError(
          Array[Byte](6, 0x01.toByte),
          dynamicValueCodec,
          SchemaError.expectationMismatch(
            List(DynamicOptic.Node.Field("elements"), DynamicOptic.Node.Case("Sequence")),
            "Expected positive collection part size, got -1"
          )
        ) &&
        decodeError(
          Array[Byte](8, 0x01.toByte),
          dynamicValueCodec,
          SchemaError.expectationMismatch(
            List(DynamicOptic.Node.Field("entries"), DynamicOptic.Node.Case("Map")),
            "Expected positive collection part size, got -1"
          )
        ) &&
        decodeError(
          Array[Byte](2, 0xfe.toByte, 0xff.toByte, 0xff.toByte, 0xff.toByte, 0x0f.toByte),
          dynamicValueCodec,
          SchemaError.expectationMismatch(
            List(DynamicOptic.Node.Field("fields"), DynamicOptic.Node.Case("Record")),
            "Expected collection size not greater than 2147483639, got 2147483647"
          )
        ) &&
        decodeError(
          Array[Byte](6, 0xfe.toByte, 0xff.toByte, 0xff.toByte, 0xff.toByte, 0x0f.toByte),
          dynamicValueCodec,
          SchemaError.expectationMismatch(
            List(DynamicOptic.Node.Field("elements"), DynamicOptic.Node.Case("Sequence")),
            "Expected collection size not greater than 2147483639, got 2147483647"
          )
        ) &&
        decodeError(
          Array[Byte](8, 0xfe.toByte, 0xff.toByte, 0xff.toByte, 0xff.toByte, 0x0f.toByte),
          dynamicValueCodec,
          SchemaError.expectationMismatch(
            List(DynamicOptic.Node.Field("entries"), DynamicOptic.Node.Case("Map")),
            "Expected collection size not greater than 2147483639, got 2147483647"
          )
        )
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
    implicit val schema: Schema[UserId] = Schema.derived.wrapTotal(x => new UserId(x), _.value)
  }

  case class Email(value: String)

  object Email {
    private[this] val EmailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".r

    implicit val schema: Schema[Email] = new Schema(
      new Reflect.Wrapper[Binding, Email, String](
        Schema[String].reflect,
        TypeName(Namespace(Seq("zio", "blocks", "avro"), Seq("AvroFormatSpec")), "Email"),
        new Binding.Wrapper(
          {
            case x @ EmailRegex(_*) => new Right(new Email(x))
            case _                  => new Left("Expected Email")
          },
          _.value
        )
      )
    )
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
}
