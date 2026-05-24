---
id: dynamic-optic
title: "DynamicOptic"
---

`DynamicOptic` is a runtime path through nested data structures in ZIO Blocks. It is the untyped,
dynamically-constructed counterpart to the typed [`Optic[S, A]`](./optics.md). Where typed optics are bound to specific
Scala types at compile time, a `DynamicOptic` is a sequence of **navigation steps** that can be built, composed, and
applied at runtime:

```scala mdoc:compile-only
import zio.blocks.schema._

// Build a path: .users[0].name
val path = DynamicOptic.root.field("users").at(0).field("name")

// Navigate a DynamicValue
val data = DynamicValue.Record(
  "users" -> DynamicValue.Sequence(
    DynamicValue.Record("name" -> DynamicValue.string("Alice"))
  )
)

val result = data.get(path).one
// Right(DynamicValue.Primitive(PrimitiveValue.String("Alice")))
```

:::tip
For a practical example of extracting column names from `DynamicOptic` paths to generate SQL, see the [Query DSL Part 2: SQL Generation](../../guides/query-dsl-sql.md) guide.
:::

## Motivation

Most of the time you work with typed `Optic[S, A]` values — they are statically verified and provide type-safe
get/set/modify operations. `DynamicOptic` is useful when:

- **The path is determined at runtime** — user input, configuration, query parameters
- **You are working with `DynamicValue`** — navigating, modifying, or querying schema-less data
- **You need schema introspection** — walking a `Schema` or `Reflect` structure programmatically
- **Serializing optic paths** — `DynamicOptic` has its own `Schema[DynamicOptic]` and can be persisted

The relationship between typed and dynamic optics:

```
   Typed World                        Dynamic World
┌──────────────────┐             ┌──────────────────┐
│  Optic[S, A]     │─────────────│  DynamicOptic    │
│                  │  toDynamic  │                  │
│  Lens, Prism,    │             │  Sequence of     │
│  Optional,       │             │  Node steps      │
│  Traversal       │             │                  │
├──────────────────┤             ├──────────────────┤
│  Operates on     │             │  Operates on     │
│  typed values    │             │  DynamicValue,   │
│  (case classes,  │             │  Schema,         │
│  sealed traits)  │             │  Reflect         │
└──────────────────┘             └──────────────────┘
```

The `Optic[S, A]` and `DynamicOptic` types serve complementary roles in ZIO Blocks' optics system. `Optic[S, A]` provides compile-time type safety through macros or manual construction, operates on typed Scala values, and can be converted to a `DynamicOptic` via the `optic.toDynamic` method. In contrast, `DynamicOptic` performs runtime type checking and is constructed through a builder API or [path interpolator](../../path-interpolator.md), operating directly on [DynamicValue](./dynamic-value.md), [Schema](./schema.md), and [Reflect](./reflect.md) representations. 

## Design & Structure

`DynamicOptic` is modeled as a case class wrapping an `IndexedSeq[Node]`, where each `Node` represents one step in a navigation path:

```
DynamicOptic(nodes: IndexedSeq[Node])

sealed trait DynamicOptic.Node
 ├── Field(name: String)            — named field in a record
 ├── Case(name: String)             — specific case in a variant
 ├── AtIndex(index: Int)            — element at index in a sequence
 ├── AtIndices(index: Seq[Int])     — elements at multiple indices
 ├── AtMapKey(key: DynamicValue)    — value at a specific map key
 ├── AtMapKeys(keys: Seq[DynamicValue]) — values at multiple map keys
 ├── Elements                       — all elements in a sequence (wildcard)
 ├── MapKeys                        — all keys in a map (wildcard)
 ├── MapValues                      — all values in a map (wildcard)
 └── Wrapped                        — inner value of a wrapper/newtype
```

Each of the `DynamicOptic` nodes represents a different way to navigate through a data structure. Here's a reference table for the nodes, their navigation semantics, and string representations:

| Node                  | Navigates             | `toString`   | `toScalaString`     |
|-----------------------|-----------------------|--------------|---------------------|
| `Field("name")`       | Record field          | `.name`      | `.name`             |
| `Case("Email")`       | Variant case          | `<Email>`    | `.when[Email]`      |
| `AtIndex(0)`          | Sequence element      | `[0]`        | `.at(0)`            |
| `AtIndices(Seq(0,2))` | Multiple elements     | `[0,2]`      | `.atIndices(0, 2)`  |
| `AtMapKey(k)`         | Map entry by key      | `{"host"}`   | `.atKey("host")`    |
| `AtMapKeys(ks)`       | Multiple entries      | `{"a", "b"}` | `.atKeys("a", "b")` |
| `Elements`            | All sequence elements | `[*]`        | `.each`             |
| `MapKeys`             | All map keys          | `{*:}`       | `.eachKey`          |
| `MapValues`           | All map values        | `{*}`        | `.eachValue`        |
| `Wrapped`             | Wrapper inner value   | `.~`         | `.wrapped`          |

Key design decisions:

- **Map keys as `DynamicValue`** — Map keys can be any type (`String`, `Int`, `Boolean`, etc.), so `AtMapKey` stores the key as a `DynamicValue` to remain type-agnostic.
- **Dual rendering** — `toString` produces a compact interpolator syntax (`.field[0]{key}`), while `toScalaString` produces Scala method-call syntax (`.field.at(0).atKey(key)`). The compact format is designed to be copy-pasteable into the `p"..."` string interpolator, while the Scala format is used in error messages.
- **Every typed `Optic` has a `toDynamic` method** — This bridges the typed and untyped worlds, allowing any `Lens`, `Prism`, `Optional`, or `Traversal` to produce its `DynamicOptic` representation.

## Construction

### Starting Points

Every `DynamicOptic` starts from `root` (the identity/empty path) or one of the pre-built singletons:

```scala mdoc:compile-only
import zio.blocks.schema.DynamicOptic

val root = DynamicOptic.root // empty path: "."
val elems = DynamicOptic.elements // "[*]"
val keys = DynamicOptic.mapKeys // "{*:}"
val values = DynamicOptic.mapValues // "{*}"
val inner = DynamicOptic.wrapped // ".~"
```

### Builder Methods

Chain builder methods on `DynamicOptic.root` (or any existing optic) to construct paths fluently:

```scala mdoc:compile-only
import zio.blocks.schema._

// Navigate into a record field, then a sequence index, then another field
val path = DynamicOptic.root.field("users").at(0).field("name")
// toString: .users[0].name

// Navigate into a map with a typed key
val configPath = DynamicOptic.root.field("config").atKey("host")
// toString: .config{"host"}

// Navigate into a variant case, then a field
val resultPath = DynamicOptic.root.field("result").caseOf("Success").field("value")
// toString: .result<Success>.value

// Select all elements, then a field on each
val allEmails = DynamicOptic.root.field("users").elements.field("email")
// toString: .users[*].email
```

| Method           | Node Produced        | Example               |
|------------------|----------------------|-----------------------|
| `.field(name)`   | `Field(name)`        | `.field("street")`    |
| `.caseOf(name)`  | `Case(name)`         | `.caseOf("Email")`    |
| `.at(index)`     | `AtIndex(index)`     | `.at(0)`              |
| `.atIndices(i*)` | `AtIndices(indices)` | `.atIndices(0, 2, 5)` |
| `.atKey[K](key)` | `AtMapKey(dv)`       | `.atKey("host")`      |
| `.atKeys[K](k*)` | `AtMapKeys(dvs)`     | `.atKeys("a", "b")`   |
| `.elements`      | `Elements`           | `.elements`           |
| `.mapKeys`       | `MapKeys`            | `.mapKeys`            |
| `.mapValues`     | `MapValues`          | `.mapValues`          |
| `.wrapped`       | `Wrapped`            | `.wrapped`            |

Note: `.atKey` and `.atKeys` require an implicit `Schema[K]` to convert the typed key to a `DynamicValue`.

### Path Interpolator

The [`p"..."` path interpolator](../../path-interpolator.md) provides a concise compile-time syntax for building
`DynamicOptic` values:

```scala mdoc:compile-only
import zio.blocks.schema._

// Equivalent builder vs interpolator
val builderPath     : DynamicOptic = DynamicOptic.root.field("users").at(0).field("name")
val interpolatorPath: DynamicOptic = p".users[0].name"
// Both produce the same DynamicOptic

// Wildcards
val allEmails = p".users[*].email"

// Map access
val host = p""".config{"host"}"""

// Variant cases
val success = p".result<Success>.value"

// Complex path
val complex = p""".groups[*].members[0].contacts{"email"}"""
```

