package jsonpatch

import zio.blocks.chunk.Chunk
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch.*
import util.ShowExpr.show

object JsonPatchOperationsExample extends App {

  // ── 1. Op.Set — replace value entirely ───────────────────────────────────

  // Op.Set replaces any Json value regardless of its current type
  show(JsonPatch.root(Op.Set(Json.String("replaced"))).apply(Json.Number(123)))

  // Set to Null regardless of the source type
  show(JsonPatch.root(Op.Set(Json.Null)).apply(Json.Object("a" -> Json.Number(1))))

  // Set to an Object, replacing an Array
  show(JsonPatch.root(Op.Set(Json.Object("reset" -> Json.Boolean(true)))).apply(Json.Array(Json.Number(1))))

  // ── 2. PrimitiveOp.NumberDelta — increment / decrement ───────────────────

  // add 5 to 10
  show(JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(5)))).apply(Json.Number(10)))

  // subtract 3 from 10
  show(JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(-3)))).apply(Json.Number(10)))

  // fractional delta: 1 + 0.001
  show(JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal("0.001")))).apply(Json.Number(1)))

  // ── 3. PrimitiveOp.StringEdit — character-level string edits ─────────────

  val base = Json.String("Hello world")

  // StringOp.Insert inserts text at the given character index
  show(
    JsonPatch
      .root(Op.PrimitiveDelta(PrimitiveOp.StringEdit(Chunk(StringOp.Insert(6, "beautiful ")))))
      .apply(base)
  )

  // StringOp.Delete removes a span of characters starting at the given index
  show(
    JsonPatch
      .root(Op.PrimitiveDelta(PrimitiveOp.StringEdit(Chunk(StringOp.Delete(0, 6)))))
      .apply(base)
  )

  // StringOp.Append adds text at the end of the string
  show(
    JsonPatch
      .root(Op.PrimitiveDelta(PrimitiveOp.StringEdit(Chunk(StringOp.Append("!")))))
      .apply(base)
  )

  // StringOp.Modify replaces a span of characters with new text
  show(
    JsonPatch
      .root(Op.PrimitiveDelta(PrimitiveOp.StringEdit(Chunk(StringOp.Modify(6, 5, "ZIO Blocks")))))
      .apply(base)
  )

  // multiple StringOps are applied in sequence within a single patch
  show(
    JsonPatch
      .root(Op.PrimitiveDelta(PrimitiveOp.StringEdit(Chunk(
        StringOp.Delete(0, 6),
        StringOp.Append("!")
      ))))
      .apply(base)
  )

  // ── 4. Op.ArrayEdit — insert / append / delete / modify ──────────────────

  val arr = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))

  // ArrayOp.Insert inserts elements at the given index, shifting the rest right
  show(JsonPatch.root(Op.ArrayEdit(Chunk(ArrayOp.Insert(0, Chunk(Json.Number(0)))))).apply(arr))

  // ArrayOp.Append adds elements at the end of the array
  show(JsonPatch.root(Op.ArrayEdit(Chunk(ArrayOp.Append(Chunk(Json.Number(4), Json.Number(5)))))).apply(arr))

  // ArrayOp.Delete removes `count` elements starting at the given index
  show(JsonPatch.root(Op.ArrayEdit(Chunk(ArrayOp.Delete(1, 1)))).apply(arr))

  // ArrayOp.Modify applies a sub-patch to the element at the given index
  show(JsonPatch.root(Op.ArrayEdit(Chunk(ArrayOp.Modify(2, Op.Set(Json.Number(99)))))).apply(arr))

  // multiple ArrayOps compose into a single atomic edit
  show(
    JsonPatch
      .root(Op.ArrayEdit(Chunk(
        ArrayOp.Insert(0, Chunk(Json.Number(0))),
        ArrayOp.Delete(3, 1),
        ArrayOp.Append(Chunk(Json.Number(4)))
      )))
      .apply(arr)
  )

  // ── 5. Op.ObjectEdit — add / remove / modify fields ──────────────────────

  val obj = Json.Object(
    "name" -> Json.String("Alice"),
    "age"  -> Json.Number(25),
    "city" -> Json.String("NYC")
  )

  // ObjectOp.Add inserts a new field into the object
  show(JsonPatch.root(Op.ObjectEdit(Chunk(ObjectOp.Add("email", Json.String("alice@example.com"))))).apply(obj))

  // ObjectOp.Remove deletes the named field from the object
  show(JsonPatch.root(Op.ObjectEdit(Chunk(ObjectOp.Remove("city")))).apply(obj))

  // ObjectOp.Modify applies a sub-patch to the value of the named field
  show(
    JsonPatch
      .root(Op.ObjectEdit(Chunk(
        ObjectOp.Modify("age", JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(1)))))
      )))
      .apply(obj)
  )

  // multiple ObjectOps compose into a single atomic edit
  show(
    JsonPatch
      .root(Op.ObjectEdit(Chunk(
        ObjectOp.Remove("city"),
        ObjectOp.Add("country", Json.String("USA")),
        ObjectOp.Modify("name", JsonPatch.root(Op.Set(Json.String("Bob"))))
      )))
      .apply(obj)
  )
}
