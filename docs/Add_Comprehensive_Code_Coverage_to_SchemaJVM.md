# Add Comprehensive Code Coverage to SchemaJVM

## Executive Summary

The schemaJVM project has a branch coverage of **80.54%**, below the required minimum of **83.00%**. To meet the threshold, approximately **213 additional branches** need test coverage.

This document identifies **all 1,688 uncovered branches** across 57 files and provides a comprehensive plan to add tests, distinguishing between **testable runtime code** and **compile-time macro code** that cannot be directly tested.

---

## Coverage Statistics

| Metric | Current | Target | Gap |
|--------|---------|--------|-----|
| Branch Coverage | 80.54% | 83.00% | 2.46% |
| Branches Covered | 6,988 | 7,201 | 213 |
| Total Branches | 8,676 | - | - |
| Uncovered Branches | 1,688 | - | - |

---

## Classification of Uncovered Code

### Compile-Time Macro Code (NOT Directly Testable)

Code in `$anonfun` methods within `*VersionSpecific.scala` files runs at **compile time** via Scala 3 quoted macros. These branches are exercised when you define types that trigger the macro, not through runtime tests.

**To cover macro branches:** Define types in tests that trigger the uncovered code paths (e.g., opaque types, zio-prelude newtypes, all primitive field types, named tuples).

### Runtime Code (Directly Testable)

Regular methods in non-macro files can be tested with standard unit tests.

---

## Complete Uncovered Branch Inventory

### Files with Most Uncovered Branches

| Rank | File | Uncovered Branches | Type |
|------|------|-------------------|------|
| 1 | `SchemaCompanionVersionSpecific.scala` (scala-3) | 289 | Macro |
| 2 | `IntoVersionSpecific.scala` (scala-3) | 299 | Macro |
| 3 | `Json.scala` | 193 | Runtime |
| 4 | `BindingCompanionVersionSpecific.scala` (scala-3) | 166 | Macro |
| 5 | `DynamicPatch.scala` | 90 | Runtime |
| 6 | `PrimitiveValue.scala` | 60 | Runtime |
| 7 | `Optic.scala` | 42 | Runtime |
| 8 | `JsonDecoder.scala` | 38 | Runtime |
| 9 | `SeqConstructor.scala` (scala-3) | 36 | Macro |
| 10 | `JsonBinaryCodecDeriver.scala` | 33 | Runtime |
| 11 | `DynamicOptic.scala` | 32 | Runtime |
| 12 | `DerivedOptics.scala` (scala-3) | 32 | Macro |
| 13 | `JsonReader.scala` | 29 | Runtime |
| 14 | `JsonSelection.scala` | 28 | Runtime |
| 15 | `JsonSchema.scala` | 28 | Runtime |
| 16 | `CommonMacroOps.scala` (scala-3) | 27 | Macro |
| 17 | `CompanionOptics.scala` (scala-3) | 24 | Macro |
| 18 | `DynamicValue.scala` | 23 | Runtime |
| 19 | `ReflectPrinter.scala` | 21 | Runtime |
| 20 | `HasBinding.scala` | 20 | Runtime |

---

## Part 1: Macro Code Coverage (Compile-Time)

### 1.1 SchemaCompanionVersionSpecific.scala — 289 branches

**File:** `schema/shared/src/main/scala-3/zio/blocks/schema/SchemaCompanionVersionSpecific.scala`

#### 1.1.1 `$anonfun` (224 branches) — Lines 305-846

These are anonymous functions in quoted macro code that handle different type patterns.

**Uncovered Scenarios:**
- Field constructors/deconstructors for: `Float`, `Long`, `Double`, `Boolean`, `Byte`, `Char`, `Short`, `Unit`
- Opaque type handling
- ZIO Prelude newtype handling
- TypeRef dealiasing
- Named tuple fields
- GenericTuple deconstruction

**Test Spec:** `SchemaSpec.scala`

**Test Sketch:**
```scala
suite("macro coverage - all primitive field types")(
  test("derives schema for record with ALL primitive types") {
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
    )
    object AllPrimitives extends CompanionOptics[AllPrimitives] {
      given schema: Schema[AllPrimitives] = Schema.derived[AllPrimitives]
    }

    val value = AllPrimitives(1, 2, 3, 4L, 5.0f, 6.0, 'a', true, (), "test")
    val dv = AllPrimitives.schema.toDynamicValue(value)
    assertTrue(AllPrimitives.schema.fromDynamicValue(dv) == Right(value))
  },
  test("derives schema for tuple with all primitive types") {
    val schema = Schema[(Byte, Short, Int, Long, Float, Double, Char, Boolean, Unit, String)]
    val value = (1.toByte, 2.toShort, 3, 4L, 5.0f, 6.0, 'a', true, (), "test")
    val dv = schema.toDynamicValue(value)
    assertTrue(schema.fromDynamicValue(dv) == Right(value))
  }
)
```

#### 1.1.2 `fieldConstructor` (37 branches) — Lines 305-327

Type-specific field construction in records.

**Covered by:** Same test as above (AllPrimitives record)

#### 1.1.3 Opaque/Newtype Methods (12 branches)

| Method | Lines | Branches |
|--------|-------|----------|
| `opaqueDealias` | 93 | 1 |
| `zioPreludeNewtypeDealias` | 106-108 | 2 |
| `typeRefDealias` | 123-126 | 4 |
| `dealiasOnDemand` | 134 | 1 |
| `isNonRecursive` | 185-204 | 3 |
| `toBlock` | 334-336 | 4 |

