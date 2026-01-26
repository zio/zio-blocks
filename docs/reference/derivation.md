---
id: derivation
title: "Type-Class Derivation"
---

Type-class derivation is one of the most powerful features in ZIO Blocks. Once you define a `Schema` for your data type, you can automatically derive type-class instances such as codecs, validators, and custom type-classes. This eliminates boilerplate code and ensures consistency across your application.

## Overview

The core API for type-class derivation consists of two key methods on `Schema[A]`:

```scala
final case class Schema[A](reflect: Reflect.Bound[A]) {
  // Direct derivation - derive an instance immediately
  def derive[TC[_]](deriver: Deriver[TC]): TC[A]
  
  // Fluent API - configure before deriving
  def deriving[TC[_]](deriver: Deriver[TC]): DerivationBuilder[TC, A]
}
```

- **`derive`**: Directly derives a type-class instance using a `Deriver`
- **`deriving`**: Returns a builder for configuring the derivation before calling `.derive`

## Basic Codec Derivation

ZIO Blocks provides built-in derivers for multiple serialization formats. Each format module exports a `Format` object with a `.deriver` property.

### JSON Codec

```scala mdoc:compile-only
import zio.blocks.schema.Schema
import zio.blocks.schema.json.JsonFormat
import zio.blocks.schema.json.JsonBinaryCodec

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
  val codec: JsonBinaryCodec[Person] = schema.derive(JsonFormat.deriver)
}

// Use the codec
val person = Person("Alice", 30)
val encoded = Person.codec.encode(person)
val decoded = Person.codec.decode(encoded)
```

### Avro Codec

```scala mdoc:compile-only
import zio.blocks.schema.Schema
import zio.blocks.schema.avro.AvroFormat
import zio.blocks.schema.avro.AvroBinaryCodec

case class User(id: Long, email: String)

object User {
  implicit val schema: Schema[User] = Schema.derived
  val codec: AvroBinaryCodec[User] = schema.derive(AvroFormat.deriver)
}
```

### TOON Codec

TOON is a compact, line-oriented, indentation-based text format optimized for LLM usage:

```scala mdoc:compile-only
import zio.blocks.schema.Schema
import zio.blocks.schema.toon.ToonFormat
import zio.blocks.schema.toon.ToonBinaryCodec

case class Product(id: String, name: String, price: BigDecimal)

object Product {
  implicit val schema: Schema[Product] = Schema.derived
  val codec: ToonBinaryCodec[Product] = schema.derive(ToonFormat.deriver)
}
```

### MessagePack Codec

```scala mdoc:compile-only
import zio.blocks.schema.Schema
import zio.blocks.schema.msgpack.MessagePackFormat
import zio.blocks.schema.msgpack.MessagePackBinaryCodec

case class Event(timestamp: Long, eventType: String, data: String)

object Event {
  implicit val schema: Schema[Event] = Schema.derived
  val codec: MessagePackBinaryCodec[Event] = schema.derive(MessagePackFormat.deriver)
}
```

## Configuring Derivers

Many derivers support configuration options. For JSON, you can customize field naming, discriminator handling, and more by configuring `JsonFormat.deriver` before calling `schema.derive`.

### Field Name Mapping

Control how field names are transformed during serialization:

```scala mdoc:compile-only
import zio.blocks.schema.Schema
import zio.blocks.schema.json.{JsonFormat, JsonBinaryCodec, NameMapper}

case class UserAccount(firstName: String, lastName: String, emailAddress: String)

object UserAccount {
  implicit val schema: Schema[UserAccount] = Schema.derived
  
  // Convert camelCase to snake_case
  val snakeCaseCodec: JsonBinaryCodec[UserAccount] = 
    schema.derive(JsonFormat.deriver.withFieldNameMapper(NameMapper.SnakeCase))
  
  // Convert to kebab-case
  val kebabCaseCodec: JsonBinaryCodec[UserAccount] = 
    schema.derive(JsonFormat.deriver.withFieldNameMapper(NameMapper.KebabCase))
  
  // Custom mapping
  val upperCaseCodec: JsonBinaryCodec[UserAccount] = 
    schema.derive(JsonFormat.deriver.withFieldNameMapper(NameMapper.Custom(_.toUpperCase)))
}

// snakeCaseCodec produces: {"first_name": "Alice", "last_name": "Smith", "email_address": "alice@example.com"}
// kebabCaseCodec produces: {"first-name": "Alice", "last-name": "Smith", "email-address": "alice@example.com"}
// upperCaseCodec produces: {"FIRSTNAME": "Alice", "LASTNAME": "Smith", "EMAILADDRESS": "alice@example.com"}
```

