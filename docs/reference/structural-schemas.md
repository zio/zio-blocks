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
| `Schema.derived[StructuralType]` | Derive schemas directly for structural types |
| Normalized type names | Deterministic, comparable type identifiers |

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

:::note JVM Requirement
The `.structural` method requires JVM because it uses reflection to create the structural type at runtime. For cross-platform code, derive schemas for Selectable/Dynamic types directly instead.
:::

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

You can derive schemas directly for structural types without starting from a nominal type.

### Scala 3: Selectable-Based Types (Cross-Platform)

For cross-platform support, define a base class extending `Selectable`:

```scala
import scala.Selectable

case class Record(fields: Map[String, Any]) extends Selectable {
  def selectDynamic(name: String): Any = fields(name)
}

// Define structural types as refinements
type PersonLike = Record { def name: String; def age: Int }
type PointLike = Record { def x: Int; def y: Int }

// Derive schema directly
val personSchema = Schema.derived[PersonLike]
val pointSchema = Schema.derived[PointLike]
```

**Requirements for Selectable types:**
1. Base class must extend `Selectable`
2. Must have either:
   - A constructor taking `Map[String, Any]`, or
   - A companion `apply(Map[String, Any]): T` method

### Scala 2: Dynamic-Based Types (Cross-Platform)

For Scala 2 cross-platform support, use `scala.Dynamic`:

```scala
import scala.language.dynamics

class DynamicRecord(val fields: Map[String, Any]) extends Dynamic {
  def selectDynamic(name: String): Any = fields(name)
}

object DynamicRecord {
  def apply(map: Map[String, Any]): DynamicRecord = new DynamicRecord(map)
}

// Define structural types
type PersonLike = DynamicRecord { def name: String; def age: Int }

// Derive schema
val schema = Schema.derived[PersonLike]
```

### Pure Structural Types (Scala 2: All Platforms, Scala 3: JVM Only)

In Scala 2, you can derive schemas for pure structural types on **all platforms** because the macro generates an anonymous Dynamic class at compile time:

```scala
// Scala 2 - Works on JVM, JS, and Native!
type PersonLike = { def name: String; def age: Int }
val schema = Schema.derived[PersonLike]

// The macro generates an anonymous Dynamic class, no runtime reflection needed
```

In Scala 3, pure structural types without a `Selectable` base only work on JVM:

```scala
// Scala 3 JVM only - uses reflection
type PointLike = { def x: Int; def y: Int }
val schema = Schema.derived[PointLike]  // Only works on JVM
```

:::caution Platform Restriction
In Scala 3, pure structural types (without `Selectable`) require reflection and only work on the JVM. Attempting to derive a schema for a pure structural type on JS or Native will result in a compile-time error.

In Scala 2, pure structural types work on all platforms because the macro generates Dynamic classes at compile time.
:::

:::note Converting Nominal to Structural
The `.structural` method on `Schema` (for converting `Schema[CaseClass]` to its structural equivalent) requires JVM on **both** Scala 2 and Scala 3 because it uses reflection to create the structural type.
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

In Scala 2, sealed traits cannot be converted to structural types because Scala 2 lacks union types. Attempting to call `.structural` on a sealed trait schema will produce a compile-time error:

```scala
// Scala 2
sealed trait Status
case object Active extends Status
case object Inactive extends Status

val schema = Schema.derived[Status]  // ✅ Works
val structural = schema.structural   // ❌ Compile error
```

**Error message:**
```
Cannot convert sum type 'Status' to structural type.

Sum types (sealed traits, enums) cannot be represented as structural types in Scala 2
because Scala 2 does not support union types.

Consider:
  - Using a case class wrapper instead of a sealed trait
  - Using the nominal schema directly
  - Upgrading to Scala 3 for union type support
```

## Platform Compatibility

| Feature | Scala 2 JVM | Scala 2 JS/Native | Scala 3 JVM | Scala 3 JS/Native |
|---------|-------------|-------------------|-------------|-------------------|
| Product `.structural` | ✅ | ❌ (needs reflection) | ✅ | ❌ (needs reflection) |
| Tuple `.structural` | ✅ | ❌ (needs reflection) | ✅ | ❌ (needs reflection) |
| Nested Products | ✅ | ✅ | ✅ | ✅ |
| Large Products (>22 fields) | ✅ | ✅ | ✅ | ✅ |
| Sealed Trait `.structural` | ❌ (no unions) | ❌ (no unions) | ✅ | ❌ (needs reflection) |
| Enum `.structural` | N/A | N/A | ✅ | ❌ (needs reflection) |
| Pure Structural Derivation | ✅ | ✅ (Dynamic) | ✅ (JVM only) | ❌ |
| Dynamic-based Derivation | ✅ | ✅ | N/A | N/A |
| Selectable-based Derivation | N/A | N/A | ✅ | ✅ |

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

// Define Selectable base for cross-platform support
case class Record(fields: Map[String, Any]) extends Selectable {
  def selectDynamic(name: String): Any = fields(name)
}

type PersonLike = Record { def name: String; def age: Int }

case class Person(name: String, age: Int)

// Convert between nominal and structural
val personToStructural = Into.derived[Person, PersonLike]
val structuralToPerson = Into.derived[PersonLike, Person]

// Bidirectional conversion
val as = As.derived[Person, PersonLike]
```

See [Schema Evolution](schema-evolution.md) for complete documentation on `Into` and `As`.

## Complete Example

Here's a complete example demonstrating structural schema features:

```scala
import zio.blocks.schema._
import scala.Selectable

// === Define a cross-platform Selectable base ===
case class Record(fields: Map[String, Any]) extends Selectable {
  def selectDynamic(name: String): Any = fields(name)
}

// === Define structural types as refinements ===
type PersonLike = Record { def name: String; def age: Int }
type AddressLike = Record { def street: String; def city: String }

// === Derive schemas for structural types ===
val personSchema = Schema.derived[PersonLike]
val addressSchema = Schema.derived[AddressLike]

// === Create instances using the Record constructor ===
def makePerson(name: String, age: Int): PersonLike =
  Record(Map("name" -> name, "age" -> age)).asInstanceOf[PersonLike]

val alice = makePerson("Alice", 30)

// Access fields via structural type refinement
println(alice.name)  // "Alice"
println(alice.age)   // 30

// === Convert to/from DynamicValue ===
val dynamic = personSchema.toDynamicValue(alice)
// DynamicValue.Record(Vector("name" -> Primitive(String("Alice")), "age" -> Primitive(Int(30))))

val restored = personSchema.fromDynamicValue(dynamic)
// Right(Record(Map("name" -> "Alice", "age" -> 30)))

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
