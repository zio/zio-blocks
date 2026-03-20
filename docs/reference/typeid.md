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

The primary way to obtain a `TypeId` is through the `TypeId.of[A]` macro, which extracts complete type metadata at compile time.

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

The minimal three-parameter form constructs a simple nominal TypeId when you need only name, owner, and classification. The full form allows complete control over type parameters, applied type arguments, definition kind, self-type, and annotations.

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

```scala mdoc:silent:reset
import zio.blocks.typeid._
```

```scala mdoc
val emailId = TypeId.opaque[Any]("Email", Owner.fromPackagePath("com.example"), representation = TypeRepr.Ref(TypeId.string))
emailId.isOpaque
emailId.representation
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

```scala mdoc:silent:reset
import zio.blocks.typeid._
```

Applied type with single type argument — `List[Int]`:

```scala mdoc
val listIntId = TypeId.applied[Any](TypeId.list, TypeRepr.Ref(TypeId.int))
listIntId.isApplied
listIntId.typeArgs
```

Applied type with multiple type arguments — `Map[String, Int]`:

```scala mdoc
val mapStringIntId = TypeId.applied[Any](
  TypeId.map,
  TypeRepr.Ref(TypeId.string),
  TypeRepr.Ref(TypeId.int)
)
mapStringIntId.isApplied
mapStringIntId.typeArgs
```

## Core Operations

This section documents all public methods on `TypeId` and its companion object, organized by category.

### Identity and Naming

These methods provide the type's name and fully qualified path.

#### `name` — Simple Type Name

Returns the unqualified name of the type.

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

Returns `owner.asString + "." + name`, or just `name` if the owner is root.

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

Returns the `Owner` representing the package, object, or type that contains this type definition.

```scala
sealed trait TypeId[A <: AnyKind] {
  def owner: Owner
}
```

```scala mdoc
orderId.owner.asString
TypeId.int.owner.asString
TypeId.uuid.owner.asString
```

#### `toString` — Idiomatic Scala Rendering

Renders the TypeId as idiomatic Scala syntax using `TypeIdPrinter`.

```scala mdoc
TypeId.of[List[Int]].toString
TypeId.of[Map[String, Int]].toString
```

### Type Parameters and Arguments

Methods for inspecting generic type information.

#### `typeParams` — Formal Type Parameters

Returns the list of formal type parameters declared by this type.

```scala
sealed trait TypeId[A <: AnyKind] {
  def typeParams: List[TypeParam]
}
```

```scala mdoc:silent:reset
import zio.blocks.typeid._
```

```scala mdoc
TypeId.list.typeParams
TypeId.map.typeParams
TypeId.int.typeParams
```

#### `typeArgs` — Applied Type Arguments

Returns the concrete type arguments when this is an applied type.

```scala
sealed trait TypeId[A <: AnyKind] {
  def typeArgs: List[TypeRepr]
}
```

```scala mdoc
TypeId.of[List[Int]].typeArgs
TypeId.of[Map[String, Int]].typeArgs
TypeId.list.typeArgs
```

#### `arity` — Number of Type Parameters

Returns `typeParams.size`.

```scala
sealed trait TypeId[A <: AnyKind] {
  def arity: Int
}
```

```scala mdoc
TypeId.list.arity
TypeId.map.arity
TypeId.int.arity
```

#### `isProperType` — Has No Type Parameters

Returns `true` when `arity == 0`, meaning this is a fully applied or ground type.

```scala mdoc
TypeId.int.isProperType
TypeId.of[List[Int]].isProperType
TypeId.list.isProperType
```

#### `isTypeConstructor` — Has Type Parameters

Returns `true` when `arity > 0`, meaning this type takes type parameters.

```scala mdoc
TypeId.list.isTypeConstructor
TypeId.map.isTypeConstructor
TypeId.int.isTypeConstructor
```

#### `isApplied` — Has Type Arguments

Returns `true` when `typeArgs.nonEmpty`.

```scala mdoc
TypeId.of[List[Int]].isApplied
TypeId.list.isApplied
```

### Type Classification

Predicates that inspect the `defKind` to determine what kind of type definition this is.

#### `defKind` — Type Definition Kind

Returns the `TypeDefKind` classifying this type (class, trait, object, enum, alias, opaque, etc.).

```scala
sealed trait TypeId[A <: AnyKind] {
  def defKind: TypeDefKind
}
```

```scala mdoc:silent:reset
import zio.blocks.typeid._

