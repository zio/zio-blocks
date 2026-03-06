---
id: allows
title: "Allows"
---

`Allows[A, S]` is a compile-time capability token that proves, at the call site, that type `A` satisfies the structural grammar `S`.

`Allows` does **not** require or use `Schema[A]`. It inspects the Scala type structure of `A` directly at compile time, using nothing but the Scala type system. Any `Schema[A]` that appears alongside `Allows` in examples is the library author's own separate constraint — it is not imposed by `Allows` itself.

## Motivation

ZIO Blocks (ZIO Schema 2) gives library authors a powerful way to build data-oriented DSLs. A library can accept `A: Schema` and use the schema at runtime to serialize, deserialize, query, or transform values of `A`. But `Allows` is useful even without a Schema — it can enforce structural preconditions on *any* generic function.

The gap is **structural preconditions**. Many generic functions only make sense for a subset of types:

- A CSV serializer requires flat records of scalars.
- An RDBMS layer cannot handle nested records as column values.
- An event bus expects a sealed trait of flat record cases.
- A JSON document store allows arbitrarily nested records but not `DynamicValue` leaves.

Today, these constraints can only be checked at runtime, producing confusing errors deep inside library internals.

`Allows[A, S]` closes this gap: the constraint is verified at the **call site**, at compile time, with precise, path-aware error messages and concrete fix suggestions.

## The Upper Bound Semantics

`Allows[A, S]` is an upper bound. A type `A` that uses only a strict subset of what `S` permits also satisfies it — just as `A <: Foo` does not require that `A` uses every method of `Foo`.

```scala
// Allows[UserRow, Record[Primitive | Optional[Primitive]]] is satisfied even if
// UserRow has no Option fields — the Optional branch is simply never needed.
```

## Grammar Nodes

All grammar nodes extend `Allows.Structural`.

| Node | Matches |
|---|---|
| `Primitive` | **Any** scalar — catch-all for all 30 Schema 2 primitive types |
| `Primitive.Boolean` | `scala.Boolean` only |
| `Primitive.Int` | `scala.Int` only |
| `Primitive.Long` | `scala.Long` only |
| `Primitive.Double` | `scala.Double` only |
| `Primitive.Float` | `scala.Float` only |
| `Primitive.String` | `java.lang.String` only |
| `Primitive.BigDecimal` | `scala.BigDecimal` only |
| `Primitive.BigInt` | `scala.BigInt` only |
| `Primitive.Unit` | `scala.Unit` only |
| `Primitive.Byte` | `scala.Byte` only |
| `Primitive.Short` | `scala.Short` only |
| `Primitive.Char` | `scala.Char` only |
| `Primitive.UUID` | `java.util.UUID` only |
| `Primitive.Currency` | `java.util.Currency` only |
| `Primitive.Instant` / `LocalDate` / `LocalDateTime` / … | Each specific `java.time.*` type |
| `Record[A]` | A case class / product type whose every field satisfies `A`. Vacuously true for zero-field records. Sealed traits and enums are **automatically unwrapped**: each case is checked individually, so no `Variant` node is needed. |
| `Sequence[A]` | Any collection (`List`, `Vector`, `Set`, `Array`, `Chunk`, …) whose element type satisfies `A` |
| `Sequence.List[A]` | `scala.collection.immutable.List` only, element type satisfies `A` |
| `Sequence.Vector[A]` | `scala.collection.immutable.Vector` only, element type satisfies `A` |
| `Sequence.Set[A]` | `scala.collection.immutable.Set` only, element type satisfies `A` |
| `Sequence.Array[A]` | `scala.Array` only, element type satisfies `A` |
| `Sequence.Chunk[A]` | `zio.blocks.chunk.Chunk` only, element type satisfies `A` |
| `IsType[A]` | Exact nominal type match: satisfied only when the checked type is exactly `A` (`=:=`) |
| `Map[K, V]` | `Map`, `HashMap`, … whose key satisfies `K` and value satisfies `V` |
| `Optional[A]` | `Option[X]` where the inner type `X` satisfies `A` |
| `Wrapped[A]` | A ZIO Prelude `Newtype`/`Subtype` wrapper whose underlying type satisfies `A` |
| `Dynamic` | `DynamicValue` — the schema-less escape hatch |
| `Self` | Recursive self-reference back to the entire enclosing `Allows[A, S]` grammar |
| `` `\|` `` | Union of two grammar nodes: `A \| B`. In Scala 2 write `` A `\|` B `` in infix position. |

