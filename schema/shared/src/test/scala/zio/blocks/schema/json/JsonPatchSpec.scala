package zio.blocks.schema.json

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.patch.{DynamicPatch, PatchMode}
import zio.test._

object JsonPatchSpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Generators
  // ─────────────────────────────────────────────────────────────────────────

  val genJsonNull: Gen[Any, Json] = Gen.const(Json.Null)

  val genJsonBoolean: Gen[Any, Json] =
    Gen.boolean.map(b => if (b) Json.True else Json.False)

  val genJsonNumber: Gen[Any, Json.Number] =
    Gen.oneOf(
      Gen.int.map(n => new Json.Number(n.toString)),
      Gen.double.map(d => new Json.Number(d.toString)),
      Gen.bigDecimal(BigDecimal(-1000000), BigDecimal(1000000)).map(bd => new Json.Number(bd.toString))
    )

  val genJsonString: Gen[Any, Json.String] =
    Gen.alphaNumericStringBounded(0, 50).map(new Json.String(_))

  def genJsonArray(depth: Int): Gen[Any, Json.Array] =
    if (depth <= 0) Gen.const(Json.Array.empty)
    else {
      Gen.listOfBounded(0, 5)(genJson(depth - 1)).map { list =>
        new Json.Array(Chunk.fromIterable(list))
      }
    }

  def genJsonObject(depth: Int): Gen[Any, Json.Object] =
    if (depth <= 0) Gen.const(Json.Object.empty)
    else {
      Gen
        .listOfBounded(0, 5)(
          for {
            key   <- Gen.alphaNumericStringBounded(1, 10)
            value <- genJson(depth - 1)
          } yield (key, value)
        )
        .map { fields =>
          new Json.Object(Chunk.fromIterable(fields))
        }
    }

  def genJson(depth: Int): Gen[Any, Json] =
    if (depth <= 0) {
      Gen.oneOf(genJsonNull, genJsonBoolean, genJsonNumber, genJsonString)
    } else {
      Gen.oneOf(
        genJsonNull,
        genJsonBoolean,
        genJsonNumber,
        genJsonString,
        genJsonArray(depth - 1),
        genJsonObject(depth - 1)
      )
    }

  val genSimpleJson: Gen[Any, Json] = genJson(2)
  val genDeepJson: Gen[Any, Json]   = genJson(3)

  def spec: Spec[TestEnvironment, Any] = suite("JsonPatchSpec")(
    monoidLawsSuite,
    diffApplyLawsSuite,
    patchModeSuite,
    operationTypeSuite,
    arrayOpSuite,
    objectOpSuite,
    stringOpSuite,
    numberDeltaSuite,
    dynamicPatchConversionSuite,
    edgeCaseSuite,
    errorCaseSuite
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Monoid Laws (L1, L2, L3)
  // ─────────────────────────────────────────────────────────────────────────

  val monoidLawsSuite: Spec[Any, Nothing] = suite("Monoid Laws for JsonPatch")(
    test("L1: left identity - (empty ++ p)(j) == p(j) for Json.Number") {
      check(genJsonNumber, genJsonNumber) { (oldJson, newJson) =>
        val patch    = JsonPatch.diff(oldJson, newJson)
        val empty    = JsonPatch.empty
        val composed = empty ++ patch

        val result1 = composed(oldJson, PatchMode.Strict)
        val result2 = patch(oldJson, PatchMode.Strict)

        assertTrue(result1 == result2)
      }
    },
    test("L1: left identity - (empty ++ p)(j) == p(j) for Json.String") {
      check(genJsonString, genJsonString) { (oldJson, newJson) =>
        val patch    = JsonPatch.diff(oldJson, newJson)
        val empty    = JsonPatch.empty
        val composed = empty ++ patch

        val result1 = composed(oldJson, PatchMode.Strict)
        val result2 = patch(oldJson, PatchMode.Strict)

        assertTrue(result1 == result2)
      }
    },
    test("L1: left identity - (empty ++ p)(j) == p(j) for Json.Object") {
      check(genJsonObject(2), genJsonObject(2)) { (oldJson, newJson) =>
        val patch    = JsonPatch.diff(oldJson, newJson)
        val empty    = JsonPatch.empty
        val composed = empty ++ patch

        val result1 = composed(oldJson, PatchMode.Strict)
        val result2 = patch(oldJson, PatchMode.Strict)

        assertTrue(result1 == result2)
      }
    },
    test("L1: left identity - (empty ++ p)(j) == p(j) for Json.Array") {
      check(genJsonArray(2), genJsonArray(2)) { (oldJson, newJson) =>
        val patch    = JsonPatch.diff(oldJson, newJson)
        val empty    = JsonPatch.empty
        val composed = empty ++ patch

        val result1 = composed(oldJson, PatchMode.Strict)
        val result2 = patch(oldJson, PatchMode.Strict)

        assertTrue(result1 == result2)
      }
    },
    test("L2: right identity - (p ++ empty)(j) == p(j) for Json.Number") {
      check(genJsonNumber, genJsonNumber) { (oldJson, newJson) =>
        val patch    = JsonPatch.diff(oldJson, newJson)
        val empty    = JsonPatch.empty
        val composed = patch ++ empty

        val result1 = composed(oldJson, PatchMode.Strict)
        val result2 = patch(oldJson, PatchMode.Strict)

        assertTrue(result1 == result2)
      }
    },
    test("L2: right identity - (p ++ empty)(j) == p(j) for Json.Object") {
      check(genJsonObject(2), genJsonObject(2)) { (oldJson, newJson) =>
        val patch    = JsonPatch.diff(oldJson, newJson)
        val empty    = JsonPatch.empty
        val composed = patch ++ empty

        val result1 = composed(oldJson, PatchMode.Strict)
        val result2 = patch(oldJson, PatchMode.Strict)

        assertTrue(result1 == result2)
      }
    },
    test("L3: associativity - ((p1 ++ p2) ++ p3)(j) == (p1 ++ (p2 ++ p3))(j) for Json.Number") {
      check(genJsonNumber, genJsonNumber, genJsonNumber, genJsonNumber) { (j0, j1, j2, j3) =>
        val p1 = JsonPatch.diff(j0, j1)
        val p2 = JsonPatch.diff(j1, j2)
        val p3 = JsonPatch.diff(j2, j3)

        val left  = (p1 ++ p2) ++ p3
        val right = p1 ++ (p2 ++ p3)

        val result1 = left(j0, PatchMode.Strict)
        val result2 = right(j0, PatchMode.Strict)

        assertTrue(result1 == result2)
      }
    },
    test("L3: associativity - ((p1 ++ p2) ++ p3)(j) == (p1 ++ (p2 ++ p3))(j) for Json.Object") {
      check(genJsonObject(2), genJsonObject(2), genJsonObject(2), genJsonObject(2)) { (j0, j1, j2, j3) =>
        val p1 = JsonPatch.diff(j0, j1)
        val p2 = JsonPatch.diff(j1, j2)
        val p3 = JsonPatch.diff(j2, j3)

        val left  = (p1 ++ p2) ++ p3
        val right = p1 ++ (p2 ++ p3)

        val result1 = left(j0, PatchMode.Strict)
        val result2 = right(j0, PatchMode.Strict)

        assertTrue(result1 == result2)
      }
    },
    test("L3: associativity for mixed types") {
      check(genSimpleJson, genSimpleJson, genSimpleJson, genSimpleJson) { (j0, j1, j2, j3) =>
        val p1 = JsonPatch.diff(j0, j1)
        val p2 = JsonPatch.diff(j1, j2)
        val p3 = JsonPatch.diff(j2, j3)

        val left  = (p1 ++ p2) ++ p3
        val right = p1 ++ (p2 ++ p3)

        val result1 = left(j0, PatchMode.Strict)
        val result2 = right(j0, PatchMode.Strict)

        assertTrue(result1 == result2)
      }
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Diff/Apply Laws (L4, L5, L6)
  // ─────────────────────────────────────────────────────────────────────────

  val diffApplyLawsSuite: Spec[Any, Nothing] = suite("Diff/Apply Laws")(
    test("L4: roundtrip - diff(a, b)(a, Strict) == Right(b) for Json.Number") {
      check(genJsonNumber, genJsonNumber) { (source, target) =>
        val patch  = JsonPatch.diff(source, target)
        val result = patch(source, PatchMode.Strict)
        assertTrue(result == new Right(target))
      }
    },
    test("L4: roundtrip - diff(a, b)(a, Strict) == Right(b) for Json.String") {
      check(genJsonString, genJsonString) { (source, target) =>
        val patch  = JsonPatch.diff(source, target)
        val result = patch(source, PatchMode.Strict)
        assertTrue(result == new Right(target))
      }
    },
    test("L4: roundtrip - diff(a, b)(a, Strict) == Right(b) for Json.Array") {
      check(genJsonArray(2), genJsonArray(2)) { (source, target) =>
        val patch  = JsonPatch.diff(source, target)
        val result = patch(source, PatchMode.Strict)
        assertTrue(result == new Right(target))
      }
    },
    test("L4: roundtrip - diff(a, b)(a, Strict) == Right(b) for Json.Object") {
      check(genJsonObject(2), genJsonObject(2)) { (source, target) =>
        val patch  = JsonPatch.diff(source, target)
        val result = patch(source, PatchMode.Strict)
        assertTrue(result == new Right(target))
      }
    },
    test("L4: roundtrip - diff(a, b)(a, Strict) == Right(b) for any Json") {
      check(genSimpleJson, genSimpleJson) { (source, target) =>
        val patch  = JsonPatch.diff(source, target)
        val result = patch(source, PatchMode.Strict)
        assertTrue(result == new Right(target))
      }
    },
    test("L5: identity diff - diff(j, j).isEmpty for any Json") {
      check(genSimpleJson) { json =>
        val patch = JsonPatch.diff(json, json)
        assertTrue(patch.isEmpty)
      }
    },
    test("L5: identity diff - diff(j, j).isEmpty for Json.Object") {
      check(genJsonObject(3)) { json =>
        val patch = JsonPatch.diff(json, json)
        assertTrue(patch.isEmpty)
      }
    },
    test("L6: diff composition - (diff(a, b) ++ diff(b, c))(a) == Right(c)") {
      check(genJsonNumber, genJsonNumber, genJsonNumber) { (a, b, c) =>
        val p1       = JsonPatch.diff(a, b)
        val p2       = JsonPatch.diff(b, c)
        val composed = p1 ++ p2
        val result   = composed(a, PatchMode.Strict)
        assertTrue(result == new Right(c))
      }
    },
    test("L6: diff composition for Json.String") {
      check(genJsonString, genJsonString, genJsonString) { (a, b, c) =>
        val p1       = JsonPatch.diff(a, b)
        val p2       = JsonPatch.diff(b, c)
        val composed = p1 ++ p2
        val result   = composed(a, PatchMode.Strict)
        assertTrue(result == new Right(c))
      }
    },
    test("L6: diff composition for Json.Object") {
      check(genJsonObject(2), genJsonObject(2), genJsonObject(2)) { (a, b, c) =>
        val p1       = JsonPatch.diff(a, b)
        val p2       = JsonPatch.diff(b, c)
        val composed = p1 ++ p2
        val result   = composed(a, PatchMode.Strict)
        assertTrue(result == new Right(c))
      }
    },
    test("L6: diff composition for mixed types") {
      check(genSimpleJson, genSimpleJson, genSimpleJson) { (a, b, c) =>
        val p1       = JsonPatch.diff(a, b)
        val p2       = JsonPatch.diff(b, c)
        val composed = p1 ++ p2
        val result   = composed(a, PatchMode.Strict)
        assertTrue(result == new Right(c))
      }
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // PatchMode Laws (L7, T7)
  // ─────────────────────────────────────────────────────────────────────────

  val patchModeSuite: Spec[Any, Nothing] = suite("PatchMode behavior")(
    test("L7: Lenient subsumes Strict - if p(j, Strict) == Right(r) then p(j, Lenient) == Right(r)") {
      check(genSimpleJson, genSimpleJson) { (source, target) =>
        val patch      = JsonPatch.diff(source, target)
        val strictRes  = patch(source, PatchMode.Strict)
        val lenientRes = patch(source, PatchMode.Lenient)
        assertTrue(
          strictRes.isRight && lenientRes == strictRes
        )
      }
    },
    test("Strict mode fails on missing field") {
      val json   = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Chunk(JsonPatch.ObjectOp.Remove("nonexistent"))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("Lenient mode skips missing field removal") {
      val json   = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Chunk(JsonPatch.ObjectOp.Remove("nonexistent"))))
      val result = patch(json, PatchMode.Lenient)
      assertTrue(result == new Right(json))
    },
    test("Clobber mode overwrites existing field on Add") {
      val json   = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Chunk(JsonPatch.ObjectOp.Add("a", new Json.Number("2")))))
      val result = patch(json, PatchMode.Clobber)
      assertTrue(result == new Right(new Json.Object(Chunk(("a", new Json.Number("2"))))))
    },
    test("Strict mode fails on out-of-bounds array delete") {
      val json   = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2")))
      val patch  = JsonPatch.root(JsonPatch.Op.ArrayEdit(Chunk(JsonPatch.ArrayOp.Delete(10, 1))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("Clobber mode clamps out-of-bounds array insert") {
      val json  = new Json.Array(Chunk(new Json.Number("1")))
      val patch =
        JsonPatch.root(JsonPatch.Op.ArrayEdit(Chunk(JsonPatch.ArrayOp.Insert(100, Chunk(new Json.Number("2"))))))
      val result = patch(json, PatchMode.Clobber)
      assertTrue(result.isRight)
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Operation Type Tests (T2)
  // ─────────────────────────────────────────────────────────────────────────

  val operationTypeSuite: Spec[Any, Nothing] = suite("Operation types")(
    test("Set operation replaces value") {
      val json   = new Json.Number("1")
      val patch  = JsonPatch.root(JsonPatch.Op.Set(new Json.Number("5")))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.Number("5")))
    },
    test("Set operation replaces with different type") {
      val json   = new Json.Number("1")
      val patch  = JsonPatch.root(JsonPatch.Op.Set(new Json.String("hello")))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.String("hello")))
    },
    test("PrimitiveDelta applies number delta") {
      val json   = new Json.Number("10")
      val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.Number("15")))
    },
    test("ArrayEdit applies array operations") {
      val json     = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2")))
      val patch    = JsonPatch.root(JsonPatch.Op.ArrayEdit(Chunk(JsonPatch.ArrayOp.Append(Chunk(new Json.Number("3"))))))
      val result   = patch(json, PatchMode.Strict)
      val expected = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
      assertTrue(result == new Right(expected))
    },
    test("ObjectEdit applies object operations") {
      val json     = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch    = JsonPatch.root(JsonPatch.Op.ObjectEdit(Chunk(JsonPatch.ObjectOp.Add("b", new Json.Number("2")))))
      val result   = patch(json, PatchMode.Strict)
      val expected = new Json.Object(Chunk(("a", new Json.Number("1")), ("b", new Json.Number("2"))))
      assertTrue(result == new Right(expected))
    },
    test("Nested operation applies nested patch") {
      val innerPatch = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(1))))
      val json       = new Json.Number("5")
      val patch      = JsonPatch.root(JsonPatch.Op.Nested(innerPatch))
      val result     = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.Number("6")))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // ArrayOp Tests (T3)
  // ─────────────────────────────────────────────────────────────────────────

  val arrayOpSuite: Spec[Any, Nothing] = suite("ArrayOp variants")(
    test("Insert adds values at index") {
      val json  = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("3")))
      val patch =
        JsonPatch.root(JsonPatch.Op.ArrayEdit(Chunk(JsonPatch.ArrayOp.Insert(1, Chunk(new Json.Number("2"))))))
      val result   = patch(json, PatchMode.Strict)
      val expected = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
      assertTrue(result == new Right(expected))
    },
    test("Append adds values at end") {
      val json  = new Json.Array(Chunk(new Json.Number("1")))
      val patch = JsonPatch.root(
        JsonPatch.Op.ArrayEdit(Chunk(JsonPatch.ArrayOp.Append(Chunk(new Json.Number("2"), new Json.Number("3")))))
      )
      val result   = patch(json, PatchMode.Strict)
      val expected = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
      assertTrue(result == new Right(expected))
    },
    test("Delete removes elements") {
      val json     = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
      val patch    = JsonPatch.root(JsonPatch.Op.ArrayEdit(Chunk(JsonPatch.ArrayOp.Delete(1, 1))))
      val result   = patch(json, PatchMode.Strict)
      val expected = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("3")))
      assertTrue(result == new Right(expected))
    },
    test("Modify updates element at index") {
      val json     = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2")))
      val modifyOp = JsonPatch.Op.Set(new Json.Number("10"))
      val patch    = JsonPatch.root(JsonPatch.Op.ArrayEdit(Chunk(JsonPatch.ArrayOp.Modify(0, modifyOp))))
      val result   = patch(json, PatchMode.Strict)
      val expected = new Json.Array(Chunk(new Json.Number("10"), new Json.Number("2")))
      assertTrue(result == new Right(expected))
    },
    test("Multiple array operations compose correctly") {
      val json  = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
      val patch = JsonPatch.root(
        JsonPatch.Op.ArrayEdit(
          Chunk(
            JsonPatch.ArrayOp.Delete(1, 1),
            JsonPatch.ArrayOp.Append(Chunk(new Json.Number("4")))
          )
        )
      )
      val result   = patch(json, PatchMode.Strict)
      val expected = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("3"), new Json.Number("4")))
      assertTrue(result == new Right(expected))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // ObjectOp Tests (T4)
  // ─────────────────────────────────────────────────────────────────────────

  val objectOpSuite: Spec[Any, Nothing] = suite("ObjectOp variants")(
    test("Add inserts new field") {
      val json   = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Chunk(JsonPatch.ObjectOp.Add("b", new Json.Number("2")))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isRight)
      result.foreach { r =>
        val obj = r.asInstanceOf[Json.Object]
        assertTrue(obj.value.length == 2)
      }
      assertTrue(result.isRight)
    },
    test("Remove deletes existing field") {
      val json     = new Json.Object(Chunk(("a", new Json.Number("1")), ("b", new Json.Number("2"))))
      val patch    = JsonPatch.root(JsonPatch.Op.ObjectEdit(Chunk(JsonPatch.ObjectOp.Remove("a"))))
      val result   = patch(json, PatchMode.Strict)
      val expected = new Json.Object(Chunk(("b", new Json.Number("2"))))
      assertTrue(result == new Right(expected))
    },
    test("Modify updates existing field") {
      val innerPatch = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
      val json       = new Json.Object(Chunk(("x", new Json.Number("10"))))
      val patch      = JsonPatch.root(JsonPatch.Op.ObjectEdit(Chunk(JsonPatch.ObjectOp.Modify("x", innerPatch))))
      val result     = patch(json, PatchMode.Strict)
      val expected   = new Json.Object(Chunk(("x", new Json.Number("15"))))
      assertTrue(result == new Right(expected))
    },
    test("Multiple object operations compose correctly") {
      val json  = new Json.Object(Chunk(("a", new Json.Number("1")), ("b", new Json.Number("2"))))
      val patch = JsonPatch.root(
        JsonPatch.Op.ObjectEdit(
          Chunk(
            JsonPatch.ObjectOp.Remove("a"),
            JsonPatch.ObjectOp.Add("c", new Json.Number("3"))
          )
        )
      )
      val result   = patch(json, PatchMode.Strict)
      val expected = new Json.Object(Chunk(("b", new Json.Number("2")), ("c", new Json.Number("3"))))
      assertTrue(result == new Right(expected))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // StringOp Tests (T5)
  // ─────────────────────────────────────────────────────────────────────────

  val stringOpSuite: Spec[Any, Nothing] = suite("StringOp variants")(
    test("Insert adds text at index") {
      val json  = new Json.String("hello world")
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(
          JsonPatch.PrimitiveOp.StringEdit(Chunk(JsonPatch.StringOp.Insert(5, " beautiful")))
        )
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.String("hello beautiful world")))
    },
    test("Delete removes characters") {
      val json  = new Json.String("hello world")
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(
          JsonPatch.PrimitiveOp.StringEdit(Chunk(JsonPatch.StringOp.Delete(5, 6)))
        )
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.String("hello")))
    },
    test("Append adds text at end") {
      val json  = new Json.String("hello")
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(
          JsonPatch.PrimitiveOp.StringEdit(Chunk(JsonPatch.StringOp.Append(" world")))
        )
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.String("hello world")))
    },
    test("Modify replaces characters") {
      val json  = new Json.String("hello world")
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(
          JsonPatch.PrimitiveOp.StringEdit(Chunk(JsonPatch.StringOp.Modify(6, 5, "there")))
        )
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.String("hello there")))
    },
    test("Multiple string operations compose correctly") {
      val json  = new Json.String("abc")
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(
          JsonPatch.PrimitiveOp.StringEdit(
            Chunk(
              JsonPatch.StringOp.Delete(1, 1),
              JsonPatch.StringOp.Append("xyz")
            )
          )
        )
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.String("acxyz")))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // NumberDelta Tests (T6)
  // ─────────────────────────────────────────────────────────────────────────

  val numberDeltaSuite: Spec[Any, Nothing] = suite("NumberDelta behavior")(
    test("Positive delta adds to number") {
      val json   = new Json.Number("10")
      val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.Number("15")))
    },
    test("Negative delta subtracts from number") {
      val json   = new Json.Number("10")
      val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(-5))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.Number("5")))
    },
    test("Zero delta has no effect") {
      val json   = new Json.Number("10")
      val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(0))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.Number("10")))
    },
    test("Decimal delta works correctly") {
      val json   = new Json.Number("10.5")
      val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal("0.25"))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.Number("10.75")))
    },
    test("Large number delta") {
      val json   = new Json.Number("999999999999999")
      val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(1))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.Number("1000000000000000")))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // DynamicPatch Conversion Tests (T8)
  // ─────────────────────────────────────────────────────────────────────────

  val dynamicPatchConversionSuite: Spec[Any, Nothing] = suite("DynamicPatch conversion")(
    test("toDynamicPatch converts Set operation") {
      val patch   = JsonPatch.root(JsonPatch.Op.Set(new Json.Number("5")))
      val dynamic = patch.toDynamicPatch
      assertTrue(!dynamic.isEmpty)
    },
    test("toDynamicPatch converts NumberDelta") {
      val patch   = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
      val dynamic = patch.toDynamicPatch
      assertTrue(!dynamic.isEmpty)
    },
    test("toDynamicPatch/fromDynamicPatch roundtrip preserves semantics for Set") {
      val json            = new Json.String("old")
      val patch           = JsonPatch.root(JsonPatch.Op.Set(new Json.String("new")))
      val dynamic         = patch.toDynamicPatch
      val back            = JsonPatch.fromDynamicPatch(dynamic)
      val originalResult  = patch(json, PatchMode.Strict)
      val roundtripResult = back.flatMap(p => p(json, PatchMode.Strict))
      assertTrue(roundtripResult == originalResult)
    },
    test("toDynamicPatch/fromDynamicPatch roundtrip preserves semantics for NumberDelta") {
      val json            = new Json.Number("10")
      val patch           = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
      val dynamic         = patch.toDynamicPatch
      val back            = JsonPatch.fromDynamicPatch(dynamic)
      val originalResult  = patch(json, PatchMode.Strict)
      val roundtripResult = back.flatMap(p => p(json, PatchMode.Strict))
      assertTrue(roundtripResult == originalResult)
    },
    test("toDynamicPatch/fromDynamicPatch roundtrip preserves semantics for StringEdit") {
      val json  = new Json.String("hello")
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(
          JsonPatch.PrimitiveOp.StringEdit(Chunk(JsonPatch.StringOp.Append(" world")))
        )
      )
      val dynamic         = patch.toDynamicPatch
      val back            = JsonPatch.fromDynamicPatch(dynamic)
      val originalResult  = patch(json, PatchMode.Strict)
      val roundtripResult = back.flatMap(p => p(json, PatchMode.Strict))
      assertTrue(roundtripResult == originalResult)
    },
    test("toDynamicPatch/fromDynamicPatch roundtrip preserves semantics for ArrayEdit.Insert") {
      val json  = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2")))
      val patch = JsonPatch.root(
        JsonPatch.Op.ArrayEdit(Chunk(JsonPatch.ArrayOp.Insert(1, Chunk(new Json.Number("99")))))
      )
      val dynamic         = patch.toDynamicPatch
      val back            = JsonPatch.fromDynamicPatch(dynamic)
      val originalResult  = patch(json, PatchMode.Strict)
      val roundtripResult = back.flatMap(p => p(json, PatchMode.Strict))
      assertTrue(roundtripResult == originalResult)
    },
    test("toDynamicPatch/fromDynamicPatch roundtrip preserves semantics for ArrayEdit.Delete") {
      val json            = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
      val patch           = JsonPatch.root(JsonPatch.Op.ArrayEdit(Chunk(JsonPatch.ArrayOp.Delete(1, 1))))
      val dynamic         = patch.toDynamicPatch
      val back            = JsonPatch.fromDynamicPatch(dynamic)
      val originalResult  = patch(json, PatchMode.Strict)
      val roundtripResult = back.flatMap(p => p(json, PatchMode.Strict))
      assertTrue(roundtripResult == originalResult)
    },
    test("toDynamicPatch/fromDynamicPatch roundtrip preserves semantics for ObjectEdit.Add") {
      val json  = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch = JsonPatch.root(
        JsonPatch.Op.ObjectEdit(Chunk(JsonPatch.ObjectOp.Add("b", new Json.Number("2"))))
      )
      val dynamic         = patch.toDynamicPatch
      val back            = JsonPatch.fromDynamicPatch(dynamic)
      val originalResult  = patch(json, PatchMode.Strict)
      val roundtripResult = back.flatMap(p => p(json, PatchMode.Strict))
      assertTrue(roundtripResult == originalResult)
    },
    test("toDynamicPatch/fromDynamicPatch roundtrip preserves semantics for ObjectEdit.Remove") {
      val json            = new Json.Object(Chunk(("a", new Json.Number("1")), ("b", new Json.Number("2"))))
      val patch           = JsonPatch.root(JsonPatch.Op.ObjectEdit(Chunk(JsonPatch.ObjectOp.Remove("b"))))
      val dynamic         = patch.toDynamicPatch
      val back            = JsonPatch.fromDynamicPatch(dynamic)
      val originalResult  = patch(json, PatchMode.Strict)
      val roundtripResult = back.flatMap(p => p(json, PatchMode.Strict))
      assertTrue(roundtripResult == originalResult)
    },
    test("toDynamicPatch/fromDynamicPatch roundtrip preserves semantics for nested path") {
      val json = new Json.Object(
        Chunk(("outer", new Json.Object(Chunk(("inner", new Json.Number("10"))))))
      )
      val nestedPath = DynamicOptic.root.field("outer").field("inner")
      val patch      = JsonPatch(
        nestedPath,
        JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5)))
      )
      val dynamic         = patch.toDynamicPatch
      val back            = JsonPatch.fromDynamicPatch(dynamic)
      val originalResult  = patch(json, PatchMode.Strict)
      val roundtripResult = back.flatMap(p => p(json, PatchMode.Strict))
      assertTrue(roundtripResult == originalResult)
    },
    test("toDynamicPatch/fromDynamicPatch roundtrip preserves semantics for arbitrary diffs") {
      check(genSimpleJson, genSimpleJson) { (source, target) =>
        val patch           = JsonPatch.diff(source, target)
        val dynamic         = patch.toDynamicPatch
        val back            = JsonPatch.fromDynamicPatch(dynamic)
        val originalResult  = patch(source, PatchMode.Strict)
        val roundtripResult = back.flatMap(p => p(source, PatchMode.Strict))
        assertTrue(roundtripResult == originalResult)
      }
    },
    test("toDynamicPatch/fromDynamicPatch roundtrip preserves semantics for ArrayOp.Append") {
      val json            = new Json.Array(Chunk(new Json.Number("1")))
      val patch           = JsonPatch.root(JsonPatch.Op.ArrayEdit(Chunk(JsonPatch.ArrayOp.Append(Chunk(new Json.Number("2"))))))
      val dynamic         = patch.toDynamicPatch
      val back            = JsonPatch.fromDynamicPatch(dynamic)
      val originalResult  = patch(json, PatchMode.Strict)
      val roundtripResult = back.flatMap(p => p(json, PatchMode.Strict))
      assertTrue(roundtripResult == originalResult)
    },
    test("toDynamicPatch/fromDynamicPatch roundtrip preserves semantics for ArrayOp.Modify") {
      val json  = new Json.Array(Chunk(new Json.Number("10")))
      val patch = JsonPatch.root(
        JsonPatch.Op.ArrayEdit(
          Chunk(
            JsonPatch.ArrayOp.Modify(0, JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
          )
        )
      )
      val dynamic         = patch.toDynamicPatch
      val back            = JsonPatch.fromDynamicPatch(dynamic)
      val originalResult  = patch(json, PatchMode.Strict)
      val roundtripResult = back.flatMap(p => p(json, PatchMode.Strict))
      assertTrue(roundtripResult == originalResult)
    },
    test("toDynamicPatch/fromDynamicPatch roundtrip preserves semantics for ObjectOp.Modify") {
      val json        = new Json.Object(Chunk(("count", new Json.Number("10"))))
      val nestedPatch = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
      val patch       = JsonPatch.root(
        JsonPatch.Op.ObjectEdit(
          Chunk(
            JsonPatch.ObjectOp.Modify("count", nestedPatch)
          )
        )
      )
      val dynamic         = patch.toDynamicPatch
      val back            = JsonPatch.fromDynamicPatch(dynamic)
      val originalResult  = patch(json, PatchMode.Strict)
      val roundtripResult = back.flatMap(p => p(json, PatchMode.Strict))
      assertTrue(roundtripResult == originalResult)
    },
    test("toDynamicPatch/fromDynamicPatch roundtrip preserves semantics for Op.Nested") {
      val innerPatch      = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(1))))
      val json            = new Json.Number("5")
      val patch           = JsonPatch.root(JsonPatch.Op.Nested(innerPatch))
      val dynamic         = patch.toDynamicPatch
      val back            = JsonPatch.fromDynamicPatch(dynamic)
      val originalResult  = patch(json, PatchMode.Strict)
      val roundtripResult = back.flatMap(p => p(json, PatchMode.Strict))
      assertTrue(roundtripResult == originalResult)
    },
    test("empty patch conversion roundtrip") {
      val patch   = JsonPatch.empty
      val dynamic = patch.toDynamicPatch
      val back    = JsonPatch.fromDynamicPatch(dynamic)
      assertTrue(back == new Right(JsonPatch.empty))
    },
    test("fromDynamicPatch fails for temporal operations") {
      val dynamicPatch = DynamicPatch.root(
        DynamicPatch.Operation.PrimitiveDelta(
          DynamicPatch.PrimitiveOp.InstantDelta(java.time.Duration.ofHours(1))
        )
      )
      val result = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(result.isLeft)
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Edge Case Tests (T9)
  // ─────────────────────────────────────────────────────────────────────────

  val edgeCaseSuite: Spec[Any, Nothing] = suite("Edge cases")(
    test("Empty array diff produces empty patch") {
      val empty1 = Json.Array.empty
      val empty2 = Json.Array.empty
      val patch  = JsonPatch.diff(empty1, empty2)
      assertTrue(patch.isEmpty)
    },
    test("Empty object diff produces empty patch") {
      val empty1 = Json.Object.empty
      val empty2 = Json.Object.empty
      val patch  = JsonPatch.diff(empty1, empty2)
      assertTrue(patch.isEmpty)
    },
    test("Empty string diff produces empty patch") {
      val empty1 = new Json.String("")
      val empty2 = new Json.String("")
      val patch  = JsonPatch.diff(empty1, empty2)
      assertTrue(patch.isEmpty)
    },
    test("Nested structure diff") {
      val source = new Json.Object(
        Chunk(
          (
            "user",
            new Json.Object(
              Chunk(
                ("name", new Json.String("Alice")),
                ("age", new Json.Number("30"))
              )
            )
          )
        )
      )
      val target = new Json.Object(
        Chunk(
          (
            "user",
            new Json.Object(
              Chunk(
                ("name", new Json.String("Bob")),
                ("age", new Json.Number("25"))
              )
            )
          )
        )
      )
      val patch  = JsonPatch.diff(source, target)
      val result = patch(source, PatchMode.Strict)
      assertTrue(result == new Right(target))
    },
    test("Deep nested object patch") {
      val source = new Json.Object(
        Chunk(
          (
            "a",
            new Json.Object(
              Chunk(
                (
                  "b",
                  new Json.Object(
                    Chunk(
                      ("c", new Json.Number("1"))
                    )
                  )
                )
              )
            )
          )
        )
      )
      val target = new Json.Object(
        Chunk(
          (
            "a",
            new Json.Object(
              Chunk(
                (
                  "b",
                  new Json.Object(
                    Chunk(
                      ("c", new Json.Number("2"))
                    )
                  )
                )
              )
            )
          )
        )
      )
      val patch  = JsonPatch.diff(source, target)
      val result = patch(source, PatchMode.Strict)
      assertTrue(result == new Right(target))
    },
    test("Array with nested objects") {
      val source = new Json.Array(
        Chunk(
          new Json.Object(Chunk(("x", new Json.Number("1")))),
          new Json.Object(Chunk(("x", new Json.Number("2"))))
        )
      )
      val target = new Json.Array(
        Chunk(
          new Json.Object(Chunk(("x", new Json.Number("10")))),
          new Json.Object(Chunk(("x", new Json.Number("20"))))
        )
      )
      val patch  = JsonPatch.diff(source, target)
      val result = patch(source, PatchMode.Strict)
      assertTrue(result == new Right(target))
    },
    test("null to value transformation") {
      val source = Json.Null
      val target = new Json.Number("42")
      val patch  = JsonPatch.diff(source, target)
      val result = patch(source, PatchMode.Strict)
      assertTrue(result == new Right(target))
    },
    test("boolean to boolean transformation") {
      val source = Json.True
      val target = Json.False
      val patch  = JsonPatch.diff(source, target)
      val result = patch(source, PatchMode.Strict)
      assertTrue(result == new Right(target))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Error Case Tests (T10)
  // ─────────────────────────────────────────────────────────────────────────

  val errorCaseSuite: Spec[Any, Nothing] = suite("Error cases")(
    test("Type mismatch: number delta on string") {
      val json   = new Json.String("hello")
      val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("Type mismatch: string edit on number") {
      val json  = new Json.Number("42")
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(
          JsonPatch.PrimitiveOp.StringEdit(Chunk(JsonPatch.StringOp.Append("x")))
        )
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("Type mismatch: array edit on object") {
      val json   = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch  = JsonPatch.root(JsonPatch.Op.ArrayEdit(Chunk(JsonPatch.ArrayOp.Append(Chunk(new Json.Number("2"))))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("Type mismatch: object edit on array") {
      val json   = new Json.Array(Chunk(new Json.Number("1")))
      val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Chunk(JsonPatch.ObjectOp.Add("a", new Json.Number("2")))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("Out of bounds: array modify index") {
      val json  = new Json.Array(Chunk(new Json.Number("1")))
      val patch = JsonPatch.root(
        JsonPatch.Op.ArrayEdit(
          Chunk(
            JsonPatch.ArrayOp.Modify(5, JsonPatch.Op.Set(new Json.Number("10")))
          )
        )
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("Out of bounds: string insert index") {
      val json  = new Json.String("abc")
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(
          JsonPatch.PrimitiveOp.StringEdit(Chunk(JsonPatch.StringOp.Insert(100, "x")))
        )
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("Out of bounds: string delete range") {
      val json  = new Json.String("abc")
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(
          JsonPatch.PrimitiveOp.StringEdit(Chunk(JsonPatch.StringOp.Delete(0, 100)))
        )
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("Missing field in object path navigation") {
      val json  = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch = JsonPatch(
        DynamicOptic.root.field("nonexistent"),
        JsonPatch.Op.Set(new Json.Number("5"))
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("Invalid path: field navigation on non-object") {
      val json  = new Json.Number("42")
      val patch = JsonPatch(
        DynamicOptic.root.field("x"),
        JsonPatch.Op.Set(new Json.Number("5"))
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("Invalid path: index navigation on non-array") {
      val json  = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch = JsonPatch(
        DynamicOptic.root.at(0),
        JsonPatch.Op.Set(new Json.Number("5"))
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    }
  )
}
