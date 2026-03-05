---
id: structural-types
title: "Structural Types"
---

Structural types enable **duck typing** with ZIO Blocks schemas. Instead of requiring a nominal type name (like `class Person`), a structural schema validates based on the **shape** of an object — the fields it provides, regardless of how it was defined.

## Overview

The `ToStructural[A]` type class converts any `Schema[A]` (for a nominal type) into a schema for the corresponding structural type. This is useful for:

- **Cross-system interop** — Convert between types with the same shape but different names
- **Anonymous objects** — Work with objects defined inline using `new { def ... }`
- **Schema evolution** — Validate only the fields you care about
- **Duck typing** — Accept any object "shaped like" the expected type

### Type Signature

The `ToStructural` type class converts a nominal `Schema[A]` into a schema for the corresponding structural type:

```scala mdoc:compile-only
trait ToStructural[A] {
  type StructuralType
  def apply(schema: Schema[A]): Schema[StructuralType]
}
```

Key characteristics:

- **JVM-only** — Structural types require reflection, only available on the JVM
- **Compile-time macro derivation** — The structural schema is generated at compile time
- **Dependent type** — `StructuralType` is a path-dependent type capturing the exact shape
- **Supports products, tuples, and sum types** — Case classes, sealed traits, enums

## Motivation

Consider a common integration scenario:

```scala
// Your system
case class Person(name: String, age: Int)

// External system (same data, different class)
case class User(name: String, age: Int)
```

Without structural types, converting between `Person` and `User` requires manual translation. With structural types, they both have the same structural schema:

```scala mdoc:compile-only
import scala.language.reflectiveCalls
import zio.blocks.schema.Schema

case class Person(name: String, age: Int)
case class User(name: String, age: Int)

val personSchema = Schema.derived[Person]
val personStructural = personSchema.structural
// Schema[{ def name: String; def age: Int }]

val userSchema = Schema.derived[User]
val userStructural = userSchema.structural
// Schema[{ def name: String; def age: Int }]

// Both schemas accept the same data shape
```

## Construction: `.structural` on a Schema

Call `.structural` on any `Schema[A]` to get the corresponding structural schema.

**Scala 3:** Using transparent inline — the return type is inferred to the full refinement type:

```scala mdoc:compile-only
import zio.blocks.schema.Schema

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]
}

val personSchema: Schema[Person] = Schema.derived[Person]
val structuralSchema: Schema[{ def name: String; def age: Int }] = personSchema.structural
```

**Scala 2:** Implicit derivation — returns `Schema[ts.StructuralType]` (path-dependent type):

```scala mdoc:compile-only
import zio.blocks.schema.Schema

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]
}

val personSchema: Schema[Person] = Schema.derived[Person]
val structuralSchema = personSchema.structural
// Type: Schema[ts.StructuralType] (structural type inferred from macro)
```

## Supported Conversions

### Product Types (Case Classes)

Both Scala 2 and 3 support structural conversion of case classes:

```scala mdoc:compile-only
import zio.blocks.schema.Schema

case class Address(street: String, city: String, zipCode: Int)
object Address {
  implicit val schema: Schema[Address] = Schema.derived[Address]
}

val schema = Schema.derived[Address]
val structural = schema.structural
// Schema[{ def street: String; def city: String; def zipCode: Int }]
```

### Tuples

Tuples convert to structural records with field names derived from positions:

```scala mdoc:compile-only
import zio.blocks.schema.Schema

type StringIntBool = (String, Int, Boolean)
implicit val schema: Schema[StringIntBool] = Schema.derived[StringIntBool]

val tupleSchema = Schema.derived[(String, Int, Boolean)]
val structuralSchema = tupleSchema.structural
// Schema[{ def _1: String; def _2: Int; def _3: Boolean }]
```

### Nested Products

Field types are recursively structuralized:

```scala mdoc:compile-only
import zio.blocks.schema.Schema

case class Address(street: String, city: String)
object Address {
  implicit val schema: Schema[Address] = Schema.derived[Address]
}

case class Person(name: String, age: Int, address: Address)
object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]
}

val personSchema = Schema.derived[Person]
val structuralSchema = personSchema.structural
// Schema[{
//   def name: String
//   def age: Int
//   def address: Address
// }]
```

### Opaque Types (Scala 3)

Opaque type aliases are unwrapped to their underlying type:

```scala mdoc:compile-only
import zio.blocks.schema.Schema

opaque type UserId = String

case class User(id: UserId, name: String)
object User {
  implicit val schema: Schema[User] = Schema.derived[User]
}

val schema = Schema.derived[User]
val structural = schema.structural
// Schema[{ def id: String; def name: String }]
// (UserId unwrapped to String)
```

### Sum Types / Sealed Traits (Scala 3)