Every specific `Primitive.Xxx` node also satisfies the catch-all `Primitive`. This means a type annotated with `Primitive.Int` is valid wherever `Primitive` or `Primitive | Primitive.Long` is required.

## Specific Primitives

The `Primitive` parent class is the catch-all: it accepts any of the 30 Schema 2 primitive types. For stricter control — such as when the target serialisation format only supports a subset — use the specific subtype nodes in `Allows.Primitive`:

```scala mdoc:compile-only
import zio.blocks.schema.comptime.Allows
import Allows._

// Only JSON-representable scalars (no UUID, Char, java.time.*)
type JsonPrimitive =
  Primitive.Boolean | Primitive.Int | Primitive.Long |
  Primitive.Double  | Primitive.String | Primitive.BigDecimal |
  Primitive.BigInt  | Primitive.Unit

def toJson[A](doc: A)(using Allows[A, Record[JsonPrimitive | Self]]): String = ???

// Only numeric types
type Numeric = Primitive.Int | Primitive.Long | Primitive.Double | Primitive.Float |
               Primitive.BigInt | Primitive.BigDecimal

def aggregate[A](data: A)(using Allows[A, Record[Numeric]]): Double = ???
```

A type annotated with `Primitive.Int` satisfies `Primitive` (the catch-all) because `Primitive.Int extends Primitive`:

```scala mdoc:compile-only
import zio.blocks.schema.comptime.Allows
import Allows._

val ev: Allows[Int, Primitive] = implicitly  // Primitive (catch-all) — ✓
val sp: Allows[Int, Primitive.Int] = implicitly  // Primitive.Int (specific) — ✓
```

### JSON Document Store Example

JSON's primitive value set is `null | boolean | number | string`. Types such as `UUID`, `Char`, and all `java.time.*` types have no native JSON representation and must be encoded as strings at the application layer. Using `JsonPrimitive` instead of the catch-all `Primitive` enforces this at compile time.

A JSON document grammar is straightforward: a JSON value is either a record (JSON object) or a sequence (JSON array), and `Self` handles all nesting:

```scala mdoc:compile-only
import zio.blocks.schema.comptime.Allows
import Allows._

type JsonPrimitive =
  Primitive.Boolean | Primitive.Int | Primitive.Long | Primitive.Double |
  Primitive.String  | Primitive.BigDecimal | Primitive.BigInt | Primitive.Unit

type Json = Record[JsonPrimitive | Self] | Sequence[JsonPrimitive | Self]

def toJson[A](doc: A)(using Allows[A, Json]): String = ???
```

`Self` recurses back to `Json` at every nested position, so `List[String]` satisfies `Sequence[JsonPrimitive | Self]` (String is JsonPrimitive), `List[Author]` satisfies it too (Author satisfies `Record[JsonPrimitive | Self]` via Self), and top-level arrays work directly.

A type with a UUID or Instant field fails at compile time:

```
[error] Schema shape violation at WithUUID.id: found Primitive(java.util.UUID),
        required Primitive.Boolean | Primitive.Int | ... | Primitive.String | ...
        UUID is not a JSON-native type — encode it as Primitive.String.
```

## Union Syntax

Union types express "or" in the grammar.

**Scala 3** uses native union type syntax:

```scala mdoc:compile-only
import zio.blocks.schema.comptime.Allows
import Allows._

def writeCsv[A](rows: Seq[A])(using
  Allows[A, Record[Primitive | Optional[Primitive]]]
): Unit = ???
```

**Scala 2** uses the `` `\|` `` infix operator from `Allows`:

```scala
import zio.blocks.schema.comptime.Allows
import Allows._

def writeCsv[A](rows: Seq[A])(implicit
  ev: Allows[A, Record[Primitive | Optional[Primitive]]]
): Unit = ???
```

Both spellings compile and produce the same semantic behavior. The grammar is identical — the only difference is how the union type is expressed.

## Use Cases

### Flat Record (CSV, RDBMS Row)

```scala mdoc:compile-only
import zio.blocks.schema.Schema
import zio.blocks.schema.comptime.Allows
import Allows._

// Flat record: only primitives and optional primitives allowed
def writeCsv[A: Schema](rows: Seq[A])(using
  Allows[A, Record[Primitive | Optional[Primitive]]]
): Unit = ???

// RDBMS INSERT: primitives, optional primitives, or string-keyed maps (JSONB)
def insert[A: Schema](value: A)(using
  Allows[A, Record[Primitive | Optional[Primitive] | Allows.Map[Primitive, Primitive]]]
): String = ???
```

If a user passes a type with nested records, they get a precise compile-time error:

```
[error] Schema shape violation at UserWithAddress.address: found Record(Address),
        required Primitive | Optional[Primitive] | Map[Primitive, Primitive]
```

### Event Bus / Message Broker

Published events are typically sealed traits of flat record cases. No `Variant` node is needed — sealed traits are automatically unwrapped:

```scala mdoc:compile-only
import zio.blocks.schema.Schema
import zio.blocks.schema.comptime.Allows
import Allows._

// DomainEvent is a sealed trait; its cases must each satisfy Record[Primitive | Sequence[Primitive]]
def publish[A: Schema](event: A)(using
  Allows[A, Record[Primitive | Optional[Primitive] | Sequence[Primitive]]]
): Unit = ???
```

If a case of the sealed trait has a nested record field, the error names that case and field:

```
[error] Schema shape violation at DomainEvent.OrderPlaced.items.<element>:
        found Record(OrderItem), required Primitive | Optional[Primitive] | Sequence[Primitive]
```

### JSON Document Store (Recursive)

A document store accepts arbitrarily nested records but not `DynamicValue` leaves. The `Self` node expresses the recursive grammar:

```scala mdoc:compile-only
import zio.blocks.schema.Schema
import zio.blocks.schema.comptime.Allows
import Allows._

type JsonDocument =
  Record[Primitive | Self | Optional[Primitive | Self] | Sequence[Primitive | Self] | Allows.Map[Primitive, Primitive | Self]]

def toJson[A: Schema](doc: A)(implicit ev: Allows[A, JsonDocument]): String = ???
```

This grammar allows:
- `case class Author(name: String, email: String)` — Record[Primitive] ✓
- `case class Book(title: String, author: Author, tags: List[String])` — Record with Self-nested record and Sequence[Primitive] ✓
- `case class Category(name: String, subcategories: List[Category])` — recursive ✓

But rejects:
- `case class Bad(name: String, payload: DynamicValue)` — DynamicValue is not in the grammar ✗

### GraphQL / Tree Structures (Self)

```scala mdoc:compile-only
import zio.blocks.schema.Schema
import zio.blocks.schema.comptime.Allows
import Allows._

def graphqlType[A: Schema]()(using
  Allows[A, Record[Primitive | Optional[Self] | Sequence[Self]]]
): String = ???

// Works:
case class TreeNode(value: Int, children: List[TreeNode])
object TreeNode { implicit val schema: Schema[TreeNode] = Schema.derived }
// graphqlType[TreeNode]() — compiles fine
```

## Sequence Subtypes

The `Sequence[A]` node accepts any collection type. When a DSL needs to restrict to a specific kind of collection — for example, a DynamoDB `Set` operation that is only valid on sets, not lists — use the `Sequence` subtypes:

```scala mdoc:compile-only
import zio.blocks.schema.comptime.Allows
import Allows._

// Only an immutable List is accepted
val listOnly: Allows[List[Int], Sequence.List[Primitive]] = implicitly

// Only an immutable Set is accepted
val setOnly: Allows[Set[Int], Sequence.Set[Primitive]] = implicitly

// Only a Vector
val vecOnly: Allows[Vector[String], Sequence.Vector[Primitive]] = implicitly

// Only an Array
val arrOnly: Allows[Array[Int], Sequence.Array[Primitive]] = implicitly

// Only a Chunk
import zio.blocks.chunk.Chunk
val chkOnly: Allows[Chunk[String], Sequence.Chunk[Primitive]] = implicitly
```

