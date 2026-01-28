# SchemaJVM Coverage Tests

This document contains complete, ready-to-implement tests for increasing schemaJVM branch coverage. Each test is fully specified with exact file locations, imports, and test code.

**Goal:** Increase branch coverage from ~80% to 83%+ (~213 additional branches needed)

**Workflow for adding each test group:**
1. Add tests to the specified file
2. Run: `sbt "project schemaJVM; coverage; test; coverageReport"`
3. Check report at: `schema/jvm/target/scala-3.3.7/scoverage-report/index.html`

---

## Test Group 1: Into Narrowing Conversion Failures

**File:** `schema/shared/src/test/scala/zio/blocks/schema/IntoSpec.scala`

**Location:** Add as a new suite inside the main `IntoSpec` spec, after the existing suites.

**Target code:** `schema/shared/src/main/scala/zio/blocks/schema/Into.scala` lines 43-94 (16 branches)

**Imports needed:** Already present in IntoSpec.scala

```scala
suite("narrowing failure branches")(
  test("shortToByte fails for values above Byte.MaxValue") {
    val result = Into[Short, Byte].into((Byte.MaxValue + 1).toShort)
    assertTrue(result.isLeft && result.left.exists(_.message.contains("out of range")))
  },
  test("shortToByte fails for values below Byte.MinValue") {
    val result = Into[Short, Byte].into((Byte.MinValue - 1).toShort)
    assertTrue(result.isLeft && result.left.exists(_.message.contains("out of range")))
  },
  test("intToByte fails for values above Byte.MaxValue") {
    val result = Into[Int, Byte].into(Byte.MaxValue + 1)
    assertTrue(result.isLeft && result.left.exists(_.message.contains("out of range")))
  },
  test("intToByte fails for values below Byte.MinValue") {
    val result = Into[Int, Byte].into(Byte.MinValue - 1)
    assertTrue(result.isLeft && result.left.exists(_.message.contains("out of range")))
  },
  test("intToShort fails for values above Short.MaxValue") {
    val result = Into[Int, Short].into(Short.MaxValue + 1)
    assertTrue(result.isLeft && result.left.exists(_.message.contains("out of range")))
  },
  test("intToShort fails for values below Short.MinValue") {
    val result = Into[Int, Short].into(Short.MinValue - 1)
    assertTrue(result.isLeft && result.left.exists(_.message.contains("out of range")))
  },
  test("longToByte fails for values above Byte.MaxValue") {
    val result = Into[Long, Byte].into(Byte.MaxValue.toLong + 1)
    assertTrue(result.isLeft && result.left.exists(_.message.contains("out of range")))
  },
  test("longToByte fails for values below Byte.MinValue") {
    val result = Into[Long, Byte].into(Byte.MinValue.toLong - 1)
    assertTrue(result.isLeft && result.left.exists(_.message.contains("out of range")))
  },
  test("longToShort fails for values above Short.MaxValue") {
    val result = Into[Long, Short].into(Short.MaxValue.toLong + 1)
    assertTrue(result.isLeft && result.left.exists(_.message.contains("out of range")))
  },
  test("longToShort fails for values below Short.MinValue") {
    val result = Into[Long, Short].into(Short.MinValue.toLong - 1)
    assertTrue(result.isLeft && result.left.exists(_.message.contains("out of range")))
  },
  test("longToInt fails for values above Int.MaxValue") {
    val result = Into[Long, Int].into(Int.MaxValue.toLong + 1)
    assertTrue(result.isLeft && result.left.exists(_.message.contains("out of range")))
  },
  test("longToInt fails for values below Int.MinValue") {
    val result = Into[Long, Int].into(Int.MinValue.toLong - 1)
    assertTrue(result.isLeft && result.left.exists(_.message.contains("out of range")))
  },
  test("doubleToFloat fails for values above Float.MaxValue") {
    val result = Into[Double, Float].into(Double.MaxValue)
    assertTrue(result.isLeft && result.left.exists(_.message.contains("out of range")))
  },
  test("floatToInt fails for non-integer float values") {
    val result = Into[Float, Int].into(3.14f)
    assertTrue(result.isLeft && result.left.exists(_.message.contains("cannot be precisely")))
  },
  test("floatToLong fails for non-integer float values") {
    val result = Into[Float, Long].into(3.14f)
    assertTrue(result.isLeft && result.left.exists(_.message.contains("cannot be precisely")))
  },
  test("doubleToInt fails for non-integer double values") {
    val result = Into[Double, Int].into(3.14)
    assertTrue(result.isLeft && result.left.exists(_.message.contains("cannot be precisely")))
  },
  test("doubleToLong fails for non-integer double values") {
    val result = Into[Double, Long].into(3.14)
    assertTrue(result.isLeft && result.left.exists(_.message.contains("cannot be precisely")))
  }
)
```

---

## Test Group 2: All Primitive Types in Records (Macro Coverage)

