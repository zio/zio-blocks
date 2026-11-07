package diffandpatch

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.json._
import zio.blocks.schema.patch._

/**
 * Diff and Patch: Putting It Together, Complete Example
 *
 * Records two edits to Zionomicon in an edit log, prints the audit lines,
 * stores the log as JSON and loads it back, replays and undoes it, applies a
 * catalog patch to a catalog another editor changed, rejects a stale patch, and
 * prints the RFC 6902 form of a change.
 *
 * Run with: cd schema-examples && sbt "runMain diffandpatch.CompleteExample"
 */
object CompleteExample extends App {

  final case class Book(id: String, title: String, price: BigDecimal, tags: List[String], edition: Int)

  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  final case class Catalog(books: Map[String, Book])

  object Catalog {
    implicit val schema: Schema[Catalog] = Schema.derived
  }

  final case class Edit(author: String, forward: DynamicPatch, backward: DynamicPatch)

  object Edit {
    implicit val schema: Schema[Edit] = Schema.derived
  }

  def auditLines[A](schema: Schema[A], before: A, after: A, patch: Patch[A]): List[String] = {
    val old                                                   = schema.toDynamicValue(before)
    val now                                                   = schema.toDynamicValue(after)
    def show(value: DynamicValue, path: DynamicOptic): String =
      value.get(path).one.fold(_ => "(none)", v => Json.fromDynamicValue(v).print)
    patch.dynamicPatch.ops.toList.map(op => s"${op.path}: ${show(old, op.path)} -> ${show(now, op.path)}")
  }

  def applyStored[A](schema: Schema[A], value: A, patch: DynamicPatch): Either[SchemaError, A] =
    patch(schema.toDynamicValue(value), PatchMode.Strict).flatMap(schema.fromDynamicValue)

  def record(author: String, before: Book, after: Book): Edit =
    Edit(author, Book.schema.diff(before, after).dynamicPatch, Book.schema.diff(after, before).dynamicPatch)

  def replay(base: Book, edits: List[Edit]): Either[SchemaError, Book] =
    edits.foldLeft[Either[SchemaError, Book]](Right(base))((book, edit) =>
      book.flatMap(applyStored(Book.schema, _, edit.forward))
    )

  def applyIfUnchanged(current: Book, base: Book, patch: Patch[Book]): Either[String, Book] =
    if (current != base)
      Left(s"${current.id} changed since this patch was made; diff again against the current version")
    else patch(current, PatchMode.Strict).left.map(_.message)

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
            cur.fields.collectFirst { case (`k`, v) => v }
              .toRight(SchemaError(s"no key $k at $path"))
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

  val zionomicon    = Book("zionomicon", "Zionomicon", BigDecimal("44.99"), List("zio", "scala"), 1)
  val secondEdition = zionomicon.copy(price = BigDecimal("39.99"), tags = List("zio", "scala", "effects"), edition = 2)
  val withSubtitle  = secondEdition.copy(title = "Zionomicon (2nd Edition)")

  // Record two edits and print what alice changed.
  val history = List(record("alice", zionomicon, secondEdition), record("bob", secondEdition, withSubtitle))
  auditLines(Book.schema, zionomicon, secondEdition, Book.schema.diff(zionomicon, secondEdition))
    .foreach(line => println(s"alice changed $line"))

  // Store the log as JSON, load it back, replay it and undo the last edit.
  val historyCodec = Schema[List[Edit]].jsonCodec
  val loaded       = historyCodec.decode(historyCodec.encodeToString(history))
  println(loaded.flatMap(replay(zionomicon, _)))
  println(loaded.flatMap(edits => applyStored(Book.schema, withSubtitle, edits.last.backward)))

  // Apply a catalog patch to a catalog that another editor changed.
  val programmingInScala = Book("programming-in-scala", "Programming in Scala", BigDecimal("59.99"), List("scala"), 5)
  val fpInScala          = Book("fp-in-scala", "Functional Programming in Scala", BigDecimal("49.99"), List("scala", "fp"), 2)
  val monday             = Catalog(Map(zionomicon.id -> zionomicon, programmingInScala.id -> programmingInScala))
  val tuesday            = Catalog(Map(zionomicon.id -> secondEdition, fpInScala.id -> fpInScala))
  val catalogPatch       = Catalog.schema.diff(monday, tuesday)
  val changedMonday      = Catalog(monday.books - programmingInScala.id)
  List(PatchMode.Strict, PatchMode.Lenient).foreach { mode =>
    println(s"$mode: ${catalogPatch(changedMonday, mode).map(_.books.keys.toList.sorted)}")
  }

  // Reject a patch whose base changed, instead of removing the wrong tag.
  val tagged         = zionomicon.copy(tags = List("zio", "scala", "effects"))
  val removeScalaTag = Book.schema.diff(tagged, tagged.copy(tags = List("zio", "effects")))
  println(applyIfUnchanged(tagged.copy(tags = List("zio", "effects")), tagged, removeScalaTag))

  // Send the change to a system that speaks RFC 6902.
  val mondayJson = Json.from(zionomicon)
  println(Rfc6902.fromJsonPatch(mondayJson, mondayJson.diff(Json.from(withSubtitle))).map(_.print))
}