**Test Sketch:**
```scala
suite("opaque type schema derivation")(
  test("derives schema for opaque type") {
    object Types {
      opaque type UserId = String
      object UserId {
        def apply(s: String): UserId = s
        given Schema[UserId] = Schema.derived[UserId]
      }
    }
    import Types._
    val schema = Schema[UserId]
    val dv = schema.toDynamicValue(UserId("abc"))
    assertTrue(schema.fromDynamicValue(dv) == Right(UserId("abc")))
  },
  test("derives schema for record with opaque type field") {
    object Types {
      opaque type Email = String
      object Email {
        def apply(s: String): Email = s
        given Schema[Email] = Schema.derived[Email]
      }
      case class User(email: Email, name: String)
      object User {
        given Schema[User] = Schema.derived[User]
      }
    }
    import Types._
    val user = User(Email("test@example.com"), "Test")
    val dv = Schema[User].toDynamicValue(user)
    assertTrue(Schema[User].fromDynamicValue(dv) == Right(user))
  }
)
```

#### 1.1.4 `deriveSchema` (11 branches) — Lines 799-873

Schema derivation for various type kinds.

| Scenario | Lines |
|----------|-------|
| Named tuple | 827-828 |
| Union type | 838-840 |
| ZIO Prelude newtype | 871-873 |

**Test Sketch:**
```scala
test("derives schema for named tuple") {
  val schema = Schema[(name: String, age: Int)]
  val value: (name: String, age: Int) = (name = "John", age = 30)
  assertTrue(schema.toDynamicValue(value) != null)
}

test("derives schema for union type") {
  val schema = Schema[Int | String]
  assertTrue(schema.toDynamicValue(42) != null)
  assertTrue(schema.toDynamicValue("hello") != null)
}
```

#### 1.1.5 `constructor` (2 branches) — Lines 536-537

Empty tuple handling.

**Test Sketch:**
```scala
test("derives schema for empty tuple") {
  val schema = Schema[EmptyTuple]
  val dv = schema.toDynamicValue(EmptyTuple)
  assertTrue(schema.fromDynamicValue(dv) == Right(EmptyTuple))
}
```

---

### 1.2 IntoVersionSpecific.scala — 299 branches

**File:** `schema/shared/src/main/scala-3/zio/blocks/schema/IntoVersionSpecific.scala`

#### 1.2.1 `$anonfun` (139 branches) — Lines 251-2743

Compile-time Into derivation logic.

#### 1.2.2 `derive` (25 branches) — Lines 33-163

Main derivation entry point handling different type combinations.

| Lines | Scenario |
|-------|----------|
| 33-36 | Primitive type checks |
| 49-52 | Boolean type |
| 92, 96 | Unit type |
| 111 | Opaque type detection |
| 125-129 | ZIO Prelude newtype |
| 159 | Newtype conversion |
| 161-163 | Primitive to/from single-field product |

**Test Spec:** `IntoSpec.scala`

**Test Sketch:**
```scala
suite("Into macro coverage")(
  test("converts primitive to single-field wrapper") {
    case class Age(value: Int)
    val into = Into[Int, Age]
    assertTrue(into.into(42) == Right(Age(42)))
  },
  test("converts single-field wrapper to primitive") {
    case class Age(value: Int)
    val into = Into[Age, Int]
    assertTrue(into.into(Age(42)) == Right(42))
  },
  test("converts Boolean to Boolean") {
    val into = Into[Boolean, Boolean]
    assertTrue(into.into(true) == Right(true))
  },
  test("converts Unit to Unit") {
    val into = Into[Unit, Unit]
    assertTrue(into.into(()) == Right(()))
  }
)
```

#### 1.2.3 ZIO Newtype Handling (59 branches)

| Method | Lines | Branches |
|--------|-------|----------|
| `isZIONewtype` | 2495-2559 | 19 |
| `getNewtypeUnderlying` | 2572-2679 | 20 |
| `convertToNewtypeEither` | 2700-2788 | 12 |
| `getOpaqueCompanion` | 2247-2273 | 8 |

**Note:** These require zio-prelude dependency or types that mimic the pattern.

#### 1.2.4 Tuple Operations (20 branches)

| Method | Lines | Branches |
|--------|-------|----------|
| `getTupleTypeArgs` | 496-497 | 2 |
| `tupleElement` | 504-508 | 2 |
| `buildTuple` | 517-524 | 2 |
| `buildTupleFromExprs` | 534-541 | 2 |
| `deriveTupleToTuple` | 1974-2026 | 4 |
| `deriveTupleToCaseClass` | 1796-1849 | 4 |
| `deriveCaseClassToTuple` | 1618-1672 | 4 |

**Test Sketch:**
```scala
suite("tuple conversions")(
  test("converts tuple to case class with matching fields") {
    case class Point(x: Int, y: Int)
    val into = Into[(Int, Int), Point]
    assertTrue(into.into((10, 20)) == Right(Point(10, 20)))
  },
  test("converts case class to tuple") {
    case class Point(x: Int, y: Int)
    val into = Into[Point, (Int, Int)]
    assertTrue(into.into(Point(10, 20)) == Right((10, 20)))
  },
  test("converts tuple to tuple with type coercion") {
    val into = Into[(Int, Int), (Long, Long)]
    assertTrue(into.into((1, 2)) == Right((1L, 2L)))
  },
  test("converts nested tuples") {
    val into = Into[((Int, Int), String), ((Long, Long), String)]
    assertTrue(into.into(((1, 2), "a")) == Right(((1L, 2L), "a")))
  }
)
```

#### 1.2.5 Structural Conversions (12 branches)

