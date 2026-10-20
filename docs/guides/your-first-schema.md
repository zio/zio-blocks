---
id: your-first-schema
title: "Your First Schema: Round-Trip a Type Through JSON"
description: "Describe a Scala type once with Schema, then encode it to JSON, decode it back, and read decode errors."
keywords:
  - "Getting Started"
  - "JSON Round Trip"
  - "Schema Derivation"
  - "Schema"
  - "SchemaError"
  - "DynamicValue"
---

Welcome! This tutorial is for Scala developers who are new to ZIO Blocks. If you can write a case class, a companion object and an `Either` pattern match, you have everything you need. You don't need any prior experience with schemas, codecs or JSON libraries to follow along.

## 1. Introduction

By the end of this tutorial, you will understand:

- What a `Schema` is and how to derive one for your own case classes
- How to encode a value to JSON and decode it back into the same value
- How nested case classes, optional fields and lists fit into one schema without extra work
- How to read a `SchemaError` and use its path to fix bad input
- Why one schema can also give you a generic data view and a JSON Schema document

We learn these concepts by building a tiny book catalog, one step at a time:

1. Describe a type with a schema
2. Encode a value to JSON
3. Decode JSON back into a value
4. Grow the model with nested types, optional fields and lists
5. Read and fix a decode error
6. Look at the same data two more ways

We recommend reading from top to bottom — each section builds on the previous one.

To follow along, add the schema module to your `build.sbt`:

```scala
// Scala 2.13 and Scala 3.3 LTS
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "@VERSION@"

// Scala 3.7 and newer
libraryDependencies += "dev.zio" %% "zio-blocks-next-schema" % "@VERSION@"
```

Scala 3.7 and newer use the `zio-blocks-next-schema` artifact name; the code in this tutorial is the same for every Scala version.

## 2. Background: One Description, Many Uses

Most Scala programs describe the same data several times. A case class says what a `Book` is, a JSON encoder says how to write it, a JSON decoder says how to read it, and a JSON Schema document tells other teams what to send. Each description is written by hand, and each one can drift away from the others when the case class changes.

ZIO Blocks takes a different approach. You describe the shape of a type once, as a `Schema`. Everything else — the JSON codec, a generic data view, the JSON Schema document, and the other formats ZIO Blocks supports — is derived from that one description. When the case class changes, the schema is derived again at compile time, and every view follows. In this tutorial we watch that happen for JSON.

## 3. Describe a Type with a Schema

A `Schema[A]` is a value that describes the structure of the type `A`: its name, its fields and their types. We rarely write one by hand. Instead, we ask the compiler to derive it with `Schema.derived` and store it as an implicit value in the type's companion object, where the rest of the library can find it.

Let's start with the smallest book we can think of — a title and a publication year:

```scala mdoc:silent
import zio.blocks.schema._

case class Book(title: String, year: Int)

object Book {
  implicit val schema: Schema[Book] = Schema.derived
}
```

The code above has three parts:

- `import zio.blocks.schema._` brings `Schema` and the extension methods we use later into scope.
- `case class Book(title: String, year: Int)` is an ordinary case class; nothing about it is special to ZIO Blocks.
- `implicit val schema: Schema[Book] = Schema.derived` derives the schema at compile time and stores it where the compiler finds it whenever code asks for a `Schema[Book]`.

A schema is a plain value, so we can print it and look at what the compiler derived:

```scala mdoc
println(Book.schema)
```

The code above prints `Book.schema`, the value we derived in the previous step. Notice that the schema records the type as a `record` named `Book` with two fields, in the order we declared them. That's the whole description — and it is all the library needs to produce JSON.

## 4. Encode a Value to JSON

With a schema in scope, every `Book` value gains a `toJsonString` extension method. It asks the schema for its JSON codec and uses it to write the value as a compact JSON string.

Let's create a book and encode it:

```scala mdoc:silent
val zionomicon = Book("Zionomicon", 2024)

val zionomiconJson = zionomicon.toJsonString
```

The code above:

- `Book("Zionomicon", 2024)` creates a regular case class value.
- `zionomicon.toJsonString` encodes it with the JSON codec derived from `Book.schema` and returns a `String`.

To see the JSON text, we print it:

```scala mdoc
println(zionomiconJson)
```

The code above prints the string that `toJsonString` returned. Notice the output: each case class field becomes a JSON field with the same name, in declaration order, and there are no extra spaces. We never wrote an encoder — the schema was enough.

## 5. Decode JSON Back Into a Value

Encoding is half of a round trip. To read JSON, we call the `fromJson` extension method on a `String` and tell it which type we expect. Decoding can fail — the text might not be valid JSON or might not match `Book` — so `fromJson` returns an `Either[SchemaError, Book]` instead of throwing.

Let's decode the JSON from the previous step and compare the result with the original value:

```scala mdoc
val decoded = zionomiconJson.fromJson[Book]

decoded == Right(zionomicon)
```

The code above:

- `zionomiconJson.fromJson[Book]` finds `Book.schema`, derives the decoder from it and parses the string.
- `Right(...)` in the output means decoding succeeded and carries the decoded `Book`.
- `decoded == Right(zionomicon)` checks the round trip: the value we get back is equal to the value we started with.

That's a complete JSON round trip: one schema, an encoder and a decoder, and the guarantee that both sides agree because they come from the same description.

## 6. Grow the Model: Nested Types, Optional Fields and Lists

Real data is rarely flat. Let's give each book an author, record the author's birth year only when we know it, and add a list of tags. We start a fresh scope so we can redefine `Book`:

```scala mdoc:silent:reset
import zio.blocks.schema._

case class Author(name: String, born: Option[Int])

case class Book(title: String, author: Author, year: Int, tags: List[String])

object Book {
  implicit val schema: Schema[Book] = Schema.derived
}
```

The code above:

- `Author` is a second case class with an optional field, `born: Option[Int]`.
- `Book` now nests an `Author` and holds a `List[String]` of tags.
- `Book.schema` is still one line. `Schema.derived` looks for a schema for each field's type and derives one when it finds none, so `Author`, `Option[Int]` and `List[String]` need no extra code.

Let's encode a book with every field filled in:

```scala mdoc
val programmingInScala = Book("Programming in Scala", Author("Martin Odersky", Some(1958)), 2008, List("scala", "fundamentals"))

println(programmingInScala.toJsonString)
```

The code above builds a book with a nested `Author`, `Some(1958)` as the birth year and a two-element tag list, then encodes the whole value in one call. The nested `Author` becomes a nested JSON object, `Some(1958)` is written as the plain number `1958`, and the list becomes a JSON array.

Now let's encode a book where we don't know the author's birth year and have no tags yet:

```scala mdoc
val zionomicon = Book("Zionomicon", Author("John A. De Goes", None), 2024, Nil)

println(zionomicon.toJsonString)
```

The code above leaves the birth year as `None` and passes `Nil`, an empty tag list. Notice what is missing from the output: the JSON codec leaves out a field whose value is `None` and a list that is empty. Decoding works the same way in reverse — a missing optional field becomes `None` and a missing list becomes an empty list:

```scala mdoc
"""{"title":"Zionomicon","author":{"name":"John A. De Goes"},"year":2024}""".fromJson[Book]
```

The schema grew with the case classes, and the round trip still holds without a single hand-written codec.

## 7. Read and Fix a Decode Error

Sooner or later, a decoder meets JSON that doesn't fit. Every failure comes back as a `Left` holding a `SchemaError`. Its `SchemaError#message` says what went wrong and where, using a path that starts at `.` for the top-level value.

Let's decode a book whose `year` field is missing:

```scala mdoc
val missingYear = """{"title":"Programming in Scala","author":{"name":"Martin Odersky"}}""".fromJson[Book]
```

