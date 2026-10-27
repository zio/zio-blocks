---
id: zio-schema-migration
title: "Migrating from ZIO Schema to ZIO Blocks Schema"
---

This guide helps you migrate an application that uses [ZIO Schema](https://github.com/zio/zio-schema) (version 1.x) to [ZIO Blocks Schema](https://github.com/zio/zio-blocks) (the schema module of ZIO Blocks). It covers the conceptual differences between the two libraries, provides a systematic mapping of data types, and shows how to rewrite the most common patterns in the idiomatic ZIO Blocks style.

**What we will cover:**

- Prerequisites and dependency changes
- The core architectural shift from `Schema[A]` as a sealed trait to `Schema[A]` as a thin wrapper over `Reflect[F, A]`
- Migrating schema definitions for primitives, records, enums, collections, optional values, and newtypes
- Replacing the annotation/modifier system
- Adapting codec derivation to the unified `Format + Deriver` model
- Replacing `DynamicValue` usage
- Migrating optics and accessor patterns
- Migrating diff, patch, and schema evolution patterns
- Handling types and features that no longer have a direct analogue

Every ZIO Blocks snippet on this page is compiled and run when the site is built, and the output shown under it is the real output. The ZIO Schema 1.x snippets are shown for comparison only; they are not compiled here.

---

## Prerequisites

Moving to ZIO Blocks Schema changes your build in two places: the dependencies you declare and the packages you import.

### Dependency Changes

Replace the ZIO Schema dependency group with ZIO Blocks Schema.

**Before (ZIO Schema 1.x):**

```scala
libraryDependencies += "dev.zio" %% "zio-schema"            % "1.x.x"
libraryDependencies += "dev.zio" %% "zio-schema-derivation" % "1.x.x"
libraryDependencies += "dev.zio" %% "zio-schema-json"       % "1.x.x"
libraryDependencies += "dev.zio" %% "zio-schema-protobuf"   % "1.x.x"
libraryDependencies += "dev.zio" %% "zio-schema-avro"       % "1.x.x"
```

**After (ZIO Blocks Schema):**

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "@VERSION@"

// Scala 3.9 and newer
libraryDependencies += "dev.zio" %% "zio-blocks-next-schema" % "@VERSION@"
scalacOptions += "-experimental"
```

The other codec formats are optional modules. On Scala 3.9 and newer, each of them also takes the `zio-blocks-next-` prefix, for example `zio-blocks-next-schema-avro`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema-avro"        % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-thrift"      % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-bson"        % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-messagepack" % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-toon"        % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-yaml"        % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-xml"         % "@VERSION@"
libraryDependencies += "dev.zio" %% "zio-blocks-schema-csv"         % "@VERSION@"
```

For scala-cli, Scala.js, or cross-building, see [How to Add ZIO Blocks to Your Build](./add-zio-blocks-to-your-build.md).

Key points:
- JSON codec support is built into `zio-blocks-schema` — no separate JSON module.
- There is no separate `zio-blocks-schema-derivation` dependency; derivation is built in.
- You do not need to add `scala-reflect` for Scala 2 macro derivation: on Scala 2.13, `zio-blocks-schema` brings it in as a transitive dependency.
- ZIO Blocks Schema has **zero runtime dependency on ZIO itself**. You do not need `zio` on your classpath for schema operations.

### Package Rename

All imports change from `zio.schema` to `zio.blocks.schema`.

**Before (ZIO Schema 1.x):**

```scala
import zio.schema._
import zio.schema.annotation._
import zio.schema.codec._
import zio.schema.meta._
```

**After (ZIO Blocks Schema):**

```scala mdoc:compile-only
import zio.blocks.schema._         // Schema, Reflect, Modifier, DynamicValue, DynamicSchema, optics
import zio.blocks.schema.binding._ // Binding, BindingResolver, registers
import zio.blocks.schema.derive._  // Deriver
import zio.blocks.schema.patch._   // Patch, PatchMode, DynamicPatch
import zio.blocks.schema.json._    // JsonFormat, JsonCodec, Json
```

There is no separate annotation package: the `Modifier` annotations live in `zio.blocks.schema`. The codecs of `zio.schema.codec` move to one package per format (`zio.blocks.schema.json`, `zio.blocks.schema.avro`, and so on), and the `MetaSchema` of `zio.schema.meta` is replaced by `DynamicSchema` in `zio.blocks.schema`.

---

## The Core Architecture Shift

The most important thing to understand when migrating is that `Schema[A]` is no longer a sealed trait hierarchy — it is a thin case class.

**Before (ZIO Schema 1.x):** `Schema` is a sealed trait with about 16 kinds of node, and 61 concrete case classes once the arity-specialised `CaseClass0` to `CaseClass22` and `Enum1` to `Enum22` are counted one by one:

```scala
sealed trait Schema[A] {
  def annotations: Chunk[Any]
  def defaultValue: Either[String, A]
  // ...
}
```

**After (ZIO Blocks Schema):** `Schema` is a case class wrapping a `Reflect`:

```scala
final case class Schema[A](reflect: Reflect.Bound[A])
```

The structural description lives in `Reflect[F[_, _], A]`, a sealed trait with eight node types. The `F` type parameter distinguishes a *bound* reflect (with runtime constructors and deconstructors) from an *unbound* one (structural information only):

```
Reflect[F, A]
├── Reflect.Record[F, A]         — case classes and other product types
├── Reflect.Variant[F, A]        — sealed traits, enums, Option, Either
├── Reflect.Sequence[F, A, C[_]] — List, Vector, Set, Chunk, etc.
├── Reflect.Map[F, K, V, M[_,_]] — Map[K, V]
├── Reflect.Primitive[F, A]      — Int, String, UUID, java.time.*, etc.
├── Reflect.Wrapper[F, A, B]     — opaque types and validated newtypes
├── Reflect.Dynamic[F]           — escape hatch for schema-agnostic data
└── Reflect.Deferred[F, A]       — recursive (self-referential) types
```

As a result, you will rarely pattern match on `Schema[A]` directly — instead, you work through `schema.reflect` when you need to inspect structure. The [Reflect reference](../reference/schema/reflect.md) describes each node type.

---

## Migrating Schema Definitions

Most schema definitions migrate by changing one call. This section goes through each kind of type and notes where the underlying model differs.

### Schema Derivation

Automatic derivation changes in name only: `DeriveSchema.gen` becomes `Schema.derived`.

**Before (ZIO Schema 1.x):**

```scala
// The type is inferred from the ascription in Scala 2 and Scala 3;
// DeriveSchema.gen[Person] also works
import zio.schema._
final case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen
}
```

**After (ZIO Blocks Schema):** the same call in Scala 2 and Scala 3:

```scala mdoc:silent:reset
import zio.blocks.schema._

final case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]
}
```

Printing a schema shows the structure that `Schema.derived` built:

```scala mdoc
println(Person.schema)
```

`Schema.derived[A]` works identically in both Scala 2 and Scala 3 in ZIO Blocks Schema, and `Schema.derived` without the type parameter works too when the type is ascribed. There is no separate `DeriveSchema` object to import. For a first walk through derivation and JSON, see [Your First Schema](./your-first-schema.md).

### Primitives

ZIO Blocks Schema has 30 primitive types: `Unit`, `Boolean`, `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `Char`, `String`, `BigInt`, `BigDecimal`, the 16 `java.time` types, `Currency`, and `UUID`. ZIO Schema's 31 standard types cover the same ground with two differences. ZIO Schema's numeric standard types are `java.math.BigInteger` and `java.math.BigDecimal`, with Scala's `BigInt` and `BigDecimal` built on top of them, while ZIO Blocks has the Scala types as primitives. ZIO Schema also has a `Binary` standard type for `Chunk[Byte]`, while in ZIO Blocks a `Chunk[Byte]` is a sequence of `Byte` values.

Implicit schemas are available in the same way.

**Before (ZIO Schema 1.x):**

```scala
val intSchema: Schema[Int]                   = Schema[Int]
val instantSchema: Schema[java.time.Instant] = Schema[java.time.Instant]
```

**After (ZIO Blocks Schema):** identical call sites:

```scala mdoc:compile-only
import zio.blocks.schema._

val intSchema: Schema[Int]                   = Schema[Int]
val instantSchema: Schema[java.time.Instant] = Schema[java.time.Instant]
```

The underlying representation changes: ZIO Schema uses `Schema.Primitive[A](standardType: StandardType[A])`, while ZIO Blocks Schema uses `Reflect.Primitive[F, A](primitiveType: PrimitiveType[A], ...)`. In ZIO Blocks, the primitive type also carries an embedded `Validation[A]` constraint (see the [Migrating Validation](#migrating-validation) section below).

### Records (Case Classes)

**Before (ZIO Schema 1.x):**

```scala
import zio.schema._

final case class Address(street: String, city: String, postCode: String)
final case class Person(name: String, age: Int, address: Address)

object Person {
  implicit val schema: Schema[Person] = DeriveSchema.gen
}
```

**After (ZIO Blocks Schema):**

```scala mdoc:silent:reset
import zio.blocks.schema._

final case class Address(street: String, city: String, postCode: String)
final case class Person(name: String, age: Int, address: Address)

object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]
}
```

The derived schema includes the nested `Address` record, even though `Address` has no schema of its own:

```scala mdoc
println(Person.schema)
```

The derivation call is identical. Internally, ZIO Blocks generates a `Reflect.Record` node, one generic node type for records of every size. ZIO Schema uses the arity-specialised `CaseClass0` to `CaseClass22` instead, and falls back to `GenericRecord` for records with more than 22 fields.

If you were writing schemas manually using `Schema.CaseClass2[...]` or similar, you will need to rewrite those. The equivalent in ZIO Blocks is to write the `Reflect.Record` directly or, preferably, just use `Schema.derived[A]`.

**Before (ZIO Schema 1.x):** manual construction for a 2-field record:

```scala
val personSchema: Schema[Person] =
  Schema.CaseClass2[String, Int, Person](
    id0 = TypeId.fromTypeName("Person"),
    field01 = Schema.Field("name", Schema[String], get0 = _.name, set0 = (p, v) => p.copy(name = v)),
    field02 = Schema.Field("age",  Schema[Int],    get0 = _.age,  set0 = (p, v) => p.copy(age = v)),
    construct0 = Person(_, _)
  )
