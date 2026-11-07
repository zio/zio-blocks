---
id: json-without-case-classes
title: "How to Read and Modify JSON Without Case Classes"
description: "Parse JSON into a tree, read and change values by path, print it back, and decode only the part you need into a case class."
keywords:
  - "JSON"
  - "JSON Tree"
  - "Untyped JSON"
  - "Json"
  - "JsonSelection"
  - "DynamicOptic"
---

In this guide, we will read and change a bookstore's JSON document without writing a case class for it, using the `Json` tree of ZIO Blocks Schema. By the end, you will have a handful of small snippets that parse JSON text, read one value deep in the document, collect many values at once, change prices, tags and fields in place, print the result, build new JSON in code, and turn one part of the document into a typed `Book` when you need it.

This is the tool for JSON whose shape you don't own or don't want to model: a webhook payload where you need 2 fields out of 50, a document where you patch one value, a third-party response whose shape keeps changing, or a quick script. We start with JSON text and work on it step by step:

- Parse JSON text into a `Json` tree and handle malformed input.
- Read one value deep in the tree, and handle a missing field or a wrong type.
- Select many values at once with a wildcard path.
- Change values, add fields and remove fields.
- Print the tree back to compact or pretty JSON text.
- Build new JSON in code.
- Decode one part of the tree into a case class.

## The Problem

When your code needs one value from a large JSON document, the usual options are both poor. You can write case classes for the whole document, which means modelling dozens of fields you never read and updating them whenever the sender adds one. Or you can search the text directly, which looks fine until the document contains the same key twice.

For example, consider an order from a bookshop's webhook, where we want the price of the first book. A regular expression finds the first `"price"` key in the text:

```scala mdoc
val webhookText =
  """{"shipping": {"method": "express", "price": 4.99}, "books": [{"title": "Zionomicon", "price": 49.99}]}"""

val pricePattern = "\"price\"\\s*:\\s*([0-9.]+)".r

println(pricePattern.findFirstMatchIn(webhookText).map(_.group(1)))
```

The code above returns the shipping price instead of the book's price, because text search has no idea which object a key belongs to. It also breaks when the value is a string with an escaped quote, when the sender reorders the keys, or when the number uses an exponent. Changing a value is worse: a text replacement can hit the wrong key, and nothing checks that the result is still valid JSON.

In this guide, we solve this with `Json`, an immutable tree that mirrors the JSON document exactly. We parse the text once, then read and change values by their path in the tree, such as `.books[0].price`, and print the tree back to text when we are done.

## Prerequisites

To follow along, add the schema module to your build. `Json` lives in it; no extra JSON module is needed:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "@VERSION@"

// Scala 3.9 and newer: the same module with the zio-blocks-next- prefix
libraryDependencies += "dev.zio" %% "zio-blocks-next-schema" % "@VERSION@"
scalacOptions += "-experimental"
```

For scala-cli, Scala.js, or cross-building, see [How to Add ZIO Blocks to Your Build](../getting-started/installation.md).

The code on this page compiles on Scala 2.13 and Scala 3, except the `json"..."` interpolator in [Build JSON in Code](#build-json-in-code), which works only on Scala 3 in this release. No knowledge of `Schema` is needed until the last section, [Decode Part of the Tree Into a Case Class](#decode-part-of-the-tree-into-a-case-class), which assumes you can derive a `Schema` as shown in [Your First Schema](./your-first-schema.md).

## The Bookstore Document

Every example on this page works on one document: a small bookstore with three books. It has the things real payloads have: nested objects, arrays of strings, decimal prices, and one book that lacks a field the other two have (`Functional Programming in Scala` has no `stock`).

We import the schema package, which provides the `p"..."` path interpolator, and the `json` package, which provides `Json` itself:

```scala mdoc:silent
import zio.blocks.schema._
import zio.blocks.schema.json._

val bookstoreText: String =
  """{
    |  "store": "Lambda Books",
    |  "currency": "USD",
    |  "books": [
    |    {
    |      "title": "Zionomicon",
    |      "authors": ["John A. De Goes", "Adam Fraser", "Milad Khajavi"],
    |      "price": 49.99,
    |      "tags": ["zio", "effects"],
    |      "stock": 12
    |    },
    |    {
    |      "title": "Programming in Scala",
    |      "authors": ["Martin Odersky", "Lex Spoon", "Bill Venners", "Frank Sommers"],
    |      "price": 59.99,
    |      "tags": ["scala", "language"],
    |      "stock": 0
    |    },
    |    {
    |      "title": "Functional Programming in Scala",
    |      "authors": ["Michael Pilquist", "Rúnar Bjarnason", "Paul Chiusano"],
    |      "price": 44.99,
    |      "tags": ["fp"]
    |    }
    |  ]
    |}""".stripMargin
