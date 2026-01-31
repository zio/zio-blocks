package zio.blocks.schema.json.patch

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, SchemaBaseSpec, SchemaError}
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch._
import zio.blocks.schema.patch.PatchMode
import zio.test._

object JsonPatchErrorSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("Error Cases")(
    invalidPathSuite,
    typeMismatchSuite,
    outOfBoundsSuite,
    patchModeComparisonSuite,
    errorMessageVerificationSuite,
    unsupportedNavigationSuite,
    elementsNavigationErrorSuite
  )

  private def assertError(result: Either[SchemaError, Json], expectedMessage: String): TestResult =
    result match {
      case Left(err) => assertTrue(err.message.contains(expectedMessage))
      case Right(_)  => assertTrue(false)
    }

  private def assertSuccess(result: Either[SchemaError, Json], expected: Json): TestResult =
    assertTrue(result == Right(expected))

  // Invalid Path Suite

  private lazy val invalidPathSuite = suite("Invalid Path")(
    test("Field not found in object produces SchemaError") {
      val original = Json.Object("name" -> Json.String("Alice"))
      val path     = DynamicOptic.root.field("nonexistent")
      val patch    = JsonPatch(path, Op.Set(Json.String("value")))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "nonexistent")
    },
    test("Index out of bounds in array produces SchemaError") {
      val original = Json.Array(Json.Number(1), Json.Number(2))
      val path     = DynamicOptic.root.at(10)
      val patch    = JsonPatch(path, Op.Set(Json.String("value")))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "out of bounds")
    },
    test("Navigate into non-object produces SchemaError") {
      val original = Json.String("not an object")
      val path     = DynamicOptic.root.field("field")
      val patch    = JsonPatch(path, Op.Set(Json.String("value")))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "Expected Object")
    },
    test("Navigate into non-array produces SchemaError") {
      val original = Json.String("not an array")
      val path     = DynamicOptic.root.at(0)
      val patch    = JsonPatch(path, Op.Set(Json.String("value")))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "Expected Array")
    },
    test("Navigate into number produces SchemaError") {
      val original = Json.Number(42)
      val path     = DynamicOptic.root.field("field")
      val patch    = JsonPatch(path, Op.Set(Json.String("value")))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "Expected Object")
    },
    test("Navigate into boolean produces SchemaError") {
      val original = Json.Boolean(true)
      val path     = DynamicOptic.root.at(0)
      val patch    = JsonPatch(path, Op.Set(Json.String("value")))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "Expected Array")
    },
    test("Navigate into null produces SchemaError") {
      val original = Json.Null
      val path     = DynamicOptic.root.field("field")
      val patch    = JsonPatch(path, Op.Set(Json.String("value")))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "Expected Object")
    },
    test("Negative array index produces SchemaError") {
      val original = Json.Array(Json.Number(1), Json.Number(2))
      val path     = DynamicOptic.root.at(-1)
      val patch    = JsonPatch(path, Op.Set(Json.String("value")))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "out of bounds")
    }
  )

  // Type Mismatch Suite

  private lazy val typeMismatchSuite = suite("Type Mismatch")(
    test("NumberDelta on non-number produces SchemaError") {
      val original = Json.String("not a number")
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(5))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "Expected Number")
    },
    test("NumberDelta on object produces SchemaError") {
      val original = Json.Object("a" -> Json.Number(1))
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(5))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "Expected Number")
    },
    test("NumberDelta on array produces SchemaError") {
      val original = Json.Array(Json.Number(1))
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(5))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "Expected Number")
    },
    test("NumberDelta on boolean produces SchemaError") {
      val original = Json.Boolean(true)
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(5))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "Expected Number")
    },
    test("NumberDelta on null produces SchemaError") {
      val original = Json.Null
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(5))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "Expected Number")
    },
    test("StringEdit on non-string produces SchemaError") {
      val original = Json.Number(42)
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.StringEdit(Vector(StringOp.Append("text")))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "Expected String")
    },
    test("StringEdit on object produces SchemaError") {
      val original = Json.Object("a" -> Json.String("x"))
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.StringEdit(Vector(StringOp.Append("text")))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "Expected String")
    },
    test("StringEdit on array produces SchemaError") {
      val original = Json.Array(Json.String("x"))
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.StringEdit(Vector(StringOp.Append("text")))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "Expected String")
    },
    test("ArrayEdit on non-array produces SchemaError") {
      val original = Json.String("not an array")
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Append(Chunk(Json.Number(1))))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "Expected Array")
    },
    test("ArrayEdit on object produces SchemaError") {
      val original = Json.Object("a" -> Json.Number(1))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Append(Chunk(Json.Number(1))))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "Expected Array")
    },
    test("ArrayEdit on number produces SchemaError") {
      val original = Json.Number(42)
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Append(Chunk(Json.Number(1))))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "Expected Array")
    },
    test("ObjectEdit on non-object produces SchemaError") {
      val original = Json.Array(Json.Number(1))
      val patch    = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Add("key", Json.String("value")))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "Expected Object")
    },
    test("ObjectEdit on string produces SchemaError") {
      val original = Json.String("not an object")
      val patch    = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Add("key", Json.String("value")))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "Expected Object")
    },
    test("ObjectEdit on number produces SchemaError") {
      val original = Json.Number(42)
      val patch    = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Add("key", Json.String("value")))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "Expected Object")
    }
  )

  // Out of Bounds Suite

  private lazy val outOfBoundsSuite = suite("Out of Bounds")(
    test("Array insert at negative index fails in Strict mode") {
      val original = Json.Array(Json.Number(1), Json.Number(2))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Insert(-1, Chunk(Json.Number(0))))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "out of bounds")
    },
    test("Array insert past end fails in Strict mode") {
      val original = Json.Array(Json.Number(1), Json.Number(2))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Insert(10, Chunk(Json.Number(0))))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "out of bounds")
    },
    test("Array delete past end fails in Strict mode") {
      val original = Json.Array(Json.Number(1), Json.Number(2))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Delete(5, 1))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "out of bounds")
    },
    test("Array delete with count past end fails in Strict mode") {
      val original = Json.Array(Json.Number(1), Json.Number(2))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Delete(1, 5))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "out of bounds")
    },
    test("Array modify at out of bounds index fails") {
      val original = Json.Array(Json.Number(1), Json.Number(2))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Modify(10, Op.Set(Json.Number(100))))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "out of bounds")
    },
    test("String insert at negative index fails") {
      val original = Json.String("hello")
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.StringEdit(Vector(StringOp.Insert(-1, "x")))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "out of bounds")
    },
    test("String insert past end is allowed") {
      // Inserting at position = length is valid (inserts at end)
      val original = Json.String("hello")
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.StringEdit(Vector(StringOp.Insert(5, "!")))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertSuccess(result, Json.String("hello!"))
    },
    test("String insert way past end fails") {
      val original = Json.String("hello")
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.StringEdit(Vector(StringOp.Insert(100, "x")))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "out of bounds")
    },
    test("String delete past end fails") {
      val original = Json.String("hello")
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.StringEdit(Vector(StringOp.Delete(10, 1)))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "out of bounds")
    },
    test("String delete with length past end fails") {
      val original = Json.String("hello")
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.StringEdit(Vector(StringOp.Delete(3, 10)))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "out of bounds")
    },
    test("String modify past end fails") {
      val original = Json.String("hello")
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.StringEdit(Vector(StringOp.Modify(10, 1, "x")))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "out of bounds")
    }
  )

  // PatchMode Comparison Suite

  private lazy val patchModeComparisonSuite = suite("PatchMode Comparison")(
    objectOpAddModeSuite,
    objectOpRemoveModeSuite,
    arrayOpInsertModeSuite,
    arrayOpDeleteModeSuite,
    modeEquivalenceSuite
  )

  private lazy val objectOpAddModeSuite = suite("ObjectOp.Add on existing key")(
    test("Strict fails when key exists") {
      val original = Json.Object("name" -> Json.String("Alice"))
      val patch    = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Add("name", Json.String("Bob")))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "already exists")
    },
    test("Lenient fails when key exists") {
      val original = Json.Object("name" -> Json.String("Alice"))
      val patch    = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Add("name", Json.String("Bob")))))
      val result   = patch.apply(original, PatchMode.Lenient)
      // Lenient returns error but caller skips - result is unchanged
      assertSuccess(result, Json.Object("name" -> Json.String("Alice")))
    },
    test("Clobber overwrites when key exists") {
      val original = Json.Object("name" -> Json.String("Alice"))
      val patch    = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Add("name", Json.String("Bob")))))
      val result   = patch.apply(original, PatchMode.Clobber)
      assertSuccess(result, Json.Object("name" -> Json.String("Bob")))
    }
  )

  private lazy val objectOpRemoveModeSuite = suite("ObjectOp.Remove on missing key")(
    test("Strict fails when key missing") {
      val original = Json.Object("name" -> Json.String("Alice"))
      val patch    = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Remove("age"))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "not found")
    },
    test("Lenient skips when key missing") {
      val original = Json.Object("name" -> Json.String("Alice"))
      val patch    = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Remove("age"))))
      val result   = patch.apply(original, PatchMode.Lenient)
      // Lenient skips the operation - object unchanged
      assertSuccess(result, Json.Object("name" -> Json.String("Alice")))
    },
    test("Clobber is no-op when key missing") {
      val original = Json.Object("name" -> Json.String("Alice"))
      val patch    = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Remove("age"))))
      val result   = patch.apply(original, PatchMode.Clobber)
      // Clobber ignores error - object unchanged
      assertSuccess(result, Json.Object("name" -> Json.String("Alice")))
    }
  )

  private lazy val arrayOpInsertModeSuite = suite("ArrayOp.Insert out of bounds")(
    test("Strict fails on out of bounds insert") {
      val original = Json.Array(Json.Number(1), Json.Number(2))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Insert(100, Chunk(Json.Number(3))))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "out of bounds")
    },
    test("Lenient skips out of bounds insert") {
      val original = Json.Array(Json.Number(1), Json.Number(2))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Insert(100, Chunk(Json.Number(3))))))
      val result   = patch.apply(original, PatchMode.Lenient)
      // Lenient skips - array unchanged
      assertSuccess(result, Json.Array(Json.Number(1), Json.Number(2)))
    },
    test("Clobber clamps out of bounds insert to end") {
      val original = Json.Array(Json.Number(1), Json.Number(2))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Insert(100, Chunk(Json.Number(3))))))
      val result   = patch.apply(original, PatchMode.Clobber)
      // Clobber clamps to valid index - inserts at end
      assertSuccess(result, Json.Array(Json.Number(1), Json.Number(2), Json.Number(3)))
    },
    test("Clobber clamps negative insert to beginning") {
      val original = Json.Array(Json.Number(1), Json.Number(2))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Insert(-5, Chunk(Json.Number(0))))))
      val result   = patch.apply(original, PatchMode.Clobber)
      // Clobber clamps to valid index - inserts at beginning
      assertSuccess(result, Json.Array(Json.Number(0), Json.Number(1), Json.Number(2)))
    }
  )

  private lazy val arrayOpDeleteModeSuite = suite("ArrayOp.Delete out of bounds")(
    test("Strict fails on out of bounds delete") {
      val original = Json.Array(Json.Number(1), Json.Number(2))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Delete(5, 1))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "out of bounds")
    },
    test("Lenient skips out of bounds delete") {
      val original = Json.Array(Json.Number(1), Json.Number(2))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Delete(5, 1))))
      val result   = patch.apply(original, PatchMode.Lenient)
      // Lenient skips - array unchanged
      assertSuccess(result, Json.Array(Json.Number(1), Json.Number(2)))
    },
    test("Clobber clamps out of bounds delete") {
      val original = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Delete(1, 10))))
      val result   = patch.apply(original, PatchMode.Clobber)
      // Clobber clamps - deletes what it can
      assertSuccess(result, Json.Array(Json.Number(1)))
    },
    test("Clobber handles negative start index") {
      val original = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Delete(-5, 2))))
      val result   = patch.apply(original, PatchMode.Clobber)
      // Clobber clamps both start and end to 0, so nothing is deleted
      assertSuccess(result, Json.Array(Json.Number(1), Json.Number(2), Json.Number(3)))
    }
  )

  private lazy val modeEquivalenceSuite = suite("Mode equivalence when valid")(
    test("When Strict succeeds, Lenient produces same result") {
      val original = Json.Object("name" -> Json.String("Alice"))
      val patch    = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Add("age", Json.Number(30)))))

      val strictResult  = patch.apply(original, PatchMode.Strict)
      val lenientResult = patch.apply(original, PatchMode.Lenient)

      assertTrue(strictResult.isRight) &&
      assertTrue(strictResult == lenientResult)
    },
    test("When Strict succeeds, Clobber produces same result") {
      val original = Json.Array(Json.Number(1), Json.Number(2))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Insert(1, Chunk(Json.Number(5))))))

      val strictResult  = patch.apply(original, PatchMode.Strict)
      val clobberResult = patch.apply(original, PatchMode.Clobber)

      assertTrue(strictResult.isRight) &&
      assertTrue(strictResult == clobberResult)
    },
    test("All modes produce same result for valid Set operation") {
      val original = Json.String("old")
      val patch    = JsonPatch.root(Op.Set(Json.String("new")))

      val strictResult  = patch.apply(original, PatchMode.Strict)
      val lenientResult = patch.apply(original, PatchMode.Lenient)
      val clobberResult = patch.apply(original, PatchMode.Clobber)

      assertTrue(strictResult == Right(Json.String("new"))) &&
      assertTrue(strictResult == lenientResult) &&
      assertTrue(strictResult == clobberResult)
    },
    test("All modes produce same result for valid NumberDelta") {
      val original = Json.Number(10)
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(5))))

      val strictResult  = patch.apply(original, PatchMode.Strict)
      val lenientResult = patch.apply(original, PatchMode.Lenient)
      val clobberResult = patch.apply(original, PatchMode.Clobber)

      assertTrue(strictResult == Right(Json.Number("15"))) &&
      assertTrue(strictResult == lenientResult) &&
      assertTrue(strictResult == clobberResult)
    },
    test("All modes produce same result for valid Append") {
      val original = Json.Array(Json.Number(1))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Append(Chunk(Json.Number(2))))))

      val strictResult  = patch.apply(original, PatchMode.Strict)
      val lenientResult = patch.apply(original, PatchMode.Lenient)
      val clobberResult = patch.apply(original, PatchMode.Clobber)

      assertTrue(strictResult == Right(Json.Array(Json.Number(1), Json.Number(2)))) &&
      assertTrue(strictResult == lenientResult) &&
      assertTrue(strictResult == clobberResult)
    }
  )

  // Error Message Verification Suite

  private lazy val errorMessageVerificationSuite = suite("Error Message Verification")(
    test("Missing field error contains field name") {
      val original = Json.Object("a" -> Json.Number(1))
      val path     = DynamicOptic.root.field("missing_field")
      val patch    = JsonPatch(path, Op.Set(Json.Number(2)))
      val result   = patch.apply(original, PatchMode.Strict)
      result match {
        case Left(err) =>
          assertTrue(err.message.contains("missing_field"))
        case Right(_) =>
          assertTrue(false)
      }
    },
    test("Index out of bounds error contains index and array length") {
      val original = Json.Array(Json.Number(1), Json.Number(2))
      val path     = DynamicOptic.root.at(50)
      val patch    = JsonPatch(path, Op.Set(Json.Number(100)))
      val result   = patch.apply(original, PatchMode.Strict)
      result match {
        case Left(err) =>
          assertTrue(err.message.contains("50")) &&
          assertTrue(err.message.contains("2"))
        case Right(_) =>
          assertTrue(false)
      }
    },
    test("Type mismatch error contains expected type") {
      val original = Json.String("text")
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(5))))
      val result   = patch.apply(original, PatchMode.Strict)
      result match {
        case Left(err) =>
          assertTrue(err.message.contains("Expected Number"))
        case Right(_) =>
          assertTrue(false)
      }
    },
    test("Object key already exists error contains key name") {
      val original = Json.Object("key" -> Json.String("value"))
      val patch    = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Add("key", Json.String("new")))))
      val result   = patch.apply(original, PatchMode.Strict)
      result match {
        case Left(err) =>
          assertTrue(err.message.contains("key")) &&
          assertTrue(err.message.contains("exists"))
        case Right(_) =>
          assertTrue(false)
      }
    },
    test("Object key not found error contains key name") {
      val original = Json.Object("a" -> Json.Number(1))
      val patch    = JsonPatch.root(Op.ObjectEdit(Vector(ObjectOp.Remove("nonexistent"))))
      val result   = patch.apply(original, PatchMode.Strict)
      result match {
        case Left(err) =>
          assertTrue(err.message.contains("nonexistent")) &&
          assertTrue(err.message.contains("not found"))
        case Right(_) =>
          assertTrue(false)
      }
    },
    test("String operation out of bounds error contains position info") {
      val original = Json.String("short")
      val patch    = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.StringEdit(Vector(StringOp.Delete(10, 5)))))
      val result   = patch.apply(original, PatchMode.Strict)
      result match {
        case Left(err) =>
          assertTrue(err.message.contains("10")) &&
          assertTrue(err.message.contains("out of bounds"))
        case Right(_) =>
          assertTrue(false)
      }
    },
    test("Array insert out of bounds error contains position info") {
      val original = Json.Array(Json.Number(1))
      val patch    = JsonPatch.root(Op.ArrayEdit(Vector(ArrayOp.Insert(100, Chunk(Json.Number(2))))))
      val result   = patch.apply(original, PatchMode.Strict)
      result match {
        case Left(err) =>
          assertTrue(err.message.contains("100")) &&
          assertTrue(err.message.contains("out of bounds"))
        case Right(_) =>
          assertTrue(false)
      }
    }
  )

  // Unsupported Navigation Suite

  private lazy val unsupportedNavigationSuite = suite("Unsupported Navigation Nodes")(
    test("Case navigation fails with appropriate error") {
      val original = Json.Object("type" -> Json.String("A"), "value" -> Json.Number(1))
      val path     = DynamicOptic.root.caseOf("SomeCase")
      val patch    = JsonPatch(path, Op.Set(Json.Number(1)))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "Case navigation not supported")
    },
    test("AtMapKey navigation fails with appropriate error") {
      val original   = Json.Object("a" -> Json.Number(1))
      val dynamicKey = DynamicValue.Primitive(PrimitiveValue.String("key"))
      val path       = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(dynamicKey)))
      val patch      = JsonPatch(path, Op.Set(Json.Number(1)))
      val result     = patch.apply(original, PatchMode.Strict)
      assertError(result, "AtMapKey not supported")
    },
    test("AtIndices navigation fails with appropriate error") {
      val original = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
      val path     = DynamicOptic.root.atIndices(0, 1, 2)
      val patch    = JsonPatch(path, Op.Set(Json.Number(1)))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "AtIndices not supported")
    },
    test("AtMapKeys navigation fails with appropriate error") {
      val original = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))
      val path     = DynamicOptic.root.atKeys("a", "b")
      val patch    = JsonPatch(path, Op.Set(Json.Number(1)))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "AtMapKeys not supported")
    },
    test("MapKeys navigation fails with appropriate error") {
      val original = Json.Object("a" -> Json.Number(1))
      val path     = DynamicOptic.root.mapKeys
      val patch    = JsonPatch(path, Op.Set(Json.Number(1)))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "MapKeys not supported")
    },
    test("MapValues navigation fails with appropriate error") {
      val original = Json.Object("a" -> Json.Number(1))
      val path     = DynamicOptic.root.mapValues
      val patch    = JsonPatch(path, Op.Set(Json.Number(1)))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "MapValues not supported")
    }
  )

  // Elements Navigation Error Suite

  private lazy val elementsNavigationErrorSuite = suite("Elements Navigation Errors")(
    test("Elements navigation on empty array fails in Strict mode") {
      val original = Json.Array.empty
      val path     = DynamicOptic.root.elements
      val patch    = JsonPatch(path, Op.Set(Json.Number(1)))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "empty array")
    },
    test("Elements navigation on non-array fails") {
      val original = Json.Object("a" -> Json.Number(1))
      val path     = DynamicOptic.root.elements
      val patch    = JsonPatch(path, Op.Set(Json.Number(1)))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "Expected Array")
    },
    test("Elements navigation on string fails") {
      val original = Json.String("hello")
      val path     = DynamicOptic.root.elements
      val patch    = JsonPatch(path, Op.Set(Json.Number(1)))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "Expected Array")
    },
    test("Elements navigation on number fails") {
      val original = Json.Number(42)
      val path     = DynamicOptic.root.elements
      val patch    = JsonPatch(path, Op.Set(Json.Number(1)))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "Expected Array")
    },
    test("applyToAllElements fails in Strict mode when one element fails") {
      val original = Json.Array(Json.Number(1), Json.String("not a number"), Json.Number(3))
      val path     = DynamicOptic.root.elements
      val patch    = JsonPatch(path, Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(1))))
      val result   = patch.apply(original, PatchMode.Strict)
      assertError(result, "Expected Number")
    },
    test("navigateAllElements fails in Strict mode when navigation fails for one element") {
      val original = Json.Array(
        Json.Object("x" -> Json.Number(1)),
        Json.Object("y" -> Json.Number(2)) // missing field "x"
      )
      val path   = DynamicOptic.root.elements.field("x")
      val patch  = JsonPatch(path, Op.Set(Json.Number(100)))
      val result = patch.apply(original, PatchMode.Strict)
      assertTrue(result.isLeft)
    }
  )
}
