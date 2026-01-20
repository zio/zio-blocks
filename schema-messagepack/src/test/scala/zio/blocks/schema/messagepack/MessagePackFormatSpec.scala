package zio.blocks.schema.messagepack

import zio.blocks.schema._

import zio.blocks.schema.messagepack.MessagePackTestUtils._
import zio.test._
import zio.test.Assertion._
import java.util.{Currency, UUID}
import org.msgpack.core.MessagePack
import scala.collection.immutable.ArraySeq

object MessagePackFormatSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("MessagePackFormatSpec")(
    suite("primitives")(
      test("Unit") {
        roundTrip(())
      },
      test("Boolean true") {
        roundTrip(true)
      },
      test("Boolean false") {
        roundTrip(false)
      },
      test("Boolean error") {
        decodeError[Boolean](Array.empty[Byte], "Unexpected end of input")
      },
      test("Byte") {
        roundTrip(1: Byte) &&
        roundTrip(Byte.MinValue) &&
        roundTrip(Byte.MaxValue) &&
        decodeError[Byte](Array.empty[Byte], "Unexpected end of input")
      },
      test("Short") {
        roundTrip(1: Short) &&
        roundTrip(Short.MinValue) &&
        roundTrip(Short.MaxValue) &&
        decodeError[Short](Array.empty[Byte], "Unexpected end of input")
      },
      test("Int") {
        roundTrip(1) &&
        roundTrip(Int.MinValue) &&
        roundTrip(Int.MaxValue) &&
        decodeError[Int](Array.empty[Byte], "Unexpected end of input")
      },
      test("Long") {
        roundTrip(1L) &&
        roundTrip(Long.MinValue) &&
        roundTrip(Long.MaxValue) &&
        decodeError[Long](Array.empty[Byte], "Unexpected end of input")
      },
      test("Float") {
        roundTrip(42.0f) &&
        roundTrip(Float.MinValue) &&
        roundTrip(Float.MaxValue) &&
        decodeError[Float](Array.empty[Byte], "Unexpected end of input")
      },
      test("Double") {
        roundTrip(42.0) &&
        roundTrip(Double.MinValue) &&
        roundTrip(Double.MaxValue) &&
        decodeError[Double](Array.empty[Byte], "Unexpected end of input")
      },
      test("Char") {
        roundTrip('7') &&
        roundTrip(Char.MinValue) &&
        roundTrip(Char.MaxValue) &&
        decodeError[Char](Array.empty[Byte], "Unexpected end of input")
      },
      test("String") {
        roundTrip("Hello") &&
        roundTrip("★\uD83C\uDFB8\uD83C\uDFA7⋆｡ °⋆") &&
        roundTrip("") &&
        decodeError[String](Array.empty[Byte], "Unexpected end of input")
      },
      test("BigInt") {
        roundTrip(BigInt("9" * 20)) &&
        roundTrip(BigInt("-" + "9" * 20)) &&
        roundTrip(BigInt(0)) &&
        roundTrip(BigInt(-1))
      },
      test("BigDecimal") {
        roundTrip(BigDecimal("9." + "9" * 20 + "E+12345")) &&
        roundTrip(BigDecimal("-9." + "9" * 20 + "E+12345")) &&
        roundTrip(BigDecimal(0))
      },
      test("DayOfWeek") {
        roundTrip(java.time.DayOfWeek.WEDNESDAY)
      },
      test("Duration") {
        roundTrip(java.time.Duration.ofNanos(1234567890123456789L))
      },
      test("Instant") {
        roundTrip(java.time.Instant.parse("2025-07-18T08:29:13.121409459Z"))
      },
      test("LocalDate") {
        roundTrip(java.time.LocalDate.parse("2025-07-18"))
      },
      test("LocalDateTime") {
        roundTrip(java.time.LocalDateTime.parse("2025-07-18T08:29:13.121409459"))
      },
      test("LocalTime") {
        roundTrip(java.time.LocalTime.parse("08:29:13.121409459"))
      },
      test("Month") {
        roundTrip(java.time.Month.of(12))
      },
      test("MonthDay") {
        roundTrip(java.time.MonthDay.of(12, 31))
      },
      test("OffsetDateTime") {
        roundTrip(java.time.OffsetDateTime.parse("2025-07-18T08:29:13.121409459-07:00"))
      },
      test("OffsetTime") {
        roundTrip(java.time.OffsetTime.parse("08:29:13.121409459-07:00"))
      },
      test("Period") {
        roundTrip(java.time.Period.of(1, 12, 31))
      },
      test("Year") {
        roundTrip(java.time.Year.of(2025))
      },
      test("YearMonth") {
        roundTrip(java.time.YearMonth.of(2025, 7))
      },
      test("ZoneId") {
        roundTrip(java.time.ZoneId.of("UTC"))
      },
      test("ZoneOffset") {
        roundTrip(java.time.ZoneOffset.ofTotalSeconds(3600))
      },
      test("ZonedDateTime") {
        roundTrip(java.time.ZonedDateTime.parse("2025-07-18T08:29:13.121409459+02:00[Europe/Warsaw]"))
      },
      test("Currency") {
        roundTrip(Currency.getInstance("USD"))
      },
      test("UUID") {
        roundTrip(UUID.randomUUID())
      }
    ),
    suite("records")(
      test("simple record") {
        roundTrip(Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")) &&
        decodeError[Record1](Array.empty[Byte], "Unexpected end of input")
      },
      test("nested record") {
        roundTrip(
          Record2(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV")
          )
        )
      },
      test("recursive record") {
        roundTrip(Recursive(1, List(Recursive(2, List(Recursive(3, Nil))))))
      },
      test("record with unit and optional fields") {
        roundTrip(Record4((), Some("VVV"))) &&
        roundTrip(Record4((), None))
      },
      test("forward-compatible record decoding - missing optional/collection fields") {
        val codec  = RecordWithDefaults.schema.derive(MessagePackFormat.deriver)
        val packer = MessagePack.newDefaultBufferPacker()
        val bytes  =
          try {
            packer.packMapHeader(1)
            packer.packString("name")
            packer.packString("test")
            packer.toByteArray
          } finally packer.close()

        assert(codec.decode(bytes))(isRight(equalTo(RecordWithDefaults("test", None, Nil, Map.empty))))
      },
      test("forward-compatible record decoding - field order independent") {
        val codec = SimpleRecord.schema.derive(MessagePackFormat.deriver)

        val outOfOrderPacker = MessagePack.newDefaultBufferPacker()
        val outOfOrderBytes  =
          try {
            outOfOrderPacker.packMapHeader(2)
            outOfOrderPacker.packString("value")
            outOfOrderPacker.packInt(42)
            outOfOrderPacker.packString("name")
            outOfOrderPacker.packString("test")
            outOfOrderPacker.toByteArray
          } finally outOfOrderPacker.close()

        val withUnknownFieldPacker = MessagePack.newDefaultBufferPacker()
        val withUnknownFieldBytes  =
          try {
            withUnknownFieldPacker.packMapHeader(3)
            withUnknownFieldPacker.packString("unknown")
            withUnknownFieldPacker.packString("ignored")
            withUnknownFieldPacker.packString("name")
            withUnknownFieldPacker.packString("test")
            withUnknownFieldPacker.packString("value")
            withUnknownFieldPacker.packInt(42)
            withUnknownFieldPacker.toByteArray
          } finally withUnknownFieldPacker.close()

        assert(codec.decode(outOfOrderBytes))(isRight(equalTo(SimpleRecord("test", 42)))) &&
        assert(codec.decode(withUnknownFieldBytes))(isRight(equalTo(SimpleRecord("test", 42))))
      }
    ),
    suite("variants")(
      test("simple enum") {
        roundTrip[Enum1](Enum1.Case1) &&
        roundTrip[Enum1](Enum1.Case2) &&
        roundTrip[Enum1](Enum1.Case3)
      },
      test("sum type with case classes") {
        roundTrip[Sum1](Sum1.C1(1)) &&
        roundTrip[Sum1](Sum1.C2("WWW"))
      },
      test("nested sum type") {
        roundTrip[Sum2](Sum2.C1(Sum1.C1(1))) &&
        roundTrip[Sum2](Sum2.C1(Sum1.C2("WWW"))) &&
        roundTrip[Sum2](Sum2.C2("VVV"))
      },
      test("Option as variant") {
        roundTrip[Option[Int]](Some(42)) &&
        roundTrip[Option[Int]](None) &&
        roundTrip[Option[String]](Some("test")) &&
        roundTrip[Option[String]](None)
      }
    ),
    suite("sequences")(
      test("primitive arrays") {
        implicit val arrayOfBooleanSchema: Schema[Array[Boolean]]   = Schema.derived
        implicit val arrayOfByteSchema: Schema[Array[Byte]]         = Schema.derived
        implicit val arrayOfShortSchema: Schema[Array[Short]]       = Schema.derived
        implicit val arrayOfCharSchema: Schema[Array[Char]]         = Schema.derived
        implicit val arrayOfIntSchema: Schema[Array[Int]]           = Schema.derived
        implicit val arrayOfLongSchema: Schema[Array[Long]]         = Schema.derived
        implicit val arrayOfFloatSchema: Schema[Array[Float]]       = Schema.derived
        implicit val arrayOfDoubleSchema: Schema[Array[Double]]     = Schema.derived
        implicit val arraySeqOfFloatSchema: Schema[ArraySeq[Float]] = Schema.derived
        implicit val listOfListSchema: Schema[List[List[Int]]]      = Schema.derived

        roundTrip[Array[Boolean]](Array[Boolean](true, false, true)) &&
        roundTrip[Array[Byte]](Array[Byte](1: Byte, 2: Byte, 3: Byte)) &&
        roundTrip[Array[Short]](Array[Short](1: Short, 2: Short, 3: Short)) &&
        roundTrip[Array[Char]](Array('1', '2', '3')) &&
        roundTrip[Array[Int]](Array[Int](1, 2, 3)) &&
        roundTrip[Array[Long]](Array[Long](1, 2, 3)) &&
        roundTrip[Array[Float]](Array[Float](1.0f, 2.0f, 3.0f)) &&
        roundTrip[Array[Double]](Array[Double](1.0, 2.0, 3.0)) &&
        roundTrip[ArraySeq[Float]](ArraySeq(1.0f, 2.0f, 3.0f)) &&
        roundTrip[List[List[Int]]](List(List(1, 2), List(3, 4, 5), List()))
      },
      test("List[Int]") {
        roundTrip((1 to 100).toList)
      },
      test("Set[Long]") {
        roundTrip(Set(1L, 2L, 3L))
      },
      test("Vector[Double]") {
        roundTrip(Vector(1.0, 2.0, 3.0))
      },
      test("List[String]") {
        roundTrip(List("foo", "bar", "baz"))
      },
      test("List of records") {
        roundTrip(
          List(
            SimpleRecord("first", 1),
            SimpleRecord("second", 2),
            SimpleRecord("third", 3)
          )
        )
      },
      test("empty list") {
        roundTrip(List.empty[Int])
      }
    ),
    suite("maps")(
      test("Map[String, Int]") {
        roundTrip(Map("a" -> 1, "b" -> 2, "c" -> 3))
      },
      test("Map[String, String]") {
        roundTrip(Map("key1" -> "value1", "key2" -> "value2"))
      },
      test("Map[Int, String]") {
        roundTrip(Map(1 -> "one", 2 -> "two", 3 -> "three"))
      },
      test("Map[String, Record]") {
        roundTrip(
          Map(
            "first"  -> SimpleRecord("first", 1),
            "second" -> SimpleRecord("second", 2)
          )
        )
      },
      test("empty map") {
        roundTrip(Map.empty[String, Int])
      },
      test("nested maps") {
        implicit val mapOfMapSchema: Schema[Map[String, Map[String, Int]]] = Schema.derived
        roundTrip[Map[String, Map[String, Int]]](
          Map(
            "outer1" -> Map("inner1" -> 1, "inner2" -> 2),
            "outer2" -> Map("inner3" -> 3)
          )
        )
      }
    ),
    suite("wrappers")(
      test("UserId wrapper") {
        roundTrip(UserId(1234567890123456789L))
      },
      test("record with wrappers") {
        roundTrip(Record3(UserId(1234567890123456789L), "test@example.com"))
      }
    ),
    suite("either")(
      test("Either[String, Int] - Left") {
        roundTrip[Either[String, Int]](Left("error"))
      },
      test("Either[String, Int] - Right") {
        roundTrip[Either[String, Int]](Right(42))
      },
      test("Either[Record, Record] - Left") {
        roundTrip[Either[SimpleRecord, SimpleRecord]](Left(SimpleRecord("left", 1)))
      },
      test("Either[Record, Record] - Right") {
        roundTrip[Either[SimpleRecord, SimpleRecord]](Right(SimpleRecord("right", 2)))
      }
    ),
    suite("complex nested structures")(
      test("deeply nested record") {
        roundTrip(
          DeepNested(
            level1 = Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            level2 = Record2(
              Record1(false, 10: Byte, 20: Short, 30, 40L, 50.0f, 60.0, '8', "WWW"),
              Record1(true, 11: Byte, 21: Short, 31, 41L, 51.0f, 61.0, '9', "XXX")
            )
          )
        )
      },
      test("record with list of variants") {
        roundTrip(
          RecordWithVariants(
            List(
              Sum1.C1(1),
              Sum1.C2("hello"),
              Sum1.C1(2)
            )
          )
        )
      },
      test("record with map of lists") {
        implicit val mapOfListsSchema: Schema[Map[String, List[Int]]] = Schema.derived
        roundTrip[Map[String, List[Int]]](
          Map(
            "a" -> List(1, 2, 3),
            "b" -> List(4, 5),
            "c" -> List()
          )
        )
      },
      test("list of maps") {
        implicit val listOfMapsSchema: Schema[List[Map[String, Int]]] = Schema.derived
        roundTrip[List[Map[String, Int]]](
          List(
            Map("x" -> 1, "y" -> 2),
            Map("z" -> 3),
            Map()
          )
        )
      }
    ),
    suite("edge cases")(
      test("empty string") {
        roundTrip("")
      },
      test("very long string") {
        roundTrip("x" * 10000)
      },
      test("special characters in string") {
        roundTrip("Hello\n\t\r\"World\"")
      },
      test("unicode in string") {
        roundTrip("こんにちは世界 🌍")
      },
      test("zero int") {
        roundTrip(0)
      },
      test("negative numbers") {
        roundTrip(-42) &&
        roundTrip(-123456789L) &&
        roundTrip(-3.14f) &&
        roundTrip(-2.718281828)
      },
      test("NaN and Infinity") {
        // NaN requires special handling because NaN != NaN by IEEE 754
        val floatCodec      = implicitly[Schema[Float]].derive(MessagePackFormat.deriver)
        val encodedFloatNaN = floatCodec.encode(Float.NaN)
        val decodedFloatNaN = floatCodec.decode(encodedFloatNaN)

        val doubleCodec      = implicitly[Schema[Double]].derive(MessagePackFormat.deriver)
        val encodedDoubleNaN = doubleCodec.encode(Double.NaN)
        val decodedDoubleNaN = doubleCodec.decode(encodedDoubleNaN)

        // Use values to suppress unused warnings in Scala 2.13
        locally((encodedFloatNaN, encodedDoubleNaN, decodedDoubleNaN))

        assert(decodedFloatNaN.map(_.isNaN))(isRight(isTrue)) &&
        assert(decodedDoubleNaN.map(_.isNaN))(isRight(isTrue)) &&
        roundTrip(Float.PositiveInfinity) &&
        roundTrip(Float.NegativeInfinity) &&
        roundTrip(Double.PositiveInfinity) &&
        roundTrip(Double.NegativeInfinity)
      },
      test("large collections") {
        roundTrip((1 to 1000).toList)
      }
    ),
    suite("dynamic values")(
      test("DynamicValue.Primitive - Unit") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Unit))
      },
      test("DynamicValue.Primitive - Boolean") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Boolean(true))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Boolean(false)))
      },
      test("DynamicValue.Primitive - Int") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Int(42))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Int(-42))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Int(0)))
      },
      test("DynamicValue.Primitive - Long") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Long(9876543210L))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Long(-9876543210L)))
      },
      test("DynamicValue.Primitive - Double") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Double(3.14159))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.Double(-2.71828)))
      },
      test("DynamicValue.Primitive - String") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.String("hello world"))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.String("")))
      },
      test("DynamicValue.Primitive - BigInt positive and negative") {
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt("12345678901234567890")))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt("-12345678901234567890")))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(0)))) &&
        roundTrip[DynamicValue](DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(-1))))
      },
      test("DynamicValue.Sequence") {
        roundTrip[DynamicValue](
          DynamicValue.Sequence(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.Int(2)),
              DynamicValue.Primitive(PrimitiveValue.Int(3))
            )
          )
        )
      },
      test("DynamicValue.Sequence - empty") {
        roundTrip[DynamicValue](DynamicValue.Sequence(Vector.empty))
      },
      test("DynamicValue.Record") {
        roundTrip[DynamicValue](
          DynamicValue.Record(
            Vector(
              "name"  -> DynamicValue.Primitive(PrimitiveValue.String("test")),
              "value" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
            )
          )
        )
      },
      test("DynamicValue.Record - empty") {
        roundTrip[DynamicValue](DynamicValue.Record(Vector.empty))
      },
      test("DynamicValue.Variant") {
        // Variant is encoded as a map with one key, so it decodes as a Record
        // This is expected behavior since MessagePack doesn't distinguish variants from records
        val variantValue = DynamicValue.Variant(
          "SomeCase",
          DynamicValue.Record(
            Vector("value" -> DynamicValue.Primitive(PrimitiveValue.Int(123)))
          )
        )
        val variantCodec   = implicitly[Schema[DynamicValue]].derive(MessagePackFormat.deriver)
        val variantEncoded = variantCodec.encode(variantValue)
        // Variant decodes as Record because MessagePack maps don't preserve variant semantics
        val variantExpected = DynamicValue.Record(
          Vector(
            "SomeCase" -> DynamicValue.Record(Vector("value" -> DynamicValue.Primitive(PrimitiveValue.Int(123))))
          )
        )
        assert(variantCodec.decode(variantEncoded))(isRight(equalTo(variantExpected)))
      },
      test("DynamicValue.Map") {
        // DynamicValue.Map is encoded as a MessagePack map, which decodes as Record
        // This is expected behavior since MessagePack doesn't distinguish maps from records
        val mapValue = DynamicValue.Map(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.String("key1")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.String("key2")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )
        val mapCodec   = implicitly[Schema[DynamicValue]].derive(MessagePackFormat.deriver)
        val mapEncoded = mapCodec.encode(mapValue)
        // Map with string keys decodes as Record because MessagePack maps don't preserve type info
        val mapExpected = DynamicValue.Record(
          Vector(
            "key1" -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
            "key2" -> DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )
        assert(mapCodec.decode(mapEncoded))(isRight(equalTo(mapExpected)))
      },
      test("DynamicValue.Map - empty") {
        // Empty map decodes as empty Record
        val emptyMapValue    = DynamicValue.Map(Vector.empty)
        val emptyMapCodec    = implicitly[Schema[DynamicValue]].derive(MessagePackFormat.deriver)
        val emptyMapEncoded  = emptyMapCodec.encode(emptyMapValue)
        val emptyMapExpected = DynamicValue.Record(Vector.empty)
        assert(emptyMapCodec.decode(emptyMapEncoded))(isRight(equalTo(emptyMapExpected)))
      },
      test("DynamicValue - nested structure") {
        roundTrip[DynamicValue](
          DynamicValue.Record(
            Vector(
              "items" -> DynamicValue.Sequence(
                Vector(
                  DynamicValue.Record(Vector("id" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))),
                  DynamicValue.Record(Vector("id" -> DynamicValue.Primitive(PrimitiveValue.Int(2))))
                )
              ),
              "metadata" -> DynamicValue.Record(
                Vector(
                  "count" -> DynamicValue.Primitive(PrimitiveValue.Int(2)),
                  "name"  -> DynamicValue.Primitive(PrimitiveValue.String("test"))
                )
              )
            )
          )
        )
      }
    ),
    suite("custom codecs")(
      test("record with a custom codec for primitives injected by optic") {
        val codec = Record1.schema
          .deriving(MessagePackFormat.deriver)
          .instance(
            Record1.i,
            new MessagePackBinaryCodec[Int](MessagePackBinaryCodec.intType) {
              def decodeUnsafe(unpacker: org.msgpack.core.MessageUnpacker): Int =
                java.lang.Integer.valueOf(unpacker.unpackString())

              def encodeUnsafe(value: Int, packer: org.msgpack.core.MessagePacker): Unit =
                packer.packString(value.toString)
            }
          )
          .derive
        roundTrip(Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"), codec)
      },
      test("record with a custom codec for primitives injected by type name") {
        val codec = Record1.schema
          .deriving(MessagePackFormat.deriver)
          .instance(
            TypeName.int,
            new MessagePackBinaryCodec[Int](MessagePackBinaryCodec.intType) {
              def decodeUnsafe(unpacker: org.msgpack.core.MessageUnpacker): Int =
                java.lang.Integer.valueOf(unpacker.unpackString())

              def encodeUnsafe(value: Int, packer: org.msgpack.core.MessagePacker): Unit =
                packer.packString(value.toString)
            }
          )
          .derive
        roundTrip(Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"), codec)
      }
    ),
    suite("interop validation")(
      test("encoded bytes are valid MessagePack - primitives") {
        // Verify our encoded output can be read by raw msgpack-core
        val intCodec = implicitly[Schema[Int]].derive(MessagePackFormat.deriver)
        val encoded  = intCodec.encode(42)

        val unpacker = MessagePack.newDefaultUnpacker(encoded)
        val rawValue = unpacker.unpackInt()
        unpacker.close()

        assert(rawValue)(equalTo(42))
      },
      test("encoded bytes are valid MessagePack - record structure") {
        // Verify record encodes as MessagePack map with correct field names
        val codec   = SimpleRecord.schema.derive(MessagePackFormat.deriver)
        val record  = SimpleRecord("test", 123)
        val encoded = codec.encode(record)

        val unpacker = MessagePack.newDefaultUnpacker(encoded)
        val mapSize  = unpacker.unpackMapHeader()

        // Collect field names
        val fields = (0 until mapSize).map { _ =>
          val key = unpacker.unpackString()
          unpacker.skipValue()
          key
        }.toSet
        unpacker.close()

        assert(mapSize)(equalTo(2)) &&
        assert(fields)(equalTo(Set("name", "value")))
      },
      test("encoded bytes are valid MessagePack - sequence structure") {
        implicit val listSchema: Schema[List[Int]] = Schema.derived
        val codec                                  = listSchema.derive(MessagePackFormat.deriver)
        val list                                   = List(1, 2, 3)
        val encoded                                = codec.encode(list)

        val unpacker  = MessagePack.newDefaultUnpacker(encoded)
        val arraySize = unpacker.unpackArrayHeader()
        val values    = (0 until arraySize).map(_ => unpacker.unpackInt()).toList
        unpacker.close()

        assert(arraySize)(equalTo(3)) &&
        assert(values)(equalTo(List(1, 2, 3)))
      },
      test("encoded bytes are valid MessagePack - variant structure") {
        val codec         = Sum1.schema.derive(MessagePackFormat.deriver)
        val variant: Sum1 = Sum1.C1(42)
        val encoded       = codec.encode(variant)

        val unpacker = MessagePack.newDefaultUnpacker(encoded)
        val mapSize  = unpacker.unpackMapHeader()
        val caseName = unpacker.unpackString()
        unpacker.close()

        // Variants encode as single-entry map: {"CaseName": value}
        assert(mapSize)(equalTo(1)) &&
        assert(caseName)(equalTo("C1"))
      }
    ),
    suite("byte length verification")(
      test("primitive encoding sizes") {
        // MessagePack has specific encoding sizes based on value ranges
        roundTrip((), 1) &&            // nil = 1 byte
        roundTrip(true, 1) &&          // bool = 1 byte
        roundTrip(false, 1) &&         // bool = 1 byte
        roundTrip(1: Byte, 1) &&       // positive fixint = 1 byte
        roundTrip(127: Byte, 1) &&     // max positive fixint = 1 byte
        roundTrip(-32: Byte, 1) &&     // negative fixint = 1 byte
        roundTrip(Byte.MinValue, 2) && // int8 = 2 bytes
        roundTrip(1, 1) &&             // positive fixint = 1 byte
        roundTrip(128, 2) &&           // uint8 = 2 bytes
        roundTrip(Int.MaxValue, 5) &&  // int32 = 5 bytes
        roundTrip(1L, 1) &&            // positive fixint = 1 byte
        roundTrip(Long.MaxValue, 9)    // int64 = 9 bytes
      },
      test("string encoding sizes") {
        roundTrip("", 1) &&   // fixstr empty = 1 byte
        roundTrip("a", 2) &&  // fixstr 1 char = 2 bytes
        roundTrip("hello", 6) // fixstr 5 chars = 6 bytes
      },
      test("record encoding size") {
        // SimpleRecord with name="x" and value=1
        // fixmap(2) + fixstr("name") + fixstr("x") + fixstr("value") + fixint(1)
        // = 1 + 5 + 2 + 6 + 1 = 15 bytes
        roundTrip(SimpleRecord("x", 1), 15)
      }
    )
  )

  // ============================================================
  // Test Data Models
  // ============================================================

  case class Record1(bl: Boolean, b: Byte, sh: Short, i: Int, l: Long, f: Float, d: Double, c: Char, s: String)
  object Record1 extends CompanionOptics[Record1] {
    implicit val schema: Schema[Record1] = Schema.derived
    val i: Lens[Record1, Int]            = $(_.i)
  }

  case class Record2(r1_1: Record1, r1_2: Record1)
  object Record2 {
    implicit val schema: Schema[Record2] = Schema.derived
  }

  case class Recursive(i: Int, ln: List[Recursive])
  object Recursive extends CompanionOptics[Recursive] {
    implicit val schema: Schema[Recursive] = Schema.derived
    val i: Lens[Recursive, Int]            = $(_.i)
  }

  case class Record3(userId: UserId, email: String)
  object Record3 {
    implicit val schema: Schema[Record3] = Schema.derived
  }

  case class Record4(hidden: Unit, optKey: Option[String])
  object Record4 {
    implicit val schema: Schema[Record4] = Schema.derived
  }

  case class SimpleRecord(name: String, value: Int)
  object SimpleRecord {
    implicit val schema: Schema[SimpleRecord] = Schema.derived
  }

  case class RecordWithDefaults(name: String, opt: Option[String], items: List[Int], props: Map[String, Int])
  object RecordWithDefaults {
    implicit val schema: Schema[RecordWithDefaults] = Schema.derived
  }

  sealed trait Enum1
  object Enum1 {
    case object Case1 extends Enum1
    case object Case2 extends Enum1
    case object Case3 extends Enum1

    implicit val schema: Schema[Enum1] = Schema.derived
  }

  sealed trait Sum1
  object Sum1 {
    case class C1(i: Int)    extends Sum1
    case class C2(s: String) extends Sum1

    implicit val schema: Schema[Sum1] = Schema.derived
  }

  sealed trait Sum2
  object Sum2 {
    case class C1(s: Sum1)   extends Sum2
    case class C2(s: String) extends Sum2

    implicit val schema: Schema[Sum2] = Schema.derived
  }

  case class UserId(value: Long)
  object UserId {
    implicit val schema: Schema[UserId] = Schema.derived.wrapTotal(x => new UserId(x), _.value)
  }

  case class DeepNested(level1: Record1, level2: Record2)
  object DeepNested {
    implicit val schema: Schema[DeepNested] = Schema.derived
  }

  case class RecordWithVariants(items: List[Sum1])
  object RecordWithVariants {
    implicit val schema: Schema[RecordWithVariants] = Schema.derived
  }

  // Either schema
  implicit val eitherStringIntSchema: Schema[Either[String, Int]]                   = Schema.derived
  implicit val eitherSimpleRecordSchema: Schema[Either[SimpleRecord, SimpleRecord]] = Schema.derived
}
