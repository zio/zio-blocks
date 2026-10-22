---
id: build-a-schema-registry
title: "How to Build a Schema Registry with DynamicSchema"
description: "Store versioned event schemas as data, check incoming events against them, rebind them to Scala types, and compare versions."
keywords:
  - "Schema Registry"
  - "Schema Versioning"
  - "Event Validation"
  - "DynamicSchema"
  - "BindingResolver"
  - "Avro"
---

In this guide, we will build a small schema registry for a bookshop's `BookPublished` events, using [`DynamicSchema`](../reference/schema/dynamic-schema.md) and [`BindingResolver`](../reference/schema/binding-resolver.md) from ZIO Blocks Schema. By the end, you will have a registry that stores every version of an event's shape in a directory, a consumer that loads a stored shape and checks incoming events against it without the event's class, a consumer that turns a stored shape back into a typed `Schema` to decode events, and a small comparison that tells you what changed between version 1 and version 2.

Services that exchange data through a message broker, an event store or an API see the shape of that data change over time. A schema registry is a central store of those shapes: it answers "what does version 3 of `BookPublished` look like?", so producers and consumers can check data against it. We start from a `Schema` derived for a case class and build the registry step by step:

- Turn a `Schema[A]` into a `DynamicSchema`, a description of the shape that is plain data.
- Store it in the registry under a subject and a version number.
- Load it in another program that has no compiled class for the event.
- Check incoming events against it and report what doesn't fit.
- Rebind it to a Scala type with a `BindingResolver` to get typed values back.
- Register version 2 and compare it with version 1.

## The Problem

A registry needs the event's shape as data that it can store, send and read back. A `Schema[A]` doesn't qualify, because it holds the Scala functions that build and take apart an `A`, and functions can't be written to a file. So teams often describe each event a second time by hand, next to the case class, and write a checker for that description.

For example, consider version 1 of `BookPublished` with an ISBN, a title and a price. A hand-written description and checker look like this:

```scala mdoc:compile-only
// A hand-written description of version 1, kept next to the case class
final case class FieldSpec(name: String, typeName: String)

val bookPublishedV1: List[FieldSpec] = List(
  FieldSpec("isbn", "String"),
  FieldSpec("title", "String"),
  FieldSpec("price", "BigDecimal")
)

// A hand-written checker for events that arrive as maps
def check(event: Map[String, Any], spec: List[FieldSpec]): List[String] = {
  val missing = spec.filterNot(f => event.contains(f.name)).map(f => s"missing field ${f.name}")
  val unknown = event.keySet.diff(spec.map(_.name).toSet).toList.map(name => s"unknown field $name")
  val wrongType = spec.flatMap { f =>
    event.get(f.name).toList.collect {
      case v if f.typeName == "String" && !v.isInstanceOf[String]         => s"${f.name} is not a String"
      case v if f.typeName == "BigDecimal" && !v.isInstanceOf[BigDecimal] => s"${f.name} is not a BigDecimal"
    }
  }
  missing ++ unknown ++ wrongType
}
```

This approach breaks down as soon as the event grows. Nothing ties `bookPublishedV1` to the case class, so a field added to one is easily forgotten in the other. Every new field type needs another case in the checker, and lists, optional fields, nested records and sealed traits each need their own code. Storing the description and reading it back in another service is more code again.

In this guide, we solve this by letting the `Schema` describe itself: `Schema#toDynamicSchema` turns it into a `DynamicSchema`, which keeps the full shape (type names, fields, cases, element types, validations) and drops the functions. `DynamicSchema` already knows how to check a value, how to be stored, and how to become a typed `Schema` again.

## Prerequisites

To follow along, add the schema module and its Avro module to your build. The registry stores schemas in Avro, for a reason explained in [Choose a Storage Format](#choose-a-storage-format):

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-schema"      % "@VERSION@",
  "dev.zio" %% "zio-blocks-schema-avro" % "@VERSION@"
)

