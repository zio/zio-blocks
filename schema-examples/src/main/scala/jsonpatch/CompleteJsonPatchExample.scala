package jsonpatch

import zio.blocks.chunk.Chunk
import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch.*
import zio.blocks.schema.patch.PatchMode

/**
 * JsonPatch — Complete Example: Collaborative Document Editing
 *
 * A realistic end-to-end scenario: a collaborative editor where multiple users
 * make changes to a shared JSON document. Each change is recorded as a
 * JsonPatch, forming an append-only log that can be:
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
  // Helpers
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

  // Navigate a Json value as an object.
  // Safe here because every commit always produces a Json.Object — we only
  // ever patch the top-level article document, never replace its type.
  def asObj(json: Json): Json.Object = json.asInstanceOf[Json.Object]

  // ═══════════════════════════════════════════════════════════════════════
  // Initial document
  //
  // The article starts as a draft. The `meta` sub-object tracks authorship,
  // version, and publication status independently of the article content so
  // that patches to metadata are narrow and don't touch the body.
  // ═══════════════════════════════════════════════════════════════════════

  val initialDoc = Json.Object(
    "id"      -> Json.Number(1001),
    "title"   -> Json.String("Introduction to ZIO Blocks"),
    "content" -> Json.String("ZIO Blocks is a library for building type-safe applications."),
    "tags"    -> Json.Array(Json.String("scala"), Json.String("zio")),
    "meta"    -> Json.Object(
      "author"  -> Json.String("alice"),
      "version" -> Json.Number(1),
      "draft"   -> Json.Boolean(true)
    )
  )

  // ═══════════════════════════════════════════════════════════════════════
  // 1. Tracking changes as an append-only patch log
  //
  // Rather than storing the full document after every edit, we store only
  // the JsonPatch that describes what changed. The current state is obtained
  // by replaying the log from `initialDoc`. This is analogous to event
  // sourcing: the log is the source of truth.
  // ═══════════════════════════════════════════════════════════════════════

  printHeader("1. Patch log — tracking every change")

  // `doc` is typed as Json (not Json.Object) because JsonPatch#apply returns
  // Either[SchemaError, Json]. The right-hand side of getOrElse is Json, so
  // Scala would widen the variable to Json if we left the type implicit.
  var doc: Json = initialDoc

  // The log is a sequence of (author, patch) pairs — one per commit.
  // In a real system this would be stored in a database or event stream.
  var log = List.empty[(String, JsonPatch)]

  // Record a change: append it to the log and advance the in-memory state.
  // Using PatchMode.Strict (the default) means any structural mismatch —
  // e.g., applying a NumberDelta to a String field — surfaces immediately
  // rather than silently producing a wrong result.
  def commit(author: String, patch: JsonPatch): Unit = {
    log = log :+ (author -> patch)
    doc = patch.apply(doc) match {
      case Right(updated) =>
        updated
      case Left(error) =>
        System.err.println(s"Failed to apply patch in Strict mode for author '$author': $error")
        throw new RuntimeException(s"JsonPatch application failed in Strict mode: $error")
    }
    println(s"  [$author committed ${patch.ops.length} op(s)]")
  }

  // ── Alice publishes the article ──────────────────────────────────────
  //
  // Two independent field updates are composed into one patch with `++`.
  // Each JsonPatch targets a specific leaf via DynamicOptic path chaining:
  //   DynamicOptic.root.field("meta").field("draft")   → meta.draft
  //   DynamicOptic.root.field("meta").field("version") → meta.version
  //
  // Op.Set replaces the draft flag outright.
  // Op.PrimitiveDelta / NumberDelta increments the version counter by 1
  // rather than replacing it with a literal value — this is more robust
  // if two editors increment the version concurrently.

  printSection("Alice publishes the article")

  val publishPatch =
    // Flip draft → false at the path meta.draft
    JsonPatch(DynamicOptic.root.field("meta").field("draft"), Op.Set(Json.Boolean(false))) ++
      // Increment meta.version by 1 (NumberDelta stores the delta, not the new value)
      JsonPatch(
        DynamicOptic.root.field("meta").field("version"),
        Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(1)))
      )

  commit("alice", publishPatch)
  println(s"  meta.draft   = ${asObj(doc).get("meta").get("draft").one}")
  println(s"  meta.version = ${asObj(doc).get("meta").get("version").one}")

  // ── Bob adds tags and refines the title ──────────────────────────────
  //
  // This patch uses Op.ObjectEdit at the root to modify two fields in one
  // pass. Each ObjectOp.Modify wraps a nested JsonPatch that is applied
  // recursively to the field's current value:
  //   - The "tags" field holds a Json.Array, so its patch uses Op.ArrayEdit
  //     with ArrayOp.Append to add elements without touching existing ones.
  //   - The "title" field is replaced entirely with Op.Set.

  printSection("Bob adds a tag and refines the title")

  val editPatch =
    JsonPatch.root(
      Op.ObjectEdit(
        Chunk(
          // Append two new tags to the existing array; Insert/Append never
          // remove existing elements, making this safe to apply in any order.
          ObjectOp.Modify(
            "tags",
            JsonPatch.root(
              Op.ArrayEdit(
                Chunk(
                  ArrayOp.Append(Chunk(Json.String("functional"), Json.String("typesafe")))
                )
              )
            )
          ),
          // Replace the title with a more specific string
          ObjectOp.Modify("title", JsonPatch.root(Op.Set(Json.String("Introduction to ZIO Blocks Schema"))))
        )
      )
    )

  commit("bob", editPatch)
  println(s"  title = ${asObj(doc).get("title").one}")
  println(s"  tags  = ${asObj(doc).get("tags").one}")

  // ── Carol fixes a typo in the content ────────────────────────────────
  //
  // We extract the current content string, compute the corrected version
  // in Scala, and wrap the replacement in a targeted Op.Set patch.
  // An alternative would be to use JsonPatch.diff on the two Json.String
  // values — diff would emit a compact StringEdit (LCS-based) instead of
  // a full Set when the strings share a long common prefix/suffix.

  printSection("Carol fixes a typo in the content")

  // Extract the current content string from the live document so the patch
  // always works relative to what is actually there, not a stale snapshot.
  val contentSource = asObj(doc).get("content").one match {
    case Right(s: Json.String) => s.value
    case _                     => ""
  }
  // Produce the corrected string in plain Scala, then wrap it in a patch
  val fixedContent = contentSource.replace("type-safe", "type-safe, modular")
  val fixPatch     = JsonPatch(
    DynamicOptic.root.field("content"),
    Op.Set(Json.String(fixedContent))
  )

  commit("carol", fixPatch)
  println(s"  content = ${asObj(doc).get("content").one}")

  // ═══════════════════════════════════════════════════════════════════════
  // 2. Replaying the log to reconstruct historical versions
  //
  // Because the log is append-only and every patch is a pure value, we can
  // reconstruct any historical snapshot by replaying a prefix of the log
  // starting from `initialDoc`. No extra storage is needed — the full
  // history is implicit in the sequence of patches.
  // ═══════════════════════════════════════════════════════════════════════

  printHeader("2. Replay — reconstruct any historical version")

  // Full replay: apply every patch in order to arrive at the current state.
  // This proves the log is consistent with `doc`.
  val replayed = log.foldLeft(initialDoc: Json) { case (state, (_, patch)) =>
    patch.apply(state).fold(err => throw new RuntimeException(s"Replay failed: $err"), identity)
  }
  println(s"  Replayed == current: ${replayed == doc}")

  // Partial replay: apply only the first commit (Alice's) to inspect the
  // intermediate state without modifying `doc` or `log`.
  val afterAlice = log.take(1).foldLeft(initialDoc: Json) { case (state, (_, patch)) =>
    patch.apply(state).fold(err => throw new RuntimeException(s"Replay failed: $err"), identity)
  }
  // Alice only touched meta — tags should still be the original two
  println(s"  After Alice — draft: ${asObj(afterAlice).get("meta").get("draft").one}")
  println(s"  After Alice — tags : ${asObj(afterAlice).get("tags").one}")

  // ═══════════════════════════════════════════════════════════════════════
  // 3. Composing all patches into a single aggregate patch
  //
  // `++` concatenates the ops of two patches, so folding the entire log
  // with `++` produces one patch whose ops list is the union of all
  // individual ops. Applying this aggregate to `initialDoc` produces the
  // same result as replaying the full log — useful for batch sync where
  // you want to send one message instead of N.
  // ═══════════════════════════════════════════════════════════════════════

  printHeader("3. Aggregate patch — compose entire log")

  // JsonPatch.empty is the identity element for ++ (like 0 for addition),
  // so foldLeft with it as the starting value is the idiomatic fold.
  val aggregate = log.map(_._2).foldLeft(JsonPatch.empty)(_ ++ _)
  println(s"  Log size     : ${log.length} patches")
  println(s"  Aggregate ops: ${aggregate.ops.length}") // sum of all individual op counts

  // Verify: applying the aggregate from scratch equals the current state
  val fromAggregate = aggregate.apply(initialDoc)
  println(s"  Applied == current: ${fromAggregate == Right(doc)}")

  // ═══════════════════════════════════════════════════════════════════════
  // 4. PatchMode in practice — handling stale patches
  //
  // In a concurrent system a client may compute a patch against a stale
  // snapshot. When that patch is applied to the current (newer) document
  // some operations may conflict. PatchMode controls the failure strategy:
  //
  //   Strict  — fail fast on the first conflict (default, safest)
  //   Lenient — skip conflicting ops, apply the rest
  //   Clobber — force all ops through, overwriting on conflict
  // ═══════════════════════════════════════════════════════════════════════

  printHeader("4. PatchMode — handling stale patches")

  // Dave computed a patch against the initial document before Bob's commit.
  // He tries to Add "tags" — but "tags" already exists in the current doc.
  // He also adds "summary", which is genuinely new and should succeed.
  val stalePatch = JsonPatch.root(
    Op.ObjectEdit(
      Chunk(
        ObjectOp.Add("summary", Json.String("A great library")), // new field — OK in all modes
        ObjectOp.Add("tags", Json.Array())                       // already exists — conflict!
      )
    )
  )

  // Strict: the entire patch fails because "tags" already exists.
  // No partial application — either all ops succeed or none do.
  val strictResult = doc.patch(stalePatch, PatchMode.Strict)

  // Lenient: the conflicting Add("tags") is silently skipped;
  // Add("summary") still goes through. Good for best-effort merges.
  val lenientResult = doc.patch(stalePatch, PatchMode.Lenient)

  // Clobber: Add("tags") overwrites the existing field with an empty array.
  // Use only when last-write-wins semantics are acceptable.
  val clobberResult = doc.patch(stalePatch, PatchMode.Clobber)

  println(s"  Strict  (fails on conflict)            : ${strictResult.isLeft}")
  println(s"  Lenient (skips 'tags', adds 'summary') : ${lenientResult.map(asObj(_).get("summary").one)}")
  println(s"  Clobber (overwrites 'tags')            : ${clobberResult.map(asObj(_).get("tags").one)}")

  // ═══════════════════════════════════════════════════════════════════════
  // 5. Serialization — patches are first-class values
  //
  // JsonPatch ships with Schema instances for every nested type, so it can
  // be encoded and decoded using any ZIO Blocks codec (JSON, Avro,
  // MessagePack, …). Here we demonstrate the round-trip through
  // DynamicPatch, the codec-agnostic intermediate representation.
  //
  // In production you would typically do:
  //   val codec = Schema[JsonPatch].derive(JsonFormat)
  //   val bytes = codec.encode(aggregate)   // serialize to JSON bytes
  //   val back  = codec.decode(bytes)       // deserialize back to JsonPatch
  // ═══════════════════════════════════════════════════════════════════════

  printHeader("5. Serialization — patches are first-class values")

  // toDynamicPatch converts every op to its generic DynamicPatch equivalent.
  // NumberDelta widens to BigDecimalDelta because JSON has a single number
  // type; all other ops map 1-to-1.
  val dynAggregate = aggregate.toDynamicPatch
  println(s"  DynamicPatch ops: ${dynAggregate.ops.length}")

  // fromDynamicPatch converts back. It can fail only for ops that have no
  // JSON representation (temporal deltas, non-string map keys) — neither of
  // which appears in our article patches.
  val recovered = JsonPatch.fromDynamicPatch(dynAggregate)
  println(s"  Roundtrip OK: ${recovered.map(_.apply(initialDoc)) == Right(Right(doc))}")
}
