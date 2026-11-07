---
id: override-codec-for-one-type
title: "How to Override the Codec for One Type Everywhere"
description: "Encode LocalDate (or any type) your own way in every derived codec by registering one custom codec on the deriver."
keywords:
  - "Custom Codecs"
  - "Date Formats"
  - "Codec Override"
  - "Deriver"
  - "withInstance"
  - "JsonCodecDeriver"
---

In this guide, we make every derived codec in a small Scala bookstore write `java.time.LocalDate` as `dd/MM/yyyy` instead of the ISO-8601 default. We write one custom codec for `LocalDate`, register it once on the deriver with `withInstance`, and derive every other codec through that deriver. Nested types pick up the override automatically, and the same recipe works for formats other than JSON.

We cover:

- Writing a custom `JsonCodec[LocalDate]`
- Registering it once on `JsonCodecDeriver`
- Deriving codecs for nested types through that deriver
- Doing the same for TOON, a second format
- Narrower scopes: one field, or one derivation
- Choosing the right tool

## The Problem

The derived JSON codec writes a `LocalDate` as an ISO-8601 string such as `2025-03-14`. Our bookstore's partner system expects `14/03/2025`, and it expects that format in every message we send it, whichever case class the date sits in.

Without a way to change one type's codec globally, every type that contains a date needs its own encoding code, and every one of them repeats the date format. Here is that approach written by hand:

```scala mdoc:compile-only
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val partnerDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

case class Order(title: String, orderedOn: LocalDate, price: BigDecimal)
case class Shelf(owner: String, openedOn: LocalDate, orders: List[Order])

def orderToJson(order: Order): String =
  s"""{"title":"${order.title}","orderedOn":"${partnerDateFormat.format(order.orderedOn)}","price":${order.price}}"""

def shelfToJson(shelf: Shelf): String =
  s"""{"owner":"${shelf.owner}","openedOn":"${partnerDateFormat.format(shelf.openedOn)}","orders":[${shelf.orders
      .map(orderToJson)
      .mkString(",")}]}"""
```

This approach doesn't scale. Each new case class with a date needs another hand-written function, the decoders need the same treatment, and nothing stops one function from forgetting the custom format. It also covers JSON only; a second wire format means writing it all again.

In ZIO Blocks, a deriver builds a codec for every type from its schema. We give the deriver one custom codec for `LocalDate`, and it uses that codec wherever a `LocalDate` appears.

## Prerequisites

This guide builds on [Your First Schema](./your-first-schema.md): you should know how to derive a `Schema` and round-trip a value through JSON.

Add the schema module, and the TOON module for the second-format section, to your `build.sbt`:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-schema"      % "@VERSION@",
  "dev.zio" %% "zio-blocks-schema-toon" % "@VERSION@"
)

// Scala 3.9 and newer (also needs scalacOptions += "-experimental")
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-next-schema"      % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-schema-toon" % "@VERSION@"
)
```

We use these imports throughout:

```scala mdoc:silent
import zio.blocks.schema._
import zio.blocks.schema.json._
import zio.blocks.typeid.TypeId

import java.time.LocalDate
import java.time.format.{DateTimeFormatter, DateTimeParseException}
```

## The Bookstore Model

Our model has two case classes. An `Order` records one book purchase, and a `Shelf` groups a reader's orders. Both contain a `LocalDate`, and `Shelf` nests `Order` inside a list:

```scala mdoc:silent
case class Order(title: String, orderedOn: LocalDate, price: BigDecimal)

object Order {
  implicit val schema: Schema[Order] = Schema.derived
}

case class Shelf(owner: String, openedOn: LocalDate, orders: List[Order])

object Shelf {
  implicit val schema: Schema[Shelf] = Schema.derived
}

val shelf = Shelf(
  owner = "Ada",
  openedOn = LocalDate.of(2025, 1, 10),
  orders = List(
    Order("Zionomicon", LocalDate.of(2025, 3, 14), BigDecimal("39.99")),
    Order("Programming in Scala", LocalDate.of(2025, 4, 2), BigDecimal("54.50")),
    Order("Functional Programming in Scala", LocalDate.of(2025, 5, 21), BigDecimal("44.00"))
  )
)
```

The order dates and prices are sample data. With the default codec, both dates come out in ISO-8601 form:

```scala mdoc
println(shelf.toJsonString)
```

## Writing the Codec for `LocalDate`

A `JsonCodec[A]` needs two methods: `decodeValue(in: JsonReader)` reads a value from the streaming reader, and `encodeValue(x, out: JsonWriter)` writes it. Two more methods convert to and from the `Json` value type; their defaults throw, so we override them too, which keeps the codec usable with `Json` values:

```scala mdoc:silent
val partnerDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

