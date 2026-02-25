# Allows: Compile-Time Schema Shape Constraints

## 1. What We Are Trying to Do and Why

ZIO Schema gives library authors a powerful way to build data-oriented DSLs without
writing macros. A library can accept `A: Schema` and use the schema at runtime to
serialize, deserialize, query, or transform values of `A` — without knowing anything
about `A` at compile time.

The gap is **structural preconditions**. Many DSLs only make sense for a subset of
the types that have schemas. A CSV serializer requires flat records of scalars. An
HTTP query-string decoder cannot handle nested maps. An event bus expects a sealed
trait of flat record cases. A recursive GraphQL emitter requires that every leaf
eventually bottoms out at a primitive. Today, these constraints can only be checked
at runtime, producing confusing errors deep inside library internals, far from where
the user called the API.

`Allows` closes this gap. It is a phantom-typed capability token verified by a macro
at the call site, at compile time, with precise, path-aware error messages and
concrete fix suggestions.

The key design insight is that `Allows[S]` is an **upper bound**: it permits any type
whose schema is structurally "no more complex than" the grammar described by `S`. A
type that uses only a subset of `S` trivially satisfies it. This is analogous to a
type bound — `A <: Foo` does not require that `A` uses every method of `Foo`.

---

## 2. Motivating Use Cases

### 2.1 RDBMS Persistence

A relational table row must be a flat record of scalars, scalar sequences, or
string-keyed maps of scalars. Nested records, variants, and recursive types cannot
be expressed as columns.

```scala
import zio.blocks.schema.Schema
import zio.blocks.schema.comptime.*
import Allows.*

def insert[A: Schema](value: A)(using
  Allows[Record[Primitive | Sequence[Primitive] | Map[String, Primitive]]]
): Unit = ???

def createTable[A: Schema]()(using
  Allows[Record[Primitive | Map[String, Primitive]]]
): String = ???
```

Error — `case class Order(id: UUID, items: List[OrderItem])` where `OrderItem` is a
record:

```
[error] Schema shape violation for Order
[error]   Required: Record[Primitive | Sequence[Primitive] | Map[String, Primitive]]
[error]   Found at Order.items: Sequence[Record[...]] — sequence element must be
[error]     Primitive, but found Record
[error]
[error]   Suggested fix: flatten OrderItem into Order, or store as
[error]   Map[String, Primitive] (e.g. JSON column)
```

### 2.2 HTTP Query String Parameters

Query strings are flat key-value pairs. Values may be scalars, optional scalars, or
comma-separated scalar lists. No nesting, no maps, no variants.

```scala
def queryParams[A: Schema](req: Request)(using
  Allows[Record[Primitive | Optional[Primitive] | Sequence[Primitive]]]
): Either[DecodeError, A]
```

Error — `case class Filter(page: Int, tags: List[List[String]])`:

```
[error] Schema shape violation for Filter
[error]   Required: Record[Primitive | Optional[Primitive] | Sequence[Primitive]]
[error]   Found at Filter.tags: Sequence[Sequence[String]]
[error]     — sequence element must be Primitive, but found Sequence
[error]
[error]   Suggested fix: use List[String] instead of List[List[String]]
```

### 2.3 CSV Serialization

CSV rows are the flattest possible structure: a record of scalars or optional
scalars. No sequences, no maps, no nesting.

```scala
def writeCsv[A: Schema](rows: Seq[A], writer: Writer)(using
  Allows[Record[Primitive | Optional[Primitive]]]
): Unit = ???

def readCsv[A: Schema](reader: Reader)(using
  Allows[Record[Primitive | Optional[Primitive]]]
): Iterator[Either[ParseError, A]] = ???
```

Error — `case class Row(id: Int, tags: Option[List[String]])`:

```
[error] Schema shape violation for Row
[error]   Required: Record[Primitive | Optional[Primitive]]
[error]   Found at Row.tags: Optional[Sequence[String]]
[error]     — inner type of Optional must be Primitive, but found Sequence
[error]
[error]   Suggested fix: use Option[String] instead of Option[List[String]]
```

### 2.4 Event Bus / Message Broker

Published events must be discriminated unions (sealed traits) of flat record cases.
The top-level type must be a variant; each case must be a flat record. A bare record
or primitive does not satisfy the constraint.

```scala
def publish[A: Schema](event: A)(using
  Allows[Variant[Record[Primitive | Sequence[Primitive]]]]
): Future[Unit] = ???

def subscribe[A: Schema](topic: String)(handler: A => Unit)(using
  Allows[Variant[Record[Primitive | Sequence[Primitive]]]]
): Subscription = ???
```

`Variant[S]` means: every case of the variant must satisfy shape `S`. The union
inside `Variant[...]` is a union of **allowed case shapes**, not a union of the
actual case types. This preserves compositionality: `Record[X]` always means "a
record whose fields satisfy `X`", regardless of whether it appears inside `Variant`.

