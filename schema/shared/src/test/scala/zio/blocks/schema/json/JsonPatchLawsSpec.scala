package zio.blocks.schema.json

import zio.blocks.schema.SchemaBaseSpec
import zio.test._

object JsonPatchLawsSpec extends SchemaBaseSpec {

  // Generators for Json values
  val genJsonNull: Gen[Any, Json] = Gen.const(Json.Null)

  val genJsonBoolean: Gen[Any, Json] = Gen.boolean.map(Json.Boolean(_))

  val genJsonNumber: Gen[Any, Json] =
    Gen.bigDecimal(BigDecimal(-1000000), BigDecimal(1000000)).map(Json.Number(_))

  val genJsonString: Gen[Any, Json] =
    Gen.alphaNumericStringBounded(0, 20).map(Json.String(_))

  // Non-recursive leaf generators
  val genJsonLeaf: Gen[Any, Json] = Gen.oneOf(
    genJsonNull,
    genJsonBoolean,
    genJsonNumber,
    genJsonString
  )

  // Depth-limited generators for arrays and objects
  def genJsonArray(maxDepth: Int): Gen[Any, Json] =
    if (maxDepth <= 0) Gen.const(Json.Array.empty)
    else
      Gen.listOfBounded(0, 3)(genJsonValue(maxDepth - 1)).map(elems => Json.Array(elems.toVector))

  def genJsonObject(maxDepth: Int): Gen[Any, Json] =
    if (maxDepth <= 0) Gen.const(Json.Object.empty)
    else
      Gen
        .listOfBounded(0, 3)(
          for {
            key   <- Gen.alphaNumericStringBounded(1, 10)
            value <- genJsonValue(maxDepth - 1)
          } yield (key, value)
        )
        .map(fields => Json.Object(fields.toVector))

  def genJsonValue(maxDepth: Int): Gen[Any, Json] =
    if (maxDepth <= 0) genJsonLeaf
    else
      Gen.oneOf(
        genJsonLeaf,
        genJsonArray(maxDepth - 1),
        genJsonObject(maxDepth - 1)
      )

  val genJson: Gen[Any, Json] = genJsonValue(2)

