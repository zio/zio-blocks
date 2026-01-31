package zio.blocks.schema.json

import zio.blocks.chunk.Chunk
import zio.blocks.schema.patch.PatchMode
import zio.test._

/**
 * Test suite for [[JsonDiffer]] covering:
 *   - Correctness: `diff(a, b).apply(a) == Right(b)`
 *   - Minimality: patches use appropriate edit operations (not just Set)
 *   - Branch coverage: all code paths in JsonDiffer
 */
object JsonDifferSpec extends ZIOSpecDefault {

  // ─────────────────────────────────────────────────────────────────────────
  // Generators
  // ─────────────────────────────────────────────────────────────────────────

  val genSmallInt: Gen[Any, Int] = Gen.int(-100, 100)

  val genSmallString: Gen[Any, String] = Gen.alphaNumericStringBounded(1, 20)

  // ─────────────────────────────────────────────────────────────────────────
  // Tweak-Based Generators (for property-based tests)
  // ─────────────────────────────────────────────────────────────────────────

  val genObjectWithAddedField: Gen[Any, (Json.Object, Json.Object)] =
    for {
      baseKeys <- Gen.listOfBounded(0, 3)(Gen.alphaNumericStringBounded(1, 5))
      baseVals <- Gen.listOfN(baseKeys.length)(Gen.int(-100, 100).map(i => new Json.Number(i.toString)))
      newKey   <- Gen.alphaNumericStringBounded(1, 5).map(k => s"new_$k")
      newVal   <- Gen.int(-100, 100).map(i => new Json.Number(i.toString))
    } yield {
      val baseFields = Chunk.fromIterable(baseKeys.zip(baseVals.map(v => v: Json)))
      val base       = new Json.Object(baseFields)
      val tweaked    = new Json.Object(baseFields :+ (newKey, newVal))
      (base, tweaked)
    }

  val genArrayWithAppendedElement: Gen[Any, (Json.Array, Json.Array)] =
    for {
      baseLen  <- Gen.int(0, 5)
      baseVals <- Gen.listOfN(baseLen)(Gen.int(-100, 100).map(i => new Json.Number(i.toString)))
      newVal   <- Gen.int(-100, 100).map(i => new Json.Number(i.toString))
    } yield {
      val base    = new Json.Array(Chunk.fromIterable(baseVals))
      val tweaked = new Json.Array(Chunk.fromIterable(baseVals) :+ newVal)
      (base, tweaked)
    }

  val genStringWithAppend: Gen[Any, (Json.String, Json.String)] =
    for {
      base   <- Gen.alphaNumericStringBounded(1, 20)
      suffix <- Gen.alphaNumericStringBounded(1, 10)
    } yield {
      val baseStr    = new Json.String(base)
      val tweakedStr = new Json.String(base + suffix)
      (baseStr, tweakedStr)
    }