Error — `case class OrderPlaced(id: UUID)` (a record, not a variant at top level):

```
[error] Schema shape violation for OrderPlaced
[error]   Required: Variant[Record[Primitive | Sequence[Primitive]]]
[error]   Found: Record[Primitive] — top-level type must be a Variant (sealed trait
[error]     or enum), but found Record
[error]
[error]   Suggested fix:
[error]     sealed trait OrderEvent
[error]     case class OrderPlaced(id: UUID) extends OrderEvent
[error]   then use Schema[OrderEvent]
```

### 2.5 Recursive GraphQL / Tree Structures

A GraphQL schema emitter must accept arbitrarily nested types, but every path
through the structure must eventually terminate at a primitive. `Self` is the
mechanism for expressing this: it refers back to the enclosing `Allows` constraint
recursively.

```scala
def graphqlType[A: Schema]()(using
  Allows[Record[Primitive | Optional[Self] | Sequence[Self] | Map[String, Self]]]
): GraphQLTypeDefinition = ???
```

`Self` means: at any recursive field position, the nested type must itself satisfy
the entire enclosing `Allows[...]` grammar. This allows a `Record` field to contain
another `Record` (via `Self`), which may itself have further nested `Record`s, and
so on — as long as every branch terminates at `Primitive`.

```scala
// Rose tree: each node has a value and children
case class TreeNode(value: Int, children: List[TreeNode])

def buildIndex[A: Schema](root: A)(using
  Allows[Record[Primitive | Sequence[Self]]]
): SearchIndex = ???
```

`TreeNode.children: List[TreeNode]` satisfies `Sequence[Self]` because `TreeNode`
itself satisfies `Record[Primitive | Sequence[Self]]`. The macro detects the
self-referential cycle and validates termination.

Mutual recursion (`Forest` ↔ `Tree`) is a **compile-time error** due to the
complexity of expressing and validating cross-type recursive grammars.

---

## 3. High-Level Structure of `zio.blocks.schema.comptime`

### 3.1 Location

`Allows` lives in the **existing `schema` module**, in the package
`zio.blocks.schema.comptime`. No new sbt module is created. The source layout
mirrors the conventions already in place for the `schema` module:

```
schema/
  shared/
    src/
      main/
        scala/
          zio/blocks/schema/comptime/
            Allows.scala                    ← phantom type + grammar nodes (pure types,
                                              cross-version)
        scala-2/
          zio/blocks/schema/comptime/
            AllowsCompanionVersionSpecific.scala  ← Scala 2 macro + |[A,B] union emulation
        scala-3/
          zio/blocks/schema/comptime/
            AllowsCompanionVersionSpecific.scala  ← Scala 3 macro + native | union support
      test/
        scala/
          zio/blocks/schema/comptime/
            AllowsSpec.scala                ← positive compile-time tests (cross-version)
        scala-2/
          zio/blocks/schema/comptime/
            AllowsNegativeSpec.scala        ← typeCheckErrors assertions (Scala 2)
        scala-3/
          zio/blocks/schema/comptime/
            AllowsNegativeSpec.scala        ← typeCheckErrors assertions (Scala 3)
```

### 3.2 The `Allows[S]` Phantom Type

`Allows.scala` contains the shared cross-version declarations. The companion object
extends `AllowsCompanionVersionSpecific`, which provides the version-specific macro
synthesis of `given` instances.

