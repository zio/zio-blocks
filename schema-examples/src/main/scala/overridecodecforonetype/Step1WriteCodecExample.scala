package overridecodecforonetype

import zio.blocks.schema._
import zio.blocks.schema.json._

import java.time.LocalDate
import java.time.format.{DateTimeFormatter, DateTimeParseException}

/**
 * How to Override the Codec for One Type Everywhere — Writing the Codec for LocalDate
 *
 * Writes a custom JsonCodec[LocalDate] for dd/MM/yyyy and uses it on its own.
 *
 * Run with: cd schema-examples && sbt "runMain
 * overridecodecforonetype.Step1WriteCodecExample"
 */
object Step1WriteCodecExample extends App {

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

  println(partnerDateCodec.encodeToString(LocalDate.of(2025, 3, 14)))
  println(partnerDateCodec.decode("\"14/03/2025\""))
  println(partnerDateCodec.decode("\"2025-03-14\""))
}
