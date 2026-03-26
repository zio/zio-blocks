---
id: typeid
title: "TypeId"
---

`TypeId[A]` represents the identity of a type or type constructor at runtime — it captures complete type metadata (names, type parameters, parent types, annotations, classification) that would otherwise be erased by the JVM and Scala.js. Use `TypeId` when you need to preserve full type information as data for serialization, code generation, registry lookups, or type-safe dispatching.

In Scala and the JVM, compile-time type information is erased at runtime. This means generic type parameters, sealed trait variants, and even opaque types become indistinguishable at runtime — `List[Int]` and `List[String]` both look like `List` to the JVM. This erasure makes it nearly impossible to implement universal serializers that work across formats (JSON, YAML, XML, MessagePack), code generators, or schema-driven transformations without losing semantic information. `TypeId` solves this by capturing complete type structure at compile time and making it available as a hashable, inspectable value at runtime.

The `TypeId` trait exposes the type's structure through a rich set of properties and predicates:

```scala
// Simplified — some members shown here are derived from abstract members
sealed trait TypeId[A <: AnyKind] {
  // Abstract members
  def name: String
  def owner: Owner
  def typeParams: List[TypeParam]
  def typeArgs: List[TypeRepr]
  def defKind: TypeDefKind
  def selfType: Option[TypeRepr]       // Self-type annotation, if any
  def aliasedTo: Option[TypeRepr]      // Target type for type aliases
  def representation: Option[TypeRepr] // Underlying type for opaque types
  def annotations: List[Annotation]

  // Derived properties
  final def fullName: String     // owner.asString + "." + name
  final def arity: Int           // typeParams.size
  final def isCaseClass: Boolean
  final def isSealed: Boolean
  final def isAlias: Boolean
  // ... many more derived predicates
}
```

> In Scala 3, `A` is bounded by `AnyKind` to support higher-kinded types. In Scala 2, the bound is omitted (`sealed trait TypeId[A]`).

Derive a `TypeId` for any type using the `TypeId.of` macro and then inspect the type's structure at runtime:

```scala mdoc:silent:reset
import zio.blocks.typeid._

case class Person(name: String, age: Int)

val id = TypeId.of[Person]
```

```scala mdoc
id.name
id.fullName
id.isCaseClass
```

## Motivation

Standard approaches to preserving type information at runtime — `ClassTag`, `TypeTag` (Scala 2), `TypeTest` (Scala 3) — each have limitations. `ClassTag` loses generic type arguments. `TypeTag` depends on `scala-reflect` and is unavailable on Scala.js. `TypeTest` only answers "is this value an instance of T?" without exposing type structure. None of them distinguish opaque types from their underlying representation.

TypeId takes a different approach: the `TypeId.of` macro captures type metadata at compile time and stores it as a plain, immutable data structure — no runtime reflection, no platform-specific APIs. This makes it suitable as a foundation for cross-platform schema systems, code generators, and type-indexed registries.

## Installation

TypeId is included in the `zio-blocks-typeid` module. Add it to your build:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-typeid" % "@VERSION"
```

For cross-platform (Scala.js):

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-typeid" % "@VERSION"
```

Supported Scala versions: 2.13.x and 3.x.

## Creating Instances

There are two approaches to creating `TypeId` values: **automatic derivation** (recommended for normal use) and **manual construction** (for advanced metaprogramming scenarios).

### Automatic Derivation

For most users and most types, automatic derivation via `TypeId.of` or implicit `derived` is the right choice. These macros extract complete type metadata at compile time, handling all type variants correctly.

#### `TypeId.of` — Macro Derivation

The primary way to obtain a `TypeId` is through the `TypeId.of[A]` macro, which extracts complete type metadata at compile time:

```scala
object TypeId {
  inline def of[A <: AnyKind]: TypeId[A]  // Scala 3
  def of[A]: TypeId[A]                     // Scala 2 (macro)
}
```

Derive a TypeId using the macro:

```scala mdoc:silent:reset
import zio.blocks.typeid._

case class User(id: Long, email: String)
```

```scala mdoc
val userId = TypeId.of[User]
userId.name
userId.fullName
userId.isCaseClass
```

#### `TypeId.derived` — Implicit Derivation

TypeId instances are available implicitly through the `derived` macro. Any function that requires a `TypeId[A]` in implicit scope will have it derived automatically — you never need to pass it manually.

From the user's perspective, the API is:

```scala
object TypeId {
  inline given derived[A <: AnyKind]: TypeId[A]  // Scala 3
  implicit def derived[A]: TypeId[A]              // Scala 2 (macro)
}
```

:::note
In Scala 3, the `[A <: AnyKind]` bound allows derivation for type constructors (e.g., `TypeId[List]`). In Scala 2, the bound is `[A]` and type constructor derivation uses `TypeId[List[_]]` syntax instead.
:::

The most common use case is accepting `TypeId[A]` as an implicit parameter:

```scala mdoc:silent:reset
import zio.blocks.typeid._

case class User(id: Long, email: String)

def describe[A](implicit typeId: TypeId[A]): String =
  s"${typeId.fullName} is a case class: ${typeId.isCaseClass}"
```

Call the function with the type argument — the TypeId is derived automatically:

```scala mdoc
describe[User]
describe[Int]
```

You can also summon a TypeId explicitly with `implicitly` (Scala 2) or `summon` (Scala 3):

```scala mdoc
val userTypeId = implicitly[TypeId[User]]
userTypeId.name
```

:::tip
Prefer `TypeId.of[A]` when you need the TypeId in a single expression. Use implicit derivation when you are writing generic functions that accept any `A` and need its TypeId alongside other implicit evidence.
:::

### Manual Derivation (Smart Constructors)

For advanced use cases — unit testing with synthetic metadata, code generators that create types dynamically, or frameworks that construct TypeIds at runtime — the smart constructor functions allow you to manually assemble TypeIds by specifying their components. These are never needed in normal user code, since `TypeId.of` handles all these cases automatically.

#### `TypeId.nominal` — Nominal Types