sealed trait Shape
case class Circle(radius: Double) extends Shape
case object Origin
```

```scala mdoc
TypeId.of[Circle].defKind
TypeId.of[Shape].defKind
TypeId.of[Origin.type].defKind
```

#### Classification Predicates

Each predicate inspects `defKind` for a specific type definition kind:

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

```scala mdoc
val shapeId  = TypeId.of[Shape]
val circleId = TypeId.of[Circle]
val originId = TypeId.of[Origin.type]

shapeId.isTrait
shapeId.isSealed
circleId.isCaseClass
originId.isObject
```

:::note
`isSealed` only checks sealed traits. A sealed abstract class or sealed enum will return `false`; use `defKind` directly to inspect those cases by pattern matching on `TypeDefKind.Class(_, isAbstract = true, ...)` or `TypeDefKind.Enum(...)`.
:::

#### Semantic Predicates

These predicates check specific semantic properties of the type after normalization:

| Predicate   | Checks                                                                |
|-------------|-----------------------------------------------------------------------|
| `isTuple`   | Normalized type is `scala.TupleN`                                     |
| `isProduct` | Normalized type is `scala.Product` or `scala.ProductN`                |
| `isSum`     | Normalized type is named `Either` or `Option` in the `scala` package (not `scala.util.Either`) |
| `isEither`  | Normalized type is `scala.util.Either`                                |
| `isOption`  | Normalized type is `scala.Option`                                     |

:::note
`isProduct` returns `true` only for Scala's built-in `scala.Product`, `scala.Product1`, etc. -- not for user-defined case classes. Use `isCaseClass` for that.
:::

:::note
`isSum` checks whether the normalized type is named `"Option"` or `"Either"` and resides directly in the `scala` package (not in a subpackage like `scala.util`). For any TypeId derived from the standard `scala.util.Either`, `isSum` returns `false` — use `isEither` instead. The `"Either"` branch of `isSum` would only match a hypothetical type named `Either` placed directly in the `scala` package. Similarly, use `isOption` for the standard `scala.Option`.
:::

### Subtype Relationships

Methods for checking type hierarchy relationships at runtime.

#### `isSubtypeOf` — Check Subtyping

Checks if this type is a subtype of another type. Handles direct inheritance, sealed trait subtypes, enum cases, transitive inheritance, and variance-aware subtyping for applied types.

```scala
sealed trait TypeId[A <: AnyKind] {
  def isSubtypeOf(other: TypeId[?]): Boolean
}
```

```scala mdoc:silent:reset
import zio.blocks.typeid._

sealed trait Animal
case class Dog(name: String) extends Animal
case class Cat(name: String) extends Animal
```

```scala mdoc
val dogId    = TypeId.of[Dog]
val animalId = TypeId.of[Animal]

dogId.isSubtypeOf(animalId)
animalId.isSubtypeOf(dogId)
```

Covariant type constructors preserve subtyping:

```scala mdoc
TypeId.of[List[Dog]].isSubtypeOf(TypeId.of[List[Animal]])
```

:::note
In Scala 2, `isSubtypeOf` does not handle `EnumCase` subtypes, types aliased to union types, or types aliased to intersection types — only the Scala 3 implementation checks those cases.
:::

#### `isSupertypeOf` — Check Supertyping

Returns `other.isSubtypeOf(this)`.

```scala
sealed trait TypeId[A <: AnyKind] {
  def isSupertypeOf(other: TypeId[?]): Boolean
}
```

```scala mdoc
animalId.isSupertypeOf(dogId)
```

#### `isEquivalentTo` — Check Type Equivalence

Returns `true` when both types are mutual subtypes: `this.isSubtypeOf(other) && other.isSubtypeOf(this)`.

```scala
sealed trait TypeId[A <: AnyKind] {
  def isEquivalentTo(other: TypeId[?]): Boolean
}
```

```scala mdoc
dogId.isEquivalentTo(dogId)
dogId.isEquivalentTo(animalId)
```

#### `parents` — Direct Parent Types

Returns the list of direct parent type representations from `defKind.baseTypes`.

```scala
sealed trait TypeId[A <: AnyKind] {
  def parents: List[TypeRepr]
}
```

```scala mdoc
dogId.parents
```

### Metadata

Methods for accessing annotations, self-type, alias target, and opaque representation.

#### `annotations` — Type Annotations

Returns the list of annotations attached to this type at compile time.

```scala
sealed trait TypeId[A <: AnyKind] {
  def annotations: List[Annotation]
}
```

```scala mdoc:silent:reset
import zio.blocks.typeid._

