package diffandpatch

import Books._

/**
 * Diff and Patch: Diff Two Versions and Turn the Patch into Audit Lines
 *
 * Diffs the first and second edition of Zionomicon, prints the patch, a string
 * edit and an empty diff, then prints one audit line per changed field.
 *
 * Run with: cd schema-examples && sbt "runMain
 * diffandpatch.DiffAndAuditExample"
 */
object DiffAndAuditExample extends App {

  val pricePatch = Book.schema.diff(zionomicon, secondEdition)
  println(pricePatch)

  println(Book.schema.diff(secondEdition, withSubtitle))

  println(Book.schema.diff(zionomicon, zionomicon).isEmpty)

  PatchHelpers.auditLines(Book.schema, zionomicon, secondEdition, pricePatch).foreach(println)
}
