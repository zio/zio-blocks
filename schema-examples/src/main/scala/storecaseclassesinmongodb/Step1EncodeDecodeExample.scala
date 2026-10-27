//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema-bson:0.0.56
//> using options -experimental
//> using file Model.scala
// Run from this folder:  scala-cli run Step1EncodeDecodeExample.scala
// Or from schema-examples:  sbt "runMain storecaseclassesinmongodb.Step1EncodeDecodeExample"

package storecaseclassesinmongodb

import org.bson.BsonDocument
import org.bson.types.ObjectId
import zio.blocks.schema._
import zio.blocks.schema.bson._

/**
 * How to Store Case Classes in MongoDB — Turn a Ticket into a BsonDocument and
 * Back
 *
 * Derives the ticket codec, prints one ticket as a BSON document and decodes it
 * back.
 */
object Step1EncodeDecodeExample {
  def main(args: Array[String]): Unit = {
    val ticketCodec: BsonCodec[Ticket] = Schema[Ticket].derive(BsonCodecDeriver)

    val loginBug = Ticket(
      id = new ObjectId("6720f1a2b3c4d5e6f7a8b9c0"),
      title = "Login fails on Safari",
      status = InProgress("ana"),
      customerEmail = Some("kim@example.com"),
      comments = List(Comment("kim", "Only on Safari 17"))
    )

    val document: BsonDocument = ticketCodec.encoder.toBsonValue(loginBug).asDocument()

    println(document.toJson)

    println(ticketCodec.decoder.fromBsonValue(document))
    println(ticketCodec.decoder.fromBsonValue(document) == Right(loginBug))
  }
}
