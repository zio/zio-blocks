package diffandpatch

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.json._
import Books._

/**
 * Converts a ZIO Blocks `JsonPatch` produced by `Json#diff` into an RFC 6902
 * JSON Patch (an array of `add`, `remove` and `replace` operations).
 *
 * This is user code, not a library API. Number deltas and string edits become a
 * `replace` with the new value, computed from `source`.
 */
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

/**
 * Diff and Patch: Exchange JSON Patches with Other Systems
 *
 * Diffs two `Json` documents, applies the `JsonPatch`, converts a typed patch
 * with `JsonPatch.fromDynamicPatch`, and converts three edits to RFC 6902,
 * checking each result against the operations we expect.
 *
 * Run with: cd schema-examples && sbt "runMain diffandpatch.JsonPatchExample"
 */
object JsonPatchExample extends App {

  val mondayJson  = Json.from(zionomicon)
  val tuesdayJson = Json.from(withSubtitle)

  val jsonPatch: JsonPatch = mondayJson.diff(tuesdayJson)
  println(jsonPatch)
  println(mondayJson.patch(jsonPatch).map(_.print))

  println(JsonPatch.fromDynamicPatch(Book.schema.diff(zionomicon, secondEdition).dynamicPatch))

  def check(label: String, source: Json, target: Json, expected: String): Unit = {
    val actual = Rfc6902.fromJsonPatch(source, source.diff(target)).map(_.print)
    println(s"$label: ${actual.fold(_.message, identity)}")
    assert(actual == Right(expected), s"$label: expected $expected")
    println(s"$label: OK")
  }

  check(
    "book",
    mondayJson,
    tuesdayJson,
    """[{"op":"replace","path":"/title","value":"Zionomicon (2nd Edition)"},""" +
      """{"op":"replace","path":"/price","value":39.99},""" +
      """{"op":"add","path":"/tags/-","value":"effects"},""" +
      """{"op":"replace","path":"/edition","value":2}]"""
  )

  check(
    "tags",
    Json.from(zionomicon.copy(tags = List("zio", "scala", "effects", "fibers"))),
    Json.from(zionomicon.copy(tags = List("zio", "fibers", "streams"))),
    """[{"op":"remove","path":"/tags/1"},""" +
      """{"op":"remove","path":"/tags/1"},""" +
      """{"op":"add","path":"/tags/-","value":"streams"}]"""
  )

  check(
    "catalog",
    Json.from(Catalog(Map(zionomicon.id -> zionomicon, programmingInScala.id -> programmingInScala))),
    Json.from(Catalog(Map(zionomicon.id -> secondEdition, fpInScala.id -> fpInScala))),
    """[{"op":"replace","path":"/books/zionomicon/price","value":39.99},""" +
      """{"op":"add","path":"/books/zionomicon/tags/-","value":"effects"},""" +
      """{"op":"replace","path":"/books/zionomicon/edition","value":2},""" +
      """{"op":"add","path":"/books/fp-in-scala","value":""" +
      """{"id":"fp-in-scala","title":"Functional Programming in Scala","price":49.99,"tags":["scala","fp"],"edition":2}},""" +
      """{"op":"remove","path":"/books/programming-in-scala"}]"""
  )
}