// Scala 3.9 and newer: the same modules with the zio-blocks-next- prefix
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-next-schema"      % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-schema-avro" % "@VERSION@"
)
scalacOptions += "-experimental"
```

For scala-cli, Scala.js, or cross-building, see [How to Add ZIO Blocks to Your Build](./add-zio-blocks-to-your-build.md). The code on this page compiles on Scala 2.13 and Scala 3, and the registry runs on the JVM only: `zio-blocks-schema-avro` is published for the JVM, and the registry writes files with `java.nio.file`. `DynamicSchema` and `BindingResolver` themselves also work on Scala.js, except `BindingResolver.reflection`, which finds classes only on the JVM.

We assume you can derive a `Schema` for a case class, as shown in [Your First Schema](./your-first-schema.md), and that you know `DynamicValue`, the untyped value tree described on the [DynamicValue reference page](../reference/schema/dynamic-value.md). The [DynamicSchema reference page](../reference/schema/dynamic-schema.md) and the [BindingResolver reference page](../reference/schema/binding-resolver.md) cover the two main types in more depth than this guide needs.

## The Events

Every example on this page uses two versions of one event. Version 1 has an ISBN, a title and a price. Version 2 adds a list of authors and an optional edition number. In a real code base there is one `BookPublished` class whose fields change between releases; here the two versions live in objects named `v1` and `v2`, so one page can show both.

We import the schema package, the binding package for `BindingResolver`, the JSON package and the Avro format, and define both versions:

```scala mdoc:silent
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import zio.blocks.schema._
import zio.blocks.schema.avro.AvroFormat
import zio.blocks.schema.binding._
import zio.blocks.schema.json._

object v1 {
  final case class BookPublished(isbn: String, title: String, price: BigDecimal)

  object BookPublished {
    implicit val schema: Schema[BookPublished] = Schema.derived
  }
}

object v2 {
  final case class BookPublished(
    isbn: String,
    title: String,
    price: BigDecimal,
    authors: List[String],
    edition: Option[Int]
  )

  object BookPublished {
    implicit val schema: Schema[BookPublished] = Schema.derived
  }
}
```

The object a class lives in is part of its identity: the stored schema of `v1.BookPublished` names the type `BookPublished` with the owner `v1`. That matters when we rebind a stored schema in [Rebind to a Scala Type](#rebind-to-a-scala-type).

## Choose a Storage Format

`Schema#toDynamicSchema` gives us the shape as a `DynamicSchema`, and the `DynamicSchema` companion has a `Schema[DynamicSchema]`, so any ZIO Blocks format can write it. The format matters, though: in 0.0.56 a `DynamicSchema` written as JSON, YAML, TOON or MessagePack reads back as an accept-anything schema, so the registry stores Avro bytes.

Seeing the failure once makes the choice clear. We take version 1's schema, an event that is wrong in four ways (a number for the ISBN, no title, a text price and an extra field), and a codec for `DynamicSchema` in each of the two formats:

```scala mdoc:silent
val v1Schema: DynamicSchema = Schema[v1.BookPublished].toDynamicSchema

val wrongEvent: DynamicValue =
  Json.parseUnsafe("""{"isbn": 9781000000011, "price": "free", "pages": 300}""").toDynamicValue

val schemaAsJson = Schema[DynamicSchema].derive(JsonFormat)
val schemaAsAvro = Schema[DynamicSchema].derive(AvroFormat)

val fromJson = schemaAsJson.decode(schemaAsJson.encode(v1Schema))
val fromAvro = schemaAsAvro.decode(schemaAsAvro.encode(v1Schema))
```

The schema we started with rejects the event. The copy that went through JSON is no longer equal to it, has become a `Dynamic` schema, and accepts the event:

```scala mdoc
println(v1Schema.check(wrongEvent))
println(fromJson.map(_ == v1Schema))
println(fromJson.map(_.reflect.isDynamic))
println(fromJson.map(_.check(wrongEvent)))
```

The copy that went through Avro is equal to the original and rejects the event just as the original does:

```scala mdoc
println(fromAvro.map(_ == v1Schema))
println(fromAvro.map(_.check(wrongEvent)))
```

The JSON codec loses two kinds of information that the stored schema depends on. It writes a tagged value such as "this node is a `Record`" as an object with one key, `{"Record": {...}}`, and reads that object back as a plain record. It also reads every JSON number back as an `Int`, a `Long` or a `BigDecimal`, whatever it was before. When `DynamicSchema` reads a shape it doesn't recognise, it falls back to `Dynamic` without reporting an error. This is a known bug in 0.0.56. Avro keeps each tag and each number's type, so it round-trips.

## Build the Registry

Our registry keeps each subject in its own directory, with one file per version: `v1.avro`, `v2.avro`, and so on. A subject is the name the registry files schemas under, such as `book-published`. Next to each Avro file it writes the same schema as indented JSON, for people to read. A map in memory caches the schemas it has already read.