```scala
package zio.blocks.schema.comptime

/**
 * A compile-time capability token that constrains the shape of a schema.
 * `Allows[S]` is satisfied when the type `A` for which a `Schema[A]` is in
 * scope is structurally compatible with the grammar described by `S`.
 *
 * Allows is an upper bound: any type "simpler than" S also satisfies it.
 * The instance carries no runtime data and is represented as a single
 * private singleton cast to the required phantom type.
 */
sealed abstract class Allows[S <: Allows.Structural]

object Allows extends AllowsCompanionVersionSpecific {

  // Single private singleton; the macro casts this to Allows[S] for any S.
  // This eliminates any per-call-site allocation.
  private[comptime] val instance: Allows[Any] = new Allows[Any] {}

  /** Root of the grammar type hierarchy. All shape descriptors extend this. */
  abstract class Structural

  /**
   * Matches any primitive type (all 30 PrimitiveType cases:
   * Unit, Boolean, Byte, Short, Int, Long, Float, Double, Char, String,
   * BigInt, BigDecimal, UUID, Currency, and all java.time.* types).
   */
  abstract class Primitive extends Structural

  /**
   * Matches a record (case class / product) whose every field satisfies A.
   * Vacuously true for zero-field records (case objects / enum singletons).
   */
  abstract class Record[A <: Structural] extends Structural

  /**
   * Matches a variant (sealed trait / enum) whose every case satisfies at
   * least one branch of A. A is a union of allowed case shapes (not a union
   * of the concrete case types). No requirement that all branches of A are
   * exercised by an actual case.
   */
  abstract class Variant[A <: Structural] extends Structural

  /**
   * Matches a sequence (List, Vector, Chunk, Set, Array, etc.) whose element
   * type satisfies A.
   */
  abstract class Sequence[A <: Structural] extends Structural

  /**
   * Matches a map (Map, HashMap, etc.) whose key satisfies K and whose value
   * satisfies V.
   */
  abstract class Map[K <: Structural, V <: Structural] extends Structural

  /**
   * Matches Option[A] where the inner type satisfies A.
   * Option is handled as a dedicated grammar node rather than as a generic
   * two-case Variant, so that users can express Optional[Primitive] without
   * being forced to spell out the full Option Variant structure.
   */
  abstract class Optional[A <: Structural] extends Structural

  /**
   * Matches a wrapper / newtype / opaque type whose underlying type satisfies A.
   */
  abstract class Wrapped[A <: Structural] extends Structural

  /**
   * Matches DynamicValue — the schema-less escape hatch. No further constraint.
   */
  abstract class Dynamic extends Structural

  /**
   * Recursive self-reference. When used inside a grammar expression, Self
   * refers back to the entire enclosing Allows[...] constraint, allowing the
   * grammar to describe recursive data structures. Mutual recursion between
   * two or more distinct types is a compile-time error.
   */
  abstract class Self extends Structural

  /**
   * Union of two grammar nodes. Used in Scala 2 to emulate the native `|`
   * union type syntax available in Scala 3. In Scala 3, use `A | B` directly.
   *
   * Example (Scala 2):
   *   Allows[Record[|[Primitive, |[Sequence[Primitive], Map[Primitive, Primitive]]]]]
   *
   * Equivalent (Scala 3):
   *   Allows[Record[Primitive | Sequence[Primitive] | Map[Primitive, Primitive]]]
   */
  abstract class |[A <: Structural, B <: Structural] extends Structural
}
```

### 3.3 The Macro

The macro is provided in `AllowsCompanionVersionSpecific.scala`, one version per
Scala major version, mixed into the `Allows` companion object.

#### Scala 3 — `scala-3/zio/blocks/schema/comptime/AllowsCompanionVersionSpecific.scala`

```scala
package zio.blocks.schema.comptime

import scala.quoted.*

trait AllowsCompanionVersionSpecific {
  inline given derived[S <: Allows.Structural, A]: Allows[S] =
    ${ AllowsMacro.deriveAllows[S, A] }
}

object AllowsMacro {
  def deriveAllows[S <: Allows.Structural : Type, A : Type](
    using Quotes
  ): Expr[Allows[S]] = ???
}
```

#### Scala 2 — `scala-2/zio/blocks/schema/comptime/AllowsCompanionVersionSpecific.scala`

```scala
package zio.blocks.schema.comptime

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

trait AllowsCompanionVersionSpecific {
  implicit def derived[S <: Allows.Structural, A]: Allows[S] =
    macro AllowsMacro.deriveAllows[S, A]
}

object AllowsMacro {
  def deriveAllows[S <: Allows.Structural, A](c: whitebox.Context): c.Expr[Allows[S]] = ???
}
```

#### Macro Algorithm

The macro receives two type arguments at elaboration time:

- `S` — the grammar type (the phantom type parameter of `Allows`)
- `A` — the user's data type inferred at the call site

**It does not use `Schema[A]` or `Reflect` at compile time.** Both are runtime
constructs. Instead, the macro inspects the Scala type structure of `A` directly,
using:

- **Scala 3**: `scala.quoted.quotes.reflect.TypeRepr`, `Symbol`, and optionally
  `scala.deriving.Mirror` for product/sum decomposition
- **Scala 2**: `c.universe.Type`, `c.universe.Symbol`, and `TypeTag`

The macro proceeds as follows:

**Step 1 — Decompose `S` into a grammar tree** by inspecting the `TypeRepr` / `Type`
of `S`:

- `Allows.Primitive` → `GrammarNode.Primitive`
- `Allows.Record[inner]` → `GrammarNode.Record(decompose(inner))`
- `Allows.Variant[inner]` → `GrammarNode.Variant(decomposeUnion(inner))`
- `Allows.Sequence[inner]` → `GrammarNode.Sequence(decompose(inner))`
- `Allows.Map[k, v]` → `GrammarNode.Map(decompose(k), decompose(v))`
- `Allows.Optional[inner]` → `GrammarNode.Optional(decompose(inner))`
- `Allows.Wrapped[inner]` → `GrammarNode.Wrapped(decompose(inner))`
- `Allows.Dynamic` → `GrammarNode.Dynamic`
- `Allows.Self` → `GrammarNode.Self`
- Scala 3 native union `A | B` → `GrammarNode.Union(List(decompose(A), decompose(B)))`
- `Allows.|[A, B]` (Scala 2 emulation) → `GrammarNode.Union(List(decompose(A), decompose(B)))`