```

**After (ZIO Blocks Schema):** prefer derivation; no manual `CaseClass*` is required:

```scala mdoc:compile-only
val personSchema: Schema[Person] = Schema.derived[Person]
```

Manual construction is still possible in ZIO Blocks Schema (by assembling a `Reflect.Record` directly), but it is substantially more involved because you must supply a `Binding.Record` with explicit `Constructor[A]` and `Deconstructor[A]` implementations that use the unboxed register system. Automatic derivation is strongly preferred; the [Binding reference](../reference/schema/binding.md) covers the manual route.

### Sealed Traits / Enums (Sum Types)

**Before (ZIO Schema 1.x):**

```scala
import zio.schema._

sealed trait Shape
case class Circle(radius: Double)                   extends Shape
case class Rectangle(width: Double, height: Double) extends Shape

object Shape {
  implicit val schema: Schema[Shape] = DeriveSchema.gen
}
```

**After (ZIO Blocks Schema):**

```scala mdoc:silent:reset
import zio.blocks.schema._

sealed trait Shape
case class Circle(radius: Double)                   extends Shape
case class Rectangle(width: Double, height: Double) extends Shape

object Shape {
  implicit val schema: Schema[Shape] = Schema.derived[Shape]
}
```

The derived schema is a variant with one case per subtype:

```scala mdoc
println(Shape.schema)
```

Again, the call is identical. Internally, ZIO Blocks generates a `Reflect.Variant` node for any number of cases, where ZIO Schema uses `Enum1` to `Enum22`, and `EnumN` beyond that.

### Optional Values

ZIO Schema has a first-class `Schema.Optional[A]` node. In ZIO Blocks, `Option[A]` is modeled as a `Reflect.Variant` with two cases (`None` and `Some`). From a user perspective this is transparent — implicit schemas for `Option[A]` exist in the same form.

**Before (ZIO Schema 1.x):**

```scala
val optSchema: Schema[Option[String]] = Schema[Option[String]]
```

**After (ZIO Blocks Schema):** identical:

```scala mdoc:compile-only
import zio.blocks.schema._

val optSchema: Schema[Option[String]] = Schema[Option[String]]
```

For value-type `Option` variants (e.g., `Option[Int]`), ZIO Blocks provides specialised implicit instances (`Schema.optionInt`, `Schema.optionLong`, etc.) that avoid boxing. These are resolved automatically by the compiler — no code change required.

:::tip
The internal modeling difference (variant vs. dedicated node) is only relevant if you are pattern-matching on the raw `Schema` or `Reflect` structure. In that case, replace any match on `Schema.Optional(inner, _)` with a check on `Reflect#isOption` and use `Reflect#optionInnerType` to retrieve the inner reflect.

**Before (ZIO Schema 1.x):** pattern matching on `Optional`:

```scala
schema match {
  case Schema.Optional(inner, _) => // use inner
  case _                         => // ...
}
```

**After (ZIO Blocks Schema):** the `Reflect#isOption` predicate and the inner node:

```scala mdoc:silent:reset
import zio.blocks.schema._

val r = Schema[Option[String]].reflect
```

The reflect of `Option[String]` is an option, and its inner node is the `String` primitive:

```scala mdoc
r.isOption
println(r.optionInnerType)
```
:::

### Either

In ZIO Schema, `Either[A, B]` is a first-class `Schema.Either[A, B]` node. In ZIO Blocks, it is modeled as a two-case `Reflect.Variant`. The implicit schema is provided automatically.

**Before (ZIO Schema 1.x):**

```scala
val eitherSchema: Schema[Either[String, Int]] = Schema.either[String, Int]
```

**After (ZIO Blocks Schema):**

```scala mdoc:compile-only
import zio.blocks.schema._

val eitherSchema: Schema[Either[String, Int]] = Schema[Either[String, Int]]
```

:::warning
ZIO Blocks Schema does not have a `Fallback[A, B]` type. If you were using `Schema.Fallback` for partial decoding, you will need to model that with a custom `Reflect.Variant` or handle it in your codec logic directly.
:::

### Collections

All standard collection types are supported with the same implicit schema pattern.

**Before (ZIO Schema 1.x):**

```scala
Schema[List[String]]
Schema[Vector[Int]]
Schema[Chunk[Double]]
Schema[Set[String]]
Schema[Map[String, Int]]
```

**After (ZIO Blocks Schema):** identical call sites:

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.chunk.Chunk