**Nominal types** are concrete type definitions: classes, traits, and objects. In contrast to [type aliases](#typeidalias--type-aliases) (which are alternative names for existing types) and [opaque types](#typeidopaque--opaque-types) (which have a hidden representation), nominal types stand as distinct, named types in the type system.

For most end users, you don't need to use `TypeId.nominal` directly. The `TypeId.of` macro automatically derives nominal TypeIds from your actual type definitions at compile time. The `nominal` smart constructor exists for **advanced use cases**: unit testing with synthetic type metadata, code generators that create types dynamically at runtime, or frameworks that assemble TypeIds programmatically. Unless you're in one of these scenarios, `TypeId.of` is the right tool.

If you do need to construct nominal TypeIds manually, the API provides two overloads:

```scala
object TypeId {
  def nominal[A <: AnyKind](name: String, owner: Owner, kind: TypeDefKind): TypeId[A]

  def nominal[A <: AnyKind](
    name: String, owner: Owner,
    typeParams: List[TypeParam] = Nil, typeArgs: List[TypeRepr] = Nil,
    defKind: TypeDefKind = TypeDefKind.Unknown,
    selfType: Option[TypeRepr] = None,
    annotations: List[Annotation] = Nil
  ): TypeId[A]
}
```

#### `TypeId.alias` — Type Aliases

**Type aliases** are alternative names for existing types. For example, `type Age = Int` creates an alias for `Int` so code can read `Age` instead of `Int`. TypeIds for type aliases preserve the distinction from their underlying type through the `aliasedTo` property, enabling alias-aware serialization and schema generation.

For normal use, you don't need `TypeId.alias` directly. When you write a type alias in your code (e.g., `type UserId = String`), the `TypeId.of` macro automatically derives the correct TypeId. The `alias` smart constructor is for **advanced use cases**: unit testing with synthetic alias metadata, code generators that create type aliases dynamically at runtime, or frameworks that normalize or transform type aliases during schema processing. Unless you're building one of these, `TypeId.of` is the right tool.

For testing or code generation, construct an alias TypeId:

```scala
object TypeId {
  def alias[A <: AnyKind](
    name: String, owner: Owner,
    typeParams: List[TypeParam] = Nil,
    aliased: TypeRepr,
    typeArgs: List[TypeRepr] = Nil,
    annotations: List[Annotation] = Nil
  ): TypeId[A]
}
```

```scala mdoc:silent:reset
import zio.blocks.typeid._
```

```scala mdoc
val ageId = TypeId.alias[Any]("Age", Owner.fromPackagePath("com.example"), aliased = TypeRepr.Ref(TypeId.int))
ageId.isAlias
ageId.aliasedTo
```

#### `TypeId.opaque` — Opaque Types

**Opaque types** (a Scala 3 feature) are types that have a distinct compile-time identity but a hidden runtime representation. For example, `opaque type UserId = String` creates a type that is distinct from `String` at compile time, but represents `String` at runtime. TypeId preserves this distinction, unlike standard reflection which erases opaque types to their underlying type — a critical capability for type-safe serialization and validation.

For normal use, you don't need `TypeId.opaque` directly. When you define an opaque type in your code, the `TypeId.of` macro automatically derives the correct TypeId with its representation. The `opaque` smart constructor is for **advanced use cases**: unit testing with synthetic opaque type metadata, code generators that create opaque types dynamically, or frameworks that need to construct type metadata for dynamically-discovered opaque types. Unless you're building one of these, `TypeId.of` is the right tool.

For testing or code generation, construct an opaque TypeId:

```scala
object TypeId {
  def opaque[A <: AnyKind](
    name: String, owner: Owner,
    typeParams: List[TypeParam] = Nil,
    representation: TypeRepr,
    typeArgs: List[TypeRepr] = Nil,
    publicBounds: TypeBounds = TypeBounds.Unbounded,
    annotations: List[Annotation] = Nil
  ): TypeId[A]
}
```

#### `TypeId.applied` — Applied Types

**Applied types** are generic types instantiated with type arguments. For example, `List[Int]` is `List` (the type constructor) applied to `Int` (the type argument), and `Map[String, Int]` is `Map` applied to two type arguments. TypeIds for applied types preserve the type arguments so serializers can generate specialized codecs, validators can type-check values, and code generators can emit correct code.

For normal use, you don't need `TypeId.applied` directly. When you write an applied type in your code (e.g., `List[Int]` or `Map[String, User]`), the `TypeId.of` macro automatically derives the correct TypeId with its type arguments preserved. The `applied` smart constructor is for **advanced use cases**: unit testing with synthetic applied type metadata, code generators that construct type expressions dynamically, or frameworks that need to build type metadata at runtime for dynamically-discovered generic types. Unless you're building one of these, `TypeId.of` is the right tool.

For testing or code generation, construct applied TypeIds by combining a type constructor with type argument expressions:

```scala
object TypeId {
  def applied[A <: AnyKind](typeConstructor: TypeId[?], args: TypeRepr*): TypeId[A]
}
```

## Core Operations

This section documents all public methods on `TypeId` and its companion object, organized by category.

### Identity and Naming

These methods provide the type's name and fully qualified path.

#### `name` — Simple Type Name

Returns the unqualified name of the type:

```scala
sealed trait TypeId[A <: AnyKind] {
  def name: String
}
```

```scala mdoc:silent:reset
import zio.blocks.typeid._

case class Order(id: String, total: Double)
val orderId = TypeId.of[Order]
```

```scala mdoc
orderId.name
TypeId.int.name
TypeId.list.name
```

#### `fullName` — Fully Qualified Name

Returns `owner.asString + "." + name`, or just `name` if the owner is root:

```scala
sealed trait TypeId[A <: AnyKind] {
  def fullName: String
}
```

```scala mdoc
orderId.fullName
TypeId.int.fullName
TypeId.string.fullName
```

#### `owner` — Enclosing Namespace

The `TypeId#owner` method returns the `Owner` — the hierarchical path showing exactly where a type is defined. This includes the complete package chain and any enclosing objects or types. `Owner` solves a critical problem: multiple types can have the same name (e.g., `User` in `com.api` and `User` in `com.admin`), and the owner uniquely distinguishes them by their definition location:

```scala
sealed trait TypeId[A <: AnyKind] {
  def owner: Owner
}
```

When we derive a `TypeId` for a custom type, the owner captures its full hierarchical location. We can then use `TypeId#fullName` to see how the owner combines with the type name:

```scala mdoc:silent:reset
import zio.blocks.typeid._

case class User(id: Long, name: String)
```

```scala mdoc
val userId = TypeId.of[User]
userId.name
// The owner shows where this User is defined
userId.owner.asString
// fullName combines owner and name into a qualified path
userId.fullName
```

When we construct types from different packages, their owners differ even though the names are identical. This is essential for registries and serializers that need to distinguish between types with conflicting names:

```scala mdoc
// A User from the admin domain
val adminUser = TypeId.nominal[Any]("User", Owner.fromPackagePath("com.admin"), TypeDefKind.Unknown)
adminUser.name
// Notice the owner is different
adminUser.owner.asString
// So the full names are distinct
adminUser.fullName

// Compare: both have name "User" but different owners
userId.name == adminUser.name
userId.owner.asString == adminUser.owner.asString
```

This distinction enables type-indexed registries where you can safely store types with identical names from different sources without collision. 

#### `toString` — Idiomatic Scala Rendering

Renders the TypeId as idiomatic Scala syntax using `TypeIdPrinter`:

```scala mdoc
TypeId.of[List[Int]].toString
TypeId.of[Map[String, Int]].toString
```

### Type Parameters and Arguments

Methods for inspecting generic type information.

#### `typeParams` — Formal Type Parameters

The `TypeId#typeParams` method returns the list of formal type parameters declared by a type. This is what makes a type generic. For a type like `Box[+A]`, `typeParams` captures the declaration of `A` — including its name, position in the parameter list, variance (whether it's covariant `+`, contravariant `−`, or invariant), and any bounds:

```scala
sealed trait TypeId[A <: AnyKind] {
  def typeParams: List[TypeParam]
}
```

To see how `TypeId` preserves type parameter information, we define several generic types with different variance patterns. Each demonstrates a different type parameter characteristic:

```scala mdoc:silent:reset
import zio.blocks.typeid._

sealed trait Container[+A]
case class Box[+A](value: A) extends Container[A]

sealed trait Sink[-T]
case class Logger[-T]() extends Sink[T]

sealed trait Cache[K, +V]
case class LRUCache[K, +V](maxSize: Int) extends Cache[K, V]
```

When we derive `TypeId` for these types, we can inspect their type parameters and see the variance that was declared:

```scala mdoc
val boxId = TypeId.of[Box]
// Box declares [+A], so we see one covariant parameter
boxId.typeParams
boxId.typeParams.head.variance
boxId.typeParams.head.name

val sinkId = TypeId.of[Sink]
// Sink declares [-T], so we see one contravariant parameter
sinkId.typeParams
sinkId.typeParams.head.variance

val cacheId = TypeId.of[Cache]
// Cache declares [K, +V], so we see two parameters with different variances
cacheId.typeParams
cacheId.typeParams.map(p => (p.name, p.variance.symbol))
```

#### `TypeParam` — Type Parameter Details

Each element in `TypeId#typeParams` is a `TypeParam` value. A type parameter defines a single formal parameter in a generic type's declaration — its name, position, variance (covariance, contravariance, invariance), bounds, and kind. When you derive a `TypeId` for a generic type like `Box[+A]`, the macro captures each declared parameter as a `TypeParam` so you can inspect them at runtime.

`TypeParam` captures these pieces of information about a type parameter:

```scala
final case class TypeParam(
  name: String,                           // "A", "T", "K", "F"
  index: Int,                             // Position: 0, 1, 2, ...
  variance: Variance = Variance.Invariant, // +, -, or none
  bounds: TypeBounds = TypeBounds.Unbounded, // >: Lower <: Upper
  kind: Kind = Kind.Type                  // *, * -> *, etc.
)
```

To inspect individual fields of a type parameter, we can extract and examine each one:

```scala mdoc:silent:reset
import zio.blocks.typeid._

sealed trait Functor[F[_]]
```

To inspect individual fields of a type parameter, extract and examine each property:

```scala mdoc
val functorId = TypeId.of[Functor]
val paramF = functorId.typeParams.head

paramF.name
paramF.index
paramF.variance
paramF.isInvariant
paramF.kind
paramF.isTypeConstructor
```

`TypeParam` provides convenience predicates for checking variance without inspecting the raw `variance` field:

```scala mdoc:silent:reset
import zio.blocks.typeid._

sealed trait Box[+A]
sealed trait Sink[-T]
sealed trait Cache[K, +V]
```

Using these types, we can check the variance predicates to verify which parameters are covariant, contravariant, or invariant:

```scala mdoc
val boxId = TypeId.of[Box]
boxId.typeParams.head.isCovariant

val sinkId = TypeId.of[Sink]
sinkId.typeParams.head.isContravariant

val cacheId = TypeId.of[Cache]
val (k, v) = (cacheId.typeParams(0), cacheId.typeParams(1))
k.isInvariant
v.isCovariant
```

#### `typeArgs` — Applied Type Arguments

**Applied types** are generic types instantiated with concrete type arguments. For example, `List[Int]` is the generic `List` type constructor applied to the `Int` type argument, and `Map[String, Int]` applies two arguments to `Map`. When you derive a `TypeId` for an applied type, the `typeArgs` method returns the concrete type arguments as a list of `TypeRepr` values — allowing you to inspect what types were plugged into the type constructor.

The `typeArgs` method is essential for schema systems and code generators that need to understand the full type structure. For instance, a serializer might need to know that `List[Int]` has `Int` as its element type, or a validator might need to distinguish between `Map[String, Int]` and `Map[String, String]`:

```scala
sealed trait TypeId[A <: AnyKind] {
  def typeArgs: List[TypeRepr]
}
```

`typeArgs` returns a list of `TypeRepr` values. `TypeRepr` is an algebraic data type that represents type expressions in the Scala type system — these can be simple type references (like `Int` or `String`), complex applied types (like `List[String]`), or compound types (like `A & B`). For a detailed breakdown of all `TypeRepr` variants, see [TypeRepr — Type Expressions](#typerepr--type-expressions).

Setup some custom generic types with different type argument patterns:

```scala mdoc:silent:reset
import zio.blocks.typeid._

case class Pair[A, B](first: A, second: B)
case class Container[T](value: T)
case class Result[E, V](error: Option[E], value: Option[V])
```

Now inspect the type arguments of various applied types:

```scala mdoc
// Simple single type argument
val containerIntId = TypeId.of[Container[Int]]
containerIntId.typeArgs

// Multiple type arguments
val pairId = TypeId.of[Pair[String, Double]]
pairId.typeArgs

// Nested applied types
val resultId = TypeId.of[Result[String, List[Int]]]
resultId.typeArgs

// Type constructor with no arguments has empty typeArgs
TypeId.of[List].typeArgs
```

When you access `typeArgs`, each element is a `TypeRepr` describing that argument. You can inspect them further to understand the structure:

```scala mdoc
val mapStringIntId = TypeId.of[Map[String, Int]]
val args = mapStringIntId.typeArgs

// First argument: String
args(0)

// Second argument: Int
args(1)
```

For more complex types, `typeArgs` captures the full structure of the arguments, including unions, intersections, function types, and tuples:

```scala mdoc:silent:reset
import zio.blocks.typeid._

// Union types (Scala 3)
case class Handler[T](process: T | String)

// Intersection types (Scala 3)
trait Readable { def read(): String }
trait Writable { def write(data: String): Unit }
case class Stream[T](data: T & Readable & Writable)

// Function type arguments
case class Transformer[A, B](f: A => B)

// Tuple type arguments
case class MultiValue[A, B, C](values: (A, B, C))
```

Now inspect the type arguments in these complex types:

```scala mdoc
// Union type argument
val handlerStrId = TypeId.of[Handler[Int]]
handlerStrId.typeArgs

// Intersection type argument
val streamId = TypeId.of[Stream[List[String]]]
streamId.typeArgs

// Function type as argument
val transformerId = TypeId.of[Transformer[String, Int]]
transformerId.typeArgs

// Tuple type as argument
val multiValueId = TypeId.of[MultiValue[String, Int, Boolean]]
multiValueId.typeArgs
```

#### `arity` — Number of Type Parameters

The **arity** of a type is the number of formal type parameters it declares. A type with arity 0 is fully applied (a "proper type"), while arity > 0 means it's a type constructor that needs to be instantiated with type arguments. Arity is useful for generic programming and type-indexed registries where you need to distinguish between different levels of type abstraction:

```scala
sealed trait TypeId[A <: AnyKind] {
  def arity: Int
}
```

Setup some generic types with different arities:

```scala mdoc:silent:reset
import zio.blocks.typeid._

case class Single[A](value: A)           // Arity 1
case class Pair[A, B](a: A, b: B)       // Arity 2
case class Triple[A, B, C](a: A, b: B, c: C) // Arity 3
case class Value(x: Int)                 // Arity 0
```

Check the arity of different types:

```scala mdoc
TypeId.of[Single].arity
TypeId.of[Pair].arity
TypeId.of[Triple].arity
TypeId.of[Value].arity

// Applied types have the same arity as their type constructor
TypeId.of[Single[Int]].arity
TypeId.of[Pair[String, Int]].arity
```

#### `isProperType` — Has No Type Parameters

A **proper type** (also called a ground type or monomorphic type) is a fully instantiated type with no unresolved type parameters. It's the opposite of a type constructor — you can directly instantiate values of a proper type, whereas a type constructor needs type arguments before it's usable. The `isProperType` predicate returns `true` when `arity == 0`, helping distinguish concrete types from abstract type constructors:

```scala mdoc:silent:reset
import zio.blocks.typeid._

case class Single[A](value: A)
case class Pair[A, B](a: A, b: B)
case class Value(x: Int)
```

Check which types are proper types:

```scala mdoc
// Proper types: fully instantiated, arity == 0
TypeId.of[Value].isProperType
TypeId.of[List[Int]].isProperType
TypeId.of[Pair[String, Int]].isProperType
TypeId.of[Int].isProperType

// Type constructors: need type arguments, arity > 0
TypeId.of[Single].isProperType
TypeId.of[Pair].isProperType
TypeId.of[List].isProperType
```

#### `isTypeConstructor` — Has Type Parameters

A **type constructor** is a parameterized type that cannot be instantiated directly — it requires concrete type arguments first. For example, `List` is a type constructor (you can't have a value of type `List`, only `List[Int]` or `List[String]`). The `isTypeConstructor` predicate returns `true` when `arity > 0`, indicating the type needs to be applied with arguments before use. This is useful for generic programming where you work with families of related types:

```scala mdoc:silent:reset
import zio.blocks.typeid._

case class Single[A](value: A)
case class Pair[A, B](a: A, b: B)
case class Value(x: Int)
```

Identify which types are type constructors:

```scala mdoc
// Type constructors: need type arguments, arity > 0
TypeId.of[Single].isTypeConstructor
TypeId.of[Pair].isTypeConstructor
TypeId.of[List].isTypeConstructor
TypeId.of[Map].isTypeConstructor

// Proper types: fully instantiated, no type parameters
TypeId.of[Value].isTypeConstructor
TypeId.of[List[Int]].isTypeConstructor
TypeId.of[Int].isTypeConstructor
```

#### `isApplied` — Has Type Arguments

An **applied type** is a generic type that has been instantiated with concrete type arguments. For example, `List[Int]` is an applied type (`List` applied to `Int`), while `List` by itself is a type constructor with no arguments applied. The `isApplied` predicate returns `true` when `typeArgs.nonEmpty`, helping distinguish between abstract type constructors and concrete instantiated types. This is useful for code generators that need to know whether a type is ready for use:

```scala mdoc:silent:reset
import zio.blocks.typeid._

case class Single[A](value: A)
case class Pair[A, B](a: A, b: B)
```

Check which types are applied:

```scala mdoc
// Applied types: have type arguments
TypeId.of[List[Int]].isApplied
TypeId.of[Pair[String, Int]].isApplied
TypeId.of[Single[Boolean]].isApplied
TypeId.of[Map[String, Double]].isApplied

// Type constructors: no type arguments applied
TypeId.of[List].isApplied
TypeId.of[Single].isApplied
TypeId.of[Pair].isApplied
TypeId.of[Map].isApplied
```

### Type Classification

**Type classification** determines what kind of type definition something is — whether it's a class, trait, object, enum, alias, opaque type, or something else. This is essential for code generators, serializers, and frameworks that need to handle different type categories differently. TypeId provides both a `defKind` property that returns detailed classification information, and convenient predicates (like `isClass`, `isTrait`, `isCaseClass`) for common checks.

#### `defKind` — Type Definition Kind

Returns the `TypeDefKind` classifying this type (class, trait, object, enum, alias, opaque, etc.):

```scala
sealed trait TypeId[A <: AnyKind] {
  def defKind: TypeDefKind
}
```

Define types representing different classifications:

```scala mdoc:silent:reset
import zio.blocks.typeid._

sealed trait Animal
case class Dog(name: String) extends Animal
case object Sentinel
type UserId = String
opaque type Email = String
enum Color { case Red; case Green; case Blue }
```

Inspect the `defKind` for each type to see how they're classified:

```scala mdoc
TypeId.of[Dog].defKind
TypeId.of[Animal].defKind
TypeId.of[Sentinel.type].defKind
TypeId.of[UserId].defKind
TypeId.of[Email].defKind
TypeId.of[Color].defKind
```

#### Classification Predicates

Each predicate inspects `defKind` for a specific type definition kind. These are convenience methods that save you from pattern matching on `defKind` directly:

| Predicate        | Returns `true` when                                         |
|------------------|-------------------------------------------------------------|
| `isClass`        | `defKind` is `TypeDefKind.Class`                            |
| `isTrait`        | `defKind` is `TypeDefKind.Trait`                            |
| `isObject`       | `defKind` is `TypeDefKind.Object`                           |
| `isEnum`         | `defKind` is `TypeDefKind.Enum`                             |
| `isAlias`        | `defKind` is `TypeDefKind.TypeAlias`                        |
| `isOpaque`       | `defKind` is `TypeDefKind.OpaqueType`                       |
| `isAbstract`     | `defKind` is `TypeDefKind.AbstractType`                     |
| `isSealed`       | `defKind` is `TypeDefKind.Trait(isSealed = true, _)` (sealed traits only) |
| `isCaseClass`    | `defKind` is `TypeDefKind.Class(_, _, isCase = true, _, _)` |
| `isValueClass`   | `defKind` is `TypeDefKind.Class(_, _, _, isValue = true, _)`|

Use the classification predicates to identify type kinds:

```scala mdoc
val animalId   = TypeId.of[Animal]
val dogId      = TypeId.of[Dog]
val sentinelId = TypeId.of[Sentinel.type]
val userIdId   = TypeId.of[UserId]
val emailId    = TypeId.of[Email]
val colorId    = TypeId.of[Color]

// Trait classifications
animalId.isTrait
animalId.isSealed

// Case class
dogId.isCaseClass

// Object/Singleton
sentinelId.isObject

// Type alias
userIdId.isAlias

// Opaque type
emailId.isOpaque

// Enum
colorId.isEnum
```

:::note
`isSealed` only checks sealed traits. A sealed abstract class or sealed enum will return `false`; use `defKind` directly to inspect those cases by pattern matching on `TypeDefKind.Class(_, isAbstract = true, ...)` or `TypeDefKind.Enum(...)`.
:::

#### Semantic Predicates

**Semantic predicates** check specific semantic properties of the type after normalization, allowing you to identify built-in Scala types like tuples, products, sums, options, and either. These are useful for generic serializers and validators that treat built-in types specially.

**Normalization** resolves type aliases and opaque types to their underlying representations. For example, if you have `type UserId = String`, normalization reveals that the underlying type is `String`. Similarly, an opaque type like `opaque type Email = String` normalizes to `String`. This allows predicates like `isOption` to work correctly even when the type is wrapped in an alias or opaque type — it will look through the wrapper to find the actual semantic type.

| Predicate   | Checks                                                 |
|-------------|--------------------------------------------------------|
| `isTuple`   | Normalized type is `scala.TupleN`                      |
| `isProduct` | Normalized type is `scala.Product` or `scala.ProductN` |
| `isSum`     | Normalized type is named `Either` or `Option`          |
| `isEither`  | Normalized type is `scala.util.Either`                 |
| `isOption`  | Normalized type is `scala.Option`                      |

Check semantic properties with practical examples:

```scala mdoc
// Tuples
TypeId.of[(String, Int)].isTuple
TypeId.of[(Int, String, Boolean)].isTuple

// Options and Either
TypeId.of[Option[String]].isOption
TypeId.of[Either[String, Int]].isEither

// Products (built-in Scala Product interface, not user case classes)
TypeId.of[Product2[String, Int]].isProduct
```

:::note
`isProduct` returns `true` only for Scala's built-in `scala.Product`, `scala.Product1`, etc. -- not for user-defined case classes. Use `isCaseClass` for that.
:::

Understanding the distinction between `isSum`, `isEither`, and `isOption`:

```scala mdoc
import zio.blocks.typeid._
// For standard library types, use isEither and isOption
TypeId.of[Option[String]].isOption
TypeId.of[Option[String]].isSum

TypeId.of[Either[String, Int]].isEither
TypeId.of[Either[String, Int]].isSum
```

[//]: # (:::note)
[//]: # (**Practical guidance:** Always use `isEither` for `scala.util.Either` and `isOption` for `scala.Option`. The `isSum` predicate is rarely needed — it checks for hypothetical types named `"Option"` or `"Either"` placed directly in the `scala` package itself &#40;not in `scala.util` subpackage&#41;, which almost never occurs in real code.)
[//]: # (:::)

### Subtype Relationships

**Subtype relationships** determine the inheritance hierarchy and compatibility between types at runtime. This is essential for type-safe dispatch, generic programming, and validating that a value of one type can be used where another type is expected. TypeId provides methods to check direct and transitive subtyping, supertyping, type equivalence, and inspect the parent types in the hierarchy.

#### `isSubtypeOf` — Check Subtyping

Checks if this type is a subtype of another type. A type is a subtype if it extends or implements the other type, either directly or transitively. This method handles direct inheritance, sealed trait subtypes, enum cases, transitive inheritance chains, and variance-aware subtyping for applied generic types:

```scala
sealed trait TypeId[A <: AnyKind] {
  def isSubtypeOf(other: TypeId[?]): Boolean
}
```

Define a type hierarchy with direct and transitive relationships:

```scala mdoc:silent:reset
import zio.blocks.typeid._

sealed trait Animal
sealed trait Mammal extends Animal
case class Dog(name: String) extends Mammal
case class Cat(name: String) extends Mammal
case class Fish(species: String) extends Animal
```

Check subtyping relationships:

```scala mdoc
val dogId     = TypeId.of[Dog]
val mammalId  = TypeId.of[Mammal]
val animalId  = TypeId.of[Animal]
val fishId    = TypeId.of[Fish]

// Direct inheritance: Dog extends Mammal
dogId.isSubtypeOf(mammalId)

// Transitive inheritance: Dog extends Mammal extends Animal
dogId.isSubtypeOf(animalId)

// Not a subtype relationship
dogId.isSubtypeOf(fishId)
fishId.isSubtypeOf(mammalId)
```

Covariant type constructors preserve subtyping relationships:

```scala mdoc
TypeId.of[List[Dog]].isSubtypeOf(TypeId.of[List[Mammal]])
TypeId.of[List[Dog]].isSubtypeOf(TypeId.of[List[Animal]])
```

**Scala 3 exclusive features:** In Scala 3, `isSubtypeOf` handles advanced type relationships that Scala 2 cannot. These examples show what works in Scala 3:

```scala mdoc:silent:reset
import zio.blocks.typeid._

// Scala 3: Enum cases
enum Color {
  case Red
  case Green
  case Blue
}

// Scala 3: Union type aliases
type StringOrInt = String | Int

// Scala 3: Intersection type aliases
trait Readable { def read(): String }
trait Writable { def write(data: String): Unit }
type ReadWrite = Readable & Writable
```

In Scala 3, `isSubtypeOf` correctly handles these advanced type cases:

```scala mdoc
// Enum cases: Red is a subtype of Color
TypeId.of[Color.Red.type].isSubtypeOf(TypeId.of[Color])

// Union type aliases: String is one of the union members
TypeId.of[String].isSubtypeOf(TypeId.of[StringOrInt])
TypeId.of[Int].isSubtypeOf(TypeId.of[StringOrInt])

// Intersection type aliases: A type implementing both traits is a subtype
val readWriteId = TypeId.of[ReadWrite]
val readableId = TypeId.of[Readable]
readWriteId.isSubtypeOf(readableId)
```

:::note
In Scala 2, `isSubtypeOf` does not handle `EnumCase` subtypes, types aliased to union types, or types aliased to intersection types — only the Scala 3 implementation checks those cases.
:::

#### `isSupertypeOf` — Check Supertyping

The mirror of `isSubtypeOf` — returns `true` if the other type is a subtype of this type. This is useful when you need to check if a type can accept instances of another type, or when validating that a container type can hold values of a more specific type:

```scala
sealed trait TypeId[A <: AnyKind] {
  def isSupertypeOf(other: TypeId[?]): Boolean
}
```

Check supertyping relationships using the same hierarchy:

```scala mdoc:silent:reset
import zio.blocks.typeid._

sealed trait Animal
sealed trait Mammal extends Animal
case class Dog(name: String) extends Mammal
case class Cat(name: String) extends Mammal
case class Fish(species: String) extends Animal

val dogId     = TypeId.of[Dog]
val mammalId  = TypeId.of[Mammal]
val animalId  = TypeId.of[Animal]
val fishId    = TypeId.of[Fish]
```

Now check supertyping relationships:

```scala mdoc
// Mammal is a supertype of Dog (Mammal can hold Dog instances)
mammalId.isSupertypeOf(dogId)

// Animal is a supertype of both Dog and Fish (Animal is the most general)
animalId.isSupertypeOf(dogId)
animalId.isSupertypeOf(fishId)

// Mammal is a supertype of Cat too
mammalId.isSupertypeOf(TypeId.of[Cat])

// But Dog is not a supertype of Mammal (can't hold all Mammals as Dogs)
dogId.isSupertypeOf(mammalId)

// And Fish is not a supertype of Mammal
fishId.isSupertypeOf(mammalId)
```

:::note
**Limitation:** TypeId's subtyping checks currently do not handle contravariance in function types. In type theory, `Mammal => String` should be a supertype of `Dog => String` due to contravariance of input parameters, but `isSupertypeOf` returns `false` for function types with subtype relationships. For practical purposes, rely on `isSupertypeOf` for class and trait hierarchies rather than complex generic type relationships.
:::

#### `isEquivalentTo` — Check Type Equivalence

Returns `true` when two types are structurally equivalent — meaning they are **mutual subtypes** of each other. In other words, both `A.isSubtypeOf(B)` and `B.isSubtypeOf(A)` must be true. Two types are equivalent when they represent the same type through different paths, or when they normalize to the same underlying type (important for type aliases and opaque types):

```scala
sealed trait TypeId[A <: AnyKind] {
  def isEquivalentTo(other: TypeId[?]): Boolean
}
```

Check type equivalence with practical examples:

```scala mdoc:silent:reset
import zio.blocks.typeid._

sealed trait Animal
sealed trait Mammal extends Animal
case class Dog(name: String) extends Mammal
case class Cat(name: String) extends Mammal

val dogId     = TypeId.of[Dog]
val mammalId  = TypeId.of[Mammal]
val animalId  = TypeId.of[Animal]
val catId     = TypeId.of[Cat]
```

Now check type equivalence:

```scala mdoc
// A type is always equivalent to itself
dogId.isEquivalentTo(dogId)

// The same type referenced twice is equivalent
val dogId2 = TypeId.of[Dog]
dogId.isEquivalentTo(dogId2)

// Different types in the hierarchy are NOT equivalent (one-way subtyping only)
dogId.isEquivalentTo(mammalId)
mammalId.isEquivalentTo(animalId)

// Cat and Dog are different types, even though both extend Mammal
dogId.isEquivalentTo(catId)
```

Type aliases normalize to the same type, making them equivalent:

```scala mdoc:silent:reset
import zio.blocks.typeid._

type UserId = String
type Username = String
```

Both aliases normalize to `String`, so they are equivalent:

```scala mdoc
val userIdType = TypeId.of[UserId]
val usernameType = TypeId.of[Username]
val stringType = TypeId.of[String]

// Both type aliases are equivalent because they normalize to the same underlying type
userIdType.isEquivalentTo(usernameType)

// And both are equivalent to their underlying type
userIdType.isEquivalentTo(stringType)
usernameType.isEquivalentTo(stringType)
```

#### `parents` — Parent Types

Returns the list of parent type representations as `TypeRepr` values, flattened across the full inheritance hierarchy. Each parent is represented as a `TypeRepr` that captures the parent type, including any type arguments it might have. This is useful for code generators, serializers, and frameworks that need to understand the inheritance structure of a type:

```scala
sealed trait TypeId[A <: AnyKind] {
  def parents: List[TypeRepr]
}
```

```scala mdoc:silent:reset
import zio.blocks.typeid._

trait Swimmer { def swim(): Unit = () }
trait Flyer   { def fly(): Unit  = () }
trait Duck extends Swimmer with Flyer
case class MallardDuck() extends Duck
```

Parents are flattened across the full hierarchy:

```scala mdoc
// Duck extends Swimmer and Flyer directly
TypeId.of[Duck].parents

// MallardDuck extends Duck — parents include Duck, Swimmer, and Flyer
TypeId.of[MallardDuck].parents
```

### Metadata

Methods for accessing annotations, self-type, alias target, and opaque representation.

#### `annotations` — Type Annotations

Returns the list of annotations attached to this type at compile time. Each `Annotation` carries the annotation's name and its argument values, making this useful for frameworks that drive behaviour from annotations (e.g. serialization hints, validation rules, or access-control markers):

```scala
sealed trait TypeId[A <: AnyKind] {
  def annotations: List[Annotation]
}
```

```scala mdoc:silent:reset
import zio.blocks.typeid._

@deprecated("use NewData instead", "2.0")
@transient
case class LegacyData(id: Int, payload: String)
case class Plain(x: Int)
```

A type with annotations reports each annotation by name; an unannotated type returns an empty list:

```scala mdoc
// LegacyData has two annotations
TypeId.of[LegacyData].annotations.map(_.name)

// Plain has no annotations
TypeId.of[Plain].annotations
```

#### `selfType` — Self-Type Annotation

Returns `Some(typeRepr)` when the trait declares a self-type (e.g., `trait Foo { self: Bar => ... }`), and `None` otherwise. Self-types express a dependency requirement: a trait that declares `self: Logger =>` can only be mixed into a class that also mixes in `Logger`. This method lets frameworks detect and validate those requirements at runtime:

```scala
sealed trait TypeId[A <: AnyKind] {
  def selfType: Option[TypeRepr]
}
```

```scala mdoc:silent:reset
import zio.blocks.typeid._

trait Logger { def log(msg: String): Unit }
trait Service { self: Logger => def doWork(): Unit = log("working") }
```

`Service` requires a `Logger` to be mixed in, while `Logger` has no self-type requirement:

```scala mdoc
// Service declares a self-type dependency on Logger
TypeId.of[Service].selfType

// Logger has no self-type requirement
TypeId.of[Logger].selfType
```

#### `aliasedTo` — Alias Target

Returns `Some(typeRepr)` for type aliases pointing to their underlying type, and `None` for nominal and opaque types. This lets you inspect what a type alias expands to without evaluating expressions at runtime:

```scala
sealed trait TypeId[A <: AnyKind] {
  def aliasedTo: Option[TypeRepr]
}
```

```scala mdoc:silent:reset
import zio.blocks.typeid._

type Age = Int
type Name = String
```

A type alias resolves to its target; a concrete type returns `None`:

```scala mdoc
// Age is an alias for Int
TypeId.of[Age].aliasedTo

// Name is an alias for String
TypeId.of[Name].aliasedTo

// Int is a concrete type, not an alias
TypeId.of[Int].aliasedTo
```

#### `representation` — Opaque Type Representation

Returns `Some(typeRepr)` for opaque types revealing their underlying representation type, and `None` for all other types. Opaque types hide their implementation behind a new name, but `representation` lets frameworks such as serializers discover what the type is actually stored as:

```scala
sealed trait TypeId[A <: AnyKind] {
  def representation: Option[TypeRepr]
}
```

```scala mdoc:silent:reset
import zio.blocks.typeid._

opaque type Email = String
opaque type UserId = Int
```

An opaque type exposes its representation; a non-opaque type returns `None`:

```scala mdoc
// Email is an opaque type backed by String
TypeId.of[Email].representation

// UserId is an opaque type backed by Int
TypeId.of[UserId].representation

// Int is not opaque
TypeId.of[Int].representation
```

### Erasure and Runtime

Methods for type erasure, runtime class lookup, and reflective construction.

#### `erased` — Erase Type Parameter

Erases the phantom type parameter, returning a `TypeId.Erased` (alias for `TypeId[TypeId.Unknown]`). This is useful when you need to store heterogeneous `TypeId` values in a collection or a type-indexed map, where the exact type parameter is unknown or irrelevant at the storage site:

```scala
sealed trait TypeId[A <: AnyKind] {
  def erased: TypeId.Erased
}
```

Different types can be stored together once erased:

```scala mdoc
val ids: List[TypeId.Erased] = List(
  TypeId.of[Int].erased,
  TypeId.of[String].erased,
  TypeId.of[Boolean].erased
)

ids.map(_.name)
```

#### `classTag` — Runtime ClassTag

Returns a `ClassTag` for this type. Returns the correct primitive `ClassTag` for Scala primitive types (`Int`, `Long`, `Boolean`, etc.) and `ClassTag.AnyRef` for all reference types. This is useful when you need to create properly-typed arrays or work with generic collections that require implicit `ClassTag` evidence at runtime:

```scala
sealed trait TypeId[A <: AnyKind] {
  lazy val classTag: scala.reflect.ClassTag[?]
}
```

On the JVM, arrays are reified — the element type is part of the array object at runtime, not erased like generics. To create an array of a generic type `T`, Scala requires a `ClassTag[T]` so the runtime knows whether to allocate a primitive array (`int[]`, `double[]`) or an object array (`Object[]`). This matters for memory efficiency: a primitive `int[]` stores 4 bytes per element unboxed, while an `Integer[]` stores heap references plus the cost of boxing each value.

`classTag` returns the correct `ClassTag` for each type:

```scala mdoc
// Primitive types have dedicated ClassTags
TypeId.of[Int].classTag
TypeId.of[Double].classTag

// Reference types use ClassTag.AnyRef
TypeId.of[String].classTag
TypeId.of[List[Int]].classTag
```

A concrete use case is a generic storage allocator that creates the right array type from a `TypeId`:

```scala mdoc:silent:reset
import zio.blocks.typeid._

def makeStorage(size: Int, id: TypeId[?]): Array[?] =
  id.classTag.newArray(size)
```

```scala mdoc
// Creates int[] (primitive, unboxed)
makeStorage(100, TypeId.int).getClass.getComponentType

// Creates double[] (primitive, unboxed)
makeStorage(100, TypeId.double).getClass.getComponentType

// Creates Object[] (reference)
makeStorage(100, TypeId.string).getClass.getComponentType
```

Another use case is detecting primitive types. Without `classTag`, you would need to enumerate every primitive with a chain of `isInstanceOf` checks:

```scala
// Without classTag: every primitive listed explicitly
def isPrimitive(value: Any): Boolean =
  value.isInstanceOf[Int]     ||
  value.isInstanceOf[Long]    ||
  value.isInstanceOf[Float]   ||
  value.isInstanceOf[Double]  ||
  value.isInstanceOf[Boolean] ||
  value.isInstanceOf[Byte]    ||
  value.isInstanceOf[Short]   ||
  value.isInstanceOf[Char]
```

This is fragile: if you forget one primitive (e.g. `Unit`) the check silently breaks. With `classTag` the same question reduces to a single comparison that can never miss a case — `ClassTag.AnyRef` is the universal fallback for every reference type, so anything that is not `AnyRef` must be a primitive:

```scala mdoc:silent:reset
import zio.blocks.typeid._

def isPrimitive(id: TypeId[?]): Boolean =
  id.classTag != scala.reflect.ClassTag.AnyRef
```

```scala mdoc
isPrimitive(TypeId.of[Int])
isPrimitive(TypeId.of[Double])
isPrimitive(TypeId.of[Boolean])
isPrimitive(TypeId.of[String])
isPrimitive(TypeId.of[List[Int]])
```

:::note
`classTag` returns `ClassTag.AnyRef` for all reference types. For matching or filtering by a specific reference type at runtime, use `clazz` instead.
:::

#### `clazz` — Runtime Class

Returns the runtime `Class[_]` for this type. On the JVM it returns `Some(Class[_])` for nominal and applied types, and `None` for alias and opaque types. On Scala.js it always returns `None` since JVM reflection is unavailable. This is the entry point for reflective operations such as instantiation, field access, or integration with Java libraries:

```scala
sealed trait TypeId[A <: AnyKind] {
  def clazz: Option[Class[?]]
}
```

```scala mdoc:silent:reset
import zio.blocks.typeid._

type Age = Int
```

```scala mdoc
// Nominal and applied types return Some on the JVM
TypeId.of[String].clazz
TypeId.of[Int].clazz
TypeId.of[List[Int]].clazz

// Alias types return None — the alias has no class of its own
TypeId.of[Age].clazz
```

:::note
On Scala.js, `clazz` always returns `None`. Use `classTag` instead when you need cross-platform runtime type information.
:::

#### `construct` — Reflective Construction

Constructs an instance using the primary constructor on the JVM by passing constructor arguments as a `Chunk[AnyRef]`. Returns `Left` with an error message on Scala.js or when construction fails (wrong argument count, wrong types, or abstract types). Primitive values must be explicitly boxed since the argument type is `AnyRef`:

```scala
sealed trait TypeId[A <: AnyKind] {
  def construct(args: Chunk[AnyRef]): Either[String, Any]
}
```

```scala mdoc:silent:reset
import zio.blocks.typeid._
import zio.Chunk

// JVM only
case class User(name: String, age: Int)

val userId = TypeId.of[User]
```

```scala mdoc
userId.construct(Chunk("Alice", 30: Integer))
userId.construct(Chunk("Bob"))
```

##### Collection Types

Collection types accept variadic arguments representing elements. Sequence-like types (`List`, `Vector`, `Set`, `Seq`, `IndexedSeq`, `Array`, `ArraySeq`, `Chunk`) each pass a variadic sequence of elements:

```scala mdoc:silent:reset
import zio.blocks.typeid._
import zio.Chunk
```

```scala mdoc
TypeId.of[List[String]].construct(Chunk("a", "b", "c"))
TypeId.of[Vector[Int]].construct(Chunk(1: Integer, 2: Integer, 3: Integer))
TypeId.of[Set[String]].construct(Chunk("x", "y", "z"))
```

Map types pass interleaved key-value pairs and fail on odd argument counts:

```scala mdoc
TypeId.of[Map[String, Int]].construct(Chunk("a", 1: Integer, "b", 2: Integer))
```

##### Sum Types

Sum types have special calling conventions. `Option` accepts 1 element to construct `Some(value)`, or 0 elements to construct `None`:

```scala mdoc
TypeId.of[Option[String]].construct(Chunk("hello"))
TypeId.of[Option[String]].construct(Chunk())
```

`Either` requires a Boolean flag as the first argument (`true` for `Right`, `false` for `Left`), followed by the value:

```scala mdoc
TypeId.of[Either[String, Int]].construct(Chunk(true: java.lang.Boolean, 42: Integer))
TypeId.of[Either[String, Int]].construct(Chunk(false: java.lang.Boolean, "error"))
```

### Normalization and Equality

**Normalization** resolves type aliases and opaque type representations to their underlying concrete types. For example, `type Age = Int` normalizes to `Int`, and chained aliases like `type UserId = NonEmpty; type NonEmpty = List[Int]` both resolve to `List[Int]`. Opaque types such as `opaque type UserId = String` normalize to their representation. Normalization is crucial because multiple syntactic names often refer to the same underlying type, enabling deduplication and caching strategies.

**Structural Equality** compares two TypeIds by their normalized form, treating types with identical underlying structure as equal. Importantly, opaque types preserve their semantic identity even after normalization—`TypeId.of[UserId]` where `opaque type UserId = String` remains distinct from `TypeId.of[String]` for equality purposes, preserving runtime type safety. This distinction enables type-safe registries and validators that respect opaque type boundaries.

These concepts are essential for building type-indexed registries that recognize multiple alias names as referring to the same handler, implementing serialization strategies based on normalized type structure, and enforcing opaque type safety in type-indexed maps where different opaque types wrapping the same base type should have separate validators or handlers.

#### `TypeId.normalize` — Resolve Aliases

Resolves chains of type aliases to the underlying type. For example, `type MyList = List[Int]` normalizes to `List[Int]`:

```scala
object TypeId {
  def normalize(id: TypeId[?]): TypeId[?]
}
```

```scala mdoc:silent:reset
import zio.blocks.typeid._

type Age = Int
```

```scala mdoc
val ageId = TypeId.of[Age]
val norm = TypeId.normalize(ageId)
norm.fullName
```

#### `TypeId.structurallyEqual` — Structural Equality

Checks if two TypeIds are structurally equal after normalization. Semantically equivalent to `==` on TypeId instances; `==` additionally short-circuits on hash mismatch for performance:

```scala
object TypeId {
  def structurallyEqual(a: TypeId[?], b: TypeId[?]): Boolean
}
```

```scala mdoc:silent:reset
import zio.blocks.typeid._

type UserId = Int
```

```scala mdoc
val a = TypeId.of[UserId]
val b = TypeId.of[Int]
TypeId.structurallyEqual(a, b)
a == b
```

#### `TypeId.structuralHash` — Structural Hash Code

Computes a hash code based on the normalized structural representation:

```scala
object TypeId {
  def structuralHash(id: TypeId[?]): Int
}
```

#### `TypeId.unapplied` — Strip Type Arguments

Returns the type constructor by stripping all type arguments. For example, `TypeId.unapplied(TypeId.of[List[Int]])` returns the equivalent of `TypeId.of[List]`:

```scala
object TypeId {
  def unapplied(id: TypeId[?]): TypeId[?]
}
```

```scala mdoc
val listInt = TypeId.of[List[Int]]
val unapplied = TypeId.unapplied(listInt)
unapplied.isApplied
unapplied.name
```

### Pattern Matching Extractors

The companion object provides extractors for pattern matching on TypeId classification:

```scala mdoc:silent:reset
import zio.blocks.typeid._

case class User(id: Long, email: String)
val userId = TypeId.of[User]
```

```scala mdoc
userId match {
  case TypeId.Nominal(name, owner, params, defKind, parents) =>
    s"Nominal type '$name' in ${owner.asString}"
  case TypeId.Alias(name, _, _, aliased) =>
    s"Alias '$name'"
  case TypeId.Opaque(name, _, _, repr, _) =>
    s"Opaque '$name'"
}
```

The extractors are:

| Extractor                                               | Matches                  |
|---------------------------------------------------------|--------------------------|
| `TypeId.Nominal(name, owner, params, defKind, parents)` | Classes, traits, objects |
| `TypeId.Alias(name, owner, params, aliased)`            | Type aliases             |
| `TypeId.Opaque(name, owner, params, repr, bounds)`      | Opaque types             |
| `TypeId.Sealed(name)`                                   | Sealed traits            |
| `TypeId.Enum(name, owner)`                              | Scala 3 enums            |

## TypeDefKind Reference

`TypeDefKind` classifies every type definition. Access it via the `defKind` property documented in [Core Operations](#type-classification).

The `defKind` property (documented in [Core Operations](#type-classification)) returns one of these variants. Use classification predicates like `isCaseClass`, `isSealed`, `isObject` for simple checks.

`TypeDefKind` has these variants:

| Variant                                              | Description                                  |
|------------------------------------------------------|----------------------------------------------|
| `Class(isFinal, isAbstract, isCase, isValue, bases)` | Class definitions                            |
| `Trait(isSealed, bases)`                             | Trait definitions                            |
| `Object(bases)`                                      | Singleton objects                            |
| `Enum(bases)`                                        | Scala 3 enums                                |
| `EnumCase(parentEnum, ordinal, isObjectCase)`        | Enum cases                                   |
| `TypeAlias`                                          | Type aliases (`type Foo = Bar`)              |
| `OpaqueType(publicBounds)`                           | Opaque types                                 |
| `AbstractType`                                       | Abstract type members                        |
| `Unknown`                                            | Unclassified or unresolvable type definition |

## Type Parameters and Generics

When you derive a TypeId for a generic type, the macro captures its type parameters (variance, bounds, kind) and any applied type arguments.

A **raw type constructor** is a generic type without any type arguments filled in. For example, `List` by itself (without `[Int]` or `[String]`) is a raw type constructor. Scala 3 supports deriving TypeIds directly for raw type constructors, but Scala 2 has restrictions due to its type system:

**Scala 3** allows you to work with raw type constructors directly:

```scala
// Scala 3 only
val listId = TypeId.of[List]  // Works: raw type constructor
```

**Scala 2** requires you to use a wildcard placeholder or implicit derivation since raw type constructors are not valid syntax:

```scala
// Scala 2 alternatives
val listId = TypeId.of[List[_]]  // Use wildcard type argument

// Or retrieve via implicit derivation
implicit val derived: TypeId[List[_]] = TypeId.of[List[_]]
```

This distinction matters when you need to capture the type constructor itself (for higher-kinded type operations) rather than concrete types like `List[Int]`.

### Inspecting Type Parameters

Define your own generic types and derive their TypeIds to see how type parameters are captured:

```scala mdoc:silent:reset
import zio.blocks.typeid._

sealed trait Container[+A]
case class Box[+A](value: A) extends Container[A]

sealed trait Cache[K, +V]
case class LRUCache[K, +V](maxSize: Int) extends Cache[K, V]
```

A single-parameter type constructor:

```scala mdoc
val containerId = TypeId.of[Container]
containerId.typeParams
containerId.arity
```

A two-parameter type constructor with mixed variance (invariant `K`, covariant `V`):

```scala mdoc
val cacheId = TypeId.of[Cache]
cacheId.typeParams
cacheId.arity
```

Each `TypeParam` records the parameter's name, position, variance, bounds, and kind:

```scala mdoc
val containerParam = containerId.typeParams.head
containerParam.name
containerParam.variance
containerParam.kind
containerParam.isCovariant
```

```scala mdoc
val cacheParams = cacheId.typeParams
cacheParams.map(p => (p.name, p.variance))
```

### Inspecting Type Arguments

When you derive a TypeId for an *applied* type (a generic type with concrete arguments), the type arguments are captured:

```scala mdoc
val boxIntId = TypeId.of[Box[Int]]
boxIntId.typeArgs
boxIntId.isApplied
```

```scala mdoc
val cacheStringIntId = TypeId.of[LRUCache[String, Int]]
cacheStringIntId.typeArgs
```

### Variance

**Variance** describes how a type parameter's subtyping relationships are preserved. **Covariant** types (`+`) preserve subtyping (if `B <: A` then `Container[B] <: Container[A]`), **contravariant** types (`-`) reverse it, and **invariant** types preserve neither. For example, `Container[+A]` is covariant—a `Container[String]` can be used where `Container[Any]` is expected. In contrast, `Cache[K, V]` where `K` is invariant means `Cache[String, Int]` cannot substitute for `Cache[Any, Int]` even if `String <: Any`.

Variance matters for type safety, polymorphism, and API design. TypeId captures variance information, enabling runtime inspection and validation:

```scala mdoc
val containerParams = TypeId.of[Container].typeParams
containerParams.map(p => (p.name, p.variance))

val cacheParams = TypeId.of[Cache].typeParams
cacheParams.map(p => (p.name, p.variance))
```

You can also work with variance values directly:

```scala mdoc
Variance.Covariant.symbol
Variance.Contravariant.symbol
Variance.Invariant.symbol
Variance.Covariant.flip
```

### Kind

**Kind** describes the "type of a type"—it captures whether something is a concrete type or a type constructor, and how many type parameters it requires. A **proper type** like `Int` or `Box[String]` has kind `*` (zero parameters). A **unary type constructor** like `List` has kind `* -> *` (takes one type parameter). A **binary type constructor** like `Map` has kind `* -> * -> *` (takes two). **Higher-kinded types** like `Runnable[F[_]]` have kinds like `(* -> *) -> *` (takes a type constructor as a parameter). Kind information is essential for generic programming, enforcing API contracts, and enabling advanced patterns like monad transformers.

TypeId captures kind information at runtime, allowing you to inspect and validate the structure of types:

```scala mdoc:silent:reset
import zio.blocks.typeid._

sealed trait Container[+A]
case class Box[+A](value: A) extends Container[A]

sealed trait Cache[K, +V]
case class LRUCache[K, +V](maxSize: Int) extends Cache[K, V]

trait Runnable[F[_]] {
  def run[A](fa: F[A]): A
}
```

A proper type (`*`) — fully concrete with no type parameters:

```scala mdoc
val boxIntId = TypeId.of[Box[Int]]
boxIntId.isApplied
boxIntId.arity
```

A unary type constructor (`* -> *`) — takes one type parameter:

```scala mdoc
val containerId = TypeId.of[Container]
containerId.arity
containerId.typeParams.map(p => (p.name, p.kind))
```

A binary type constructor (`* -> * -> *`) — takes two type parameters:

```scala mdoc
val cacheId = TypeId.of[Cache]
cacheId.arity
cacheId.typeParams.map(p => (p.name, p.kind))
```

A higher-kinded type (`(* -> *) -> *`) — a type parameter that itself is a type constructor:

```scala mdoc
val runnableId = TypeId.of[Runnable]
runnableId.typeParams.head.kind
runnableId.typeParams.head.kind.arity
```

| Kind                      | Notation        | Arity | Examples              |
|---------------------------|-----------------|-------|-----------------------|
| `Kind.Type` / `Kind.Star` | `*`             | 0     | `Int`, `Box[Int]`     |
| `Kind.Star1`              | `* -> *`        | 1     | `Container`, `Option` |
| `Kind.Star2`              | `* -> * -> *`   | 2     | `Cache`, `Either`     |
| `Kind.HigherStar1`        | `(* -> *) -> *` | 1     | `Runnable`            |

## Subtype Relationships

**Subtype relationships** determine if one type is a subtype of another, enabling type-safe polymorphism and dispatch at runtime. This is essential for checking if a value of one type can be used where another type is expected. TypeId handles direct inheritance, transitive inheritance chains, sealed trait cases, and variance-aware subtyping for generic types.

Three key methods work together to express the full range of type relationships:

```scala mdoc:silent:reset
import zio.blocks.typeid._

sealed trait Animal
sealed trait Mammal extends Animal
case class Dog(name: String) extends Mammal
case class Cat(name: String) extends Mammal
case class Fish(species: String) extends Animal
```

**`isSubtypeOf`** checks if this type is a subtype of another (direct or transitive):

```scala mdoc
val dogId     = TypeId.of[Dog]
val mammalId  = TypeId.of[Mammal]
val animalId  = TypeId.of[Animal]
val fishId    = TypeId.of[Fish]

// Direct inheritance: Dog extends Mammal
dogId.isSubtypeOf(mammalId)

// Transitive inheritance: Dog extends Mammal extends Animal
dogId.isSubtypeOf(animalId)

// Not a subtype relationship
dogId.isSubtypeOf(fishId)
fishId.isSubtypeOf(mammalId)
```

**`isSupertypeOf`** is the reverse—checks if this type is a supertype (parent) of another:

```scala mdoc
mammalId.isSupertypeOf(dogId)
animalId.isSupertypeOf(dogId)
dogId.isSupertypeOf(animalId)
```

**`isEquivalentTo`** checks if two types are exactly the same:

```scala mdoc
dogId.isEquivalentTo(dogId)
dogId.isEquivalentTo(mammalId)
animalId.isEquivalentTo(animalId)
```

**Variance-aware subtyping** for generic types respects covariance and contravariance. Covariant type constructors like `List[+A]` preserve subtyping relationships:

```scala mdoc
val listDogId    = TypeId.of[List[Dog]]
val listMammalId = TypeId.of[List[Mammal]]
val listAnimalId = TypeId.of[List[Animal]]

listDogId.isSubtypeOf(listMammalId)
listDogId.isSubtypeOf(listAnimalId)
listMammalId.isSubtypeOf(listAnimalId)
```

These methods are essential for building type-safe registries, implementing generic serializers that dispatch based on type hierarchy, and validating API contracts that require specific type relationships.

## Annotations

**Annotations** are metadata attached to types at compile time. TypeId captures them at runtime, making this metadata available for introspection, validation, and dispatch logic. Annotations enable building smart serializers, validators, and code generators that adjust their behavior based on type-level metadata.

TypeId exposes each annotation as an `Annotation` object containing the annotation's type and its arguments. This is essential for frameworks that need to read compile-time metadata (like JPA, validation libraries, or custom serialization frameworks) but want to remain generic and support multiple annotation schemes:

```scala mdoc:silent:reset
import zio.blocks.typeid._

@transient
case class ImportantData(id: Int, payload: String)

case class Plain(x: Int)
```

Inspect annotations on a type:

```scala mdoc
val importantId = TypeId.of[ImportantData]
importantId.annotations
importantId.annotations.map(_.name)

TypeId.of[Plain].annotations
```

Annotations can have arguments and parameters. Create a custom annotation to see how arguments are captured:

```scala mdoc:silent:reset
import zio.blocks.typeid._

// Custom annotation with parameters
case class ApiEndpoint(version: Int, deprecated: Boolean = false) extends scala.annotation.StaticAnnotation

@ApiEndpoint(version = 2, deprecated = true)
case class UserV2(id: String, name: String)
```

Derive the TypeId and inspect annotation arguments:

```scala mdoc
val userV2Id = TypeId.of[UserV2]
userV2Id.annotations
userV2Id.annotations.head.args
```

### Annotation Data Model

Each `Annotation` contains the annotation's `TypeId` and a list of `AnnotationArg` values representing the arguments:

| Type                                           | Description                                        |
|------------------------------------------------|----------------------------------------------------|
| `Annotation(typeId, args)`                     | An annotation instance with its type and arguments |
| `AnnotationArg.Const(value)`                   | A constant value argument                          |
| `AnnotationArg.Named(name, value)`             | A named parameter                                  |
| `AnnotationArg.ArrayArg(values)`               | An array of arguments                              |
| `AnnotationArg.Nested(annotation)`             | A nested annotation                                |
| `AnnotationArg.ClassOf(tpe)`                   | A `classOf[T]` argument                            |
| `AnnotationArg.EnumValue(enumType, valueName)` | An enum constant                                   |

**Use cases:** Annotations are commonly used to drive serialization strategies, enforce validation rules, mark types for code generation, configure persistence metadata, or enable framework-specific behavior without requiring explicit configuration objects.

## Namespaces and Owners

Every type has an `owner` — the hierarchical path that tells you where the type is defined (its package, enclosing object, or enclosing type). Owner is essential when you need to identify types by their origin, filter schemas by namespace, or build type-indexed registries that respect module boundaries.

When building cross-module systems (middleware, gateways, plugin registries, code generators), you often need to distinguish between types from different packages or modules. For example, you might want to:
- Apply different serialization strategies to types from `com.internal.domain` vs `com.external.api`
- Build a type registry keyed by both type name *and* origin package (to handle name collisions across modules)
- Validate that a deserialized type comes from a trusted package

Owner gives you the tools to make these decisions at runtime.

### Inspecting Owners

When you derive a TypeId, the `owner` property captures where the type is defined:

```scala mdoc:silent:reset
import zio.blocks.typeid._

case class MyType(x: Int)
```

```scala mdoc
val myId = TypeId.of[MyType]
myId.owner
myId.owner.asString
myId.fullName
```

Owner provides methods to inspect and navigate the hierarchy:

```scala mdoc
myId.owner.parent
myId.owner.lastName
myId.owner.isRoot
```

### Owner Structure: Packages, Terms, and Types

An Owner is a chain of segments: packages, terms (objects/values), and types. For a type defined as:

```scala
package com.example
object Outer {
  class Inner
}
```

The owner of `Inner` has three segments: `com`, `example` (packages), and `Outer` (term):

```scala mdoc:silent:reset
import zio.blocks.typeid._

object ExampleModule {
  case class Config(timeout: Int)
}
```

```scala mdoc
val configId = TypeId.of[ExampleModule.Config]
configId.owner.asString
```

### TermPath

`TermPath` represents paths to term values and is used in TypeRepr expressions for singleton types (like `obj.field.type`). Singleton type information exists at compile time but is **erased at runtime** by the JVM — both `TypeId.of[HttpStatus.OK.type]` and `TypeId.of[HttpStatus.NotFound.type]` resolve to the same underlying `Int` TypeId. TermPath is useful in type representation structures and code generators that need to capture the compile-time singleton distinction for metaprogramming.

When the macro encounters a singleton type (a `TermRef` in Scala's reflection API), it recursively walks the qualifier chain to build the term path and stores it as `TypeRepr.Singleton(path)` for use in type expressions.

Derive TypeIds for singleton values to see them resolve to their underlying type:

```scala mdoc:silent:reset
import zio.blocks.typeid._

object HttpStatus {
  val OK = 200
  val NotFound = 404
}
```

```scala mdoc
val okSingletonId = TypeId.of[HttpStatus.OK.type]
okSingletonId.name

val notFoundSingletonId = TypeId.of[HttpStatus.NotFound.type]
notFoundSingletonId.name

okSingletonId == notFoundSingletonId
```

**When to use TermPath:** In TypeRepr expressions and code generators that need to represent the compile-time path to a value. While singleton types are erased at runtime, TermPath captures this distinction for reflection and metaprogramming scenarios.

## TypeRepr — Type Expressions

`TypeRepr` represents type expressions in the Scala type system. While `TypeId` identifies a type *definition*, `TypeRepr` represents how types are *used* in expressions — as type arguments, parent types, alias targets, and more.

You encounter `TypeRepr` values when inspecting `typeArgs`, parent types in `defKind`, and alias targets:

```scala mdoc:silent:reset
import zio.blocks.typeid._
```

When you derive a TypeId for an applied type, the `typeArgs` are `TypeRepr` values representing the type arguments:

```scala mdoc
TypeId.of[Int & String].typeArgs
TypeId.of[Map[String, Int]].typeArgs
```

Here is a reference of the different `TypeRepr` variants you may encounter when inspecting TypeIds:

| Category     | Variant                                          | Example                                     |
|--------------|--------------------------------------------------|---------------------------------------------|
| **Common**   | `Ref(id)`                                        | `Int`, `String` — reference to a named type |
|              | `ParamRef(param, depth)`                         | `A` — reference to a type parameter         |
|              | `Applied(tycon, args)`                           | `List[Int]` — parameterized type            |
| **Compound** | `Intersection(types)`                            | `A & B` (Scala 3) or `A with B` (Scala 2)   |
|              | `Union(types)`                                   | `A \| B` (Scala 3)                          |
|              | `Tuple(elems)`                                   | `(A, B, C)`, named tuples                   |
|              | `Function(params, result)`                       | `(A, B) => C`                               |
|              | `ContextFunction(params, result)`                | `(A, B) ?=> C` (Scala 3)                    |
| **Special**  | `Singleton(path)`                                | `x.type`                                    |
|              | `ThisType(owner)`                                | `this.type`                                 |
|              | `TypeProjection(qualifier, name)`                | `Outer#Inner`                               |
|              | `TypeSelect(qualifier, name)`                    | `qual.Member`                               |
|              | `Structural(parents, members)`                   | `{ def foo: Int }`                          |
| **Advanced** | `TypeLambda(params, body)`                       | `[X] =>> F[X]`                              |
|              | `Wildcard(bounds)`                               | `?`, `? <: Upper`                           |
|              | `ByName(underlying)`                             | `=> A`                                      |
|              | `Repeated(element)`                              | `A*`                                        |
|              | `Annotated(underlying, annotations)`             | `A @anno`                                   |
|              | `Constant.*`                                     | `42`, `"foo"`, `true` (literal types)       |
| **Builtins** | `AnyType`, `NothingType`, `NullType`, `UnitType` | Special types                               |

## Erased TypeId

For type-indexed collections where the type parameter doesn't matter, erase it:

```scala mdoc:silent:reset
import zio.blocks.typeid._
```

```scala mdoc
val erased: TypeId.Erased = TypeId.of[Int].erased
erased
```

Erased TypeIds are the key to building type-indexed maps:

```scala mdoc
val registry: Map[TypeId.Erased, String] = Map(
  TypeId.of[Int].erased    -> "Integer type",
  TypeId.of[String].erased -> "String type"
)

registry.get(TypeId.of[Int].erased)
registry.get(TypeId.of[Double].erased)
```

## Predefined TypeIds

TypeId provides instances for common types:

**Core Interfaces:** `TypeId.charSequence` (`java.lang`), `comparable` (`java.lang`), `serializable` (`java.io`)

**Primitives:** `TypeId.unit`, `boolean`, `byte`, `short`, `int`, `long`, `float`, `double`, `char`, `string`, `bigInt`, `bigDecimal`

**Collections:** `TypeId.option`, `some`, `none`, `list`, `vector`, `set`, `seq`, `indexedSeq`, `map`, `either`, `array`, `arraySeq`, `chunk`

**java.time:** `TypeId.dayOfWeek`, `duration`, `instant`, `localDate`, `localDateTime`, `localTime`, `month`, `monthDay`, `offsetDateTime`, `offsetTime`, `period`, `year`, `yearMonth`, `zoneId`, `zoneOffset`, `zonedDateTime`

**java.util:** `TypeId.currency`, `uuid`

**Scala 3 only:** `TypeId.iarray` — `IArray[T]`, the immutable array type.

## Integration with Schema

TypeId is central to ZIO Blocks' schema system. Every `Reflect` node carries an associated TypeId:

```scala
import zio.blocks.schema._

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}
```

You can access the TypeId from a schema's reflection:

```scala
val reflect = Schema[Person].reflect
val typeId = reflect.typeId

typeId.name        // "Person"
typeId.isCaseClass // true
```

### Schema Transformations

TypeId is automatically attached when transforming schemas. The `transform` method takes an implicit `TypeId[B]` parameter, so the TypeId for the target type is resolved at compile time:

```scala
case class Email(value: String)

object Email {
  implicit val schema: Schema[Email] = Schema[String]
    .transform(Email(_), _.value)
    // TypeId[Email] is resolved implicitly — no extra call needed
}
```

### Schema Derivation

The `Deriver` trait receives a `TypeId` for each node in the schema. Methods like `deriveRecord` and `deriveVariant` include a `typeId: TypeId[A]` parameter alongside fields/cases, bindings, documentation, modifiers, and more. This lets you inspect the type's structure, annotations, and relationships when generating code.

For details on the full `Deriver` API and how to implement custom derivers, see the [Type Class Derivation](./type-class-derivation.md) reference.

## Comparison with Alternatives

TypeId occupies a different niche from the reflection and type-tagging mechanisms in the Scala ecosystem:

| Feature                           | `TypeId` | `ClassTag` | `TypeTag` (Scala 2) | `TypeTest` (Scala 3) | `Mirror` (Scala 3) |
|-----------------------------------|----------|------------|---------------------|----------------------|--------------------|
| Preserves generic type args       | Yes      | No         | Yes                 | No                   | No                 |
| Distinguishes opaque types        | Yes      | No         | No                  | No                   | No                 |
| Available on Scala.js             | Yes      | Partial    | No                  | Yes                  | Yes                |
| Cross-version (2 & 3)             | Yes      | Yes        | Scala 2 only        | Scala 3 only         | Scala 3 only       |
| Pure data (no runtime reflection) | Yes      | No         | No                  | No                   | Yes                |
| Captures annotations              | Yes      | No         | Yes                 | No                   | No                 |
| Captures variance & kind          | Yes      | No         | Yes                 | No                   | No                 |
| Subtype relationship checks       | Yes      | No         | Yes                 | Yes                  | No                 |

**When to migrate from `ClassTag`:** If you only need `ClassTag` to create arrays of the correct runtime type, keep using it — TypeId does not replace that functionality. If you are using `ClassTag` to identify or dispatch on types, TypeId provides strictly more information (generics, opaque types, annotations) and works identically on JVM and Scala.js.

**When to migrate from `TypeTag` / `WeakTypeTag`:** These are Scala 2-only, depend on `scala-reflect`, and are not available on Scala.js. TypeId captures comparable metadata (full name, type arguments, variance, annotations) as a pure data structure without runtime reflection, and works across Scala 2, Scala 3, JVM, and Scala.js.

**When to migrate from `TypeTest`:** `TypeTest` is a Scala 3 mechanism for safe pattern matching on types. It answers "is this value an instance of T?" but does not expose type structure, annotations, or generic arguments. Use TypeId when you need to inspect or serialize type metadata, not just test membership.

**When to migrate from `Mirror`:** `Mirror` provides structural information about products and sums for derivation in Scala 3. TypeId complements `Mirror` by adding namespace information (owner/package), annotations, opaque type support, and cross-version compatibility. In ZIO Blocks, the schema derivation system uses TypeId rather than `Mirror`.

## Running the Examples

All code from this guide is available as runnable examples in the `schema-examples` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

### Basic Usage

Demonstrates deriving TypeIds for case classes, accessing their properties (name, fullName, owner, arity), using predefined TypeIds for built-in types, and implicit derivation:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("schema-examples/src/main/scala/typeid/TypeIdBasicExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/typeid/TypeIdBasicExample.scala))

```bash
sbt "schema-examples/runMain typeid.TypeIdBasicExample"
```

### Subtype Relationships

Demonstrates subtype checking with `isSubtypeOf`, `isSupertypeOf`, and `isEquivalentTo`, including direct inheritance, transitive inheritance, sealed trait cases, and variance-aware subtyping for applied types like `List[Dog] <: List[Animal]`:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("schema-examples/src/main/scala/typeid/TypeIdSubtypingExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/typeid/TypeIdSubtypingExample.scala))

```bash
sbt "schema-examples/runMain typeid.TypeIdSubtypingExample"
```

### Normalization and Registries

Demonstrates type alias handling, normalization to underlying types, structural equality, and building type-indexed registries using erased TypeIds:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("schema-examples/src/main/scala/typeid/TypeIdNormalizationExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/typeid/TypeIdNormalizationExample.scala))

```bash
sbt "schema-examples/runMain typeid.TypeIdNormalizationExample"
```

### Opaque Types

Demonstrates how TypeId preserves the semantic distinction of opaque types, enabling runtime type safety that pure Scala reflection cannot provide. Shows building type-indexed validator registries keyed by opaque type identity:

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("schema-examples/src/main/scala/typeid/OpaqueTypesExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/typeid/OpaqueTypesExample.scala))

```bash
sbt "schema-examples/runMain typeid.OpaqueTypesExample"
```