### Discriminator Configuration

For sealed traits (sum types), control how cases are distinguished:

```scala mdoc:compile-only
import zio.blocks.schema.Schema
import zio.blocks.schema.json.{JsonFormat, JsonBinaryCodec, DiscriminatorKind}

sealed trait Shape
object Shape {
  case class Circle(radius: Double) extends Shape
  case class Rectangle(width: Double, height: Double) extends Shape
  
  implicit val schema: Schema[Shape] = Schema.derived
  
  // Default: wrapped discriminator {"Circle": {"radius": 5.0}}
  val defaultCodec: JsonBinaryCodec[Shape] = schema.derive(JsonFormat.deriver)
  
  // Field discriminator: {"type": "Circle", "radius": 5.0}
  val fieldCodec: JsonBinaryCodec[Shape] = 
    schema.derive(JsonFormat.deriver.withDiscriminatorKind(DiscriminatorKind.Field("type")))
  
  // No discriminator (infer from structure)
  val noDiscriminatorCodec: JsonBinaryCodec[Shape] = 
    schema.derive(JsonFormat.deriver.withDiscriminatorKind(DiscriminatorKind.None))
}
```

### Optional Field Handling

Control how `Option`, empty collections, and default values are serialized:

```scala mdoc:compile-only
import zio.blocks.schema.Schema
import zio.blocks.schema.json.{JsonFormat, JsonBinaryCodec}

case class Config(
  host: String,
  port: Option[Int],
  tags: List[String],
  retries: Int = 3
)

object Config {
  implicit val schema: Schema[Config] = Schema.derived
  
  // Skip None values in output
  val transientNoneCodec: JsonBinaryCodec[Config] = 
    schema.derive(JsonFormat.deriver.withTransientNone(true))
  
  // Skip empty collections
  val transientEmptyCodec: JsonBinaryCodec[Config] = 
    schema.derive(JsonFormat.deriver.withTransientEmptyCollection(true))
  
  // Skip fields with default values
  val transientDefaultCodec: JsonBinaryCodec[Config] = 
    schema.derive(JsonFormat.deriver.withTransientDefaultValue(true))
  
  // Combine multiple options
  val minimalCodec: JsonBinaryCodec[Config] = 
    schema.derive(
      JsonFormat.deriver
        .withTransientNone(true)
        .withTransientEmptyCollection(true)
        .withTransientDefaultValue(true)
    )
}
```

### Strict Validation

Enable strict validation to reject unexpected fields or require all fields to be present:

```scala mdoc:compile-only
import zio.blocks.schema.Schema
import zio.blocks.schema.json.{JsonFormat, JsonBinaryCodec}

case class StrictConfig(host: String, port: Int)

object StrictConfig {
  implicit val schema: Schema[StrictConfig] = Schema.derived
  
  // Reject extra fields in input
  val strictCodec: JsonBinaryCodec[StrictConfig] = 
    schema.derive(JsonFormat.deriver.withRejectExtraFields(true))
}

// This will fail: {"host": "localhost", "port": 8080, "unexpected": "field"}
```

## The Fluent Deriving API

The `.deriving()` method returns a `DerivationBuilder` that allows you to configure instance overrides and modifier overrides before calling `.derive`.

### Basic Usage

```scala mdoc:compile-only
import zio.blocks.schema.Schema
import zio.blocks.schema.json.{JsonFormat, JsonBinaryCodec}

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
  
  // These are equivalent:
  val codec1: JsonBinaryCodec[Person] = schema.derive(JsonFormat.deriver)
  val codec2: JsonBinaryCodec[Person] = schema.deriving(JsonFormat.deriver).derive
}
```

### Instance Overrides

Use instance overrides to provide custom type-class instances for specific fields or nested types. The optic syntax `$(_.field)` is provided by extending `CompanionOptics[A]`:

```scala mdoc:compile-only
import zio.blocks.schema.{Schema, CompanionOptics}
import zio.blocks.schema.json.{JsonFormat, JsonBinaryCodec}

// Custom wrapper type with validation
case class Email(value: String)

object Email {
  implicit val schema: Schema[Email] = Schema[String]
    .transformOrFail(
      str => if (str.contains("@")) Right(Email(str)) else Left(zio.blocks.schema.SchemaError.validationFailed("Invalid email")),
      _.value
    )
    .withTypeName[Email]
  
  // Custom codec with special handling
  // For this example, we'll define a simple custom codec
  val customCodec: JsonBinaryCodec[Email] = schema.derive(JsonFormat.deriver)
}

case class User(id: Long, email: Email)

object User extends CompanionOptics[User] {
  implicit val schema: Schema[User] = Schema.derived
  
  // Override the derived codec for the Email field using the optic syntax
  val codec: JsonBinaryCodec[User] = schema
    .deriving(JsonFormat.deriver)
    .instance($(_.email), Email.customCodec)
    .derive
}
```

In this example, we provide a custom `JsonBinaryCodec[Email]` for the `email` field instead of using the automatically derived one. The `$(_.email)` syntax is a type-safe way to create an optic that targets the `email` field, provided by the `CompanionOptics` trait.

### Multiple Overrides

You can chain multiple `.instance()` calls to override different fields:

```scala mdoc:compile-only
import zio.blocks.schema.{Schema, CompanionOptics}
import zio.blocks.schema.json.{JsonFormat, JsonBinaryCodec}

case class Custom1(value: String)
case class Custom2(value: Int)

case class Complex(
  field1: Custom1,
  field2: Custom2,
  field3: String
)

object Complex extends CompanionOptics[Complex] {
  implicit val schema: Schema[Complex] = Schema.derived
  
  // Define custom codecs for Custom1 and Custom2
  val custom1Codec: JsonBinaryCodec[Custom1] = Schema[Custom1].derive(JsonFormat.deriver)
  val custom2Codec: JsonBinaryCodec[Custom2] = Schema[Custom2].derive(JsonFormat.deriver)
  
  val codec: JsonBinaryCodec[Complex] = schema
    .deriving(JsonFormat.deriver)
    .instance($(_.field1), custom1Codec)
    .instance($(_.field2), custom2Codec)
    .derive
}
```

## Caching and Performance

The `Schema` class caches format-based codec instances internally when using the `encode` and `decode` methods. Direct calls to `.derive()` are not cached and will recompute the codec each time.

```scala mdoc:compile-only
import zio.blocks.schema.Schema
import zio.blocks.schema.json.{JsonFormat, JsonBinaryCodec}

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Direct derivation - NOT cached, recomputed each time
val codec1 = Schema[Person].derive(JsonFormat.deriver)
val codec2 = Schema[Person].derive(JsonFormat.deriver)
// codec1 eq codec2  // false - different instances

// Cache derived codecs as vals to avoid recomputation
val cachedCodec: JsonBinaryCodec[Person] = Schema[Person].derive(JsonFormat.deriver)
```

However, the `Schema` class does cache codecs internally when using format-based `encode` and `decode` methods:

```scala mdoc:compile-only
import java.nio.ByteBuffer
import zio.blocks.schema.Schema
import zio.blocks.schema.json.JsonFormat

val person = Person("Alice", 30)

// These calls use Schema's internal cache (keyed by Format)
// The first call derives and caches, subsequent calls reuse
val buffer = ByteBuffer.allocate(1024)
Schema[Person].encode(JsonFormat)(buffer)(person)
val decoded = Schema[Person].decode(JsonFormat)(buffer)
```

For best performance, cache derived codecs as `val` fields in your companion objects rather than calling `.derive()` repeatedly.

## Creating Custom Derivers

You can create custom type-class derivers by implementing the `Deriver[TC]` trait. This allows you to derive any type-class from a schema.

### The Deriver Trait

```scala
trait Deriver[TC[_]] {
  def derivePrimitive[F[_, _], A](
    primitiveType: PrimitiveType[A],
    typeName: TypeName[A],
    binding: Binding[BindingType.Primitive, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  ): Lazy[TC[A]]

  def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeName: TypeName[A],
    binding: Binding[BindingType.Record, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[A]]

  def deriveVariant[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ?]],
    typeName: TypeName[A],
    binding: Binding[BindingType.Variant, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[A]]

  def deriveSequence[F[_, _], C[_], A](
    element: Reflect[F, A],
    typeName: TypeName[C[A]],
    binding: Binding[BindingType.Seq[C], C[A]],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[C[A]]]

  def deriveMap[F[_, _], M[_, _], K, V](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeName: TypeName[M[K, V]],
    binding: Binding[BindingType.Map[M], M[K, V]],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[M[K, V]]]

  def deriveDynamic[F[_, _]](
    binding: Binding[BindingType.Dynamic, DynamicValue],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[DynamicValue]]

  def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeName: TypeName[A],
    wrapperPrimitiveType: Option[PrimitiveType[A]],
    binding: Binding[BindingType.Wrapper[A, B], A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[A]]
}
```

