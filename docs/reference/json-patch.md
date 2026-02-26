---
id: json-patch
title: "JsonPatch"
---

`JsonPatch` is an **untyped, composable patch** for [`Json`](./json.md) values. It represents a sequence of operations that transform one `Json` value into another — computed automatically via a diff algorithm or constructed manually. The two fundamental operations are `JsonPatch.diff` to compute a patch between two `Json` values, and `JsonPatch#apply` to apply it.

`JsonPatch`:
- is a pure value — applying it never mutates the input
- is composable via `++`, sequencing two patches one after another
- supports three failure-handling modes: `Strict`, `Lenient`, and `Clobber`
- carries its own `Schema` instances for full serialization support
- converts bidirectionally to/from `DynamicPatch` for use in generic patching pipelines

```scala
final case class JsonPatch(ops: Chunk[JsonPatch.JsonPatchOp])
```

## Motivation

In most systems, updating JSON data means transmitting the entire new value — even when only a single field changed. `JsonPatch` solves this by representing changes as a **first-class value** that can be:

- **Transmitted efficiently** — send only what changed, not the entire document
- **Stored for audit logs** — record every change for compliance, debugging, or undo
- **Composed** — merge multiple changes into a single atomic patch
- **Serialized** — persist patches to disk or a message queue and replay them later

```
Source JSON                    Target JSON
┌─────────────────────┐        ┌─────────────────────┐
│ { "name": "Alice",  │        │ { "name": "Alice",  │
│   "age": 25,        │──diff──│   "age": 26,        │
│   "city": "NYC" }   │        │   "city": "NYC" }   │
└─────────────────────┘        └─────────────────────┘
           │
           ▼
      JsonPatch {
        ObjectEdit(
          Modify("age", NumberDelta(1))
        )
      }
           │
           ▼ apply
┌─────────────────────┐
│ { "name": "Alice",  │
│   "age": 26,        │
│   "city": "NYC" }   │
└─────────────────────┘
```

The "hello world" for `JsonPatch` is diff-then-apply:

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}

val source = Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(25))
val target = Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(26))

val patch: JsonPatch = JsonPatch.diff(source, target)

// Roundtrip is always guaranteed
assert(patch.apply(source) == Right(target))
```

## Creating Patches

### `JsonPatch.diff`

Computes the minimal `JsonPatch` that transforms `source` into `target`. Uses a smart diff strategy per value type — see the [Diffing Algorithm](#diffing-algorithm) section for details.

```scala
object JsonPatch {
  def diff(source: Json, target: Json): JsonPatch
}
```

`diff` is also available as an extension method `Json#diff`:

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}

// Via companion object
val p1 = JsonPatch.diff(Json.Number(10), Json.Number(15))

// Via extension method on Json
val p2 = Json.Number(10).diff(Json.Number(15))

// Nested object diff produces minimal ObjectEdit
val p3 = JsonPatch.diff(
  Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2)),
  Json.Object("a" -> Json.Number(1), "b" -> Json.Number(9))
)
// p3 only touches "b", leaves "a" unchanged
```

### `JsonPatch.root`

Creates a patch with a single operation applied at the root of the value.

```scala
object JsonPatch {
  def root(operation: JsonPatch.Op): JsonPatch
}
```

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch._
import zio.blocks.chunk.Chunk

// Replace the entire value
val replaceAll = JsonPatch.root(Op.Set(Json.Null))

// Increment a number at the root
val increment = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(1))))

// Add a field to a root object
val addField = JsonPatch.root(Op.ObjectEdit(Chunk(ObjectOp.Add("active", Json.Boolean(true)))))
```

### `JsonPatch.apply`

Creates a patch with a single operation applied at the specified `DynamicOptic` path. Use this when targeting a nested location within the value.

```scala
object JsonPatch {
  def apply(path: DynamicOptic, operation: JsonPatch.Op): JsonPatch
}
```

Paths are built fluently on `DynamicOptic.root` using `.field(name)` to navigate object fields and `.at(index)` to navigate array elements:

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch._
import zio.blocks.schema.DynamicOptic

// Navigate to root → "user" → "age" and increment by 1
val path  = DynamicOptic.root.field("user").field("age")
val patch = JsonPatch(path, Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(1))))

