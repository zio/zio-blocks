---
id: read-and-write-yaml
title: "How to Read and Write YAML Documents"
description: "Read YAML files into case classes, write CI-style YAML from Scala values, convert to JSON, and guard against the YAML that schema-yaml silently misreads."
keywords:
  - "YAML"
  - "Configuration Files"
  - "YamlCodec"
  - "YamlFormat"
  - "YamlReader"
  - "YamlWriter"
---

In this guide, we will read a bookstore's catalog from a YAML file into Scala case classes and write a CI workflow file from Scala values, using the same `Schema` that already gives you JSON. By the end, you will have a small set of functions that decode YAML with readable errors, write YAML in the style you choose, convert YAML to and from JSON, and refuse a document that the reader would otherwise load with parts missing.

We start with a short catalog and add one capability at a time:

- Read a YAML catalog into case classes.
- Report decode errors with the path of the bad value.
- Write a CI workflow as YAML.
- Control indentation, flow style and document markers.
- Build YAML values in code.
- Convert between YAML and JSON.
- Learn what the YAML module does not support, and guard against silent data loss.

## The Problem

YAML is the format of Kubernetes manifests, GitHub Actions workflows, docker-compose files, OpenAPI specs and many application configuration files. A first attempt at reading one by hand splits each line at the colon:

```scala mdoc:compile-only
def readFlat(text: String): Map[String, String] =
  text.linesIterator
    .map(_.trim)
    .filter(line => line.nonEmpty && !line.startsWith("#"))
    .flatMap { line =>
      line.split(":", 2) match {
        case Array(key, value) => Some(key.trim -> value.trim.stripPrefix("\"").stripSuffix("\""))
        case _                 => None
      }
    }
    .toMap

val book  = readFlat("title: Zionomicon\npages: 600\n")
val pages = book("pages").toInt
```

This works for one flat record and nothing else. A list of authors, a nested record, a value that contains a colon, or two books in one file all break it, and every field still has to be converted and checked by hand. A YAML library solves the parsing, but most Scala YAML libraries come with their own codec type class, so you end up maintaining JSON codecs and YAML codecs side by side.

In this guide, we solve this with `zio-blocks-schema-yaml`: a codec derived from the `Schema` you already have converts YAML to and from your case classes, and the `Yaml` value type lets you build, inspect and convert documents in code. The module is small and lenient, and some YAML features are not supported; the last two sections show exactly which ones, and how to make sure a document was read in full.

## Prerequisites

Add the schema and YAML modules to your build:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-schema"      % "@VERSION@",
  "dev.zio" %% "zio-blocks-schema-yaml" % "@VERSION@"
)

// Scala 3.9 and newer (also needs scalacOptions += "-experimental"): the same modules with the zio-blocks-next- prefix
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-next-schema"      % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-schema-yaml" % "@VERSION@"
)
```

[How to Add ZIO Blocks to Your Build](../getting-started/installation.md) explains the two artifact names and how to choose one per Scala version. Everything in this guide works on Scala 2.13 and Scala 3, except the `yaml"..."` interpolator, which is Scala 3 only in @VERSION@ (see [Build YAML Values in Code](#build-yaml-values-in-code)).

This guide assumes you can derive a `Schema` for a case class, as shown in [Your First Schema](./your-first-schema.md). [One Type, Many Formats](./one-type-many-formats.md) shows a YAML codec next to the other formats; here we go into the details of YAML.

## The Catalog Model

Our catalog file lists books, and each book has a title, its authors, an ISBN, a price, a page count, some tags and the formats it is sold in:

```scala mdoc:silent
import zio.blocks.schema._
import zio.blocks.schema.yaml._

sealed trait Format

object Format {
  case object Paperback extends Format
  case object Hardcover extends Format
  case object Ebook     extends Format

  val all: List[Format] = List(Paperback, Hardcover, Ebook)

  implicit val schema: Schema[Format] = Schema[String].transform[Format](
    name => all.find(_.toString == name).getOrElse(throw new IllegalArgumentException(s"Unknown format: $name")),
    _.toString
  )
}

