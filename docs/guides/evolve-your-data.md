---
id: evolve-your-data
title: "Evolve Your Data: Change Your Types with Into, As and Migration"
description: "Validate incoming data with Into, convert between versions both ways with As, and rename stored fields with a compile-checked Migration."
keywords:
  - "Schema Evolution"
  - "Data Versioning"
  - "Into"
  - "As"
  - "Migration"
  - "SchemaError"
---

Welcome! This tutorial is for Scala developers who have finished [Your First Schema](./your-first-schema.md), or who already know how to write a case class, derive a `Schema` for it and read an `Either`. You don't need any prior experience with data conversion libraries or schema migrations to follow along.

## 1. Introduction

By the end of this tutorial, you will understand:

- How `Into` converts one type into another and validates the values on the way
- Why `Into` reports every bad field at once instead of stopping at the first one
- How `As` converts between two versions of a type in both directions
- How a `Migration` describes a change such as a field rename, and how the compiler checks that it is complete
- When to reach for each of the three tools

We learn these concepts by evolving a small book catalog, one step at a time:

1. Convert incoming data with `Into`
2. Collect every error at once
3. Convert both ways with `As`
4. Rename a field with `Migration`
5. Let the compiler check a migration
6. Run a migration backwards and look inside it

We recommend reading from top to bottom — each section builds on the previous one.

To follow along, add the schema module to your `build.sbt`:

```scala
// Scala 2.13 and Scala 3.3 LTS
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "@VERSION@"

// Scala 3.7 and newer
libraryDependencies += "dev.zio" %% "zio-blocks-next-schema" % "@VERSION@"
```

The code in this tutorial is compiled with Scala 3.

## 2. Background: Data Outlives Its Types

A case class describes the shape of your data today. The data itself lives much longer: it arrives from other services in their own shapes, it sits in databases and message queues, and it is read by older and newer versions of your own program at the same time. Every time a shape changes, something has to turn the old shape into the new one.

ZIO Blocks gives you three tools for that job, and each one adds a guarantee to the one before it. `Into[A, B]` converts an `A` into a `B` in one direction and checks every value on the way. `As[A, B]` does the same in both directions, so two versions of a type can be converted back and forth. `Migration[A, B]` describes a change between two versions as data — rename this field, add that one — and the compiler refuses to build a migration that forgets a field. In this tutorial we use all three on the same book catalog.

## 3. Convert Incoming Data with Into

Our catalog receives books from a partner's feed. The feed is written in JavaScript, so every number arrives as a `Double`. Our own `BookV1` type uses `Int` for the publication year and the edition number. An `Into[BookDto, BookV1]` is a value that converts one into the other.

Let's define both types and derive the conversion:

```scala mdoc:silent
import zio.blocks.schema._

case class BookDto(title: String, author: String, year: Double, edition: Double)

case class BookV1(title: String, author: String, year: Int, edition: Int)

val dtoToBook: Into[BookDto, BookV1] = Into.derived[BookDto, BookV1]
```

The code above:

- `BookDto` is the shape the feed sends, with `Double` numbers.
- `BookV1` is our domain type, with `Int` numbers.
- `Into.derived[BookDto, BookV1]` generates the conversion at compile time. It matches the fields by name and, for `year` and `edition`, uses the built-in `Into[Double, Int]` conversion that ZIO Blocks provides.

Converting can fail, because not every `Double` is a whole number. That's why `Into#into` returns an `Either[SchemaError, BookV1]`. Let's convert a book from the feed:

```scala mdoc
val zionomiconDto = BookDto("Zionomicon", "John A. De Goes", 2024.0, 1.0)

println(dtoToBook.into(zionomiconDto))
```

The code above:

- `BookDto("Zionomicon", ...)` builds a value in the feed's shape.
- `dtoToBook.into(zionomiconDto)` converts it and returns a `Right` holding a `BookV1`.

Notice that `2024.0` became the `Int` `2024`. We never wrote a conversion function: the field names told `Into.derived` which fields belong together, and the field types told it which conversion to use.

## 4. Collect Every Error at Once

