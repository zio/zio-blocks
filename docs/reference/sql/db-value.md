---
id: db-value
title: "DbValue"
description: "Reference for DbValue, the sealed ADT of typed SQL column values shared by Frag, DbCodec, and SqlDialect in the sql module."
keywords:
  - "DbValue sealed ADT"
  - "SQL column value types"
  - "DbNull DbInt DbString"
  - "typed database parameters"
  - "SqlDialect typeName"
  - "DbCodec toDbValues"
  - "Frag params representation"
---

`DbValue` is a sealed algebraic data type that represents every SQL column value the `sql` module handles as a typed Scala value. One `case object` — `DbNull` — represents SQL `NULL`; eighteen `final case class` subtypes each wrap a single Scala value corresponding to a SQL type: integers, decimals, booleans, strings, bytes, temporal types, UUIDs, and arrays.

Key properties:
- **Sealed ADT** — all variants are known at compile time; pattern matching on `DbValue` is exhaustive and the compiler warns on missing cases.
- **Exhaustive SQL type coverage** — every common JDBC column type has a dedicated variant, eliminating the need for `Any`-typed containers or integer-keyed type switches.
- **Type-safe** — each variant carries a Scala value of the correct type, not an `Object` or untyped `Any`.
- **Uniform intermediate representation** — `Frag` stores its parameters as `IndexedSeq[DbValue]`, `DbCodec#toDbValues` produces `IndexedSeq[DbValue]`, and `SqlDialect#typeName` maps a `DbValue` to its DDL type string; all module participants use the same currency.

The structural shape of `DbValue` is:

```scala
sealed trait DbValue

object DbValue {
  case object DbNull                                                        extends DbValue
  final case class DbInt(value: Int)                                        extends DbValue
  final case class DbLong(value: Long)                                      extends DbValue
  final case class DbDouble(value: Double)                                  extends DbValue
  final case class DbFloat(value: Float)                                    extends DbValue
  final case class DbBoolean(value: Boolean)                                extends DbValue
  final case class DbString(value: String)                                  extends DbValue
  final case class DbBigDecimal(value: scala.BigDecimal)                    extends DbValue
  final case class DbBytes(value: Array[Byte])                              extends DbValue
  final case class DbShort(value: Short)                                    extends DbValue
  final case class DbByte(value: Byte)                                      extends DbValue
  final case class DbChar(value: Char)                                      extends DbValue
  final case class DbLocalDate(value: java.time.LocalDate)                  extends DbValue
  final case class DbLocalDateTime(value: java.time.LocalDateTime)          extends DbValue
  final case class DbLocalTime(value: java.time.LocalTime)                  extends DbValue
  final case class DbInstant(value: java.time.Instant)                      extends DbValue
  final case class DbDuration(value: java.time.Duration)                    extends DbValue
  final case class DbUUID(value: java.util.UUID)                            extends DbValue
  final case class DbArray(elementType: String, elements: IndexedSeq[Any]) extends DbValue
}
```

## Quick Showcase

The following shows the core uses of `DbValue`: constructing several typed variants, reading the wrapped scalar via the `value` field, and exhaustive pattern matching to dispatch on the SQL type:

```scala
import zio.blocks.sql.DbValue
import java.time.{Instant, LocalDate}
import java.util.UUID

// Construct typed database values
val nullVal  = DbValue.DbNull
val intVal   = DbValue.DbInt(42)
val longVal  = DbValue.DbLong(9999999999L)
val strVal   = DbValue.DbString("Alice")
val boolVal  = DbValue.DbBoolean(true)
val dateVal  = DbValue.DbLocalDate(LocalDate.of(2024, 3, 14))
val uuidVal  = DbValue.DbUUID(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
val arrayVal = DbValue.DbArray("TEXT", IndexedSeq("a", "b", "c"))

// Access the wrapped scalar directly via the `value` field
val n: Int       = intVal.value   // 42
val s: String    = strVal.value   // "Alice"
val d: LocalDate = dateVal.value  // 2024-03-14

// Pattern-match to dispatch on the runtime SQL type
def label(v: DbValue): String = v match {
  case DbValue.DbNull             => "NULL"
  case DbValue.DbInt(n)           => s"INTEGER($n)"
  case DbValue.DbLong(n)          => s"BIGINT($n)"
  case DbValue.DbString(s)        => s"TEXT($s)"
  case DbValue.DbBoolean(b)       => s"BOOLEAN($b)"
  case DbValue.DbLocalDate(d)     => s"DATE($d)"
  case DbValue.DbUUID(id)         => s"UUID($id)"
  case DbValue.DbArray(t, elems)  => s"ARRAY[$t](${elems.mkString(",")})"
  case other                      => other.getClass.getSimpleName
}

label(intVal)  // "INTEGER(42)"
label(strVal)  // "TEXT(Alice)"
label(nullVal) // "NULL"
```

