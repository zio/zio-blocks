package jsonwithoutcaseclasses

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * How to Read and Modify JSON Without Case Classes — Parse JSON Text
 *
 * Parses the bookstore document with Json.parse, and prints the errors for a
 * trailing comma and for a missing comma between two fields.
 *
 * Run with: cd schema-examples && sbt "runMain
 * jsonwithoutcaseclasses.Step1ParseExample"
 */
object Step1ParseExample {
  def main(args: Array[String]): Unit = {
    val bookstore: Json = Json.parse(Bookstore.bookstoreText) match {
      case Right(json) => json
      case Left(error) => throw error
    }

    println(bookstore.print(WriterConfig.withIndentionStep(2)))

    println(Json.parse("""{"title": "Zionomicon", "price": 49.99,}"""))

    println(Json.parse("""{"title": "Zionomicon" "price": 49.99}"""))
  }
}
