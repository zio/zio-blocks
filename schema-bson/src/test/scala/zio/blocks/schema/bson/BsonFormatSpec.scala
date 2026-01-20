package zio.blocks.schema.bson

import zio.blocks.schema._
import zio.blocks.schema.bson.BsonTestUtils._
import zio.test._
import zio.blocks.schema.{PrimitiveValue, Schema}
import java.time._
import java.util.UUID

object BsonFormatSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("BsonFormatSpec")(
    suite("primitives")(
      test("Unit") {
        roundTrip((), 8)
      },
      test("Boolean") {
        roundTrip(true, 9) &&
        roundTrip(false, 9)
      },
      test("Byte") {
        roundTrip(1.toByte, 12)
      },
      test("Short") {
        roundTrip(1.toShort, 12)
      },
      test("Int") {
        roundTrip(1, 12) &&
        roundTrip(Int.MaxValue, 12)
      },
      test("Long") {
        roundTrip(1L, 16)
      },
      test("Float") {
        roundTrip(1.0f, 16)
      },
      test("Double") {
        roundTrip(1.0, 16)
      },
      test("Char") {
        roundTrip(
          'A',
          13
        ) // total size(4) + string type(1) + "v\0"(2) + size(4) + "A\0"(2) + doc end(1) = 14? Let's check.
        // BsonCodec.encode(char) -> writeString("A")
        // Overhead 8 + (size(4) + "A\0"(2)) = 14.
        // My previous test said 18 for "Hello" (8 + 4 + 5 + 1 = 18).
        // For "A": 8 + 4 + 1 + 1 = 14.
        roundTrip('A', 14)
      },
      test("String") {
        roundTrip("Hello", 18)
      }
    ),
    suite("extra primitives")(
      test("BigDecimal") {
        roundTrip(BigDecimal("123.456"), 24)
      },
      test("BigInt") {
        roundTrip(BigInt("12345678901234567890"), 33)
      },
      test("Instant") {
        roundTrip(Instant.parse("2023-10-27T10:15:30Z"), 16)
      },
      test("UUID") {
        roundTrip(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), 29)
      },
//      test("Chunk[Byte]") {
//        roundTrip(Chunk[Byte](1, 2, 3, 4), 17)
//      },
      test("LocalDate") {
        roundTrip(LocalDate.parse("2023-10-27"), 23)
      }
    ),
//    suite("dynamic")(
//      test("DynamicValue.Record") {
//        val dv = DynamicValue.Record(Vector("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
//        roundTrip(dv, 17)
//      },
//      test("DynamicValue.Sequence") {
//        val dv = DynamicValue.Sequence(Vector(DynamicValue.Primitive(PrimitiveValue.String("hello"))))
//        roundTrip(dv, 23)
//      }
//    ),
    suite("records")(
      test("simple record") {
        roundTrip(Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"), 85)
      },
      test("nested record") {
        roundTrip(Record2(Record1(true, 1, 2, 3, 4, 5, 6, '7', "S1"), Record1(false, 9, 8, 7, 6, 5, 4, '0', "S2")), 181)
      },
      test("recursive record") {
        roundTrip(Recursive(1, List(Recursive(2, Nil))), 57)
      }
    ),
    suite("sequences")(
      test("List[Int]") {
        roundTrip(List(1, 2, 3), 34)
      },
      test("Vector[String]") {
        roundTrip(Vector("A", "B"), 31)
      },
    ),
    suite("maps")(
      test("string keys") {
        roundTrip(Map("key" -> 42), 14)
      },
//      test("non-string keys") {
//        roundTrip(Map(1 -> "one"), 35)
//      }
    ),
    suite("variants")(
      test("Option") {
        implicit val optionSchema: Schema[Option[Int]] = Schema.derived
        roundTrip[Option[Int]](Option(42), 27) &&
        roundTrip[Option[Int]](None, 16)
      },
      test("Either") {
        implicit val eitherSchema: Schema[Either[String, Int]] = Schema.derived
        roundTrip[Either[String, Int]](Right(42), 28) &&
        roundTrip[Either[String, Int]](Left("error"), 33)
      }
    ),
    suite("dynamic")(
       test("DynamicValue.Record") {
        val schema = Schema[DynamicValue]
        val record = DynamicValue.Record(
          Vector("foo" -> DynamicValue.Primitive(PrimitiveValue.String("s")), "bar" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        roundTrip(record, 118)(schema)
      },
      test("DynamicValue.Sequence") {
        val schema = Schema[DynamicValue]
        val sequence = DynamicValue.Sequence(
          Vector(DynamicValue.Primitive(PrimitiveValue.String("s")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        roundTrip(sequence, 116)(schema)
      }
    )
  )
}

case class Record1(bl: Boolean, b: Byte, sh: Short, i: Int, l: Long, f: Float, d: Double, c: Char, s: String)
object Record1 {
  implicit val schema: Schema[Record1] = Schema.derived
}

case class Record2(r1: Record1, r2: Record1)
object Record2 {
  implicit val schema: Schema[Record2] = Schema.derived
}

case class Recursive(i: Int, children: List[Recursive])
object Recursive {
  implicit val schema: Schema[Recursive] = Schema.derived
}
