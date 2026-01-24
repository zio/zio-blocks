package zio.blocks.schema.json

import zio.blocks.schema.SchemaBaseSpec
import zio.blocks.schema.JavaTimeGen._
import zio.blocks.schema._
import zio.test._
import zio.test.Assertion.{containsString, isLeft}
import zio.test.TestAspect.exceptNative
import java.time._
import java.util.{Currency, UUID}

object JsonInterpolatorSpec extends SchemaBaseSpec {
  case class Person(name: String, age: Int) {
    override def toString: String = Person.jsonCodec.encodeToString(this)
  }
  object Person {
    implicit val schema: Schema[Person]    = Schema.derived
    val jsonCodec: JsonBinaryCodec[Person] = schema.derive(JsonBinaryCodecDeriver)
  }

  case class Address(street: String, city: String)
  object Address { implicit val schema: Schema[Address] = Schema.derived }

  case class Employee(name: String, age: Int, address: Address)
  object Employee { implicit val schema: Schema[Employee] = Schema.derived }

  case class Inner(value: Int)
  object Inner { implicit val schema: Schema[Inner] = Schema.derived }

  case class Outer(inner: Inner, inners: List[Inner])
  object Outer { implicit val schema: Schema[Outer] = Schema.derived }

  sealed trait Status
  object Status {
    case object Active                   extends Status
    case class Suspended(reason: String) extends Status
    implicit val schema: Schema[Status] = Schema.derived
  }

  case class Item(name: String)
  object Item { implicit val schema: Schema[Item] = Schema.derived }

  case class Point(x: Int, y: Int)
  object Point { implicit val schema: Schema[Point] = Schema.derived }

  case class Stats(count: Int)
  object Stats { implicit val schema: Schema[Stats] = Schema.derived }

  case class Data(value: Int)
  object Data { implicit val schema: Schema[Data] = Schema.derived }

  case class ArrayItem(n: Int)
  object ArrayItem { implicit val schema: Schema[ArrayItem] = Schema.derived }

