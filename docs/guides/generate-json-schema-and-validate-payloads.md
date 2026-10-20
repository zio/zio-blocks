---
id: generate-json-schema-and-validate-payloads
title: "How to Generate JSON Schema from a Type and Validate Payloads Against It"
description: "Derive a draft 2020-12 JSON Schema contract from a case class, publish it, and validate raw JSON payloads against it or an external schema."
keywords:
  - "API Contract"
  - "JSON Schema"
  - "Payload Validation"
  - "JsonSchema"
  - "Schema.fromJsonSchema"
---

In this guide, we build the API contract for a book-catalog service that accepts new book submissions. We derive a JSON Schema document (draft 2020-12) from the `BookSubmission` case class, publish it as a file that other teams can read, validate raw JSON payloads against it, and load a stricter schema from another team. By the end, you will have one program that generates the contract from the type and rejects bad payloads with readable error messages.

We take an incremental approach:

- Generate the JSON Schema contract from the case class with `Schema#toJsonSchema`
- Publish the contract as a JSON file and read it back
- Validate untyped JSON payloads with `JsonSchema#check`
- Load an external JSON Schema with `JsonSchema.parse` and decode payloads through `Schema.fromJsonSchema`
- Choose between typed decoding and JSON Schema validation

## The Problem

A service that accepts JSON from other teams needs a written contract — a JSON Schema document that says which fields exist, which are required, and what type each one has. Writing that document by hand means describing the same type twice: once as a Scala case class and once as JSON.

The two descriptions drift apart silently. Rename a field, make one optional, or add a list, and the case class still compiles while the contract keeps promising the old shape. Clients that validate against the stale contract send payloads the service rejects, and nothing fails until production.

For example, a hand-written contract for a book submission sits next to the case class it describes:

```scala mdoc:compile-only
case class BookSubmission(title: String, firstAuthor: String, year: Int, isbn: Option[String], tags: List[String])

// Kept in sync with BookSubmission by hand, and shipped to other teams.
val bookSubmissionContract: String =
  """{
    |  "type": "object",
    |  "properties": {
    |    "title": { "type": "string" },
    |    "firstAuthor": { "type": "string" },
    |    "year": { "type": "integer" },
    |    "isbn": { "type": "string" },
    |    "tags": { "type": "array", "items": { "type": "string" } }
    |  },
    |  "required": ["title", "firstAuthor", "year"]
    |}""".stripMargin
```

Nothing connects the string to the case class. When `firstAuthor` becomes `authors: List[String]`, the compiler stays quiet, and checking an incoming payload against the string still needs a separate JSON Schema library.

In this guide, we derive the contract from the case class itself, so it changes whenever the type changes, and we validate payloads with the same library that generates it.

## Prerequisites

Add the schema module to your `build.sbt`:

```scala
// Scala 2.13 and Scala 3.3 LTS
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "@VERSION@"

// Scala 3.7 and newer
libraryDependencies += "dev.zio" %% "zio-blocks-next-schema" % "@VERSION@"
```

This guide assumes you can derive a `Schema` for a case class and decode JSON with it, as shown in the [Your First Schema](./your-first-schema.md) tutorial. The JSON Schema types, such as `JsonSchema` and `Json`, live in the `zio.blocks.schema.json` package.

## The Contract Type

Our service accepts one kind of payload: a book submission with a title, the first author's name, the publication year, an optional ISBN, and a list of tags. We derive its schema in the companion object and mark the record with `@Modifier.noExtraFields()`, because a contract for incoming data should reject fields it does not know:

```scala mdoc:silent
import zio.blocks.schema._
import zio.blocks.schema.json._

@Modifier.noExtraFields()
case class BookSubmission(
  title: String,
  firstAuthor: String,
  year: Int,
  isbn: Option[String],
  tags: List[String]
)

object BookSubmission {
  implicit val schema: Schema[BookSubmission] = Schema.derived
}
```

## Generate the JSON Schema Contract

`Schema#toJsonSchema` derives a `JsonSchema` from the same description that the JSON codec uses, so the contract and the decoder agree on every field. We derive the contract once and keep it as a value:

```scala mdoc:silent
val contract: JsonSchema = BookSubmission.schema.toJsonSchema
```

`JsonSchema#toString` pretty-prints the document, so printing the contract shows exactly what other teams receive:

```scala mdoc
println(contract)
```

The contract that `Schema#toJsonSchema` derives follows the codec's rules for each field:

- `"required"` lists `title`, `firstAuthor` and `year`. The `Option` field `isbn` and the `List` field `tags` are left out, because the decoder accepts a payload without them — fields with a default value are left out too.
- `isbn` accepts `null` as well as a string (`"type": ["null", "string"]`), and `tags` accepts `null` as well as an array.
- `year` carries `minimum` and `maximum` bounds, because a JSON number outside the `Int` range cannot become an `Int`.
- `"additionalProperties": false` comes from `@Modifier.noExtraFields()`. Without the annotation, the key is absent and extra properties are allowed.
- `"title"` names the type that each part of the contract derives from.

The output contains no `$schema` keyword; the document follows draft 2020-12, the version that `JsonSchema` implements.

:::note
Constraints that you add to a schema with `Validation` (see [Validation](../reference/schema/validation.md)), such as a minimum length or a pattern, do not appear in the contract that `Schema#toJsonSchema` derives. The only bounds in the output come from the field types, like the `Int` range on `year`.
:::

## Publish the Contract as a JSON File

Other teams need the contract as a file, not as a Scala value. `JsonSchema#toJson` turns the contract into a `Json` value, and `Json#print` renders it as text — compact by default, or indented when we pass a `WriterConfig`. We write the indented text to a file with the JDK's `Files.writeString`:

```scala mdoc:silent
import java.nio.file.{Files, Path}

val contractText: String = contract.toJson.print(WriterConfig.withIndentionStep2)

val contractFile: Path = Files.createTempFile("book-submission", ".schema.json")
Files.writeString(contractFile, contractText)
```

The indented text is the same document that `println(contract)` shows. The compact form fits on one line, which suits logs and HTTP responses:

```scala mdoc
println(contract.toJson.print)
```

Reading the file back with `JsonSchema.parse` gives a contract equal to the one we write:

```scala mdoc
println(JsonSchema.parse(Files.readString(contractFile)) == Right(contract))
```

