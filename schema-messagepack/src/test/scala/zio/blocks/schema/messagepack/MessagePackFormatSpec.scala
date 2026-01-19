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
    )
  )
}
