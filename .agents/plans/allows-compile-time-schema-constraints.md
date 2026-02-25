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

### 3.1 Module

A new sbt cross-project `schema-comptime` (JVM + Scala.js) that depends on `schema`.
It lives entirely in Scala 3 (macro implementation requires Scala 3 `quotes`). The
package is `zio.blocks.schema.comptime`.

```
schema-comptime/
  shared/
    src/
      main/
        scala/
          zio/blocks/schema/comptime/
            Allows.scala          ← phantom type + grammar nodes (pure types)
        scala-3/
          zio/blocks/schema/comptime/
            AllowsMacro.scala     ← macro implementation
            AllowsVersionSpecific.scala ← `given Allows[S]` synthesised by macro
      test/
        scala/
          zio/blocks/schema/comptime/
            AllowsSpec.scala      ← positive and negative compile-time tests
        scala-3/
          zio/blocks/schema/comptime/
            AllowsNegativeSpec.scala ← typeCheckErrors assertions
```

### 3.2 The `Allows[S]` Phantom Type

```scala
package zio.blocks.schema.comptime

/**
 * A compile-time capability token that constrains the shape of a schema.
 * `Allows[S]` is satisfied when the schema of the implicit `Schema[A]` in
 * scope is structurally compatible with the grammar described by `S`.
 *
 * Allows is an upper bound: any schema "simpler than" S also satisfies it.
 */
sealed abstract class Allows[S <: Allows.Structural]

object Allows {

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
   * Optional is a convenience alias; Option is derived as Variant internally
   * but the macro recognises it structurally and applies this grammar node.
   */
  abstract class Optional[A <: Structural] extends Structural

  /**
   * Matches a wrapper / newtype / opaque type (Reflect.Wrapper) whose
   * underlying type satisfies A.
   */
  abstract class Wrapped[A <: Structural] extends Structural

  /**
   * Matches DynamicValue (Reflect.Dynamic) — the schema-less escape hatch.
   * No further constraint.
   */
  abstract class Dynamic extends Structural

  /**
   * Recursive self-reference. When used inside a grammar expression,
   * Self refers back to the entire enclosing Allows[...] constraint.
   * Allows the grammar to describe recursive data structures.
   * Mutual recursion is a compile-time error.
   */
  abstract class Self extends Structural
}
```

### 3.3 The Macro

`AllowsMacro.scala` (Scala 3 only) provides:

```scala
package zio.blocks.schema.comptime

import scala.quoted.*
import zio.blocks.schema.{Schema, Reflect}

object AllowsMacro {
  def deriveAllows[S <: Allows.Structural : Type, A : Type](
    using Quotes
  ): Expr[Allows[S]] = ???
}
```

The macro is invoked via a `given` synthesis in `AllowsVersionSpecific.scala`:

```scala
package zio.blocks.schema.comptime

import scala.quoted.*
import zio.blocks.schema.Schema

trait AllowsVersionSpecific {
  inline given derived[S <: Allows.Structural, A](
    using schema: Schema[A]
  ): Allows[S] = ${ AllowsMacro.deriveAllows[S, A] }
}
```

#### Macro Algorithm

The macro receives two type arguments at elaboration time:

- `S` — the grammar type (the phantom type parameter of `Allows`)
- `A` — the user's data type (from the `Schema[A]` in scope)

It proceeds as follows:

1. **Decompose `S`** into a grammar tree by inspecting the `TypeRepr` of `S`:
   - `Allows.Primitive` → `GrammarNode.Primitive`
   - `Allows.Record[inner]` → `GrammarNode.Record(decompose(inner))`
   - `Allows.Variant[inner]` → `GrammarNode.Variant(decomposeUnion(inner))`
   - `Allows.Sequence[inner]` → `GrammarNode.Sequence(decompose(inner))`
   - `Allows.Map[k, v]` → `GrammarNode.Map(decompose(k), decompose(v))`
   - `Allows.Optional[inner]` → `GrammarNode.Optional(decompose(inner))`
   - `Allows.Wrapped[inner]` → `GrammarNode.Wrapped(decompose(inner))`
   - `Allows.Dynamic` → `GrammarNode.Dynamic`
   - `Allows.Self` → `GrammarNode.Self`
   - Union `A | B` → `GrammarNode.Union(List(decompose(A), decompose(B)))`