For the full parsing and serialization API, see [Parsing and Serialization](../reference/schema/built-in-codecs/json/json-schema.md#parsing-and-serialization) in the JSON Schema reference.

## Validate an Untyped Payload

A gateway, a message consumer or a test fixture often holds raw JSON text rather than a Scala value. We parse the text with `Json.parse` and validate the result with `JsonSchema#check`, which returns `None` when the payload conforms and `Some(SchemaError)` when it does not. A small helper prints either outcome:

```scala mdoc:silent
def validate(schema: JsonSchema, payload: String): Unit =
  Json.parse(payload) match {
    case Left(parseError) => println(s"not JSON: ${parseError.message}")
    case Right(json)      =>
      schema.check(json) match {
        case None        => println("valid")
        case Some(error) => println(error.message)
      }
  }
```

A complete submission for Programming in Scala passes:

```scala mdoc
validate(
  contract,
  """{"title":"Programming in Scala","firstAuthor":"Martin Odersky","year":2008,"isbn":"9780981531601","tags":["scala","fundamentals"]}"""
)
```

A submission for Zionomicon without an ISBN or tags passes too, because the contract does not require them:

```scala mdoc
validate(contract, """{"title":"Zionomicon","firstAuthor":"John A. De Goes","year":2024}""")
```

A submission with an unknown `publisher` field, the year sent as a string and no author fails, and `JsonSchema#check` reports every problem at once, one per line:

```scala mdoc
validate(contract, """{"title":"Zionomicon","year":"2024","publisher":"Ziverge"}""")
```

Each line ends with the path to the failing value. The first line comes from `"additionalProperties": false`: it reports the extra `publisher` field, but its path points at the enclosing object (`.`) rather than at the field itself.

A year that does not fit in an `Int` breaks the `maximum` bound that the contract derives from the field type:

```scala mdoc
validate(
  contract,
  """{"title":"Functional Programming in Scala","firstAuthor":"Paul Chiusano","year":20140000000}"""
)
```

When you only need a yes-or-no answer, `JsonSchema#conforms` returns a `Boolean` instead. For more on the validation API, see [Validation](../reference/schema/built-in-codecs/json/json-schema.md#validation) in the JSON Schema reference.

## Load a Schema from Another Source

Sometimes the contract comes from somewhere else — a partner team, a schema registry or a configuration file read at startup. Suppose the catalog team publishes a stricter schema for the same payload: titles and authors must be non-empty, the year must be 1450 or later, an ISBN must be an ISBN-13 without hyphens, and tags must not repeat:

```scala mdoc:silent
val catalogContractText: String =
  """{
    |  "$schema": "https://json-schema.org/draft/2020-12/schema",
    |  "type": "object",
    |  "properties": {
    |    "title": { "type": "string", "minLength": 1 },
    |    "firstAuthor": { "type": "string", "minLength": 1 },
    |    "year": { "type": "integer", "minimum": 1450 },
    |    "isbn": { "type": "string", "pattern": "^97[89][0-9]{10}$" },
    |    "tags": { "type": "array", "items": { "type": "string" }, "uniqueItems": true }
    |  },
    |  "required": ["title", "firstAuthor", "year"],
    |  "additionalProperties": false
    |}""".stripMargin
```

We can use this document in two ways: check `Json` values against it directly, or turn it into a `Schema[Json]` that validates while it decodes.

### Check Payloads Against the Parsed Schema

`JsonSchema.parse` turns the text into a `JsonSchema`, or returns a `SchemaError` when the text is not a valid schema. We fail fast at startup when the document is broken, then reuse the `validate` helper from the previous section:

```scala mdoc:silent
val catalogContract: JsonSchema =
  JsonSchema.parse(catalogContractText).fold(error => throw error, identity)
```

The Zionomicon submission passes the stricter schema:

```scala mdoc
validate(catalogContract, """{"title":"Zionomicon","firstAuthor":"John A. De Goes","year":2024}""")
```

A submission that breaks the catalog team's rules — an empty title, a hyphenated ISBN and a repeated tag — fails with one message per broken rule:

```scala mdoc
validate(
  catalogContract,
  """{"title":"","firstAuthor":"Paul Chiusano","year":2014,"isbn":"978-1617290657","tags":["fp","fp"]}"""
)
```

`JsonSchema#check` also validates the `format` keyword (`email`, `uuid`, `date-time` and others) by default; see [Format Validation](../reference/schema/built-in-codecs/json/json-schema.md#format-validation) for the supported formats and how to turn it off.

### Decode Through a Validating Schema

`Schema.fromJsonSchema` turns a `JsonSchema` into a `Schema[Json]`, which we can hand to any code that expects a `Schema`:

```scala mdoc:silent
val catalogSchema: Schema[Json] = Schema.fromJsonSchema(catalogContract)
```

Decoding with its `Schema#jsonCodec` runs in two stages. First the codec decodes the JSON text into the structure that the schema describes; then, as it builds the resulting `Json` value, it runs `JsonSchema#check` and fails the decode if the value does not conform. A successful decode returns the payload:

```scala mdoc
println(
  catalogSchema.jsonCodec.decode(
    """{"title":"Programming in Scala","firstAuthor":"Martin Odersky","year":2008,"isbn":"9780981531601","tags":["scala"]}"""
  )
)
```

A payload that breaks the catalog rules fails with the same messages that `JsonSchema#check` reports, followed by `at: .wrapped`, the path of the wrapper that runs the check:

```scala mdoc
catalogSchema.jsonCodec.decode(
  """{"title":"","firstAuthor":"Paul Chiusano","year":2014,"isbn":"978-1617290657","tags":["fp","fp"]}"""
) match {
  case Left(error) => println(error.message)
  case Right(json) => println(s"decoded: $json")
}
```

:::warning
The first stage treats every property listed in `"properties"` as required, even when `"required"` leaves it out. The Zionomicon submission, which conforms to the catalog schema, fails to decode because it has no `isbn`:

```scala mdoc
println(
  catalogSchema.jsonCodec.decode("""{"title":"Zionomicon","firstAuthor":"John A. De Goes","year":2024}""")
)
```

When payloads may leave out optional properties, parse them with `Json.parse` and validate them with `JsonSchema#check`, as in the previous section.
:::

## Typed Decoding or JSON Schema Validation?

When the payload is meant for our own Scala code, we can skip the JSON Schema and decode straight into the case class with `fromJson`. Decoding the bad Zionomicon submission from earlier this way gives a typed result, but only the first problem the decoder meets:

```scala mdoc
"""{"title":"Zionomicon","year":"2024","publisher":"Ziverge"}""".fromJson[BookSubmission] match {
  case Left(error) => println(error.message)
  case Right(book) => println(s"decoded: $book")
}
```

The two approaches answer different questions:

| Approach                                      | Result                                | Errors reported                         | Needs a Scala type |
| --------------------------------------------- | ------------------------------------- | --------------------------------------- | ------------------ |
| `fromJson[BookSubmission]`                    | `Either[SchemaError, BookSubmission]` | First error only                        | Yes                |
| `Json.parse` then `JsonSchema#check`          | `Option[SchemaError]`                 | All errors at once                      | No                 |
| `Schema.fromJsonSchema(...).jsonCodec.decode` | `Either[SchemaError, Json]`           | First structural error, then all others | No                 |

Decode straight into the case class when the payload is for your own service and you want a `BookSubmission` to work with: the derived contract and the typed decoder accept the same payloads, so validating first adds nothing.

Validate with JSON Schema first when:

- The contract is shared with services written in other languages, and the JSON Schema document is the source of truth that every team checks against.
- The schema arrives at runtime — from a registry, a partner or a configuration file — and there is no case class for it.
- The caller needs every problem in one response, for example in the body of an HTTP 400 reply.
- The code forwards or stores the JSON as it is, such as a gateway or an event log, and never needs a typed value.

## Putting It Together

The complete example derives the contract, writes it to a file, validates payloads against it, loads the catalog team's schema and compares typed decoding with JSON Schema validation:

```scala mdoc:embed:schema-examples/src/main/scala/generatejsonschemaandvalidatepayloads/CompleteExample.scala
```

## Running the Examples

All examples in this guide have corresponding runnable Scala files in the `schema-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/schema-examples
```

Run them in order to follow the guide step by step.

### Generate and Publish the Contract

This example derives the JSON Schema contract for `BookSubmission`, prints it pretty and compact, writes it to a temporary file and parses the file back.

<details>
  <summary>schema-examples/src/main/scala/generatejsonschemaandvalidatepayloads/Step1GenerateContractExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/generatejsonschemaandvalidatepayloads/Step1GenerateContractExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain generatejsonschemaandvalidatepayloads.Step1GenerateContractExample"
```

### Validate Untyped Payloads

This example parses JSON text with `Json.parse` and validates it against the contract that `Schema#toJsonSchema` derives, using `JsonSchema#check`, printing the result for valid submissions and for a missing field, a wrong type, an unknown field and an out-of-range year.

<details>
  <summary>schema-examples/src/main/scala/generatejsonschemaandvalidatepayloads/Step2ValidatePayloadsExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/generatejsonschemaandvalidatepayloads/Step2ValidatePayloadsExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain generatejsonschemaandvalidatepayloads.Step2ValidatePayloadsExample"
```

### Load an External Schema

This example parses the catalog team's JSON Schema with `JsonSchema.parse`, checks payloads against it, and decodes payloads through `Schema.fromJsonSchema`, including the payload without an `isbn` that the decoder rejects.

<details>
  <summary>schema-examples/src/main/scala/generatejsonschemaandvalidatepayloads/Step3ExternalSchemaExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/generatejsonschemaandvalidatepayloads/Step3ExternalSchemaExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain generatejsonschemaandvalidatepayloads.Step3ExternalSchemaExample"
```

### Compare Typed Decoding with Validation

This example decodes the same bad payload with `fromJson[BookSubmission]` and with `JsonSchema#check`, showing the first error from the typed decoder next to every error from the JSON Schema.

<details>
  <summary>schema-examples/src/main/scala/generatejsonschemaandvalidatepayloads/Step4TypedVersusUntypedExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/generatejsonschemaandvalidatepayloads/Step4TypedVersusUntypedExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain generatejsonschemaandvalidatepayloads.Step4TypedVersusUntypedExample"
```

### The Complete Example

The complete example from "Putting It Together" runs everything from the guide in one program:

```bash
sbt "runMain generatejsonschemaandvalidatepayloads.CompleteExample"
```

To compile every example at once without running them:

```bash
sbt compile
```

## Going Further

- [JSON Schema](../reference/schema/built-in-codecs/json/json-schema.md) covers building schemas by hand, the combinators, every supported keyword and the known limitations.
- [Dynamic Schema](../reference/schema/dynamic-schema.md) validates `DynamicValue`s against a schema with `DynamicSchema#check` and `DynamicSchema#conforms`; `schema-examples/src/main/scala/dynamicschema/DynamicSchemaValidationExample.scala` shows it end to end.
- [Your First Schema](./your-first-schema.md) introduces `Schema`, `fromJson` and `SchemaError` from the beginning.
