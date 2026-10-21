---
id: validated-newtypes
title: "How to Define Validated Newtypes and Collect Every Validation Error"
description: "Wrap ISBNs, titles and prices in types that only a check can build, enforce the check in every codec, and report all errors at once."
keywords:
  - "Parse, Don't Validate"
  - "Newtype"
  - "Smart Constructor"
  - "Error Accumulation"
  - "Schema#transform"
  - "SchemaError"
---

In this guide, we build the input model for a bookstore API that accepts new book submissions as JSON. Each value that has rules (the ISBN, the title, the price and the page count) gets its own small type, a *newtype*, that can only be created by passing its check. We declare each check once, on the type's `Schema`, so every codec derived from that schema enforces it. By the end, you will have a decoder that turns bad input into a list of every problem, each with the field it belongs to, ready to send back as the body of a `400 Bad Request` response.

We take an incremental approach:

- Wrap each value in a newtype with a private constructor and a smart constructor (a function that checks its input before building the value)
- Attach the smart constructor to the type's `Schema` with `Schema#transform`
- Decode JSON and see the first failure with its field path
- Collect every failure in one pass with `Schema#fromDynamicValue`
- Turn a `SchemaError` into a structured error response
- Reuse the same rules for a second format, TOON

## The Problem

A `Book` that stores its ISBN, title and price as `String` and `BigDecimal` accepts any string and any number. An ISBN of `"123"`, a blank title and a negative price all type-check, so bad values travel deep into the program before anything notices.

The usual fix is a `validate` function, and that is where the trouble starts. Nothing forces anyone to call it, so the HTTP handler calls it, the CSV importer forgets to, and a test fixture builds a `Book` directly. The rules end up copied or skipped, and a function that receives a `Book` cannot tell whether it was checked.

For example, a typical model and its validation look like this:

```scala mdoc:compile-only
final case class Book(isbn: String, title: String, price: BigDecimal, pages: Int)

def validate(book: Book): Either[String, Book] =
  if (book.isbn.length != 13) Left(s"bad ISBN: ${book.isbn}")
  else if (book.title.trim.isEmpty) Left("blank title")
  else if (book.price <= 0) Left(s"bad price: ${book.price}")
  else if (book.pages <= 0) Left(s"bad page count: ${book.pages}")
  else Right(book)

// Compiles and runs: nothing stops us from skipping validate.
val unchecked = Book("123", "   ", BigDecimal(-45), 0)
```

The checks stop at the first problem, report no field path, and live apart from the type they protect.

In this guide, we follow the "parse, don't validate" approach instead: we turn raw input into a type that can only hold valid values, so having a `Book` is proof that it was checked.

## Prerequisites

This guide builds on [Your First Schema](./your-first-schema.md): you should know how to derive a `Schema` for a case class and decode JSON with it. Add the schema module, and the TOON module used in one section, to your `build.sbt`:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-schema"      % "@VERSION@",
  "dev.zio" %% "zio-blocks-schema-toon" % "@VERSION@"
)

// Scala 3.9 and newer
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-next-schema"      % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-schema-toon" % "@VERSION@"
)
scalacOptions += "-experimental"
```

On Scala 2.13, also add this compiler option. It makes the generated `apply` and `copy` methods of a case class as private as its constructor, which Scala 3 does by default. Without it, `Isbn("123")` compiles on Scala 2.13 and skips the check:

```scala
scalacOptions += "-Xsource-features:case-apply-copy-access"
```

We use these imports throughout:

```scala mdoc:silent
import zio.blocks.schema._
import zio.blocks.schema.json._
```

## Wrap Each Value in a Validated Newtype

A newtype here is a one-field case class with a private constructor. The only public way to build one is its smart constructor, `parse`, which returns `Either[SchemaError, A]`: `Right` with the value when the input passes the check, or `Left` with a `SchemaError` that explains why it failed. `SchemaError.validationFailed` builds that error from a message.

Parsing can also normalize the input. `Isbn.parse` accepts `978-1-61729-065-7` with hyphens, verifies the 13 digits and the check digit, and stores the bare digits. Each companion object then turns the smart constructor into a `Schema` with `Schema#transform`, which takes a function from the underlying type (`to`) and a function back (`from`):

