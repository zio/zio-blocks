package jsonwithoutcaseclasses

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * How to Read and Modify JSON Without Case Classes — Putting It Together
 *
 * Handles a bookstore webhook end to end: parses the payload, reads the store
 * name and every title, raises the prices by 10%, tags the first book, removes
 * the internal `stock` field, decodes the books into case classes, and prints
 * the document it would publish.
 *
 * Run with: cd schema-examples && sbt "runMain
 * jsonwithoutcaseclasses.CompleteExample"
 */
object CompleteExample {

  case class Book(title: String, authors: List[String], price: BigDecimal, tags: List[String])

  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  val payload: String =
    """{
      |  "store": "Lambda Books",
      |  "currency": "USD",
      |  "books": [
      |    {
      |      "title": "Zionomicon",
      |      "authors": ["John A. De Goes", "Adam Fraser"],
      |      "price": 49.99,
      |      "tags": ["zio", "effects"],
      |      "stock": 12
      |    },
      |    {
      |      "title": "Programming in Scala",
      |      "authors": ["Martin Odersky", "Lex Spoon", "Bill Venners", "Frank Sommers"],
      |      "price": 59.99,
      |      "tags": ["scala", "language"],
      |      "stock": 0
      |    },
      |    {
      |      "title": "Functional Programming in Scala",
      |      "authors": ["Michael Pilquist", "Rúnar Bjarnason", "Paul Chiusano"],
      |      "price": 44.99,
      |      "tags": ["fp"]
      |    }
      |  ]
      |}""".stripMargin

  def main(args: Array[String]): Unit = {
    val bookstore: Json = Json.parse(payload) match {
      case Right(json) => json
      case Left(error) => throw error
    }

    println(bookstore.get(p".store").as[String])
    println(bookstore.get(p".books[*].title").asAll[String])

    val raised: Json = bookstore.modify(p".books[*].price") {
      case Json.Number(price) =>
        Json.Number((price * BigDecimal("1.1")).setScale(2, BigDecimal.RoundingMode.HALF_UP))
      case other => other
    }

    val tagged: Json = raised.modify(p".books[0].tags") {
      case Json.Array(tags) => Json.Array(tags :+ Json.String("bestseller"))
      case other            => other
    }

    val published: Json = tagged.modify(p".books[*]")(_.delete(p".stock"))

    println(published.get(p".books[*]").asAll[Book])

    println(published.print(WriterConfig.withIndentionStep(2)))
  }
}
