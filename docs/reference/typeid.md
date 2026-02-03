---
id: typeid
title: "TypeId"
---

# TypeId

`TypeId[A]` represents the identity of a type or type constructor at runtime. It provides rich type identity information including the type's name, owner (package/class/object), type parameters, classification (nominal, alias, or opaque), parent types, and annotations.

## Overview

TypeId is fundamental to ZIO Blocks' schema system, enabling:

- **Type identification** - Uniquely identify types across serialization boundaries
- **Subtype checking** - Determine inheritance relationships at runtime
- **Type normalization** - Resolve type aliases to their underlying types
- **Schema derivation** - Automatically derive schemas for user-defined types

```scala
import zio.blocks.typeid._

// Derive TypeId for your types
case class Person(name: String, age: Int)
val personId: TypeId[Person] = TypeId.of[Person]

// Access type information
personId.name       // "Person"
personId.fullName   // "com.example.Person"
personId.isCaseClass // true

// Use predefined TypeIds
TypeId.int.fullName      // "scala.Int"
TypeId.string.fullName   // "java.lang.String"
TypeId.list.arity        // 1 (type constructor)
```

## Installation

TypeId is included in the `zio-blocks-typeid` module. Add it to your build:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-typeid" % "<version>"
```

Cross-platform support: TypeId works on JVM and Scala.js.

## Creating TypeIds

### Automatic Derivation

The simplest way to get a TypeId is via macro derivation:

```scala
import zio.blocks.typeid._

case class User(id: Long, email: String)

// Scala 3
val userId: TypeId[User] = TypeId.of[User]

// Scala 2
val userId: TypeId[User] = TypeId.of[User]

// Or use implicit derivation
val userId: TypeId[User] = implicitly[TypeId[User]]
```

The macro extracts complete type information including:
- Type name and owner
- Type parameters and variance
- Parent types (for sealed traits and enums)
- Whether it's a case class, sealed trait, enum, etc.

### Manual Construction

For manual type registration or testing, use smart constructors:

```scala
// Nominal types (classes, traits, objects)
val myTypeId = TypeId.nominal[MyType](
  name = "MyType",
  owner = Owner.fromPackagePath("com.example"),
  defKind = TypeDefKind.Class(isCase = true)
)

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

### Basic Properties

```scala
val id = TypeId.of[Person]

id.name           // "Person" - simple name
id.fullName       // "com.example.Person" - fully qualified
id.owner          // Owner representing the package/enclosing type
id.arity          // 0 for proper types, n for type constructors
id.typeParams     // List of TypeParam for type constructors
id.typeArgs       // List of TypeRepr for applied types
```

### Type Classification

```scala
id.isClass        // true for classes
id.isTrait        // true for traits
id.isObject       // true for singleton objects
id.isEnum         // true for Scala 3 enums
id.isCaseClass    // true for case classes
id.isValueClass   // true for value classes (extends AnyVal)
id.isSealed       // true for sealed traits
id.isAlias        // true for type aliases
id.isOpaque       // true for opaque types
id.isAbstract     // true for abstract type members

id.isProperType      // arity == 0
id.isTypeConstructor // arity > 0
id.isApplied         // has type arguments
```

### Common Type Checks

```scala
id.isTuple   // scala.TupleN
id.isProduct // scala.ProductN
id.isSum     // Either or Option
id.isEither  // scala.util.Either
id.isOption  // scala.Option
```

### Subtype Relationships

```scala
sealed trait Animal
case class Dog(name: String) extends Animal

val dogId = TypeId.of[Dog]
val animalId = TypeId.of[Animal]

dogId.isSubtypeOf(animalId)    // true
animalId.isSupertypeOf(dogId)  // true
dogId.isEquivalentTo(dogId)    // true
```

