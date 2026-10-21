package feedconfigintoawiregraph

import zio.blocks.config._
import zio.blocks.config.yaml._
import zio.blocks.context.Context
import zio.blocks.schema.Schema
import zio.blocks.scope._

/**
 * How to Feed Configuration into a Wire Dependency Graph — Stopping at Startup
 * on a Bad Setting
 *
 * A malformed catalog section stops the graph with a full report.
 *
 * Run with: cd config-examples && sbt "runMain
 * feedconfigintoawiregraph.Step4BadSetting"
 */
object Step4BadSetting {

  final case class DbConfig(url: String, user: String, password: Secret, poolSize: Int)
  object DbConfig {
    implicit val schema: Schema[DbConfig] = Schema.derived
  }

  final case class CatalogConfig(featuredTitle: String, pageSize: Int)
  object CatalogConfig {
    implicit val schema: Schema[CatalogConfig] = Schema.derived
  }

  class Db(config: DbConfig) extends AutoCloseable {
    println(
      s"Db: opening ${config.poolSize} connections to ${config.url} as ${config.user}, password ${config.password}"
    )

    def titles(): List[String] =
      List("Zionomicon", "Programming in Scala", "Functional Programming in Scala")

    def close(): Unit = println("Db: closing the connection pool")
  }

  class InventoryRepo(db: Db) {
    println("InventoryRepo: ready")

    def inStock(): List[String] = db.titles()
  }

  class Catalog(repo: InventoryRepo, config: CatalogConfig) extends AutoCloseable {
    println(s"Catalog: featuring ${config.featuredTitle}")

    def frontPage(): String =
      s"Featured: ${config.featuredTitle} | In stock: ${repo.inStock().take(config.pageSize).mkString(", ")}"

    def close(): Unit = println("Catalog: flushing the page cache")
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

  def catalogResource(source: ConfigSource): Resource[Catalog] =
    Resource.from[Catalog](
      Wire(source),
      Config.wire[DbConfig]("db"),
      Config.wire[CatalogConfig]("catalog")
    )

  val badCatalog = ConfigSource.fromMap(Map("catalog.pageSize" -> "two"), "bad-catalog").orElse(sectionSource)

  def main(args: Array[String]): Unit =
    try {
      Scope.global.scoped { scope =>
        import scope._
        val catalog = allocate(catalogResource(badCatalog))
        $(catalog)(_.frontPage())
      }
    } catch {
      case e: ConfigLoadException => println(e.getMessage)
    }
}
