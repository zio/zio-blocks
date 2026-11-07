---
id: customize-json-encoding
title: "How to Customize JSON Field Names, ADT Encoding and Optional Fields"
description: "Change the JSON shape a Schema produces: snake_case names, renamed fields, type discriminators, null handling and strict decoding."
keywords:
  - "JSON Customization"
  - "Field Naming"
  - "Sealed Trait Encoding"
  - "JsonCodecDeriver"
  - "Modifier"
  - "DiscriminatorKind"
---

In this guide, we change the JSON that a derived `Schema` reads and writes until it matches an API contract we don't control. By the end, you will know how to rename fields to `snake_case`, rename one field while still accepting its old name, tag sealed trait cases with a `"type"` field or with no tag at all, decide whether `None` is written as `null`, leave fields out of the JSON, and reject unknown fields.

Each section is a short recipe: the problem, the code, and the JSON it produces. We use two tools throughout — [`Modifier`](../reference/schema/modifier.md) annotations, which change the schema of one type, and `JsonCodecDeriver` settings, which change every type a codec touches — and finish with a comparison to help you choose between them.

This guide covers:

- Renaming every field with a naming strategy
- Renaming one field and accepting old names
- Tagging sealed trait cases with a type field
- Encoding sealed traits without a tag
- Encoding case-object enums as strings
- Controlling optional fields, empty collections and default values
- Rejecting unknown fields
- Leaving a field out of the JSON
- Choosing between global settings and per-type modifiers

## The Problem

The default JSON format of ZIO Blocks uses Scala field names as they are, wraps each sealed trait case in an object keyed by the case name, and leaves out `None` values and empty lists. That's a good default for data we write and read ourselves, but the services we talk to often expect something else: `snake_case` keys, a `"type"` field next to the case's own fields, or an explicit `null` for every missing value.

When the format is fixed by someone else, the usual fallback is to write the JSON by hand. For example, to send a book to a service that expects `snake_case` keys and no empty arrays, we might write:

```scala mdoc:compile-only
case class Author(fullName: String)
case class Book(title: String, publishedYear: Int, isbn13: Option[String], author: Author, tags: List[String])

def quote(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

def authorToJson(author: Author): String =
  s"""{"full_name":${quote(author.fullName)}}"""

def bookToJson(book: Book): String = {
  val fields = List(
    Some(s""""title":${quote(book.title)}"""),
    Some(s""""published_year":${book.publishedYear}"""),
    book.isbn13.map(isbn => s""""isbn13":${quote(isbn)}"""),
    Some(s""""author":${authorToJson(book.author)}"""),
    if (book.tags.isEmpty) None else Some(s""""tags":${book.tags.map(quote).mkString("[", ",", "]")}""")
  )
  fields.flatten.mkString("{", ",", "}")
}
```

This approach repeats every field name a second time, has no decoder at all, and silently drifts from the case class the next time someone adds a field. Each new type, and each sealed trait with its own tagging rule, needs another hand-written function.

In this guide, we keep `Schema.derived` and change the format instead, with one annotation or one deriver setting per rule.

## Prerequisites

We use the same schema module as the [Your First Schema](./your-first-schema.md) tutorial:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "@VERSION@"

// Scala 3.9 and newer (also needs scalacOptions += "-experimental")
libraryDependencies += "dev.zio" %% "zio-blocks-next-schema" % "@VERSION@"
```

This guide assumes you can derive a schema with `Schema.derived` and round-trip a value with `toJsonString` and `fromJson`, as shown in [Your First Schema](./your-first-schema.md). Every example uses two imports: `zio.blocks.schema._` brings in `Schema`, `Modifier` and `NameMapper`, and `zio.blocks.schema.json._` brings in `JsonCodecDeriver`, `JsonCodec` and `DiscriminatorKind`.

## The Book Catalog Model

Our running example is a small catalog of Scala books. A `Book` has a nested `Author`, an optional ISBN, a list of tags, an optional edition number and a `language` field with a default value:

```scala mdoc:silent
import zio.blocks.schema._
import zio.blocks.schema.json._

