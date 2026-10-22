package diffandpatch

import zio.blocks.schema._
import zio.blocks.schema.json._
import zio.blocks.schema.patch._

/**
 * Diff and Patch: the shared model
 *
 * The book, the catalog and the edit-log entry used by every step example, the
 * sample books, and two helpers from the guide: `auditLines` and `applyStored`.
 */
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

object Books {
  val zionomicon: Book = Book("zionomicon", "Zionomicon", BigDecimal("44.99"), List("zio", "scala"), 1)

  val secondEdition: Book =
    zionomicon.copy(price = BigDecimal("39.99"), tags = List("zio", "scala", "effects"), edition = 2)

  val withSubtitle: Book = secondEdition.copy(title = "Zionomicon (2nd Edition)")

  val programmingInScala: Book =
    Book("programming-in-scala", "Programming in Scala", BigDecimal("59.99"), List("scala"), 5)

  val fpInScala: Book =
    Book("fp-in-scala", "Functional Programming in Scala", BigDecimal("49.99"), List("scala", "fp"), 2)
}

object PatchHelpers {

  /**
   * One line per changed path: the path, the old value and the new value as
   * JSON.
   */
  def auditLines[A](schema: Schema[A], before: A, after: A, patch: Patch[A]): List[String] = {
    val old                                                   = schema.toDynamicValue(before)
    val now                                                   = schema.toDynamicValue(after)
    def show(value: DynamicValue, path: DynamicOptic): String =
      value.get(path).one.fold(_ => "(none)", v => Json.fromDynamicValue(v).print)
    patch.dynamicPatch.ops.toList.map(op => s"${op.path}: ${show(old, op.path)} -> ${show(now, op.path)}")
  }

  /** Applies a stored `DynamicPatch` to a typed value. */
  def applyStored[A](
    schema: Schema[A],
    value: A,
    patch: DynamicPatch,
    mode: PatchMode = PatchMode.Strict
  ): Either[SchemaError, A] =
    patch(schema.toDynamicValue(value), mode).flatMap(schema.fromDynamicValue)
}
