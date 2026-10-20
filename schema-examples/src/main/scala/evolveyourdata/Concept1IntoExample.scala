package evolveyourdata

import zio.blocks.schema._

/**
 * Evolve Your Data: Convert Incoming Data with Into
 *
 * Derives an `Into[BookDto, BookV1]` and converts a book from the feed.
 *
 * Run with: cd schema-examples && sbt "runMain evolveyourdata.Concept1IntoExample"
 */
object Concept1IntoExample extends App {

  case class BookDto(title: String, author: String, year: Double, edition: Double)

  case class BookV1(title: String, author: String, year: Int, edition: Int)

  val dtoToBook: Into[BookDto, BookV1] = Into.derived[BookDto, BookV1]

  val zionomiconDto = BookDto("Zionomicon", "John A. De Goes", 2024.0, 1.0)

  println(dtoToBook.into(zionomiconDto))
}
