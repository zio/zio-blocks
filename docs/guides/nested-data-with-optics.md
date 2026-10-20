---
id: nested-data-with-optics
title: "Navigate and Update Nested Data with Optics"
description: "Read and update fields deep inside immutable Scala data with lenses, optionals and traversals, without nested copy calls."
keywords:
  - "Getting Started"
  - "Immutable Updates"
  - "Optics"
  - "Lens"
  - "Traversal"
  - "OpticCheck"
---

Welcome! This tutorial is for Scala developers who have finished [Your First Schema](./your-first-schema.md), so you know how to derive a `Schema` for a case class. You don't need any prior experience with optics, or with optics libraries such as Monocle or Quicklens, to follow along.

## 1. Introduction

By the end of this tutorial, you will understand:

- What an optic is, and how to declare one from a path with the `$(...)` macro
- How a `Lens` reads, replaces and modifies one field, however deeply it is nested
- How `.when[B]`, `.each` and `.atKey(k)` reach one case of a sealed trait, every element of a list and one entry of a map
- How `modifyOrFail` explains why an update found nothing to change
- How the same paths look as strings, with the `p"..."` interpolator

We learn these concepts by managing the stock of a small bookstore, one step at a time:

1. Put a lens on one field
2. Follow a path into nested data
3. Focus on one case with `.when[B]`
4. Update every element with `.each`
5. Reach into a map with `.atKey`
6. See why an update failed with `modifyOrFail`
7. Write the same paths as strings with `p"..."`

We recommend reading from top to bottom — each section builds on the previous one.

To follow along, add the schema module to your `build.sbt`. Optics are part of it, so there is nothing else to install:

```scala
// Scala 2.13 and Scala 3.3 LTS
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "@VERSION@"

// Scala 3.7 and newer
libraryDependencies += "dev.zio" %% "zio-blocks-next-schema" % "@VERSION@"
```

The code in this tutorial is the same for Scala 2.13 and Scala 3.

## 2. Background: Paths Into Immutable Data

Case classes are immutable, so "changing" a field means building a new value with that field replaced. For a field at the top level, `copy` does the job. For a field two or three levels down, every level needs its own `copy`, and a field inside every element of a list needs a `map` around the `copy`. The path to the field — "the author's name of this book", "the stock of every book on every shelf" — gets lost in the plumbing.

An optic turns that path into a value. You describe the path once, for example "the `name` of the `author` of a `Book`", and the optic gives you operations to read the value at the end of the path and to build an updated copy of the whole structure. ZIO Blocks builds optics from your `Schema`, so each optic also knows the structure it walks through. That is how it can tell you, in words, why an update found nothing to change.

## 3. Put a Lens on One Field

A `Lens[S, A]` focuses on exactly one field of type `A` inside a value of type `S`. Because a case class always has every one of its fields, a lens always finds its target.

We declare optics with the `$(...)` macro, which comes from the `CompanionOptics[S]` trait. We extend it in the companion object, next to the schema, and pass `$` a lambda that selects a path. Here is the model for one book, with two lenses. The page counts, file sizes and stock levels in this tutorial are sample data:

```scala mdoc:silent
import zio.blocks.schema._

case class Author(name: String)

sealed trait Format
case class Paperback(pages: Int)  extends Format
case class Ebook(sizeMb: Double) extends Format

case class Book(title: String, author: Author, format: Format, stock: Int)

object Book extends CompanionOptics[Book] {
  implicit val schema: Schema[Book] = Schema.derived

  val title: Lens[Book, String]      = $(_.title)
  val authorName: Lens[Book, String] = $(_.author.name)
}
```

The code above:

- `import zio.blocks.schema._` brings `Schema`, `CompanionOptics` and the optic types into scope.
- `Author`, `Format` and `Book` are ordinary case classes and a sealed trait; `author` holds the book's first author.
- `object Book extends CompanionOptics[Book]` gives the companion object the `$` macro for paths that start at a `Book`.
- `implicit val schema` comes first, because the macro uses the schema to build each optic.
- `$(_.title)` reads as "from a book, go to `title`" and returns a `Lens[Book, String]`.
- `$(_.author.name)` is a longer path; we use it in the next section.

Let's read the title of a book with `Lens#get`:

```scala mdoc
val programmingInScala = Book("Programming in Scala", Author("Martin Odersky"), Paperback(860), 7)

println(Book.title.get(programmingInScala))
```

The code above builds a `Book` and passes it to `Book.title.get`, which returns the value at the end of the lens's path.

To update the field, we have two methods. `Lens#replace` puts a new value in place, and `Lens#modify` applies a function to the current value. Both return a new `Book`:

```scala mdoc
println(Book.title.replace(programmingInScala, "Programming in Scala, Fifth Edition"))

println(Book.title.modify(programmingInScala, _.toUpperCase))

println(programmingInScala)
```

The code above:

- `Book.title.replace(programmingInScala, "...")` returns a copy of the book with the new title.
- `Book.title.modify(programmingInScala, _.toUpperCase)` returns a copy whose title is the old title in upper case.
- `println(programmingInScala)` shows that the original book did not change: optics never mutate, they build new values.

An optic is an ordinary value, so we can print it too:

```scala mdoc
println(Book.title)
```

Notice that the printed form matches the path we passed to `$`. When a program holds many optics, this makes it easy to see which one is which.

## 4. Follow a Path Into Nested Data

A path can go through as many fields as we like. Each `.field` step goes one level deeper, and the result is still a `Lens`, because every step always finds its field. The `Book.authorName` lens from the previous section goes from a `Book` to its `author`, then to the author's `name`:

```scala mdoc
println(Book.authorName.get(programmingInScala))
```

The code above follows both steps of the path and returns the name of the book's first author.

Now let's update the name and compare the result with the same update written by hand:

```scala mdoc:silent
val shouting = Book.authorName.modify(programmingInScala, _.toUpperCase)

val byHand = programmingInScala.copy(author = programmingInScala.author.copy(name = programmingInScala.author.name.toUpperCase))
```

```scala mdoc
println(shouting)
println(shouting == byHand)
```

The code above:

- `Book.authorName.modify(...)` builds a new `Author` with the upper-case name, then a new `Book` that holds it.
- `byHand` does the same with two nested `copy` calls; notice how the path `author.name` is spread across the whole expression.
- `shouting == byHand` confirms that both give the same book.

The optic only rebuilds the values on its path. Everything off the path is shared with the original:

```scala mdoc
println(shouting.author eq programmingInScala.author)
println(shouting.format eq programmingInScala.format)
```

The first line prints `false`, because the `Author` is on the path and was rebuilt. The second prints `true`, because `format` is not on the path, so the new book points at the very same `Paperback` object.

## 5. Focus on One Case with `.when[B]`

Some books are paperbacks and some are ebooks. A path step `.when[B]` selects one case of a sealed trait. The value might be a different case, so the path no longer always finds its target: `$(_.format.when[Paperback].pages)` gives an `Optional[Book, Int]`, an optic that focuses on zero or one value.

Let's add this optic to the companion object. We start a fresh scope so we can redefine `Book`:

```scala mdoc:silent:reset
import zio.blocks.schema._

case class Author(name: String)

sealed trait Format
case class Paperback(pages: Int)  extends Format
case class Ebook(sizeMb: Double) extends Format

case class Book(title: String, author: Author, format: Format, stock: Int)

object Book extends CompanionOptics[Book] {
  implicit val schema: Schema[Book] = Schema.derived

  val title: Lens[Book, String]          = $(_.title)
  val authorName: Lens[Book, String]     = $(_.author.name)
  val paperbackPages: Optional[Book, Int] = $(_.format.when[Paperback].pages)
}

val programmingInScala = Book("Programming in Scala", Author("Martin Odersky"), Paperback(860), 7)

val zionomicon = Book("Zionomicon", Author("John A. De Goes"), Ebook(5.2), 3)
```

The code above reads `$(_.format.when[Paperback].pages)` as "the format, if it is a `Paperback`, then its page count", and defines `programmingInScala` as a paperback and `zionomicon` as an ebook in our sample data.

:::caution
Path steps such as `.when[B]`, `.each` and `.atKey(k)` exist only inside an object that extends `CompanionOptics`. Declare optics that use them in the companion object, as we do here. Outside it, the compiler reports an error such as ``value each is not a member of List[Shelf]``.
:::

An `Optional` reads with `Optional#getOption`, which returns `Some` when the target is there and `None` when it isn't:

```scala mdoc
println(Book.paperbackPages.getOption(programmingInScala))
println(Book.paperbackPages.getOption(zionomicon))
```

The code above returns the page count of the paperback in a `Some`, and `None` for the ebook.

Updates follow the same rule. `modify` changes the value when the target is there, and returns the input unchanged when it isn't:

```scala mdoc
println(Book.paperbackPages.modify(programmingInScala, _ + 16))

println(Book.paperbackPages.modify(zionomicon, _ + 16) == zionomicon)
```

The code above adds 16 pages to the paperback, and leaves the ebook exactly as it was: the comparison prints `true`.

## 6. Update Every Element with `.each`