**File:** `schema/shared/src/test/scala-3/zio/blocks/schema/SchemaVersionSpecificSpec.scala`

**Location:** Add as a new suite inside the main spec.

**Target code:** `schema/shared/src/main/scala-3/zio/blocks/schema/SchemaCompanionVersionSpecific.scala` lines 305-846 (fieldConstructor/fieldDeconstructor for each primitive type)

**Imports needed:** Already present

```scala
suite("all primitive field types macro coverage")(
  test("derives schema for record with all primitive field types") {
    case class AllPrimitives(
      b: Byte,
      s: Short,
      i: Int,
      l: Long,
      f: Float,
      d: Double,
      c: Char,
      bool: Boolean,
      u: Unit,
      str: String
    ) derives Schema

    val value = AllPrimitives(1.toByte, 2.toShort, 3, 4L, 5.0f, 6.0, 'a', true, (), "test")
    val schema = Schema[AllPrimitives]
    val dv = schema.toDynamicValue(value)
    val roundTrip = schema.fromDynamicValue(dv)

    assert(roundTrip)(isRight(equalTo(value))) &&
    assert(schema.reflect.asRecord.map(_.fields.length))(isSome(equalTo(10)))
  },
  test("derives schema for tuple with all primitive types") {
    val schema = Schema[(Byte, Short, Int, Long, Float, Double, Char, Boolean, Unit, String)]
    val value = (1.toByte, 2.toShort, 3, 4L, 5.0f, 6.0, 'a', true, (), "test")
    val dv = schema.toDynamicValue(value)
    val roundTrip = schema.fromDynamicValue(dv)

    assert(roundTrip)(isRight(equalTo(value)))
  },
  test("derives schema for EmptyTuple") {
    val schema = Schema[EmptyTuple]
    val dv = schema.toDynamicValue(EmptyTuple)
    val roundTrip = schema.fromDynamicValue(dv)

    assert(roundTrip)(isRight(equalTo(EmptyTuple)))
  }
)
```

---

## Test Group 3: Opaque Type Schema Derivation (Scala 3 Only)

**File:** `schema/shared/src/test/scala-3/zio/blocks/schema/SchemaVersionSpecificSpec.scala`

**Location:** Add as a new suite inside the main spec.

**Target code:** `schema/shared/src/main/scala-3/zio/blocks/schema/SchemaCompanionVersionSpecific.scala` lines 93, 106-108, 123-126, 134, 185-204 (opaque/newtype handling)

```scala
suite("opaque type macro coverage")(
  test("derives schema for simple opaque type") {
    object Types {
      opaque type UserId = String
      object UserId {
        def apply(s: String): UserId = s
        def unwrap(id: UserId): String = id
        given Schema[UserId] = Schema.derived[UserId]
      }
    }
    import Types._

    val schema = summon[Schema[UserId]]
    val id = UserId("user-123")
    val dv = schema.toDynamicValue(id)
    val roundTrip = schema.fromDynamicValue(dv)

    assert(roundTrip)(isRight(equalTo(id)))
  },
  test("derives schema for opaque type wrapping Int") {
    object Types {
      opaque type Age = Int
      object Age {
        def apply(n: Int): Age = n
        def unwrap(age: Age): Int = age
        given Schema[Age] = Schema.derived[Age]
      }
    }
    import Types._

    val schema = summon[Schema[Age]]
    val age = Age(42)
    val dv = schema.toDynamicValue(age)
    val roundTrip = schema.fromDynamicValue(dv)

    assert(roundTrip)(isRight(equalTo(age)))
  },
  test("derives schema for record with opaque type field") {
    object Types {
      opaque type Email = String
      object Email {
        def apply(s: String): Email = s
        given Schema[Email] = Schema.derived[Email]
      }

      case class User(email: Email, name: String) derives Schema
    }
    import Types._

    val user = User(Email("test@example.com"), "Test")
    val schema = Schema[User]
    val dv = schema.toDynamicValue(user)
    val roundTrip = schema.fromDynamicValue(dv)

    assert(roundTrip)(isRight(equalTo(user)))
  }
)
```

---

## Test Group 4: Named Tuple Schema Derivation (Scala 3 Only)

**File:** `schema/shared/src/test/scala-3/zio/blocks/schema/SchemaVersionSpecificSpec.scala`

**Location:** Add as a new suite inside the main spec.

**Target code:** `schema/shared/src/main/scala-3/zio/blocks/schema/SchemaCompanionVersionSpecific.scala` lines 827-828 (named tuple handling)