## Construction / Creating Instances

`DbValue` instances are constructed by referencing `DbValue.DbNull` directly or by calling the primary constructor of one of the eighteen `final case class` subtypes. Because `DbValue` is sealed, the Scala compiler verifies that every pattern match covers all variants, making the addition of new subtypes a compile-time-visible change across the entire codebase.

### `DbValue.DbNull` — Represent a SQL NULL value

`DbValue.DbNull` is the sole `case object` in the `DbValue` hierarchy. It represents a SQL `NULL` column value — the absence of any typed value. It is the value that `DbParam[Option[A]]` produces for `None` and that `DbParam[Maybe[A]]` produces for `Maybe.absent`. When `DbCodec` reads a nullable column that contains `NULL`, it matches on `DbNull` to produce `None` or `Maybe.absent` in the decoded result.

The declaration is:

```scala
case object DbNull extends DbValue
```

Reference the singleton object directly — no construction is needed:

```scala
import zio.blocks.sql.DbValue

val nullVal: DbValue = DbValue.DbNull
// nullVal == DbValue.DbNull  →  true
```

### `DbValue.DbInt` — Represent a 32-bit integer column value

`DbValue.DbInt` wraps a Scala `Int` for storage or retrieval from a SQL `INTEGER` column. The `sql"..."` interpolator produces it automatically when a Scala `Int` is bound as a parameter via `DbParam[Int]`.

The declaration is:

```scala
final case class DbInt(value: Int) extends DbValue
```

Construct a `DbInt` by passing the integer value to its primary constructor:

```scala
import zio.blocks.sql.DbValue

val v = DbValue.DbInt(42)
v.value // 42
```

### `DbValue.DbLong` — Represent a 64-bit integer column value

`DbValue.DbLong` wraps a Scala `Long` for storage or retrieval from a SQL `BIGINT` column (PostgreSQL) or `INTEGER` column (SQLite, which uses 64-bit integers natively).

The declaration is:

```scala
final case class DbLong(value: Long) extends DbValue
```

Construct a `DbLong` by passing the long value to its primary constructor:

```scala
import zio.blocks.sql.DbValue

val v = DbValue.DbLong(9999999999L)
v.value // 9999999999
```

### `DbValue.DbDouble` — Represent a 64-bit floating-point column value

`DbValue.DbDouble` wraps a Scala `Double` for storage or retrieval from a SQL `DOUBLE PRECISION` column (PostgreSQL) or `REAL` column (SQLite).

The declaration is:

```scala
final case class DbDouble(value: Double) extends DbValue
```

Construct a `DbDouble` by passing the double value to its primary constructor:

```scala
import zio.blocks.sql.DbValue

val v = DbValue.DbDouble(3.14159)
v.value // 3.14159
```

### `DbValue.DbFloat` — Represent a 32-bit floating-point column value

`DbValue.DbFloat` wraps a Scala `Float` for storage or retrieval from a SQL `REAL` column (both PostgreSQL and SQLite).

The declaration is:

```scala
final case class DbFloat(value: Float) extends DbValue
```

Construct a `DbFloat` by passing the float value to its primary constructor:

```scala
import zio.blocks.sql.DbValue

val v = DbValue.DbFloat(2.71f)
v.value // 2.71
```

### `DbValue.DbBoolean` — Represent a boolean column value

`DbValue.DbBoolean` wraps a Scala `Boolean` for storage or retrieval from a SQL `BOOLEAN` column (PostgreSQL) or `INTEGER` column (SQLite, which stores `true` as `1` and `false` as `0`).

The declaration is:

```scala
final case class DbBoolean(value: Boolean) extends DbValue
```

Construct a `DbBoolean` by passing the boolean value to its primary constructor:

```scala
import zio.blocks.sql.DbValue

val v = DbValue.DbBoolean(true)
v.value // true
```

### `DbValue.DbString` — Represent a text column value

`DbValue.DbString` wraps a Scala `String` for storage or retrieval from a SQL `TEXT` column. It is also the carrier type that `DbCodec.jsonb` uses for JSON-encoded complex values stored in a single `TEXT` or `JSONB` column.

The declaration is:

```scala
final case class DbString(value: String) extends DbValue
```

Construct a `DbString` by passing the string value to its primary constructor:

```scala
import zio.blocks.sql.DbValue

val v = DbValue.DbString("hello, world")
v.value // "hello, world"
```

### `DbValue.DbBigDecimal` — Represent a high-precision decimal column value

`DbValue.DbBigDecimal` wraps a Scala `BigDecimal` for storage or retrieval from a SQL `NUMERIC` column (PostgreSQL) or `TEXT` column (SQLite, which encodes decimals as text strings to preserve precision).

The declaration is:

```scala
final case class DbBigDecimal(value: scala.BigDecimal) extends DbValue
```

Construct a `DbBigDecimal` by passing the decimal value to its primary constructor:

```scala
import zio.blocks.sql.DbValue

val v = DbValue.DbBigDecimal(BigDecimal("123.456789"))
v.value // 123.456789
```

### `DbValue.DbBytes` — Represent a binary column value

`DbValue.DbBytes` wraps an `Array[Byte]` for storage or retrieval from a SQL `BYTEA` column (PostgreSQL) or `BLOB` column (SQLite).

The declaration is:

```scala
final case class DbBytes(value: Array[Byte]) extends DbValue
```

Construct a `DbBytes` by passing the byte array to its primary constructor:

```scala
import zio.blocks.sql.DbValue

val v = DbValue.DbBytes(Array[Byte](0x01, 0x02, 0x03))
v.value.length // 3
```

:::caution
`Array[Byte]` uses reference equality by default. Two `DbBytes` instances wrapping arrays with identical contents are not `==` to each other unless they share the same array reference. Compare byte content with `v.value.sameElements(other.value)`.
:::

### `DbValue.DbShort` — Represent a 16-bit integer column value

`DbValue.DbShort` wraps a Scala `Short` for storage or retrieval from a SQL `SMALLINT` column (PostgreSQL) or `INTEGER` column (SQLite).

The declaration is:

```scala
final case class DbShort(value: Short) extends DbValue
```

Construct a `DbShort` by passing the short value to its primary constructor:

```scala
import zio.blocks.sql.DbValue

val v = DbValue.DbShort(100.toShort)
v.value // 100
```

### `DbValue.DbByte` — Represent an 8-bit integer column value

`DbValue.DbByte` wraps a Scala `Byte` for storage or retrieval from a SQL `SMALLINT` column (both PostgreSQL and SQLite map it to `SMALLINT`, since no direct `TINYINT` equivalent exists in ANSI SQL).

The declaration is:

```scala
final case class DbByte(value: Byte) extends DbValue
```

Construct a `DbByte` by passing the byte value to its primary constructor:

```scala
import zio.blocks.sql.DbValue

val v = DbValue.DbByte(50.toByte)
v.value // 50
```

### `DbValue.DbChar` — Represent a single-character column value

`DbValue.DbChar` wraps a Scala `Char` for storage or retrieval from a SQL `CHAR(1)` column (PostgreSQL) or `TEXT` column (SQLite).

The declaration is:

```scala
final case class DbChar(value: Char) extends DbValue
```

Construct a `DbChar` by passing the character value to its primary constructor:

```scala
import zio.blocks.sql.DbValue

val v = DbValue.DbChar('A')
v.value // 'A'
```

### `DbValue.DbLocalDate` — Represent a calendar date without time

`DbValue.DbLocalDate` wraps a `java.time.LocalDate` for storage or retrieval from a SQL `DATE` column (PostgreSQL) or `TEXT` column (SQLite, ISO-8601 format).

The declaration is:

```scala
final case class DbLocalDate(value: java.time.LocalDate) extends DbValue
```

Construct a `DbLocalDate` by passing a `LocalDate` instance to its primary constructor:

```scala
import zio.blocks.sql.DbValue
import java.time.LocalDate

val v = DbValue.DbLocalDate(LocalDate.of(2024, 3, 14))
v.value // 2024-03-14
```