**Step 2 — Inspect the type structure of `A`** by examining the `Symbol` of `A`:

- If `A` is a primitive (Boolean, Int, String, UUID, Instant, etc.) →
  `TypeNode.Primitive`
- If `A` is a case class / product → `TypeNode.Record(fields: List[(name, TypeNode)])`
  by inspecting primary constructor parameters
- If `A` is a sealed trait / abstract class / enum → `TypeNode.Variant(cases: List[TypeNode])`
  by inspecting `knownDirectSubclasses` (Scala 2) or `Symbol.children` (Scala 3)
- If `A` is `Option[B]` → `TypeNode.Optional(inspect(B))`
- If `A` is a known collection type (List, Vector, Set, Chunk, Array, etc.) →
  `TypeNode.Sequence(inspect(elementType))`
- If `A` is a known map type (Map, HashMap, etc.) →
  `TypeNode.Map(inspect(keyType), inspect(valueType))`
- If `A` is an opaque type / newtype wrapper → `TypeNode.Wrapped(inspect(underlyingType))`
- If `A` is `DynamicValue` → `TypeNode.Dynamic`
- If `A` is a type alias → dereference and re-inspect

**Step 3 — Walk `TypeNode` against `GrammarNode`**, carrying:
- The current grammar node `G`
- The path so far (list of field/case names for error messages)
- A `seen: Set[TypeSymbol]` for cycle detection (mutual recursion → error)
- The root type `A0` and root grammar `G0` for `Self` resolution

Check table:

| Type node | Grammar node | Result |
|---|---|---|
| `TypeNode.Primitive` | `GrammarNode.Primitive` | ✓ |
| `TypeNode.Primitive` | `GrammarNode.Union(gs)` | ✓ if any `g` in `gs` accepts Primitive |
| `TypeNode.Record(fields)` | `GrammarNode.Record(inner)` | recurse each field against `inner` |
| `TypeNode.Record(fields)` | `GrammarNode.Union(gs)` | ✓ if any `g` in `gs` is `Record(...)` that accepts all fields |
| `TypeNode.Variant(cases)` | `GrammarNode.Variant(inners)` | each case must satisfy `Union(inners)` |
| `TypeNode.Sequence(elem)` | `GrammarNode.Sequence(inner)` | recurse `elem` against `inner` |
| `TypeNode.Map(k, v)` | `GrammarNode.Map(gk, gv)` | recurse `k` against `gk`, `v` against `gv` |
| `TypeNode.Wrapped(inner)` | `GrammarNode.Wrapped(g)` | recurse `inner` against `g` |
| `TypeNode.Dynamic` | `GrammarNode.Dynamic` | ✓ |
| `TypeNode.Optional(inner)` | `GrammarNode.Optional(g)` | recurse `inner` against `g` |
| any — type symbol in `seen`, symbol ≠ root | any | ✗ compile error: mutual recursion |
| any — type symbol in `seen`, symbol == root | `GrammarNode.Self` | ✓ self-recursion accepted |
| any — type symbol in `seen`, symbol == root | other grammar | recurse root against root grammar |
| any | `GrammarNode.Self` | recurse type against root grammar `G0` |

**Step 4 — Error accumulation**: all violations are collected before reporting, so
the user sees the full set of problems in one compilation pass rather than being
forced to fix one at a time.

**Step 5 — Error reporting**: each error includes:
- The path to the violating node (`Order.items`, `Filter.tags.elem`, etc.)
- What was found (`Sequence[Record[...]]`)
- What was required at that position
- A concrete suggested fix where possible

**Step 6 — Success**: the macro emits
`Allows.instance.asInstanceOf[Allows[S]]`. The singleton `Allows.instance` is
allocated once, privately, in the companion object. Every successful call site
reuses this same instance via a cast — zero per-call-site allocation.

---

## 4. Data Types for Exhaustive Testing

The following data types are the shared fixture for all test suites. They are
designed to cover every grammar node, every combination, and every edge case.

### 4.1 Primitive-only types

