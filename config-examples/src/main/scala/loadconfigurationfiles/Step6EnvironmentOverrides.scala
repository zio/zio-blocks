package loadconfigurationfiles

import java.nio.file.{Files, Path}

import zio.blocks.config._
import zio.blocks.config.hocon._
import zio.blocks.schema.Schema

/**
 * How to Load Configuration Files on the JVM and in the Browser — Overriding
 * File Values From the Environment
 *
 * Layers SysPropSource and EnvSource over a HOCON file and prints where each
 * value came from. Provenance of the secret db.password is never printed.
 *
 * Run with: BOOKSTORE_DB_PASSWORD=production-password sbt 'set run /
 * javaOptions += "-Dbookstore.catalog.pageSize=50"' "runMain
 * loadconfigurationfiles.Step6EnvironmentOverrides" (from config-examples)
 */
object Step6EnvironmentOverrides {

  final case class ServerConfig(host: String, port: Int)
  object ServerConfig {
    implicit val schema: Schema[ServerConfig] = Schema.derived
  }

  final case class CatalogConfig(featuredBook: String, pageSize: Int)
  object CatalogConfig {
    implicit val schema: Schema[CatalogConfig] = Schema.derived
  }

  final case class DbConfig(url: String, user: String, password: Secret, poolSize: Int)
  object DbConfig {
    implicit val schema: Schema[DbConfig] = Schema.derived
  }

  final case class BookstoreConfig(server: ServerConfig, catalog: CatalogConfig, db: DbConfig)
  object BookstoreConfig {
    implicit val schema: Schema[BookstoreConfig] = Schema.derived
  }

  val workDir: Path = Files.createTempDirectory("bookstore").toRealPath()
  val confDir: Path = Files.createDirectories(workDir.resolve("conf"))

  def write(name: String, text: String): Path = {
    val file = confDir.resolve(name)
    Files.createDirectories(file.getParent)
    Files.write(file, text.getBytes("UTF-8"))
  }

  def report(error: ConfigError): Unit =
    println(error.getMessage.replace(workDir.toString, "<work>"))

  def decode(source: Either[ConfigError, ConfigSource]): Either[List[ConfigError], BookstoreConfig] =
    source.left.map(List(_)).flatMap(Config.load[BookstoreConfig](_))

  def loadBookstore(path: Path): Either[List[ConfigError], BookstoreConfig] =
    decode(ConfigSource.fromFile(path.toString))

  val productionEnv: ConfigSource = EnvSource.keyFormat(KeyFormat.UpperSnakeCase).prefix("bookstore")

  val systemProperties: ConfigSource = SysPropSource.prefix("bookstore")

  def main(args: Array[String]): Unit = {
    val applicationConf = write(
      "application.conf",
      """server {
        |  host = "0.0.0.0"
        |  port = 8080
        |}
        |catalog {
        |  featuredBook = "Zionomicon"
        |  pageSize = 20
        |}
        |db {
        |  url = "jdbc:postgresql://localhost:5432/bookstore"
        |  user = "bookstore"
        |  password = "dev-password"
        |  poolSize = 10
        |}
        |""".stripMargin
    )

    val fileSource = ConfigSource.fromFile(applicationConf.toString) match {
      case Right(source) => source
      case Left(error)   => throw error
    }

    // System properties win over the environment, and the environment wins over the file.
    val settings = systemProperties.orElse(productionEnv).orElse(fileSource)

    val loaded = Config.loadWithProvenance[BookstoreConfig](settings) match {
      case Right(provenance) => provenance
      case Left(errors)      => throw errors.head
    }

    println(loaded.value)
    println(loaded.provenanceOf("catalog.pageSize"))
    println(loaded.provenanceOf("server.port"))
  }
}
