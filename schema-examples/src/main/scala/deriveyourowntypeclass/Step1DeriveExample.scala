package deriveyourowntypeclass

import Bookstore._

/**
 * How to Derive Your Own Type Class from a Schema — Deriving the Type Class and
 * Printing the Result
 *
 * Derives Redact for Book and Order from their schemas and prints the three
 * books and the order.
 *
 * Run with: cd schema-examples && sbt "runMain
 * deriveyourowntypeclass.Step1DeriveExample"
 */
object Step1DeriveExample {
  def main(args: Array[String]): Unit = {
    val redactBook: Redact[Book] = Book.schema.derive(RedactDeriver)
    List(zionomicon, programmingInScala, fpInScala).foreach(book => println(redactBook.redact(book)))

    val redactOrder: Redact[Order] = Order.schema.derive(RedactDeriver)
    println(redactOrder.redact(order))
  }
}
