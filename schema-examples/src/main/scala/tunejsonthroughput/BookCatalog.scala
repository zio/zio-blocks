package tunejsonthroughput

import zio.blocks.schema._
import zio.blocks.schema.json._

/**
 * Tune JSON Throughput: The Book Catalog Model
 *
 * The shared Book model, its codecs and a 4,000-book catalog used by the
 * section examples. It has no main method.
 */
case class Book(id: Long, title: String, authors: List[String], year: Int, tags: List[String])
object Book {
  implicit val schema: Schema[Book] = Schema.derived

  val codec: JsonCodec[Book]              = schema.jsonCodec
  val catalogCodec: JsonCodec[List[Book]] = Schema[List[Book]].jsonCodec
}

object BookCatalog {
  val classics: List[Book] = List(
    Book(1L, "Zionomicon", List("John A. De Goes", "Adam Fraser", "Milad Khajavi"), 2024, List("zio", "effects")),
    Book(2L, "Programming in Scala", List("Martin Odersky", "Lex Spoon", "Bill Venners"), 2008, List("scala")),
    Book(3L, "Functional Programming in Scala", List("Paul Chiusano", "Rúnar Bjarnason"), 2014, List("fp")),
    Book(4L, "Scala for the Impatient", List("Cay S. Horstmann"), 2012, List("scala")),
    Book(5L, "Hands-on Scala Programming", List("Li Haoyi"), 2020, List("scala", "tooling")),
    Book(6L, "Essential Effects", List("Adam Rosien"), 2021, List("cats-effect", "effects")),
    Book(7L, "Practical FP in Scala", List("Gabriel Volpe"), 2020, List("fp", "cats-effect")),
    Book(8L, "Scala with Cats", List("Noel Welsh", "Dave Gurnell"), 2017, List("cats", "fp"))
  )

  val catalog: List[Book] =
    List.tabulate(4000)(i => classics(i % classics.size).copy(id = i + 1L))

  val catalogBytes: Array[Byte] = Book.catalogCodec.encode(catalog)
}