```scala
// Positive: all primitive types as top-level Record fields
case class AllPrimitives(
  unit: Unit,
  boolean: Boolean,
  byte: Byte,
  short: Short,
  int: Int,
  long: Long,
  float: Float,
  double: Double,
  char: Char,
  str: String,
  bigInt: BigInt,
  bigDecimal: BigDecimal,
  uuid: java.util.UUID,
  currency: java.util.Currency,
  instant: java.time.Instant,
  localDate: java.time.LocalDate,
  localDateTime: java.time.LocalDateTime,
  localTime: java.time.LocalTime,
  zonedDateTime: java.time.ZonedDateTime,
  offsetDateTime: java.time.OffsetDateTime,
  offsetTime: java.time.OffsetTime,
  duration: java.time.Duration,
  period: java.time.Period,
  year: java.time.Year,
  yearMonth: java.time.YearMonth,
  monthDay: java.time.MonthDay,
  month: java.time.Month,
  dayOfWeek: java.time.DayOfWeek,
  zoneId: java.time.ZoneId,
  zoneOffset: java.time.ZoneOffset
)

// Degenerate: zero-field record (case object)
case object EmptyRecord
case object SingletonEvent
```

### 4.2 Optional fields

```scala
case class WithOptionalPrimitive(id: Int, name: Option[String])
case class WithOptionalRecord(id: Int, address: Option[Address])
case class NestedOption(x: Option[Option[Int]])  // negative: not Allows[Record[Optional[Primitive]]]
```

### 4.3 Sequence fields

```scala
case class WithSeqPrimitive(ids: List[Int], names: Vector[String])
case class WithSeqRecord(orders: List[Order])      // negative for flat-only constraints
case class WithSeqSeq(matrix: List[List[Int]])     // negative: nested sequence
case class WithChunk(data: zio.blocks.chunk.Chunk[String])
case class WithSet(tags: Set[String])
```

### 4.4 Map fields

```scala
case class WithStringMap(meta: Map[String, Int])
case class WithStringMapRecord(meta: Map[String, Address])  // negative for Primitive-valued map
case class WithIntMap(counts: Map[Int, String])             // negative for Map[String, _] constraints
```

### 4.5 Nested records (depth 2)

```scala
case class Address(street: String, city: String, zip: String)
case class Person(name: String, age: Int, address: Address)
// Person satisfies Allows[Record[Primitive | Self]] but not Allows[Record[Primitive]]
```

### 4.6 Variants — flat cases

```scala
sealed trait Shape
case class Circle(radius: Double) extends Shape
case class Rectangle(width: Double, height: Double) extends Shape
case object Point extends Shape  // zero-field case — satisfies Record[Primitive] vacuously
```

### 4.7 Variants — cases with sequences

```scala
sealed trait Event
case class UserCreated(id: UUID, name: String) extends Event
case class TagsUpdated(id: UUID, tags: List[String]) extends Event
case class OrderPlaced(id: UUID, items: List[OrderItem]) extends Event  // OrderItem is a record
```

### 4.8 Variants — nested variant cases

```scala
sealed trait Outer
sealed trait Inner extends Outer
case class InnerA(x: Int) extends Inner
case class InnerB(y: String) extends Inner
case class OuterC(z: Boolean) extends Outer
```

### 4.9 Wrapper / newtype types

```scala
opaque type UserId = UUID
object UserId { def apply(u: UUID): UserId = u }

opaque type Amount = BigDecimal
object Amount { def apply(n: BigDecimal): Amount = n }

case class Invoice(id: UserId, total: Amount)
// UserId satisfies Allows[Wrapped[Primitive]]
// Invoice satisfies Allows[Record[Wrapped[Primitive]]]
```

### 4.10 Dynamic

```scala
case class WithDynamic(name: String, payload: DynamicValue)
// satisfies Allows[Record[Primitive | Dynamic]]
// does NOT satisfy Allows[Record[Primitive]]
```

### 4.11 Recursive (self-referential) types

```scala
// Direct self-recursion — satisfies Allows[Record[Primitive | Sequence[Self]]]
case class TreeNode(value: Int, children: List[TreeNode])

// Direct self-recursion with optional — satisfies Allows[Record[Primitive | Optional[Self]]]
case class LinkedList(value: String, next: Option[LinkedList])

// Mutually recursive — compile-time error regardless of grammar
case class Forest(trees: List[Tree])
case class Tree(value: Int, children: Forest)
```

### 4.12 Option at top-level (not inside Record)

```scala
// Top-level Option — satisfies Allows[Optional[Primitive]] but not Allows[Record[_]]
// (tested via the macro directly, not via Schema)
```

### 4.13 Complex realistic types (positive and negative cross-tests)

```scala
// RDBMS-compatible
case class UserRow(id: UUID, name: String, age: Int, email: Option[String])

// RDBMS-incompatible (nested record)
case class OrderRow(id: UUID, customer: Person, amount: BigDecimal)

// Event-bus-compatible
sealed trait DomainEvent
case class AccountOpened(id: UUID, owner: String) extends DomainEvent
case class FundsDeposited(accountId: UUID, amount: BigDecimal) extends DomainEvent
case class AccountClosed(id: UUID) extends DomainEvent

// Event-bus-incompatible (not a variant at top level)
case class JustARecord(id: UUID, value: Int)

// GraphQL-compatible (recursive, all leaves primitive)
case class Category(name: String, subcategories: List[Category])

// GraphQL-incompatible (DynamicValue leaf — not primitive)
case class BadNode(name: String, extra: DynamicValue, children: List[BadNode])
```