```scala
suite("named tuple macro coverage")(
  test("derives schema for named tuple") {
    val schema = Schema[(name: String, age: Int)]
    val value: (name: String, age: Int) = (name = "John", age = 30)
    val dv = schema.toDynamicValue(value)
    val roundTrip = schema.fromDynamicValue(dv)

    assert(roundTrip)(isRight(equalTo(value)))
  },
  test("derives schema for named tuple with all primitive types") {
    val schema = Schema[(b: Byte, s: Short, i: Int, l: Long)]
    val value: (b: Byte, s: Short, i: Int, l: Long) = (b = 1.toByte, s = 2.toShort, i = 3, l = 4L)
    val dv = schema.toDynamicValue(value)
    val roundTrip = schema.fromDynamicValue(dv)

    assert(roundTrip)(isRight(equalTo(value)))
  },
  test("derives schema for nested named tuple") {
    val schema = Schema[(outer: (inner: String, value: Int), flag: Boolean)]
    val value: (outer: (inner: String, value: Int), flag: Boolean) = 
      (outer = (inner = "test", value = 42), flag = true)
    val dv = schema.toDynamicValue(value)
    val roundTrip = schema.fromDynamicValue(dv)

    assert(roundTrip)(isRight(equalTo(value)))
  }
)
```

---

## Test Group 5: Into Tuple Conversions (Scala 3 Only)

**File:** `schema/shared/src/test/scala-3/zio/blocks/schema/IntoVersionSpecificSpec.scala`

**Location:** Add as a new suite inside the main spec.

**Target code:** `schema/shared/src/main/scala-3/zio/blocks/schema/IntoVersionSpecific.scala` lines 496-541, 1618-2026 (tuple operations)

```scala
suite("tuple conversion macro coverage")(
  test("converts tuple to case class with matching arity") {
    case class Point(x: Int, y: Int)
    val into = Into[(Int, Int), Point]
    val result = into.into((10, 20))

    assert(result)(isRight(equalTo(Point(10, 20))))
  },
  test("converts case class to tuple") {
    case class Point(x: Int, y: Int)
    val into = Into[Point, (Int, Int)]
    val result = into.into(Point(10, 20))

    assert(result)(isRight(equalTo((10, 20))))
  },
  test("converts tuple to tuple with element type coercion") {
    val into = Into[(Int, Int), (Long, Long)]
    val result = into.into((1, 2))

    assert(result)(isRight(equalTo((1L, 2L))))
  },
  test("converts nested tuple to nested tuple") {
    val into = Into[((Int, Int), String), ((Long, Long), String)]
    val result = into.into(((1, 2), "test"))

    assert(result)(isRight(equalTo(((1L, 2L), "test"))))
  },
  test("converts 3-element tuple to case class") {
    case class Triple(a: Int, b: String, c: Boolean)
    val into = Into[(Int, String, Boolean), Triple]
    val result = into.into((1, "hello", true))

    assert(result)(isRight(equalTo(Triple(1, "hello", true))))
  },
  test("converts case class to 3-element tuple") {
    case class Triple(a: Int, b: String, c: Boolean)
    val into = Into[Triple, (Int, String, Boolean)]
    val result = into.into(Triple(1, "hello", true))

    assert(result)(isRight(equalTo((1, "hello", true))))
  }
)
```

---

## Test Group 6: Into Primitive Type Checks

**File:** `schema/shared/src/test/scala-3/zio/blocks/schema/IntoVersionSpecificSpec.scala`

**Location:** Add as a new suite inside the main spec.

**Target code:** `schema/shared/src/main/scala-3/zio/blocks/schema/IntoVersionSpecific.scala` lines 33-163 (primitive type handling in derive)

```scala
suite("primitive type macro coverage")(
  test("converts Boolean to Boolean") {
    val into = Into[Boolean, Boolean]
    assert(into.into(true))(isRight(equalTo(true))) &&
    assert(into.into(false))(isRight(equalTo(false)))
  },
  test("converts Unit to Unit") {
    val into = Into[Unit, Unit]
    assert(into.into(()))(isRight(equalTo(())))
  },
  test("converts primitive to single-field wrapper") {
    case class Age(value: Int)
    val into = Into[Int, Age]
    assert(into.into(42))(isRight(equalTo(Age(42))))
  },
  test("converts single-field wrapper to primitive") {
    case class Age(value: Int)
    val into = Into[Age, Int]
    assert(into.into(Age(42)))(isRight(equalTo(42)))
  },
  test("converts Byte single-field wrapper") {
    case class ByteWrapper(value: Byte)
    val into = Into[Byte, ByteWrapper]
    assert(into.into(42.toByte))(isRight(equalTo(ByteWrapper(42.toByte))))
  },
  test("converts Short single-field wrapper") {
    case class ShortWrapper(value: Short)
    val into = Into[Short, ShortWrapper]
    assert(into.into(42.toShort))(isRight(equalTo(ShortWrapper(42.toShort))))
  },
  test("converts Long single-field wrapper") {
    case class LongWrapper(value: Long)
    val into = Into[Long, LongWrapper]
    assert(into.into(42L))(isRight(equalTo(LongWrapper(42L))))
  },
  test("converts Float single-field wrapper") {
    case class FloatWrapper(value: Float)
    val into = Into[Float, FloatWrapper]
    assert(into.into(3.14f))(isRight(equalTo(FloatWrapper(3.14f))))
  },
  test("converts Double single-field wrapper") {
    case class DoubleWrapper(value: Double)
    val into = Into[Double, DoubleWrapper]
    assert(into.into(3.14))(isRight(equalTo(DoubleWrapper(3.14))))
  },
  test("converts Char single-field wrapper") {
    case class CharWrapper(value: Char)
    val into = Into[Char, CharWrapper]
    assert(into.into('X'))(isRight(equalTo(CharWrapper('X'))))
  },
  test("converts String single-field wrapper") {
    case class StringWrapper(value: String)
    val into = Into[String, StringWrapper]
    assert(into.into("hello"))(isRight(equalTo(StringWrapper("hello"))))
  }
)
```