case class Author(fullName: String)
object Author {
  implicit val schema: Schema[Author] = Schema.derived
}

case class Book(
  title: String,
  publishedYear: Int,
  isbn13: Option[String],
  author: Author,
  tags: List[String],
  edition: Option[Int],
  language: String = "en"
)
object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

val fpInScala = Book(
  "Functional Programming in Scala",
  2014,
  Some("9781617290657"),
  Author("Paul Chiusano"),
  List("scala", "functional-programming"),
  Some(1)
)
```

With no customization, `toJsonString` writes every field under its Scala name and leaves out `language` because it holds the default value:

```scala mdoc
println(fpInScala.toJsonString)
```

This is the shape we change in the rest of the guide.

## Rename Every Field With a Naming Strategy

The most common request is a different naming convention, such as `snake_case` for a Python or Ruby service. A `NameMapper` converts each Scala field name; the built-in strategies are `NameMapper.SnakeCase`, `NameMapper.KebabCase`, `NameMapper.CamelCase`, `NameMapper.PascalCase`, `NameMapper.Identity` (the default) and `NameMapper.Custom(f)`, which applies any `String => String` function.

To apply a strategy to every type in a codec, we derive a codec from the schema with `JsonCodecDeriver#withFieldNameMapper`. `Schema#derive` returns a `JsonCodec[Book]`:

```scala mdoc:silent
val snakeCaseCodec: JsonCodec[Book] =
  Book.schema.derive(JsonCodecDeriver.withFieldNameMapper(NameMapper.SnakeCase))
```

We call `JsonCodec#encodeToString` and `JsonCodec#decode` on the codec directly:

```scala mdoc
println(snakeCaseCodec.encodeToString(fpInScala))
snakeCaseCodec.decode(snakeCaseCodec.encodeToString(fpInScala)) == Right(fpInScala)
```

The mapper reaches the nested `Author` too, so `fullName` becomes `full_name`. Digits don't start a new word, so `isbn13` stays as it is.

:::warning
`toJsonString` and `fromJson` always use the default codec of the schema. A codec built with `Schema#derive` applies its settings only when you call it yourself, so keep it in one place — for example, as a `val` in the companion object — and use it everywhere you read or write that JSON.
:::

When only one type needs the convention, annotate it with `Modifier.fieldNaming` instead. The annotation becomes part of the schema, so the plain `toJsonString` picks it up. It accepts `"snake_case"`, `"kebab-case"`, `"camelCase"`, `"PascalCase"` and `"identity"`:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.json._

case class Author(fullName: String)
object Author {
  implicit val schema: Schema[Author] = Schema.derived
}

@Modifier.fieldNaming("snake_case")
case class Book(title: String, publishedYear: Int, isbn13: Option[String], author: Author)
object Book {
  implicit val schema: Schema[Book] = Schema.derived
}
```

Now we encode a book with the plain extension method:

```scala mdoc
println(Book("Programming in Scala", 2008, Some("9780981531601"), Author("Martin Odersky")).toJsonString)
```

The annotation renames only the fields declared directly in `Book`. The nested `Author` keeps `fullName`, because its own schema has no annotation. A strategy name the library doesn't know, such as `"SCREAMING_SNAKE"`, fails with an `IllegalArgumentException` when the codec is derived, not while decoding.

## Rename One Field and Accept Its Old Name

Sometimes a single field needs a different name — the API calls the ISBN `isbn`, not `isbn13` — and older clients still send a previous name. `Modifier.rename` sets the name used for both encoding and decoding, and `Modifier.alias` adds an extra name that the decoder accepts but the encoder never writes:

```scala mdoc:silent:reset
import zio.blocks.schema._

case class Book(
  title: String,
  @Modifier.rename("isbn")
  @Modifier.alias("isbn_13")
  isbn13: Option[String]
)
object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

