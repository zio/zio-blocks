---
id: diff-and-patch
title: "How to Compute, Store and Replay Changes with Diff and Patch"
description: "Diff two versions of a value, store the patch as JSON, apply it with the right PatchMode, replay an edit history, and send JSON patches."
keywords:
  - "Change Tracking"
  - "Audit Log"
  - "Undo and Redo"
  - "Schema#diff"
  - "Patch"
  - "JsonPatch"
---

In this guide, we track the changes made to a book catalog: who lowered the price of *Zionomicon*, from what to what, and what the catalog looked like before each edit. Instead of storing a full copy of every version, we compute a *patch*, a small value that describes only what changed, with `Schema#diff`. By the end, you will have an edit log that stores each change as JSON, replays it from a base version, undoes the last edit, and sends changes to other systems as JSON patches.

We take an incremental approach:

- Diff two versions of a `Book` and read the patch
- Turn a patch into audit-log lines
- Apply a patch, and compose two patches into one
- Store a patch as JSON text and load it back
- Choose a `PatchMode` for when the data changed after the patch was made
- Keep an edit history, replay it and undo the last edit
- Exchange JSON patches with other systems, including the standard RFC 6902 format

## The Problem

Many programs need to know *what changed* between two versions of their data, not just the new version. An audit log answers "who changed this price, and from what?". A client that syncs with a server wants to send only the fields that changed. An editor needs undo, and a collaboration feature stores a stream of small edits and replays them.

Without help, we compare the two values by hand, one field at a time. Every new field needs another line, lists and maps need their own comparison code, and nothing turns the result back into an update we can apply later.

For example, an audit function for a `Book` often looks like this:

```scala mdoc:compile-only
final case class Book(id: String, title: String, price: BigDecimal, tags: List[String], edition: Int)

def changes(before: Book, after: Book): List[String] = {
  val title   = if (before.title != after.title) List(s"title: ${before.title} -> ${after.title}") else Nil
  val price   = if (before.price != after.price) List(s"price: ${before.price} -> ${after.price}") else Nil
  val added   = after.tags.filterNot(before.tags.contains).map(t => s"tag added: $t")
  val removed = before.tags.filterNot(after.tags.contains).map(t => s"tag removed: $t")
  val edition = if (before.edition != after.edition) List(s"edition: ${before.edition} -> ${after.edition}") else Nil
  title ++ price ++ added ++ removed ++ edition
}
```

The function forgets new fields silently, describes changes only as text, and gives us nothing to *apply*: to replay or undo the edit, we would write a second function that reads these strings back.

In this guide, we let the schema do this work. `Schema#diff` compares any two values of a type that has a `Schema` and returns a `Patch`, which we can print, store, send and apply.

## Prerequisites

This guide builds on [Your First Schema](./your-first-schema.md): you should know how to derive a `Schema` for a case class. Add the schema module to your `build.sbt` (see [How to Add ZIO Blocks to Your Build](./add-zio-blocks-to-your-build.md) for the full setup):

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "@VERSION@"

// Scala 3.9 and newer
libraryDependencies += "dev.zio" %% "zio-blocks-next-schema" % "@VERSION@"
scalacOptions += "-experimental"
```

Everything in this guide works the same on Scala 2.13 and Scala 3. We use these imports throughout:

```scala mdoc:silent
import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.json._
import zio.blocks.schema.patch._
```

## The Book Model

Our catalog holds books. Each `Book` has an id, a title, a price, a list of tags and an edition number, which covers the field kinds a diff treats differently: strings, numbers and lists. The `Schema` is all `Schema#diff` needs:

```scala mdoc:silent
final case class Book(id: String, title: String, price: BigDecimal, tags: List[String], edition: Int)

object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

val zionomicon = Book("zionomicon", "Zionomicon", BigDecimal("44.99"), List("zio", "scala"), 1)
```

## Diff Two Versions of a Book

Suppose an editor publishes the second edition of *Zionomicon*: the price drops by five dollars, the book gets an `effects` tag and the edition goes up. We pass the old and the new version to `Schema#diff`:

```scala mdoc:silent
val secondEdition = zionomicon.copy(price = BigDecimal("39.99"), tags = List("zio", "scala", "effects"), edition = 2)

val pricePatch: Patch[Book] = Book.schema.diff(zionomicon, secondEdition)
```

A `Patch` prints as a readable list of operations, one per changed field:

```scala mdoc
println(pricePatch)
```

