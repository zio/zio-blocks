package zio.blocks.schema.json.patch

import zio.blocks.schema.SchemaBaseSpec
import zio.blocks.schema.json.JsonPatch
import zio.blocks.schema.patch.PatchMode
import zio.test._

/**
 * Property-based tests for [[JsonPatch]] algebraic laws.
 *
 * Laws verified:
 *   - LAW-01: Monoid left identity - (empty ++ p).apply(j) == p.apply(j)
 *   - LAW-02: Monoid right identity - (p ++ empty).apply(j) == p.apply(j)
 *   - LAW-03: Monoid associativity - ((p1 ++ p2) ++ p3).apply(j) == (p1 ++ (p2
 * ++ p3)).apply(j)
 *   - LAW-04: Roundtrip - diff(a, b).apply(a) == Right(b)
 *   - LAW-05: Identity diff - diff(j, j).isEmpty
 *   - LAW-06: Diff composition - (diff(a, b) ++ diff(b, c)).apply(a) ==
 *     Right(c)
 *   - LAW-07: Lenient subsumes Strict - if Strict succeeds, Lenient produces
 *     same result
 */
object JsonPatchLawsSpec extends SchemaBaseSpec {

  override def spec: Spec[TestEnvironment, Any] =
    suite("JsonPatch Algebraic Laws")(
      monoidLaws,
      roundtripLaws,
      modeLaws
    ) @@ TestAspect.samples(100)

  // ─────────────────────────────────────────────────────────────────────────
  // Monoid Laws
  // ─────────────────────────────────────────────────────────────────────────

  private val monoidLaws = suite("Monoid Laws")(
    test("LAW-01: Left identity - (empty ++ p).apply(j) == p.apply(j)") {
      check(JsonGen.genJson, JsonGen.genJson) { (a, b) =>
        val p     = JsonPatch.diff(a, b)
        val left  = (JsonPatch.empty ++ p).apply(a)
        val right = p.apply(a)
        assertTrue(left == right)
      }
    },
    test("LAW-02: Right identity - (p ++ empty).apply(j) == p.apply(j)") {
      check(JsonGen.genJson, JsonGen.genJson) { (a, b) =>
        val p     = JsonPatch.diff(a, b)
        val left  = (p ++ JsonPatch.empty).apply(a)
        val right = p.apply(a)
        assertTrue(left == right)
      }
    },
    test("LAW-03: Associativity - ((p1 ++ p2) ++ p3).apply(j) == (p1 ++ (p2 ++ p3)).apply(j)") {
      check(JsonGen.genJson, JsonGen.genJson, JsonGen.genJson, JsonGen.genJson) { (a, b, c, j) =>
        val p1 = JsonPatch.diff(a, b)
        val p2 = JsonPatch.diff(b, c)
        val p3 = JsonPatch.diff(c, a)

        val leftAssoc  = ((p1 ++ p2) ++ p3).apply(j)
        val rightAssoc = (p1 ++ (p2 ++ p3)).apply(j)

        assertTrue(leftAssoc == rightAssoc)
      }
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Roundtrip Laws
  // ─────────────────────────────────────────────────────────────────────────

  private val roundtripLaws = suite("Roundtrip Laws")(
    test("LAW-04: Roundtrip - diff(a, b).apply(a) == Right(b)") {
      check(JsonGen.genJson, JsonGen.genJson) { (a, b) =>
        val patch  = JsonPatch.diff(a, b)
        val result = patch.apply(a)
        assertTrue(result == Right(b))
      }
    },
    test("LAW-05: Identity diff - diff(j, j).isEmpty") {
      check(JsonGen.genJson) { j =>
        val patch = JsonPatch.diff(j, j)
        assertTrue(patch.isEmpty)
      }
    },
    test("LAW-06: Diff composition - (diff(a, b) ++ diff(b, c)).apply(a) == Right(c)") {
      check(JsonGen.genJson, JsonGen.genJson, JsonGen.genJson) { (a, b, c) =>
        val p1       = JsonPatch.diff(a, b)
        val p2       = JsonPatch.diff(b, c)
        val composed = p1 ++ p2
        val result   = composed.apply(a)
        assertTrue(result == Right(c))
      }
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Mode Laws
  // ─────────────────────────────────────────────────────────────────────────

  private val modeLaws = suite("Mode Laws")(
    test("LAW-07: Lenient subsumes Strict - if Strict succeeds, Lenient produces same result") {
      check(JsonGen.genJson, JsonGen.genJson) { (a, b) =>
        val patch        = JsonPatch.diff(a, b)
        val strictResult = patch.apply(a, PatchMode.Strict)

        // If Strict succeeds, Lenient must also succeed with same result
        strictResult match {
          case Right(strictValue) =>
            val lenientResult = patch.apply(a, PatchMode.Lenient)
            assertTrue(lenientResult == Right(strictValue))
          case Left(_) =>
            // Strict failed, no constraint on Lenient
            assertTrue(true)
        }
      }
    }
  )
}
