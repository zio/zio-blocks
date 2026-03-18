---
id: typeid
title: "TypeId"
---

`TypeId[A]` represents the identity of a type or type constructor at runtime — it captures complete type metadata (names, type parameters, parent types, annotations, classification) that would otherwise be erased by the JVM and Scala.js. Use `TypeId` when you need to preserve full type information as data for serialization, code generation, registry lookups, or type-safe dispatching.

In Scala and the JVM, compile-time type information is erased at runtime. This means generic type parameters, sealed trait variants, and even opaque types become indistinguishable at runtime — `List[Int]` and `List[String]` both look like `List` to the JVM. This erasure makes it nearly impossible to implement universal serializers that work across formats (JSON, YAML, XML, MessagePack), code generators, or schema-driven transformations without losing semantic information. `TypeId` solves this by capturing complete type structure at compile time and making it available as a hashable, inspectable value at runtime.

When to use `TypeId` vs alternatives:

| Use Case | Best Choice |
|---|---|
| Encode type info as data for serialization, code generation, or dispatch  | `TypeId`           |
| Create arrays of the correct runtime type                                   | `ClassTag`         |
| Check if a single value matches a known type                                | Pattern match with `TypeTest` |
| Manually handle a handful of hardcoded types                                | Hard-coded `if/else` |

The `TypeId` trait exposes the type's structure through a rich set of properties and predicates:

```scala
sealed trait TypeId[A <: AnyKind] {
  def name: String
  def fullName: String
  def owner: Owner
  def arity: Int
  def typeParams: List[TypeParam]
  def typeArgs: List[TypeRepr]

  def isCaseClass: Boolean
  def isSealed: Boolean
  def isAlias: Boolean
  // ... many more properties
}
```

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

### Why TypeId Exists

In Scala, type information is erased at runtime — the JVM and JavaScript runtimes do not preserve generic type parameters or compile-time type structure. This erasure makes it difficult to implement universal serialization (JSON, binary formats, XML, YAML), code generation, and schema-based transformations that rely on full type knowledge.

`TypeId[A]` solves this problem by capturing and preserving complete type metadata at compile time, making it available as a pure data structure at runtime. This metadata includes the type's name, package location, type parameters, variance, parent types, annotations, and classification (case class, sealed trait, enum, etc.).

### Real-World Use Cases

#### Schema-Driven Serialization

TypeId is central to ZIO Blocks' universal schema system. Every schema derivation receives a TypeId so that codec generators (for JSON, YAML, MessagePack, binary formats, XML) can decide how to serialize and deserialize data:

```
                    TypeId[Person]
                        │
        ┌───────────────┼───────────────┐
        ▼               ▼               ▼
    JsonBinary      YamlBinary      XmlBinary
    Codec[Person]  Codec[Person]   Codec[Person]
```

Code generators use TypeId metadata to emit specialized, efficient codecs. For example, a JSON codec generator inspects whether a type is a case class (preserving field names), a sealed trait (handling variants), or an opaque type (wrapping the underlying type).

#### Type-Indexed Registries and Lookup

Applications often maintain registries of schemas or validators keyed by type. TypeId provides a pure, hashable data structure for this:

```scala
import zio.blocks.typeid._

case class Person(name: String, age: Int)
case class Order(id: String, total: Double)

// Build a registry of type IDs
val typeRegistry: Map[TypeId.Erased, String] = Map(
  TypeId.of[Person].erased -> "Person schema",
  TypeId.of[Order].erased -> "Order schema",
  TypeId.of[String].erased -> "String schema"
)

// Later, look up metadata by type at runtime
def getMetadata(typeId: TypeId[?]): Option[String] =
  typeRegistry.get(typeId.erased)
```

#### Type-Safe Polymorphic Operations

When working with sealed type hierarchies, TypeId allows you to dispatch to the correct handler based on type relationships:

```scala
import zio.blocks.typeid._

sealed trait Animal
case class Dog(name: String) extends Animal
case class Cat(name: String) extends Animal

val dogId = TypeId.of[Dog]
val catId = TypeId.of[Cat]
val animalId = TypeId.of[Animal]

// Check inheritance at runtime
if (dogId.isSubtypeOf(animalId)) {
  println("Dog is a subtype of Animal")
}

// Or match on type relationships
def handleAnimal(typeId: TypeId[? <: Animal]): String = typeId match {
  case t if t.isEquivalentTo(dogId) => "Handling dog"
  case t if t.isEquivalentTo(catId) => "Handling cat"
  case _ => "Handling unknown animal"
}
```

#### Opaque Types

TypeId preserves the distinction of opaque types, treating them as distinct from their representation type. This enables runtime type checking that respects the semantic boundaries that opaque types provide.

Opaque types are a Scala 3 feature that allow you to create distinct types that have a different representation at runtime. TypeId captures this distinction, unlike pure Scala reflection which cannot distinguish opaque types from their underlying types. Here's the concrete difference:

```scala
// Define opaque types wrapping String
opaque type UserId = String
opaque type Email = String

// Pure Scala reflection (cannot distinguish)
classOf[UserId] == classOf[String]  // true — erased to String
classOf[Email] == classOf[String]   // true — erased to String
// Problem: cannot distinguish UserId from Email or String at runtime

// TypeId (preserves distinction)
TypeId.of[UserId] != TypeId.of[String]  // true — preserved
TypeId.of[Email] != TypeId.of[String]   // true — preserved
TypeId.of[UserId] != TypeId.of[Email]   // true — opaque types are distinct

// Real-world use case: type-safe validator registry
val validators: Map[TypeId.Erased, String => Boolean] = Map(
  TypeId.of[UserId].erased -> { id => id.nonEmpty && id.forall(_.isDigit) },
  TypeId.of[Email].erased -> { email => email.contains("@") && email.contains(".") }
)

// Dispatch to correct validator based on opaque type
def validate(value: String, typeId: TypeId[_]): Boolean =
  validators.get(typeId.erased).map(_(value)).getOrElse(false)

validate("12345", TypeId.of[UserId])              // Checks digits only
validate("user@example.com", TypeId.of[Email])    // Checks email format
```

### TypeId and the Schema Stack

TypeId is fundamental to ZIO Blocks' schema system. Here's where it fits in the stack:

```
         ┌─────────────────────────────────────┐
         │             Schema[A]               │
         │          (Reflect[F, A])            │
         └──────────────┬──────────────────────┘
                        │ carries
                        ▼
         ┌─────────────────────────────────────┐
         │            TypeId[A]                │
         │  name · owner · kind · typeParams   │
         │  subtypes · annotations · aliases   │
         └──────────────────────────────────────┘
```

Every record and variant derivation receives the TypeId so you can inspect the type's structure, annotations, and relationships when generating code.

## Installation

TypeId is included in the `zio-blocks-typeid` module. Add it to your build:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-typeid" % "<version>"
```

Cross-platform support: TypeId works on JVM and Scala.js.

## Deriving and Inspecting TypeIds

The primary way to use TypeId is to **derive** it for your types, then **inspect** the result. The `TypeId.of` macro extracts complete type information at compile time.

### Basic Derivation

```scala mdoc:silent:reset
import zio.blocks.typeid._

case class User(id: Long, email: String)
```

Derive a TypeId using the `TypeId.of` macro:

```scala mdoc
val userId = TypeId.of[User]
```

Or use implicit derivation:

```scala mdoc
val userIdImplicit = implicitly[TypeId[User]]
```

### Inspecting Properties

Once derived, inspect the type's structure through its properties:

```scala mdoc
userId.name
userId.fullName
userId.owner.asString
userId.arity
userId.isCaseClass
userId.isProperType
userId.isTypeConstructor
```

### Pattern Matching

TypeId provides extractors for pattern matching on derived types:

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
- `TypeId.Nominal(name, owner, params, defKind, parents)` — classes, traits, objects
- `TypeId.Alias(name, owner, params, aliased)` — type aliases
- `TypeId.Opaque(name, owner, params, repr, bounds)` — opaque types
- `TypeId.Sealed(name)` — sealed traits
- `TypeId.Enum(name, owner)` — Scala 3 enums

## Type Classification

TypeId captures what *kind* of type definition each type is — class, trait, object, enum, etc. Derive a TypeId and use classification predicates or inspect `defKind` directly.

```scala mdoc:silent:reset
import zio.blocks.typeid._

sealed trait Shape
case class Circle(radius: Double) extends Shape
case class Square(side: Double) extends Shape
case object Origin
```

```scala mdoc
val shapeId  = TypeId.of[Shape]
val circleId = TypeId.of[Circle]
val originId = TypeId.of[Origin.type]
```

### Classification Predicates

```scala mdoc
shapeId.isTrait
shapeId.isSealed

circleId.isClass
circleId.isCaseClass