```

## Parse JSON Text

`Json.parse` turns JSON text into a `Json` tree. It never throws: it returns `Either[SchemaError, Json]`, with `Right` holding the tree and `Left` holding what went wrong. To parse the bookstore and stop with an exception if the text is malformed:

```scala mdoc:silent
val bookstore: Json = Json.parse(bookstoreText) match {
  case Right(json)  => json
  case Left(error) => throw error
}
```

`Json` is a sealed trait with one case class per JSON kind: `Json.Object`, `Json.Array`, `Json.String`, `Json.Number`, `Json.Boolean` and `Json.Null`. The tree keeps the document's key order, and every number is held as a `BigDecimal`, so `49.99` stays exactly `49.99`.

When the text is not valid JSON, `Json.parse` returns `Left`. For a trailing comma and for a missing comma between two fields:

```scala mdoc
println(Json.parse("""{"title": "Zionomicon", "price": 49.99,}"""))

println(Json.parse("""{"title": "Zionomicon" "price": 49.99}"""))
```

The message says what the parser expected and, after `at:`, where in the document structure it was (`.` is the root object). It gives no line or column: in this release, a parse error never carries a character offset, so for a large document you have to find the spot by its path in the structure.

`Json.parse` also accepts `Array[Byte]`, `java.nio.ByteBuffer` and `Chunk[Byte]`, so a raw HTTP request body needs no conversion to `String` first. When the input is trusted, such as a resource file shipped with your program, `Json.parseUnsafe` returns the `Json` directly and throws the `SchemaError` on malformed input.

## Read One Value Deep in the Tree

To read a value, we walk to it and decode it. `Json#get` takes a key (for an object) or an index (for an array) and returns a `JsonSelection`: the values found so far, or the error that stopped the walk. `JsonSelection#as[A]` then decodes the selected value into a Scala type.

To read the first book's price one step at a time, use `Json#get` for the `books` key, `JsonSelection#apply` for the index, and `JsonSelection#get` for the `price` key:

```scala mdoc
bookstore.get("books")(0).get("price").as[BigDecimal]
```

The same walk can be written as one path with the `p"..."` interpolator, which reads like the JSON it walks: `.books` selects a key, `[0]` an array index. `Json#get` also accepts this path:

```scala mdoc
bookstore.get(p".books[0].price").as[BigDecimal]

bookstore.get(p".books[1].authors[0]").as[String]
```

The `p"..."` path is checked when your code compiles, and it accepts only literal text. When the key or the index is only known at run time, chain `Json#get` and `JsonSelection#get` calls with the runtime values instead, for example `bookstore.get("books")(index).get(field)`.

:::tip
A key that is not a Scala identifier, such as `first-name`, can be written in `p"..."` only as a quoted key, `p"""{"first-name"}"""`, which `Json#get`, `Json#modify` and `Json#set` follow, but `Json#delete` and `Json#insert` ignore it and return the document unchanged. Build a path that works with every method with `DynamicOptic.root.field("first-name")`, which `Json#get` accepts the same way.
:::

Every read returns an `Either`, so a missing field and a value of the wrong type are ordinary results, not exceptions. When the second book has no `isbn` field, and when we read a title as a number:

```scala mdoc
println(bookstore.get(p".books[1].isbn").as[String])

println(bookstore.get(p".books[0].title").as[BigDecimal])

println(bookstore.get(p".books[5].title").as[String])
```

A failed step stops the walk, and the error passes through every later `JsonSelection#get`, so we check for errors once at the end. The path in a decode error (`at: .`) is relative to the selected value, not to the document. A not-found error names only the last key, without the path to it, so a long walk is easier to debug when you keep the path at hand.

For a field that may be missing, turn the `Either` into an `Option`. The third book has no `stock`, so we read it with a default:

```scala mdoc
bookstore.get(p".books[2].stock").as[Int].getOrElse(0)
```

## Select Many Values at Once

A path can select more than one value. `[*]` selects every element of an array, so `.books[*].title` selects the title of every book. `JsonSelection#asAll[A]` decodes all selected values and returns them in a `Chunk`. To list every title and add up every price:

```scala mdoc
println(bookstore.get(p".books[*].title").asAll[String])

println(bookstore.get(p".books[*].price").asAll[BigDecimal].map(_.sum))
```

`JsonSelection#filter` keeps the selected values that pass a test, and we can keep walking from the values that remain. To list the titles of the books that cost less than 50:

```scala mdoc
println(
  bookstore
    .get(p".books[*]")
    .filter(book => book.get("price").as[BigDecimal].exists(_ < 50))
    .get("title")
    .asAll[String]
)
```

`JsonSelection#as[A]` expects exactly one value, so `bookstore.get(p".books[*].title").as[String]` fails with `Expected single value but got 3`. Use `JsonSelection#asAll` for a wildcard path, and `JsonSelection#as` for a path that names one value.

:::warning
A wildcard path skips the elements that lack the field. `bookstore.get(p".books[*].stock").asAll[Int]` returns `Chunk(12, 0)`, two values for three books, with no error. It fails only when no element has the field at all. When you need one result per element, walk the elements yourself and give each missing field a default:

```scala mdoc
println(bookstore.get("books").one.map(_.elements.map(book => book.get("stock").as[Int].getOrElse(0))))
```
:::

`JsonSelection#one` returns the single selected value as `Either[SchemaError, Json]`, and `Json#elements` returns an array's elements as a `Chunk[Json]`.

## Change Values in the Tree

`Json` is immutable: every change returns a new tree and leaves the original as it was. Each change method takes a path, which can contain `[*]` to change every element of an array at once.

`Json#modify` replaces each value at the path with the result of a function. To raise every price by 10%, we match on `Json.Number`, which holds a `BigDecimal`, and round the result to cents:

```scala mdoc:silent
val raised: Json = bookstore.modify(p".books[*].price") {
  case Json.Number(price) =>
    Json.Number((price * BigDecimal("1.1")).setScale(2, BigDecimal.RoundingMode.HALF_UP))
  case other => other
}
```

We read the prices back to check the change:

```scala mdoc
println(raised.get(p".books[*].price").asAll[BigDecimal])
```

Without `setScale`, the new prices would keep every digit of the multiplication, such as `54.989`, because `BigDecimal` arithmetic never rounds by itself.

The same method changes an array. To add a tag to the first book, we append a `Json.String` to the `Chunk` that `Json.Array` holds:

```scala mdoc:silent
val tagged: Json = raised.modify(p".books[0].tags") {
  case Json.Array(tags) => Json.Array(tags :+ Json.String("bestseller"))
  case other            => other
}
```

`Json#set` replaces a value that exists, and `Json#insert` adds a field that doesn't exist yet. To rename the store and give every book an `available` field that says whether its `stock` is above zero, we insert into each book through `Json#modify`:

```scala mdoc:silent
val withAvailability: Json = tagged
  .set(p".store", Json.String("Lambda Books Online"))
  .modify(p".books[*]") { book =>
    book.insert(p".available", Json.Boolean(book.get("stock").as[Int].exists(_ > 0)))
  }
```

`Json#delete` removes a field or an array element. To remove the internal `stock` field from every book before publishing the document, we delete it inside each book:

```scala mdoc:silent
val published: Json = withAvailability.modify(p".books[*]")(_.delete(p".stock"))
```

`JsonSelection#toChunk` returns the selected values as a `Chunk[Json]`. Each book now has its new fields and no `stock`:

```scala mdoc
println(published.get(p".books[*]").toChunk.map(_.print).mkString("\n"))
```

:::warning
Do not write `withAvailability.delete(p".books[*].stock")` in this release. A known bug in 0.0.56 removes every book that has no `stock` field, together with the field: here, `Functional Programming in Scala` would disappear from the array. Deleting inside each element with `modify(p".books[*]")(_.delete(p".stock"))`, as above, keeps every book.
:::

A change at a path that doesn't exist does nothing and reports nothing, so a typo in a path changes nothing silently. When the path must exist, use the `OrFail` variant of the method (`Json#modifyOrFail`, `Json#setOrFail`, `Json#deleteOrFail`, `Json#insertOrFail`), which returns `Either[SchemaError, Json]`. With the key `currency` misspelled:

```scala mdoc
println(published.set(p".curency", Json.String("EUR")) == published)

println(published.setOrFail(p".curency", Json.String("EUR")))
```

`Json#modifyOrFail` takes a partial function, and it also fails when the function has no case for a value it finds, so it can turn the `case other => other` fallback above into an error.

## Print JSON Text

