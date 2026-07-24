---
id: attributes
title: "Attributes"
description: "Immutable, unboxed typed key–value attribute store shared across spans, logs, metrics, and Resource in the telemetry module."
keywords:
  - "Typed Attribute Collection"
  - "Unboxed Primitive Storage"
  - "AttributeKey Typed Lookup"
  - "AttributesBuilder Incremental Construction"
  - "Telemetry Shared Vocabulary"
  - "Zero-Alloc Visitor"
  - "OpenTelemetry Attribute Semantics"
---

`Attributes` is an immutable, unboxed collection of typed key–value attribute pairs that serves as the shared vocabulary across all three OpenTelemetry pillars in the telemetry module — spans, log records, and metrics — as well as `Resource`. Each attribute binds an `AttributeKey[A]` (a string name paired with a static value type) to a value of type `A`. The implementation stores `Long`, `Double`, and `Boolean` values unboxed in a shared `Long` array (doubles via bit-cast, booleans as `1L`/`0L`), with a parallel `Byte`-discriminator array selecting the slot; `String` values occupy a separate `String` array. This parallel-array layout keeps GC pressure low on hot instrumentation paths.

- **Immutable** — Once constructed, no in-place mutation is possible; `++` and builder output always produce new instances.
- **Unboxed primitive storage** — `Long`, `Double`, and `Boolean` are stored in a `Long` array without boxing, eliminating `AttributeValue` wrapper allocation on the common fast path.
- **Cached hashCode** — Computed as a commutative sum of per-entry hashes at construction time, so equality checks and hash lookups incur no recomputation cost.
- **Last-write-wins** — `Attributes#get` scans entries from the last index backward, so the most-recently-added occurrence of a key takes precedence. This is consistent with how `AttributesBuilder#put` overwrites existing keys and with how `++` positions the right-hand operand after the left.
- **Eight attribute types** — `String`, `Long`, `Double`, `Boolean`, and the four `Seq` variants thereof (`Seq[String]`, `Seq[Long]`, `Seq[Double]`, `Seq[Boolean]`).

The full public surface groups into element access, iteration, merge, and conversion on `Attributes` itself, plus the `AttributesBuilder` companion for incremental construction:

```scala
final class Attributes private (...) {
  // Element Access
  def size: Int
  def isEmpty: Boolean
  def get[A](key: AttributeKey[A]): Option[A]    // O(n) reverse scan; last-write-wins

  // Iteration — choose the right one for your context
  def accept(visitor: AttributeVisitor): Unit     // zero-alloc; delivers primitives unboxed
  def foreach(f: (String, AttributeValue) => Unit): Unit  // allocates AttributeValue wrappers

  // Merge
  def ++(other: Attributes): Attributes           // O(n); values from `other` win on conflict

  // Conversion
  def toMap: Map[String, AttributeValue]
}

object Attributes {
  val empty: Attributes                           // pre-allocated zero value
  def of[A](key: AttributeKey[A], value: A): Attributes
  def builder: AttributesBuilder

  val ServiceName: AttributeKey[String]           // "service.name"
  val ServiceVersion: AttributeKey[String]        // "service.version"

  class AttributesBuilder private[Attributes] () {
    // Typed put — selects the correct slot automatically
    def put[A](key: AttributeKey[A], value: A): AttributesBuilder

    // Convenience raw-key overloads — no AttributeKey required
    def put(key: String, value: String): AttributesBuilder
    def put(key: String, value: Long): AttributesBuilder
    def put(key: String, value: Double): AttributesBuilder
    def put(key: String, value: Boolean): AttributesBuilder

    def build: Attributes           // copies to trimmed arrays
    def buildAndReset(): Attributes // zero-copy hand-off; resets builder to capacity 8
    def clear(): Unit               // resets to empty, retains backing arrays
  }
}
```

## Usage

The following block demonstrates building an `Attributes` collection from a builder, merging it with a single-attribute collection, performing a type-safe lookup, and iterating without boxing:

```scala mdoc:compile-only
import zio.blocks.telemetry.{Attributes, AttributeKey, AttributeVisitor}

// Incremental construction via builder
val requestAttrs: Attributes = Attributes.builder
  .put(AttributeKey.string("http.method"), "GET")
  .put(AttributeKey.long("http.status_code"), 200L)
  .put(AttributeKey.double("latency.ms"), 4.2)
  .put(AttributeKey.boolean("from.cache"), false)
  .build

// Single-attribute shorthand using a predefined key
val svcAttrs: Attributes = Attributes.of(Attributes.ServiceName, "checkout-service")

// Merge — values from the right operand take precedence on duplicate keys
val merged: Attributes = svcAttrs ++ requestAttrs
assert(merged.size == 5)

// Type-safe lookup returns Option[A] inferred from the key's type parameter
val status: Option[Long] = merged.get(AttributeKey.long("http.status_code"))
assert(status == Some(200L))

// Zero-allocation iteration via the visitor protocol
requestAttrs.accept(new AttributeVisitor {
  def visitString(key: String, value: String): Unit   = println(s"$key=$value")
  def visitLong(key: String, value: Long): Unit       = println(s"$key=$value")
  def visitDouble(key: String, value: Double): Unit   = println(s"$key=$value")
  def visitBoolean(key: String, value: Boolean): Unit = println(s"$key=$value")
})
```

## Construction / Creating Instances

We can build `Attributes` in three ways: by referencing the pre-allocated empty singleton, by constructing a one-element collection from a single key–value pair, or by using a mutable builder that accumulates multiple entries before producing an immutable result.

### `Attributes.empty` — The zero value

`Attributes.empty` is a pre-allocated singleton with no entries. Use it as the zero value when attributes are conditionally omitted, and pass it wherever an `Attributes` is expected without incurring any allocation:

```scala
object Attributes {
  val empty: Attributes
}
```

The empty singleton satisfies both `isEmpty` and `size == 0`, and the `++` operator short-circuits on it: `a ++ Attributes.empty` returns `a` unchanged, and `Attributes.empty ++ b` returns `b` unchanged:

```scala mdoc:compile-only
import zio.blocks.telemetry.{Attributes, AttributeKey}

val e = Attributes.empty
assert(e.isEmpty)
assert(e.size == 0)

// Short-circuit identity
val attrs = Attributes.of(AttributeKey.string("k"), "v")
assert((attrs ++ Attributes.empty) eq attrs)
assert((Attributes.empty ++ attrs) eq attrs)
```

### `Attributes.of` — Single-attribute shorthand

`Attributes.of` constructs a one-element `Attributes` from a typed key and its corresponding value, delegating to an internal builder and returning the result immediately:

```scala
object Attributes {
  def of[A](key: AttributeKey[A], value: A): Attributes
}
```

This is the most concise way to attach a single attribute — for example, when constructing a `Resource` with only a service name. The type parameter `A` is inferred from the key, so the compiler rejects a value whose type does not match:

```scala mdoc:compile-only
import zio.blocks.telemetry.{Attributes, AttributeKey}

val attrs = Attributes.of(AttributeKey.string("env"), "production")
assert(attrs.size == 1)
assert(attrs.get(AttributeKey.string("env")) == Some("production"))
```

### `Attributes.builder` — Incremental construction

`Attributes.builder` returns a fresh `AttributesBuilder` that accumulates entries one by one. Call `build` when done to obtain an immutable `Attributes` with the accumulated entries trimmed to exact size:

```scala
object Attributes {
  def builder: AttributesBuilder
}
```

The builder is especially useful when the number of attributes is not known at the call site, or when entries must be added conditionally. The following example shows the typical flow of constructing, populating, and finalizing:

```scala mdoc:compile-only
import zio.blocks.telemetry.{Attributes, AttributeKey}

val attrs: Attributes = Attributes.builder
  .put(AttributeKey.string("region"), "us-east-1")
  .put(AttributeKey.long("shard"), 3L)
  .put(AttributeKey.double("cpu.load"), 0.72)
  .put(AttributeKey.boolean("primary"), true)
  .build

assert(attrs.size == 4)
assert(attrs.get(AttributeKey.string("region")) == Some("us-east-1"))
```

## Predefined Instances

