---
id: encode-and-decode-records-with-avro
title: "How to Encode and Decode Records with Avro"
description: "Derive an Avro codec for order events, write the .avsc schema, encode and decode records and streams, and read the bytes with Apache Avro."
keywords:
  - "Avro"
  - "Avro Schema"
  - "Binary Serialization"
  - "Interoperability"
  - "AvroCodec"
  - "AvroFormat"
---

In this guide, we encode order events as Avro records with `AvroFormat` from ZIO Blocks Schema. By the end, you will have one codec, derived from a sealed trait, that writes the `.avsc` schema file another team needs, turns each event into compact bytes and back, writes and reads a stream of events, and produces bytes that Apache Avro's own Java library reads. A typical place for these bytes is a Kafka message body; the same bytes work in a file, a queue or an HTTP body, so nothing in this guide depends on a broker.

We start from the event types and build up in this order:

- Derive the codec and write the `.avsc` schema file.
- Encode and decode one event, then a stream of events.
- See what `AvroCodec#decode` does with bad input.
- Check how each Scala type maps to Avro, which a reader in another language must know.
- Read and write the same bytes with Apache Avro.
- Learn what the module leaves to you: schema evolution, mismatched schemas, container files and registry framing.

## The Problem

Avro bytes hold only values. The writer and the reader must agree on a schema, a JSON document usually kept in an `.avsc` file, that says which field each value belongs to. When you write that schema by hand, it describes your case class a second time, and nothing keeps the two in step.

For example, consider the `OrderShipped` event. With Apache Avro alone, you write the schema as a JSON string and copy every field into a generic record by name:

```scala mdoc:compile-only
// The schema repeats the case class by hand
val schemaJson =
  """{"type":"record","name":"OrderShipped","fields":[
    |  {"name":"orderId","type":"long"},
    |  {"name":"shippedOn","type":{"type":"record","name":"LocalDate","fields":[
    |    {"name":"year","type":"int"},{"name":"month","type":"int"},{"name":"day","type":"int"}]}},
    |  {"name":"carrier","type":"string"}]}""".stripMargin
val schema = new org.apache.avro.Schema.Parser().parse(schemaJson)

// Every field is copied by name: a misspelled name compiles, and fails only when this line runs
val shippedOn = new org.apache.avro.generic.GenericData.Record(schema.getField("shippedOn").schema())
shippedOn.put("year", 2024)
shippedOn.put("month", 3)
shippedOn.put("day", 9)

val record = new org.apache.avro.generic.GenericData.Record(schema)
record.put("orderId", 7L)
record.put("shippedOn", shippedOn)
record.put("carrier", "DHL")

// The writer and the encoder are set up by hand too
val out     = new java.io.ByteArrayOutputStream()
val encoder = org.apache.avro.io.EncoderFactory.get.binaryEncoder(out, null)
new org.apache.avro.generic.GenericDatumWriter[AnyRef](schema).write(record, encoder)
encoder.flush()
```

Add a field to the case class and you must remember to change the JSON, the `GenericData.Record#put` calls and the reading code in every service that consumes the event. A mistake shows up as a decoding failure in another service, often long after the change. In this guide, the case class is the single source: its `Schema` derives the Avro schema, the encoder and the decoder, so they cannot drift apart.

## Prerequisites

Add the Avro module to your build. It runs on the JVM only; there is no Scala.js artifact:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-schema-avro" % "@VERSION@"

// Scala 3.9 and newer (also needs scalacOptions += "-experimental")
libraryDependencies += "dev.zio" %% "zio-blocks-next-schema-avro" % "@VERSION@"
```

The module depends on Apache Avro (`org.apache.avro:avro`), so the Apache Avro classes used later in this guide are already on the classpath. We assume you can derive a `Schema` for a case class, as shown in [Your First Schema](./your-first-schema.md).

Every code block in this guide uses these imports:

```scala mdoc:silent
import zio.blocks.schema._
import zio.blocks.schema.avro._
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.time.{Instant, LocalDate}
```

## The Order Events

The running example is the life of an order: placed, shipped or cancelled. `OrderEvent` is a sealed trait and each event is a case class, so one codec covers all three:

```scala mdoc:silent
sealed trait OrderEvent

object OrderEvent {
  implicit val schema: Schema[OrderEvent] = Schema.derived
}

case class OrderPlaced(
  orderId: Long,
  customer: String,
  amount: BigDecimal,
  placedAt: Instant,
  note: Option[String],
  items: List[LineItem]
) extends OrderEvent