Schema[List[String]]
Schema[Vector[Int]]
Schema[Chunk[Double]] // uses zio.blocks.chunk.Chunk
Schema[Set[String]]
Schema[Map[String, Int]]
```

Note that `Chunk` is now `zio.blocks.chunk.Chunk` (not `zio.Chunk`). This is a zero-dependency replacement with the same API surface for typical usage; see the [Chunk reference](../reference/chunk.md).

ZIO Schema's `NonEmptyChunk`, `NonEmptySet` and `NonEmptyMap` schemas do not have direct equivalents in ZIO Blocks Schema. The recommended approach is to model them as wrapper types.

**Before (ZIO Schema 1.x):** the `NonEmptyChunk` implicit schema:

```scala
val schema: Schema[NonEmptyChunk[String]] = Schema[NonEmptyChunk[String]]
```

**After (ZIO Blocks Schema):** a validated wrapper:

```scala mdoc:silent:reset
import zio.blocks.schema._

final case class NonEmptyList[A] private (values: List[A])
object NonEmptyList {
  def apply[A](head: A, tail: A*): NonEmptyList[A] = new NonEmptyList(head :: tail.toList)

  implicit def schema[A](implicit element: Schema[A]): Schema[NonEmptyList[A]] =
    Schema[List[A]].transform(
      to = list =>
        if (list.nonEmpty) new NonEmptyList(list)
        else throw SchemaError.validationFailed("List must not be empty"),
      from = _.values
    )
}
```

Decoding an empty list fails with the wrapper's message, and a non-empty one succeeds:

```scala mdoc
println(Schema[NonEmptyList[String]].fromDynamicValue(Schema[List[String]].toDynamicValue(Nil)))
println(Schema[NonEmptyList[String]].fromDynamicValue(Schema[List[String]].toDynamicValue(List("sale", "new"))))
```

### Newtypes and Opaque Types

ZIO Schema uses `Schema#transform` and `Schema#transformOrFail` (which wrap a `Transform` node) for both validated newtypes and lossless wrappers.

**Before (ZIO Schema 1.x):**

```scala
implicit val bigDecimalSchema: Schema[BigDecimal] =
  Schema.primitive[java.math.BigDecimal].transform(BigDecimal(_), _.bigDecimal)
```

ZIO Blocks Schema has `Schema#transform(to: A => B, from: B => A)`, which produces a `Reflect.Wrapper` node. The `to` and `from` functions are total but can throw to indicate failure. The method also requires an implicit `TypeId[B]`, which is derived automatically by the macro system for any concrete named type — you will not need to supply it manually for ordinary case classes.

**After (ZIO Blocks Schema):** a validated wrapper and a lossless one:

```scala mdoc:silent:reset
import zio.blocks.schema._

case class Email(value: String)
object Email {
  // TypeId[Email] is resolved implicitly from the macro-derived instance
  implicit val schema: Schema[Email] =
    Schema[String].transform(
      to = str =>
        if (str.contains('@')) Email(str)
        else throw SchemaError.validationFailed("Not a valid email address"),
      from = _.value
    )
}

case class UserId(value: Long)
object UserId {
  implicit val schema: Schema[UserId] =
    Schema[Long].transform(UserId(_), _.value)
}
```

A wrapper decodes from the value it wraps, and the validated one rejects bad input with the thrown message:

```scala mdoc
println(UserId.schema.toDynamicValue(UserId(42L)))
println(Email.schema.fromDynamicValue(Schema[String].toDynamicValue("alice@example.com")))
println(Email.schema.fromDynamicValue(Schema[String].toDynamicValue("not-an-email")))
```

:::warning
In ZIO Schema, `Schema#transformOrFail` accepts functions that return `Either[String, B]`. In ZIO Blocks, `Schema#transform` uses total functions that throw on failure — use `throw SchemaError.validationFailed(message)` in the `to` function to signal failure. There is no `transformOrFail` method. To collect every validation error instead of stopping at the first, see [How to Define Validated Newtypes](./validated-newtypes.md).
:::

### Lazy / Recursive Schemas

**Before (ZIO Schema 1.x):**

```scala
import zio.schema._

case class Tree(value: Int, children: List[Tree])
object Tree {
  implicit lazy val schema: Schema[Tree] = DeriveSchema.gen
  // Or manually with Schema.defer:
  // implicit lazy val schema: Schema[Tree] = Schema.CaseClass2(
  //   ..., field02 = Schema.Field("children", Schema.defer(Schema.list(schema)), ...)
  // )
}
```

**After (ZIO Blocks Schema):**

```scala mdoc:silent:reset
import zio.blocks.schema._

case class Tree(value: Int, children: List[Tree])
object Tree {
  implicit val schema: Schema[Tree] = Schema.derived[Tree]
}
```

A tree round-trips through `DynamicValue` with this schema:

```scala mdoc
val tree = Tree(1, List(Tree(2, Nil), Tree(3, Nil)))

println(Tree.schema.fromDynamicValue(Tree.schema.toDynamicValue(tree)))
```

Recursive types are handled automatically by the macro. Internally, ZIO Blocks generates a `Reflect.Deferred` node for the recursive reference — you do not need to use `Schema.defer` manually. The `implicit val` (not `lazy val`) is sufficient.

If you were wrapping a recursive reference manually with `Schema.defer(...)`, simply remove that wrapper — recursive references inside `Schema.derived` are handled for you.

---

## Migrating Annotations and Modifiers

ZIO Schema uses an open `Chunk[Any]` annotation system. ZIO Blocks Schema replaces this with a strongly-typed, sealed `Modifier` hierarchy, described on the [Modifier reference page](../reference/schema/modifier.md). Most modifiers are read by the JSON codec, so the examples below encode with the schema's JSON codec, `Schema#jsonCodec` (see [Migrating Codec Derivation](#migrating-codec-derivation)). [How to Customize JSON Encoding](./customize-json-encoding.md) covers the JSON options in depth.

### Transient Fields

**Before (ZIO Schema 1.x):**

```scala
import zio.schema.annotation._

final case class User(name: String, @transientField password: String = "")
object User {
  implicit val schema: Schema[User] = DeriveSchema.gen
}
```

**After (ZIO Blocks Schema):** as in ZIO Schema 1.x, a transient field needs a default value, except that ZIO Blocks also accepts an `Option` or a collection without one. The JSON codec leaves the field out when it encodes, so the decoder fills it in from the default; without one, `Schema.derived` fails to compile:

```scala mdoc:silent:reset
import zio.blocks.schema._

final case class User(name: String, @Modifier.transient() password: String = "")
object User {
  implicit val schema: Schema[User] = Schema.derived[User]
}

val userJson: String = User.schema.jsonCodec.encodeToString(User("alice", "s3cr3t"))
```

The encoded JSON has no `password` key, and decoding it gives the default back:

```scala mdoc
println(userJson)
println(User.schema.jsonCodec.decode(userJson))
```

The modifier only affects codecs: `Schema#toDynamicValue` still includes the field. Besides JSON, the TOON and BSON codecs honour it; the other format modules ignore it. For a field that should be read but never written, use `@Modifier.encodeTransient()` instead.

### Field Renaming

**Before (ZIO Schema 1.x):** the `@fieldName` annotation:

```scala
import zio.schema.annotation._

final case class Product(@fieldName("product_name") name: String, price: Double)
```

**After (ZIO Blocks Schema):** the `@Modifier.rename` annotation:

```scala mdoc:silent:reset
import zio.blocks.schema._

final case class Product(@Modifier.rename("product_name") name: String, price: Double)
object Product {
  implicit val schema: Schema[Product] = Schema.derived[Product]
}
```

The JSON codec writes the new name:

```scala mdoc
println(Product.schema.jsonCodec.encodeToString(Product("Widget", 9.99)))
```

### Field Aliases (for Decoding)

**Before (ZIO Schema 1.x):** the `@fieldNameAliases` annotation:

```scala
import zio.schema.annotation._

final case class Config(@fieldNameAliases("max-size", "max_size") maxSize: Int)
```

**After (ZIO Blocks Schema):** the `@Modifier.alias` annotation, one alias per annotation:

```scala mdoc:silent:reset
import zio.blocks.schema._

final case class Config(
  @Modifier.alias("max-size")
  @Modifier.alias("max_size")
  maxSize: Int
)
object Config {
  implicit val schema: Schema[Config] = Schema.derived[Config]
}
```

The decoder accepts every alias, as well as the field's own name:

```scala mdoc
println(Config.schema.jsonCodec.decode("""{"max-size":10}"""))
println(Config.schema.jsonCodec.decode("""{"max_size":20}"""))
println(Config.schema.jsonCodec.decode("""{"maxSize":30}"""))
```

### Codec-Specific Configuration

ZIO Schema 1.x has no generic key-value annotation: its core annotations, such as `@fieldDefaultValue` and `@optionalField`, have fixed meanings for every codec. ZIO Blocks Schema has one, `@Modifier.config(key, value)`, with keys that follow the convention `format.property`:

```scala mdoc:silent:reset
import zio.blocks.schema._

final case class Message(
  @Modifier.config("protobuf.field-id", "1") id: Long,
  @Modifier.config("protobuf.field-id", "2") content: String
)
object Message {
  implicit val schema: Schema[Message] = Schema.derived[Message]
}
```

None of the built-in codecs reads a `Modifier.config` key; the modifier carries settings for a deriver that you write (see [Migrating Type Class Derivation](#migrating-type-class-derivation)), which finds them on the schema's fields:

```scala mdoc
Message.schema.reflect.asRecord.get.fields.foreach { field =>
  println(s"${field.name}: ${field.modifiers}")
}
```

### Discriminator and Case Name Annotations

**Before (ZIO Schema 1.x):**

```scala
import zio.schema.annotation._

@discriminatorName("type")
sealed trait Event
@caseName("user_created")
case class UserCreated(userId: String) extends Event
```

**After (ZIO Blocks Schema):** `Modifier.discriminator` on the sealed trait and `Modifier.rename` on the case:

```scala mdoc:silent:reset
import zio.blocks.schema._

@Modifier.discriminator("type")
sealed trait Event
@Modifier.rename("user_created")
case class UserCreated(userId: String) extends Event

object Event {
  implicit val schema: Schema[Event] = Schema.derived[Event]
}
```

The JSON codec writes the case name into the `type` field:

```scala mdoc
println(Event.schema.jsonCodec.encodeToString(UserCreated("u-42")))
```

### Programmatic Annotation

ZIO Schema allows adding annotations at any time via `schema.annotate(annotation)`. In ZIO Blocks, you add modifiers with `Schema#modifier`.

**Before (ZIO Schema 1.x):**

```scala
val schema2 = schema.annotate(someAnnotation)
```

**After (ZIO Blocks Schema):**

```scala mdoc:silent
val schema2 = Event.schema.modifier(Modifier.config("key", "value"))
```

The new schema carries the modifier on its root node:

```scala mdoc
println(schema2.reflect.modifiers)
```

`Schema#modifier` takes a `Modifier.Reflect`, a modifier of the type itself (`Modifier.config`, `Modifier.discriminator`, `Modifier.noExtraFields`, `Modifier.fieldNaming` or `Modifier.caseNaming`). Field-level modifiers such as `Modifier.rename` go on the fields, as annotations.

---

## Migrating Codec Derivation

Codecs are where the two libraries differ most at the call site. This section covers how codecs are obtained, how they read and write data, and what replaces the JSON module and the streaming codecs.

### The Unified Format Model

ZIO Schema has separate codec APIs in each codec sub-module (e.g., `JsonCodec.jsonCodec`, `ProtobufCodec.protobufCodec`). ZIO Blocks Schema introduces a unified `codec.Format` interface that all codec modules implement, and codecs are derived via a consistent call.

**Before (ZIO Schema 1.x):** each codec module has its own factory:

```scala
// JsonCodec.jsonCodec returns a zio.json.JsonCodec (a text codec from the zio-json library)
import zio.schema.codec.JsonCodec
val jsonCodec = JsonCodec.jsonCodec(Person.schema)

// ProtobufCodec.protobufCodec returns a BinaryCodec[A] (Chunk[Byte] in / out)
import zio.schema.codec.ProtobufCodec
val protoCodec: BinaryCodec[Person] = ProtobufCodec.protobufCodec(Person.schema)
```

**After (ZIO Blocks Schema):** all codecs come from `Schema#derive(format)`:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.json.{JsonCodec, JsonFormat}
import zio.blocks.schema.avro.{AvroCodec, AvroFormat}

final case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]
}

val jsonCodec: JsonCodec[Person] = Person.schema.derive(JsonFormat)
val avroCodec: AvroCodec[Person] = Person.schema.derive(AvroFormat)
```

Each call to `Schema#derive` derives a new codec, so keep the result, for example in an implicit `val`. `Schema#getInstance(format)` derives the codec on first use and caches it in the schema, and `Schema#jsonCodec` is the cached JSON codec:

```scala mdoc
Person.schema.derive(JsonFormat) eq Person.schema.derive(JsonFormat)
Person.schema.getInstance(JsonFormat) eq Person.schema.getInstance(JsonFormat)
Person.schema.jsonCodec eq Person.schema.getInstance(JsonFormat)
```

### Encoding and Decoding

The codec interface differs significantly between the two libraries.

**Before (ZIO Schema 1.x):**

```scala
// Protobuf (true BinaryCodec: Chunk[Byte] in/out)
import zio.schema.codec.ProtobufCodec
val codec: BinaryCodec[Person] = ProtobufCodec.protobufCodec(Person.schema)
val encoded: Chunk[Byte]              = codec.encode(Person("Alice", 30))
val decoded: Either[DecodeError, Person] = codec.decode(encoded)

// JSON (zio-json JsonCodec: String in/out)
import zio.schema.codec.JsonCodec
val jsonCodec = JsonCodec.jsonCodec(Person.schema)
val json: String                       = jsonCodec.encodeJson(Person("Alice", 30), None).toString
val fromJson: Either[String, Person]   = jsonCodec.decodeJson(json)
```

**After (ZIO Blocks Schema):** `Schema#encode` and `Schema#decode` work with every format. Binary formats, which include JSON, write to and read from a `java.nio.ByteBuffer`; text formats such as CSV use a `java.nio.CharBuffer`:

```scala mdoc:silent
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

val person = Person("Alice", 30)

val buffer = ByteBuffer.allocate(1024)
Person.schema.encode(JsonFormat)(buffer)(person)
buffer.flip()
```

The buffer now holds the JSON bytes, and decoding reads them back:

```scala mdoc
println(StandardCharsets.UTF_8.decode(buffer.duplicate()).toString)
println(Person.schema.decode(JsonFormat)(buffer))
```

The JSON codec also works with strings and byte arrays directly, which is the closest match to zio-json's `JsonEncoder#encodeJson` and `JsonDecoder#decodeJson`:

```scala mdoc
println(jsonCodec.encodeToString(person))
println(jsonCodec.decode("""{"name":"Bob","age":31}"""))
println(jsonCodec.decode("""{"name":"Bob"}"""))
```

Decoding errors are `SchemaError` values instead of `DecodeError` or `String`. ZIO Blocks codecs do not depend on `zio-json` or any other external codec library.

### JSON Codec

JSON support is built into the core `zio-blocks-schema` module — no separate dependency is needed.

**Before (ZIO Schema 1.x):** a separate `zio-schema-json` module:

```scala
libraryDependencies += "dev.zio" %% "zio-schema-json" % "1.x.x"
import zio.schema.codec.JsonCodec
val codec = JsonCodec.jsonCodec(Person.schema)  // returns zio.json.JsonCodec
```

