//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema-bson:0.0.56
//> using options -experimental
//> using file Model.scala
// Run from this folder:  scala-cli run Step2ObjectIdExample.scala
// Or from schema-examples:  sbt "runMain storecaseclassesinmongodb.Step2ObjectIdExample"

package storecaseclassesinmongodb

import org.bson.BsonDocument
import org.bson.types.ObjectId
import zio.blocks.schema._
import zio.blocks.schema.bson._

/**
 * How to Store Case Classes in MongoDB — Use MongoDB's ObjectId as the Document
 * Id
 *
 * Shows that the id is stored as BSON's own ObjectId type and that a string id
 * does not decode.
 */
object Step2ObjectIdExample {
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

    val document: BsonDocument = ticketCodec.encoder.toBsonValue(loginBug).asDocument()

    println(document.get("_id").getBsonType)

    println(
      ticketCodec.decoder.fromBsonValue(
        BsonDocument.parse(
          """{"_id": "6720f1a2b3c4d5e6f7a8b9c0", "title": "Login fails on Safari", "status": {"Open": {}}}"""
        )
      )
    )
  }
}
