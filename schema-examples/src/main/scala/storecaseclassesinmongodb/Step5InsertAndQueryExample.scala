//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema-bson:0.0.56
//> using dep org.mongodb:mongodb-driver-sync:5.13.0
//> using options -experimental
//> using file Model.scala
//> using file TicketCodec.scala
//> using file MongoGlue.scala
// Needs MongoDB:  docker run --rm -d -p 27017:27017 --name mongo mongo:7
// Run from this folder:  scala-cli run Step5InsertAndQueryExample.scala
// Or from schema-examples:  sbt "runMain storecaseclassesinmongodb.Step5InsertAndQueryExample"

package storecaseclassesinmongodb

import com.mongodb.client.model.Filters
import org.bson.BsonDocument
import org.bson.types.ObjectId

/**
 * How to Store Case Classes in MongoDB — Insert and Query with the MongoDB Java
 * Driver
 *
 * Inserts three tickets into a real MongoDB collection and finds them by id, by
 * status, by customer email and by comment author.
 */
object Step5InsertAndQueryExample {
  def main(args: Array[String]): Unit =
    Mongo.withDatabase("helpdesk") { database =>
      val tickets = new TypedCollection(
        database.getCollection("tickets", classOf[BsonDocument]),
        TicketCodec.nullableTicketCodec
      )
      tickets.raw.drop() // start every run from an empty collection

      val loginBug = Ticket(
        id = new ObjectId(),
        title = "Login fails on Safari",
        status = InProgress("ana"),
        customerEmail = Some("kim@example.com"),
        comments = List(Comment("kim", "Only on Safari 17"))
      )
      val wrongInvoice = Ticket(new ObjectId(), "Invoice total is wrong", Closed("refunded"))
      val darkMode     = Ticket(new ObjectId(), "Add a dark mode", Open, Some("lee@example.com"))
      List(loginBug, wrongInvoice, darkMode).foreach(tickets.insert)

      println(s"stored: ${tickets.raw.find(Filters.eq("_id", loginBug.id)).first().toJson}")
      println(s"by id: ${tickets.find(Filters.eq("_id", loginBug.id))}")
      println(s"closed: ${tickets.find(Filters.exists("status.Closed")).map(_.map(_.title))}")
      println(s"from kim: ${tickets.find(Filters.eq("customerEmail", "kim@example.com")).map(_.map(_.title))}")
      println(s"no email: ${tickets.find(Filters.eq("customerEmail", null)).map(_.map(_.title))}")
      println(s"kim commented: ${tickets.find(Filters.eq("comments.authorName", "kim")).map(_.map(_.title))}")
    }
}
