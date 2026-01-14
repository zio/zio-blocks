package zio.blocks.schema.toon

import zio.blocks.schema.toon.ToonTestUtils._
import zio.blocks.schema._
import zio.blocks.schema.binding.Binding
import zio.test._
import java.time._
import java.util.{Currency, UUID}

object ToonBinaryCodecDeriverSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("ToonBinaryCodecDeriverSpec")(
    suite("primitives")(
      test("Unit") {
        roundTrip((), "null")
      },
      test("Boolean") {
        roundTrip(true, "true") &&
        roundTrip(false, "false") &&
        decodeError[Boolean]("yes", "Expected boolean")
      },
      test("Byte") {
        roundTrip(1: Byte, "1") &&
        roundTrip(Byte.MinValue, "-128") &&
        roundTrip(Byte.MaxValue, "127")
      },
      test("Short") {
        roundTrip(1: Short, "1") &&
        roundTrip(Short.MinValue, "-32768") &&
        roundTrip(Short.MaxValue, "32767")
      },
      test("Int") {
        roundTrip(42, "42") &&
        roundTrip(Int.MinValue, "-2147483648") &&
        roundTrip(Int.MaxValue, "2147483647")
      },
      test("Long") {
        roundTrip(42L, "42") &&
        roundTrip(Long.MinValue, "-9223372036854775808") &&
        roundTrip(Long.MaxValue, "9223372036854775807")
      },
      test("Float") {
        roundTrip(0.0f, "0") &&
        roundTrip(5.0f, "5") &&
        decode("-3.14", -3.14f) &&
        roundTrip(1.5f, "1.5") &&
        encode(Float.NaN, "null") &&
        encode(Float.PositiveInfinity, "null") &&
        encode(Float.NegativeInfinity, "null")
        // Note: Float.MinValue/MaxValue cannot round-trip because TOON forbids
        // scientific notation, and the plain decimal form exceeds representable precision
      },
      test("Double") {
        roundTrip(0.0, "0") &&
        roundTrip(6.0, "6") &&
        roundTrip(-2.71828, "-2.71828") &&
        roundTrip(1.5, "1.5") &&
        encode(Double.NaN, "null") &&
        encode(Double.PositiveInfinity, "null") &&
        encode(Double.NegativeInfinity, "null") &&
        encode(-0.0, "0")
        // Note: Double.MinValue/MaxValue cannot round-trip because TOON forbids
        // scientific notation, and the plain decimal form exceeds representable precision
      },
      test("Char") {
        roundTrip('A', "A") &&
        roundTrip('7', "7")
      },
      test("String") {
        roundTrip("Hello", "Hello") &&
        roundTrip("Hello World", "Hello World") &&
        roundTrip("", "\"\"")
      },
      test("BigInt") {
        roundTrip(BigInt(0), "0") &&
        roundTrip(BigInt(12345), "12345") &&
        roundTrip(BigInt("-" + "9" * 20), "-" + "9" * 20)
      },
      test("BigDecimal") {
        roundTrip(BigDecimal("0.0"), "0") &&
        roundTrip(BigDecimal("123.45"), "123.45") &&
        roundTrip(BigDecimal("1.0"), "1") &&
        roundTrip(BigDecimal("100.00"), "100")
      },
      test("DayOfWeek") {
        roundTrip(DayOfWeek.MONDAY, "MONDAY") &&
        roundTrip(DayOfWeek.FRIDAY, "FRIDAY") &&
        decodeError[DayOfWeek]("FUNDAY", "Invalid day of week")
      },
      test("Duration") {
        roundTrip(Duration.ofSeconds(0), "PT0S") &&
        roundTrip(Duration.ofHours(2), "PT2H") &&
        decodeError[Duration]("5 hours", "Invalid duration")
      },
      test("Instant") {
        roundTrip(Instant.EPOCH, "\"1970-01-01T00:00:00Z\"") &&
        decodeError[Instant]("yesterday", "Invalid instant")
      },
      test("LocalDate") {
        roundTrip(LocalDate.of(2025, 1, 11), "2025-01-11") &&
        decodeError[LocalDate]("2025/01/11", "Invalid local date")
      },
      test("LocalDateTime") {
        roundTrip(LocalDateTime.of(2025, 1, 11, 10, 30), "\"2025-01-11T10:30\"")
      },
      test("LocalTime") {
        roundTrip(LocalTime.of(10, 30), "\"10:30\"") &&
        decodeError[LocalTime]("25:99", "Invalid local time")
      },
      test("Month") {
        roundTrip(Month.JANUARY, "JANUARY") &&
        decodeError[Month]("SMARCH", "Invalid month")
      },
      test("MonthDay") {
        roundTrip(MonthDay.of(1, 11), "\"--01-11\"")
      },
      test("OffsetDateTime") {
        roundTrip(
          OffsetDateTime.of(LocalDateTime.of(2025, 1, 11, 10, 30), ZoneOffset.ofHours(1)),
          "\"2025-01-11T10:30+01:00\""
        )
      },
      test("OffsetTime") {
        roundTrip(OffsetTime.of(LocalTime.of(10, 30), ZoneOffset.ofHours(1)), "\"10:30+01:00\"")
      },
      test("Period") {
        roundTrip(Period.ofDays(0), "P0D") &&
        roundTrip(Period.of(1, 2, 3), "P1Y2M3D")
      },
      test("Year") {
        roundTrip(Year.of(2025), "2025")
      },
      test("YearMonth") {
        roundTrip(YearMonth.of(2025, 1), "2025-01")
      },
      test("ZoneId") {
        roundTrip(ZoneId.of("UTC"), "UTC") &&
        decodeError[ZoneId]("Fake/Timezone", "Invalid zone id")
      },
      test("ZoneOffset") {
        roundTrip(ZoneOffset.ofHours(0), "Z") &&
        roundTrip(ZoneOffset.ofHours(1), "\"+01:00\"")
      },
      test("ZonedDateTime") {
        roundTrip(
          ZonedDateTime.of(LocalDateTime.of(2025, 1, 11, 10, 30), ZoneId.of("UTC")),
          "\"2025-01-11T10:30Z[UTC]\""
        )
      },
      test("Currency") {
        roundTrip(Currency.getInstance("USD"), "USD") &&
        decodeError[Currency]("FAKE", "Invalid currency")
      },
      test("UUID") {
        roundTrip(
          UUID.fromString("00000000-0000-0001-0000-000000000001"),
          "00000000-0000-0001-0000-000000000001"
        ) &&
        decodeError[UUID]("not-a-uuid", "Invalid UUID")
      }
    ),
    suite("string quoting")(
      test("empty string must be quoted") {
        encode[String]("", "\"\"")
      },
      test("strings with leading/trailing whitespace must be quoted") {
        encode[String](" hello", "\" hello\"") &&
        encode[String]("hello ", "\"hello \"") &&
        encode[String]("  ", "\"  \"")
      },
      test("boolean-like strings must be quoted") {
        encode[String]("true", "\"true\"") &&
        encode[String]("false", "\"false\"") &&
        encode[String]("null", "\"null\"")
      },
      test("numeric-like strings must be quoted") {
        encode[String]("42", "\"42\"") &&
        encode[String]("-3.14", "\"-3.14\"") &&
        encode[String]("1e6", "\"1e6\"") &&
        encode[String]("05", "\"05\"")
      },
      test("strings with colon must be quoted") {
        encode[String]("key:value", "\"key:value\"") &&
        encode[String]("10:30", "\"10:30\"")
      },
      test("strings with quotes/backslash must be quoted and escaped") {
        encode[String]("say \"hi\"", "\"say \\\"hi\\\"\"") &&
        encode[String]("path\\to", "\"path\\\\to\"")
      },
      test("strings with control characters must be quoted and escaped") {
        encode[String]("line1\nline2", "\"line1\\nline2\"") &&
        encode[String]("col1\tcol2", "\"col1\\tcol2\"") &&
        encode[String]("return\r", "\"return\\r\"")
      },
      test("strings with brackets/braces must be quoted") {
        encode[String]("[array]", "\"[array]\"") &&
        encode[String]("{object}", "\"{object}\"") &&
        encode[String]("arr[0]", "\"arr[0]\"")
      },
      test("strings starting with hyphen must be quoted") {
        encode[String]("-", "\"-\"") &&
        encode[String]("-flag", "\"-flag\"") &&
        encode[String]("--option", "\"--option\"")
      },
      test("strings with comma need not be quoted at top level") {
        roundTrip("a,b", "a,b") &&
        roundTrip("1,2,3", "1,2,3")
      },
      test("unicode and emoji strings need not be quoted") {
        roundTrip("Привіт", "Привіт") &&
        roundTrip("🎸🎧", "🎸🎧") &&
        roundTrip("日本語", "日本語")
      },
      test("strings with internal spaces need not be quoted") {
        roundTrip("hello world", "hello world") &&
        roundTrip("foo bar baz", "foo bar baz")
      }
    ),
    suite("records")(
      test("simple record") {
        val expected =
          """bl: true
            |b: 1
            |sh: 2
            |i: 3
            |l: 4
            |f: 5
            |d: 6
            |c: 7
            |s: VVV""".stripMargin
        roundTrip(
          Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
          expected
        )
      },
      test("nested record") {
        val expected =
          """r1_1:
            |  bl: true
            |  b: 1
            |  sh: 2
            |  i: 3
            |  l: 4
            |  f: 5
            |  d: 6
            |  c: 7
            |  s: VVV
            |r1_2:
            |  bl: false
            |  b: 2
            |  sh: 3
            |  i: 4
            |  l: 5
            |  f: 6
            |  d: 7
            |  c: 8
            |  s: WWW""".stripMargin
        roundTrip(
          Record2(
            Record1(true, 1: Byte, 2: Short, 3, 4L, 5.0f, 6.0, '7', "VVV"),
            Record1(false, 2: Byte, 3: Short, 4, 5L, 6.0f, 7.0, '8', "WWW")
          ),
          expected
        )
      },
      test("record with optional field") {
        val expectedSome =
          """hidden: null
            |optKey: VVV""".stripMargin
        roundTrip(Record4((), Some("VVV")), expectedSome) &&
        roundTrip(Record4((), None), "hidden: null")
      },
      test("record with optional field in middle") {
        val expectedSome =
          """first: 1
            |optMiddle: hello
            |last: 2""".stripMargin
        val expectedNone =
          """first: 1
            |last: 2""".stripMargin
        roundTrip(RecordWithOptionalMiddle(1, Some("hello"), 2), expectedSome) &&
        roundTrip(RecordWithOptionalMiddle(1, None, 2), expectedNone)
      }
    ),
    suite("sequences")(
      test("primitive values") {
        val expected = "xs[3]: 1,2,3"
        roundTrip(IntList(List(1, 2, 3)), expected)
      },
      test("empty sequence") {
        roundTrip(IntList(Nil), "")
      },
      test("string values") {
        val expected = "xs[2]: hello,world"
        roundTrip(StringList(List("hello", "world")), expected)
      }
    ),
    suite("variants")(
      test("case object enumeration") {
        roundTrip[TrafficLight](TrafficLight.Green, "Green") &&
        roundTrip[TrafficLight](TrafficLight.Yellow, "Yellow") &&
        roundTrip[TrafficLight](TrafficLight.Red, "Red")
      },
      test("ADT with discriminator") {
        val catExpected =
          """Cat:
            |  name: Whiskers
            |  lives: 9""".stripMargin
        val dogExpected =
          """Dog:
            |  name: Buddy
            |  breed: Labrador""".stripMargin
        roundTrip[Pet](Pet.Cat("Whiskers", 9), catExpected) &&
        roundTrip[Pet](Pet.Dog("Buddy", "Labrador"), dogExpected)
      },
      test("option") {
        roundTrip(Option(42), "42") &&
        roundTrip[Option[Int]](None, "null")
      },
      test("either") {
        val rightExpected =
          """Right:
            |  value: 42""".stripMargin
        val leftExpected =
          """Left:
            |  value: error""".stripMargin
        roundTrip[Either[String, Int]](Right(42), rightExpected) &&
        roundTrip[Either[String, Int]](Left("error"), leftExpected)
      }
    ),
    suite("maps")(
      test("string key map") {
        val expected =
          """a: 1
            |b: 2""".stripMargin
        roundTrip(StringIntMap(Map("a" -> 1, "b" -> 2)), expected)
      },
      test("empty map") {
        roundTrip(StringIntMap(Map.empty), "")
      },
      test("map field alongside other fields") {
        val expected =
          """name: test
            |metadata:
            |  role: admin
            |  status: active
            |count: 42""".stripMargin
        roundTrip(RecordWithMapField("test", Map("role" -> "admin", "status" -> "active"), 42), expected)
      },
      test("map field alongside other fields with empty map") {
        val expected =
          """name: test
            |count: 42""".stripMargin
        roundTrip(RecordWithMapField("test", Map.empty, 42), expected)
      }
    ),
    suite("property-based")(
      test("Int roundtrip") {
        check(Gen.int) { x =>
          roundTrip(x, x.toString)
        }
      },
      test("Long roundtrip") {
        check(Gen.long) { x =>
          roundTrip(x, x.toString)
        }
      },
      test("Boolean roundtrip") {
        check(Gen.boolean) { x =>
          roundTrip(x, x.toString)
        }
      }
    ),
    suite("edge cases")(
      test("deeply nested records (2 levels)") {
        val expected =
          """r1_1:
            |  bl: true
            |  b: 1
            |  sh: 2
            |  i: 3
            |  l: 4
            |  f: 5
            |  d: 6
            |  c: 7
            |  s: inner
            |r1_2:
            |  bl: false
            |  b: 2
            |  sh: 3
            |  i: 4
            |  l: 5
            |  f: 6
            |  d: 7
            |  c: 8
            |  s: outer""".stripMargin
        roundTrip(
          Record2(
            Record1(true, 1, 2, 3, 4L, 5.0f, 6.0, '7', "inner"),
            Record1(false, 2, 3, 4, 5L, 6.0f, 7.0, '8', "outer")
          ),
          expected
        )
      },
      test("record with all optional fields - all None") {
        roundTrip(AllOptional(None, None, None), "")
      },
      test("record with all optional fields - Some in last position") {
        roundTrip(AllOptional(None, None, Some(true)), "c: true")
      },
      test("list with many elements") {
        val nums     = (1 to 10).toList
        val expected = s"xs[10]: ${nums.mkString(",")}"
        roundTrip(IntList(nums), expected)
      },
      test("list with single element") {
        roundTrip(IntList(List(42)), "xs[1]: 42")
      },
      test("map with simple string values") {
        val expected =
          """a: hello
            |b: world""".stripMargin
        roundTrip(StringStringMap(Map("a" -> "hello", "b" -> "world")), expected)
      },
      test("string with all escape sequences") {
        val input = "tab:\there\nnewline\rcarriage\\backslash\"quote"
        encode[String](input, "\"tab:\\there\\nnewline\\rcarriage\\\\backslash\\\"quote\"")
      },
      test("very long string") {
        val longStr = "a" * 1000
        roundTrip(longStr, longStr)
      },
      test("integer boundary values") {
        roundTrip(Int.MinValue, "-2147483648") &&
        roundTrip(Int.MaxValue, "2147483647") &&
        roundTrip(0, "0") &&
        roundTrip(-1, "-1") &&
        roundTrip(1, "1")
      },
      test("long boundary values") {
        roundTrip(Long.MinValue, "-9223372036854775808") &&
        roundTrip(Long.MaxValue, "9223372036854775807")
      },
      test("special unicode characters") {
        roundTrip("αβγδ", "αβγδ") &&   // Greek
        roundTrip("中文字符", "中文字符") &&   // Chinese
        roundTrip("עברית", "עברית") && // Hebrew (RTL)
        roundTrip("🚀🎉💻", "🚀🎉💻")  // Emoji
      },
      test("unicode strings that require quoting") {
        roundTrip("日本:語", "\"日本:語\"") &&           // colon forces quoting
        roundTrip("中文,字符", "中文,字符") &&             // comma doesn't force quoting at top level
        roundTrip("Привіт світ", "Привіт світ") && // spaces don't force quoting
        roundTrip(" 日本語", "\" 日本語\"") &&           // leading space forces quoting
        roundTrip("🎸[test]", "\"🎸[test]\"")      // brackets force quoting
      },
      test("strings with backslashes") {
        roundTrip("test\\", "\"test\\\\\"") &&                   // ends with backslash
        roundTrip("test\\\\", "\"test\\\\\\\\\"") &&             // ends with two backslashes
        roundTrip("path\\to\\file", "\"path\\\\to\\\\file\"") && // backslashes in middle
        roundTrip("\\start", "\"\\\\start\"") &&                 // starts with backslash
        roundTrip("quote\"here", "\"quote\\\"here\"")            // embedded quote
      }
    ),
    suite("format features")(
      test("inline array format for primitives") {
        val result = encode(IntList(List(1, 2, 3)), "xs[3]: 1,2,3")
        result
      },
      test("record field ordering preserved") {
        val expected =
          """bl: true
            |b: 1
            |sh: 2
            |i: 3
            |l: 4
            |f: 5
            |d: 6
            |c: 7
            |s: test""".stripMargin
        encode(Record1(true, 1, 2, 3, 4L, 5.0f, 6.0, '7', "test"), expected)
      },
      test("variant uses key discriminator") {
        encode[Pet](
          Pet.Cat("Whiskers", 9),
          """Cat:
  name: Whiskers
  lives: 9"""
        ) &&
        encode[Pet](
          Pet.Dog("Buddy", "Lab"),
          """Dog:
  name: Buddy
  breed: Lab"""
        )
      },
      test("null representation for Unit") {
        roundTrip((), "null")
      },
      test("null representation for None") {
        roundTrip[Option[Int]](None, "null")
      },
      test("simple enum uses name only") {
        encode[TrafficLight](TrafficLight.Red, "Red") &&
        encode[TrafficLight](TrafficLight.Green, "Green")
      }
    ),
    suite("extra fields")(
      test("extra fields are ignored by default") {
        val toon = """name: Alice
                     |age: 25
                     |unknownField: ignored""".stripMargin
        decode(toon, SimplePerson("Alice", 25))
      },
      test("extra fields cause error when rejectExtraFields is true") {
        val deriver = ToonBinaryCodecDeriver.withRejectExtraFields(true)
        val codec   = deriveCodec(SimplePerson.schema, deriver)
        val toon    = """name: Alice
                        |age: 25
                        |unknownField: ignored""".stripMargin
        decodeError(toon, "Unexpected field: unknownField", codec)
      },
      test("known fields work with rejectExtraFields true") {
        val deriver = ToonBinaryCodecDeriver.withRejectExtraFields(true)
        val codec   = deriveCodec(SimplePerson.schema, deriver)
        val toon    = """name: Bob
                        |age: 30""".stripMargin
        decode(toon, SimplePerson("Bob", 30), codec)
      }
    ),
    suite("discriminators")(
      test("Key discriminator encodes variant with key wrapper") {
        val expected = """Dog:
                         |  name: Buddy
                         |  breed: Labrador""".stripMargin
        encode[Pet](Pet.Dog("Buddy", "Labrador"), expected)
      },
      test("Field discriminator encodes type field inline") {
        val deriver  = ToonBinaryCodecDeriver.withDiscriminatorKind(DiscriminatorKind.Field("type"))
        val codec    = deriveCodec(Pet.schema, deriver)
        val expected = """type: Dog
                         |name: Buddy
                         |breed: Labrador""".stripMargin
        encode(Pet.Dog("Buddy", "Labrador"), expected, codec)
      },
      test("Field discriminator roundtrips correctly") {
        val deriver = ToonBinaryCodecDeriver.withDiscriminatorKind(DiscriminatorKind.Field("type"))
        val codec   = deriveCodec(Pet.schema, deriver)
        val toon    = """type: Cat
                        |name: Whiskers
                        |lives: 9""".stripMargin
        decode(toon, Pet.Cat("Whiskers", 9), codec)
      },
      test("None discriminator encodes without wrapper") {
        val deriver  = ToonBinaryCodecDeriver.withDiscriminatorKind(DiscriminatorKind.None)
        val codec    = deriveCodec(Pet.schema, deriver)
        val expected = """name: Buddy
                         |breed: Labrador""".stripMargin
        encode(Pet.Dog("Buddy", "Labrador"), expected, codec)
      },
      test("None discriminator decode works with try-each-case") {
        val deriver = ToonBinaryCodecDeriver.withDiscriminatorKind(DiscriminatorKind.None)
        val codec   = deriveCodec(Pet.schema, deriver)
        val dogToon = """name: Buddy
                        |breed: Labrador""".stripMargin
        decode(dogToon, Pet.Dog("Buddy", "Labrador"), codec)
      },
      test("None discriminator roundtrips correctly") {
        val deriver = ToonBinaryCodecDeriver.withDiscriminatorKind(DiscriminatorKind.None)
        val codec   = deriveCodec(Pet.schema, deriver)
        val dog     = Pet.Dog("Buddy", "Labrador")
        val cat     = Pet.Cat("Whiskers", 9)
        val dogToon = """name: Buddy
                        |breed: Labrador""".stripMargin
        val catToon = """name: Whiskers
                        |lives: 9""".stripMargin
        encode(dog, dogToon, codec) &&
        encode(cat, catToon, codec) &&
        decode(dogToon, dog, codec) &&
        decode(catToon, cat, codec)
      }
    ),
    suite("integration")(
      test("user profile with address") {
        val expected =
          """name: John Doe
            |age: 30
            |email: john@example.com
            |address:
            |  street: 123 Main St
            |  city: Anytown
            |  zip: "12345"""".stripMargin
        roundTrip(
          UserProfile(
            "John Doe",
            30,
            "john@example.com",
            Address("123 Main St", "Anytown", "12345")
          ),
          expected
        )
      },
      test("order with line items") {
        val expected =
          """orderId: ORD-001
            |items[2]: Widget,Gadget
            |total: 99.99""".stripMargin
        roundTrip(
          Order("ORD-001", List("Widget", "Gadget"), BigDecimal("99.99")),
          expected
        )
      },
      test("config with nested options") {
        val expected =
          """host: localhost
            |port: 8080
            |ssl: true""".stripMargin
        roundTrip(
          ServerConfig("localhost", 8080, Some(true)),
          expected
        )
      }
    ),
    suite("array formats")(
      test("ArrayFormat.Tabular encodes record arrays in tabular format") {
        val deriver = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.Tabular)
        val codec   = deriveCodec(PersonList.schema, deriver)
        val persons = PersonList(
          List(
            SimplePerson("Alice", 25),
            SimplePerson("Bob", 30)
          )
        )
        val expected = """people[2]{name,age}:
                         |  Alice,25
                         |  Bob,30""".stripMargin
        encode(persons, expected, codec)
      },
      test("ArrayFormat.Tabular with custom delimiter") {
        val deriver = ToonBinaryCodecDeriver
          .withArrayFormat(ArrayFormat.Tabular)
          .withDelimiter(Delimiter.Pipe)
        val codec   = deriveCodec(PersonList.schema, deriver)
        val persons = PersonList(
          List(
            SimplePerson("Alice", 25),
            SimplePerson("Bob", 30)
          )
        )
        val expected = """people[2|]{name|age}:
                         |  Alice|25
                         |  Bob|30""".stripMargin
        encode(persons, expected, codec)
      },
      test("ArrayFormat.List forces list format even for primitives") {
        val deriver  = ToonBinaryCodecDeriver.withArrayFormat(ArrayFormat.List)
        val codec    = deriveCodec(IntList.schema, deriver)
        val list     = IntList(List(1, 2, 3))
        val expected = """xs[3]:
                         |  - 1
                         |  - 2
                         |  - 3""".stripMargin
        encode(list, expected, codec)
      },
      test("withDelimiter affects inline arrays") {
        val deriver  = ToonBinaryCodecDeriver.withDelimiter(Delimiter.Pipe)
        val codec    = deriveCodec(IntList.schema, deriver)
        val list     = IntList(List(1, 2, 3))
        val expected = """xs[3|]: 1|2|3"""
        encode(list, expected, codec)
      }
    ),
    suite("DynamicValue")(
      test("DynamicValue Map encodes correctly") {
        val map = DynamicValue.Map(
          Vector(
            (
              DynamicValue.Primitive(PrimitiveValue.String("key1")),
              DynamicValue.Primitive(PrimitiveValue.String("value1"))
            ),
            (DynamicValue.Primitive(PrimitiveValue.Int(42)), DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
          )
        )
        val codec   = ToonBinaryCodec.dynamicValueCodec
        val encoded = new String(codec.encode(map), java.nio.charset.StandardCharsets.UTF_8).trim
        assertTrue(encoded.contains("key1: value1")) &&
        assertTrue(encoded.contains("42: true"))
      },
      test("DynamicValue - default config") {
        check(ToonDynamicValueGen.genDynamicValue) { value =>
          val codec      = ToonBinaryCodec.dynamicValueCodec
          val encoded    = codec.encodeToString(value, WriterConfig)
          val decoded    = codec.decode(encoded, ReaderConfig)
          val normalized = ToonDynamicValueGen.normalize(value)
          assertTrue(decoded == Right(normalized))
        }
      },
      test("DynamicValue - tab delimiter") {
        check(ToonDynamicValueGen.genDynamicValue) { value =>
          val codec      = ToonBinaryCodec.dynamicValueCodec
          val writerCfg  = WriterConfig.withDelimiter(Delimiter.Tab)
          val readerCfg  = ReaderConfig.withDelimiter(Delimiter.Tab)
          val encoded    = codec.encodeToString(value, writerCfg)
          val decoded    = codec.decode(encoded, readerCfg)
          val normalized = ToonDynamicValueGen.normalize(value)
          assertTrue(decoded == Right(normalized))
        }
      },
      test("DynamicValue - pipe delimiter") {
        check(ToonDynamicValueGen.genDynamicValue) { value =>
          val codec      = ToonBinaryCodec.dynamicValueCodec
          val writerCfg  = WriterConfig.withDelimiter(Delimiter.Pipe)
          val readerCfg  = ReaderConfig.withDelimiter(Delimiter.Pipe)
          val encoded    = codec.encodeToString(value, writerCfg)
          val decoded    = codec.decode(encoded, readerCfg)
          val normalized = ToonDynamicValueGen.normalize(value)
          assertTrue(decoded == Right(normalized))
        }
      },
      test("DynamicValue - key folding") {
        check(ToonDynamicValueGen.genDynamicValue) { value =>
          val codec      = ToonBinaryCodec.dynamicValueCodec
          val writerCfg  = WriterConfig.withKeyFolding(KeyFolding.Safe)
          val readerCfg  = ReaderConfig.withExpandPaths(PathExpansion.Safe)
          val encoded    = codec.encodeToString(value, writerCfg)
          val decoded    = codec.decode(encoded, readerCfg)
          val normalized = ToonDynamicValueGen.normalize(value)
          assertTrue(decoded == Right(normalized))
        }
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

  object Record1 {
    implicit val schema: Schema[Record1] = Schema.derived
  }

  case class Record2(r1_1: Record1, r1_2: Record1)

  object Record2 {
    implicit val schema: Schema[Record2] = Schema.derived
  }

  case class Record4(hidden: Unit, optKey: Option[String])

  object Record4 {
    implicit val schema: Schema[Record4] = Schema.derived
  }

  case class IntList(xs: List[Int])

  object IntList {
    implicit val schema: Schema[IntList] = Schema.derived
  }

  case class StringList(xs: List[String])

  object StringList {
    implicit val schema: Schema[StringList] = Schema.derived
  }

  case class StringIntMap(m: Map[String, Int])

  object StringIntMap {
    implicit val schema: Schema[StringIntMap] = Schema.derived
  }

  case class SimplePerson(name: String, age: Int)

  object SimplePerson {
    implicit val schema: Schema[SimplePerson] = Schema.derived
  }

  sealed trait TrafficLight

  object TrafficLight {
    implicit val schema: Schema[TrafficLight] = Schema.derived

    case object Red    extends TrafficLight
    case object Yellow extends TrafficLight
    case object Green  extends TrafficLight
  }

  sealed trait Pet

  object Pet {
    implicit val schema: Schema[Pet] = Schema.derived

    case class Cat(name: String, lives: Int)    extends Pet
    case class Dog(name: String, breed: String) extends Pet
  }

  implicit val eitherSchema: Schema[Either[String, Int]] = Schema.derived

  case class NestedRecord(inner: Option[NestedRecord], value: String)

  object NestedRecord {
    implicit val schema: Schema[NestedRecord] = Schema.derived
  }

  case class AllOptional(a: Option[Int], b: Option[String], c: Option[Boolean])

  object AllOptional {
    implicit val schema: Schema[AllOptional] = Schema.derived
  }

  case class StringStringMap(m: Map[String, String])

  object StringStringMap {
    implicit val schema: Schema[StringStringMap] = Schema.derived
  }

  case class Address(street: String, city: String, zip: String)

  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  case class UserProfile(name: String, age: Int, email: String, address: Address)

  object UserProfile {
    implicit val schema: Schema[UserProfile] = Schema.derived
  }

  case class Order(orderId: String, items: List[String], total: BigDecimal)

  object Order {
    implicit val schema: Schema[Order] = Schema.derived
  }

  case class ServerConfig(host: String, port: Int, ssl: Option[Boolean])

  object ServerConfig {
    implicit val schema: Schema[ServerConfig] = Schema.derived
  }

  case class PersonList(people: List[SimplePerson])

  case class RecordWithOptionalMiddle(first: Int, optMiddle: Option[String], last: Int)

  object RecordWithOptionalMiddle {
    implicit val schema: Schema[RecordWithOptionalMiddle] = Schema.derived
  }

  object PersonList {
    implicit val schema: Schema[PersonList] = Schema.derived
  }

  case class RecordWithMapField(name: String, metadata: Map[String, String], count: Int)

  object RecordWithMapField {
    implicit val schema: Schema[RecordWithMapField] = Schema.derived
  }
}
