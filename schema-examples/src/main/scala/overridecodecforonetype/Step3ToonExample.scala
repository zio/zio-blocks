package overridecodecforonetype

import zio.blocks.schema._
import zio.blocks.schema.toon._

import java.time.LocalDate
import java.time.format.{DateTimeFormatter, DateTimeParseException}

/**
 * How to Override the Codec for One Type Everywhere — Doing the Same for TOON
 *
 * Registers a custom TOON date codec on ToonCodecDeriver and derives a Shelf codec through it.
 *
 * Run with: cd schema-examples && sbt "runMain
 * overridecodecforonetype.Step3ToonExample"
 */
object Step3ToonExample extends App {

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

  val partnerToon = ToonCodecDeriver.withInstance[LocalDate](partnerDateToonCodec)

  val shelfToonCodec: ToonCodec[Shelf] = Schema[Shelf].deriving(partnerToon).derive

  println(shelfToonCodec.encodeToString(shelf))
  println(s"round trip: ${shelfToonCodec.decode(shelfToonCodec.encodeToString(shelf)) == Right(shelf)}")
}