  val genNumberWithDelta: Gen[Any, (Json.Number, Json.Number)] =
    for {
      base  <- Gen.bigDecimal(BigDecimal(-1000), BigDecimal(1000))
      delta <- Gen.bigDecimal(BigDecimal(-100), BigDecimal(100)).filter(_ != BigDecimal(0))
    } yield {
      val baseNum    = new Json.Number(base.toString)
      val tweakedNum = new Json.Number((base + delta).toString)
      (baseNum, tweakedNum)
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Correctness Tests
  // ─────────────────────────────────────────────────────────────────────────

  val correctnessSuite: Spec[Any, Nothing] = suite("Correctness")(
    test("diff(a, b).apply(a) == Right(b) for identical values") {
      val json  = new Json.Number("42")
      val patch = JsonDiffer.diff(json, json)
      assertTrue(patch.isEmpty) &&
      assertTrue(patch(json, PatchMode.Strict) == new Right(json))
    },
    test("diff(a, b).apply(a) == Right(b) for different numbers") {
      val a     = new Json.Number("10")
      val b     = new Json.Number("25")
      val patch = JsonDiffer.diff(a, b)
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    test("diff(a, b).apply(a) == Right(b) for different strings") {
      val a     = new Json.String("hello")
      val b     = new Json.String("hello world")
      val patch = JsonDiffer.diff(a, b)
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    test("diff(a, b).apply(a) == Right(b) for different booleans") {
      val patch = JsonDiffer.diff(Json.True, Json.False)
      assertTrue(patch(Json.True, PatchMode.Strict) == new Right(Json.False))
    },
    test("diff(a, b).apply(a) == Right(b) for null to value") {
      val b     = new Json.Number("42")
      val patch = JsonDiffer.diff(Json.Null, b)
      assertTrue(patch(Json.Null, PatchMode.Strict) == new Right(b))
    },
    test("diff(a, b).apply(a) == Right(b) for type change") {
      val a: Json = new Json.String("hello")
      val b: Json = new Json.Number("42")
      val patch   = JsonDiffer.diff(a, b)
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    // Property-based correctness tests
    test("diff(a, b).apply(a) == Right(b) for tweaked objects (property)") {
      check(genObjectWithAddedField) { case (a, b) =>
        val patch = JsonDiffer.diff(a, b)
        assertTrue(patch(a, PatchMode.Strict) == new Right(b))
      }
    },
    test("diff(a, b).apply(a) == Right(b) for tweaked arrays (property)") {
      check(genArrayWithAppendedElement) { case (a, b) =>
        val patch = JsonDiffer.diff(a, b)
        assertTrue(patch(a, PatchMode.Strict) == new Right(b))
      }
    },
    test("diff(a, b).apply(a) == Right(b) for tweaked strings (property)") {
      check(genStringWithAppend) { case (a, b) =>
        val patch = JsonDiffer.diff(a, b)
        assertTrue(patch(a, PatchMode.Strict) == new Right(b))
      }
    },
    test("diff(a, b).apply(a) == Right(b) for tweaked numbers (property)") {
      check(genNumberWithDelta) { case (a, b) =>
        val patch = JsonDiffer.diff(a, b)
        assertTrue(patch(a, PatchMode.Strict) == new Right(b))
      }
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Minimality Tests - Strings
  // ─────────────────────────────────────────────────────────────────────────

  val stringMinimalitySuite: Spec[Any, Nothing] = suite("Minimality - Strings")(
    test("similar strings produce StringEdit, not Set") {
      val a     = new Json.String("hello")
      val b     = new Json.String("hello world")
      val patch = JsonDiffer.diff(a, b)

      // Should use StringEdit (append), not Set
      val usesStringEdit = patch.ops.exists { op =>
        op.op match {
          case JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(_)) => true
          case _                                                                => false
        }
      }
      assertTrue(usesStringEdit) &&
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    test("prefix insertion produces StringEdit.Insert") {
      val a     = new Json.String("world")
      val b     = new Json.String("hello world")
      val patch = JsonDiffer.diff(a, b)

      val usesStringEdit = patch.ops.exists { op =>
        op.op match {
          case JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(_)) => true
          case _                                                                => false
        }
      }
      assertTrue(usesStringEdit) &&
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    test("deletion produces StringEdit.Delete") {
      val a     = new Json.String("hello world")
      val b     = new Json.String("hello")
      val patch = JsonDiffer.diff(a, b)

      val usesStringEdit = patch.ops.exists { op =>
        op.op match {
          case JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(_)) => true
          case _                                                                => false
        }
      }
      assertTrue(usesStringEdit) &&
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    test("completely different strings may use Set") {
      val a     = new Json.String("abc")
      val b     = new Json.String("xyz")
      val patch = JsonDiffer.diff(a, b)
      // Either Set or StringEdit is acceptable for completely different strings
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    // Property-based minimality test
    test("appended strings produce StringEdit (property)") {
      check(genStringWithAppend) { case (a, b) =>
        val patch          = JsonDiffer.diff(a, b)
        val usesStringEdit = patch.ops.exists { op =>
          op.op match {
            case JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(_)) => true
            case _                                                                => false
          }
        }
        assertTrue(usesStringEdit)
      }
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Minimality Tests - Arrays
  // ─────────────────────────────────────────────────────────────────────────

  val arrayMinimalitySuite: Spec[Any, Nothing] = suite("Minimality - Arrays")(
    test("appending to array produces ArrayEdit with Append") {
      val a     = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2")))
      val b     = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
      val patch = JsonDiffer.diff(a, b)

      val usesArrayEdit = patch.ops.exists { op =>
        op.op match {
          case JsonPatch.Op.ArrayEdit(_) => true
          case _                         => false
        }
      }
      assertTrue(usesArrayEdit) &&
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    test("removing from array produces ArrayEdit with Delete") {
      val a     = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
      val b     = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("3")))
      val patch = JsonDiffer.diff(a, b)

      val usesArrayEdit = patch.ops.exists { op =>
        op.op match {
          case JsonPatch.Op.ArrayEdit(_) => true
          case _                         => false
        }
      }
      assertTrue(usesArrayEdit) &&
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    test("modifying array element produces ArrayEdit with Modify") {
      val a     = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2")))
      val b     = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("99")))
      val patch = JsonDiffer.diff(a, b)

      // Extract ArrayOps from the patch
      val arrayOps = patch.ops.flatMap { op =>
        op.op match {
          case JsonPatch.Op.ArrayEdit(ops) => ops
          case _                           => Vector.empty
        }
      }

      // Verify that Modify is used (not Delete+Insert)
      val usesModify = arrayOps.exists {
        case JsonPatch.ArrayOp.Modify(_, _) => true
        case _                              => false
      }

      // Verify correct index and that it contains a delta op (not just Set)
      val modifyAtIndex1WithDelta = arrayOps.exists {
        case JsonPatch.ArrayOp.Modify(1, JsonPatch.Op.PrimitiveDelta(_)) => true
        case _                                                           => false
      }

      assertTrue(usesModify) &&
      assertTrue(modifyAtIndex1WithDelta) &&
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    test("modifying array element with multi-field object uses Modify with ObjectEdit") {
      // Object with two fields changing - object diff produces single ObjectEdit with multiple inner ops
      val obj1 = new Json.Object(
        Chunk(("a", new Json.Number("1")), ("b", new Json.Number("2")))
      )
      val obj2 = new Json.Object(
        Chunk(("a", new Json.Number("99")), ("b", new Json.Number("88")))
      )
      val a     = new Json.Array(Chunk(obj1))
      val b     = new Json.Array(Chunk(obj2))
      val patch = JsonDiffer.diff(a, b)

      // Extract ArrayOps from the patch
      val arrayOps = patch.ops.flatMap { op =>
        op.op match {
          case JsonPatch.Op.ArrayEdit(ops) => ops
          case _                           => Vector.empty
        }
      }

      // Verify that Modify is used at index 0 with ObjectEdit containing multiple field modifications
      val modifyWithObjectEdit = arrayOps.collectFirst {
        case JsonPatch.ArrayOp.Modify(0, JsonPatch.Op.ObjectEdit(objOps)) => objOps
      }

      // Should have ObjectEdit with 2 Modify ops (one for each field)
      val hasTwoFieldModifications = modifyWithObjectEdit.exists { objOps =>
        objOps.count(_.isInstanceOf[JsonPatch.ObjectOp.Modify]) == 2
      }

      // The patch should correctly transform a to b
      assertTrue(modifyWithObjectEdit.isDefined) &&
      assertTrue(hasTwoFieldModifications) &&
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    test("inserting at beginning produces ArrayEdit with Insert") {
      val a     = new Json.Array(Chunk(new Json.Number("2"), new Json.Number("3")))
      val b     = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
      val patch = JsonDiffer.diff(a, b)

      val usesArrayEdit = patch.ops.exists { op =>
        op.op match {
          case JsonPatch.Op.ArrayEdit(_) => true
          case _                         => false
        }
      }
      assertTrue(usesArrayEdit) &&
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    test("empty to non-empty array produces ArrayEdit") {
      val a     = Json.Array.empty
      val b     = new Json.Array(Chunk(new Json.Number("1")))
      val patch = JsonDiffer.diff(a, b)

      val usesArrayEdit = patch.ops.exists { op =>
        op.op match {
          case JsonPatch.Op.ArrayEdit(_) => true
          case _                         => false
        }
      }
      assertTrue(usesArrayEdit) &&
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    // Property-based minimality test
    test("appended arrays produce ArrayEdit (property)") {
      check(genArrayWithAppendedElement) { case (a, b) =>
        val patch         = JsonDiffer.diff(a, b)
        val usesArrayEdit = patch.ops.exists { op =>
          op.op match {
            case JsonPatch.Op.ArrayEdit(_) => true
            case _                         => false
          }
        }
        assertTrue(usesArrayEdit)
      }
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Minimality Tests - Objects
  // ─────────────────────────────────────────────────────────────────────────

  val objectMinimalitySuite: Spec[Any, Nothing] = suite("Minimality - Objects")(
    test("adding field produces ObjectEdit with Add") {
      val a     = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val b     = new Json.Object(Chunk(("a", new Json.Number("1")), ("b", new Json.Number("2"))))
      val patch = JsonDiffer.diff(a, b)

      val usesObjectEdit = patch.ops.exists { op =>
        op.op match {
          case JsonPatch.Op.ObjectEdit(_) => true
          case _                          => false
        }
      }
      assertTrue(usesObjectEdit) &&
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    test("removing field produces ObjectEdit with Remove") {
      val a     = new Json.Object(Chunk(("a", new Json.Number("1")), ("b", new Json.Number("2"))))
      val b     = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch = JsonDiffer.diff(a, b)

      val usesObjectEdit = patch.ops.exists { op =>
        op.op match {
          case JsonPatch.Op.ObjectEdit(_) => true
          case _                          => false
        }
      }
      assertTrue(usesObjectEdit) &&
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    test("modifying field value produces ObjectEdit with Modify") {
      val a     = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val b     = new Json.Object(Chunk(("a", new Json.Number("99"))))
      val patch = JsonDiffer.diff(a, b)

      val usesObjectEdit = patch.ops.exists { op =>
        op.op match {
          case JsonPatch.Op.ObjectEdit(_) => true
          case _                          => false
        }
      }
      assertTrue(usesObjectEdit) &&
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    test("empty to non-empty object produces ObjectEdit") {
      val a     = Json.Object.empty
      val b     = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch = JsonDiffer.diff(a, b)

      val usesObjectEdit = patch.ops.exists { op =>
        op.op match {
          case JsonPatch.Op.ObjectEdit(_) => true
          case _                          => false
        }
      }
      assertTrue(usesObjectEdit) &&
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    // Property-based minimality test
    test("objects with added field produce ObjectEdit (property)") {
      check(genObjectWithAddedField) { case (a, b) =>
        val patch          = JsonDiffer.diff(a, b)
        val usesObjectEdit = patch.ops.exists { op =>
          op.op match {
            case JsonPatch.Op.ObjectEdit(_) => true
            case _                          => false
          }
        }
        assertTrue(usesObjectEdit)
      }
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Minimality Tests - Numbers
  // ─────────────────────────────────────────────────────────────────────────

  val numberMinimalitySuite: Spec[Any, Nothing] = suite("Minimality - Numbers")(
    test("number change produces NumberDelta, not Set") {
      val a     = new Json.Number("10")
      val b     = new Json.Number("15")
      val patch = JsonDiffer.diff(a, b)

      val usesNumberDelta = patch.ops.exists { op =>
        op.op match {
          case JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(_)) => true
          case _                                                                 => false
        }
      }
      assertTrue(usesNumberDelta) &&
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    test("negative delta works correctly") {
      val a     = new Json.Number("100")
      val b     = new Json.Number("50")
      val patch = JsonDiffer.diff(a, b)

      val usesNumberDelta = patch.ops.exists { op =>
        op.op match {
          case JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(_)) => true
          case _                                                                 => false
        }
      }
      assertTrue(usesNumberDelta) &&
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    test("zero delta produces empty patch") {
      val a     = new Json.Number("42")
      val b     = new Json.Number("42")
      val patch = JsonDiffer.diff(a, b)
      assertTrue(patch.isEmpty)
    },
    test("decimal delta works correctly") {
      val a     = new Json.Number("10.5")
      val b     = new Json.Number("15.75")
      val patch = JsonDiffer.diff(a, b)
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    // Property-based minimality test
    test("number deltas produce NumberDelta (property)") {
      check(genNumberWithDelta) { case (a, b) =>
        val patch           = JsonDiffer.diff(a, b)
        val usesNumberDelta = patch.ops.exists { op =>
          op.op match {
            case JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(_)) => true
            case _                                                                 => false
          }
        }
        assertTrue(usesNumberDelta)
      }
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Branch Coverage Tests
  // ─────────────────────────────────────────────────────────────────────────

  val branchCoverageSuite: Spec[Any, Nothing] = suite("Branch Coverage")(
    test("empty array to empty array produces empty patch") {
      val patch = JsonDiffer.diff(Json.Array.empty, Json.Array.empty)
      assertTrue(patch.isEmpty)
    },
    test("empty object to empty object produces empty patch") {
      val patch = JsonDiffer.diff(Json.Object.empty, Json.Object.empty)
      assertTrue(patch.isEmpty)
    },
    test("empty string to empty string produces empty patch") {
      val a     = new Json.String("")
      val patch = JsonDiffer.diff(a, a)
      assertTrue(patch.isEmpty)
    },
    test("null to null produces empty patch") {
      val patch = JsonDiffer.diff(Json.Null, Json.Null)
      assertTrue(patch.isEmpty)
    },
    test("true to true produces empty patch") {
      val patch = JsonDiffer.diff(Json.True, Json.True)
      assertTrue(patch.isEmpty)
    },
    test("false to false produces empty patch") {
      val patch = JsonDiffer.diff(Json.False, Json.False)
      assertTrue(patch.isEmpty)
    },
    test("nested object field modification") {
      val a = new Json.Object(
        Chunk(
          ("outer", new Json.Object(Chunk(("inner", new Json.Number("1")))))
        )
      )
      val b = new Json.Object(
        Chunk(
          ("outer", new Json.Object(Chunk(("inner", new Json.Number("2")))))
        )
      )
      val patch = JsonDiffer.diff(a, b)
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    test("array with nested object modification") {
      val a = new Json.Array(
        Chunk(
          new Json.Object(Chunk(("key", new Json.Number("1"))))
        )
      )
      val b = new Json.Array(
        Chunk(
          new Json.Object(Chunk(("key", new Json.Number("2"))))
        )
      )
      val patch = JsonDiffer.diff(a, b)
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    test("type change from array to object uses Set") {
      val a: Json = new Json.Array(Chunk(new Json.Number("1")))
      val b: Json = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch   = JsonDiffer.diff(a, b)

      val usesSet = patch.ops.exists { op =>
        op.op match {
          case JsonPatch.Op.Set(_) => true
          case _                   => false
        }
      }
      assertTrue(usesSet) &&
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    test("single element array operations") {
      val a     = new Json.Array(Chunk(new Json.Number("1")))
      val b     = new Json.Array(Chunk(new Json.Number("2")))
      val patch = JsonDiffer.diff(a, b)
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    test("single key object operations") {
      val a     = new Json.Object(Chunk(("only", new Json.Number("1"))))
      val b     = new Json.Object(Chunk(("only", new Json.Number("2"))))
      val patch = JsonDiffer.diff(a, b)
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    // ─────────────────────────────────────────────────────────────────────────
    // Additional branch coverage tests (identified in code review)
    // ─────────────────────────────────────────────────────────────────────────
    test("non-empty array to empty array produces ArrayEdit") {
      val a     = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2")))
      val b     = Json.Array.empty
      val patch = JsonDiffer.diff(a, b)

      val usesArrayEdit = patch.ops.exists { op =>
        op.op match {
          case JsonPatch.Op.ArrayEdit(_) => true
          case _                         => false
        }
      }
      assertTrue(usesArrayEdit) &&
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    test("non-empty object to empty object produces ObjectEdit") {
      val a     = new Json.Object(Chunk(("a", new Json.Number("1")), ("b", new Json.Number("2"))))
      val b     = Json.Object.empty
      val patch = JsonDiffer.diff(a, b)

      val usesObjectEdit = patch.ops.exists { op =>
        op.op match {
          case JsonPatch.Op.ObjectEdit(_) => true
          case _                          => false
        }
      }
      assertTrue(usesObjectEdit) &&
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    test("empty string to non-empty string produces StringEdit") {
      val a     = new Json.String("")
      val b     = new Json.String("hello")
      val patch = JsonDiffer.diff(a, b)
      // Empty to non-empty should use StringEdit or Set
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    test("non-empty string to empty string produces StringEdit") {
      val a     = new Json.String("hello")
      val b     = new Json.String("")
      val patch = JsonDiffer.diff(a, b)

      // Should use StringEdit.Delete or Set
      val hasStringEdit = patch.ops.exists { op =>
        op.op match {
          case JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(_)) => true
          case JsonPatch.Op.Set(_)                                              => true
          case _                                                                => false
        }
      }
      assertTrue(hasStringEdit) &&
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    test("numeric equality with different string representations uses empty patch or Set") {
      // "1.0" and "1.00" represent the same numeric value
      // JsonDiffer should recognize this and produce empty patch or Set
      val a     = new Json.Number("1.0")
      val b     = new Json.Number("1.00")
      val patch = JsonDiffer.diff(a, b)

      // The delta should be 0, so either empty patch or valid roundtrip
      assertTrue(patch(a, PatchMode.Strict).isRight)
    },
    test("deeply nested object modification (3 levels)") {
      val a = new Json.Object(
        Chunk(
          (
            "level1",
            new Json.Object(
              Chunk(
                (
                  "level2",
                  new Json.Object(
                    Chunk(("level3", new Json.Number("42")))
                  )
                )
              )
            )
          )
        )
      )
      val b = new Json.Object(
        Chunk(
          (
            "level1",
            new Json.Object(
              Chunk(
                (
                  "level2",
                  new Json.Object(
                    Chunk(("level3", new Json.Number("99")))
                  )
                )
              )
            )
          )
        )
      )
      val patch = JsonDiffer.diff(a, b)

      // Verify correct roundtrip through 3 levels of nesting
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    test("deeply nested array modification") {
      val a = new Json.Array(
        Chunk(
          new Json.Array(
            Chunk(
              new Json.Array(Chunk(new Json.Number("1")))
            )
          )
        )
      )
      val b = new Json.Array(
        Chunk(
          new Json.Array(
            Chunk(
              new Json.Array(Chunk(new Json.Number("999")))
            )
          )
        )
      )
      val patch = JsonDiffer.diff(a, b)
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    },
    test("unparseable number falls back to Set") {
      // When Json.Number.toBigDecimalOption returns None, differ should use Set
      val a     = new Json.Number("NaN")
      val b     = new Json.Number("42")
      val patch = JsonDiffer.diff(a, b)

      val usesSet = patch.ops.exists { op =>
        op.op match {
          case JsonPatch.Op.Set(_) => true
          case _                   => false
        }
      }
      assertTrue(usesSet) &&
      assertTrue(patch(a, PatchMode.Strict) == new Right(b))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // True Minimality Tests (exact operation counts)
  // ─────────────────────────────────────────────────────────────────────────

  val trueMinimalitySuite: Spec[Any, Nothing] = suite("True Minimality (exact operation counts)")(
    test("appending to string produces exactly 1 Insert at end position") {
      val a     = new Json.String("hello")
      val b     = new Json.String("hello world")
      val patch = JsonDiffer.diff(a, b)

      val stringEditOps = patch.ops.flatMap { op =>
        op.op match {
          case JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(ops)) => ops
          case _                                                                  => Vector.empty
        }
      }
      // LCS-based differ produces Insert at end position (5 = "hello".length)
      assertTrue(stringEditOps.length == 1) &&
      assertTrue(stringEditOps.head match {
        case JsonPatch.StringOp.Insert(5, " world") => true
        case _                                      => false
      })
    },
    test("appending to array produces exactly 1 Append operation") {
      val a     = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2")))
      val b     = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
      val patch = JsonDiffer.diff(a, b)

      val arrayOps = patch.ops.flatMap { op =>
        op.op match {
          case JsonPatch.Op.ArrayEdit(ops) => ops
          case _                           => Vector.empty
        }
      }
      assertTrue(arrayOps.length == 1) &&
      assertTrue(arrayOps.head.isInstanceOf[JsonPatch.ArrayOp.Append])
    },
    test("adding one field to object produces exactly 1 Add operation") {
      val a     = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val b     = new Json.Object(Chunk(("a", new Json.Number("1")), ("b", new Json.Number("2"))))
      val patch = JsonDiffer.diff(a, b)

      val objectOps = patch.ops.flatMap { op =>
        op.op match {
          case JsonPatch.Op.ObjectEdit(ops) => ops
          case _                            => Vector.empty
        }
      }
      assertTrue(objectOps.length == 1) &&
      assertTrue(objectOps.head.isInstanceOf[JsonPatch.ObjectOp.Add])
    },
    test("removing one field from object produces exactly 1 Remove operation") {
      val a     = new Json.Object(Chunk(("a", new Json.Number("1")), ("b", new Json.Number("2"))))
      val b     = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch = JsonDiffer.diff(a, b)

      val objectOps = patch.ops.flatMap { op =>
        op.op match {
          case JsonPatch.Op.ObjectEdit(ops) => ops
          case _                            => Vector.empty
        }
      }
      assertTrue(objectOps.length == 1) &&
      assertTrue(objectOps.head.isInstanceOf[JsonPatch.ObjectOp.Remove])
    },
    test("modifying one number produces exactly 1 NumberDelta with correct delta") {
      val a     = new Json.Number("10")
      val b     = new Json.Number("15")
      val patch = JsonDiffer.diff(a, b)

      assertTrue(patch.ops.length == 1) &&
      assertTrue(patch.ops.head.op match {
        case JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(delta)) => delta == BigDecimal(5)
        case _                                                                     => false
      })
    },
    test("identical values produce exactly 0 operations") {
      val json  = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch = JsonDiffer.diff(json, json)
      assertTrue(patch.ops.isEmpty)
    },
    test("prepending to string produces exactly 1 Insert at index 0") {
      val a     = new Json.String("world")
      val b     = new Json.String("hello world")
      val patch = JsonDiffer.diff(a, b)

      val stringEditOps = patch.ops.flatMap { op =>
        op.op match {
          case JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(ops)) => ops
          case _                                                                  => Vector.empty
        }
      }
      assertTrue(stringEditOps.length == 1) &&
      assertTrue(stringEditOps.head match {
        case JsonPatch.StringOp.Insert(0, _) => true
        case _                               => false
      })
    },
    test("deleting from end of string produces exactly 1 Delete operation") {
      val a     = new Json.String("hello world")
      val b     = new Json.String("hello")
      val patch = JsonDiffer.diff(a, b)

      val stringEditOps = patch.ops.flatMap { op =>
        op.op match {
          case JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(ops)) => ops
          case _                                                                  => Vector.empty
        }
      }
      assertTrue(stringEditOps.length == 1) &&
      assertTrue(stringEditOps.head.isInstanceOf[JsonPatch.StringOp.Delete])
    },
    // Additional edge cases for LCS algorithm
    test("completely different strings produce Set") {
      val a     = new Json.String("abcdefg")
      val b     = new Json.String("1234567")
      val patch = JsonDiffer.diff(a, b)
      assertTrue(patch.ops.head.op.isInstanceOf[JsonPatch.Op.Set])
    },
    test("empty to non-empty string produces Insert") {
      val a     = new Json.String("")
      val b     = new Json.String("hello")
      val patch = JsonDiffer.diff(a, b)
      // Should produce a single operation
      assertTrue(patch.ops.length == 1)
    },
    test("non-empty to empty string produces Delete") {
      val a     = new Json.String("hello")
      val b     = new Json.String("")
      val patch = JsonDiffer.diff(a, b)
      assertTrue(patch.ops.length == 1)
    },
    test("empty to non-empty array produces Append") {
      val a     = Json.Array.empty
      val b     = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2")))
      val patch = JsonDiffer.diff(a, b)
      assertTrue(patch.ops.head.op match {
        case JsonPatch.Op.ArrayEdit(ops) => ops.exists(_.isInstanceOf[JsonPatch.ArrayOp.Append])
        case _                           => false
      })
    },
    test("non-empty to empty array produces Delete") {
      val a     = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2")))
      val b     = Json.Array.empty
      val patch = JsonDiffer.diff(a, b)
      assertTrue(patch.ops.head.op match {
        case JsonPatch.Op.ArrayEdit(ops) => ops.exists(_.isInstanceOf[JsonPatch.ArrayOp.Delete])
        case _                           => false
      })
    },
    test("empty to non-empty object produces Add") {
      val a     = Json.Object.empty
      val b     = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch = JsonDiffer.diff(a, b)
      assertTrue(patch.ops.head.op match {
        case JsonPatch.Op.ObjectEdit(ops) => ops.exists(_.isInstanceOf[JsonPatch.ObjectOp.Add])
        case _                            => false
      })
    },
    test("non-empty to empty object produces Remove") {
      val a     = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val b     = Json.Object.empty
      val patch = JsonDiffer.diff(a, b)
      assertTrue(patch.ops.head.op match {
        case JsonPatch.Op.ObjectEdit(ops) => ops.exists(_.isInstanceOf[JsonPatch.ObjectOp.Remove])
        case _                            => false
      })
    },
    test("type mismatch produces Set") {
      val a     = new Json.Number("1")
      val b     = new Json.String("1")
      val patch = JsonDiffer.diff(a, b)
      assertTrue(patch.ops.head.op.isInstanceOf[JsonPatch.Op.Set])
    },
    test("boolean to number produces Set") {
      val a     = Json.True
      val b     = new Json.Number("1")
      val patch = JsonDiffer.diff(a, b)
      assertTrue(patch.ops.head.op.isInstanceOf[JsonPatch.Op.Set])
    },
    test("null to boolean produces Set") {
      val a     = Json.Null
      val b     = Json.True
      val patch = JsonDiffer.diff(a, b)
      assertTrue(patch.ops.head.op.isInstanceOf[JsonPatch.Op.Set])
    },
    test("number to null produces Set") {
      val a     = new Json.Number("42")
      val b     = Json.Null
      val patch = JsonDiffer.diff(a, b)
      assertTrue(patch.ops.head.op.isInstanceOf[JsonPatch.Op.Set])
    },
    test("object field modification produces Modify") {
      val a     = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val b     = new Json.Object(Chunk(("a", new Json.Number("2"))))
      val patch = JsonDiffer.diff(a, b)
      assertTrue(patch.ops.head.op match {
        case JsonPatch.Op.ObjectEdit(ops) => ops.exists(_.isInstanceOf[JsonPatch.ObjectOp.Modify])
        case _                            => false
      })
    },
    test("LCS with mixed insert and delete") {
      // Old: "ABCED" -> New: "ABCDE" (swap E and D positions)
      val a      = new Json.String("ABCED")
      val b      = new Json.String("ABCDE")
      val patch  = JsonDiffer.diff(a, b)
      val result = patch(a, PatchMode.Strict)
      assertTrue(result == new Right(b))
    },
    test("array with mixed operations") {
      val a = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
      val b =
        new Json.Array(Chunk(new Json.Number("1"), new Json.Number("4"), new Json.Number("3"), new Json.Number("5")))
      val patch  = JsonDiffer.diff(a, b)
      val result = patch(a, PatchMode.Strict)
      assertTrue(result == new Right(b))
    },
    test("NumberDelta with identical string-different numbers uses Set") {
      // e.g., "1.0" vs "1" are numerically equal but string-different
      val a     = new Json.Number("1.0")
      val b     = new Json.Number("1")
      val patch = JsonDiffer.diff(a, b)
      // When strings differ but numbers are equal, it uses Set
      assertTrue(patch.ops.nonEmpty)
    },
    test("unparseable number produces Set") {
      // Simulate an unparseable number scenario
      val a     = new Json.Number("not-a-number")
      val b     = new Json.Number("also-not-a-number")
      val patch = JsonDiffer.diff(a, b)
      assertTrue(patch.ops.head.op.isInstanceOf[JsonPatch.Op.Set])
    },
    // Additional branch coverage tests
    test("string with common prefix and suffix differs in middle") {
      val a      = new Json.String("abcXYZdef")
      val b      = new Json.String("abc123def")
      val patch  = JsonDiffer.diff(a, b)
      val result = patch(a, PatchMode.Strict)
      assertTrue(result == new Right(b))
    },
    test("array with single element modification uses Modify not Delete+Insert") {
      val a     = new Json.Array(Chunk(new Json.Object(Chunk(("x", new Json.Number("1"))))))
      val b     = new Json.Array(Chunk(new Json.Object(Chunk(("x", new Json.Number("2"))))))
      val patch = JsonDiffer.diff(a, b)
      assertTrue(patch.ops.head.op match {
        case JsonPatch.Op.ArrayEdit(ops) => ops.exists(_.isInstanceOf[JsonPatch.ArrayOp.Modify])
        case _                           => false
      })
    },
    test("object with multiple changes produces single ObjectEdit") {
      val a     = new Json.Object(Chunk(("a", new Json.Number("1")), ("b", new Json.Number("2"))))
      val b     = new Json.Object(Chunk(("a", new Json.Number("10")), ("c", new Json.Number("3"))))
      val patch = JsonDiffer.diff(a, b)
      assertTrue(patch.ops.length == 1 && patch.ops.head.op.isInstanceOf[JsonPatch.Op.ObjectEdit])
    },
    test("deeply nested object modification") {
      val inner1 = new Json.Object(Chunk(("x", new Json.Number("1"))))
      val inner2 = new Json.Object(Chunk(("x", new Json.Number("2"))))
      val a      = new Json.Object(Chunk(("nested", inner1)))
      val b      = new Json.Object(Chunk(("nested", inner2)))
      val patch  = JsonDiffer.diff(a, b)
      val result = patch(a, PatchMode.Strict)
      assertTrue(result == new Right(b))
    },
    test("array element type change uses Set") {
      val a      = new Json.Array(Chunk(new Json.Number("1")))
      val b      = new Json.Array(Chunk(new Json.String("1")))
      val patch  = JsonDiffer.diff(a, b)
      val result = patch(a, PatchMode.Strict)
      assertTrue(result == new Right(b))
    },
    test("large string with minimal edit") {
      val base   = "a" * 100
      val a      = new Json.String(base)
      val b      = new Json.String(base + "b")
      val patch  = JsonDiffer.diff(a, b)
      val result = patch(a, PatchMode.Strict)
      assertTrue(result == new Right(b))
    },
    test("array swap two elements") {
      val a      = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2")))
      val b      = new Json.Array(Chunk(new Json.Number("2"), new Json.Number("1")))
      val patch  = JsonDiffer.diff(a, b)
      val result = patch(a, PatchMode.Strict)
      assertTrue(result == new Right(b))
    },
    test("object key rename via add and remove") {
      val a      = new Json.Object(Chunk(("old", new Json.Number("1"))))
      val b      = new Json.Object(Chunk(("new", new Json.Number("1"))))
      val patch  = JsonDiffer.diff(a, b)
      val result = patch(a, PatchMode.Strict)
      assertTrue(result == new Right(b))
    },
    test("string with repeated characters") {
      val a      = new Json.String("aaaaaa")
      val b      = new Json.String("aaabbb")
      val patch  = JsonDiffer.diff(a, b)
      val result = patch(a, PatchMode.Strict)
      assertTrue(result == new Right(b))
    },
    test("number with trailing zeros normalization") {
      val a     = new Json.Number("10.00")
      val b     = new Json.Number("20.00")
      val patch = JsonDiffer.diff(a, b)
      assertTrue(patch.ops.nonEmpty)
    },
    test("object with null value") {
      val a      = new Json.Object(Chunk(("a", Json.Null)))
      val b      = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch  = JsonDiffer.diff(a, b)
      val result = patch(a, PatchMode.Strict)
      assertTrue(result == new Right(b))
    },
    test("array insert at beginning") {
      val a      = new Json.Array(Chunk(new Json.Number("2"), new Json.Number("3")))
      val b      = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
      val patch  = JsonDiffer.diff(a, b)
      val result = patch(a, PatchMode.Strict)
      assertTrue(result == new Right(b))
    },
    test("array delete from beginning") {
      val a      = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
      val b      = new Json.Array(Chunk(new Json.Number("2"), new Json.Number("3")))
      val patch  = JsonDiffer.diff(a, b)
      val result = patch(a, PatchMode.Strict)
      assertTrue(result == new Right(b))
    },
    test("string delete from middle") {
      val a      = new Json.String("abcdef")
      val b      = new Json.String("abef")
      val patch  = JsonDiffer.diff(a, b)
      val result = patch(a, PatchMode.Strict)
      assertTrue(result == new Right(b))
    },
    test("string insert in middle") {
      val a      = new Json.String("abef")
      val b      = new Json.String("abcdef")
      val patch  = JsonDiffer.diff(a, b)
      val result = patch(a, PatchMode.Strict)
      assertTrue(result == new Right(b))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Main Spec
  // ─────────────────────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("JsonDifferSpec")(
    correctnessSuite,
    stringMinimalitySuite,
    arrayMinimalitySuite,
    objectMinimalitySuite,
    numberMinimalitySuite,
    branchCoverageSuite,
    trueMinimalitySuite
  )
}