Sealed traits and enums convert to union types with nested method syntax:

```scala mdoc:compile-only
import zio.blocks.schema.Schema

sealed trait Shape
object Shape {
  case class Circle(radius: Double) extends Shape
  case class Rectangle(width: Double, height: Double) extends Shape
  implicit val schema: Schema[Shape] = Schema.derived[Shape]
}

val schema = Schema.derived[Shape]
val structural = schema.structural
// Schema[
//   { def Circle: { def radius: Double } } |
//   { def Rectangle: { def height: Double; def width: Double } }
// ]
// (cases sorted alphabetically)
```

**Enum syntax** (Scala 3):

```scala mdoc:compile-only
import zio.blocks.schema.Schema

enum Color {
  case Red, Green, Blue
}
object Color {
  implicit val schema: Schema[Color] = Schema.derived[Color]
}

val schema = Schema.derived[Color]
val structural = schema.structural
// Schema[
//   { def Blue: {} } |
//   { def Green: {} } |
//   { def Red: {} }
// ]
```

Cases appear in **alphabetical order** in the union type. This alphabetical ordering (applied to fields in products and case names in unions) ensures **deterministic, normalized type identity**: two structural types with the same fields but different declaration order produce the same structural type and normalized name. This is essential for predictable schema evolution and cross-system interop.

## Direct Structural Derivation (Scala 3)

Create a schema directly for a structural type without a nominal base:

```scala mdoc:compile-only
import zio.blocks.schema.Schema

// No case class needed — define the schema for the shape directly
val personStructural = Schema.derived[{ def name: String; def age: Int }]

// The schema is ready to use with values matching that structural shape
```

This is only supported in **Scala 3** with the right macro machinery.

## Working with Structural Schemas

### Round-tripping Through DynamicValue

Structural schemas enable **cross-type conversion through `DynamicValue`** — encode a value of one nominal type and decode it as a *different* nominal type with the same structural shape. This is the core benefit of structural types for system integration.

#### Motivation

In real integrations, you often receive data from an external system shaped like one type, but you need to work with it as a different type in your system. Without structural types, field-by-field translation is required. With structural types, if both types have identical shape, `DynamicValue` acts as the seamless bridge.

Common scenarios:
- **API gateways** — receive a `PersonDTO` from an external API, decode as your internal `Person` type
- **Message brokers** — consume an event shaped like `UserEvent`, convert to your domain `Account` type
- **Data pipelines** — records with identical fields but different class names from different services

#### When to use

| Scenario | Use this | Don't use |
|----------|----------|-----------|
| Two nominal types have identical shape; convert between them | Encode with one schema, decode with the other | Manual translation |
| Convert same type to/from DynamicValue | Use nominal schema directly | Structural schema (unnecessary) |

#### Cross-type conversion in action

Set up two types with identical structural shape:

```scala mdoc:silent
import zio.blocks.schema.Schema
import zio.blocks.schema.SchemaError

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]
}

case class Employee(name: String, age: Int)
object Employee {
  implicit val schema: Schema[Employee] = Schema.derived[Employee]
}

val personSchema = Schema.derived[Person]
val employeeSchema = Schema.derived[Employee]
```

Now encode a `Person` to `DynamicValue` and decode it as an `Employee`:

```scala mdoc
val person = Person("Alice", 30)
val dynamic = personSchema.toDynamicValue(person)

val employee: Either[SchemaError, Employee] =
  employeeSchema.fromDynamicValue(dynamic)
```

The structural shape guarantee ensures type-safe conversion: at compile time, you know both schemas accept the same fields, so round-tripping through `DynamicValue` is safe and zero-cost.

### Accessing Structural Values

Accessing fields on a structural type requires importing `scala.language.reflectiveCalls` or using Scala 3's `Selectable` extension methods:

In Scala 3, structural types work through the `Selectable` trait, which provides safe reflective access to fields. You can access fields on objects with the matching structure directly:

```scala
// After decoding from DynamicValue, you have an object with the structural shape
val person: { def name: String; def age: Int } = ???
val name = person.name  // Safe reflective access in Scala 3
```