**After (ZIO Blocks Schema):** built into `zio-blocks-schema`; no extra dependency:

```scala mdoc:compile-only
import zio.blocks.schema.json.JsonFormat

val codec = Person.schema.derive(JsonFormat) // returns JsonCodec[Person]
```

To change field naming, discriminators or other JSON settings for a whole codec, derive it from a configured `JsonCodecDeriver`, as [How to Customize JSON Encoding](./customize-json-encoding.md) shows.

### Streaming Codecs

ZIO Schema's streaming codec methods (`streamEncoder`, `streamDecoder`) integrated with `ZStream`. ZIO Blocks Schema codecs encode and decode one value at a time, and they do not depend on ZIO's streaming primitives. The JSON codec can read a value from a `java.io.InputStream` and write one to a `java.io.OutputStream` without collecting it into an array first (see [How to Tune JSON Throughput](./tune-json-throughput.md)). If you need a stream of values, wrap the codec in your effect system's streaming abstraction.

---

## Migrating DynamicValue

`DynamicValue` is the untyped value tree in both libraries, but its cases and its API changed. The [DynamicValue reference](../reference/schema/dynamic-value.md) documents the ZIO Blocks version in full.

### Structure Changes

The `DynamicValue` ADT is significantly simplified in ZIO Blocks — from 15 cases down to 6. The key differences:

- ZIO Schema's `DynamicValue.Primitive[A](value: A, standardType: StandardType[A])` stores the raw value and its `StandardType` inline. ZIO Blocks wraps the scalar in a `PrimitiveValue` case class instead.
- `DynamicValue.Record` drops the `TypeId` parameter and uses `Chunk[(String, DynamicValue)]` instead of `ListMap[String, DynamicValue]`.
- `Option`, `Either`, and `Tuple` are no longer dedicated ADT cases — they are represented structurally using `Variant` and `Record`.

| ZIO Schema                                                  | ZIO Blocks Schema                                                   |
|-------------------------------------------------------------|---------------------------------------------------------------------|
| `Primitive[A](value: A, standardType: StandardType[A])`     | `Primitive(value: PrimitiveValue)`                                  |
| `Record(id: TypeId, values: ListMap[String, DynamicValue])` | `Record(fields: Chunk[(String, DynamicValue)])` — no TypeId         |
| `Enumeration(id: TypeId, value: (String, DynamicValue))`    | `Variant(caseNameValue: String, value: DynamicValue)`               |
| `Sequence(values: Chunk[DynamicValue])`                     | `Sequence(elements: Chunk[DynamicValue])`                           |
| `Dictionary(entries: Chunk[(DynamicValue, DynamicValue)])`  | `Map(entries: Chunk[(DynamicValue, DynamicValue)])`                 |
| `SomeValue(value: DynamicValue)`                            | `Variant("Some", Record(Chunk("value" -> ...)))`                    |
| `NoneValue`                                                 | `Variant("None", Record(Chunk.empty))`                              |
| `LeftValue(value: DynamicValue)`                            | `Variant("Left", Record(Chunk("value" -> ...)))`                    |
| `RightValue(value: DynamicValue)`                           | `Variant("Right", Record(Chunk("value" -> ...)))`                   |
| `Tuple(left, right)`                                        | `Record(Chunk("_1" -> left, "_2" -> right))`                        |
| `SetValue(values: Set[DynamicValue])`                       | `Sequence(elements: Chunk[DynamicValue])`                           |
| `BothValue(left, right)`                                    | No direct equivalent (used by `Fallback`, which is removed)         |
| `DynamicAst(ast: MetaSchema)`                               | No direct equivalent                                                |
| `Singleton[A](instance: A)`                                 | No direct equivalent                                                |
| `Error(message: String)`                                    | No direct equivalent — use `SchemaError`                            |

The sixth ZIO Blocks case, `DynamicValue.Null`, stands for a missing value and has no ZIO Schema counterpart. To see the `Option`, `Either` and tuple encodings, convert a few values and print their structure:

```scala mdoc:silent:reset
import zio.blocks.schema._

val some  = Schema[Option[Int]].toDynamicValue(Some(1))
val none  = Schema[Option[Int]].toDynamicValue(None)
val left  = Schema[Either[String, Int]].toDynamicValue(Left("oops"))
val tuple = Schema.derived[(String, Int)].toDynamicValue(("Alice", 30))
```

Tuples have no implicit schema in ZIO Blocks, so the tuple's schema comes from `Schema.derived`.

The `@ {tag: ...}` suffix marks a variant case in the printed form:

```scala mdoc
println(some)
println(none)
println(left)
println(tuple)
```

### Primitive Values

In ZIO Schema, primitive values are stored inline in `DynamicValue.Primitive[A](value: A, standardType: StandardType[A])`. There is no separate `PrimitiveValue` type. In ZIO Blocks, a sealed `PrimitiveValue` ADT wraps each primitive.

**Before (ZIO Schema 1.x):** value and `StandardType` are separate constructor arguments:

```scala
import zio.schema.{DynamicValue, StandardType}
val pv: DynamicValue = DynamicValue.Primitive(42, StandardType[Int])
val ps: DynamicValue = DynamicValue.Primitive("hello", StandardType[String])
```

**After (ZIO Blocks Schema):** the value is wrapped in a `PrimitiveValue` case class:

```scala mdoc:compile-only
import zio.blocks.schema.{DynamicValue, PrimitiveValue}

val pv: DynamicValue = DynamicValue.Primitive(PrimitiveValue.Int(42))
val ps: DynamicValue = DynamicValue.Primitive(PrimitiveValue.String("hello"))
```

The `PrimitiveValue` case names (`Int`, `Long`, `String`, `Boolean`, `Double`, etc.) match the Scala primitive names.

### Converting Between Typed Values and DynamicValue

**Before (ZIO Schema 1.x):**

```scala
// toDynamic is a method on Schema[A], not on the value itself
val dv: DynamicValue         = Person.schema.toDynamic(person)
// toTypedValue requires an implicit Schema[Person] in scope
val back: Either[String, Person] = dv.toTypedValue[Person]
// or, on the schema
val back2: Either[String, Person] = Person.schema.fromDynamic(dv)
```

**After (ZIO Blocks Schema):**

```scala mdoc:silent:reset
import zio.blocks.schema._

final case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]
}

val person = Person("Alice", 30)

val dv: DynamicValue                  = Person.schema.toDynamicValue(person)
val back: Either[SchemaError, Person] = Person.schema.fromDynamicValue(dv)
```

The value goes to a `DynamicValue` and comes back unchanged:

```scala mdoc
println(dv)
println(back)
```

Two things change: `Schema#toDynamic` is renamed `Schema#toDynamicValue`, and `DynamicValue#toTypedValue` and `Schema#fromDynamic` are replaced by `Schema#fromDynamicValue`. The error type changes from `String` to `SchemaError`.

### DynamicValue Operations

ZIO Blocks `DynamicValue` has a rich operation API that was absent in ZIO Schema. Where ZIO Schema required you to convert back to a typed value to manipulate data, you can now operate directly on `DynamicValue`:

```scala mdoc:silent
import zio.blocks.chunk.Chunk

val record = DynamicValue.Record(
  Chunk(
    "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
    "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
  )
)

val other = DynamicValue.Record(
  Chunk(
    "name" -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
    "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(31))
  )
)
```