`Json#print` writes the tree as compact JSON text, with no spaces or line breaks. `Json#print(config)` takes a `WriterConfig`; `WriterConfig.withIndentionStep(2)` indents nested values by two spaces. To print the first book both ways, we take it out of its selection with `JsonSelection#one`:

```scala mdoc:silent
val firstBook: Json = published.get(p".books[0]").one.getOrElse(Json.Null)
```

Then we print it compact and indented:

```scala mdoc
println(firstBook.print)

println(firstBook.print(WriterConfig.withIndentionStep(2)))
```

`Json#toString` prints the same indented form, so `println(json)` shows readable JSON while you debug. Three things hold for both forms:

- Keys come out in the order they had in the input, and inserted keys come last.
- Numbers keep the text they were parsed from, so `49.99` prints as `49.99` and `1.10` keeps its trailing zero.
- Strings are escaped where JSON requires it, and non-ASCII characters such as the `ú` in `Rúnar` are written as they are. `WriterConfig.withEscapeUnicode(true)` writes them as `\u` escapes instead.

Two trees are equal with `==` when they hold the same values, and the key order of an object does not matter. The order of array elements does:

```scala mdoc
Json.parse("""{"title": "Zionomicon", "price": 49.99}""") ==
  Json.parse("""{"price": 49.99, "title": "Zionomicon"}""")

Json.parse("""["zio", "effects"]""") == Json.parse("""["effects", "zio"]""")
```

This makes `==` a good way to compare an expected document with an actual one in a test, without caring how either was formatted.

## Build JSON in Code

Sometimes the JSON comes from your own code: a request body, a test fixture, or a new element to add to a document. ZIO Blocks has two ways to write it.

### With Constructors

The `Json` case classes build any document, on Scala 2.13 and Scala 3. `Json.Object` and `Json.Array` take their contents as arguments. To build a fourth book and append it to the `books` array:

```scala mdoc:silent
val handsOnScala: Json = Json.Object(
  "title"   -> Json.String("Hands-on Scala Programming"),
  "authors" -> Json.Array(Json.String("Li Haoyi")),
  "price"   -> Json.Number(BigDecimal("39.99")),
  "tags"    -> Json.Array(Json.String("scala"), Json.String("tooling"))
)

val fourBooks: Json = published.modify(p".books") {
  case Json.Array(books) => Json.Array(books :+ handsOnScala)
  case other             => other
}
```

The new book is the last element of the array:

```scala mdoc
println(fourBooks.get(p".books[*].title").asAll[String])
```

:::warning
Build decimal numbers from a `BigDecimal` or a `String`, not from a `Double`. In this release, a known bug in 0.0.56 makes `Json.Number(49.99)` print as `49.990` and `Json.Number(2.5)` as `2.50`. `Json.Number(BigDecimal("49.99"))` prints `49.99`. `Int` and `Long` values are not affected.
:::

### With the `json"..."` Interpolator (Scala 3)

On Scala 3, the `json"..."` interpolator from `zio.blocks.schema.json` writes the same document as JSON text, with Scala values spliced in. The text is checked when your code compiles: a malformed literal such as `json"""{"title": }"""` fails with `Invalid JSON literal: expected JSON value at: .title`.

To build the same fourth book from text, with its title, price and tags spliced in from Scala values:

```scala mdoc:silent
val title = "Hands-on Scala Programming"
val price = BigDecimal("39.99")
val tags  = List("scala", "tooling")

val handsOnScalaFromText: Json =
  json"""{"title": $title, "authors": ["Li Haoyi"], "price": $price, "tags": $tags}"""
```

The result is the same tree that the constructors built:

```scala mdoc
println(handsOnScalaFromText.print)

handsOnScalaFromText == handsOnScala
```

A spliced value is encoded as JSON, not pasted into the text, so a `String` with a quote or a backslash in it is escaped correctly, and a `List` becomes an array. A `Json` value is spliced as it is. Any other type needs an implicit `Schema` for it: a `case class` with a derived `Schema` becomes an object.

On Scala 2.13, `json"..."` does not compile in this release: every call fails with `object JsonInterpolatorRuntime in package json cannot be accessed as a member of package zio.blocks.schema.json`, a known bug in 0.0.56. On Scala 2.13, build JSON with the constructors above, or parse a string with `Json.parse`.

## Decode Part of the Tree Into a Case Class

Once you know the shape of one part of the document, you can decode just that part into a case class and leave the rest as JSON. We model a book with the four fields we care about, and derive its `Schema`:

```scala mdoc:silent
case class Book(title: String, authors: List[String], price: BigDecimal, tags: List[String])

object Book {
  implicit val schema: Schema[Book] = Schema.derived
}
```

`JsonSelection#as[Book]` decodes one selected value, and `JsonSelection#asAll[Book]` decodes all of them. To decode the first book, and then every book:

```scala mdoc
bookstore.get(p".books[0]").as[Book]

println(bookstore.get(p".books[*]").asAll[Book].map(_.map(_.title)))
```

The decoder ignores fields that `Book` doesn't declare, such as `stock`, so the case class can model only the part of the payload you read. `Json#as[A]` works the same way when you already hold a `Json` value, and a missing `List` field decodes as an empty list.

When the sub-tree doesn't match the case class, the result is a `Left`. Its path is relative to the decoded value: for a book whose price is a string, the error points at `.price`, not at the book's place in a larger document:

```scala mdoc:silent
val badBook: Json = Json.parse("""{"title": "Functional Programming in Scala", "price": "44.99"}""").getOrElse(Json.Null)
```

Decoding it with `Json#as` reports the price:

```scala mdoc
println(badBook.as[Book])
```

`Json#as` stops at the first error. To report every problem in a payload at once, convert the tree to a `DynamicValue` with `Json#toDynamicValue` and decode it with `Schema#fromDynamicValue`, the approach the guide [How to Define Validated Newtypes and Collect Every Validation Error](./validated-newtypes.md) uses to collect every decode error:

```scala mdoc
println(Schema[Book].fromDynamicValue(badBook.toDynamicValue))
```

This route is stricter than `Json#as`, a known difference in 0.0.56: besides the wrong price, it reports the missing `authors` and `tags` lists, which `Json#as` decodes as empty lists. It also rejects a missing `Option` field or a missing field that has a default value, which `Json#as` fills with `None` or the default. Send those fields explicitly when you decode this way.

To go the other way and put a typed value back into a document, `Json.from(value)` encodes it with its `Schema`. To replace the first book with a copy that has a new price:

```scala mdoc:silent
val zionomicon: Book = bookstore.get(p".books[0]").as[Book].getOrElse(sys.error("no first book"))

val reprinted: Json = bookstore.set(p".books[0]", Json.from(zionomicon.copy(price = BigDecimal("39.99"))))
```

The first element is now the encoded `Book`:

```scala mdoc
println(reprinted.get(p".books[0]").one.map(_.print))
```

The encoded book replaces the whole element, so fields that `Book` doesn't declare, such as `stock`, are gone from it. To change one field and keep the rest, use `Json#set` on that field's path instead.

## Coming From circe or ujson

If you come from circe's cursors and optics, or from ujson, most operations have a direct counterpart. This table covers the tree operations on this page; for moving derived codecs, see [Coming from circe, zio-json or jsoniter-scala](./json-library-migration.md).

| Task                     | circe / ujson                                                 | ZIO Blocks                                              |
| ------------------------ | ------------------------------------------------------------- | ------------------------------------------------------- |
| Parse text               | `io.circe.parser.parse(text)`, `ujson.read(text)`             | `Json.parse(text)`                                      |
| Read one value           | `json.hcursor.downField("books").downN(0).get[BigDecimal]("price")` | `json.get(p".books[0].price").as[BigDecimal]`     |
| Read by runtime index    | `ujson` `json("books")(i)("price").num`                       | `json.get("books")(i).get("price").as[BigDecimal]`      |
| Change every element     | circe-optics `root.books.each.price.bigDecimal.modify(f)(json)` | `json.modify(p".books[*].price")(f)`                  |
| Remove a field           | `json.hcursor.downField("stock").delete`                      | `json.delete(p".stock")`                                |
| Print compact or pretty  | `json.noSpaces`, `json.spaces2`                               | `json.print`, `json.print(WriterConfig.withIndentionStep(2))` |
| Decode a sub-tree        | `cursor.as[Book]`                                             | `json.get(p".books[0]").as[Book]`                       |
| JSON literal             | circe-literal `json"..."`                                     | `json"..."` (Scala 3)                                   |

## Putting It Together

