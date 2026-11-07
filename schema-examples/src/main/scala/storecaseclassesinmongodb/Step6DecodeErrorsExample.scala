//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema-bson:0.0.56
//> using options -experimental
//> using file Model.scala
// Run from this folder:  scala-cli run Step6DecodeErrorsExample.scala
// Or from schema-examples:  sbt "runMain storecaseclassesinmongodb.Step6DecodeErrorsExample"

package storecaseclassesinmongodb

import org.bson.BsonDocument
import zio.blocks.schema._
import zio.blocks.schema.bson._
import zio.blocks.schema.bson.BsonSchemaCodec.Config

object strict {
  @Modifier.noExtraFields()
  final case class Comment(authorName: String, body: String)

  object Comment {
    implicit val schema: Schema[Comment] = Schema.derived
  }
}

/**
 * How to Store Case Classes in MongoDB — Handle Decode Errors and Extra Fields
 *
 * Decodes documents that don't match the ticket model and shows how extra
 * fields are skipped or rejected.
 */
object Step6DecodeErrorsExample {
  // Setup from "Turn a Ticket into a BsonDocument and Back": the codec
  val ticketCodec: BsonCodec[Ticket] = Schema[Ticket].derive(BsonCodecDeriver)

  def readTicket(json: String): Unit =
    ticketCodec.decoder.fromBsonValue(BsonDocument.parse(json)) match {
      case Right(ticket) => println(s"decoded: ${ticket.title}")
      case Left(error)   => println(s"failed: ${error.render}")
    }

  def main(args: Array[String]): Unit = {
    readTicket("""{"_id": {"$oid": "6720f1a2b3c4d5e6f7a8b9c0"}, "status": {"Open": {}}}""")
    readTicket("""{"_id": {"$oid": "6720f1a2b3c4d5e6f7a8b9c0"}, "title": 42, "status": {"Open": {}}}""")
    readTicket(
      """{"_id": {"$oid": "6720f1a2b3c4d5e6f7a8b9c0"}, "title": "Export to CSV", "status": {"Open": {}}, "comments": [{"author": "sam", "body": "+1"}]}"""
    )
    readTicket("""{"_id": {"$oid": "6720f1a2b3c4d5e6f7a8b9c0"}, "title": "Export to CSV", "status": {"Pending": {}}}""")

    val withPriority =
      """{"_id": {"$oid": "6720f1a2b3c4d5e6f7a8b9c0"}, "title": "Export to CSV", "status": {"Open": {}}, "priority": 2}"""

    readTicket(withPriority)

    println(
      BsonSchemaCodec
        .bsonCodec(Schema[Ticket], Config.withIgnoreExtraFields(false))
        .decoder
        .fromBsonValue(BsonDocument.parse(withPriority))
        .left
        .map(_.render)
    )

    println(
      Schema[strict.Comment]
        .derive(BsonCodecDeriver)
        .decoder
        .fromBsonValue(BsonDocument.parse("""{"authorName": "sam", "body": "+1", "likes": 3}"""))
        .left
        .map(_.render)
    )
  }
}