A feed can also send values that make no sense for our type. Let's send a book whose year and edition are not whole numbers:

```scala mdoc
val brokenDto = BookDto("Programming in Scala", "Martin Odersky", 2008.5, 1.5)

dtoToBook.into(brokenDto) match {
  case Left(error) => println(error.message)
  case Right(book) => println(s"Converted: $book")
}
```

The code above:

- `BookDto("Programming in Scala", ...)` carries two bad values: `2008.5` and `1.5`.
- `dtoToBook.into(brokenDto)` returns a `Left` holding a `SchemaError`.
- `case Left(error) => println(error.message)` prints the error as readable text.

Notice that the message lists **both** problems. `Into` doesn't stop at the first bad field; it converts every field, collects every failure and returns them together in one `SchemaError`. Each entry names the source field, the target field and the reason, so whoever runs the feed can fix all of the bad values in one go instead of discovering them one by one.

:::tip
Try changing `2008.5` back to `2008.0` and running the example again. The message then lists only the `edition` problem.
:::

## 5. Convert Both Ways with As

Our catalog grows: the next version of the service also stores the book's ISBN. Not every book has a known ISBN, so the new field is an `Option`:

```scala mdoc:silent
case class BookV2(title: String, author: String, year: Int, edition: Int, isbn: Option[String])
```

During a rolling deploy, some servers run the old code and some run the new code, and they exchange books. The new servers need to read `BookV1` values, and the old servers need to read `BookV2` values. An `As[BookV1, BookV2]` is an `Into` that also knows the way back:

```scala mdoc:silent
val v1AndV2: As[BookV1, BookV2] = As.derived[BookV1, BookV2]

val v2ToV1: As[BookV2, BookV1] = v1AndV2.reverse
```

The code above:

- `As.derived[BookV1, BookV2]` derives both directions at compile time: `As#into` turns a `BookV1` into a `BookV2`, and `As#from` turns a `BookV2` back into a `BookV1`.
- `v1AndV2.reverse` is the same pair of conversions with the directions swapped, so `v2ToV1.into` goes from `BookV2` to `BookV1`.

Let's take a `BookV1` forward to `BookV2`:

```scala mdoc
val programmingInScalaV1 = BookV1("Programming in Scala", "Martin Odersky", 2008, 1)

println(v1AndV2.into(programmingInScalaV1))
```

The code above converts the book forward. The `BookV1` has no ISBN, so `isbn` becomes `None`. Now let's take a book that a new server already filled in, with its ISBN, and send it back to an old server:

```scala mdoc
val programmingInScalaV2 = BookV2("Programming in Scala", "Martin Odersky", 2008, 1, Some("978-0981531601"))

println(v2ToV1.into(programmingInScalaV2))
```

The code above converts the `BookV2` back into a `BookV1`. The old shape has nowhere to keep the ISBN, so the result leaves it out. Finally, let's check the round trip that a rolling deploy relies on — forward to `BookV2` and back again:

```scala mdoc
println(v1AndV2.into(programmingInScalaV1).flatMap(v1AndV2.from))
```

The code above chains `into` and `from` with `flatMap`, so the result is `Right` only if both steps succeed. Notice that we get back exactly the `BookV1` we started with.

:::note
`As` checks at compile time that every field can travel in both directions. A field that exists only in the newer type must be an `Option`, so the conversion back has nothing to invent. `As` pairs fields by name, so a renamed field looks like one field removed and a new one added — and when the new one is not an `Option`, such as an `Int`, `As.derived` refuses to compile.
:::

## 6. Rename a Field with Migration

The catalog's next version renames `year` to `publishedYear`, because the team wants to add other years later. As we just saw, `As` pairs fields by name, so a rename is exactly the change it can't describe. This is a job for `Migration`.

A `Migration[A, B]` is built step by step from a list of changes. To build one, the library needs a `Schema` for both versions, because it applies the changes to a generic view of the data:

```scala mdoc:silent
import zio.blocks.schema.migration.Migration

case class BookV3(title: String, author: String, publishedYear: Int, edition: Int, isbn: Option[String])

implicit val bookV2Schema: Schema[BookV2] = Schema.derived
implicit val bookV3Schema: Schema[BookV3] = Schema.derived

val v2ToV3: Migration[BookV2, BookV3] =
  Migration
    .newBuilder[BookV2, BookV3]
    .renameField(_.year, _.publishedYear)
    .build
```

The code above:

- `import zio.blocks.schema.migration.Migration` brings `Migration` into scope; it lives in its own package.
- `BookV3` is the new shape: the same fields as `BookV2`, with `year` renamed to `publishedYear`.
- The two `implicit val`s derive the schemas that `Migration.newBuilder` asks for.
- `Migration.newBuilder[BookV2, BookV3]` starts an empty builder.
- `.renameField(_.year, _.publishedYear)` records one change. The two arguments are selectors: `_.year` points at the field in `BookV2` and `_.publishedYear` at the field in `BookV3`, so a typo in either name is a compile error.
- `.build` turns the recorded changes into a `Migration[BookV2, BookV3]`. Fields that keep their name and type, such as `title` and `isbn`, are carried over without a step of their own.

Like `Into` and `As`, `Migration#apply` returns an `Either[SchemaError, BookV3]`. Let's migrate a stored book:

```scala mdoc
val fpInScalaV2 = BookV2("Functional Programming in Scala", "Paul Chiusano", 2014, 1, Some("978-1617290657"))

println(v2ToV3(fpInScalaV2))
```

The code above migrates a `BookV2` to a `BookV3`. Notice that the value of `year` now sits in `publishedYear`, and every other field kept its value.

## 7. Let the Compiler Check a Migration

A migration is only useful if it accounts for every field. Let's forget the rename and try to build the same migration with no steps at all:

```scala mdoc:fail
Migration.newBuilder[BookV2, BookV3].build
```

The code above does not compile, and the output shows the compiler error. `build` compares the fields of `BookV2` with the fields of `BookV3`. Notice that the message names both sides of the forgotten rename: `publishedYear` is a target field that no step provides, and `year` is a source field that no step handles. The message also lists the builder methods that can fix each side.

This check runs every time the code compiles. When someone later adds a field to `BookV3` and forgets to update the migration, the build fails before any stored data is touched.

## 8. Run a Migration Backwards and Look Inside It

A migration is a description of changes, not a function hidden inside a closure. That gives us two things for free. The first is `Migration#reverse`, which undoes each step in the opposite order:

```scala mdoc:silent
val v3ToV2: Migration[BookV3, BookV2] = v2ToV3.reverse

val fpInScalaV3 = BookV3("Functional Programming in Scala", "Paul Chiusano", 2014, 1, Some("978-1617290657"))
```

The code above:

- `v2ToV3.reverse` builds a `Migration[BookV3, BookV2]` that renames `publishedYear` back to `year`.
- `fpInScalaV3` is the book we migrated in section 6, now in its `BookV3` shape.

Let's apply the reversed migration:

```scala mdoc
println(v3ToV2(fpInScalaV3))
```

The code above returns a `Right` holding the original `BookV2`, with the publication year back in `year`.

The second is that we can print the steps themselves with `Migration#actions`:

```scala mdoc
println(v2ToV3.actions)
```

The code above prints the actions inside `v2ToV3`. Notice that there is one `RenameField` action holding the path of the field and its new name — plain data, with no Scala function inside. Underneath every `Migration` sits this untyped `DynamicMigration`, which is why a migration can be inspected, reversed and combined with another migration.

## 9. Putting It Together

This example combines all the concepts we've learned — validating feed data with `Into`, collecting several errors at once, converting between two versions with `As`, renaming a field with `Migration` and running the migration backwards:

```scala mdoc:embed:schema-examples/src/main/scala/evolveyourdata/CompleteExample.scala
```

## 10. Running the Examples

All examples in this tutorial have corresponding runnable Scala files in the `schema-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/schema-examples
```

Run them in order to follow the tutorial step by step.

### Convert Incoming Data with Into

This example derives an `Into[BookDto, BookV1]` and converts a book from the feed.