```scala mdoc:silent
final case class Isbn private (value: String)

object Isbn {
  def parse(input: String): Either[SchemaError, Isbn] = {
    val digits = input.filterNot(c => c == '-' || c == ' ')
    if (digits.length != 13 || !digits.forall(_.isDigit))
      Left(SchemaError.validationFailed(s"ISBN must have 13 digits, got '$input'"))
    else {
      val weighted = digits.init.zipWithIndex.map { case (c, i) => c.asDigit * (if (i % 2 == 0) 1 else 3) }
      if ((10 - weighted.sum % 10) % 10 == digits.last.asDigit) Right(new Isbn(digits))
      else Left(SchemaError.validationFailed(s"ISBN check digit is wrong in '$input'"))
    }
  }

  implicit val schema: Schema[Isbn] =
    Schema[String].transform(s => parse(s).fold(e => throw e, identity), _.value)
}

final case class Title private (value: String)

object Title {
  def parse(input: String): Either[SchemaError, Title] = {
    val trimmed = input.trim
    if (trimmed.isEmpty) Left(SchemaError.validationFailed("title must not be blank"))
    else if (trimmed.length > 200)
      Left(SchemaError.validationFailed(s"title must be at most 200 characters, got ${trimmed.length}"))
    else Right(new Title(trimmed))
  }

  implicit val schema: Schema[Title] =
    Schema[String].transform(s => parse(s).fold(e => throw e, identity), _.value)
}

final case class Price private (amount: BigDecimal)

object Price {
  def parse(amount: BigDecimal): Either[SchemaError, Price] =
    if (amount > 0) Right(new Price(amount))
    else Left(SchemaError.validationFailed(s"price must be positive, got $amount"))

  implicit val schema: Schema[Price] =
    Schema[BigDecimal].transform(a => parse(a).fold(e => throw e, identity), _.amount)
}

final case class PageCount private (value: Int)

object PageCount {
  def parse(value: Int): Either[SchemaError, PageCount] =
    if (value > 0) Right(new PageCount(value))
    else Left(SchemaError.validationFailed(s"page count must be positive, got $value"))

  implicit val schema: Schema[PageCount] =
    Schema[Int].transform(n => parse(n).fold(e => throw e, identity), _.value)
}

final case class Book(isbn: Isbn, title: Title, price: Price, pages: PageCount)

object Book {
  implicit val schema: Schema[Book] = Schema.derived
}
```

The `to` function of `Schema#transform` returns a plain `B`, not an `Either`, so a failed check has to throw. `SchemaError` is an exception, so `parse(s).fold(e => throw e, identity)` rethrows the `Left` unchanged, and the decoder catches it and returns it as a `Left` with the field path added. The `parse` method stays the single place where the rule lives; the schema only adapts it.

Calling the smart constructor directly shows both outcomes, including the hyphenated ISBN of *Programming in Scala*, Fifth Edition, and the same ISBN with a wrong check digit:

```scala mdoc
Isbn.parse("978-0-9971480-0-8")
Isbn.parse("978-0-9971480-0-9").left.map(_.message)
Title.parse("   ").left.map(_.message)
```

`Schema.derived` for `Book` picks up the four implicit schemas from the companion objects. A newtype's schema is a *wrapper* around its underlying type, so JSON shows the bare string or number, not a nested object:

```scala mdoc:silent
val fpInScala: Book =
  Book.schema.jsonCodec
    .decode("""{"isbn":"978-1-61729-065-7","title":"Functional Programming in Scala","price":44.99,"pages":320}""")
    .fold(e => throw e, identity)
```

Encoding the decoded book writes the ISBN without hyphens, because `Isbn.parse` stored the normalized digits:

```scala mdoc
println(Book.schema.jsonCodec.encodeToString(fpInScala))
```

## Reject Bad Input When Decoding JSON

Decoding goes through the same smart constructors, because the derived JSON codec calls each wrapper's `to` function. A valid payload decodes to a `Book`, and an invalid one returns `Left(SchemaError)` instead of throwing:

```scala mdoc
println(Book.schema.jsonCodec.decode("""{"isbn":"9781617290657","title":"Functional Programming in Scala","price":44.99,"pages":320}"""))

Book.schema.jsonCodec
  .decode("""{"isbn":"9781617290657","title":"   ","price":44.99,"pages":320}""")
  .left
  .foreach(e => println(e.message))
```

The message ends with the path of the field that failed: `.title` for the field, followed by `.wrapped`, the step from a newtype to the value inside it.