Each subtype extends `Sequence[A]`, so a grammar written with the parent `Sequence` still accepts all collection kinds. A grammar written with a subtype rejects other kinds at compile time:

```
// Set[Int] does NOT satisfy Sequence.List[Primitive] — compile error:
[error] Shape violation at Set: found Sequence[scala.Int], required Sequence.List[...]
val bad: Allows[Set[Int], Sequence.List[Primitive]] = implicitly
```

### DynamoDB-style set operations

A DynamoDB grammar can encode the distinction between set types and list types exactly, without any additional runtime proof. We use `Sequence.Set` to narrow the grammar to sets-only operations:

```scala mdoc:compile-only
import zio.blocks.schema.comptime.Allows
import Allows._

type N  = Primitive.Int | Primitive.Long | Primitive.Float | Primitive.Double | Primitive.Short
type S  = Primitive.String
type NS = Sequence.Set[N | Wrapped[N]]
type SS = Sequence.Set[S | Wrapped[S]]

// addSet is only callable with a Set — List[Int] or Vector[Int] would fail at compile time
def addSet[A](set: scala.collection.immutable.Set[A])(implicit
  ev: Allows[scala.collection.immutable.Set[A], NS | SS]
): String = "ok"
```

## `IsType[A]`

`IsType[A]` is a nominal type predicate. It is satisfied only when the checked Scala type is exactly `A` (i.e. `checked =:= A`). It is most useful as an element constraint inside `Sequence` subtypes, where it aligns the element type of a collection with a polymorphic method type parameter.

