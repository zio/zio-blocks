---
id: structural-schemas
title: "Structural Schemas"
sidebar_label: "Structural Schemas"
---

Structural schemas allow you to work with types defined by their structure rather than their nominal identity. This enables interoperability between compatible types, schema evolution without type coupling, and working with anonymous or refinement types.

## Overview

A **structural type** is defined by its members (fields, methods) rather than its class name:

```scala
// Nominal type - identified by name "Person"
case class Person(name: String, age: Int)

// Structural type - identified by its structure
type PersonLike = { def name: String; def age: Int }
```

ZIO Blocks extends `Schema[A]` to support structural types through:

| Feature | Description |
|---------|-------------|
| `.structural` | Convert any product schema to its structural equivalent |
| `Schema.derived[StructuralType]` | Derive schemas directly for structural types (JVM only) |
| Normalized type names | Deterministic, comparable type identifiers |

:::caution JVM Only
Structural type support requires reflection and is only available on the JVM. Attempting to use structural types on Scala.js or Scala Native will result in a compile-time error.
:::

## Converting to Structural Schemas

Any schema for a product type (case class, tuple) can be converted to its structural representation:

```scala
import zio.blocks.schema._

case class Person(name: String, age: Int)

val nominalSchema = Schema.derived[Person]
val structuralSchema = nominalSchema.structural

// Type: Schema[{ def age: Int; def name: String }]
// Note: fields are alphabetically ordered in the structural type
```

### Type Name Normalization

Structural type names are normalized for consistent comparison:

```scala
case class User(name: String, age: Int)
case class Person(name: String, age: Int)

// Both produce the same structural type name
Schema.derived[User].structural.reflect.typeName.name
// => "{age:Int,name:String}"

Schema.derived[Person].structural.reflect.typeName.name
// => "{age:Int,name:String}"
```

Rules for type name normalization:
- Fields are sorted alphabetically by name
- Format: `{field1:Type1,field2:Type2,...}`
- No whitespace
- Primitive types use simple names (`Int`, `String`, `Boolean`)

### Supported Product Types

| Type | Supported | Example |
|------|-----------|---------|
| Case classes | ✅ | `case class Person(name: String)` |
| Nested case classes | ✅ | `case class Outer(inner: Inner)` |
| Large products (>22 fields) | ✅ | Case classes with 25, 30, or more fields |
| Tuples | ✅ | `(String, Int, Boolean)` |
| Case objects | ✅ | `case object Empty` |

### Collections and Wrappers

Structural conversion preserves collection and wrapper types:

```scala
case class Container(
  items: List[String],
  metadata: Option[Int],
  lookup: Map[String, Int],
  result: Either[String, Int]
)

val structural = Schema.derived[Container].structural
// Type: Schema[{
//   def items: List[String];
//   def lookup: Map[String, Int];
//   def metadata: Option[Int];
//   def result: Either[String, Int]
// }]
```

## Deriving Schemas for Structural Types

You can derive schemas directly for structural types. This feature requires JVM because it uses reflection for deconstruction.

```scala
// JVM only - uses reflection
type PersonLike = { def name: String; def age: Int }
val schema = Schema.derived[PersonLike]
```

:::caution Platform Restriction
Structural type derivation requires reflection and only works on the JVM. Attempting to derive a schema for a structural type on JS or Native will result in a compile-time error:

```
Cannot derive Schema for structural type '...' on JS/Native.

Structural types require reflection which is only available on JVM.

Consider using a case class instead.
```
:::

## Sum Types and Union Types

### Scala 3: Sealed Traits and Enums

Sealed traits and enums are fully supported for schema derivation:

```scala
sealed trait Result
case class Success(value: Int) extends Result
case class Failure(error: String) extends Result

val schema = Schema.derived[Result]
// Schema is a Variant with cases "Success" and "Failure"

// Round-trip through DynamicValue
val value: Result = Success(42)
val dynamic = schema.toDynamicValue(value)
// DynamicValue.Variant("Success", DynamicValue.Record(...))

val restored = schema.fromDynamicValue(dynamic)
// Right(Success(42))
```

Enums work the same way:

```scala
enum Status {
  case Active
  case Inactive
  case Pending(reason: String)
}

val schema = Schema.derived[Status]
// Variant with cases "Active", "Inactive", "Pending"
```

### Scala 2: Sum Type Limitation

In Scala 2, sealed traits cannot be converted to structural types because Scala 2 lacks union types. Attempting to call `.structural` on a sealed trait schema will produce a compile-time error.

## Platform Compatibility

| Feature | Scala 2 JVM | Scala 2 JS/Native | Scala 3 JVM | Scala 3 JS/Native |
|---------|-------------|-------------------|-------------|-------------------|
| Product `.structural` | ✅ | ❌ (needs reflection) | ✅ | ❌ (needs reflection) |
| Tuple `.structural` | ✅ | ❌ (needs reflection) | ✅ | ❌ (needs reflection) |
| Nested Products | ✅ | ✅ | ✅ | ✅ |
| Large Products (>22 fields) | ✅ | ✅ | ✅ | ✅ |
| Sealed Trait `.structural` | ❌ (no unions) | ❌ (no unions) | ✅ | ❌ (needs reflection) |
| Enum `.structural` | N/A | N/A | ✅ | ❌ (needs reflection) |
| Pure Structural Derivation | ✅ | ❌ (needs reflection) | ✅ | ❌ (needs reflection) |

**Key:**
- **`.structural`** = Converting `Schema[NominalType]` to `Schema[StructuralType]`
- **Derivation** = `Schema.derived[StructuralType]` for structural types directly
- ✅ = Supported
- ❌ = Not supported (compile-time error with helpful message)
- N/A = Not applicable to this Scala version

## Recursive Type Detection

Structural schemas cannot represent recursive types. The macro will detect and reject:

### Direct Recursion

```scala
case class Tree(value: Int, children: List[Tree])

val schema = Schema.derived[Tree]
schema.structural  // ❌ Compile error
```

**Error message:**
```
Cannot convert recursive type 'Tree' to structural type.

The type 'Tree' contains a recursive reference through field 'children'.

Recursive types require nominal identity for proper representation.
Consider using the nominal schema directly.
```

### Mutual Recursion

```scala
case class Node(edges: List[Edge])
case class Edge(target: Node)

Schema.derived[Node].structural  // ❌ Compile error
```

**Error message:**
```
Cannot convert mutually recursive types to structural type.

The type 'Node' is involved in a recursive cycle:
  Node -> Edge -> Node

Mutually recursive types require nominal identity for proper representation.
```

## Relationship with Into/As

Structural schemas work seamlessly with the `Into` and `As` type classes for schema evolution:

```scala
import zio.blocks.schema._

// JVM only
type PersonLike = { def name: String; def age: Int }

case class Person(name: String, age: Int)

// Convert between nominal and structural (JVM only)
val personToStructural = Into.derived[Person, PersonLike]
val structuralToPerson = Into.derived[PersonLike, Person]

// Bidirectional conversion
val as = As.derived[Person, PersonLike]
```

See [Schema Evolution](schema-evolution.md) for complete documentation on `Into` and `As`.

## Complete Example

Here's a complete example demonstrating structural schema features (JVM only):

```scala
import zio.blocks.schema._

// === Define structural types ===
type PersonLike = { def name: String; def age: Int }
type AddressLike = { def street: String; def city: String }

// === Derive schemas for structural types (JVM only) ===
val personSchema = Schema.derived[PersonLike]
val addressSchema = Schema.derived[AddressLike]


// === Check structural type name ===
personSchema.reflect.typeName.name
// "{age:Int,name:String}"

// === Convert nominal schema to structural ===
case class User(name: String, email: String)

val userSchema = Schema.derived[User]
val structuralUserSchema = userSchema.structural
// Schema[{ def email: String; def name: String }]

structuralUserSchema.reflect.typeName.name
// "{email:String,name:String}"

// === Structural compatibility ===
case class Person(name: String, age: Int)
case class Employee(age: Int, name: String)

val personStructural = Schema.derived[Person].structural
val employeeStructural = Schema.derived[Employee].structural

// Same structure produces same type name (fields sorted alphabetically)
assert(personStructural.reflect.typeName.name == employeeStructural.reflect.typeName.name)
// Both are "{age:Int,name:String}"
```