The JSON codec stops at the first failure. A *Zionomicon* submission with three bad values reports only the ISBN:

```scala mdoc:silent
val zionomiconJson = """{"isbn":"978-ZIO","title":"Zionomicon","price":-45,"pages":0}"""
```

The price and the page count are never checked:

```scala mdoc
Book.schema.jsonCodec.decode(zionomiconJson).left.foreach(e => println(e.message))
```

Failing fast is the right choice inside a service, where any failure means the message is rejected. A client filling in a form needs every problem at once, which the next section provides.

## Collect Every Error in One Pass

`Schema#fromDynamicValue` builds a typed value from a `DynamicValue`, the library's schema-less representation of data. Unlike the JSON codec, it keeps going after a failure and collects every error into one `SchemaError`. To use it on JSON, we parse the text with `Json.parse`, which checks only the JSON syntax, convert the result with `Json#toDynamicValue`, and hand that to the validated schema:

```scala mdoc:silent
def decodeAll(json: String): Either[SchemaError, Book] =
  Json.parse(json).flatMap(parsed => Book.schema.fromDynamicValue(parsed.toDynamicValue))
```

The *Zionomicon* submission now reports all three problems:

```scala mdoc
decodeAll(zionomiconJson).left.foreach(e => println(e.message))
```

Structural errors are collected together with the newtype checks. A payload with a wrong type for `pages` and no `price` at all reports both, next to the ISBN failure:

```scala mdoc
decodeAll("""{"isbn":"978-ZIO","title":"Zionomicon","pages":"many"}""").left.foreach(e => println(e.message))
```

A valid payload decodes to the same `Book` as before, and JSON numbers such as `44.99` or `45` become a `BigDecimal` price either way:

```scala mdoc
println(decodeAll("""{"isbn":"978-1-61729-065-7","title":"Functional Programming in Scala","price":45,"pages":320}"""))
```

The same approach works for input that is already a Scala value, such as a row type from a database or a request type with plain fields. Convert it to a `DynamicValue` with its own schema's `Schema#toDynamicValue`, then rebuild it with the validated schema; the field names have to match:

```scala mdoc
final case class BookSubmission(isbn: String, title: String, price: BigDecimal, pages: Int)

object BookSubmission {
  implicit val schema: Schema[BookSubmission] = Schema.derived
}

val submission = BookSubmission("978-ZIO", "Zionomicon", BigDecimal(-45), 0)

Book.schema
  .fromDynamicValue(BookSubmission.schema.toDynamicValue(submission))
  .left
  .foreach(e => println(e.message))
```

:::tip
`Into.derived[BookSubmission, Book]` also collects every field error, given an implicit `Into[String, Isbn]` (and so on) built from each `parse` method. Its errors carry the root path `.`, though, with the field name only inside the message text (`converting field BookSubmission.isbn to Book.isbn failed`), so `Schema#fromDynamicValue` is the better choice when you report errors per field.
:::

## Turn the Errors into a 400 Response

A `SchemaError` holds a non-empty list of `SchemaError.Single` values in `SchemaError#errors`. Each one has a `source`, a `DynamicOptic` that describes the path to the failing value, and a `message` that already ends with that path. An API response reads better as a list of objects with the field and the problem kept apart, so we build one with two small helpers.

The field name comes from the `DynamicOptic.Node.Field` steps of the path, which skips the `.wrapped` step. A `SchemaError.MissingField` points at the record that lacks the field, so we add its `fieldName`. The text without the path comes from the specific error type:

```scala mdoc:silent
final case class FieldError(field: String, message: String)

object FieldError {
  implicit val schema: Schema[FieldError] = Schema.derived
}

final case class ErrorResponse(status: Int, errors: List[FieldError])

object ErrorResponse {
  implicit val schema: Schema[ErrorResponse] = Schema.derived
}

def fieldOf(error: SchemaError.Single): String = {
  val path = error.source.nodes.collect { case DynamicOptic.Node.Field(name) => name }
  error match {
    case missing: SchemaError.MissingField => (path :+ missing.fieldName).mkString(".")
    case _                                 => path.mkString(".")
  }
}

def detailOf(error: SchemaError.Single): String = error match {
  case e: SchemaError.ConversionFailed    => e.details
  case _: SchemaError.MissingField        => "is required"
  case e: SchemaError.ExpectationMismatch => e.expectation
  case e: SchemaError.Message             => e.details
  case other                              => other.message
}

def toResponse(error: SchemaError): ErrorResponse =
  ErrorResponse(400, error.errors.map(e => FieldError(fieldOf(e), detailOf(e))))
```