---

## Test Group 7: Binding for Some/Left/Right (Scala 3 Only)

**File:** `schema/shared/src/test/scala-3/zio/blocks/schema/binding/BindingOfVersionSpecificSpec.scala`

**Location:** Add as a new suite inside the main spec.

**Target code:** `schema/shared/src/main/scala-3/zio/blocks/schema/binding/BindingCompanionVersionSpecific.scala` lines 319-374 (deriveSomeBinding, deriveLeftBinding, deriveRightBinding)

**Note:** The file already has `import zio.blocks.schema.binding._` via the package declaration.

```scala
suite("Some/Left/Right binding macro coverage")(
  test("derives binding for Some[Int]") {
    val binding = Binding.of[Some[Int]]
    val regs = binding.usedRegisters
    assertTrue(regs.ints >= 1 || regs.objects >= 1)
  },
  test("derives binding for Some[String]") {
    val binding = Binding.of[Some[String]]
    val regs = binding.usedRegisters
    assertTrue(regs.objects >= 1)
  },
  test("derives binding for Some[Long]") {
    val binding = Binding.of[Some[Long]]
    val regs = binding.usedRegisters
    assertTrue(regs.longs >= 1 || regs.objects >= 1)
  },
  test("derives binding for Some[Double]") {
    val binding = Binding.of[Some[Double]]
    val regs = binding.usedRegisters
    assertTrue(regs.doubles >= 1 || regs.objects >= 1)
  },
  test("derives binding for Left[String, Int]") {
    val binding = Binding.of[Left[String, Int]]
    val regs = binding.usedRegisters
    assertTrue(regs.objects >= 1)
  },
  test("derives binding for Left[Int, String]") {
    val binding = Binding.of[Left[Int, String]]
    val regs = binding.usedRegisters
    assertTrue(regs.ints >= 1 || regs.objects >= 1)
  },
  test("derives binding for Left[Long, Boolean]") {
    val binding = Binding.of[Left[Long, Boolean]]
    val regs = binding.usedRegisters
    assertTrue(regs.longs >= 1 || regs.objects >= 1)
  },
  test("derives binding for Right[String, Int]") {
    val binding = Binding.of[Right[String, Int]]
    val regs = binding.usedRegisters
    assertTrue(regs.ints >= 1 || regs.objects >= 1)
  },
  test("derives binding for Right[Int, String]") {
    val binding = Binding.of[Right[Int, String]]
    val regs = binding.usedRegisters
    assertTrue(regs.objects >= 1)
  },
  test("derives binding for Right[Boolean, Double]") {
    val binding = Binding.of[Right[Boolean, Double]]
    val regs = binding.usedRegisters
    assertTrue(regs.doubles >= 1 || regs.objects >= 1)
  },
  test("derives binding for Right[Byte, Short]") {
    val binding = Binding.of[Right[Byte, Short]]
    val regs = binding.usedRegisters
    assertTrue(regs.shorts >= 1 || regs.bytes >= 1 || regs.objects >= 1)
  }
)
```

---

## Test Group 8: Json.fromDynamicValue All Primitive Types

**File:** `schema/shared/src/test/scala/zio/blocks/schema/json/JsonSpec.scala`

**Location:** Add as a new suite inside the main spec.

**Target code:** `schema/shared/src/main/scala/zio/blocks/schema/json/Json.scala` lines 831-860 (fromPrimitiveValue)

**Required imports:**
```scala
import java.time._
import java.util.{Currency, UUID}
```

