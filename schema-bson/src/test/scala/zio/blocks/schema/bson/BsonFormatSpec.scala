package zio.blocks.schema.bson

import org.bson.types.ObjectId
import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._
import java.time._

object BsonFormatSpec extends ZIOSpecDefault {

  // Test case classes
  case class Person(name: String, age: Int)
  object Person {
    given Reflect[BsonFormat.type, Person] = Reflect.derive[BsonFormat.type, Person]
  }

  case class Address(street: String, city: String, zipCode: Int)
  object Address {
    given Reflect[BsonFormat.type, Address] = Reflect.derive[BsonFormat.type, Address]
  }

  case class Employee(id: Long, person: Person, address: Address, salary: Double)
  object Employee {
    given Reflect[BsonFormat.type, Employee] = Reflect.derive[BsonFormat.type, Employee]
  }

  // Sealed trait for variant testing
  sealed trait Shape
  object Shape {
    case class Circle(radius: Double) extends Shape
    case class Rectangle(width: Double, height: Double) extends Shape
    case class Triangle(base: Double, height: Double) extends Shape

    given Reflect[BsonFormat.type, Shape] = Reflect.derive[BsonFormat.type, Shape]
  }

  // Collection types
  case class Library(name: String, books: List[String])
  object Library {
    given Reflect[BsonFormat.type, Library] = Reflect.derive[BsonFormat.type, Library]
  }

  case class Scores(values: Vector[Int])
  object Scores {
    given Reflect[BsonFormat.type, Scores] = Reflect.derive[BsonFormat.type, Scores]
  }

  // Optional fields
  case class OptionalData(required: String, optional: Option[Int])
  object OptionalData {
    given Reflect[BsonFormat.type, OptionalData] = Reflect.derive[BsonFormat.type, OptionalData]
  }

  // Time types
  case class TimeData(
    instant: Instant,
    localDate: LocalDate,
    localTime: LocalTime,
    localDateTime: LocalDateTime
  )
  object TimeData {
    given Reflect[BsonFormat.type, TimeData] = Reflect.derive[BsonFormat.type, TimeData]
  }

  def roundTripTest[A](name: String, value: A)(implicit codec: BsonFormat.TypeClass[A]): Spec[Any, Nothing] =
    test(s"round trip: $name") {
      val encoded = codec.encode(value)
      val decoded = codec.decode(encoded)
      assert(decoded)(isRight(equalTo(value)))
    }