val pis = Book("Programming in Scala", Some("9780981531601"))
```

The encoder writes the renamed key:

```scala mdoc
println(pis.toJsonString)
```

The decoder accepts both the new name and the alias:

```scala mdoc
"""{"title":"Programming in Scala","isbn":"9780981531601"}""".fromJson[Book]
"""{"title":"Programming in Scala","isbn_13":"9780981531601"}""".fromJson[Book]
```

The original Scala name is no longer one of the accepted names. Because `isbn13` is optional and unknown fields are ignored by default, input that still uses it decodes without an error, but the value is lost:

```scala mdoc
"""{"title":"Programming in Scala","isbn13":"9780981531601"}""".fromJson[Book]
```

If clients may still send the Scala name, add it as an alias too, for example `@Modifier.alias("isbn13")`. `Modifier.rename` also wins over any naming strategy from the previous section, so it is the way to make one exception to a `snake_case` rule.

## Tag Sealed Trait Cases With a Type Field

A sealed trait needs a tag in the JSON so the decoder knows which case to build. Model the formats a book is sold in:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.json._

sealed trait Format
object Format {
  case class Paperback(pages: Int)    extends Format
  case class Ebook(sizeMb: Double)    extends Format
  case class Audiobook(minutes: Int)  extends Format

  implicit val schema: Schema[Format] = Schema.derived
}

val paperback: Format = Format.Paperback(320)
```

By default, the case name is the key of a wrapper object around the case's fields. This is `DiscriminatorKind.Key`:

```scala mdoc
println(paperback.toJsonString)
```

Many APIs expect a flat object with a `"type"` field instead. `JsonCodecDeriver#withDiscriminatorKind` with `DiscriminatorKind.Field` produces that shape, and `JsonCodecDeriver#withCaseNameMapper` changes how case names are written:

```scala mdoc:silent
val typeFieldCodec: JsonCodec[Format] =
  Format.schema.derive(
    JsonCodecDeriver
      .withDiscriminatorKind(DiscriminatorKind.Field("type"))
      .withCaseNameMapper(NameMapper.SnakeCase)
  )
```

The new codec writes the case name into a `"type"` field and reads it back:

```scala mdoc
println(typeFieldCodec.encodeToString(paperback))
typeFieldCodec.decode("""{"type":"audiobook","minutes":600}""")
```

The decoder looks up the `"type"` field wherever it appears in the object, so it doesn't need to come first.

The same choice can live on the sealed trait itself. `Modifier.discriminator` selects a field discriminator, `Modifier.caseNaming` applies a naming strategy to the case names, and `Modifier.rename` on a case overrides one name:

```scala mdoc:silent:reset
import zio.blocks.schema._

@Modifier.discriminator("type")
@Modifier.caseNaming("snake_case")
sealed trait Format
object Format {
  case class Paperback(pages: Int)   extends Format
  case class Ebook(sizeMb: Double)   extends Format
  @Modifier.rename("audio")
  case class Audiobook(minutes: Int) extends Format

  implicit val schema: Schema[Format] = Schema.derived
}

val formats: List[Format] = List(Format.Paperback(320), Format.Ebook(4.5), Format.Audiobook(600))
```

Because the annotations are part of the schema, `toJsonString` uses them:

```scala mdoc
formats.foreach(format => println(format.toJsonString))
```

## Encode Sealed Traits Without a Tag

Some APIs send a case's fields with no tag at all and expect the reader to work out the case from the fields that are present. `DiscriminatorKind.None` writes each case as a plain object and, when decoding, tries the cases in declaration order until one succeeds:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.json._

sealed trait Format
object Format {
  case class Paperback(pages: Int)   extends Format
  case class Ebook(sizeMb: Double)   extends Format
  case class Audiobook(minutes: Int) extends Format

  implicit val schema: Schema[Format] = Schema.derived
}