---

## 5. Test Suites

### 5.1 `AllowsPrimitiveSpec`

**Purpose**: `Allows[Primitive]` for every one of the 30 primitive types, tested
individually and in combination.

**Positive cases**:
- Each of the 30 primitive types satisfies `Allows[Primitive]` when used as a
  top-level type (e.g. `Int`, `UUID`, `Instant`, etc.)

**Negative cases**:
- `List[Int]` does NOT satisfy `Allows[Primitive]`
- `Option[Int]` does NOT satisfy `Allows[Primitive]`
- `AllPrimitives` (a Record) does NOT satisfy `Allows[Primitive]`
- `Shape` (a Variant) does NOT satisfy `Allows[Primitive]`

### 5.2 `AllowsRecordSpec`

**Purpose**: `Allows[Record[...]]` grammar variations.

**Positive cases**:
- `AllPrimitives` satisfies `Allows[Record[Primitive]]`
- `EmptyRecord` (case object) satisfies `Allows[Record[Primitive]]` (vacuously)
- `SingletonEvent` satisfies `Allows[Record[Primitive]]` (vacuously)
- `WithOptionalPrimitive` satisfies `Allows[Record[Primitive | Optional[Primitive]]]`
- `WithSeqPrimitive` satisfies `Allows[Record[Primitive | Sequence[Primitive]]]`
- `WithStringMap` satisfies `Allows[Record[Primitive | Map[Primitive, Primitive]]]`
- `UserRow` satisfies `Allows[Record[Primitive | Optional[Primitive]]]`
- `Invoice` satisfies `Allows[Record[Wrapped[Primitive]]]`
- `WithDynamic` satisfies `Allows[Record[Primitive | Dynamic]]`
- `Person` satisfies `Allows[Record[Primitive | Self]]`
  (the `address: Address` field is itself a `Record[Primitive]`, which satisfies `Self`)

**Negative cases**:
- `Person` does NOT satisfy `Allows[Record[Primitive]]` (address field is a Record)
- `WithSeqRecord` does NOT satisfy `Allows[Record[Primitive | Sequence[Primitive]]]`
  (sequence element is Record, not Primitive)
- `WithSeqSeq` does NOT satisfy `Allows[Record[Primitive | Sequence[Primitive]]]`
  (inner sequence element is not Primitive)
- `NestedOption` does NOT satisfy `Allows[Record[Optional[Primitive]]]`
- `OrderRow` does NOT satisfy `Allows[Record[Primitive]]` (customer is Record)
- `WithDynamic` does NOT satisfy `Allows[Record[Primitive]]` (payload is Dynamic)

### 5.3 `AllowsVariantSpec`

**Purpose**: `Allows[Variant[...]]` grammar variations.

**Positive cases**:
- `Shape` satisfies `Allows[Variant[Record[Primitive]]]`
  - `Point` (zero-field) satisfies vacuously
  - `Circle`, `Rectangle` satisfy with primitive fields
- `Event` with `UserCreated`, `TagsUpdated` satisfies
  `Allows[Variant[Record[Primitive | Sequence[Primitive]]]]`
- `DomainEvent` satisfies `Allows[Variant[Record[Primitive]]]`
- `Shape` satisfies `Allows[Variant[Record[Primitive] | Primitive]]`
  (Primitive branch unused — fine under Allows semantics)
- `Outer` with nested sub-sealed-trait satisfies
  `Allows[Variant[Record[Primitive]]]` if all leaf cases have primitive fields

**Negative cases**:
- `JustARecord` does NOT satisfy `Allows[Variant[Record[Primitive]]]`
  (top-level is Record, not Variant)
- `Event` with `OrderPlaced` does NOT satisfy
  `Allows[Variant[Record[Primitive | Sequence[Primitive]]]]`
  (`OrderPlaced.items` is `List[OrderItem]` where `OrderItem` is a Record)
- `AllPrimitives` (a Record) does NOT satisfy `Allows[Variant[Record[Primitive]]]`

### 5.4 `AllowsSequenceSpec`

**Purpose**: `Allows[Sequence[...]]` at top-level and nested.

**Positive cases**:
- `List[Int]` satisfies `Allows[Sequence[Primitive]]`
- `Vector[String]` satisfies `Allows[Sequence[Primitive]]`
- `List[Address]` satisfies `Allows[Sequence[Record[Primitive]]]`
- `List[List[Int]]` satisfies `Allows[Sequence[Sequence[Primitive]]]`
- `Chunk[String]` satisfies `Allows[Sequence[Primitive]]`
- `Set[Int]` satisfies `Allows[Sequence[Primitive]]`