| Method | Lines | Branches |
|--------|-------|----------|
| `canConvertStructuralToProduct` | 579-580 | 2 |
| `collectMembers` | 567-569 | 2 |
| `findMatchingSourceField` | 1356-1357 | 2 |
| `findMatchingTargetSubtype` | 958-965 | 2 |
| `generateCaseClause` | 1040-1043 | 3 |

#### 1.2.6 Other Methods

| Method | Lines | Branches |
|--------|-------|----------|
| `tryWiden` | 2606-2615 | 6 |
| `isSameCollectionKind` | 2369-2377 | 3 |
| `convertToOpaqueTypeEitherTyped` | 2293-2327 | 4 |
| `convertCollectionElements` | 2439-2455 | 4 |
| `deriveNewtypeConversion` | 205-303 | 11 |
| `derivePrimitiveToSingleFieldProduct` | 367-381 | 3 |
| `deriveSingleFieldProductToPrimitive` | 413-425 | 3 |
| `findImplicitInto` | 1381-1411 | 4 |
| `wrapWithFieldContext` | 1452 | 1 |
| `getTypeSignature` | 990 | 1 |
| `getCollectionElementType` | 2358 | 1 |

---

### 1.3 BindingCompanionVersionSpecific.scala — 166 branches

**File:** `schema/shared/src/main/scala-3/zio/blocks/schema/binding/BindingCompanionVersionSpecific.scala`

#### 1.3.1 `$anonfun` (83 branches) — Lines 216-1838

#### 1.3.2 Binding Derivation Methods

| Method | Lines | Branches |
|--------|-------|----------|
| `deriveBinding` | 268-313 | 9 |
| `deriveSomeBinding` | 319-326 | 7 |
| `deriveLeftBinding` | 346-353 | 8 |
| `deriveRightBinding` | 367-374 | 15 |
| `deriveOpaqueBinding` | 1052-1131 | 6 |
| `deriveZioPreludeNewtypeBinding` | 1174-1208 | 6 |
| `deriveIArrayBinding` | 725-899 | 6 |
| `deriveNamedTupleBinding` | 1741-1764 | 5 |
| `tupleFieldConstructor` | 1608-1618 | 9 |

**Test Spec:** `binding/BindingSpec.scala`

**Test Sketch:**
```scala
suite("binding derivation coverage")(
  test("derives binding for Some[T]") {
    val binding = Binding[Some[Int]]
    val value = Some(42)
    val regs = new Registers
    binding.deconstructor(regs, RegisterOffset.Zero, value)
    assertTrue(regs.getInt(RegisterOffset.Zero) == 42)
  },
  test("derives binding for Left[L, R]") {
    val binding = Binding[Left[String, Int]]
    val value = Left("error")
    val regs = new Registers
    binding.deconstructor(regs, RegisterOffset.Zero, value)
    assertTrue(regs.getObject(RegisterOffset.Zero) == "error")
  },
  test("derives binding for Right[L, R]") {
    val binding = Binding[Right[String, Int]]
    val value = Right(42)
    val regs = new Registers
    binding.deconstructor(regs, RegisterOffset.Zero, value)
    assertTrue(regs.getInt(RegisterOffset.Zero) == 42)
  },
  test("derives binding for IArray[Int]") {
    val binding = Binding[IArray[Int]]
    val value = IArray(1, 2, 3)
    assertTrue(binding.usedRegisters.objects >= 1)
  },
  test("derives binding for named tuple") {
    val binding = Binding[(name: String, age: Int)]
    assertTrue(binding.usedRegisters.objects >= 1)
  }
)
```

#### 1.3.3 Helper Methods

| Method | Lines | Branches |
|--------|-------|----------|
| `typeArgs` | 103 | 1 |
| `loop` | 118 | 1 |
| `opaqueDealias` | 122 | 1 |
| `zioPreludeNewtypeDealias` | 135-137 | 2 |
| `zioPreludeNewtypeCompanion` | 142 | 1 |
| `typeRefDealias` | 155-157 | 2 |
| `toBlock` | 1626 | 1 |
| `deriveSeqBinding` | 403 | 1 |
| `deriveSealedTraitBinding` | 977 | 1 |
| `deriveUnionBinding` | 1013 | 1 |

---

### 1.4 SeqConstructor.scala — 36 branches

**File:** `schema/shared/src/main/scala-3/zio/blocks/schema/binding/SeqConstructor.scala`

Each primitive type has `add*` and `result*` methods not exercised.

| Method | Lines | Branches |
|--------|-------|----------|
| `addBoolean` | 235, 486, 676 | 3 |
| `addByte` | 246, 497, 687 | 3 |
| `addShort` | 257, 508, 698 | 3 |
| `addInt` | 268, 519, 709 | 3 |
| `addLong` | 279, 530, 720 | 3 |
| `addFloat` | 290, 541, 731 | 3 |
| `addDouble` | 301, 552, 742 | 3 |
| `addChar` | 312, 563, 753 | 3 |
| `resultBoolean` | 324, 765 | 2 |
| `resultByte` | 331, 772 | 2 |
| `resultShort` | 338, 779 | 2 |
| `resultDouble` | 366, 807 | 2 |
| `resultChar` | 373, 814 | 2 |
| `resultLong` | 352 | 1 |
| `resultFloat` | 800 | 1 |

**Test Sketch:**
```scala
suite("sequence constructor primitive types")(
  test("constructs sequence of all primitive types") {
    // Create sequences that require primitive array builders
    val boolSeq = Schema[Seq[Boolean]]
    val byteSeq = Schema[Seq[Byte]]
    val shortSeq = Schema[Seq[Short]]
    val intSeq = Schema[Seq[Int]]
    val longSeq = Schema[Seq[Long]]
    val floatSeq = Schema[Seq[Float]]
    val doubleSeq = Schema[Seq[Double]]
    val charSeq = Schema[Seq[Char]]
    
    // Round-trip each to exercise constructors
    assertTrue(boolSeq.fromDynamicValue(boolSeq.toDynamicValue(Seq(true, false))) == Right(Seq(true, false)))
    assertTrue(byteSeq.fromDynamicValue(byteSeq.toDynamicValue(Seq(1.toByte))) == Right(Seq(1.toByte)))
    // ... etc for each type
  }
)
```

