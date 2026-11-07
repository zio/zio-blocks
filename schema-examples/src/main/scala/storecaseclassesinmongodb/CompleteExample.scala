//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema-bson:0.0.56
//> using dep org.mongodb:mongodb-driver-sync:5.13.0
//> using options -experimental
//> using file Model.scala
//> using file TicketCodec.scala
//> using file MongoGlue.scala
// Needs MongoDB:  docker run --rm -d -p 27017:27017 --name mongo mongo:7
// Run from this folder:  scala-cli run CompleteExample.scala
// Or from schema-examples:  sbt "runMain storecaseclassesinmongodb.CompleteExample"

package storecaseclassesinmongodb

import com.mongodb.client.model.Filters
import org.bson.BsonDocument
import org.bson.types.ObjectId

/**
 * How to Store Case Classes in MongoDB — Putting It Together
 *
 * Stores tickets in MongoDB, queries them, and reports a document that another
 * program wrote in a shape the codec cannot read.
 */
object CompleteExample {
  def main(args: Array[String]): Unit =
    Mongo.withDatabase("helpdesk") { database =>
      val tickets = new TypedCollection(
        database.getCollection("tickets", classOf[BsonDocument]),
        TicketCodec.nullableTicketCodec
      )
      tickets.raw.drop() // start every run from an empty collection

      List(
        Ticket(
          id = new ObjectId(),
          title = "Login fails on Safari",
          status = InProgress("ana"),
          customerEmail = Some("kim@example.com"),
          comments = List(Comment("kim", "Only on Safari 17"))
        ),
        Ticket(new ObjectId(), "Invoice total is wrong", Closed("refunded")),
        Ticket(new ObjectId(), "Add a dark mode", Open, Some("lee@example.com"))
      ).foreach(tickets.insert)

      // Another program wrote this ticket: it adds "priority" and names the comment author "author"
      tickets.raw.insertOne(
        BsonDocument.parse(
          """{"title": "Export to CSV", "status": {"Open": {}}, "priority": 2, "comments": [{"author": "sam", "body": "+1"}]}"""
        )
      )

      println(s"closed: ${tickets.find(Filters.exists("status.Closed")).map(_.map(_.title))}")
      println(s"from kim: ${tickets.find(Filters.eq("customerEmail", "kim@example.com")).map(_.map(_.title))}")
      tickets.find(Filters.exists("status.Open")) match {
        case Right(open) => println(s"open: ${open.map(_.title)}")
        case Left(error) => println(s"open: cannot read a stored ticket, ${error.render}")
      }
    }
}
