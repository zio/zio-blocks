package readandwriteyaml

import zio.blocks.schema._
import zio.blocks.schema.yaml._

/**
 * How to Read and Write YAML Documents — Complete Example
 *
 * Reads the catalog with the key check, reports decode errors for a damaged
 * copy, and writes the CI workflow through writeChecked and prints it.
 *
 * Run with: cd schema-examples && sbt "runMain
 * readandwriteyaml.CompleteExample"
 */
object CompleteExample {

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
      |    authors: [John A. De Goes, Adam Fraser]
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
    authors = List("John A. De Goes", "Adam Fraser"),
    isbn = "978-0-9862-0040-6",
    price = 49.99,
    pageCount = 744,
    tags = List("zio", "effects"),
    formats = List(Format.Ebook, Format.Paperback)
  )

  case class Branches(branches: List[String])

  object Branches {
    implicit val schema: Schema[Branches] = Schema.derived
  }

  case class Triggers(push: Branches, pullRequest: Branches)

  object Triggers {
    implicit val schema: Schema[Triggers] = Schema.derived
  }

  case class Step(name: String, uses: Option[String] = None, run: Option[String] = None)

  object Step {
    implicit val schema: Schema[Step] = Schema.derived
  }

  case class Job(runsOn: String, timeoutMinutes: Int, steps: List[Step])

  object Job {
    implicit val schema: Schema[Job] = Schema.derived
  }

  case class Workflow(name: String, on: Triggers, jobs: Map[String, Job])

  object Workflow {
    implicit val schema: Schema[Workflow] = Schema.derived
  }

  val workflowCodec: YamlCodec[Workflow] = Workflow.schema.derive(YamlFormat)

  val ciWorkflow: Workflow = Workflow(
    name = "CI",
    on = Triggers(push = Branches(List("main")), pullRequest = Branches(List("main"))),
    jobs = Map(
      "test" -> Job(
        runsOn = "ubuntu-latest",
        timeoutMinutes = 30,
        steps = List(
          Step("Check out", uses = Some("actions/checkout@v4")),
          Step("Set up sbt", uses = Some("sbt/setup-sbt@v1")),
          Step("Test", run = Some("sbt +test"))
        )
      )
    )
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
    decodeChecked(catalogCodec, catalogYaml) match {
      case Right(catalog) =>
        catalog.books.foreach(book => println(s"${book.title}: ${book.authors.mkString(", ")}, ${book.price}"))
      case Left(message) => println(message)
    }

    report(catalogYaml.replace("price: 59.95", "price: cheap"))
    report(catalogYaml.replace("formats: [Ebook]", "formats: [Kindle]"))
    println(decodeChecked(catalogCodec, catalogYaml.replace("    page-count: 744", "    page-count:744")))

    writeChecked(workflowCodec, ciWorkflow) match {
      case Right(text)   => println(text)
      case Left(message) => println(message)
    }
  }
}