```scala
suite("fromDynamicValue primitive coverage")(
  test("converts all PrimitiveValue types to Json") {
    import java.time._
    import java.util.{Currency, UUID}

    val testCases: List[(PrimitiveValue, Json => Boolean)] = List(
      (PrimitiveValue.Unit, _ == Json.Object.empty),
      (PrimitiveValue.Boolean(true), _ == Json.True),
      (PrimitiveValue.Boolean(false), _ == Json.False),
      (PrimitiveValue.Byte(42.toByte), j => j.as(JsonType.Number).exists(_.value == "42")),
      (PrimitiveValue.Short(100.toShort), j => j.as(JsonType.Number).exists(_.value == "100")),
      (PrimitiveValue.Int(1000), j => j.as(JsonType.Number).exists(_.value == "1000")),
      (PrimitiveValue.Long(10000L), j => j.as(JsonType.Number).exists(_.value == "10000")),
      (PrimitiveValue.Float(3.5f), j => j.as(JsonType.Number).isDefined),
      (PrimitiveValue.Double(3.14159), j => j.as(JsonType.Number).isDefined),
      (PrimitiveValue.Char('X'), j => j.as(JsonType.String).exists(_.value == "X")),
      (PrimitiveValue.String("hello"), j => j.as(JsonType.String).exists(_.value == "hello")),
      (PrimitiveValue.BigInt(BigInt("123456789012345678901234567890")), j => j.as(JsonType.Number).isDefined),
      (PrimitiveValue.BigDecimal(BigDecimal("123.456789")), j => j.as(JsonType.Number).isDefined),
      (PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY), j => j.as(JsonType.String).exists(_.value == "MONDAY")),
      (PrimitiveValue.Month(Month.JANUARY), j => j.as(JsonType.String).exists(_.value == "JANUARY")),
      (PrimitiveValue.Duration(Duration.ofHours(1)), j => j.as(JsonType.String).isDefined),
      (PrimitiveValue.Instant(Instant.EPOCH), j => j.as(JsonType.String).isDefined),
      (PrimitiveValue.LocalDate(LocalDate.of(2024, 1, 15)), j => j.as(JsonType.String).isDefined),
      (PrimitiveValue.LocalDateTime(LocalDateTime.of(2024, 1, 15, 12, 30)), j => j.as(JsonType.String).isDefined),
      (PrimitiveValue.LocalTime(LocalTime.of(12, 30, 45)), j => j.as(JsonType.String).isDefined),
      (PrimitiveValue.MonthDay(MonthDay.of(1, 15)), j => j.as(JsonType.String).isDefined),
      (PrimitiveValue.OffsetDateTime(OffsetDateTime.of(2024, 1, 15, 12, 30, 0, 0, ZoneOffset.UTC)), j => j.as(JsonType.String).isDefined),
      (PrimitiveValue.OffsetTime(OffsetTime.of(12, 30, 0, 0, ZoneOffset.UTC)), j => j.as(JsonType.String).isDefined),
      (PrimitiveValue.Period(Period.ofDays(30)), j => j.as(JsonType.String).isDefined),
      (PrimitiveValue.Year(Year.of(2024)), j => j.as(JsonType.String).isDefined),
      (PrimitiveValue.YearMonth(YearMonth.of(2024, 1)), j => j.as(JsonType.String).isDefined),
      (PrimitiveValue.ZoneId(ZoneId.of("UTC")), j => j.as(JsonType.String).isDefined),
      (PrimitiveValue.ZoneOffset(ZoneOffset.UTC), j => j.as(JsonType.String).isDefined),
      (PrimitiveValue.ZonedDateTime(ZonedDateTime.of(2024, 1, 15, 12, 30, 0, 0, ZoneId.of("UTC"))), j => j.as(JsonType.String).isDefined),
      (PrimitiveValue.Currency(Currency.getInstance("USD")), j => j.as(JsonType.String).exists(_.value == "USD")),
      (PrimitiveValue.UUID(UUID.fromString("12345678-1234-1234-1234-123456789012")), j => j.as(JsonType.String).isDefined)
    )

    testCases.foldLeft(assertTrue(true)) { case (acc, (pv, check)) =>
      val json = Json.fromDynamicValue(DynamicValue.Primitive(pv))
      acc && assertTrue(check(json))
    }
  }
)
```

---

## Test Group 9: JsonDecoder Error Cases

**File:** `schema/shared/src/test/scala/zio/blocks/schema/json/JsonSpec.scala`

**Location:** Add as a new suite inside the main spec.

**Target code:** `schema/shared/src/main/scala/zio/blocks/schema/json/JsonDecoder.scala` lines 65-469 (else branches)

