/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jsonpatch

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch.*
import zio.blocks.schema.patch.{DynamicPatch, PatchMode}
import util.ShowExpr.show

object JsonPatchCompositionExample extends App {

  // ── 1. Composition with ++ ────────────────────────────────────────────────

  val json = Json.Object(
    "name"  -> Json.String("Alice"),
    "score" -> Json.Number(100),
    "city"  -> Json.String("NYC")
  )

  val renamePatch = JsonPatch(DynamicOptic.root.field("name"), Op.Set(Json.String("Bob")))
  val scorePatch  =
    JsonPatch(DynamicOptic.root.field("score"), Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(50))))
  val cityPatch = JsonPatch(DynamicOptic.root.field("city"), Op.Set(Json.String("SF")))
  val combined  = renamePatch ++ scorePatch ++ cityPatch

  // ++ sequences patches left-to-right; all three fields are updated in one step
  show(combined)
  show(combined.apply(json))

  // empty is the identity element for ++: empty ++ patch == patch
  show((JsonPatch.empty ++ scorePatch) == scorePatch)

  // patch ++ empty == patch
  show((scorePatch ++ JsonPatch.empty) == scorePatch)

  val v0  = Json.Object("count" -> Json.Number(0))
  val v1  = Json.Object("count" -> Json.Number(1))
  val v2  = Json.Object("count" -> Json.Number(2))
  val p01 = JsonPatch.diff(v0, v1)
  val p12 = JsonPatch.diff(v1, v2)

  // composing sequential diffs yields a patch that jumps two steps at once
  show((p01 ++ p12).apply(v0))

  // ── 2. PatchMode — Strict / Lenient / Clobber ─────────────────────────────

  val obj = Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2))

  val conflictPatch = JsonPatch.root(
    Op.ObjectEdit(
      Chunk(
        ObjectOp.Add("a", Json.Number(99)),
        ObjectOp.Add("c", Json.Number(3))
      )
    )
  )

  // Strict fails when a field already exists and Add would overwrite it
  show(obj.patch(conflictPatch, PatchMode.Strict))

  // Lenient skips operations that conflict, leaving existing fields untouched
  show(obj.patch(conflictPatch, PatchMode.Lenient))

  // Clobber overwrites existing fields without error
  show(obj.patch(conflictPatch, PatchMode.Clobber))

  val arrJson          = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
  val outOfBoundsPatch = JsonPatch.root(Op.ArrayEdit(Chunk(ArrayOp.Modify(10, Op.Set(Json.Number(0))))))

  // Strict returns Left for an out-of-bounds array index
  show(arrJson.patch(outOfBoundsPatch, PatchMode.Strict))

  // Lenient silently ignores the out-of-bounds operation
  show(arrJson.patch(outOfBoundsPatch, PatchMode.Lenient))

  // ── 3. JsonPatch → DynamicPatch ───────────────────────────────────────────

  val numPatch: JsonPatch = JsonPatch.diff(Json.Number(10), Json.Number(15))
  val dyn: DynamicPatch   = numPatch.toDynamicPatch

  // toDynamicPatch converts a JsonPatch to the generic DynamicPatch representation
  show(numPatch)
  show(dyn)

  // ── 4. DynamicPatch → JsonPatch ───────────────────────────────────────────

  val originalPatch: JsonPatch                  = JsonPatch.diff(Json.Number(1), Json.Number(5))
  val dynPatch: DynamicPatch                    = originalPatch.toDynamicPatch
  val recovered: Either[SchemaError, JsonPatch] = JsonPatch.fromDynamicPatch(dynPatch)

  // fromDynamicPatch reconstructs the original JsonPatch from a DynamicPatch
  show(recovered)

  // roundtrip: toDynamicPatch then fromDynamicPatch recovers the original patch
  show(recovered == Right(originalPatch))

  import zio.blocks.schema.patch.DynamicPatch.{PrimitiveOp, Operation, DynamicPatchOp}
  import zio.blocks.schema.DynamicOptic

  val temporalOp = new DynamicPatch(
    Chunk(
      new DynamicPatchOp(
        DynamicOptic.root,
        new Operation.PrimitiveDelta(new PrimitiveOp.InstantDelta(java.time.Duration.ofSeconds(60)))
      )
    )
  )

  // non-Json primitive ops (e.g. InstantDelta) are rejected by fromDynamicPatch
  show(JsonPatch.fromDynamicPatch(temporalOp).isLeft)
  show(JsonPatch.fromDynamicPatch(temporalOp).left.map(_.message))
}
