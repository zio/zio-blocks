---
id: llm-data-with-toon
title: "How to Send Compact Data to an LLM with TOON and Parse the Reply"
description: "Encode records as tabular TOON for an LLM prompt, then decode the model's TOON reply back into case classes."
keywords:
  - "LLM Prompts"
  - "Token Savings"
  - "Tabular Arrays"
  - "ToonCodecDeriver"
  - "ArrayFormat"
  - "Delimiter"
---

In this guide, we will send a list of Scala books to a large language model (LLM) as compact TOON, ask it to recommend one, and decode its reply into a case class. By the end, you will have a prompt builder that encodes records in TOON's tabular layout and a decoder that turns the model's TOON answer into a typed `Recommendation`, or into a clear error when the answer is malformed.

We take an incremental approach:

- Encode a catalog of books as tabular TOON and compare its size with JSON
- See why `ArrayFormat.Tabular` must be set explicitly
- Pick a delimiter that keeps values unquoted
- Build the prompt string
- Decode the model's reply, handle malformed replies and strip the prose around them

No LLM is called anywhere in this guide: the model's reply is a fixed string, so every output you see is deterministic.

## The Problem

LLM APIs bill and limit by tokens, and a prompt that carries data spends most of its tokens on that data. JSON repeats every field name in every record and wraps every string in quotes, so a list of 50 records pays for its field names 50 times. TOON (Token-Oriented Object Notation) sends a uniform list of records as a table instead: the field names appear once in a header, and each record becomes one row of values.

The reply is the other half. Asking the model to answer in a structured format only helps if the answer is checked: a model can rename a field, leave one out, or wrap its answer in friendly prose. Without a decoder, that check is a hand-written parser.

For example, without a codec library, preparing the prompt and reading the reply looks like this:

```scala mdoc:compile-only
case class Book(title: String, firstAuthor: String, year: Int, tags: String)
case class Recommendation(title: String, reason: String)

// Building the table by hand: header, quoting and escaping are all on us
def toTable(books: List[Book]): String =
  books
    .map(b => s"${b.title},${b.firstAuthor},${b.year},${b.tags}") // breaks when a value contains a comma
    .mkString("books{title,firstAuthor,year,tags}:\n  ", "\n  ", "")

// Parsing the reply by hand: every field, every failure case
def parseReply(reply: String): Either[String, Recommendation] = {
  val fields = reply.linesIterator.flatMap { line =>
    line.split(":", 2) match {
      case Array(key, value) => Some(key.trim -> value.trim)
      case _                 => None
    }
  }.toMap
  for {
    title  <- fields.get("title").toRight("missing title")
    reason <- fields.get("reason").toRight("missing reason")
  } yield Recommendation(title, reason)
}
```

This approach doesn't scale: the table writer silently corrupts any value that contains a comma, the parser silently drops lines it doesn't understand, and both must be rewritten whenever a field is added.

In this guide, we derive one TOON codec per type from its `Schema` instead. The codec writes the table, quotes values only when needed, and reports every malformed reply as a `SchemaError`.

## Prerequisites

Add the TOON module to your `build.sbt`. It depends on the schema module, so you don't need to add that separately:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-schema-toon" % "@VERSION@"

// Scala 3.9 and newer (also needs scalacOptions += "-experimental")
libraryDependencies += "dev.zio" %% "zio-blocks-next-schema-toon" % "@VERSION@"
```

All code in this guide uses these two imports:

```scala mdoc:silent
import zio.blocks.schema._
import zio.blocks.schema.toon._
```

This guide assumes you can derive a `Schema` for a case class and read a `SchemaError`. If not, start with the tutorial [Your First Schema](./your-first-schema.md).

## The Book Catalog

Our catalog holds three books. Each `Book` has only primitive fields, which is what lets TOON lay the list out as a table. The `tags` field is a single comma-separated string rather than a `List[String]` for that reason. A `Catalog` wraps the list in a named field, so the TOON header reads `books[3]{...}:`, a label the model can refer to. To define the catalog:

```scala mdoc:silent
case class Book(title: String, firstAuthor: String, year: Int, tags: String)

object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

case class Catalog(books: List[Book])

object Catalog {
  implicit val schema: Schema[Catalog] = Schema.derived
}

