package zio.blocks.schema.json.patch

import zio.blocks.schema.SchemaBaseSpec
import zio.blocks.schema.json.JsonPatch
import zio.blocks.schema.patch.PatchMode
import zio.test._

object JsonPatchLawsSpec extends SchemaBaseSpec {

  override def spec: Spec[TestEnvironment, Any] =
    suite("JsonPatch Algebraic Laws")(
      monoidLaws,
      roundtripLaws,
      modeLaws,
      structuralDiffLaws
    ) @@ TestAspect.samples(100)

  // Monoid Laws
  private val monoidLaws = suite("Monoid Laws")(
    test("Left identity: (empty ++ p).apply(j) == p.apply(j)") {
      check(JsonGen.genTestPair) { case (a, b) =>
        val p     = JsonPatch.diff(a, b)
        val left  = (JsonPatch.empty ++ p).apply(a)
        val right = p.apply(a)
        assertTrue(left == right)
      }
    },
    test("Right identity: (p ++ empty).apply(j) == p.apply(j)") {
      check(JsonGen.genTestPair) { case (a, b) =>
        val p     = JsonPatch.diff(a, b)
        val left  = (p ++ JsonPatch.empty).apply(a)
        val right = p.apply(a)
        assertTrue(left == right)
      }
    },
    test("Associativity: ((p1 ++ p2) ++ p3).apply(j) == (p1 ++ (p2 ++ p3)).apply(j)") {
      check(JsonGen.genTestTriple, JsonGen.genJson) { case ((a, b, c), j) =>
        val p1 = JsonPatch.diff(a, b)
        val p2 = JsonPatch.diff(b, c)
        val p3 = JsonPatch.diff(c, a)

        val leftAssoc  = ((p1 ++ p2) ++ p3).apply(j)
        val rightAssoc = (p1 ++ (p2 ++ p3)).apply(j)

        assertTrue(leftAssoc == rightAssoc)
      }
    }
  )

  // Roundtrip Laws
  private val roundtripLaws = suite("Roundtrip Laws")(
    test("Roundtrip: diff(a, b).apply(a) == Right(b)") {
      check(JsonGen.genTestPair) { case (a, b) =>
        val patch  = JsonPatch.diff(a, b)
        val result = patch.apply(a)
        assertTrue(result == Right(b))
      }
    },
    test("Identity: diff(j, j).isEmpty") {
      check(JsonGen.genJson) { j =>
        val patch = JsonPatch.diff(j, j)
        assertTrue(patch.isEmpty)
      }
    },
    test("Composition: (diff(a, b) ++ diff(b, c)).apply(a) == Right(c)") {
      check(JsonGen.genTestTriple) { case (a, b, c) =>
        val p1       = JsonPatch.diff(a, b)
        val p2       = JsonPatch.diff(b, c)
        val composed = p1 ++ p2
        val result   = composed.apply(a)
        assertTrue(result == Right(c))
      }
    }
  )

  // Mode Laws
  private val modeLaws = suite("Mode Laws")(
    test("Lenient subsumes Strict: if Strict succeeds, Lenient gives same result") {
      check(JsonGen.genTestPair) { case (a, b) =>
        val patch        = JsonPatch.diff(a, b)
        val strictResult = patch.apply(a, PatchMode.Strict)

        strictResult match {
          case Right(strictValue) =>
            val lenientResult = patch.apply(a, PatchMode.Lenient)
            assertTrue(lenientResult == Right(strictValue))
          case Left(_) =>
            assertTrue(true)
        }
      }
    }
  )

  // Structural Diff Laws
  private val structuralDiffLaws = suite("Structural Diff Laws")(
    suite("Object Operations")(
      test("Field addition roundtrip") {
        check(JsonGen.genWithAddition) { case (a, b) =>
          val patch  = JsonPatch.diff(a, b)
          val result = patch.apply(a)
          assertTrue(result == Right(b))
        }
      },
      test("Field removal roundtrip") {
        check(JsonGen.genWithRemoval) { case (a, b) =>
          val patch  = JsonPatch.diff(a, b)
          val result = patch.apply(a)
          assertTrue(result == Right(b))
        }
      },
      test("Nested change roundtrip") {
        check(JsonGen.genWithNestedChange) { case (a, b) =>
          val patch  = JsonPatch.diff(a, b)
          val result = patch.apply(a)
          assertTrue(result == Right(b))
        }
      }
    ),
    suite("Array Operations")(
      test("Array element change roundtrip") {
        check(JsonGen.genArrayWithLCS) { case (a, b) =>
          val patch  = JsonPatch.diff(a, b)
          val result = patch.apply(a)
          assertTrue(result == Right(b))
        }
      }
    ),
    suite("Primitive Operations")(
      test("Number change roundtrip") {
        check(JsonGen.genWithNumberChange) { case (a, b) =>
          val patch  = JsonPatch.diff(a, b)
          val result = patch.apply(a)
          assertTrue(result == Right(b))
        }
      },
      test("String change roundtrip") {
        check(JsonGen.genWithStringChange) { case (a, b) =>
          val patch  = JsonPatch.diff(a, b)
          val result = patch.apply(a)
          assertTrue(result == Right(b))
        }
      }
    ),
    suite("Type Changes")(
      test("Type change roundtrip") {
        check(JsonGen.genWithTypeChange) { case (a, b) =>
          val patch  = JsonPatch.diff(a, b)
          val result = patch.apply(a)
          assertTrue(result == Right(b))
        }
      }
    ),
    suite("Tweaked Generator Tests")(
      test("Tweaked pair roundtrip") {
        check(JsonGen.genTweakedPair) { case (a, b) =>
          val patch  = JsonPatch.diff(a, b)
          val result = patch.apply(a)
          assertTrue(result == Right(b))
        }
      },
      test("Tweaked Triple composition") {
        check(JsonGen.genTestTriple) { case (a, b, c) =>
          val p1       = JsonPatch.diff(a, b)
          val p2       = JsonPatch.diff(b, c)
          val composed = p1 ++ p2
          val result   = composed.apply(a)
          assertTrue(result == Right(c))
        }
      }
    )
  )
}