val partnerDateCodec: JsonCodec[LocalDate] = new JsonCodec[LocalDate] {
  private def parse(s: String): LocalDate =
    try LocalDate.parse(s, partnerDateFormat)
    catch { case _: DateTimeParseException => error(s"expected a dd/MM/yyyy date, got: $s") }

  def decodeValue(in: JsonReader): LocalDate =
    parse(in.readString())

  def encodeValue(x: LocalDate, out: JsonWriter): Unit =
    out.writeVal(partnerDateFormat.format(x))

  override def decodeValue(json: Json): LocalDate = json match {
    case s: Json.String => parse(s.value)
    case _              => error("expected a JSON string")
  }

  override def encodeValue(x: LocalDate): Json =
    Json.String(partnerDateFormat.format(x))
}
```

The codec reads and writes a JSON string, the same JSON type the default `LocalDate` codec uses; only the text inside changes. `error` is a method on `JsonCodec` that reports a decode failure, so a malformed date becomes a `SchemaError` instead of an uncaught exception.

## Registering the Codec on the Deriver

`JsonCodecDeriver` is the deriver behind the default JSON codecs. Its `withInstance` method returns a new deriver that uses our codec for `LocalDate` and derives everything else as before:

```scala mdoc:silent
val partnerJson = JsonCodecDeriver.withInstance[LocalDate](partnerDateCodec)
```

`withInstance[A]` finds the type through an implicit `TypeId[A]`, which ZIO Blocks provides for `LocalDate` and the other built-in types. The original `JsonCodecDeriver` is unchanged, so the default codecs keep working.

To use a money type as well, chain another call, for example `.withInstance[BigDecimal](priceAsStringCodec)`. Each call adds one override.

## Deriving Codecs Through the Deriver

`Schema#deriving` starts a derivation with a given deriver, and `.derive` builds the codec. We derive one codec per top-level type we send:

```scala mdoc:silent
val orderCodec: JsonCodec[Order] = Schema[Order].deriving(partnerJson).derive
val shelfCodec: JsonCodec[Shelf] = Schema[Shelf].deriving(partnerJson).derive
```

The `Shelf` codec uses the custom date format for its own `openedOn` field and for the `orderedOn` field of every `Order` inside the list:

```scala mdoc
println(orderCodec.encodeToString(shelf.orders.head))
println(shelfCodec.encodeToString(shelf))
```

The deriver matches the override by `TypeId` at every level of the schema, so we never mention `Order` or its fields when we register the override. Decoding uses the same codec, so the round trip returns the original value:

```scala mdoc
println(shelfCodec.decode(shelfCodec.encodeToString(shelf)) == Right(shelf))
```

:::warning
`toJsonString` and `fromJson` always use the default codec from `Schema#jsonCodec`, not a deriver you configured. Call `encodeToString` and `decode` on the codec you derived. An `implicit JsonCodec[LocalDate]` in scope doesn't change derivation either: the deriver never searches for codec implicits, so the override must be registered with `withInstance`.
:::

## Doing the Same for TOON

Every format in ZIO Blocks has its own deriver, and `withInstance` is defined on the shared `Deriver` trait, so the recipe is the same for each one. Here it is for [TOON](../reference/schema/built-in-codecs/toon.md), a compact text format. A `ToonCodec` has the same `decodeValue`/`encodeValue` pair, over `ToonReader` and `ToonWriter`:

```scala mdoc:silent
import zio.blocks.schema.toon._

val partnerDateToonCodec: ToonCodec[LocalDate] = new ToonCodec[LocalDate] {
  def decodeValue(in: ToonReader): LocalDate = {
    in.skipBlankLines()
    val s = in.readString()
    try LocalDate.parse(s, partnerDateFormat)
    catch { case _: DateTimeParseException => error(s"expected a dd/MM/yyyy date, got: $s") }
  }

  def encodeValue(x: LocalDate, out: ToonWriter): Unit =
    out.writeString(partnerDateFormat.format(x))
}

val partnerToon = ToonCodecDeriver.withInstance[LocalDate](partnerDateToonCodec)

val shelfToonCodec: ToonCodec[Shelf] = Schema[Shelf].deriving(partnerToon).derive
```

`skipBlankLines()` mirrors the built-in TOON `LocalDate` codec, which skips blank lines before reading a value. The TOON output carries the custom date format for both `Shelf` and the nested orders:

```scala mdoc
println(shelfToonCodec.encodeToString(shelf))
```

## Narrower Scope: One Field

Sometimes only one field should use the custom format. The second `withInstance` overload takes the parent type's `TypeId`, the field name and the codec. Here only `Order.orderedOn` changes, and `Shelf.openedOn` keeps the ISO-8601 format:

```scala mdoc:silent
val orderDateOnly = JsonCodecDeriver.withInstance(TypeId.of[Order], "orderedOn", partnerDateCodec)

val shelfCodecOrderDateOnly: JsonCodec[Shelf] = Schema[Shelf].deriving(orderDateOnly).derive
```