val json = Json.Object("user" -> Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(25)))
// patch.apply(json) == Right(Json.Object("user" -> Json.Object("name" -> ..., "age" -> 26)))
```

### `JsonPatch.empty`

The empty patch. Applying it to any `Json` value returns that value unchanged. `empty` is the identity element for `++`.

```scala
object JsonPatch {
  val empty: JsonPatch
}
```

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}

JsonPatch.empty.isEmpty                           // true
JsonPatch.empty.apply(Json.Number(42))            // Right(Json.Number(42))
(JsonPatch.empty ++ JsonPatch.empty).isEmpty      // true
```

### `JsonPatch.fromDynamicPatch`

Converts a generic `DynamicPatch` to a `JsonPatch`. Returns `Left[SchemaError]` for operations not representable in JSON:
- Temporal deltas (`InstantDelta`, `DurationDelta`, etc.) — JSON has no native time type
- Non-string map keys — JSON object keys must always be strings

All numeric delta types (`IntDelta`, `LongDelta`, `DoubleDelta`, etc.) are widened to `NumberDelta(BigDecimal)`.

```scala
object JsonPatch {
  def fromDynamicPatch(patch: DynamicPatch): Either[SchemaError, JsonPatch]
}
```

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.patch.DynamicPatch
import zio.blocks.schema.SchemaError

val dynPatch: DynamicPatch       = JsonPatch.diff(Json.Number(1), Json.Number(2)).toDynamicPatch
val back: Either[SchemaError, JsonPatch] = JsonPatch.fromDynamicPatch(dynPatch)
// back == Right(JsonPatch that applies NumberDelta(1))
```

## Core Operations

### Applying Patches

#### `apply`

Applies this patch to a `Json` value. Returns `Right` with the patched value on success, or `Left[SchemaError]` on failure. The `mode` parameter controls failure handling — see [`PatchMode`](#patchmode).

```scala
case class JsonPatch(ops: Chunk[JsonPatch.JsonPatchOp]) {
  def apply(value: Json, mode: PatchMode = PatchMode.Strict): Either[SchemaError, Json]
}
```

`apply` is also available via the `Json#patch` extension method:

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch._
import zio.blocks.schema.patch.PatchMode
import zio.blocks.chunk.Chunk

val json  = Json.Object("score" -> Json.Number(10))
val patch = JsonPatch.root(Op.ObjectEdit(Chunk(
  ObjectOp.Modify("score", JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(5)))))
)))

// Via JsonPatch directly
patch.apply(json)                           // Right({"score": 15})

// Via Json extension method (default Strict mode)
json.patch(patch)                           // Right({"score": 15})

// With an explicit mode
json.patch(patch, PatchMode.Lenient)        // Right({"score": 15})
```

#### `isEmpty`

Returns `true` if this patch contains no operations.

```scala
case class JsonPatch(ops: Chunk[JsonPatch.JsonPatchOp]) {
  def isEmpty: Boolean
}
```

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}

JsonPatch.empty.isEmpty                     // true
JsonPatch.diff(Json.Number(1), Json.Number(1)).isEmpty  // true — no change, no ops
```

### Composing Patches

#### `++`

Concatenates two patches. The resulting patch applies `this` first, then `that`.

```scala
case class JsonPatch(ops: Chunk[JsonPatch.JsonPatchOp]) {
  def ++(that: JsonPatch): JsonPatch
}
```

`++` is the principal way to build complex patches from smaller parts:

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch._
import zio.blocks.schema.DynamicOptic
import zio.blocks.chunk.Chunk

// Two independent patches on different fields
val renamePatch = JsonPatch(
  DynamicOptic.root.field("name"),
  Op.Set(Json.String("Bob"))
)
val agePatch = JsonPatch(
  DynamicOptic.root.field("age"),
  Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(1)))
)

val combined = renamePatch ++ agePatch

val json = Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(25))
combined.apply(json)
// Right(Json.Object("name" -> "Bob", "age" -> 26))
```

### Converting

#### `toDynamicPatch`

Converts this `JsonPatch` to a [`DynamicPatch`](./patch.md). Always succeeds. `NumberDelta` widens to `BigDecimalDelta`.

```scala
case class JsonPatch(ops: Chunk[JsonPatch.JsonPatchOp]) {
  def toDynamicPatch: DynamicPatch
}
```

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.patch.DynamicPatch

val patch: JsonPatch     = JsonPatch.diff(Json.Number(1), Json.Number(5))
val dyn: DynamicPatch    = patch.toDynamicPatch
```

## PatchMode

