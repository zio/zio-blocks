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

```scala
import zio.blocks.typeid._

case class Person(name: String, age: Int)

val id = TypeId.of[Person]

// Inspect type structure at runtime
println(s"Type name: ${id.name}")           // "Person"
println(s"Full name: ${id.fullName}")       // "Person"
println(s"Is case class: ${id.isCaseClass}") // true
```

## Motivation

### Why TypeId Exists

In Scala, type information is erased at runtime — the JVM and JavaScript runtimes do not preserve generic type parameters or compile-time type structure. This erasure makes it difficult to implement universal serialization (JSON, binary formats, XML, YAML), code generation, and schema-based transformations that rely on full type knowledge.

`TypeId[A]` solves this problem by capturing and preserving complete type metadata at compile time, making it available as a pure data structure at runtime. This metadata includes the type's name, package location, type parameters, variance, parent types, annotations, and classification (case class, sealed trait, enum, etc.).

### Real-World Use Cases

**Schema-Driven Serialization**

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

**Type-Indexed Registries and Lookup**

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

**Type-Safe Polymorphic Operations**

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

**Opaque Types**

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

### How TypeId Fits into the Schema Stack

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

## Creating TypeIds

TypeId can be created automatically via macro derivation or manually using smart constructors for advanced use cases.

### Automatic Derivation

The simplest way to get a TypeId is via macro derivation:

```scala mdoc:silent:reset
import zio.blocks.typeid._

case class User(id: Long, email: String)
```

You can derive a TypeId using the `TypeId.of` macro, which works identically in both Scala 2 and Scala 3:

```scala
val userId: TypeId[User] = TypeId.of[User]
```

Or use implicit derivation:

```scala
val userIdImplicit: TypeId[User] = implicitly[TypeId[User]]
```

The macro extracts complete type information including type name, owner, type parameters, variance, parent types (for sealed traits and enums), and classification (case class, sealed trait, enum, etc.).

### Manual Construction

For manual type registration or testing, use smart constructors. For example, to create a nominal type:

```scala
// Nominal types (classes, traits, objects)
val myTypeId = TypeId.nominal[MyType](
  name = "MyType",
  owner = Owner.fromPackagePath("com.example"),
  defKind = TypeDefKind.Class(isCase = true)
)
```

To construct type aliases and opaque types:

```scala
// Type aliases
val aliasId = TypeId.alias[Age](
  name = "Age",
  owner = Owner.fromPackagePath("com.example"),
  aliased = TypeRepr.Ref(TypeId.int)
)

// Opaque types (Scala 3)
val emailId = TypeId.opaque[Email](
  name = "Email",
  owner = Owner.fromPackagePath("com.example"),
  representation = TypeRepr.Ref(TypeId.string)
)
```

### Applied Types

Create applied types (type constructors with arguments):

```scala
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

## TypeId Properties

Once derived, you can inspect a TypeId's structure and metadata through its properties.

### Basic Properties

You can inspect the basic properties of a TypeId:

```scala
import zio.blocks.typeid._

case class Person(name: String, age: Int)
val id = TypeId.of[Person]

id.name       // "Person"
id.fullName   // "Person"
id.arity      // 0
```

Additional properties include `TypeId#owner` (Owner representing the package/enclosing type), `TypeId#typeParams` (List of TypeParam for type constructors), and `TypeId#typeArgs` (List of TypeRepr for applied types).

### Type Classification

TypeId provides predicates to classify types:

```scala
val id = TypeId.of[Person]

id.isClass        // true
id.isCaseClass    // true
id.isProperType   // true
id.isTypeConstructor // false
```

Other classification predicates include `TypeId#isTrait`, `TypeId#isObject`, `TypeId#isEnum`, `TypeId#isValueClass`, `TypeId#isSealed`, `TypeId#isAlias`, `TypeId#isOpaque`, `TypeId#isAbstract`, `TypeId#isApplied`, `TypeId#isTuple`, `TypeId#isProduct`, `TypeId#isSum`, `TypeId#isEither`, and `TypeId#isOption`.

### Subtype Relationships

TypeId can determine inheritance relationships at runtime. Use the subtype checking methods:

