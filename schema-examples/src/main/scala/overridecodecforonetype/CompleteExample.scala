package overridecodecforonetype

import zio.blocks.schema._
import zio.blocks.schema.json._
import zio.blocks.schema.toon._

import java.time.LocalDate
import java.time.format.{DateTimeFormatter, DateTimeParseException}

/**
 * How to Override the Codec for One Type Everywhere — Putting It Together
 *
 * Combines custom LocalDate codecs for JSON and TOON, registered once on each
 * deriver, for the nested bookstore model with round-trip checks.
 *
 * Run with: cd schema-examples && sbt "runMain
 * overridecodecforonetype.CompleteExample"
 */
object CompleteExample extends App {

  case class Order(title: String, orderedOn: LocalDate, price: BigDecimal)

  object Order {
    implicit val schema: Schema[Order] = Schema.derived
  }

  case class Shelf(owner: String, openedOn: LocalDate, orders: List[Order])

  object Shelf {
    implicit val schema: Schema[Shelf] = Schema.derived
  }

  val shelf = Shelf(
    owner = "Ada",
    openedOn = LocalDate.of(2025, 1, 10),
    orders = List(
      Order("Zionomicon", LocalDate.of(2025, 3, 14), BigDecimal("39.99")),
      Order("Programming in Scala", LocalDate.of(2025, 4, 2), BigDecimal("54.50")),
      Order("Functional Programming in Scala", LocalDate.of(2025, 5, 21), BigDecimal("44.00"))
    )
  )

  val partnerDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

  val partnerDateCodec: JsonCodec[LocalDate] = new JsonCodec[LocalDate] {
    private def parse(s: String): LocalDate =
      try LocalDate.parse(s, partnerDateFormat)
      catch { case _: DateTimeParseException => error(s"expected a dd/MM/yyyy date, got: $s") }

    def decodeValue(in: JsonReader): LocalDate =
      parse(in.readString())

    def encodeValue(x: LocalDate, out: JsonWriter): Unit =
      out.writeVal(partnerDateFormat.format(x))

    override def decodeValue(json: Json): LocalDate = json match {
      case s: Json.String => parse(s.value)
      case _              => error("expected a JSON string")
    }

    override def encodeValue(x: LocalDate): Json =
      Json.String(partnerDateFormat.format(x))
  }

  val partnerDateToonCodec: ToonCodec[LocalDate] = new ToonCodec[LocalDate] {
    def decodeValue(in: ToonReader): LocalDate = {
      in.skipBlankLines()
      val s = in.readString()
      try LocalDate.parse(s, partnerDateFormat)
      catch { case _: DateTimeParseException => error(s"expected a dd/MM/yyyy date, got: $s") }
    }

    def encodeValue(x: LocalDate, out: ToonWriter): Unit =
      out.writeString(partnerDateFormat.format(x))
  }

  val partnerJson = JsonCodecDeriver.withInstance[LocalDate](partnerDateCodec)
  val partnerToon = ToonCodecDeriver.withInstance[LocalDate](partnerDateToonCodec)

  val shelfCodec: JsonCodec[Shelf]     = Schema[Shelf].deriving(partnerJson).derive
  val shelfToonCodec: ToonCodec[Shelf] = Schema[Shelf].deriving(partnerToon).derive

  println(s"default JSON: ${shelf.toJsonString}")
  println(s"partner JSON: ${shelfCodec.encodeToString(shelf)}")
  println(s"partner TOON:\n${shelfToonCodec.encodeToString(shelf)}")
  println(s"JSON round trip: ${shelfCodec.decode(shelfCodec.encodeToString(shelf)) == Right(shelf)}")
  println(s"TOON round trip: ${shelfToonCodec.decode(shelfToonCodec.encodeToString(shelf)) == Right(shelf)}")
}
