//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema-bson:0.0.56
//> using options -experimental
//> using file Model.scala
// Run from this folder:  scala-cli run Step3SumTypesExample.scala
// Or from schema-examples:  sbt "runMain storecaseclassesinmongodb.Step3SumTypesExample"

package storecaseclassesinmongodb

import org.bson.BsonDocument
import org.bson.types.ObjectId
import zio.blocks.schema._
import zio.blocks.schema.bson._
import zio.blocks.schema.bson.BsonSchemaCodec.{Config, SumTypeHandling}

object annotated {
  @Modifier.discriminator("type")
  @Modifier.caseNaming("snake_case")
  sealed trait Status
  case object Open                              extends Status
  final case class InProgress(assignee: String) extends Status

  object Status {
    implicit val schema: Schema[Status] = Schema.derived
  }

  @Modifier.fieldNaming("snake_case")
  final case class Comment(authorName: String, body: String)

  object Comment {
    implicit val schema: Schema[Comment] = Schema.derived
  }
}

/**
 * How to Store Case Classes in MongoDB — Choose How Sealed Traits Are Stored
 *
 * Encodes each Status case under each sum-type setting, shows that a codec-wide
 * setting also changes Option, and applies modifiers to single types.
 */
object Step3SumTypesExample {
  def storedAs(config: Config, status: Status): String =
    new BsonDocument("status", BsonSchemaCodec.bsonCodec(Schema[Status], config).encoder.toBsonValue(status)).toJson

  def main(args: Array[String]): Unit = {
    // Setup from "Turn a Ticket into a BsonDocument and Back": one ticket
    val loginBug = Ticket(
      id = new ObjectId("6720f1a2b3c4d5e6f7a8b9c0"),
      title = "Login fails on Safari",
      status = InProgress("ana"),
      customerEmail = Some("kim@example.com"),
      comments = List(Comment("kim", "Only on Safari 17"))
    )

    val statuses = List[Status](Open, InProgress("ana"), Closed("refunded"))

    println("WrapperWithClassNameField (the default):")
    statuses.foreach(status => println("  " + storedAs(Config, status)))

    println("DiscriminatorField(\"type\"):")
    statuses.foreach(status =>
      println("  " + storedAs(Config.withSumTypeHandling(SumTypeHandling.DiscriminatorField("type")), status))
    )

    println("NoDiscriminator:")
    statuses.foreach(status =>
      println("  " + storedAs(Config.withSumTypeHandling(SumTypeHandling.NoDiscriminator), status))
    )

    println("WrapperWithClassNameField with withClassNameMapping(_.toLowerCase):")
    statuses.foreach(status => println("  " + storedAs(Config.withClassNameMapping(_.toLowerCase), status)))

    println(
      BsonSchemaCodec
        .bsonCodec(Schema[Ticket], Config.withSumTypeHandling(SumTypeHandling.DiscriminatorField("type")))
        .encoder
        .toBsonValue(loginBug.copy(customerEmail = None))
        .asDocument()
        .toJson
    )

    val annotatedStatusCodec = Schema[annotated.Status].derive(BsonCodecDeriver)
    println(annotatedStatusCodec.encoder.toBsonValue(annotated.InProgress("ana")).asDocument().toJson)
    println(annotatedStatusCodec.encoder.toBsonValue(annotated.Open).asDocument().toJson)

    println(
      Schema[annotated.Comment]
        .derive(BsonCodecDeriver)
        .encoder
        .toBsonValue(annotated.Comment("kim", "Thanks"))
        .asDocument()
        .toJson
    )
  }
}
