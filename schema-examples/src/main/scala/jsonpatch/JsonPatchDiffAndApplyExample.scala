package jsonpatch

import zio.blocks.schema.json.{Json, JsonPatch}
import util.ShowExpr.show

object JsonPatchDiffAndApplyExample extends App {

  // ── 1. Number diff ───────────────────────────────────────────────────────

  val n1     = Json.Number(10)
  val n2     = Json.Number(15)
  val nPatch = JsonPatch.diff(n1, n2)

  // diff between two numbers produces a NumberDelta patch
  show(nPatch)
  // applying the patch transforms the source into the target
  show(nPatch.apply(n1))

  // ── 2. String diff ───────────────────────────────────────────────────────

  val s1     = Json.String("hello world")
  val s2     = Json.String("hello ZIO Blocks")
  val sPatch = JsonPatch.diff(s1, s2)

  // diff between two strings produces a character-level StringEdit patch
  show(sPatch)
  // applying the patch reconstructs the target string
  show(sPatch.apply(s1))

  // ── 3. Object diff ───────────────────────────────────────────────────────

  val obj1 = Json.Object(
    "name" -> Json.String("Alice"),
    "age"  -> Json.Number(25),
    "city" -> Json.String("NYC")
  )
  val obj2 = Json.Object(
    "name" -> Json.String("Alice"),
    "age"  -> Json.Number(26),
    "city" -> Json.String("NYC")
  )
  val objPatch = JsonPatch.diff(obj1, obj2)

  // only the changed field (age: 25 → 26) appears in the patch
  show(objPatch)
  // applying the patch increments the age field
  show(objPatch.apply(obj1))

  // ── 4. Array diff ────────────────────────────────────────────────────────

  val arr1     = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
  val arr2     = Json.Array(Json.Number(1), Json.Number(3), Json.Number(4))
  val arrPatch = JsonPatch.diff(arr1, arr2)

  // the patch records the minimal edits needed to transform arr1 into arr2
  show(arrPatch)
  // result matches arr2 exactly
  show(arrPatch.apply(arr1))

  // ── 5. Extension methods: Json#diff and Json#patch ────────────────────────

  val doc1     = Json.Object("version" -> Json.Number(1), "status" -> Json.String("draft"))
  val doc2     = Json.Object("version" -> Json.Number(2), "status" -> Json.String("published"))
  val extPatch = doc1.diff(doc2)

  // Json#diff is an extension method equivalent to JsonPatch.diff
  show(extPatch)
  // Json#patch applies the patch using the extension method
  show(doc1.patch(extPatch))

  // ── 6. Roundtrip guarantee ───────────────────────────────────────────────

  val complex1 = Json.Object(
    "user" -> Json.Object(
      "name"   -> Json.String("Bob"),
      "scores" -> Json.Array(Json.Number(90), Json.Number(85), Json.Number(92))
    ),
    "active" -> Json.Boolean(true)
  )
  val complex2 = Json.Object(
    "user" -> Json.Object(
      "name"   -> Json.String("Bob"),
      "scores" -> Json.Array(Json.Number(90), Json.Number(95), Json.Number(92))
    ),
    "active" -> Json.Boolean(false)
  )
  val roundtripPatch = JsonPatch.diff(complex1, complex2)

  // applying a diff patch always reconstructs the exact target value
  show(roundtripPatch.apply(complex1) == Right(complex2))
}
