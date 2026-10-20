---
id: json-library-migration
title: "Coming from circe, zio-json or jsoniter-scala"
description: "Move JSON codecs from circe, zio-json or jsoniter-scala to one ZIO Blocks Schema, keeping your field names, discriminators and custom formats."
keywords:
  - "JSON"
  - "Migration"
  - "circe"
  - "zio-json"
  - "jsoniter-scala"
  - "JsonCodecDeriver"
---

This guide moves a small catalog of Scala books from circe, zio-json or jsoniter-scala to ZIO Blocks. By the end, each type has one derived `Schema` instead of a library-specific codec, your naming, discriminator and date-format rules live on a `JsonCodecDeriver` or in `Modifier` annotations, and you know the five places where habits from your old library silently do the wrong thing. Each source library gets its own section with the same three parts: the code you wrote, the code you write now, and what changes. Read the concept map, then jump to the section for the library you are leaving.

- [Concept Map](#concept-map)
- [Coming from circe](#coming-from-circe)
- [Coming from zio-json](#coming-from-zio-json)
- [Coming from jsoniter-scala](#coming-from-jsoniter-scala)
- [Traps for Migrating Code](#traps-for-migrating-code)
- [What You Gain and What Is Different](#what-you-gain-and-what-is-different)
- [Migration Checklist](#migration-checklist)

## The Problem

All three libraries derive a JSON codec from a case class, but the codec is the end of the road: it only does JSON. The same `Book` needs a second derivation for YAML, Avro or a database row, and every naming or discriminator rule is configured again for each one. The configuration also lives in a different place in each library — a `Configuration` value in implicit scope for circe, annotations for zio-json, a `CodecMakerConfig` passed to a macro for jsoniter-scala — so moving between them means relearning where each rule goes.

This is the old code, the same type with a codec for each library (not compiled here):

```scala
// circe
import io.circe.Codec
case class Book(title: String, firstAuthor: String, year: Int, isbn: Option[String], tags: List[String])
    derives Codec.AsObject

// zio-json
import zio.json._
object Book { implicit val codec: JsonCodec[Book] = DeriveJsonCodec.gen[Book] }

// jsoniter-scala
import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros._
object Book { implicit val codec: JsonValueCodec[Book] = JsonCodecMaker.make }
```

In ZIO Blocks, a type derives a `Schema` once. The JSON codec is derived from that schema, and so is every other format's codec, from the same description and the same annotations.

## Prerequisites

Add the schema module, which includes the JSON codec:

```scala
// Scala 2.13 and Scala 3.3 LTS
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "@VERSION@"

// Scala 3.7 and newer
libraryDependencies += "dev.zio" %% "zio-blocks-next-schema" % "@VERSION@"
```

For Scala.js, use `%%%`. You can remove the circe, zio-json or jsoniter-scala dependency once the last codec is gone; on the JVM the schema module has no third-party dependencies, and on Scala.js it adds only the `scala-java-time` libraries.

This guide assumes you already know one of the three source libraries. It maps their concepts onto ZIO Blocks rather than teaching `Schema` from scratch, so if `Schema.derived`, `toJsonString` and `fromJson` are new to you, work through [Your First Schema](./your-first-schema.md) first. Every ZIO Blocks snippet below starts from these imports:

```scala mdoc:silent
import zio.blocks.schema._
import zio.blocks.schema.json._
```

`zio.blocks.schema._` brings in `Schema`, `Modifier`, `NameMapper` and the `toJsonString` and `fromJson` extension methods; `zio.blocks.schema.json._` brings in `JsonCodec`, `JsonCodecDeriver` and `DiscriminatorKind`.

## The Book Catalog Model

The running example is a catalog of Scala books. A `Book` records its title, its first author, the year it came out, an ISBN when we know it, and a list of tags. A sealed `Format` says how a copy is sold:

```scala mdoc:silent
case class Book(title: String, firstAuthor: String, year: Int, isbn: Option[String], tags: List[String])
object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

sealed trait Format
object Format {
  case class Paperback(pages: Int) extends Format
  case class Ebook(sizeMb: Double) extends Format

  implicit val schema: Schema[Format] = Schema.derived
}

val zionomicon = Book("Zionomicon", "John A. De Goes", 2024, None, List("zio", "effects"))
val programmingInScala =
  Book("Programming in Scala", "Martin Odersky", 2008, Some("9780981531601"), List("scala"))
val fpInScala =
  Book("Functional Programming in Scala", "Paul Chiusano", 2014, Some("9781617290657"), Nil)
```

The ISBN of Zionomicon is unknown here, so it is `None`, and the page counts and file sizes in later examples are sample data.

## Concept Map

The table maps each task to its spelling in the three source libraries and in ZIO Blocks:

| Task                           | circe 0.14                                    | zio-json 1.x                                     | jsoniter-scala 2                                        | ZIO Blocks                                                                                    |
| ------------------------------ | --------------------------------------------- | ------------------------------------------------ | ------------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| Derive a codec                 | `deriveCodec[A]`, `derives Codec.AsObject`    | `DeriveJsonCodec.gen[A]`                         | `JsonCodecMaker.make[A]`                                | `Schema.derived[A]`; the JSON codec comes from the schema                                     |
| Encode to a string             | `a.asJson.noSpaces`                           | `a.toJson`                                       | `writeToString(a)`                                      | `a.toJsonString`                                                                              |
| Decode from a string           | `decode[A](s)`                                | `s.fromJson[A]`                                  | `readFromString[A](s)`                                  | `s.fromJson[A]`                                                                               |
| Decode error                   | `Left(io.circe.Error)`                        | `Left(String)`                                   | throws `JsonReaderException`                            | `Left(SchemaError)`                                                                           |
| JSON AST                       | `io.circe.Json`                               | `zio.json.ast.Json`                              | none                                                    | `zio.blocks.schema.json.Json`, from `a.toJson`                                                |
| Naming strategy for fields     | `Configuration#withSnakeCaseMemberNames`      | `@jsonMemberNames(SnakeCase)`                    | `CodecMakerConfig#withFieldNameMapper`                  | `JsonCodecDeriver#withFieldNameMapper`, `@Modifier.fieldNaming`                               |
| Rename one field               | `@JsonKey` (circe-generic-extras, Scala 2)    | `@jsonField("name")`                             | `@named("name")`                                        | `@Modifier.rename("name")`                                                                    |
| Accept an old field name       | none                                          | `@jsonAliases("old")`                            | none                                                    | `@Modifier.alias("old")`                                                                      |
| Type discriminator field       | `Configuration#withDiscriminator("type")`     | `@jsonDiscriminator("type")`                     | `"type"` by default                                     | `DiscriminatorKind.Field("type")`, `@Modifier.discriminator("type")`                          |
| Case names                     | `Configuration#withTransformConstructorNames` | `@jsonHint`, `@jsonHintNames`                    | `@named`, `CodecMakerConfig#withAdtLeafClassNameMapper` | `@Modifier.rename` on the case, `@Modifier.caseNaming`, `JsonCodecDeriver#withCaseNameMapper` |
| Leave a field out              | none                                          | `@jsonExclude`                                   | `@transient`                                            | `@Modifier.transient()`                                                                       |
| Use defaults for missing input | `Configuration#withDefaults`                  | on by default                                    | on by default                                           | on by default                                                                                 |
| Reject unknown fields          | `Configuration#withStrictDecoding`            | `@jsonNoExtraFields`                             | `CodecMakerConfig#withSkipUnexpectedFields(false)`      | `@Modifier.noExtraFields()`, `JsonCodecDeriver#withRejectExtraFields(true)`                   |
| Custom codec for one type      | a given `Encoder` and `Decoder` in scope      | a given `JsonEncoder` and `JsonDecoder` in scope | a given `JsonValueCodec` in scope                       | `JsonCodecDeriver.withInstance[A](codec)`                                                     |

The last row is the biggest change. In all three libraries, the derived codec searches implicit scope for an encoder and decoder for each field type. ZIO Blocks derivation never does: it reads the field types from the schema and uses its own codecs, unless you register an override on the deriver. The [traps section](#an-implicit-codec-in-scope-is-ignored) shows what happens when you keep the old habit.

## Coming from circe

circe derives an `Encoder` and a `Decoder` (or both, as a `Codec`) per type, and configured derivation reads a `Configuration` from implicit scope. In ZIO Blocks, the type gets a `Schema`, and configuration is a value you pass to the deriver.

### Derived Codecs

The first change replaces the codec with a schema and keeps the encode and decode calls almost as they were.

#### You Wrote This

This is the old code, circe 0.14 on Scala 3 (not compiled here):

```scala
import io.circe.Codec
import io.circe.parser.decode
import io.circe.syntax._

case class Book(title: String, firstAuthor: String, year: Int, isbn: Option[String], tags: List[String])
    derives Codec.AsObject

val programmingInScala = Book("Programming in Scala", "Martin Odersky", 2008, Some("9780981531601"), List("scala"))

println(programmingInScala.asJson.noSpaces)
println(decode[Book](programmingInScala.asJson.noSpaces))
```

#### You Write This

The schema in the companion replaces `derives Codec.AsObject`, `toJsonString` replaces `asJson.noSpaces`, and `fromJson` replaces `decode`:

```scala mdoc
println(programmingInScala.toJsonString)
println(programmingInScala.toJsonString.fromJson[Book])
```

### Configured Derivation

circe's configured derivation puts the rules in a `Configuration`. The ZIO Blocks equivalent is a `JsonCodecDeriver` with the same rules, from which we derive the codec for each top-level type we send.

#### You Wrote This

This is the old code, with `snake_case` member names, a `"type"` discriminator and default values (not compiled here):

```scala
import io.circe.derivation.{Configuration, ConfiguredCodec}

given Configuration = Configuration.default.withSnakeCaseMemberNames.withDiscriminator("type").withDefaults

case class Book(title: String, firstAuthor: String, year: Int, isbn: Option[String], tags: List[String])
    derives ConfiguredCodec

sealed trait Format derives ConfiguredCodec
object Format {
  case class Paperback(pages: Int) extends Format
  case class Ebook(sizeMb: Double) extends Format
}
```

#### You Write This

`JsonCodecDeriver#withFieldNameMapper` sets the naming strategy and `JsonCodecDeriver#withDiscriminatorKind` with `DiscriminatorKind.Field` sets the discriminator. `Schema#derive` builds a codec through that deriver:

```scala mdoc:silent
val apiDeriver: JsonCodecDeriver =
  JsonCodecDeriver
    .withFieldNameMapper(NameMapper.SnakeCase)
    .withDiscriminatorKind(DiscriminatorKind.Field("type"))

val bookCodec: JsonCodec[Book]     = Book.schema.derive(apiDeriver)
val formatCodec: JsonCodec[Format] = Format.schema.derive(apiDeriver)
```

We call `JsonCodec#encodeToString` and `JsonCodec#decode` on the derived codecs:

```scala mdoc
println(bookCodec.encodeToString(programmingInScala))
println(formatCodec.encodeToString(Format.Paperback(320)))
println(formatCodec.decode("""{"type":"Ebook","size_mb":4.5}"""))
```

There is no equivalent of `withDefaults` to turn on: a field with a default value is filled in when it is missing from the input, and left out of the output when it holds that value. If the rule belongs to one type rather than to the service you talk to, annotate the type with `@Modifier.fieldNaming("snake_case")` or `@Modifier.discriminator("type")` instead, and the plain `toJsonString` picks it up. [How to Customize JSON Field Names, ADT Encoding and Optional Fields](./customize-json-encoding.md) covers every option and when to pick an annotation over a deriver setting.

### Custom Codecs for One Type

In circe, a custom `Encoder[LocalDate]` and `Decoder[LocalDate]` in implicit scope change every derived codec that contains a `LocalDate`. In ZIO Blocks, you write one `JsonCodec[LocalDate]` and register it on the deriver.

#### You Wrote This

This is the old code, writing dates as `dd/MM/yyyy` (not compiled here):

```scala
import io.circe.{Codec, Decoder, Encoder}
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.util.Try

val partnerDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

given Encoder[LocalDate] = Encoder.encodeString.contramap(partnerDateFormat.format)
given Decoder[LocalDate] = Decoder.decodeString.emap { s =>
  Try(LocalDate.parse(s, partnerDateFormat)).toEither.left.map(_ => s"expected a dd/MM/yyyy date, got: $s")
}

case class Order(title: String, orderedOn: LocalDate) derives Codec.AsObject
```

#### You Write This

A `JsonCodec[LocalDate]` reads and writes the string through the streaming `JsonReader` and `JsonWriter`. `JsonCodecDeriver#withInstance` registers it, and `Schema#deriving` derives a codec through the resulting deriver:

```scala mdoc:silent
import java.time.LocalDate
import java.time.format.{DateTimeFormatter, DateTimeParseException}

val partnerDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

val partnerDateCodec: JsonCodec[LocalDate] = new JsonCodec[LocalDate] {
  def decodeValue(in: JsonReader): LocalDate = {
    val s = in.readString()
    try LocalDate.parse(s, partnerDateFormat)
    catch { case _: DateTimeParseException => error(s"expected a dd/MM/yyyy date, got: $s") }
  }

  def encodeValue(x: LocalDate, out: JsonWriter): Unit =
    out.writeVal(partnerDateFormat.format(x))
}

case class Order(title: String, orderedOn: LocalDate)
object Order {
  implicit val schema: Schema[Order] = Schema.derived
}

val partnerDeriver = JsonCodecDeriver.withInstance[LocalDate](partnerDateCodec)
val orderCodec: JsonCodec[Order] = Order.schema.deriving(partnerDeriver).derive
```

The derived codec uses the custom format for `orderedOn`, and would do so for a `LocalDate` at any depth:

```scala mdoc
println(orderCodec.encodeToString(Order("Zionomicon", LocalDate.of(2025, 3, 14))))
println(orderCodec.decode("""{"title":"Zionomicon","orderedOn":"14/03/2025"}"""))
```

The order date is sample data. [How to Override the Codec for One Type Everywhere](./override-codec-for-one-type.md) covers the full codec, including the methods for the `Json` value type, and the narrower scopes of one field or one derivation.

### Decode Errors

circe returns an `io.circe.Error`, either a `ParsingFailure` or a `DecodingFailure` with a cursor history. ZIO Blocks returns a `SchemaError`, whose `SchemaError#message` names the problem and the path to it:

```scala mdoc
"""{"title":"Zionomicon","firstAuthor":"John A. De Goes","year":"2024"}""".fromJson[Book] match {
  case Left(error) => println(error.message)
  case Right(book) => println(s"Decoded: $book")
}
```

The path after `at:` starts at `.` for the top-level value, so `.year` is the `year` field of the book. circe's `Decoder[Int]` accepts the string `"2024"`, so circe decodes this input; ZIO Blocks requires a JSON number. `SchemaError` extends `Exception`, so code that converts the result with `Either#toTry` or rethrows the error keeps working.

### What Changes

- **A schema replaces the codec.** `derives Codec.AsObject` or `deriveCodec` becomes `implicit val schema: Schema[A] = Schema.derived` in the companion. The same schema gives you every other format.
- **Configuration is a value, not an implicit.** A `Configuration` in implicit scope becomes a `JsonCodecDeriver` that you pass to `Schema#derive`, or a `Modifier` annotation on the type.
- **Codecs in implicit scope are ignored.** Register a custom codec with `JsonCodecDeriver.withInstance`; see the [traps](#an-implicit-codec-in-scope-is-ignored).
- **`None` and empty lists are left out by default.** circe writes `"isbn":null` and `"tags":[]`; ZIO Blocks omits both. See the [traps](#none-and-empty-collections-are-left-out).
- **Numbers must be JSON numbers.** circe decodes `"year":"2024"` into an `Int`; ZIO Blocks rejects it with `illegal number`.
- **Missing collections decode as empty.** circe fails with `Missing required field` when `tags` is absent and has no default; ZIO Blocks decodes it as `Nil`.
- **The default sealed trait shape is the same.** Without a discriminator, both wrap the case in an object keyed by its name, such as `{"Paperback":{"pages":320}}`. With a discriminator, circe writes the `"type"` field last and ZIO Blocks writes it first; both read it from any position.

## Coming from zio-json

zio-json and ZIO Blocks look alike on purpose: both derive from case classes, both configure with annotations, and both have `toJson` and `fromJson`. Most annotations map one to one, but two method names mean something slightly different.

### Derived Codecs

`DeriveJsonCodec.gen` becomes `Schema.derived`, and the call sites keep their names.

#### You Wrote This

This is the old code, zio-json 1.x (not compiled here):

```scala
import zio.json._

case class Book(title: String, firstAuthor: String, year: Int, isbn: Option[String], tags: List[String])
object Book {
  implicit val codec: JsonCodec[Book] = DeriveJsonCodec.gen[Book]
}

val json: String = programmingInScala.toJson
val back: Either[String, Book] = json.fromJson[Book]
```

#### You Write This

The schema replaces the codec. `toJson` becomes `toJsonString`, and `fromJson` keeps its name:

```scala mdoc:silent
val json: String                    = programmingInScala.toJsonString
val back: Either[SchemaError, Book] = json.fromJson[Book]
```

The string and the decoded value are the same as before:

```scala mdoc
println(json)
println(back)
```

In ZIO Blocks, `toJson` exists too, but it returns the JSON AST, a `zio.blocks.schema.json.Json`, rather than a `String`. Its `toString` renders the JSON with indentation:

```scala mdoc:silent
val ast: Json = programmingInScala.toJson
```

Printing the AST shows the same fields:

```scala mdoc
println(ast)
```

The error side of `fromJson` is a `SchemaError` instead of a `String`. Where the old code expected a `String`, map the error to its message with `SchemaError#message`:

```scala mdoc
val asString: Either[String, Book] = """{"title":"Zionomicon"}""".fromJson[Book].left.map(_.message)
println(asString)
```

### Annotations

zio-json annotations become `Modifier` annotations. They are part of the schema, so `toJsonString`, `fromJson` and every codec derived from the schema follow them.

#### You Wrote This

This is the old code, zio-json 1.x, with a naming strategy, a renamed field, an accepted old name, an excluded field, a discriminator and `snake_case` case names (not compiled here):

```scala
import zio.json._

@jsonMemberNames(SnakeCase)
case class Book(
  title: String,
  firstAuthor: String,
  year: Int,
  @jsonField("isbn_13") @jsonAliases("isbn") isbn: Option[String],
  tags: List[String],
  @jsonExclude searchScore: Double = 0.0
)

@jsonDiscriminator("type")
@jsonHintNames(SnakeCase)
sealed trait Format
object Format {
  case class Paperback(pages: Int) extends Format
  case class Ebook(sizeMb: Double) extends Format
}
```

#### You Write This

Each zio-json annotation has a `Modifier` counterpart, and the naming strategies are written as strings such as `"snake_case"`:

```scala mdoc:silent:reset
import zio.blocks.schema._

@Modifier.fieldNaming("snake_case")
case class Book(
  title: String,
  firstAuthor: String,
  year: Int,
  @Modifier.rename("isbn_13") @Modifier.alias("isbn") isbn: Option[String],
  tags: List[String],
  @Modifier.transient() searchScore: Double = 0.0
)
object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

@Modifier.discriminator("type")
@Modifier.caseNaming("snake_case")
sealed trait Format
object Format {
  case class Paperback(pages: Int) extends Format
  case class Ebook(sizeMb: Double) extends Format

  implicit val schema: Schema[Format] = Schema.derived
}

val programmingInScala =
  Book("Programming in Scala", "Martin Odersky", 2008, Some("9780981531601"), List("scala"), 0.93)
```

The plain extension methods follow the annotations. The renamed field and its alias both decode, and the transient field takes its default:

```scala mdoc
println(programmingInScala.toJsonString)
println((Format.Paperback(320): Format).toJsonString)
println("""{"title":"Programming in Scala","first_author":"Martin Odersky","year":2008,"isbn":"9780981531601"}""".fromJson[Book])
```

The full mapping, including the annotations not shown here:

| zio-json                       | ZIO Blocks                                          |
| ------------------------------ | --------------------------------------------------- |
| `@jsonField("name")`           | `@Modifier.rename("name")`                          |
| `@jsonAliases("a", "b")`       | `@Modifier.alias("a") @Modifier.alias("b")`         |
| `@jsonMemberNames(SnakeCase)`  | `@Modifier.fieldNaming("snake_case")`               |
| `@jsonDiscriminator("type")`   | `@Modifier.discriminator("type")`                   |
| `@jsonHint("name")`            | `@Modifier.rename("name")` on the case              |
| `@jsonHintNames(SnakeCase)`    | `@Modifier.caseNaming("snake_case")`                |
| `@jsonExclude`                 | `@Modifier.transient()`                             |
| `@jsonNoExtraFields`           | `@Modifier.noExtraFields()`                         |

### What Changes

- **`toJson` returns a `Json` value.** Use `toJsonString` for the `String` that zio-json's `toJson` returns.
- **`fromJson` returns `Either[SchemaError, A]`.** Use `SchemaError#message` where you need the old `String`.
- **Encoders in implicit scope are ignored.** In zio-json, an implicit `JsonEncoder[LocalDate]` and `JsonDecoder[LocalDate]` change every derived codec; register a `JsonCodec[LocalDate]` with `JsonCodecDeriver.withInstance` instead. See the [traps](#an-implicit-codec-in-scope-is-ignored).
- **`@Modifier.rename` replaces the name.** Like `@jsonField`, the Scala name is no longer accepted; add `@Modifier.alias` with the old name if clients still send it. See the [traps](#modifierrename-drops-the-scala-name).
- **Empty collections are left out.** zio-json writes `"tags":[]`; ZIO Blocks omits the field, and decodes a missing collection as empty where zio-json fails with `.tags(missing)`. See the [traps](#none-and-empty-collections-are-left-out). `None` is omitted by both.
- **Numbers must be JSON numbers.** zio-json decodes `"year":"2024"` into an `Int`; ZIO Blocks rejects it.
- **Transient fields need a default.** A `@Modifier.transient()` field must have a default value unless it is an `Option` or a collection; `Schema.derived` reports the missing default at compile time.

## Coming from jsoniter-scala

jsoniter-scala generates a codec at compile time from a `CodecMakerConfig`. In ZIO Blocks, `Schema.derived` describes the type, and a `JsonCodecDeriver` plays the role of the config: its `with*` methods mirror most of the options.

### Derived Codecs

`JsonCodecMaker.make` becomes `Schema.derived`, and `writeToString` and `readFromString` become methods on the codec or extension methods on the value.

#### You Wrote This

This is the old code, jsoniter-scala 2 (not compiled here):

```scala
import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros._

case class Book(title: String, firstAuthor: String, year: Int, isbn: Option[String], tags: List[String])
object Book {
  implicit val codec: JsonValueCodec[Book] =
    JsonCodecMaker.make(CodecMakerConfig.withFieldNameMapper(JsonCodecMaker.enforce_snake_case))
}

val json = writeToString(programmingInScala)
val back: Book = readFromString[Book](json) // throws JsonReaderException on bad input
```

#### You Write This

A `JsonCodecDeriver` takes the place of `CodecMakerConfig`, and `Schema#derive` takes the place of `JsonCodecMaker.make`. Keep the derived codec in the companion, as you kept the jsoniter-scala one:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.json._

case class Book(title: String, firstAuthor: String, year: Int, isbn: Option[String], tags: List[String])
object Book {
  implicit val schema: Schema[Book] = Schema.derived
  val codec: JsonCodec[Book]        = schema.derive(JsonCodecDeriver.withFieldNameMapper(NameMapper.SnakeCase))
}

val programmingInScala =
  Book("Programming in Scala", "Martin Odersky", 2008, Some("9780981531601"), List("scala"))
```

`JsonCodec#encodeToString` and `JsonCodec#decode` replace `writeToString` and `readFromString`. Bad input comes back as a `Left` instead of an exception:

```scala mdoc
println(Book.codec.encodeToString(programmingInScala))
println(Book.codec.decode(Book.codec.encodeToString(programmingInScala)))
println(Book.codec.decode("""{"title":"Zionomicon","first_author":42}"""))
```

`JsonCodec` also reads and writes `Array[Byte]`, `java.nio.ByteBuffer` and `java.io.InputStream`/`OutputStream`, the counterparts of `readFromArray`, `readFromByteBuffer` and `readFromStream`.

### Mapping `CodecMakerConfig`

Most `CodecMakerConfig` options that shape the JSON have a `JsonCodecDeriver` counterpart. jsoniter-scala's `requireCollectionFields` and `requireDefaultFields` also make the encoder write those fields, and so do their counterparts, so each is a single setting:

| `CodecMakerConfig`                                       | `JsonCodecDeriver`                                       |
| -------------------------------------------------------- | -------------------------------------------------------- |
| `withFieldNameMapper(JsonCodecMaker.enforce_snake_case)` | `withFieldNameMapper(NameMapper.SnakeCase)`              |
| `withAdtLeafClassNameMapper(f)`                          | `withCaseNameMapper(NameMapper.Custom(f))`               |
| `withDiscriminatorFieldName(Some("type"))` (default)     | `withDiscriminatorKind(DiscriminatorKind.Field("type"))` |
| `withDiscriminatorFieldName(None)`                       | `withDiscriminatorKind(DiscriminatorKind.Key)` (default) |
| `withTransientNone(b)`                                   | `withTransientNone(b)`                                   |
| `withTransientEmpty(b)`                                  | `withTransientEmptyCollection(b)`                        |
| `withTransientDefault(b)`                                | `withTransientDefaultValue(b)`                           |
| `withRequireCollectionFields(true)`                      | `withRequireCollectionFields(true)`                      |
| `withRequireDefaultFields(true)`                         | `withRequireDefaultValueFields(true)`                    |
| `withSkipUnexpectedFields(false)`                        | `withRejectExtraFields(true)`                            |
| `withCheckFieldDuplication(true)` (default)              | always on                                                |

Several options have no `JsonCodecDeriver` setting:

- `withIsStringified` and `@stringified`, which read and write numbers and booleans as JSON strings.
- `withMapAsArray`; maps are always JSON objects.
- The input limits: `withBigDecimalPrecision`, `withBigDecimalScaleLimit`, `withBigDecimalDigitsLimit`, `withBigIntDigitsLimit`, `withBitSetValueLimit`, `withMapMaxInsertNumber` and `withSetMaxInsertNumber`.
- `withRequireDiscriminatorFirst`; the ZIO Blocks decoder finds the discriminator field at any position.
- `withInlineOneValueClasses`, `withSkipNestedOptionValues`, `withCirceLikeObjectEncoding`, `withUseScalaEnumValueId`, `withTransientNull`, `withEncodingOnly` and `withDecodingOnly`.

If your API depends on one of the encoding options, register a custom codec for the affected type with `JsonCodecDeriver.withInstance`, as in the [circe section](#custom-codecs-for-one-type). `withAllowRecursiveTypes` has no counterpart because none is needed: `Schema.derived` handles recursive types.

### Performance

jsoniter-scala users usually pick it for speed. The repository's `benchmarks` project has a JMH benchmark, `JsonListOfRecordsBenchmark`, that reads and writes lists of records with jsoniter-scala, zio-json, ZIO Schema and ZIO Blocks, but the repository publishes no results. Run it on your own hardware and payloads before deciding.

### What Changes

- **The default sealed trait shape is different.** jsoniter-scala writes a `"type"` field by default; ZIO Blocks wraps the case in an object keyed by its name. Set `DiscriminatorKind.Field("type")` to keep your current JSON.
- **Bad input returns a `Left`.** `JsonCodec#decode` returns `Either[SchemaError, A]` instead of throwing.
- **The defaults for `None`, empty collections and default values match.** Both libraries leave them out of the output and fill them back in when they are missing from the input.
- **The discriminator can come anywhere.** jsoniter-scala requires the `"type"` field first by default; ZIO Blocks reads it from any position and writes it first.
- **Codecs in implicit scope are ignored.** A hand-written `JsonValueCodec` for a field type is not picked up; register it with `JsonCodecDeriver.withInstance`.

## Traps for Migrating Code

These five habits compile in ZIO Blocks but don't do what they did in your old library. None of them produces an error, so check for each one as you migrate.

### An Implicit Codec in Scope Is Ignored

In circe, zio-json and jsoniter-scala, an encoder and decoder for `LocalDate` in implicit scope change every derived codec. In ZIO Blocks, the deriver picks the codec for each field from the schema and never searches implicit scope, so the implicit below has no effect:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.json._
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val partnerDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

implicit val partnerDateCodec: JsonCodec[LocalDate] = new JsonCodec[LocalDate] {
  def decodeValue(in: JsonReader): LocalDate = LocalDate.parse(in.readString(), partnerDateFormat)
  def encodeValue(x: LocalDate, out: JsonWriter): Unit = out.writeVal(partnerDateFormat.format(x))
}

case class Order(title: String, orderedOn: LocalDate)
object Order {
  implicit val schema: Schema[Order] = Schema.derived
}

val order = Order("Zionomicon", LocalDate.of(2025, 3, 14))
```

The date still comes out in ISO-8601 form:

```scala mdoc
println(order.toJsonString)
```

Register the codec with `JsonCodecDeriver.withInstance` and derive through that deriver instead:

```scala mdoc
println(Order.schema.deriving(JsonCodecDeriver.withInstance[LocalDate](partnerDateCodec)).derive.encodeToString(order))
```

### `toJsonString` Ignores a Codec You Derived

`toJsonString`, `toJson` and `fromJson` always use `Schema#jsonCodec`, the codec from the default `JsonCodecDeriver`. A codec built with `Schema#derive` or `Schema#deriving` applies its settings only when you call it yourself:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.json._

case class Book(title: String, firstAuthor: String, year: Int, isbn: Option[String], tags: List[String])
object Book {
  implicit val schema: Schema[Book] = Schema.derived
  val snakeCaseCodec: JsonCodec[Book] = schema.derive(JsonCodecDeriver.withFieldNameMapper(NameMapper.SnakeCase))
}

val programmingInScala =
  Book("Programming in Scala", "Martin Odersky", 2008, Some("9780981531601"), List("scala"))
```

The extension method writes `firstAuthor`, and only the codec writes `first_author`:

```scala mdoc
println(programmingInScala.toJsonString)
println(Book.snakeCaseCodec.encodeToString(programmingInScala))
```

Keep a derived codec in one place, such as the companion, and call it everywhere you read or write that JSON. If the rule should apply to every caller, put it on the type with a `Modifier` annotation instead, which `toJsonString` does follow.

### `@Modifier.rename` Drops the Scala Name

`@Modifier.rename` replaces the field name for both encoding and decoding, so the Scala name is no longer accepted. Unknown fields are skipped by default, so for an `Option` field, input that still uses the Scala name decodes without an error and the value is lost:

```scala mdoc:silent:reset
import zio.blocks.schema._

case class Book(title: String, @Modifier.rename("isbn_13") isbn: Option[String])
object Book {
  implicit val schema: Schema[Book] = Schema.derived
}
```

The ISBN sent under its Scala name becomes `None`:

```scala mdoc
println("""{"title":"Programming in Scala","isbn":"9780981531601"}""".fromJson[Book])
```

Add the old name with `@Modifier.alias`, which the decoder accepts but the encoder never writes, the way `@jsonAliases` works in zio-json:

```scala mdoc:silent:reset
import zio.blocks.schema._

case class Book(title: String, @Modifier.rename("isbn_13") @Modifier.alias("isbn") isbn: Option[String])
object Book {
  implicit val schema: Schema[Book] = Schema.derived
}
```

Now both names decode:

```scala mdoc
println("""{"title":"Programming in Scala","isbn":"9780981531601"}""".fromJson[Book])
println("""{"title":"Programming in Scala","isbn_13":"9780981531601"}""".fromJson[Book])
```

### An Implicit `NameMapper` Does Nothing

circe users put a `Configuration` in implicit scope, so an implicit `NameMapper` in the companion looks like the natural translation. Nothing reads it:

```scala mdoc:silent:reset
import zio.blocks.schema._

case class Book(title: String, firstAuthor: String)
object Book {
  implicit val schema: Schema[Book]   = Schema.derived
  implicit val nameMapper: NameMapper = NameMapper.SnakeCase
}
```

The output keeps the Scala name `firstAuthor`:

```scala mdoc
println(Book("Programming in Scala", "Martin Odersky").toJsonString)
```

Use `@Modifier.fieldNaming("snake_case")` on the type, or `JsonCodecDeriver#withFieldNameMapper` with `Schema#derive`, as in the [circe section](#configured-derivation).

### `None` and Empty Collections Are Left Out

By default, the encoder leaves out a `None` field and an empty collection, and the decoder fills them back in as `None` and an empty collection when they are missing. circe writes them as `null` and `[]`, so a client that expects every key to be present breaks:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.json._

case class Book(title: String, firstAuthor: String, year: Int, isbn: Option[String], tags: List[String])
object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

val zionomicon = Book("Zionomicon", "John A. De Goes", 2024, None, Nil)
```

The default output has no `isbn` and no `tags`:

```scala mdoc
println(zionomicon.toJsonString)
```

To keep circe's shape, turn both off on the deriver:

```scala mdoc:silent
val circeShaped: JsonCodec[Book] =
  Book.schema.derive(JsonCodecDeriver.withTransientNone(false).withTransientEmptyCollection(false))
```

`None` is then written as `null` and an empty list as `[]`:

```scala mdoc
println(circeShaped.encodeToString(zionomicon))
```

`JsonCodecDeriver#withTransientDefaultValue(false)` does the same for fields that hold their default value. [Control Optional Fields, Empty Collections and Defaults](./customize-json-encoding.md#control-optional-fields-empty-collections-and-defaults) covers the matching decoder settings, which make missing fields an error.

## What You Gain and What Is Different

You gain:

- **One derivation for every format.** The `Schema` that gives you JSON also gives you YAML, Avro, MessagePack, TOON and the other formats in [One Type, Many Formats](./one-type-many-formats.md), with the same annotations.
- **Configuration you can see.** Rules are values on a `JsonCodecDeriver` or annotations on the type, not instances found through implicit search, so the codec a call site uses is the one it names.
- **Errors with paths, never exceptions.** `fromJson` and `JsonCodec#decode` return `Either[SchemaError, A]`, and the message names the field that failed.
- **No third-party JSON dependencies.** The schema module runs on Scala 2.13 and 3, on the JVM and Scala.js, and needs no JSON library.

What is different:

- **Implicit codecs are not part of derivation.** Overrides go through `JsonCodecDeriver.withInstance`.
- **The extension methods use the default codec.** A configured codec must be called directly.
- **`None` and empty collections are omitted by default**, as in jsoniter-scala and unlike circe.
- **Some jsoniter-scala options have no counterpart**, such as stringified numbers and maps as arrays.

## Migration Checklist

1. Add `zio-blocks-schema` (or `zio-blocks-next-schema` on Scala 3.7 and newer) and `import zio.blocks.schema._`.
2. Replace every `deriveCodec`, `derives Codec.AsObject`, `DeriveJsonCodec.gen` or `JsonCodecMaker.make` with `implicit val schema: Schema[A] = Schema.derived` in the companion.
3. Replace `asJson.noSpaces`, zio-json `toJson` and `writeToString` with `toJsonString`, and `decode`, `fromJson` and `readFromString` with `fromJson` or `JsonCodec#decode`.
4. Replace error handling on `io.circe.Error`, `String` or `JsonReaderException` with `SchemaError`.
5. Turn a circe `Configuration` or a jsoniter-scala `CodecMakerConfig` into a `JsonCodecDeriver`, derive codecs with `Schema#derive`, and call those codecs directly instead of `toJsonString`.
6. Replace zio-json annotations with their `Modifier` counterparts, and add `@Modifier.alias` for any renamed field whose Scala name clients still send.
7. Replace each implicit codec for a field type with a `JsonCodec` registered through `JsonCodecDeriver.withInstance`, and delete the implicit.
8. Decide whether `None` and empty collections may be omitted; if not, set `withTransientNone(false)` and `withTransientEmptyCollection(false)`.
9. If you came from jsoniter-scala, set `DiscriminatorKind.Field("type")` to keep your sealed trait JSON.
10. Round-trip a sample of real payloads through the new codecs and compare the JSON with what the old library writes.

## Putting It Together

This is the catalog fully migrated: `Schema.derived` for every type, one `JsonCodecDeriver` that writes `snake_case` names, a `"type"` field, explicit `null`s and empty lists, and dates as `dd/MM/yyyy`, and a renamed ISBN that still accepts its old name:

```scala mdoc:embed:schema-examples/src/main/scala/jsonlibrarymigration/BookCatalogMigratedExample.scala
```

## Running the Examples

The complete example is a runnable file in the `schema-examples` project. Clone the repository and change into the project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/schema-examples
```

<details>
  <summary>schema-examples/src/main/scala/jsonlibrarymigration/BookCatalogMigratedExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/jsonlibrarymigration/BookCatalogMigratedExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain jsonlibrarymigration.BookCatalogMigratedExample"
```

To check that the example compiles without running it:

```bash
sbt compile
```

## See Also

- [Your First Schema](./your-first-schema.md): derive a schema, round-trip JSON and read decode errors.
- [How to Customize JSON Field Names, ADT Encoding and Optional Fields](./customize-json-encoding.md): every naming, discriminator and optional-field option.
- [How to Override the Codec for One Type Everywhere](./override-codec-for-one-type.md): custom codecs with `Deriver#withInstance`.
- [JSON](../reference/schema/built-in-codecs/json/index.md): the JSON codec reference.
- [Migrating from ZIO Schema](./zio-schema-migration.md): the migration guide for ZIO Schema 1.x users.
