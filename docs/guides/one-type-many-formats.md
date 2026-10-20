---
id: one-type-many-formats
title: "One Type, Many Formats: Export a Catalog as JSON, YAML, CSV, XML, MessagePack, TOON and Avro"
sidebar_label: "One Type, Many Formats"
description: "Derive JSON, YAML, XML, TOON, MessagePack, Avro and CSV codecs from one Schema and round-trip a book catalog through each."
keywords:
  - "Serialization Formats"
  - "Codec Derivation"
  - "Round Trip"
  - "Schema"
  - "Format"
  - "BinaryCodec"
---

Welcome! This tutorial is for Scala developers who have finished [Your First Schema](./your-first-schema.md) and can derive a `Schema` and round-trip a value through JSON. You don't need any prior experience with YAML, XML, CSV, TOON, MessagePack or Avro to follow along.

## 1. Introduction

By the end of this tutorial, you will understand:

- How one `Schema` value drives a codec for every format ZIO Blocks supports
- How to get a codec with `Schema#derive` and a format object, and how to encode and decode with it
- What the text formats (JSON, YAML, XML, TOON) and the binary formats (MessagePack, Avro) look like for the same data
- Why CSV accepts only flat records, and how a small flat row type solves that

We learn these concepts by exporting a small catalog of Scala books, one format at a time:

1. Model the catalog
2. Encode it to JSON the long way
3. Swap in YAML
4. Swap in XML
5. Swap in TOON
6. Swap in MessagePack
7. Swap in Avro
8. Flatten the catalog for CSV

We recommend reading from top to bottom — each section builds on the previous one.

Each format lives in its own module. To follow along, add the schema module and the six format modules to your `build.sbt`:

```scala
// Scala 2.13 and Scala 3.3 LTS
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-schema"             % "@VERSION@",
  "dev.zio" %% "zio-blocks-schema-yaml"        % "@VERSION@",
  "dev.zio" %% "zio-blocks-schema-xml"         % "@VERSION@",
  "dev.zio" %% "zio-blocks-schema-toon"        % "@VERSION@",
  "dev.zio" %% "zio-blocks-schema-messagepack" % "@VERSION@",
  "dev.zio" %% "zio-blocks-schema-avro"        % "@VERSION@",
  "dev.zio" %% "zio-blocks-schema-csv"         % "@VERSION@"
)

// Scala 3.7 and newer: the same modules with the zio-blocks-next- prefix
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-next-schema"             % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-schema-yaml"        % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-schema-xml"         % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-schema-toon"        % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-schema-messagepack" % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-schema-avro"        % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-schema-csv"         % "@VERSION@"
)
```

The JSON codec ships inside `zio-blocks-schema` itself, so it needs no extra module. The code in this tutorial is the same for every Scala version.

## 2. Background: One Schema, Many Codecs

In [Your First Schema](./your-first-schema.md), we derived a `Schema[Book]` and got a JSON codec for free. JSON is only one of many formats your data may need: YAML for configuration files, XML for older services, CSV for spreadsheets, MessagePack or Avro for compact messages between services, and TOON for compact prompts to large language models. In most libraries, each of those formats means another set of encoders and decoders to write and keep in sync with the case class.

ZIO Blocks treats a format as a plug-in. Each format module provides a format object — `YamlFormat`, `XmlFormat`, `AvroFormat` and so on — that knows how to turn any `Schema` into a codec for that format. The schema describes the shape of the data once; the format object decides how that shape is written. So adding a format to a program means adding its module, importing its format object, and writing one line that derives the codec. In this tutorial, we watch that one line produce seven very different outputs from the same catalog.

## 3. Model the Catalog

Let's start with the data. A `Book` has a title, an author, a publication year and a list of tags, and a `Catalog` holds a list of books. As in the first tutorial, each type gets a derived schema in its companion object:

```scala mdoc:silent
import zio.blocks.schema._

case class Book(title: String, author: String, year: Int, tags: List[String])

object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

case class Catalog(books: List[Book])

object Catalog {
  implicit val schema: Schema[Catalog] = Schema.derived
}
```

The code above:

- `Book` stores its authors as one `String` and its tags as a `List[String]`.
- `Catalog` wraps a `List[Book]`, so the whole export is one value with one top-level name.
- `Schema.derived` derives each schema at compile time; `Catalog.schema` finds `Book.schema` for the list's elements.