The patch lists only the three fields that changed. It does not store the new values: numbers change by a *delta* (`-= 5.00`, `+= 1`), and the list gets an append operation (`+ "effects"`). Unchanged fields such as `title` do not appear at all.

A text field changes by an edit at a position, not by replacing the whole string. Adding a subtitle inserts text at index 10:

```scala mdoc
println(Book.schema.diff(secondEdition, secondEdition.copy(title = "Zionomicon (2nd Edition)")))
```

When nothing changed, the diff is empty, which `Patch#isEmpty` reports:

```scala mdoc
Book.schema.diff(zionomicon, zionomicon).isEmpty
```

## Turn a Patch into Audit Lines

An audit log wants lines like "price: 44.99 -> 39.99", with the old and the new value. The patch knows *where* each change happened: `Patch#dynamicPatch` exposes the untyped `DynamicPatch`, whose operations (`DynamicPatch#ops`) each carry a `path`, a `DynamicOptic` that prints as `.price` for the price field, and an `operation`. We look up each path in the old and the new value, converted to a `DynamicValue` with `Schema#toDynamicValue`, and print both sides as JSON:

```scala mdoc:silent
def auditLines[A](schema: Schema[A], before: A, after: A, patch: Patch[A]): List[String] = {
  val old = schema.toDynamicValue(before)
  val now = schema.toDynamicValue(after)
  def show(value: DynamicValue, path: DynamicOptic): String =
    value.get(path).one.fold(_ => "(none)", v => Json.fromDynamicValue(v).print)
  patch.dynamicPatch.ops.toList.map(op => s"${op.path}: ${show(old, op.path)} -> ${show(now, op.path)}")
}
```

The function works for any type with a `Schema`, so new fields show up in the audit without changes to it:

```scala mdoc
auditLines(Book.schema, zionomicon, secondEdition, pricePatch).foreach(println)
```

## Apply a Patch

`Schema#patch` applies a patch to a value and returns `Either[SchemaError, A]`. Applying the patch to the version it was computed from gives back the new version, which is the basic law of diff and patch:

```scala mdoc
Book.schema.patch(zionomicon, pricePatch)

Book.schema.patch(zionomicon, pricePatch) == Right(secondEdition)
```

