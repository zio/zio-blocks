package loadconfigurationfiles

import java.nio.file.{Files, Path}

import zio.blocks.config._
import zio.blocks.config.hocon._
import zio.blocks.config.yaml._
import zio.blocks.schema.Schema

/**
 * How to Load Configuration Files on the JVM and in the Browser — Putting It
 * Together
 *
 * Writes a split application.conf and a defaults.conf into a temporary folder,
 * loads them with fromFile locked to that folder, layers system properties and
 * BOOKSTORE_* environment variables over them, prints the settings and where
 * three of them came from, and shows the errors for an include outside the
 * folder and for a broken YAML file.
 *
 * Run with: cd config-examples && BOOKSTORE_DB_PASSWORD=production-password sbt
 * "runMain loadconfigurationfiles.CompleteExample"
 */
object CompleteExample {

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
    // 1. Write a split application.conf and a shared defaults.conf.
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
    write("db/pool.conf", "poolSize = 10\n")
    write(
      "db/db.conf",
      """user = "bookstore"
        |password = "dev-password"
        |include "pool.conf"
        |""".stripMargin
    )
    val applicationConf = write(
      "application.conf",
      """include "defaults.conf"
        |catalog.featuredBook = "Zionomicon"
        |db {
        |  host = "db.internal"
        |  include "db/db.conf"
        |}
        |""".stripMargin
    )

    // 2. Load the file locked to the configuration folder, then layer the process settings over it.
    val fileSource = ConfigSource.fromFile(applicationConf.toString, allowedBase = Some(confDir.toFile)) match {
      case Right(source) => source
      case Left(error)   => throw error
    }
    val settings = systemProperties.orElse(productionEnv).orElse(fileSource)

    val loaded = Config.loadWithProvenance[BookstoreConfig](settings) match {
      case Right(provenance) => provenance
      case Left(errors)      => throw errors.head
    }

    // 3. The settings and where three non-secret values came from.
    println(loaded.value)
    println(loaded.provenanceOf("catalog.pageSize"))
    println(loaded.provenanceOf("catalog.featuredBook"))
    println(loaded.provenanceOf("server.port"))

    // 4. An include that leaves the folder fails the whole load.
    Files.write(workDir.resolve("secrets.conf"), "db.password = \"production-password\"\n".getBytes("UTF-8"))
    val escapeConf = write("escape.conf", "include \"../secrets.conf\"\n")
    ConfigSource.fromFile(escapeConf.toString, allowedBase = Some(confDir.toFile)) match {
      case Left(ConfigError.ParseError(_, _, _, Some(cause))) =>
        println(cause.getMessage.replace(workDir.toString, "<work>"))
      case other => println(other.map(_.sourceId))
    }

    // 5. A broken YAML file parses, but the typed load reports every missing key.
    val brokenYaml = write(
      "broken.yaml",
      """server:
        |  host: 0.0.0.0
        |  port: 8080
        |catalog:
        |  featuredBook: Zionomicon
        |  pageSize:20
        |db:
        |  url: jdbc:postgresql://localhost:5432/bookstore
        |  user: bookstore
        |  password: dev-password
        |  poolSize: 10
        |""".stripMargin
    )
    val yamlText = new String(Files.readAllBytes(brokenYaml), "UTF-8")
    decode(ConfigSource.fromYaml(yamlText, brokenYaml.getFileName.toString)).left.foreach(_.foreach(report))
  }
}