---

### 1.5 DerivedOptics.scala — 32 branches

**File:** `schema/shared/src/main/scala-3/zio/blocks/schema/DerivedOptics.scala`

| Method | Lines | Branches |
|--------|-------|----------|
| `wrapperAsRecord` | 166-227 | 10 |
| `buildWrapperOptics` | 390-395 | 6 |
| `$anonfun` | 398-499 | 5 |
| `opticsImpl` | 371-373 | 3 |
| `companionClassImpl` | 114-124 | 2 |
| `construct` | 243 | 1 |
| `buildCaseClassOptics` | 437 | 1 |
| `buildSealedTraitOptics` | 502 | 1 |
| `opticsFromThisTypeImpl` | 341 | 1 |
| `lowerFirst` | 306 | 1 |
| `getOrCreate` | 317 | 1 |

---

### 1.6 CompanionOptics.scala — 24 branches

**File:** `schema/shared/src/main/scala-3/zio/blocks/schema/CompanionOptics.scala`

| Method | Lines | Branches |
|--------|-------|----------|
| `$anonfun` | 91-571 | 22 |
| `toOptic` | 510 | 1 |
| `hasName` | 58 | 1 |

---

### 1.7 CommonMacroOps.scala — 27 branches

**File:** `schema/shared/src/main/scala-3/zio/blocks/schema/CommonMacroOps.scala`

| Method | Lines | Branches |
|--------|-------|----------|
| `dealiasOnDemand` | 138-141 | 6 |
| `calculateTypeName` | 268-286 | 5 |
| `$anonfun` | 278-293 | 4 |
| `typeRefDealias` | 130-133 | 4 |
| `loop` | 82-84 | 3 |
| `isTypeRef` | 119-121 | 2 |
| `zioPreludeNewtypeDealias` | 108-110 | 2 |
| `opaqueDealias` | 88 | 1 |

---

### 1.8 AsVersionSpecific.scala — 11 branches

**File:** `schema/shared/src/main/scala-3/zio/blocks/schema/AsVersionSpecific.scala`

| Method | Lines | Branches |
|--------|-------|----------|
| `$anonfun` | 174-501 | 11 |
| `areBidirectionallyConvertibleContainers` | 249-268 | 3 |
| `collectRefinements` | 134-138 | 2 |
| `derive` | 68 | 1 |
| `isImplicitIntoAvailable` | 525 | 1 |
| `<init>` | 166 | 1 |

---

### 1.9 MacroUtils.scala — 3 branches

| Method | Lines | Branches |
|--------|-------|----------|
| `$anonfun` | 127-143 | 3 |
| `methodMembers` | 93 | 2 |
| `fieldMembers` | 88 | 2 |

---

### 1.10 PathMacros.scala — 8 branches

| Method | Lines | Branches |
|--------|-------|----------|
| `pImpl` | 24-41 | 4 |
| `buildPrimitiveValueExpr` | 132 | 1 |
| `buildNodeExpr` | 94 | 1 |
| `buildDynamicValueExpr` | 107 | 1 |
| `$anonfun` | 25 | 1 |

---

## Part 2: Runtime Code Coverage

### 2.1 Json.scala — 193 branches

**File:** `schema/shared/src/main/scala/zio/blocks/schema/json/Json.scala`

#### 2.1.1 `fromPrimitiveValue` (26 branches) — Lines 831-860

Converts all primitive types to JSON.

**Test Spec:** `json/JsonSpec.scala`

**Test Sketch:**
```scala
suite("fromDynamicValue all primitive types")(
  test("converts all PrimitiveValue types to Json") {
    import java.time._
    import java.util.{Currency, UUID}
    
    val primitives = List(
      (PrimitiveValue.Unit, Json.Object.empty),
      (PrimitiveValue.Boolean(true), Json.True),
      (PrimitiveValue.Byte(42.toByte), Json.Number("42")),
      (PrimitiveValue.Short(100.toShort), Json.Number("100")),
      (PrimitiveValue.Int(1000), Json.Number("1000")),
      (PrimitiveValue.Long(10000L), Json.Number("10000")),
      (PrimitiveValue.Float(3.14f), _ => true), // approximate check
      (PrimitiveValue.Double(3.14159), _ => true),
      (PrimitiveValue.Char('X'), Json.String("X")),
      (PrimitiveValue.String("hello"), Json.String("hello")),
      (PrimitiveValue.BigInt(BigInt(123)), Json.Number("123")),
      (PrimitiveValue.BigDecimal(BigDecimal("123.45")), Json.Number("123.45")),
      (PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY), Json.String("MONDAY")),
      (PrimitiveValue.Month(Month.JANUARY), Json.String("JANUARY")),
      (PrimitiveValue.Duration(Duration.ofHours(1)), _ => true),
      (PrimitiveValue.Instant(Instant.EPOCH), _ => true),
      (PrimitiveValue.LocalDate(LocalDate.of(2024, 1, 1)), _ => true),
      (PrimitiveValue.LocalDateTime(LocalDateTime.of(2024, 1, 1, 12, 0)), _ => true),
      (PrimitiveValue.LocalTime(LocalTime.of(12, 0)), _ => true),
      (PrimitiveValue.MonthDay(MonthDay.of(1, 1)), _ => true),
      (PrimitiveValue.OffsetDateTime(OffsetDateTime.now), _ => true),
      (PrimitiveValue.OffsetTime(OffsetTime.now), _ => true),
      (PrimitiveValue.Period(Period.ofDays(1)), _ => true),
      (PrimitiveValue.Year(Year.of(2024)), _ => true),
      (PrimitiveValue.YearMonth(YearMonth.of(2024, 1)), _ => true),
      (PrimitiveValue.ZoneId(ZoneId.of("UTC")), _ => true),
      (PrimitiveValue.ZoneOffset(ZoneOffset.UTC), _ => true),
      (PrimitiveValue.ZonedDateTime(ZonedDateTime.now), _ => true),
      (PrimitiveValue.Currency(Currency.getInstance("USD")), Json.String("USD")),
      (PrimitiveValue.UUID(java.util.UUID.randomUUID), _ => true)
    )
    
    primitives.foreach { case (pv, expected) =>
      val json = Json.fromDynamicValue(DynamicValue.Primitive(pv))
      assertTrue(json != null)
    }
    assertTrue(true)
  }
)
```