2. **Obtain the `Reflect` tree** of `A` by summoning `Schema[A]` and reading its
   `reflect` field. The `Reflect` tree is inspected structurally via `Reflect`'s
   pattern-matchable node types.

3. **Walk `Reflect` against the grammar**, carrying:
   - The current grammar node `G`
   - The path so far (list of field/case names for error messages)
   - A `seen: Set[TypeId]` for cycle detection (mutual recursion → error)
   - The root grammar `G0` for `Self` resolution

   At each step, `check(reflect: Reflect[_, _], grammar: GrammarNode, path: List[String])`:

   | Reflect node | Grammar node | Result |
   |---|---|---|
   | `Reflect.Primitive` | `GrammarNode.Primitive` | ✓ |
   | `Reflect.Primitive` | `GrammarNode.Union(gs)` | ✓ if any `g` in `gs` accepts Primitive |
   | `Reflect.Record(fields)` | `GrammarNode.Record(inner)` | recurse each field against `inner` |
   | `Reflect.Record(fields)` | `GrammarNode.Union(gs)` | ✓ if any `g` in `gs` is `Record(...)` that accepts all fields |
   | `Reflect.Variant(cases)` | `GrammarNode.Variant(inners)` | each case must satisfy `Union(inners)` |
   | `Reflect.Sequence(elem)` | `GrammarNode.Sequence(inner)` | recurse `elem` against `inner` |
   | `Reflect.Map(k, v)` | `GrammarNode.Map(gk, gv)` | recurse `k` against `gk`, `v` against `gv` |
   | `Reflect.Wrapper(inner)` | `GrammarNode.Wrapped(g)` | recurse `inner` against `g` |
   | `Reflect.Dynamic` | `GrammarNode.Dynamic` | ✓ |
   | `Reflect.Deferred(...)` | any — `typeId` in `seen` | ✗ compile error: mutual recursion |
   | `Reflect.Deferred(...)` | `GrammarNode.Self` | ✓ self-recursion accepted |
   | `Reflect.Deferred(...)` | any — `typeId` == root typeId | recurse root against root grammar |
   | any | `GrammarNode.Self` | recurse against root grammar `G0` |

   Special rule for `Option[A]`: the macro recognises `Option` by `TypeId` and
   routes it to `GrammarNode.Optional(inner)` checking rather than treating it as a
   generic two-case `Variant`.

4. **Error accumulation**: all violations are collected (not short-circuited) before
   reporting, so the user sees the full set of problems in one compilation pass.

5. **Error reporting**: each error includes:
   - The path to the violating node (`Order.items`, `Filter.tags.elem`, etc.)
   - What was found (`Sequence[Record[...]]`)
   - What was required at that position
   - A concrete suggested fix

6. **Success**: the macro emits `new Allows[S] {}` (a trivial anonymous subclass).
   The instance carries no runtime data — it is erased to a singleton allocation.

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
case class WithSeqRecord(orders: List[Order])     // negative for flat-only constraints
case class WithSeqSeq(matrix: List[List[Int]])    // negative: nested sequence
case class WithChunk(data: zio.blocks.chunk.Chunk[String])
case class WithSet(tags: Set[String])
```

### 4.4 Map fields

```scala
case class WithStringMap(meta: Map[String, Int])
case class WithStringMapRecord(meta: Map[String, Address]) // negative for Primitive-valued map
case class WithIntMap(counts: Map[Int, String])            // negative for Map[String, _] constraints
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

// Mutual recursion — compile-time error regardless of grammar
case class Forest(trees: List[Tree])
case class Tree(value: Int, children: Forest)
```

### 4.12 Option at top-level (not inside Record)

```scala
// Top-level Option — satisfies Allows[Optional[Primitive]] but not Allows[Record[_]]
val optSchema: Schema[Option[Int]] = Schema.derived
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
- Each of the 30 `PrimitiveType` cases satisfies `Allows[Primitive]` when used as a
  top-level `Schema[A]` (e.g. `Schema[Int]`, `Schema[UUID]`, etc.)

