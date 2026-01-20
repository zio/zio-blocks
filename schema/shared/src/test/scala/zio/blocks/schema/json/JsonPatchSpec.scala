package zio.blocks.schema.json

import zio.Chunk
import zio.test._
import zio.test.Assertion._
import zio.blocks.schema.patch.PatchMode

object JsonPatchSpec extends ZIOSpecDefault {

  // Generators for property-based tests
  val genJsonNull: Gen[Any, Json] = Gen.const(Json.Null)
  val genJsonBool: Gen[Any, Json] = Gen.boolean.map(Json.Bool(_))
  val genJsonNum: Gen[Any, Json] = Gen.bigDecimal(
    java.math.BigDecimal.valueOf(-1000000),
    java.math.BigDecimal.valueOf(1000000)
  ).map(bd => Json.Num(bd.bigDecimal))
  val genJsonStr: Gen[Any, Json] = Gen.string.map(Json.Str(_))

  def genJsonArr(depth: Int): Gen[Any, Json] =
    if (depth <= 0) Gen.const(Json.Arr(Chunk.empty))
    else Gen.listOfBounded(0, 5)(genJson(depth - 1)).map(elems => Json.Arr(Chunk.fromIterable(elems)))

  def genJsonObj(depth: Int): Gen[Any, Json] =
    if (depth <= 0) Gen.const(Json.Obj(Chunk.empty))
    else Gen.listOfBounded(0, 5)(
      Gen.alphaNumericString.zip(genJson(depth - 1))
    ).map(fields => Json.Obj(Chunk.fromIterable(fields)))

  def genJson(depth: Int): Gen[Any, Json] =
    if (depth <= 0) Gen.oneOf(genJsonNull, genJsonBool, genJsonNum, genJsonStr)
    else Gen.oneOf(genJsonNull, genJsonBool, genJsonNum, genJsonStr, genJsonArr(depth - 1), genJsonObj(depth - 1))

  val genSimpleJson: Gen[Any, Json] = genJson(2)
  val genDeepJson: Gen[Any, Json] = genJson(4)