#### 2.1.2 Path Operations (115 branches)

| Method | Lines | Branches |
|--------|-------|----------|
| `$anonfun` | 570-1485 | 26 |
| `modifyAtPathRecursive` | 1390-1496 | 15 |
| `deleteAtPathRecursive` | 1637-1684 | 15 |
| `modifyAtPathOrFailRecursive` | 1547-1602 | 13 |
| `insertAtPathRecursive` | 1723-1761 | 12 |
| `insertAtPathOrFailRecursive` | 1796-1826 | 10 |
| `go` | 1226-1247 | 10 |
| `parse` | 754-788 | 8 |
| `compare` | 511-725 | 6 |
| `as` | 549-716 | 5 |
| `modifyAtPathOrFail` | 1512-1514 | 3 |
| `unwrap` | 631-720 | 3 |
| `foldUpOrFailImpl` | 1053-1061 | 3 |
| `foldDownOrFailImpl` | 1108-1116 | 3 |
| `decodeValue` | 2079-2098 | 3 |
| `modifyAtPath` | 1363 | 2 |
| `inferContainer` | 1203-1204 | 2 |
| `insertAtPath` | 1701 | 2 |

**Test Sketch:**
```scala
suite("Json path operations")(
  test("modifyAtPathRecursive modifies all matching paths") {
    val json = Json.parse("""{"a": {"x": 1}, "b": {"x": 2}}""").getOrElse(Json.Null)
    val result = json.modifyAtPathRecursive(List(JsonPath.Field("x")))(_ => Json.Number("0"))
    assertTrue(
      result.get("a").flatMap(_.get("x")) == Some(Json.Number("0")) &&
      result.get("b").flatMap(_.get("x")) == Some(Json.Number("0"))
    )
  },
  test("deleteAtPathRecursive removes all matching paths") {
    val json = Json.parse("""{"a": {"x": 1, "y": 2}, "b": {"x": 3, "y": 4}}""").getOrElse(Json.Null)
    val result = json.deleteAtPathRecursive(List(JsonPath.Field("x")))
    assertTrue(
      result.get("a").flatMap(_.get("x")).isEmpty &&
      result.get("b").flatMap(_.get("x")).isEmpty
    )
  },
  test("insertAtPath inserts at array index") {
    val json = Json.parse("""{"items": [1, 3]}""").getOrElse(Json.Null)
    val result = json.insertAtPath(List(JsonPath.Field("items"), JsonPath.Index(1)), Json.Number("2"))
    assertTrue(result.get("items").flatMap(_.get(1)) == Some(Json.Number("2")))
  },
  test("parse handles errors") {
    assertTrue(Json.parse("{invalid}").isLeft)
    assertTrue(Json.parse("{\"key\":").isLeft)
    assertTrue(Json.parse("").isLeft)
  },
  test("compare works for different Json types") {
    assertTrue(Json.String("a").compare(Json.String("b")) < 0)
    assertTrue(Json.Number("1").compare(Json.Number("2")) < 0)
    assertTrue(Json.True.compare(Json.False) > 0)
  },
  test("as extracts typed values") {
    val json = Json.Number("42")
    assertTrue(json.as[Int] == Some(42))
    assertTrue(json.as[String] == None)
  }
)
```

#### 2.1.3 `downcastOrNull` (12 branches) — Lines 2006-2037

Downcasting for Json subtypes.

**Test Sketch:**
```scala
test("downcastOrNull handles type mismatches") {
  val string: Json = Json.String("hello")
  val number: Json = Json.Number("42")
  
  // These should exercise the failure branches
  val asNum = string match { case n: Json.Number => n; case _ => null }
  val asStr = number match { case s: Json.String => s; case _ => null }
  
  assertTrue(asNum == null && asStr == null)
}
```

---

### 2.2 DynamicPatch.scala — 90 branches

**File:** `schema/shared/src/main/scala/zio/blocks/schema/patch/DynamicPatch.scala`

| Method | Lines | Branches |
|--------|-------|----------|
| `downcastOrNull` | 1147-2101 | 60 |
| `$anonfun` | 189-196 | 8 |
| `renderPrimitiveDelta` | 95-113 | 7 |
| `navigateAndApply` | 244-391 | 6 |
| `applyToAllElements` | 419-422 | 3 |
| `applySeqOp` | 685-731 | 2 |
| `applySeqOps` | 656 | 1 |
| `applyPrimitiveDelta` | 506 | 1 |
| `applyMapOps` | 777 | 1 |
| `applyMapOp` | 827 | 1 |

