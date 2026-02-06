package zio.blocks.schema

import zio.test._
import java.util.UUID
import java.time._
import java.util.Currency

object SchemaMatchSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("SchemaMatchSpec")(
    suite("Wildcard")(
      test("matches Primitive") {
        val wildcard = SchemaRepr.Wildcard
        val value    = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        assertTrue(SchemaMatch.matches(wildcard, value))
      },
      test("matches Record") {
        val wildcard = SchemaRepr.Wildcard
        val value    = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        assertTrue(SchemaMatch.matches(wildcard, value))
      },
      test("matches Variant") {
        val wildcard = SchemaRepr.Wildcard
        val value    = DynamicValue.Variant("Left", DynamicValue.int(42))
        assertTrue(SchemaMatch.matches(wildcard, value))
      },
      test("matches Sequence") {
        val wildcard = SchemaRepr.Wildcard
        val value    = DynamicValue.Sequence(DynamicValue.int(1), DynamicValue.int(2))
        assertTrue(SchemaMatch.matches(wildcard, value))
      },
      test("matches Map") {
        val wildcard = SchemaRepr.Wildcard
        val value    = DynamicValue.Map(DynamicValue.string("a") -> DynamicValue.int(1))
        assertTrue(SchemaMatch.matches(wildcard, value))
      },
      test("matches Null") {
        val wildcard = SchemaRepr.Wildcard
        assertTrue(SchemaMatch.matches(wildcard, DynamicValue.Null))
      }
    ),
    suite("Primitive")(
      test("string matches string") {
        val pattern = SchemaRepr.Primitive("string")
        val value   = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("String (uppercase) matches string") {
        val pattern = SchemaRepr.Primitive("String")
        val value   = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("string rejects int") {
        val pattern = SchemaRepr.Primitive("string")
        val value   = DynamicValue.Primitive(PrimitiveValue.Int(42))
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("int matches int") {
        val pattern = SchemaRepr.Primitive("int")
        val value   = DynamicValue.Primitive(PrimitiveValue.Int(42))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("int rejects long") {
        val pattern = SchemaRepr.Primitive("int")
        val value   = DynamicValue.Primitive(PrimitiveValue.Long(42L))
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("long matches long") {
        val pattern = SchemaRepr.Primitive("long")
        val value   = DynamicValue.Primitive(PrimitiveValue.Long(42L))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("short matches short") {
        val pattern = SchemaRepr.Primitive("short")
        val value   = DynamicValue.Primitive(PrimitiveValue.Short(42.toShort))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("byte matches byte") {
        val pattern = SchemaRepr.Primitive("byte")
        val value   = DynamicValue.Primitive(PrimitiveValue.Byte(42.toByte))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("float matches float") {
        val pattern = SchemaRepr.Primitive("float")
        val value   = DynamicValue.Primitive(PrimitiveValue.Float(3.14f))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("double matches double") {
        val pattern = SchemaRepr.Primitive("double")
        val value   = DynamicValue.Primitive(PrimitiveValue.Double(3.14))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("boolean matches boolean") {
        val pattern = SchemaRepr.Primitive("boolean")
        val value   = DynamicValue.Primitive(PrimitiveValue.Boolean(true))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("char matches char") {
        val pattern = SchemaRepr.Primitive("char")
        val value   = DynamicValue.Primitive(PrimitiveValue.Char('x'))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("unit matches unit") {
        val pattern = SchemaRepr.Primitive("unit")
        val value   = DynamicValue.Primitive(PrimitiveValue.Unit)
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("bigint matches bigint") {
        val pattern = SchemaRepr.Primitive("bigint")
        val value   = DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(123456789)))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("bigdecimal matches bigdecimal") {
        val pattern = SchemaRepr.Primitive("bigdecimal")
        val value   = DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("123.456")))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("uuid matches uuid") {
        val pattern = SchemaRepr.Primitive("uuid")
        // Use fixed UUID instead of randomUUID() for Scala.js compatibility (no SecureRandom)
        val value = DynamicValue.Primitive(PrimitiveValue.UUID(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("instant matches instant") {
        val pattern = SchemaRepr.Primitive("instant")
        val value   = DynamicValue.Primitive(PrimitiveValue.Instant(Instant.now()))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("localdate matches localdate") {
        val pattern = SchemaRepr.Primitive("localdate")
        val value   = DynamicValue.Primitive(PrimitiveValue.LocalDate(LocalDate.now()))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("localtime matches localtime") {
        val pattern = SchemaRepr.Primitive("localtime")
        val value   = DynamicValue.Primitive(PrimitiveValue.LocalTime(LocalTime.now()))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("localdatetime matches localdatetime") {
        val pattern = SchemaRepr.Primitive("localdatetime")
        val value   = DynamicValue.Primitive(PrimitiveValue.LocalDateTime(LocalDateTime.now()))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("offsettime matches offsettime") {
        val pattern = SchemaRepr.Primitive("offsettime")
        val value   = DynamicValue.Primitive(PrimitiveValue.OffsetTime(OffsetTime.now()))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("offsetdatetime matches offsetdatetime") {
        val pattern = SchemaRepr.Primitive("offsetdatetime")
        val value   = DynamicValue.Primitive(PrimitiveValue.OffsetDateTime(OffsetDateTime.now()))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("zoneddatetime matches zoneddatetime") {
        val pattern = SchemaRepr.Primitive("zoneddatetime")
        val value   = DynamicValue.Primitive(PrimitiveValue.ZonedDateTime(ZonedDateTime.now()))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("dayofweek matches dayofweek") {
        val pattern = SchemaRepr.Primitive("dayofweek")
        val value   = DynamicValue.Primitive(PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("month matches month") {
        val pattern = SchemaRepr.Primitive("month")
        val value   = DynamicValue.Primitive(PrimitiveValue.Month(Month.JANUARY))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("monthday matches monthday") {
        val pattern = SchemaRepr.Primitive("monthday")
        val value   = DynamicValue.Primitive(PrimitiveValue.MonthDay(MonthDay.of(1, 15)))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("year matches year") {
        val pattern = SchemaRepr.Primitive("year")
        val value   = DynamicValue.Primitive(PrimitiveValue.Year(Year.of(2024)))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("yearmonth matches yearmonth") {
        val pattern = SchemaRepr.Primitive("yearmonth")
        val value   = DynamicValue.Primitive(PrimitiveValue.YearMonth(YearMonth.of(2024, 1)))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("period matches period") {
        val pattern = SchemaRepr.Primitive("period")
        val value   = DynamicValue.Primitive(PrimitiveValue.Period(Period.ofDays(30)))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("duration matches duration") {
        val pattern = SchemaRepr.Primitive("duration")
        val value   = DynamicValue.Primitive(PrimitiveValue.Duration(Duration.ofHours(1)))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("zoneoffset matches zoneoffset") {
        val pattern = SchemaRepr.Primitive("zoneoffset")
        val value   = DynamicValue.Primitive(PrimitiveValue.ZoneOffset(ZoneOffset.UTC))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("zoneid matches zoneid") {
        val pattern = SchemaRepr.Primitive("zoneid")
        val value   = DynamicValue.Primitive(PrimitiveValue.ZoneId(ZoneId.of("UTC")))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("currency matches currency") {
        val pattern = SchemaRepr.Primitive("currency")
        val value   = DynamicValue.Primitive(PrimitiveValue.Currency(Currency.getInstance("USD")))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("primitive pattern does not match Record") {
        val pattern = SchemaRepr.Primitive("string")
        val value   = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("primitive pattern does not match Null") {
        val pattern = SchemaRepr.Primitive("string")
        assertTrue(!SchemaMatch.matches(pattern, DynamicValue.Null))
      },
      test("primitive pattern does not match Sequence") {
        val pattern = SchemaRepr.Primitive("string")
        val value   = DynamicValue.Sequence(DynamicValue.string("hello"))
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("primitive pattern does not match Variant") {
        val pattern = SchemaRepr.Primitive("string")
        val value   = DynamicValue.Variant("Some", DynamicValue.string("hello"))
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("primitive pattern does not match Map") {
        val pattern = SchemaRepr.Primitive("string")
        val value   = DynamicValue.Map(DynamicValue.string("key") -> DynamicValue.int(1))
        assertTrue(!SchemaMatch.matches(pattern, value))
      }
    ),
    suite("Record")(
      test("matches record with same fields") {
        val pattern = SchemaRepr.Record(
          Vector(
            "name" -> SchemaRepr.Primitive("string"),
            "age"  -> SchemaRepr.Primitive("int")
          )
        )
        val value = DynamicValue.Record(
          "name" -> DynamicValue.string("Alice"),
          "age"  -> DynamicValue.int(30)
        )
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("matches record with extra fields (subset matching)") {
        val pattern = SchemaRepr.Record(
          Vector(
            "name" -> SchemaRepr.Primitive("string")
          )
        )
        val value = DynamicValue.Record(
          "name"  -> DynamicValue.string("Alice"),
          "age"   -> DynamicValue.int(30),
          "email" -> DynamicValue.string("alice@example.com")
        )
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("rejects record missing required field") {
        val pattern = SchemaRepr.Record(
          Vector(
            "name"  -> SchemaRepr.Primitive("string"),
            "email" -> SchemaRepr.Primitive("string")
          )
        )
        val value = DynamicValue.Record(
          "name" -> DynamicValue.string("Alice"),
          "age"  -> DynamicValue.int(30)
        )
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("rejects record where field has wrong type") {
        val pattern = SchemaRepr.Record(
          Vector(
            "name" -> SchemaRepr.Primitive("string"),
            "age"  -> SchemaRepr.Primitive("int")
          )
        )
        val value = DynamicValue.Record(
          "name" -> DynamicValue.string("Alice"),
          "age"  -> DynamicValue.string("thirty") // Wrong type
        )
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("empty pattern matches any record") {
        val pattern = SchemaRepr.Record(Vector.empty)
        val value   = DynamicValue.Record(
          "name" -> DynamicValue.string("Alice"),
          "age"  -> DynamicValue.int(30)
        )
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("empty pattern matches empty record") {
        val pattern = SchemaRepr.Record(Vector.empty)
        val value   = DynamicValue.Record.empty
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("non-empty pattern rejects empty record") {
        val pattern = SchemaRepr.Record(
          Vector(
            "name" -> SchemaRepr.Primitive("string")
          )
        )
        val value = DynamicValue.Record.empty
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("record pattern does not match Primitive") {
        val pattern = SchemaRepr.Record(
          Vector(
            "name" -> SchemaRepr.Primitive("string")
          )
        )
        val value = DynamicValue.string("Alice")
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("record pattern does not match Sequence") {
        val pattern = SchemaRepr.Record(Vector("name" -> SchemaRepr.Primitive("string")))
        val value   = DynamicValue.Sequence(DynamicValue.string("Alice"))
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("record pattern does not match Variant") {
        val pattern = SchemaRepr.Record(Vector("name" -> SchemaRepr.Primitive("string")))
        val value   = DynamicValue.Variant("Some", DynamicValue.string("Alice"))
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("record pattern does not match Map") {
        val pattern = SchemaRepr.Record(Vector("name" -> SchemaRepr.Primitive("string")))
        val value   = DynamicValue.Map(DynamicValue.string("name") -> DynamicValue.string("Alice"))
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("matches nested records") {
        val pattern = SchemaRepr.Record(
          Vector(
            "address" -> SchemaRepr.Record(
              Vector(
                "city" -> SchemaRepr.Primitive("string")
              )
            )
          )
        )
        val value = DynamicValue.Record(
          "name"    -> DynamicValue.string("Alice"),
          "address" -> DynamicValue.Record(
            "street" -> DynamicValue.string("123 Main St"),
            "city"   -> DynamicValue.string("Boston")
          )
        )
        assertTrue(SchemaMatch.matches(pattern, value))
      }
    ),
    suite("Variant")(
      test("matches variant with matching case") {
        val pattern = SchemaRepr.Variant(
          Vector(
            "Left"  -> SchemaRepr.Primitive("int"),
            "Right" -> SchemaRepr.Primitive("string")
          )
        )
        val value = DynamicValue.Variant("Left", DynamicValue.int(42))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("rejects variant with non-matching case name") {
        val pattern = SchemaRepr.Variant(
          Vector(
            "Left" -> SchemaRepr.Primitive("int")
          )
        )
        val value = DynamicValue.Variant("Right", DynamicValue.int(42))
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("rejects variant with wrong payload type") {
        val pattern = SchemaRepr.Variant(
          Vector(
            "Left" -> SchemaRepr.Primitive("int")
          )
        )
        val value = DynamicValue.Variant("Left", DynamicValue.string("not an int"))
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("matches when multiple cases exist and one matches") {
        val pattern = SchemaRepr.Variant(
          Vector(
            "Left"   -> SchemaRepr.Primitive("int"),
            "Right"  -> SchemaRepr.Primitive("string"),
            "Middle" -> SchemaRepr.Primitive("boolean")
          )
        )
        val value = DynamicValue.Variant("Middle", DynamicValue.boolean(true))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("variant pattern does not match Record") {
        val pattern = SchemaRepr.Variant(
          Vector(
            "Left" -> SchemaRepr.Primitive("int")
          )
        )
        val value = DynamicValue.Record("Left" -> DynamicValue.int(42))
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("variant pattern does not match Sequence") {
        val pattern = SchemaRepr.Variant(Vector("Left" -> SchemaRepr.Primitive("int")))
        val value   = DynamicValue.Sequence(DynamicValue.int(42))
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("variant pattern does not match Map") {
        val pattern = SchemaRepr.Variant(Vector("Left" -> SchemaRepr.Primitive("int")))
        val value   = DynamicValue.Map(DynamicValue.string("Left") -> DynamicValue.int(42))
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("variant pattern does not match Primitive") {
        val pattern = SchemaRepr.Variant(Vector("Left" -> SchemaRepr.Primitive("int")))
        val value   = DynamicValue.int(42)
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("matches variant with nested record payload") {
        val pattern = SchemaRepr.Variant(
          Vector(
            "Success" -> SchemaRepr.Record(
              Vector(
                "value" -> SchemaRepr.Primitive("string")
              )
            )
          )
        )
        val value = DynamicValue.Variant(
          "Success",
          DynamicValue.Record(
            "value"  -> DynamicValue.string("ok"),
            "status" -> DynamicValue.int(200)
          )
        )
        assertTrue(SchemaMatch.matches(pattern, value))
      }
    ),
    suite("Sequence")(
      test("matches sequence of strings") {
        val pattern = SchemaRepr.Sequence(SchemaRepr.Primitive("string"))
        val value   = DynamicValue.Sequence(
          DynamicValue.string("a"),
          DynamicValue.string("b"),
          DynamicValue.string("c")
        )
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("rejects sequence with wrong element type") {
        val pattern = SchemaRepr.Sequence(SchemaRepr.Primitive("string"))
        val value   = DynamicValue.Sequence(
          DynamicValue.int(1),
          DynamicValue.int(2)
        )
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("rejects sequence with mixed types when pattern expects one type") {
        val pattern = SchemaRepr.Sequence(SchemaRepr.Primitive("string"))
        val value   = DynamicValue.Sequence(
          DynamicValue.string("a"),
          DynamicValue.int(1)
        )
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("empty sequence matches any element pattern") {
        val pattern = SchemaRepr.Sequence(SchemaRepr.Primitive("string"))
        val value   = DynamicValue.Sequence.empty
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("sequence with wildcard element matches any sequence") {
        val pattern = SchemaRepr.Sequence(SchemaRepr.Wildcard)
        val value   = DynamicValue.Sequence(
          DynamicValue.string("a"),
          DynamicValue.int(1),
          DynamicValue.boolean(true)
        )
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("sequence pattern does not match Primitive") {
        val pattern = SchemaRepr.Sequence(SchemaRepr.Primitive("string"))
        val value   = DynamicValue.string("not a sequence")
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("sequence pattern does not match Record") {
        val pattern = SchemaRepr.Sequence(SchemaRepr.Primitive("int"))
        val value   = DynamicValue.Record("items" -> DynamicValue.int(1))
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("sequence pattern does not match Map") {
        val pattern = SchemaRepr.Sequence(SchemaRepr.Primitive("int"))
        val value   = DynamicValue.Map(DynamicValue.int(1) -> DynamicValue.int(10))
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("sequence pattern does not match Variant") {
        val pattern = SchemaRepr.Sequence(SchemaRepr.Primitive("int"))
        val value   = DynamicValue.Variant("Some", DynamicValue.int(42))
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("matches sequence of records") {
        val pattern = SchemaRepr.Sequence(
          SchemaRepr.Record(
            Vector(
              "id" -> SchemaRepr.Primitive("int")
            )
          )
        )
        val value = DynamicValue.Sequence(
          DynamicValue.Record("id" -> DynamicValue.int(1), "name" -> DynamicValue.string("A")),
          DynamicValue.Record("id" -> DynamicValue.int(2), "name" -> DynamicValue.string("B"))
        )
        assertTrue(SchemaMatch.matches(pattern, value))
      }
    ),
    suite("Map")(
      test("matches map with correct key and value types") {
        val pattern = SchemaRepr.Map(SchemaRepr.Primitive("string"), SchemaRepr.Primitive("int"))
        val value   = DynamicValue.Map(
          DynamicValue.string("a") -> DynamicValue.int(1),
          DynamicValue.string("b") -> DynamicValue.int(2)
        )
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("rejects map with wrong key type") {
        val pattern = SchemaRepr.Map(SchemaRepr.Primitive("string"), SchemaRepr.Primitive("int"))
        val value   = DynamicValue.Map(
          DynamicValue.int(1) -> DynamicValue.int(10)
        )
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("rejects map with wrong value type") {
        val pattern = SchemaRepr.Map(SchemaRepr.Primitive("string"), SchemaRepr.Primitive("int"))
        val value   = DynamicValue.Map(
          DynamicValue.string("a") -> DynamicValue.string("not an int")
        )
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("empty map matches any key/value pattern") {
        val pattern = SchemaRepr.Map(SchemaRepr.Primitive("string"), SchemaRepr.Primitive("int"))
        val value   = DynamicValue.Map.empty
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("map with wildcard patterns matches any map") {
        val pattern = SchemaRepr.Map(SchemaRepr.Wildcard, SchemaRepr.Wildcard)
        val value   = DynamicValue.Map(
          DynamicValue.string("a") -> DynamicValue.int(1),
          DynamicValue.int(2)      -> DynamicValue.boolean(true)
        )
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("map pattern does not match Sequence") {
        val pattern = SchemaRepr.Map(SchemaRepr.Primitive("string"), SchemaRepr.Primitive("int"))
        val value   = DynamicValue.Sequence(DynamicValue.int(1))
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("map pattern does not match Record") {
        val pattern = SchemaRepr.Map(SchemaRepr.Primitive("string"), SchemaRepr.Primitive("int"))
        val value   = DynamicValue.Record("a" -> DynamicValue.int(1))
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("map pattern does not match Variant") {
        val pattern = SchemaRepr.Map(SchemaRepr.Primitive("string"), SchemaRepr.Primitive("int"))
        val value   = DynamicValue.Variant("Some", DynamicValue.int(1))
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("map pattern does not match Primitive") {
        val pattern = SchemaRepr.Map(SchemaRepr.Primitive("string"), SchemaRepr.Primitive("int"))
        val value   = DynamicValue.int(42)
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("matches map with record values") {
        val pattern = SchemaRepr.Map(
          SchemaRepr.Primitive("string"),
          SchemaRepr.Record(Vector("count" -> SchemaRepr.Primitive("int")))
        )
        val value = DynamicValue.Map(
          DynamicValue.string("a") -> DynamicValue.Record("count" -> DynamicValue.int(5)),
          DynamicValue.string("b") -> DynamicValue.Record("count" -> DynamicValue.int(10))
        )
        assertTrue(SchemaMatch.matches(pattern, value))
      }
    ),
    suite("Optional")(
      test("matches Null") {
        val pattern = SchemaRepr.Optional(SchemaRepr.Primitive("string"))
        assertTrue(SchemaMatch.matches(pattern, DynamicValue.Null))
      },
      test("matches value of inner type") {
        val pattern = SchemaRepr.Optional(SchemaRepr.Primitive("string"))
        val value   = DynamicValue.string("hello")
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("rejects value of wrong type") {
        val pattern = SchemaRepr.Optional(SchemaRepr.Primitive("string"))
        val value   = DynamicValue.int(42)
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("nested optional matches inner Null") {
        val pattern = SchemaRepr.Optional(SchemaRepr.Optional(SchemaRepr.Primitive("string")))
        assertTrue(SchemaMatch.matches(pattern, DynamicValue.Null))
      },
      test("optional with wildcard matches anything") {
        val pattern = SchemaRepr.Optional(SchemaRepr.Wildcard)
        assertTrue(
          SchemaMatch.matches(pattern, DynamicValue.Null) &&
            SchemaMatch.matches(pattern, DynamicValue.int(42)) &&
            SchemaMatch.matches(pattern, DynamicValue.Record("x" -> DynamicValue.int(1)))
        )
      },
      test("optional record matches record value") {
        val pattern = SchemaRepr.Optional(
          SchemaRepr.Record(
            Vector(
              "name" -> SchemaRepr.Primitive("string")
            )
          )
        )
        val value = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("optional sequence matches sequence") {
        val pattern = SchemaRepr.Optional(SchemaRepr.Sequence(SchemaRepr.Primitive("int")))
        val value   = DynamicValue.Sequence(DynamicValue.int(1), DynamicValue.int(2))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("optional map matches map") {
        val pattern = SchemaRepr.Optional(SchemaRepr.Map(SchemaRepr.Primitive("string"), SchemaRepr.Primitive("int")))
        val value   = DynamicValue.Map(DynamicValue.string("a") -> DynamicValue.int(1))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("optional variant matches variant") {
        val pattern = SchemaRepr.Optional(SchemaRepr.Variant(Vector("Left" -> SchemaRepr.Primitive("int"))))
        val value   = DynamicValue.Variant("Left", DynamicValue.int(42))
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("optional does not match wrong type") {
        val pattern = SchemaRepr.Optional(SchemaRepr.Primitive("string"))
        val value   = DynamicValue.Sequence(DynamicValue.string("hello"))
        assertTrue(!SchemaMatch.matches(pattern, value))
      }
    ),
    suite("Nominal")(
      test("always returns false (requires schema context)") {
        val pattern = SchemaRepr.Nominal("Person")
        val value   = DynamicValue.Record(
          "name" -> DynamicValue.string("Alice"),
          "age"  -> DynamicValue.int(30)
        )
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("does not match even with matching structure") {
        val pattern = SchemaRepr.Nominal("String")
        val value   = DynamicValue.string("hello")
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("nominal does not match sequence") {
        val pattern = SchemaRepr.Nominal("List")
        val value   = DynamicValue.Sequence(DynamicValue.int(1))
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("nominal does not match map") {
        val pattern = SchemaRepr.Nominal("Map")
        val value   = DynamicValue.Map(DynamicValue.string("a") -> DynamicValue.int(1))
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("nominal does not match variant") {
        val pattern = SchemaRepr.Nominal("Either")
        val value   = DynamicValue.Variant("Left", DynamicValue.int(42))
        assertTrue(!SchemaMatch.matches(pattern, value))
      },
      test("nominal does not match Null") {
        val pattern = SchemaRepr.Nominal("Null")
        assertTrue(!SchemaMatch.matches(pattern, DynamicValue.Null))
      }
    ),
    suite("Nested patterns")(
      test("deeply nested record pattern") {
        val pattern = SchemaRepr.Record(
          Vector(
            "level1" -> SchemaRepr.Record(
              Vector(
                "level2" -> SchemaRepr.Record(
                  Vector(
                    "value" -> SchemaRepr.Primitive("int")
                  )
                )
              )
            )
          )
        )
        val value = DynamicValue.Record(
          "level1" -> DynamicValue.Record(
            "level2" -> DynamicValue.Record(
              "value" -> DynamicValue.int(42)
            )
          )
        )
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("sequence of sequences") {
        val pattern = SchemaRepr.Sequence(SchemaRepr.Sequence(SchemaRepr.Primitive("int")))
        val value   = DynamicValue.Sequence(
          DynamicValue.Sequence(DynamicValue.int(1), DynamicValue.int(2)),
          DynamicValue.Sequence(DynamicValue.int(3), DynamicValue.int(4))
        )
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("record containing sequence containing record") {
        val pattern = SchemaRepr.Record(
          Vector(
            "items" -> SchemaRepr.Sequence(
              SchemaRepr.Record(
                Vector(
                  "id" -> SchemaRepr.Primitive("int")
                )
              )
            )
          )
        )
        val value = DynamicValue.Record(
          "items" -> DynamicValue.Sequence(
            DynamicValue.Record("id" -> DynamicValue.int(1)),
            DynamicValue.Record("id" -> DynamicValue.int(2))
          )
        )
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("variant inside sequence") {
        val pattern = SchemaRepr.Sequence(
          SchemaRepr.Variant(
            Vector(
              "Some" -> SchemaRepr.Primitive("int"),
              "None" -> SchemaRepr.Primitive("unit")
            )
          )
        )
        val value = DynamicValue.Sequence(
          DynamicValue.Variant("Some", DynamicValue.int(1)),
          DynamicValue.Variant("None", DynamicValue.unit),
          DynamicValue.Variant("Some", DynamicValue.int(2))
        )
        assertTrue(SchemaMatch.matches(pattern, value))
      },
      test("map with record keys and sequence values") {
        val pattern = SchemaRepr.Map(
          SchemaRepr.Record(Vector("key" -> SchemaRepr.Primitive("string"))),
          SchemaRepr.Sequence(SchemaRepr.Primitive("int"))
        )
        val value = DynamicValue.Map(
          DynamicValue.Record("key" -> DynamicValue.string("a")) -> DynamicValue.Sequence(
            DynamicValue.int(1),
            DynamicValue.int(2)
          )
        )
        assertTrue(SchemaMatch.matches(pattern, value))
      }
    )
  )
}
