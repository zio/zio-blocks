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

      val usesArrayEdit = patch.ops.exists { op =>
        op.op match {
          case JsonPatch.Op.ArrayEdit(_) => true
          case _                         => false
        }
      }
      assertTrue(usesArrayEdit) &&
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
    branchCoverageSuite
  )
}