  def spec: Spec[TestEnvironment, Any] = suite("JsonInterpolatorSpec")(
    suite("key position interpolation")(
      test("supports all PrimitiveType types as keys") {
        val s: String                      = "key"
        val b: Boolean                     = true
        val byte: Byte                     = 1
        val short: Short                   = 2
        val int: Int                       = 3
        val long: Long                     = 4L
        val float: Float                   = 1.5f
        val double: Double                 = 2.5
        val char: Char                     = 'k'
        val bigInt: BigInt                 = BigInt("12345678901234567890")
        val bigDec: BigDecimal             = BigDecimal("123.456")
        val uuid: UUID                     = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val instant: Instant               = Instant.now()
        val localDate: LocalDate           = LocalDate.of(2024, 1, 15)
        val localTime: LocalTime           = LocalTime.of(10, 30)
        val localDateTime: LocalDateTime   = LocalDateTime.of(2024, 1, 15, 10, 30)
        val offsetTime: OffsetTime         = OffsetTime.of(10, 30, 0, 0, ZoneOffset.UTC)
        val offsetDateTime: OffsetDateTime = OffsetDateTime.now()
        val zonedDateTime: ZonedDateTime   = ZonedDateTime.now()
        val duration: Duration             = Duration.ofHours(1)
        val period: Period                 = Period.ofDays(30)
        val dayOfWeek: DayOfWeek           = DayOfWeek.MONDAY
        val month: Month                   = Month.JANUARY
        val monthDay: MonthDay             = MonthDay.of(1, 15)
        val year: Year                     = Year.of(2024)
        val yearMonth: YearMonth           = YearMonth.of(2024, 1)
        val zoneId: ZoneId                 = ZoneId.of("UTC")
        val zoneOffset: ZoneOffset         = ZoneOffset.UTC
        val currency: Currency             = Currency.getInstance("USD")

        assertTrue(
          json"""{$s: 1}""".get(s).int == Right(1),
          json"""{$b: 1}""".get("true").int == Right(1),
          json"""{$byte: 1}""".get("1").int == Right(1),
          json"""{$short: 1}""".get("2").int == Right(1),
          json"""{$int: 1}""".get("3").int == Right(1),
          json"""{$long: 1}""".get("4").int == Right(1),
          json"""{$float: 1}""".get("1.5").int == Right(1),
          json"""{$double: 1}""".get("2.5").int == Right(1),
          json"""{$char: 1}""".get("k").int == Right(1),
          json"""{$bigInt: 1}""".get("12345678901234567890").int == Right(1),
          json"""{$bigDec: 1}""".get("123.456").int == Right(1),
          json"""{$uuid: 1}""".get(uuid.toString).int == Right(1),
          json"""{$instant: 1}""".get(instant.toString).int == Right(1),
          json"""{$localDate: 1}""".get(localDate.toString).int == Right(1),
          json"""{$localTime: 1}""".get(localTime.toString).int == Right(1),
          json"""{$localDateTime: 1}""".get(localDateTime.toString).int == Right(1),
          json"""{$offsetTime: 1}""".get(offsetTime.toString).int == Right(1),
          json"""{$offsetDateTime: 1}""".get(offsetDateTime.toString).int == Right(1),
          json"""{$zonedDateTime: 1}""".get(zonedDateTime.toString).int == Right(1),
          json"""{$duration: 1}""".get(duration.toString).int == Right(1),
          json"""{$period: 1}""".get(period.toString).int == Right(1),
          json"""{$dayOfWeek: 1}""".get(dayOfWeek.toString).int == Right(1),
          json"""{$month: 1}""".get(month.toString).int == Right(1),
          json"""{$monthDay: 1}""".get(monthDay.toString).int == Right(1),
          json"""{$year: 1}""".get(year.toString).int == Right(1),
          json"""{$yearMonth: 1}""".get(yearMonth.toString).int == Right(1),
          json"""{$zoneId: 1}""".get(zoneId.toString).int == Right(1),
          json"""{$zoneOffset: 1}""".get(zoneOffset.toString).int == Right(1),
          json"""{$currency: 1}""".get("USD").int == Right(1)
        )
      },

      test("property: stringable types work as keys") {
        check(Gen.long.zip(Gen.long).map { case (m, l) => new UUID(m, l) }) { uuid =>
          assertTrue(json"""{$uuid: "v"}""".get(uuid.toString).string == Right("v"))
        } &&
        check(Gen.int) { n =>
          assertTrue(json"""{$n: "v"}""".get(n.toString).string == Right("v"))
        } &&
        check(genInstant) { instant =>
          assertTrue(json"""{$instant: "v"}""".get(instant.toString).string == Right("v"))
        } &&
        check(genLocalDate) { date =>
          assertTrue(json"""{$date: "v"}""".get(date.toString).string == Right("v"))
        }
      },

      test("compile fails for non-stringable types in key position") {
        typeCheck("""
          case class Point(x: Int, y: Int)
          object Point { implicit val schema: Schema[Point] = Schema.derived }
          val p = Point(1, 2)
          json"{ $p: \"value\" }"
        """).map(assert(_)(isLeft(containsString("key"))))
      } @@ exceptNative
    ),

    suite("value position interpolation")(
      test("supports types with Schema") {
        val alice  = Employee("Alice", 30, Address("123 Main", "NYC"))
        val result = json"""{"employee": $alice}"""

        assertTrue(
          result.get("employee").get("name").string == Right("Alice"),
          result.get("employee").get("age").int == Right(30),
          result.get("employee").get("address").get("city").string == Right("NYC")
        )
      },

      test("supports nested complex types") {
        val o      = Outer(Inner(1), List(Inner(2), Inner(3)))
        val result = json"""{"data": $o}"""

        assertTrue(
          result.get("data").get("inner").get("value").int == Right(1),
          result.get("data").get("inners")(0).get("value").int == Right(2)
        )
      },

      test("supports sealed traits") {
        val active: Status    = Status.Active
        val suspended: Status = Status.Suspended("Payment overdue")

        assertTrue(
          json"""{"status": $active}""".get("status").one.isRight,
          json"""{"status": $suspended}""".get("status").get("Suspended").get("reason").string == Right(
            "Payment overdue"
          )
        )
      },

      test("supports Option of complex types") {
        val some: Option[Item] = Some(Item("thing"))
        val none: Option[Item] = None

        assertTrue(
          json"""{"item": $some}""".get("item").get("name").string == Right("thing"),
          json"""{"item": $none}""".get("item").one == Right(Json.Null)
        )
      },

      test("supports collections of complex types") {
        val points   = List(Point(1, 2), Point(3, 4))
        val pointSet = Set(Point(5, 6))
        val pointVec = Vector(Point(7, 8))

        assertTrue(
          json"""{"points": $points}""".get("points")(0).get("x").int == Right(1),
          json"""{"points": $pointSet}""".get("points")(0).get("x").int == Right(5),
          json"""{"points": $pointVec}""".get("points")(0).get("x").int == Right(7)
        )
      },

      test("supports Map with complex value types") {
        val data   = Map("a" -> Stats(10), "b" -> Stats(20))
        val result = json"""{"stats": $data}"""

        assertTrue(
          result.get("stats").get("a").get("count").int == Right(10),
          result.get("stats").get("b").get("count").int == Right(20)
        )
      },

      test("compile fails for types without JsonEncoder") {
        typeCheck("""
          case class NoSchema(x: Int)
          val v = NoSchema(1)
          json"{"value": $v}"
        """).map(assert(_)(isLeft))
      } @@ exceptNative
    ),

    suite("string literal interpolation")(
      test("supports String interpolation in strings") {
        val name = "Alice"
        assertTrue(
          json"""{"greeting": "Hello, $name!"}""".get("greeting").string == Right("Hello, Alice!")
        )
      },

      test("supports numeric types in strings") {
        val x   = 42
        val y   = 3.14
        val big = BigInt("12345678901234567890")

        assertTrue(
          json"""{"msg": "x is $x"}""".get("msg").string == Right("x is 42"),
          json"""{"msg": "y is $y"}""".get("msg").string == Right("y is 3.14"),
          json"""{"msg": "big is $big"}""".get("msg").string == Right("big is 12345678901234567890")
        )
      },

      test("supports UUID in strings") {
        val id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        assertTrue(
          json"""{"ref": "user-$id"}""".get("ref").string == Right("user-550e8400-e29b-41d4-a716-446655440000")
        )
      },

      test("supports temporal types in strings") {
        val date    = LocalDate.of(2024, 1, 15)
        val time    = LocalTime.of(10, 30, 0)
        val instant = Instant.parse("2024-01-15T10:30:00Z")

        assertTrue(
          json"""{"file": "report-$date.pdf"}""".get("file").string == Right("report-2024-01-15.pdf"),
          json"""{"log": "Event at $time"}""".get("log").string == Right("Event at 10:30"),
          json"""{"ts": "Created: $instant"}""".get("ts").string == Right("Created: 2024-01-15T10:30:00Z")
        )
      },

      test("supports Currency in strings") {
        val currency = Currency.getInstance("USD")
        assertTrue(
          json"""{"label": "Price in $currency"}""".get("label").string == Right("Price in USD")
        )
      },

      test("supports ${expr} syntax for expressions") {
        val x     = 10
        val items = List("a", "b", "c")

        assertTrue(
          json"""{"range": "${x * 2} to ${x * 3}"}""".get("range").string == Right("20 to 30"),
          json"""{"count": "Found ${items.size} items"}""".get("count").string == Right("Found 3 items")
        )
      },

      test("supports multiple interpolations in one string") {
        val date    = LocalDate.of(2024, 1, 15)
        val version = 3
        val env     = "prod"

        assertTrue(
          json"""{"path": "/data/$env/$date/v$version/output.json"}""".get("path").string ==
            Right("/data/prod/2024-01-15/v3/output.json")
        )
      },

      test("handles empty interpolation results") {
        val empty = ""
        assertTrue(
          json"""{"msg": "[$empty]"}""".get("msg").string == Right("[]")
        )
      },

      test("handles special characters in interpolated strings") {
        val path  = "foo/bar"
        val query = "a=1&b=2"

        assertTrue(
          json"""{"url": "http://example.com/$path?$query"}""".get("url").string ==
            Right("http://example.com/foo/bar?a=1&b=2")
        )
      },

      test("compile fails for non-stringable types in string literals") {
        typeCheck("""
          case class Point(x: Int, y: Int)
          object Point { implicit val schema: Schema[Point] = Schema.derived }
          val p = Point(1, 2)
          json"{"msg": "Point is $p"}"
        """).map(assert(_)(isLeft))
      } @@ exceptNative
    ),

    suite("mixed interpolation contexts")(
      test("combines key, value, and string interpolation") {
        val key       = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val data      = Data(42)
        val timestamp = Instant.now()

        val result = json"""{
          $key: {
            "data": $data,
            "note": "Recorded at $timestamp"
          }
        }"""

        assertTrue(
          result.get(key.toString).get("data").get("value").int == Right(42),
          result.get(key.toString).get("note").string == Right(s"Recorded at $timestamp")
        )
      },

      test("multiple keys with different stringable types") {
        val intKey  = 1
        val uuidKey = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val dateKey = LocalDate.of(2024, 1, 15)

        val result = json"""{
          $intKey: "one",
          $uuidKey: "uuid",
          $dateKey: "date"
        }"""

        assertTrue(
          result.get("1").string == Right("one"),
          result.get(uuidKey.toString).string == Right("uuid"),
          result.get("2024-01-15").string == Right("date")
        )
      },

      test("array with mixed value types") {
        val item = ArrayItem(1)
        val num  = 42
        val str  = "hello"

        val result = json"""[$item, $num, $str]"""

        assertTrue(
          result(0).get("n").int == Right(1),
          result(1).int == Right(42),
          result(2).string == Right("hello")
        )
      }
    ),
    test("supports interpolated keys and values of other types with overridden toString") {
      val x = Person("Alice", 20)
      assertTrue(
        json"""{"x": $x}""".get("x").one == Right(Json.obj("name" -> Json.str("Alice"), "age" -> Json.number(20))),
        json"""{${x.toString}: "v"}""".get(x.toString).string == Right("v")
      )
    },
    test("doesn't compile for invalid json") {
      typeCheck {
        """json"1e""""
      }.map(assert(_)(isLeft(containsString("Invalid JSON literal: unexpected end of input at: .")))) &&
      typeCheck {
        """json"[1,02]""""
      }.map(assert(_)(isLeft(containsString("Invalid JSON literal: illegal number with leading zero at: .at(1)"))))
    } @@ exceptNative
  )
}