Next, we fill the catalog with three books about Scala:

```scala mdoc:silent
val catalog = Catalog(
  List(
    Book("Zionomicon", "John A. De Goes, Adam Fraser", 2024, List("zio", "effects")),
    Book("Programming in Scala", "Martin Odersky, Lex Spoon, Bill Venners", 2008, List("scala", "fundamentals")),
    Book("Functional Programming in Scala", "Paul Chiusano, Rúnar Bjarnason", 2014, List("scala", "fp"))
  )
)
```

The code above builds the one value we export in every section that follows. Notice that two of the author lists contain commas — that detail matters when we reach CSV.

## 4. Encode to JSON the Long Way

In the first tutorial, `toJsonString` and `fromJson` did all the work. Behind those shortcuts sits a codec — a value that can both encode and decode one type in one format. Let's get the JSON codec explicitly, because the same three steps work for every other format in this tutorial:

```scala mdoc:silent
import java.nio.charset.StandardCharsets.UTF_8
import zio.blocks.schema.json.JsonFormat

val jsonCodec = Catalog.schema.derive(JsonFormat)

val jsonBytes = jsonCodec.encode(catalog)
```

The code above:

- `import zio.blocks.schema.json.JsonFormat` brings the JSON format object into scope.
- `Catalog.schema.derive(JsonFormat)` walks the catalog's schema and builds a `JsonCodec[Catalog]`.
- `jsonCodec.encode(catalog)` writes the catalog and returns the result as an `Array[Byte]`.

The bytes are UTF-8 text, so we turn them into a `String` to read them, and then decode the same bytes back:

```scala mdoc
println(new String(jsonBytes, UTF_8))

jsonCodec.decode(jsonBytes) == Right(catalog)
```

The code above prints the JSON text and then checks the round trip. `JsonCodec#decode` returns an `Either[SchemaError, Catalog]`, just like `fromJson`, and `true` tells us that decoding gave back exactly the catalog we started with.

These are the three steps we repeat for every format: derive a codec from the schema, encode a value to bytes, decode the bytes back. The `toJsonString` shortcut from the first tutorial asks the schema for this same JSON codec and calls `JsonCodec#encodeToString` on it.

## 5. Swap In YAML

Now for the moment this tutorial is about. To export the catalog as YAML, we change only the format object. This step uses the `zio-blocks-schema-yaml` module:

```scala mdoc:silent
import zio.blocks.schema.yaml.YamlFormat

val yamlCodec = Catalog.schema.derive(YamlFormat)

val yamlBytes = yamlCodec.encode(catalog)
```

The code above is the JSON code with `JsonFormat` replaced by `YamlFormat`. We didn't write anything YAML-specific — `Catalog.schema` already says everything the YAML codec needs. Let's print the result and check the round trip:

```scala mdoc
println(new String(yamlBytes, UTF_8))

yamlCodec.decode(yamlBytes) == Right(catalog)
```

The code above prints the YAML and decodes it back. Notice how the same structure looks in YAML: the `books` field becomes a block sequence with one `-` entry per book, each book's fields become `key: value` lines, and the nested `tags` list becomes a sequence of its own.

## 6. Swap In XML

XML follows the same pattern. This step uses the `zio-blocks-schema-xml` module:

```scala mdoc:silent
import zio.blocks.schema.xml.XmlFormat

val xmlCodec = Catalog.schema.derive(XmlFormat)

val xmlBytes = xmlCodec.encode(catalog)
```

Let's print the XML and decode it back:

```scala mdoc
println(new String(xmlBytes, UTF_8))

xmlCodec.decode(xmlBytes) == Right(catalog)
```

The code above prints one compact line of XML. Every XML document needs a single root element, and the codec names it after the type, so the catalog becomes `<Catalog>`. Each field becomes a child element with the field's name, and each list element is wrapped in an `<item>` element — both for the books and for each book's tags.

:::tip
To print indented XML for people to read, pass a writer configuration: `xmlCodec.encodeToString(catalog, WriterConfig.pretty)` with `import zio.blocks.schema.xml.WriterConfig`.
:::

## 7. Swap In TOON