The `Attributes` companion declares two `AttributeKey[String]` constants for the most commonly used OpenTelemetry semantic conventions. Pass them to `Attributes.of` or `AttributesBuilder#put` just like any other typed key:

| Constant                    | Key name            | Value type |
| --------------------------- | ------------------- | ---------- |
| `Attributes.ServiceName`    | `"service.name"`    | `String`   |
| `Attributes.ServiceVersion` | `"service.version"` | `String`   |

These constants are shared across the module — `Resource`, `Span`, and log records can all carry the same keys — so using the predefined constants avoids misspelling the canonical key name:

```scala mdoc:compile-only
import zio.blocks.telemetry.{Attributes, Resource}

val resource = Resource(
  Attributes.builder
    .put(Attributes.ServiceName, "payments-service")
    .put(Attributes.ServiceVersion, "2.1.4")
    .build
)

assert(resource.attributes.get(Attributes.ServiceName) == Some("payments-service"))
```

## Core Operations

The methods on `Attributes` divide into four categories: element access, iteration, merge, and conversion. The mutable `AttributesBuilder` companion — obtained via `Attributes.builder` — is covered in the Builder subsection below.

### Element Access

The element-access group — `size`, `isEmpty`, and `get` — queries the collection without modifying it.

#### `size` — Count of stored attributes

`Attributes#size` returns the number of key–value pairs currently stored in the collection. This is an O(1) read of the internal `len` field:

```scala
final class Attributes private (...) {
  def size: Int
}
```

The count includes duplicate keys if the same key was added more than once (e.g., via `++`), because `Attributes` stores entries as ordered pairs rather than collapsing duplicates:

```scala mdoc:compile-only
import zio.blocks.telemetry.{Attributes, AttributeKey}

val a = Attributes.of(AttributeKey.string("k"), "v1")
val b = Attributes.of(AttributeKey.string("k"), "v2")
val merged = a ++ b

// Both entries are present; `get` returns the last one
assert(merged.size == 2)
assert(merged.get(AttributeKey.string("k")) == Some("v2"))
```

#### `isEmpty` — Emptiness test

`Attributes#isEmpty` returns `true` when the collection contains no attributes. It is equivalent to `size == 0` but reads more expressively in conditionals:

```scala
final class Attributes private (...) {
  def isEmpty: Boolean
}
```

The most common use is guarding a merge or export so that an empty `Attributes` does not produce unnecessary allocations:

```scala mdoc:compile-only
import zio.blocks.telemetry.Attributes

assert(Attributes.empty.isEmpty)
assert(!Attributes.of(zio.blocks.telemetry.AttributeKey.string("k"), "v").isEmpty)
```

#### `get` — Typed lookup by key

`Attributes#get` retrieves the value associated with a typed key, returning `None` if the key is absent. The search scans entries from the last index to the first, so when the same key appears multiple times the most-recently-added value is returned:

```scala
final class Attributes private (...) {
  def get[A](key: AttributeKey[A]): Option[A]
}
```

The return type is `Option[A]`, where `A` is the value type declared by the `AttributeKey`. Because the type is carried statically by the key, no cast is required at the call site:

```scala mdoc:compile-only
import zio.blocks.telemetry.{Attributes, AttributeKey}

val key = AttributeKey.long("http.status_code")
val attrs = Attributes.of(key, 200L)

val result: Option[Long] = attrs.get(key)
assert(result == Some(200L))

val missing: Option[Long] = attrs.get(AttributeKey.long("nonexistent"))
assert(missing == None)
```

:::caution
`get` performs a linear O(n) scan. For repeated random-access lookups over a large `Attributes`, convert to a `Map` with `toMap` first and query the map.
:::

### Iteration

The iteration group — `accept` and `foreach` — visits every attribute in insertion order. We choose between them based on whether we can afford to box primitive values.

#### `accept` — Zero-allocation visitor

`Attributes#accept` dispatches each entry to the matching `visit*` method on an `AttributeVisitor` without creating any intermediate wrapper objects. `Long`, `Double`, and `Boolean` values are passed directly as unboxed primitives:

```scala
final class Attributes private (...) {
  def accept(visitor: AttributeVisitor): Unit
}
```

