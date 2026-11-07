package loadconfigurationfiles

import java.nio.file.{Files, Path}

import zio.blocks.config._
import zio.blocks.config.hocon._
import scala.io.Source
import zio.blocks.schema.Schema

/**
 * How to Load Configuration Files on the JVM and in the Browser — Loading
 * application.conf From the Classpath
 *
 * Reads a single application.conf from the classpath with fromHocon, then
 * resolves the includes of a split configuration with HoconParser.parse and a
 * resource-reading include function.
 *
 * Run with: cd config-examples && sbt "runMain
 * loadconfigurationfiles.Step7Classpath"
 */
object Step7Classpath {

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

  def resourceText(name: String): Option[String] =
    Option(getClass.getClassLoader.getResourceAsStream(name)).map { in =>
      try Source.fromInputStream(in, "UTF-8").mkString
      finally in.close()
    }

  val fromClasspath: Either[ConfigError, ConfigSource] =
    resourceText("loadconfigurationfiles/application.conf") match {
      case Some(text) => ConfigSource.fromHocon(text, "classpath:application.conf")
      case None       => Left(ConfigError.ParseError("application.conf", "classpath", "existing resource"))
    }

  def parseSplit(root: String): Either[ConfigError, ConfigSource] =
    resourceText(s"loadconfigurationfiles/split/$root") match {
      case None       => Left(ConfigError.ParseError("", s"classpath:$root", "existing resource"))
      case Some(text) =>
        HoconParser.parse(text, name => resourceText(s"loadconfigurationfiles/split/$name")) match {
          case Right(tree) => Right(ConfigSource.fromMap(HoconValue.flatten(tree), s"hocon:$root"))
          case Left(error) => Left(ConfigError.ParseError("", s"hocon:$root", "valid HOCON", Some(error)))
        }
    }

  def main(args: Array[String]): Unit = {
    println(decode(fromClasspath))
    println(decode(parseSplit("application.conf")))
  }
}