The registry has four operations. `SchemaRegistry#register` stores a schema and returns its version number; registering a schema that is already stored returns the existing version instead of adding a new one. `SchemaRegistry#versions` lists the stored versions of a subject, `SchemaRegistry#load` reads one version, and `SchemaRegistry#latest` reads the newest one:

```scala mdoc:silent
final class SchemaRegistry(root: Path) {
  private val codec = Schema[DynamicSchema].derive(AvroFormat)
  private val cache = scala.collection.mutable.Map.empty[(String, Int), DynamicSchema]

  def versions(subject: String): List[Int] = {
    val names = Option(root.resolve(subject).toFile.list()).fold(List.empty[String])(_.toList)
    names.collect { case name if name.matches("v[0-9]+\\.avro") => name.drop(1).dropRight(5).toInt }.sorted
  }

  def register(subject: String, schema: DynamicSchema): Int =
    versions(subject).find(version => load(subject, version) == schema) match {
      case Some(existing) => existing
      case None           =>
        val version = versions(subject).lastOption.fold(1)(_ + 1)
        val dir     = Files.createDirectories(root.resolve(subject))
        Files.write(dir.resolve(s"v$version.avro"), codec.encode(schema))
        Files.write(dir.resolve(s"v$version.json"), jsonView(schema).getBytes(UTF_8))
        cache((subject, version)) = schema
        version
    }

  def load(subject: String, version: Int): DynamicSchema =
    cache.getOrElseUpdate((subject, version), read(subject, version))

  def latest(subject: String): Option[(Int, DynamicSchema)] =
    versions(subject).lastOption.map(version => (version, load(subject, version)))

  private def read(subject: String, version: Int): DynamicSchema = {
    val bytes  = Files.readAllBytes(root.resolve(subject).resolve(s"v$version.avro"))
    val schema = codec.decode(bytes).fold(error => throw error, identity)
    if (schema.reflect.isDynamic)
      throw new IllegalStateException(s"$subject v$version was read back as an accept-anything schema")
    schema
  }

  private def jsonView(schema: DynamicSchema): String =
    Json.fromDynamicValue(DynamicSchema.toDynamicValue(schema)).print(WriterConfig.withIndentionStep(2))
}
```

Here is what each part does:

- `Schema[DynamicSchema].derive(AvroFormat)` builds the Avro codec that `SchemaRegistry#register` writes with and `SchemaRegistry#read` reads with.
- `SchemaRegistry#register` compares the new schema with every stored version using `==`, which compares the two shapes field by field.
- `SchemaRegistry#read` throws when a schema reads back as `Dynamic`. Avro never does this, but the check costs two lines and turns a silent accept-anything registry into an error if someone later swaps the format.
- `SchemaRegistry#jsonView` turns the schema into a `DynamicValue` with `DynamicSchema.toDynamicValue` and prints it as JSON.

:::warning
The `.json` file is for people only. In 0.0.56 neither `Schema[DynamicSchema]`'s JSON codec nor `DynamicSchema.fromDynamicValue` reads it back correctly, so the registry never loads it. Load schemas from the `.avro` files.
:::

## Register Version 1

A producer, the service that publishes `BookPublished` events, registers the event's schema when it starts. We create a registry in a temporary directory and register version 1 twice, as a producer does each time it restarts:

```scala mdoc:silent
val root: Path               = Files.createTempDirectory("schema-registry")
val registry: SchemaRegistry = new SchemaRegistry(root)
```

The first call stores the schema as version 1. The second call finds the same schema already stored and returns 1 again, so the directory still holds one version:

```scala mdoc
println(registry.register("book-published", Schema[v1.BookPublished].toDynamicSchema))
println(registry.register("book-published", Schema[v1.BookPublished].toDynamicSchema))
println(registry.versions("book-published"))
println(root.resolve("book-published").toFile.list().toList.sorted)
```

The JSON view shows what the registry stored. We read it back as text, along with the sizes of both files:

```scala mdoc:silent
val v1Dir: Path  = root.resolve("book-published")
val view: String = new String(Files.readAllBytes(v1Dir.resolve("v1.json")), UTF_8)
```

The first lines name the type and its owner. On this page the owner starts with the objects that mdoc wraps around the code (`repl`, `MdocSession` and `MdocApp`), followed by `v1`:

```scala mdoc
println(view.linesIterator.take(22).mkString("\n"))
println(s"v1.json: ${Files.size(v1Dir.resolve("v1.json"))} bytes, v1.avro: ${Files.size(v1Dir.resolve("v1.avro"))} bytes")
```

Most of that size is type identity: every type in the schema, down to `String` and `BigDecimal`, is stored with its full name, owner and kind, so a reader can tell a `java.lang.String` from any other type called `String`.

:::warning
In 0.0.56, a schema for a recursive type, such as a `Chapter` that holds a `List[Chapter]`, can't be stored: `DynamicSchema.toDynamicValue` and the codecs follow the recursion forever and fail with a `StackOverflowError`. This is a known bug. The non-recursive shapes that events usually have (records, lists, maps, options, sealed traits) store fine.
:::

## Load the Schema Without the Class

A consumer, a service that reads `BookPublished` events, might be a gateway that forwards events and has never compiled `BookPublished`. It loads the shape from the registry and works with it as data.

We open the same directory with a new registry, so nothing comes from the producer's cache, and load version 1:

```scala mdoc:silent
val consumerRegistry: SchemaRegistry = new SchemaRegistry(root)
val storedV1: DynamicSchema          = consumerRegistry.load("book-published", 1)
```

The stored schema can tell us its fields without any compiled class. The `describe` helper reads them from `DynamicSchema#reflect`, the shape's tree: `Reflect#asRecord` returns the record node when the shape is a record, and each field has a name and the `TypeId` of its value:

```scala mdoc
def describe(schema: DynamicSchema): String =
  schema.reflect.asRecord match {
    case Some(record) =>
      record.fields.map(field => s"${field.name}: ${field.value.typeId}").mkString(s"${schema.typeId.name}(", ", ", ")")
    case None => schema.typeId.toString
  }

println(describe(storedV1))
```

The loaded schema is also equal to the one the producer derived, which is how a service that does have the class can confirm that its class matches a registered version:

```scala mdoc
println(storedV1 == Schema[v1.BookPublished].toDynamicSchema)
println(storedV1 == Schema[v2.BookPublished].toDynamicSchema)
```

## Check Incoming Events

`DynamicSchema#check` takes a `DynamicValue` and returns `None` when the value fits the schema, or `Some(SchemaError)` that says what doesn't fit and where. `DynamicSchema#conforms` returns the same answer as a `Boolean`.

The producer turns each event into a `DynamicValue` with `Schema#toDynamicValue` and sends it. Avro serves for the events too: `Schema[DynamicValue].derive(AvroFormat)` writes a `DynamicValue` with every tag and number type intact, so the consumer reads back exactly what the producer sent:

```scala mdoc:silent
val eventCodec = Schema[DynamicValue].derive(AvroFormat)

val zionomicon = v1.BookPublished("978-1-00-000001-1", "Zionomicon", BigDecimal("49.99"))
val sent: Array[Byte] = eventCodec.encode(Schema[v1.BookPublished].toDynamicValue(zionomicon))

val received: DynamicValue = eventCodec.decode(sent).fold(error => throw error, identity)
```

The event the producer sent fits version 1:

```scala mdoc
println(storedV1.check(received))
println(storedV1.conforms(received))
```

Events from other sources may not fit. We parse four wrong ones as JSON:

```scala mdoc:silent
val textPrice   = Json.parseUnsafe("""{"isbn": "978-1-00-000001-1", "title": "Zionomicon", "price": "49.99"}""")
val noTitle     = Json.parseUnsafe("""{"isbn": "978-1-00-000001-1", "price": 49.99}""")
val extraField  = Json.parseUnsafe("""{"isbn": "978-1-00-000001-1", "title": "Zionomicon", "price": 49.99, "pages": 312}""")
val manyMistakes = Json.parseUnsafe("""{"isbn": 9781000000011, "title": 42}""")
```

`Json#toDynamicValue` turns each parsed document into a `DynamicValue`, which we check against version 1:

```scala mdoc
println(storedV1.check(textPrice.toDynamicValue))
println(storedV1.check(noTitle.toDynamicValue))
println(storedV1.check(extraField.toDynamicValue))
println(storedV1.check(manyMistakes.toDynamicValue))
```

Each error names the problem and the path where it occurs: `.price` is the `price` field, and `.` is the record itself. The last event has three mistakes, but `DynamicSchema#check` stops at the first one it finds and reports only that, so a consumer that wants to list every mistake needs another pass after the first one is fixed.