originId.isObject
```

### The `defKind` Property

For more detail, inspect `defKind` directly:

```scala mdoc
circleId.defKind
shapeId.defKind
originId.defKind
```

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

Full list of classification predicates: `isClass`, `isTrait`, `isObject`, `isEnum`, `isAlias`, `isOpaque`, `isAbstract`, `isSealed`, `isCaseClass`, `isValueClass`, `isTuple`, `isProduct`, `isSum`, `isOption`, `isEither`, `isProperType`, `isTypeConstructor`, `isApplied`.

## Type Parameters and Generics

When you derive a TypeId for a generic type, the macro captures its type parameters (variance, bounds, kind) and any applied type arguments.

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

| Kind | Notation | Arity | Examples |
|---|---|---|---|
| `Kind.Type` / `Kind.Star` | `*` | 0 | `Int`, `Box[Int]` |
| `Kind.Star1` | `* -> *` | 1 | `Container`, `Option` |
| `Kind.Star2` | `* -> * -> *` | 2 | `Cache`, `Either` |
| `Kind.HigherStar1` | `(* -> *) -> *` | 1 | `Runnable` |

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

Types are organized hierarchically. The `owner` property tells you where a type is defined.

### Inspecting Owners

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

### Building Owners

For comparisons or manual construction, build owners with `Owner.fromPackagePath` or the `/` combinator:

```scala mdoc
val owner1 = Owner.fromPackagePath("com.example.app")
owner1.asString
owner1.parent.asString
owner1.lastName
```

```scala mdoc
val owner2 = Owner.Root / "com" / "example"
owner2.asString
```

### Predefined Owners

Common package namespaces are available as predefined owners (used internally by TypeId):

| Owner                            | Package                      |
|----------------------------------|------------------------------|
| `Owner.scala`                    | `scala`                      |
| `Owner.scalaUtil`                | `scala.util`                 |
| `Owner.scalaCollectionImmutable` | `scala.collection.immutable` |
| `Owner.javaLang`                 | `java.lang`                  |
| `Owner.javaTime`                 | `java.time`                  |
| `Owner.javaUtil`                 | `java.util`                  |

### TermPath

`TermPath` represents paths to term values, used for singleton types:

```scala mdoc
val path = TermPath.fromOwner(
  Owner.fromPackagePath("com.example").term("MyObject"),
  "value"
)
path.asString
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

On JVM, TypeId can retrieve the corresponding `Class` and construct instances:

```scala
val typeId = TypeId.of[Person]
val clazz: Option[Class[_]] = typeId.clazz

// Construct instances (JVM only)
val result: Either[String, Any] = typeId.construct(Chunk("Alice", 30))
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

TypeId is preserved when transforming schemas:

```scala
case class Email(value: String)

object Email {
  implicit val schema: Schema[Email] = Schema[String]
    .transform(Email(_), _.value)
    .withTypeName[Email]  // Sets TypeId to Email
}
```

### Schema Derivation

The `Deriver` trait receives TypeId for each node in the schema:

```scala
trait Deriver[TC[_]] {
  def deriveRecord[A](
    typeId: TypeId[A],
    fields: => Chunk[Deriver.Field[TC, A, _]]
  ): TC[A]

  def deriveVariant[A](
    typeId: TypeId[A],
    cases: => Chunk[Deriver.Case[TC, A, _]]
  ): TC[A]
}
```

Every record and variant derivation receives the TypeId so you can inspect the type's structure, annotations, and relationships when generating code. Note: The actual `Deriver` API includes additional parameters like `binding`, `doc`, and `modifiers` — see the source for the complete signature.

## Predefined TypeIds

TypeId provides instances for common types:

**Primitives:** `TypeId.unit`, `boolean`, `byte`, `short`, `int`, `long`, `float`, `double`, `char`, `string`, `bigInt`, `bigDecimal`

**Collections:** `TypeId.option`, `some`, `none`, `list`, `vector`, `set`, `seq`, `indexedSeq`, `map`, `either`, `array`, `arraySeq`, `chunk`

**java.time:** `TypeId.dayOfWeek`, `duration`, `instant`, `localDate`, `localDateTime`, `localTime`, `month`, `monthDay`, `offsetDateTime`, `offsetTime`, `period`, `year`, `yearMonth`, `zoneId`, `zoneOffset`, `zonedDateTime`

**java.util:** `TypeId.currency`, `uuid`

## Advanced — Manual Construction

For testing, code generation, or manual type registration, TypeId provides smart constructors that bypass macro derivation.

### Nominal Types

```scala mdoc:compile-only
import zio.blocks.typeid._

val myTypeId = TypeId.nominal[Any](
  name = "MyType",
  owner = Owner.fromPackagePath("com.example"),
  defKind = TypeDefKind.Class(isCase = true)
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

The following example applications demonstrate TypeId usage:

- `schema-examples/src/main/scala/typeid/TypeIdBasicExample.scala` — Deriving TypeIds and accessing properties
- `schema-examples/src/main/scala/typeid/TypeIdSubtypingExample.scala` — Subtype relationships and variance
- `schema-examples/src/main/scala/typeid/TypeIdNormalizationExample.scala` — Type aliases and normalization
- `schema-examples/src/main/scala/typeid/OpaqueTypesExample.scala` — Opaque types and type-safe registries

Run them with:

```bash
sbt "schema-examples/runMain typeid.TypeIdBasicExample"
sbt "schema-examples/runMain typeid.TypeIdSubtypingExample"
sbt "schema-examples/runMain typeid.TypeIdNormalizationExample"
sbt "schema-examples/runMain typeid.OpaqueTypesExample"
```
