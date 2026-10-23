package jsonwithoutcaseclasses

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * How to Read and Modify JSON Without Case Classes — The Bookstore Document
 *
 * The bookstore document that the step examples share: the JSON text and the
 * parsed `bookstore` tree. This file has no main method.
 */
object Bookstore {

  val bookstoreText: String =
    """{
      |  "store": "Lambda Books",
      |  "currency": "USD",
      |  "books": [
      |    {
      |      "title": "Zionomicon",
      |      "authors": ["John A. De Goes", "Adam Fraser", "Milad Khajavi"],
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

  val bookstore: Json = Json.parse(bookstoreText) match {
    case Right(json) => json
    case Left(error) => throw error
  }
}