For more details, see the [Scala documentation on structural types](https://docs.scala-lang.org/scala3/reference/other-new-features/structural-types.html).

## Compile-Time Errors and Limitations

### JVM Only

Structural types require reflection and are only available on the JVM:

```
error: Structural types require reflection which is only available on JVM.
```

This applies to all platforms outside JVM (JS, Native). The implementation uses `Platform.supportsReflection` to detect and reject structural types at compile time on non-JVM platforms.

### Recursive Types

Cannot derive structural schemas for recursive types:

```
error: Cannot generate structural type for recursive type X.
```

Example:

```scala mdoc:compile-only
import zio.blocks.schema.Schema

case class LinkedList(value: Int, next: Option[LinkedList])

// This fails at compile time: LinkedList refers to itself
val schema = Schema.derived[LinkedList]
val structural = schema.structural
// error: Cannot generate structural type for recursive type LinkedList
```

### Mutually Recursive Types

Types that are mutually recursive also fail at compile time.

### Scala 2 Sum Type Limitation

In **Scala 2**, only product types (case classes) and tuples are supported:

```
error: Only product types (case classes) and tuples are currently supported.
Sealed traits and enums require Scala 3.
```

Additionally, **Scala 2** returns `Schema[ts.StructuralType]` (path-dependent type), not the fully-refined union type visible in Scala 3.

### Scala 2 Deeply Nested Types Limitation

In **Scala 2**, accessing fields on deeply nested structural types requires explicit casting. While nested structures are supported, reflection limitations prevent field chaining:

```scala mdoc:compile-only
import scala.language.reflectiveCalls
import zio.blocks.schema.Schema

case class Address(street: String)
case class Company(name: String, address: Address)
case class Employee(name: String, company: Company)

val schema = Schema.derived[Employee]
val structural = schema.structural

// Create a structural value
val employee: { def name: String; def company: { def address: { def street: String } } } = ???

// Direct field access — this works
employee.name

// First level of nesting — this works
employee.company

// Cannot chain field access to nested structural types
// employee.company.address.street

// Workaround — cast to StructuralRecord
val companyStruct = employee.company.asInstanceOf[StructuralRecord]
companyStruct.selectDynamic("address")  // returns the Address structural object
```

This limitation does **not** apply to Scala 3, where `Selectable` provides seamless field access across arbitrary nesting depth.

## Integration

### With Schema Evolution Macros

Structural schemas work with [Schema Evolution](./schema-evolution/into.md) macros for cross-type conversion. When two types share the same structural shape, the conversion machinery can work across type boundaries:

```scala mdoc:compile-only
import zio.blocks.schema.Schema

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]
}

case class PersonDTO(name: String, age: Int)
object PersonDTO {
  implicit val schema: Schema[PersonDTO] = Schema.derived[PersonDTO]
}

// Both types have identical structural schemas
val personSchema = Schema.derived[Person]
val dtoSchema = Schema.derived[PersonDTO]

// They share the same structural shape:
// Schema[{ def name: String; def age: Int }]
```

### With Binding.of (Serialization)

Structural types are also supported by the `Binding.of` macro for high-performance serialization via register-based encoding:

```scala mdoc:compile-only
import zio.blocks.schema.binding.Binding

// Direct structural type serialization (JVM only)
val binding = Binding.of[{ def name: String; def age: Int }]

// Works with nested structural types
val nestedBinding = Binding.of[{
  def name: String
  def address: { def street: String; def city: String }
}]

// Works with containers
val containerBinding = Binding.of[{
  def name: String
  def emails: List[String]
}]
```

This enables anonymous structural types to benefit from ZIO Blocks' high-performance serialization without requiring nominal case class definitions. Like `Schema.structural`, this is **JVM-only**.

See [Binding](./binding.md) for detailed serialization documentation.

### With DynamicValue

Structural schemas integrate with `DynamicValue` for dynamic value manipulation:

```scala mdoc:compile-only
import zio.blocks.schema.Schema
import zio.blocks.schema.DynamicValue
import zio.blocks.schema.DynamicOptic

case class Config(host: String, port: Int, debug: Boolean)

val schema = Schema.derived[Config]

val config = Config("localhost", 8080, true)
val dynamic = schema.toDynamicValue(config)

// Modify using DynamicOptic
val updated = dynamic.set(
  DynamicOptic.root.field("port"),
  DynamicValue.int(9000)
)

// Reconstruct
val reconstructed = schema.fromDynamicValue(updated)
```

See [DynamicValue](./dynamic-value.md) for path-based manipulation.

## Running the Examples

Example applications demonstrating structural types are available in `schema-examples`:

```sh
# Simple product type
sbt "schema-examples/runMain structural.StructuralSimpleProductExample"

# Nested products
sbt "schema-examples/runMain structural.StructuralNestedProductExample"

# Sealed trait (Scala 3)
sbt "schema-examples/runMain structural.StructuralSealedTraitExample"

# Enum (Scala 3)
sbt "schema-examples/runMain structural.StructuralEnumExample"

# Tuples
sbt "schema-examples/runMain structural.StructuralTupleExample"

# Integration with Into macro
sbt "schema-examples/runMain structural.StructuralIntoExample"
```

## See Also

- [Schema](./schema.md) — Core schema API
- [DynamicValue](./dynamic-value.md) — Schema-less value representation
- [Schema Evolution](./schema-evolution/into.md) — Cross-type conversion