**Negative cases**:
- `Schema[List[Int]]` does NOT satisfy `Allows[Primitive]`
- `Schema[Option[Int]]` does NOT satisfy `Allows[Primitive]`
- `Schema[AllPrimitives]` (a Record) does NOT satisfy `Allows[Primitive]`
- `Schema[Shape]` (a Variant) does NOT satisfy `Allows[Primitive]`

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
- `Person` satisfies `Allows[Record[Primitive | Self]]` (Address is a Record[Primitive])

**Negative cases**:
- `Person` does NOT satisfy `Allows[Record[Primitive]]` (address field is Record)
- `WithSeqRecord` does NOT satisfy `Allows[Record[Primitive | Sequence[Primitive]]]`
  (sequence element is Record, not Primitive)
- `WithSeqSeq` does NOT satisfy `Allows[Record[Primitive | Sequence[Primitive]]]`
  (element of element is not Primitive)
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
  (even though no case is a bare Primitive — unused branches are fine)
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
- `Schema[List[Int]]` satisfies `Allows[Sequence[Primitive]]`
- `Schema[Vector[String]]` satisfies `Allows[Sequence[Primitive]]`
- `Schema[List[Address]]` satisfies `Allows[Sequence[Record[Primitive]]]`
- `Schema[List[List[Int]]]` satisfies `Allows[Sequence[Sequence[Primitive]]]`
- `Schema[Chunk[String]]` satisfies `Allows[Sequence[Primitive]]`
- `Schema[Set[Int]]` satisfies `Allows[Sequence[Primitive]]`

**Negative cases**:
- `Schema[List[Address]]` does NOT satisfy `Allows[Sequence[Primitive]]`
- `Schema[List[List[Int]]]` does NOT satisfy `Allows[Sequence[Primitive]]`

### 5.5 `AllowsMapSpec`

**Purpose**: `Allows[Map[K, V]]` at top-level and nested.

**Positive cases**:
- `Schema[Map[String, Int]]` satisfies `Allows[Map[Primitive, Primitive]]`
- `Schema[Map[String, Int]]` satisfies `Allows[Map[Primitive, Primitive]]`
- `Schema[Map[String, Address]]` satisfies `Allows[Map[Primitive, Record[Primitive]]]`
- `Schema[Map[Int, List[String]]]` satisfies
  `Allows[Map[Primitive, Sequence[Primitive]]]`

**Negative cases**:
- `Schema[Map[String, Address]]` does NOT satisfy `Allows[Map[Primitive, Primitive]]`
- `Schema[Map[String, Int]]` does NOT satisfy `Allows[Map[String, Primitive]]`
  (note: `String` in phantom position must be `Primitive`, not a literal type)
- `Schema[Map[List[Int], String]]` does NOT satisfy `Allows[Map[Primitive, Primitive]]`

### 5.6 `AllowsOptionalSpec`

**Purpose**: `Allows[Optional[...]]` at top-level and nested.

**Positive cases**:
- `Schema[Option[Int]]` satisfies `Allows[Optional[Primitive]]`
- `Schema[Option[Address]]` satisfies `Allows[Optional[Record[Primitive]]]`
- `Schema[Option[List[Int]]]` satisfies `Allows[Optional[Sequence[Primitive]]]`

**Negative cases**:
- `Schema[Option[Address]]` does NOT satisfy `Allows[Optional[Primitive]]`
- `Schema[Option[List[Int]]]` does NOT satisfy `Allows[Optional[Primitive]]`
- `Schema[Option[Option[Int]]]` does NOT satisfy `Allows[Optional[Primitive]]`

### 5.7 `AllowsWrappedSpec`

**Purpose**: `Allows[Wrapped[...]]` for opaque/newtype types.