`PatchMode` controls how `apply` reacts when an operation's precondition is not met (e.g., a field is missing, or `ObjectOp.Add` targets a key that already exists):

| Mode | Behaviour |
|------|-----------|
| `PatchMode.Strict` (default) | Returns `Left[SchemaError]` on the first failure |
| `PatchMode.Lenient` | Silently skips failing operations; returns `Right` with partial result |
| `PatchMode.Clobber` | Overwrites on conflicts; forces through missing-field errors where possible |

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch._
import zio.blocks.schema.patch.PatchMode
import zio.blocks.chunk.Chunk

val json = Json.Object("a" -> Json.Number(1))

// ObjectOp.Add fails in Strict mode when the key already exists
val patch = JsonPatch.root(Op.ObjectEdit(Chunk(ObjectOp.Add("a", Json.Number(99)))))

json.patch(patch, PatchMode.Strict)    // Left(SchemaError(...))
json.patch(patch, PatchMode.Lenient)   // Right({"a": 1}) — Add skipped
json.patch(patch, PatchMode.Clobber)   // Right({"a": 99}) — Add overwrites
```

## Operation Types

A `JsonPatch` is a sequence of `JsonPatchOp` values. Each `JsonPatchOp` pairs a `DynamicOptic` path with an `Op`:

```scala
final case class JsonPatchOp(path: DynamicOptic, operation: Op)
```

The full `Op` hierarchy:

```
Op (sealed trait)
 ├── Op.Set                — replace the target value entirely
 ├── Op.PrimitiveDelta     — numeric increment or string edit
 │    ├── PrimitiveOp.NumberDelta
 │    └── PrimitiveOp.StringEdit
 │         ├── StringOp.Insert
 │         ├── StringOp.Delete
 │         ├── StringOp.Append
 │         └── StringOp.Modify
 ├── Op.ArrayEdit          — insert / append / delete / modify array elements
 │    ├── ArrayOp.Insert
 │    ├── ArrayOp.Append
 │    ├── ArrayOp.Delete
 │    └── ArrayOp.Modify
 ├── Op.ObjectEdit         — add / remove / modify object fields
 │    ├── ObjectOp.Add
 │    ├── ObjectOp.Remove
 │    └── ObjectOp.Modify
 └── Op.Nested             — groups a sub-patch under a shared path prefix
```

### `Op.Set`

Replaces the target value with a new `Json` value, regardless of the current value. Works on any `Json` type.

```scala
final case class Set(value: Json) extends Op
```

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch._

// Replace number with string
JsonPatch.root(Op.Set(Json.String("replaced"))).apply(Json.Number(123))
// Right(Json.String("replaced"))

// Reset a nested field to null
import zio.blocks.schema.DynamicOptic
val patch = JsonPatch(DynamicOptic.root.field("status"), Op.Set(Json.Null))
patch.apply(Json.Object("status" -> Json.String("active"), "id" -> Json.Number(1)))
// Right({"status": null, "id": 1})
```

### `Op.PrimitiveDelta`

Applies a primitive mutation to a scalar value — either a numeric increment (`NumberDelta`) or a sequence of string edits (`StringEdit`).

```scala
final case class PrimitiveDelta(op: PrimitiveOp) extends Op
```

#### `PrimitiveOp.NumberDelta`

Adds `delta` to a `Json.Number`. Use a negative value to subtract. Fails if the target is not a `Json.Number`.

```scala
final case class NumberDelta(delta: BigDecimal) extends PrimitiveOp
```

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch._

val inc = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(5))))
val dec = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(-3))))

inc.apply(Json.Number(10))  // Right(Json.Number(15))
dec.apply(Json.Number(10))  // Right(Json.Number(7))
```

#### `PrimitiveOp.StringEdit`

Applies a sequence of `StringOp` operations to a `Json.String`. `JsonPatch.diff` generates `StringEdit` automatically when it is more compact than a full `Set`.

```scala
final case class StringEdit(ops: Chunk[StringOp]) extends PrimitiveOp
```

The `StringOp` cases:

| Case | Parameters | Effect |
|------|-----------|--------|
| `StringOp.Insert(index, text)` | position, text | Inserts `text` before character `index` |
| `StringOp.Delete(index, length)` | position, count | Removes `length` characters starting at `index` |
| `StringOp.Append(text)` | text | Appends `text` to the end |
| `StringOp.Modify(index, length, text)` | position, count, text | Replaces `length` characters at `index` with `text` |

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch._
import zio.blocks.chunk.Chunk

// Insert "Hello, " before "world"
val patch = JsonPatch.root(
  Op.PrimitiveDelta(PrimitiveOp.StringEdit(Chunk(StringOp.Insert(0, "Hello, "))))
)
patch.apply(Json.String("world"))
// Right(Json.String("Hello, world"))
```