The `Left` holds the error as data: what the decoder expected and the path where it stopped. To read it as one human-readable message, we pattern match on the `Either`:

```scala mdoc
missingYear match {
  case Left(error) => println(error.message)
  case Right(book) => println(s"Decoded: $book")
}
```

The code above:

- `"""...""".fromJson[Book]` returns a `Left` because `year` is required — its type is `Int`, not `Option[Int]`.
- `case Left(error) => println(error.message)` prints the human-readable message from the `SchemaError`.
- `at: .` in the message says the problem is in the top-level object itself.

The path becomes really useful when the problem is deep inside the JSON. Let's send the author's name as a number:

```scala mdoc
val badName = """{"title":"Programming in Scala","author":{"name":42},"year":2008}""".fromJson[Book]

badName match {
  case Left(error) => println(error.message)
  case Right(book) => println(s"Decoded: $book")
}
```

The message says the decoder expected `"`, the opening quote of a JSON string, but found the number `42`. Notice the path after `at:` — it walks from the top-level object into `author` and then into `name`, so we know exactly which value to fix. Let's fix both problems in the input and decode again:

```scala mdoc
val fixed = """{"title":"Programming in Scala","author":{"name":"Martin Odersky"},"year":2008}""".fromJson[Book]
```

The code above repeats the decode with `name` as a string and `year` present. The result is a `Right`, and the decoded book has `born = None` and an empty tag list, just like in the previous section.

:::note
`fromJson` never throws on bad input. Because the return type is `Either[SchemaError, Book]`, the compiler reminds us to handle the failure case every time we decode.
:::

## 8. Look at the Same Data Two More Ways

The schema gives us more than a JSON codec. To finish, let's look at two more views that come from the same `Book.schema`.

The first is `Schema#toDynamicValue`, which turns a value into a `DynamicValue` — a generic, type-independent tree of records, sequences and primitives. Library code uses it to work with any data without knowing its Scala type. Let's convert `programmingInScala`:

```scala mdoc
println(Book.schema.toDynamicValue(programmingInScala))
```

The code above converts the book into a `DynamicValue` and prints that generic tree. Notice that the printed tree follows the structure of `Book`: a record with `title`, `author`, `year` and `tags` fields. One detail differs from the JSON: `born` appears as a value tagged `Some`, because the generic view keeps the `Option` case that the JSON codec flattened into a plain number. The `show` extension method prints the same text in one call, as `println(programmingInScala.show)`.

The second view is `Schema#toJsonSchema`, which describes the JSON format itself as a JSON Schema document that other services and tools can read:

```scala mdoc
println(Book.schema.toJsonSchema)
```

The code above:

- `Book.schema.toJsonSchema` derives a JSON Schema document from the same description that our encoder and decoder use.
- `"required"` lists `title`, `author` and `year` — exactly the fields our decoder rejected when they were missing — while the optional `born` and the `tags` list are not required.

That's the "one description, many uses" idea from the start of this tutorial, made visible: the case class, the JSON codec, the generic view and the JSON Schema document all agree because they come from one schema.

## 9. Putting It Together

This example combines all the concepts we've learned — deriving a schema for nested case classes, encoding to JSON, decoding back, checking the round trip, handling a decode error and printing the generic view:

```scala mdoc:embed:schema-examples/src/main/scala/yourfirstschema/CompleteExample.scala
```

## 10. Running the Examples

All examples in this tutorial have corresponding runnable Scala files in the `schema-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/schema-examples
```

Run them in order to follow the tutorial step by step.

### Describe a Type with a Schema

This example derives a schema for the flat `Book` case class and prints it, showing the `record` that the compiler derived.

<details>
  <summary>schema-examples/src/main/scala/yourfirstschema/Concept1DescribeExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/yourfirstschema/Concept1DescribeExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain yourfirstschema.Concept1DescribeExample"