case class OrderShipped(orderId: Long, shippedOn: LocalDate, carrier: String) extends OrderEvent

case class OrderCancelled(orderId: Long, reason: String) extends OrderEvent

case class LineItem(sku: String, quantity: Int, unitPrice: BigDecimal)
```

The fields cover what readers of Avro data usually ask about: ids, money, timestamps, an optional note and a list of line items.

<details>
  <summary>The same model as a file, shared by every example: <code>Model.scala</code></summary>

```scala mdoc:embed:schema-examples/src/main/scala/avrorecords/Model.scala:show-line-numbers
```

</details>

## Write the .avsc Schema File

An Avro reader needs the schema that the writer encodes with. Derive the codec once from the schema of the sealed trait; the result is an `AvroCodec[OrderEvent]`, which carries that schema:

```scala mdoc:silent
val codec = OrderEvent.schema.derive(AvroFormat)
```

`AvroCodec#avroSchema` returns the schema as an Apache Avro `Schema`, and Apache Avro's `SchemaFormatter` turns it into the JSON text of an `.avsc` file. To print the schema of the smallest event, `OrderCancelled`:

```scala mdoc
import org.apache.avro.SchemaFormatter

println(SchemaFormatter.format("json/pretty", Schema.derived[OrderCancelled].derive(AvroFormat).avroSchema))
```

The namespace above is `repl.MdocSession.MdocApp` because the documentation build compiles this page inside its own object. In your project, the namespace is the package of the case class (`avrorecords` in the example files), so moving a case class to another package changes the schema you share.

For the whole sealed trait, `SchemaFormatter.format("json/pretty", codec.avroSchema)` prints a union: a JSON array with one record per event, in the order the events are declared. Write that text to `order-event.avsc` with `java.nio.file.Files#writeString` and hand the file to the team that reads your events; the example file for this section does exactly that. `SchemaFormatter` is the printer since Apache Avro 1.12; the older `Schema#toString(true)` is deprecated.

## Encode and Decode One Event

`AvroCodec#encode` turns a value into a byte array, and `AvroCodec#decode` turns the bytes back into a value. To encode a placed order:

```scala mdoc:silent
val event: OrderEvent = OrderPlaced(
  orderId = 42L,
  customer = "Ada",
  amount = BigDecimal("19.99"),
  placedAt = Instant.ofEpochSecond(1700000000L, 123000000),
  note = Some("gift"),
  items = List(LineItem("sku-1", 2, BigDecimal("9.995")))
)

val bytes = codec.encode(event)
```

To print the bytes in hexadecimal and decode them back:

```scala mdoc
println(s"${bytes.length} bytes: ${bytes.map(b => f"$b%02x").mkString(" ")}")

println(codec.decode(bytes))
```

The bytes hold values only; the field names live in the schema. The first byte, `00`, is the index of the `OrderPlaced` branch of the union. Then come the order id `54` (42 as a zig-zag variable-length number) and the customer `06 41 64 61` (a length of 3, then the letters `Ada`). `AvroCodec#decode` returns an `Either[SchemaError, OrderEvent]`, so a failure is a value to handle, not an exception.

<details>
  <summary>Runnable version, with the codec from “Write the .avsc Schema File”: <code>Step2EncodeDecodeExample.scala</code></summary>

```scala mdoc:embed:schema-examples/src/main/scala/avrorecords/Step2EncodeDecodeExample.scala:show-line-numbers
```

</details>

## Write Several Events to One Stream

`AvroCodec#encode(value, OutputStream)` writes one record to any `OutputStream`, such as a file, a socket or a response body. To write three shipped orders to one buffer:

```scala mdoc:silent
val shipped: List[OrderEvent] = List(
  OrderShipped(1L, LocalDate.of(2024, 1, 1), "DHL"),
  OrderShipped(2L, LocalDate.of(2024, 1, 2), "UPS"),
  OrderShipped(3L, LocalDate.of(2024, 1, 3), "FedEx")
)

val buffer = new ByteArrayOutputStream()

shipped.foreach(e => codec.encode(e, buffer))

val input = new ByteArrayInputStream(buffer.toByteArray)
```

`AvroCodec#decode(InputStream)` reads exactly one record and leaves the rest of the stream unread, so each call continues where the previous one stopped. To read the three events back:

```scala mdoc
println(s"stream holds ${buffer.size()} bytes")

shipped.foreach(_ => println(codec.decode(input)))

println(s"bytes left in the stream: ${input.available()}")
```

