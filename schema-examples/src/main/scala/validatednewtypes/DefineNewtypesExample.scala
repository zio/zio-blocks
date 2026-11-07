package validatednewtypes

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * Validated Newtypes: Define the Newtypes
 *
 * Calls each smart constructor with good and bad input, then decodes and
 * encodes Functional Programming in Scala to show that the newtypes appear in
 * JSON as bare values.
 *
 * Run with: cd schema-examples && sbt "runMain
 * validatednewtypes.DefineNewtypesExample"
 */
object DefineNewtypesExample extends App {

  println(Isbn.parse("978-0-9971480-0-8"))
  println(Isbn.parse("978-0-9971480-0-9").left.map(_.message))
  println(Title.parse("   ").left.map(_.message))

  val fpInScala: Book =
    Book.schema.jsonCodec
      .decode("""{"isbn":"978-1-61729-065-7","title":"Functional Programming in Scala","price":44.99,"pages":320}""")
      .fold(e => throw e, identity)

  println(fpInScala)
  println(Book.schema.jsonCodec.encodeToString(fpInScala))
}
