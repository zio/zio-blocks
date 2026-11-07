package readandwriteyaml

import zio.blocks.schema._
import zio.blocks.schema.yaml._

/**
 * How to Read and Write YAML Documents — Guard Against Silent Data Loss
 *
 * Runs the key check on documents that YamlReader reads only in part, and the
 * output check on values the writer does not quote.
 *
 * Run with: cd schema-examples && sbt "runMain
 * readandwriteyaml.Step7GuardAgainstDataLossExample"
 */
object Step7GuardAgainstDataLossExample {

  sealed trait Format

  object Format {
    case object Paperback extends Format
    case object Hardcover extends Format
    case object Ebook     extends Format

    val all: List[Format] = List(Paperback, Hardcover, Ebook)

    implicit val schema: Schema[Format] = Schema[String].transform[Format](
      name => all.find(_.toString == name).getOrElse(throw new IllegalArgumentException(s"Unknown format: $name")),
      _.toString
    )
  }

  case class Book(
    title: String,
    authors: List[String],
    isbn: String,
    price: Double,
    pageCount: Int,
    tags: List[String],
    formats: List[Format]
  )

  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  case class Catalog(store: String, books: List[Book])

  object Catalog {
    implicit val schema: Schema[Catalog] = Schema.derived
  }

  val catalogCodec: YamlCodec[Catalog] = Catalog.schema.derive(YamlFormat)

  val catalogYaml: String =
    """# Books on the front table this month
      |store: Scala Books Berlin
      |books:
      |  - title: Zionomicon
      |    authors: [John A. De Goes, Adam Fraser, Milad Khajavi]
      |    isbn: 978-0-9862-0040-6
      |    price: 49.99
      |    page-count: 744
      |    tags: [zio, effects, concurrency]
      |    formats: [Ebook]
      |  - title: Programming in Scala
      |    authors:
      |      - Martin Odersky
      |      - Lex Spoon
      |      - Bill Venners
      |      - Frank Sommers
      |    isbn: "9780997148008"
      |    price: 59.95
      |    page-count: 852
      |    tags: [scala, language]
      |    formats:
      |      - Paperback
      |      - Ebook
      |""".stripMargin

  val zionomicon: Book = Book(
    title = "Zionomicon",
    authors = List("John A. De Goes", "Adam Fraser", "Milad Khajavi"),
    isbn = "978-0-9862-0040-6",
    price = 49.99,
    pageCount = 744,
    tags = List("zio", "effects"),
    formats = List(Format.Ebook, Format.Paperback)
  )

  val keyLine = """^\s*(?:-\s+)?([A-Za-z0-9_.-]+):(?:\s|$)""".r

  def keysInText(text: String): List[String] =
    text.linesIterator.flatMap(line => keyLine.findPrefixMatchOf(line).map(_.group(1))).toList

  def keysInTree(yaml: Yaml): List[String] = yaml match {
    case Yaml.Mapping(entries) =>
      entries.toList.flatMap {
        case (Yaml.Scalar(key, _), value) => key :: keysInTree(value)
        case (_, value)                   => keysInTree(value)
      }
    case Yaml.Sequence(elements) => elements.toList.flatMap(keysInTree)
    case _                       => Nil
  }

  def readChecked(text: String): Either[String, Yaml] = {
    val tree   = YamlReader.read(text)
    val unread = keysInText(text).diff(keysInTree(tree))
    if (unread.isEmpty) Right(tree)
    else Left(s"YAML not fully read, missing keys: ${unread.distinct.mkString(", ")}")
  }

  def decodeChecked[A](codec: YamlCodec[A], text: String): Either[String, A] =
    readChecked(text).flatMap { tree =>
      try Right(codec.decodeValue(tree))
      catch { case _: Exception => codec.decode(text).left.map(_.getMessage) }
    }

  def misreadByOtherReaders(yaml: Yaml): List[String] = yaml match {
    case Yaml.Mapping(entries) =>
      entries.toList.flatMap { case (key, value) => misreadByOtherReaders(key) ++ misreadByOtherReaders(value) }
    case Yaml.Sequence(elements) => elements.toList.flatMap(misreadByOtherReaders)
    case Yaml.Scalar(text, _)    =>
      if (text.startsWith("#") || text.startsWith("- ") || text == "-" || text.endsWith(":")) List(text) else Nil
    case Yaml.NullValue => Nil
  }

  def writeChecked[A](
    codec: YamlCodec[A],
    value: A,
    options: YamlOptions = YamlOptions.default
  ): Either[String, String] = {
    val tree  = codec.encodeValue(value)
    val text  = YamlWriter.write(tree, options)
    val risky = misreadByOtherReaders(tree)
    if (risky.nonEmpty) Left(s"Other YAML readers would misread: ${risky.mkString(", ")}")
    else if (codec.decode(text) != Right(value)) Left("The YAML doesn't read back as the same value")
    else Right(text)
  }

  def report(yaml: String): Unit =
    catalogCodec.decode(yaml) match {
      case Right(catalog) => println(s"OK: ${catalog.books.size} books")
      case Left(error)    => println(error.getMessage)
    }

  def main(args: Array[String]): Unit = {
    report(catalogYaml.replace("    isbn: 978-0-9862-0040-6", "\tisbn: 978-0-9862-0040-6"))

    println(decodeChecked(catalogCodec, catalogYaml).map(_.books.map(_.title)))
    println(decodeChecked(catalogCodec, catalogYaml.replace("    page-count: 744", "    page-count:744")))
    println(readChecked("steps:\n- name: Check out\n- name: Test\ntimeout-minutes: 30\n"))

    println(writeChecked(Book.schema.derive(YamlFormat), zionomicon).map(_ => "OK"))
    println(writeChecked(Book.schema.derive(YamlFormat), zionomicon.copy(tags = List("#scala", "zio"))))
    println(
      writeChecked(Book.schema.derive(YamlFormat), zionomicon.copy(title = "Zionomicon, 2nd edition"), YamlOptions.flow)
    )
    println(writeChecked(Book.schema.derive(YamlFormat), zionomicon.copy(title = "Zionomicon ")))
  }
}