`DynamicSchema#check` compares primitive types exactly, and that matters for JSON. `Json#toDynamicValue` reads a whole number such as `50` as an `Int`, so a JSON price of `50` fails a `BigDecimal` field, even though the typed JSON codec accepts it:

```scala mdoc:silent
val wholePrice = Json.parseUnsafe("""{"isbn": "978-1-00-000001-1", "title": "Zionomicon", "price": 50}""")
```

The schema rejects the price, and the codec of the compiled class reads it as `50`:

```scala mdoc
println(storedV1.check(wholePrice.toDynamicValue))
println(v1.BookPublished.schema.jsonCodec.decode(wholePrice.print))
```

:::warning
Check JSON events with `DynamicSchema#check` only when their fields are strings, booleans, `Int`s, lists and records. `Json#toDynamicValue` reads a whole number as an `Int`, or as a `Long` when it is too big for an `Int`, and every other number as a `BigDecimal`. So `Double`, `Float`, `Short` and `Byte` fields always fail, a `Long` field fails for small values, and a `BigDecimal` field fails for whole numbers. An `Option` field fails too: the schema expects a tagged `Some` or `None`, and JSON has a value or `null`. For JSON events with such fields, rebind the schema and decode with its JSON codec, as the next section shows. `DynamicSchema#check` also doesn't run the checks of a newtype built with `Schema#transform`, such as a smart constructor that rejects an invalid ISBN; see [How to Define Validated Newtypes and Collect Every Validation Error](./validated-newtypes.md).
:::

## Rebind to a Scala Type

A consumer that has compiled `BookPublished` wants typed values, not a `DynamicValue`. `DynamicSchema#rebind` turns a stored schema back into a `Schema[A]` by attaching a binding (the functions that build and take apart a value) to each node of the shape. It gets the bindings from a `BindingResolver`, which looks them up by each node's `TypeId`.

We build a resolver from two parts joined with `BindingResolver#++`, which tries the left side first. `BindingResolver.empty.bind(Binding.of[v1.BookPublished])` supplies the binding for the event's record, and `BindingResolver.defaults` supplies the bindings for primitives such as `String` and `BigDecimal`, the `java.time` types, and the standard collections:

```scala mdoc:silent
val v1Resolver: BindingResolver =
  BindingResolver.empty.bind(Binding.of[v1.BookPublished]) ++ BindingResolver.defaults

val typedV1: Schema[v1.BookPublished] = storedV1.rebind[v1.BookPublished](v1Resolver)
```

The rebound schema works like a derived one. Its JSON codec decodes events into `v1.BookPublished` values, accepts the whole-number price that `DynamicSchema#check` rejected, and reports a wrong field with its path:

```scala mdoc
println(typedV1.jsonCodec.decode(wholePrice.print))
println(typedV1.jsonCodec.decode(textPrice.print))
```

When the resolver has no binding for a type that the stored schema names, `DynamicSchema#rebind` throws a `RebindException` at once. That is what happens when a consumer's class has been renamed or moved since the schema was registered: the stored schema still names the old type, and the consumer's binding is for a different one. Rebinding version 1 with only `v2.BookPublished` bound fails like this:

```scala mdoc:silent
val renamedResolver: BindingResolver =
  BindingResolver.empty.bind(Binding.of[v2.BookPublished]) ++ BindingResolver.defaults
```

The call throws before it returns a schema, and the exception's message names what is missing:

```scala mdoc
try storedV1.rebind[v2.BookPublished](renamedResolver)
catch { case e: RebindException => println(e.getMessage) }
```

The message names the missing type with its full name, here with the wrapper objects that mdoc puts around this page's code, and the path `.` says that the root of the schema is missing its binding. To keep rebinding working, keep the event class's name and package stable across versions, and change its fields instead.

:::tip
On the JVM, `BindingResolver.reflection` finds the binding for any case class by its name, so `BindingResolver.reflection ++ BindingResolver.defaults` rebinds a record schema without a `Binding.of` line per class. On Scala.js it finds nothing, so bind each record with `Binding.of` there.
:::

## Register Version 2 and Compare It With Version 1

The producer's next release adds `authors` and `edition`. It registers the new shape, and the registry stores it as version 2:

```scala mdoc
println(registry.register("book-published", Schema[v2.BookPublished].toDynamicSchema))
println(registry.versions("book-published"))
```