TOON (Token-Oriented Object Notation) is a text format designed to carry structured data to large language models in fewer tokens than JSON. This step uses the `zio-blocks-schema-toon` module:

```scala mdoc:silent
import zio.blocks.schema.toon.ToonFormat

val toonCodec = Catalog.schema.derive(ToonFormat)

val toonBytes = toonCodec.encode(catalog)
```

Let's print the TOON text and decode it back:

```scala mdoc
println(new String(toonBytes, UTF_8))

toonCodec.decode(toonBytes) == Right(catalog)
```

The code above prints the catalog as TOON. Notice what TOON leaves out compared with JSON: there are no braces and no quotes around keys or plain strings. Each list states its length up front — `books[3]` and `tags[2]` — and a list of plain values such as `tags` fits on one line as `zio,effects`.

## 8. Swap In MessagePack

The formats so far produce text. MessagePack is a binary format: it stores the same structure as JSON, but with compact type tags instead of punctuation. This step uses the `zio-blocks-schema-messagepack` module:

```scala mdoc:silent
import zio.blocks.schema.msgpack.MessagePackFormat

val msgpackCodec = Catalog.schema.derive(MessagePackFormat)

val msgpackBytes = msgpackCodec.encode(catalog)
```

Binary bytes aren't readable as text, so instead of printing them as a `String`, we write a small helper that shows the first 16 bytes in hexadecimal:

```scala mdoc:silent
def hexPrefix(bytes: Array[Byte]): String =
  bytes.take(16).map(b => f"$b%02x").mkString(" ")
```

Now let's compare the size with JSON, look at the first bytes and check the round trip:

```scala mdoc
println(s"MessagePack: ${msgpackBytes.length} bytes, JSON: ${jsonBytes.length} bytes")

println(hexPrefix(msgpackBytes))

msgpackCodec.decode(msgpackBytes) == Right(catalog)
```

The code above shows that the MessagePack export is smaller than the JSON one. The hex prefix still follows the catalog's structure: `81` starts a map with one entry, `a5` starts a 5-byte string, `62 6f 6f 6b 73` spells `books`, `93` starts an array of three books, and `84` starts the first book as a map with four entries, whose first key is `title`. Field names travel inside the bytes, just as they do in JSON.

## 9. Swap In Avro

Avro is a binary format that is popular with Apache Kafka and big-data tools. This step uses the `zio-blocks-schema-avro` module, which builds on the Apache Avro library and runs on the JVM only — it is not available for Scala.js:

```scala mdoc:silent
import zio.blocks.schema.avro.AvroFormat

val avroCodec = Catalog.schema.derive(AvroFormat)

val avroBytes = avroCodec.encode(catalog)
```

Let's compare the size with the previous two formats, look at the first bytes and check the round trip:

```scala mdoc
println(s"Avro: ${avroBytes.length} bytes, MessagePack: ${msgpackBytes.length} bytes, JSON: ${jsonBytes.length} bytes")

println(hexPrefix(avroBytes))

avroCodec.decode(avroBytes) == Right(catalog)
```

The code above shows that Avro is the smallest export so far. Its bytes contain no field names at all: `06` is the number of books (Avro writes 3 as `06`), `14` is the length of the first title (10), and the next ten bytes spell `Zionomicon`. A reader needs the Avro schema to know which value is which field, and the codec derived that from `Catalog.schema` as well — `AvroCodec#avroSchema` returns it.

ZIO Blocks has two more binary formats, both JVM only: Thrift, derived with `ThriftFormat` from `zio-blocks-schema-thrift`, and BSON, derived with `BsonCodecDeriver` from `zio-blocks-schema-bson`.

## 10. Flatten the Catalog for CSV

CSV is different from every format so far. A CSV file is a table: one row per record, one column per field, and every cell is plain text. There is no place in a cell for a list, so a `Book` with its `tags` doesn't fit. The CSV deriver checks this when we derive the codec and throws an exception. Let's see the message. This step uses the `zio-blocks-schema-csv` module:

```scala mdoc
import zio.blocks.schema.csv.CsvFormat

println(scala.util.Try(Book.schema.derive(CsvFormat)))
```