case class Book(
  title: String,
  authors: List[String],
  isbn: String,
  price: Double,
  pageCount: Int,
  tags: List[String],
  formats: List[Format]
)

object Book {
  implicit val schema: Schema[Book] = Schema.derived
}

case class Catalog(store: String, books: List[Book])

object Catalog {
  implicit val schema: Schema[Catalog] = Schema.derived
}
```

`Format` gets its schema from `Schema[String].transform` instead of `Schema.derived`, so each format is written as its name (`Ebook`). A derived schema for a sealed trait writes each case as a mapping with one key, the case name, and the case's fields under it, so a case object becomes `Ebook: {}` and a plain `Ebook` in the file fails to decode with `Expected mapping with single key for variant`. That is the right shape for cases with fields, but YAML files list enumeration values as plain words.

## Read a YAML Catalog Into Case Classes

`Schema#derive(YamlFormat)` derives a `YamlCodec` from the schema. Its `YamlCodec#decode` method takes the YAML text and returns `Either[SchemaError, A]`:

```scala mdoc:silent
val catalogCodec: YamlCodec[Catalog] = Catalog.schema.derive(YamlFormat)

val catalogYaml: String =
  """# Books on the front table this month
    |store: Scala Books Berlin
    |books:
    |  - title: Zionomicon
    |    authors: [John A. De Goes, Adam Fraser, Milad Khajavi]
    |    isbn: 978-0-9862-0040-6
    |    price: 49.99
    |    page-count: 744
    |    tags: [zio, effects, concurrency]
    |    formats: [Ebook]
    |  - title: Programming in Scala
    |    authors:
    |      - Martin Odersky
    |      - Lex Spoon
    |      - Bill Venners
    |      - Frank Sommers
    |    isbn: "9780997148008"
    |    price: 59.95
    |    page-count: 852
    |    tags: [scala, language]
    |    formats:
    |      - Paperback
    |      - Ebook
    |""".stripMargin
```

To decode the catalog and print each book with its authors and price:

```scala mdoc
catalogCodec.decode(catalogYaml) match {
  case Right(catalog) => catalog.books.foreach(book => println(s"${book.title}: ${book.authors.mkString(", ")}, ${book.price}"))
  case Left(error)    => println(error.getMessage)
}
```

The file mixes the two ways YAML writes a list: the authors of Zionomicon are a flow sequence in square brackets, and the authors of Programming in Scala are a block sequence with one `- ` item per line. The codec reads both. It also skips the comment, and ignores keys that the case class doesn't have.

