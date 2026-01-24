package zio.blocks.schema

import zio.test._
import DynamicValueGen._
import zio.test.Assertion.{equalTo, not}

object DynamicValueSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("DynamicValueSpec")(
    dynamicValueToStringSuite,
    suite("DynamicValue equals and hashCode properties with Generators")(
      test("symmetry") {
        check(genDynamicValue, genDynamicValue) { (value1, value2) =>
          assertTrue((value1 == value2) == (value2 == value1))
        }
      },
      test("transitivity") {
        check(genDynamicValue, genDynamicValue, genDynamicValue) { (value1, value2, value3) =>
          // If value1 equals value2 and value2 equals value3 then value1 should equal value3.
          assertTrue(!(value1 == value2 && value2 == value3) || (value1 == value3))
        }
      },
      test("consistency of hashCode for equal values") {
        check(genDynamicValue, genDynamicValue) { (value1, value2) =>
          // For equal values the hashCodes must be equal
          assertTrue(!(value1 == value2) || (value1.hashCode == value2.hashCode))
        }
      },
      test("inequality for different types or structures") {
        check(genDynamicValue, genDynamicValue) { (value1, value2) =>
          // verifies that when two values are not equal they indeed do not compare equal
          assertTrue((value1 != value2) || (value1 == value2))
        }
      },
      test("inequality for other non dynamic value types") {
        check(genDynamicValue, Gen.string) { (dynamicValue, str) =>
          assert(dynamicValue: Any)(not(equalTo(str)))
        }
      },
      test("nested structure equality and hashCode consistency") {
        val nestedGen = for {
          innerValue <- genRecord
          outerValue <- genRecord
        } yield DynamicValue.Record(Vector("inner" -> innerValue, "outer" -> outerValue))

        check(nestedGen, nestedGen) { (nested1, nested2) =>
          assertTrue((nested1 == nested2) == (nested1.hashCode == nested2.hashCode))
        }
      },
      test("structure equality and hashCode consistency for variants with the same case names") {
        check(genDynamicValue, genDynamicValue) { (value1, value2) =>
          val variant1 = DynamicValue.Variant("case1", value1)
          val variant2 = DynamicValue.Variant("case1", value2)
          assertTrue(!(variant1 == variant2) || (variant1.hashCode == variant2.hashCode))
        }
      },
      test("structure equality and hashCode consistency for maps with the same keys") {
        check(genDynamicValue, genDynamicValue, genDynamicValue) { (key, value1, value2) =>
          val map1 = DynamicValue.Map(Vector((key, value1), (key, value2)))
          val map2 = DynamicValue.Map(Vector((key, value1), (key, value2)))
          assertTrue(!(map1 == map2) || (map1.hashCode == map2.hashCode))
        }
      }
    ),
    suite("DynamicValue compare and equals properties with Generators")(
      test("symmetry") {
        check(genDynamicValue, genDynamicValue) { (value1, value2) =>
          assertTrue(value1.compare(value2) == -value2.compare(value1)) &&
          assertTrue((value1 > value2) == (value2 < value1)) &&
          assertTrue((value1 >= value2) == (value2 <= value1))
        }
      },
      test("transitivity") {
        check(genDynamicValue, genDynamicValue, genDynamicValue) { (value1, value2, value3) =>
          assertTrue(!(value1 > value2 && value2 > value3) || (value1 > value3)) &&
          assertTrue(!(value1 >= value2 && value2 >= value3) || (value1 >= value3)) &&
          assertTrue(!(value1 < value2 && value2 < value3) || (value1 < value3)) &&
          assertTrue(!(value1 <= value2 && value2 <= value3) || (value1 <= value3))
        }
      },
      test("consistency of compare for equal values") {
        check(genDynamicValue, genDynamicValue) { (value1, value2) =>
          assertTrue((value1 == value2) == (value1.compare(value2) == 0))
        }
      },
      test("nested structure equality and compare consistency") {
        val nestedGen = for {
          innerValue <- genRecord
          outerValue <- genRecord
        } yield DynamicValue.Record(Vector("inner" -> innerValue, "outer" -> outerValue))

        check(nestedGen, nestedGen) { (nested1, nested2) =>
          assertTrue((nested1 == nested2) == (nested1.compare(nested2) == 0))
        }
      },
      test("structure equality and compare consistency for variants with the same case names") {
        check(genDynamicValue, genDynamicValue) { (value1, value2) =>
          val variant1 = DynamicValue.Variant("case1", value1)
          val variant2 = DynamicValue.Variant("case1", value2)
          assertTrue((variant1 == variant2) == (variant1.compare(variant2) == 0))
        }
      },
      test("structure equality and compare consistency for maps with the same keys") {
        check(genDynamicValue, genDynamicValue, genDynamicValue) { (key, value1, value2) =>
          val map1 = DynamicValue.Map(Vector((key, value1), (key, value2)))
          val map2 = DynamicValue.Map(Vector((key, value2), (key, value1)))
          assertTrue((map1 == map2) == (map1.compare(map2) == 0))
        }
      }
    )
  )

  val dynamicValueToStringSuite: Spec[Any, Nothing] = suite("DynamicValue.toString (EJSON format)")(
    test("renders primitive Int") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Int(42))
      assertTrue(dv.toString == "42")
    },
    test("renders primitive negative Int") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Int(-123))
      assertTrue(dv.toString == "-123")
    },
    test("renders primitive Long") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Long(9876543210L))
      assertTrue(dv.toString == "9876543210")
    },
    test("renders primitive Short") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Short(1234.toShort))
      assertTrue(dv.toString == "1234")
    },
    test("renders primitive Byte") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Byte(127.toByte))
      assertTrue(dv.toString == "127")
    },
    test("renders primitive Float") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Float(3.5f))
      assertTrue(dv.toString == "3.5")
    },
    test("renders primitive Double") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Double(2.71828))
      assertTrue(dv.toString == "2.71828")
    },
    test("renders primitive Char") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Char('X'))
      assertTrue(dv.toString == "'X'")
    },
    test("renders primitive String with quotes") {
      val dv = DynamicValue.Primitive(PrimitiveValue.String("hello"))
      assertTrue(dv.toString == "\"hello\"")
    },
    test("renders empty String") {
      val dv = DynamicValue.Primitive(PrimitiveValue.String(""))
      assertTrue(dv.toString == "\"\"")
    },
    test("renders primitive Boolean true") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Boolean(true))
      assertTrue(dv.toString == "true")
    },
    test("renders primitive Boolean false") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Boolean(false))
      assertTrue(dv.toString == "false")
    },
    test("renders Unit as ()") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Unit)
      assertTrue(dv.toString == "()")
    },
    test("renders BigInt") {
      val dv = DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt("123456789012345678901234567890")))
      assertTrue(dv.toString == "123456789012345678901234567890")
    },
    test("renders BigDecimal") {
      val dv = DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("123.456789")))
      assertTrue(dv.toString == "123.456789")
    },
    test("renders Record with unquoted keys") {
      val dv = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
          "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(42))
        )
      )
      assertTrue(dv.toString == "{ name: \"John\", age: 42 }")
    },
    test("renders empty Record") {
      val dv = DynamicValue.Record(Vector.empty)
      assertTrue(dv.toString == "{  }")
    },
    test("renders single-field Record") {
      val dv = DynamicValue.Record(Vector("only" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
      assertTrue(dv.toString == "{ only: 1 }")
    },
    test("renders Sequence") {
      val dv = DynamicValue.Sequence(
        Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        )
      )
      assertTrue(dv.toString == "[1, 2, 3]")
    },
    test("renders empty Sequence") {
      val dv = DynamicValue.Sequence(Vector.empty)
      assertTrue(dv.toString == "[]")
    },
    test("renders single-element Sequence") {
      val dv = DynamicValue.Sequence(Vector(DynamicValue.Primitive(PrimitiveValue.Int(42))))
      assertTrue(dv.toString == "[42]")
    },
    test("renders Sequence of Strings") {
      val dv = DynamicValue.Sequence(
        Vector(
          DynamicValue.Primitive(PrimitiveValue.String("a")),
          DynamicValue.Primitive(PrimitiveValue.String("b"))
        )
      )
      assertTrue(dv.toString == "[\"a\", \"b\"]")
    },
    test("renders Map with string keys (quoted)") {
      val dv = DynamicValue.Map(
        Vector(
          DynamicValue.Primitive(PrimitiveValue.String("key1")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.String("key2")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
      )
      assertTrue(dv.toString == "{ \"key1\": 1, \"key2\": 2 }")
    },
    test("renders Map with non-string keys (unquoted)") {
      val dv = DynamicValue.Map(
        Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)) -> DynamicValue.Primitive(PrimitiveValue.String("one")),
          DynamicValue.Primitive(PrimitiveValue.Int(2)) -> DynamicValue.Primitive(PrimitiveValue.String("two"))
        )
      )
      assertTrue(dv.toString == "{ 1: \"one\", 2: \"two\" }")
    },
    test("renders empty Map") {
      val dv = DynamicValue.Map(Vector.empty)
      assertTrue(dv.toString == "{  }")
    },
    test("renders Map with boolean keys") {
      val dv = DynamicValue.Map(
        Vector(
          DynamicValue.Primitive(PrimitiveValue.Boolean(true))  -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Boolean(false)) -> DynamicValue.Primitive(PrimitiveValue.Int(0))
        )
      )
      assertTrue(dv.toString == "{ true: 1, false: 0 }")
    },
    test("renders Variant with tag annotation") {
      val dv = DynamicValue.Variant(
        "Some",
        DynamicValue.Record(Vector("value" -> DynamicValue.Primitive(PrimitiveValue.Int(42))))
      )
      assertTrue(dv.toString == "{ value: 42 } @ {tag: \"Some\"}")
    },
    test("renders Variant with empty Record") {
      val dv = DynamicValue.Variant("None", DynamicValue.Record(Vector.empty))
      assertTrue(dv.toString == "{  } @ {tag: \"None\"}")
    },
    test("renders Variant with primitive value") {
      val dv = DynamicValue.Variant("Value", DynamicValue.Primitive(PrimitiveValue.Int(100)))
      assertTrue(dv.toString == "100 @ {tag: \"Value\"}")
    },
    test("escapes special characters in strings") {
      val dv = DynamicValue.Primitive(PrimitiveValue.String("hello\nworld\t\"quoted\""))
      assertTrue(dv.toString == "\"hello\\nworld\\t\\\"quoted\\\"\"")
    },
    test("escapes backslash in strings") {
      val dv = DynamicValue.Primitive(PrimitiveValue.String("path\\to\\file"))
      assertTrue(dv.toString == "\"path\\\\to\\\\file\"")
    },
    test("escapes carriage return in strings") {
      val dv = DynamicValue.Primitive(PrimitiveValue.String("line1\rline2"))
      assertTrue(dv.toString == "\"line1\\rline2\"")
    },
    test("escapes form feed and backspace in strings") {
      val dv = DynamicValue.Primitive(PrimitiveValue.String("a\fb\bc"))
      assertTrue(dv.toString == "\"a\\fb\\bc\"")
    },
    test("renders nested structures") {
      val inner = DynamicValue.Record(
        Vector(
          "x" -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          "y" -> DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
      )
      val outer = DynamicValue.Record(
        Vector(
          "point" -> inner,
          "label" -> DynamicValue.Primitive(PrimitiveValue.String("origin"))
        )
      )
      assertTrue(outer.toString == "{ point: { x: 1, y: 2 }, label: \"origin\" }")
    },
    test("renders deeply nested sequences") {
      val dv = DynamicValue.Sequence(
        Vector(
          DynamicValue.Sequence(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.Int(2))
            )
          ),
          DynamicValue.Sequence(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.Int(3)),
              DynamicValue.Primitive(PrimitiveValue.Int(4))
            )
          )
        )
      )
      assertTrue(dv.toString == "[[1, 2], [3, 4]]")
    },
    test("renders Map with Record values") {
      val dv = DynamicValue.Map(
        Vector(
          DynamicValue.Primitive(PrimitiveValue.String("person")) ->
            DynamicValue.Record(Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))))
        )
      )
      assertTrue(dv.toString == "{ \"person\": { name: \"John\" } }")
    },
    test("renders Sequence with Variant elements") {
      val dv = DynamicValue.Sequence(
        Vector(
          DynamicValue.Variant("Left", DynamicValue.Primitive(PrimitiveValue.Int(1))),
          DynamicValue.Variant("Right", DynamicValue.Primitive(PrimitiveValue.String("ok")))
        )
      )
      assertTrue(dv.toString == "[1 @ {tag: \"Left\"}, \"ok\" @ {tag: \"Right\"}]")
    },
    test("renders Instant with type metadata") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Instant(java.time.Instant.parse("2024-01-15T10:30:00Z")))
      assertTrue(dv.toString == "\"2024-01-15T10:30:00Z\" @ {type: \"instant\"}")
    },
    test("renders Duration with type metadata") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Duration(java.time.Duration.ofHours(2)))
      assertTrue(dv.toString == "\"PT2H\" @ {type: \"duration\"}")
    },
    test("renders LocalDate with type metadata") {
      val dv = DynamicValue.Primitive(PrimitiveValue.LocalDate(java.time.LocalDate.of(2024, 1, 15)))
      assertTrue(dv.toString == "\"2024-01-15\" @ {type: \"localDate\"}")
    },
    test("renders LocalTime with type metadata") {
      val dv = DynamicValue.Primitive(PrimitiveValue.LocalTime(java.time.LocalTime.of(10, 30, 0)))
      assertTrue(dv.toString == "\"10:30\" @ {type: \"localTime\"}")
    },
    test("renders LocalDateTime with type metadata") {
      val dv =
        DynamicValue.Primitive(PrimitiveValue.LocalDateTime(java.time.LocalDateTime.of(2024, 1, 15, 10, 30, 0)))
      assertTrue(dv.toString == "\"2024-01-15T10:30\" @ {type: \"localDateTime\"}")
    },
    test("renders OffsetDateTime with type metadata") {
      val dv = DynamicValue.Primitive(
        PrimitiveValue.OffsetDateTime(
          java.time.OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, java.time.ZoneOffset.UTC)
        )
      )
      assertTrue(dv.toString == "\"2024-01-15T10:30Z\" @ {type: \"offsetDateTime\"}")
    },
    test("renders ZonedDateTime with type metadata") {
      val dv = DynamicValue.Primitive(
        PrimitiveValue.ZonedDateTime(
          java.time.ZonedDateTime.of(2024, 1, 15, 10, 30, 0, 0, java.time.ZoneId.of("UTC"))
        )
      )
      assertTrue(dv.toString == "\"2024-01-15T10:30Z[UTC]\" @ {type: \"zonedDateTime\"}")
    },
    test("renders DayOfWeek with type metadata") {
      val dv = DynamicValue.Primitive(PrimitiveValue.DayOfWeek(java.time.DayOfWeek.MONDAY))
      assertTrue(dv.toString == "\"MONDAY\" @ {type: \"dayOfWeek\"}")
    },
    test("renders Month with type metadata") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Month(java.time.Month.JANUARY))
      assertTrue(dv.toString == "\"JANUARY\" @ {type: \"month\"}")
    },
    test("renders Year with type metadata") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Year(java.time.Year.of(2024)))
      assertTrue(dv.toString == "\"2024\" @ {type: \"year\"}")
    },
    test("renders YearMonth with type metadata") {
      val dv = DynamicValue.Primitive(PrimitiveValue.YearMonth(java.time.YearMonth.of(2024, 1)))
      assertTrue(dv.toString == "\"2024-01\" @ {type: \"yearMonth\"}")
    },
    test("renders MonthDay with type metadata") {
      val dv = DynamicValue.Primitive(PrimitiveValue.MonthDay(java.time.MonthDay.of(1, 15)))
      assertTrue(dv.toString == "\"--01-15\" @ {type: \"monthDay\"}")
    },
    test("renders Period with type metadata") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Period(java.time.Period.of(1, 2, 3)))
      assertTrue(dv.toString == "\"P1Y2M3D\" @ {type: \"period\"}")
    },
    test("renders ZoneId with type metadata") {
      val dv = DynamicValue.Primitive(PrimitiveValue.ZoneId(java.time.ZoneId.of("America/New_York")))
      assertTrue(dv.toString == "\"America/New_York\" @ {type: \"zoneId\"}")
    },
    test("renders ZoneOffset with type metadata") {
      val dv = DynamicValue.Primitive(PrimitiveValue.ZoneOffset(java.time.ZoneOffset.ofHours(-5)))
      assertTrue(dv.toString == "\"-05:00\" @ {type: \"zoneOffset\"}")
    },
    test("renders OffsetTime with type metadata") {
      val dv = DynamicValue.Primitive(
        PrimitiveValue.OffsetTime(java.time.OffsetTime.of(10, 30, 0, 0, java.time.ZoneOffset.UTC))
      )
      assertTrue(dv.toString == "\"10:30Z\" @ {type: \"offsetTime\"}")
    },
    test("renders UUID with type metadata") {
      val uuid = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
      val dv   = DynamicValue.Primitive(PrimitiveValue.UUID(uuid))
      assertTrue(dv.toString == "\"550e8400-e29b-41d4-a716-446655440000\" @ {type: \"uuid\"}")
    },
    test("renders Currency with type metadata") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Currency(java.util.Currency.getInstance("USD")))
      assertTrue(dv.toString == "\"USD\" @ {type: \"currency\"}")
    }
  )
}