@transient
case class ImportantData(id: Int, payload: String)
case class Plain(x: Int)
```

```scala mdoc
TypeId.of[ImportantData].annotations.map(_.name)
TypeId.of[Plain].annotations
```

#### `selfType` — Self-Type Annotation

Returns `Some(typeRepr)` when the trait declares a self-type (e.g., `trait Foo { self: Bar => ... }`), and `None` otherwise.

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

```scala mdoc
TypeId.of[Service].selfType
TypeId.of[Logger].selfType
```

#### `aliasedTo` — Alias Target

Returns `Some(typeRepr)` for type aliases, `None` for nominal and opaque types.

```scala
sealed trait TypeId[A <: AnyKind] {
  def aliasedTo: Option[TypeRepr]
}
```

```scala mdoc:silent:reset
import zio.blocks.typeid._
```

```scala mdoc
val ageAlias = TypeId.alias[Any]("Age", Owner.Root, aliased = TypeRepr.Ref(TypeId.int))
ageAlias.aliasedTo
TypeId.int.aliasedTo
```

#### `representation` — Opaque Type Representation

Returns `Some(typeRepr)` for opaque types (the underlying representation type), `None` otherwise.

```scala
sealed trait TypeId[A <: AnyKind] {
  def representation: Option[TypeRepr]
}
```

```scala mdoc
val opaqueEmail = TypeId.opaque[Any]("Email", Owner.Root, representation = TypeRepr.Ref(TypeId.string))
opaqueEmail.representation
TypeId.int.representation
```

### Erasure and Runtime

Methods for type erasure, runtime class lookup, and reflective construction.

#### `erased` — Erase Type Parameter

Erases the phantom type parameter, returning a `TypeId.Erased` (alias for `TypeId[TypeId.Unknown]`). Use this when storing TypeIds in type-indexed maps.

```scala
sealed trait TypeId[A <: AnyKind] {
  def erased: TypeId.Erased
}
```

```scala mdoc
val erased: TypeId.Erased = TypeId.int.erased
erased
```

#### `classTag` — Runtime ClassTag

Returns a `ClassTag` for this type. Returns the correct primitive `ClassTag` for Scala primitive types and `ClassTag.AnyRef` for all reference types. Useful for creating properly-typed arrays at runtime.

```scala
sealed trait TypeId[A <: AnyKind] {
  lazy val classTag: scala.reflect.ClassTag[?]
}
```

#### `clazz` — Runtime Class

Returns the runtime `Class[_]` for this type (on the JVM only). On Scala.js, `clazz` returns `None` for all types since JVM reflection is unavailable. On the JVM, it returns `None` for alias and opaque TypeIds, and `Some(Class[_])` for nominal and applied types.

```scala
sealed trait TypeId[A <: AnyKind] {
  def clazz: Option[Class[?]]
}
```

:::note
The JVM behavior (returning the correct `Class[_]` for nominal and applied types) is verified by test annotations like `@jvmOnly`. On Scala.js, the method always returns `None`.
:::

#### `construct` — Reflective Construction

Constructs an instance using the primary constructor on the JVM, or returns `Left` with an error message on Scala.js or when construction fails.

```scala
sealed trait TypeId[A <: AnyKind] {
  def construct(args: Chunk[AnyRef]): Either[String, Any]
}
```

Primitive values must be explicitly boxed when passed to `construct`. In Scala 2, unboxed primitives like `30` do not auto-box to `AnyRef`:

```scala
// JVM example (not runnable in mdoc due to platform specificity)
val personId = TypeId.of[Person]
personId.clazz            // Some(class Person) on JVM, None on Scala.js
personId.construct(Chunk("Alice", 30: Integer))  // Right(Person(Alice,30)) on JVM
```

:::note
Special calling conventions apply to certain types:

**Collection types** accept variadic arguments representing elements:
- **Sequence-like types** (`List`, `Vector`, `Set`, `Seq`, `IndexedSeq`, `Array`, `ArraySeq`, `Chunk`): Pass a variadic sequence of elements. For example, `construct(Chunk("a", "b", "c"))` returns `Right(List("a", "b", "c"))`.
- **Map:** Pass interleaved key-value pairs and fails on odd argument counts. For example, `construct(Chunk("a", 1: Integer, "b", 2: Integer))` returns `Right(Map("a" -> 1, "b" -> 2))`.

**Sum types** have special calling conventions:
- **Option:** Pass 1 element to construct `Some(value)`, or 0 elements to construct `None`.
- **Either:** First argument is a Boolean flag (`true` = `Right`, `false` = `Left`), followed by the value. For example, `construct(Chunk(true: java.lang.Boolean, value))` for `Right(value)`, or `construct(Chunk(false: java.lang.Boolean, value))` for `Left(value)`.
:::

### Normalization and Equality

Companion object methods for normalization, equality checking, and type constructor stripping.

#### `TypeId.normalize` — Resolve Aliases

Resolves chains of type aliases to the underlying type. For example, `type MyList = List[Int]` normalizes to `List[Int]`.

```scala
object TypeId {
  def normalize(id: TypeId[?]): TypeId[?]
}
```

```scala mdoc:silent:reset
import zio.blocks.typeid._
```

```scala mdoc
val alias = TypeId.alias[Any]("Age", Owner.Root, aliased = TypeRepr.Ref(TypeId.int))
val norm = TypeId.normalize(alias)
norm.fullName
```

#### `TypeId.structurallyEqual` — Structural Equality

Checks if two TypeIds are structurally equal after normalization. Semantically equivalent to `==` on TypeId instances; `==` additionally short-circuits on hash mismatch for performance.

```scala
object TypeId {
  def structurallyEqual(a: TypeId[?], b: TypeId[?]): Boolean
}
```

```scala mdoc
val a = TypeId.alias[Any]("X", Owner.Root, aliased = TypeRepr.Ref(TypeId.int))
val b = TypeId.alias[Any]("X", Owner.Root, aliased = TypeRepr.Ref(TypeId.int))
TypeId.structurallyEqual(a, b)
a == b
```

#### `TypeId.structuralHash` — Structural Hash Code

Computes a hash code based on the normalized structural representation.

```scala
object TypeId {
  def structuralHash(id: TypeId[?]): Int
}
```

#### `TypeId.unapplied` — Strip Type Arguments

Returns the type constructor by stripping all type arguments. For example, `TypeId.unapplied(TypeId.of[List[Int]])` returns the equivalent of `TypeId.of[List]`.

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

The companion object provides extractors for pattern matching on TypeId classification.

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

| Extractor                                          | Matches                  |
|----------------------------------------------------|--------------------------|
| `TypeId.Nominal(name, owner, params, defKind, parents)` | Classes, traits, objects |
| `TypeId.Alias(name, owner, params, aliased)`       | Type aliases             |
| `TypeId.Opaque(name, owner, params, repr, bounds)` | Opaque types             |
| `TypeId.Sealed(name)`                              | Sealed traits            |
| `TypeId.Enum(name, owner)`                         | Scala 3 enums            |

## TypeDefKind Reference

`TypeDefKind` classifies every type definition. Access it via the `defKind` property documented in [Core Operations](#type-classification).

The `defKind` property (documented in [Core Operations](#type-classification)) returns one of these variants. Use classification predicates like `isCaseClass`, `isSealed`, `isObject` for simple checks.

`TypeDefKind` has these variants:

| Variant                                              | Description                     |
|------------------------------------------------------|---------------------------------|
| `Class(isFinal, isAbstract, isCase, isValue, bases)` | Class definitions               |
| `Trait(isSealed, bases)`                             | Trait definitions               |
| `Object(bases)`                                      | Singleton objects               |
| `Enum(bases)`                                        | Scala 3 enums                   |
| `EnumCase(parentEnum, ordinal, isObjectCase)`        | Enum cases                      |
| `TypeAlias`                                          | Type aliases (`type Foo = Bar`) |
| `OpaqueType(publicBounds)`                           | Opaque types                    |
| `AbstractType`                                       | Abstract type members           |
| `Unknown`                                            | Unclassified or unresolvable type definition |

## Type Parameters and Generics

When you derive a TypeId for a generic type, the macro captures its type parameters (variance, bounds, kind) and any applied type arguments.

:::warning
Passing a raw type constructor (e.g. `TypeId.of[Container]`) requires Scala 3. In Scala 2, the `TypeId.of[A]` signature has bound `A` rather than `A <: AnyKind`, so raw type constructors like `Container` (a `* -> *` kind) are not valid Scala 2 syntax. On Scala 2, use `TypeId.of[Container[_]]` or retrieve the TypeId through implicit derivation instead.
:::

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

Variance values are `Covariant` (+), `Contravariant` (-), and `Invariant`:

```scala mdoc
Variance.Covariant.symbol
Variance.Contravariant.symbol
Variance.Invariant.symbol
Variance.Covariant.flip
```

### Kind

Kind describes the "type of a type" — whether it's a proper type or a type constructor. Derive TypeIds for types of different kinds to see this in action:

A proper type (`*`) — takes no type parameters:

```scala mdoc
val properTypeId = TypeId.of[Box[Int]]
properTypeId.typeParams.map(_.kind)
properTypeId.arity
```

A unary type constructor (`* -> *`) — takes one type parameter:

```scala mdoc
val containerKind = TypeId.of[Container].typeParams.map(_.kind)
containerKind
TypeId.of[Container].arity
```

A binary type constructor (`* -> * -> *`) — takes two type parameters:

```scala mdoc
val cacheKind = TypeId.of[Cache].typeParams.map(_.kind)
cacheKind
TypeId.of[Cache].arity
```

A higher-kinded type (`(* -> *) -> *`) — a type parameter that itself takes a type parameter:

```scala mdoc:silent
trait Runnable[F[_]] {
  def run[A](fa: F[A]): A
}
```

```scala mdoc
val runnableId = TypeId.of[Runnable]
runnableId.typeParams.map(_.kind)
runnableId.typeParams.head.kind.arity
```

| Kind                      | Notation        | Arity | Examples              |
|---------------------------|-----------------|-------|-----------------------|
| `Kind.Type` / `Kind.Star` | `*`             | 0     | `Int`, `Box[Int]`     |
| `Kind.Star1`              | `* -> *`        | 1     | `Container`, `Option` |
| `Kind.Star2`              | `* -> * -> *`   | 2     | `Cache`, `Either`     |
| `Kind.HigherStar1`        | `(* -> *) -> *` | 1     | `Runnable`            |

## Subtype Relationships

TypeId can determine inheritance relationships at runtime, handling direct inheritance, sealed trait subtypes, transitive inheritance, and variance-aware subtyping for applied types.

```scala mdoc:silent:reset
import zio.blocks.typeid._