```scala mdoc
println(shelfCodecOrderDateOnly.encodeToString(shelf))
```

The field type isn't checked against the codec at compile time, so make sure the codec's type matches the field's type.

## Narrower Scope: One Derivation

To override for a single derivation without building a new deriver, call `instance` on the `DerivationBuilder` that `deriving` returns. It accepts a `TypeId` (with or without a field name) or an optic:

```scala mdoc:silent
val oneOffShelfCodec: JsonCodec[Shelf] =
  Schema[Shelf]
    .deriving(JsonCodecDeriver)
    .instance(TypeId.of[LocalDate], partnerDateCodec)
    .derive
```

```scala mdoc
println(oneOffShelfCodec.encodeToString(shelf))
```

When the deriver and the builder both override the same type or field, the deriver's override wins. See [Custom Type-class Instances](../reference/schema/type-class-derivation.md#custom-type-class-instances) for the optic overload and the full resolution order.

The deriver also accepts field modifiers through `withModifier`, for example `withModifier(TypeId.of[Order], "title", Modifier.rename("bookTitle"))`. For JSON field names, ADT encoding and optional fields, see [How to Customize JSON Field Names, ADT Encoding and Optional Fields](./customize-json-encoding.md).

## Which Tool When

| Goal                                                      | Tool                                                                             | Scope                                                |
|-----------------------------------------------------------|----------------------------------------------------------------------------------|------------------------------------------------------|
| One type encoded the same way in every derived codec      | `Deriver#withInstance[A](codec)`                                                 | Every codec derived through that deriver             |
| One field of one type encoded differently                 | `Deriver#withInstance(TypeId.of[P], "field", codec)`                             | That field, in every codec from that deriver         |
| A one-off override for a single codec                     | `DerivationBuilder#instance(typeId or optic, codec)`                             | The one `.derive` call                               |
| Full control of a whole type's wire format, used directly | A hand-written `JsonCodec[A]`, called directly or plugged in with `withInstance` | Only where you call it; derivation ignores implicits |

## Putting It Together

This example combines everything from the guide: a custom `LocalDate` codec for JSON and TOON, registered once on each deriver, then used for the nested bookstore model and checked with a round trip:

```scala mdoc:embed:schema-examples/src/main/scala/overridecodecforonetype/CompleteExample.scala
```

## Running the Examples

All examples in this guide have corresponding runnable Scala files in the `schema-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/schema-examples
```

Run them in order to follow the guide step by step.

### Writing the Codec for `LocalDate`

This example writes the custom `JsonCodec[LocalDate]` and uses it on its own to encode and decode a single date.

<details>
  <summary>schema-examples/src/main/scala/overridecodecforonetype/Step1WriteCodecExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/overridecodecforonetype/Step1WriteCodecExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain overridecodecforonetype.Step1WriteCodecExample"
```

### Registering the Codec and Deriving Nested Types

This example registers the codec on `JsonCodecDeriver`, derives codecs for `Order` and `Shelf`, and prints both next to the default output.

<details>
  <summary>schema-examples/src/main/scala/overridecodecforonetype/Step2DeriveEverywhereExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/overridecodecforonetype/Step2DeriveEverywhereExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain overridecodecforonetype.Step2DeriveEverywhereExample"
```

### Doing the Same for TOON

This example applies the same recipe to `ToonCodecDeriver`.

<details>
  <summary>schema-examples/src/main/scala/overridecodecforonetype/Step3ToonExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/overridecodecforonetype/Step3ToonExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain overridecodecforonetype.Step3ToonExample"
```

### Narrower Scopes

This example overrides only `Order.orderedOn`, then overrides `LocalDate` for a single derivation with `DerivationBuilder#instance`.

<details>
  <summary>schema-examples/src/main/scala/overridecodecforonetype/Step4NarrowerScopesExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/overridecodecforonetype/Step4NarrowerScopesExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain overridecodecforonetype.Step4NarrowerScopesExample"
```

### The Complete Example

The complete example from "Putting It Together" runs the JSON and TOON overrides in one program:

```bash
sbt "runMain overridecodecforonetype.CompleteExample"
```

To check that every example compiles without running it, use:

```bash
sbt compile
```

## Going Further

- [Customizing Derivation with Instance and Modifier Overrides](../reference/schema/type-class-derivation.md#customizing-derivation-with-instance-and-modifier-overrides) covers every `withInstance` and `withModifier` overload and how overrides chain.
- [How to Derive Your Own Type Class from a Schema](./derive-your-own-type-class.md) writes a `Deriver` for a type class of your own, beyond codecs.
- [Codec](../reference/schema/codec.md) explains how formats, derivers and codecs fit together.
- [TOON](../reference/schema/built-in-codecs/toon.md) documents the TOON format and its deriver settings.