`Schema#patch` always uses `PatchMode.Strict`. To choose the mode, call `Patch#apply(value, mode)` instead; we look at the modes in [Choose a PatchMode When the Data Changed](#choose-a-patchmode-when-the-data-changed).

:::warning
`Patch#apply(value)`, with no mode, returns a plain `Book`, not an `Either`. It applies the patch in `PatchMode.Lenient`, which skips the operations that fail, and returns the original value if the result cannot be built. A failed patch then looks the same as a patch that did nothing. Prefer `Patch#apply(value, mode)` or `Schema#patch`, which report the error.
:::

## Compose Patches into One

Two patches compose with `Patch#++`: the result applies the left patch first, then the right one. Here, a second edit adds the subtitle:

```scala mdoc:silent
val withSubtitle  = secondEdition.copy(title = "Zionomicon (2nd Edition)")
val subtitlePatch = Book.schema.diff(secondEdition, withSubtitle)
```

Applying the composed patch to the first edition gives the book after both edits:

```scala mdoc
(pricePatch ++ subtitlePatch)(zionomicon, PatchMode.Strict)
```

## Store a Patch as JSON and Load It Back

To keep a patch in a database, write it to a file or send it over the network, we store its `DynamicPatch`. `DynamicPatch` has its own `Schema`, so it has a JSON codec like any other type:

```scala mdoc:silent
val patchCodec = Schema[DynamicPatch].jsonCodec

val storedText: String = patchCodec.encodeToString(pricePatch.dynamicPatch)
```

The JSON names each operation and its path:

```scala mdoc
println(storedText)
```

Decoding the text gives back an equal `DynamicPatch`:

```scala mdoc
patchCodec.decode(storedText).map(_ == pricePatch.dynamicPatch)
```

A decoded `DynamicPatch` is not a `Patch[Book]`, and `Patch` has no public way to wrap one again. Instead, we apply it to the book's `DynamicValue` and convert the result back with `Schema#fromDynamicValue`. A small helper does both steps:

```scala mdoc:silent
def applyStored[A](schema: Schema[A], value: A, patch: DynamicPatch, mode: PatchMode = PatchMode.Strict): Either[SchemaError, A] =
  patch(schema.toDynamicValue(value), mode).flatMap(schema.fromDynamicValue)
```

Loading the stored text and applying it to the first edition gives the second edition:

```scala mdoc
patchCodec.decode(storedText).flatMap(loaded => applyStored(Book.schema, zionomicon, loaded))
```

The JSON refers to fields by name, so a stored patch keeps working when we add fields to `Book`, as long as the fields it names still exist.

## Choose a PatchMode When the Data Changed

A stored or sent patch is often applied to a value that changed after the patch was made: two editors work on the same catalog, or a client syncs a change made while offline. `PatchMode` decides what happens when an operation no longer fits:

| Mode      | When an operation does not fit                                                                                     |
| --------- | ------------------------------------------------------------------------------------------------------------------ |
| `Strict`  | Stops and returns `Left` with the first error                                                                      |
| `Lenient` | Skips that operation and applies the rest                                                                          |
| `Clobber` | Like `Lenient`, but overwrites a map key that already exists and clamps list inserts and deletes to the list's end |

To see the modes at work, we keep the books in a `Catalog`, a map from book id to `Book`:

```scala mdoc:silent
final case class Catalog(books: Map[String, Book])

object Catalog {
  implicit val schema: Schema[Catalog] = Schema.derived
}

val programmingInScala = Book("programming-in-scala", "Programming in Scala", BigDecimal("59.99"), List("scala"), 5)
val fpInScala          = Book("fp-in-scala", "Functional Programming in Scala", BigDecimal("49.99"), List("scala", "fp"), 2)

val monday  = Catalog(Map(zionomicon.id -> zionomicon, programmingInScala.id -> programmingInScala))
val tuesday = Catalog(Map(zionomicon.id -> secondEdition, fpInScala.id -> fpInScala))

val catalogPatch = Catalog.schema.diff(monday, tuesday)
```

The patch modifies the *Zionomicon* entry, adds *Functional Programming in Scala* and removes *Programming in Scala*. The added book's fields print without indentation, a known bug in 0.0.56:

```scala mdoc
println(catalogPatch)
```

A helper applies the patch in all three modes, so we can compare them:

```scala mdoc:silent
def applyInEveryMode(target: Catalog): Unit =
  List(PatchMode.Strict, PatchMode.Lenient, PatchMode.Clobber).foreach { mode =>
    println(s"$mode: ${catalogPatch(target, mode).map(_.books.values.map(b => s"${b.id} ${b.price}").toList.sorted)}")
  }
```

Now suppose another editor added *Functional Programming in Scala* first, at a price of 45.00. `Strict` refuses the patch, `Lenient` keeps the other editor's book, and `Clobber` overwrites it with ours:

```scala mdoc
applyInEveryMode(monday.copy(books = monday.books + (fpInScala.id -> fpInScala.copy(price = BigDecimal("45.00")))))
```

If the other editor removed *Programming in Scala* already, `Strict` fails because the key is gone, and the other two modes skip the removal:

```scala mdoc
applyInEveryMode(monday.copy(books = monday.books - programmingInScala.id))
```

Use `Strict` when a conflict must reach a person or a retry loop, `Lenient` when the latest writer should not undo other people's additions, and `Clobber` when our patch should win.

## Strict Mode Does Not Detect Every Change

`Strict` only checks that each operation *fits* the value: a map key exists, an index is inside the list, an insert position is inside the string. It does not check that the value still holds what it held when the patch was made. Numeric deltas, string edits and list operations by index all apply to whatever value is there now.

For example, the *Zionomicon* price patch says "minus 5.00", not "set to 39.99". If someone set the price to 42.00 in the meantime, the patch still applies, and the result is 37.00:

```scala mdoc
pricePatch(zionomicon.copy(price = BigDecimal("42.00")), PatchMode.Strict)
```

Lists are riskier, because a diff removes list elements by index. This patch removes the `scala` tag, which sits at index 1:

```scala mdoc:silent
val tagged         = zionomicon.copy(tags = List("zio", "scala", "effects"))
val removeScalaTag = Book.schema.diff(tagged, tagged.copy(tags = List("zio", "effects")))
```

The patch stores only the position:

```scala mdoc
println(removeScalaTag)
```

:::warning
If someone already removed the `scala` tag, index 1 now holds `effects`, and `Strict` removes it without an error. The book loses a tag that nobody asked to remove:

```scala mdoc
removeScalaTag(tagged.copy(tags = List("zio", "effects")), PatchMode.Strict)
```
:::

When the result must be exact, check that the value is still the one the patch was computed from before applying it. If it changed, reject the edit or compute a fresh diff against the current value. Comparing the whole value works for any type; a version number stored next to the value does the same job with less data to send:

```scala mdoc:silent
def applyIfUnchanged(current: Book, base: Book, patch: Patch[Book]): Either[String, Book] =
  if (current != base) Left(s"${current.id} changed since this patch was made; diff again against the current version")
  else patch(current, PatchMode.Strict).left.map(_.message)
```

The same patch is now rejected for the changed book, and still applies to the original:

```scala mdoc
applyIfUnchanged(tagged.copy(tags = List("zio", "effects")), tagged, removeScalaTag)

applyIfUnchanged(tagged, tagged, removeScalaTag)
```

## Keep an Edit History, Replay It and Undo

An edit history is a list of patches plus whatever we want to know about each one. We record two patches per edit: the *forward* patch from the old to the new version, and the *backward* patch from the new to the old version, which undoes the edit. Both are `DynamicPatch` values, so the case class gets a `Schema` and a JSON codec like any other:

```scala mdoc:silent
final case class Edit(author: String, forward: DynamicPatch, backward: DynamicPatch)

object Edit {
  implicit val schema: Schema[Edit] = Schema.derived
}

def record(author: String, before: Book, after: Book): Edit =
  Edit(author, Book.schema.diff(before, after).dynamicPatch, Book.schema.diff(after, before).dynamicPatch)

val history: List[Edit] = List(
  record("alice", zionomicon, secondEdition),
  record("bob", secondEdition, withSubtitle)
)
```

The whole history is a `List[Edit]`, so one codec stores it as JSON text, in the same shape as the stored patch above:

```scala mdoc:silent
val historyCodec = Schema[List[Edit]].jsonCodec

val historyText: String = historyCodec.encodeToString(history)
```

Decoding the text gives back the same history:

```scala mdoc
historyCodec.decode(historyText) == Right(history)
```

Replaying the history is a fold over the edits, starting from the base version. Each step applies one forward patch with the `applyStored` helper, and the first failure stops the replay:

```scala mdoc:silent
def replay(base: Book, edits: List[Edit]): Either[SchemaError, Book] =
  edits.foldLeft[Either[SchemaError, Book]](Right(base))((book, edit) => book.flatMap(applyStored(Book.schema, _, edit.forward)))
```

Replaying every edit gives the latest version, and replaying a prefix gives any earlier version:

```scala mdoc
replay(zionomicon, history)

replay(zionomicon, history.take(1))
```

To undo the last edit, we apply its backward patch to the current version:

```scala mdoc
applyStored(Book.schema, withSubtitle, history.last.backward)
```

A history only replays from the base it was recorded against. Started from a different book, Bob's insert at index 10 no longer fits a three-letter title, and the replay stops with an error:

```scala mdoc
replay(zionomicon.copy(title = "ZIO"), history)
```

To store one patch instead of many, for example to squash old history, `DynamicPatch#++` composes the forward patches in order:

```scala mdoc
println(history.map(_.forward).foldLeft(DynamicPatch.empty)(_ ++ _))
```

## Exchange JSON Patches with Other Systems

When the data is JSON rather than a Scala type, for example a document from another service, `Json#diff` compares two `Json` values and returns a `JsonPatch`, and `Json#patch` applies one. `Json.from` turns a `Book` into `Json` through its schema:

```scala mdoc:silent
val mondayJson  = Json.from(zionomicon)
val tuesdayJson = Json.from(withSubtitle)

val jsonPatch: JsonPatch = mondayJson.diff(tuesdayJson)
```

The `JsonPatch` holds the same kinds of operations as a typed patch, nested by object key:

```scala mdoc
println(jsonPatch)

mondayJson.patch(jsonPatch).map(_.print)
```

A typed patch's `DynamicPatch` converts to a `JsonPatch` with `JsonPatch.fromDynamicPatch`, which returns `Either[SchemaError, JsonPatch]` because temporal deltas and non-string map keys have no JSON form. `JsonPatch` has a `Schema`, so another ZIO Blocks service can receive it with `JsonPatch.schema.jsonCodec`.

The ZIO Blocks `JsonPatch` is its own format, not the [RFC 6902](https://datatracker.ietf.org/doc/html/rfc6902) "JSON Patch" standard that HTTP `PATCH` APIs and many JSON libraries understand. RFC 6902 has no number deltas or string edits: it sends operations such as `add`, `remove` and `replace` (it also defines `move`, `copy` and `test`) with [JSON Pointer](https://datatracker.ietf.org/doc/html/rfc6901) paths such as `/tags/1`. The [JsonPatch reference](../reference/schema/built-in-codecs/json/json-patch.md) lists every operation and compares the two formats.

## Convert a JsonPatch to RFC 6902

To send a change to a system that expects RFC 6902, we convert the `JsonPatch` ourselves. The converter below is our own code, not a library API. It walks the operations in order and keeps the current JSON value next to them, applying each operation with `JsonPatch.root`, so that every index and every computed value refers to the document as it is at that point:

```scala mdoc:silent
object Rfc6902 {
  def fromJsonPatch(source: Json, patch: JsonPatch): Either[SchemaError, Json] =
    patchOps(source, "", patch).map(ops => Json.Array(Chunk.from(ops)))

  private def patchOps(value: Json, path: String, patch: JsonPatch): Either[SchemaError, Vector[Json]] =
    steps(value, patch.ops.toVector)(op => JsonPatch(Chunk.single(op))) { (current, op) =>
      if (op.path.nodes.nonEmpty) Left(SchemaError(s"only root paths are supported, got ${op.path}"))
      else opOps(current, path, op.operation)
    }

  private def opOps(value: Json, path: String, op: JsonPatch.Op): Either[SchemaError, Vector[Json]] = op match {
    case JsonPatch.Op.Set(v)            => Right(Vector(rfc("replace", path, Some(v))))
    case JsonPatch.Op.PrimitiveDelta(_) => JsonPatch.root(op)(value).map(v => Vector(rfc("replace", path, Some(v))))
    case JsonPatch.Op.Nested(p)         => patchOps(value, path, p)
    case JsonPatch.Op.ArrayEdit(edits)  =>
      steps(value, edits.toVector)(e => JsonPatch.root(JsonPatch.Op.ArrayEdit(Chunk.single(e)))) {
        case (_, JsonPatch.ArrayOp.Insert(i, vs)) =>
          Right(vs.toVector.zipWithIndex.map { case (v, k) => rfc("add", s"$path/${i + k}", Some(v)) })
        case (_, JsonPatch.ArrayOp.Append(vs))     => Right(vs.toVector.map(v => rfc("add", s"$path/-", Some(v))))
        case (_, JsonPatch.ArrayOp.Delete(i, n))   => Right(Vector.fill(n)(rfc("remove", s"$path/$i", None)))
        case (cur, JsonPatch.ArrayOp.Modify(i, o)) =>
          cur.elements.lift(i).toRight(SchemaError(s"no index $i at $path")).flatMap(opOps(_, s"$path/$i", o))
      }
    case JsonPatch.Op.ObjectEdit(edits) =>
      steps(value, edits.toVector)(e => JsonPatch.root(JsonPatch.Op.ObjectEdit(Chunk.single(e)))) {
        case (_, JsonPatch.ObjectOp.Add(k, v))      => Right(Vector(rfc("add", s"$path/${escape(k)}", Some(v))))
        case (_, JsonPatch.ObjectOp.Remove(k))      => Right(Vector(rfc("remove", s"$path/${escape(k)}", None)))
        case (cur, JsonPatch.ObjectOp.Modify(k, p)) =>
          cur.fields.collectFirst { case (`k`, v) => v }.toRight(SchemaError(s"no key $k at $path"))
            .flatMap(patchOps(_, s"$path/${escape(k)}", p))
      }
  }

  // Emits the RFC 6902 operations for each edit, then applies the edit, so later edits see the updated value.
  private def steps[E](value: Json, edits: Vector[E])(asPatch: E => JsonPatch)(
    emit: (Json, E) => Either[SchemaError, Vector[Json]]
  ): Either[SchemaError, Vector[Json]] =
    edits
      .foldLeft[Either[SchemaError, (Json, Vector[Json])]](Right((value, Vector.empty))) { (acc, e) =>
        acc.flatMap { case (current, out) =>
          for {
            ops  <- emit(current, e)
            next <- asPatch(e)(current)
          } yield (next, out ++ ops)
        }
      }
      .map(_._2)

  private def escape(key: String): String = key.replace("~", "~0").replace("/", "~1")

  private def rfc(op: String, path: String, value: Option[Json]): Json =
    Json.Object(Chunk.from(Vector("op" -> Json.String(op), "path" -> Json.String(path)) ++ value.map("value" -> _)))
}
```

Converting the *Zionomicon* patch gives standard RFC 6902 operations:

```scala mdoc
Rfc6902.fromJsonPatch(mondayJson, jsonPatch).map(_.print).foreach(println)
```

List edits become `add` and `remove` operations by index. Here, we remove two tags and append one:

```scala mdoc:silent
val retagged = Json.from(zionomicon.copy(tags = List("zio", "scala", "effects", "fibers")))
val trimmed  = Json.from(zionomicon.copy(tags = List("zio", "fibers", "streams")))
```

The result has two `remove` operations at index 1, because the first removal shifts the next tag into its place:

```scala mdoc
Rfc6902.fromJsonPatch(retagged, retagged.diff(trimmed)).map(_.print).foreach(println)
```

The conversion loses some detail, and it needs the source document. A number delta becomes a `replace` with the new number, which the converter computes from the source, and a string edit becomes a `replace` of the whole string. The result says "set the price to 39.99", not "lower it by 5.00". The converter accepts the patches that `Json#diff` and `JsonPatch.diff` produce, whose operations all sit at the root path, and returns `Left` for a hand-built patch with a deeper path. Going the other way, reading an RFC 6902 patch that another system sends us, is out of scope for this guide.

## Putting It Together

The complete example defines the catalog, records two edits, stores the history as JSON, replays and undoes it, shows `PatchMode` on a changed catalog, and prints the RFC 6902 form of a change:

```scala mdoc:embed:schema-examples/src/main/scala/diffandpatch/CompleteExample.scala
```

## Running the Examples

All examples in this guide have corresponding runnable Scala files in the `schema-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/schema-examples
```

The step examples share the model in `BookModel.scala`:

<details>
  <summary>schema-examples/src/main/scala/diffandpatch/BookModel.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/diffandpatch/BookModel.scala:show-line-numbers
```

</details>

Run them in order to follow the guide step by step.

### Diff and Audit

This example diffs the first and second edition of *Zionomicon*, prints the patch, and turns it into audit lines.

<details>
  <summary>schema-examples/src/main/scala/diffandpatch/DiffAndAuditExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/diffandpatch/DiffAndAuditExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain diffandpatch.DiffAndAuditExample"
```

### Apply and Compose Patches

This example applies a patch with `Schema#patch`, checks the round trip, and composes two patches with `Patch#++`.

<details>
  <summary>schema-examples/src/main/scala/diffandpatch/ApplyAndComposeExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/diffandpatch/ApplyAndComposeExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain diffandpatch.ApplyAndComposeExample"
```

### Store a Patch as JSON

This example encodes a `DynamicPatch` as JSON text, decodes it, and applies the loaded patch.

<details>
  <summary>schema-examples/src/main/scala/diffandpatch/StorePatchExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/diffandpatch/StorePatchExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain diffandpatch.StorePatchExample"
```

### Patch Modes

This example applies a catalog patch in every `PatchMode` to two changed catalogs, shows how `Strict` misses a changed price and a changed tag list, and rejects a stale patch with a base check.

<details>
  <summary>schema-examples/src/main/scala/diffandpatch/PatchModeExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/diffandpatch/PatchModeExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain diffandpatch.PatchModeExample"
```

### Edit History

This example records two edits, stores the history as JSON, replays it, undoes the last edit, and squashes the history into one patch.

<details>
  <summary>schema-examples/src/main/scala/diffandpatch/EditHistoryExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/diffandpatch/EditHistoryExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain diffandpatch.EditHistoryExample"
```

### JSON Patches and RFC 6902

This example diffs two `Json` documents, applies the `JsonPatch`, and converts three edits to RFC 6902, checking each result against the expected operations.

<details>
  <summary>schema-examples/src/main/scala/diffandpatch/JsonPatchExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/diffandpatch/JsonPatchExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain diffandpatch.JsonPatchExample"
```

### The Complete Example

The complete example runs every step in one program:

```bash
sbt "runMain diffandpatch.CompleteExample"
```

To compile every example at once without running them:

```bash
sbt compile
```

## Going Further

- [Patch](../reference/schema/patch.md) documents every patch operation, including building patches by hand with optics, such as `Patch.set` and `Patch.increment`.
- [JsonPatch](../reference/schema/built-in-codecs/json/json-patch.md) and [JsonDiffer](../reference/schema/built-in-codecs/json/json-differ.md) cover patches on raw JSON in detail.
- [DynamicValue](../reference/schema/dynamic-value.md) explains the schema-less representation that patches work on.
- [Navigate and Update Nested Data with Optics](./nested-data-with-optics.md) shows the optics that hand-built patches use.
- [Migrating from ZIO Schema to ZIO Blocks Schema](./zio-schema-migration.md) maps ZIO Schema's diff and patch API to this one.
