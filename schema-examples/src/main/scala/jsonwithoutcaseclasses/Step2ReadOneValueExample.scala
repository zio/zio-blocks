package jsonwithoutcaseclasses

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * How to Read and Modify JSON Without Case Classes — Read One Value Deep in the
 * Tree
 *
 * Reads single values by key, by index and by path, and prints the errors for a
 * missing field, a wrong type and an index out of range.
 *
 * Run with: cd schema-examples && sbt "runMain
 * jsonwithoutcaseclasses.Step2ReadOneValueExample"
 */
object Step2ReadOneValueExample {
  def main(args: Array[String]): Unit = {
    val bookstore: Json = Bookstore.bookstore

    println(bookstore.get("books")(0).get("price").as[BigDecimal])

    println(bookstore.get(p".books[0].price").as[BigDecimal])

    println(bookstore.get(p".books[1].authors[0]").as[String])

    println(bookstore.get(p".books[1].isbn").as[String])

    println(bookstore.get(p".books[0].title").as[BigDecimal])

    println(bookstore.get(p".books[5].title").as[String])

    println(bookstore.get(p".books[2].stock").as[Int].getOrElse(0))
  }
}