See [Path Interpolator](../../path-interpolator.md) for the full syntax reference.

### From Typed Optics

Every typed `Optic[S, A]` can be converted to a `DynamicOptic` via `toDynamic`:

```scala mdoc:compile-only
import zio.blocks.schema._

case class Address(street: String, city: String)

object Address {
  implicit val schema: Schema[Address] = Schema.derived
}

case class Person(name: String, address: Address)

object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person] = Schema.derived
  val name: Lens[Person, String] = optic(_.name)
  val street: Lens[Person, String] = optic(_.address.street)
}

val dynamicName: DynamicOptic = Person.name.toDynamic
// toString: .name

val dynamicStreet: DynamicOptic = Person.street.toDynamic
// toString: .address.street
```

See [Optics](./optics.md) for more on typed optics.

## Operations

### Composition

`DynamicOptic` values compose via the `apply` method, which concatenates their node sequences:

```scala mdoc:compile-only
import zio.blocks.schema._

val users = DynamicOptic.root.field("users")
val first = DynamicOptic.root.at(0)
val name = DynamicOptic.root.field("name")

// Compose three paths into one
val fullPath = users(first)(name)
// toString: .users[0].name

// Compose with pre-built singletons
val allUserNames = users(DynamicOptic.elements)(name)
// toString: .users[*].name

// Compose with interpolator-built paths
val emails = users(p"[*].email")
// toString: .users[*].email
```

### DynamicValue Operations

`DynamicOptic` is the path argument for all `DynamicValue` operations: `get(path)` for retrieval (with `.one` or `.toChunk`), `modify(path)(f)` for transformation, `set(path, value)` for replacement, `delete(path)` for removal, and `insert(path, value)` for addition. By default, the mutating operations (`modify`, `set`, `delete`, `insert`) are lenient and return the original value unchanged if the path doesn't resolve, whereas `get(path)` yields a failing `DynamicValueSelection` on a missing path (though calling `.toChunk` on it will produce an empty chunk). Each operation also has a strict `*OrFail` variant returning `Either[SchemaError, DynamicValue]` with error details on failure.

For example, the `DynamicValue#get` method uses `DynamicOptic` to navigate and extract values:

```scala mdoc:compile-only
import zio.blocks.schema._

val data = DynamicValue.Record(
  "users" -> DynamicValue.Sequence(
    DynamicValue.Record("name" -> DynamicValue.string("Alice"), "age" -> DynamicValue.int(30)),
    DynamicValue.Record("name" -> DynamicValue.string("Bob"), "age" -> DynamicValue.int(25))
  )
)

// Get a single value
val firstName = data.get(p".users[0].name").one
// Right(DynamicValue.Primitive(PrimitiveValue.String("Alice")))

// Get all matching values (wildcard)
val allNames = data.get(p".users[*].name").toChunk
// Chunk(DynamicValue.string("Alice"), DynamicValue.string("Bob"))
```

### Schema & Reflect Operations

`DynamicOptic` can navigate schema structures, not just values. This is useful for schema introspection and
metaprogramming.

1. The `Schema#get` method takes a `DynamicOptic` path and returns the `Reflect` for the nested component at that path, if it exists. This allows you to programmatically explore the structure of a schema:

```scala mdoc:compile-only
import zio.blocks.schema._

case class Address(street: String, city: String)

object Address {
  implicit val schema: Schema[Address] = Schema.derived
}

case class Person(name: String, address: Address)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Get the Reflect for the "street" field inside Person
val streetReflect: Option[Reflect.Bound[?]] =
  Schema[Person].get(p".address.street")
```

2. The `DynamicSchema#get` method works similarly, allowing you to navigate a `DynamicSchema` structure:

```scala mdoc:compile-only
import zio.blocks.schema._

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val dynSchema = Schema[Person].toDynamicSchema

val nameReflect: Option[Reflect.Unbound[_]] =
  dynSchema.get(p".name")
```

3. By applying a `DynamicOptic` directly to a `Reflect` value, you can navigate the reflected structure of a type:

```scala mdoc:compile-only
import zio.blocks.schema._

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val reflect = Schema[Person].reflect
val path = p".name"

// Using the DynamicOptic's apply method
val result: Option[Reflect[?, ?]] = path(reflect)
```

## Failure Path in OpticCheck

Typed optics use `DynamicOptic` internally for error reporting. When a typed optic operation fails (e.g., a `Prism` encounters the wrong case), the error includes the `DynamicOptic` path to pinpoint exactly where the failure occurred.

```
OpticCheck(errors: ::[Single])
 └── Single (sealed trait)
      ├── Error (sealed trait)
      │    ├── UnexpectedCase    — prism matched wrong variant case
      │    └── WrappingError     — wrapper conversion failed
      └── Warning (sealed trait)
           ├── EmptySequence     — traversal over empty sequence
           ├── SequenceIndexOutOfBounds — index beyond sequence length
           ├── MissingKey        — map key not found
           └── EmptyMap          — traversal over empty map
```

Every `OpticCheck.Single` carries two `DynamicOptic` paths:

- **`full`** — The complete optic path that was being evaluated
- **`prefix`** — The path up to the point where the error occurred

Error messages use `toScalaString` for human-readable output:

```
During attempted access at .when[Email].subject,
encountered an unexpected case at .when[Email]:
expected Email, but got Push
```

## Path String Syntax

`DynamicOptic` provides two string formats for different contexts:

- **`toString`** — Compact path syntax matching the [`p"..."` interpolator](../../path-interpolator.md) format
- **`toScalaString`** — Scala method call syntax used in error messages

| Node                      | `toString`   | `toScalaString`     |
|---------------------------|--------------|---------------------|
| `Field("name")`           | `.name`      | `.name`             |
| `Case("Email")`           | `<Email>`    | `.when[Email]`      |
| `AtIndex(0)`              | `[0]`        | `.at(0)`            |
| `AtIndices(Seq(0, 2))`    | `[0,2]`      | `.atIndices(0, 2)`  |
| `AtMapKey(string "host")` | `{"host"}`   | `.atKey("host")`    |
| `AtMapKey(int 42)`        | `{42}`       | `.atKey(42)`        |
| `AtMapKey(bool true)`     | `{true}`     | `.atKey(true)`      |
| `AtMapKey(char 'a')`      | `{'a'}`      | `.atKey('a')`       |
| `AtMapKeys(strings)`      | `{"a", "b"}` | `.atKeys("a", "b")` |
| `Elements`                | `[*]`        | `.each`             |
| `MapKeys`                 | `{*:}`       | `.eachKey`          |
| `MapValues`               | `{*}`        | `.eachValue`        |
| `Wrapped`                 | `.~`         | `.wrapped`          |
| root                      | `.`          | `.`                 |

## Serialization

`DynamicOptic` has an implicit `Schema[DynamicOptic]` defined in its companion object, which means it can be serialized and deserialized just like any other schema-equipped type. This enables storing optic paths in databases, sending them over the wire, or including them in configuration files:

```scala mdoc:compile-only
import zio.blocks.schema._

// Schema[DynamicOptic] is available implicitly
val opticSchema: Schema[DynamicOptic] = Schema[DynamicOptic]

// Convert to/from DynamicValue for serialization
val path = p".users[0].name"
val serialized: DynamicValue = opticSchema.toDynamicValue(path)
```


## Search Optics

A **Search optic** recursively traverses a data structure to find **all occurrences** matching a type or schema. It produces a `Traversal[S, A]` that collects matches in depth-first, left-to-right order.

### Motivation

Search optics address scenarios where you need to:

- **Find all values of a specific type** across deeply nested structures (e.g., all `String` fields in a nested record)
- **Extract all data matching a structural pattern** (e.g., all records with `{ name: string, age: int }` schema)
- **Transform all matching occurrences** in untyped or partially-typed data
- **Query data without knowing the exact path** — the search discovers all paths automatically

### Typed API: `.searchFor[T]`

Use the `.searchFor[T]` extension method on a `CompanionOptics` to search for all values of type `T`:

```scala mdoc:compile-only
import zio.blocks.schema._

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

case class Address(city: String)
object Address {
  implicit val schema: Schema[Address] = Schema.derived
}

case class Company(name: String, employees: List[Person], hq: Address)
object Company extends CompanionOptics[Company] {
  implicit val schema: Schema[Company] = Schema.derived
}

// Find all Person instances within a Company
val personSearch: Traversal[Company, Person] = Company.optic(_.searchFor[Person])

val company = Company(
  "Acme",
  List(Person("Alice", 30), Person("Bob", 25)),
  Address("NYC")
)

// Modify all persons (increment their age)
val updated: Company = personSearch.modify(company, p => p.copy(age = p.age + 1))
// Company("Acme", List(Person("Alice", 31), Person("Bob", 26)), Address("NYC"))
```

### Dynamic API: Path Strings with `#` Prefix

Use the `#` prefix in path strings to specify type or schema patterns:

```scala mdoc:compile-only
import zio.blocks.schema._

val data = DynamicValue.Record(
  "company" -> DynamicValue.Record(
    "name" -> DynamicValue.string("Acme"),
    "employees" -> DynamicValue.Sequence(
      DynamicValue.Record("name" -> DynamicValue.string("Alice")),
      DynamicValue.Record("name" -> DynamicValue.string("Bob"))
    )
  )
)

// Find all strings in the data
val allStrings = data.get(p"#string").toChunk
// Chunk(
//   DynamicValue.string("Acme"),
//   DynamicValue.string("Alice"),
//   DynamicValue.string("Bob")
// )

// Find all records matching a schema
val recordsWithName = data.get(p"#record { name: string }").toChunk

// Modify all matching values
val modified = data.modify(p"#string")(dv =>
  dv match {
    case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
      DynamicValue.string(s.toUpperCase)
    case other => other
  }
)
```

### Supported Patterns

Search optics support both **type-based** and **schema-based** patterns:

| Pattern                        | Matches                                | Example                    |
|--------------------------------|----------------------------------------|---------------------------|
| `#Nominal` (type name)         | Values of that type name               | `#Person`                  |
| `#int`, `#string`, `#boolean`  | Specific primitive types (case-insensitive) | `#int`, `#string`, `#boolean` |
| `#record { ... }`              | Records with matching fields           | `#record { name: string }` |
| `#variant { ... }`             | Variant cases with matching content    | `#variant { Error: string }` |
| `#list(...)`                   | Lists/sequences with element type     | `#list(string)`            |
| `#map(...)`                    | Maps with key and value types          | `#map(string, int)`        |
| `#option(...)`                 | Optional values with inner type       | `#option(int)`             |

### Traversal Order

Results are collected in **depth-first, left-to-right order**:

```scala mdoc:compile-only
import zio.blocks.schema._

val nested = DynamicValue.Record(
  "a" -> DynamicValue.int(1),
  "b" -> DynamicValue.Record(
    "c" -> DynamicValue.int(2),
    "d" -> DynamicValue.int(3)
  ),
  "e" -> DynamicValue.int(4)
)

// Depth-first, left-to-right: 1, 2, 3, 4
val allInts = nested.get(p"#int").toChunk
// Chunk(1, 2, 3, 4)
```

### Known Limitation: Nominal Matching in Untyped Contexts

`Nominal` pattern matching (e.g., `#Person`) returns `false` when applied to `DynamicValue` or `Json` because these untyped structures carry no type identity. To match nominally-typed data, use one of these approaches:

- **Use the typed API**: `optic(_.searchFor[Person])`
- **Or use structural patterns**: `p"#record { name: string, age: int }"`

Here's how the two approaches differ in practice:

```scala mdoc:compile-only
import zio.blocks.schema._

val dynamicData: DynamicValue = ???

// This won't match — no type identity available
val nominalMatch = dynamicData.get(p"#Person").toChunk
// Chunk()

// This works — structural matching
val structuralMatch = dynamicData.get(p"#record { name: string, age: int }").toChunk
// Chunk(...matching records...)
```

## See Also

- [DynamicSchema](./dynamic-schema.md) — use `DynamicSchema#get` with a `DynamicOptic` to navigate schema trees and inspect nested type structures.
- [Optics](./optics.md) — reference page for typed `Optic[S, A]` and reflective optics concepts.