### Example: Pretty Printer

Here's a simple example of a custom deriver that generates pretty-printed string representations:

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.derive._
import zio.blocks.schema.binding._

// Our type-class: pretty-print any type
trait PrettyPrinter[A] {
  def print(value: A): String
}

object PrettyPrinter {
  // Deriver implementation
  object deriver extends Deriver[PrettyPrinter] {
    
    def derivePrimitive[F[_, _], A](
      primitiveType: PrimitiveType[A],
      typeName: TypeName[A],
      binding: Binding[BindingType.Primitive, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect]
    ): Lazy[PrettyPrinter[A]] = Lazy {
      new PrettyPrinter[A] {
        def print(value: A): String = value.toString
      }
    }
    
    def deriveRecord[F[_, _], A](
      fields: IndexedSeq[Term[F, A, ?]],
      typeName: TypeName[A],
      binding: Binding[BindingType.Record, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[PrettyPrinter[A]] = Lazy {
      new PrettyPrinter[A] {
        def print(value: A): String = {
          val deconstructor = binding.deconstructor
          val fieldValues = fields.map { field =>
            val fieldValue = deconstructor.projectDynamic(value, field.label)
            val printer = D.instance(field.reflect).value
            s"${field.label} = ${printer.print(fieldValue.asInstanceOf[field.A])}"
          }
          s"${typeName.shortName}(${fieldValues.mkString(", ")})"
        }
      }
    }
    
    def deriveVariant[F[_, _], A](
      cases: IndexedSeq[Term[F, A, ?]],
      typeName: TypeName[A],
      binding: Binding[BindingType.Variant, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[PrettyPrinter[A]] = Lazy {
      new PrettyPrinter[A] {
        def print(value: A): String = {
          val deconstructor = binding.deconstructor
          val caseIndex = deconstructor.indexOf(value)
          val selectedCase = cases(caseIndex)
          val caseValue = deconstructor.projectDynamic(value, caseIndex)
          val printer = D.instance(selectedCase.reflect).value
          printer.print(caseValue.asInstanceOf[selectedCase.A])
        }
      }
    }
    
    def deriveSequence[F[_, _], C[_], A](
      element: Reflect[F, A],
      typeName: TypeName[C[A]],
      binding: Binding[BindingType.Seq[C], C[A]],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[PrettyPrinter[C[A]]] = Lazy {
      new PrettyPrinter[C[A]] {
        def print(value: C[A]): String = {
          val printer = D.instance(element).value
          val elements = binding.deconstructor.toChunk(value)
          s"[${elements.map(printer.print).mkString(", ")}]"
        }
      }
    }
    
    def deriveMap[F[_, _], M[_, _], K, V](
      key: Reflect[F, K],
      value: Reflect[F, V],
      typeName: TypeName[M[K, V]],
      binding: Binding[BindingType.Map[M], M[K, V]],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[PrettyPrinter[M[K, V]]] = Lazy {
      new PrettyPrinter[M[K, V]] {
        def print(value: M[K, V]): String = {
          val keyPrinter = D.instance(key).value
          val valuePrinter = D.instance(value).value
          val entries = binding.deconstructor.toChunk(value)
          val entryStrings = entries.map { case (k, v) =>
            s"${keyPrinter.print(k)} -> ${valuePrinter.print(v)}"
          }
          s"{${entryStrings.mkString(", ")}}"
        }
      }
    }
    
    def deriveDynamic[F[_, _]](
      binding: Binding[BindingType.Dynamic, DynamicValue],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[PrettyPrinter[DynamicValue]] = Lazy {
      new PrettyPrinter[DynamicValue] {
        def print(value: DynamicValue): String = value.toString
      }
    }
    
    def deriveWrapper[F[_, _], A, B](
      wrapped: Reflect[F, B],
      typeName: TypeName[A],
      wrapperPrimitiveType: Option[PrimitiveType[A]],
      binding: Binding[BindingType.Wrapper[A, B], A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[PrettyPrinter[A]] = Lazy {
      new PrettyPrinter[A] {
        def print(value: A): String = {
          val unwrapped = binding.deconstructor.unwrap(value)
          val printer = D.instance(wrapped).value
          printer.print(unwrapped)
        }
      }
    }
  }
}

// Usage
case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
  val printer: PrettyPrinter[Person] = schema.derive(PrettyPrinter.deriver)
}

val person = Person("Alice", 30)
// printer.print(person) => "Person(name = Alice, age = 30)"
```

## Format Abstraction

The `Format` abstraction provides a unified interface for working with serialization formats:

```scala
// From zio.blocks.schema.codec package
trait Format {
  type DecodeInput
  type EncodeOutput
  type TypeClass[A] <: Codec[DecodeInput, EncodeOutput, A]
  
  def mimeType: String
  def deriver: Deriver[TypeClass]
}
```

This abstraction enables you to write format-agnostic code:

```scala mdoc:compile-only
import zio.blocks.schema.Schema
import zio.blocks.schema.codec.Format

def getMimeType[F <: Format](format: F): String = format.mimeType

// Example usage with specific formats
import zio.blocks.schema.json.JsonFormat
import zio.blocks.schema.avro.AvroFormat

val jsonMime = getMimeType(JsonFormat)    // "application/json"
val avroMime = getMimeType(AvroFormat)    // "application/avro"
```

## Best Practices

### 1. Define Schemas in Companion Objects

```scala mdoc:compile-only
import zio.blocks.schema.Schema

case class User(id: Long, name: String)

object User {
  implicit val schema: Schema[User] = Schema.derived
}
```

### 2. Cache Derived Codecs

Define codecs as `val` fields to avoid re-derivation:

```scala mdoc:compile-only
import zio.blocks.schema.Schema
import zio.blocks.schema.json.{JsonFormat, JsonBinaryCodec}

case class Product(id: String, name: String)

object Product {
  implicit val schema: Schema[Product] = Schema.derived
  val jsonCodec: JsonBinaryCodec[Product] = schema.derive(JsonFormat.deriver)
}
```

### 3. Use Format-Specific Configuration

Take advantage of format-specific configuration options:

```scala mdoc:compile-only
import zio.blocks.schema.Schema
import zio.blocks.schema.json.{JsonFormat, JsonBinaryCodec, NameMapper}

case class ApiResponse(
  statusCode: Int,
  responseBody: String,
  contentType: String
)

object ApiResponse {
  implicit val schema: Schema[ApiResponse] = Schema.derived
  
  // Use snake_case for API compatibility
  val codec: JsonBinaryCodec[ApiResponse] = 
    schema.derive(JsonFormat.deriver.withFieldNameMapper(NameMapper.SnakeCase))
}
```

### 4. Leverage the Fluent API for Complex Cases

For complex derivations with instance overrides, use the fluent API. Remember to extend `CompanionOptics[A]` for type-safe field selection:

```scala mdoc:compile-only
import zio.blocks.schema.{Schema, CompanionOptics}
import zio.blocks.schema.json.{JsonFormat, JsonBinaryCodec}
import java.time.Instant

case class CustomInstant(value: Instant)

object CustomInstant {
  implicit val schema: Schema[CustomInstant] = Schema[Instant]
    .transform(CustomInstant(_), _.value)
    .withTypeName[CustomInstant]
  
  // Custom codec with special formatting
  val customCodec: JsonBinaryCodec[CustomInstant] = schema.derive(JsonFormat.deriver)
}

case class Event(
  id: String,
  timestamp: CustomInstant,
  data: String
)

object Event extends CompanionOptics[Event] {
  implicit val schema: Schema[Event] = Schema.derived
  
  val codec: JsonBinaryCodec[Event] = schema
    .deriving(JsonFormat.deriver)
    .instance($(_.timestamp), CustomInstant.customCodec)
    .derive
}
```

## Supported Formats

| Format | Module | Description |
|--------|--------|-------------|
| **JSON** | `zio-blocks-schema` | Fast, type-safe JSON (included in core) |
| **Avro** | `zio-blocks-schema-avro` | Apache Avro binary format |
| **TOON** | `zio-blocks-schema-toon` | Compact, LLM-optimized text format |
| **MessagePack** | `zio-blocks-schema-messagepack` | Efficient binary serialization |
| **BSON** | `zio-blocks-schema-bson` | MongoDB's binary JSON format |
| **Thrift** | `zio-blocks-schema-thrift` | Apache Thrift |
| **Protobuf** | `zio-blocks-schema-protobuf` | Protocol Buffers (planned) |

## See Also

- [Schema](./schema.md) - Core schema functionality
- [JSON](./json.md) - JSON-specific features and the `Json` ADT
- [Reflect](./reflect.md) - Understanding schema structure
- [Binding](./binding.md) - Construction and deconstruction