Let's put books on shelves. A path step `.each` goes into every element of a collection. Because a collection can hold any number of elements — including none — a path with `.each` gives a `Traversal[S, A]`, an optic that focuses on zero or more values at once.

We add a shelf, and an inventory that holds the shelves and a price list. The companion object of `Inventory` declares a traversal over the stock of every book on every shelf:

```scala mdoc:silent
case class Shelf(name: String, books: List[Book])

case class Inventory(store: String, shelves: List[Shelf], prices: Map[String, BigDecimal])

object Inventory extends CompanionOptics[Inventory] {
  implicit val schema: Schema[Inventory] = Schema.derived

  val allStock: Traversal[Inventory, Int] = $(_.shelves.each.books.each.stock)
}

val fpInScala = Book("Functional Programming in Scala", Author("Paul Chiusano"), Paperback(320), 0)

val inventory = Inventory(
  store = "Lambda Books",
  shelves = List(
    Shelf("Scala", List(programmingInScala, fpInScala)),
    Shelf("ZIO", List(zionomicon))
  ),
  prices = Map("Programming in Scala" -> BigDecimal("59.95"), "Zionomicon" -> BigDecimal("49.99"))
)
```

The code above:

- `Shelf` holds a list of books, and `Inventory` holds a list of shelves and a `Map` from title to price.
- `Inventory.schema` is derived like `Book.schema`, and reuses it for the books on each shelf.
- `$(_.shelves.each.books.each.stock)` reads as "every shelf, then every book on it, then its stock".
- `inventory` has two shelves with three books, and prices for two of them.

A `Traversal` has no `get`, because there can be many values. Instead, `Traversal#fold` combines them, here into a list:

```scala mdoc
println(Inventory.allStock.fold(inventory)(List.empty[Int], (stocks, stock) => stocks :+ stock))
```

The code above starts from an empty list and appends each stock value, shelf by shelf and book by book.

A delivery has arrived with one more copy of every book. `Traversal#modify` applies the function to every focused value:

```scala mdoc:silent
val restocked = Inventory.allStock.modify(inventory, _ + 1)
```

```scala mdoc
println(Inventory.allStock.fold(restocked)(List.empty[Int], (stocks, stock) => stocks :+ stock))
```

The code above restocks all three books in a single call. Written by hand, the same update needs a `map` over the shelves, a `map` over each shelf's books and two `copy` calls.

## 7. Reach Into a Map with `.atKey`

A path step `.atKey(k)` focuses on the value stored under key `k` in a `Map`. The key might be missing, so this gives an `Optional`, like `.when[B]`.

Let's add two price optics to the companion object of `Inventory`: one for Zionomicon, and one for Functional Programming in Scala, which has no entry in our price list. We start a fresh scope again, so this block repeats the model and the data:

```scala mdoc:silent:reset
import zio.blocks.schema._

case class Author(name: String)

sealed trait Format
case class Paperback(pages: Int)  extends Format
case class Ebook(sizeMb: Double) extends Format

case class Book(title: String, author: Author, format: Format, stock: Int)

object Book extends CompanionOptics[Book] {
  implicit val schema: Schema[Book] = Schema.derived

  val title: Lens[Book, String]          = $(_.title)
  val authorName: Lens[Book, String]     = $(_.author.name)
  val paperbackPages: Optional[Book, Int] = $(_.format.when[Paperback].pages)
}

case class Shelf(name: String, books: List[Book])

case class Inventory(store: String, shelves: List[Shelf], prices: Map[String, BigDecimal])

object Inventory extends CompanionOptics[Inventory] {
  implicit val schema: Schema[Inventory] = Schema.derived

  val allStock: Traversal[Inventory, Int]              = $(_.shelves.each.books.each.stock)
  val zionomiconPrice: Optional[Inventory, BigDecimal] = $(_.prices.atKey("Zionomicon"))
  val fpInScalaPrice: Optional[Inventory, BigDecimal]  = $(_.prices.atKey("Functional Programming in Scala"))
}

val programmingInScala = Book("Programming in Scala", Author("Martin Odersky"), Paperback(860), 7)

val zionomicon = Book("Zionomicon", Author("John A. De Goes"), Ebook(5.2), 3)

val fpInScala = Book("Functional Programming in Scala", Author("Paul Chiusano"), Paperback(320), 0)

val inventory = Inventory(
  store = "Lambda Books",
  shelves = List(
    Shelf("Scala", List(programmingInScala, fpInScala)),
    Shelf("ZIO", List(zionomicon))
  ),
  prices = Map("Programming in Scala" -> BigDecimal("59.95"), "Zionomicon" -> BigDecimal("49.99"))
)
```

