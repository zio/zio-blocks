package evolveyourdata

import zio.blocks.schema._
import zio.blocks.schema.migration.Migration

/**
 * Evolve Your Data: Rename a Field with Migration
 *
 * Builds a `Migration[BookV2, BookV3]` that renames `year` to `publishedYear`
 * and applies it.
 *
 * Run with: cd schema-examples && sbt "runMain
 * evolveyourdata.Concept4MigrationExample"
 */
object Concept4MigrationExample extends App {

  case class BookV2(title: String, author: String, year: Int, edition: Int, isbn: Option[String])

  case class BookV3(title: String, author: String, publishedYear: Int, edition: Int, isbn: Option[String])

  implicit val bookV2Schema: Schema[BookV2] = Schema.derived
  implicit val bookV3Schema: Schema[BookV3] = Schema.derived

  val v2ToV3: Migration[BookV2, BookV3] =
    Migration
      .newBuilder[BookV2, BookV3]
      .renameField(_.year, _.publishedYear)
      .build

  val fpInScalaV2 = BookV2("Functional Programming in Scala", "Paul Chiusano", 2014, 1, Some("978-1617290657"))

  println(v2ToV3(fpInScalaV2))
}