```scala
suite("JsonDecoder error branches")(
  test("stringDecoder fails on non-string Json values") {
    assertTrue(JsonDecoder[String].decode(Json.Number("42")).isLeft) &&
    assertTrue(JsonDecoder[String].decode(Json.True).isLeft) &&
    assertTrue(JsonDecoder[String].decode(Json.Null).isLeft) &&
    assertTrue(JsonDecoder[String].decode(Json.Array.empty).isLeft) &&
    assertTrue(JsonDecoder[String].decode(Json.Object.empty).isLeft)
  },
  test("booleanDecoder fails on non-boolean Json values") {
    assertTrue(JsonDecoder[Boolean].decode(Json.String("true")).isLeft) &&
    assertTrue(JsonDecoder[Boolean].decode(Json.Number("1")).isLeft) &&
    assertTrue(JsonDecoder[Boolean].decode(Json.Null).isLeft)
  },
  test("intDecoder fails on non-number Json values") {
    assertTrue(JsonDecoder[Int].decode(Json.String("42")).isLeft) &&
    assertTrue(JsonDecoder[Int].decode(Json.True).isLeft) &&
    assertTrue(JsonDecoder[Int].decode(Json.Null).isLeft)
  },
  test("intDecoder fails on non-integer number") {
    assertTrue(JsonDecoder[Int].decode(Json.Number("3.14")).isLeft)
  },
  test("longDecoder fails on non-number Json values") {
    assertTrue(JsonDecoder[Long].decode(Json.String("42")).isLeft) &&
    assertTrue(JsonDecoder[Long].decode(Json.True).isLeft)
  },
  test("doubleDecoder fails on non-number Json values") {
    assertTrue(JsonDecoder[Double].decode(Json.String("3.14")).isLeft) &&
    assertTrue(JsonDecoder[Double].decode(Json.Null).isLeft)
  },
  test("floatDecoder fails on non-number Json values") {
    assertTrue(JsonDecoder[Float].decode(Json.String("3.14")).isLeft)
  },
  test("byteDecoder fails on out-of-range values") {
    assertTrue(JsonDecoder[Byte].decode(Json.Number("128")).isLeft) &&
    assertTrue(JsonDecoder[Byte].decode(Json.Number("-129")).isLeft)
  },
  test("shortDecoder fails on out-of-range values") {
    assertTrue(JsonDecoder[Short].decode(Json.Number("32768")).isLeft) &&
    assertTrue(JsonDecoder[Short].decode(Json.Number("-32769")).isLeft)
  },
  test("optionDecoder handles None for null") {
    assert(JsonDecoder[Option[Int]].decode(Json.Null))(isRight(equalTo(None)))
  },
  test("optionDecoder handles Some for non-null") {
    assert(JsonDecoder[Option[Int]].decode(Json.Number("42")))(isRight(equalTo(Some(42))))
  },
  test("listDecoder fails on non-array") {
    assertTrue(JsonDecoder[List[Int]].decode(Json.Object.empty).isLeft)
  },
  test("mapDecoder fails on non-object") {
    assertTrue(JsonDecoder[Map[String, Int]].decode(Json.Array.empty).isLeft)
  }
)
```

---

## Test Group 10: Json Path Operations

**File:** `schema/shared/src/test/scala/zio/blocks/schema/json/JsonSpec.scala`

**Location:** Add as a new suite inside the main spec.

**Target code:** `schema/shared/src/main/scala/zio/blocks/schema/json/Json.scala` lines 1390-1826 (path operations)

```scala
suite("Json path operation coverage")(
  test("modifyAtPathRecursive modifies all matching nested paths") {
    val json = Json.parse("""{"a": {"x": 1}, "b": {"x": 2}, "c": {"y": 3}}""").getOrElse(Json.Null)
    val result = json.modifyAtPathRecursive(List(JsonPath.Field("x")))(_ => Json.Number("0"))
    
    assertTrue(
      result.get("a").flatMap(_.get("x")) == Some(Json.Number("0")) &&
      result.get("b").flatMap(_.get("x")) == Some(Json.Number("0")) &&
      result.get("c").flatMap(_.get("y")) == Some(Json.Number("3"))
    )
  },
  test("deleteAtPathRecursive removes all matching nested paths") {
    val json = Json.parse("""{"a": {"x": 1, "y": 2}, "b": {"x": 3, "z": 4}}""").getOrElse(Json.Null)
    val result = json.deleteAtPathRecursive(List(JsonPath.Field("x")))
    
    assertTrue(
      result.get("a").flatMap(_.get("x")).isEmpty &&
      result.get("b").flatMap(_.get("x")).isEmpty &&
      result.get("a").flatMap(_.get("y")).isDefined &&
      result.get("b").flatMap(_.get("z")).isDefined
    )
  },
  test("insertAtPath inserts value at array index") {
    val json = Json.parse("""{"items": [1, 3, 4]}""").getOrElse(Json.Null)
    val result = json.insertAtPath(List(JsonPath.Field("items"), JsonPath.Index(1)), Json.Number("2"))
    val items = result.get("items").flatMap(_.as(JsonType.Array)).map(_.values)
    
    assertTrue(
      items.exists(_.length == 4) &&
      items.flatMap(_.lift(1)) == Some(Json.Number("2"))
    )
  },
  test("insertAtPathOrFail fails when path does not exist") {
    val json = Json.parse("""{"a": 1}""").getOrElse(Json.Null)
    val result = json.insertAtPathOrFail(List(JsonPath.Field("nonexistent"), JsonPath.Index(0)), Json.Number("1"))
    
    assertTrue(result.isLeft)
  },
  test("modifyAtPathOrFail fails when path does not exist") {
    val json = Json.parse("""{"a": 1}""").getOrElse(Json.Null)
    val result = json.modifyAtPathOrFail(List(JsonPath.Field("nonexistent")))(_ => Json.Number("2"))
    
    assertTrue(result.isLeft)
  },
  test("parse handles malformed JSON") {
    assertTrue(Json.parse("{invalid}").isLeft) &&
    assertTrue(Json.parse("{\"key\":").isLeft) &&
    assertTrue(Json.parse("[1, 2,]").isLeft) &&
    assertTrue(Json.parse("").isLeft)
  },
  test("compare orders different Json types correctly") {
    assertTrue(Json.Null.compare(Json.True) < 0) &&
    assertTrue(Json.True.compare(Json.False) > 0) &&
    assertTrue(Json.String("a").compare(Json.String("b")) < 0) &&
    assertTrue(Json.Number("1").compare(Json.Number("2")) < 0) &&
    assertTrue(Json.Array.empty.compare(Json.Object.empty) < 0)
  },
  test("as extracts typed values from Json") {
    assertTrue(Json.Number("42").as[Int] == Some(42)) &&
    assertTrue(Json.String("hello").as[String] == Some("hello")) &&
    assertTrue(Json.True.as[Boolean] == Some(true)) &&
    assertTrue(Json.Number("42").as[String] == None)
  },
  test("unwrap returns inner value for matching type") {
    val arr = Json.Array(Json.Number("1"), Json.Number("2"))
    val obj = Json.Object("a" -> Json.Number("1"))
    
    assertTrue(arr.unwrap(JsonType.Array).isDefined) &&
    assertTrue(obj.unwrap(JsonType.Object).isDefined) &&
    assertTrue(arr.unwrap(JsonType.Object).isEmpty)
  }
)
```

