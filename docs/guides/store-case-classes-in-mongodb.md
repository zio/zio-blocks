---
id: store-case-classes-in-mongodb
title: "How to Store Case Classes in MongoDB"
description: "Derive a BSON codec from a Schema, store support tickets in MongoDB with the Java driver, and pick how sealed traits and options are stored."
keywords:
  - "MongoDB"
  - "BSON"
  - "Document Database"
  - "ObjectId"
  - "BsonCodec"
  - "BsonSchemaCodec"
---

In this guide, we store the tickets of a small help desk in MongoDB with the `schema-bson` module of ZIO Blocks Schema. By the end, you will have one codec, derived from a `Schema`, that turns a `Ticket` case class into a `BsonDocument` and back, uses MongoDB's own `ObjectId` as the `_id`, stores a sealed trait in the shape your queries need, and reads tickets that the official MongoDB Java driver finds.

We start from the ticket types and build up in this order:

- Derive the codec, turn a ticket into a BSON document and read it back.
- Use a MongoDB `ObjectId` as the document id.
- Choose how the `Status` sealed trait is stored.
- Store optional fields in a shape other MongoDB programs expect.
- Insert and query tickets with the MongoDB Java driver.
- Read the errors the codec reports for documents it can't decode, and decide what to do with extra fields.

## The Problem

MongoDB stores BSON documents, and the Java driver reads and writes `BsonDocument` or its own `Document` type, not your case classes. Without a codec, every type needs two hand-written functions, one that builds the document and one that reads it back, and a sealed trait needs a tag of your own design.

For example, consider a support ticket with a status and a list of comments. With the `org.bson` classes alone, the mapping looks like this:

```scala mdoc:compile-only
import org.bson._
import org.bson.types.ObjectId
import scala.jdk.CollectionConverters._

sealed trait Status
case object Open                              extends Status
final case class InProgress(assignee: String) extends Status
final case class Closed(resolution: String)   extends Status

final case class Comment(authorName: String, body: String)
final case class Ticket(id: ObjectId, title: String, status: Status, comments: List[Comment])

def toDocument(ticket: Ticket): BsonDocument = {
  val status = ticket.status match {
    case Open                 => new BsonDocument("type", new BsonString("Open"))
    case InProgress(assignee) =>
      new BsonDocument("type", new BsonString("InProgress")).append("assignee", new BsonString(assignee))
    case Closed(resolution) =>
      new BsonDocument("type", new BsonString("Closed")).append("resolution", new BsonString(resolution))
  }
  val comments = new BsonArray()
  ticket.comments.foreach { comment =>
    comments.add(
      new BsonDocument("authorName", new BsonString(comment.authorName))
        .append("body", new BsonString(comment.body))
    )
  }
  new BsonDocument("_id", new BsonObjectId(ticket.id))
    .append("title", new BsonString(ticket.title))
    .append("status", status)
    .append("comments", comments)
}

// Every field name is repeated here: a typo compiles, and fails only when a document is read
def fromDocument(document: BsonDocument): Ticket = {
  val status = document.getDocument("status")
  Ticket(
    id = document.getObjectId("_id").getValue,
    title = document.getString("title").getValue,
    status = status.getString("type").getValue match {
      case "Open"       => Open
      case "InProgress" => InProgress(status.getString("assignee").getValue)
      case "Closed"     => Closed(status.getString("resolution").getValue)
    },
    comments = document.getArray("comments").getValues.asScala.toList.map { value =>
      val comment = value.asDocument()
      Comment(comment.getString("authorName").getValue, comment.getString("body").getValue)
    }
  )
}
```

Add a field or a new status, and both functions must change in step. A missing field throws `BsonInvalidOperationException` (`Document does not contain key authorName`) from deep inside `fromDocument`, without saying which comment of which ticket, and an unknown status throws a `MatchError`. In this guide, the `Schema` of each type derives the codec instead, so the document shape follows the case class.

## Prerequisites

Add the BSON module and the MongoDB Java driver to your build. The module runs on the JVM only, and the code in this guide compiles on Scala 2.13 and Scala 3:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-schema-bson" % "@VERSION@"