### `DbValue.DbLocalDateTime` — Represent a date-time without timezone

`DbValue.DbLocalDateTime` wraps a `java.time.LocalDateTime` for storage or retrieval from a SQL `TIMESTAMP` column (PostgreSQL) or `TEXT` column (SQLite, ISO-8601 format). It carries no timezone offset; use `DbInstant` for UTC-anchored timestamps that must preserve timezone information across reads.

The declaration is:

```scala
final case class DbLocalDateTime(value: java.time.LocalDateTime) extends DbValue
```

Construct a `DbLocalDateTime` by passing a `LocalDateTime` instance to its primary constructor:

```scala
import zio.blocks.sql.DbValue
import java.time.LocalDateTime

val v = DbValue.DbLocalDateTime(LocalDateTime.of(2024, 3, 14, 12, 30, 0))
v.value // 2024-03-14T12:30
```

### `DbValue.DbLocalTime` — Represent a time of day without date

`DbValue.DbLocalTime` wraps a `java.time.LocalTime` for storage or retrieval from a SQL `TIME` column (PostgreSQL) or `TEXT` column (SQLite, ISO-8601 format).

The declaration is:

```scala
final case class DbLocalTime(value: java.time.LocalTime) extends DbValue
```

Construct a `DbLocalTime` by passing a `LocalTime` instance to its primary constructor:

```scala
import zio.blocks.sql.DbValue
import java.time.LocalTime

val v = DbValue.DbLocalTime(LocalTime.of(14, 30, 45))
v.value // 14:30:45
```

### `DbValue.DbInstant` — Represent a UTC instant

`DbValue.DbInstant` wraps a `java.time.Instant` for storage or retrieval from a SQL `TIMESTAMPTZ` column (PostgreSQL) or `TEXT` column (SQLite, ISO-8601 format). Use this variant for wall-clock timestamps that must preserve a precise point in time regardless of the server's local timezone.

The declaration is:

```scala
final case class DbInstant(value: java.time.Instant) extends DbValue
```

Construct a `DbInstant` by passing an `Instant` instance to its primary constructor:

```scala
import zio.blocks.sql.DbValue
import java.time.Instant

val v = DbValue.DbInstant(Instant.parse("2024-03-14T12:30:00Z"))
v.value // 2024-03-14T12:30:00Z
```

### `DbValue.DbDuration` — Represent a duration or interval

`DbValue.DbDuration` wraps a `java.time.Duration` for storage or retrieval from a SQL `INTERVAL` column (PostgreSQL) or `TEXT` column (SQLite, ISO-8601 duration format).

The declaration is:

```scala
final case class DbDuration(value: java.time.Duration) extends DbValue
```

Construct a `DbDuration` by passing a `Duration` instance to its primary constructor:

```scala
import zio.blocks.sql.DbValue
import java.time.Duration

val v = DbValue.DbDuration(Duration.ofHours(2))
v.value // PT2H
```

### `DbValue.DbUUID` — Represent a universally unique identifier

`DbValue.DbUUID` wraps a `java.util.UUID` for storage or retrieval from a SQL `UUID` column (PostgreSQL, stored as a native 128-bit value) or `TEXT` column (SQLite, stored in standard hyphenated string form).

The declaration is:

```scala
final case class DbUUID(value: java.util.UUID) extends DbValue
```

Construct a `DbUUID` by passing a `UUID` instance to its primary constructor:

```scala
import zio.blocks.sql.DbValue
import java.util.UUID

val v = DbValue.DbUUID(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
v.value // 550e8400-e29b-41d4-a716-446655440000
```

### `DbValue.DbArray` — Represent a typed array or collection

`DbValue.DbArray` differs from every other subtype: instead of a single typed `value` field, it carries two fields — `elementType: String` naming the SQL element type (e.g. `"TEXT"`, `"INTEGER"`) and `elements: IndexedSeq[Any]` holding the array contents. It represents SQL `ARRAY` columns and collection-typed values that `DbCodecDeriver` serializes as database arrays.

The declaration is:

```scala
final case class DbArray(elementType: String, elements: IndexedSeq[Any]) extends DbValue
```

Construct a `DbArray` by providing the SQL element type name and the element sequence:

```scala
import zio.blocks.sql.DbValue

val v = DbValue.DbArray("TEXT", IndexedSeq("alpha", "beta", "gamma"))
v.elementType // "TEXT"
v.elements    // IndexedSeq("alpha", "beta", "gamma")
```

:::note
`DbArray` uses `IndexedSeq[Any]` for its elements because the JDBC API returns array elements as `Object[]`, making a statically typed `IndexedSeq` impractical at the driver boundary. The `elementType` string identifies the expected SQL element type so that `DbParamWriter` can create a correctly typed JDBC array during binding.
:::

## Core Operations

`DbValue` defines no methods of its own beyond the standard case-class machinery. All operations on a `DbValue` are extraction operations: exhaustive pattern matching to dispatch on the SQL type, `unapply`-based destructuring to bind the inner value in a `case` arm, and direct `.value` field access when the concrete subtype is already known.

### Extraction

The extraction category covers the three ways to retrieve information from a `DbValue`: a full pattern match across all variants, `unapply` destructuring inside individual `case` patterns, and direct `.value` field access on a narrowed subtype. These three forms compose freely — a single `case` arm can both test the variant and bind the value in one expression.

#### Pattern matching — Dispatch on the SQL type

A `match`/`case` block over a `DbValue` is the primary way to branch on the runtime SQL type. Because `DbValue` is sealed, the Scala compiler checks that every case is handled and issues an exhaustiveness warning when a variant is missing. A wildcard arm (`case _`) suppresses the check but sacrifices the safety guarantee.

The sealed trait itself carries no dispatch method; the match expression is the intended dispatch mechanism:

```scala
sealed trait DbValue
// no methods — pattern matching is the intended dispatch form
```

The following function reproduces the same type-name mapping that `SqlDialect#typeName` uses internally, covering every variant of `DbValue`:

```scala
import zio.blocks.sql.DbValue

def sqlTypeName(v: DbValue): String = v match {
  case DbValue.DbNull              => "NULL"
  case _: DbValue.DbInt            => "INTEGER"
  case _: DbValue.DbLong           => "BIGINT"
  case _: DbValue.DbDouble         => "DOUBLE PRECISION"
  case _: DbValue.DbFloat          => "REAL"
  case _: DbValue.DbBoolean        => "BOOLEAN"
  case _: DbValue.DbString         => "TEXT"
  case _: DbValue.DbBigDecimal     => "NUMERIC"
  case _: DbValue.DbBytes          => "BYTEA"
  case _: DbValue.DbShort          => "SMALLINT"
  case _: DbValue.DbByte           => "SMALLINT"
  case _: DbValue.DbChar           => "CHAR(1)"
  case _: DbValue.DbLocalDate      => "DATE"
  case _: DbValue.DbLocalDateTime  => "TIMESTAMP"
  case _: DbValue.DbLocalTime      => "TIME"
  case _: DbValue.DbInstant        => "TIMESTAMPTZ"
  case _: DbValue.DbDuration       => "INTERVAL"
  case _: DbValue.DbUUID           => "UUID"
  case DbValue.DbArray(t, _)       => s"$t ARRAY"
}
```

Notice that `DbNull` matches without an `_` prefix because it is a `case object` — no type projection is needed.

#### `unapply` — Destructure inside a `case` pattern

Every `final case class` subtype provides a compiler-generated `unapply` extractor that binds the wrapped value in a `case` pattern. The single-field variants bind that one value directly; `DbArray` binds two values (`elementType` and `elements`); `DbNull` requires no binding.

The extractor shapes, as generated by the compiler, are:

```scala
// Single-field extractors (one per subtype, shown for illustration):
object DbInt   { def unapply(v: DbValue.DbInt):    Some[Int]                        = Some(v.value) }
object DbString{ def unapply(v: DbValue.DbString):  Some[String]                    = Some(v.value) }
// Two-field extractor for DbArray:
object DbArray { def unapply(v: DbValue.DbArray): Some[(String, IndexedSeq[Any])]   = Some((v.elementType, v.elements)) }
```

Use extractors when you need both the variant check and the inner value in the same expression:

```scala
import zio.blocks.sql.DbValue

def doubleNumeric(v: DbValue): Option[DbValue] = v match {
  case DbValue.DbInt(n)    => Some(DbValue.DbInt(n * 2))
  case DbValue.DbLong(n)   => Some(DbValue.DbLong(n * 2L))
  case DbValue.DbDouble(d) => Some(DbValue.DbDouble(d * 2.0))
  case _                   => None
}

doubleNumeric(DbValue.DbInt(21))     // Some(DbValue.DbInt(42))
doubleNumeric(DbValue.DbString("x")) // None
```

The two-field extractor for `DbArray` binds both fields simultaneously in a single `case` arm:

```scala
import zio.blocks.sql.DbValue

val v: DbValue = DbValue.DbArray("INTEGER", IndexedSeq(1, 2, 3))
v match {
  case DbValue.DbArray(elemType, elems) =>
    s"$elemType array of ${elems.size} elements"
  case _ => "not an array"
}
// "INTEGER array of 3 elements"
```

#### `value` field access — Extract the wrapped scalar directly

All `DbValue` subtypes except `DbNull` and `DbArray` expose a `value` field of the exact Scala type they wrap. After narrowing to a concrete subtype — either through pattern matching or a typed binding — we can read the field directly with no additional extraction step.

The `value` field is defined on each case class's primary constructor:

```scala
final case class DbInt(value: Int)                   extends DbValue
final case class DbString(value: String)             extends DbValue
final case class DbInstant(value: java.time.Instant) extends DbValue
// ... and so on for every single-field subtype
```

The following example narrows a list of `DbValue` instances and extracts the integer values using direct field access in the collection extractor:

```scala
import zio.blocks.sql.DbValue

val params: List[DbValue] = List(
  DbValue.DbInt(1),
  DbValue.DbString("hello"),
  DbValue.DbBoolean(true),
  DbValue.DbInt(99)
)

val ints: List[Int] = params.collect {
  case DbValue.DbInt(n) => n
}
// List(1, 99)
```

When the type is already narrowed via a typed local binding, direct field access avoids a redundant match:

```scala
import zio.blocks.sql.DbValue

val raw: DbValue = DbValue.DbLong(999L)
raw match {
  case l: DbValue.DbLong => l.value * 2  // 1998L, no second match needed
  case _                 => 0L
}
```

## Subtypes / Variants

`DbValue` defines one `case object` and eighteen `final case class` subtypes. The full type hierarchy is:

```
DbValue (sealed trait)
├── DbNull                   — SQL NULL (case object, no field)
├── DbInt                    — Int            → INTEGER / INTEGER
├── DbLong                   — Long           → BIGINT  / INTEGER
├── DbDouble                 — Double         → DOUBLE PRECISION / REAL
├── DbFloat                  — Float          → REAL    / REAL
├── DbBoolean                — Boolean        → BOOLEAN / INTEGER
├── DbString                 — String         → TEXT    / TEXT
├── DbBigDecimal             — BigDecimal     → NUMERIC / TEXT
├── DbBytes                  — Array[Byte]    → BYTEA   / BLOB
├── DbShort                  — Short          → SMALLINT / INTEGER
├── DbByte                   — Byte           → SMALLINT / INTEGER
├── DbChar                   — Char           → CHAR(1) / TEXT
├── DbLocalDate              — LocalDate      → DATE      / TEXT
├── DbLocalDateTime          — LocalDateTime  → TIMESTAMP / TEXT
├── DbLocalTime              — LocalTime      → TIME      / TEXT
├── DbInstant                — Instant        → TIMESTAMPTZ / TEXT
├── DbDuration               — Duration       → INTERVAL  / TEXT
├── DbUUID                   — UUID           → UUID      / TEXT
└── DbArray                  — (elementType: String, elements: IndexedSeq[Any])
```

The table below maps each variant to its Scala field type and to the DDL type strings produced by `SqlDialect#typeName` for the two built-in dialects:

| Variant           | Scala field type              | PostgreSQL DDL type   | SQLite DDL type     |
|-------------------|-------------------------------|-----------------------|---------------------|
| `DbNull`          | *(none — case object)*        | `NULL`                | `NULL`              |
| `DbInt`           | `Int`                         | `INTEGER`             | `INTEGER`           |
| `DbLong`          | `Long`                        | `BIGINT`              | `INTEGER`           |
| `DbDouble`        | `Double`                      | `DOUBLE PRECISION`    | `REAL`              |
| `DbFloat`         | `Float`                       | `REAL`                | `REAL`              |
| `DbBoolean`       | `Boolean`                     | `BOOLEAN`             | `INTEGER`           |
| `DbString`        | `String`                      | `TEXT`                | `TEXT`              |
| `DbBigDecimal`    | `scala.BigDecimal`            | `NUMERIC`             | `TEXT`              |
| `DbBytes`         | `Array[Byte]`                 | `BYTEA`               | `BLOB`              |
| `DbShort`         | `Short`                       | `SMALLINT`            | `INTEGER`           |
| `DbByte`          | `Byte`                        | `SMALLINT`            | `INTEGER`           |
| `DbChar`          | `Char`                        | `CHAR(1)`             | `TEXT`              |
| `DbLocalDate`     | `java.time.LocalDate`         | `DATE`                | `TEXT`              |
| `DbLocalDateTime` | `java.time.LocalDateTime`     | `TIMESTAMP`           | `TEXT`              |
| `DbLocalTime`     | `java.time.LocalTime`         | `TIME`                | `TEXT`              |
| `DbInstant`       | `java.time.Instant`           | `TIMESTAMPTZ`         | `TEXT`              |
| `DbDuration`      | `java.time.Duration`          | `INTERVAL`            | `TEXT`              |
| `DbUUID`          | `java.util.UUID`              | `UUID`                | `TEXT`              |
| `DbArray`         | `(String, IndexedSeq[Any])`   | *(not in typeName)*   | *(not in typeName)* |

`DbNull` is the canonical null sentinel across the module: `DbParam[Option[A]]` produces `DbNull` for `None`, and `DbParam[Maybe[A]]` produces `DbNull` for `Maybe.absent`. When `DbCodec` reads a nullable column and `DbResultReader#wasNull` returns `true`, the codec matches on `DbNull` to produce `None` or `Maybe.absent` in the decoded record. Non-optional codecs that encounter `DbNull` throw `IllegalStateException` immediately, surfacing schema mismatches as loud errors rather than silent coercions.

`DbArray` is the only variant with two fields and no `value` accessor. It is produced by `DbCodecDeriver` for collection-typed fields and is not currently included in `SqlDialect#typeName`, so DDL generation for array columns requires custom handling if needed.

## Comparisons

### `DbValue` vs `java.sql.Types` constants

`java.sql.Types` is a class of static integer constants — for example, `Types.INTEGER = 4`, `Types.VARCHAR = 12`, `Types.TIMESTAMP = 93` — that JDBC uses to identify SQL column types at runtime. `DbValue` replaces that integer-keyed scheme with a sealed ADT where the Scala type system enforces exhaustiveness and each variant carries the actual value.

| Aspect                   | `DbValue`                                                                   | `java.sql.Types`                                                               |
|--------------------------|-----------------------------------------------------------------------------|--------------------------------------------------------------------------------|
| **Representation**       | Sealed ADT — one case per SQL type, each carrying its Scala value           | Integer constants — no type-level distinction between SQL types                |
| **Exhaustiveness**       | Compiler warns on unmatched cases in a `match` expression                   | No compile-time help; a missing `case` is a silent runtime omission            |
| **Value carriage**       | Each case class wraps the real Scala value                                  | Constants carry no value — reading/writing requires separate JDBC calls        |
| **Type safety**          | Pattern matching resolves directly to a concrete Scala type                 | `getObject(index)` returns `Object`; the caller casts at the call site         |
| **Extensibility**        | Sealed — no external subtypes; exhaustiveness guaranteed at compile time    | `int` constants — anyone can define new values; no compile-time protection     |
| **Null handling**        | `DbNull` is a first-class match arm alongside all other variants            | `Types.NULL = 0` is a constant; null detection additionally requires `wasNull()` |

### `DbValue` vs sealed case object enumerations

A simpler enumeration approach represents SQL types as sealed case objects that act as type tags, carrying no value payload. `DbValue` subtypes carry the actual SQL value alongside the type, not just a type label. The contrast makes the difference concrete:

```scala
// A type-tag-only alternative — NOT what DbValue does
sealed trait SqlType
case object SqlInt  extends SqlType
case object SqlText extends SqlType
case object SqlNull extends SqlType
// ...
```

The table below shows where `DbValue`'s value-carrying design differs from a tag-only enumeration in practice:

| Aspect                  | `DbValue` (value-carrying ADT)                                               | Type-tag enumeration                                                       |
|-------------------------|------------------------------------------------------------------------------|----------------------------------------------------------------------------|
| **Value storage**       | Each case class holds the real Scala value (`DbInt(42)`)                     | No value — the payload must be stored separately (`(SqlInt, Any)`)         |
| **Pattern match result**| The value is extracted in the same `case` arm that checks the type           | Extraction requires a second lookup into a separate parallel structure      |
| **Null modeling**       | `DbNull` is a case object; the match arm needs no extraction at all          | Indistinguishable from other type tags without explicit special-casing      |
| **JDBC alignment**      | Mirrors JDBC's typed methods (`setInt`, `setString`, etc.) precisely         | Requires an additional dispatch step to map the tag to the JDBC method      |
| **Composability**       | `IndexedSeq[DbValue]` carries both type and value — no parallel arrays       | A type array and a value array must be kept in sync by convention           |

## Integration

`DbValue` is the shared value currency across the `sql` module. Four types produce or consume it in every SQL workflow: `DbParam`, `Frag`, `DbCodec`, and `SqlDialect`. The flow of a `DbValue` through a typical parameterized query illustrates how these participants cooperate:

```
sql"SELECT * FROM user WHERE id = $userId"
                                  │
                                  ▼  DbParam[Int]#toDbValue(userId)
                          DbValue.DbInt(42)       ← typed intermediate value
                                  │
         ┌────────────────────────┼──────────────────────────────────┐
         ▼                        ▼                                  ▼
  stored in Frag.params    logged by SqlLogger           bound to PreparedStatement
  (IndexedSeq[DbValue])    (IndexedSeq[DbValue]          via DbParamWriter
                            passed to onSuccess)          (dispatched by variant)
```

The integration points within the module are:

- **`DbParam`** is the typeclass that converts a Scala value to a `DbValue` for use in the `sql"..."` interpolator. `DbParam[Int]` produces `DbValue.DbInt`, `DbParam[String]` produces `DbValue.DbString`, `DbParam[Option[A]]` produces `DbValue.DbNull` for `None` and delegates to the inner `DbParam[A]` for `Some`. The `sql"..."` macro calls `DbParam[A]#toDbValue` for each interpolated expression and stores the result in `Frag.params`.
- **`Frag`** stores its bound parameters as `IndexedSeq[DbValue]` in the `params` field. The `Frag#queryParams` accessor exposes this sequence for logging and testing. During execution, each element of `params` is dispatched to the appropriate `DbParamWriter` method (`setInt`, `setString`, `setNull`, etc.) by matching on the `DbValue` variant — the same pattern shown in the Extraction section above.
- **`DbCodec`** produces `DbValue` sequences through `DbCodec#toDbValues(value: A): IndexedSeq[DbValue]`. The result is parallel to `DbCodec#columns`: element `i` of the `IndexedSeq` corresponds to column `i`. `Frag.values[A]` calls `DbCodec[A].toDbValues` for each row to build a multi-row `VALUES` fragment, and `SqlLogger` receives the same `IndexedSeq[DbValue]` for observability — both type and value are available without any raw-JDBC casting.
- **`SqlDialect`** consumes `DbValue` through `SqlDialect#typeName(dbValue: DbValue): String`, which maps a representative `DbValue` instance to the DDL type string for that SQL type. `Table#createTable` calls `typeName` once per column, using the `DbValue` supplied by `TableMetadata` for that column's Scala type. A column whose codec produces `DbValue.DbInstant` receives `TIMESTAMPTZ` in PostgreSQL and `TEXT` in SQLite; a column whose codec produces `DbValue.DbBoolean` receives `BOOLEAN` in PostgreSQL and `INTEGER` in SQLite.

`DbValue` is intentionally free of module-specific logic: it defines no methods, carries no JDBC imports, and has no dependency on `Frag`, `DbCodec`, or `SqlDialect`. This keeps it a pure data carrier that any module participant can match on and produce without introducing circular dependencies. JDBC binding logic lives in `DbParamWriter`, type-name mapping lives in `SqlDialect`, and codec serialization lives in `DbCodec`; `DbValue` is only the typed token that flows between them. For the sibling types in this module, see [Frag](./frag.md), [DbCodec](./db-codec.md), and the [SQL module index](./index.md).