The code above wraps the derivation in `Try` so we can print the failure instead of stopping the program. The exception is thrown at run time, when we call `Schema#derive`, not at compile time. The CSV deriver rejects lists, maps, `Option` fields and sealed traits in the same way.

The fix is a flat row type that holds only the columns we want in the table. Let's define `BookRow` and turn each book into a row:

```scala mdoc:silent
case class BookRow(title: String, author: String, year: Int)

object BookRow {
  implicit val schema: Schema[BookRow] = Schema.derived
}

val rows = catalog.books.map(book => BookRow(book.title, book.author, book.year))

val csvCodec = BookRow.schema.derive(CsvFormat)
```

The code above derives the CSV codec for `BookRow`, which works because every field is a plain value. Notice that we derive the codec for one row, not for the catalog — a CSV codec encodes and decodes one record at a time.

CSV is also the one text format in this tutorial: `CsvFormat` is a `TextFormat`, so its codec writes characters into a `java.nio.CharBuffer` instead of returning bytes. Let's write a small helper that encodes one row into a buffer and returns the line as a `String`:

```scala mdoc:silent
import java.nio.CharBuffer

def toCsvLine(row: BookRow): String = {
  val buffer = CharBuffer.allocate(256)
  csvCodec.encode(row, buffer)
  buffer.flip()
  buffer.toString
}

val csvLines = rows.map(toCsvLine)
```

The code above:

- `CharBuffer.allocate(256)` creates a buffer large enough for one line.
- `csvCodec.encode(row, buffer)` writes the row's fields, separated by commas and ending with a line break.
- `buffer.flip()` switches the buffer from writing to reading, so `buffer.toString` returns what was written.

The codec writes data rows only; the header line comes from `CsvCodec#headerNames`, the list of column names. Let's print the header and the three lines:

```scala mdoc
println(csvCodec.headerNames.mkString(","))

csvLines.foreach(line => println(line.stripLineEnd))
```

The code above prints a small CSV table. Notice the author column: a value that contains a comma is wrapped in double quotes, so the commas inside `"John A. De Goes, Adam Fraser"` don't split the cell. The derived codec always uses the default CSV settings — a comma between fields, double quotes and a `\r\n` line ending.

Finally, let's decode each line back into a `BookRow` and check the round trip:

```scala mdoc
csvLines.map(line => csvCodec.decode(CharBuffer.wrap(line))) == rows.map(Right(_))
```

The code above decodes each line with `CsvCodec#decode` and compares the results with the original rows. The tags are not in the CSV, so the round trip holds for `BookRow`, not for `Book` — choosing which fields go into the table is part of designing a CSV export.

## 11. Putting It Together

This example combines all the concepts we've learned — one `Catalog` schema, a codec derived for each of the six structured formats with the same `Schema#derive` call, a round trip through each, and a flat `BookRow` for CSV:

```scala mdoc:embed:schema-examples/src/main/scala/onetypemanyformats/CompleteExample.scala
```

## 12. Running the Examples

All examples in this tutorial have corresponding runnable Scala files in the `schema-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/schema-examples
```

Run them in order to follow the tutorial step by step.

### Model the Catalog and Encode to JSON

This example models the catalog, derives its JSON codec with `Schema#derive`, prints the JSON and checks the round trip.

<details>
  <summary>schema-examples/src/main/scala/onetypemanyformats/Concept1JsonExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/onetypemanyformats/Concept1JsonExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain onetypemanyformats.Concept1JsonExample"
```

### Swap In YAML

This example derives the YAML codec from the same schema, prints the YAML and checks the round trip.

<details>
  <summary>schema-examples/src/main/scala/onetypemanyformats/Concept2YamlExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/onetypemanyformats/Concept2YamlExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain onetypemanyformats.Concept2YamlExample"
```

### Swap In XML

This example derives the XML codec, prints the XML with its `<Catalog>` root element and checks the round trip.

<details>
  <summary>schema-examples/src/main/scala/onetypemanyformats/Concept3XmlExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/onetypemanyformats/Concept3XmlExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain onetypemanyformats.Concept3XmlExample"
```

### Swap In TOON

This example derives the TOON codec, prints the TOON text and checks the round trip.

<details>
  <summary>schema-examples/src/main/scala/onetypemanyformats/Concept4ToonExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/onetypemanyformats/Concept4ToonExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain onetypemanyformats.Concept4ToonExample"
```