// Scala 3.9 and newer (also needs scalacOptions += "-experimental")
libraryDependencies += "dev.zio" %% "zio-blocks-next-schema-bson" % "@VERSION@"

// The synchronous MongoDB Java driver
libraryDependencies += "org.mongodb" % "mongodb-driver-sync" % "5.13.0"
```

The BSON module depends on `org.mongodb:bson` 5.13.0, the BSON library that the driver itself uses, so `BsonDocument` and `ObjectId` are on the classpath without the driver. Driver 5.13.0 depends on the same `bson` version. We assume you can derive a `Schema` for a case class, as shown in [Your First Schema](./your-first-schema.md).

The sections that talk to MongoDB need a running server. If you have Docker, this command starts MongoDB 7 on the default port:

```bash
docker run --rm -d -p 27017:27017 --name mongo mongo:7
```

Every code block in this guide uses these imports:

```scala mdoc:silent
import zio.blocks.schema._
import zio.blocks.schema.bson._
import zio.blocks.schema.bson.BsonSchemaCodec.{Config, SumTypeHandling}
import org.bson.BsonDocument
import org.bson.types.ObjectId
```

## The Ticket Model

A help-desk ticket has a MongoDB id, a title, a status, an optional customer email and a list of comments. `Status` is a sealed trait whose cases carry different data:

```scala mdoc:silent
sealed trait Status
case object Open                              extends Status
final case class InProgress(assignee: String) extends Status
final case class Closed(resolution: String)   extends Status

object Status {
  implicit val schema: Schema[Status] = Schema.derived
}

final case class Comment(authorName: String, body: String)

object Comment {
  implicit val schema: Schema[Comment] = Schema.derived
}

final case class Ticket(
  @Modifier.rename("_id") id: ObjectId,
  title: String,
  status: Status,
  customerEmail: Option[String] = None,
  comments: List[Comment] = Nil
)

object Ticket {
  import ObjectIdSupport.objectIdSchema

  implicit val schema: Schema[Ticket] = Schema.derived
}
```

Two lines in `Ticket` are there for MongoDB. `Modifier.rename("_id")` stores the `id` field under `_id`, the field MongoDB uses as the primary key, while Scala code keeps the name `id`. The `ObjectIdSupport.objectIdSchema` import gives `Schema.derived` a schema for `org.bson.types.ObjectId`, which has none of its own; without it, derivation fails with `Cannot derive schema for 'org.bson.types.ObjectId'`. The defaults on `customerEmail` and `comments` matter when a stored document lacks those fields, as [Store Optional Fields](#store-optional-fields) shows.

The cases of `Status` are declared next to the trait, not inside its companion object. Both layouts work in a normal source file, but `Schema.derived` crashes the compiler when the trait is declared inside a class and its cases sit in its companion, a known bug in 0.0.56, and this page's code is compiled that way.

## Turn a Ticket into a BsonDocument and Back

`Schema#derive` with `BsonCodecDeriver` derives a `BsonCodec`, which holds a `BsonEncoder` and a `BsonDecoder`. `BsonEncoder#toBsonValue` turns a value into a `BsonValue`, and for a case class that value is a document:

```scala mdoc:silent
val ticketCodec: BsonCodec[Ticket] = Schema[Ticket].derive(BsonCodecDeriver)

val loginBug = Ticket(
  id = new ObjectId("6720f1a2b3c4d5e6f7a8b9c0"),
  title = "Login fails on Safari",
  status = InProgress("ana"),
  customerEmail = Some("kim@example.com"),
  comments = List(Comment("kim", "Only on Safari 17"))
)

val document: BsonDocument = ticketCodec.encoder.toBsonValue(loginBug).asDocument()
```

`BsonDocument#toJson` prints the document in MongoDB's Extended JSON, the form `mongosh` shows:

```scala mdoc
println(document.toJson)
```

Each case class field becomes a document field with the same name, `List` becomes an array, and nested case classes become nested documents. The `status` and `customerEmail` shapes are the module's defaults for sealed traits and for `Option`; the next sections change both.