val untaggedCodec: JsonCodec[Format] =
  Format.schema.derive(JsonCodecDeriver.withDiscriminatorKind(DiscriminatorKind.None))
```

Encoding drops the tag, and decoding picks the first case whose fields fit:

```scala mdoc
println(untaggedCodec.encodeToString(Format.Paperback(320)))
untaggedCodec.decode("""{"minutes":600}""")
```

`{"minutes":600}` is not a valid `Paperback` or `Ebook`, because their required fields are missing, so the decoder moves on and builds an `Audiobook`. Input that fits no case fails with one generic message:

```scala mdoc
untaggedCodec.decode("""{"chapters":12}""") match {
  case Left(error) => println(error.message)
  case Right(format) => println(s"Decoded: $format")
}
```

:::warning
Without a tag, the order of the cases decides the result. A case whose fields are all optional, or a case whose fields are a subset of a later case's fields, matches input meant for the later case, and the decoder never reaches it. Use `DiscriminatorKind.None` only when every case has a required field that no earlier case accepts, and prefer a type field whenever you control the format.
:::

## Encode Case-Object Enums as Strings

A sealed trait whose cases are all case objects is an enumeration, and the JSON codec writes each value as a plain string by default:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.json._

sealed trait Availability
object Availability {
  case object InStock    extends Availability
  case object OutOfPrint extends Availability
  case object Preorder   extends Availability

  implicit val schema: Schema[Availability] = Schema.derived
}

val outOfPrint: Availability = Availability.OutOfPrint
```

The plain encoder writes the case name as a string, a case name mapper changes its spelling, and `JsonCodecDeriver#withEnumValuesAsStrings` set to `false` switches back to the wrapper-object form used for other sealed traits:

```scala mdoc
println(outOfPrint.toJsonString)
println(Availability.schema.derive(JsonCodecDeriver.withCaseNameMapper(NameMapper.SnakeCase)).encodeToString(outOfPrint))
println(Availability.schema.derive(JsonCodecDeriver.withEnumValuesAsStrings(false)).encodeToString(outOfPrint))
```

`@Modifier.caseNaming("snake_case")` on the sealed trait gives the same `snake_case` strings through `toJsonString`.

## Control Optional Fields, Empty Collections and Defaults

By default, the encoder leaves out three kinds of fields, and the decoder fills them back in when they are missing:

| Field value                        | Encoder setting (default)                                       | Decoder setting (default)                                               |
| ---------------------------------- | --------------------------------------------------------------- | ----------------------------------------------------------------------- |
| `None` in an `Option` field        | `JsonCodecDeriver#withTransientNone(true)`: left out            | `JsonCodecDeriver#withRequireOptionFields(false)`: may be missing       |
| An empty collection                | `JsonCodecDeriver#withTransientEmptyCollection(true)`: left out | `JsonCodecDeriver#withRequireCollectionFields(false)`: may be missing   |
| A value equal to the field default | `JsonCodecDeriver#withTransientDefaultValue(true)`: left out    | `JsonCodecDeriver#withRequireDefaultValueFields(false)`: may be missing |

Return to the book model from the start of the guide and add a book whose ISBN and edition are unknown and which has no tags yet:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.json._

case class Author(fullName: String)
object Author {
  implicit val schema: Schema[Author] = Schema.derived
}

case class Book(
  title: String,
  publishedYear: Int,
  isbn13: Option[String],
  author: Author,
  tags: List[String],
  edition: Option[Int],
  language: String = "en"
)
object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

val zionomicon = Book("Zionomicon", 2024, None, Author("John A. De Goes"), Nil, None)
```

The default codec leaves out `isbn13`, `tags`, `edition` and `language`:

```scala mdoc
println(zionomicon.toJsonString)
```

If the receiving service expects every field to be present, turn the three encoder settings off. `None` is then written as `null`, an empty list as `[]`, and the default value as itself:

```scala mdoc:silent
val explicitCodec: JsonCodec[Book] =
  Book.schema.derive(
    JsonCodecDeriver
      .withTransientNone(false)
      .withTransientEmptyCollection(false)
      .withTransientDefaultValue(false)
  )