**Negative cases**:
- `List[Address]` does NOT satisfy `Allows[Sequence[Primitive]]`
- `List[List[Int]]` does NOT satisfy `Allows[Sequence[Primitive]]`

### 5.5 `AllowsMapSpec`

**Purpose**: `Allows[Map[K, V]]` at top-level and nested.

**Positive cases**:
- `Map[String, Int]` satisfies `Allows[Map[Primitive, Primitive]]`
- `Map[String, Address]` satisfies `Allows[Map[Primitive, Record[Primitive]]]`
- `Map[Int, List[String]]` satisfies `Allows[Map[Primitive, Sequence[Primitive]]]`

**Negative cases**:
- `Map[String, Address]` does NOT satisfy `Allows[Map[Primitive, Primitive]]`
- `Map[List[Int], String]` does NOT satisfy `Allows[Map[Primitive, Primitive]]`

### 5.6 `AllowsOptionalSpec`

**Purpose**: `Allows[Optional[...]]` at top-level and nested.

**Positive cases**:
- `Option[Int]` satisfies `Allows[Optional[Primitive]]`
- `Option[Address]` satisfies `Allows[Optional[Record[Primitive]]]`
- `Option[List[Int]]` satisfies `Allows[Optional[Sequence[Primitive]]]`

**Negative cases**:
- `Option[Address]` does NOT satisfy `Allows[Optional[Primitive]]`
- `Option[List[Int]]` does NOT satisfy `Allows[Optional[Primitive]]`
- `Option[Option[Int]]` does NOT satisfy `Allows[Optional[Primitive]]`

### 5.7 `AllowsWrappedSpec`

**Purpose**: `Allows[Wrapped[...]]` for opaque/newtype types.

**Positive cases**:
- `UserId` satisfies `Allows[Wrapped[Primitive]]`
- `Amount` satisfies `Allows[Wrapped[Primitive]]`
- `Invoice` satisfies `Allows[Record[Wrapped[Primitive]]]`

**Negative cases**:
- `UserId` does NOT satisfy `Allows[Primitive]`
  (it is `Wrapped[Primitive]`, not a bare primitive)
- `UserId` does NOT satisfy `Allows[Record[Primitive]]`

### 5.8 `AllowsDynamicSpec`

**Purpose**: `Allows[Dynamic]` and rejection of `Dynamic` where not permitted.

**Positive cases**:
- `DynamicValue` satisfies `Allows[Dynamic]`
- `WithDynamic` satisfies `Allows[Record[Primitive | Dynamic]]`

**Negative cases**:
- `DynamicValue` does NOT satisfy `Allows[Primitive]`
- `WithDynamic` does NOT satisfy `Allows[Record[Primitive]]`

### 5.9 `AllowsRecursiveSpec`

**Purpose**: `Self` in recursive grammars — positive self-recursion cases, negative
cases where the recursive type violates the grammar, and compile-time errors for
mutual recursion.

**Positive cases**:
- `TreeNode` satisfies `Allows[Record[Primitive | Sequence[Self]]]`
  (`children: List[TreeNode]` — the element type is `TreeNode` itself, which
  satisfies the enclosing grammar recursively)
- `LinkedList` satisfies `Allows[Record[Primitive | Optional[Self]]]`
  (`next: Option[LinkedList]` — same reasoning)
- `Category` satisfies
  `Allows[Record[Primitive | Sequence[Self] | Map[String, Self]]]`
- Non-recursive types satisfy `Self`-containing grammars without issue, since the
  `Self` position is never reached:
  - `AllPrimitives` satisfies `Allows[Record[Primitive | Sequence[Self]]]`
  - `UserRow` satisfies `Allows[Record[Primitive | Optional[Self]]]`

**Negative cases — grammar violation in recursive type**:
- `BadNode` does NOT satisfy `Allows[Record[Primitive | Sequence[Self]]]`
  (the `extra: DynamicValue` field violates `Primitive | Sequence[Self]`)
- `TreeNode` does NOT satisfy `Allows[Record[Primitive]]`
  (the `children` field is `List[TreeNode]`, a Sequence, not a Primitive)

**Negative cases — mutual recursion (compile-time error)**:
- `Forest` produces a compile-time error for any `Allows[...]` constraint:
  ```
  [error] Mutual recursion detected for Forest:
  [error]   Forest → Tree → Forest
  [error]   Mutually recursive types are not supported by Allows
  ```
- `Tree` produces the same error (either type in the cycle triggers it)

### 5.10 `AllowsUnionGrammarSpec`

