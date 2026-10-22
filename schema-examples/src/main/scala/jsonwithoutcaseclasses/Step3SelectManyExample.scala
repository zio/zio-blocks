package jsonwithoutcaseclasses

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * How to Read and Modify JSON Without Case Classes — Select Many Values at Once
 *
 * Selects every title and price with the [*] wildcard, filters the books by
 * price, and reads a field that one book lacks.
 *
 * Run with: cd schema-examples && sbt "runMain
 * jsonwithoutcaseclasses.Step3SelectManyExample"
 */
object Step3SelectManyExample {
  def main(args: Array[String]): Unit = {
    val bookstore: Json = Bookstore.bookstore

    println(bookstore.get(p".books[*].title").asAll[String])

    println(bookstore.get(p".books[*].price").asAll[BigDecimal].map(_.sum))

    println(
      bookstore
        .get(p".books[*]")
        .filter(book => book.get("price").as[BigDecimal].exists(_ < 50))
        .get("title")
        .asAll[String]
    )

    println(bookstore.get(p".books[*].title").as[String])

    println(bookstore.get(p".books[*].stock").asAll[Int])

    println(bookstore.get("books").one.map(_.elements.map(book => book.get("stock").as[Int].getOrElse(0))))
  }
}