The code above adds `zionomiconPrice` and `fpInScalaPrice`. The path `_.prices.atKey("Zionomicon")` reads as "the price list, then the value under the key `"Zionomicon"`".

Let's look up and change the price of Zionomicon:

```scala mdoc
println(Inventory.zionomiconPrice.getOption(inventory))

println(Inventory.zionomiconPrice.replace(inventory, BigDecimal("44.99")).prices)
```

The code above looks up the current price with `getOption(inventory)`, which returns it wrapped in `Some`, and builds a new inventory with the new price using `replace(inventory, BigDecimal("44.99"))`; we print only its `prices` to keep the output short.

Now let's see what an `Optional` does with the missing key:

```scala mdoc
println(Inventory.fpInScalaPrice.getOption(inventory))

println(Inventory.fpInScalaPrice.modify(inventory, _ + 1) == inventory)
```

The code above returns `None` for the missing key, and `modify` returns the inventory unchanged, just like the ebook in section 5.

## 8. See Why an Update Failed with `modifyOrFail`

Returning the input unchanged is often what we want. Sometimes, though, a missing target is a bug: we expected a paperback, or we expected the price to be listed. For those cases, every optic has `modifyOrFail`, which returns an `Either[OpticCheck, S]`. On success it holds the updated value in a `Right`. On failure it holds an `OpticCheck` in a `Left`, and `OpticCheck#message` says what the optic expected and where it stopped.

Let's try to add pages to the ebook:

```scala mdoc
Book.paperbackPages.modifyOrFail(zionomicon, _ + 16) match {
  case Left(check) => println(check.message)
  case Right(book) => println(s"Updated: $book")
}
```

The code above calls `Book.paperbackPages.modifyOrFail(zionomicon, _ + 16)`, which returns a `Left` because Zionomicon's format is an `Ebook`; `check.message` names the full path of the optic, the step where it stopped, the case it expected and the case it found.

Now let's try to raise the missing price:

```scala mdoc
Inventory.fpInScalaPrice.modifyOrFail(inventory, _ + 1) match {
  case Left(check)    => println(check.message)
  case Right(updated) => println(s"Updated: ${updated.prices}")
}
```

This time the message says that the key is missing, and shows the path with the key we asked for. When the target is there, `modifyOrFail` returns the updated value:

```scala mdoc
println(Book.paperbackPages.modifyOrFail(programmingInScala, _ + 16).map(_.format))
```

The code above returns a `Right` holding the updated book; we map over it to print only its new format. Every optic has `modifyOrFail` — including lenses, which always return a `Right`, and traversals, which fail when they find no elements.

:::tip
Use `modify` when "nothing to change" is a normal outcome, and `modifyOrFail` when it means the data is not what you expected.
:::

## 9. Write the Same Paths as Strings with `p"..."`

Everything so far was checked by the compiler against our case classes. ZIO Blocks also has a string syntax for paths, the `p"..."` interpolator, for code that works with data generically instead of with one Scala type. The string is checked at compile time too, and the result is a `DynamicOptic`: the same path, without the Scala types at each end.

Let's write the stock path as a string and compare it with the `allStock` traversal from section 6:

```scala mdoc
val stockPath: DynamicOptic = p".shelves[*].books[*].stock"

println(stockPath)
println(Inventory.allStock.toDynamic == stockPath)
```

The code above writes the path from section 6 as the string `p".shelves[*].books[*].stock"`, where `[*]` means "every element", like `.each`, and turns the typed traversal into its `DynamicOptic` with `Inventory.allStock.toDynamic`; the comparison prints `true`.