ZIO Blocks has no built-in compatibility check between two versions, like the backward and forward rules of an Avro registry. A small comparison of the record fields is enough for this event, and it is ours to write. The `compare` function below lists each field that was removed, added or changed type, and marks the changes that break old data, meaning that a reader on the new version can't decode an event written with the old one:

```scala mdoc:silent
final case class Change(description: String, breaksOldData: Boolean)

def compare(older: DynamicSchema, newer: DynamicSchema): List[Change] = {
  def fields(schema: DynamicSchema): Map[String, Reflect.Unbound[_]] =
    schema.reflect.asRecord.fold(Map.empty[String, Reflect.Unbound[_]])(_.fields.map(f => f.name -> f.value).toMap)

  val before = fields(older)
  val after  = fields(newer)

  val removed = (before.keySet -- after.keySet).toList.sorted.map { name =>
    Change(s"removed $name", breaksOldData = false)
  }
  val added = (after.keySet -- before.keySet).toList.sorted.map { name =>
    val field  = after(name)
    val filled = field.isOption || field.isCollection
    Change(s"added $name: ${field.typeId}", breaksOldData = !filled)
  }
  val changed = (before.keySet intersect after.keySet).toList.sorted.collect {
    case name if before(name).typeId != after(name).typeId =>
      Change(s"changed $name: ${before(name).typeId} -> ${after(name).typeId}", breaksOldData = true)
  }
  removed ++ added ++ changed
}
```

The rules come from how the JSON codec reads an event that lacks a field. It fills a missing `Option` field with `None` and a missing collection with an empty one, so adding such a field doesn't break old data. Adding any other field does, and so does changing a field's type. Removing a field doesn't, because the codec skips fields it doesn't know. The stored schema of a case class doesn't record a field's default value in 0.0.56, so `compare` can't tell that a field with a default is safe and reports it as breaking.

Comparing the two stored versions gives two additions, neither of which breaks old data:

```scala mdoc:silent
val changes: List[Change] = compare(registry.load("book-published", 1), registry.load("book-published", 2))
```

Each change prints on its own line, followed by whether any of them breaks old data:

```scala mdoc
changes.foreach(println)
println(changes.exists(_.breaksOldData))
```

A consumer on version 2 rebinds the new schema. `v2.BookPublished` has an `Option[Int]` field, and `BindingResolver.defaults` has no binding for `Option`, so the resolver needs three more: the `Option[Int]` variant itself, its `Some` case for an `Int` (`Binding.Record.someInt`), and its `None` case (`Binding.Record.none`). Without them, `DynamicSchema#rebind` fails with `Missing Record binding for type scala.Some at path: .edition<Some>`:

```scala mdoc:silent
val v2Resolver: BindingResolver =
  BindingResolver.empty
    .bind(Binding.of[v2.BookPublished])
    .bind(Binding.of[Option[Int]])
    .bind(Binding.Record.someInt)
    .bind(Binding.Record.none) ++ BindingResolver.defaults

val typedV2: Schema[v2.BookPublished] = consumerRegistry.load("book-published", 2).rebind[v2.BookPublished](v2Resolver)
```

The version 2 consumer decodes new events, and also old events written with version 1, as `compare` predicted:

```scala mdoc:silent
val fpInScala: String =
  """{"isbn": "978-1-00-000002-8", "title": "Functional Programming in Scala", "price": 44.99,
    | "authors": ["Michael Pilquist", "Rúnar Bjarnason", "Paul Chiusano"], "edition": 2}""".stripMargin
```

We decode the new event, then the two version 1 events from earlier, the one without a title and the one with a whole-number price:

```scala mdoc
println(typedV2.jsonCodec.decode(fpInScala))
println(typedV2.jsonCodec.decode(noTitle.print))
println(typedV2.jsonCodec.decode(wholePrice.print))
```

The last line decodes a version 1 event into a version 2 value, with an empty author list and no edition. The middle line fails because that event lacks a title, which no version of the event allows.

:::note
Decode old events with the JSON codec, as above, not with `Schema#fromDynamicValue`. In 0.0.56, `Schema#fromDynamicValue` reports a missing `Option` or collection field as `Missing field` instead of filling it, so it rejects version 1 events that the JSON codec accepts. This is a known bug.
:::

## Putting It Together

The complete example runs the whole life of a subject in one program: the producer registers version 1, a gateway loads it without the class and checks events, a typed consumer rebinds it, and the producer's next release registers version 2, which the registry compares with version 1 before the consumer upgrades:

