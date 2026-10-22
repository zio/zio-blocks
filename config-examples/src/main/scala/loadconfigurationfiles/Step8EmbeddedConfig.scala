package loadconfigurationfiles

import java.nio.file.{Files, Path}

import zio.blocks.config._
import zio.blocks.config.hocon._
import zio.blocks.schema.Schema

/**
 * How to Load Configuration Files on the JVM and in the Browser — Loading the
 * Same Settings in the Browser
 *
 * The platform-independent part of the browser setup: the configuration is
 * compiled into the program as a HOCON String constant, or as a map of split
 * files, and parsed with fromHocon or HoconParser.parse. It runs on the JVM
 * here and behaves the same on Scala.js.
 *
 * Run with: cd config-examples && sbt "runMain
 * loadconfigurationfiles.Step8EmbeddedConfig"
 */
object Step8EmbeddedConfig {

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

  val embeddedConf: String =
    """server { host = "0.0.0.0", port = 8080 }
      |catalog { featuredBook = "Zionomicon", pageSize = 20 }
      |db {
      |  url = "jdbc:postgresql://localhost:5432/bookstore"
      |  user = "bookstore"
      |  password = "dev-password"
      |  poolSize = 10
      |}
      |""".stripMargin

  val bundledFiles: Map[String, String] = Map(
    "application.conf" ->
      """include "server.conf"
        |include "catalog.conf"
        |db {
        |  include "db.conf"
        |}
        |""".stripMargin,
    "server.conf"  -> "server { host = \"0.0.0.0\", port = 8080 }\n",
    "catalog.conf" -> "catalog { featuredBook = \"Zionomicon\", pageSize = 20 }\n",
    "db.conf"      ->
      """url = "jdbc:postgresql://localhost:5432/bookstore"
        |user = "bookstore"
        |password = "dev-password"
        |poolSize = 10
        |""".stripMargin
  )

  def parseBundled(files: Map[String, String], root: String): Either[ConfigError, ConfigSource] =
    HoconParser.parse(files(root), files.get) match {
      case Right(tree) => Right(ConfigSource.fromMap(HoconValue.flatten(tree), s"hocon:$root"))
      case Left(error) => Left(ConfigError.ParseError("", s"hocon:$root", "valid HOCON", Some(error)))
    }

  def main(args: Array[String]): Unit = {
    println(decode(ConfigSource.fromHocon(embeddedConf, "embedded.conf")))
    println(decode(parseBundled(bundledFiles, "application.conf")))
  }
}