The `AttributeVisitor` trait requires four abstract methods for the scalar types; the four `Seq` overloads have default no-op implementations and only need overriding if `Seq`-typed attributes are present:

```scala mdoc:compile-only
import zio.blocks.telemetry.{Attributes, AttributeKey, AttributeVisitor}

val attrs = Attributes.builder
  .put(AttributeKey.string("env"), "prod")
  .put(AttributeKey.long("shard"), 2L)
  .put(AttributeKey.boolean("debug"), false)
  .build

val keys = List.newBuilder[String]
attrs.accept(new AttributeVisitor {
  def visitString(key: String, value: String): Unit   = keys += key
  def visitLong(key: String, value: Long): Unit       = keys += key
  def visitDouble(key: String, value: Double): Unit   = keys += key
  def visitBoolean(key: String, value: Boolean): Unit = keys += key
})

assert(keys.result() == List("env", "shard", "debug"))
```

#### `foreach` — Functional iteration with boxed values

`Attributes#foreach` calls a function for every attribute, wrapping each value in the appropriate `AttributeValue` case class variant. This is convenient when the consumer already works with `AttributeValue` and boxing overhead is acceptable:

```scala
final class Attributes private (...) {
  def foreach(f: (String, AttributeValue) => Unit): Unit
}
```

The `AttributeValue` variants are `StringValue`, `LongValue`, `DoubleValue`, `BooleanValue`, `StringSeqValue`, `LongSeqValue`, `DoubleSeqValue`, and `BooleanSeqValue`:

```scala mdoc:compile-only
import zio.blocks.telemetry.{Attributes, AttributeKey, AttributeValue}

val attrs = Attributes.builder
  .put(AttributeKey.string("region"), "eu-west-1")
  .put(AttributeKey.long("port"), 8080L)
  .build

var entries = Map.empty[String, AttributeValue]
attrs.foreach { (k, v) => entries = entries + ((k, v)) }

assert(entries("region") == AttributeValue.StringValue("eu-west-1"))
assert(entries("port")   == AttributeValue.LongValue(8080L))
```

:::tip
Prefer `accept` over `foreach` in hot paths such as span export or metric recording, where the allocation of `AttributeValue` wrappers would accumulate under high throughput.
:::

### Merge

The merge group contains the single `++` operator, which concatenates two `Attributes` collections into one.

#### `++` — Concatenate two collections

`Attributes#++` merges this collection with `other` by copying both backing arrays into a fresh set of arrays of size `this.size + other.size`. Entries from `other` appear after entries from `this`, so on a duplicate key `other`'s value takes precedence under the reverse-scan semantics of `get`:

```scala
final class Attributes private (...) {
  def ++(other: Attributes): Attributes
}
```

When either operand is empty the result is the other operand unchanged — no arrays are allocated and no copy is performed:

```scala mdoc:compile-only
import zio.blocks.telemetry.{Attributes, AttributeKey}

val base    = Attributes.of(AttributeKey.string("env"), "prod")
val overlay = Attributes.of(AttributeKey.string("env"), "staging")

val merged = base ++ overlay
// "staging" wins because it comes from the right operand
assert(merged.get(AttributeKey.string("env")) == Some("staging"))
assert(merged.size == 2)
```

### Conversion

The conversion group exposes `toMap`, which exports the collection as a standard Scala `Map`.

#### `toMap` — Export to `Map[String, AttributeValue]`

`Attributes#toMap` converts the collection to a `Map[String, AttributeValue]` by iterating with `foreach` and accumulating entries. On duplicate keys the last entry wins, matching `get` semantics:

```scala
final class Attributes private (...) {
  def toMap: Map[String, AttributeValue]
}
```

This is useful when passing attributes to code that expects a plain `Map`, or for snapshot inspection in tests. The following example converts a small `Attributes` to a map and checks the result:

```scala mdoc:compile-only
import zio.blocks.telemetry.{Attributes, AttributeKey, AttributeValue}

val attrs = Attributes.builder
  .put(AttributeKey.string("service"), "api")
  .put(AttributeKey.long("port"), 443L)
  .build

val m: Map[String, AttributeValue] = attrs.toMap
assert(m("service") == AttributeValue.StringValue("api"))
assert(m("port")    == AttributeValue.LongValue(443L))
```

