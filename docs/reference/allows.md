---
id: allows
title: "Allows"
---

`Allows[A, S]` is a compile-time capability token that proves, at the call site, that type `A` satisfies the structural grammar `S`. It closes the gap between the runtime flexibility of `Schema[A]` and the compile-time safety that DSL authors need.

## Motivation

ZIO Blocks (ZIO Schema 2) gives library authors a powerful way to build data-oriented DSLs. A library can accept `A: Schema` and use the schema at runtime to serialize, deserialize, query, or transform values of `A` — without knowing anything about `A` at compile time.

The gap is **structural preconditions**. Many DSLs only make sense for a subset of the types that have schemas:

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
| `Primitive` | Any scalar: `Unit`, `Boolean`, `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `Char`, `String`, `BigInt`, `BigDecimal`, `UUID`, `Currency`, all `java.time.*` types |
| `Record[A]` | A case class / product type whose every field satisfies `A`. Vacuously true for zero-field records (case objects, enum singletons). |
| `Variant[A]` | A sealed trait / enum whose every case satisfies at least one branch of `A`. No requirement that all branches of `A` are exercised. |
| `Sequence[A]` | `List`, `Vector`, `Set`, `Array`, `Chunk`, … whose element type satisfies `A` |
| `Map[K, V]` | `Map`, `HashMap`, … whose key satisfies `K` and value satisfies `V` |
| `Optional[A]` | `Option[X]` where the inner type `X` satisfies `A` |
| `Wrapped[A]` | A ZIO Prelude `Newtype`/`Subtype` wrapper whose underlying type satisfies `A` |
| `Dynamic` | `DynamicValue` — the schema-less escape hatch |
| `Self` | Recursive self-reference back to the entire enclosing `Allows[A, S]` grammar |
| `` `\|` `` | Union of two grammar nodes: `A \| B`. In Scala 2 write `` A `\|` B `` in infix position. |

## Union Syntax

Union types express "or" in the grammar.

**Scala 3** uses native union type syntax:

```scala mdoc:compile-only
import zio.blocks.schema.Schema
import zio.blocks.schema.comptime.Allows
import Allows._

def writeCsv[A: Schema](rows: Seq[A])(using
  Allows[A, Record[Primitive | Optional[Primitive]]]
): Unit = ???
```

**Scala 2** uses the `` `\|` `` infix operator from `Allows`:

```scala
import zio.blocks.schema.Schema
import zio.blocks.schema.comptime.Allows
import Allows._

def writeCsv[A: Schema](rows: Seq[A])(implicit
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

Published events must be sealed traits of flat record cases:

```scala mdoc:compile-only
import zio.blocks.schema.Schema
import zio.blocks.schema.comptime.Allows
import Allows._

def publish[A: Schema](event: A)(using
  Allows[A, Variant[Record[Primitive | Optional[Primitive] | Sequence[Primitive]]]]
): Unit = ???
```

If a plain record (not a variant) is passed:

```
[error] Schema shape violation at UserRow: found Record(UserRow),
        required Variant[...]
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
object TreeNode { implicit val schema = Schema.derived[TreeNode] }
// graphqlType[TreeNode]() — compiles fine
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

```scala mdoc:compile-only
import zio.blocks.schema.Schema
import zio.blocks.schema.comptime.Allows
import Allows._
import zio.prelude.Newtype

type ProductCode = ProductCode.Type
object ProductCode extends Newtype[String] {
  implicit val schema: Schema[ProductCode] =
    Schema[String].transform(_.asInstanceOf[ProductCode], _.asInstanceOf[String])
}

// ProductCode satisfies Wrapped[Primitive] — its underlying String is Primitive
val ev: Allows[ProductCode, Wrapped[Primitive]] = implicitly
```

**Scala 3 opaque types** are resolved to their underlying type by the macro (they are transparent), so `opaque type UserId = UUID` satisfies `Primitive` (not `Wrapped[Primitive]`):

```scala
opaque type UserId = java.util.UUID
// UserId satisfies Allows[UserId, Primitive] — resolved to UUID (a primitive)
```

## `Variant[A]` Semantics

`Variant[A]` means: **every case** of the variant must satisfy at least one branch of `A`. The union inside `Variant[...]` describes allowed case shapes.

No requirement that all branches of `A` are exercised. A variant whose cases all happen to be `Record[Primitive]` satisfies `Variant[Record[Primitive] | Sequence[Primitive]]` — the `Sequence[Primitive]` branch is simply unused, which is fine under upper-bound semantics.

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
