package overridecodecforonetype

import zio.blocks.schema._
import zio.blocks.schema.json._
import zio.blocks.typeid.TypeId

import java.time.LocalDate
import java.time.format.{DateTimeFormatter, DateTimeParseException}

/**
 * How to Override the Codec for One Type Everywhere — Narrower Scopes: One Field or One Derivation
 *
 * Overrides only Order.orderedOn, then overrides LocalDate for a single derivation with DerivationBuilder#instance.
 *
 * Run with: cd schema-examples && sbt "runMain
 * overridecodecforonetype.Step4NarrowerScopesExample"
 */
object Step4NarrowerScopesExample extends App {

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

  val orderDateOnly = JsonCodecDeriver.withInstance(TypeId.of[Order], "orderedOn", partnerDateCodec)

  val shelfCodecOrderDateOnly: JsonCodec[Shelf] = Schema[Shelf].deriving(orderDateOnly).derive

  println(s"field only: ${shelfCodecOrderDateOnly.encodeToString(shelf)}")

  val oneOffShelfCodec: JsonCodec[Shelf] =
    Schema[Shelf]
      .deriving(JsonCodecDeriver)
      .instance(TypeId.of[LocalDate], partnerDateCodec)
      .derive

  println(s"one-off:    ${oneOffShelfCodec.encodeToString(shelf)}")
}
