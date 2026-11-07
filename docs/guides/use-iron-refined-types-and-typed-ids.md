---
id: use-iron-refined-types-and-typed-ids
title: "How to Use Iron Refined Types and Typed IDs with Schema"
description: "Derive Schema and JSON codecs for case classes with Iron refined fields, read constraint failures, and keep entity IDs apart with one generic Id type."
keywords:
  - "Refined Types"
  - "Iron"
  - "Typed IDs"
  - "Opaque Type"
  - "Schema"
  - "SchemaError"
---

In this guide, we build the input model of a small online shop: customers, products and orders that arrive as JSON. Each field with a rule, such as an email address, a product code or a price, gets an [Iron](https://github.com/Iltotore/iron) refined type like `String :| MinLength[1]`, and Iron's `iron-zio-blocks-schema` module turns each one into a `Schema`. We derive the case classes' schemas as usual, so every JSON codec checks the rules while it decodes. Then we give customer and order IDs their own types with one opaque `Id[+A]`, so the compiler stops us from passing an order ID where a customer ID belongs.

We take an incremental approach:

- Refine the fields of a case class and derive its `Schema` and JSON codec
- Decode JSON and read Iron's constraint failures as `SchemaError`s with field paths
- Name a refined type with Iron's `RefinedType`
- Refine numeric fields with a validated newtype, because of a known bug in ZIO Blocks 0.0.52 to 0.0.56
- See which constraints reach JSON Schema and `DynamicSchema`
- Keep IDs apart with one generic `Id[+A]` and a single `Schema` for every entity
- Choose between Iron and plain validated newtypes

## The Problem

A shop model that stores an email as `String`, a price as `BigDecimal` and every ID as `Long` accepts any string, any number and any ID. A blank customer name, a negative price and a customer ID passed as an order ID all type-check, and nothing stops them until a database constraint or a confused support ticket does.

The usual fix is a validation function and some careful parameter names, and both depend on discipline. Nothing forces a caller to run `validate`, and nothing stops a caller from swapping two `Long` arguments.

For example, a typical model and its checks look like this:

```scala mdoc:compile-only
final case class CustomerRow(id: Long, name: String, email: String)
final case class OrderRow(id: Long, customer: Long, total: BigDecimal)

def validate(customer: CustomerRow): Either[String, CustomerRow] =
  if (customer.name.isEmpty || customer.name.length > 30) Left(s"bad name: ${customer.name}")
  else if (!customer.email.matches("^[^@\\s]+@[^@\\s]+\\.[a-z]+$")) Left(s"bad email: ${customer.email}")
  else Right(customer)

def cancelOrder(orderId: Long, customerId: Long): String = s"order $orderId cancelled for customer $customerId"

// Compiles and runs: the checks are skipped and the IDs are swapped.
val unchecked = CustomerRow(1L, "", "not-an-email")
cancelOrder(unchecked.id, 10L)
```

The rules live apart from the types they protect, the JSON decoder knows nothing about them, and the two IDs are interchangeable.

In this guide, we move each rule into a type. Iron writes the rule in the type itself (`String :| MinLength[1]`), ZIO Blocks enforces it in every codec derived from the schema, and a typed `Id[Order]` can't stand in for an `Id[Customer]`.

## Prerequisites

This guide builds on [Your First Schema](./your-first-schema.md): you should know how to derive a `Schema` for a case class and decode JSON with it. [How to Define Validated Newtypes](./validated-newtypes.md) shows the plain ZIO Blocks way to attach a check to a type, which we compare with Iron at the end.

Iron is a Scala 3 library, and its 3.4 releases are built with Scala 3.9, so this guide needs Scala 3.9 or newer. Add the schema module and Iron's ZIO Blocks module, which brings Iron itself, to your `build.sbt`:

```scala
scalaVersion := "3.9.0"

libraryDependencies ++= Seq(
  "dev.zio"            %% "zio-blocks-next-schema" % "@VERSION@",
  ("io.github.iltotore" %% "iron-zio-blocks-schema" % "3.4.1")
    .exclude("dev.zio", "zio-blocks-schema_3")
)
scalacOptions += "-experimental"
```

On Scala 3.9 and newer, ZIO Blocks is published as `zio-blocks-next-*`, and those artifacts need `-experimental`. Iron's module depends on the other artifact line, `zio-blocks-schema` 0.0.51, which holds the same packages. The `exclude` keeps that second copy off the classpath, so Iron's instances run against the `zio-blocks-next-schema` we chose.

We use these imports throughout. The `zioBlocksSchema.given` import brings Iron's `Schema` instances into scope:

```scala mdoc:silent
import io.github.iltotore.iron._
import io.github.iltotore.iron.constraint.all._
import io.github.iltotore.iron.zioBlocksSchema.given
import zio.blocks.schema._
import zio.blocks.schema.json._
```

## Refine the Fields of a Case Class

An Iron refined type is a base type and a constraint joined by `:|`: `String :| MinLength[1]` is a `String` that Iron has checked to have at least one character. At runtime it is the plain `String`, so it costs nothing to carry around. We name each refined type with a type alias and use the aliases as field types.

For a customer, we refine the display name to 1 to 30 characters and the email to a simple pattern. We keep the pattern in its own alias, `EmailFormat`, so we can reuse it later. The case class derives its schema with `Schema.derived`, exactly as it would with `String` fields:

```scala mdoc:silent
type EmailFormat = Match["^[^@\\s]+@[^@\\s]+\\.[a-z]+$"]
type Email       = String :| EmailFormat
type DisplayName = String :| (MinLength[1] & MaxLength[30])

final case class Customer(name: DisplayName, email: Email)

object Customer {
  implicit val schema: Schema[Customer] = Schema.derived
}
```

`Schema.derived` needs a `Schema` for each field type, and Iron's `zioBlocksSchema` provides one for every `A :| C` whose base type `A` has a schema and whose constraint `C` Iron can check at runtime. That instance wraps the base type's schema with a check, so decoding a field tests the constraint. Encoding writes the plain value:

```scala mdoc
val ada = Customer("Ada", "ada@example.com")

println(Customer.schema.jsonCodec.encodeToString(ada))
```

Iron checks a literal at compile time, so `Customer("Ada", "ada@example.com")` needs no explicit refinement. A literal that breaks the constraint doesn't compile:

```scala mdoc:fail
val blank: DisplayName = ""
```

## Read Constraint Failures from the Decoder

When the JSON holds a value that breaks a constraint, the decoder returns a `Left` with a `SchemaError`. Its message is Iron's description of the constraint, followed by the path of the field that failed.

A valid payload decodes to a `Customer`, and a blank name or a malformed email does not:

```scala mdoc
println(Customer.schema.jsonCodec.decode("""{"name":"Ada","email":"ada@example.com"}"""))
println(Customer.schema.jsonCodec.decode("""{"name":"","email":"ada@example.com"}""").left.map(_.message))
println(Customer.schema.jsonCodec.decode("""{"name":"Ada","email":"ada-at-example"}""").left.map(_.message))
```

The path ends in `.wrapped` because ZIO Blocks sees a refined type as a wrapper around its base type: `.name.wrapped` is the `String` inside the `DisplayName` field. The message `Should have a minimum length of 1 & Should have a maximum length of 30` comes from Iron and describes both halves of the `&`.

Values that arrive outside JSON, from a form field or a command-line argument, go through Iron's `refineEither` extension method, which runs the same constraint and returns Iron's message on failure:

```scala mdoc
val fromForm: String = "grace@example.com"

println(fromForm.refineEither[EmailFormat])
println("grace-at-example".refineEither[EmailFormat])
```

## Name a Refined Type with RefinedType

A type alias like `DisplayName` is only a name: any `String :| (MinLength[1] & MaxLength[30])` is a `DisplayName`, whatever it was meant for. When a refined value should be its own type, Iron's `RefinedType` creates one, with a companion object that refines values for us. Iron's module gives these new types a `Schema` too.

For products, we make the stock-keeping unit (SKU) a `RefinedType` and refine the price with `Positive`:

```scala mdoc:silent
type Sku = Sku.T
object Sku extends RefinedType[String, Match["^[A-Z]{3}-[0-9]{4}$"]]

type Price = BigDecimal :| Positive

final case class Product(sku: Sku, title: DisplayName, price: Price, tags: List[DisplayName])

object Product {
  implicit val schema: Schema[Product] = Schema.derived
}
```

`Sku.apply` refines a literal at compile time and `Sku.either` refines a runtime value, so the companion replaces both the literal check and `refineEither`:

```scala mdoc
println(Sku("KEY-0042"))
println(Sku.either("key-42"))
```

The decoder checks every refined field, including each element of a collection. A wrong SKU, a negative price and a blank tag each fail with their own path:

```scala mdoc
println(Product.schema.jsonCodec.decode("""{"sku":"KEY-0042","title":"Mechanical keyboard","price":89.90,"tags":["usb","wired"]}"""))
println(Product.schema.jsonCodec.decode("""{"sku":"key-42","title":"Mechanical keyboard","price":89.90,"tags":[]}""").left.map(_.message))
println(Product.schema.jsonCodec.decode("""{"sku":"KEY-0042","title":"Mechanical keyboard","price":-1,"tags":[]}""").left.map(_.message))
println(Product.schema.jsonCodec.decode("""{"sku":"KEY-0042","title":"Mechanical keyboard","price":89.90,"tags":[""]}""").left.map(_.message))
```

The SKU's path has two `.wrapped` steps: one for the `RefinedType` around the refined `String`, and one for the refinement around the `String` itself. `.tags.at(0)` points at the first element of the list.

## Refine Numeric Fields with a Validated Newtype

An order line needs a quantity, and `Int :| Positive` is the obvious type for it. On ZIO Blocks 0.0.56, a refined field over a JVM primitive doesn't work inside a derived case class.

:::warning[Numeric Iron fields in case classes]
A known bug in ZIO Blocks 0.0.52 to 0.0.56: an Iron field whose base type is `Int`, `Long`, `Double`, `Float`, `Short`, `Byte`, `Char` or `Boolean` decodes as `0` (or `false`) inside a derived case class, even when the input is valid, and the constraint never sees the real value. With fields of several types, decoding can fail with a `ClassCastException` instead. A standalone `Schema[Int :| Positive]` works, and so do refinements of `String`, `BigDecimal` and `BigInt`. Until a fixed release, use a validated newtype for numeric fields, as below.
:::

A validated newtype is a one-field case class with a private constructor and a smart constructor (a function that checks its input before building the value). Its `Schema` comes from `Schema#transform`, which takes a function from the base type and a function back. The `to` function returns a plain `Quantity`, so a failed check rethrows the `SchemaError`, and the decoder turns it into a `Left`:

```scala mdoc:silent
final case class Quantity private (value: Int)

object Quantity {
  def parse(value: Int): Either[SchemaError, Quantity] =
    if (value > 0) Right(new Quantity(value))
    else Left(SchemaError.validationFailed(s"quantity must be positive, got $value"))

  implicit val schema: Schema[Quantity] =
    Schema[Int].transform(n => parse(n).fold(e => throw e, identity), _.value)
}

final case class Line(sku: Sku, quantity: Quantity)

object Line {
  implicit val schema: Schema[Line] = Schema.derived
}
```

The line mixes an Iron field and a newtype field, and both rules apply on decode:

```scala mdoc
println(Line.schema.jsonCodec.decode("""{"sku":"KEY-0042","quantity":2}"""))
println(Line.schema.jsonCodec.decode("""{"sku":"KEY-0042","quantity":0}""").left.map(_.message))
```

## See Where the Constraints Go

Iron's constraints protect the decoder, and some of them also travel with the schema. For each constraint that has an exact equivalent among ZIO Blocks' [`Validation`](../reference/schema/validation.md) rules (positive and negative numbers, inclusive bounds, exact values, string patterns and string lengths), Iron's module attaches that `Validation` to the base type's schema. It also stores Iron's message on the schema as a `Modifier.config` entry named `iron.validation.message`.

`Schema#toJsonSchema` doesn't emit `Validation` rules, so the JSON Schema of `Customer` describes both fields as plain strings, without `minLength`, `maxLength` or `pattern`:

```scala mdoc
println(Customer.schema.toJsonSchema.toJson)
```

`DynamicSchema` does carry them. `Schema#toDynamicSchema` turns a schema into a value that can be stored or sent to another service, and `DynamicSchema#check` tests a `DynamicValue` against it without a decoder. The blank name fails with the `Validation` rule that Iron attached, not with Iron's own message:

```scala mdoc:silent
val blankName = DynamicValue.Record(
  "name"  -> DynamicValue.string(""),
  "email" -> DynamicValue.string("ada@example.com")
)
```

Checking it reports the failed rule and its path:

```scala mdoc
println(Customer.schema.toDynamicSchema.check(blankName))
```

When your API contract has to state the limits, write them into a JSON Schema document by hand and load it with `JsonSchema.parse`, as the "Load a Schema from Another Source" section of [How to Generate JSON Schema from a Type and Validate Payloads Against It](./generate-json-schema-and-validate-payloads.md) does.

## Keep IDs Apart with a Typed Id

Refined types check values; IDs need a different kind of safety. A customer ID and an order ID are both `Long`s that pass every check, and the bug is using one where the other belongs. One opaque type with a type parameter fixes that for every entity at once: `Id[Customer]` and `Id[Order]` are different types at compile time and the same `Long` at runtime. This part needs no Iron.

An opaque type is transparent inside the scope that defines it, so we put `Id` and its companion in a small object of their own. The companion has a constructor, an extension method that reads the `Long` back, and one generic `Schema` that serves `Id[A]` for every `A`:

```scala mdoc:silent
object ids {
  opaque type Id[+A] = Long

  object Id {
    def apply[A](value: Long): Id[A] = value

    extension [A](id: Id[A]) def value: Long = id

    implicit def schema[A]: Schema[Id[A]] = Schema.long.transform(Id[A](_), _.value)
  }
}
import ids._
```

:::warning
Write `Schema.long`, not `Schema[Long]`, inside the opaque type's scope. There `Id[A]` is a `Long`, so `Schema[Long]` finds the `implicit def schema` itself: the code compiles with only an "Infinite loop in function body" warning and throws a `StackOverflowError` at runtime.
:::

An order refers to its own ID, its customer's ID and a set of customers who watch its delivery. A field can hold any collection of IDs, such as `Set[Id[Customer]]`, because each `Id[Customer]` has a schema:

```scala mdoc:silent
final case class Order(id: Id[Order], customer: Id[Customer], lines: List[Line], watchers: Set[Id[Customer]])

object Order {
  implicit val schema: Schema[Order] = Schema.derived
}

val order = Order(
  id = Id(10L),
  customer = Id(1L),
  lines = List(Line(Sku("KEY-0042"), Quantity.parse(2).toOption.get)),
  watchers = Set(Id(1L), Id(2L))
)

val orderJson = Order.schema.jsonCodec.encodeToString(order)
```

In JSON, each ID is the plain number, and decoding gives the typed IDs back:

```scala mdoc
println(orderJson)
println(Order.schema.jsonCodec.decode(orderJson))
```

The compiler now rejects an ID of the wrong entity. Passing the order's customer ID to a function that cancels an order doesn't compile:

```scala mdoc:fail
def cancel(id: Id[Order]): String = s"order ${id.value} cancelled"

cancel(order.customer)
```

The `+` in `Id[+A]` makes `Id` covariant: an `Id[Customer]` is also an `Id[Any]`. That suits code that handles every ID the same way, such as an audit log:

```scala mdoc
def audit(id: Id[Any]): String = s"audit entry for id ${id.value}"

println(audit(order.id))
println(audit(order.customer))
```

## Choose Between Iron and Validated Newtypes

Iron and a hand-written validated newtype both end with a type that only holds valid values and a `Schema` that checks on decode. They differ in where the rule lives:

| Concern                     | Iron refined type                                         | Validated newtype                                     |
| --------------------------- | --------------------------------------------------------- | ----------------------------------------------------- |
| Scala versions              | Scala 3.9 and newer                                       | Scala 2.13 and Scala 3                                |
| Extra dependency            | `iron-zio-blocks-schema`                                  | None                                                  |
| Writing a rule              | A constraint type, such as `MinLength[1] & MaxLength[30]` | A smart constructor, such as `Quantity.parse`         |
| Literals                    | Checked at compile time                                   | Checked at runtime, by the smart constructor          |
| Normalizing input           | Not supported: the value is kept as written               | Supported, for example trimming or removing hyphens   |
| Error message               | Iron's description of the constraint                      | Your own message                                      |
| `Validation` on the schema  | Added for the constraints that have an exact equivalent   | Not added                                             |
| Numeric fields in 0.0.56    | Broken inside case classes                                | Work                                                  |

Choose Iron when your rules are the common ones (lengths, patterns, ranges, signs) and you want them visible in the type. Choose a validated newtype when a rule needs real code, such as an ISBN check digit, when you normalize the input, when you need Scala 2.13, or for numeric fields on ZIO Blocks 0.0.56. Typed IDs work the same way with either choice.

## Putting It Together

The complete example decodes two incoming orders with the model from this guide: one valid, and one with a lowercase SKU in its first line. The model lives in `Model.scala` and `Ids.scala`, and the program in `CompleteExample.scala`. Unlike the sections, the program wraps decoding in a `receive` function that turns the result into one line of text:

```scala mdoc:embed:zio-blocks-examples/iron-refined-types/src/main/scala/ironrefinedtypes/CompleteExample.scala
```

Running it prints the decoded order and the error of the rejected one:

```text
accepted order 10 for customer 1: List(Line(KEY-0042,Quantity(2)))
rejected order: Should match ^[A-Z]{3}-[0-9]{4}$ at: .lines.at(0).sku.wrapped.wrapped
```

## Running the Examples

All runnable code from this guide is in [`zio-blocks-examples/iron-refined-types/src/main/scala/ironrefinedtypes/`](https://github.com/zio/zio-blocks/tree/main/zio-blocks-examples/iron-refined-types/src/main/scala/ironrefinedtypes). Run any file from that folder with `scala-cli run <File>.scala`, or from `zio-blocks-examples` with `sbt "ironRefinedTypes/runMain ironrefinedtypes.<ObjectName>"`.

## Going Further

- [How to Define Validated Newtypes and Collect Every Validation Error](./validated-newtypes.md) collects every failure of a payload in one pass and turns `SchemaError`s into an error response.
- [Validation](../reference/schema/validation.md) lists the `Validation` rules that Iron's module attaches and where ZIO Blocks enforces them.
- [Allows](../reference/schema/allows.md) restricts which shapes a function accepts at compile time, and treats opaque types as wrappers of their underlying type.
- Iron's [ZIO Blocks module page](https://github.com/Iltotore/iron/blob/main/docs/_docs/modules/zio-blocks-schema.md) shows how to map your own constraints to `Validation` with a `ZioBlocksValidation` given, and how to turn off the attached `Validation` or message with `ZioBlocksSchemaConfig`.
