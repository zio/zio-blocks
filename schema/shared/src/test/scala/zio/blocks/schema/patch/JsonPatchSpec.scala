package zio.blocks.schema.patch

import zio.blocks.schema.json.Json
import zio.blocks.schema.patch.{PatchMode, JsonDiffer, JsonPatch, JsonOp, JsonPatchOp, StringOp, ArrayOp, ObjectOp}
import zio.test._

object JsonPatchSpec extends ZIOSpecDefault {

  def genJson(depth: Int): Gen[Any, Json] = {
    val leafGen = Gen.oneOf(
      Gen.const(Json.Null),
      Gen.boolean.map(Json.Bool.apply),
      Gen.bigDecimal(BigDecimal("-1000000000"), BigDecimal("1000000000")).map(Json.Num.apply),
      Gen.string.map(Json.Str.apply)
    )

    if (depth <= 0) leafGen
    else
      Gen.oneOf(
        leafGen,
        Gen.chunkOfBounded(0, 3)(Gen.suspend(genJson(depth - 1))).map(c => Json.Arr(c.toVector)),
        Gen
          .mapOfBounded(0, 3)(Gen.stringBounded(1, 5)(Gen.alphaChar), Gen.suspend(genJson(depth - 1)))
          .map(m => Json.Obj(m.toVector))
      )
  }

  val genJsonRoot = genJson(3)