To read the document back, `BsonDecoder#fromBsonValue` returns `Either[BsonDecoder.Error, Ticket]`:

```scala mdoc
println(ticketCodec.decoder.fromBsonValue(document))
println(ticketCodec.decoder.fromBsonValue(document) == Right(loginBug))
```

<details>
  <summary>Runnable version, with the shared model: <code>Step1EncodeDecodeExample.scala</code></summary>

```scala mdoc:embed:schema-examples/src/main/scala/storecaseclassesinmongodb/Model.scala:show-line-numbers
```

```scala mdoc:embed:schema-examples/src/main/scala/storecaseclassesinmongodb/Step1EncodeDecodeExample.scala:show-line-numbers
```

</details>

## Use MongoDB's ObjectId as the Document Id

Every MongoDB document has an `_id`, and most collections use an `ObjectId`: 12 bytes that start with a timestamp, generated without asking the server. With the `ObjectIdSupport` import, the codec stores an `ObjectId` field as BSON's own `ObjectId` type, not as its hex string:

```scala mdoc
println(document.get("_id").getBsonType)
```

That is the type the `_id` index and other MongoDB tools expect, and a document whose `_id` holds the hex string as a plain string does not decode:

```scala mdoc
println(ticketCodec.decoder.fromBsonValue(BsonDocument.parse(
  """{"_id": "6720f1a2b3c4d5e6f7a8b9c0", "title": "Login fails on Safari", "status": {"Open": {}}}"""
)))
```

We create the id in the application with `new ObjectId()` before inserting the ticket, so the `Ticket` value and the stored document carry the same id from the start. The Java driver adds a generated `_id` to a document that has none, but a `Ticket` always has an id, so the codec always writes one.

:::warning
Don't make the id an `Option[ObjectId]` so the driver can fill it in. In 0.0.56 an `Option` is stored as a sub-document (see [Store Optional Fields](#store-optional-fields)), so `None` becomes the id `{"None": {}}`, and the second such insert fails with `E11000 duplicate key error ... dup key: { _id: { None: {} } }`.
:::

You don't need `BsonSchemaCodec.Config#withNativeObjectId` for this: the `ObjectIdSupport` import already selects the native type, and in 0.0.56 the setting changes nothing.

<details>
  <summary>Runnable version, with setup from the previous section: <code>Step2ObjectIdExample.scala</code></summary>

```scala mdoc:embed:schema-examples/src/main/scala/storecaseclassesinmongodb/Step2ObjectIdExample.scala:show-line-numbers
```

</details>

## Choose How Sealed Traits Are Stored

A sealed trait needs a marker in the document that says which case it holds, and the shape of that marker decides how you query by case. `BsonSchemaCodec.bsonCodec` takes a `BsonSchemaCodec.Config`, and `BsonSchemaCodec.Config#withSumTypeHandling` picks one of three shapes. To compare them, we encode each `Status` case under each setting, inside a `status` field as it appears in a ticket:

```scala mdoc:silent
def storedAs(config: Config, status: Status): String =
  new BsonDocument("status", BsonSchemaCodec.bsonCodec(Schema[Status], config).encoder.toBsonValue(status)).toJson

val statuses = List[Status](Open, InProgress("ana"), Closed("refunded"))
```

Printing every case under each setting, plus one with lower-case case names, shows the shapes side by side:

```scala mdoc
println("WrapperWithClassNameField (the default):")
statuses.foreach(status => println("  " + storedAs(Config, status)))

println("DiscriminatorField(\"type\"):")
statuses.foreach(status => println("  " + storedAs(Config.withSumTypeHandling(SumTypeHandling.DiscriminatorField("type")), status)))

println("NoDiscriminator:")
statuses.foreach(status => println("  " + storedAs(Config.withSumTypeHandling(SumTypeHandling.NoDiscriminator), status)))

println("WrapperWithClassNameField with withClassNameMapping(_.toLowerCase):")
statuses.foreach(status => println("  " + storedAs(Config.withClassNameMapping(_.toLowerCase), status)))
```

