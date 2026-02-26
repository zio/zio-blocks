package jsonpatch

import zio.blocks.chunk.Chunk
import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch.*
import zio.blocks.schema.patch.{DynamicPatch, PatchMode}

/**
 * JsonPatch — Step 4: Composition, PatchMode, and DynamicPatch
 *
 * This step covers:
 *   - Composing patches with ++ (sequencing multiple changes)
 *   - PatchMode: Strict, Lenient, and Clobber failure-handling strategies
 *   - Converting to and from DynamicPatch for generic pipeline interop
 *
 * Run with: sbt "schema-examples/runMain jsonpatch.Step4CompositionAndModes"
 */
object Step4CompositionAndModes extends App {

  def printHeader(title: String): Unit = {
    println()
    println("=" * 60)
    println(title)
    println("=" * 60)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 1. Patch composition with ++
  //    ++ concatenates the ops of two patches. The combined patch applies
  //    `this` first, then `that`. JsonPatch.empty is the identity element.
  // ─────────────────────────────────────────────────────────────────────────

  printHeader("1. Composition with ++")

  val json = Json.Object(
    "name"  -> Json.String("Alice"),
    "score" -> Json.Number(100),
    "city"  -> Json.String("NYC")
  )

  // Three focused patches — each targets one field
  val renamePatch = JsonPatch(DynamicOptic.root.field("name"), Op.Set(Json.String("Bob")))
  val scorePatch  = JsonPatch(DynamicOptic.root.field("score"),
    Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(50))))
  val cityPatch   = JsonPatch(DynamicOptic.root.field("city"), Op.Set(Json.String("SF")))

  // Combine into a single patch that applies all three in sequence
  val combined = renamePatch ++ scorePatch ++ cityPatch
  println(s"Combined patch:\n$combined")
  println(s"Applied: ${combined.apply(json)}")

  // empty is the identity for ++
  println(s"\nempty ++ patch == patch: ${(JsonPatch.empty ++ scorePatch) == scorePatch}")
  println(s"patch ++ empty == patch: ${(scorePatch ++ JsonPatch.empty) == scorePatch}")

  // Diff-based composition: accumulate changes across multiple versions
  val v0 = Json.Object("count" -> Json.Number(0))
  val v1 = Json.Object("count" -> Json.Number(1))
  val v2 = Json.Object("count" -> Json.Number(2))

  val p01      = JsonPatch.diff(v0, v1)
  val p12      = JsonPatch.diff(v1, v2)
  val p02      = p01 ++ p12           // patch from v0 to v2 in one step
  println(s"\nv0 → v2 combined: ${p02.apply(v0)}")

  // ─────────────────────────────────────────────────────────────────────────
  // 2. PatchMode — controls failure handling when a precondition is not met
  //
  //    Strict  (default) — return Left(SchemaError) on the first failure
  //    Lenient           — skip failing operations, continue with the rest
  //    Clobber           — overwrite / force through conflicts
  // ─────────────────────────────────────────────────────────────────────────

  printHeader("2. PatchMode — Strict / Lenient / Clobber")

  val obj = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))

  // ObjectOp.Add fails when the key already exists
  val conflictPatch = JsonPatch.root(Op.ObjectEdit(Chunk(
    ObjectOp.Add("a", Json.Number(99)),   // "a" already exists → conflict
    ObjectOp.Add("c", Json.Number(3))     // "c" is new → would succeed
  )))

  val strict  = obj.patch(conflictPatch, PatchMode.Strict)
  val lenient = obj.patch(conflictPatch, PatchMode.Lenient)
  val clobber = obj.patch(conflictPatch, PatchMode.Clobber)

  println(s"Strict  (fails on conflict)   : $strict")
  println(s"Lenient (skips conflict)      : $lenient")
  println(s"Clobber (overwrites conflict) : $clobber")

  // Lenient is useful when applying a patch that may not perfectly match the
  // current state — e.g., an optimistic update where some ops may be stale
  val arrJson = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
  val outOfBoundsPatch = JsonPatch.root(Op.ArrayEdit(Chunk(
    ArrayOp.Modify(10, Op.Set(Json.Number(0)))  // index 10 doesn't exist
  )))
  println(s"\nOut-of-bounds Strict : ${arrJson.patch(outOfBoundsPatch, PatchMode.Strict)}")
  println(s"Out-of-bounds Lenient: ${arrJson.patch(outOfBoundsPatch, PatchMode.Lenient)}")

  // ─────────────────────────────────────────────────────────────────────────
  // 3. Converting to DynamicPatch
  //    toDynamicPatch always succeeds. NumberDelta widens to BigDecimalDelta.
  //    Use this to pass a JsonPatch into generic patching infrastructure or
  //    to serialize using DynamicPatch's schema.
  // ─────────────────────────────────────────────────────────────────────────

  printHeader("3. JsonPatch → DynamicPatch")

  val numPatch: JsonPatch    = JsonPatch.diff(Json.Number(10), Json.Number(15))
  val dyn:      DynamicPatch = numPatch.toDynamicPatch
  println(s"JsonPatch:    $numPatch")
  println(s"DynamicPatch: $dyn")

  // ─────────────────────────────────────────────────────────────────────────
  // 4. Converting from DynamicPatch
  //    fromDynamicPatch fails for temporal deltas (no time type in JSON) and
  //    non-string map keys (JSON keys must be strings).
  //    All numeric delta variants widen to NumberDelta(BigDecimal).
  // ─────────────────────────────────────────────────────────────────────────

  printHeader("4. DynamicPatch → JsonPatch")

  // Roundtrip: JsonPatch → DynamicPatch → JsonPatch
  val originalPatch: JsonPatch                  = JsonPatch.diff(Json.Number(1), Json.Number(5))
  val dynPatch:      DynamicPatch               = originalPatch.toDynamicPatch
  val recovered:     Either[_, JsonPatch]       = JsonPatch.fromDynamicPatch(dynPatch)

  println(s"Original : $originalPatch")
  println(s"Recovered: $recovered")
  println(s"Roundtrip OK: ${recovered == Right(originalPatch)}")

  // fromDynamicPatch rejects temporal operations (JSON has no time type)
  import zio.blocks.schema.patch.DynamicPatch.{PrimitiveOp as DynPrimOp, Operation as DynOp, DynamicPatchOp}
  import zio.blocks.schema.DynamicOptic as DOp

  val temporalOp  = new DynamicPatch(Chunk(new DynamicPatchOp(
    DOp.root,
    new DynOp.PrimitiveDelta(new DynPrimOp.InstantDelta(java.time.Duration.ofSeconds(60)))
  )))
  val temporalResult = JsonPatch.fromDynamicPatch(temporalOp)
  println(s"\nTemporal op rejected: ${temporalResult.isLeft}")
  println(s"Error: ${temporalResult.left.map(_.message)}")
}