  def spec: Spec[TestEnvironment, Any] = suite("JsonPatchLawsSpec")(
    suite("Monoid Laws for JsonPatch")(
      test("L1: Left identity - (empty ++ p)(j) == p(j)") {
        check(genJson, genJson) { (old, new_) =>
          val patch = JsonPatch.diff(old, new_)
          val empty = JsonPatch.empty

          val composed = empty ++ patch
          val result1  = composed(old, JsonPatchMode.Strict)
          val result2  = patch(old, JsonPatchMode.Strict)

          assertTrue(result1 == result2)
        }
      },
      test("L2: Right identity - (p ++ empty)(j) == p(j)") {
        check(genJson, genJson) { (old, new_) =>
          val patch = JsonPatch.diff(old, new_)
          val empty = JsonPatch.empty

          val composed = patch ++ empty
          val result1  = composed(old, JsonPatchMode.Strict)
          val result2  = patch(old, JsonPatchMode.Strict)

          assertTrue(result1 == result2)
        }
      },
      test("L3: Associativity - ((p1 ++ p2) ++ p3)(j) == (p1 ++ (p2 ++ p3))(j)") {
        check(genJson, genJson, genJson, genJson) { (v0, v1, v2, v3) =>
          val p1 = JsonPatch.diff(v0, v1)
          val p2 = JsonPatch.diff(v1, v2)
          val p3 = JsonPatch.diff(v2, v3)

          val left  = (p1 ++ p2) ++ p3
          val right = p1 ++ (p2 ++ p3)

          val result1 = left(v0, JsonPatchMode.Strict)
          val result2 = right(v0, JsonPatchMode.Strict)

          assertTrue(result1 == result2)
        }
      }
    ),
    suite("Diff/Apply Laws")(
      test("L4: Roundtrip - diff(a, b)(a) == Right(b) (Numbers)") {
        check(genJsonNumber, genJsonNumber) { (old, new_) =>
          val patch  = JsonPatch.diff(old, new_)
          val result = patch(old, JsonPatchMode.Strict)

          assertTrue(result == Right(new_))
        }
      },
      test("L4: Roundtrip - diff(a, b)(a) == Right(b) (Strings)") {
        check(genJsonString, genJsonString) { (old, new_) =>
          val patch  = JsonPatch.diff(old, new_)
          val result = patch(old, JsonPatchMode.Strict)

          assertTrue(result == Right(new_))
        }
      },
      test("L4: Roundtrip - diff(a, b)(a) == Right(b) (Booleans)") {
        check(genJsonBoolean, genJsonBoolean) { (old, new_) =>
          val patch  = JsonPatch.diff(old, new_)
          val result = patch(old, JsonPatchMode.Strict)

          assertTrue(result == Right(new_))
        }
      },
      test("L4: Roundtrip - diff(a, b)(a) == Right(b) (Arrays)") {
        check(genJsonArray(2), genJsonArray(2)) { (old, new_) =>
          val patch  = JsonPatch.diff(old, new_)
          val result = patch(old, JsonPatchMode.Strict)

          assertTrue(result == Right(new_))
        }
      },
      test("L4: Roundtrip - diff(a, b)(a) == Right(b) (Objects)") {
        check(genJsonObject(2), genJsonObject(2)) { (old, new_) =>
          val patch  = JsonPatch.diff(old, new_)
          val result = patch(old, JsonPatchMode.Strict)

          assertTrue(result == Right(new_))
        }
      },
      test("L4: Roundtrip - diff(a, b)(a) == Right(b) (Mixed)") {
        check(genJson, genJson) { (old, new_) =>
          val patch  = JsonPatch.diff(old, new_)
          val result = patch(old, JsonPatchMode.Strict)

          assertTrue(result == Right(new_))
        }
      },
      test("L5: Identity diff - diff(j, j).isEmpty") {
        check(genJson) { value =>
          val patch = JsonPatch.diff(value, value)
          assertTrue(patch.isEmpty)
        }
      },
      test("L6: Diff composition - (diff(a, b) ++ diff(b, c))(a) == Right(c)") {
        check(genJson, genJson, genJson) { (a, b, c) =>
          val p1 = JsonPatch.diff(a, b)
          val p2 = JsonPatch.diff(b, c)

          val composed = p1 ++ p2
          val result   = composed(a, JsonPatchMode.Strict)

          assertTrue(result == Right(c))
        }
      }
    ),
    suite("PatchMode Laws")(
      test("L7: Lenient subsumes Strict - if p(j, Strict) == Right(r) then p(j, Lenient) == Right(r)") {
        check(genJson, genJson) { (old, new_) =>
          val patch        = JsonPatch.diff(old, new_)
          val strictResult = patch(old, JsonPatchMode.Strict)

          strictResult match {
            case Right(r) =>
              val lenientResult = patch(old, JsonPatchMode.Lenient)
              assertTrue(lenientResult == Right(r))
            case Left(_) =>
              // If Strict fails, we don't care about Lenient behavior
              assertTrue(true)
          }
        }
      }
    ),
    suite("Empty Patch Identity")(
      test("empty patch is true identity - Number") {
        check(genJsonNumber) { value =>
          val empty  = JsonPatch.empty
          val result = empty(value, JsonPatchMode.Strict)
          assertTrue(result == Right(value))
        }
      },
      test("empty patch is true identity - String") {
        check(genJsonString) { value =>
          val empty  = JsonPatch.empty
          val result = empty(value, JsonPatchMode.Strict)
          assertTrue(result == Right(value))
        }
      },
      test("empty patch is true identity - Array") {
        check(genJsonArray(2)) { value =>
          val empty  = JsonPatch.empty
          val result = empty(value, JsonPatchMode.Strict)
          assertTrue(result == Right(value))
        }
      },
      test("empty patch is true identity - Object") {
        check(genJsonObject(2)) { value =>
          val empty  = JsonPatch.empty
          val result = empty(value, JsonPatchMode.Strict)
          assertTrue(result == Right(value))
        }
      }
    ),
    suite("Sequential Composition")(
      test("composing sequential diffs preserves final value - Numbers") {
        check(genJsonNumber, genJsonNumber, genJsonNumber) { (v0, v1, v2) =>
          val p1 = JsonPatch.diff(v0, v1)
          val p2 = JsonPatch.diff(v1, v2)

          val composed = p1 ++ p2
          val result   = composed(v0, JsonPatchMode.Strict)

          assertTrue(result == Right(v2))
        }
      },
      test("composing sequential diffs preserves final value - Strings") {
        check(genJsonString, genJsonString, genJsonString) { (v0, v1, v2) =>
          val p1 = JsonPatch.diff(v0, v1)
          val p2 = JsonPatch.diff(v1, v2)

          val composed = p1 ++ p2
          val result   = composed(v0, JsonPatchMode.Strict)

          assertTrue(result == Right(v2))
        }
      },
      test("composing sequential diffs preserves final value - Arrays") {
        check(genJsonArray(2), genJsonArray(2), genJsonArray(2)) { (v0, v1, v2) =>
          val p1 = JsonPatch.diff(v0, v1)
          val p2 = JsonPatch.diff(v1, v2)

          val composed = p1 ++ p2
          val result   = composed(v0, JsonPatchMode.Strict)

          assertTrue(result == Right(v2))
        }
      },
      test("composing sequential diffs preserves final value - Objects") {
        check(genJsonObject(2), genJsonObject(2), genJsonObject(2)) { (v0, v1, v2) =>
          val p1 = JsonPatch.diff(v0, v1)
          val p2 = JsonPatch.diff(v1, v2)

          val composed = p1 ++ p2
          val result   = composed(v0, JsonPatchMode.Strict)

          assertTrue(result == Right(v2))
        }
      }
    )
  )
}