```

### Encode a Value to JSON

This example encodes a `Book` to a compact JSON string with `toJsonString`.

<details>
  <summary>schema-examples/src/main/scala/yourfirstschema/Concept2EncodeExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/yourfirstschema/Concept2EncodeExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain yourfirstschema.Concept2EncodeExample"
```

### Decode JSON Back Into a Value

This example decodes the JSON with `fromJson` and checks that the round trip returns the original value.

<details>
  <summary>schema-examples/src/main/scala/yourfirstschema/Concept3DecodeExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/yourfirstschema/Concept3DecodeExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain yourfirstschema.Concept3DecodeExample"
```

### Grow the Model

This example adds a nested `Author`, an optional birth year and a list of tags, and shows how `None` and empty lists are left out of the JSON and restored on decode.

<details>
  <summary>schema-examples/src/main/scala/yourfirstschema/Concept4NestedTypesExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/yourfirstschema/Concept4NestedTypesExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain yourfirstschema.Concept4NestedTypesExample"
```

### Read and Fix a Decode Error

This example decodes JSON with a missing field and a wrongly typed nested field, prints each `SchemaError` message with its path, and then decodes the fixed input.

<details>
  <summary>schema-examples/src/main/scala/yourfirstschema/Concept5DecodeErrorsExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/yourfirstschema/Concept5DecodeErrorsExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain yourfirstschema.Concept5DecodeErrorsExample"
```

### Look at the Same Data Two More Ways

This example prints the `DynamicValue` view of a book and the JSON Schema document derived from `Book.schema`.

<details>
  <summary>schema-examples/src/main/scala/yourfirstschema/Concept6OtherViewsExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/yourfirstschema/Concept6OtherViewsExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain yourfirstschema.Concept6OtherViewsExample"
```

### The Complete Example

The complete example from "Putting It Together" runs everything from the tutorial in one program:

```bash
sbt "runMain yourfirstschema.CompleteExample"
```

To compile every example at once without running them:

```bash
sbt compile
```

## 11. What You've Learned

In this tutorial, you learned:

- What a `Schema` is — one description of a type's shape — and how to derive it with `Schema.derived`
- How to encode a value with `toJsonString` and decode it with `fromJson`, and why the round trip holds
- How nested case classes, `Option` fields and `List` fields are derived automatically, and how `None` and empty lists are left out of the JSON
- How to read a `SchemaError` message and use its path to find and fix bad input
- How the same schema also gives you a `DynamicValue` view and a JSON Schema document

You now have a solid foundation in ZIO Blocks schemas. The next step is to tune the JSON format and use the same schema with other formats.

## 12. Where to Go Next

- **Want to see every extension method?** Read [Extension Syntax](../reference/schema/syntax.md), which covers `toJson`, `toJsonBytes`, `show` and more.
- **Ready to customize the JSON?** The [JSON Codec](../reference/schema/built-in-codecs/json/index.md) overview shows how the codec pieces fit together, and [JSON Configuration](../reference/schema/built-in-codecs/json/json-config.md) covers reader and writer settings and field-name mapping with `NameMapper`.
- **Want to understand errors in depth?** Read [SchemaError](../reference/schema/schema-error.md) for every error case and how errors are combined.
- **Curious about the generic view?** Read [DynamicValue](../reference/schema/dynamic-value.md).
- **Need another format?** Follow [One Type, Many Formats](./one-type-many-formats.md) to derive JSON, YAML, XML, TOON, MessagePack, Avro and CSV codecs from the same `Schema`. The [Built-in Codecs](../reference/schema/built-in-codecs/index.md) overview lists Avro, BSON, CSV, MessagePack, Thrift, TOON, XML and YAML, all derived from the same `Schema`.
- **Want the full API?** The [Schema](../reference/schema/schema.md) reference page documents every operation on `Schema`.