The 32 bytes are the three records back to back, with no length and no separator between them. The stream carries no count and no end marker either, so the reader must know when to stop; here we read as many events as we wrote.

<details>
  <summary>Runnable version, with the codec from “Write the .avsc Schema File”: <code>Step3StreamEventsExample.scala</code></summary>

```scala mdoc:embed:schema-examples/src/main/scala/avrorecords/Step3StreamEventsExample.scala:show-line-numbers
```

</details>

## Handle Bad Input

`AvroCodec#decode` reports malformed bytes as a `SchemaError` that names the path of the field it was reading. When the bytes end too early, the error points at the field where the input ran out:

```scala mdoc
println(codec.decode(bytes.take(10)))
```

Extra bytes after a complete record are not an error. `AvroCodec#decode` returns the record and ignores the rest:

```scala mdoc
println(codec.decode(bytes ++ Array[Byte](1, 2, 3)))
```

:::warning
Two records in one byte array decode as the first record, with no error. Give every message its own byte array, or frame the messages yourself, for example with a length prefix.
:::

A collection whose length prefix is impossibly large fails before any element is read. The limit is `AvroCodec.maxCollectionSize`, 2147483639 elements. To build such a prefix with Apache Avro's encoder and decode it as a list:

```scala mdoc:silent
import org.apache.avro.io.EncoderFactory

val hugeList = new ByteArrayOutputStream()
val prefixEncoder = EncoderFactory.get.binaryEncoder(hugeList, null)
prefixEncoder.writeLong(AvroCodec.maxCollectionSize.toLong + 1L)
prefixEncoder.writeLong(0L)
prefixEncoder.flush()
```

The decoder reads the length prefix and stops there:

```scala mdoc
println(Schema[List[Int]].derive(AvroFormat).decode(hugeList.toByteArray))
```

<details>
  <summary>Runnable version, with the event and bytes from the earlier sections: <code>Step4BadInputExample.scala</code></summary>

```scala mdoc:embed:schema-examples/src/main/scala/avrorecords/Step4BadInputExample.scala:show-line-numbers
```

</details>

## Check How Scala Types Map to Avro

A reader in Java, Python or another Avro library works from the `.avsc` file, so it sees each Scala type as the Avro type that the codec derives. To print that Avro type for the common Scala types, we define a small helper and a few sample types, a sealed trait of case objects, a one-field case class, and an id built with `Schema#transform`:

```scala mdoc:silent
import zio.blocks.chunk.Chunk
import java.util.UUID

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
```

To print the Avro type of each one:

```scala mdoc
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
```

The table sums up the output and what a reader in another language should know about each type:

| Scala type                            | Avro type                                                 | What a reader in another language sees                                                        |
| ------------------------------------- | --------------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| `Int`, `Short`, `Byte`                | `int`                                                     | A 32-bit number; `Short` and `Byte` are widened                                               |
| `Long`                                | `long`                                                    |                                                                                               |
| `Float`, `Double`                     | `float`, `double`                                         |                                                                                               |
| `Boolean`                             | `boolean`                                                 |                                                                                               |
| `String`                              | `string`                                                  |                                                                                               |
| `Char`                                | `int`                                                     | The character's code, not a one-letter string                                                 |
| `Unit`                                | `null`                                                    |                                                                                               |
| `BigInt`                              | `bytes`                                                   | Two's-complement big-endian bytes                                                             |
| `BigDecimal`                          | record `scala.BigDecimal`                                 | `mantissa` bytes plus `scale`, `precision` and `roundingMode`; not the `decimal` logical type |
| `Array[Byte]`, `Chunk[Byte]`          | array of `int`                                            | One number per byte, not `bytes`                                                              |
| `List[A]`                             | array of `A`                                              |                                                                                               |
| `Map[String, A]`                      | map of `A`                                                |                                                                                               |
| `Instant`                             | record `java.time.Instant`                                | `epochSecond` and `nano`; not the `timestamp-millis` logical type                             |
| `LocalDate`                           | record `java.time.LocalDate`                              | `year`, `month` and `day`; not the `date` logical type                                        |
| `UUID`                                | `fixed` of 16 bytes named `java.util.UUID`                | Not the `uuid` logical type                                                                   |
| `Option[A]`                           | union of records `scala.None` and `scala.Some`            | Not `["null", A]`; `None` is branch 0 and `Some` holds `value`                                |
| `Either[A, B]`                        | union of records `scala.util.Left` and `scala.util.Right` | Each side holds `value`                                                                       |
| Sealed trait                          | union of one record per case, in declaration order        | A case object is a record with no fields                                                      |
| Case class                            | record named after the class, namespace from its package  | A one-field class such as `CustomerId` stays a record                                         |
| Newtype built with `Schema#transform` | the wrapped type                                          | `Sku` is a plain `string`                                                                     |