`BsonSchemaCodec.Config#withClassNameMapping` changes the stored case names, here to lower case. The three shapes lead to different queries:

| Setting                      | Stored shape                             | Query for closed tickets                           |
| ---------------------------- | ---------------------------------------- | -------------------------------------------------- |
| `WrapperWithClassNameField`  | the case name wraps the case's fields    | `Filters.exists("status.Closed")`                  |
| `DiscriminatorField("type")` | a `type` field next to the case's fields | `Filters.eq("status.type", "Closed")`              |
| `NoDiscriminator`            | the case's fields only                   | none by case name; a case object is a plain string |

`NoDiscriminator` stores no case name, so the decoder tries the cases in order and keeps the first one that decodes; two cases with the same fields can't be told apart.

A `Config` applies to every sealed trait in the codec, and in 0.0.56 that includes `Option`, so a whole-codec `DiscriminatorField` setting also changes how `customerEmail` is stored:

```scala mdoc
println(
  BsonSchemaCodec
    .bsonCodec(Schema[Ticket], Config.withSumTypeHandling(SumTypeHandling.DiscriminatorField("type")))
    .encoder
    .toBsonValue(loginBug.copy(customerEmail = None))
    .asDocument()
    .toJson
)
```

To change one sealed trait only, put modifiers on the type itself instead. `Modifier.discriminator` stores a discriminator field for this trait only, and `Modifier.caseNaming` renames its cases; on a case class, `Modifier.fieldNaming` renames every field. Here they are on separate copies of `Status` and `Comment`, declared inside an object so the names don't clash with the model:

```scala mdoc:silent
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

val annotatedStatusCodec = Schema[annotated.Status].derive(BsonCodecDeriver)
```

The annotated `Status` stores a `type` field with snake_case case names, and the annotated `Comment` stores snake_case field names:

```scala mdoc
println(annotatedStatusCodec.encoder.toBsonValue(annotated.InProgress("ana")).asDocument().toJson)
println(annotatedStatusCodec.encoder.toBsonValue(annotated.Open).asDocument().toJson)

println(Schema[annotated.Comment].derive(BsonCodecDeriver).encoder.toBsonValue(annotated.Comment("kim", "Thanks")).asDocument().toJson)
```

A modifier on a type wins over the `Config` for that type. The same modifiers also change JSON and the other formats derived from the schema; [How to Customize JSON Encoding](./customize-json-encoding.md) covers them in detail. The rest of this guide keeps the default wrapper shape for `Status`.

<details>
  <summary>Runnable version: <code>Step3SumTypesExample.scala</code></summary>

```scala mdoc:embed:schema-examples/src/main/scala/storecaseclassesinmongodb/Step3SumTypesExample.scala:show-line-numbers
```

</details>

## Store Optional Fields

In 0.0.56 the codec stores an `Option` like any other sealed trait: `Some` and `None` are its cases, so `customerEmail` becomes a small document:

```scala mdoc
def storedEmail(codec: BsonCodec[Ticket], ticket: Ticket): String =
  new BsonDocument("customerEmail", codec.encoder.toBsonValue(ticket).asDocument().get("customerEmail")).toJson

println(storedEmail(ticketCodec, loginBug))
println(storedEmail(ticketCodec, loginBug.copy(customerEmail = None)))
```

The codec reads its own documents back, and the `= None` default on `customerEmail` lets it read a document that has no `customerEmail` field. A document written by another program, which stores a missing email as `null` and a present one as a plain string, fails to decode:

```scala mdoc
def decodeEmail(json: String) = ticketCodec.decoder.fromBsonValue(BsonDocument.parse(json)).map(_.customerEmail)

println(decodeEmail("""{"_id": {"$oid": "6720f1a2b3c4d5e6f7a8b9c0"}, "title": "Login fails on Safari", "status": {"Open": {}}}"""))
println(decodeEmail("""{"_id": {"$oid": "6720f1a2b3c4d5e6f7a8b9c0"}, "title": "Login fails on Safari", "status": {"Open": {}}, "customerEmail": null}"""))
println(decodeEmail("""{"_id": {"$oid": "6720f1a2b3c4d5e6f7a8b9c0"}, "title": "Login fails on Safari", "status": {"Open": {}}, "customerEmail": "kim@example.com"}"""))
```