```scala
import zio.blocks.typeid._

sealed trait Animal
case class Dog(name: String) extends Animal

val dogId = TypeId.of[Dog]
val animalId = TypeId.of[Animal]

dogId.isSubtypeOf(animalId)     // true
animalId.isSupertypeOf(dogId)   // true
dogId.isEquivalentTo(dogId)     // true
```

Subtype checking handles direct inheritance, enum cases and their parent enums, sealed trait subtypes, transitive inheritance, and variance-aware subtyping for applied types.

### Pattern Matching

TypeId provides extractors for pattern matching:

```scala
typeId match {
  case TypeId.Nominal(name, owner, params, defKind, parents) =>
    // Regular types

  case TypeId.Alias(name, owner, params, aliased) =>
    // Type aliases - aliased is the underlying TypeRepr

  case TypeId.Opaque(name, owner, params, repr, bounds) =>
    // Opaque types - repr is the representation type

  case TypeId.Sealed(name) =>
    // Sealed traits

  case TypeId.Enum(name, owner) =>
    // Scala 3 enums
}
```

## TypeRepr

`TypeRepr` represents type expressions in the Scala type system. While `TypeId` identifies a type definition, `TypeRepr` represents how types are used in expressions.

### Basic Type References

Reference a named type or type parameter:

```scala
// Reference to a named type
TypeRepr.Ref(TypeId.int)            // Int
TypeRepr.Ref(TypeId.string)         // String

// Reference to a type parameter
TypeRepr.ParamRef(TypeParam.A)      // A
TypeRepr.ParamRef(param, depth = 1) // nested binder reference
```

### Applied Types

Represent parameterized types like `List[Int]`:

```scala
// List[Int]
TypeRepr.Applied(
  TypeRepr.Ref(TypeId.list),
  List(TypeRepr.Ref(TypeId.int))
)

// Map[String, Int]
TypeRepr.Applied(
  TypeRepr.Ref(TypeId.map),
  List(TypeRepr.Ref(TypeId.string), TypeRepr.Ref(TypeId.int))
)
```

### Compound Types

Represent intersection and union types:

```scala
// Intersection: A & B (Scala 3) or A with B (Scala 2)
TypeRepr.Intersection(List(typeA, typeB))

// Union: A | B (Scala 3 only)
TypeRepr.Union(List(typeA, typeB))

// Convenience constructors handle edge cases
TypeRepr.intersection(List(typeA))      // returns typeA (not Intersection)
TypeRepr.intersection(Nil)              // returns AnyType
TypeRepr.union(List(typeA))             // returns typeA
TypeRepr.union(Nil)                     // returns NothingType
```

### Function Types

Represent function types with their parameters and result:

```scala
// A => B
TypeRepr.Function(List(typeA), typeB)

// (A, B) => C
TypeRepr.Function(List(typeA, typeB), typeC)

// (A, B) ?=> C (context function, Scala 3)
TypeRepr.ContextFunction(List(typeA, typeB), typeC)
```

### Tuple Types

Represent tuple types, including named tuples:

```scala
// (A, B, C)
TypeRepr.Tuple(List(
  TupleElement(None, typeA),
  TupleElement(None, typeB),
  TupleElement(None, typeC)
))

// Named tuples (Scala 3.5+): (name: String, age: Int)
TypeRepr.Tuple(List(
  TupleElement(Some("name"), TypeRepr.Ref(TypeId.string)),
  TupleElement(Some("age"), TypeRepr.Ref(TypeId.int))
))

// Convenience for unnamed tuples
TypeRepr.tuple(List(typeA, typeB, typeC))
```

### Structural Types

Represent structural types with members:

```scala
// { def foo: Int }
TypeRepr.Structural(
  parents = Nil,
  members = List(
    Member.Def("foo", Nil, Nil, TypeRepr.Ref(TypeId.int))
  )
)

// AnyRef { type T; val x: T }
TypeRepr.Structural(
  parents = List(TypeRepr.Ref(anyRefId)),
  members = List(
    Member.TypeMember("T"),
    Member.Val("x", TypeRepr.ParamRef(paramT))
  )
)
```

### Path-Dependent and Singleton Types

Represent singleton and path-dependent types:

```scala
// x.type (singleton type)
TypeRepr.Singleton(TermPath.fromOwner(owner, "x"))

// this.type
TypeRepr.ThisType(owner)

// Outer#Inner (type projection)
TypeRepr.TypeProjection(outerType, "Inner")

// qualifier.Member (type selection)
TypeRepr.TypeSelect(qualifierType, "Member")
```

### Special Types

TypeRepr provides constructors for special type forms:

```scala
TypeRepr.AnyType       // Any
TypeRepr.NothingType   // Nothing
TypeRepr.NullType      // Null
TypeRepr.UnitType      // Unit
TypeRepr.AnyKindType   // AnyKind (for kind-polymorphic contexts)
```

### Constant/Literal Types

Represent literal types like `42` or `"foo"`:

```scala
TypeRepr.Constant.IntConst(42)         // 42 (literal type)
TypeRepr.Constant.StringConst("foo")   // "foo"
TypeRepr.Constant.BooleanConst(true)   // true
TypeRepr.Constant.ClassOfConst(tpe)    // classOf[T]
```

### Type Lambdas (Scala 3)

Represent higher-order type expressions:

```scala
// [X] =>> F[X]
TypeRepr.TypeLambda(
  params = List(TypeParam("X", 0)),
  body = TypeRepr.Applied(
    TypeRepr.ParamRef(paramF),
    List(TypeRepr.ParamRef(paramX))
  )
)
```

### Wildcards and Bounds

Represent wildcard types and type parameter bounds:

```scala
// ?
TypeRepr.Wildcard()

// ? <: Upper
TypeRepr.Wildcard(TypeBounds.upper(upperType))

// ? >: Lower
TypeRepr.Wildcard(TypeBounds.lower(lowerType))

// ? >: Lower <: Upper
TypeRepr.Wildcard(TypeBounds(lowerType, upperType))
```

### Parameter Modifiers

Represent by-name, varargs, and annotated types:

```scala
// => A (by-name)
TypeRepr.ByName(typeA)

// A* (varargs/repeated)
TypeRepr.Repeated(typeA)

// A @annotation
TypeRepr.Annotated(typeA, List(annotation))
```

## Namespaces and Type Names

Types are organized hierarchically using owners, term paths, and other namespace constructs that capture where types are defined.

### Owner

`Owner` represents where a type is defined in the package hierarchy:

```scala
// From package path
val owner = Owner.fromPackagePath("com.example.app")

// Build incrementally
val owner = Owner.Root / "com" / "example"

// Add term (object) segment
val owner = (Owner.Root / "com").term("MyObject")

// Add type segment
val owner = (Owner.Root / "com").tpe("MyClass")
```

Owner provides introspection properties:

```scala
owner.asString    // "com.example" - dot-separated path
owner.isRoot      // true if empty
owner.parent      // Parent owner (or Root)
owner.lastName    // Last segment name
```

### Predefined Owners

TypeId provides common package namespaces as predefined owners:

```scala
Owner.scala                      // scala
Owner.scalaUtil                  // scala.util
Owner.scalaCollectionImmutable   // scala.collection.immutable
Owner.javaLang                   // java.lang
Owner.javaTime                   // java.time
Owner.javaUtil                   // java.util
```

### TermPath

`TermPath` represents paths to term values, used to construct singleton types:

```scala
// com.example.MyObject.value.type
val path = TermPath.fromOwner(
  Owner.fromPackagePath("com.example").term("MyObject"),
  "value"
)

path.asString     // "com.example.MyObject.value"
path.isEmpty      // false
path / "nested"   // Append segment
```

## Type Parameters

TypeId represents the type parameters of generic types through the TypeParam, TypeBounds, Variance, and Kind types, providing fine-grained metadata about parametricity.

### TypeParam

Represents a type parameter specification:

```scala
// Basic type parameter
TypeParam("A", index = 0)

// Covariant (+A)
TypeParam("A", 0, Variance.Covariant)
TypeParam.covariant("A", 0)

// Contravariant (-A)
TypeParam("A", 0, Variance.Contravariant)
TypeParam.contravariant("A", 0)

// With bounds (A <: Upper)
TypeParam.bounded("A", 0, upper = TypeRepr.Ref(upperType))

// Higher-kinded (F[_])
TypeParam.higherKinded("F", 0, arity = 1)
TypeParam("F", 0, kind = Kind.Star1)

// Full specification
TypeParam(
  name = "A",
  index = 0,
  variance = Variance.Covariant,
  bounds = TypeBounds.upper(someType),
  kind = Kind.Type
)
```

