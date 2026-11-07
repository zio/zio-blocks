package validatednewtypes

import zio.blocks.schema._
import zio.blocks.schema.json._
import zio.blocks.schema.toon.ToonFormat

/**
 * Validated Newtypes: Decode TOON with the Same Rules
 *
 * Derives a TOON codec from Book.schema, round-trips a valid book and rejects
 * the Zionomicon submission with the same message and path as JSON.
 *
 * Run with: cd schema-examples && sbt "runMain
 * validatednewtypes.ToonFormatExample"
 */
object ToonFormatExample extends App {

  val fpInScala: Book =
    Book.schema.jsonCodec
      .decode("""{"isbn":"978-1-61729-065-7","title":"Functional Programming in Scala","price":44.99,"pages":320}""")
      .fold(e => throw e, identity)

  val toonCodec = Book.schema.derive(ToonFormat)

  println(toonCodec.encodeToString(fpInScala))

  toonCodec
    .decode("isbn: \"978-ZIO\"\ntitle: Zionomicon\nprice: -45\npages: 0")
    .left
    .foreach(e => println(e.message))
}