<details>
  <summary>schema-examples/src/main/scala/evolveyourdata/Concept1IntoExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/evolveyourdata/Concept1IntoExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain evolveyourdata.Concept1IntoExample"
```

### Collect Every Error at Once

This example converts a feed record with two bad fields and prints the single `SchemaError` that lists both.

<details>
  <summary>schema-examples/src/main/scala/evolveyourdata/Concept2ErrorAccumulationExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/evolveyourdata/Concept2ErrorAccumulationExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain evolveyourdata.Concept2ErrorAccumulationExample"
```

### Convert Both Ways with As

This example derives an `As[BookV1, BookV2]`, converts in both directions and checks the round trip.

<details>
  <summary>schema-examples/src/main/scala/evolveyourdata/Concept3AsExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/evolveyourdata/Concept3AsExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain evolveyourdata.Concept3AsExample"
```

### Rename a Field with Migration

This example builds a `Migration[BookV2, BookV3]` that renames `year` to `publishedYear` and applies it.

<details>
  <summary>schema-examples/src/main/scala/evolveyourdata/Concept4MigrationExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/evolveyourdata/Concept4MigrationExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain evolveyourdata.Concept4MigrationExample"
```

The compile-time check from "Let the Compiler Check a Migration" has no runnable example, because code that fails to compile can't run. To see it, remove the `.renameField(...)` line from this file and run `sbt compile`.

### Run a Migration Backwards and Look Inside It

This example reverses the migration, applies it to a `BookV3` and prints the migration's actions.

<details>
  <summary>schema-examples/src/main/scala/evolveyourdata/Concept5ReverseMigrationExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/evolveyourdata/Concept5ReverseMigrationExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain evolveyourdata.Concept5ReverseMigrationExample"
```

### The Complete Example

The complete example from "Putting It Together" runs everything from the tutorial in one program:

```bash
sbt "runMain evolveyourdata.CompleteExample"
```

To compile every example at once without running them:

```bash
sbt compile
```

## 11. What You've Learned

In this tutorial, you learned:

- How `Into.derived` converts one type into another, pairing fields by name and validating values such as a `Double` that must become an `Int`
- How `Into` collects every failing field into one `SchemaError` instead of stopping at the first
- How `As.derived` gives you both directions of a conversion, and why fields that exist only in the newer type must be `Option`s
- How `Migration.newBuilder` records changes such as `renameField`, and how `build` refuses to compile a migration that misses a field
- How a migration can be reversed and inspected, because it is data

When your data changes shape, pick the tool by the guarantee you need. Reach for `Into` when data flows one way and must be validated, such as input from another service. Reach for `As` when two versions must be converted back and forth and every change is name-preserving. Reach for `Migration` when you evolve stored data between versions and want the compiler to check that every field is accounted for.

## 12. Where to Go Next

- **Want every conversion rule?** Read the [Into](../reference/schema/schema-evolution/into.md) and [As](../reference/schema/schema-evolution/as.md) reference pages, which cover numeric widening and narrowing, collections, `Option`, custom conversions and more. The [Schema Evolution](../reference/schema/schema-evolution/index.md) overview shows how the two fit together.
- **Need more migration steps?** The [Migration](../reference/schema/migration.md) reference page documents every builder method, including `addField`, `dropField`, `transformField` and `renameCase`, and how migrations are composed.
- **Want to write default values and transformations?** Read [SchemaExpr](../reference/schema/schema-expr.md), the expression type that builder methods such as `addField` take.
- **Want to understand errors in depth?** Read [SchemaError](../reference/schema/schema-error.md) for every error case and how errors are combined.
- **More runnable examples:** the `schema-examples` project has more `Into` examples in [`into/`](https://github.com/zio/zio-blocks/tree/main/schema-examples/src/main/scala/into) and `As` examples in [`as/`](https://github.com/zio/zio-blocks/tree/main/schema-examples/src/main/scala/as).
- **Moving from ZIO Schema?** That's a different kind of migration: see [Migrating from ZIO Schema to ZIO Blocks Schema](./zio-schema-migration.md).
