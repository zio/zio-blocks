package jsonpatch

import zio.blocks.schema.json.{Json, JsonPatch}

/**
 * JsonPatch — Step 1: Diff and Apply
 *
 * The most common JsonPatch workflow: compute the minimal patch between two
 * Json values with JsonPatch.diff, then apply it with JsonPatch#apply.
 *
 * Run with: sbt "schema-examples/runMain jsonpatch.Step1DiffAndApply"
 */
object Step1DiffAndApply extends App {

  def printHeader(title: String): Unit = {
    println()
    println("=" * 60)
    println(title)
    println("=" * 60)
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 1. Number diff — emits a NumberDelta (stores the difference, not the
  //    full new value, making it compact for incremental counters).
  // ─────────────────────────────────────────────────────────────────────────

  printHeader("1. Number diff")

  val n1    = Json.Number(10)
  val n2    = Json.Number(15)
  val nPatch = JsonPatch.diff(n1, n2)

  println(s"Source : $n1")
  println(s"Target : $n2")
  println(s"Patch  :\n$nPatch")
  println(s"Applied: ${nPatch.apply(n1)}")

  // ─────────────────────────────────────────────────────────────────────────
  // 2. String diff — uses an LCS (Longest Common Subsequence) algorithm.
  //    Emits a StringEdit when it is smaller than a plain Set; falls back
  //    to Set when the entire string changes.
  // ─────────────────────────────────────────────────────────────────────────

  printHeader("2. String diff")

  val s1    = Json.String("hello world")
  val s2    = Json.String("hello ZIO Blocks")
  val sPatch = JsonPatch.diff(s1, s2)

  println(s"Source : $s1")
  println(s"Target : $s2")
  println(s"Patch  :\n$sPatch")
  println(s"Applied: ${sPatch.apply(s1)}")

  // ─────────────────────────────────────────────────────────────────────────
  // 3. Object diff — produces an ObjectEdit that touches only the changed
  //    fields, leaving untouched fields alone.
  // ─────────────────────────────────────────────────────────────────────────

  printHeader("3. Object diff")

  val obj1 = Json.Object(
    "name"  -> Json.String("Alice"),
    "age"   -> Json.Number(25),
    "city"  -> Json.String("NYC")
  )
  val obj2 = Json.Object(
    "name"  -> Json.String("Alice"),
    "age"   -> Json.Number(26),
    "city"  -> Json.String("NYC")
  )
  val objPatch = JsonPatch.diff(obj1, obj2)

  println(s"Source : $obj1")
  println(s"Target : $obj2")
  println(s"Patch  :\n$objPatch")   // Only touches "age", skips "name" and "city"
  println(s"Applied: ${objPatch.apply(obj1)}")

  // ─────────────────────────────────────────────────────────────────────────
  // 4. Array diff — uses LCS-based alignment to emit minimal Insert/Delete
  //    ops rather than replacing the whole array.
  // ─────────────────────────────────────────────────────────────────────────

  printHeader("4. Array diff")

  val arr1 = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
  val arr2 = Json.Array(Json.Number(1), Json.Number(3), Json.Number(4))
  val arrPatch = JsonPatch.diff(arr1, arr2)

  println(s"Source : $arr1")
  println(s"Target : $arr2")
  println(s"Patch  :\n$arrPatch")
  println(s"Applied: ${arrPatch.apply(arr1)}")

  // ─────────────────────────────────────────────────────────────────────────
  // 5. Extension method: Json#diff and Json#patch
  //    diff and apply are also available as extension methods on Json.
  // ─────────────────────────────────────────────────────────────────────────

  printHeader("5. Extension methods: Json#diff and Json#patch")

  val doc1 = Json.Object("version" -> Json.Number(1), "status" -> Json.String("draft"))
  val doc2 = Json.Object("version" -> Json.Number(2), "status" -> Json.String("published"))

  // Same as JsonPatch.diff(doc1, doc2)
  val extPatch = doc1.diff(doc2)
  println(s"Patch  :\n$extPatch")

  // Same as extPatch.apply(doc1)
  val result = doc1.patch(extPatch)
  println(s"Applied: $result")

  // ─────────────────────────────────────────────────────────────────────────
  // 6. Roundtrip guarantee
  //    For any source and target, diff(source, target).apply(source) == Right(target).
  // ─────────────────────────────────────────────────────────────────────────

  printHeader("6. Roundtrip guarantee")

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
  val roundtripResult = roundtripPatch.apply(complex1)
  println(s"Roundtrip OK: ${roundtripResult == Right(complex2)}")
}