:::tip
For most use cases, let `JsonPatch.diff` generate `StringEdit` automatically. The diff algorithm uses an LCS (Longest Common Subsequence) comparison and only emits `StringEdit` when it produces fewer bytes than a plain `Set`.
:::

### `Op.ArrayEdit`

Applies a sequence of `ArrayOp` operations to a `Json.Array`. Operations are applied in order, and each one sees the result of the previous.

```scala
final case class ArrayEdit(ops: Chunk[ArrayOp]) extends Op
```

The `ArrayOp` cases:

| Case | Parameters | Effect |
|------|-----------|--------|
| `ArrayOp.Insert(index, values)` | position, elements | Inserts `values` before `index` |
| `ArrayOp.Append(values)` | elements | Appends `values` to the end |
| `ArrayOp.Delete(index, count)` | position, count | Removes `count` elements starting at `index` |
| `ArrayOp.Modify(index, op)` | position, op | Applies `op` to the element at `index` |

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch._
import zio.blocks.chunk.Chunk

// [1, 2, 3] → prepend 0, drop last, append 4 → [0, 1, 2, 4]
val patch = JsonPatch.root(
  Op.ArrayEdit(Chunk(
    ArrayOp.Insert(0, Chunk(Json.Number(0))),
    ArrayOp.Delete(3, 1),
    ArrayOp.Append(Chunk(Json.Number(4)))
  ))
)
val original = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
patch.apply(original)
// Right(Json.Array(0, 1, 2, 4))
```

:::note
Array indices in `ArrayOp.Delete` and `ArrayOp.Modify` refer to the state of the array **after** all preceding ops in the same `ArrayEdit` have been applied.
:::

### `Op.ObjectEdit`

Applies a sequence of `ObjectOp` operations to a `Json.Object`. Operations are applied in order.

```scala
final case class ObjectEdit(ops: Chunk[ObjectOp]) extends Op
```

The `ObjectOp` cases:

| Case | Parameters | Effect |
|------|-----------|--------|
| `ObjectOp.Add(key, value)` | field name, value | Adds a new field; fails in `Strict` mode if key exists |
| `ObjectOp.Remove(key)` | field name | Removes an existing field |
| `ObjectOp.Modify(key, patch)` | field name, sub-patch | Applies `patch` recursively to the field value |

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch._
import zio.blocks.chunk.Chunk

val original = Json.Object(
  "name"  -> Json.String("Alice"),
  "age"   -> Json.Number(25),
  "city"  -> Json.String("NYC")
)

val patch = JsonPatch.root(Op.ObjectEdit(Chunk(
  ObjectOp.Add("email", Json.String("alice@example.com")),
  ObjectOp.Remove("city"),
  ObjectOp.Modify("age", JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(1)))))
)))

patch.apply(original)
// Right({"name": "Alice", "age": 26, "email": "alice@example.com"})
```

### `Op.Nested`

Groups a sub-patch under a shared path prefix. `JsonPatch.diff` emits `Nested` automatically when multiple operations share a common navigation path — this avoids repeating the full path in each `JsonPatchOp`.

```scala
final case class Nested(patch: JsonPatch) extends Op
```

You rarely need to construct `Nested` manually; it is primarily an internal optimization used by the diff algorithm.

## Diffing Algorithm

`JsonPatch.diff` (and its alias `Json#diff`) delegate to `JsonDiffer.diff`, which selects the most compact representation for each type of change:

| Value type | Change | Strategy |
|------------|--------|----------|
| Any | No change | No operation emitted |
| `Json.Number` | Value changed | `NumberDelta` — stores the numeric difference |
| `Json.String` | Value changed | `StringEdit` via LCS if smaller; otherwise `Set` |
| `Json.Array` | Elements changed | `ArrayEdit` with LCS-aligned `Insert`/`Delete`/`Append`/`Modify` |
| `Json.Object` | Fields changed | `ObjectEdit` with recursive per-field diff |
| Any | Type changed | `Set` — full replacement |

