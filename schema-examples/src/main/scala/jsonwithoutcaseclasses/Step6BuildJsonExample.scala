package jsonwithoutcaseclasses

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * How to Read and Modify JSON Without Case Classes — Build JSON in Code
 *
 * Builds a fourth book with the Json constructors and with the json"..."
 * interpolator, checks that both give the same tree, and appends it to the
 * document. The interpolator works only on Scala 3 in this release: on Scala
 * 2.13 it fails to compile (known bug in 0.0.56), so use the constructors
 * there.
 *
 * Run with: cd schema-examples && sbt "runMain
 * jsonwithoutcaseclasses.Step6BuildJsonExample"
 */
object Step6BuildJsonExample {
  def main(args: Array[String]): Unit = {
    val bookstore: Json = Bookstore.bookstore

    val handsOnScala: Json = Json.Object(
      "title"   -> Json.String("Hands-on Scala Programming"),
      "authors" -> Json.Array(Json.String("Li Haoyi")),
      "price"   -> Json.Number(BigDecimal("39.99")),
      "tags"    -> Json.Array(Json.String("scala"), Json.String("tooling"))
    )

    val fourBooks: Json = bookstore.modify(p".books") {
      case Json.Array(books) => Json.Array(books :+ handsOnScala)
      case other             => other
    }

    println(fourBooks.get(p".books[*].title").asAll[String])

    val title = "Hands-on Scala Programming"
    val price = BigDecimal("39.99")
    val tags  = List("scala", "tooling")

    val handsOnScalaFromText: Json =
      json"""{"title": $title, "authors": ["Li Haoyi"], "price": $price, "tags": $tags}"""

    println(handsOnScalaFromText.print)

    println(handsOnScalaFromText == handsOnScala)
  }
}
