package onetypemanyformats

import java.nio.CharBuffer
import java.nio.charset.StandardCharsets.UTF_8
import zio.blocks.schema._
import zio.blocks.schema.avro.AvroFormat
import zio.blocks.schema.csv.CsvFormat
import zio.blocks.schema.json.JsonFormat
import zio.blocks.schema.msgpack.MessagePackFormat
import zio.blocks.schema.toon.ToonFormat
import zio.blocks.schema.xml.XmlFormat
import zio.blocks.schema.yaml.YamlFormat

/**
 * One Type, Many Formats — Complete Example
 *
 * Derives JSON, YAML, XML, TOON, MessagePack and Avro codecs from one Catalog
 * schema, round-trips each, then exports a flat BookRow as CSV.
 *
 * Run with: cd schema-examples && sbt "runMain
 * onetypemanyformats.CompleteExample"
 */
object CompleteExample extends App {

  case class Book(title: String, author: String, year: Int, tags: List[String])

  object Book {
    implicit val schema: Schema[Book] = Schema.derived
  }

  case class Catalog(books: List[Book])

  object Catalog {
    implicit val schema: Schema[Catalog] = Schema.derived
  }

  val catalog = Catalog(
    List(
      Book("Zionomicon", "John A. De Goes, Adam Fraser and Milad Khajavi", 2024, List("zio", "effects")),
      Book("Programming in Scala", "Martin Odersky, Lex Spoon, Bill Venners", 2008, List("scala", "fundamentals")),
      Book("Functional Programming in Scala", "Paul Chiusano, Rúnar Bjarnason", 2014, List("scala", "fp"))
    )
  )

  // One schema, six structured formats: only the format object changes
  val jsonCodec = Catalog.schema.derive(JsonFormat)
  val yamlCodec = Catalog.schema.derive(YamlFormat)
  val xmlCodec  = Catalog.schema.derive(XmlFormat)
  val toonCodec = Catalog.schema.derive(ToonFormat)
  val msgCodec  = Catalog.schema.derive(MessagePackFormat)
  val avroCodec = Catalog.schema.derive(AvroFormat)

  val jsonBytes = jsonCodec.encode(catalog)
  println(new String(jsonBytes, UTF_8))
  println(jsonCodec.decode(jsonBytes) == Right(catalog))

  val yamlBytes = yamlCodec.encode(catalog)
  println(new String(yamlBytes, UTF_8))
  println(yamlCodec.decode(yamlBytes) == Right(catalog))

  val xmlBytes = xmlCodec.encode(catalog)
  println(new String(xmlBytes, UTF_8))
  println(xmlCodec.decode(xmlBytes) == Right(catalog))

  val toonBytes = toonCodec.encode(catalog)
  println(new String(toonBytes, UTF_8))
  println(toonCodec.decode(toonBytes) == Right(catalog))

  val msgBytes = msgCodec.encode(catalog)
  println(s"MessagePack: ${msgBytes.length} bytes, round trip: ${msgCodec.decode(msgBytes) == Right(catalog)}")

  val avroBytes = avroCodec.encode(catalog)
  println(s"Avro: ${avroBytes.length} bytes, round trip: ${avroCodec.decode(avroBytes) == Right(catalog)}")

  // CSV accepts only flat records, so export a BookRow per book
  case class BookRow(title: String, author: String, year: Int)

  object BookRow {
    implicit val schema: Schema[BookRow] = Schema.derived
  }

  val rows     = catalog.books.map(book => BookRow(book.title, book.author, book.year))
  val csvCodec = BookRow.schema.derive(CsvFormat)

  def toCsvLine(row: BookRow): String = {
    val buffer = CharBuffer.allocate(256)
    csvCodec.encode(row, buffer)
    buffer.flip()
    buffer.toString
  }

  val csvLines = rows.map(toCsvLine)
  println(csvCodec.headerNames.mkString(","))
  csvLines.foreach(line => println(line.stripLineEnd))
  println(csvLines.map(line => csvCodec.decode(CharBuffer.wrap(line))) == rows.map(Right(_)))
}
