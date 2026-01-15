package zio.blocks.schema.json

import zio.test._

object JsonPatchLawsSpec extends ZIOSpecDefault {

  private val genLeaf: Gen[Any, Json] =
    Gen.oneOf(
      Gen.const(Json.Null),
      Gen.boolean.map(Json.Boolean(_)),
      Gen.alphaNumericStringBounded(0, 30).map(Json.String(_)),
      Gen.long(-1000000L, 1000000L).map(n => Json.Number(n.toString))
    )

  private def genJson(depth: Int): Gen[Any, Json] =
    if (depth <= 0) genLeaf
    else {
      val genArray =
        Gen.listOfBounded(0, 5)(genJson(depth - 1)).map(v => Json.Array(v.toVector))

      val genObject = for {
        keys <- Gen.listOfBounded(0, 5)(Gen.alphaNumericStringBounded(1, 12))
        vals <- Gen.listOfBounded(keys.length, keys.length)(genJson(depth - 1))
      } yield {
        // enforce unique keys (keep last)
        val m = (keys zip vals).foldLeft(Map.empty[String, Json]) { case (acc, (k, v)) => acc.updated(k, v) }
        Json.Object(m.toVector)
      }

      Gen.oneOf(genLeaf, genArray, genObject)
    }

  private val genJsonValue: Gen[Any, Json] = genJson(depth = 3)

  def spec: Spec[TestEnvironment, Any] =
    suite("JsonPatchLawsSpec")(
      suite("Monoid laws")(
        test("left identity: empty ++ p == p") {
          check(genJsonValue, genJsonValue) { (a, b) =>
            val p     = JsonPatch.diff(a, b)
            val left  = (JsonPatch.empty ++ p)(a, JsonPatchMode.Strict)
            val right = p(a, JsonPatchMode.Strict)
            assertTrue(left == right)
          }
        },
        test("right identity: p ++ empty == p") {
          check(genJsonValue, genJsonValue) { (a, b) =>
            val p     = JsonPatch.diff(a, b)
            val left  = (p ++ JsonPatch.empty)(a, JsonPatchMode.Strict)
            val right = p(a, JsonPatchMode.Strict)
            assertTrue(left == right)
          }
        },
        test("associativity: (p1 ++ p2) ++ p3 == p1 ++ (p2 ++ p3)") {
          check(genJsonValue, genJsonValue, genJsonValue, genJsonValue) { (a, b, c, d) =>
            val p1 = JsonPatch.diff(a, b)
            val p2 = JsonPatch.diff(b, c)
            val p3 = JsonPatch.diff(c, d)
            val l  = ((p1 ++ p2) ++ p3)(a, JsonPatchMode.Strict)
            val r  = (p1 ++ (p2 ++ p3))(a, JsonPatchMode.Strict)
            assertTrue(l == r)
          }
        }
      ),
      suite("Diff laws")(
        test("roundtrip: diff(a, b)(a) == Right(b)") {
          check(genJsonValue, genJsonValue) { (a, b) =>
            val p = JsonPatch.diff(a, b)
            assertTrue(p(a, JsonPatchMode.Strict) == Right(b))
          }
        },
        test("identity diff: diff(a, a) is empty") {
          check(genJsonValue) { a =>
            assertTrue(JsonPatch.diff(a, a).isEmpty)
          }
        },
        test("diff composition: (diff(a,b) ++ diff(b,c))(a) == Right(c)") {
          check(genJsonValue, genJsonValue, genJsonValue) { (a, b, c) =>
            val p = JsonPatch.diff(a, b) ++ JsonPatch.diff(b, c)
            assertTrue(p(a, JsonPatchMode.Strict) == Right(c))
          }
        }
      ),
      suite("Mode laws")(
        test("lenient subsumes strict: if Strict succeeds, Lenient yields same result") {
          check(genJsonValue, genJsonValue) { (a, b) =>
            val p      = JsonPatch.diff(a, b)
            val strict = p(a, JsonPatchMode.Strict)
            strict match {
              case Right(r) =>
                assertTrue(p(a, JsonPatchMode.Lenient) == Right(r))
              case Left(_) =>
                // no constraint if strict fails
                assertTrue(true)
            }
          }
        }
      )
    )
}
