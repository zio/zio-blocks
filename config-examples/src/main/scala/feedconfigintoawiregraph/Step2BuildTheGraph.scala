package feedconfigintoawiregraph

import zio.blocks.config._
import zio.blocks.config.yaml._
import zio.blocks.context.Context
import zio.blocks.schema.Schema
import zio.blocks.scope._

/**
 * How to Feed Configuration into a Wire Dependency Graph — Building the Whole
 * Graph
 *
 * Layers env over YAML over defaults and builds the bookstore graph.
 *
 * Run with: cd config-examples && sbt "runMain
 * feedconfigintoawiregraph.Step2BuildTheGraph"
 */
object Step2BuildTheGraph {

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

  val env = ConfigSource.fromMap(Map("db.password" -> "change-me"), "env")

  val bookstoreYaml =
    """db:
      |  url: jdbc:postgresql://localhost:5432/bookstore
      |  user: bookstore
      |catalog:
      |  featuredTitle: Zionomicon
      |""".stripMargin

  val yaml = ConfigSource.fromYaml(bookstoreYaml, "bookstore.yaml") match {
    case Right(source) => source
    case Left(error)   => throw error
  }

  val defaults = ConfigSource.fromMap(Map("db.poolSize" -> "10", "catalog.pageSize" -> "3"), "defaults")

  val settings = env.orElse(yaml).orElse(defaults)

  def catalogResource(source: ConfigSource): Resource[Catalog] =
    Resource.from[Catalog](
      Wire(source),
      Config.wire[DbConfig]("db"),
      Config.wire[CatalogConfig]("catalog")
    )

  def main(args: Array[String]): Unit = {
    val frontPage = Scope.global.scoped { scope =>
      import scope._
      val catalog = allocate(catalogResource(settings))
      $(catalog)(_.frontPage())
    }

    println(frontPage)
  }
}