The complete example handles a bookstore webhook end to end: it parses the payload, reads the store name and every title, raises the prices, tags the first book, removes the internal `stock` field, decodes the books into case classes, and prints the document it would publish. It carries its own copy of the document, so it does not need `Bookstore.scala`, the support file that the step examples under [Running the Examples](#running-the-examples) share:

```scala mdoc:embed:schema-examples/src/main/scala/jsonwithoutcaseclasses/CompleteExample.scala
```

## Running the Examples

All examples in this guide have runnable Scala files in the `schema-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/schema-examples
```

The step examples share the bookstore document, which `Bookstore.scala` next to them parses once. The complete example has its own copy and doesn't need it.

<details>
  <summary>schema-examples/src/main/scala/jsonwithoutcaseclasses/Bookstore.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/jsonwithoutcaseclasses/Bookstore.scala:show-line-numbers
```

</details>

### Parse JSON Text

This example parses the bookstore document and prints the errors for two malformed inputs.

<details>
  <summary>schema-examples/src/main/scala/jsonwithoutcaseclasses/Step1ParseExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/jsonwithoutcaseclasses/Step1ParseExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain jsonwithoutcaseclasses.Step1ParseExample"
```

### Read One Value Deep in the Tree

This example reads single values by key, by index and by path, and prints the errors for a missing field and a wrong type.

<details>
  <summary>schema-examples/src/main/scala/jsonwithoutcaseclasses/Step2ReadOneValueExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/jsonwithoutcaseclasses/Step2ReadOneValueExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain jsonwithoutcaseclasses.Step2ReadOneValueExample"
```

### Select Many Values at Once

This example selects every title and price with `[*]`, filters the books by price, and reads a field that one book lacks.

<details>
  <summary>schema-examples/src/main/scala/jsonwithoutcaseclasses/Step3SelectManyExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/jsonwithoutcaseclasses/Step3SelectManyExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain jsonwithoutcaseclasses.Step3SelectManyExample"
```

### Change Values in the Tree

This example raises the prices, adds a tag, sets and inserts fields, removes `stock` from every book, and shows how `Json#setOrFail` catches a misspelled path.

<details>
  <summary>schema-examples/src/main/scala/jsonwithoutcaseclasses/Step4ChangeTreeExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/jsonwithoutcaseclasses/Step4ChangeTreeExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain jsonwithoutcaseclasses.Step4ChangeTreeExample"
```

### Print JSON Text

This example prints a book as compact and as indented JSON, and compares two documents whose keys come in a different order.

<details>
  <summary>schema-examples/src/main/scala/jsonwithoutcaseclasses/Step5PrintExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/jsonwithoutcaseclasses/Step5PrintExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain jsonwithoutcaseclasses.Step5PrintExample"
```

### Build JSON in Code

This example builds a new book with the `Json` constructors and with the `json"..."` interpolator, and appends it to the document. It uses `json"..."`, so it needs Scala 3, which the `schema-examples` build uses.

<details>
  <summary>schema-examples/src/main/scala/jsonwithoutcaseclasses/Step6BuildJsonExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/jsonwithoutcaseclasses/Step6BuildJsonExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain jsonwithoutcaseclasses.Step6BuildJsonExample"
```

### Decode Part of the Tree Into a Case Class

This example decodes one book and every book into `Book`, prints the errors for a malformed book from both decoders, and writes an updated book back into the document.

<details>
  <summary>schema-examples/src/main/scala/jsonwithoutcaseclasses/Step7DecodeSubTreeExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/jsonwithoutcaseclasses/Step7DecodeSubTreeExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain jsonwithoutcaseclasses.Step7DecodeSubTreeExample"
```

### The Complete Example

Run the complete example from the `schema-examples` directory:

```bash
sbt "runMain jsonwithoutcaseclasses.CompleteExample"
```

### Compile All Examples

To compile every example in the project without running any of them, run this from the `schema-examples` directory:

```bash
sbt compile
```

## Going Further

- The [Json reference page](../reference/schema/built-in-codecs/json/json.md) covers the rest of the tree API: `transformUp` and `transformKeys` for rewriting a whole document, `prune` and `retain`, `merge` with its strategies, and folds.
- The [JsonSelection reference page](../reference/schema/built-in-codecs/json/json-selection.md) lists every way to filter, combine and extract a selection.
- The [path interpolator reference](../reference/schema/path-interpolator.md) documents the full `p"..."` syntax, including index ranges such as `[0:2]` and several indices such as `[0,2]`.
- To compute the difference between two documents and apply it as a patch, see [JSON Patch](../reference/schema/built-in-codecs/json/json-patch.md).
- When the shape is known and stable, derive a codec for the whole type instead: [How to Customize JSON Encoding](./customize-json-encoding.md) shows how to control field names, optional fields and enums.
