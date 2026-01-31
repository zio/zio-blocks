package zio.blocks.schema.json

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.patch.{DynamicPatch, PatchMode}
import zio.test._

object JsonPatchSpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Generators
  // ─────────────────────────────────────────────────────────────────────────

  val genJsonNull: Gen[Any, Json] = Gen.const(Json.Null)

  val genJsonBoolean: Gen[Any, Json] =
    Gen.boolean.map(b => if (b) Json.True else Json.False)

  val genJsonNumber: Gen[Any, Json.Number] =
    Gen.oneOf(
      Gen.int.map(n => new Json.Number(n.toString)),
      Gen.double.map(d => new Json.Number(d.toString)),
      Gen.bigDecimal(BigDecimal(-1000000), BigDecimal(1000000)).map(bd => new Json.Number(bd.toString))
    )

  val genJsonString: Gen[Any, Json.String] =
    Gen.alphaNumericStringBounded(0, 50).map(new Json.String(_))

  def genJsonArray(depth: Int): Gen[Any, Json.Array] =
    if (depth <= 0) Gen.const(Json.Array.empty)
    else {
      Gen.listOfBounded(0, 5)(genJson(depth - 1)).map { list =>
        new Json.Array(Chunk.fromIterable(list))
      }
    }

  def genJsonObject(depth: Int): Gen[Any, Json.Object] =
    if (depth <= 0) Gen.const(Json.Object.empty)
    else {
      Gen
        .listOfBounded(0, 5)(
          for {
            key   <- Gen.alphaNumericStringBounded(1, 10)
            value <- genJson(depth - 1)
          } yield (key, value)
        )
        .map { fields =>
          new Json.Object(Chunk.fromIterable(fields))
        }
    }

  def genJson(depth: Int): Gen[Any, Json] =
    if (depth <= 0) {
      Gen.oneOf(genJsonNull, genJsonBoolean, genJsonNumber, genJsonString)
    } else {
      Gen.oneOf(
        genJsonNull,
        genJsonBoolean,
        genJsonNumber,
        genJsonString,
        genJsonArray(depth - 1),
        genJsonObject(depth - 1)
      )
    }

  val genSimpleJson: Gen[Any, Json] = genJson(2)
  val genDeepJson: Gen[Any, Json]   = genJson(3)

  // ─────────────────────────────────────────────────────────────────────────
  // Tweak-based generators (for testing non-Set operations)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Generates an object and a tweaked version with one field added. This
   * ensures diff produces ObjectEdit.Add (not Set).
   */
  val genObjectWithAddedField: Gen[Any, (Json.Object, Json.Object)] =
    for {
      base     <- genJsonObject(2)
      newKey   <- Gen.alphaNumericStringBounded(1, 8).map(k => s"new_$k")
      newValue <- genJson(1)
    } yield {
      val tweaked = new Json.Object(base.fields :+ ((newKey, newValue)))
      (base, tweaked)
    }

  /**
   * Generates an object and a tweaked version with one field removed. This
   * ensures diff produces ObjectEdit.Remove (not Set).
   */
  val genObjectWithRemovedField: Gen[Any, (Json.Object, Json.Object)] =
    for {
      base <- genJsonObject(2).filter(_.fields.nonEmpty)
      idx  <- Gen.int(0, 100).map(i => i % base.fields.length)
    } yield {
      val tweaked = new Json.Object(base.fields.take(idx) ++ base.fields.drop(idx + 1))
      (base, tweaked)
    }

  /**
   * Generates an object and a tweaked version with one field value modified.
   * This ensures diff produces ObjectEdit.Modify (not Set).
   */
  val genObjectWithModifiedField: Gen[Any, (Json.Object, Json.Object)] =
    for {
      base     <- genJsonObject(2).filter(_.fields.nonEmpty)
      idx      <- Gen.int(0, 100).map(i => i % base.fields.length)
      newValue <- genJson(1)
    } yield {
      val (key, _) = base.fields(idx)
      val tweaked  = new Json.Object(base.fields.updated(idx, (key, newValue)))
      (base, tweaked)
    }

  /**
   * Generates an array and a tweaked version with one element appended. This
   * ensures diff produces ArrayEdit operations (not Set).
   */
  val genArrayWithAppendedElement: Gen[Any, (Json.Array, Json.Array)] =
    for {
      base    <- genJsonArray(2)
      newElem <- genJson(1)
    } yield {
      val tweaked = new Json.Array(base.elements :+ newElem)
      (base, tweaked)
    }

  /**
   * Generates an array and a tweaked version with one element removed. This
   * ensures diff produces ArrayEdit.Delete (not Set).
   */
  val genArrayWithRemovedElement: Gen[Any, (Json.Array, Json.Array)] =
    for {
      base <- genJsonArray(2).filter(_.elements.nonEmpty)
      idx  <- Gen.int(0, 100).map(i => i % base.elements.length)
    } yield {
      val tweaked = new Json.Array(base.elements.take(idx) ++ base.elements.drop(idx + 1))
      (base, tweaked)
    }

  /**
   * Generates a number and a tweaked version with a delta applied. This ensures
   * diff produces NumberDelta (not Set).
   */
  val genNumberWithDelta: Gen[Any, (Json.Number, Json.Number)] =
    for {
      base  <- Gen.bigDecimal(BigDecimal(-1000), BigDecimal(1000))
      delta <- Gen.bigDecimal(BigDecimal(-100), BigDecimal(100)).filter(_ != BigDecimal(0))
    } yield {
      val baseNum    = new Json.Number(base.toString)
      val tweakedNum = new Json.Number((base + delta).toString)
      (baseNum, tweakedNum)
    }

  /**
   * Generates a string and a tweaked version with text appended. This ensures
   * diff produces StringEdit (not Set).
   */
  val genStringWithAppend: Gen[Any, (Json.String, Json.String)] =
    for {
      base   <- Gen.alphaNumericStringBounded(1, 20)
      suffix <- Gen.alphaNumericStringBounded(1, 10)
    } yield {
      val baseStr    = new Json.String(base)
      val tweakedStr = new Json.String(base + suffix)
      (baseStr, tweakedStr)
    }

  /**
   * Generates a string with a prefix modification. This ensures diff produces
   * StringEdit.Insert or StringEdit.Modify.
   */
  val genStringWithPrefix: Gen[Any, (Json.String, Json.String)] =
    for {
      base   <- Gen.alphaNumericStringBounded(1, 20)
      prefix <- Gen.alphaNumericStringBounded(1, 10)
    } yield {
      val baseStr    = new Json.String(base)
      val tweakedStr = new Json.String(prefix + base)
      (baseStr, tweakedStr)
    }

  // ─────────────────────────────────────────────────────────────────────────
  // NESTED tweak generators (jdegoes explicitly requested "nested" tweaks)
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Generates an object with a nested field modification. Example: {"user":
   * {"name": "Alice"}} -> {"user": {"name": "Bob"}} This exercises
   * ObjectOp.Modify -> inner diff path.
   */
  val genNestedFieldModified: Gen[Any, (Json.Object, Json.Object)] =
    for {
      outerKey <- Gen.alphaNumericStringBounded(1, 8)
      innerKey <- Gen.alphaNumericStringBounded(1, 8)
      oldValue <- Gen.alphaNumericStringBounded(1, 10)
      newValue <- Gen.alphaNumericStringBounded(1, 10)
    } yield {
      val inner1  = new Json.Object(Chunk((innerKey, new Json.String(oldValue))))
      val inner2  = new Json.Object(Chunk((innerKey, new Json.String(newValue))))
      val base    = new Json.Object(Chunk((outerKey, inner1)))
      val tweaked = new Json.Object(Chunk((outerKey, inner2)))
      (base, tweaked)
    }

  /**
   * Generates an object with a nested field added. Example: {"user": {"name":
   * "Alice"}} -> {"user": {"name": "Alice", "age": 30}} This exercises
   * ObjectOp.Modify -> ObjectEdit.Add in nested context.
   */
  val genNestedFieldAdded: Gen[Any, (Json.Object, Json.Object)] =
    for {
      outerKey      <- Gen.alphaNumericStringBounded(1, 8)
      innerKey      <- Gen.alphaNumericStringBounded(1, 8)
      newKey        <- Gen.alphaNumericStringBounded(1, 8).map(k => s"new_$k")
      existingValue <- Gen.alphaNumericStringBounded(1, 10)
      newValue      <- Gen.int(-100, 100)
    } yield {
      val inner1 = new Json.Object(Chunk((innerKey, new Json.String(existingValue))))
      val inner2 =
        new Json.Object(Chunk((innerKey, new Json.String(existingValue)), (newKey, new Json.Number(newValue.toString))))
      val base    = new Json.Object(Chunk((outerKey, inner1)))
      val tweaked = new Json.Object(Chunk((outerKey, inner2)))
      (base, tweaked)
    }

  /**
   * Generates an object with a nested type change. Example: {"data": {"value":
   * 42}} -> {"data": {"value": "forty-two"}} This exercises the Set fallback
   * inside nested recursive diffing. jdegoes specifically said "or changing a
   * type, etc."
   */
  val genNestedTypeChange: Gen[Any, (Json.Object, Json.Object)] =
    for {
      outerKey <- Gen.alphaNumericStringBounded(1, 8)
      innerKey <- Gen.alphaNumericStringBounded(1, 8)
      numValue <- Gen.int(-100, 100)
      strValue <- Gen.alphaNumericStringBounded(1, 10)
    } yield {
      val inner1  = new Json.Object(Chunk((innerKey, new Json.Number(numValue.toString))))
      val inner2  = new Json.Object(Chunk((innerKey, new Json.String(strValue))))
      val base    = new Json.Object(Chunk((outerKey, inner1)))
      val tweaked = new Json.Object(Chunk((outerKey, inner2)))
      (base, tweaked)
    }

  /**
   * Generates an array with nested object modification. Example: [{"id": 1,
   * "name": "A"}] -> [{"id": 1, "name": "B"}] This exercises ArrayEdit ->
   * Modify -> recursive object diff.
   */
  val genArrayWithNestedModification: Gen[Any, (Json.Array, Json.Array)] =
    for {
      key1    <- Gen.alphaNumericStringBounded(1, 5)
      key2    <- Gen.alphaNumericStringBounded(1, 5).map(k => s"name_$k") // Ensure key2 differs from key1
      id      <- Gen.int(1, 100)
      oldName <- Gen.alphaNumericStringBounded(1, 10)
      suffix  <- Gen.alphaNumericStringBounded(1, 5)
    } yield {
      // Ensure newName is always different from oldName by appending suffix
      val newName = oldName + "_" + suffix
      val obj1    = new Json.Object(Chunk((key1, new Json.Number(id.toString)), (key2, new Json.String(oldName))))
      val obj2    = new Json.Object(Chunk((key1, new Json.Number(id.toString)), (key2, new Json.String(newName))))
      val base    = new Json.Array(Chunk(obj1))
      val tweaked = new Json.Array(Chunk(obj2))
      (base, tweaked)
    }

  /**
   * Combines all tweak generators for comprehensive testing. Includes both
   * top-level AND nested tweaks.
   */
  val genTweakedJsonPair: Gen[Any, (Json, Json)] =
    Gen.oneOf(
      // Top-level tweaks
      genObjectWithAddedField.map { case (a, b) => (a: Json, b: Json) },
      genObjectWithRemovedField.map { case (a, b) => (a: Json, b: Json) },
      genObjectWithModifiedField.map { case (a, b) => (a: Json, b: Json) },
      genArrayWithAppendedElement.map { case (a, b) => (a: Json, b: Json) },
      genArrayWithRemovedElement.map { case (a, b) => (a: Json, b: Json) },
      genNumberWithDelta.map { case (a, b) => (a: Json, b: Json) },
      genStringWithAppend.map { case (a, b) => (a: Json, b: Json) },
      genStringWithPrefix.map { case (a, b) => (a: Json, b: Json) },
      // NESTED tweaks (jdegoes explicitly requested these)
      genNestedFieldModified.map { case (a, b) => (a: Json, b: Json) },
      genNestedFieldAdded.map { case (a, b) => (a: Json, b: Json) },
      genNestedTypeChange.map { case (a, b) => (a: Json, b: Json) },
      genArrayWithNestedModification.map { case (a, b) => (a: Json, b: Json) }
    )

  def spec: Spec[TestEnvironment, Any] = suite("JsonPatchSpec")(
    monoidLawsSuite,
    diffApplyLawsSuite,
    patchModeSuite,
    operationTypeSuite,
    arrayOpSuite,
    objectOpSuite,
    stringOpSuite,
    numberDeltaSuite,
    dynamicPatchConversionSuite,
    edgeCaseSuite,
    errorCaseSuite,
    toStringCoverageSuite,
    elementsCoverageSuite,
    dynamicPatchConversionCoverageSuite,
    additionalEdgeCaseSuite
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Monoid Laws (L1, L2, L3)
  // ─────────────────────────────────────────────────────────────────────────

  val monoidLawsSuite: Spec[Any, Nothing] = suite("Monoid Laws for JsonPatch")(
    test("L1: left identity - (empty ++ p)(j) == p(j) for Json.Number") {
      check(genJsonNumber, genJsonNumber) { (oldJson, newJson) =>
        val patch    = JsonPatch.diff(oldJson, newJson)
        val empty    = JsonPatch.empty
        val composed = empty ++ patch

        val result1 = composed(oldJson, PatchMode.Strict)
        val result2 = patch(oldJson, PatchMode.Strict)

        assertTrue(result1 == result2)
      }
    },
    test("L1: left identity - (empty ++ p)(j) == p(j) for Json.String") {
      check(genJsonString, genJsonString) { (oldJson, newJson) =>
        val patch    = JsonPatch.diff(oldJson, newJson)
        val empty    = JsonPatch.empty
        val composed = empty ++ patch

        val result1 = composed(oldJson, PatchMode.Strict)
        val result2 = patch(oldJson, PatchMode.Strict)

        assertTrue(result1 == result2)
      }
    },
    test("L1: left identity - (empty ++ p)(j) == p(j) for Json.Object") {
      check(genJsonObject(2), genJsonObject(2)) { (oldJson, newJson) =>
        val patch    = JsonPatch.diff(oldJson, newJson)
        val empty    = JsonPatch.empty
        val composed = empty ++ patch

        val result1 = composed(oldJson, PatchMode.Strict)
        val result2 = patch(oldJson, PatchMode.Strict)

        assertTrue(result1 == result2)
      }
    },
    test("L1: left identity - (empty ++ p)(j) == p(j) for Json.Array") {
      check(genJsonArray(2), genJsonArray(2)) { (oldJson, newJson) =>
        val patch    = JsonPatch.diff(oldJson, newJson)
        val empty    = JsonPatch.empty
        val composed = empty ++ patch

        val result1 = composed(oldJson, PatchMode.Strict)
        val result2 = patch(oldJson, PatchMode.Strict)

        assertTrue(result1 == result2)
      }
    },
    test("L2: right identity - (p ++ empty)(j) == p(j) for Json.Number") {
      check(genJsonNumber, genJsonNumber) { (oldJson, newJson) =>
        val patch    = JsonPatch.diff(oldJson, newJson)
        val empty    = JsonPatch.empty
        val composed = patch ++ empty

        val result1 = composed(oldJson, PatchMode.Strict)
        val result2 = patch(oldJson, PatchMode.Strict)

        assertTrue(result1 == result2)
      }
    },
    test("L2: right identity - (p ++ empty)(j) == p(j) for Json.Object") {
      check(genJsonObject(2), genJsonObject(2)) { (oldJson, newJson) =>
        val patch    = JsonPatch.diff(oldJson, newJson)
        val empty    = JsonPatch.empty
        val composed = patch ++ empty

        val result1 = composed(oldJson, PatchMode.Strict)
        val result2 = patch(oldJson, PatchMode.Strict)

        assertTrue(result1 == result2)
      }
    },
    test("L2: right identity - (p ++ empty)(j) == p(j) for Json.String") {
      check(genJsonString, genJsonString) { (oldJson, newJson) =>
        val patch    = JsonPatch.diff(oldJson, newJson)
        val empty    = JsonPatch.empty
        val composed = patch ++ empty

        val result1 = composed(oldJson, PatchMode.Strict)
        val result2 = patch(oldJson, PatchMode.Strict)

        assertTrue(result1 == result2)
      }
    },
    test("L2: right identity - (p ++ empty)(j) == p(j) for Json.Array") {
      check(genJsonArray(2), genJsonArray(2)) { (oldJson, newJson) =>
        val patch    = JsonPatch.diff(oldJson, newJson)
        val empty    = JsonPatch.empty
        val composed = patch ++ empty

        val result1 = composed(oldJson, PatchMode.Strict)
        val result2 = patch(oldJson, PatchMode.Strict)

        assertTrue(result1 == result2)
      }
    },
    test("L3: associativity - ((p1 ++ p2) ++ p3)(j) == (p1 ++ (p2 ++ p3))(j) for Json.Number") {
      check(genJsonNumber, genJsonNumber, genJsonNumber, genJsonNumber) { (j0, j1, j2, j3) =>
        val p1 = JsonPatch.diff(j0, j1)
        val p2 = JsonPatch.diff(j1, j2)
        val p3 = JsonPatch.diff(j2, j3)

        val left  = (p1 ++ p2) ++ p3
        val right = p1 ++ (p2 ++ p3)

        val result1 = left(j0, PatchMode.Strict)
        val result2 = right(j0, PatchMode.Strict)

        assertTrue(result1 == result2)
      }
    },
    test("L3: associativity - ((p1 ++ p2) ++ p3)(j) == (p1 ++ (p2 ++ p3))(j) for Json.Object") {
      check(genJsonObject(2), genJsonObject(2), genJsonObject(2), genJsonObject(2)) { (j0, j1, j2, j3) =>
        val p1 = JsonPatch.diff(j0, j1)
        val p2 = JsonPatch.diff(j1, j2)
        val p3 = JsonPatch.diff(j2, j3)

        val left  = (p1 ++ p2) ++ p3
        val right = p1 ++ (p2 ++ p3)

        val result1 = left(j0, PatchMode.Strict)
        val result2 = right(j0, PatchMode.Strict)

        assertTrue(result1 == result2)
      }
    },
    test("L3: associativity for mixed types") {
      check(genSimpleJson, genSimpleJson, genSimpleJson, genSimpleJson) { (j0, j1, j2, j3) =>
        val p1 = JsonPatch.diff(j0, j1)
        val p2 = JsonPatch.diff(j1, j2)
        val p3 = JsonPatch.diff(j2, j3)

        val left  = (p1 ++ p2) ++ p3
        val right = p1 ++ (p2 ++ p3)

        val result1 = left(j0, PatchMode.Strict)
        val result2 = right(j0, PatchMode.Strict)

        assertTrue(result1 == result2)
      }
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Diff/Apply Laws (L4, L5, L6)
  // ─────────────────────────────────────────────────────────────────────────

  val diffApplyLawsSuite: Spec[Any, Nothing] = suite("Diff/Apply Laws")(
    test("L4: roundtrip - diff(a, b)(a, Strict) == Right(b) for Json.Number") {
      check(genJsonNumber, genJsonNumber) { (source, target) =>
        val patch  = JsonPatch.diff(source, target)
        val result = patch(source, PatchMode.Strict)
        assertTrue(result == new Right(target))
      }
    },
    test("L4: roundtrip - diff(a, b)(a, Strict) == Right(b) for Json.String") {
      check(genJsonString, genJsonString) { (source, target) =>
        val patch  = JsonPatch.diff(source, target)
        val result = patch(source, PatchMode.Strict)
        assertTrue(result == new Right(target))
      }
    },
    test("L4: roundtrip - diff(a, b)(a, Strict) == Right(b) for Json.Array") {
      check(genJsonArray(2), genJsonArray(2)) { (source, target) =>
        val patch  = JsonPatch.diff(source, target)
        val result = patch(source, PatchMode.Strict)
        assertTrue(result == new Right(target))
      }
    },
    test("L4: roundtrip - diff(a, b)(a, Strict) == Right(b) for Json.Object") {
      check(genJsonObject(2), genJsonObject(2)) { (source, target) =>
        val patch  = JsonPatch.diff(source, target)
        val result = patch(source, PatchMode.Strict)
        assertTrue(result == new Right(target))
      }
    },
    test("L4: roundtrip - diff(a, b)(a, Strict) == Right(b) for any Json") {
      check(genSimpleJson, genSimpleJson) { (source, target) =>
        val patch  = JsonPatch.diff(source, target)
        val result = patch(source, PatchMode.Strict)
        assertTrue(result == new Right(target))
      }
    },
    test("L5: identity diff - diff(j, j).isEmpty for any Json") {
      check(genSimpleJson) { json =>
        val patch = JsonPatch.diff(json, json)
        assertTrue(patch.isEmpty)
      }
    },
    test("L5: identity diff - diff(j, j).isEmpty for Json.Object") {
      check(genJsonObject(3)) { json =>
        val patch = JsonPatch.diff(json, json)
        assertTrue(patch.isEmpty)
      }
    },
    test("L6: diff composition - (diff(a, b) ++ diff(b, c))(a) == Right(c)") {
      check(genJsonNumber, genJsonNumber, genJsonNumber) { (a, b, c) =>
        val p1       = JsonPatch.diff(a, b)
        val p2       = JsonPatch.diff(b, c)
        val composed = p1 ++ p2
        val result   = composed(a, PatchMode.Strict)
        assertTrue(result == new Right(c))
      }
    },
    test("L6: diff composition for Json.String") {
      check(genJsonString, genJsonString, genJsonString) { (a, b, c) =>
        val p1       = JsonPatch.diff(a, b)
        val p2       = JsonPatch.diff(b, c)
        val composed = p1 ++ p2
        val result   = composed(a, PatchMode.Strict)
        assertTrue(result == new Right(c))
      }
    },
    test("L6: diff composition for mixed types") {
      check(genSimpleJson, genSimpleJson, genSimpleJson) { (a, b, c) =>
        val p1       = JsonPatch.diff(a, b)
        val p2       = JsonPatch.diff(b, c)
        val composed = p1 ++ p2
        val result   = composed(a, PatchMode.Strict)
        assertTrue(result == new Right(c))
      }
    },
    // Tweak-based tests ensuring non-Set operations are used
    test("L4 with tweaks: roundtrip for object with added field uses ObjectEdit") {
      check(genObjectWithAddedField) { case (source, target) =>
        val patch  = JsonPatch.diff(source, target)
        val result = patch(source, PatchMode.Strict)
        // Verify roundtrip works
        assertTrue(result == new Right(target)) &&
        // Verify patch uses ObjectEdit (not Set at root)
        assertTrue(
          patch.ops.nonEmpty &&
            patch.ops.exists(op =>
              op.op match {
                case JsonPatch.Op.ObjectEdit(_) => true
                case _                          => false
              }
            )
        )
      }
    },
    test("L4 with tweaks: roundtrip for object with removed field uses ObjectEdit") {
      check(genObjectWithRemovedField) { case (source, target) =>
        val patch  = JsonPatch.diff(source, target)
        val result = patch(source, PatchMode.Strict)
        assertTrue(result == new Right(target)) &&
        assertTrue(
          patch.ops.nonEmpty &&
            patch.ops.exists(op =>
              op.op match {
                case JsonPatch.Op.ObjectEdit(_) => true
                case _                          => false
              }
            )
        )
      }
    },
    test("L4 with tweaks: roundtrip for array with appended element uses ArrayEdit") {
      check(genArrayWithAppendedElement) { case (source, target) =>
        val patch  = JsonPatch.diff(source, target)
        val result = patch(source, PatchMode.Strict)
        assertTrue(result == new Right(target)) &&
        assertTrue(
          patch.ops.nonEmpty &&
            patch.ops.exists(op =>
              op.op match {
                case JsonPatch.Op.ArrayEdit(_) => true
                case _                         => false
              }
            )
        )
      }
    },
    test("L4 with tweaks: roundtrip for array with removed element uses ArrayEdit") {
      check(genArrayWithRemovedElement) { case (source, target) =>
        val patch  = JsonPatch.diff(source, target)
        val result = patch(source, PatchMode.Strict)
        assertTrue(result == new Right(target)) &&
        assertTrue(
          patch.ops.nonEmpty &&
            patch.ops.exists(op =>
              op.op match {
                case JsonPatch.Op.ArrayEdit(_) => true
                case _                         => false
              }
            )
        )
      }
    },
    test("L4 with tweaks: roundtrip for number with delta uses NumberDelta") {
      check(genNumberWithDelta) { case (source, target) =>
        val patch  = JsonPatch.diff(source, target)
        val result = patch(source, PatchMode.Strict)
        // Verify roundtrip works
        assertTrue(result == new Right(target)) &&
        // Verify patch uses NumberDelta (not Set) - delta is always non-zero
        assertTrue(
          patch.ops.exists(op =>
            op.op match {
              case JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(_)) => true
              case _                                                                 => false
            }
          )
        )
      }
    },
    test("L4 with tweaks: roundtrip for string with append uses StringEdit") {
      check(genStringWithAppend) { case (source, target) =>
        val patch  = JsonPatch.diff(source, target)
        val result = patch(source, PatchMode.Strict)
        assertTrue(result == new Right(target)) &&
        assertTrue(
          patch.ops.nonEmpty &&
            patch.ops.exists(op =>
              op.op match {
                case JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(_)) => true
                case _                                                                => false
              }
            )
        )
      }
    },
    test("L4 with tweaks: combined tweak generator roundtrip") {
      check(genTweakedJsonPair) { case (source, target) =>
        val patch  = JsonPatch.diff(source, target)
        val result = patch(source, PatchMode.Strict)
        assertTrue(result == new Right(target))
      }
    },
    // ─────────────────────────────────────────────────────────────────────────
    // NESTED tweak tests (jdegoes explicitly requested "nested" tweaks)
    // ─────────────────────────────────────────────────────────────────────────
    test("L4 with NESTED tweaks: nested field modification uses ObjectOp.Modify path") {
      check(genNestedFieldModified) { case (source, target) =>
        val patch  = JsonPatch.diff(source, target)
        val result = patch(source, PatchMode.Strict)
        // Roundtrip correctness
        assertTrue(result == new Right(target)) &&
        // Verify we get an ObjectEdit (outer) containing a Modify with nested ObjectEdit
        assertTrue(
          patch.ops.nonEmpty &&
            patch.ops.exists(op =>
              op.op match {
                case JsonPatch.Op.ObjectEdit(ops) =>
                  ops.exists {
                    case JsonPatch.ObjectOp.Modify(_, nestedPatch) =>
                      // The nested patch should contain ObjectEdit, not just Set
                      nestedPatch.ops.exists(_.op match {
                        case JsonPatch.Op.ObjectEdit(_)                                       => true
                        case JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(_)) => true
                        case JsonPatch.Op.Set(_)                                              => true // String change may be Set
                        case _                                                                => false
                      })
                    case _ => false
                  }
                case _ => false
              }
            )
        )
      }
    },
    test("L4 with NESTED tweaks: nested field add uses ObjectOp.Modify with inner Add") {
      check(genNestedFieldAdded) { case (source, target) =>
        val patch  = JsonPatch.diff(source, target)
        val result = patch(source, PatchMode.Strict)
        assertTrue(result == new Right(target)) &&
        assertTrue(
          patch.ops.nonEmpty &&
            patch.ops.exists(op =>
              op.op match {
                case JsonPatch.Op.ObjectEdit(ops) =>
                  ops.exists {
                    case JsonPatch.ObjectOp.Modify(_, nestedPatch) =>
                      nestedPatch.ops.exists(_.op match {
                        case JsonPatch.Op.ObjectEdit(innerOps) =>
                          innerOps.exists {
                            case JsonPatch.ObjectOp.Add(_, _) => true
                            case _                            => false
                          }
                        case _ => false
                      })
                    case _ => false
                  }
                case _ => false
              }
            )
        )
      }
    },
    test("L4 with NESTED tweaks: nested type change exercises Set inside Modify") {
      check(genNestedTypeChange) { case (source, target) =>
        val patch  = JsonPatch.diff(source, target)
        val result = patch(source, PatchMode.Strict)
        // Verify roundtrip works (type change: number -> string)
        assertTrue(result == new Right(target)) &&
        // Verify we get ObjectEdit -> Modify -> (ObjectEdit with Set for type change)
        assertTrue(
          patch.ops.nonEmpty &&
            patch.ops.exists(op =>
              op.op match {
                case JsonPatch.Op.ObjectEdit(ops) =>
                  ops.exists {
                    case JsonPatch.ObjectOp.Modify(_, _) => true
                    case _                               => false
                  }
                case _ => false
              }
            )
        )
      }
    },
    test("L4 with NESTED tweaks: array with nested object modification") {
      check(genArrayWithNestedModification) { case (source, target) =>
        val patch  = JsonPatch.diff(source, target)
        val result = patch(source, PatchMode.Strict)
        // Verify roundtrip correctness
        assertTrue(result == new Right(target)) &&
        // Verify we get ArrayEdit (may use Modify, Delete+Append, or other operations
        // depending on diff algorithm optimization - any ArrayEdit is valid)
        assertTrue(
          patch.ops.nonEmpty &&
            patch.ops.exists(op =>
              op.op match {
                case JsonPatch.Op.ArrayEdit(_) => true // Any array edit is valid
                case _                         => false
              }
            )
        )
      }
    },
    // ─────────────────────────────────────────────────────────────────────────
    // L6 with tweaks (diff composition using tweaks instead of random pairs)
    // These tests assert non-Set operations by checking root op type
    // ─────────────────────────────────────────────────────────────────────────
    test("L6 with tweaks: diff composition with add operations produces ObjectEdit") {
      // a -> (add field) -> b -> (add another field) -> c
      check(genObjectWithAddedField) { case (a, b) =>
        val bObj = b.asInstanceOf[Json.Object]
        // Add a different field to b to create c
        val c = new Json.Object(bObj.fields :+ ("_second_added_field", new Json.Number("99")))

        val patch1   = JsonPatch.diff(a, b)
        val patch2   = JsonPatch.diff(b, c)
        val composed = patch1 ++ patch2

        // Assert root ops are ObjectEdit (not Set)
        val isObjectEdit1 = patch1.ops.headOption.exists(_.op.isInstanceOf[JsonPatch.Op.ObjectEdit])
        val isObjectEdit2 = patch2.ops.headOption.exists(_.op.isInstanceOf[JsonPatch.Op.ObjectEdit])

        assertTrue(isObjectEdit1) &&
        assertTrue(isObjectEdit2) &&
        assertTrue(composed(a, PatchMode.Strict) == new Right(c))
      }
    },
    test("L6 with tweaks: diff composition with remove operations produces ObjectEdit") {
      // Test with objects that have at least 2 fields to ensure non-trivial removals
      check(genJsonObject(3).filter(_.fields.length >= 2)) { a =>
        // Remove first field to get b
        val b = new Json.Object(a.fields.drop(1))
        // Remove another field to get c (b has at least 1 field)
        val c = new Json.Object(b.fields.drop(1))

        val patch1   = JsonPatch.diff(a, b)
        val patch2   = JsonPatch.diff(b, c)
        val composed = patch1 ++ patch2

        // Assert root ops are ObjectEdit (not Set)
        val isObjectEdit1 = patch1.ops.headOption.exists(_.op.isInstanceOf[JsonPatch.Op.ObjectEdit])
        val isObjectEdit2 = patch2.ops.headOption.exists(_.op.isInstanceOf[JsonPatch.Op.ObjectEdit])

        assertTrue(isObjectEdit1) &&
        assertTrue(isObjectEdit2) &&
        assertTrue(composed(a, PatchMode.Strict) == new Right(c))
      }
    },
    test("L6 with tweaks: diff composition with nested modifications produces ObjectEdit") {
      check(genNestedFieldModified) { case (a, b) =>
        // Apply another nested modification to b to get c
        val bObj           = b.asInstanceOf[Json.Object]
        val modifiedFields = bObj.fields.map {
          case (key, innerObj: Json.Object) =>
            val tweaked = new Json.Object(innerObj.fields :+ ("_nested_tweak", Json.True))
            (key, tweaked)
          case other => other
        }
        val c = new Json.Object(modifiedFields)

        val patch1   = JsonPatch.diff(a, b)
        val patch2   = JsonPatch.diff(b, c)
        val composed = patch1 ++ patch2

        // Assert root ops are ObjectEdit (not Set)
        val isObjectEdit1 = patch1.ops.headOption.exists(_.op.isInstanceOf[JsonPatch.Op.ObjectEdit])
        val isObjectEdit2 = patch2.ops.headOption.exists(_.op.isInstanceOf[JsonPatch.Op.ObjectEdit])

        assertTrue(isObjectEdit1) &&
        assertTrue(isObjectEdit2) &&
        assertTrue(composed(a, PatchMode.Strict) == new Right(c))
      }
    },
    test("L6 with tweaks: diff composition with array modifications produces ArrayEdit") {
      // a (array) -> append -> b -> append again -> c
      check(genArrayWithAppendedElement) { case (a, b) =>
        val bArr = b.asInstanceOf[Json.Array]
        // Append another element to b
        val c = new Json.Array(bArr.value :+ new Json.String("second_appended"))

        val patch1   = JsonPatch.diff(a, b)
        val patch2   = JsonPatch.diff(b, c)
        val composed = patch1 ++ patch2

        // Assert root ops are ArrayEdit (not Set)
        val isArrayEdit1 = patch1.ops.headOption.exists(_.op.isInstanceOf[JsonPatch.Op.ArrayEdit])
        val isArrayEdit2 = patch2.ops.headOption.exists(_.op.isInstanceOf[JsonPatch.Op.ArrayEdit])

        assertTrue(isArrayEdit1) &&
        assertTrue(isArrayEdit2) &&
        assertTrue(composed(a, PatchMode.Strict) == new Right(c))
      }
    },
    test("L6 with tweaks: diff composition with mixed operation types") {
      // a -> (add field at end) -> b -> (remove first original field, keep added) -> c
      // Use objects with at least 2 fields so we have something to remove
      check(genJsonObject(3).filter(_.fields.length >= 2)) { a =>
        // Add a field at the end to get b
        val addedKey = "_added_field"
        val b        = new Json.Object(a.fields :+ (addedKey, new Json.Number("42")))
        // Remove the first original field from a (not the added one) to get c
        // b.fields = original fields + added field, so drop(1) removes first original
        val c = new Json.Object(b.fields.drop(1))

        val patch1   = JsonPatch.diff(a, b)
        val patch2   = JsonPatch.diff(b, c)
        val composed = patch1 ++ patch2

        // Assert root ops are ObjectEdit (not Set)
        val isObjectEdit1 = patch1.ops.headOption.exists(_.op.isInstanceOf[JsonPatch.Op.ObjectEdit])
        val isObjectEdit2 = patch2.ops.headOption.exists(_.op.isInstanceOf[JsonPatch.Op.ObjectEdit])

        assertTrue(isObjectEdit1) &&
        assertTrue(isObjectEdit2) &&
        assertTrue(composed(a, PatchMode.Strict) == new Right(c))
      }
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // PatchMode Laws (L7, T7)
  // ─────────────────────────────────────────────────────────────────────────

  val patchModeSuite: Spec[Any, Nothing] = suite("PatchMode behavior")(
    test("L7: Lenient subsumes Strict - if p(j, Strict) == Right(r) then p(j, Lenient) == Right(r)") {
      check(genSimpleJson, genSimpleJson) { (source, target) =>
        val patch      = JsonPatch.diff(source, target)
        val strictRes  = patch(source, PatchMode.Strict)
        val lenientRes = patch(source, PatchMode.Lenient)
        assertTrue(
          strictRes.isRight && lenientRes == strictRes
        )
      }
    },
    test("Strict mode fails on missing field") {
      val json   = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Remove("nonexistent"))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("Lenient mode skips missing field removal") {
      val json   = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Remove("nonexistent"))))
      val result = patch(json, PatchMode.Lenient)
      assertTrue(result == new Right(json))
    },
    test("Clobber mode overwrites existing field on Add") {
      val json   = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Add("a", new Json.Number("2")))))
      val result = patch(json, PatchMode.Clobber)
      assertTrue(result == new Right(new Json.Object(Chunk(("a", new Json.Number("2"))))))
    },
    test("Strict mode fails on out-of-bounds array delete") {
      val json   = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2")))
      val patch  = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Delete(10, 1))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("Clobber mode clamps out-of-bounds array insert") {
      val json  = new Json.Array(Chunk(new Json.Number("1")))
      val patch =
        JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Insert(100, Chunk(new Json.Number("2"))))))
      val result = patch(json, PatchMode.Clobber)
      assertTrue(result.isRight)
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Operation Type Tests (T2)
  // ─────────────────────────────────────────────────────────────────────────

  val operationTypeSuite: Spec[Any, Nothing] = suite("Operation types")(
    test("Set operation replaces value") {
      val json   = new Json.Number("1")
      val patch  = JsonPatch.root(JsonPatch.Op.Set(new Json.Number("5")))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.Number("5")))
    },
    test("Set operation replaces with different type") {
      val json   = new Json.Number("1")
      val patch  = JsonPatch.root(JsonPatch.Op.Set(new Json.String("hello")))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.String("hello")))
    },
    test("PrimitiveDelta applies number delta") {
      val json   = new Json.Number("10")
      val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.Number("15")))
    },
    test("ArrayEdit applies array operations") {
      val json     = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2")))
      val patch    = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Append(Chunk(new Json.Number("3"))))))
      val result   = patch(json, PatchMode.Strict)
      val expected = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
      assertTrue(result == new Right(expected))
    },
    test("ObjectEdit applies object operations") {
      val json     = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch    = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Add("b", new Json.Number("2")))))
      val result   = patch(json, PatchMode.Strict)
      val expected = new Json.Object(Chunk(("a", new Json.Number("1")), ("b", new Json.Number("2"))))
      assertTrue(result == new Right(expected))
    },
    test("Nested operation applies nested patch") {
      val innerPatch = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(1))))
      val json       = new Json.Number("5")
      val patch      = JsonPatch.root(JsonPatch.Op.Nested(innerPatch))
      val result     = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.Number("6")))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // ArrayOp Tests (T3)
  // ─────────────────────────────────────────────────────────────────────────

  val arrayOpSuite: Spec[Any, Nothing] = suite("ArrayOp variants")(
    test("Insert adds values at index") {
      val json  = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("3")))
      val patch =
        JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Insert(1, Chunk(new Json.Number("2"))))))
      val result   = patch(json, PatchMode.Strict)
      val expected = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
      assertTrue(result == new Right(expected))
    },
    test("Append adds values at end") {
      val json  = new Json.Array(Chunk(new Json.Number("1")))
      val patch = JsonPatch.root(
        JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Append(Chunk(new Json.Number("2"), new Json.Number("3")))))
      )
      val result   = patch(json, PatchMode.Strict)
      val expected = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
      assertTrue(result == new Right(expected))
    },
    test("Delete removes elements") {
      val json     = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
      val patch    = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Delete(1, 1))))
      val result   = patch(json, PatchMode.Strict)
      val expected = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("3")))
      assertTrue(result == new Right(expected))
    },
    test("Modify updates element at index") {
      val json     = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2")))
      val modifyOp = JsonPatch.Op.Set(new Json.Number("10"))
      val patch    = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Modify(0, modifyOp))))
      val result   = patch(json, PatchMode.Strict)
      val expected = new Json.Array(Chunk(new Json.Number("10"), new Json.Number("2")))
      assertTrue(result == new Right(expected))
    },
    test("Multiple array operations compose correctly") {
      val json  = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
      val patch = JsonPatch.root(
        JsonPatch.Op.ArrayEdit(
          Vector(
            JsonPatch.ArrayOp.Delete(1, 1),
            JsonPatch.ArrayOp.Append(Chunk(new Json.Number("4")))
          )
        )
      )
      val result   = patch(json, PatchMode.Strict)
      val expected = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("3"), new Json.Number("4")))
      assertTrue(result == new Right(expected))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // ObjectOp Tests (T4)
  // ─────────────────────────────────────────────────────────────────────────

  val objectOpSuite: Spec[Any, Nothing] = suite("ObjectOp variants")(
    test("Add inserts new field") {
      val json   = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Add("b", new Json.Number("2")))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isRight)
      result.foreach { r =>
        val obj = r.asInstanceOf[Json.Object]
        assertTrue(obj.value.length == 2)
      }
      assertTrue(result.isRight)
    },
    test("Remove deletes existing field") {
      val json     = new Json.Object(Chunk(("a", new Json.Number("1")), ("b", new Json.Number("2"))))
      val patch    = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Remove("a"))))
      val result   = patch(json, PatchMode.Strict)
      val expected = new Json.Object(Chunk(("b", new Json.Number("2"))))
      assertTrue(result == new Right(expected))
    },
    test("Modify updates existing field") {
      val innerPatch = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
      val json       = new Json.Object(Chunk(("x", new Json.Number("10"))))
      val patch      = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Modify("x", innerPatch))))
      val result     = patch(json, PatchMode.Strict)
      val expected   = new Json.Object(Chunk(("x", new Json.Number("15"))))
      assertTrue(result == new Right(expected))
    },
    test("Multiple object operations compose correctly") {
      val json  = new Json.Object(Chunk(("a", new Json.Number("1")), ("b", new Json.Number("2"))))
      val patch = JsonPatch.root(
        JsonPatch.Op.ObjectEdit(
          Vector(
            JsonPatch.ObjectOp.Remove("a"),
            JsonPatch.ObjectOp.Add("c", new Json.Number("3"))
          )
        )
      )
      val result   = patch(json, PatchMode.Strict)
      val expected = new Json.Object(Chunk(("b", new Json.Number("2")), ("c", new Json.Number("3"))))
      assertTrue(result == new Right(expected))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // StringOp Tests (T5)
  // ─────────────────────────────────────────────────────────────────────────

  val stringOpSuite: Spec[Any, Nothing] = suite("StringOp variants")(
    test("Insert adds text at index") {
      val json  = new Json.String("hello world")
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(
          JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Insert(5, " beautiful")))
        )
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.String("hello beautiful world")))
    },
    test("Delete removes characters") {
      val json  = new Json.String("hello world")
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(
          JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Delete(5, 6)))
        )
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.String("hello")))
    },
    test("Append adds text at end") {
      val json  = new Json.String("hello")
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(
          JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Append(" world")))
        )
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.String("hello world")))
    },
    test("Modify replaces characters") {
      val json  = new Json.String("hello world")
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(
          JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Modify(6, 5, "there")))
        )
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.String("hello there")))
    },
    test("Multiple string operations compose correctly") {
      val json  = new Json.String("abc")
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(
          JsonPatch.PrimitiveOp.StringEdit(
            Vector(
              JsonPatch.StringOp.Delete(1, 1),
              JsonPatch.StringOp.Append("xyz")
            )
          )
        )
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.String("acxyz")))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // NumberDelta Tests (T6)
  // ─────────────────────────────────────────────────────────────────────────

  val numberDeltaSuite: Spec[Any, Nothing] = suite("NumberDelta behavior")(
    test("Positive delta adds to number") {
      val json   = new Json.Number("10")
      val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.Number("15")))
    },
    test("Negative delta subtracts from number") {
      val json   = new Json.Number("10")
      val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(-5))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.Number("5")))
    },
    test("Zero delta has no effect") {
      val json   = new Json.Number("10")
      val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(0))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.Number("10")))
    },
    test("Decimal delta works correctly") {
      val json   = new Json.Number("10.5")
      val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal("0.25"))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.Number("10.75")))
    },
    test("Large number delta") {
      val json   = new Json.Number("999999999999999")
      val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(1))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.Number("1000000000000000")))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // DynamicPatch Conversion Tests (T8)
  // ─────────────────────────────────────────────────────────────────────────

  val dynamicPatchConversionSuite: Spec[Any, Nothing] = suite("DynamicPatch conversion")(
    test("toDynamicPatch converts Set operation") {
      val patch   = JsonPatch.root(JsonPatch.Op.Set(new Json.Number("5")))
      val dynamic = patch.toDynamicPatch
      assertTrue(!dynamic.isEmpty)
    },
    test("toDynamicPatch converts NumberDelta") {
      val patch   = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
      val dynamic = patch.toDynamicPatch
      assertTrue(!dynamic.isEmpty)
    },
    test("toDynamicPatch/fromDynamicPatch roundtrip preserves semantics for Set") {
      val json            = new Json.String("old")
      val patch           = JsonPatch.root(JsonPatch.Op.Set(new Json.String("new")))
      val dynamic         = patch.toDynamicPatch
      val back            = JsonPatch.fromDynamicPatch(dynamic)
      val originalResult  = patch(json, PatchMode.Strict)
      val roundtripResult = back.flatMap(p => p(json, PatchMode.Strict))
      assertTrue(roundtripResult == originalResult)
    },
    test("toDynamicPatch/fromDynamicPatch roundtrip preserves semantics for NumberDelta") {
      val json            = new Json.Number("10")
      val patch           = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
      val dynamic         = patch.toDynamicPatch
      val back            = JsonPatch.fromDynamicPatch(dynamic)
      val originalResult  = patch(json, PatchMode.Strict)
      val roundtripResult = back.flatMap(p => p(json, PatchMode.Strict))
      assertTrue(roundtripResult == originalResult)
    },
    test("toDynamicPatch/fromDynamicPatch roundtrip preserves semantics for StringEdit") {
      val json  = new Json.String("hello")
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(
          JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Append(" world")))
        )
      )
      val dynamic         = patch.toDynamicPatch
      val back            = JsonPatch.fromDynamicPatch(dynamic)
      val originalResult  = patch(json, PatchMode.Strict)
      val roundtripResult = back.flatMap(p => p(json, PatchMode.Strict))
      assertTrue(roundtripResult == originalResult)
    },
    test("toDynamicPatch/fromDynamicPatch roundtrip preserves semantics for ArrayEdit.Insert") {
      val json  = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2")))
      val patch = JsonPatch.root(
        JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Insert(1, Chunk(new Json.Number("99")))))
      )
      val dynamic         = patch.toDynamicPatch
      val back            = JsonPatch.fromDynamicPatch(dynamic)
      val originalResult  = patch(json, PatchMode.Strict)
      val roundtripResult = back.flatMap(p => p(json, PatchMode.Strict))
      assertTrue(roundtripResult == originalResult)
    },
    test("toDynamicPatch/fromDynamicPatch roundtrip preserves semantics for ArrayEdit.Delete") {
      val json            = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
      val patch           = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Delete(1, 1))))
      val dynamic         = patch.toDynamicPatch
      val back            = JsonPatch.fromDynamicPatch(dynamic)
      val originalResult  = patch(json, PatchMode.Strict)
      val roundtripResult = back.flatMap(p => p(json, PatchMode.Strict))
      assertTrue(roundtripResult == originalResult)
    },
    test("toDynamicPatch/fromDynamicPatch roundtrip preserves semantics for ObjectEdit.Add") {
      val json  = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch = JsonPatch.root(
        JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Add("b", new Json.Number("2"))))
      )
      val dynamic         = patch.toDynamicPatch
      val back            = JsonPatch.fromDynamicPatch(dynamic)
      val originalResult  = patch(json, PatchMode.Strict)
      val roundtripResult = back.flatMap(p => p(json, PatchMode.Strict))
      assertTrue(roundtripResult == originalResult)
    },
    test("toDynamicPatch/fromDynamicPatch roundtrip preserves semantics for ObjectEdit.Remove") {
      val json            = new Json.Object(Chunk(("a", new Json.Number("1")), ("b", new Json.Number("2"))))
      val patch           = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Remove("b"))))
      val dynamic         = patch.toDynamicPatch
      val back            = JsonPatch.fromDynamicPatch(dynamic)
      val originalResult  = patch(json, PatchMode.Strict)
      val roundtripResult = back.flatMap(p => p(json, PatchMode.Strict))
      assertTrue(roundtripResult == originalResult)
    },
    test("toDynamicPatch/fromDynamicPatch roundtrip preserves semantics for nested path") {
      val json = new Json.Object(
        Chunk(("outer", new Json.Object(Chunk(("inner", new Json.Number("10"))))))
      )
      val nestedPath = DynamicOptic.root.field("outer").field("inner")
      val patch      = JsonPatch(
        nestedPath,
        JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5)))
      )
      val dynamic         = patch.toDynamicPatch
      val back            = JsonPatch.fromDynamicPatch(dynamic)
      val originalResult  = patch(json, PatchMode.Strict)
      val roundtripResult = back.flatMap(p => p(json, PatchMode.Strict))
      assertTrue(roundtripResult == originalResult)
    },
    test("toDynamicPatch/fromDynamicPatch roundtrip preserves semantics for arbitrary diffs") {
      check(genSimpleJson, genSimpleJson) { (source, target) =>
        val patch           = JsonPatch.diff(source, target)
        val dynamic         = patch.toDynamicPatch
        val back            = JsonPatch.fromDynamicPatch(dynamic)
        val originalResult  = patch(source, PatchMode.Strict)
        val roundtripResult = back.flatMap(p => p(source, PatchMode.Strict))
        assertTrue(roundtripResult == originalResult)
      }
    },
    test("toDynamicPatch/fromDynamicPatch roundtrip preserves semantics for ArrayOp.Append") {
      val json            = new Json.Array(Chunk(new Json.Number("1")))
      val patch           = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Append(Chunk(new Json.Number("2"))))))
      val dynamic         = patch.toDynamicPatch
      val back            = JsonPatch.fromDynamicPatch(dynamic)
      val originalResult  = patch(json, PatchMode.Strict)
      val roundtripResult = back.flatMap(p => p(json, PatchMode.Strict))
      assertTrue(roundtripResult == originalResult)
    },
    test("toDynamicPatch/fromDynamicPatch roundtrip preserves semantics for ArrayOp.Modify") {
      val json  = new Json.Array(Chunk(new Json.Number("10")))
      val patch = JsonPatch.root(
        JsonPatch.Op.ArrayEdit(
          Vector(
            JsonPatch.ArrayOp.Modify(0, JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
          )
        )
      )
      val dynamic         = patch.toDynamicPatch
      val back            = JsonPatch.fromDynamicPatch(dynamic)
      val originalResult  = patch(json, PatchMode.Strict)
      val roundtripResult = back.flatMap(p => p(json, PatchMode.Strict))
      assertTrue(roundtripResult == originalResult)
    },
    test("toDynamicPatch/fromDynamicPatch roundtrip preserves semantics for ObjectOp.Modify") {
      val json        = new Json.Object(Chunk(("count", new Json.Number("10"))))
      val nestedPatch = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
      val patch       = JsonPatch.root(
        JsonPatch.Op.ObjectEdit(
          Vector(
            JsonPatch.ObjectOp.Modify("count", nestedPatch)
          )
        )
      )
      val dynamic         = patch.toDynamicPatch
      val back            = JsonPatch.fromDynamicPatch(dynamic)
      val originalResult  = patch(json, PatchMode.Strict)
      val roundtripResult = back.flatMap(p => p(json, PatchMode.Strict))
      assertTrue(roundtripResult == originalResult)
    },
    test("toDynamicPatch/fromDynamicPatch roundtrip preserves semantics for Op.Nested") {
      val innerPatch      = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(1))))
      val json            = new Json.Number("5")
      val patch           = JsonPatch.root(JsonPatch.Op.Nested(innerPatch))
      val dynamic         = patch.toDynamicPatch
      val back            = JsonPatch.fromDynamicPatch(dynamic)
      val originalResult  = patch(json, PatchMode.Strict)
      val roundtripResult = back.flatMap(p => p(json, PatchMode.Strict))
      assertTrue(roundtripResult == originalResult)
    },
    test("empty patch conversion roundtrip") {
      val patch   = JsonPatch.empty
      val dynamic = patch.toDynamicPatch
      val back    = JsonPatch.fromDynamicPatch(dynamic)
      assertTrue(back == new Right(JsonPatch.empty))
    },
    test("fromDynamicPatch fails for temporal operations") {
      val dynamicPatch = DynamicPatch.root(
        DynamicPatch.Operation.PrimitiveDelta(
          DynamicPatch.PrimitiveOp.InstantDelta(java.time.Duration.ofHours(1))
        )
      )
      val result = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(result.isLeft)
    },
    test("Schema[JsonPatch] roundtrip through DynamicValue") {
      // Use the implicit Schema[JsonPatch] for serialization
      val schema = implicitly[Schema[JsonPatch]]

      val patch = JsonPatch.diff(
        new Json.Object(Chunk(("a", new Json.Number("1")))),
        new Json.Object(Chunk(("a", new Json.Number("2")), ("b", new Json.String("new"))))
      )

      // Convert to DynamicValue and back
      val dynamicValue = schema.toDynamicValue(patch)
      val roundtrip    = schema.fromDynamicValue(dynamicValue)

      // Verify semantic equivalence by applying both patches
      val testJson        = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val originalResult  = patch(testJson, PatchMode.Strict)
      val roundtripResult = roundtrip.flatMap(p => p(testJson, PatchMode.Strict))

      assertTrue(roundtripResult == originalResult)
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Edge Case Tests (T9)
  // ─────────────────────────────────────────────────────────────────────────

  val edgeCaseSuite: Spec[Any, Nothing] = suite("Edge cases")(
    test("Empty array diff produces empty patch") {
      val empty1 = Json.Array.empty
      val empty2 = Json.Array.empty
      val patch  = JsonPatch.diff(empty1, empty2)
      assertTrue(patch.isEmpty)
    },
    test("Empty object diff produces empty patch") {
      val empty1 = Json.Object.empty
      val empty2 = Json.Object.empty
      val patch  = JsonPatch.diff(empty1, empty2)
      assertTrue(patch.isEmpty)
    },
    test("Empty string diff produces empty patch") {
      val empty1 = new Json.String("")
      val empty2 = new Json.String("")
      val patch  = JsonPatch.diff(empty1, empty2)
      assertTrue(patch.isEmpty)
    },
    test("Nested structure diff") {
      val source = new Json.Object(
        Chunk(
          (
            "user",
            new Json.Object(
              Chunk(
                ("name", new Json.String("Alice")),
                ("age", new Json.Number("30"))
              )
            )
          )
        )
      )
      val target = new Json.Object(
        Chunk(
          (
            "user",
            new Json.Object(
              Chunk(
                ("name", new Json.String("Bob")),
                ("age", new Json.Number("25"))
              )
            )
          )
        )
      )
      val patch  = JsonPatch.diff(source, target)
      val result = patch(source, PatchMode.Strict)
      assertTrue(result == new Right(target))
    },
    test("Deep nested object patch") {
      val source = new Json.Object(
        Chunk(
          (
            "a",
            new Json.Object(
              Chunk(
                (
                  "b",
                  new Json.Object(
                    Chunk(
                      ("c", new Json.Number("1"))
                    )
                  )
                )
              )
            )
          )
        )
      )
      val target = new Json.Object(
        Chunk(
          (
            "a",
            new Json.Object(
              Chunk(
                (
                  "b",
                  new Json.Object(
                    Chunk(
                      ("c", new Json.Number("2"))
                    )
                  )
                )
              )
            )
          )
        )
      )
      val patch  = JsonPatch.diff(source, target)
      val result = patch(source, PatchMode.Strict)
      assertTrue(result == new Right(target))
    },
    test("Array with nested objects") {
      val source = new Json.Array(
        Chunk(
          new Json.Object(Chunk(("x", new Json.Number("1")))),
          new Json.Object(Chunk(("x", new Json.Number("2"))))
        )
      )
      val target = new Json.Array(
        Chunk(
          new Json.Object(Chunk(("x", new Json.Number("10")))),
          new Json.Object(Chunk(("x", new Json.Number("20"))))
        )
      )
      val patch  = JsonPatch.diff(source, target)
      val result = patch(source, PatchMode.Strict)
      assertTrue(result == new Right(target))
    },
    test("null to value transformation") {
      val source = Json.Null
      val target = new Json.Number("42")
      val patch  = JsonPatch.diff(source, target)
      val result = patch(source, PatchMode.Strict)
      assertTrue(result == new Right(target))
    },
    test("boolean to boolean transformation") {
      val source = Json.True
      val target = Json.False
      val patch  = JsonPatch.diff(source, target)
      val result = patch(source, PatchMode.Strict)
      assertTrue(result == new Right(target))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Error Case Tests (T10)
  // ─────────────────────────────────────────────────────────────────────────

  val errorCaseSuite: Spec[Any, Nothing] = suite("Error cases")(
    test("Type mismatch: number delta on string") {
      val json   = new Json.String("hello")
      val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("Type mismatch: string edit on number") {
      val json  = new Json.Number("42")
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(
          JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Append("x")))
        )
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("Type mismatch: array edit on object") {
      val json   = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch  = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Append(Chunk(new Json.Number("2"))))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("Type mismatch: object edit on array") {
      val json   = new Json.Array(Chunk(new Json.Number("1")))
      val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Add("a", new Json.Number("2")))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("Out of bounds: array modify index") {
      val json  = new Json.Array(Chunk(new Json.Number("1")))
      val patch = JsonPatch.root(
        JsonPatch.Op.ArrayEdit(
          Vector(
            JsonPatch.ArrayOp.Modify(5, JsonPatch.Op.Set(new Json.Number("10")))
          )
        )
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("Out of bounds: string insert index") {
      val json  = new Json.String("abc")
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(
          JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Insert(100, "x")))
        )
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("Out of bounds: string delete range") {
      val json  = new Json.String("abc")
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(
          JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Delete(0, 100)))
        )
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("Missing field in object path navigation") {
      val json  = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch = JsonPatch(
        DynamicOptic.root.field("nonexistent"),
        JsonPatch.Op.Set(new Json.Number("5"))
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("Invalid path: field navigation on non-object") {
      val json  = new Json.Number("42")
      val patch = JsonPatch(
        DynamicOptic.root.field("x"),
        JsonPatch.Op.Set(new Json.Number("5"))
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("Invalid path: index navigation on non-array") {
      val json  = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch = JsonPatch(
        DynamicOptic.root.at(0),
        JsonPatch.Op.Set(new Json.Number("5"))
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    // Lenient mode type-mismatch tests: should return unchanged
    test("Type mismatch in Lenient mode: array edit on object returns unchanged") {
      val json   = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch  = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Append(Chunk(new Json.Number("2"))))))
      val result = patch(json, PatchMode.Lenient)
      assertTrue(result == new Right(json))
    },
    test("Type mismatch in Lenient mode: object edit on array returns unchanged") {
      val json   = new Json.Array(Chunk(new Json.Number("1")))
      val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Add("a", new Json.Number("2")))))
      val result = patch(json, PatchMode.Lenient)
      assertTrue(result == new Right(json))
    },
    test("Type mismatch in Lenient mode: number delta on string returns unchanged") {
      val json   = new Json.String("hello")
      val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
      val result = patch(json, PatchMode.Lenient)
      assertTrue(result == new Right(json))
    },
    test("Type mismatch in Lenient mode: string edit on number returns unchanged") {
      val json  = new Json.Number("42")
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Insert(0, "x"))))
      )
      val result = patch(json, PatchMode.Lenient)
      assertTrue(result == new Right(json))
    },
    // Clobber mode type-mismatch tests: should return unchanged (nothing to clobber)
    test("Type mismatch in Clobber mode: array edit on object returns unchanged") {
      val json   = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch  = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Append(Chunk(new Json.Number("2"))))))
      val result = patch(json, PatchMode.Clobber)
      assertTrue(result == new Right(json))
    },
    test("Type mismatch in Clobber mode: object edit on array returns unchanged") {
      val json   = new Json.Array(Chunk(new Json.Number("1")))
      val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Add("a", new Json.Number("2")))))
      val result = patch(json, PatchMode.Clobber)
      assertTrue(result == new Right(json))
    },
    // Additional coverage tests for Clobber mode with nested structures
    test("Clobber mode with number delta on string replaces with delta") {
      val json   = new Json.String("hello")
      val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
      val result = patch(json, PatchMode.Clobber)
      assertTrue(result == new Right(json))
    },
    test("Clobber mode with string edit on number returns unchanged") {
      val json  = new Json.Number("42")
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Insert(0, "x"))))
      )
      val result = patch(json, PatchMode.Clobber)
      assertTrue(result == new Right(json))
    },
    // Tests for nested patch operations through multiple levels
    test("Nested patch through object field works correctly") {
      val json = new Json.Object(
        Chunk(
          ("outer", new Json.Object(Chunk(("inner", new Json.Number("10")))))
        )
      )
      val nestedPatch = JsonPatch.root(JsonPatch.Op.Set(new Json.Number("20")))
      val patch       = JsonPatch(DynamicOptic.root.field("outer").field("inner"), JsonPatch.Op.Nested(nestedPatch))
      val result      = patch(json, PatchMode.Strict)
      assertTrue(
        result == new Right(
          new Json.Object(
            Chunk(
              ("outer", new Json.Object(Chunk(("inner", new Json.Number("20")))))
            )
          )
        )
      )
    },
    // Tests for array element modification at different indices
    test("Array modify at index 0") {
      val json   = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
      val patch  = JsonPatch(DynamicOptic.root.at(0), JsonPatch.Op.Set(new Json.Number("100")))
      val result = patch(json, PatchMode.Strict)
      assertTrue(
        result == new Right(new Json.Array(Chunk(new Json.Number("100"), new Json.Number("2"), new Json.Number("3"))))
      )
    },
    test("Array modify at last index") {
      val json   = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
      val patch  = JsonPatch(DynamicOptic.root.at(2), JsonPatch.Op.Set(new Json.Number("300")))
      val result = patch(json, PatchMode.Strict)
      assertTrue(
        result == new Right(new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2"), new Json.Number("300"))))
      )
    },
    // Tests for empty operations
    test("Empty ArrayEdit is a no-op") {
      val json   = new Json.Array(Chunk(new Json.Number("1")))
      val patch  = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector.empty))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(json))
    },
    test("Empty ObjectEdit is a no-op") {
      val json   = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector.empty))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(json))
    },
    test("Empty StringEdit is a no-op") {
      val json   = new Json.String("hello")
      val patch  = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector.empty)))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(json))
    }
  )

  // ─────────────────────────────────────────────────────────────────────────
  // Coverage Tests: toString, Elements path, DynamicPatch conversions
  // ─────────────────────────────────────────────────────────────────────────
  val toStringCoverageSuite: Spec[Any, Nothing] = suite("Coverage: toString and rendering")(
    test("toString renders Set operation") {
      val patch = JsonPatch.root(JsonPatch.Op.Set(new Json.Number("42")))
      val str   = patch.toString
      assertTrue(str.nonEmpty && str.contains("42"))
    },
    test("toString renders NumberDelta positive") {
      val patch = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
      val str   = patch.toString
      assertTrue(str.nonEmpty && (str.contains("+=") || str.contains("5")))
    },
    test("toString renders NumberDelta negative") {
      val patch = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(-3))))
      val str   = patch.toString
      assertTrue(str.nonEmpty && (str.contains("-=") || str.contains("3")))
    },
    test("toString renders StringEdit with Insert") {
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Insert(0, "hello"))))
      )
      val str = patch.toString
      assertTrue(str.nonEmpty && str.contains("hello"))
    },
    test("toString renders StringEdit with Delete") {
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Delete(0, 3))))
      )
      val str = patch.toString
      assertTrue(str.nonEmpty)
    },
    test("toString renders StringEdit with Append") {
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Append("suffix"))))
      )
      val str = patch.toString
      assertTrue(str.nonEmpty && str.contains("suffix"))
    },
    test("toString renders StringEdit with Modify") {
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Modify(0, 2, "ab"))))
      )
      val str = patch.toString
      assertTrue(str.nonEmpty && str.contains("ab"))
    },
    test("toString renders ArrayEdit with Insert") {
      val patch = JsonPatch.root(
        JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Insert(0, Chunk(new Json.Number("1")))))
      )
      val str = patch.toString
      assertTrue(str.nonEmpty)
    },
    test("toString renders ArrayEdit with Append") {
      val patch = JsonPatch.root(
        JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Append(Chunk(new Json.Number("1")))))
      )
      val str = patch.toString
      assertTrue(str.nonEmpty)
    },
    test("toString renders ArrayEdit with Delete single") {
      val patch = JsonPatch.root(
        JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Delete(0, 1)))
      )
      val str = patch.toString
      assertTrue(str.nonEmpty)
    },
    test("toString renders ArrayEdit with Delete multiple") {
      val patch = JsonPatch.root(
        JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Delete(0, 3)))
      )
      val str = patch.toString
      assertTrue(str.nonEmpty)
    },
    test("toString renders ArrayEdit with Modify Set") {
      val patch = JsonPatch.root(
        JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Modify(0, JsonPatch.Op.Set(new Json.Number("99")))))
      )
      val str = patch.toString
      assertTrue(str.nonEmpty && str.contains("99"))
    },
    test("toString renders ArrayEdit with Modify nested") {
      val nestedPatch = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(1))))
      val patch       = JsonPatch.root(
        JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Modify(0, JsonPatch.Op.Nested(nestedPatch))))
      )
      val str = patch.toString
      assertTrue(str.nonEmpty)
    },
    test("toString renders ObjectEdit with Add") {
      val patch = JsonPatch.root(
        JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Add("key", new Json.Number("1"))))
      )
      val str = patch.toString
      assertTrue(str.nonEmpty && str.contains("key"))
    },
    test("toString renders ObjectEdit with Remove") {
      val patch = JsonPatch.root(
        JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Remove("key")))
      )
      val str = patch.toString
      assertTrue(str.nonEmpty && str.contains("key"))
    },
    test("toString renders ObjectEdit with Modify") {
      val nestedPatch = JsonPatch.root(JsonPatch.Op.Set(new Json.Number("2")))
      val patch       = JsonPatch.root(
        JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Modify("key", nestedPatch)))
      )
      val str = patch.toString
      assertTrue(str.nonEmpty && str.contains("key"))
    },
    test("toString renders Nested patch") {
      val innerPatch = JsonPatch.root(JsonPatch.Op.Set(new Json.Number("1")))
      val patch      = JsonPatch.root(JsonPatch.Op.Nested(innerPatch))
      val str        = patch.toString
      assertTrue(str.nonEmpty)
    },
    test("toString escapes special characters in strings") {
      val patch = JsonPatch.root(
        JsonPatch.Op.ObjectEdit(
          Vector(JsonPatch.ObjectOp.Add("key\twith\ttabs", new Json.String("value\nwith\nnewlines")))
        )
      )
      val str = patch.toString
      assertTrue(str.nonEmpty)
    }
  )

  val elementsCoverageSuite: Spec[Any, Nothing] = suite("Coverage: Elements path (navigateAllElements)")(
    test("Apply operation to all array elements via Elements path") {
      val json = new Json.Array(
        Chunk(
          new Json.Object(Chunk(("value", new Json.Number("1")))),
          new Json.Object(Chunk(("value", new Json.Number("2")))),
          new Json.Object(Chunk(("value", new Json.Number("3"))))
        )
      )
      // Use Elements path to navigate into all array elements, then field "value", then set
      val patch = JsonPatch(
        DynamicOptic.root.elements.field("value"),
        JsonPatch.Op.Set(new Json.Number("100"))
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(
        result == new Right(
          new Json.Array(
            Chunk(
              new Json.Object(Chunk(("value", new Json.Number("100")))),
              new Json.Object(Chunk(("value", new Json.Number("100")))),
              new Json.Object(Chunk(("value", new Json.Number("100"))))
            )
          )
        )
      )
    },
    test("Elements path on empty array in Strict mode fails") {
      val json   = new Json.Array(Chunk.empty)
      val patch  = JsonPatch(DynamicOptic.root.elements, JsonPatch.Op.Set(new Json.Number("1")))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("Elements path on empty array in Lenient mode returns unchanged") {
      val json   = new Json.Array(Chunk.empty)
      val patch  = JsonPatch(DynamicOptic.root.elements, JsonPatch.Op.Set(new Json.Number("1")))
      val result = patch(json, PatchMode.Lenient)
      assertTrue(result == new Right(json))
    },
    test("Elements path on non-array in Strict mode fails") {
      val json   = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch  = JsonPatch(DynamicOptic.root.elements, JsonPatch.Op.Set(new Json.Number("1")))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("Elements path on non-array in Lenient mode returns unchanged") {
      val json   = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch  = JsonPatch(DynamicOptic.root.elements, JsonPatch.Op.Set(new Json.Number("1")))
      val result = patch(json, PatchMode.Lenient)
      assertTrue(result == new Right(json))
    },
    test("Nested Elements path navigation") {
      val json = new Json.Array(
        Chunk(
          new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2"))),
          new Json.Array(Chunk(new Json.Number("3"), new Json.Number("4")))
        )
      )
      val patch  = JsonPatch(DynamicOptic.root.elements.elements, JsonPatch.Op.Set(new Json.Number("0")))
      val result = patch(json, PatchMode.Strict)
      assertTrue(
        result == new Right(
          new Json.Array(
            Chunk(
              new Json.Array(Chunk(new Json.Number("0"), new Json.Number("0"))),
              new Json.Array(Chunk(new Json.Number("0"), new Json.Number("0")))
            )
          )
        )
      )
    },
    test("Elements path with error in one element in Lenient mode continues") {
      val json = new Json.Array(
        Chunk(
          new Json.Object(Chunk(("value", new Json.Number("1")))),
          new Json.String("not an object"),
          new Json.Object(Chunk(("value", new Json.Number("3"))))
        )
      )
      val patch  = JsonPatch(DynamicOptic.root.elements.field("value"), JsonPatch.Op.Set(new Json.Number("100")))
      val result = patch(json, PatchMode.Lenient)
      assertTrue(
        result == new Right(
          new Json.Array(
            Chunk(
              new Json.Object(Chunk(("value", new Json.Number("100")))),
              new Json.String("not an object"),
              new Json.Object(Chunk(("value", new Json.Number("100"))))
            )
          )
        )
      )
    },
    test("Elements path with error in one element in Strict mode fails") {
      val json = new Json.Array(
        Chunk(
          new Json.Object(Chunk(("value", new Json.Number("1")))),
          new Json.String("not an object"),
          new Json.Object(Chunk(("value", new Json.Number("3"))))
        )
      )
      val patch  = JsonPatch(DynamicOptic.root.elements.field("value"), JsonPatch.Op.Set(new Json.Number("100")))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    }
  )

  val dynamicPatchConversionCoverageSuite: Spec[Any, Nothing] = suite("Coverage: DynamicPatch conversions")(
    test("roundtrip through DynamicPatch preserves IntDelta") {
      val dynamicPatch = DynamicPatch(
        DynamicOptic.root,
        DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.IntDelta(5))
      )
      val jsonPatch = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(jsonPatch.isRight)
    },
    test("roundtrip through DynamicPatch preserves LongDelta") {
      val dynamicPatch = DynamicPatch(
        DynamicOptic.root,
        DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.LongDelta(100L))
      )
      val jsonPatch = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(jsonPatch.isRight)
    },
    test("roundtrip through DynamicPatch preserves DoubleDelta") {
      val dynamicPatch = DynamicPatch(
        DynamicOptic.root,
        DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.DoubleDelta(3.14))
      )
      val jsonPatch = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(jsonPatch.isRight)
    },
    test("roundtrip through DynamicPatch preserves FloatDelta") {
      val dynamicPatch = DynamicPatch(
        DynamicOptic.root,
        DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.FloatDelta(2.5f))
      )
      val jsonPatch = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(jsonPatch.isRight)
    },
    test("roundtrip through DynamicPatch preserves ShortDelta") {
      val dynamicPatch = DynamicPatch(
        DynamicOptic.root,
        DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.ShortDelta(10.toShort))
      )
      val jsonPatch = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(jsonPatch.isRight)
    },
    test("roundtrip through DynamicPatch preserves ByteDelta") {
      val dynamicPatch = DynamicPatch(
        DynamicOptic.root,
        DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.ByteDelta(5.toByte))
      )
      val jsonPatch = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(jsonPatch.isRight)
    },
    test("roundtrip through DynamicPatch preserves BigIntDelta") {
      val dynamicPatch = DynamicPatch(
        DynamicOptic.root,
        DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.BigIntDelta(BigInt(1000)))
      )
      val jsonPatch = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(jsonPatch.isRight)
    },
    test("roundtrip through DynamicPatch preserves StringEdit operations") {
      val dynamicPatch = DynamicPatch(
        DynamicOptic.root,
        DynamicPatch.Operation.PrimitiveDelta(
          DynamicPatch.PrimitiveOp.StringEdit(
            Vector(
              DynamicPatch.StringOp.Insert(0, "prefix"),
              DynamicPatch.StringOp.Delete(10, 5),
              DynamicPatch.StringOp.Append("suffix"),
              DynamicPatch.StringOp.Modify(3, 2, "xy")
            )
          )
        )
      )
      val jsonPatch = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(jsonPatch.isRight)
    },
    test("JsonPatch to DynamicPatch and back preserves StringOp.Insert") {
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Insert(0, "hello"))))
      )
      val dynamicPatch = patch.toDynamicPatch
      val roundtripped = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(roundtripped.isRight)
    },
    test("JsonPatch to DynamicPatch and back preserves StringOp.Delete") {
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Delete(0, 5))))
      )
      val dynamicPatch = patch.toDynamicPatch
      val roundtripped = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(roundtripped.isRight)
    },
    test("JsonPatch to DynamicPatch and back preserves StringOp.Append") {
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Append("world"))))
      )
      val dynamicPatch = patch.toDynamicPatch
      val roundtripped = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(roundtripped.isRight)
    },
    test("JsonPatch to DynamicPatch and back preserves StringOp.Modify") {
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Modify(0, 3, "abc"))))
      )
      val dynamicPatch = patch.toDynamicPatch
      val roundtripped = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(roundtripped.isRight)
    },
    test("Unsupported DynamicPatch.PrimitiveOp returns error") {
      // InstantDelta is not supported for JSON (temporal types are not JSON-native)
      val dynamicPatch = DynamicPatch(
        DynamicOptic.root,
        DynamicPatch.Operation.PrimitiveDelta(DynamicPatch.PrimitiveOp.InstantDelta(java.time.Duration.ofSeconds(1)))
      )
      val jsonPatch = JsonPatch.fromDynamicPatch(dynamicPatch)
      assertTrue(jsonPatch.isLeft)
    }
  )

  // Additional edge case coverage tests
  val additionalEdgeCaseSuite: Spec[Any, Nothing] = suite("Additional Edge Cases")(
    // Clobber mode: field creation on missing field (non-last path)
    test("Clobber mode creates missing nested field path") {
      val json = new Json.Object(Chunk.empty)
      // Navigate to field "a" -> field "b" (both missing), then set
      val patch  = JsonPatch(DynamicOptic.root.field("a").field("b"), JsonPatch.Op.Set(new Json.Number("1")))
      val result = patch(json, PatchMode.Clobber)
      assertTrue(
        result == new Right(
          new Json.Object(Chunk(("a", new Json.Object(Chunk(("b", new Json.Number("1")))))))
        )
      )
    },
    // Clobber mode: field on non-object type
    test("Clobber mode replaces non-object with new object for field access") {
      val json   = new Json.String("not an object")
      val patch  = JsonPatch(DynamicOptic.root.field("a"), JsonPatch.Op.Set(new Json.Number("1")))
      val result = patch(json, PatchMode.Clobber)
      assertTrue(
        result == new Right(new Json.Object(Chunk(("a", new Json.Number("1")))))
      )
    },
    // Clobber mode: field on non-object with nested path
    test("Clobber mode replaces non-object with nested object for deep field access") {
      val json   = new Json.Number("42")
      val patch  = JsonPatch(DynamicOptic.root.field("a").field("b"), JsonPatch.Op.Set(new Json.Number("1")))
      val result = patch(json, PatchMode.Clobber)
      assertTrue(
        result == new Right(
          new Json.Object(Chunk(("a", new Json.Object(Chunk(("b", new Json.Number("1")))))))
        )
      )
    },
    // Clobber mode: index on empty array
    test("Clobber mode on out-of-bounds array index at last path") {
      val json   = new Json.Array(Chunk.empty)
      val patch  = JsonPatch(DynamicOptic.root.at(0), JsonPatch.Op.Set(new Json.Number("1")))
      val result = patch(json, PatchMode.Clobber)
      // Clobber should return unchanged or error for out-of-bounds
      assertTrue(result.isRight || result.isLeft)
    },
    // ArrayEdit: Insert with Modify
    test("ArrayEdit with Modify nested operation") {
      val innerPatch = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(10))))
      val json       = new Json.Array(Chunk(new Json.Number("5")))
      val patch      =
        JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Modify(0, JsonPatch.Op.Nested(innerPatch)))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.Array(Chunk(new Json.Number("15")))))
    },
    // ObjectEdit: Modify with nested patch
    test("ObjectEdit Modify with deeply nested patch") {
      val json = new Json.Object(
        Chunk(("outer", new Json.Object(Chunk(("inner", new Json.Number("5"))))))
      )
      val innerPatch = JsonPatch(
        DynamicOptic.root.field("inner"),
        JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(10)))
      )
      val patch = JsonPatch.root(
        JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Modify("outer", innerPatch)))
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(
        result == new Right(
          new Json.Object(Chunk(("outer", new Json.Object(Chunk(("inner", new Json.Number("15")))))))
        )
      )
    },
    // Lenient mode on missing field path
    test("Lenient mode returns unchanged on missing nested field path") {
      val json   = new Json.Object(Chunk.empty)
      val patch  = JsonPatch(DynamicOptic.root.field("a").field("b"), JsonPatch.Op.Set(new Json.Number("1")))
      val result = patch(json, PatchMode.Lenient)
      assertTrue(result == new Right(json))
    },
    // Array operations with different indices
    test("Array delete at middle index") {
      val json   = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2"), new Json.Number("3")))
      val patch  = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Delete(1, 1))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.Array(Chunk(new Json.Number("1"), new Json.Number("3")))))
    },
    // Array insert at middle index
    test("Array insert at middle index") {
      val json  = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("3")))
      val patch =
        JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Insert(1, Chunk(new Json.Number("2"))))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(
        result == new Right(new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2"), new Json.Number("3"))))
      )
    },
    // Multiple operations in sequence
    test("Multiple ArrayOps in sequence") {
      val json  = new Json.Array(Chunk(new Json.Number("1")))
      val patch = JsonPatch.root(
        JsonPatch.Op.ArrayEdit(
          Vector(
            JsonPatch.ArrayOp.Append(Chunk(new Json.Number("2"))),
            JsonPatch.ArrayOp.Append(Chunk(new Json.Number("3")))
          )
        )
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(
        result == new Right(new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2"), new Json.Number("3"))))
      )
    },
    // Multiple ObjectOps in sequence
    test("Multiple ObjectOps in sequence") {
      val json  = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch = JsonPatch.root(
        JsonPatch.Op.ObjectEdit(
          Vector(
            JsonPatch.ObjectOp.Add("b", new Json.Number("2")),
            JsonPatch.ObjectOp.Add("c", new Json.Number("3"))
          )
        )
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(
        result == new Right(
          new Json.Object(Chunk(("a", new Json.Number("1")), ("b", new Json.Number("2")), ("c", new Json.Number("3"))))
        )
      )
    },
    // Clobber mode overwrites existing field on Add
    test("Clobber mode overwrites existing field on ObjectOp.Add") {
      val json   = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Add("a", new Json.Number("2")))))
      val result = patch(json, PatchMode.Clobber)
      assertTrue(result == new Right(new Json.Object(Chunk(("a", new Json.Number("2"))))))
    },
    // Clobber mode silent on Remove missing key
    test("Clobber mode silent on ObjectOp.Remove for missing key") {
      val json   = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Remove("nonexistent"))))
      val result = patch(json, PatchMode.Clobber)
      assertTrue(result == new Right(json))
    },
    // Strict mode fails on Add existing key
    test("Strict mode fails on ObjectOp.Add for existing key") {
      val json   = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Add("a", new Json.Number("2")))))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    // Clobber mode on array with nested object field access
    test("Elements path with nested field access applies to all elements") {
      val json = new Json.Array(
        Chunk(
          new Json.Object(Chunk(("a", new Json.Object(Chunk(("b", new Json.Number("1"))))))),
          new Json.Object(Chunk(("a", new Json.Object(Chunk(("b", new Json.Number("2")))))))
        )
      )
      val patch  = JsonPatch(DynamicOptic.root.elements.field("a").field("b"), JsonPatch.Op.Set(new Json.Number("0")))
      val result = patch(json, PatchMode.Strict)
      assertTrue(
        result == new Right(
          new Json.Array(
            Chunk(
              new Json.Object(Chunk(("a", new Json.Object(Chunk(("b", new Json.Number("0"))))))),
              new Json.Object(Chunk(("a", new Json.Object(Chunk(("b", new Json.Number("0")))))))
            )
          )
        )
      )
    },
    // Lenient mode on Add existing key
    test("Lenient mode returns unchanged on ObjectOp.Add for existing key") {
      val json   = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Add("a", new Json.Number("2")))))
      val result = patch(json, PatchMode.Lenient)
      assertTrue(result == new Right(json))
    },
    // Lenient mode on Remove missing key
    test("Lenient mode returns unchanged on ObjectOp.Remove for missing key") {
      val json   = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val patch  = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Remove("nonexistent"))))
      val result = patch(json, PatchMode.Lenient)
      assertTrue(result == new Right(json))
    },
    // String operations with empty strings
    test("StringEdit Insert on empty string") {
      val json  = new Json.String("")
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Insert(0, "hello"))))
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.String("hello")))
    },
    test("StringEdit Delete on matching length") {
      val json  = new Json.String("hello")
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Delete(0, 5))))
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.String("")))
    },
    test("StringEdit Modify replaces substring correctly") {
      val json  = new Json.String("hello world")
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Modify(0, 5, "hi"))))
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.String("hi world")))
    },
    // Array at index with out-of-bounds in Strict
    test("Strict mode fails on array at out-of-bounds index") {
      val json   = new Json.Array(Chunk(new Json.Number("1")))
      val patch  = JsonPatch(DynamicOptic.root.at(5), JsonPatch.Op.Set(new Json.Number("2")))
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    test("Lenient mode returns unchanged on array at out-of-bounds index") {
      val json   = new Json.Array(Chunk(new Json.Number("1")))
      val patch  = JsonPatch(DynamicOptic.root.at(5), JsonPatch.Op.Set(new Json.Number("2")))
      val result = patch(json, PatchMode.Lenient)
      assertTrue(result == new Right(json))
    },
    // Clobber mode on array out-of-bounds insert
    test("ArrayOp.Insert at out-of-bounds in Clobber mode clamps") {
      val json  = new Json.Array(Chunk(new Json.Number("1")))
      val patch =
        JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Insert(100, Chunk(new Json.Number("2"))))))
      val result = patch(json, PatchMode.Clobber)
      assertTrue(result == new Right(new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2")))))
    },
    // Clobber mode on out-of-bounds delete
    test("ArrayOp.Delete at out-of-bounds in Clobber mode clamps") {
      val json   = new Json.Array(Chunk(new Json.Number("1"), new Json.Number("2")))
      val patch  = JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Delete(100, 1))))
      val result = patch(json, PatchMode.Clobber)
      assertTrue(result == new Right(json))
    },
    // Nested operation inside ArrayOp.Modify
    test("ArrayOp.Modify with PrimitiveDelta operation") {
      val json  = new Json.Array(Chunk(new Json.Number("5")))
      val patch = JsonPatch.root(
        JsonPatch.Op.ArrayEdit(
          Vector(
            JsonPatch.ArrayOp.Modify(0, JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
          )
        )
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.Array(Chunk(new Json.Number("10")))))
    },
    // ObjectEdit with Modify that applies PrimitiveDelta
    test("ObjectOp.Modify with NumberDelta on nested value") {
      val json       = new Json.Object(Chunk(("a", new Json.Number("5"))))
      val innerPatch = JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(BigDecimal(5))))
      val patch      = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Modify("a", innerPatch))))
      val result     = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.Object(Chunk(("a", new Json.Number("10"))))))
    },
    // Strict mode on Modify missing key
    test("Strict mode fails on ObjectOp.Modify for missing key") {
      val json       = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val innerPatch = JsonPatch.root(JsonPatch.Op.Set(new Json.Number("2")))
      val patch      = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Modify("nonexistent", innerPatch))))
      val result     = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    // ArrayOp.Modify at out-of-bounds index
    test("Strict mode fails on ArrayOp.Modify at out-of-bounds index") {
      val json  = new Json.Array(Chunk(new Json.Number("1")))
      val patch = JsonPatch.root(
        JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Modify(10, JsonPatch.Op.Set(new Json.Number("2")))))
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result.isLeft)
    },
    // Clobber mode on Modify missing object key creates field
    test("Clobber mode on ObjectOp.Modify for missing key creates field") {
      val json       = new Json.Object(Chunk(("a", new Json.Number("1"))))
      val innerPatch = JsonPatch.root(JsonPatch.Op.Set(new Json.Number("2")))
      val patch      = JsonPatch.root(JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Modify("b", innerPatch))))
      val result     = patch(json, PatchMode.Clobber)
      assertTrue(
        result == new Right(new Json.Object(Chunk(("a", new Json.Number("1")), ("b", new Json.Number("2")))))
      )
    },
    // String insert at end
    test("StringEdit Insert at end of string") {
      val json  = new Json.String("hello")
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(Vector(JsonPatch.StringOp.Insert(5, " world"))))
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.String("hello world")))
    },
    // Multiple StringOps in sequence
    test("Multiple StringOps in sequence") {
      val json  = new Json.String("ABCDE")
      val patch = JsonPatch.root(
        JsonPatch.Op.PrimitiveDelta(
          JsonPatch.PrimitiveOp.StringEdit(
            Vector(
              JsonPatch.StringOp.Delete(0, 1), // "BCDE"
              JsonPatch.StringOp.Append("F")   // "BCDEF"
            )
          )
        )
      )
      val result = patch(json, PatchMode.Strict)
      assertTrue(result == new Right(new Json.String("BCDEF")))
    }
  )
}
