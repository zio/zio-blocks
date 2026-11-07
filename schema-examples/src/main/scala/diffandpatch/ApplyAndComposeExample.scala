package diffandpatch

import zio.blocks.schema.patch._
import Books._

/**
 * Diff and Patch: Apply a Patch and Compose Patches
 *
 * Applies a patch with `Schema#patch`, checks that diff then patch gives back
 * the new version, and composes two patches with `Patch#++`.
 *
 * Run with: cd schema-examples && sbt "runMain
 * diffandpatch.ApplyAndComposeExample"
 */
object ApplyAndComposeExample extends App {

  val pricePatch    = Book.schema.diff(zionomicon, secondEdition)
  val subtitlePatch = Book.schema.diff(secondEdition, withSubtitle)

  println(Book.schema.patch(zionomicon, pricePatch))
  println(Book.schema.patch(zionomicon, pricePatch) == Right(secondEdition))

  println((pricePatch ++ subtitlePatch)(zionomicon, PatchMode.Strict))
}
