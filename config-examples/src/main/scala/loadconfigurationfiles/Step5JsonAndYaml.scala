package loadconfigurationfiles

import java.nio.file.{Files, Path}

import zio.blocks.config._
import zio.blocks.config.hocon._
import zio.blocks.config.json._
import zio.blocks.config.yaml._
import zio.blocks.schema.Schema

/**
 * How to Load Configuration Files on the JVM and in the Browser — Loading JSON
 * and YAML Files
 *
 * Reads a JSON and a YAML file with ConfigSource.fromJson and fromYaml, decodes
 * the same case class from both, and shows how the typed load guards against
 * lenient YAML.
 *
 * Run with: cd config-examples && sbt "runMain
 * loadconfigurationfiles.Step5JsonAndYaml"
 */
object Step5JsonAndYaml {

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

  def readConfigFile(path: Path)(
    parse: (String, String) => Either[ConfigError, ConfigSource]
  ): Either[ConfigError, ConfigSource] =
    parse(new String(Files.readAllBytes(path), "UTF-8"), path.getFileName.toString)

  def main(args: Array[String]): Unit = {
    val configJson = write(
      "config.json",
      """{
        |  "server": { "host": "0.0.0.0", "port": 8080 },
        |  "catalog": { "featuredBook": "Zionomicon", "pageSize": 20 },
        |  "db": {
        |    "url": "jdbc:postgresql://localhost:5432/bookstore",
        |    "user": "bookstore",
        |    "password": "dev-password",
        |    "poolSize": 10
        |  }
        |}
        |""".stripMargin
    )

    val configYaml = write(
      "config.yaml",
      """server:
        |  host: 0.0.0.0
        |  port: 8080
        |catalog:
        |  featuredBook: Zionomicon
        |  pageSize: 20
        |db:
        |  url: jdbc:postgresql://localhost:5432/bookstore
        |  user: bookstore
        |  password: dev-password
        |  poolSize: 10
        |""".stripMargin
    )

    println(decode(readConfigFile(configJson)(ConfigSource.fromJson(_, _))))
    println(decode(readConfigFile(configYaml)(ConfigSource.fromYaml(_, _))))

    // Guarding against lenient YAML
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

    println(readConfigFile(brokenYaml)(ConfigSource.fromYaml(_, _)).map(_.all("").keys.toList.sorted))
    decode(readConfigFile(brokenYaml)(ConfigSource.fromYaml(_, _))).left.foreach(_.foreach(report))
  }
}