TypeParam provides introspection:

```scala
param.name              // "A"
param.index             // Position in parameter list
param.variance          // Covariant, Contravariant, or Invariant
param.bounds            // TypeBounds
param.kind              // Kind (*, * -> *, etc.)

param.isCovariant       // variance == Covariant
param.isContravariant   // variance == Contravariant
param.isInvariant       // variance == Invariant
param.hasUpperBound     // bounds.upper.isDefined
param.hasLowerBound     // bounds.lower.isDefined
param.isProperType      // kind == Kind.Type
param.isTypeConstructor // kind != Kind.Type
```

### TypeBounds

Represents type parameter bounds:

```scala
// No bounds (>: Nothing <: Any)
TypeBounds.Unbounded

// Upper bound only (<: Upper)
TypeBounds.upper(upperType)

// Lower bound only (>: Lower)
TypeBounds.lower(lowerType)

// Both bounds (>: Lower <: Upper)
TypeBounds(lowerType, upperType)

// Type alias bounds (lower == upper)
TypeBounds.alias(aliasType)
```

TypeBounds provides introspection:

```scala
bounds.lower            // Option[TypeRepr]
bounds.upper            // Option[TypeRepr]
bounds.isUnbounded      // No bounds specified
bounds.hasOnlyUpper     // Only upper bound
bounds.hasOnlyLower     // Only lower bound
bounds.hasBothBounds    // Both bounds specified
bounds.isAlias          // lower == upper
bounds.aliasType        // Option[TypeRepr] if alias
```

### Variance

Represents type parameter variance:

```scala
Variance.Covariant      // +A
Variance.Contravariant  // -A
Variance.Invariant      // A

variance.symbol         // "+", "-", or ""
variance.isCovariant
variance.isContravariant
variance.isInvariant
variance.flip           // Covariant <-> Contravariant
variance * other        // Combine variances
```

### Kind

Represents the "kind" of a type (type of types):

```scala
Kind.Type               // * (proper type like Int, String)
Kind.Star               // Alias for Type
Kind.Star1              // * -> * (List, Option)
Kind.Star2              // * -> * -> * (Map, Either)
Kind.HigherStar1        // (* -> *) -> * (Functor, Monad)

Kind.constructor(0)     // *
Kind.constructor(1)     // * -> *
Kind.constructor(2)     // * -> * -> *

// Custom kinds
Kind.Arrow(List(Kind.Type), Kind.Type)  // * -> *
Kind.Arrow(List(Kind.Star1), Kind.Type) // (* -> *) -> *
```

Kind provides introspection:

```scala
kind.isProperType   // kind == Kind.Type
kind.arity          // Number of type parameters
```

## Members (Structural Types)

Structural types use members to represent the fields, methods, and type members that comprise their interface.

### Val/Var Members

Represent value and variable members in structural types:

```scala
// val x: Int
Member.Val("x", TypeRepr.Ref(TypeId.int))

// var y: String
Member.Val("y", TypeRepr.Ref(TypeId.string), isVar = true)
```

### Method Members

Represent method members with their parameters and return type:

```scala
// def foo: Int
Member.Def("foo", Nil, Nil, TypeRepr.Ref(TypeId.int))

// def bar(x: Int): String
Member.Def(
  name = "bar",
  typeParams = Nil,
  paramLists = List(List(Param("x", TypeRepr.Ref(TypeId.int)))),
  result = TypeRepr.Ref(TypeId.string)
)

// def baz[A](x: A)(implicit y: Ordering[A]): List[A]
Member.Def(
  name = "baz",
  typeParams = List(TypeParam.A),
  paramLists = List(
    List(Param("x", TypeRepr.ParamRef(TypeParam.A))),
    List(Param("y", orderingA, isImplicit = true))
  ),
  result = listA
)
```

### Type Members