Subtype checking handles:
- Direct inheritance
- Enum cases and their parent enums
- Sealed trait subtypes
- Transitive inheritance
- Variance-aware subtyping for applied types

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
    
  case TypeId.Sealed(name, subtypes) =>
    // Sealed traits - subtypes is List[TypeRepr]
    
  case TypeId.Enum(name, owner, cases) =>
    // Scala 3 enums - cases is List[EnumCaseInfo]
}
```

## TypeRepr

`TypeRepr` represents type expressions in the Scala type system. While `TypeId` identifies a type definition, `TypeRepr` represents how types are used in expressions.

### Basic Type References

```scala
// Reference to a named type
TypeRepr.Ref(TypeId.int)            // Int
TypeRepr.Ref(TypeId.string)         // String

// Reference to a type parameter
TypeRepr.ParamRef(TypeParam.A)      // A
TypeRepr.ParamRef(param, depth = 1) // nested binder reference
```

### Applied Types

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

```scala
// A => B
TypeRepr.Function(List(typeA), typeB)

// (A, B) => C
TypeRepr.Function(List(typeA, typeB), typeC)

// (A, B) ?=> C (context function, Scala 3)
TypeRepr.ContextFunction(List(typeA, typeB), typeC)
```

### Tuple Types

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

```scala
TypeRepr.AnyType       // Any
TypeRepr.NothingType   // Nothing
TypeRepr.NullType      // Null
TypeRepr.UnitType      // Unit
TypeRepr.AnyKindType   // AnyKind (for kind-polymorphic contexts)
```

### Constant/Literal Types

```scala
TypeRepr.Constant.IntConst(42)         // 42 (literal type)
TypeRepr.Constant.StringConst("foo")   // "foo"
TypeRepr.Constant.BooleanConst(true)   // true
TypeRepr.Constant.ClassOfConst(tpe)    // classOf[T]
```

### Type Lambdas (Scala 3)

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

```scala
// => A (by-name)
TypeRepr.ByName(typeA)

// A* (varargs/repeated)
TypeRepr.Repeated(typeA)

// A @annotation
TypeRepr.Annotated(typeA, List(annotation))
```

## Namespaces and Type Names

### Owner

`Owner` represents where a type is defined in the package hierarchy:

```scala
// From package path
val owner = Owner.fromPackagePath("com.example.app")
// Owner(List(Package("com"), Package("example"), Package("app")))

// Build incrementally
val owner = Owner.Root / "com" / "example"

// Add term (object) segment
val owner = (Owner.Root / "com").term("MyObject")

// Add type segment
val owner = (Owner.Root / "com").tpe("MyClass")
```

Owner properties:

```scala
owner.asString    // "com.example" - dot-separated path
owner.isRoot      // true if empty
owner.parent      // Parent owner (or Root)
owner.lastName    // Last segment name
```

### Predefined Owners

TypeId provides common namespaces:

```scala
Owner.scala                      // scala
Owner.scalaUtil                  // scala.util
Owner.scalaCollectionImmutable   // scala.collection.immutable
Owner.javaLang                   // java.lang
Owner.javaTime                   // java.time
Owner.javaUtil                   // java.util
```

### TermPath

`TermPath` represents paths to term values (for singleton types):

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

TypeParam properties:

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

TypeBounds properties:

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

Kind properties:

```scala
kind.isProperType   // kind == Kind.Type
kind.arity          // Number of type parameters
```

## Members (Structural Types)

### Val/Var Members

```scala
// val x: Int
Member.Val("x", TypeRepr.Ref(TypeId.int))

// var y: String
Member.Val("y", TypeRepr.Ref(TypeId.string), isVar = true)
```

### Method Members

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

Classifies what kind of type definition a TypeId represents:

### Class

```scala
TypeDefKind.Class(
  isFinal = false,
  isAbstract = false,
  isCase = true,       // case class
  isValue = false,     // extends AnyVal
  bases = List(...)    // parent types
)
```

### Trait

```scala
TypeDefKind.Trait(
  isSealed = true,
  knownSubtypes = List(subtypeRef1, subtypeRef2),
  bases = List(...)
)
```

### Object

```scala
TypeDefKind.Object(
  bases = List(...)
)
```

### Enum (Scala 3)

```scala
TypeDefKind.Enum(
  cases = List(
    EnumCaseInfo("Red", ordinal = 0, isObjectCase = true),
    EnumCaseInfo("RGB", ordinal = 1, 
      params = List(
        EnumCaseParam("r", intType),
        EnumCaseParam("g", intType),
        EnumCaseParam("b", intType)
      ),
      isObjectCase = false
    )
  ),
  bases = List(...)
)

