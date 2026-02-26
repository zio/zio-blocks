package jsonpatch

import zio.blocks.chunk.Chunk
import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch.*
import zio.blocks.schema.patch.PatchMode

/**
 * JsonPatch — Complete Example: Collaborative Document Editing
 *
 * A realistic end-to-end scenario: a collaborative editor where multiple
 * users make changes to a shared JSON document. Each change is recorded as
 * a JsonPatch, forming an append-only log that can be:
 *   - Applied to reconstruct any historical version of the document
 *   - Serialized and sent over the network to sync remote clients
 *   - Composed to create a single patch representing multiple changes
 *
 * Domain: a simple article document with title, content, tags, and metadata.
 *
 * Run with: sbt "schema-examples/runMain jsonpatch.CompleteJsonPatchExample"
 */
object CompleteJsonPatchExample extends App {

  // ═══════════════════════════════════════════════════════════════════════
  // Domain helpers
  // ═══════════════════════════════════════════════════════════════════════

  def printHeader(title: String): Unit = {
    println()
    println("═" * 60)
    println(s"  $title")
    println("═" * 60)
  }

  def printSection(title: String): Unit = {
    println()
    println(s"── $title ──")
  }

  /** Apply a patch, printing a summary, and return the new state. */
  def applyChange(doc: Json, patch: JsonPatch, description: String): Json = {
    println(s"  [$description]")
    println(s"    Patch ops: ${patch.ops.length}")
    patch.apply(doc) match {
      case Right(updated) => updated
      case Left(err)      =>
        println(s"    ERROR: ${err.message}")
        doc
    }
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Initial document
  // ═══════════════════════════════════════════════════════════════════════

  val initialDoc = Json.Object(
    "id"      -> Json.Number(1001),
    "title"   -> Json.String("Introduction to ZIO Blocks"),
    "content" -> Json.String("ZIO Blocks is a library for building type-safe applications."),
    "tags"    -> Json.Array(Json.String("scala"), Json.String("zio")),
    "meta"    -> Json.Object(
      "author"   -> Json.String("alice"),
      "version"  -> Json.Number(1),
      "draft"    -> Json.Boolean(true)
    )
  )

  // ═══════════════════════════════════════════════════════════════════════
  // 1. Tracking changes as an append-only patch log
  // ═══════════════════════════════════════════════════════════════════════

  printHeader("1. Patch log — tracking every change")

  var doc = initialDoc
  var log = List.empty[(String, JsonPatch)]   // (author, patch)

  // Helper: record a change made by an author
  def commit(author: String, patch: JsonPatch): Unit = {
    log = log :+ (author -> patch)
    doc = patch.apply(doc).getOrElse(doc)
    println(s"  [$author committed ${patch.ops.length} op(s)]")
  }

  printSection("Alice publishes the article")

  // Alice marks the article as no longer a draft and bumps the version
  val publishPatch =
    JsonPatch(DynamicOptic.root.field("meta").field("draft"),   Op.Set(Json.Boolean(false))) ++
    JsonPatch(DynamicOptic.root.field("meta").field("version"), Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(1))))

  commit("alice", publishPatch)
  println(s"  meta.draft   = ${doc.get("meta").get("draft").one}")
  println(s"  meta.version = ${doc.get("meta").get("version").one}")

  printSection("Bob adds a tag and refines the title")

  val editPatch =
    JsonPatch.root(Op.ObjectEdit(Chunk(
      ObjectOp.Modify("tags", JsonPatch.root(Op.ArrayEdit(Chunk(
        ArrayOp.Append(Chunk(Json.String("functional"), Json.String("typesafe")))
      )))),
      ObjectOp.Modify("title", JsonPatch.root(Op.Set(Json.String("Introduction to ZIO Blocks Schema"))))
    )))

  commit("bob", editPatch)
  println(s"  title = ${doc.get("title").one}")
  println(s"  tags  = ${doc.get("tags").one}")

  printSection("Carol fixes a typo in the content")

  val contentSource = doc.get("content").one.get.asInstanceOf[Json.String].value
  val fixedContent  = contentSource.replace("type-safe", "type-safe, modular")
  val fixPatch      = JsonPatch(
    DynamicOptic.root.field("content"),
    Op.Set(Json.String(fixedContent))
  )

  commit("carol", fixPatch)
  println(s"  content = ${doc.get("content").one}")

  // ═══════════════════════════════════════════════════════════════════════
  // 2. Replaying the log to reconstruct historical versions
  // ═══════════════════════════════════════════════════════════════════════

  printHeader("2. Replay — reconstruct any historical version")

  // Replay from the beginning to get the current state
  val replayed = log.foldLeft(initialDoc) { case (state, (author, patch)) =>
    patch.apply(state).getOrElse(state)
  }
  println(s"  Replayed == current: ${replayed == doc}")

  // Replay only the first commit to inspect state after Alice's change
  val afterAlice = log.take(1).foldLeft(initialDoc) { case (state, (_, patch)) =>
    patch.apply(state).getOrElse(state)
  }
  println(s"  After Alice — draft: ${afterAlice.get("meta").get("draft").one}")
  println(s"  After Alice — tags : ${afterAlice.get("tags").one}")

  // ═══════════════════════════════════════════════════════════════════════
  // 3. Composing all patches into a single aggregate patch
  //    Useful for batch sync: send one patch instead of the whole log.
  // ═══════════════════════════════════════════════════════════════════════

  printHeader("3. Aggregate patch — compose entire log")

  val aggregate = log.map(_._2).foldLeft(JsonPatch.empty)(_ ++ _)
  println(s"  Log size    : ${log.length} patches")
  println(s"  Aggregate ops: ${aggregate.ops.length}")

  val fromAggregate = aggregate.apply(initialDoc)
  println(s"  Applied == current: ${fromAggregate == Right(doc)}")

  // ═══════════════════════════════════════════════════════════════════════
  // 4. PatchMode in practice — optimistic updates
  //    A stale patch (computed against an older version) may conflict with
  //    the current state. Lenient mode lets us apply what we can.
  // ═══════════════════════════════════════════════════════════════════════

  printHeader("4. PatchMode — handling stale patches")

  // Dave computed a patch against the initial doc, not knowing Bob already
  // renamed the title. His patch tries to Add a field that now exists.
  val stalePatch = JsonPatch.root(Op.ObjectEdit(Chunk(
    ObjectOp.Add("summary", Json.String("A great library")),  // new — OK
    ObjectOp.Add("tags", Json.Array())                        // "tags" already exists — conflict!
  )))

  val strictResult  = doc.patch(stalePatch, PatchMode.Strict)
  val lenientResult = doc.patch(stalePatch, PatchMode.Lenient)
  val clobberResult = doc.patch(stalePatch, PatchMode.Clobber)

  println(s"  Strict  (fails on conflict)       : ${strictResult.isLeft}")
  println(s"  Lenient (skips 'tags', adds 'summary'): ${lenientResult.map(_.get("summary").one)}")
  println(s"  Clobber (overwrites 'tags')       : ${clobberResult.map(_.get("tags").one)}")

  // ═══════════════════════════════════════════════════════════════════════
  // 5. Serialization — JsonPatch carries its own Schema
  //    Round-trip via DynamicPatch to show that patches are data.
  // ═══════════════════════════════════════════════════════════════════════

  printHeader("5. Serialization — patches are first-class values")

  // Convert to DynamicPatch (the serialization-ready representation)
  val dynAggregate = aggregate.toDynamicPatch
  println(s"  DynamicPatch ops: ${dynAggregate.ops.length}")

  // Convert back to JsonPatch
  val recovered = JsonPatch.fromDynamicPatch(dynAggregate)
  println(s"  Roundtrip OK: ${recovered.map(_.apply(initialDoc)) == Right(Right(doc))}")
}
