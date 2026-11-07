package loadconfigurationfiles

import java.nio.file.{Files, Path}

import zio.blocks.config._
import zio.blocks.config.hocon._
import zio.blocks.schema.Schema

/**
 * How to Load Configuration Files on the JVM and in the Browser — Loading
 * application.conf From a File
 *
 * Writes an application.conf into a temporary folder, loads it with
 * ConfigSource.fromFile into one typed case class, and shows the error for a
 * path that does not exist.
 *
 * Run with: cd config-examples && sbt "runMain
 * loadconfigurationfiles.Step1LoadHoconFile"
 */
object Step1LoadHoconFile {

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

  val applicationConf: Path = write(
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

  def main(args: Array[String]): Unit = {
    println(loadBookstore(applicationConf))
    loadBookstore(confDir.resolve("aplication.conf")).left.foreach(_.foreach(report))
  }
}