The response type has its own derived schema, so the body is encoded with the same JSON codec as everything else:

```scala mdoc
decodeAll("""{"isbn":"978-ZIO","title":"Zionomicon","pages":"many"}""").left.foreach { e =>
  println(ErrorResponse.schema.jsonCodec.encodeToString(toResponse(e)))
}
```

An HTTP handler sends this body with status 400 when `decodeAll` returns `Left`, and continues with a fully checked `Book` when it returns `Right`. The upcoming guide on validating query parameters and headers applies the same idea to the rest of an HTTP request.

## Enforce the Same Rules in Another Format

The checks live in the `Schema`, not in the JSON codec, so any codec derived from `Book.schema` runs them. Deriving a TOON codec with `Schema#derive` and decoding the *Zionomicon* submission rejects the same ISBN, with the same message and path:

```scala mdoc:silent
import zio.blocks.schema.toon.ToonFormat

val toonCodec = Book.schema.derive(ToonFormat)
```

The good book round-trips through TOON, and the bad one fails:

```scala mdoc
println(toonCodec.encodeToString(fpInScala))

toonCodec
  .decode("isbn: \"978-ZIO\"\ntitle: Zionomicon\nprice: -45\npages: 0")
  .left
  .foreach(e => println(e.message))
```

Like the JSON codec, the TOON codec stops at the first failure. For every error, decode TOON into `BookSubmission` and rebuild it with `Schema#fromDynamicValue`, as in the previous section.

:::warning
The CSV codec (`zio-blocks-schema-csv`) also runs the newtype checks, but in this release `CsvCodec#decode` throws the `SchemaError` when a check fails instead of returning `Left`. Wrap CSV decoding of validated types in `scala.util.Try` until this is fixed.
:::

## Where the Rules Do Not Appear

The rules live in Scala code, so they don't appear in the JSON Schema contract derived from `Book.schema`. Each newtype shows up as its underlying type:

```scala mdoc
println(Book.schema.toJsonSchema)
```

If other teams need the rules in the contract, add them to a hand-written JSON Schema and load it with `JsonSchema.parse`, as described in [How to Generate JSON Schema from a Type and Validate Payloads Against It](./generate-json-schema-and-validate-payloads.md).

The schema module also has built-in `Validation` constraints, such as `Validation.Numeric.Positive` and `Validation.String.NonEmpty`, attached to primitive types and described in [Validation](../reference/schema/validation.md). In this release only `DynamicSchema#check` enforces them: the derived codecs and `Schema#fromDynamicValue` ignore them, and `Schema#toJsonSchema` does not emit them. Smart constructors are the way to enforce a rule.

## Use Scala 3 Opaque Types

On Scala 3, an opaque type alias is a newtype with no wrapper object at run time: inside its defining scope `Isbn` is a `String`, and outside it is a distinct type. The schema works the same way. Put the smart constructor and a `Schema` built with `Schema#transform` in the companion object, where the compiler finds it for `Schema.derived`, and define `Book` outside the object that declares the opaque type:

```scala mdoc:nest
// Scala 3 only
object catalog {
  opaque type Isbn = String

  object Isbn {
    def parse(input: String): Either[SchemaError, Isbn] = {
      val digits = input.filterNot(c => c == '-' || c == ' ')
      if (digits.length == 13 && digits.forall(_.isDigit)) Right(digits)
      else Left(SchemaError.validationFailed(s"ISBN must have 13 digits, got '$input'"))
    }

    given Schema[Isbn] = Schema[String].transform(s => parse(s).fold(e => throw e, identity), identity)

    extension (isbn: Isbn) def value: String = isbn
  }
}

final case class Book(isbn: catalog.Isbn, title: String) derives Schema

println(Schema[Book].jsonCodec.decode("""{"isbn":"978-1-61729-065-7","title":"Functional Programming in Scala"}""").map(_.isbn.value))

Schema[Book].jsonCodec
  .decode("""{"isbn":"978-ZIO","title":"Zionomicon"}""")
  .left
  .foreach(e => println(e.message))

Json
  .parse("""{"isbn":"978-ZIO","title":"Zionomicon"}""")
  .flatMap(json => Schema[Book].fromDynamicValue(json.toDynamicValue))
  .left
  .foreach(e => println(e.message))
```

