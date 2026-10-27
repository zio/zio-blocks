//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema-bson:0.0.56
//> using options -experimental
//> using file Model.scala
//> using file TicketCodec.scala
// Run from this folder:  scala-cli run Step4OptionalFieldsExample.scala
// Or from schema-examples:  sbt "runMain storecaseclassesinmongodb.Step4OptionalFieldsExample"

package storecaseclassesinmongodb

import org.bson.BsonDocument
import org.bson.types.ObjectId
import zio.blocks.schema._
import zio.blocks.schema.bson._

/**
 * How to Store Case Classes in MongoDB — Store Optional Fields
 *
 * Shows how the derived codec stores an Option, which documents it cannot read,
 * and how the codec from TicketCodec.scala stores null and plain strings.
 */
object Step4OptionalFieldsExample {
  def main(args: Array[String]): Unit = {
    // Setup from "Turn a Ticket into a BsonDocument and Back": the codec and one ticket
    val ticketCodec: BsonCodec[Ticket] = Schema[Ticket].derive(BsonCodecDeriver)

    val loginBug = Ticket(
      id = new ObjectId("6720f1a2b3c4d5e6f7a8b9c0"),
      title = "Login fails on Safari",
      status = InProgress("ana"),
      customerEmail = Some("kim@example.com"),
      comments = List(Comment("kim", "Only on Safari 17"))
    )

    def storedEmail(codec: BsonCodec[Ticket], ticket: Ticket): String =
      new BsonDocument("customerEmail", codec.encoder.toBsonValue(ticket).asDocument().get("customerEmail")).toJson

    println(storedEmail(ticketCodec, loginBug))
    println(storedEmail(ticketCodec, loginBug.copy(customerEmail = None)))

    def decodeEmail(json: String) = ticketCodec.decoder.fromBsonValue(BsonDocument.parse(json)).map(_.customerEmail)

    println(
      decodeEmail(
        """{"_id": {"$oid": "6720f1a2b3c4d5e6f7a8b9c0"}, "title": "Login fails on Safari", "status": {"Open": {}}}"""
      )
    )
    println(
      decodeEmail(
        """{"_id": {"$oid": "6720f1a2b3c4d5e6f7a8b9c0"}, "title": "Login fails on Safari", "status": {"Open": {}}, "customerEmail": null}"""
      )
    )
    println(
      decodeEmail(
        """{"_id": {"$oid": "6720f1a2b3c4d5e6f7a8b9c0"}, "title": "Login fails on Safari", "status": {"Open": {}}, "customerEmail": "kim@example.com"}"""
      )
    )

    val nullableTicketCodec = TicketCodec.nullableTicketCodec

    println(storedEmail(nullableTicketCodec, loginBug))
    println(storedEmail(nullableTicketCodec, loginBug.copy(customerEmail = None)))

    def decodeEmailNullable(json: String) =
      nullableTicketCodec.decoder.fromBsonValue(BsonDocument.parse(json)).map(_.customerEmail)

    println(
      decodeEmailNullable(
        """{"_id": {"$oid": "6720f1a2b3c4d5e6f7a8b9c0"}, "title": "Login fails on Safari", "status": {"Open": {}}, "customerEmail": null}"""
      )
    )
    println(
      decodeEmailNullable(
        """{"_id": {"$oid": "6720f1a2b3c4d5e6f7a8b9c0"}, "title": "Login fails on Safari", "status": {"Open": {}}, "customerEmail": "kim@example.com"}"""
      )
    )
  }
}