```

The same book now comes out with every field, and it still decodes back to the original value:

```scala mdoc
println(explicitCodec.encodeToString(zionomicon))
explicitCodec.decode(explicitCodec.encodeToString(zionomicon)) == Right(zionomicon)
```

The decoder settings work the other way: they make decoding strict. With `JsonCodecDeriver#withRequireOptionFields` set to `true`, an `Option` field must appear in the input, either with a value or as `null`:

```scala mdoc:silent
val strictCodec: JsonCodec[Book] =
  Book.schema.derive(JsonCodecDeriver.withRequireOptionFields(true))
```

Decoding the default output of the previous step now fails, because `isbn13` and `edition` are missing:

```scala mdoc
strictCodec.decode("""{"title":"Zionomicon","publishedYear":2024,"author":{"fullName":"John A. De Goes"}}""") match {
  case Left(error) => println(error.message)
  case Right(book) => println(s"Decoded: $book")
}
```

`JsonCodecDeriver#withRequireCollectionFields` does the same for collections, and `JsonCodecDeriver#withRequireDefaultValueFields` ignores field defaults when decoding, so a field such as `language` must be present even though it has a default. Both settings also change the encoder: with `withRequireCollectionFields(true)` the encoder writes empty collections, and with `withRequireDefaultValueFields(true)` it writes default values, instead of leaving them out. Set them only when you want that output too.

## Reject Unknown Fields

By default, the decoder skips JSON fields that don't belong to the type. That keeps old readers working when a writer adds fields, but it also hides typos in hand-written input. `Modifier.noExtraFields` makes the decoder of one type reject unknown fields:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.json._

@Modifier.noExtraFields()
case class Author(fullName: String)
object Author {
  implicit val schema: Schema[Author] = Schema.derived
}

case class Book(title: String, author: Author)
object Book {
  implicit val schema: Schema[Book] = Schema.derived
}
```

`Book` still skips an unknown `subtitle`, while `Author` rejects a misspelled field and reports where it is:

```scala mdoc
"""{"title":"Zionomicon","subtitle":"A Guide","author":{"fullName":"John A. De Goes"}}""".fromJson[Book]

"""{"title":"Zionomicon","author":{"fullname":"John A. De Goes"}}""".fromJson[Book] match {
  case Left(error) => println(error.message)
  case Right(book) => println(s"Decoded: $book")
}
```

To reject unknown fields in every type a codec handles, use `JsonCodecDeriver#withRejectExtraFields` instead:

```scala mdoc
Book.schema
  .derive(JsonCodecDeriver.withRejectExtraFields(true))
  .decode("""{"title":"Zionomicon","subtitle":"A Guide","author":{"fullName":"John A. De Goes"}}""") match {
  case Left(error) => println(error.message)
  case Right(book) => println(s"Decoded: $book")
}
```

## Leave a Field Out of the JSON

Some fields belong to the Scala value but not to the JSON: a relevance score computed in memory, or an old identifier that we still accept from clients but no longer send. `Modifier.transient` leaves a field out of the JSON in both directions, and `Modifier.encodeTransient` leaves it out only when encoding:

```scala mdoc:silent:reset
import zio.blocks.schema._

case class Book(
  title: String,
  @Modifier.transient() searchScore: Double = 0.0,
  @Modifier.encodeTransient() legacyId: Option[Long]
)
object Book {
  implicit val schema: Schema[Book] = Schema.derived
}
```

The encoder writes neither field:

```scala mdoc
println(Book("Zionomicon", 0.93, Some(42L)).toJsonString)
```

When decoding, a transient field ignores the input and takes its default value, while an encode-transient field is still read:

```scala mdoc
"""{"title":"Zionomicon","searchScore":0.5,"legacyId":42}""".fromJson[Book]
```