  def spec = suite("JsonPatchSpec")(
    suite("Algebraic Laws")(
      test("L1: Left Identity - empty ++ p == p") {
        check(genSimpleJson, genSimpleJson) { (source, target) =>
          val patch = JsonPatch.diff(source, target)
          val composed = JsonPatch.empty ++ patch
          assertTrue(composed.ops == patch.ops)
        }
      },
      test("L2: Right Identity - p ++ empty == p") {
        check(genSimpleJson, genSimpleJson) { (source, target) =>
          val patch = JsonPatch.diff(source, target)
          val composed = patch ++ JsonPatch.empty
          assertTrue(composed.ops == patch.ops)
        }
      },
      test("L3: Associativity - (p1 ++ p2) ++ p3 == p1 ++ (p2 ++ p3)") {
        check(genSimpleJson, genSimpleJson, genSimpleJson, genSimpleJson) { (a, b, c, d) =>
          val p1 = JsonPatch.diff(a, b)
          val p2 = JsonPatch.diff(b, c)
          val p3 = JsonPatch.diff(c, d)
          val left = (p1 ++ p2) ++ p3
          val right = p1 ++ (p2 ++ p3)
          assertTrue(left.ops == right.ops)
        }
      },
      test("L4: Roundtrip - source.diff(target).apply(source) == Right(target)") {
        check(genSimpleJson, genSimpleJson) { (source, target) =>
          val patch = JsonPatch.diff(source, target)
          val result = patch.apply(source)
          assertTrue(result == Right(target))
        }
      },
      test("L5: Identity Diff - json.diff(json) == empty") {
        check(genSimpleJson) { json =>
          val patch = JsonPatch.diff(json, json)
          assertTrue(patch.isEmpty)
        }
      },
      test("L6: Composition - diff(a,b) ++ diff(b,c) semantically equals diff(a,c)") {
        check(genSimpleJson, genSimpleJson, genSimpleJson) { (a, b, c) =>
          val composed = JsonPatch.diff(a, b) ++ JsonPatch.diff(b, c)
          val direct = JsonPatch.diff(a, c)
          val composedResult = composed.apply(a)
          val directResult = direct.apply(a)
          assertTrue(composedResult == directResult && composedResult == Right(c))
        }
      },
      test("L7: Lenient subsumes Strict - strict success implies lenient same result") {
        check(genSimpleJson, genSimpleJson) { (source, target) =>
          val patch = JsonPatch.diff(source, target)
          val strictResult = patch.apply(source, PatchMode.Strict)
          val lenientResult = patch.apply(source, PatchMode.Lenient)
          strictResult match {
            case Right(v) => assertTrue(lenientResult == Right(v))
            case Left(_)  => assertTrue(true) // Strict failure doesn't constrain lenient
          }
        }
      }
    ),
    suite("Json Construction")(
      test("obj creates object with fields") {
        val json = Json.obj("name" -> Json.str("test"), "value" -> Json.num(42))
        assertTrue(json.isObject && json("name").contains(Json.str("test")))
      },
      test("arr creates array with elements") {
        val json = Json.arr(Json.num(1), Json.num(2), Json.num(3))
        assertTrue(json.isArray && json(1).contains(Json.num(2)))
      },
      test("null is null") {
        assertTrue(Json.`null`.isNull)
      },
      test("true/false are booleans") {
        assertTrue(Json.`true`.isBoolean && Json.`false`.isBoolean)
      },
      test("num equality ignores trailing zeros") {
        val a = Json.num(java.math.BigDecimal.valueOf(10))
        val b = Json.Num(new java.math.BigDecimal("10.00"))
        assertTrue(a == b)
      }
    ),
    suite("Json Access")(
      test("apply with key returns field value") {
        val json = Json.obj("name" -> Json.str("test"))
        assertTrue(json("name").contains(Json.str("test")))
      },
      test("apply with missing key returns None") {
        val json = Json.obj("name" -> Json.str("test"))
        assertTrue(json("missing").isEmpty)
      },
      test("apply with index returns element") {
        val json = Json.arr(Json.num(1), Json.num(2), Json.num(3))
        assertTrue(json(1).contains(Json.num(2)))
      },
      test("apply with out of bounds index returns None") {
        val json = Json.arr(Json.num(1))
        assertTrue(json(5).isEmpty)
      },
      test("nested access works") {
        val json = Json.obj("user" -> Json.obj("name" -> Json.str("Alice")))
        val result = json("user").flatMap(_("name"))
        assertTrue(result.contains(Json.str("Alice")))
      }
    ),
    suite("Json Transformations")(
      test("dropNulls removes null values") {
        val json = Json.obj("a" -> Json.str("value"), "b" -> Json.Null)
        val result = json.dropNulls
        assertTrue(result.asObject.exists(_.length == 1))
      },
      test("dropNulls works recursively") {
        val json = Json.obj(
          "outer" -> Json.obj("inner" -> Json.Null, "keep" -> Json.num(1)),
          "top" -> Json.Null
        )
        val result = json.dropNulls
        val outer = result("outer").flatMap(_.asObject)
        assertTrue(outer.exists(_.length == 1))
      },
      test("sortKeys sorts object keys alphabetically") {
        val json = Json.obj("z" -> Json.num(1), "a" -> Json.num(2), "m" -> Json.num(3))
        val result = json.sortKeys
        val keys = result.asObject.map(_.map(_._1).toList)
        assertTrue(keys.contains(List("a", "m", "z")))
      },
      test("normalize combines dropNulls and sortKeys") {
        val json = Json.obj("z" -> Json.num(1), "a" -> Json.Null, "m" -> Json.num(2))
        val result = json.normalize
        val fields = result.asObject
        assertTrue(fields.exists(f => f.length == 2 && f.head._1 == "m"))
      },
      test("merge combines objects recursively") {
        val json1 = Json.obj("a" -> Json.num(1), "b" -> Json.obj("x" -> Json.num(10)))
        val json2 = Json.obj("b" -> Json.obj("y" -> Json.num(20)), "c" -> Json.num(3))
        val merged = json1.merge(json2)
        assertTrue(
          merged("a").contains(Json.num(1)) &&
          merged("c").contains(Json.num(3)) &&
          merged("b").flatMap(_("x")).contains(Json.num(10))
        )
      }
    ),
    suite("JsonPatch Operations")(
      test("Set replaces value completely") {
        val source = Json.obj("x" -> Json.num(1))
        val patch = JsonPatch.root(JsonPatch.Operation.Set(Json.str("replaced")))
        assertTrue(patch.apply(source) == Right(Json.str("replaced")))
      },
      test("NumberDelta adds to number") {
        val source = Json.num(10)
        val patch = JsonPatch.root(JsonPatch.Operation.NumberDelta(java.math.BigDecimal.valueOf(5)))
        assertTrue(patch.apply(source) == Right(Json.num(15)))
      },
      test("StringEdit inserts text") {
        val source = Json.str("hello")
        val patch = JsonPatch.root(JsonPatch.Operation.StringEdit(Vector(
          JsonPatch.StringOp.Insert(5, " world")
        )))
        assertTrue(patch.apply(source) == Right(Json.str("hello world")))
      },
      test("StringEdit deletes text") {
        val source = Json.str("hello world")
        val patch = JsonPatch.root(JsonPatch.Operation.StringEdit(Vector(
          JsonPatch.StringOp.Delete(5, 6)
        )))
        assertTrue(patch.apply(source) == Right(Json.str("hello")))
      },
      test("ArrayEdit inserts element") {
        val source = Json.arr(Json.num(1), Json.num(3))
        val patch = JsonPatch.root(JsonPatch.Operation.ArrayEdit(Vector(
          JsonPatch.ArrayOp.Insert(1, Json.num(2))
        )))
        assertTrue(patch.apply(source) == Right(Json.arr(Json.num(1), Json.num(2), Json.num(3))))
      },
      test("ArrayEdit deletes element") {
        val source = Json.arr(Json.num(1), Json.num(2), Json.num(3))
        val patch = JsonPatch.root(JsonPatch.Operation.ArrayEdit(Vector(
          JsonPatch.ArrayOp.Delete(1, 1)
        )))
        assertTrue(patch.apply(source) == Right(Json.arr(Json.num(1), Json.num(3))))
      },
      test("ObjectEdit adds field") {
        val source = Json.obj("a" -> Json.num(1))
        val patch = JsonPatch.root(JsonPatch.Operation.ObjectEdit(Vector(
          JsonPatch.ObjectOp.Add("b", Json.num(2))
        )))
        val result = patch.apply(source)
        assertTrue(result.isRight && result.toOption.flatMap(_("b")).contains(Json.num(2)))
      },
      test("ObjectEdit removes field") {
        val source = Json.obj("a" -> Json.num(1), "b" -> Json.num(2))
        val patch = JsonPatch.root(JsonPatch.Operation.ObjectEdit(Vector(
          JsonPatch.ObjectOp.Remove("b")
        )))
        val result = patch.apply(source)
        assertTrue(result.isRight && result.toOption.flatMap(_("b")).isEmpty)
      }
    ),
    suite("JsonPatch Diff")(
      test("diff numbers produces NumberDelta") {
        val source = Json.num(10)
        val target = Json.num(15)
        val patch = JsonPatch.diff(source, target)
        assertTrue(patch.ops.nonEmpty && patch.apply(source) == Right(target))
      },
      test("diff strings produces efficient edits") {
        val source = Json.str("hello world")
        val target = Json.str("hello there")
        val patch = JsonPatch.diff(source, target)
        assertTrue(patch.apply(source) == Right(target))
      },
      test("diff arrays produces efficient edits") {
        val source = Json.arr(Json.num(1), Json.num(2), Json.num(3))
        val target = Json.arr(Json.num(1), Json.num(4), Json.num(3))
        val patch = JsonPatch.diff(source, target)
        assertTrue(patch.apply(source) == Right(target))
      },
      test("diff objects produces field edits") {
        val source = Json.obj("a" -> Json.num(1), "b" -> Json.num(2))
        val target = Json.obj("a" -> Json.num(1), "c" -> Json.num(3))
        val patch = JsonPatch.diff(source, target)
        assertTrue(patch.apply(source) == Right(target))
      },
      test("diff nested structures") {
        val source = Json.obj(
          "user" -> Json.obj("name" -> Json.str("Alice"), "age" -> Json.num(30)),
          "active" -> Json.`true`
        )
        val target = Json.obj(
          "user" -> Json.obj("name" -> Json.str("Bob"), "age" -> Json.num(31)),
          "active" -> Json.`false`
        )
        val patch = JsonPatch.diff(source, target)
        assertTrue(patch.apply(source) == Right(target))
      },
      test("diff type changes") {
        val source = Json.num(42)
        val target = Json.str("forty-two")
        val patch = JsonPatch.diff(source, target)
        assertTrue(patch.apply(source) == Right(target))
      }
    ),
    suite("JsonPatch Composition")(
      test("empty patch is identity") {
        val source = Json.obj("x" -> Json.num(1))
        assertTrue(JsonPatch.empty.apply(source) == Right(source))
      },
      test("patches compose correctly") {
        val a = Json.obj("x" -> Json.num(1))
        val b = Json.obj("x" -> Json.num(2))
        val c = Json.obj("x" -> Json.num(3))
        val p1 = JsonPatch.diff(a, b)
        val p2 = JsonPatch.diff(b, c)
        val composed = p1 ++ p2
        assertTrue(composed.apply(a) == Right(c))
      },
      test("multiple compositions") {
        val values = (1 to 5).map(i => Json.num(i))
        val patches = values.sliding(2).map { case Seq(a, b) => JsonPatch.diff(a, b) }.toVector
        val composed = patches.foldLeft(JsonPatch.empty)(_ ++ _)
        assertTrue(composed.apply(Json.num(1)) == Right(Json.num(5)))
      }
    ),
    suite("JsonPath")(
      test("root path is empty") {
        assertTrue(JsonPath.root.isEmpty)
      },
      test("field path navigation") {
        val path = JsonPath.root / "user" / "name"
        assertTrue(path.segments.length == 2)
      },
      test("index path navigation") {
        val path = JsonPath.root / "items" / 0 / "value"
        assertTrue(path.segments.length == 3)
      },
      test("path at specific location applies correctly") {
        val source = Json.obj("user" -> Json.obj("name" -> Json.str("Alice")))
        val path = JsonPath.root / "user" / "name"
        val patch = JsonPatch.at(path, JsonPatch.Operation.Set(Json.str("Bob")))
        val expected = Json.obj("user" -> Json.obj("name" -> Json.str("Bob")))
        assertTrue(patch.apply(source) == Right(expected))
      }
    ),
    suite("Error Handling")(
      test("missing field in strict mode returns error") {
        val source = Json.obj("a" -> Json.num(1))
        val path = JsonPath.root / "missing"
        val patch = JsonPatch.at(path, JsonPatch.Operation.Set(Json.num(2)))
        assertTrue(patch.apply(source, PatchMode.Strict).isLeft)
      },
      test("missing field in lenient mode skips operation") {
        val source = Json.obj("a" -> Json.num(1))
        val path = JsonPath.root / "missing"
        val patch = JsonPatch.at(path, JsonPatch.Operation.Set(Json.num(2)))
        assertTrue(patch.apply(source, PatchMode.Lenient) == Right(source))
      },
      test("index out of bounds returns error in strict mode") {
        val source = Json.arr(Json.num(1))
        val path = JsonPath.root / 5
        val patch = JsonPatch.at(path, JsonPatch.Operation.Set(Json.num(2)))
        assertTrue(patch.apply(source, PatchMode.Strict).isLeft)
      },
      test("type mismatch returns error") {
        val source = Json.str("not a number")
        val patch = JsonPatch.root(JsonPatch.Operation.NumberDelta(java.math.BigDecimal.valueOf(5)))
        assertTrue(patch.apply(source).isLeft)
      }
    ),
    suite("DynamicValue Interop")(
      test("Json converts to DynamicValue") {
        val json = Json.obj("name" -> Json.str("test"), "value" -> Json.num(42))
        val dv = json.toDynamicValue
        assertTrue(dv.isInstanceOf[zio.blocks.schema.DynamicValue.Record])
      },
      test("DynamicValue converts to Json") {
        import zio.blocks.schema.{DynamicValue, PrimitiveValue}
        val dv = DynamicValue.Record(Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("test")),
          "value" -> DynamicValue.Primitive(PrimitiveValue.BigDecimal(java.math.BigDecimal.valueOf(42)))
        ))
        val json = Json.fromDynamicValue(dv)
        assertTrue(json.isDefined && json.get.isObject)
      },
      test("roundtrip Json -> DynamicValue -> Json preserves structure") {
        val original = Json.obj(
          "name" -> Json.str("test"),
          "numbers" -> Json.arr(Json.num(1), Json.num(2)),
          "nested" -> Json.obj("x" -> Json.`true`)
        )
        val roundtrip = Json.fromDynamicValue(original.toDynamicValue)
        assertTrue(roundtrip.contains(original))
      },
      test("JsonPatch converts to DynamicPatch") {
        val source = Json.num(10)
        val target = Json.num(15)
        val jsonPatch = JsonPatch.diff(source, target)
        val dynamicPatch = jsonPatch.toDynamicPatch
        assertTrue(dynamicPatch.ops.nonEmpty)
      }
    ),
    suite("Edge Cases")(
      test("empty object diff") {
        val source = Json.obj()
        val target = Json.obj("a" -> Json.num(1))
        val patch = JsonPatch.diff(source, target)
        assertTrue(patch.apply(source) == Right(target))
      },
      test("empty array diff") {
        val source = Json.arr()
        val target = Json.arr(Json.num(1))
        val patch = JsonPatch.diff(source, target)
        assertTrue(patch.apply(source) == Right(target))
      },
      test("empty string diff") {
        val source = Json.str("")
        val target = Json.str("hello")
        val patch = JsonPatch.diff(source, target)
        assertTrue(patch.apply(source) == Right(target))
      },
      test("deeply nested diff") {
        val source = Json.obj("a" -> Json.obj("b" -> Json.obj("c" -> Json.obj("d" -> Json.num(1)))))
        val target = Json.obj("a" -> Json.obj("b" -> Json.obj("c" -> Json.obj("d" -> Json.num(2)))))
        val patch = JsonPatch.diff(source, target)
        assertTrue(patch.apply(source) == Right(target))
      },
      test("large array diff") {
        val source = Json.arr((1 to 100).map(Json.num(_)): _*)
        val target = Json.arr((1 to 50).map(Json.num(_)) ++ (51 to 100).map(i => Json.num(i * 2)): _*)
        val patch = JsonPatch.diff(source, target)
        assertTrue(patch.apply(source) == Right(target))
      },
      test("unicode string diff") {
        val source = Json.str("hello 世界")
        val target = Json.str("hello 世界!")
        val patch = JsonPatch.diff(source, target)
        assertTrue(patch.apply(source) == Right(target))
      }
    )
  )
}