:::caution
`toMap` allocates a boxed `Map` and `AttributeValue` wrappers for every entry. Do not call it on hot paths such as within span export callbacks or per-request middleware — iterate with `accept` instead.
:::

### Builder

`AttributesBuilder` is the mutable companion class used to accumulate attributes incrementally before producing an immutable `Attributes`. We obtain one via `Attributes.builder`. All five `put` overloads return `this`, enabling fluent chaining. The builder starts with a backing capacity of 8 and doubles when full.

#### `AttributesBuilder#put` (typed key) — Add a typed attribute

`AttributesBuilder#put[A]` accepts an `AttributeKey[A]` and a value of the corresponding type `A`. It delegates to the appropriate raw-key overload based on the key's `AttributeType`, or calls the internal `putSeq` path for `Seq`-typed attributes. If the key already exists in the builder, the existing slot is overwritten (last-write-wins):

```scala
class AttributesBuilder private[Attributes] () {
  def put[A](key: AttributeKey[A], value: A): AttributesBuilder
}
```

This overload is the safest choice because the compiler statically prevents mismatched value types. The following example builds an `Attributes` carrying all four scalar types:

```scala mdoc:compile-only
import zio.blocks.telemetry.{Attributes, AttributeKey}

val attrs = Attributes.builder
  .put(AttributeKey.string("region"), "us-east-1")
  .put(AttributeKey.long("status"), 200L)
  .put(AttributeKey.double("latency.ms"), 1.5)
  .put(AttributeKey.boolean("ok"), true)
  .build

assert(attrs.size == 4)
```

#### `AttributesBuilder#put` (primitive overloads) — Convenience raw-key methods

When we already know the value type at the call site, four convenience overloads accept a plain `String` key name and the value directly. This avoids constructing an `AttributeKey` and is handy for ad-hoc instrumentation:

| Overload signature                                              | Stored as                                     |
| --------------------------------------------------------------- | --------------------------------------------- |
| `def put(key: String, value: String): AttributesBuilder`        | String slot; `T_STRING` discriminator         |
| `def put(key: String, value: Long): AttributesBuilder`          | Long array unboxed; `T_LONG` discriminator    |
| `def put(key: String, value: Double): AttributesBuilder`        | Long array via `doubleToRawLongBits`; `T_DOUBLE` |
| `def put(key: String, value: Boolean): AttributesBuilder`       | Long array as `1L`/`0L`; `T_BOOLEAN`         |

All four overloads return `this` for chaining, and an existing key is overwritten rather than duplicated. The following example uses all four in a single builder chain:

```scala mdoc:compile-only
import zio.blocks.telemetry.{Attributes, AttributeKey}

val attrs = Attributes.builder
  .put("http.method", "POST")
  .put("http.status_code", 201L)
  .put("latency.ms", 12.4)
  .put("cache.hit", false)
  .build

assert(attrs.get(AttributeKey.long("http.status_code")) == Some(201L))
assert(attrs.get(AttributeKey.double("latency.ms")).isDefined)
```

#### `AttributesBuilder#build` — Produce an immutable snapshot

`AttributesBuilder#build` copies the builder's content into new trimmed arrays (via `java.util.Arrays.copyOf`) and returns the resulting `Attributes`. The builder itself is left intact and can continue to accumulate entries:

```scala
class AttributesBuilder private[Attributes] () {
  def build: Attributes
}
```

If the builder is empty at the time of the call, `build` returns `Attributes.empty` without allocating any arrays:

```scala mdoc:compile-only
import zio.blocks.telemetry.Attributes

val b = Attributes.builder
b.put("k", "v")

val first  = b.build
b.put("k2", "v2")
val second = b.build

// Both snapshots are independent; `first` still has only one entry
assert(first.size  == 1)
assert(second.size == 2)
```

#### `AttributesBuilder#buildAndReset` — Zero-copy hand-off for pooled builders