val catalog = Catalog(
  List(
    Book("Zionomicon", "John A. De Goes", 2024, "zio, effects, concurrency"),
    Book("Programming in Scala", "Martin Odersky", 2008, "scala, language, fundamentals"),
    Book("Functional Programming in Scala", "Paul Chiusano", 2014, "fp, scala, exercises")
  )
)
```

:::warning
The tabular layout needs every field of the record to be a primitive value: a string, a number, a boolean and so on. A field that holds a list or a nested record breaks the table. Flatten such fields before encoding, as `tags` does here.
:::

## Encoding the Records for a Prompt

`ToonCodecDeriver` is the deriver behind `ToonFormat`, and its `ToonCodecDeriver#with*` methods return a configured copy. We call `ToonCodecDeriver#withArrayFormat(ArrayFormat.Tabular)` to ask for the tabular layout, derive a codec for `Catalog` with it, and encode the catalog with `ToonCodec#encodeToString`:

```scala mdoc:silent
val tabular = ToonCodecDeriver.withArrayFormat(ArrayFormat.Tabular)

val catalogCodec: ToonCodec[Catalog] = Catalog.schema.derive(tabular)

val catalogToon = catalogCodec.encodeToString(catalog)
```

The result is the text we put in the prompt:

```scala mdoc
println(catalogToon)
```

The header `books[3]{title,firstAuthor,year,tags}:` names the field, the number of rows and the column names, once. Each line below it is one book. The `tags` values contain commas, so the encoder quotes them to keep them apart from the commas that separate columns.

For comparison, here is the same catalog as JSON, from the `toJsonString` extension method that the schema provides:

```scala mdoc:silent
val catalogJson = catalog.toJsonString
```

To print the JSON text and compare its length with the TOON text:

```scala mdoc
println(catalogJson)
println(s"JSON: ${catalogJson.length} characters, TOON: ${catalogToon.length} characters")
```

The TOON text is shorter because it states each field name once instead of once per book, and drops most quotes and braces. Characters are only a rough proxy for cost: the exact token count depends on the model's tokenizer, so measure with your provider's token counter before relying on a number.

The savings grow with the number of rows and shrink with the shape of the data. TOON pays off for long lists of uniform, flat records. For deeply nested or irregular data, where few records share the same fields, it saves little over compact JSON.

## Why `ArrayFormat.Tabular` Must Be Set

`ArrayFormat` has four cases: `Auto`, `Tabular`, `Inline` and `List`. The default deriver, the one `derive(ToonFormat)` uses, is set to `Auto`. In practice, `Auto` writes a list of records in list form, one `- ` item per record with every field name repeated, even when the records are flat and uniform. Only `ArrayFormat.Tabular` produces the table.

To compare the two layouts, encode the same catalog with both settings:

```scala mdoc:silent
val autoToon = Catalog.schema.derive(ToonFormat).encodeToString(catalog)
```

To print both encodings and their lengths side by side:

```scala mdoc
println("ArrayFormat.Auto:")
println(autoToon)
println()
println("ArrayFormat.Tabular:")
println(catalogToon)
println()
println(s"Auto: ${autoToon.length} characters, Tabular: ${catalogToon.length} characters")
```

The list form is valid TOON and decodes back to the same `Catalog`, but it repeats `title:`, `firstAuthor:`, `year:` and `tags:` for every book. That repetition is exactly what we wanted to avoid, so always set `ArrayFormat.Tabular` when encoding records for a prompt.

## Choosing a Delimiter

With the default comma delimiter, any value that contains a comma must be quoted, as the `tags` column showed. `ToonCodecDeriver#withDelimiter` picks a different column separator: `Delimiter.Comma` (the default), `Delimiter.Tab` or `Delimiter.Pipe`. A fourth case, `Delimiter.None`, exists for the reader's internal use and isn't meant for encoding.

To see how each delimiter changes the encoded table:

```scala mdoc
for (delimiter <- List(Delimiter.Comma, Delimiter.Pipe, Delimiter.Tab)) {
  val codec = Catalog.schema.derive(tabular.withDelimiter(delimiter))
  println(s"$delimiter:")
  println(codec.encodeToString(catalog))
  println()
}
```

The pipe and tab versions need no quotes, because no value contains a pipe or a tab. The header records the delimiter, as in `books[3|]` or `books[3\t]` (with a real tab character), so a decoder knows how to split the rows. Pick the delimiter that is least likely to appear in your values:

- `Delimiter.Comma` suits numbers, IDs and short words.
- `Delimiter.Pipe` suits free text with commas, such as titles, tags and descriptions, and stays readable.
- `Delimiter.Tab` also avoids quoting free text, but tabs are invisible in logs and easy to lose when text is copied.

The codec reads its own output back without any extra setting:

```scala mdoc:silent
val pipeCodec = Catalog.schema.derive(tabular.withDelimiter(Delimiter.Pipe))
```

To check that the codec reads its own output back:

```scala mdoc
println(pipeCodec.decode(pipeCodec.encodeToString(catalog)) == Right(catalog))
```

## Building the Prompt

A prompt is a plain string, so we build it with ordinary Scala. It holds three things: a short instruction, the TOON block, and a description of the reply we want. Naming the reply's fields and showing their shape makes it far more likely that the model answers in a form our decoder accepts. To assemble the prompt:

```scala mdoc:silent
val catalogPipeToon = pipeCodec.encodeToString(catalog)

def buildPrompt(catalogToon: String, request: String): String =
  List(
    "You recommend books from the catalog below. The catalog is in TOON format.",
    "",
    catalogToon,
    "",
    s"Request: $request",
    "",
    "Reply in TOON with exactly two fields and no other text:",
    "title: <a title from the catalog>",
    "reason: <one sentence>"
  ).mkString("\n")

val prompt = buildPrompt(catalogPipeToon, "I know Scala and want to learn effect systems.")
```

This is the text we would send to the model:

```scala mdoc
println(prompt)
```

## Decoding the Model's Reply

The model's answer is described by a case class like any other type. We derive its codec with `ToonCodecDeriver#withRejectExtraFields(true)`, so any key the case class doesn't declare is an error instead of being ignored. The reason for that choice shows up below, when the model adds prose. To define the reply type and its strict codec:

```scala mdoc:silent
case class Recommendation(title: String, reason: String)

object Recommendation {
  implicit val schema: Schema[Recommendation] = Schema.derived
}

val replyCodec: ToonCodec[Recommendation] =
  Recommendation.schema.derive(ToonCodecDeriver.withRejectExtraFields(true))
```

Suppose the model answered with this text. `ToonCodec#decode` returns an `Either[SchemaError, Recommendation]`:

```scala mdoc:silent
val reply =
  """title: Zionomicon
    |reason: It teaches ZIO, an effect system for Scala, from first steps to concurrency.""".stripMargin
```

To decode the reply:

```scala mdoc
println(replyCodec.decode(reply))
```

A single record's value runs to the end of its line, so the commas in `reason` need no quotes.

### Reading a Malformed Reply

Models don't always follow instructions. `ToonCodec#decode` turns each kind of mistake into a `Left`, and the error's `message` names the field at fault and its path (`.` is the top-level record):

```scala mdoc:silent
val malformedReplies = List(
  "missing field"     -> "title: Zionomicon",
  "renamed field"     -> "book: Zionomicon\nreason: It teaches ZIO.",
  "extra field"       -> "title: Zionomicon\nreason: It teaches ZIO.\nconfidence: 0.9",
  "prose before TOON" -> "Sure! Here is my pick:\ntitle: Zionomicon\nreason: It teaches ZIO."
)
```

To decode each malformed reply and print its error:

```scala mdoc
for ((label, text) <- malformedReplies)
  replyCodec.decode(text) match {
    case Left(error)  => println(s"$label: ${error.message}")
    case Right(value) => println(s"$label: decoded $value")
  }
```

The last case is the reason for `ToonCodecDeriver#withRejectExtraFields(true)`. The line `Sure! Here is my pick:` ends with a colon, so TOON reads it as a key named `Sure! Here is my pick`. With the default deriver, unknown keys are skipped, and that prose would be accepted without a word:

```scala mdoc:silent
val lenientCodec = Recommendation.schema.derive(ToonFormat)
```

To decode the prose-prefixed reply with the lenient codec:

```scala mdoc
println(lenientCodec.decode("Sure! Here is my pick:\ntitle: Zionomicon\nreason: It teaches ZIO."))
```

Rejecting extra fields turns that silent pass into an error you can log, retry or show.

### Stripping Prose and Code Fences

A common reply shape wraps the TOON in a Markdown code fence, with prose before and after it. That text isn't TOON, so `ToonCodec#decode` rejects it:

```scala mdoc:silent
val chattyReply =
  """Happy to help! Here is my recommendation:
    |
    |```toon
    |title: Zionomicon
    |reason: It teaches ZIO, an effect system for Scala, from first steps to concurrency.
    |```
    |
    |Enjoy the book!""".stripMargin
```

To decode the chatty reply with the strict codec:

```scala mdoc
println(replyCodec.decode(chattyReply).left.map(_.message))
```

Plain string handling is enough to cut out the fenced block before decoding. When there is no fence, the helper returns the trimmed reply unchanged:

```scala mdoc:silent
def extractToon(reply: String): String = {
  val fence = "```"
  val open  = reply.indexOf(fence)
  if (open < 0) reply.trim
  else {
    val bodyStart = reply.indexOf('\n', open) + 1
    val close     = reply.indexOf(fence, bodyStart)
    reply.substring(bodyStart, if (close < 0) reply.length else close).trim
  }
}
```

Now the chatty reply decodes:

```scala mdoc
println(replyCodec.decode(extractToon(chattyReply)))
```

Prose without a fence is left in place on purpose. Guessing where TOON starts in free text is fragile, and the strict codec already reports it as an error.

## Putting It Together

This example combines every step: it encodes the catalog as pipe-delimited tabular TOON, builds the prompt, and decodes a fixed model reply, stripping its code fence and rejecting extra fields:

```scala mdoc:embed:schema-examples/src/main/scala/llmdatawithtoon/CompleteExample.scala
```

## Running the Examples

All examples in this guide have corresponding runnable Scala files in the `schema-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/schema-examples
```

### Encoding the Records for a Prompt

This example encodes the catalog as tabular TOON and compares its length with the JSON encoding.

<details>
  <summary>schema-examples/src/main/scala/llmdatawithtoon/Step1EncodeForPromptExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/llmdatawithtoon/Step1EncodeForPromptExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain llmdatawithtoon.Step1EncodeForPromptExample"
```

### Why `ArrayFormat.Tabular` Must Be Set

This example prints the catalog with `ArrayFormat.Auto` and with `ArrayFormat.Tabular`, side by side.

<details>
  <summary>schema-examples/src/main/scala/llmdatawithtoon/Step2AutoVsTabularExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/llmdatawithtoon/Step2AutoVsTabularExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain llmdatawithtoon.Step2AutoVsTabularExample"
```

### Choosing a Delimiter

This example prints the catalog with the comma, pipe and tab delimiters and decodes the pipe version back.

<details>
  <summary>schema-examples/src/main/scala/llmdatawithtoon/Step3DelimitersExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/llmdatawithtoon/Step3DelimitersExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain llmdatawithtoon.Step3DelimitersExample"
```

### Building the Prompt

This example builds the prompt string around the pipe-delimited catalog and prints it.

<details>
  <summary>schema-examples/src/main/scala/llmdatawithtoon/Step4BuildPromptExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/llmdatawithtoon/Step4BuildPromptExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain llmdatawithtoon.Step4BuildPromptExample"
```

### Decoding the Model's Reply

This example decodes a well-formed reply, prints the error for each malformed reply, and strips a code fence before decoding.

<details>
  <summary>schema-examples/src/main/scala/llmdatawithtoon/Step5DecodeReplyExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/llmdatawithtoon/Step5DecodeReplyExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain llmdatawithtoon.Step5DecodeReplyExample"
```

### Complete Example

Run the complete example from the `schema-examples` directory:

```bash
sbt "runMain llmdatawithtoon.CompleteExample"
```

To compile every example at once without running them:

```bash
sbt compile
```

## Going Further

- The [TOON Codec Module](../reference/schema/built-in-codecs/toon.md) reference documents every `ToonCodecDeriver` option, including `ReaderConfig` and `WriterConfig`, key folding and discriminators.
- The [Built-in Formats and Codecs](../reference/schema/built-in-codecs/index.md) overview shows the other formats you can derive from the same `Schema`.
- The upcoming tutorial "One Type, Many Formats" encodes one type to TOON alongside JSON, YAML, CSV and other formats.
- To ask the model for several picks, decode a record with a list field, such as `case class Picks(picks: List[Recommendation])`. The model can answer with a tabular block like `picks[2]{title,reason}:`.
