package typedconfigloader

import zio.blocks.config._
import zio.blocks.schema.Schema

/**
 * Build a Typed Configuration Loader — Concept 1: Describe the Configuration as
 * a Case Class
 *
 * Describes a bookstore service's settings as case classes, derives a Schema
 * for the top-level one, and prints a hand-built configuration whose password
 * is redacted by Secret.
 *
 * Run with: cd config-examples && sbt "runMain
 * typedconfigloader.Concept1DescribeExample"
 */
object Concept1DescribeExample extends App {

  case class DbConfig(host: String, port: Int = 5432, user: String, password: Secret)

  case class BookstoreConfig(
    name: String,
    port: Int = 8080,
    featuredTitle: String,
    db: DbConfig,
    tags: List[String],
    discountPercent: Option[Int]
  )

  object BookstoreConfig {
    implicit val schema: Schema[BookstoreConfig] = Schema.derived
  }

  println(
    BookstoreConfig(
      "Scala Bookstore",
      8080,
      "Zionomicon",
      DbConfig("localhost", 5432, "bookstore", Secret("dev-password")),
      List("scala"),
      None
    )
  )
}
