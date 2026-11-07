//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema-avro:0.0.56
//> using options -experimental
// Run from this folder:  scala-cli run Step5TypeMappingExample.scala
// Or from schema-examples:  sbt "runMain avrorecords.Step5TypeMappingExample"

package avrorecords

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.avro._
import java.time.{Instant, LocalDate}
import java.util.UUID

/**
 * How to Encode and Decode Records with Avro — Check How Scala Types Map to
 * Avro
 *
 * Prints the Avro schema that the codec derives for common Scala types.
 */
object Step5TypeMappingExample {

  def avsc[A](implicit schema: Schema[A]): String = schema.derive(AvroFormat).avroSchema.toString

  sealed trait Status
  case object Active extends Status
  case object Closed extends Status

  case class CustomerId(value: Long)

  case class Sku(value: String)

  implicit val statusSchema: Schema[Status]         = Schema.derived
  implicit val customerIdSchema: Schema[CustomerId] = Schema.derived
  implicit val skuSchema: Schema[Sku]               = Schema[String].transform(Sku(_), _.value)
  implicit val bytesSchema: Schema[Array[Byte]]     = Schema.derived
  implicit val chunkSchema: Schema[Chunk[Byte]]     = Schema.derived

  def main(args: Array[String]): Unit =
    List(
      "Int"                 -> avsc[Int],
      "Long"                -> avsc[Long],
      "Short"               -> avsc[Short],
      "Byte"                -> avsc[Byte],
      "Char"                -> avsc[Char],
      "Float"               -> avsc[Float],
      "Double"              -> avsc[Double],
      "Boolean"             -> avsc[Boolean],
      "String"              -> avsc[String],
      "Unit"                -> avsc[Unit],
      "BigInt"              -> avsc[BigInt],
      "BigDecimal"          -> avsc[BigDecimal],
      "Array[Byte]"         -> avsc[Array[Byte]],
      "Chunk[Byte]"         -> avsc[Chunk[Byte]],
      "List[Int]"           -> avsc[List[Int]],
      "Map[String, Int]"    -> avsc[Map[String, Int]],
      "Instant"             -> avsc[Instant],
      "LocalDate"           -> avsc[LocalDate],
      "UUID"                -> avsc[UUID],
      "Option[Int]"         -> avsc[Option[Int]],
      "Either[String, Int]" -> avsc[Either[String, Int]],
      "Status"              -> avsc[Status],
      "CustomerId"          -> avsc[CustomerId],
      "Sku"                 -> avsc[Sku]
    ).foreach { case (scalaType, avroType) => println(s"$scalaType: $avroType") }
}