sealed trait Animal
case class Dog(name: String) extends Animal
case class Cat(name: String) extends Animal
```

```scala mdoc
val dogId    = TypeId.of[Dog]
val animalId = TypeId.of[Animal]

dogId.isSubtypeOf(animalId)
animalId.isSupertypeOf(dogId)
dogId.isEquivalentTo(dogId)
dogId.isEquivalentTo(animalId)
```

Covariant type constructors preserve subtyping:

```scala mdoc
val listDogId    = TypeId.of[List[Dog]]
val listAnimalId = TypeId.of[List[Animal]]
listDogId.isSubtypeOf(listAnimalId)
```

## Annotations

TypeId captures annotations attached to types at compile time, making them available at runtime as data.

```scala mdoc:silent:reset
import zio.blocks.typeid._

@transient
case class ImportantData(id: Int, payload: String)
```

Derive the TypeId and inspect its annotations:

```scala mdoc
val importantId = TypeId.of[ImportantData]
importantId.annotations
importantId.annotations.map(_.name)
```

An unannotated type has no annotations:

```scala mdoc:silent
case class Plain(x: Int)
```

```scala mdoc
TypeId.of[Plain].annotations
```

### Annotation Data Model

Each `Annotation` contains the annotation's `TypeId` and a list of `AnnotationArg` values:

| Type                                           | Description                                        |
|------------------------------------------------|----------------------------------------------------|
| `Annotation(typeId, args)`                     | An annotation instance with its type and arguments |
| `AnnotationArg.Const(value)`                   | A constant value argument                          |
| `AnnotationArg.Named(name, value)`             | A named parameter                                  |
| `AnnotationArg.ArrayArg(values)`               | An array of arguments                              |
| `AnnotationArg.Nested(annotation)`             | A nested annotation                                |
| `AnnotationArg.ClassOf(tpe)`                   | A `classOf[T]` argument                            |
| `AnnotationArg.EnumValue(enumType, valueName)` | An enum constant                                   |

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

You can construct nested owners using the `term()` and `tpe()` methods:

```scala mdoc
val moduleOwner = Owner.fromPackagePath("com.example").term("ExampleModule")
moduleOwner.asString
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

