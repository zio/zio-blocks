package jsonpatch

import zio.blocks.chunk.Chunk
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch.*

/**
 * JsonPatch — Step 3: Patch Operations
 *
 * A tour of every Op type and its sub-operations: Set, PrimitiveDelta
 * (NumberDelta and StringEdit), ArrayEdit (Insert/Append/Delete/Modify), and
 * ObjectEdit (Add/Remove/Modify).
 *
 * Run with: sbt "schema-examples/runMain jsonpatch.Step3PatchOperations"
 */
object Step3PatchOperations extends App {

  def printHeader(title: String): Unit = {
    println()
    println("=" * 60)
    println(title)
    println("=" * 60)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 1. Op.Set — replaces the target value entirely.
  //    Works on any Json type regardless of what is currently there.
  // ─────────────────────────────────────────────────────────────────────────

  printHeader("1. Op.Set — replace value entirely")

  val setString  = JsonPatch.root(Op.Set(Json.String("replaced")))
  val setNull    = JsonPatch.root(Op.Set(Json.Null))
  val setObject  = JsonPatch.root(Op.Set(Json.Object("reset" -> Json.Boolean(true))))

  println(s"Number → String : ${setString.apply(Json.Number(123))}")
  println(s"Object → Null   : ${setNull.apply(Json.Object("a" -> Json.Number(1)))}")
  println(s"Array  → Object : ${setObject.apply(Json.Array(Json.Number(1)))}")

  // ─────────────────────────────────────────────────────────────────────────
  // 2. PrimitiveOp.NumberDelta — adds a BigDecimal delta to a Json.Number.
  //    Use negative values to subtract. Fails on non-Number targets.
  // ─────────────────────────────────────────────────────────────────────────

  printHeader("2. PrimitiveOp.NumberDelta — increment/decrement")

  val inc = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(5))))
  val dec = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(-3))))
  val big = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal("0.001"))))

  println(s"10 + 5   = ${inc.apply(Json.Number(10))}")
  println(s"10 - 3   = ${dec.apply(Json.Number(10))}")
  println(s"1 + 0.001 = ${big.apply(Json.Number(1))}")

  // ─────────────────────────────────────────────────────────────────────────
  // 3. PrimitiveOp.StringEdit — applies character-level edits to a string.
  //    Four StringOp variants: Insert, Delete, Append, Modify.
  //    JsonPatch.diff generates these automatically; you can also build them
  //    by hand for precise control.
  // ─────────────────────────────────────────────────────────────────────────

  printHeader("3. PrimitiveOp.StringEdit — character-level string edits")

  val base = Json.String("Hello world")

  // Insert "beautiful " before "world" (index 6)
  val insertPatch = JsonPatch.root(
    Op.PrimitiveDelta(PrimitiveOp.StringEdit(Chunk(StringOp.Insert(6, "beautiful "))))
  )
  println(s"Insert : ${insertPatch.apply(base)}")

  // Delete "Hello " (index 0, length 6)
  val deletePatch = JsonPatch.root(
    Op.PrimitiveDelta(PrimitiveOp.StringEdit(Chunk(StringOp.Delete(0, 6))))
  )
  println(s"Delete : ${deletePatch.apply(base)}")

  // Append "!"
  val appendPatch = JsonPatch.root(
    Op.PrimitiveDelta(PrimitiveOp.StringEdit(Chunk(StringOp.Append("!"))))
  )
  println(s"Append : ${appendPatch.apply(base)}")

  // Modify "world" → "ZIO Blocks" (index 6, length 5)
  val modifyPatch = JsonPatch.root(
    Op.PrimitiveDelta(PrimitiveOp.StringEdit(Chunk(StringOp.Modify(6, 5, "ZIO Blocks"))))
  )
  println(s"Modify : ${modifyPatch.apply(base)}")

  // Multiple StringOps in a single StringEdit (applied in sequence)
  val multiPatch = JsonPatch.root(
    Op.PrimitiveDelta(PrimitiveOp.StringEdit(Chunk(
      StringOp.Delete(0, 6),    // remove "Hello "
      StringOp.Append("!")      // append "!"
    )))
  )
  println(s"Multi  : ${multiPatch.apply(base)}")

  // ─────────────────────────────────────────────────────────────────────────
  // 4. Op.ArrayEdit — insert, append, delete, and modify array elements.
  //    Operations are applied left-to-right; indices refer to the array
  //    state after all preceding ops in the same ArrayEdit.
  // ─────────────────────────────────────────────────────────────────────────

  printHeader("4. Op.ArrayEdit — insert / append / delete / modify")

  val arr = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))

  // Insert at index 0 (prepend)
  val insertArr = JsonPatch.root(Op.ArrayEdit(Chunk(ArrayOp.Insert(0, Chunk(Json.Number(0))))))
  println(s"Insert 0 at [0]: ${insertArr.apply(arr)}")

  // Append to the end
  val appendArr = JsonPatch.root(Op.ArrayEdit(Chunk(ArrayOp.Append(Chunk(Json.Number(4), Json.Number(5))))))
  println(s"Append [4,5]   : ${appendArr.apply(arr)}")

  // Delete 1 element at index 1
  val deleteArr = JsonPatch.root(Op.ArrayEdit(Chunk(ArrayOp.Delete(1, 1))))
  println(s"Delete [1]     : ${deleteArr.apply(arr)}")

  // Modify element at index 2 (replace with Op.Set)
  val modifyArr = JsonPatch.root(Op.ArrayEdit(Chunk(ArrayOp.Modify(2, Op.Set(Json.Number(99))))))
  println(s"Modify [2]→99  : ${modifyArr.apply(arr)}")

  // Combine multiple ArrayOps in one edit: [1,2,3] → [0,1,2,4]
  val combinedArr = JsonPatch.root(Op.ArrayEdit(Chunk(
    ArrayOp.Insert(0, Chunk(Json.Number(0))),  // [0,1,2,3]
    ArrayOp.Delete(3, 1),                      // [0,1,2]  (index 3 = original element 3)
    ArrayOp.Append(Chunk(Json.Number(4)))       // [0,1,2,4]
  )))
  println(s"Combined       : ${combinedArr.apply(arr)}")

  // ─────────────────────────────────────────────────────────────────────────
  // 5. Op.ObjectEdit — add, remove, and modify object fields.
  //    Add fails in Strict mode if the key already exists (see Step 4 for
  //    PatchMode options).
  // ─────────────────────────────────────────────────────────────────────────

  printHeader("5. Op.ObjectEdit — add / remove / modify fields")

  val obj = Json.Object(
    "name"  -> Json.String("Alice"),
    "age"   -> Json.Number(25),
    "city"  -> Json.String("NYC")
  )

  // Add a new field
  val addOp = JsonPatch.root(Op.ObjectEdit(Chunk(ObjectOp.Add("email", Json.String("alice@example.com")))))
  println(s"Add email  : ${addOp.apply(obj)}")

  // Remove a field
  val removeOp = JsonPatch.root(Op.ObjectEdit(Chunk(ObjectOp.Remove("city"))))
  println(s"Remove city: ${removeOp.apply(obj)}")

  // Modify a field with a sub-patch (increment age)
  val modifyOp = JsonPatch.root(Op.ObjectEdit(Chunk(
    ObjectOp.Modify("age", JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(1)))))
  )))
  println(s"Increment age: ${modifyOp.apply(obj)}")

  // Multiple ObjectOps in a single edit
  val multiObj = JsonPatch.root(Op.ObjectEdit(Chunk(
    ObjectOp.Remove("city"),
    ObjectOp.Add("country", Json.String("USA")),
    ObjectOp.Modify("name", JsonPatch.root(Op.Set(Json.String("Bob"))))
  )))
  println(s"Multi-field edit: ${multiObj.apply(obj)}")
}
