package validatednewtypes

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * Validated Newtypes: Reject Bad JSON
 *
 * Decodes a valid payload, a payload with a blank title, and the Zionomicon
 * payload with three bad values. The JSON codec stops at the first failure.
 *
 * Run with: cd schema-examples && sbt "runMain
 * validatednewtypes.RejectBadJsonExample"
 */
object RejectBadJsonExample extends App {

  println(
    Book.schema.jsonCodec.decode(
      """{"isbn":"9781617290657","title":"Functional Programming in Scala","price":44.99,"pages":320}"""
    )
  )

  Book.schema.jsonCodec
    .decode("""{"isbn":"9781617290657","title":"   ","price":44.99,"pages":320}""")
    .left
    .foreach(e => println(e.message))

  val zionomiconJson = """{"isbn":"978-ZIO","title":"Zionomicon","price":-45,"pages":0}"""

  Book.schema.jsonCodec.decode(zionomiconJson).left.foreach(e => println(e.message))
}
