package feedconfigintoawiregraph

import zio.blocks.config._
import zio.blocks.config.yaml._
import zio.blocks.context.Context
import zio.blocks.schema.Schema
import zio.blocks.scope._

/**
 * How to Feed Configuration into a Wire Dependency Graph — Reading One Settings
 * Section
 *
 * Runs one Config.wire by hand against a map source.
 *
 * Run with: cd config-examples && sbt "runMain
 * feedconfigintoawiregraph.Step1ReadOneSection"
 */
object Step1ReadOneSection {

  final case class DbConfig(url: String, user: String, password: Secret, poolSize: Int)
  object DbConfig {
    implicit val schema: Schema[DbConfig] = Schema.derived
  }

  val sectionSource = ConfigSource.fromMap(
    Map(
      "db.url"      -> "jdbc:postgresql://localhost:5432/bookstore",
      "db.user"     -> "bookstore",
      "db.password" -> "change-me",
      "db.poolSize" -> "10"
    ),
    "sections"
  )

  def main(args: Array[String]): Unit =
    Scope.global.scoped { scope =>
      val dbConfig = Config.wire[DbConfig]("db").make(scope, Context[ConfigSource](sectionSource))
      println(dbConfig)
    }
}
