package zio.blocks.schema.json.patch

import zio.blocks.chunk.Chunk
import zio.blocks.schema.json.Json
import zio.test.Gen

/**
 * Generators for [[Json]] values used in property-based tests.
 *
 * Design decisions:
 *   - Max depth of 3 to keep tests fast while catching most issues
 *   - Max 10 elements per array/object for bounded test runtime
 *   - Alphanumeric strings for object keys to avoid JSON encoding issues
 *   - Numbers cover integers, decimals, negatives, positives, and zero
 */
object JsonGen {

  // ─────────────────────────────────────────────────────────────────────────
  // Primitive Generators
  // ─────────────────────────────────────────────────────────────────────────

  /** Always generates `Json.Null`. */
  val genNull: Gen[Any, Json] = Gen.const(Json.Null)

  /** Generates `Json.Boolean(true)` or `Json.Boolean(false)`. */
  val genBoolean: Gen[Any, Json] = Gen.boolean.map(Json.Boolean(_))

  /** Generates `Json.Number` with integers, decimals, negative, positive, and zero. */
  val genNumber: Gen[Any, Json] = Gen.oneOf(
    Gen.const(Json.Number("0")),
    Gen.const(Json.Number("-0")),
    Gen.int.map(i => Json.Number(i.toString)),
    Gen.long.map(l => Json.Number(l.toString)),
    Gen.double.filter(d => !d.isNaN && !d.isInfinite).map(d => Json.Number(d.toString)),
    Gen.bigDecimal(BigDecimal(-1000000), BigDecimal(1000000)).map(bd => Json.Number(bd.toString))
  )

  /** Generates `Json.String` with empty, short, long, and alphanumeric content. */
  val genString: Gen[Any, Json] = Gen.oneOf(
    Gen.const(Json.String("")),
    Gen.alphaNumericStringBounded(1, 5).map(Json.String(_)),
    Gen.alphaNumericStringBounded(5, 20).map(Json.String(_)),
    Gen.alphaNumericStringBounded(20, 50).map(Json.String(_))
  )

  /** Generates any primitive Json value (null, boolean, number, string). */
  val genPrimitive: Gen[Any, Json] = Gen.oneOf(genNull, genBoolean, genNumber, genString)

  // ─────────────────────────────────────────────────────────────────────────
  // Composite Generators (with depth control)
  // ─────────────────────────────────────────────────────────────────────────

  private val maxDepth: Int    = 3
  private val maxElements: Int = 10

  /**
   * Generates `Json.Array` with 0 to maxElements elements.
   * At depth 0, only primitives are generated.
   */
  private def genArrayWithDepth(depth: Int): Gen[Any, Json] =
    Gen.listOfBounded(0, maxElements)(genJsonWithDepth(depth - 1)).map { elems =>
      Json.Array(Chunk.from(elems))
    }

  /**
   * Generates `Json.Object` with 0 to maxElements fields and unique keys.
   * At depth 0, only primitives are generated for values.
   */
  private def genObjectWithDepth(depth: Int): Gen[Any, Json] =
    Gen
      .listOfBounded(0, maxElements) {
        for {
          key   <- Gen.alphaNumericStringBounded(1, 10)
          value <- genJsonWithDepth(depth - 1)
        } yield (key, value)
      }
      .map { fields =>
        // Deduplicate keys (keep first occurrence)
        val uniqueFields = fields.distinctBy(_._1)
        Json.Object(Chunk.from(uniqueFields))
      }

  /**
   * Generates any Json value recursively, bounded by depth.
   * At depth 0, only primitives are generated.
   */
  private def genJsonWithDepth(depth: Int): Gen[Any, Json] =
    if (depth <= 0) genPrimitive
    else
      Gen.oneOf(
        genPrimitive,
        genArrayWithDepth(depth),
        genObjectWithDepth(depth)
      )

  // ─────────────────────────────────────────────────────────────────────────
  // Top-level Exports
  // ─────────────────────────────────────────────────────────────────────────

  /** Default generator for any Json value (max depth 3, max 10 elements). */
  val genJson: Gen[Any, Json] = genJsonWithDepth(maxDepth)

  /** Generates two independent Json values for diff testing. */
  val genJsonPair: Gen[Any, (Json, Json)] = genJson.zip(genJson)

  /** Generates three independent Json values for associativity testing. */
  val genJsonTriple: Gen[Any, (Json, Json, Json)] =
    for {
      a <- genJson
      b <- genJson
      c <- genJson
    } yield (a, b, c)

  /** Generates a JsonPatch by diffing two random Json values. */
  def genPatch: Gen[Any, zio.blocks.schema.json.JsonPatch] =
    genJsonPair.map { case (a, b) => zio.blocks.schema.json.JsonPatch.diff(a, b) }
}
