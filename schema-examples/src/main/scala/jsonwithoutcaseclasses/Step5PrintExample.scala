package jsonwithoutcaseclasses

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * How to Read and Modify JSON Without Case Classes — Print JSON Text
 *
 * Prints the first book as compact and as indented JSON, and compares two
 * documents whose keys come in a different order.
 *
 * Run with: cd schema-examples && sbt "runMain
 * jsonwithoutcaseclasses.Step5PrintExample"
 */
object Step5PrintExample {
  def main(args: Array[String]): Unit = {
    val bookstore: Json = Bookstore.bookstore

    val firstBook: Json = bookstore.get(p".books[0]").one.getOrElse(Json.Null)

    println(firstBook.print)

    println(firstBook.print(WriterConfig.withIndentionStep(2)))

    println(
      Json.parse("""{"title": "Zionomicon", "price": 49.99}""") ==
        Json.parse("""{"price": 49.99, "title": "Zionomicon"}""")
    )

    println(Json.parse("""["zio", "effects"]""") == Json.parse("""["effects", "zio"]"""))
  }
}
