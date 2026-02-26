package jsonpatch

import zio.blocks.chunk.Chunk
import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch.*

/**
 * JsonPatch — Step 2: Manual Patch Construction
 *
 * Instead of computing patches automatically via diff, we can build them by
 * hand using JsonPatch.root (operation at the root), JsonPatch.apply (operation
 * at a specific path), and JsonPatch.empty (the identity patch).
 *
 * Run with: sbt "schema-examples/runMain jsonpatch.Step2ManualPatches"
 */
object Step2ManualPatches extends App {

  def printHeader(title: String): Unit = {
    println()
    println("=" * 60)
    println(title)
    println("=" * 60)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 1. JsonPatch.root — single operation at the root of the value.
  //    The simplest way to build a patch when you want to transform the
  //    top-level Json value directly.
  // ─────────────────────────────────────────────────────────────────────────

  printHeader("1. JsonPatch.root — operation at the root")

  // Replace the root value entirely
  val replaceRoot = JsonPatch.root(Op.Set(Json.Number(99)))
  println(s"Set root to 99: ${replaceRoot.apply(Json.String("anything"))}")

  // Increment the root number
  val incrementRoot = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(5))))
  println(s"Increment 10 by 5: ${incrementRoot.apply(Json.Number(10))}")

  // Add a field to the root object
  val addField = JsonPatch.root(Op.ObjectEdit(Chunk(ObjectOp.Add("active", Json.Boolean(true)))))
  val withActive = addField.apply(Json.Object("name" -> Json.String("Alice")))
  println(s"Add 'active' field: $withActive")

  // ─────────────────────────────────────────────────────────────────────────
  // 2. JsonPatch.apply — operation at a specific DynamicOptic path.
  //    Use DynamicOptic.root.field(name) and .at(index) to navigate to
  //    nested locations before applying the operation.
  // ─────────────────────────────────────────────────────────────────────────

  printHeader("2. JsonPatch(path, op) — operation at a path")

  val json = Json.Object(
    "user" -> Json.Object(
      "name" -> Json.String("Alice"),
      "age"  -> Json.Number(25)
    )
  )

  // Navigate to root → "user" → "age" and increment by 1
  val agePath  = DynamicOptic.root.field("user").field("age")
  val birthday = JsonPatch(agePath, Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(1))))
  println(s"Birthday patch:\n$birthday")
  println(s"Applied: ${birthday.apply(json)}")

  // Navigate to root → "user" → "name" and replace
  val namePath   = DynamicOptic.root.field("user").field("name")
  val rename     = JsonPatch(namePath, Op.Set(Json.String("Bob")))
  println(s"\nRename patch:\n$rename")
  println(s"Applied: ${rename.apply(json)}")

  // Navigate to an array element: root → "scores" → [1]
  val scoreJson  = Json.Object("scores" -> Json.Array(Json.Number(80), Json.Number(90), Json.Number(70)))
  val scorePath  = DynamicOptic.root.field("scores").at(1)
  val fixScore   = JsonPatch(scorePath, Op.Set(Json.Number(95)))
  println(s"\nFix scores[1]:\n$fixScore")
  println(s"Applied: ${fixScore.apply(scoreJson)}")

  // ─────────────────────────────────────────────────────────────────────────
  // 3. JsonPatch.empty — the identity patch.
  //    Applying it returns the input value unchanged. Useful as a starting
  //    point when building patches conditionally.
  // ─────────────────────────────────────────────────────────────────────────

  printHeader("3. JsonPatch.empty — identity patch")

  val empty = JsonPatch.empty
  println(s"Is empty: ${empty.isEmpty}")
  println(s"Applied:  ${empty.apply(Json.Number(42))}")

  // Build a patch conditionally — start with empty and append if needed
  val value = Json.Object("x" -> Json.Number(5))
  val maybeNegative = if (true) JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(-1)))) else JsonPatch.empty
  val maybeAddY     = if (false) JsonPatch(DynamicOptic.root.field("y"), Op.Set(Json.Number(0))) else JsonPatch.empty
  val conditional   = maybeNegative ++ maybeAddY
  println(s"Conditional patch applied: ${conditional.apply(value)}")
}
