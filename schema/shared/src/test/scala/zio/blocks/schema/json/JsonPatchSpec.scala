package zio.blocks.schema.json

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.SchemaBaseSpec
import zio.test._

object JsonPatchSpec extends SchemaBaseSpec {

  val genJsonNull: Gen[Any, Json] = Gen.const(Json.Null)
  val genJsonBool: Gen[Any, Json] = Gen.boolean.map(Json.Bool)
  val genJsonNum: Gen[Any, Json]  = Gen.oneOf(
    Gen.int.map(i => Json.Num(i.toString)),
    Gen.long.map(l => Json.Num(l.toString)),
    Gen.double.map(d => Json.Num(d.toString))
  )
  val genJsonStr: Gen[Any, Json]       = Gen.alphaNumericStringBounded(0, 20).map(Json.Str)
  val genJsonPrimitive: Gen[Any, Json] = Gen.oneOf(genJsonNull, genJsonBool, genJsonNum, genJsonStr)

  def genJsonArr(depth: Int): Gen[Any, Json] =
    if (depth <= 0) genJsonPrimitive
    else Gen.listOfBounded(0, 3)(genJson(depth - 1)).map(ls => Json.Arr(ls.toVector))

  def genJsonObj(depth: Int): Gen[Any, Json] =
    if (depth <= 0) genJsonPrimitive
    else
      Gen
        .listOfBounded(0, 3) {
          for {
            key   <- Gen.alphaNumericStringBounded(1, 10)
            value <- genJson(depth - 1)
          } yield (key, value)
        }
        .map(fields => Json.Obj(fields.toVector))

  def genJson(depth: Int): Gen[Any, Json] =
    if (depth <= 0) genJsonPrimitive
    else Gen.oneOf(genJsonPrimitive, genJsonArr(depth), genJsonObj(depth))

  val genJsonValue: Gen[Any, Json] = genJson(2)

