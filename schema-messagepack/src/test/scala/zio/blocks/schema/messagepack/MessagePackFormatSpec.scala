package zio.blocks.schema.messagepack

import zio.blocks.schema._
import zio.blocks.schema.messagepack.MessagePackTestUtils._
import zio.test._

import java.time._
import java.util.{Currency, UUID}

object MessagePackFormatSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("MessagePackFormatSpec")(
    suite("primitives")(
      test("Unit") {
        roundTrip(())
      },
      test("Boolean") {
        roundTrip(true) &&
        roundTrip(false)
      },
      test("Byte") {
        roundTrip(1: Byte) &&
        roundTrip(Byte.MinValue) &&
        roundTrip(Byte.MaxValue)
      },
      test("Short") {
        roundTrip(1: Short) &&
        roundTrip(Short.MinValue) &&
        roundTrip(Short.MaxValue)
      },
      test("Int") {
        roundTrip(1) &&
        roundTrip(Int.MinValue) &&
        roundTrip(Int.MaxValue)
      },
      test("Long") {
        roundTrip(1L) &&
        roundTrip(Long.MinValue) &&
        roundTrip(Long.MaxValue)
      },
      test("Float") {
        roundTrip(42.0f) &&
        roundTrip(Float.MinValue) &&
        roundTrip(Float.MaxValue)
      },
      test("Double") {
        roundTrip(42.0) &&
        roundTrip(Double.MinValue) &&
        roundTrip(Double.MaxValue)
      },
      test("Char") {
        roundTrip('7') &&
        roundTrip(Char.MinValue) &&
        roundTrip(Char.MaxValue)
      },
      test("String") {
        roundTrip("Hello") &&
        roundTrip("") &&
        roundTrip("Unicode: \u00e9\u00e8\u00ea")
      },
      test("BigInt") {
        roundTrip(BigInt("9" * 20)) &&
        roundTrip(BigInt("-" + "9" * 20))
      },
      test("BigDecimal") {
        roundTrip(BigDecimal("9." + "9" * 20 + "E+12345")) &&
        roundTrip(BigDecimal("-9." + "9" * 20 + "E-12345"))
      },
      test("DayOfWeek") {
        roundTrip(java.time.DayOfWeek.WEDNESDAY) &&
        roundTrip(java.time.DayOfWeek.SUNDAY)
      },
      test("Duration") {
        roundTrip(java.time.Duration.ofNanos(1234567890123456789L)) &&
        roundTrip(java.time.Duration.ZERO)
      },
      test("Instant") {
        roundTrip(java.time.Instant.parse("2025-07-18T08:29:13.121409459Z")) &&
        roundTrip(java.time.Instant.EPOCH)
      },
      test("LocalDate") {
        roundTrip(java.time.LocalDate.parse("2025-07-18")) &&
        roundTrip(java.time.LocalDate.MIN)
      },
      test("LocalDateTime") {
        roundTrip(java.time.LocalDateTime.parse("2025-07-18T08:29:13.121409459"))
      },
      test("LocalTime") {
        roundTrip(java.time.LocalTime.parse("08:29:13.121409459")) &&
        roundTrip(java.time.LocalTime.MIN)
      },
      test("Month") {
        roundTrip(java.time.Month.of(12)) &&
        roundTrip(java.time.Month.JANUARY)
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
        roundTrip(java.time.Period.of(1, 12, 31)) &&
        roundTrip(java.time.Period.ZERO)
      },
      test("Year") {
        roundTrip(java.time.Year.of(2025)) &&
        roundTrip(java.time.Year.of(-1000))
      },
      test("YearMonth") {
        roundTrip(java.time.YearMonth.of(2025, 7))
      },
      test("ZoneId") {
        roundTrip(java.time.ZoneId.of("UTC")) &&
        roundTrip(java.time.ZoneId.of("America/New_York"))
      },
      test("ZoneOffset") {
        roundTrip(java.time.ZoneOffset.ofTotalSeconds(3600)) &&
        roundTrip(java.time.ZoneOffset.UTC)
      },
      test("ZonedDateTime") {
        roundTrip(java.time.ZonedDateTime.parse("2025-07-18T08:29:13.121409459+02:00[Europe/Warsaw]"))
      },
      test("Currency") {
        roundTrip(Currency.getInstance("USD")) &&
        roundTrip(Currency.getInstance("EUR"))
      },
      test("UUID") {
        roundTrip(UUID.randomUUID()) &&
        roundTrip(UUID.fromString("00000000-0000-0000-0000-000000000000"))
      }
    ),
    suite("records")(
      test("simple case class") {
        case class Person(name: String, age: Int)
        implicit val personSchema: Schema[Person] = Schema.derived
        roundTrip(Person("Alice", 30))
      },
      test("nested case class") {
        case class Address(street: String, city: String)
        case class Person(name: String, address: Address)
        implicit val addressSchema: Schema[Address] = Schema.derived
        implicit val personSchema: Schema[Person]   = Schema.derived
        roundTrip(Person("Bob", Address("123 Main St", "Springfield")))
      },
      test("case class with optional field") {
        case class Person(name: String, nickname: Option[String])
        implicit val personSchema: Schema[Person] = Schema.derived
        roundTrip(Person("Charlie", Some("Chuck"))) &&
        roundTrip(Person("Dave", None))
      }
    ),
    suite("variants")(
      test("sealed trait") {
        sealed trait Shape
        case class Circle(radius: Double)                   extends Shape
        case class Rectangle(width: Double, height: Double) extends Shape

        implicit val circleSchema: Schema[Circle]       = Schema.derived
        implicit val rectangleSchema: Schema[Rectangle] = Schema.derived
        implicit val shapeSchema: Schema[Shape]         = Schema.derived

        roundTrip[Shape](Circle(5.0)) &&
        roundTrip[Shape](Rectangle(3.0, 4.0))
      }
    ),
    suite("sequences")(
      test("List[Int]") {
        roundTrip(List(1, 2, 3, 4, 5)) &&
        roundTrip(List.empty[Int])
      },
      test("Vector[String]") {
        roundTrip(Vector("a", "b", "c")) &&
        roundTrip(Vector.empty[String])
      },
      test("List of case class") {
        case class Item(name: String, price: Double)
        implicit val itemSchema: Schema[Item] = Schema.derived
        roundTrip(List(Item("Apple", 1.50), Item("Banana", 0.75)))
      }
    ),
    suite("maps")(
      test("Map[String, Int]") {
        roundTrip(Map("a" -> 1, "b" -> 2, "c" -> 3)) &&
        roundTrip(Map.empty[String, Int])
      },
      test("Map[String, String]") {
        roundTrip(Map("name" -> "Alice", "city" -> "Boston"))
      }
    ),
    suite("forward compatibility")(
      test("decode record with unknown fields - skips extra fields gracefully") {
        // Simulates schema evolution: newer producer adds fields, older consumer ignores them
        // This tests that unpacker.skipValue() correctly handles unknown fields

        // PersonV2 has an extra "email" field that PersonV1 doesn't know about
        case class PersonV1(name: String, age: Int)
        case class PersonV2(name: String, age: Int, email: String)

        implicit val personV1Schema: Schema[PersonV1] = Schema.derived
        implicit val personV2Schema: Schema[PersonV2] = Schema.derived

        val codecV1 = personV1Schema.derive(MessagePackFormat.deriver)
        val codecV2 = personV2Schema.derive(MessagePackFormat.deriver)

        // Encode with V2 (has extra field)
        val personV2  = PersonV2("Alice", 30, "alice@example.com")
        val encodedV2 = codecV2.encode(personV2)

        // Decode with V1 (should skip unknown "email" field)
        val decodedV1 = codecV1.decode(encodedV2)

        assertTrue(decodedV1 == Right(PersonV1("Alice", 30)))
      },
      test("decode record with multiple unknown fields") {
        case class SimpleRecord(id: Int)
        case class ExtendedRecord(id: Int, name: String, tags: List[String], active: Boolean)

        implicit val simpleSchema: Schema[SimpleRecord]     = Schema.derived
        implicit val extendedSchema: Schema[ExtendedRecord] = Schema.derived

        val codecSimple   = simpleSchema.derive(MessagePackFormat.deriver)
        val codecExtended = extendedSchema.derive(MessagePackFormat.deriver)

        val extended = ExtendedRecord(42, "Test", List("a", "b"), true)
        val encoded  = codecExtended.encode(extended)
        val decoded  = codecSimple.decode(encoded)

        assertTrue(decoded == Right(SimpleRecord(42)))
      }
    ),
    suite("nested collections")(
      test("List[List[Int]]") {
        roundTrip(List(List(1, 2), List(3, 4, 5), List.empty[Int]))
      },
      test("Map[String, List[String]]") {
        roundTrip(Map("fruits" -> List("apple", "banana"), "veggies" -> List("carrot")))
      },
      test("List[Map[String, Int]]") {
        roundTrip(List(Map("a" -> 1), Map("b" -> 2, "c" -> 3)))
      }
    ),
    suite("edge cases")(
      test("empty case class") {
        case class Empty()
        implicit val emptySchema: Schema[Empty] = Schema.derived
        roundTrip(Empty())
      },
      test("case class with many fields") {
        case class ManyFields(
          f1: Int,
          f2: Int,
          f3: Int,
          f4: Int,
          f5: Int,
          f6: Int,
          f7: Int,
          f8: Int,
          f9: Int,
          f10: Int
        )
        implicit val manyFieldsSchema: Schema[ManyFields] = Schema.derived
        roundTrip(ManyFields(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
      },
      test("deeply nested record") {
        case class Inner(value: Int)
        case class Middle(inner: Inner)
        case class Outer(middle: Middle)
        implicit val innerSchema: Schema[Inner]   = Schema.derived
        implicit val middleSchema: Schema[Middle] = Schema.derived
        implicit val outerSchema: Schema[Outer]   = Schema.derived
        roundTrip(Outer(Middle(Inner(42))))
      }
    )
  )
}