:::warning
Storing `Option` as `{"Some": {"value": ...}}` and `{"None": {}}` is a known bug in 0.0.56. It also means a query for tickets without an email must match `{"None": {}}` instead of `null`. Without the `= None` default, a document that lacks the field fails with `Missing required field: customerEmail`.
:::

Until that is fixed, we give `Option[String]` a codec of our own that stores `None` as BSON `null` and `Some` as the plain string. A `BsonCodec` is a pair of an encoder and a decoder; each has a method for the streaming `BsonWriter`/`BsonReader` path and one for whole `BsonValue`s:

```scala mdoc:silent
import org.bson.{BsonNull, BsonReader, BsonString, BsonType, BsonValue, BsonWriter}
import zio.blocks.typeid.TypeId

val nullableString: BsonCodec[Option[String]] = BsonCodec(
  new BsonEncoder[Option[String]] {
    def encode(writer: BsonWriter, value: Option[String], ctx: BsonEncoder.EncoderContext): Unit =
      value match {
        case Some(text) => writer.writeString(text)
        case None       => writer.writeNull()
      }

    def toBsonValue(value: Option[String]): BsonValue =
      value.fold[BsonValue](BsonNull.VALUE)(text => new BsonString(text))
  },
  new BsonDecoder[Option[String]] {
    def decodeUnsafe(
      reader: BsonReader,
      trace: List[BsonTrace],
      ctx: BsonDecoder.BsonDecoderContext
    ): Option[String] =
      reader.getCurrentBsonType match {
        case BsonType.NULL   => reader.readNull(); None
        case BsonType.STRING => Some(reader.readString())
        case other           => throw BsonDecoder.Error(trace, s"Expected STRING or NULL but got $other")
      }

    def fromBsonValueUnsafe(
      value: BsonValue,
      trace: List[BsonTrace],
      ctx: BsonDecoder.BsonDecoderContext
    ): Option[String] =
      if (value.isNull) None
      else if (value.isString) Some(value.asString().getValue)
      else throw BsonDecoder.Error(trace, s"Expected STRING or NULL but got ${value.getBsonType}")
  }
)
```

`Schema#deriving` starts a derivation we can adjust, and `DerivationBuilder#instance` with a `TypeId` uses our codec for every `Option[String]` in the ticket:

```scala mdoc:silent
val nullableTicketCodec: BsonCodec[Ticket] =
  Schema[Ticket]
    .deriving(BsonCodecDeriver)
    .instance(TypeId.of[Option[String]], nullableString)
    .derive
```

The ticket now stores the email the way other MongoDB programs do, and reads their documents:

```scala mdoc
println(storedEmail(nullableTicketCodec, loginBug))
println(storedEmail(nullableTicketCodec, loginBug.copy(customerEmail = None)))

def decodeEmailNullable(json: String) = nullableTicketCodec.decoder.fromBsonValue(BsonDocument.parse(json)).map(_.customerEmail)

println(decodeEmailNullable("""{"_id": {"$oid": "6720f1a2b3c4d5e6f7a8b9c0"}, "title": "Login fails on Safari", "status": {"Open": {}}, "customerEmail": null}"""))
println(decodeEmailNullable("""{"_id": {"$oid": "6720f1a2b3c4d5e6f7a8b9c0"}, "title": "Login fails on Safari", "status": {"Open": {}}, "customerEmail": "kim@example.com"}"""))
```

The override covers `Option[String]` only; another optional type, such as `Option[Int]`, needs a codec of its own. A missing field still decodes through the `= None` default, because the codec reads a missing field from the field's default value. The rest of this guide stores tickets with `nullableTicketCodec`. [How to Override the Codec for One Type](./override-codec-for-one-type.md) shows more ways to replace part of a derived codec.

<details>
  <summary>Runnable version, with the codec in a shared file: <code>Step4OptionalFieldsExample.scala</code></summary>