  def spec = suite("BsonFormatSpec")(
    suite("Primitive types")(
      roundTripTest("Unit", ()),
      roundTripTest("Boolean true", true),
      roundTripTest("Boolean false", false),
      roundTripTest("Byte", 42.toByte),
      roundTripTest("Short", 1000.toShort),
      roundTripTest("Int", 123456),
      roundTripTest("Long", 9876543210L),
      roundTripTest("Float", 3.14f),
      roundTripTest("Double", 2.718281828),
      roundTripTest("Char", 'A'),
      roundTripTest("String", "Hello, BSON!"),
      roundTripTest("BigInt", BigInt("12345678901234567890")),
      roundTripTest("BigDecimal", BigDecimal("123.456789"))
    ),

    suite("Case classes")(
      roundTripTest(
        "Simple case class",
        Person("Alice", 30)
      ),
      roundTripTest(
        "Nested case class",
        Employee(
          id = 1001L,
          person = Person("Bob", 25),
          address = Address("123 Main St", "Springfield", 12345),
          salary = 75000.50
        )
      )
    ),

    suite("Sealed traits / Variants")(
      roundTripTest(
        "Circle",
        Shape.Circle(5.0): Shape
      ),
      roundTripTest(
        "Rectangle",
        Shape.Rectangle(4.0, 6.0): Shape
      ),
      roundTripTest(
        "Triangle",
        Shape.Triangle(3.0, 4.0): Shape
      )
    ),

    suite("Collections")(
      roundTripTest(
        "List of strings",
        Library("City Library", List("Book1", "Book2", "Book3"))
      ),
      roundTripTest(
        "Vector of ints",
        Scores(Vector(100, 95, 87, 92))
      ),
      roundTripTest(
        "Empty list",
        Library("Empty Library", List.empty[String])
      )
    ),

    suite("Optional values")(
      roundTripTest(
        "With Some value",
        OptionalData("required", Some(42))
      ),
      roundTripTest(
        "With None value",
        OptionalData("required", None)
      )
    ),

    suite("Java time types")(
      roundTripTest(
        "Instant",
        Instant.parse("2024-01-15T10:30:00Z")
      ),
      roundTripTest(
        "LocalDate",
        LocalDate.of(2024, 1, 15)
      ),
      roundTripTest(
        "LocalTime",
        LocalTime.of(14, 30, 45)
      ),
      roundTripTest(
        "LocalDateTime",
        LocalDateTime.of(2024, 1, 15, 14, 30, 45)
      ),
      roundTripTest(
        "Duration",
        Duration.ofHours(2).plusMinutes(30)
      ),
      roundTripTest(
        "Period",
        Period.of(1, 6, 15)
      ),
      roundTripTest(
        "DayOfWeek",
        DayOfWeek.MONDAY
      ),
      roundTripTest(
        "Month",
        Month.JANUARY
      ),
      roundTripTest(
        "Year",
        Year.of(2024)
      ),
      roundTripTest(
        "YearMonth",
        YearMonth.of(2024, 1)
      ),
      roundTripTest(
        "MonthDay",
        MonthDay.of(1, 15)
      ),
      roundTripTest(
        "ZoneId",
        ZoneId.of("America/New_York")
      ),
      roundTripTest(
        "ZoneOffset",
        ZoneOffset.ofHours(-5)
      ),
      roundTripTest(
        "ZonedDateTime",
        ZonedDateTime.of(2024, 1, 15, 14, 30, 0, 0, ZoneId.of("UTC"))
      ),
      roundTripTest(
        "OffsetDateTime",
        OffsetDateTime.of(2024, 1, 15, 14, 30, 0, 0, ZoneOffset.UTC)
      ),
      roundTripTest(
        "OffsetTime",
        OffsetTime.of(14, 30, 0, 0, ZoneOffset.UTC)
      ),
      roundTripTest(
        "Complete TimeData",
        TimeData(
          instant = Instant.parse("2024-01-15T10:30:00Z"),
          localDate = LocalDate.of(2024, 1, 15),
          localTime = LocalTime.of(14, 30, 45),
          localDateTime = LocalDateTime.of(2024, 1, 15, 14, 30, 45)
        )
      )
    ),

    suite("Other types")(
      roundTripTest(
        "UUID",
        java.util.UUID.randomUUID()
      ),
      roundTripTest(
        "Currency",
        java.util.Currency.getInstance("USD")
      )
    ),

    suite("Edge cases")(
      test("Empty string") {
        val codec = summon[BsonFormat.TypeClass[String]]
        val encoded = codec.encode("")
        val decoded = codec.decode(encoded)
        assert(decoded)(isRight(equalTo("")))
      },
      test("Large numbers") {
        val codec = summon[BsonFormat.TypeClass[Long]]
        val value = Long.MaxValue
        val encoded = codec.encode(value)
        val decoded = codec.decode(encoded)
        assert(decoded)(isRight(equalTo(value)))
      },
      test("Negative numbers") {
        val codec = summon[BsonFormat.TypeClass[Int]]
        val value = -12345
        val encoded = codec.encode(value)
        val decoded = codec.decode(encoded)
        assert(decoded)(isRight(equalTo(value)))
      },
      test("Special double values") {
        val codec = summon[BsonFormat.TypeClass[Double]]
        for {
          posInf <- {
            val encoded = codec.encode(Double.PositiveInfinity)
            val decoded = codec.decode(encoded)
            assert(decoded)(isRight(equalTo(Double.PositiveInfinity)))
          }
          negInf <- {
            val encoded = codec.encode(Double.NegativeInfinity)
            val decoded = codec.decode(encoded)
            assert(decoded)(isRight(equalTo(Double.NegativeInfinity)))
          }
        } yield posInf && negInf
      }
    ),

    suite("Error handling")(
      test("Decode invalid data") {
        val codec = summon[BsonFormat.TypeClass[Person]]
        val invalidData = Array[Byte](0, 0, 0, 0) // Invalid BSON
        val result = codec.decode(java.nio.ByteBuffer.wrap(invalidData))
        assert(result)(isLeft)
      }
    )
  )
}