TypeDefKind.EnumCase(
  parentEnum = parentEnumRef,
  ordinal = 0,
  isObjectCase = true
)
```

### Type Aliases and Opaque Types

```scala
TypeDefKind.TypeAlias              // type Foo = Bar

TypeDefKind.OpaqueType(
  publicBounds = TypeBounds.Unbounded  // Bounds visible outside
)

TypeDefKind.AbstractType           // Abstract type member
```

## Annotations

Represent Scala/Java annotations attached to types:

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

Annotation argument types:

```scala
AnnotationArg.Const(value)           // Constant value
AnnotationArg.ArrayArg(values)       // Array of args
AnnotationArg.Named(name, value)     // Named parameter
AnnotationArg.Nested(annotation)     // Nested annotation
AnnotationArg.ClassOf(typeRepr)      // classOf[T]
AnnotationArg.EnumValue(enumType, valueName)  // Enum constant
```

## Predefined TypeIds

TypeId provides instances for common types:

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

TypeId is central to ZIO Blocks' schema system. Every `Reflect` node has an associated TypeId:

```scala
import zio.blocks.schema._

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Access TypeId from schema
val reflect = Schema[Person].reflect
val typeId = reflect.typeId

typeId.name        // "Person"
typeId.isCaseClass // true
```

### Schema Transformations

TypeId is captured when transforming schemas:

```scala
case class Email(value: String)

object Email {
  implicit val schema: Schema[Email] = Schema[String]
    .transform(Email(_), _.value)
    .withTypeName[Email]  // Sets TypeId to Email
}
```

### Schema Derivation

The `Deriver` trait receives TypeId for each node:

```scala
trait Deriver[TC[_]] {
  def deriveRecord[A](
    typeId: TypeId[A],
    fields: => Chunk[Deriver.Field[TC, A, _]],
    ...
  ): TC[A]
  
  def deriveVariant[A](
    typeId: TypeId[A],
    cases: => Chunk[Deriver.Case[TC, A, _]],
    ...
  ): TC[A]
  
  // ... other methods
}
```

## Type Normalization

Type aliases are normalized to their underlying types for comparison:

```scala
type Age = Int

val ageId = TypeId.alias[Age]("Age", owner, Nil, TypeRepr.Ref(TypeId.int))
val normalized = TypeId.normalize(ageId)

normalized.fullName  // "scala.Int" (not "Age")
```

Normalization handles nested aliases and type arguments:

```scala
type IntList = List[Int]
type MyIntList = IntList

// Normalizing MyIntList resolves through IntList to List[Int]
```

## Equality and Hashing

TypeId uses structural equality that accounts for type aliases:

```scala
val alias1 = TypeId.alias[A]("A", owner, Nil, TypeRepr.Ref(TypeId.int))
val alias2 = TypeId.alias[A]("A", owner, Nil, TypeRepr.Ref(TypeId.int))

alias1 == alias2  // true (structural equality)

// Works correctly in hash maps
val map = Map(alias1 -> "value")
map(alias2)  // "value"
```

## Erased TypeId

For type-indexed collections where the type parameter doesn't matter:

```scala
// TypeId.Erased is TypeId[TypeId.Unknown]
val erased: TypeId.Erased = typeId.erased

// Use in maps keyed by type
val typeRegistry: Map[TypeId.Erased, Schema[_]] = Map(
  TypeId.int.erased -> Schema[Int],
  TypeId.string.erased -> Schema[String]
)
```

## Runtime Reflection

On JVM, TypeId can retrieve the corresponding `Class`:

```scala
val typeId = TypeId.of[Person]
val clazz: Option[Class[_]] = typeId.clazz

// Construct instances (JVM only)
val result: Either[String, Any] = typeId.construct(Chunk("Alice", 30))
```