`DynamicValue#get` navigates and returns a `DynamicValueSelection`, which supports chaining; `DynamicValueSelection#one` extracts a single value as an `Either[SchemaError, DynamicValue]`. `DynamicValue#set` replaces the value at a path and returns the new `DynamicValue` (it leaves the value unchanged when the path is not found; `DynamicValue#setOrFail` returns an `Either` instead). `DynamicValue#diff` computes a `DynamicPatch` between two values:

```scala mdoc
println(record.get("name").one)

println(record.set(DynamicOptic.root.field("name"), DynamicValue.Primitive(PrimitiveValue.String("Bob"))))

println(record.diff(other))
```

---

## Migrating Schema Introspection

ZIO Schema describes a schema as data with `MetaSchema`. ZIO Blocks has `DynamicSchema` for the same job, and it can be stored, loaded and turned back into a typed schema.

### MetaSchema → DynamicSchema

ZIO Schema has `MetaSchema` (a type-erased structural description of a schema) and `schema.ast` to convert to it. ZIO Blocks uses `DynamicSchema` for the same purpose.

**Before (ZIO Schema 1.x):**

```scala
val meta: MetaSchema = schema.ast
val back: Schema[_]  = meta.toSchema
```

**After (ZIO Blocks Schema):** `Schema#toDynamicSchema` goes one way, and `DynamicSchema#rebind` goes back to a typed schema, taking the bindings (the functions that build and take apart values) from a `BindingResolver`:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.binding._

final case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]
}

val dynSchema: DynamicSchema = Person.schema.toDynamicSchema

val resolver             = BindingResolver.empty.bind(Binding.of[Person]) ++ BindingResolver.defaults
val rebound: Schema[Person] = dynSchema.rebind[Person](resolver)
```

`DynamicSchema` wraps a `Reflect[NoBinding, _]` — the full structural description without runtime constructors or deconstructors. Use it for:

- Runtime structural validation of `DynamicValue` instances
- Dynamic schema loading from configuration or network
- Schema inspection without compile-time type information

To check data against the structure, we need one value that matches it and one that misses a field:

```scala mdoc:silent
import zio.blocks.chunk.Chunk

val value = DynamicValue.Record(
  Chunk(
    "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
    "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
  )
)
val incomplete = DynamicValue.Record(
  Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")))
)
```

`DynamicSchema#check` returns the first mismatch as an `Option[SchemaError]`, and `DynamicSchema#conforms` returns a `Boolean`:

```scala mdoc
dynSchema.conforms(value)
dynSchema.check(value)
println(dynSchema.check(incomplete))
```

:::info
ZIO Blocks Schema includes an explicit migration API for evolving values between schema versions. The entry point is [`Migration.newBuilder[A, B]`](../reference/schema/migration.md), which builds a typed `Migration[A, B]` backed by a serializable `DynamicMigration`.

This is a different model from ZIO Schema's `schema.migrate(newSchema)` and `schema.coerce(newSchema)` methods, which work out the conversion from the two schemas at runtime:

1. Build a migration explicitly with operations like `addField`, `dropField`, `renameField`, `changeFieldType`, and `transformField`.
2. Apply the resulting `Migration[A, B]` to typed values, or inspect/transport the underlying `DynamicMigration`.

Use this when you want structural schema evolution as first-class data rather than implicit derivation. To convert between two versions of a type without writing the steps, derive an `Into[A, B]` instead. [Evolve Your Data](./evolve-your-data.md) walks through both.
:::

### Schema Serialization

ZIO Schema supports serializing a schema itself (via `schema.serializable`, a `Schema[Schema[A]]`). In ZIO Blocks, the serializable form of a schema is its `DynamicSchema`: `DynamicSchema.schema` is a `Schema[DynamicSchema]`, so any codec can store and load one. A loaded `DynamicSchema` checks values directly, or turns into a `Schema[A]` with `DynamicSchema#rebind`:

```scala mdoc:silent
val stored: String = DynamicSchema.schema.jsonCodec.encodeToString(dynSchema)
val loaded         = DynamicSchema.schema.jsonCodec.decode(stored)
```

The schema loaded back from JSON accepts the same value:

```scala mdoc
println(loaded.map(_.conforms(value)))
```

The stored form keeps field names, cases, type ids, modifiers, docs and `Validation` constraints, but not the bindings or the default values of case class fields. A schema of a recursive type cannot be stored this way in 0.0.56 (a known bug). [How to Build a Schema Registry](./build-a-schema-registry.md) builds a registry on this API.

---

## Migrating Optics

ZIO Schema uses an `AccessorBuilder` pattern that delegates optic creation to an external `zio-schema-optics` module. ZIO Blocks Schema includes a complete, first-class optics system in the core module.

### Generating Optics

**Before (ZIO Schema 1.x with `zio-schema-optics`):**

```scala
import zio.schema._
import zio.schema.optics._

case class Person(name: String, age: Int)
object Person {
  // makeAccessors needs the concrete CaseClass2 type to give typed lenses
  implicit val schema: Schema.CaseClass2[String, Int, Person] =
    DeriveSchema.gen[Person].asInstanceOf[Schema.CaseClass2[String, Int, Person]]
  val (name, age) = schema.makeAccessors(ZioOpticsBuilder)
}
```

**After (ZIO Blocks Schema):** extend `CompanionOptics[Person]` in the companion object and define each optic with its `optic` macro (also available as `$`). It works the same in Scala 2 and Scala 3, and the compiler checks the field path:

```scala mdoc:silent:reset
import zio.blocks.schema._

case class Person(name: String, age: Int)
object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person] = Schema.derived[Person]

  val name: Lens[Person, String] = optic(_.name)
  val age: Lens[Person, Int]     = optic(_.age)
}
```

To get a lens for every field without writing them, extend `DerivedOptics` instead: its `DerivedOptics#optics` member holds one lens per field, named after the field. In 0.0.56 this works on Scala 3 only; on Scala 2, `DerivedOptics#optics` compiles only inside the `zio.blocks.schema` package (a known bug):

```scala mdoc:compile-only
// Scala 3 only in 0.0.56
import zio.blocks.schema._

case class Customer(name: String, email: String)
object Customer extends DerivedOptics {
  implicit val schema: Schema[Customer] = Schema.derived[Customer]
}

val email: Lens[Customer, String] = Customer.optics.email
```

You can also look a lens up by field name at runtime, from the schema's record node. The name and the type are not checked at compile time, so `Reflect.Record#lensByName` returns an `Option`:

```scala mdoc:compile-only
val nameLens: Option[Lens[Person, String]] =
  Person.schema.reflect.asRecord.flatMap(_.lensByName[String]("name"))
```

[Work with Nested Data Using Optics](./nested-data-with-optics.md) teaches optics step by step.

### Using Optics

The four optic types in ZIO Blocks are `Lens`, `Prism`, `Optional`, and `Traversal`. Their usage API is similar to standard optics libraries, with one naming difference: ZIO Blocks uses `Lens#replace` where Monocle and many other optics libraries have `set`.

To read, modify and replace a field with the lenses defined above:

```scala mdoc
val person = Person("Alice", 30)

Person.name.get(person)
Person.name.modify(person, _.toUpperCase)
Person.name.replace(person, "Bob")
```

For sealed traits, use `Prism`. `Reflect.Variant#prismByName` looks one up by case name:

```scala mdoc:silent:reset
import zio.blocks.schema._

sealed trait Shape
case class Circle(radius: Double)          extends Shape
case class Rectangle(w: Double, h: Double) extends Shape

object Shape {
  implicit val schema: Schema[Shape] = Schema.derived[Shape]

  val circlePrism: Prism[Shape, Circle] =
    schema.reflect.asVariant.get.prismByName[Circle]("Circle").get
}
```