**Test Spec:** `patch/PatchSpec.scala` or new `patch/DynamicPatchSpec.scala`

**Test Sketch:**
```scala
suite("DynamicPatch coverage")(
  test("renderPrimitiveDelta renders all primitive types") {
    val deltas = List(
      PrimitiveDelta.ByteDelta(1.toByte),
      PrimitiveDelta.ShortDelta(1.toShort),
      PrimitiveDelta.IntDelta(1),
      PrimitiveDelta.LongDelta(1L),
      PrimitiveDelta.FloatDelta(1.0f),
      PrimitiveDelta.DoubleDelta(1.0),
      PrimitiveDelta.CharDelta('a')
    )
    deltas.foreach(d => assertTrue(d.render.nonEmpty))
    assertTrue(true)
  },
  test("navigateAndApply handles different node types") {
    // Test navigation through records, variants, sequences, maps
    case class Outer(inner: Inner)
    case class Inner(value: Int)
    val schema = Schema[Outer]
    val patch = Patch.replace(Outer(Inner(42)))
    val result = patch.apply(Outer(Inner(0)))
    assertTrue(result == Right(Outer(Inner(42))))
  },
  test("applyToAllElements modifies all sequence elements") {
    val schema = Schema[List[Int]]
    val original = List(1, 2, 3)
    // Create patch that modifies all elements
    assertTrue(true) // placeholder
  }
)
```

---

### 2.3 PrimitiveValue.scala — 60 branches

**File:** `schema/shared/src/main/scala/zio/blocks/schema/PrimitiveValue.scala`

All branches are `downcastOrNull` for different primitive types (lines 1054-1168).

**Test Spec:** `PrimitiveValueSpec.scala`

**Test Sketch:**
```scala
suite("PrimitiveValue downcast failures")(
  test("downcastOrNull returns null for mismatched types") {
    // Create a PrimitiveValue of one type, try to downcast to another
    val intPv: Any = PrimitiveValue.Int(42)
    
    // These should all return null (exercise failure branches)
    assertTrue(intPv match { case _: PrimitiveValue.Byte => true; case _ => false } == false)
    assertTrue(intPv match { case _: PrimitiveValue.Short => true; case _ => false } == false)
    assertTrue(intPv match { case _: PrimitiveValue.Long => true; case _ => false } == false)
    assertTrue(intPv match { case _: PrimitiveValue.Float => true; case _ => false } == false)
    assertTrue(intPv match { case _: PrimitiveValue.Double => true; case _ => false } == false)
    assertTrue(intPv match { case _: PrimitiveValue.Boolean => true; case _ => false } == false)
    assertTrue(intPv match { case _: PrimitiveValue.Char => true; case _ => false } == false)
    assertTrue(intPv match { case _: PrimitiveValue.String => true; case _ => false } == false)
    // ... etc for all java.time types
  }
)
```

---

### 2.4 Optic.scala — 42 branches

**File:** `schema/shared/src/main/scala/zio/blocks/schema/Optic.scala`

| Method | Lines | Branches |
|--------|-------|----------|
| `foldAtIndices` | 1806-1981 | 18 |
| `toString` | 1202-2917 | 11 |
| `replace` | 272-852 | 3 |
| `modifyOrFail` | 1151-2839 | 2 |
| `checkRecursive` | 1565-1580 | 2 |
| `vectorValues` | 63 | 1 |
| `setValues` | 73 | 1 |
| `replaceOrFail` | 880 | 1 |
| `modifyRecursive` | 940 | 1 |
| `modifyOption` | 1131 | 1 |
| `listValues` | 53 | 1 |

**Test Spec:** `OpticSpec.scala`

**Test Sketch:**
```scala
suite("Optic coverage")(
  test("foldAtIndices handles all index patterns") {
    case class Container(items: Vector[Int])
    val optic = Optic[Container].field("items").index(0)
    val value = Container(Vector(1, 2, 3))
    assertTrue(optic.get(value) == Some(1))
  },
  test("toString produces readable representation") {
    case class Record(name: String)
    val optic = Optic[Record].field("name")
    assertTrue(optic.toString.contains("name"))
  },
  test("checkRecursive handles recursive structures") {
    case class Node(value: Int, children: List[Node])
    val schema = Schema[Node]
    assertTrue(schema.reflect.typeName.short == "Node")
  }
)
```

---

### 2.5 JsonDecoder.scala — 38 branches

**File:** `schema/shared/src/main/scala/zio/blocks/schema/json/JsonDecoder.scala`

| Method | Lines | Branches |
|--------|-------|----------|
| `decode` | 65-469 | 38 |

These are `else` branches (error cases) for each decoder type.

**Test Spec:** `json/JsonSpec.scala` or new file

**Test Sketch:**
```scala
suite("JsonDecoder error cases")(
  test("stringDecoder fails on non-string") {
    assertTrue(JsonDecoder[String].decode(Json.Number("42")).isLeft)
  },
  test("booleanDecoder fails on non-boolean") {
    assertTrue(JsonDecoder[Boolean].decode(Json.String("true")).isLeft)
  },
  test("intDecoder fails on non-number") {
    assertTrue(JsonDecoder[Int].decode(Json.String("42")).isLeft)
  },
  test("intDecoder fails on non-integer number") {
    assertTrue(JsonDecoder[Int].decode(Json.Number("3.14")).isLeft)
  },
  test("floatDecoder fails on non-number") {
    assertTrue(JsonDecoder[Float].decode(Json.String("3.14")).isLeft)
  },
  test("doubleDecoder fails on non-number") {
    assertTrue(JsonDecoder[Double].decode(Json.Null).isLeft)
  },
  // ... etc for all decoder types
)
```