Represent type members with optional bounds:

```scala
// type T
Member.TypeMember("T")

// type T <: Upper
Member.TypeMember("T", upperBound = Some(upperType))

// type T = Alias (isAlias when lower == upper)
Member.TypeMember("T",
  lowerBound = Some(aliasType),
  upperBound = Some(aliasType)
)
```

## TypeDefKind

Classifies what kind of type definition a TypeId represents. The kind metadata tells you whether a type is a class, trait, object, enum, or special form like a type alias.

### Class

Represents a class definition:

```scala
// Represents a class definition
TypeDefKind.Class(
  isFinal = false,
  isAbstract = false,
  isCase = true,       // case class
  isValue = false,     // extends AnyVal
  bases = Nil          // parent types
)
```

### Trait

Represents a trait definition:

```scala
TypeDefKind.Trait(
  isSealed = true,
  bases = Nil
)
```

### Object

Represents a singleton object:

```scala
TypeDefKind.Object(
  bases = Nil
)
```

### Enum (Scala 3)

Represents enum definitions and cases:

```scala
TypeDefKind.Enum(
  bases = Nil
)
```

For enum cases:

```scala
TypeDefKind.EnumCase(
  parentEnum = TypeRepr.Ref(TypeId.int), // Simplified example
  ordinal = 0,
  isObjectCase = true
)
```

### Type Aliases and Opaque Types

Represents type aliases and opaque type definitions:

```scala
TypeDefKind.TypeAlias              // type Foo = Bar

TypeDefKind.OpaqueType(
  publicBounds = TypeBounds.Unbounded  // Bounds visible outside
)

TypeDefKind.AbstractType           // Abstract type member
```

## Annotations

Annotations represent Scala/Java annotations attached to types:

```scala
Annotation(
  typeId = TypeId.of[deprecated],
  args = List(
    AnnotationArg.Named("message",
      AnnotationArg.Const("use newMethod")),
    AnnotationArg.Named("since",
      AnnotationArg.Const("1.0"))
  )
)
```

Annotation arguments come in several forms:

```scala
AnnotationArg.Const(value)           // Constant value
AnnotationArg.ArrayArg(values)       // Array of args
AnnotationArg.Named(name, value)     // Named parameter
AnnotationArg.Nested(annotation)     // Nested annotation
AnnotationArg.ClassOf(typeRepr)      // classOf[T]
AnnotationArg.EnumValue(enumType, valueName)  // Enum constant
```

## Predefined TypeIds

TypeId provides instances for common types. These are useful as building blocks when constructing applied types:

### Primitives

```scala
TypeId.unit      // scala.Unit
TypeId.boolean   // scala.Boolean
TypeId.byte      // scala.Byte
TypeId.short     // scala.Short
TypeId.int       // scala.Int
TypeId.long      // scala.Long
TypeId.float     // scala.Float
TypeId.double    // scala.Double
TypeId.char      // scala.Char
TypeId.string    // java.lang.String
TypeId.bigInt    // scala.BigInt
TypeId.bigDecimal // scala.BigDecimal
```

### Collections

```scala
TypeId.option     // scala.Option
TypeId.some       // scala.Some
TypeId.none       // scala.None
TypeId.list       // scala.collection.immutable.List
TypeId.vector     // scala.collection.immutable.Vector
TypeId.set        // scala.collection.immutable.Set
TypeId.seq        // scala.collection.immutable.Seq
TypeId.indexedSeq // scala.collection.immutable.IndexedSeq
TypeId.map        // scala.collection.immutable.Map
TypeId.either     // scala.util.Either
TypeId.array      // scala.Array
TypeId.arraySeq   // scala.collection.immutable.ArraySeq
TypeId.chunk      // zio.blocks.chunk.Chunk
```

### java.time Types