```scala mdoc:embed:schema-examples/src/main/scala/storecaseclassesinmongodb/TicketCodec.scala:show-line-numbers
```

```scala mdoc:embed:schema-examples/src/main/scala/storecaseclassesinmongodb/Step4OptionalFieldsExample.scala:show-line-numbers
```

</details>

## Insert and Query with the MongoDB Java Driver

ZIO Blocks has no MongoDB `CodecProvider` or `CodecRegistry`, the driver's own way to plug in codecs. We don't need one: the driver reads and writes `BsonDocument` natively, so we open the collection as a `MongoCollection[BsonDocument]` and convert at the boundary. This glue is our own code, not a ZIO Blocks API, and it is short enough to copy:

```scala mdoc:embed:schema-examples/src/main/scala/storecaseclassesinmongodb/MongoGlue.scala:show-line-numbers
```

`TypedCollection#insert` encodes a value and calls `MongoCollection#insertOne`. `TypedCollection#find` runs a driver filter and decodes every document it returns and, if any fail to decode, returns the error of the first one. `Mongo.withDatabase` opens a client for one block of work and closes it afterwards. Its default connection string sets `serverSelectionTimeoutMS=2000`, so with no server running the program stops after two seconds with a one-line message instead of waiting 30 seconds, the driver's default. Set `MONGODB_URI` to use another server.

The example below inserts three tickets and queries them with the driver's `Filters`. Each filter names fields as the codec stores them: `status.Closed` comes from the wrapper shape of `Status`, `customerEmail` is a plain string thanks to `nullableTicketCodec`, and `comments.authorName` matches any element of the `comments` array:

```scala mdoc:embed:schema-examples/src/main/scala/storecaseclassesinmongodb/Step5InsertAndQueryExample.scala:show-line-numbers
```

Against `mongo:7`, it prints the following (the ids differ on each run):

```text
stored: {"_id": {"$oid": "6ae0dfc103971ce61e94881e"}, "title": "Login fails on Safari", "status": {"InProgress": {"assignee": "ana"}}, "customerEmail": "kim@example.com", "comments": [{"authorName": "kim", "body": "Only on Safari 17"}]}
by id: Right(List(Ticket(6ae0dfc103971ce61e94881e,Login fails on Safari,InProgress(ana),Some(kim@example.com),List(Comment(kim,Only on Safari 17)))))
closed: Right(List(Invoice total is wrong))
from kim: Right(List(Login fails on Safari))
no email: Right(List(Invoice total is wrong))
kim commented: Right(List(Login fails on Safari))
```

`Filters.eq("customerEmail", null)` matches documents whose field is `null` or missing, which is how MongoDB itself treats `null` in a query. The driver also prints an SLF4J warning to standard error when no SLF4J logging backend is on the classpath; add one to your build to route the driver's logs.

## Handle Decode Errors and Extra Fields

A collection is shared over time: older versions of your program, scripts and other services write to it, so a stored document can differ from the case class. `BsonDecoder#fromBsonValue` returns a `BsonDecoder.Error` for such a document, and `BsonDecoder.Error#render` gives the path to the bad value followed by the message. The path is empty when the problem is in the top-level document:

```scala mdoc
def readTicket(json: String): Unit =
  ticketCodec.decoder.fromBsonValue(BsonDocument.parse(json)) match {
    case Right(ticket) => println(s"decoded: ${ticket.title}")
    case Left(error)   => println(s"failed: ${error.render}")
  }

readTicket("""{"_id": {"$oid": "6720f1a2b3c4d5e6f7a8b9c0"}, "status": {"Open": {}}}""")
readTicket("""{"_id": {"$oid": "6720f1a2b3c4d5e6f7a8b9c0"}, "title": 42, "status": {"Open": {}}}""")
readTicket("""{"_id": {"$oid": "6720f1a2b3c4d5e6f7a8b9c0"}, "title": "Export to CSV", "status": {"Open": {}}, "comments": [{"author": "sam", "body": "+1"}]}""")
readTicket("""{"_id": {"$oid": "6720f1a2b3c4d5e6f7a8b9c0"}, "title": "Export to CSV", "status": {"Pending": {}}}""")
```

