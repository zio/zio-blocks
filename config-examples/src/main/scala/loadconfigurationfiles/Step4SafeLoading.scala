package loadconfigurationfiles

import java.nio.file.{Files, Path}

import zio.blocks.config._
import zio.blocks.config.hocon._
import zio.blocks.schema.Schema

/**
 * How to Load Configuration Files on the JVM and in the Browser — Loading Files
 * Safely
 *
 * Shows an include escaping the configuration folder (and allowedBase refusing
 * it), then a 2 MB file refused by the default size limit and accepted by
 * fromFileWithLimits.
 *
 * Run with: cd config-examples && sbt "runMain
 * loadconfigurationfiles.Step4SafeLoading"
 */
object Step4SafeLoading {

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
    // 1. Keeping includes inside the configuration folder
    Files.write(workDir.resolve("secrets.conf"), "db.password = \"production-password\"\n".getBytes("UTF-8"))

    val escapeConf = write("escape.conf", "include \"../secrets.conf\"\n")

    println(ConfigSource.fromFile(escapeConf.toString).map(_.all("").keys.toList))

    ConfigSource.fromFile(escapeConf.toString, allowedBase = Some(confDir.toFile)) match {
      case Left(ConfigError.ParseError(_, _, _, Some(cause))) =>
        println(cause.getMessage.replace(workDir.toString, "<work>"))
      case other => println(other)
    }

    // 2. Capping the file size
    val largeConf = write("large.conf", "# padding\n" * (200 * 1024))

    ConfigSource.fromFile(largeConf.toString).left.foreach(report)

    println(
      ConfigSource
        .fromFileWithLimits(largeConf.toString, allowedBase = Some(confDir.toFile), maxFileBytes = 4L * 1024 * 1024)
        .map(_.sourceId)
    )
  }
}
