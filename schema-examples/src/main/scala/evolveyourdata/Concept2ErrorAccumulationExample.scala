package evolveyourdata

import zio.blocks.schema._

/**
 * Evolve Your Data: Collect Every Error at Once
 *
 * Converts a feed record with two bad fields and prints the single
 * `SchemaError` that lists both.
 *
 * Run with: cd schema-examples && sbt "runMain
 * evolveyourdata.Concept2ErrorAccumulationExample"
 */
object Concept2ErrorAccumulationExample extends App {

  case class BookDto(title: String, author: String, year: Double, edition: Double)

  case class BookV1(title: String, author: String, year: Int, edition: Int)

  val dtoToBook: Into[BookDto, BookV1] = Into.derived[BookDto, BookV1]

  val brokenDto = BookDto("Programming in Scala", "Martin Odersky", 2008.5, 1.5)

  dtoToBook.into(brokenDto) match {
    case Left(error) => println(error.message)
    case Right(book) => println(s"Converted: $book")
  }
}