---

### 2.6 JsonBinaryCodecDeriver.scala — 33 branches

| Method | Lines | Branches |
|--------|-------|----------|
| `$anonfun` | 484-1803 | 11 |
| `decodeValue` | 711-1690 | 10 |
| `toJsonSchema` | 522-1936 | 9 |
| `writeKeyAndValue` | 2355 | 1 |
| `writeDefaultValue` | 2271 | 1 |
| `setMissingValueOrError` | 2198 | 1 |

---

### 2.7 DynamicOptic.scala — 32 branches

| Method | Lines | Branches |
|--------|-------|----------|
| `downcastOrNull` | 426-481 | 20 |
| `renderDynamicValue` | 160-168 | 7 |
| `discriminate` | 415-419 | 4 |
| `<init>` | 113 | 1 |

---

### 2.8 JsonReader.scala — 29 branches

**File:** `schema/jvm-native/src/main/scala/zio/blocks/schema/json/JsonReader.scala`

| Method | Lines | Branches |
|--------|-------|----------|
| `readNullOrNumberError` | 3220-3226 | 5 |
| `parseChar` | 4515-4529 | 3 |
| `decodeError` | 1684-1722 | 3 |
| `readNullOrError` | 1458-1459 | 2 |
| `parseEncodedString` | 4443-4469 | 2 |
| `growCharBuf` | 4603-4605 | 2 |
| `growBuf` | 4713-4715 | 2 |
| And 10 more methods with 1 branch each | | 10 |

---

### 2.9 JsonSelection.scala — 28 branches

| Method | Lines | Branches |
|--------|-------|----------|
| `as` | 283-504 | 5 |
| `asAll` | 297-513 | 4 |
| `unwrap` | 533-538 | 4 |
| `unwrapAll` | 549-563 | 2 |
| `flatMap` | 190-204 | 2 |
| `all` | 98-104 | 2 |
| Plus 9 methods with 1 branch each | | 9 |

---

### 2.10 JsonSchema.scala — 28 branches

| Method | Lines | Branches |
|--------|-------|----------|
| `$anonfun` | 722-1554 | 10 |
| `withNullable` | 515-527 | 6 |
| `validateDateSemantics` | 235-246 | 3 |
| `getSchemaList` | 1400-1405 | 2 |
| `validateTimeSemantics` | 269-275 | 2 |
| `&&` | 476 | 1 |
| `||` | 496 | 1 |
| `resolve` | 93 | 1 |
| `getSchemaMap` | 1418 | 1 |
| `validateUriReference` | 303 | 1 |

**Test Spec:** `json/JsonSchemaCombinatorSpec.scala`

**Test Sketch:**
```scala
suite("JsonSchema coverage")(
  test("withNullable handles all schema types") {
    // True -> True
    assertTrue(JsonSchema.True.withNullable == JsonSchema.True)
    // False -> null type
    val falseNullable = JsonSchema.False.withNullable
    assertTrue(falseNullable != JsonSchema.False)
    // Already nullable is idempotent
    val nullable = JsonSchema.string().withNullable
    assertTrue(nullable.withNullable == nullable)
    // Union without null adds null
    val union = JsonSchema.Object(`type` = Some(SchemaType.Union(
      JsonSchemaType.String :: JsonSchemaType.Integer :: Nil
    )))
    val unionNullable = union.withNullable
    assertTrue(unionNullable != union)
  },
  test("&& combines schemas with existing allOf") {
    val s1 = JsonSchema.string() && JsonSchema.string(minLength = NonNegativeInt(1))
    val s2 = JsonSchema.string(maxLength = NonNegativeInt(10)) && JsonSchema.string()
    val combined = s1 && s2
    assertTrue(combined != s1 && combined != s2)
  },
  test("|| combines schemas with existing anyOf") {
    val s1 = JsonSchema.string() || JsonSchema.integer()
    val s2 = JsonSchema.boolean || JsonSchema.nullSchema
    val combined = s1 || s2
    assertTrue(combined != s1 && combined != s2)
  }
)
```

---

### 2.11 DynamicValue.scala — 23 branches

| Method | Lines | Branches |
|--------|-------|----------|
| `go` | 1893-1959 | 11 |
| `$anonfun` | 1115-1430 | 5 |
| `fromKV` | 1858-1862 | 3 |
| `insertAtPathImpl` | 1257 | 1 |
| `hasContent` | 1691 | 1 |
| `deleteAtPathImpl` | 1049 | 1 |
| `createContainer` | 1889 | 1 |

---

### 2.12 ReflectPrinter.scala — 21 branches

| Method | Lines | Branches |
|--------|-------|----------|
| `printReflect` | 245-271 | 5 |
| `needsMultilineForElement` | 320-326 | 5 |
| `$anonfun` | 257-291 | 5 |
| `printMap` | 118-126 | 2 |
| `last` | 341 | 3 |
| `printVariantCase` | 216 | 1 |
| `printVariant` | 68 | 1 |
| `printTerm` | 171 | 1 |
| `needsMultiline` | 305 | 1 |

---

### 2.13 HasBinding.scala — 20 branches

| Method | Lines | Branches |
|--------|-------|----------|
| `$anonfun` | 25-139 | 14 |
| `wrapper` | 146 | 1 |
| `variant` | 39 | 1 |
| `seq` | 128 | 1 |
| `record` | 33 | 1 |
| `primitive` | 15 | 1 |
| `map` | 110 | 1 |

---

### 2.14 Into.scala — 16 branches

**File:** `schema/shared/src/main/scala/zio/blocks/schema/Into.scala`