### Swap In MessagePack

This example derives the MessagePack codec, compares its size with JSON, prints the first bytes in hexadecimal and checks the round trip.

<details>
  <summary>schema-examples/src/main/scala/onetypemanyformats/Concept5MessagePackExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/onetypemanyformats/Concept5MessagePackExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain onetypemanyformats.Concept5MessagePackExample"
```

### Swap In Avro

This example derives the Avro codec, compares its size with MessagePack and JSON, prints the first bytes in hexadecimal and checks the round trip.

<details>
  <summary>schema-examples/src/main/scala/onetypemanyformats/Concept6AvroExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/onetypemanyformats/Concept6AvroExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain onetypemanyformats.Concept6AvroExample"
```

### Flatten the Catalog for CSV

This example shows why a `Book` with tags can't become CSV, derives the codec for the flat `BookRow`, prints the CSV table and checks the round trip.

<details>
  <summary>schema-examples/src/main/scala/onetypemanyformats/Concept7CsvExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/onetypemanyformats/Concept7CsvExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain onetypemanyformats.Concept7CsvExample"
```

### The Complete Example

The complete example from "Putting It Together" runs everything from the tutorial in one program:

```bash
sbt "runMain onetypemanyformats.CompleteExample"
```

To compile every example at once without running them:

```bash
sbt compile
```

## 13. What You've Learned

In this tutorial, you learned:

- How one `Schema` drives a codec for every format: adding a format means adding its module, importing its format object and deriving the codec with one `Schema#derive` call
- How to encode a value with a codec, decode it back, and check that the round trip returns the original value
- What JSON, YAML, XML and TOON look like for the same data, and how the binary formats MessagePack and Avro trade readability for size
- Why CSV accepts only flat records, and how a flat `BookRow` type turns a nested model into a table

The table below sums up the formats in this tutorial:

| Format      | Format object       | Module                          | Output | Notes                                 |
| ----------- | ------------------- | ------------------------------- | ------ | ------------------------------------- |
| JSON        | `JsonFormat`        | `zio-blocks-schema`             | Bytes  | Built into the schema module          |
| YAML        | `YamlFormat`        | `zio-blocks-schema-yaml`        | Bytes  | Readable configuration format         |
| XML         | `XmlFormat`         | `zio-blocks-schema-xml`         | Bytes  | Root element named after the type     |
| TOON        | `ToonFormat`        | `zio-blocks-schema-toon`        | Bytes  | Compact text for language models      |
| MessagePack | `MessagePackFormat` | `zio-blocks-schema-messagepack` | Bytes  | Binary, keeps field names             |
| Avro        | `AvroFormat`        | `zio-blocks-schema-avro`        | Bytes  | Binary, no field names, JVM only      |
| CSV         | `CsvFormat`         | `zio-blocks-schema-csv`         | Chars  | Flat records only, one row per encode |

You now know how to take one Scala type to any format ZIO Blocks supports. The next step is to tune a format's output — its field names, indentation or array layout — with that format's configuration.

## 14. Where to Go Next

- **Want an overview of every format?** The [Built-in Codecs](../reference/schema/built-in-codecs/index.md) page lists each format with its codec type, MIME type and module.
- **Curious how format objects work?** Read [Format](../reference/schema/format.md) for the `Format` abstraction behind `Schema#derive`.
- **Ready to tune one format?** Each format has its own reference page: [JSON](../reference/schema/built-in-codecs/json/index.md), [YAML](../reference/schema/built-in-codecs/yaml.md), [XML](../reference/schema/built-in-codecs/xml.md), [TOON](../reference/schema/built-in-codecs/toon.md), [MessagePack](../reference/schema/built-in-codecs/messagepack.md), [Avro](../reference/schema/built-in-codecs/avro.md) and [CSV](../reference/schema/built-in-codecs/csv.md).
- **Need Thrift or BSON?** Read [Thrift](../reference/schema/built-in-codecs/thrift.md) and [BSON](../reference/schema/built-in-codecs/bson.md).
- **Want to know how derivation works?** Read [Type Class Derivation](../reference/schema/type-class-derivation.md) to see how a `Deriver` turns a schema into a codec.