Construct a TermPath manually to represent a term value path:

```scala mdoc
val okPath = TermPath.fromOwner(
  Owner.Root.term("HttpStatus"),
  "OK"
)
okPath.asString

val nestedPath = okPath / "metadata"
nestedPath.asString
```

**When to use TermPath:** In TypeRepr expressions and code generators that need to represent the compile-time path to a value. While singleton types are erased at runtime, TermPath captures this distinction for reflection and metaprogramming scenarios.

### Constructing Owners for Common Packages

To construct owners for common packages, use `Owner.fromPackagePath`:

```scala mdoc
Owner.fromPackagePath("scala")
Owner.fromPackagePath("scala.collection.immutable")
Owner.fromPackagePath("java.lang")
Owner.fromPackagePath("java.time")
```

## TypeRepr — Type Expressions

`TypeRepr` represents type expressions in the Scala type system. While `TypeId` identifies a type *definition*, `TypeRepr` represents how types are *used* in expressions — as type arguments, parent types, alias targets, and more.

You encounter `TypeRepr` values when inspecting `typeArgs`, parent types in `defKind`, and alias targets:

```scala mdoc:silent:reset
import zio.blocks.typeid._
```

```scala mdoc
TypeId.of[List[Int]].typeArgs
TypeId.of[Map[String, Int]].typeArgs
```