`Prism#getOption` returns the case when the value is one, and `Prism#reverseGet` widens a case back to the sealed trait:

```scala mdoc
val shape: Shape = Circle(5.0)

Shape.circlePrism.getOption(shape)
Shape.circlePrism.getOption(Rectangle(1.0, 2.0))
Shape.circlePrism.reverseGet(Circle(3.0))
```

The [Optics reference](../reference/schema/optics.md) covers `Optional`, `Traversal` and optic composition.

### Schema Expressions (New in ZIO Blocks)

ZIO Blocks introduces `SchemaExpr[S, A]`, a typed expression language built on top of optics. There is no equivalent in ZIO Schema. These allow you to build inspectable, composable predicates and computations:

```scala mdoc:silent:reset
import zio.blocks.schema._

case class Product(name: String, price: Double, inStock: Boolean)
object Product extends CompanionOptics[Product] {
  implicit val schema: Schema[Product] = Schema.derived[Product]

  val price: Lens[Product, Double]    = optic(_.price)
  val inStock: Lens[Product, Boolean] = optic(_.inStock)
}

// Build a typed predicate expression
val cheapAndInStock: SchemaExpr[Product, Boolean] =
  (Product.price < 100.0) && (Product.inStock === true)
```

`SchemaExpr#eval` returns `Right` with the results on success, and `Left(OpticCheck)` when a prism in the path does not match:

```scala mdoc
cheapAndInStock.eval(Product("Widget", 49.99, inStock = true))
cheapAndInStock.eval(Product("Gadget", 149.99, inStock = true))
```

The [SchemaExpr reference](../reference/schema/schema-expr.md) lists the operators.

---

## Migrating Diff and Patch

Both libraries compute a patch from two values and apply it to a third. The call sites stay the same, while the patch type and the error type change. [How to Diff and Patch Values](./diff-and-patch.md) covers the ZIO Blocks API in depth.

### Diff

ZIO Schema uses `Differ.fromSchema(schema)(a, b)` or the convenience method `schema.diff(a, b)`. ZIO Blocks Schema uses the same convenience method.

**Before (ZIO Schema 1.x):**

```scala
val patch: Patch[Person] = Person.schema.diff(person1, person2)
```

**After (ZIO Blocks Schema):**

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.patch._

case class Person(name: String, age: Int)
object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person] = Schema.derived[Person]

  val name: Lens[Person, String] = optic(_.name)
  val age: Lens[Person, Int]     = optic(_.age)
}

val person1 = Person("Alice", 30)
val person2 = Person("Bob", 31)

val patch: Patch[Person] = Person.schema.diff(person1, person2)
```

The printed patch lists one operation per changed field:

```scala mdoc
println(patch)
```

The call site is identical, but the underlying `Patch` types are different.

### Patch Application

**Before (ZIO Schema 1.x):**

```scala
val result: Either[String, Person] = Person.schema.patch(person, patch)
```

**After (ZIO Blocks Schema):** `Schema#patch`, or equivalently `Patch#apply` with `PatchMode.Strict`:

```scala mdoc
Person.schema.patch(person1, patch)
patch.apply(person1, PatchMode.Strict)
```

The error type changes from `String` to `SchemaError`.

### Creating Patches Programmatically

ZIO Schema has no optic-based patch constructors: to build a patch by hand, you assemble the cases of its `Patch` ADT, such as `Patch.Record` and `Patch.Total`. ZIO Blocks Schema provides `Patch` smart constructors that take an optic. `Patch.set` sets a field through a lens, and `Patch#++` composes two patches:

```scala mdoc:silent
val renamePatch: Patch[Person] = Patch.set(Person.name, "Bob")
val combined: Patch[Person]    = renamePatch ++ Patch.set(Person.age, 31)
```

Applying the combined patch sets both fields:

```scala mdoc
combined(person1, PatchMode.Strict)
```

---

## Migrating Type Class Derivation

Both libraries can derive a user-defined type class from a schema. The interface keeps its name, `Deriver`, but what it receives changes.

### Before (ZIO Schema 1.x)

ZIO Schema 1.x has `zio.schema.Deriver[F[_]]` in the `zio-schema-derivation` module. A deriver implements `deriveRecord`, `deriveEnum`, `derivePrimitive`, `deriveOption`, `deriveSequence`, `deriveMap` and `deriveTransformedRecord`, receives the `Schema` nodes of the type, and is called with `Derive.derive[F, A](deriver)`. ZIO Schema's own codecs do not use it: each codec module walks the schema with its own code.

### After (ZIO Blocks Schema)

ZIO Blocks Schema's `Deriver[TC]` plays the same role, but it receives `Reflect` nodes and their bindings instead of `Schema` nodes, and you call it with `Schema#derive(deriver)`. The built-in codecs use it too: `JsonFormat`'s deriver is a `Deriver[JsonCodec]`, so a custom codec and a custom type class are written the same way. A deriver implements one method per kind of `Reflect` node:

```scala
trait Deriver[TC[_]] {
  def derivePrimitive[A](primitiveType: PrimitiveType[A], typeId: TypeId[A], binding: Binding.Primitive[A], doc: Doc, modifiers: Seq[Modifier.Reflect], defaultValue: Option[A], examples: Seq[A]): Lazy[TC[A]]
  def deriveRecord[F[_, _], A](fields: IndexedSeq[Term[F, A, _]], typeId: TypeId[A], binding: Binding.Record[A], doc: Doc, modifiers: Seq[Modifier.Reflect], defaultValue: Option[A], examples: Seq[A])(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[A]]
  def deriveVariant[F[_, _], A](cases: IndexedSeq[Term[F, A, _]], typeId: TypeId[A], binding: Binding.Variant[A], doc: Doc, modifiers: Seq[Modifier.Reflect], defaultValue: Option[A], examples: Seq[A])(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[A]]
  def deriveSequence[F[_, _], C[_], A](element: Reflect[F, A], typeId: TypeId[C[A]], binding: Binding.Seq[C, A], doc: Doc, modifiers: Seq[Modifier.Reflect], defaultValue: Option[C[A]], examples: Seq[C[A]])(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[C[A]]]
  def deriveMap[F[_, _], M[_, _], K, V](key: Reflect[F, K], value: Reflect[F, V], typeId: TypeId[M[K, V]], binding: Binding.Map[M, K, V], doc: Doc, modifiers: Seq[Modifier.Reflect], defaultValue: Option[M[K, V]], examples: Seq[M[K, V]])(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[M[K, V]]]
  def deriveDynamic[F[_, _]](binding: Binding.Dynamic, doc: Doc, modifiers: Seq[Modifier.Reflect], defaultValue: Option[DynamicValue], examples: Seq[DynamicValue])(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[DynamicValue]]
  def deriveWrapper[F[_, _], A, B](wrapped: Reflect[F, B], typeId: TypeId[A], binding: Binding.Wrapper[A, B], doc: Doc, modifiers: Seq[Modifier.Reflect], defaultValue: Option[A], examples: Seq[A])(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[A]]
}
```

The companion example `DeriveShowExample` implements all seven methods for a `Show` type class, then derives `Show` for records, variants, recursive types, `DynamicValue` and a wrapper with `Schema#derive(DeriveShow)`. For `Person("Alice", 30)` it prints `Person(name = "Alice", age = 30)`:

```scala mdoc:embed:schema-examples/src/main/scala/typeclassderivation/DeriveShowExample.scala
```

[How to Derive Your Own Type Class](./derive-your-own-type-class.md) builds a deriver step by step, and the [Type Class Derivation reference](../reference/schema/type-class-derivation.md) documents the interface.

---

## Migrating Validation