The primary motivation (GitHub issue #1172) is DSL methods that must constrain both the container kind and the element type in a single `Allows` expression. Without `IsType`, a separate type class (like `Containable`) is needed to connect `A` in `contains[A]` to the element type of the collection. With `IsType`, the connection is expressed directly in the grammar.

To use `IsType[A]` with a polymorphic `A`, require `IsNominalType[A]` from `zio-blocks-typeid` at the call site. This ensures the macro always sees a concrete type when it evaluates `IsType[A]`:

```scala mdoc:compile-only
import zio.blocks.schema.comptime.Allows
import Allows._
import zio.blocks.typeid.IsNominalType

// `To` must be a Set whose element type is exactly `A`.
// IsNominalType[A] ensures A is concrete at the call site — an unresolved
// type parameter would fail to produce IsNominalType and the call site
// would not compile.
def contains[To, From, A: IsNominalType](a: A)(implicit
  ev: Allows[To, Sequence.Set[IsType[A]]]
): Boolean = ev.ne(null)

// Compiles: Set[Int] satisfies Sequence.Set[IsType[Int]]
val r1: Boolean = contains[Set[Int], Nothing, Int](42)

// Compiles: Set[String] satisfies Sequence.Set[IsType[String]]
val r2: Boolean = contains[Set[String], Nothing, String]("hello")
```

A mismatch between the element type and `A` is a compile-time error:

```
[error] Shape violation at ...<element>: found Primitive(java.lang.String),
        required IsType[Int]
```

`IsType[A]` can also appear as a standalone constraint, or anywhere a grammar node is accepted:

```scala mdoc:compile-only
import zio.blocks.schema.comptime.Allows

// Int satisfies IsType[Int] exactly
val ev: Allows[Int, Allows.IsType[Int]] = implicitly

// List[String] satisfies Sequence[IsType[String]]
val ev2: Allows[List[String], Allows.Sequence[Allows.IsType[String]]] = implicitly
```

## The `Self` Grammar Node

`Self` refers back to the entire enclosing `Allows[A, S]` grammar. It allows the grammar to describe recursive data structures.

**Non-recursive types** satisfy `Self`-containing grammars without issue: if no field ever recurses back to the root type, the `Self` position is never reached, and the constraint is vacuously satisfied.

**Mutual recursion** between two or more distinct types is a compile-time error:

```
[error] Mutually recursive types are not supported by Allows.
        Cycle: Forest -> Tree -> Forest
```

## `Wrapped[A]` and Newtypes

The `Wrapped[A]` node matches ZIO Prelude `Newtype` and `Subtype` wrappers. The underlying type must satisfy `A`.

```scala
// ZIO Prelude Newtype pattern:
import zio.prelude.Newtype
object ProductCode extends Newtype[String]
type ProductCode = ProductCode.Type

given Schema[ProductCode] =
  Schema[String].transform(_.asInstanceOf[ProductCode], _.asInstanceOf[String])

// ProductCode satisfies Wrapped[Primitive] — its underlying String is Primitive
val ev: Allows[ProductCode, Wrapped[Primitive]] = implicitly
```

**Scala 3 opaque types** are resolved to their underlying type by the macro (they are transparent), so `opaque type UserId = UUID` satisfies `Primitive` (not `Wrapped[Primitive]`):

```scala
opaque type UserId = java.util.UUID
// UserId satisfies Allows[UserId, Primitive] — resolved to UUID (a primitive)
```

## Sealed Traits and Enums (Auto-Unwrap)

Sealed traits and enums are **automatically unwrapped** by the macro. Whenever a sealed type is encountered at any grammar check position, the macro recursively checks every case against the same grammar. This makes a `Variant` grammar node unnecessary.

```scala mdoc:compile-only
import zio.blocks.schema.comptime.Allows
import Allows._

sealed trait Shape
case class Circle(radius: Double)                    extends Shape
case class Rectangle(width: Double, height: Double)  extends Shape
case object Point                                    extends Shape

// No Variant node — Shape is auto-unwrapped, all cases checked against Record[Primitive]
val ev: Allows[Shape, Record[Primitive]] = implicitly
```

Auto-unwrap is recursive: if a case is itself a sealed trait, its cases are unwrapped too, to any depth.

Union branches (`A | B`) work naturally with auto-unwrap: unused branches are fine under `Allows` upper-bound semantics.

## Error Messages

When a type does not satisfy the grammar, the macro reports:

1. **The path** to the violating field: `Order.items.<element>`
2. **What was found**: `Record(OrderItem)`
3. **What was required**: `Primitive | Sequence[Primitive]`
4. **A hint** where applicable

Multiple violations are reported in a single compilation pass — the user sees all problems at once.

Example:

```
[error] Schema shape violation at UserWithAddress.address: found Record(Address),
        required Primitive | Optional[Primitive] | Map[Primitive, Primitive]
[error]   Hint: Type 'Address' does not match any allowed shape
```

## Singleton / Zero-Field Records

`Record[A]` is vacuously true for case objects and zero-field records, since there are no fields to violate the constraint:

```scala mdoc:compile-only
import zio.blocks.schema.Schema
import zio.blocks.schema.comptime.Allows
import Allows._

case object EmptyEvent
implicit val schema: Schema[EmptyEvent.type] = Schema.derived

val ev: Allows[EmptyEvent.type, Record[Primitive]] = implicitly  // vacuously true
```

## Runtime Cost

`Allows[A, S]` carries **zero runtime overhead**. The macro emits a reference to a single private singleton `Allows.instance` cast to the required type. There is no per-call-site allocation.

## Scala 2 vs Scala 3

| Feature | Scala 2 | Scala 3 |
|---|---|---|
| Union syntax | `` A `\|` B `` infix | `A \| B` native union type |
| Summon syntax | `implicitly[Allows[A, S]]` | `summon[Allows[A, S]]` or `implicitly` |
| Evidence parameter | `(implicit ev: Allows[A, S])` | `(using Allows[A, S])` |
| Opaque type detection | ZIO Prelude only | Scala 3 opaque types + ZIO Prelude + neotype |
| Derivation keyword | `Schema.derived` implicit | `Schema.derived` or `derives Schema` |

Both Scala versions produce the same macro behavior and the same error messages.