`BsonDecoder.Error#message` holds the message without the path, and `BsonDecoder.Error#toString` combines both as `Path: <path>, error: <message>`.

A field the case class doesn't have is skipped by default, so a document that another program extended still decodes. Here is such a document, with a `priority` field that `Ticket` lacks:

```scala mdoc:silent
val withPriority =
  """{"_id": {"$oid": "6720f1a2b3c4d5e6f7a8b9c0"}, "title": "Export to CSV", "status": {"Open": {}}, "priority": 2}"""
```

The default codec decodes it and ignores `priority`:

```scala mdoc
readTicket(withPriority)
```

To reject such documents instead, `BsonSchemaCodec.Config#withIgnoreExtraFields(false)` makes every record in the codec strict:

```scala mdoc
println(BsonSchemaCodec.bsonCodec(Schema[Ticket], Config.withIgnoreExtraFields(false)).decoder.fromBsonValue(BsonDocument.parse(withPriority)).left.map(_.render))
```

`Modifier.noExtraFields` does the same for one case class, whatever the `Config` says:

```scala mdoc
object strict {
  @Modifier.noExtraFields()
  final case class Comment(authorName: String, body: String)

  object Comment {
    implicit val schema: Schema[Comment] = Schema.derived
  }
}

println(
  Schema[strict.Comment]
    .derive(BsonCodecDeriver)
    .decoder
    .fromBsonValue(BsonDocument.parse("""{"authorName": "sam", "body": "+1", "likes": 3}"""))
    .left
    .map(_.render)
)
```

Ignoring extra fields is the safer default for a shared collection: a newer program can add a field without breaking older readers. Strict decoding suits collections that only your program writes, where an unknown field points to a bug.

<details>
  <summary>Runnable version: <code>Step6DecodeErrorsExample.scala</code></summary>

```scala mdoc:embed:schema-examples/src/main/scala/storecaseclassesinmongodb/Step6DecodeErrorsExample.scala:show-line-numbers
```

</details>

## Putting It Together

The complete example stores the three tickets from the insert section with `nullableTicketCodec` and queries them. It differs from the sections in one way: it also inserts, through the raw collection, a ticket that another program wrote with an extra `priority` field and a comment whose author field is named `author`. The extra field is skipped, but the misnamed comment field makes the query for open tickets fail with the path to the bad value:

```scala mdoc:embed:schema-examples/src/main/scala/storecaseclassesinmongodb/CompleteExample.scala:show-line-numbers
```

Against `mongo:7`, it prints:

```text
closed: Right(List(Invoice total is wrong))
from kim: Right(List(Login fails on Safari))
open: cannot read a stored ticket, .comments[0]: Missing required field: authorName
```

## Running the Examples

All runnable code from this guide is in [`schema-examples/src/main/scala/storecaseclassesinmongodb/`](https://github.com/zio/zio-blocks/tree/main/schema-examples/src/main/scala/storecaseclassesinmongodb). Run any file from that folder with `scala-cli run <File>.scala`, or from `schema-examples` with `sbt "runMain storecaseclassesinmongodb.<ObjectName>"`. `Step5InsertAndQueryExample` and `CompleteExample` need the MongoDB server from [Prerequisites](#prerequisites).

## Going Further

- The [BSON codec reference](../reference/schema/built-in-codecs/bson.md) covers every `BsonSchemaCodec.Config` setting, the type mappings and the `BsonEncoder` and `BsonDecoder` traits.
- [One Type, Many Formats](./one-type-many-formats.md) derives JSON, YAML, Avro and other codecs from the same kind of `Schema`, so the ticket model can also be an HTTP payload.
- [How to Encode and Decode Records with Avro](./encode-and-decode-records-with-avro.md) applies the same approach to Avro records for Kafka and files.
- [How to Customize JSON Encoding](./customize-json-encoding.md) explains the `Modifier` annotations this guide uses on `Status` and `Comment`.