```scala
TypeId.dayOfWeek      // java.time.DayOfWeek
TypeId.duration       // java.time.Duration
TypeId.instant        // java.time.Instant
TypeId.localDate      // java.time.LocalDate
TypeId.localDateTime  // java.time.LocalDateTime
TypeId.localTime      // java.time.LocalTime
TypeId.month          // java.time.Month
TypeId.monthDay       // java.time.MonthDay
TypeId.offsetDateTime // java.time.OffsetDateTime
TypeId.offsetTime     // java.time.OffsetTime
TypeId.period         // java.time.Period
TypeId.year           // java.time.Year
TypeId.yearMonth      // java.time.YearMonth
TypeId.zoneId         // java.time.ZoneId
TypeId.zoneOffset     // java.time.ZoneOffset
TypeId.zonedDateTime  // java.time.ZonedDateTime
```

### java.util Types

```scala
TypeId.currency   // java.util.Currency
TypeId.uuid       // java.util.UUID
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

The `Deriver` trait receives TypeId for each node in the schema. The key methods that receive TypeId are (simplified example):

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

## Type Normalization

Type aliases are normalized to their underlying types for comparison:

```scala mdoc:silent:reset
import zio.blocks.typeid._

type Age = Int
val owner = Owner.Root

val ageId = TypeId.alias[Age]("Age", owner, Nil, TypeRepr.Ref(TypeId.int))
```

When you normalize an alias, it resolves to the underlying type:

```scala mdoc
val normalized = TypeId.normalize(ageId)
normalized.fullName
```

Normalization handles nested aliases and type arguments, resolving chains like `type MyIntList = IntList` where `type IntList = List[Int]` down to `List[Int]`.

## Equality and Hashing

TypeId uses structural equality that accounts for type aliases:

```scala mdoc:silent:reset
import zio.blocks.typeid._

val owner = Owner.Root
val alias1 = TypeId.alias[Int]("A", owner, Nil, TypeRepr.Ref(TypeId.int))
val alias2 = TypeId.alias[Int]("A", owner, Nil, TypeRepr.Ref(TypeId.int))
```

Structurally identical TypeIds are considered equal:

```scala mdoc
alias1 == alias2
```

This structural equality works correctly in hash-based collections:

```scala mdoc
val map = Map(alias1 -> "value")
map.get(alias2)
```

## Erased TypeId

For type-indexed collections where the type parameter doesn't matter, you can erase the type parameter:

```scala mdoc:silent:reset
import zio.blocks.typeid._
```

```scala mdoc
// TypeId.Erased is TypeId[TypeId.Unknown]
val erased: TypeId.Erased = TypeId.int.erased
```

Erased TypeIds are useful for building type registries keyed by type:

```scala
// Use in maps keyed by type
val typeRegistry: Map[TypeId.Erased, String] = Map(
  TypeId.int.erased -> "Int Schema",
  TypeId.string.erased -> "String Schema"
)
```

## Runtime Reflection

On JVM, TypeId can retrieve the corresponding `Class` and construct instances:

```scala
val typeId = TypeId.of[Person]
val clazz: Option[Class[_]] = typeId.clazz

// Construct instances (JVM only)
val result: Either[String, Any] = typeId.construct(Chunk("Alice", 30))
```

## TypeId vs Related Concepts

TypeId provides richer type information than other Scala/JVM reflection mechanisms:

**TypeId vs `scala.reflect.ClassTag`:** ClassTag only provides the erased class; TypeId gives you full structural information including type parameters, parent types, annotations, and variance.

**TypeId vs `java.lang.Class`:** Class is JVM-only and always erased; TypeId is pure Scala, works cross-platform (JVM and Scala.js), and preserves complete type structure.

**TypeId vs Scala 2 `TypeTag` / Scala 3 `TypeTest`:** These introduce heavier compiler-runtime dependencies and macro complexity. TypeId is a pure data structure that works uniformly across Scala versions.

## Running the Examples

The following example applications demonstrate TypeId usage:

- `schema-examples/src/main/scala/typeid/TypeIdBasicExample.scala` — Deriving TypeIds and accessing properties
- `schema-examples/src/main/scala/typeid/TypeIdSubtypingExample.scala` — Subtype relationships and variance
- `schema-examples/src/main/scala/typeid/TypeIdNormalizationExample.scala` — Type aliases and normalization

Run them with:

```bash
sbt "schema-examples/runMain typeid.TypeIdBasicExample"
sbt "schema-examples/runMain typeid.TypeIdSubtypingExample"
sbt "schema-examples/runMain typeid.TypeIdNormalizationExample"
```
