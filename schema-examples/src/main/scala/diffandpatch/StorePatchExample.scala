package diffandpatch

import zio.blocks.schema._
import zio.blocks.schema.patch._
import Books._

/**
 * Diff and Patch: Store a Patch as JSON and Load It Back
 *
 * Encodes the `DynamicPatch` of the Zionomicon price change as JSON text,
 * decodes it, and applies the loaded patch to the first edition.
 *
 * Run with: cd schema-examples && sbt "runMain diffandpatch.StorePatchExample"
 */
object StorePatchExample extends App {

  val pricePatch = Book.schema.diff(zionomicon, secondEdition)
  val patchCodec = Schema[DynamicPatch].jsonCodec

  val storedText: String = patchCodec.encodeToString(pricePatch.dynamicPatch)
  println(storedText)

  println(patchCodec.decode(storedText).map(_ == pricePatch.dynamicPatch))

  println(patchCodec.decode(storedText).flatMap(loaded => PatchHelpers.applyStored(Book.schema, zionomicon, loaded)))
}
