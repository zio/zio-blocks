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
import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch.*
import util.ShowExpr.show

object JsonPatchManualBuildExample extends App {

  // ── 1. JsonPatch.root — operation at the root ─────────────────────────────

  // Op.Set replaces whatever value is at the root
  show(JsonPatch.root(Op.Set(Json.Number(99))).apply(Json.String("anything")))

  // NumberDelta increments the root number by 5
  show(JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(5)))).apply(Json.Number(10)))

  // ObjectEdit.Add inserts a new field into the root object
  show(
    JsonPatch
      .root(Op.ObjectEdit(Chunk(ObjectOp.Add("active", Json.Boolean(true)))))
      .apply(Json.Object("name" -> Json.String("Alice")))
  )

  // ── 2. JsonPatch(path, op) — operation at a path ──────────────────────────

  val json = Json.Object(
    "user" -> Json.Object(
      "name" -> Json.String("Alice"),
      "age"  -> Json.Number(25)
    )
  )

  val agePath  = DynamicOptic.root.field("user").field("age")
  val birthday = JsonPatch(agePath, Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(1))))

  // a path-based patch targets a nested field by optic
  show(birthday)
  // incrementing age at user.age
  show(birthday.apply(json))

  val namePath = DynamicOptic.root.field("user").field("name")
  val rename   = JsonPatch(namePath, Op.Set(Json.String("Bob")))

  // Op.Set at a nested path replaces only that field
  show(rename.apply(json))

  val scoreJson = Json.Object("scores" -> Json.Array(Json.Number(80), Json.Number(90), Json.Number(70)))
  val scorePath = DynamicOptic.root.field("scores").at(1)
  val fixScore  = JsonPatch(scorePath, Op.Set(Json.Number(95)))

  // addressing an array element by index via .at(1)
  show(fixScore.apply(scoreJson))

  // ── 3. JsonPatch.empty — identity patch ───────────────────────────────────

  // isEmpty returns true for the empty patch
  show(JsonPatch.empty.isEmpty)

  // applying the empty patch leaves the value unchanged
  show(JsonPatch.empty.apply(Json.Number(42)))

  // ── 4. Conditional composition with empty as the neutral element ───────────

  val value    = Json.Object("x" -> Json.Number(5))
  val maybeNeg =
    if (true) JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(-1)))) else JsonPatch.empty
  val maybeAddY   = if (false) JsonPatch(DynamicOptic.root.field("y"), Op.Set(Json.Number(0))) else JsonPatch.empty
  val conditional = maybeNeg ++ maybeAddY

  // only the active branch (decrement x) is applied; the false branch is a no-op
  show(conditional.apply(value))
}