Because the decoder has to produce a value for a field it never reads, a transient or encode-transient field needs a default value unless it is an `Option` or a collection, which fall back to `None` or an empty collection. Without one, `Schema.derived` fails at compile time with a `Missing default value for transient or encodeTransient field` error that names the field and its type, so the mistake never reaches production.

## Choose Between Global Settings and Per-Type Modifiers

Most rules in this guide can be applied in two ways. The table below sums up where each one lives:

| Rule                               | Per-type modifier                                      | Global deriver setting                                                 |
| ---------------------------------- | ------------------------------------------------------ | ---------------------------------------------------------------------- |
| Field naming strategy              | `@Modifier.fieldNaming(...)`                           | `JsonCodecDeriver#withFieldNameMapper(...)`                            |
| One field or case name             | `@Modifier.rename(...)`                                | —                                                                      |
| Extra accepted names               | `@Modifier.alias(...)`                                 | —                                                                      |
| Type field discriminator           | `@Modifier.discriminator(...)`                         | `JsonCodecDeriver#withDiscriminatorKind(DiscriminatorKind.Field(...))` |
| No discriminator                   | —                                                      | `JsonCodecDeriver#withDiscriminatorKind(DiscriminatorKind.None)`       |
| Case naming strategy               | `@Modifier.caseNaming(...)`                            | `JsonCodecDeriver#withCaseNameMapper(...)`                             |
| Enums as wrapper objects           | —                                                      | `JsonCodecDeriver#withEnumValuesAsStrings(false)`                      |
| Optional, empty and default fields | —                                                      | `JsonCodecDeriver#withTransient*` and `JsonCodecDeriver#withRequire*`  |
| Leave a field out                  | `@Modifier.transient()`, `@Modifier.encodeTransient()` | —                                                                      |
| Reject unknown fields              | `@Modifier.noExtraFields()`                            | `JsonCodecDeriver#withRejectExtraFields(true)`                         |

Use a modifier when the rule describes the type itself — "this API always calls the ISBN `isbn`". The modifier travels with the schema, so `toJsonString`, `fromJson` and every codec derived from the schema agree on it, and nobody can forget to pass a setting.

Use a deriver setting when the rule describes the service you are talking to — "this partner wants `snake_case` and `null` everywhere". One codec applies the rule to every nested type without annotating each one, and a different codec for a different service can use different settings for the same types.

When both are present, the modifier wins: a type annotated with `@Modifier.fieldNaming("snake_case")` keeps `snake_case` names even in a codec built with `JsonCodecDeriver#withFieldNameMapper(NameMapper.KebabCase)`, and a `@Modifier.discriminator` on a sealed trait overrides `JsonCodecDeriver#withDiscriminatorKind`. `@Modifier.noExtraFields()` can only turn rejection on, never off.

To see the precedence in action, annotate a type with one strategy and build a codec with another:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.json._

@Modifier.fieldNaming("snake_case")
case class Book(title: String, publishedYear: Int)
object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

val kebabCodec: JsonCodec[Book] =
  Book.schema.derive(JsonCodecDeriver.withFieldNameMapper(NameMapper.KebabCase))
```

The codec still writes `snake_case` names, because the annotation on `Book` takes precedence over the deriver setting:

```scala mdoc
println(kebabCodec.encodeToString(Book("Zionomicon", 2024)))
```

## Putting It Together

This example combines the recipes into one API format: `snake_case` field names, a renamed ISBN with an alias, a `"type"` field for book formats, explicit `null` values and strict decoding of unknown fields:

```scala mdoc:embed:schema-examples/src/main/scala/customizejsonencoding/CompleteExample.scala
```

## Running the Examples

All examples in this guide have corresponding runnable Scala files in the `schema-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/schema-examples
```

Each example runs on its own, so you can jump straight to the recipe you need.

### Rename Every Field With a Naming Strategy

This example applies `snake_case` names globally with `JsonCodecDeriver#withFieldNameMapper` and to one type with `@Modifier.fieldNaming`:

<details>
  <summary>schema-examples/src/main/scala/customizejsonencoding/FieldNamingExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/customizejsonencoding/FieldNamingExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain customizejsonencoding.FieldNamingExample"
```

### Rename One Field and Accept Its Old Name

This example renames one field with `@Modifier.rename`, accepts an old name with `@Modifier.alias`, and shows that the original Scala name is no longer read:

<details>
  <summary>schema-examples/src/main/scala/customizejsonencoding/RenameAndAliasExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/customizejsonencoding/RenameAndAliasExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain customizejsonencoding.RenameAndAliasExample"
```

### Tag Sealed Trait Cases With a Type Field

This example compares the default wrapper-key encoding with a `"type"` field, set first through `JsonCodecDeriver` and then through modifiers on the sealed trait:

<details>
  <summary>schema-examples/src/main/scala/customizejsonencoding/DiscriminatorFieldExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/customizejsonencoding/DiscriminatorFieldExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain customizejsonencoding.DiscriminatorFieldExample"
```

### Encode Sealed Traits Without a Tag

This example encodes sealed trait cases without a tag using `DiscriminatorKind.None` and decodes them by trying each case in order:

<details>
  <summary>schema-examples/src/main/scala/customizejsonencoding/NoDiscriminatorExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/customizejsonencoding/NoDiscriminatorExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain customizejsonencoding.NoDiscriminatorExample"
```

### Encode Case-Object Enums as Strings

This example writes a case-object enumeration as a string, with a case name mapper, and as a wrapper object:

<details>
  <summary>schema-examples/src/main/scala/customizejsonencoding/EnumStringsExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/customizejsonencoding/EnumStringsExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain customizejsonencoding.EnumStringsExample"
```

### Control Optional Fields, Empty Collections and Defaults

This example writes `None`, empty lists and default values explicitly, and makes decoding require every `Option` field:

<details>
  <summary>schema-examples/src/main/scala/customizejsonencoding/OptionalFieldsExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/customizejsonencoding/OptionalFieldsExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain customizejsonencoding.OptionalFieldsExample"
```

### Reject Unknown Fields

This example rejects unknown fields for one type with `@Modifier.noExtraFields` and for every type with `JsonCodecDeriver#withRejectExtraFields`:

<details>
  <summary>schema-examples/src/main/scala/customizejsonencoding/RejectExtraFieldsExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/customizejsonencoding/RejectExtraFieldsExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain customizejsonencoding.RejectExtraFieldsExample"
```

### Leave a Field Out of the JSON

This example leaves one field out in both directions with `@Modifier.transient` and another only when encoding with `@Modifier.encodeTransient`:

<details>
  <summary>schema-examples/src/main/scala/customizejsonencoding/TransientFieldsExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/customizejsonencoding/TransientFieldsExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain customizejsonencoding.TransientFieldsExample"
```

### The Complete Example

The complete example from "Putting It Together" combines every recipe into one API format:

```bash
sbt "runMain customizejsonencoding.CompleteExample"
```

To compile every example at once without running them:

```bash
sbt compile
```

## Going Further

- [Modifier](../reference/schema/modifier.md) documents every modifier, including the JSON modifiers used in this guide.
- [JSON Configuration](../reference/schema/built-in-codecs/json/json-config.md) covers `WriterConfig` for pretty-printing, `ReaderConfig` and `NameMapper`.
- [How to Tune JSON Encoding and Decoding for Throughput](./tune-json-throughput.md) shows how to decode from bytes and streams, reuse codecs and size buffers for speed.
- [Your First Schema](./your-first-schema.md) is the tutorial this guide builds on.
- To change how one type is encoded everywhere, such as a custom `LocalDate` format, see the instance overrides in [Type-Class Derivation](../reference/schema/type-class-derivation.md).