Two rules decide which keys the codec looks for. Keys are the field names in kebab-case: the `pageCount` field is read from the key `page-count`. The YAML codec always converts field names this way, and in @VERSION@ it ignores `@Modifier.rename`, `@Modifier.transient` and the other field annotations that the JSON codec honours, so the [JSON customizations](./customize-json-encoding.md) don't carry over to YAML. A field named `imagePullPolicy` can't be read from a Kubernetes file that spells the key `imagePullPolicy`; [Convert Between YAML and JSON](#convert-between-yaml-and-json) shows a way around this. GitHub issue [#1327](https://github.com/zio/zio-blocks/issues/1327) tracks configurable field names. Scalars are read as text and converted by the field's type: the ISBN of Programming in Scala is quoted because it looks like a number, but an unquoted ISBN is fine too, because a `String` field keeps the text either way and a `Double` field parses it.

If you prefer extension methods, `YamlSyntax` adds `String#fromYaml` to `String`, and `toYaml`, `toYamlString` and `toYamlBytes` to any value with a `Schema`:

```scala mdoc:silent
import zio.blocks.schema.yaml.YamlSyntax._

val zionomicon = Book(
  title = "Zionomicon",
  authors = List("John A. De Goes", "Adam Fraser", "Milad Khajavi"),
  isbn = "978-0-9862-0040-6",
  price = 49.99,
  pageCount = 744,
  tags = List("zio", "effects"),
  formats = List(Format.Ebook, Format.Paperback)
)
```

To write the book as YAML and check that it reads back as the same value:

```scala mdoc
println(zionomicon.toYamlString)
println(zionomicon.toYamlString.fromYaml[Book] == Right(zionomicon))
```

The codec also decodes from `Array[Byte]` and `java.nio.ByteBuffer`, so you can pass the bytes of a file straight in: `catalogCodec.decode(Files.readAllBytes(path))`.

## Report Decode Errors

When a value is missing or has the wrong type, `YamlCodec#decode` returns a `Left` with a `SchemaError`. Its message names what went wrong and the path to the value, written as Scala navigation from the root (`.books.at(1).price` is the `price` field of the second book):

```scala mdoc
def report(yaml: String): Unit =
  catalogCodec.decode(yaml) match {
    case Right(catalog) => println(s"OK: ${catalog.books.size} books")
    case Left(error)    => println(error.getMessage)
  }

report(catalogYaml.replace("price: 59.95", "price: cheap"))
report(catalogYaml.replace("page-count: 744", "page-count: many"))
report(catalogYaml.replace("formats: [Ebook]", "formats: [Kindle]"))
report(catalogYaml.replace("    isbn: \"9780997148008\"\n", ""))
```

The messages read as follows:

- A wrong scalar names the field that holds it. The path uses the Scala field name (`.books.at(0).pageCount`), not the YAML key `page-count`.
- The `Format` error ends in a `wrapped` path step, the step that ZIO Blocks adds for a type built with `Schema#transform`. It is an internal detail that shows up in the path.
- A missing field is named by its YAML key, and the path points at the record that should contain it, not at the field.

None of the messages has a line or column number: the codec decodes a tree of YAML values, and the tree doesn't remember where each value came from. A path is usually enough to find the value in a file of this size.

Malformed YAML is a different story. `YamlReader`, which turns the text into that tree, never reports a syntax error. It is lenient: it reads what it can, and in some cases it drops the rest of the document without a word. [Guard Against Silent Data Loss](#guard-against-silent-data-loss) shows how to detect that.

## Write a CI Workflow as YAML

Writing YAML is the other half. Our bookstore builds its Scala code on GitHub Actions, and we want to generate the workflow file from Scala values, for example to keep the Scala and JDK versions in one place. Kebab-case keys are what GitHub Actions expects (`runs-on`, `timeout-minutes`), so the default field naming works in our favour here:

```scala mdoc:silent
case class Branches(branches: List[String])

object Branches {
  implicit val schema: Schema[Branches] = Schema.derived
}

case class Triggers(push: Branches, pullRequest: Branches)

object Triggers {
  implicit val schema: Schema[Triggers] = Schema.derived
}

case class Step(name: String, uses: Option[String] = None, run: Option[String] = None)

object Step {
  implicit val schema: Schema[Step] = Schema.derived
}

case class Job(runsOn: String, timeoutMinutes: Int, steps: List[Step])

object Job {
  implicit val schema: Schema[Job] = Schema.derived
}

case class Workflow(name: String, on: Triggers, jobs: Map[String, Job])

object Workflow {
  implicit val schema: Schema[Workflow] = Schema.derived
}

val workflowCodec: YamlCodec[Workflow] = Workflow.schema.derive(YamlFormat)

val ciWorkflow = Workflow(
  name = "CI",
  on = Triggers(push = Branches(List("main")), pullRequest = Branches(List("main"))),
  jobs = Map(
    "test" -> Job(
      runsOn = "ubuntu-latest",
      timeoutMinutes = 30,
      steps = List(
        Step("Check out", uses = Some("actions/checkout@v4")),
        Step("Set up sbt", uses = Some("sbt/setup-sbt@v1")),
        Step("Test", run = Some("sbt +test"))
      )
    )
  )
)
```

`YamlCodec#encodeToString` writes the value as a YAML string, and `YamlCodec#encode` writes the same text as UTF-8 bytes, ready for `Files.write`:

```scala mdoc
println(workflowCodec.encodeToString(ciWorkflow))
```

The output follows a few fixed rules:

- Keys come in field order, and `Map` entries in the map's order. Field names are in kebab-case: `pullRequest` became `pull-request`.
- A `None` field is left out, so each step has only the keys it uses. A field with a default value is always written.
- The key `on` is quoted. YAML 1.1 readers read a bare `on`, `off`, `yes` or `no` as a boolean, so the writer quotes these words wherever they appear. GitHub reads `"on"` as the key `on`.
- Strings that would read back as something else are double-quoted: text that looks like a number, a boolean or `null`, text that starts with a YAML indicator such as `[`, `&` or `!`, and text that contains `: `, ` #` or a line break. `Int`, `Double` and `Boolean` fields are written as plain numbers and booleans.

Two value types are written differently from what you might expect. A string with line breaks is written as a double-quoted string with `\n` escapes, never as a `|` block, so a multi-line `run:` script is valid YAML in this form, just harder to read. `BigDecimal` and `BigInt` fields are written as quoted strings (`price: "49.99"`), so other YAML readers see a string, not a number; use `Double`, `Int` or `Long` for numbers that other tools must read as numbers.

## Control the Output Style

`YamlCodec#encodeToString` always uses the default style. To change it, encode the value to the `Yaml` tree with `YamlCodec#encodeValue`, then write the tree with `YamlWriter.write` and a `YamlOptions`:

```scala mdoc:silent
val workflowTree: Yaml = workflowCodec.encodeValue(ciWorkflow)
```

To write the tree with four-space indentation and a leading `---` line:

```scala mdoc
println(YamlWriter.write(workflowTree, YamlOptions(indentStep = 4, documentMarkers = true)))
```

`YamlOptions` has three settings:

| Setting           | Default | Effect                                                                     |
|-------------------|---------|----------------------------------------------------------------------------|
| `indentStep`      | `2`     | Spaces per nesting level                                                   |
| `documentMarkers` | `false` | Start the output with a `---` line                                         |
| `flowStyle`       | `false` | Write every mapping as `{k: v}` and every sequence as `[a, b]` on one line |

`YamlOptions.default`, `YamlOptions.pretty` (document markers on) and `YamlOptions.flow` (flow style on) are ready-made values, and `Yaml#print` and `Yaml#printPretty` are shortcuts for the first two. Flow style suits short values such as one step:

```scala mdoc:silent
val testStep = Step("Test", run = Some("sbt +test"))
```

To write the step in flow style:

```scala mdoc
println(YamlWriter.write(Step.schema.derive(YamlFormat).encodeValue(testStep), YamlOptions.flow))
```

:::warning
In @VERSION@, flow style doesn't quote commas, `]` or `}` inside strings. A title such as `Scala, the Language` is written unquoted and reads back as `Scala`, with the rest lost. Use flow style only for values you know contain none of these characters, or check the output as shown in [Guard Against Silent Data Loss](#guard-against-silent-data-loss).
:::

## Build YAML Values in Code

The codec turns case classes into `Yaml` trees, but you can also build a tree directly. `Yaml` has four cases: `Yaml.Mapping`, `Yaml.Sequence`, `Yaml.Scalar` and `Yaml.NullValue`. A scalar holds its text and an optional `YamlTag`; the tag tells the writer that the text is a number or a boolean, so it isn't quoted:

```scala mdoc:silent
val deployment: Yaml = Yaml.Mapping.fromStringKeys(
  "service"  -> Yaml.Scalar("bookstore-api"),
  "image"    -> Yaml.Scalar("ghcr.io/scala-books/bookstore-api:1.4.2"),
  "replicas" -> Yaml.Scalar("3", tag = Some(YamlTag.Int)),
  "public"   -> Yaml.Scalar("true", tag = Some(YamlTag.Bool)),
  "regions"  -> Yaml.Sequence(Yaml.Scalar("eu-central"), Yaml.Scalar("us-east"))
)
```

To print the tree in the default block style:

```scala mdoc
println(deployment.print)
```

To read a tree, match on the four cases. `YamlReader.read` parses text into a tree, which is useful when the shape of a document isn't known in advance:

```scala mdoc
YamlReader.read(catalogYaml) match {
  case Yaml.Mapping(entries) => println(entries.map(_._1.print).mkString(", "))
  case other                 => println(s"Not a mapping: ${other.print}")
}
```

On Scala 3, the `yaml"..."` interpolator builds a tree from YAML text with Scala values spliced in. Each spliced value needs a `Schema`, which the compiler checks:

```scala mdoc:silent
val featuredTitle = "Zionomicon"
val featuredPrice = 49.99

val featured: Yaml =
  yaml"""title: $featuredTitle
price: $featuredPrice
note: "Our book of the month is $featuredTitle"
"""

case class Featured(title: String, price: Double, note: String)

object Featured {
  implicit val schema: Schema[Featured] = Schema.derived
}
```

To print the tree and decode it into `Featured`:

```scala mdoc
println(featured.print)
println(Featured.schema.derive(YamlFormat).decodeValue(featured))
```

The interpolator builds the text and parses it again with `YamlReader`, so its result carries no tags: `price` is printed quoted, like any untyped scalar from the reader. The codec doesn't mind, because it reads scalars as text. This makes the interpolator handy for test inputs, and less so for output.

:::warning
The interpolator has known bugs in @VERSION@:

- On Scala 2.13 it doesn't compile in user code; the error says that `YamlInterpolatorRuntime` cannot be accessed. Build trees with the `Yaml` constructors there.
- Splice only scalar values (strings, numbers, booleans, dates). A spliced `List`, case class or `Yaml` tree is written in block style on the line of its key, so it is misread without an error: a list loses all but its first item and every line after it, and a record's fields move up to the parent mapping.
- Escape sequences in the literal are not processed: `yaml"title: Zionomicon\nprice: 49.99"` is one key whose value contains a backslash and an `n`. Write multi-line YAML in a triple-quoted literal, as above.
- The compile-time check covers the spliced types, not the YAML syntax: a literal with an unclosed `[` still compiles.
:::

## Convert Between YAML and JSON

`Yaml#toJson` converts a tree to a ZIO Blocks `Json` value, and `Yaml.fromJson` converts back. JSON to YAML keeps every type: JSON numbers and booleans become tagged scalars, so they are written unquoted, and JSON strings that look like numbers are quoted:

```scala mdoc:silent
import zio.blocks.schema.json.Json

val bookJson: Json = Json.from(zionomicon)
```

To print the YAML that the JSON value produces:

```scala mdoc
println(Yaml.fromJson(bookJson).print)
```

This tree came from the JSON codec, so the keys are the JSON field names (`pageCount`), not kebab-case. The price prints as `49.990` because of a known bug in @VERSION@: the JSON codec turns a `Double` into a `Json.Number` with an extra trailing zero. The value is the same.

YAML to JSON has to guess, because `YamlReader` keeps the text of each scalar but not whether it was quoted. `Yaml#toJson` makes a scalar a JSON number when its text starts with a digit or `-` and parses as a number, and a JSON boolean or `null` when its text is `true`, `false`, `null` or `~` (in lower, title or upper case). Everything else becomes a JSON string:

```scala mdoc
println(YamlReader.read("isbn: \"9780997148008\"\nversion: \"1.10\"\nimage-tag: 1.4.2\nin-stock: \"true\"\n").toJson.print)
```

The quotes were lost: the quoted ISBN and version became numbers (`1.10` keeps its trailing zero only because the number is a `BigDecimal`), and the quoted `"true"` became a boolean. `1.4.2` isn't a number, so it stayed a string.

The conversion still has a use when the YAML keys are in camelCase, as in Kubernetes manifests: decoding the JSON with the JSON codec uses the field names as written, and honours `@Modifier.rename`. `Json#as[A]` decodes a `Json` value with the type's schema:

```scala mdoc:silent
case class Container(name: String, image: String, imagePullPolicy: String, containerPort: Int)

object Container {
  implicit val schema: Schema[Container] = Schema.derived
}

val containerYaml =
  """name: bookstore-api
    |image: ghcr.io/scala-books/bookstore-api:1.4.2
    |imagePullPolicy: IfNotPresent
    |containerPort: 8080
    |""".stripMargin
```

To decode the YAML through the JSON codec, first with a valid container and then with a name that looks like a number:

```scala mdoc
println(YamlReader.read(containerYaml).toJson.as[Container])
println(YamlReader.read(containerYaml.replace("name: bookstore-api", "name: \"2024\"")).toJson.as[Container].left.map(_.getMessage))
```

The second call, on a container whose name is `"2024"`, shows the limit of this route: a quoted value that looks like a number reaches the JSON codec as a number, and a `String` field rejects it. The error is loud, so no data is lost, but a file that quotes numeric strings can't be read this way.

[How to Write an OpenAPI Document and Export It as JSON or YAML](./write-and-export-openapi.md) uses `Yaml.fromJson` to export an OpenAPI document, and shows a workaround for the quoting bug described in the next section.

## What schema-yaml Does Not Support

`YamlReader` implements the parts of YAML that hand-written files use most: block and flow mappings and sequences, plain, single-quoted and double-quoted scalars, `|` and `>` blocks, comments, and one leading `---`. What it doesn't implement, it doesn't reject either. A sequence at the same indentation as its key is the form hand-written Kubernetes and docker-compose files use most. To see how `YamlReader` reads one:

```scala mdoc
println(YamlReader.read("steps:\n- name: Check out\n- name: Test\n"))
```

The table lists what @VERSION@ does with each unsupported form, as run:

| YAML form                                                                                      | What `YamlReader` does in @VERSION@                                                                              |
|------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| A sequence at the same indentation as its key (`steps:` then `- name: a` on the next line)     | Reads `steps` as `null` and the items as odd keys named `- name`. Common in Kubernetes and docker-compose files. |
| A sequence nested directly in a sequence (`- - a`)                                             | Keeps the first item as the text `- a` and drops the rest of the document                                        |
| An anchor on a block value, and `<<` merge keys (`defaults: &defaults` plus an indented block) | Reads the anchor as text and drops the rest of the document                                                      |
| Anchors and aliases on scalars (`&a text`, `*a`)                                               | Keeps them as part of the text                                                                                   |
| Tags (`!!str 123`, `!Ref name`)                                                                | Keeps them as part of the text                                                                                   |
| A second document after `---` or `...`                                                         | Drops it                                                                                                         |
| A tab as indentation                                                                           | Moves the indented keys up to the parent level                                                                   |
| An unterminated `"`, `'`, `[` or `{`                                                           | Accepts it and drops the last character of the value                                                             |
| A line it can't read, such as `pages:744` with no space after the colon                        | Drops that line and every line after it                                                                          |
| The final line break of a literal or folded block                                              | Drops it, and ignores the chomping indicators `-` and `+` that ask to strip or keep line breaks                  |
| `yes`, `no`, `on`, `off`, `True`                                                               | Reads them as text: a `String` field keeps the word, a `Boolean` field accepts only `true` and `false`           |
| An empty value, `null` or `~` in a `String` field                                              | Decodes it as the empty string                                                                                   |

The writer has gaps of its own. It quotes most strings that need quoting, but in @VERSION@ it leaves these unquoted:

- A string that starts with `#`, such as an OpenAPI `$ref` (`#/components/schemas/Book`).
- A string that starts with `- ` or ends with `:`.
- A string with leading or trailing spaces.
- Commas and closing brackets in flow style (see [Control the Output Style](#control-the-output-style)).

The first two are read back correctly by `YamlReader`, which is lenient, but YAML readers that follow the spec misread them or reject the file: the `#` starts a comment, so the value is lost. Leading and trailing spaces are lost by every reader, including `YamlReader`.

## Guard Against Silent Data Loss

The codec can't tell you about data the reader never hands to it, so the defence starts before decoding. Three habits cover the cases above.

**Prefer required fields for data you can't lose.** A field without a default value and without `Option` fails the decode when its key is missing, so a dropped section becomes an error instead of a smaller value. `Catalog` and `Book` follow this rule, so the stray tab below at least causes an error:

```scala mdoc
report(catalogYaml.replace("    isbn: 978-0-9862-0040-6", "\tisbn: 978-0-9862-0040-6"))
```

A field with a default or an `Option` type would have quietly taken its default.

**Check that every key in the text was read.** A key that the reader drops is in the text but not in the tree. Comparing the keys of both catches every case in the table above that loses keys, and it costs a few lines:

```scala mdoc:silent
val keyLine = """^\s*(?:-\s+)?([A-Za-z0-9_.-]+):(?:\s|$)""".r

def keysInText(text: String): List[String] =
  text.linesIterator.flatMap(line => keyLine.findPrefixMatchOf(line).map(_.group(1))).toList

def keysInTree(yaml: Yaml): List[String] = yaml match {
  case Yaml.Mapping(entries) =>
    entries.toList.flatMap {
      case (Yaml.Scalar(key, _), value) => key :: keysInTree(value)
      case (_, value)                   => keysInTree(value)
    }
  case Yaml.Sequence(elements) => elements.toList.flatMap(keysInTree)
  case _                       => Nil
}

def readChecked(text: String): Either[String, Yaml] = {
  val tree   = YamlReader.read(text)
  val unread = keysInText(text).diff(keysInTree(tree))
  if (unread.isEmpty) Right(tree)
  else Left(s"YAML not fully read, missing keys: ${unread.distinct.mkString(", ")}")
}

def decodeChecked[A](codec: YamlCodec[A], text: String): Either[String, A] =
  readChecked(text).flatMap { tree =>
    try Right(codec.decodeValue(tree))
    catch { case _: Exception => codec.decode(text).left.map(_.getMessage) }
  }
```

`List#diff` removes one occurrence per match, so a key that appears in several books is counted once per book. `decodeChecked` decodes the checked tree with `YamlCodec#decodeValue`, which throws on a decode error; in that case it decodes the text again with `YamlCodec#decode` to get the `SchemaError` message. Here it is on three documents that `YamlReader` reads without an error:

```scala mdoc
println(decodeChecked(catalogCodec, catalogYaml).map(_.books.map(_.title)))
println(decodeChecked(catalogCodec, catalogYaml.replace("    page-count: 744", "    page-count:744")))
println(readChecked("steps:\n- name: Check out\n- name: Test\ntimeout-minutes: 30\n"))
```

The check matches keys at the start of a line only. It doesn't see keys inside flow mappings or quoted keys, and a `|` block whose lines look like `key: value` makes it report keys that were never meant to be keys. It catches dropped lines, not wrong values, so keep the codec's own checks too.

**Check what you write before you publish it.** Reading the output back with the codec catches the losses that `YamlReader` itself makes, such as leading spaces and commas in flow style. The strings that `YamlReader` reads correctly but other readers misread need a separate look:

```scala mdoc:silent
def misreadByOtherReaders(yaml: Yaml): List[String] = yaml match {
  case Yaml.Mapping(entries)   => entries.toList.flatMap { case (key, value) => misreadByOtherReaders(key) ++ misreadByOtherReaders(value) }
  case Yaml.Sequence(elements) => elements.toList.flatMap(misreadByOtherReaders)
  case Yaml.Scalar(text, _)    =>
    if (text.startsWith("#") || text.startsWith("- ") || text == "-" || text.endsWith(":")) List(text) else Nil
  case Yaml.NullValue => Nil
}

def writeChecked[A](codec: YamlCodec[A], value: A, options: YamlOptions = YamlOptions.default): Either[String, String] = {
  val tree  = codec.encodeValue(value)
  val text  = YamlWriter.write(tree, options)
  val risky = misreadByOtherReaders(tree)
  if (risky.nonEmpty) Left(s"Other YAML readers would misread: ${risky.mkString(", ")}")
  else if (codec.decode(text) != Right(value)) Left("The YAML doesn't read back as the same value")
  else Right(text)
}
```

To run both checks, first on a plain book and then on three values that fail:

```scala mdoc
println(writeChecked(Book.schema.derive(YamlFormat), zionomicon).map(_ => "OK"))
println(writeChecked(Book.schema.derive(YamlFormat), zionomicon.copy(tags = List("#scala", "zio"))))
println(writeChecked(Book.schema.derive(YamlFormat), zionomicon.copy(title = "Zionomicon, 2nd edition"), YamlOptions.flow))
println(writeChecked(Book.schema.derive(YamlFormat), zionomicon.copy(title = "Zionomicon ")))
```

When the check fails, change the value (drop the trailing space), or write in block style instead of flow style. For `#` values in a document that must keep them, quote them after printing, as the OpenAPI guide does for `$ref`.

## Putting It Together

The complete example reads a catalog with the key check, reports its decode errors, and writes the CI workflow after checking the output:

```scala mdoc:embed:schema-examples/src/main/scala/readandwriteyaml/CompleteExample.scala
```

## Running the Examples

All examples in this guide have runnable Scala files in the `schema-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/schema-examples
```

Each example builds its YAML in memory, so it runs anywhere without setup.

### Read a YAML Catalog Into Case Classes

This example decodes the catalog with the derived codec and with the `String#fromYaml` extension method, and writes a book back out.

<details>
  <summary>schema-examples/src/main/scala/readandwriteyaml/Step1ReadCatalogExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/readandwriteyaml/Step1ReadCatalogExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain readandwriteyaml.Step1ReadCatalogExample"
```

### Report Decode Errors

This example decodes four damaged catalogs and prints each error message with its path.

<details>
  <summary>schema-examples/src/main/scala/readandwriteyaml/Step2DecodeErrorsExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/readandwriteyaml/Step2DecodeErrorsExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain readandwriteyaml.Step2DecodeErrorsExample"
```

### Write a CI Workflow as YAML

This example writes a GitHub Actions workflow from case classes and reads it back.

<details>
  <summary>schema-examples/src/main/scala/readandwriteyaml/Step3WriteWorkflowExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/readandwriteyaml/Step3WriteWorkflowExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain readandwriteyaml.Step3WriteWorkflowExample"
```

### Control the Output Style

This example writes the same workflow with a wider indentation and document markers, and one step in flow style.

<details>
  <summary>schema-examples/src/main/scala/readandwriteyaml/Step4OutputStyleExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/readandwriteyaml/Step4OutputStyleExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain readandwriteyaml.Step4OutputStyleExample"
```

### Build YAML Values in Code

This example builds a tree with the `Yaml` constructors, walks a parsed tree, and builds a test input with the `yaml"..."` interpolator. Because of the interpolator, this file compiles on Scala 3 only.

<details>
  <summary>schema-examples/src/main/scala/readandwriteyaml/Step5BuildYamlInCodeExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/readandwriteyaml/Step5BuildYamlInCodeExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain readandwriteyaml.Step5BuildYamlInCodeExample"
```

### Convert Between YAML and JSON

This example converts a book from JSON to YAML, shows how quoted scalars change type on the way to JSON, and reads a camelCase container spec through JSON.

<details>
  <summary>schema-examples/src/main/scala/readandwriteyaml/Step6YamlAndJsonExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/readandwriteyaml/Step6YamlAndJsonExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain readandwriteyaml.Step6YamlAndJsonExample"
```

### Guard Against Silent Data Loss

This example runs the key check on documents that `YamlReader` reads only in part, and the output check on values the writer doesn't quote.

<details>
  <summary>schema-examples/src/main/scala/readandwriteyaml/Step7GuardAgainstDataLossExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/readandwriteyaml/Step7GuardAgainstDataLossExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain readandwriteyaml.Step7GuardAgainstDataLossExample"
```

### The Complete Example

Run the complete example from the `schema-examples` directory:

```bash
sbt "runMain readandwriteyaml.CompleteExample"
```

### Compile All Examples

To compile every example in the project without running any of them, run this from the `schema-examples` directory:

```bash
sbt compile
```

## Going Further

- The [YAML reference page](../reference/schema/built-in-codecs/yaml.md) lists the primitive types the codec supports and the methods of `YamlCodec`, `Yaml` and `YamlOptions`.
- [One Type, Many Formats](./one-type-many-formats.md) derives JSON, YAML, XML, TOON, MessagePack, Avro and CSV codecs from one schema.
- [Build a Typed Configuration Loader](./typed-config-loader.md) loads application settings from YAML with environment overrides and defaults, through the `config-yaml` module, which uses the same `YamlReader`.
- [How to Read and Write CSV and TSV Files](./read-and-write-csv-and-tsv-files.md) does the same job for tabular data.