Validation moves from an annotation-based combinator library to two smaller mechanisms: constraints on primitive types and checks inside wrapper types.

### Before (ZIO Schema 1.x)

ZIO Schema uses a composable `Validation[A]`, combined with `&&`, `||`, `!`, `allOf`, `anyOf` and `contramap`, and attached to a field with the `@validate(...)` annotation. `Schema.validate` checks the field validations of a value:

```scala
import zio.schema._
import zio.schema.validation._
import zio.schema.annotation._

case class User(
  @validate(Validation.greaterThan(0)) age: Int,
  @validate(Validation.minLength(3))   name: String
)
object User {
  implicit val schema: Schema[User] = DeriveSchema.gen
}

Schema.validate(User(-1, "Al"))
// Returns a Chunk[ValidationError] with one error per violation
```

### After (ZIO Blocks Schema)

ZIO Blocks Schema has a simpler, non-composable `Validation[A]` that is embedded inside `PrimitiveType[A]` and checked only by `DynamicSchema#check` (and `DynamicSchema#conforms`), not by `Schema#fromDynamicValue` or the codecs. One primitive carries one constraint, and there are no combinators. A derived schema carries `Validation.None` on every primitive; a constraint comes in when a schema is loaded from JSON Schema with `Schema.fromJsonSchema`, or when you build the primitive node yourself:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.binding.Binding

val positive = PrimitiveType.Int(Validation.Numeric.Positive)
val positiveIntSchema: Schema[Int] =
  new Schema(new Reflect.Primitive[Binding, Int](positive, positive.typeId, positive.binding))

val minusOne = DynamicValue.Primitive(PrimitiveValue.Int(-1))
```

`DynamicSchema#check` reports the violated constraint, while `Schema#fromDynamicValue` accepts the same value:

```scala mdoc
println(positiveIntSchema.toDynamicSchema.check(minusOne))
println(positiveIntSchema.fromDynamicValue(minusOne))
```

For validated types, use `Schema#transform` with a throwing `to` function, which signals failure during `Schema#fromDynamicValue` and decoding:

```scala mdoc:silent:reset
import zio.blocks.schema._

final case class Age(value: Int)
object Age {
  implicit val schema: Schema[Age] =
    Schema[Int].transform(
      to = n => if (n > 0) Age(n) else throw SchemaError.validationFailed("Must be positive"),
      from = _.value
    )
}
```

Decoding `-1` now fails with the message thrown in `to`:

```scala mdoc
println(Age.schema.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(-1))))
```

For validation across several fields, the same rule applies: put the check in the `to` function of a wrapper, or in application-level code that runs after decoding.

:::info
If you rely heavily on ZIO Schema's composable validation (combining validators with `&&`, `||`, `!`, `allOf`, `anyOf` or `contramap`), you will need to implement that logic in the `to` function of a `Schema#transform` wrapper, or in application-level validation code. ZIO Blocks Schema's built-in `Validation` is deliberately simpler: it covers the most common primitive constraints without the complexity of a full combinator library. [How to Define Validated Newtypes](./validated-newtypes.md) shows how to collect every error, and the [Validation reference](../reference/schema/validation.md) lists the built-in constraints.
:::

---

## Migrating the Fail Schema

ZIO Schema provides `Schema.fail[A](message: String)` to represent the absence of schema information.

**Before (ZIO Schema 1.x):**

```scala
val missing: Schema[MyType] = Schema.fail("No schema available for MyType")
```

ZIO Blocks Schema has no equivalent `Fail` schema node. The recommended approach is to leave the implicit schema undefined and let the compiler report the missing instance, or to throw from a type class derivation. If you need a runtime placeholder for data whose shape is not known, use the schema of `DynamicValue`, which accepts any value.

**After (ZIO Blocks Schema):**

```scala mdoc:compile-only
import zio.blocks.schema._

val placeholder: Schema[DynamicValue] = Schema[DynamicValue]
```

---

## Summary of Missing Features

The following ZIO Schema features do not yet have equivalents in ZIO Blocks Schema, or have a different one:

| Feature                                                    | Status                                                                                                   |
|------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| `Schema.fail` / fail schemas                               | Not available                                                                                            |
| `schema.migrate` / `schema.coerce`                         | Replaced by explicit [`Migration.newBuilder[A, B]`](../reference/schema/migration.md) and `Into[A, B]`   |
| `MetaSchema` / schema serialization                        | Replaced by `DynamicSchema`, stored with `DynamicSchema.schema` and turned back into a `Schema[A]` with `DynamicSchema#rebind`; recursive types cannot be stored yet |
| `Fallback[A, B]` schema                                    | Not available                                                                                            |
| `NonEmptyChunk` / `NonEmptySet` / `NonEmptyMap` schemas    | Not available — use wrapper types                                                                        |
| `Schema.singleton` / singleton schemas                     | Not available                                                                                            |
| `DynamicValue.BothValue` / `DynamicValue.DynamicAst`       | Not available                                                                                            |
| Composable `Validation` (`&&`, `\|\|`, `!`)                | Not available — use `Schema#transform` with throwing functions                                           |
| Streaming codec methods (`streamEncoder`, `streamDecoder`) | Not available — wrap codecs in your effect system                                                        |
| ZIO `Chunk` (from `zio-core`)                              | Replaced by `zio.blocks.chunk.Chunk`                                                                     |

---

## Running the Examples

All code from this guide is available as runnable examples in the `schema-examples` module, a standalone sbt build that depends on the published ZIO Blocks artifacts.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/schema-examples
```

**2. Run individual examples with sbt:**

```bash
# Step 1: Schema derivation, primitives, and DynamicValue roundtrip
sbt "runMain ziosschemamigration.Step1SchemaDerivedAndPrimitives"

# Step 2: Modifiers and transform (annotations, newtypes)
sbt "runMain ziosschemamigration.Step2ModifiersAndTransform"

# Step 3: Optics (Lens, Prism) and DynamicSchema validation
sbt "runMain ziosschemamigration.Step3OpticsAndDynamicSchema"

# Step 4: Diff and patch
sbt "runMain ziosschemamigration.Step4DiffAndPatch"

# Complete example: end-to-end e-commerce domain
sbt "runMain ziosschemamigration.CompleteMigrationExample"

# Type class derivation — deriving Show from a Schema
sbt "runMain typeclassderivation.DeriveShowExample"

# Type class derivation — deriving a random generator from a Schema
sbt "runMain typeclassderivation.DeriveGenExample"
```

**3. Or compile all examples at once:**

```bash
sbt compile
```

---

## Going Further

- [Schema Reference](../reference/schema/schema.md) — full `Schema[A]` API
- [Reflect Reference](../reference/schema/reflect.md) — the `Reflect[F, A]` node types
- [Binding Reference](../reference/schema/binding.md) — constructors, deconstructors, and the register system
- [Optics Reference](../reference/schema/optics.md) — `Lens`, `Prism`, `Optional`, `Traversal`
- [Type Class Derivation Reference](../reference/schema/type-class-derivation.md) — implementing `Deriver[TC]`
- [Codec Reference](../reference/schema/codec.md) — the `Format` and `Codec` infrastructure
- [DynamicValue Reference](../reference/schema/dynamic-value.md) — the `DynamicValue` API
- [DynamicSchema Reference](../reference/schema/dynamic-schema.md) — storing, checking and rebinding schemas
- [Validation Reference](../reference/schema/validation.md) — built-in validation constraints
- [Evolve Your Data](./evolve-your-data.md) — `Into`, `As` and `Migration` between versions of a type
- [How to Use Scala 3 Named Tuples with Schema](./use-named-tuples-with-schema.md) — schemas for named tuples
