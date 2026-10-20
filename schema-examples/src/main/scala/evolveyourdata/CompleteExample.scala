package evolveyourdata

import zio.blocks.schema._
import zio.blocks.schema.migration.Migration

/**
 * Evolve Your Data: Change Your Types with Into, As and Migration — Complete
 * Example
 *
 * Validates feed data with `Into`, collects several errors at once, converts
 * between two versions with `As`, renames a field with `Migration` and runs
 * the migration backwards.
 *
 * Run with: cd schema-examples && sbt "runMain evolveyourdata.CompleteExample"
 */
object CompleteExample extends App {

  case class BookDto(title: String, author: String, year: Double, edition: Double)

  case class BookV1(title: String, author: String, year: Int, edition: Int)

  case class BookV2(title: String, author: String, year: Int, edition: Int, isbn: Option[String])

  case class BookV3(title: String, author: String, publishedYear: Int, edition: Int, isbn: Option[String])

  // Convert incoming data with Into
  val dtoToBook: Into[BookDto, BookV1] = Into.derived[BookDto, BookV1]

  val zionomiconDto = BookDto("Zionomicon", "John A. De Goes", 2024.0, 1.0)

  println(dtoToBook.into(zionomiconDto))

  // Collect every error at once
  val brokenDto = BookDto("Programming in Scala", "Martin Odersky", 2008.5, 1.5)

  dtoToBook.into(brokenDto) match {
    case Left(error) => println(error.message)
    case Right(book) => println(s"Converted: $book")
  }

  // Convert both ways with As
  val v1AndV2: As[BookV1, BookV2] = As.derived[BookV1, BookV2]

  val v2ToV1: As[BookV2, BookV1] = v1AndV2.reverse

  val programmingInScalaV1 = BookV1("Programming in Scala", "Martin Odersky", 2008, 1)

  println(v1AndV2.into(programmingInScalaV1))

  val programmingInScalaV2 = BookV2("Programming in Scala", "Martin Odersky", 2008, 1, Some("978-0981531601"))

  println(v2ToV1.into(programmingInScalaV2))

  println(v1AndV2.into(programmingInScalaV1).flatMap(v1AndV2.from))

  // Rename a field with Migration
  implicit val bookV2Schema: Schema[BookV2] = Schema.derived
  implicit val bookV3Schema: Schema[BookV3] = Schema.derived

  val v2ToV3: Migration[BookV2, BookV3] =
    Migration
      .newBuilder[BookV2, BookV3]
      .renameField(_.year, _.publishedYear)
      .build

  val fpInScalaV2 = BookV2("Functional Programming in Scala", "Paul Chiusano", 2014, 1, Some("978-1617290657"))

  println(v2ToV3(fpInScalaV2))

  // Run the migration backwards and look inside it
  val v3ToV2: Migration[BookV3, BookV2] = v2ToV3.reverse

  val fpInScalaV3 = BookV3("Functional Programming in Scala", "Paul Chiusano", 2014, 1, Some("978-1617290657"))

  println(v3ToV2(fpInScalaV3))

  println(v2ToV3.actions)
}