  def spec = suite("JsonPatchSpec")(
    suite("Monoid Laws")(
      test("left identity: (empty ++ p)(j) == p(j)") {
        check(genJsonValue, genJsonValue) { (old, new_) =>
          val patch    = JsonPatch.diff(old, new_)
          val composed = JsonPatch.empty ++ patch
          val result1  = composed(old, JsonPatchMode.Strict)
          val result2  = patch(old, JsonPatchMode.Strict)
          assertTrue(result1 == result2)
        }
      },
      test("right identity: (p ++ empty)(j) == p(j)") {
        check(genJsonValue, genJsonValue) { (old, new_) =>
          val patch    = JsonPatch.diff(old, new_)
          val composed = patch ++ JsonPatch.empty
          val result1  = composed(old, JsonPatchMode.Strict)
          val result2  = patch(old, JsonPatchMode.Strict)
          assertTrue(result1 == result2)
        }
      },
      test("associativity: ((p1 ++ p2) ++ p3)(j) == (p1 ++ (p2 ++ p3))(j)") {
        check(genJsonValue, genJsonValue, genJsonValue, genJsonValue) { (v0, v1, v2, v3) =>
          val p1      = JsonPatch.diff(v0, v1)
          val p2      = JsonPatch.diff(v1, v2)
          val p3      = JsonPatch.diff(v2, v3)
          val left    = (p1 ++ p2) ++ p3
          val right   = p1 ++ (p2 ++ p3)
          val result1 = left(v0, JsonPatchMode.Strict)
          val result2 = right(v0, JsonPatchMode.Strict)
          assertTrue(result1 == result2)
        }
      }
    ),
    suite("Diff/Apply Laws")(
      test("roundtrip: diff(a, b)(a) == Right(b)") {
        check(genJsonValue, genJsonValue) { (source, target) =>
          val patch  = JsonPatch.diff(source, target)
          val result = patch(source, JsonPatchMode.Strict)
          assertTrue(result == Right(target))
        }
      },
      test("identity diff: diff(j, j).isEmpty") {
        check(genJsonValue) { json =>
          val patch = JsonPatch.diff(json, json)
          assertTrue(patch.isEmpty)
        }
      },
      test("diff composition: (diff(a, b) ++ diff(b, c))(a) == Right(c)") {
        check(genJsonValue, genJsonValue, genJsonValue) { (a, b, c) =>
          val p1       = JsonPatch.diff(a, b)
          val p2       = JsonPatch.diff(b, c)
          val composed = p1 ++ p2
          val result   = composed(a, JsonPatchMode.Strict)
          assertTrue(result == Right(c))
        }
      }
    ),
    suite("PatchMode Laws")(
      test("lenient subsumes strict") {
        check(genJsonValue, genJsonValue) { (source, target) =>
          val patch        = JsonPatch.diff(source, target)
          val strictResult = patch(source, JsonPatchMode.Strict)
          strictResult match {
            case Right(r) =>
              val lenientResult = patch(source, JsonPatchMode.Lenient)
              assertTrue(lenientResult == Right(r))
            case Left(_) => assertTrue(true)
          }
        }
      }
    ),
    suite("Operation Types")(
      test("Op.Set replaces value") {
        val json   = Json.Num(10)
        val patch  = JsonPatch.root(JsonPatch.Op.Set(Json.Num(42)))
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result == Right(Json.Num(42)))
      },
      test("Op.PrimitiveDelta - NumberDelta") {
        val json   = Json.Num(10)
        val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result == Right(Json.Num("15")))
      },
      test("Op.PrimitiveDelta - StringEdit") {
        val json  = Json.Str("hello")
        val patch = JsonPatch.root(
          JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Append(" world"))))
        )
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result == Right(Json.Str("hello world")))
      },
      test("Op.ArrayEdit - Insert") {
        val json  = Json.Arr(Vector(Json.Num(1), Json.Num(2)))
        val patch = JsonPatch.root(
          JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Insert(1, Vector(Json.Num(10)))))
        )
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result == Right(Json.Arr(Vector(Json.Num(1), Json.Num(10), Json.Num(2)))))
      },
      test("Op.ObjectEdit - Add") {
        val json  = Json.Obj(Vector("a" -> Json.Num(1)))
        val patch = JsonPatch.root(
          JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Add("b", Json.Num(2))))
        )
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result == Right(Json.Obj(Vector("a" -> Json.Num(1), "b" -> Json.Num(2)))))
      }
    ),
    suite("ArrayOp Variants")(
      test("Append") {
        val json   = Json.Arr(Vector(Json.Num(1)))
        val patch  = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Append(Vector(Json.Num(2))))))
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result == Right(Json.Arr(Vector(Json.Num(1), Json.Num(2)))))
      },
      test("Delete") {
        val json   = Json.Arr(Vector(Json.Num(1), Json.Num(2), Json.Num(3)))
        val patch  = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Delete(1, 1))))
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result == Right(Json.Arr(Vector(Json.Num(1), Json.Num(3)))))
      },
      test("Modify") {
        val json  = Json.Arr(Vector(Json.Num(1), Json.Num(2)))
        val patch = JsonPatch.root(
          JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Modify(1, JsonPatch.Op.Set(Json.Num(20)))))
        )
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result == Right(Json.Arr(Vector(Json.Num(1), Json.Num(20)))))
      }
    ),
    suite("ObjectOp Variants")(
      test("Add") {
        val json   = Json.Obj(Vector("a" -> Json.Num(1)))
        val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Add("b", Json.Num(2)))))
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result == Right(Json.Obj(Vector("a" -> Json.Num(1), "b" -> Json.Num(2)))))
      },
      test("Remove") {
        val json   = Json.Obj(Vector("a" -> Json.Num(1), "b" -> Json.Num(2)))
        val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Remove("a"))))
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result == Right(Json.Obj(Vector("b" -> Json.Num(2)))))
      }
    ),
    suite("StringOp Variants")(
      test("Insert") {
        val json  = Json.Str("hello")
        val patch = JsonPatch.root(
          JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Insert(5, " world"))))
        )
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result == Right(Json.Str("hello world")))
      },
      test("Delete") {
        val json  = Json.Str("hello world")
        val patch = JsonPatch.root(
          JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Delete(5, 6))))
        )
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result == Right(Json.Str("hello")))
      },
      test("Modify") {
        val json  = Json.Str("hello")
        val patch = JsonPatch.root(
          JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Modify(0, 5, "hi"))))
        )
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result == Right(Json.Str("hi")))
      }
    ),
    suite("PatchMode Behaviors")(
      test("Strict fails on missing field") {
        val json   = Json.Obj(Vector("a" -> Json.Num(1)))
        val patch  = JsonPatch(DynamicOptic.root.field("nonexistent"), JsonPatch.Op.Set(Json.Num(42)))
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("Lenient skips on missing field") {
        val json   = Json.Obj(Vector("a" -> Json.Num(1)))
        val patch  = JsonPatch(DynamicOptic.root.field("nonexistent"), JsonPatch.Op.Set(Json.Num(42)))
        val result = patch(json, JsonPatchMode.Lenient)
        assertTrue(result == Right(json))
      },
      test("Clobber overwrites existing on Add") {
        val json          = Json.Obj(Vector("a" -> Json.Num(1)))
        val patch         = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Add("a", Json.Num(100)))))
        val strictResult  = patch(json, JsonPatchMode.Strict)
        val clobberResult = patch(json, JsonPatchMode.Clobber)
        assertTrue(strictResult.isLeft && clobberResult == Right(Json.Obj(Vector("a" -> Json.Num(100)))))
      }
    ),
    suite("Edge Cases")(
      test("Empty array roundtrip") {
        val source = Json.Arr.empty
        val target = Json.Arr(Vector(Json.Num(1), Json.Num(2)))
        val patch  = JsonPatch.diff(source, target)
        val result = patch(source, JsonPatchMode.Strict)
        assertTrue(result == Right(target))
      },
      test("Empty object roundtrip") {
        val source = Json.Obj.empty
        val target = Json.Obj(Vector("a" -> Json.Num(1)))
        val patch  = JsonPatch.diff(source, target)
        val result = patch(source, JsonPatchMode.Strict)
        assertTrue(result == Right(target))
      },
      test("Empty string roundtrip") {
        val source = Json.Str("")
        val target = Json.Str("hello")
        val patch  = JsonPatch.diff(source, target)
        val result = patch(source, JsonPatchMode.Strict)
        assertTrue(result == Right(target))
      },
      test("Nested structures roundtrip") {
        val source = Json.Obj(Vector("user" -> Json.Obj(Vector("name" -> Json.Str("Alice")))))
        val target = Json.Obj(Vector("user" -> Json.Obj(Vector("name" -> Json.Str("Bob")))))
        val patch  = JsonPatch.diff(source, target)
        val result = patch(source, JsonPatchMode.Strict)
        assertTrue(result == Right(target))
      }
    ),
    suite("Error Cases")(
      test("Type mismatch - number delta on string") {
        val json   = Json.Str("hello")
        val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("Array index out of bounds") {
        val json   = Json.Arr(Vector(Json.Num(1), Json.Num(2)))
        val patch  = JsonPatch(DynamicOptic.root.at(10), JsonPatch.Op.Set(Json.Num(42)))
        val result = patch(json, JsonPatchMode.Strict)
        assertTrue(result.isLeft)
      }
    ),
    suite("Empty Patch Identity")(
      test("Empty patch returns unchanged value") {
        check(genJsonValue) { json =>
          val empty  = JsonPatch.empty
          val result = empty(json, JsonPatchMode.Strict)
          assertTrue(result == Right(json))
        }
      }
    )
  )
}
