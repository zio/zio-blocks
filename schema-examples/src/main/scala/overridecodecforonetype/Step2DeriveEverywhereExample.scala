package overridecodecforonetype

import zio.blocks.schema._
import zio.blocks.schema.json._

import java.time.LocalDate
import java.time.format.{DateTimeFormatter, DateTimeParseException}

/**
 * How to Override the Codec for One Type Everywhere — Registering the Codec and Deriving Nested Types
 *
 * Registers the custom date codec on JsonCodecDeriver and derives Order and Shelf codecs through it.
 *
 * Run with: cd schema-examples && sbt "runMain
 * overridecodecforonetype.Step2DeriveEverywhereExample"
 */
object Step2DeriveEverywhereExample extends App {

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

  val partnerJson = JsonCodecDeriver.withInstance[LocalDate](partnerDateCodec)

  val orderCodec: JsonCodec[Order] = Schema[Order].deriving(partnerJson).derive
  val shelfCodec: JsonCodec[Shelf] = Schema[Shelf].deriving(partnerJson).derive

  println(s"default:  ${shelf.toJsonString}")
  println(s"order:    ${orderCodec.encodeToString(shelf.orders.head)}")
  println(s"shelf:    ${shelfCodec.encodeToString(shelf)}")
  println(s"round trip: ${shelfCodec.decode(shelfCodec.encodeToString(shelf)) == Right(shelf)}")
}
