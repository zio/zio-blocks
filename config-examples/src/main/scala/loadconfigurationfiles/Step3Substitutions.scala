package loadconfigurationfiles

import java.nio.file.{Files, Path}

import zio.blocks.config._
import zio.blocks.config.hocon._
import zio.blocks.schema.Schema

/**
 * How to Load Configuration Files on the JVM and in the Browser — Reusing
 * Values With Substitutions and Overrides
 *
 * A shared defaults.conf builds the JDBC URL with substitutions; layered.conf
 * includes it and overrides two values.
 *
 * Run with: cd config-examples && sbt "runMain
 * loadconfigurationfiles.Step3Substitutions"
 */
object Step3Substitutions {

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

  def main(args: Array[String]): Unit = {
    write(
      "defaults.conf",
      """server {
        |  host = "0.0.0.0"
        |  port = 8080
        |}
        |catalog {
        |  featuredBook = "Programming in Scala"
        |  pageSize = 20
        |}
        |db {
        |  host = "localhost"
        |  port = 5432
        |  name = "bookstore"
        |  url = "jdbc:postgresql://"${db.host}":"${db.port}"/"${db.name}
        |  user = "bookstore"
        |  password = "dev-password"
        |  poolSize = 10
        |}
        |""".stripMargin
    )

    val layeredConf = write(
      "layered.conf",
      """include "defaults.conf"
        |catalog.featuredBook = "Zionomicon"
        |db.host = "db.internal"
        |""".stripMargin
    )

    println(loadBookstore(layeredConf))
  }
}
