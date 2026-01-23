---
id: typeid
title: "TypeId"
---

# TypeId

TypeId provides compile-time type identity for Scala types, enabling type registry operations, subtype checking, and type equality without runtime reflection.

## Overview

`TypeId[A]` captures complete type information at compile time, including:
- Owner path (package, objects, classes)
- Type name and type parameters
- Parent types and variance information
- Support for type aliases, opaque types, and compound types

## Basic Usage

```scala
import zio.blocks.typeid._

// Derive TypeId for any type
val stringId: TypeId[String] = TypeId.of[String]
val listIntId: TypeId[List[Int]] = TypeId.of[List[Int]]

// Use as Map keys in type registries
val registry: Map[TypeId[_], String] = Map(
  TypeId.of[String] -> "string handler",
  TypeId.of[Int] -> "int handler"
)
```

## Type Operations

### Subtype Checking

```scala
// Check subtype relationships
TypeId.of[Nothing].isSubtypeOf(TypeId.of[Any])     // true
TypeId.of[List[Int]].isSubtypeOf(TypeId.of[Seq[Int]]) // true

// Variance-aware checking
TypeId.of[List[String]].isSubtypeOf(TypeId.of[List[Any]]) // true (covariant)
```

### Type Equality

TypeId implements proper `equals` and `hashCode` for use as Map/Set keys:

```scala
TypeId.of[String] == TypeId.of[String]  // true
TypeId.of[List[Int]] == TypeId.of[List[String]]  // false

// Type aliases are transparent
type Age = Int
TypeId.of[Age] == TypeId.of[Int]  // true
```

### Type Metadata

```scala
val id = TypeId.of[String]
id.fullName  // "java.lang.String" - fully qualified name
id.name      // "String"
id.arity     // number of type parameters

// Type classification helpers
id.isClass        // true for classes
id.isTrait        // true for traits
id.isObject       // true for singleton objects
id.isEnum         // true for Scala 3 enums
id.isAlias        // true for type aliases
id.isOpaque       // true for opaque types
id.isSealed       // true for sealed traits
id.isCaseClass    // true for case classes
id.isValueClass   // true for value classes
```

## Compound Types

TypeId supports intersection and union types with canonicalization:

```scala
// Intersection types (order-independent)
TypeId.of[Serializable with Runnable] == TypeId.of[Runnable with Serializable]  // true

// Union types (Scala 3)
TypeId.of[Int | String]  // captures both components
```

## Integration with Schema

TypeId is used internally by ZIO Blocks Schema to identify types in:
- Type registries for codec derivation
- Schema evolution and migration
- Dynamic value handling

```scala
import zio.blocks.schema._

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Schema captures TypeId automatically
val typeId = Schema[Person].reflect.typeId
```