### TypeRepr Variants

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

## Opaque Types and Type Aliases

### Opaque Types

TypeId preserves the distinction of opaque types — they are not erased to their representation type:

```scala mdoc:silent:reset
import zio.blocks.typeid._

opaque type UserId = String
opaque type Email = String
```

```scala mdoc
val userIdType = TypeId.of[UserId]
val emailType  = TypeId.of[Email]

userIdType.name
userIdType.isOpaque

userIdType.isEquivalentTo(TypeId.string)
userIdType.isEquivalentTo(emailType)
```

This enables type-safe registries keyed by opaque type — see the [Erased TypeId](#erased-typeid-and-registries) section.

### Type Aliases and Normalization

Type aliases can be normalized to their underlying types:

```scala mdoc:silent:reset
import zio.blocks.typeid._

type Age = Int
val ageId = TypeId.alias[Age]("Age", Owner.Root, Nil, TypeRepr.Ref(TypeId.int))
```

```scala mdoc
ageId.name
ageId.isAlias

val normalized = TypeId.normalize(ageId)
normalized.fullName
```

Normalization resolves chains of aliases (e.g., `type MyIntList = IntList` where `type IntList = List[Int]` resolves to `List[Int]`).

## Erased TypeId and Registries

### Erased TypeId

For type-indexed collections where the type parameter doesn't matter, erase it:

```scala mdoc:silent:reset
import zio.blocks.typeid._
```

```scala mdoc
val erased: TypeId.Erased = TypeId.int.erased
erased
```

### Equality and Hashing

TypeId uses structural equality:

```scala mdoc
val alias1 = TypeId.alias[Int]("A", Owner.Root, Nil, TypeRepr.Ref(TypeId.int))
val alias2 = TypeId.alias[Int]("A", Owner.Root, Nil, TypeRepr.Ref(TypeId.int))

alias1 == alias2

val map = Map(alias1 -> "value")
map.get(alias2)
```

### Building Registries

Erased TypeIds are the key to building type-indexed maps:

```scala mdoc
val registry: Map[TypeId.Erased, String] = Map(
  TypeId.int.erased    -> "Integer type",
  TypeId.string.erased -> "String type"
)

registry.get(TypeId.int.erased)
registry.get(TypeId.double.erased)
```

### Runtime Reflection

The `clazz` and `construct` methods are available on all platforms. On the JVM, `clazz` returns the corresponding `Class[_]` and `construct` uses reflection to create instances. On Scala.js, both methods are no-ops — `clazz` returns `None` and `construct` returns `Left` with an error message.

Note: Primitive values must be explicitly boxed when passed to `construct` (e.g., `30: Integer` instead of `30`).

```scala
val typeId = TypeId.of[Person]
val clazz: Option[Class[_]] = typeId.clazz            // Some(...) on JVM, None on Scala.js

val result: Either[String, Any] = typeId.construct(Chunk("Alice", 30: Integer))  // Right(...) on JVM, Left(...) on Scala.js
```

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

## Predefined TypeIds

TypeId provides instances for common types:

**Core Interfaces:** `TypeId.charSequence` (`java.lang`), `comparable` (`java.lang`), `serializable` (`java.io`)

**Primitives:** `TypeId.unit`, `boolean`, `byte`, `short`, `int`, `long`, `float`, `double`, `char`, `string`, `bigInt`, `bigDecimal`

**Collections:** `TypeId.option`, `some`, `none`, `list`, `vector`, `set`, `seq`, `indexedSeq`, `map`, `either`, `array`, `arraySeq`, `chunk`

**java.time:** `TypeId.dayOfWeek`, `duration`, `instant`, `localDate`, `localDateTime`, `localTime`, `month`, `monthDay`, `offsetDateTime`, `offsetTime`, `period`, `year`, `yearMonth`, `zoneId`, `zoneOffset`, `zonedDateTime`

**java.util:** `TypeId.currency`, `uuid`

**Scala 3 only:** `TypeId.iarray` — `IArray[T]`, the immutable array type.

## Comparison with Alternatives

TypeId occupies a different niche from the reflection and type-tagging mechanisms in the Scala ecosystem:

| Feature | `TypeId` | `ClassTag` | `TypeTag` (Scala 2) | `TypeTest` (Scala 3) | `Mirror` (Scala 3) |
|---|---|---|---|---|---|
| Preserves generic type args | Yes | No | Yes | No | No |
| Distinguishes opaque types | Yes | No | No | No | No |
| Available on Scala.js | Yes | Partial | No | Yes | Yes |
| Cross-version (2 & 3) | Yes | Yes | Scala 2 only | Scala 3 only | Scala 3 only |
| Pure data (no runtime reflection) | Yes | No | No | No | Yes |
| Captures annotations | Yes | No | Yes | No | No |
| Captures variance & kind | Yes | No | Yes | No | No |
| Subtype relationship checks | Yes | No | Yes | Yes | No |

**When to migrate from `ClassTag`:** If you only need `ClassTag` to create arrays of the correct runtime type, keep using it — TypeId does not replace that functionality. If you are using `ClassTag` to identify or dispatch on types, TypeId provides strictly more information (generics, opaque types, annotations) and works identically on JVM and Scala.js.

**When to migrate from `TypeTag` / `WeakTypeTag`:** These are Scala 2-only, depend on `scala-reflect`, and are not available on Scala.js. TypeId captures comparable metadata (full name, type arguments, variance, annotations) as a pure data structure without runtime reflection, and works across Scala 2, Scala 3, JVM, and Scala.js.

**When to migrate from `TypeTest`:** `TypeTest` is a Scala 3 mechanism for safe pattern matching on types. It answers "is this value an instance of T?" but does not expose type structure, annotations, or generic arguments. Use TypeId when you need to inspect or serialize type metadata, not just test membership.

**When to migrate from `Mirror`:** `Mirror` provides structural information about products and sums for derivation in Scala 3. TypeId complements `Mirror` by adding namespace information (owner/package), annotations, opaque type support, and cross-version compatibility. In ZIO Blocks, the schema derivation system uses TypeId rather than `Mirror`.

## Advanced — Manual Construction

For testing, code generation, or manual type registration, TypeId provides smart constructors that bypass macro derivation.

### Nominal Types

The `TypeId.of` macro expands into `TypeId.nominal(...)` calls for classes, traits, and objects. When the type has no type parameters, type arguments, self-type, or annotations, the macro uses the three-parameter overload (`name`, `owner`, `kind`). For example, `TypeId.of[Int]` generates the equivalent of:

```scala mdoc:compile-only
import zio.blocks.typeid._

TypeId.nominal[Int](
  name = "Int",
  owner = Owner.fromPackagePath("scala"),
  kind = TypeDefKind.Unknown
)
```

When the type has type parameters, self-types, or annotations, the macro uses the full overload. Note that this overload names the parameter `defKind` (not `kind`). For example, for a sealed trait with a covariant type parameter like `sealed trait Container[+A]`, the macro generates the equivalent of:

```scala mdoc:compile-only
import zio.blocks.typeid._

TypeId.nominal[Any](
  name = "Container",
  owner = Owner.fromPackagePath("com.example"),
  typeParams = List(TypeParam.covariant("A", 0)),
  typeArgs = Nil,
  defKind = TypeDefKind.Trait(isSealed = true),
  selfType = None,
  annotations = Nil
)
```

### Type Aliases and Opaque Types

```scala mdoc:compile-only
import zio.blocks.typeid._

val aliasId = TypeId.alias[Any](
  name = "Age",
  owner = Owner.fromPackagePath("com.example"),
  aliased = TypeRepr.Ref(TypeId.int)
)

val opaqueId = TypeId.opaque[Any](
  name = "Email",
  owner = Owner.fromPackagePath("com.example"),
  representation = TypeRepr.Ref(TypeId.string)
)
```

### Applied Types

```scala mdoc:compile-only
import zio.blocks.typeid._

// List[Int]
val listIntId = TypeId.applied[List[Int]](
  TypeId.list,
  TypeRepr.Ref(TypeId.int)
)

// Map[String, Int]
val mapId = TypeId.applied[Map[String, Int]](
  TypeId.map,
  TypeRepr.Ref(TypeId.string),
  TypeRepr.Ref(TypeId.int)
)
```

### TypeRepr Construction

For building type expressions programmatically (e.g., in code generators):

```scala mdoc:compile-only
import zio.blocks.typeid._

// Basic references
TypeRepr.Ref(TypeId.int)
TypeRepr.ParamRef(TypeParam("A", 0))

// Applied: List[Int]
TypeRepr.Applied(
  TypeRepr.Ref(TypeId.list),
  List(TypeRepr.Ref(TypeId.int))
)

// Compound types
TypeRepr.Intersection(List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string)))
TypeRepr.Union(List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string)))
TypeRepr.Function(List(TypeRepr.Ref(TypeId.int)), TypeRepr.Ref(TypeId.string))
TypeRepr.tuple(List(TypeRepr.Ref(TypeId.int), TypeRepr.Ref(TypeId.string)))

// Special types
TypeRepr.AnyType
TypeRepr.NothingType
TypeRepr.Wildcard(TypeBounds.upper(TypeRepr.Ref(TypeId.int)))
TypeRepr.ByName(TypeRepr.Ref(TypeId.int))
TypeRepr.Repeated(TypeRepr.Ref(TypeId.int))

// Literal types
TypeRepr.Constant.IntConst(42)
TypeRepr.Constant.StringConst("foo")
```

### TypeParam Construction

```scala mdoc:compile-only
import zio.blocks.typeid._

TypeParam("A", index = 0)                                  // Invariant A
TypeParam.covariant("A", 0)                                // +A
TypeParam.contravariant("A", 0)                            // -A
TypeParam.bounded("A", 0, upper = TypeRepr.Ref(TypeId.int)) // A <: Int
TypeParam.higherKinded("F", 0, arity = 1)                  // F[_]
```

### Annotation Construction

For testing annotation-based dispatch without actual annotated types:

```scala mdoc:compile-only
import zio.blocks.typeid._

Annotation(
  typeId = TypeId.int, // Simplified — would normally be the annotation's TypeId
  args = List(
    AnnotationArg.Named("message", AnnotationArg.Const("use newMethod")),
    AnnotationArg.Named("since", AnnotationArg.Const("1.0"))
  )
)
```

### Members (Structural Types)

For constructing structural type representations:

```scala mdoc:compile-only
import zio.blocks.typeid._

// { val x: Int; def foo(y: String): Boolean }
TypeRepr.Structural(
  parents = Nil,
  members = List(
    Member.Val("x", TypeRepr.Ref(TypeId.int)),
    Member.Def(
      name = "foo",
      typeParams = Nil,
      paramLists = List(List(Param("y", TypeRepr.Ref(TypeId.string)))),
      result = TypeRepr.Ref(TypeId.boolean)
    )
  )
)
```

## Running the Examples

All code from this guide is available as runnable examples in the `schema-examples` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

### Basic Usage

Demonstrates deriving TypeIds for case classes, accessing their properties (name, fullName, owner, arity), using predefined TypeIds for built-in types, and implicit derivation.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("schema-examples/src/main/scala/typeid/TypeIdBasicExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/typeid/TypeIdBasicExample.scala))

```bash
sbt "schema-examples/runMain typeid.TypeIdBasicExample"
```

### Subtype Relationships

Demonstrates subtype checking with `isSubtypeOf`, `isSupertypeOf`, and `isEquivalentTo`, including direct inheritance, transitive inheritance, sealed trait cases, and variance-aware subtyping for applied types like `List[Dog] <: List[Animal]`.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("schema-examples/src/main/scala/typeid/TypeIdSubtypingExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/typeid/TypeIdSubtypingExample.scala))

```bash
sbt "schema-examples/runMain typeid.TypeIdSubtypingExample"
```

### Normalization and Registries

Demonstrates type alias handling, normalization to underlying types, structural equality, and building type-indexed registries using erased TypeIds.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("schema-examples/src/main/scala/typeid/TypeIdNormalizationExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/typeid/TypeIdNormalizationExample.scala))

```bash
sbt "schema-examples/runMain typeid.TypeIdNormalizationExample"
```

### Opaque Types

Demonstrates how TypeId preserves the semantic distinction of opaque types, enabling runtime type safety that pure Scala reflection cannot provide. Shows building type-indexed validator registries keyed by opaque type identity.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("schema-examples/src/main/scala/typeid/OpaqueTypesExample.scala")
```

([source](https://github.com/zio/zio-blocks/blob/main/schema-examples/src/main/scala/typeid/OpaqueTypesExample.scala))

```bash
sbt "schema-examples/runMain typeid.OpaqueTypesExample"
```