Four rows surprise readers in other languages. `Option` is a union of two records, not the `["null", T]` union that most Avro libraries write for an optional field, so a reader that expects `null` for a missing value does not find one. `Array[Byte]` is an array of `int` rather than `bytes`, so a byte payload comes out as a list of numbers. `BigDecimal`, `Instant` and `LocalDate` are records and `UUID` is a 16-byte `fixed`, not the `decimal`, `timestamp-millis`, `date` and `uuid` logical types. And `Char` is a number, not a one-letter string. zio-blocks does not write those other forms, so if another team expects them, agree on the layout with them using the `.avsc` file.

<details>
  <summary>Runnable version, as one program: <code>Step5TypeMappingExample.scala</code></summary>

```scala mdoc:embed:schema-examples/src/main/scala/avrorecords/Step5TypeMappingExample.scala:show-line-numbers
```

</details>

## Read the Bytes with Apache Avro

The proof that the bytes are standard Avro is to read them with another Avro library. Apache Avro's `GenericDatumReader` reads them with the schema that zio-blocks emits, parsed by Apache Avro's own parser:

```scala mdoc:silent
import org.apache.avro.Schema.Parser
import org.apache.avro.generic.{GenericData, GenericDatumReader, GenericDatumWriter}
import org.apache.avro.io.DecoderFactory

val avroSchema = new Parser().parse(codec.avroSchema.toString)

val reader = new GenericDatumReader[AnyRef](avroSchema)

def readWithApacheAvro(bytes: Array[Byte]): AnyRef =
  reader.read(null, DecoderFactory.get.binaryDecoder(bytes, null))
```

To read a shipped order and the placed order from the earlier sections:

```scala mdoc
println(readWithApacheAvro(codec.encode(shipped.head)))

println(readWithApacheAvro(bytes))
```

Apache Avro returns a `GenericRecord` for each event. The placed order shows how the types from the previous section look to a Java reader: `amount` is a record of raw `mantissa` bytes, `scale`, `precision` and `roundingMode`, `placedAt` is a record of seconds and nanoseconds, and `note` is the `Some` record with its `value`.

<details>
  <summary>Runnable version, with the codec and events from the earlier sections: <code>Step6ReadWithApacheAvroExample.scala</code></summary>

```scala mdoc:embed:schema-examples/src/main/scala/avrorecords/Step6ReadWithApacheAvroExample.scala:show-line-numbers
```

</details>

## Write Bytes with Apache Avro

The other direction is to write with Apache Avro and read with zio-blocks. The second branch of the union is the `OrderShipped` record, so we fill a generic record of that branch and write it with `GenericDatumWriter`:

```scala mdoc:silent
val shippedSchema = avroSchema.getTypes.get(1)

val shippedOn = new GenericData.Record(shippedSchema.getField("shippedOn").schema())
shippedOn.put("year", 2024)
shippedOn.put("month", 3)
shippedOn.put("day", 9)

val fromJava = new GenericData.Record(shippedSchema)
fromJava.put("orderId", 7L)
fromJava.put("shippedOn", shippedOn)
fromJava.put("carrier", "DHL")

val javaBytes = new ByteArrayOutputStream()
val javaEncoder = EncoderFactory.get.binaryEncoder(javaBytes, null)
new GenericDatumWriter[AnyRef](avroSchema).write(fromJava, javaEncoder)
javaEncoder.flush()
```

To decode the bytes that Apache Avro wrote:

```scala mdoc
println(codec.decode(javaBytes.toByteArray))
```

<details>
  <summary>Runnable version, with the codec and the parsed schema from the earlier sections: <code>Step7WriteWithApacheAvroExample.scala</code></summary>

```scala mdoc:embed:schema-examples/src/main/scala/avrorecords/Step7WriteWithApacheAvroExample.scala:show-line-numbers
```

</details>

## What Avro Support Does Not Cover

The module encodes and decodes one value with one schema. Several things that Avro users expect from other libraries are left to you.

### Old and New Versions of a Type

There is no reader and writer schema resolution: the codec reads bytes with its own schema only, and a default value in the new type is not used to fill a field that the old bytes lack. To see both directions, we define two versions of an order, the second with a new field that has a default:

```scala mdoc:silent
case class OrderV1(orderId: Long, customer: String)

object OrderV1 {
  implicit val schema: Schema[OrderV1] = Schema.derived
}

case class OrderV2(orderId: Long, customer: String, priority: Int = 0)

object OrderV2 {
  implicit val schema: Schema[OrderV2] = Schema.derived
}

val v1Bytes = OrderV1.schema.derive(AvroFormat).encode(OrderV1(9L, "Bob"))
val v2Bytes = OrderV2.schema.derive(AvroFormat).encode(OrderV2(9L, "Bob", 3))
```

To read old bytes with the new type, and new bytes with the old type:

```scala mdoc
println(OrderV2.schema.derive(AvroFormat).decode(v1Bytes))

println(OrderV1.schema.derive(AvroFormat).decode(v2Bytes))
```

Old bytes fail with an error at the new field, and new bytes read by the old type lose the new field without an error. To change a type safely, convert between versions in Scala with `Into`, `As` or `Migration`, as shown in [Evolve Your Data](./evolve-your-data.md).

### Bytes Written by a Different Type

Avro bytes carry no schema id and no fingerprint, so nothing stops a reader from decoding bytes with the wrong schema. To decode the bytes of a shipped order as an `OrderV1`:

```scala mdoc
println(OrderV1.schema.derive(AvroFormat).decode(codec.encode(shipped.head)))
```

The decode succeeds and returns nonsense. The union index becomes the `orderId`, the real order id becomes the length of `customer`, and `customer` holds one byte of the date; the remaining bytes are ignored, as in the previous section. Keep track of which schema wrote the bytes, for example with one topic or file per event type, a version field, or a schema registry.

### Container Files and Registry Framing

The codec writes the bytes of a single value. An Avro container file, the `.avro` file that Apache Avro's `DataFileWriter` writes with its schema embedded, is not supported, so a reader of a container file needs Apache Avro itself. There is also no registry framing: a schema registry stores your `.avsc` files under version ids and expects each message to start with the id of its schema, which you add yourself. [Build a Schema Registry](./build-a-schema-registry.md) shows how to store schemas as data in ZIO Blocks.

<details>
  <summary>Runnable version, with the codec and events from the earlier sections: <code>Step8AvroLimitsExample.scala</code></summary>

```scala mdoc:embed:schema-examples/src/main/scala/avrorecords/Step8AvroLimitsExample.scala:show-line-numbers
```

</details>

## Putting It Together

The complete program derives the codec, prints the `.avsc` schema of the whole union, writes a stream of three events to a buffer, reads them back, and checks the round trip. It differs from the sections in three ways: the stream mixes an `OrderPlaced`, an `OrderShipped` and an `OrderCancelled` event, where the stream section wrote only shipped orders; it prints the schema of the whole union rather than of `OrderCancelled`; and it ends by comparing the decoded events with the originals:

```scala mdoc:embed:schema-examples/src/main/scala/avrorecords/CompleteExample.scala
```

Running it prints the following; the `.avsc` text, 120 lines in full, is cut after its first field, where the `...` line stands:

```text
[ {
  "type" : "record",
  "name" : "OrderPlaced",
  "namespace" : "avrorecords",
  "fields" : [ {
    "name" : "orderId",
    "type" : "long"
...
stream holds 71 bytes
Right(OrderPlaced(42,Ada,19.99,2023-11-14T22:13:20.123Z,Some(gift),List(LineItem(sku-1,2,9.995))))
Right(OrderShipped(42,2024-01-03,DHL))
Right(OrderCancelled(43,customer request))
decoded events equal the originals: true
```

## Running the Examples

All runnable code from this guide is in [`schema-examples/src/main/scala/avrorecords/`](https://github.com/zio/zio-blocks/tree/main/schema-examples/src/main/scala/avrorecords). Run any file from that folder with `scala-cli run <File>.scala`, or from `schema-examples` with `sbt "runMain avrorecords.<ObjectName>"`.

## Going Further

- The [Avro codec reference](../reference/schema/built-in-codecs/avro.md) covers every method of `AvroCodec`, the error model and recursive types.
- [One Type, Many Formats](./one-type-many-formats.md) derives JSON, MessagePack, Avro and other codecs from one schema and compares their sizes.
- [Build a Schema Registry](./build-a-schema-registry.md) stores versioned event schemas as data, the usual next step once several services exchange Avro messages.
- [Evolve Your Data](./evolve-your-data.md) changes a type between versions with `Into`, `As` and `Migration`.
- The `AvroListOfRecordsBenchmark` in the repository's `benchmarks` module compares this codec with avro4s and zio-schema-avro.