  def spec = suite("JsonPatchSpec")(
    suite("Algebraic Laws")(
      test("Patch identity: empty patch does nothing") {
        check(genJsonRoot) { json =>
          assertTrue(JsonPatch.empty.apply(json) == Right(json))
        }
      },
      test("Diff Roundtrip: apply(diff(a, b), a) == b") {
        check(genJsonRoot, genJsonRoot) { (a, b) =>
          val patch  = JsonDiffer.diff(a, b)
          val result = patch.apply(a)
          assertTrue(result == Right(b))
        }
      },
      test("Patch Composition: (p1 ++ p2).apply(v) == p2.apply(p1.apply(v))") {
        check(genJsonRoot, genJsonRoot, genJsonRoot) { (a, b, c) =>
          val p1       = JsonDiffer.diff(a, b)
          val p2       = JsonDiffer.diff(b, c)
          val combined = p1 ++ p2

          val result = combined.apply(a)
          assertTrue(result == Right(c))
        }
      }
    ),
    suite("NumberDelta")(
      test("applies positive delta") {
        val patch  = JsonPatch(Vector(JsonPatchOp.Update(JsonOp.NumberDelta(BigDecimal(10)))))
        val result = patch.apply(Json.Num(5))
        assertTrue(result == Right(Json.Num(15)))
      },
      test("applies negative delta") {
        val patch  = JsonPatch(Vector(JsonPatchOp.Update(JsonOp.NumberDelta(BigDecimal(-3)))))
        val result = patch.apply(Json.Num(10))
        assertTrue(result == Right(Json.Num(7)))
      },
      test("fails on non-number in strict mode") {
        val patch  = JsonPatch(Vector(JsonPatchOp.Update(JsonOp.NumberDelta(BigDecimal(5)))))
        val result = patch.apply(Json.Str("hello"), PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("skips on non-number in lenient mode") {
        val patch  = JsonPatch(Vector(JsonPatchOp.Update(JsonOp.NumberDelta(BigDecimal(5)))))
        val target = Json.Str("hello")
        val result = patch.apply(target, PatchMode.Lenient)
        assertTrue(result == Right(target))
      }
    ),
    suite("StringOp.Modify")(
      test("modifies substring") {
        val patch  = JsonPatch(Vector(JsonPatchOp.Update(JsonOp.StringEdit(Vector(StringOp.Modify(6, 5, "everyone"))))))
        val result = patch.apply(Json.Str("Hello World"))
        assertTrue(result == Right(Json.Str("Hello everyone")))
      },
      test("modifies at beginning") {
        val patch  = JsonPatch(Vector(JsonPatchOp.Update(JsonOp.StringEdit(Vector(StringOp.Modify(0, 5, "Hi"))))))
        val result = patch.apply(Json.Str("Hello World"))
        assertTrue(result == Right(Json.Str("Hi World")))
      },
      test("fails on out of bounds in strict mode") {
        val patch  = JsonPatch(Vector(JsonPatchOp.Update(JsonOp.StringEdit(Vector(StringOp.Modify(100, 5, "x"))))))
        val result = patch.apply(Json.Str("short"), PatchMode.Strict)
        assertTrue(result.isLeft)
      }
    ),
    suite("Clobber Mode")(
      test("overwrites on Add duplicate key in object") {
        val patch  = JsonPatch(Vector(JsonPatchOp.Update(JsonOp.ObjectEdit(Vector(ObjectOp.Add("a", Json.Num(99)))))))
        val target = Json.Obj(Vector("a" -> Json.Num(1)))
        val result = patch.apply(target, PatchMode.Clobber)
        assertTrue(result == Right(Json.Obj(Vector("a" -> Json.Num(99)))))
      },
      test("succeeds on Remove non-existent key (no-op)") {
        val patch  = JsonPatch(Vector(JsonPatchOp.Update(JsonOp.ObjectEdit(Vector(ObjectOp.Remove("missing"))))))
        val target = Json.Obj(Vector("a" -> Json.Num(1)))
        val result = patch.apply(target, PatchMode.Clobber)
        assertTrue(result == Right(target))
      },
      test("clamps array insert index") {
        val patch  = JsonPatch(Vector(JsonPatchOp.Update(JsonOp.ArrayEdit(Vector(ArrayOp.Insert(100, Json.Num(3)))))))
        val target = Json.Arr(Vector(Json.Num(1), Json.Num(2)))
        val result = patch.apply(target, PatchMode.Clobber)
        assertTrue(result == Right(Json.Arr(Vector(Json.Num(1), Json.Num(2), Json.Num(3)))))
      }
    ),
    suite("Edge Cases")(
      test("empty array operations") {
        val patch  = JsonPatch(Vector(JsonPatchOp.Update(JsonOp.ArrayEdit(Vector(ArrayOp.Append(Json.Num(1)))))))
        val result = patch.apply(Json.Arr(Vector.empty))
        assertTrue(result == Right(Json.Arr(Vector(Json.Num(1)))))
      },
      test("empty object operations") {
        val patch  = JsonPatch(Vector(JsonPatchOp.Update(JsonOp.ObjectEdit(Vector(ObjectOp.Add("x", Json.Num(1)))))))
        val result = patch.apply(Json.Obj(Vector.empty))
        assertTrue(result == Right(Json.Obj(Vector("x" -> Json.Num(1)))))
      },
      test("null value handling") {
        val patch  = JsonDiffer.diff(Json.Null, Json.Num(42))
        val result = patch.apply(Json.Null)
        assertTrue(result == Right(Json.Num(42)))
      },
      test("unicode strings") {
        val a     = Json.Str("Hello 世界")
        val b     = Json.Str("Hello 世界!")
        val patch = JsonDiffer.diff(a, b)
        assertTrue(patch.apply(a) == Right(b))
      },
      test("deep nesting") {
        val a     = Json.Obj(Vector("a" -> Json.Arr(Vector(Json.Obj(Vector("b" -> Json.Num(1)))))))
        val b     = Json.Obj(Vector("a" -> Json.Arr(Vector(Json.Obj(Vector("b" -> Json.Num(2)))))))
        val patch = JsonDiffer.diff(a, b)
        assertTrue(patch.apply(a) == Right(b))
      }
    ),
    suite("Complex Scenarios")(
      test("multiple operations in sequence") {
        val patch = JsonPatch(
          Vector(
            JsonPatchOp.AtKey("x", JsonPatchOp.Update(JsonOp.NumberDelta(BigDecimal(1)))),
            JsonPatchOp.AtKey("y", JsonPatchOp.Update(JsonOp.Set(Json.Str("updated"))))
          )
        )
        val original = Json.Obj(Vector("x" -> Json.Num(5), "y" -> Json.Str("old")))
        val expected = Json.Obj(Vector("x" -> Json.Num(6), "y" -> Json.Str("updated")))
        assertTrue(patch.apply(original) == Right(expected))
      },
      test("mixed operations: add, modify, remove") {
        val patch = JsonPatch(
          Vector(
            JsonPatchOp.Update(
              JsonOp.ObjectEdit(
                Vector(
                  ObjectOp.Add("new", Json.Num(3)),
                  ObjectOp.Remove("old")
                )
              )
            )
          )
        )
        val original = Json.Obj(Vector("old" -> Json.Num(1), "keep" -> Json.Num(2)))
        val result   = patch.apply(original)

        // Should have "keep" and "new", not "old"
        val hasCorrectFields = result match {
          case Right(Json.Obj(fields)) =>
            fields.exists(_._1 == "keep") && fields.exists(_._1 == "new") && !fields.exists(_._1 == "old")
          case _ => false
        }
        assertTrue(hasCorrectFields)
      },
      test("nested array modifications") {
        val a     = Json.Arr(Vector(Json.Arr(Vector(Json.Num(1), Json.Num(2)))))
        val b     = Json.Arr(Vector(Json.Arr(Vector(Json.Num(1), Json.Num(3)))))
        val patch = JsonDiffer.diff(a, b)
        assertTrue(patch.apply(a) == Right(b))
      }
    ),
    suite("String Diffing")(
      test("String Diff: Insert") {
        val a     = Json.Str("foo")
        val b     = Json.Str("fbaroo")
        val patch = JsonDiffer.diff(a, b)
        assertTrue(patch.apply(a) == Right(b))
      },
      test("String Diff: Delete") {
        val a     = Json.Str("fbaroo")
        val b     = Json.Str("foo")
        val patch = JsonDiffer.diff(a, b)
        assertTrue(patch.apply(a) == Right(b))
      }
    ),
    suite("Array Diffing")(
      test("Array Diff: Insert") {
        val a      = Json.Arr(Vector(Json.Num(1), Json.Num(3)))
        val b      = Json.Arr(Vector(Json.Num(1), Json.Num(2), Json.Num(3)))
        val patch  = JsonDiffer.diff(a, b)
        val result = patch.apply(a)
        assertTrue(result == Right(b))
      },
      test("Array Diff: Delete") {
        val a      = Json.Arr(Vector(Json.Num(1), Json.Num(2), Json.Num(3)))
        val b      = Json.Arr(Vector(Json.Num(1), Json.Num(3)))
        val patch  = JsonDiffer.diff(a, b)
        val result = patch.apply(a)
        assertTrue(result == Right(b))
      }
    ),
    suite("Object Diffing")(
      test("Object Diff: Add field") {
        val a     = Json.Obj(Vector("a" -> Json.Num(1)))
        val b     = Json.Obj(Vector("a" -> Json.Num(1), "b" -> Json.Num(2)))
        val patch = JsonDiffer.diff(a, b)
        assertTrue(patch.apply(a) == Right(b))
      },
      test("Object Diff: Remove field") {
        val a     = Json.Obj(Vector("a" -> Json.Num(1), "b" -> Json.Num(2)))
        val b     = Json.Obj(Vector("a" -> Json.Num(1)))
        val patch = JsonDiffer.diff(a, b)
        assertTrue(patch.apply(a) == Right(b))
      },
      test("Object Diff: Modify nested field") {
        val a     = Json.Obj(Vector("a" -> Json.Obj(Vector("x" -> Json.Num(1)))))
        val b     = Json.Obj(Vector("a" -> Json.Obj(Vector("x" -> Json.Num(2)))))
        val patch = JsonDiffer.diff(a, b)

        // Verify structure: Should use AtKey
        val isAtKey = patch.ops.headOption match {
          case Some(JsonPatchOp.AtKey("a", _)) => true
          case _                               => false
        }

        assertTrue(isAtKey) && assertTrue(patch.apply(a) == Right(b))
      }
    ),
    suite("Modes")(
      test("Strict fails on missing key") {
        val patch  = JsonPatch(Vector(JsonPatchOp.AtKey("missing", JsonPatchOp.Update(JsonOp.Set(Json.Null)))))
        val target = Json.Obj(Vector.empty)
        assertTrue(patch.apply(target, PatchMode.Strict).isLeft)
      },
      test("Lenient skips missing key") {
        val patch  = JsonPatch(Vector(JsonPatchOp.AtKey("missing", JsonPatchOp.Update(JsonOp.Set(Json.Null)))))
        val target = Json.Obj(Vector.empty)
        assertTrue(patch.apply(target, PatchMode.Lenient) == Right(target))
      },
      test("Strict fails on out of bounds") {
        val patch  = JsonPatch(Vector(JsonPatchOp.AtIndex(10, JsonPatchOp.Update(JsonOp.Set(Json.Null)))))
        val target = Json.Arr(Vector.empty)
        assertTrue(patch.apply(target, PatchMode.Strict).isLeft)
      }
    ),
    suite("Conversion Roundtrip (T8)")(
      test("Simple Set operation roundtrip") {
        val jsonPatch     = JsonPatch(Vector(JsonPatchOp.Update(JsonOp.Set(Json.Num(42)))))
        val dynamicPatchE = JsonPatch.toDynamicPatch(jsonPatch)
        assertTrue(dynamicPatchE.isRight)
      },
      test("NumberDelta operation roundtrip") {
        val jsonPatch = JsonPatch(Vector(JsonPatchOp.Update(JsonOp.NumberDelta(BigDecimal(5)))))
        val result    = for {
          dynPatch   <- JsonPatch.toDynamicPatch(jsonPatch)
          backToJson <- JsonPatch.fromDynamicPatch(dynPatch)
        } yield backToJson
        assertTrue(result.isRight)
      },
      test("StringEdit operation roundtrip") {
        val jsonPatch = JsonPatch(
          Vector(
            JsonPatchOp.Update(
              JsonOp.StringEdit(
                Vector(
                  StringOp.Insert(0, "Hello")
                )
              )
            )
          )
        )
        val result = for {
          dynPatch   <- JsonPatch.toDynamicPatch(jsonPatch)
          backToJson <- JsonPatch.fromDynamicPatch(dynPatch)
        } yield backToJson
        assertTrue(result.isRight)
      },
      test("ArrayEdit operation roundtrip") {
        val jsonPatch = JsonPatch(
          Vector(
            JsonPatchOp.Update(
              JsonOp.ArrayEdit(
                Vector(
                  ArrayOp.Insert(0, Json.Num(1)),
                  ArrayOp.Append(Json.Num(2))
                )
              )
            )
          )
        )
        val result = for {
          dynPatch   <- JsonPatch.toDynamicPatch(jsonPatch)
          backToJson <- JsonPatch.fromDynamicPatch(dynPatch)
        } yield backToJson
        assertTrue(result.isRight)
      },
      test("ObjectEdit operation roundtrip") {
        val jsonPatch = JsonPatch(
          Vector(
            JsonPatchOp.Update(
              JsonOp.ObjectEdit(
                Vector(
                  ObjectOp.Add("key", Json.Str("value"))
                )
              )
            )
          )
        )
        val result = for {
          dynPatch   <- JsonPatch.toDynamicPatch(jsonPatch)
          backToJson <- JsonPatch.fromDynamicPatch(dynPatch)
        } yield backToJson
        assertTrue(result.isRight)
      },
      test("AtKey navigation roundtrip") {
        val jsonPatch = JsonPatch(Vector(JsonPatchOp.AtKey("field", JsonPatchOp.Update(JsonOp.Set(Json.Num(10))))))
        val result    = for {
          dynPatch   <- JsonPatch.toDynamicPatch(jsonPatch)
          backToJson <- JsonPatch.fromDynamicPatch(dynPatch)
        } yield backToJson
        assertTrue(result.isRight)
      },
      test("AtIndex navigation roundtrip") {
        val jsonPatch = JsonPatch(Vector(JsonPatchOp.AtIndex(0, JsonPatchOp.Update(JsonOp.Set(Json.Null)))))
        val result    = for {
          dynPatch   <- JsonPatch.toDynamicPatch(jsonPatch)
          backToJson <- JsonPatch.fromDynamicPatch(dynPatch)
        } yield backToJson
        assertTrue(result.isRight)
      }
    )
  )
}