A `DynamicOptic` works on `DynamicValue`, the generic data view from [Your First Schema](./your-first-schema.md#8-look-at-the-same-data-two-more-ways). Let's convert the inventory and select the stock values with the string path:

```scala mdoc:silent
val dynamicInventory = Inventory.schema.toDynamicValue(inventory)
```

```scala mdoc
println(dynamicInventory.get(stockPath).toChunk.mkString(", "))
```

The code above prints the same three stock values that `allStock` folded in section 6, this time selected as `DynamicValue`s by a path written as a string. The [Path Interpolator](../reference/schema/path-interpolator.md) reference shows the full string syntax, including cases (`<Paperback>`) and map keys.

## 10. Putting It Together

This example combines all the concepts we've learned — lenses in the companion object, a nested path, an optional on one case and on one map key, a traversal over every book, `modifyOrFail` for a missing target and the same path as a `p"..."` string:

```scala mdoc:embed:schema-examples/src/main/scala/nesteddatawithoptics/CompleteExample.scala
```

## 11. Running the Examples

All examples in this tutorial have corresponding runnable Scala files in the `schema-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/schema-examples
```

Run them in order to follow the tutorial step by step.

### Put a Lens on One Field

This example declares a lens on a book's title in the companion object, then reads, replaces and modifies the title.

<details>
  <summary>schema-examples/src/main/scala/nesteddatawithoptics/Concept1LensExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/nesteddatawithoptics/Concept1LensExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain nesteddatawithoptics.Concept1LensExample"
```

### Follow a Path Into Nested Data

This example uses a lens on the first author's name, compares its update with nested `copy` calls, and shows which values are shared with the original.

<details>
  <summary>schema-examples/src/main/scala/nesteddatawithoptics/Concept2NestedPathExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/nesteddatawithoptics/Concept2NestedPathExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain nesteddatawithoptics.Concept2NestedPathExample"
```

### Focus on One Case with `.when[B]`

This example declares an optional on the page count of paperbacks and shows that it finds nothing in an ebook.

<details>
  <summary>schema-examples/src/main/scala/nesteddatawithoptics/Concept3WhenExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/nesteddatawithoptics/Concept3WhenExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain nesteddatawithoptics.Concept3WhenExample"
```

### Update Every Element with `.each`

This example declares a traversal over the stock of every book on every shelf, folds it into a list and restocks every book in one call.

<details>
  <summary>schema-examples/src/main/scala/nesteddatawithoptics/Concept4EachExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/nesteddatawithoptics/Concept4EachExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain nesteddatawithoptics.Concept4EachExample"
```

### Reach Into a Map with `.atKey`

This example reads and replaces one price in the price list, and shows what happens when the key is missing.

<details>
  <summary>schema-examples/src/main/scala/nesteddatawithoptics/Concept5AtKeyExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/nesteddatawithoptics/Concept5AtKeyExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain nesteddatawithoptics.Concept5AtKeyExample"
```

### See Why an Update Failed with `modifyOrFail`

This example prints the `OpticCheck` message for a wrong case and for a missing key, and the `Right` result of a successful update.

<details>
  <summary>schema-examples/src/main/scala/nesteddatawithoptics/Concept6ModifyOrFailExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/nesteddatawithoptics/Concept6ModifyOrFailExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain nesteddatawithoptics.Concept6ModifyOrFailExample"
```

### Write the Same Paths as Strings with `p"..."`

This example writes the stock path as a `p"..."` string, compares it with the typed traversal and uses it on the generic view of the inventory.

<details>
  <summary>schema-examples/src/main/scala/nesteddatawithoptics/Concept7PathInterpolatorExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/nesteddatawithoptics/Concept7PathInterpolatorExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain nesteddatawithoptics.Concept7PathInterpolatorExample"
```

### The Complete Example

The complete example from "Putting It Together" runs everything from the tutorial in one program:

```bash
sbt "runMain nesteddatawithoptics.CompleteExample"
```

To compile every example at once without running them:

```bash
sbt compile
```

## 12. What You've Learned

In this tutorial, you learned:

- What an optic is — a path into a data structure, stored as a value — and how to declare one with `$(...)` in a companion object that extends `CompanionOptics`
- How a `Lens` reads with `get` and updates with `replace` and `modify`, at any depth, sharing everything off its path
- How `.when[B]` and `.atKey(k)` give an `Optional` that may find nothing, and how `.each` gives a `Traversal` over every element
- How `modifyOrFail` returns an `OpticCheck` that explains why an update found nothing to change
- How `p"..."` writes the same path as a string and gives a `DynamicOptic` for generic data

You now have a solid foundation in ZIO Blocks optics. The next step is to explore the other optic operations, and to see how optics become the building blocks of a query language.

## 13. Where to Go Next

- **Want the full optics API?** Read [Optics](../reference/schema/optics.md), which covers every operation on `Lens`, `Prism`, `Optional` and `Traversal`, building optics by hand, composing optics and their debug-friendly `toString`.
- **Need paths at run time?** Read [DynamicOptic](../reference/schema/dynamic-optic.md) and the [Path Interpolator](../reference/schema/path-interpolator.md) syntax.
- **Want to understand failures in depth?** The [Failure Path in OpticCheck](../reference/schema/dynamic-optic.md#failure-path-in-opticcheck) section of DynamicOptic shows the kinds of failure an optic can report, such as a wrong case, an empty sequence and a missing map key.
- **Ready to build on optics?** The four-part Query DSL series starts with [Writing a Query DSL with Reified Optics](./query-dsl-reified-optics.md), then [SQL](./query-dsl-sql.md), [Extending the DSL](./query-dsl-extending.md) and the [Fluent Builder](./query-dsl-fluent-builder.md).
