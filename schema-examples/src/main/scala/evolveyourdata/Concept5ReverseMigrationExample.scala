package evolveyourdata

import zio.blocks.schema._
import zio.blocks.schema.migration.Migration

/**
 * Evolve Your Data: Run a Migration Backwards and Look Inside It
 *
 * Reverses the migration, applies it to a `BookV3` and prints the migration's
 * actions.
 *
 * Run with: cd schema-examples && sbt "runMain
 * evolveyourdata.Concept5ReverseMigrationExample"
 */
object Concept5ReverseMigrationExample extends App {

  case class BookV2(title: String, author: String, year: Int, edition: Int, isbn: Option[String])

  case class BookV3(title: String, author: String, publishedYear: Int, edition: Int, isbn: Option[String])

  implicit val bookV2Schema: Schema[BookV2] = Schema.derived
  implicit val bookV3Schema: Schema[BookV3] = Schema.derived

  val v2ToV3: Migration[BookV2, BookV3] =
    Migration
      .newBuilder[BookV2, BookV3]
      .renameField(_.year, _.publishedYear)
      .build

  val v3ToV2: Migration[BookV3, BookV2] = v2ToV3.reverse

  val fpInScalaV3 = BookV3("Functional Programming in Scala", "Paul Chiusano", 2014, 1, Some("978-1617290657"))

  println(v3ToV2(fpInScalaV3))

  println(v2ToV3.actions)
}
