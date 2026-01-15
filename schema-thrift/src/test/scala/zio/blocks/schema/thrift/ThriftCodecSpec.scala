package zio.blocks.schema.thrift

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._
import zio.Scope

object ThriftCodecSpec extends ZIOSpecDefault {

  case class Person(name: String, age: Int)
  case class Address(street: String, city: String)
  case class User(name: String, address: Address)
  case class Node(value: Int, next: Option[Node])

  sealed trait Color
  case object Red   extends Color
  case object Green extends Color
  case object Blue  extends Color

  sealed trait Shape
  case class Circle(radius: Double)                   extends Shape
  case class Rectangle(width: Double, height: Double) extends Shape

  def spec: Spec[TestEnvironment with Scope, Any] = suite("ThriftCodecSpec")(
    suite("primitives")(
      test("Unit") {
        encodeAndDecode(Schema[Unit], ())
      },
      test("Boolean") {
        encodeAndDecode(Schema[Boolean], true) &&
        encodeAndDecode(Schema[Boolean], false)
      },
      test("Byte") {
        encodeAndDecode(Schema[Byte], 123.toByte)
      },
      test("Short") {
        encodeAndDecode(Schema[Short], 123.toShort)
      },
      test("Int") {
        encodeAndDecode(Schema[Int], 12345)
      },
      test("Long") {
        encodeAndDecode(Schema[Long], 1234567890L)
      },
      test("Float") {
        encodeAndDecode(Schema[Float], 123.45f)
      },
      test("Double") {
        encodeAndDecode(Schema[Double], 123.456789)
      },
      test("String") {
        encodeAndDecode(Schema[String], "Hello Thrift")
      },
      test("BigInt") {
        encodeAndDecode(Schema[BigInt], BigInt("12345678901234567890"))
      },
      test("BigDecimal") {
        encodeAndDecode(Schema[BigDecimal], BigDecimal("123.45678901234567890"))
      },
      test("UUID") {
        encodeAndDecode(Schema[java.util.UUID], java.util.UUID.randomUUID())
      },
      test("Char") {
        encodeAndDecode(Schema[Char], 'A')
      },
      test("Currency") {
        encodeAndDecode(Schema[java.util.Currency], java.util.Currency.getInstance("USD"))
      }
    ),
    suite("time")(
      test("DayOfWeek") {
        encodeAndDecode(Schema[java.time.DayOfWeek], java.time.DayOfWeek.MONDAY)
      },
      test("Month") {
        encodeAndDecode(Schema[java.time.Month], java.time.Month.JANUARY)
      },
      test("Year") {
        encodeAndDecode(Schema[java.time.Year], java.time.Year.of(2023))
      },
      test("Duration") {
        encodeAndDecode(Schema[java.time.Duration], java.time.Duration.ofMinutes(5))
      },
      test("Period") {
        encodeAndDecode(Schema[java.time.Period], java.time.Period.ofDays(5))
      },
      test("Instant") {
        encodeAndDecode(Schema[java.time.Instant], java.time.Instant.now())
      },
      test("LocalDate") {
        encodeAndDecode(Schema[java.time.LocalDate], java.time.LocalDate.now())
      },
      test("LocalTime") {
        encodeAndDecode(Schema[java.time.LocalTime], java.time.LocalTime.now())
      },
      test("LocalDateTime") {
        encodeAndDecode(Schema[java.time.LocalDateTime], java.time.LocalDateTime.now())
      },
      test("OffsetTime") {
        encodeAndDecode(Schema[java.time.OffsetTime], java.time.OffsetTime.now())
      },
      test("OffsetDateTime") {
        encodeAndDecode(Schema[java.time.OffsetDateTime], java.time.OffsetDateTime.now())
      },
      test("ZonedDateTime") {
        encodeAndDecode(Schema[java.time.ZonedDateTime], java.time.ZonedDateTime.now())
      },
      test("ZoneId") {
        encodeAndDecode(Schema[java.time.ZoneId], java.time.ZoneId.systemDefault())
      },
      test("ZoneOffset") {
        encodeAndDecode(Schema[java.time.ZoneOffset], java.time.ZoneOffset.UTC)
      },
      test("MonthDay") {
        encodeAndDecode(Schema[java.time.MonthDay], java.time.MonthDay.now())
      },
      test("YearMonth") {
        encodeAndDecode(Schema[java.time.YearMonth], java.time.YearMonth.now())
      }
    ),
    suite("sequences")(
      test("List[Int]") {
        encodeAndDecode(Schema[List[Int]], List(1, 2, 3))
      },
      test("Set[String]") {
        encodeAndDecode(Schema[Set[String]], Set("a", "b", "c"))
      },
      test("Vector[Double]") {
        encodeAndDecode(Schema[Vector[Double]], Vector(1.1, 2.2, 3.3))
      }
    ),
    suite("maps")(
      test("Map[String, Int]") {
        encodeAndDecode(Schema[Map[String, Int]], Map("a" -> 1, "b" -> 2))
      },
      test("Map[Int, String]") {
        encodeAndDecode(Schema[Map[Int, String]], Map(1 -> "a", 2 -> "b"))
      }
    ),
    suite("records")(
      test("Case Class") {
        val schema = Schema.derived[Person]
        encodeAndDecode(schema, Person("Alice", 30))
      },
      test("Nested Record") {
        val schema = Schema.derived[User]
        encodeAndDecode(schema, User("Bob", Address("123 Main St", "New York")))
      },
      test("Recursive Record") {
        implicit lazy val schema: Schema[Node] = Schema.derived[Node]
        val list                               = Node(1, Some(Node(2, None)))
        encodeAndDecode(schema, list)
      }
    ),
    suite("variants")(
      test("Sealed Trait (Enum)") {
        implicit val schema: Schema[Color] = Schema.derived[Color]
        encodeAndDecode(schema, Red) && encodeAndDecode(schema, Green)
        // Blue is unused in original test, but I left it defined. It shouldn't trigger local unused warning now.
      },
      test("Sealed Trait (ADT)") {
        val schema = Schema.derived[Shape]

        encodeAndDecode(schema, Circle(5.0)) && encodeAndDecode(schema, Rectangle(3.0, 4.0))
      },
      test("Option[Int]") {
        encodeAndDecode(Schema.optionInt, Some(42)) &&
        encodeAndDecode(Schema.optionInt, None)
      },
      test("Either[String, Int]") {
        val schema = Schema[Either[String, Int]]
        encodeAndDecode(schema, Right(42)) &&
        encodeAndDecode(schema, Left("Error"))
      }
    ),
    suite("dynamic")(
      test("DynamicValue Primitive") {
        val schema = Schema[DynamicValue]
        val value  = Schema[Int].toDynamicValue(42)
        encodeAndDecode(schema, value)
      }
    )
  )

  def encodeAndDecode[A](schema: Schema[A], value: A): TestResult = {
    val binaryCodec = schema.derive(ThriftFormat.deriver)
    val output      = java.nio.ByteBuffer.allocate(4096)
    try {
      binaryCodec.encode(value, output)
      output.flip()
      val decoded = binaryCodec.decode(output)
      assert(decoded)(isRight(equalTo(value)))
    } catch {
      case e: Exception => assertTrue(false) ?? s"Encoding/Decoding failed: $e"
    }
  }
}