---

## Test Group 11: DynamicPatch Operations

**File:** `schema/shared/src/test/scala/zio/blocks/schema/patch/DynamicPatchSpec.scala`

**Location:** Add as a new suite at the end of the existing spec.

**Target code:** `schema/shared/src/main/scala/zio/blocks/schema/patch/DynamicPatch.scala` lines 95-113, 244-422 (renderPrimitiveDelta, navigateAndApply, applyToAllElements)

```scala
suite("DynamicPatch additional coverage")(
  test("PrimitiveDelta.render covers all numeric delta types") {
    val deltas = List(
      (PrimitiveDelta.ByteDelta(1.toByte), _.nonEmpty),
      (PrimitiveDelta.ShortDelta(1.toShort), _.nonEmpty),
      (PrimitiveDelta.IntDelta(100), _.contains("100")),
      (PrimitiveDelta.LongDelta(1000L), _.contains("1000")),
      (PrimitiveDelta.FloatDelta(1.5f), _.nonEmpty),
      (PrimitiveDelta.DoubleDelta(2.5), _.nonEmpty),
      (PrimitiveDelta.CharDelta('X'), _.nonEmpty)
    )
    
    deltas.foldLeft(assertTrue(true)) { case (acc, (delta, check)) =>
      acc && assertTrue(check(delta.render))
    }
  },
  test("applies patch to deeply nested record field") {
    val original = DynamicValue.Record(
      Chunk(
        "level1" -> DynamicValue.Record(
          Chunk(
            "level2" -> DynamicValue.Record(
              Chunk(
                "value" -> intVal(1)
              )
            )
          )
        )
      )
    )
    val patch = DynamicPatch(
      DynamicOptic.root.field("level1").field("level2").field("value"),
      DynamicPatch.Operation.Set(intVal(99))
    )
    val result = patch(original)
    
    val expected = DynamicValue.Record(
      Chunk(
        "level1" -> DynamicValue.Record(
          Chunk(
            "level2" -> DynamicValue.Record(
              Chunk(
                "value" -> intVal(99)
              )
            )
          )
        )
      )
    )
    assertTrue(result == Right(expected))
  },
  test("applies patch through variant structure") {
    val original = DynamicValue.Variant("Some", intVal(42))
    val patch = DynamicPatch(
      DynamicOptic.root,
      DynamicPatch.Operation.Set(DynamicValue.Variant("Some", intVal(99)))
    )
    val result = patch(original)
    
    assertTrue(result == Right(DynamicValue.Variant("Some", intVal(99))))
  },
  test("navigateAndApply handles map values") {
    val original = DynamicValue.Map(
      Chunk(
        stringVal("key1") -> intVal(1),
        stringVal("key2") -> intVal(2)
      )
    )
    val patch = DynamicPatch(
      DynamicOptic.root.key(stringVal("key1")),
      DynamicPatch.Operation.Set(intVal(100))
    )
    val result = patch(original)
    
    assertTrue(result.isRight)
  }
)
```

---

## Test Group 12: JsonSchema Combinators

**File:** `schema/shared/src/test/scala/zio/blocks/schema/json/JsonSchemaCombinatorSpec.scala`

**Location:** Add as a new suite inside the main spec.

**Target code:** `schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchema.scala` lines 476, 496, 515-527 (&&, ||, withNullable)

**Note:** Use the same patterns as existing tests in this file (see `stringSchema`, `integerSchema`, etc. private vals at top of file).