```scala mdoc:embed:schema-examples/src/main/scala/buildaschemaregistry/CompleteExample.scala
```

## Running the Examples

All examples in this guide have runnable Scala files in the `schema-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/schema-examples
```

The step examples share two files that sit next to them: `BookEvents.scala` holds both versions of the event, and `SchemaRegistry.scala` holds the registry. The complete example has its own copies and doesn't need them.

<details>
  <summary>schema-examples/src/main/scala/buildaschemaregistry/BookEvents.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/buildaschemaregistry/BookEvents.scala:show-line-numbers
```

</details>

<details>
  <summary>schema-examples/src/main/scala/buildaschemaregistry/SchemaRegistry.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/buildaschemaregistry/SchemaRegistry.scala:show-line-numbers
```

</details>

### Choose a Storage Format

This example round-trips version 1's schema through JSON and through Avro, and checks a wrong event against each copy.

<details>
  <summary>schema-examples/src/main/scala/buildaschemaregistry/Step1StorageFormatExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/buildaschemaregistry/Step1StorageFormatExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain buildaschemaregistry.Step1StorageFormatExample"
```

### Register Version 1

This example registers version 1 twice, lists the stored files, and prints the start of the JSON view and the sizes of both files.

<details>
  <summary>schema-examples/src/main/scala/buildaschemaregistry/Step2RegisterExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/buildaschemaregistry/Step2RegisterExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain buildaschemaregistry.Step2RegisterExample"
```

### Load the Schema Without the Class

This example loads version 1 in a fresh registry, prints its fields, and compares it with the schemas of both event classes.

<details>
  <summary>schema-examples/src/main/scala/buildaschemaregistry/Step3LoadWithoutClassExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/buildaschemaregistry/Step3LoadWithoutClassExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain buildaschemaregistry.Step3LoadWithoutClassExample"
```

### Check Incoming Events

This example sends an event as Avro bytes, checks it against version 1, and checks four wrong events and one JSON event with a whole-number price.

<details>
  <summary>schema-examples/src/main/scala/buildaschemaregistry/Step4CheckEventsExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/buildaschemaregistry/Step4CheckEventsExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain buildaschemaregistry.Step4CheckEventsExample"
```

### Rebind to a Scala Type

This example rebinds version 1 to `v1.BookPublished`, decodes JSON events with the rebound schema, and shows the `RebindException` for a type the resolver doesn't know.

<details>
  <summary>schema-examples/src/main/scala/buildaschemaregistry/Step5RebindExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/buildaschemaregistry/Step5RebindExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain buildaschemaregistry.Step5RebindExample"
```

### Register Version 2 and Compare It With Version 1

This example registers version 2, compares it with version 1, rebinds it with the `Option` bindings, and decodes a new event and an old one.

<details>
  <summary>schema-examples/src/main/scala/buildaschemaregistry/Step6CompareVersionsExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/buildaschemaregistry/Step6CompareVersionsExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain buildaschemaregistry.Step6CompareVersionsExample"
```

### The Complete Example

This example runs the whole guide in one program:

```bash
sbt "runMain buildaschemaregistry.CompleteExample"
```

### Compile All Examples

To compile every example without running one, run this from the `schema-examples` directory:

```bash
sbt compile
```

## Going Further

- The [DynamicSchema reference page](../reference/schema/dynamic-schema.md) covers the rest of the type: reading docs, modifiers and examples from a stored schema, navigating it with a `DynamicOptic`, and `DynamicSchema#toSchema`.
- The [BindingResolver reference page](../reference/schema/binding-resolver.md) shows how to bind sequence and map types, wrappers and variants, and how resolvers combine.
- A JSON Schema document describes the same kind of shape, but for JSON only and for any tool that speaks JSON Schema. [How to Generate JSON Schema from a Type and Validate Payloads Against It](./generate-json-schema-and-validate-payloads.md) shows that route. A `DynamicSchema` registry keeps the full ZIO Blocks shape, including sealed traits and type names, and turns back into a typed `Schema` that works with every format.
- To send the same events as JSON, Avro, MessagePack and more from one schema, see [One Type, Many Formats](./one-type-many-formats.md).
- To work with JSON events whose shape you don't model at all, see [How to Read and Modify JSON Without Case Classes](./json-without-case-classes.md).