`AttributesBuilder#buildAndReset` hands the builder's internal arrays directly to the new `Attributes` instance (no copy), then replaces them with fresh arrays of capacity 8. This is the preferred approach when the builder comes from an object pool, because it avoids the trimmed-copy allocation that `build` performs:

```scala
class AttributesBuilder private[Attributes] () {
  def buildAndReset(): Attributes
}
```

After the call the builder is immediately ready to accept new entries, and the returned `Attributes` owns the old arrays exclusively:

```scala mdoc:compile-only
import zio.blocks.telemetry.{Attributes, AttributeKey}

val b = Attributes.builder
b.put(AttributeKey.string("trace.id"), "abc123")

val snapshot = b.buildAndReset()

// Builder is now empty and reusable
assert(snapshot.size == 1)
assert(b.build == Attributes.empty)
```

#### `AttributesBuilder#clear` — Reset without reallocation

`AttributesBuilder#clear` zeros out the builder's active slots and resets the length to zero, but keeps the backing arrays in place. This avoids a GC-triggering reallocation when we want to reuse the builder for a fresh batch of attributes:

```scala
class AttributesBuilder private[Attributes] () {
  def clear(): Unit
}
```

After `clear`, `build` returns `Attributes.empty` just as it would for a brand-new builder:

```scala mdoc:compile-only
import zio.blocks.telemetry.Attributes

val b = Attributes.builder
b.put("region", "ap-southeast-1")
b.clear()

assert(b.build == Attributes.empty)
```

## Subtypes / Variants

`Attributes` exposes one companion sub-type, the mutable builder class documented in full in the Builder section of Core Operations above:

| Type                | Relationship                        | Role                                                                              |
| ------------------- | ----------------------------------- | --------------------------------------------------------------------------------- |
| `AttributesBuilder` | Inner class of `Attributes` companion | Mutable, reusable accumulator; obtained via `Attributes.builder`; not thread-safe |

`AttributesBuilder` is not thread-safe and is intended for single-threaded construction followed by a call to `build` or `buildAndReset`. Do not share a builder instance across threads.

## Comparison

### vs. OpenTelemetry Java SDK `Attributes`

The OpenTelemetry Java SDK's `io.opentelemetry.api.common.Attributes` type stores attribute values as boxed `Object` instances in a flat array. Every `Long`, `Double`, or `Boolean` value is autoboxed when stored and again when retrieved. The ZIO Blocks implementation eliminates this by keeping a parallel `Long` array for scalar primitives and reading them back with a discriminator byte, avoiding per-attribute allocation on hot paths:

| Aspect                        | ZIO Blocks `Attributes`                            | OTel Java SDK `Attributes`       |
| ----------------------------- | -------------------------------------------------- | -------------------------------- |
| Primitive storage             | Unboxed `Long` array (doubles bit-cast, booleans 1L/0L) | Boxed `Object` array          |
| Type-safe key                 | `AttributeKey[A]` — value type is static           | `AttributeKey<T>` — similar      |
| Lookup                        | O(n) reverse scan, last-write-wins                 | O(n) forward scan                |
| hashCode caching              | Commutative sum computed once at construction      | Recomputed on every call         |
| Immutability                  | Fully immutable                                    | Fully immutable                  |

### vs. `scala.collection.immutable.Map[String, Any]`

A `Map[String, Any]` is the naive alternative for carrying heterogeneous key–value pairs. It has two structural disadvantages compared to `Attributes`:

| Aspect              | `Attributes`                                             | `Map[String, Any]`                                       |
| ------------------- | -------------------------------------------------------- | -------------------------------------------------------- |
| Type safety         | `AttributeKey[A]` binds key to value type statically; `get` returns `Option[A]` without a cast | Value type is `Any`; every retrieval requires a runtime cast |
| Primitive boxing    | `Long`, `Double`, `Boolean` stored unboxed               | All values boxed as `AnyRef` on insertion                |
| Iteration protocol  | `accept` delivers primitives unboxed via virtual dispatch | Pattern match on `Any` required; boxing unavoidable      |
| Interop with OTel   | Direct structural match to OTel attribute semantics      | Requires a conversion layer                              |