:::tip
`diff` followed by `apply` is always a lossless roundtrip: for any `source` and `target`, `JsonPatch.diff(source, target).apply(source) == Right(target)`.
:::

## JsonPatch vs RFC 6902 JSON Patch

ZIO Blocks' `JsonPatch` is **not** an implementation of [RFC 6902](https://datatracker.ietf.org/doc/html/rfc6902). The two share the same motivation but differ in design:

| | ZIO Blocks `JsonPatch` | RFC 6902 JSON Patch |
|---|---|---|
| Operations | Typed ADT (`Op.Set`, `Op.ArrayEdit`, …) | String-tagged JSON objects (`"op": "replace"`) |
| Paths | `DynamicOptic` (typed, composable) | JSON Pointer strings (`"/a/b/0"`) |
| Number changes | `NumberDelta` (stores diff) | `replace` (stores full new value) |
| String changes | LCS-based `StringEdit` | `replace` only |
| Array changes | LCS-aligned insert/delete | `add`, `remove`, `replace` at absolute indices |
| Serialization | Via ZIO Blocks `Schema` in any format | Always JSON |
| Composition | `++` operator | Array concatenation |

Use `JsonPatch` when working within ZIO Blocks. For interoperability with RFC 6902 tooling, convert the patch to JSON using the built-in `Schema` instances and reformat as needed.

## Advanced Usage

### Building a Change Log

Because `JsonPatch` is a pure value with a `Schema`, we can serialize every change and replay or audit it later:

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.Schema

// Assume we have a codec available (e.g., JSON, MessagePack)
// val codec = Schema[JsonPatch].derive(JsonFormat)

// Every mutation is a patch — store it instead of overwriting
val v0 = Json.Object("count" -> Json.Number(0))
val v1 = Json.Object("count" -> Json.Number(1))
val v2 = Json.Object("count" -> Json.Number(2))

val log: List[JsonPatch] = List(
  JsonPatch.diff(v0, v1),
  JsonPatch.diff(v1, v2)
)

// Replay: reconstruct any historical state
val replay = log.foldLeft(v0)((state, patch) => patch.apply(state).getOrElse(state))
assert(replay == v2)
```

### Composing Targeted Sub-Patches

We can build a single patch that updates multiple nested fields by combining focused per-field patches with `++`:

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch._
import zio.blocks.schema.DynamicOptic

def setField(field: String, value: Json): JsonPatch =
  JsonPatch(DynamicOptic.root.field(field), Op.Set(value))

val patch =
  setField("status", Json.String("active")) ++
  setField("updatedAt", Json.String("2025-01-01"))

val doc = Json.Object("status" -> Json.String("draft"), "id" -> Json.Number(42))
patch.apply(doc)
// Right({"status": "active", "id": 42, "updatedAt": "2025-01-01"})
```

## Integration

### With `Json`

`Json` exposes two extension methods as entry points into `JsonPatch`:

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.patch.PatchMode

val source = Json.Object("x" -> Json.Number(1))
val target = Json.Object("x" -> Json.Number(2))

val patch: JsonPatch       = source.diff(target)          // compute patch
val result                 = source.patch(patch)          // apply (Strict)
val lenient                = source.patch(patch, PatchMode.Lenient)
```

See [Json](./json.md) for the complete `Json` API.

### With `DynamicPatch`

`JsonPatch` and `DynamicPatch` are bidirectionally convertible. This is useful when patches originate from the typed `Patch[S]` system and need to be applied to raw JSON:

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.patch.DynamicPatch
import zio.blocks.schema.SchemaError

val jsonPatch: JsonPatch                  = JsonPatch.diff(Json.Number(1), Json.Number(3))

// JsonPatch → DynamicPatch (always succeeds)
val dynPatch: DynamicPatch               = jsonPatch.toDynamicPatch

// DynamicPatch → JsonPatch (may fail for temporal ops or non-string keys)
val back: Either[SchemaError, JsonPatch] = JsonPatch.fromDynamicPatch(dynPatch)
```

See [Patching](./patch.md) for the typed `Patch[S]` API.

### Serialization

`JsonPatch` ships with `Schema` instances for all nested operation types, enabling round-trip serialization via any ZIO Blocks codec:

```scala mdoc:compile-only
import zio.blocks.schema.json.JsonPatch
import zio.blocks.schema.Schema

val schema: Schema[JsonPatch] = implicitly[Schema[JsonPatch]]
```

See [Codec & Format](./codec.md) for how to derive and use codecs.
