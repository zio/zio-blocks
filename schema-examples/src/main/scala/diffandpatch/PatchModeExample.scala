package diffandpatch

import zio.blocks.schema.patch._
import Books._

/**
 * Diff and Patch: Choose a PatchMode When the Data Changed
 *
 * Applies a catalog patch in Strict, Lenient and Clobber mode to two catalogs
 * that another editor changed, shows that Strict mode misses a changed price
 * and a changed tag list, and rejects a stale patch by checking its base.
 *
 * Run with: cd schema-examples && sbt "runMain diffandpatch.PatchModeExample"
 */
object PatchModeExample extends App {

  val monday  = Catalog(Map(zionomicon.id -> zionomicon, programmingInScala.id -> programmingInScala))
  val tuesday = Catalog(Map(zionomicon.id -> secondEdition, fpInScala.id -> fpInScala))

  val catalogPatch = Catalog.schema.diff(monday, tuesday)
  println(catalogPatch)

  def applyInEveryMode(target: Catalog): Unit =
    List(PatchMode.Strict, PatchMode.Lenient, PatchMode.Clobber).foreach { mode =>
      println(s"$mode: ${catalogPatch(target, mode).map(_.books.values.map(b => s"${b.id} ${b.price}").toList.sorted)}")
    }

  // Another editor added Functional Programming in Scala first, at 45.00.
  applyInEveryMode(monday.copy(books = monday.books + (fpInScala.id -> fpInScala.copy(price = BigDecimal("45.00")))))

  // Another editor removed Programming in Scala already.
  applyInEveryMode(monday.copy(books = monday.books - programmingInScala.id))

  // A numeric delta applies to whatever price is there now.
  val pricePatch = Book.schema.diff(zionomicon, secondEdition)
  println(pricePatch(zionomicon.copy(price = BigDecimal("42.00")), PatchMode.Strict))

  // A list removal by index removes the wrong tag when the list changed.
  val tagged         = zionomicon.copy(tags = List("zio", "scala", "effects"))
  val removeScalaTag = Book.schema.diff(tagged, tagged.copy(tags = List("zio", "effects")))
  println(removeScalaTag)
  println(removeScalaTag(tagged.copy(tags = List("zio", "effects")), PatchMode.Strict))

  def applyIfUnchanged(current: Book, base: Book, patch: Patch[Book]): Either[String, Book] =
    if (current != base)
      Left(s"${current.id} changed since this patch was made; diff again against the current version")
    else patch(current, PatchMode.Strict).left.map(_.message)

  println(applyIfUnchanged(tagged.copy(tags = List("zio", "effects")), tagged, removeScalaTag))
  println(applyIfUnchanged(tagged, tagged, removeScalaTag))
}