**Purpose**: Union types (`A | B | C` in Scala 3; `|[A, |[B, C]]` in Scala 2) in
grammar positions — at field level, variant case level, and at top level.

**Positive cases**:
- `AllPrimitives` satisfies `Allows[Record[Primitive | Optional[Primitive]]]`
  (fields are all primitives; Optional branch unused — fine under Allows semantics)
- `Shape` satisfies `Allows[Variant[Record[Primitive] | Primitive]]`
  (Primitive branch unused — fine)
- `WithSeqPrimitive` satisfies
  `Allows[Record[Primitive | Sequence[Primitive] | Map[Primitive, Primitive]]]`
- `UserRow` satisfies
  `Allows[Record[Primitive | Optional[Primitive] | Sequence[Primitive]]]`

**Negative cases**:
- `WithSeqRecord` does NOT satisfy
  `Allows[Record[Primitive | Sequence[Primitive]]]`
  (the sequence element is a Record which does not match `Primitive`)

### 5.11 `AllowsErrorMessageSpec`

**Purpose**: Verify the quality and precision of compile-time error messages.

This suite uses `typeCheckErrors` (from ZIO Test) to assert that:

1. The error message names the violating field path precisely
   (`Order.items`, not just `items`)
2. The error message states what was found (`Sequence[Record[...]]`)
3. The error message states what was required at that position (`Sequence[Primitive]`)
4. The error message includes a suggested fix
5. For mutual recursion, the error identifies both types in the cycle
6. For top-level shape mismatch (Record where Variant required), the error
   correctly names the outer mismatch
7. **All violations are reported in a single compilation pass** — not short-circuited
   after the first error; a type with 3 violating fields must produce 3 errors

### 5.12 `AllowsCompositionSpec`

**Purpose**: Verify that `Allows` composes correctly when used in realistic
library-style signatures.

```scala
// Simulated RDBMS insert — compile-time check at call site
def testInsertCompiles(): Unit = {
  def insert[A: Schema](v: A)(using Allows[Record[Primitive]]): Unit = ()
  insert(UserRow(UUID.randomUUID(), "Alice", 30, Some("a@b.com")))  // ✓
}

def testInsertRejects(): Unit = {
  def insert[A: Schema](v: A)(using Allows[Record[Primitive]]): Unit = ()
  // must not compile:
  insert(Person("Alice", 30, Address("1 Main St", "Springfield", "12345")))
}

// Simulated event bus publish
def testPublishCompiles(): Unit = {
  def publish[A: Schema](e: A)(using Allows[Variant[Record[Primitive]]]): Unit = ()
  publish(AccountOpened(UUID.randomUUID(), "Alice"))  // ✓ (DomainEvent)
}

def testPublishRejects(): Unit = {
  def publish[A: Schema](e: A)(using Allows[Variant[Record[Primitive]]]): Unit = ()
  // must not compile:
  publish(UserRow(UUID.randomUUID(), "Alice", 30, None))
}
```

---

## 6. Implementation Notes

- **Runtime allocation**: The `Allows[S]` instance produced by the macro is
  `Allows.instance.asInstanceOf[Allows[S]]` — a single private singleton declared
  once in the companion object and reused at every call site via a cast. There is no
  per-call-site allocation.

- **Type inspection, not Reflect**: The macro inspects the Scala type structure of
  `A` directly using `TypeRepr` (Scala 3) or `c.universe.Type` (Scala 2). It does
  not use `Schema[A]` or `Reflect` — those are runtime constructs not available at
  macro expansion time. Mirrors (`scala.deriving.Mirror`) may be used in Scala 3 as
  a convenient alternative to direct symbol inspection for product/sum decomposition.

- **Recursive type detection**: When the macro encounters a type it has already
  visited (tracked via a `Set` of type symbols):
  - If the symbol matches the root type `A0` → self-recursion, accepted under `Self`
  - If the symbol is a different type already in the set → mutual recursion,
    compile-time error identifying the cycle

- **`Option[A]` special-casing**: `Option[A]` is a sealed trait with two cases
  (`Some` and `None`) but is surfaced in the grammar as `Optional[A]` for
  ergonomics. The macro recognises `Option` by its fully-qualified type constructor
  and routes it through `GrammarNode.Optional` checking.

- **Scala 2 union emulation**: Scala 2 does not have native union types. The `|[A, B]`
  type in `Allows` companion provides the equivalent of `A | B`. Users nest `|` for
  three-or-more alternatives: `|[A, |[B, C]]`. The macro handles both `|[A, B]`
  and Scala 3 native `A | B` via the same `GrammarNode.Union` internal
  representation.

- **Location**: All files live within the existing `schema` module under
  `zio.blocks.schema.comptime`. No new sbt module or `build.sbt` changes are
  required beyond any coverage threshold adjustments.