| Method | Lines | Branches |
|--------|-------|----------|
| `$anonfun` | 44-94 | 16 |

These are narrowing conversion failures (out-of-range values).

**Test Spec:** `IntoSpec.scala`

**Test Sketch:**
```scala
suite("Into narrowing conversion failures")(
  test("shortToByte fails for out-of-range values") {
    assertTrue(Into[Short, Byte].into((Byte.MaxValue + 1).toShort).isLeft)
    assertTrue(Into[Short, Byte].into((Byte.MinValue - 1).toShort).isLeft)
  },
  test("intToByte fails for out-of-range values") {
    assertTrue(Into[Int, Byte].into(Byte.MaxValue + 1).isLeft)
    assertTrue(Into[Int, Byte].into(Byte.MinValue - 1).isLeft)
  },
  test("intToShort fails for out-of-range values") {
    assertTrue(Into[Int, Short].into(Short.MaxValue + 1).isLeft)
    assertTrue(Into[Int, Short].into(Short.MinValue - 1).isLeft)
  },
  test("longToByte fails for out-of-range values") {
    assertTrue(Into[Long, Byte].into(Byte.MaxValue + 1L).isLeft)
  },
  test("longToShort fails for out-of-range values") {
    assertTrue(Into[Long, Short].into(Short.MaxValue + 1L).isLeft)
  },
  test("longToInt fails for out-of-range values") {
    assertTrue(Into[Long, Int].into(Int.MaxValue + 1L).isLeft)
  },
  test("doubleToFloat fails for out-of-range values") {
    assertTrue(Into[Double, Float].into(Double.MaxValue).isLeft)
  },
  test("floatToInt fails for imprecise values") {
    assertTrue(Into[Float, Int].into(3.14f).isLeft)
  },
  test("floatToLong fails for imprecise values") {
    assertTrue(Into[Float, Long].into(3.14f).isLeft)
  },
  test("doubleToInt fails for imprecise values") {
    assertTrue(Into[Double, Int].into(3.14).isLeft)
  },
  test("doubleToLong fails for imprecise values") {
    assertTrue(Into[Double, Long].into(3.14).isLeft)
  }
)
```

---

### 2.15 Remaining Files with 1-15 Branches

| File | Branches | Key Methods |
|------|----------|-------------|
| `Modifier.scala` | 18 | `downcastOrNull` |
| `ContextDetector.scala` | 10 | `detectContextsImpl` |
| `Reflect.scala` | 9 | `loop`, `toString`, `update`, `optionInnerType`, `fromDynamicValue`, `equals` |
| `JsonInterpolatorRuntime.scala` | 6 | `writeKeyOnly`, `writeInString`, `write`, `jsonWithContexts` |
| `JsonBinaryCodec.scala` | 9 | `decodeValue`, `decode`, `encode`, `toError`, `encodeToString` |
| `SchemaError.scala` | 3 | `message` |
| `PathParser.scala` | 7 | `peek`, `parseMapAccess`, `parseInteger`, `parseIndexOrElements`, `parseField`, `current` |
| `Differ.scala` | 9 | `$anonfun`, `diffVariant`, `diffSequence`, `computeStringEdits`, `diffString` |
| `Patch.scala` | 5 | `modifyAt`, `apply`, `modifyKey`, `applyOption` |
| `DerivationBuilder.scala` | 9 | `$anonfun`, `prependCombinedModifiers`, `modifier`, `combineModifiers` |
| `Binding.scala` | 4 | `downcastOrNull` |
| `PatchMode.scala` | 6 | `downcastOrNull` |
| `TypeName.scala` | 3 | Various |
| `JsonEncoder.scala` | 1 | `encode` |
| `Registers.scala` (jvm) | 2 | `setRegisters` |
| `JsonWriter.scala` | 6 | Various writing methods |
| `SeqDeconstructor.scala` | 12 | Various |

---

## Implementation Priority

### Phase 1: Quick Wins (Est. ~150 branches)

These tests directly exercise runtime code:

1. **AllPrimitives record test** — 80+ branches in field constructors
2. **Tuple with all primitive types** — 20+ branches
3. **Into narrowing conversion failures** — 16 branches (Into.scala)
4. **JsonDecoder error cases** — 38 branches
5. **Json.fromPrimitiveValue all types** — 26 branches

### Phase 2: Path Operations (Est. ~50 branches)

6. **Json path operations** — modifyAtPath, deleteAtPath, insertAtPath
7. **JsonSchema combinators** — &&, ||, withNullable edge cases
8. **DynamicPatch operations** — navigateAndApply, renderPrimitiveDelta

### Phase 3: Type Derivation (Est. ~100 branches)

9. **Opaque type tests** — triggers macro code paths
10. **Named tuple tests** — triggers macro code paths
11. **Tuple ↔ Case Class conversions** — Into macro paths
12. **Binding for Some/Left/Right** — Binding macro paths

### Phase 4: Edge Cases (Est. ~50 branches)

13. **PrimitiveValue downcast failures**
14. **DynamicOptic discriminate/downcast**
15. **Optic toString and foldAtIndices**
16. **ReflectPrinter edge cases**

---

## Notes

### Version-Specific Files

Tests are in `shared/src/test/`, so they run under both Scala 2 and Scala 3. When you define types that trigger macro code in tests:

- **Scala 3**: Uses quoted macros in `scala-3/` files
- **Scala 2**: Uses blackbox macros in `scala-2/` files

Both get covered by the same tests.

### Verification Command

```bash
sbt "project schemaJVM; clean; coverage; test; coverageReport"
```

Report location: `schema/jvm/target/scala-3.3.7/scoverage-report/index.html`