```scala
suite("withNullable edge cases")(
  test("withNullable on True returns True") {
    assertTrue(JsonSchema.True.withNullable == JsonSchema.True)
  },
  test("withNullable on False creates nullable False") {
    val result = JsonSchema.False.withNullable
    // False.withNullable should result in a schema that allows null
    assertTrue(result.conforms(Json.Null))
  },
  test("withNullable on string schema allows null") {
    val nullable = stringSchema.withNullable
    assertTrue(
      nullable.conforms(Json.String("hello")),
      nullable.conforms(Json.Null)
    )
  },
  test("withNullable is idempotent") {
    val nullable = stringSchema.withNullable
    val doubleNullable = nullable.withNullable
    assertTrue(
      nullable.conforms(Json.Null),
      doubleNullable.conforms(Json.Null),
      nullable.conforms(Json.String("test")),
      doubleNullable.conforms(Json.String("test"))
    )
  }
),
suite("&& (allOf) combinator edge cases")(
  test("&& combines when left side already has allOf") {
    val s1 = minLength3 && maxLength5
    val combined = s1 && stringSchema
    assertTrue(
      combined.conforms(Json.String("abc")),
      !combined.conforms(Json.String("ab"))
    )
  },
  test("&& combines when right side already has allOf") {
    val s1 = minLength3 && maxLength5
    val combined = stringSchema && s1
    assertTrue(
      combined.conforms(Json.String("abcd")),
      !combined.conforms(Json.String("ab"))
    )
  },
  test("&& combines when both sides have allOf") {
    val s1 = minLength3 && maxLength5
    val s2 = stringSchema && stringSchema
    val combined = s1 && s2
    assertTrue(
      combined.conforms(Json.String("abc")),
      !combined.conforms(Json.String("ab"))
    )
  }
),
suite("|| (anyOf) combinator edge cases")(
  test("|| combines when left side already has anyOf") {
    val s1 = stringSchema || integerSchema
    val combined = s1 || booleanSchema
    assertTrue(
      combined.conforms(Json.String("hello")),
      combined.conforms(Json.Number(42)),
      combined.conforms(Json.True)
    )
  },
  test("|| combines when right side already has anyOf") {
    val s1 = stringSchema || integerSchema
    val combined = booleanSchema || s1
    assertTrue(
      combined.conforms(Json.String("hello")),
      combined.conforms(Json.Number(42)),
      combined.conforms(Json.False)
    )
  },
  test("|| combines when both sides have anyOf") {
    val s1 = stringSchema || integerSchema
    val s2 = booleanSchema || nullSchema
    val combined = s1 || s2
    assertTrue(
      combined.conforms(Json.String("hello")),
      combined.conforms(Json.Number(42)),
      combined.conforms(Json.True),
      combined.conforms(Json.Null)
    )
  }
)
```

---

## Verification Workflow

After adding each test group:

1. **Run tests with coverage:**
   ```bash
   ROOT="$(git rev-parse --show-toplevel)"
   mkdir -p "$ROOT/.git/agent-logs"
   LOG="$ROOT/.git/agent-logs/sbt-$(date +%s)-$$.log"
   sbt -Dsbt.color=false "project schemaJVM; clean; coverage; test; coverageReport" >"$LOG" 2>&1
   echo "Exit: $?"
   ```

2. **Check for failures:**
   ```bash
   grep -n -i -E 'error|exception|failed|failure' "$LOG" | head -30 || true
   ```

3. **Review coverage report:**
   Open `schema/jvm/target/scala-3.3.7/scoverage-report/index.html` in browser.

4. **Format after all tests pass:**
   ```bash
   sbt "schemaJVM/scalafmt; schemaJVM/Test/scalafmt"
   ```

---

## Priority Order

For maximum impact with minimum effort:

1. **Test Group 1** (Into narrowing) — 16+ branches, simple runtime tests
2. **Test Group 2** (All primitives in records) — 80+ branches, macro coverage
3. **Test Group 9** (JsonDecoder errors) — 38 branches, simple runtime tests  
4. **Test Group 8** (Json.fromDynamicValue) — 26 branches, runtime coverage
5. **Test Group 3-4** (Opaque/Named tuples) — Macro coverage for Scala 3 features
6. **Test Group 5-6** (Into tuple/primitive) — Macro coverage for conversions
7. **Test Group 7** (Binding Some/Left/Right) — Macro coverage for binding derivation
8. **Test Group 10-12** (Json/Patch operations) — Runtime edge cases

---

## Notes

- All tests extend `SchemaBaseSpec` which provides proper test aspects for JVM
- Tests in `shared/src/test/scala/` run on both Scala 2 and Scala 3
- Tests in `shared/src/test/scala-3/` are Scala 3 only (named tuples, opaque types)
- Use `assert(x)(equalTo(y))` or `assertTrue(condition)` assertion style
- Always verify tests compile and pass before checking coverage impact