**Positive cases**:
- `Schema[UserId]` satisfies `Allows[Wrapped[Primitive]]`
- `Schema[Amount]` satisfies `Allows[Wrapped[Primitive]]`
- `Schema[UserId]` satisfies `Allows[Primitive | Wrapped[Primitive]]`
- `Invoice` satisfies `Allows[Record[Wrapped[Primitive]]]`

**Negative cases**:
- `Schema[UserId]` does NOT satisfy `Allows[Primitive]`
  (it is `Wrapped[Primitive]`, not bare `Primitive`, unless auto-unwrapping is chosen)
- `Schema[UserId]` does NOT satisfy `Allows[Record[Primitive]]`

### 5.8 `AllowsDynamicSpec`

**Purpose**: `Allows[Dynamic]` and rejection of `Dynamic` where not permitted.

**Positive cases**:
- `Schema[DynamicValue]` satisfies `Allows[Dynamic]`
- `WithDynamic` satisfies `Allows[Record[Primitive | Dynamic]]`

**Negative cases**:
- `Schema[DynamicValue]` does NOT satisfy `Allows[Primitive]`
- `WithDynamic` does NOT satisfy `Allows[Record[Primitive]]`

### 5.9 `AllowsSelfSpec`

**Purpose**: `Self` in recursive grammars; mutual recursion error.

**Positive cases**:
- `TreeNode` satisfies `Allows[Record[Primitive | Sequence[Self]]]`
- `LinkedList` satisfies `Allows[Record[Primitive | Optional[Self]]]`
- `Category` satisfies
  `Allows[Record[Primitive | Sequence[Self] | Map[String, Self]]]`
- Non-recursive types satisfy `Self`-containing grammars vacuously:
  - `AllPrimitives` satisfies `Allows[Record[Primitive | Sequence[Self]]]`
    (there are no recursive fields, so `Self` is never reached)
  - `UserRow` satisfies `Allows[Record[Primitive | Optional[Self]]]`

**Negative cases**:
- `BadNode` does NOT satisfy `Allows[Record[Primitive | Sequence[Self]]]`
  (the `extra: DynamicValue` field violates `Primitive`)
- `Forest` + `Tree` (mutual recursion) produce a **compile-time error**:
  ```
  [error] Mutual recursion detected in schema for Forest:
  [error]   Forest → Tree → Forest
  [error]   Mutually recursive types are not supported by Allows
  ```

### 5.10 `AllowsUnionGrammarSpec`

**Purpose**: Union types (`A | B | C`) in grammar positions — at field level,
variant case level, and at top level.

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
  (even though `Record` branch of the union is unused, the actual seq element
  is a Record which does not match `Primitive`)

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
   after the first error. A type with 3 violating fields must produce 3 errors.

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

## 6. Open Implementation Notes

- The `Allows[S]` instance produced by the macro is **erased at runtime** — it is a
  single anonymous class allocation with no data fields. Libraries may choose to
  `inline` functions accepting `Allows` to eliminate even this allocation.

- The macro must handle `Reflect.Deferred` carefully: when it encounters a deferred
  node whose `TypeId` matches the root type, it is direct self-recursion and is
  accepted under `Self`. When it encounters a `TypeId` that is different from the
  root but has already been seen in the walk, it is mutual recursion and is an error.
  When it encounters a `TypeId` never seen before, it unfolds the deferred node and
  continues walking.

- `Option[A]` is represented in ZIO Schema as a two-case `Reflect.Variant`. The
  macro must special-case `Option` by its `TypeId` and route it through
  `GrammarNode.Optional` checking rather than generic `Variant` checking, to
  preserve the ergonomic `Optional[A]` grammar node for users.

- The `Allows` module lives in `zio.blocks.schema.comptime`, a new sbt module that
  depends on `schema`. It does not modify `schema` itself.

- Scala 2 is **not supported** for `schema-comptime`. The implementation requires
  Scala 3 inline macros (`scala.quoted`). The module is Scala 3-only.

- The `testJVM` and `testJS` command aliases in `build.sbt` must be updated to
  include `schema-comptimeJVM/test` and `schema-comptimeJS/test` respectively.
  Similarly for `docJVM` and `docJS`.