Where `Book` is defined matters. Inside `object catalog`, `Isbn` is the same type as `String`, so a `Book` derived there uses the plain `String` schema and accepts any ISBN without calling `parse`. For the same reason, an opaque type needs its own `given Schema`: without one, `Schema.derived` sees through the alias and accepts any string.

## Putting It Together

The complete example defines the four newtypes and `Book`, decodes a batch of three submissions (*Functional Programming in Scala*, *Zionomicon* and *Programming in Scala*), collects every error, and prints either the decoded book or the `400` response body for each:

```scala mdoc:embed:schema-examples/src/main/scala/validatednewtypes/CompleteExample.scala
```

## Running the Examples

All examples in this guide have corresponding runnable Scala files in the `schema-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/schema-examples
```

The step examples share the newtypes in `BookModel.scala`:

<details>
  <summary>schema-examples/src/main/scala/validatednewtypes/BookModel.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/validatednewtypes/BookModel.scala:show-line-numbers
```

</details>

Run them in order to follow the guide step by step.

### Define the Newtypes

This example calls each smart constructor with good and bad input, then decodes and encodes *Functional Programming in Scala* to show that the newtypes appear in JSON as bare values.

<details>
  <summary>schema-examples/src/main/scala/validatednewtypes/DefineNewtypesExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/validatednewtypes/DefineNewtypesExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain validatednewtypes.DefineNewtypesExample"
```

### Reject Bad JSON

This example decodes a valid payload, a payload with a blank title, and the *Zionomicon* payload with three bad values, which reports only the first.

<details>
  <summary>schema-examples/src/main/scala/validatednewtypes/RejectBadJsonExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/validatednewtypes/RejectBadJsonExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain validatednewtypes.RejectBadJsonExample"
```

### Collect Every Error

This example decodes JSON through `Json#toDynamicValue` and `Schema#fromDynamicValue` to collect every error, including a wrong type and a missing field, and validates a `BookSubmission` value the same way.

<details>
  <summary>schema-examples/src/main/scala/validatednewtypes/CollectAllErrorsExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/validatednewtypes/CollectAllErrorsExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain validatednewtypes.CollectAllErrorsExample"
```

### Build the Error Response

This example turns the collected `SchemaError` into an `ErrorResponse` with one `FieldError` per problem and prints it as JSON.

<details>
  <summary>schema-examples/src/main/scala/validatednewtypes/ErrorResponseExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/validatednewtypes/ErrorResponseExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain validatednewtypes.ErrorResponseExample"
```

### Decode TOON with the Same Rules

This example derives a TOON codec from `Book.schema`, round-trips a valid book, and rejects the *Zionomicon* submission.

<details>
  <summary>schema-examples/src/main/scala/validatednewtypes/ToonFormatExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/validatednewtypes/ToonFormatExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain validatednewtypes.ToonFormatExample"
```

### Opaque Types on Scala 3

This example defines `Isbn` as a Scala 3 opaque type with a `given Schema` and decodes a good and a bad payload. It compiles on Scala 3 only.

<details>
  <summary>schema-examples/src/main/scala/validatednewtypes/OpaqueTypesExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/validatednewtypes/OpaqueTypesExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain validatednewtypes.OpaqueTypesExample"
```

### The Complete Example

This example is the program from [Putting It Together](#putting-it-together).

Run it from the `schema-examples` directory:

```bash
sbt "runMain validatednewtypes.CompleteExample"
```

To compile every example at once without running them:

```bash
sbt compile
```

## Going Further

- [SchemaError](../reference/schema/schema-error.md) covers every error case, how paths are built, and how to combine errors with `SchemaError#++`.
- [DynamicValue](../reference/schema/dynamic-value.md) explains the schema-less representation that `Schema#fromDynamicValue` reads.
- [How to Override the Codec for One Type](./override-codec-for-one-type.md) shows how to change how a type is encoded when a wrapper is not enough.
- [How to Customize JSON Encoding](./customize-json-encoding.md) covers field names, optional fields and strict decoding for the types around your newtypes.
