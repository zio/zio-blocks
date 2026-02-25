---
id: json-patch
title: "JsonPatch"
---

`JsonPatch` is an **untyped, composable patch** for [`Json`](./json.md) values. It represents a sequence of operations that transform one `Json` value into another, computed either automatically via a diff algorithm or constructed manually from individual operations.

```scala
final case class JsonPatch(ops: Chunk[JsonPatch.JsonPatchOp])
```

`JsonPatch`:
- is a pure value — applying it never mutates the input
- is composable via `++`, sequencing patches one after another
- supports three failure-handling modes: `Strict`, `Lenient`, and `Clobber`
- can be serialized and deserialized through its `Schema` instances
- converts bidirectionally to/from `DynamicPatch` for use in generic patching pipelines

## Overview

A `JsonPatch` is a sequence of `JsonPatchOp` values. Each op pairs a `DynamicOptic` path (where to apply the change) with an `Op` (what to change):

```
JsonPatch
 └── Chunk[JsonPatchOp]
      └── JsonPatchOp
           ├── path: DynamicOptic     (navigates to the target location)
           └── operation: Op          (describes the transformation)
                ├── Op.Set            (replace entirely)
                ├── Op.PrimitiveDelta (number increment or string edit)
                ├── Op.ArrayEdit      (insert / append / delete / modify elements)
                ├── Op.ObjectEdit     (add / remove / modify fields)
                └── Op.Nested         (groups a sub-patch under a path)
```

The most common workflow is to diff two `Json` values and apply the resulting patch:

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.patch.PatchMode

val source = Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(25))
val target = Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(26))

val patch: JsonPatch          = JsonPatch.diff(source, target)
val result: Either[_, Json]   = patch.apply(source)
// result == Right(target)
```

## Creating Patches

### `JsonPatch.diff` — automatic diff

Computes the minimal patch that transforms `source` into `target`. The algorithm uses:
- `NumberDelta` for numeric changes (stores the delta, not the full value)
- `StringEdit` (LCS-based) when more compact than a full `Set`
- `ArrayEdit` (LCS-based) for element-level array changes
- `ObjectEdit` for field-by-field object changes
- `Set` as a fallback when types differ or no better representation exists

```scala
object JsonPatch {
  def diff(source: Json, target: Json): JsonPatch
}
```

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}

// Number change → NumberDelta
val p1 = JsonPatch.diff(Json.Number(10), Json.Number(15))

// String change → StringEdit or Set, whichever is smaller
val p2 = JsonPatch.diff(Json.String("hello"), Json.String("hello world"))

// Type change → Set
val p3 = JsonPatch.diff(Json.Number(1), Json.String("one"))

// Nested object → ObjectEdit with recursive patches
val p4 = JsonPatch.diff(
  Json.Object("a" -> Json.Number(1), "b" -> Json.Number(2)),
  Json.Object("a" -> Json.Number(1), "b" -> Json.Number(3))
)
```

`JsonPatch.diff` is also available as an extension method on `Json`:

```scala mdoc:compile-only
import zio.blocks.schema.json.Json

val patch = Json.Number(10).diff(Json.Number(15))
```

### `JsonPatch.root` — single operation at the root

Creates a patch with one operation applied to the root of the value.

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
val setOp = JsonPatch.root(Op.Set(Json.Number(42)))

// Add a field to an object
val addField = JsonPatch.root(
  Op.ObjectEdit(Chunk(ObjectOp.Add("score", Json.Number(100))))
)

// Append an element to an array
val appendElem = JsonPatch.root(
  Op.ArrayEdit(Chunk(ArrayOp.Append(Chunk(Json.Number(4)))))
)
```

### `JsonPatch.apply` — single operation at a path

Creates a patch with one operation applied at a specific `DynamicOptic` path.

```scala
object JsonPatch {
  def apply(path: DynamicOptic, operation: JsonPatch.Op): JsonPatch
}
```

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch._
import zio.blocks.schema.DynamicOptic

// Modify a nested field: { "user": { "age": 25 } } → { "user": { "age": 26 } }
val path  = DynamicOptic(DynamicOptic.Node.Field("user")) / DynamicOptic.Node.Field("age")
val patch = JsonPatch(path, Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(1))))
```

### `JsonPatch.empty` — identity patch

The empty patch applies no operations and is the identity element for `++`.

```scala
object JsonPatch {
  val empty: JsonPatch
}
```

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}

val p = JsonPatch.empty
p.isEmpty // true
```

### `JsonPatch.fromDynamicPatch` — conversion from `DynamicPatch`

Converts a generic `DynamicPatch` to a `JsonPatch`. Not all `DynamicPatch` operations are representable in JSON — temporal deltas (for `Instant`, `Duration`, etc.) and non-string map keys are rejected.

```scala
object JsonPatch {
  def fromDynamicPatch(patch: DynamicPatch): Either[SchemaError, JsonPatch]
}
```

All numeric delta types (`IntDelta`, `LongDelta`, `DoubleDelta`, etc.) are widened to `NumberDelta(BigDecimal)`, since JSON has a single number type.

:::note
Use `fromDynamicPatch` when receiving patches from a generic patching pipeline that may have originated from typed `Patch[S]` operations.
:::

## Applying Patches

### `JsonPatch#apply`

Applies the patch to a `Json` value. Returns `Right` with the patched value, or `Left` with a `SchemaError` if an operation fails.

```scala
final case class JsonPatch(ops: Chunk[JsonPatch.JsonPatchOp]) {
  def apply(value: Json, mode: PatchMode = PatchMode.Strict): Either[SchemaError, Json]
}
```

`apply` is also available as an extension method on `Json` via `Json#patch`:

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch._
import zio.blocks.chunk.Chunk

val json  = Json.Object("count" -> Json.Number(5))
val patch = JsonPatch.root(Op.ObjectEdit(Chunk(ObjectOp.Modify("count",
  JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(1))))
))))

val result = json.patch(patch)
// result == Right(Json.Object("count" -> Json.Number(6)))
```

## PatchMode

The `mode` parameter controls how `apply` handles precondition failures:

| Mode | Behaviour |
|------|-----------|
| `PatchMode.Strict` (default) | Returns `Left[SchemaError]` on the first failure |
| `PatchMode.Lenient` | Silently skips operations that fail preconditions |
| `PatchMode.Clobber` | Overwrites or forces through conflicts |

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch._
import zio.blocks.schema.patch.PatchMode
import zio.blocks.chunk.Chunk

val json  = Json.Object("a" -> Json.Number(1))
// Adding a key that already exists
val patch = JsonPatch.root(Op.ObjectEdit(Chunk(ObjectOp.Add("a", Json.Number(99)))))

// Strict: fails because "a" already exists
val strict  = json.patch(patch, PatchMode.Strict)   // Left(...)

// Lenient: skips the failing Add, returns original
val lenient = json.patch(patch, PatchMode.Lenient)  // Right(json)

// Clobber: overwrites "a"
val clobber = json.patch(patch, PatchMode.Clobber)  // Right(Json.Object("a" -> Json.Number(99)))
```

## Composing Patches

### `JsonPatch#++`

Concatenates two patches into one that applies `this` first, then `that`.

```scala
final case class JsonPatch(ops: Chunk[JsonPatch.JsonPatchOp]) {
  def ++(that: JsonPatch): JsonPatch
}
```

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch._

val setToFifty  = JsonPatch.root(Op.Set(Json.Number(50)))
val addTen      = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(10))))
val combined    = setToFifty ++ addTen

val result = combined.apply(Json.Number(0))
// result == Right(Json.Number(60))
```

### `JsonPatch#isEmpty`

Returns `true` if the patch contains no operations.

```scala
final case class JsonPatch(ops: Chunk[JsonPatch.JsonPatchOp]) {
  def isEmpty: Boolean
}
```

## Converting to `DynamicPatch`

### `JsonPatch#toDynamicPatch`

Converts this `JsonPatch` to a `DynamicPatch` for use in generic infrastructure or serialization via `DynamicPatch`'s schema.

```scala
final case class JsonPatch(ops: Chunk[JsonPatch.JsonPatchOp]) {
  def toDynamicPatch: DynamicPatch
}
```

`NumberDelta` widens to `BigDecimalDelta` during the conversion.

## Operation Reference

### `Op.Set`

Replaces the target entirely with a new value, regardless of what is there.

```scala
final case class Set(value: Json) extends Op
```

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch._

val patch  = JsonPatch.root(Op.Set(Json.String("replaced")))
val result = patch.apply(Json.Number(123))
// result == Right(Json.String("replaced"))
```

### `Op.PrimitiveDelta`

Applies a primitive mutation — either a numeric increment or a string edit.

```scala
final case class PrimitiveDelta(op: PrimitiveOp) extends Op
```

#### `PrimitiveOp.NumberDelta`

Adds `delta` to a `Json.Number`. Use a negative delta to subtract.

```scala
final case class NumberDelta(delta: BigDecimal) extends PrimitiveOp
```

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch._

val increment = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(5))))
val decrement = JsonPatch.root(Op.PrimitiveDelta(PrimitiveOp.NumberDelta(BigDecimal(-3))))

increment.apply(Json.Number(10)) // Right(Json.Number(15))
decrement.apply(Json.Number(10)) // Right(Json.Number(7))
```

#### `PrimitiveOp.StringEdit`

Applies a sequence of `StringOp` operations to a `Json.String`. The `JsonDiffer` uses an LCS algorithm to generate compact string edits automatically.

```scala
final case class StringEdit(ops: Chunk[StringOp]) extends PrimitiveOp
```

| `StringOp` | Description |
|------------|-------------|
| `StringOp.Insert(index, text)` | Inserts `text` at character position `index` |
| `StringOp.Delete(index, length)` | Removes `length` characters starting at `index` |
| `StringOp.Append(text)` | Appends `text` to the end |
| `StringOp.Modify(index, length, text)` | Replaces `length` chars at `index` with `text` |

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

### `Op.ArrayEdit`

Applies a sequence of `ArrayOp` operations to a `Json.Array`. Operations are applied in order.

```scala
final case class ArrayEdit(ops: Chunk[ArrayOp]) extends Op
```

| `ArrayOp` | Description |
|-----------|-------------|
| `ArrayOp.Insert(index, values)` | Inserts `values` before position `index` |
| `ArrayOp.Append(values)` | Appends `values` to the end |
| `ArrayOp.Delete(index, count)` | Removes `count` elements starting at `index` |
| `ArrayOp.Modify(index, op)` | Applies `op` to the element at `index` |

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch._
import zio.blocks.chunk.Chunk

val original = Json.Array(Json.Number(1), Json.Number(2), Json.Number(3))
val patch = JsonPatch.root(
  Op.ArrayEdit(Chunk(
    ArrayOp.Insert(0, Chunk(Json.Number(0))),  // prepend 0
    ArrayOp.Delete(3, 1),                      // remove original element at index 2 (now at 3)
    ArrayOp.Append(Chunk(Json.Number(4)))       // append 4
  ))
)
// [1, 2, 3] → [0, 1, 2, 4]
```

### `Op.ObjectEdit`

Applies a sequence of `ObjectOp` operations to a `Json.Object`. Operations are applied in order.

```scala
final case class ObjectEdit(ops: Chunk[ObjectOp]) extends Op
```

| `ObjectOp` | Description |
|------------|-------------|
| `ObjectOp.Add(key, value)` | Adds a new field; fails in `Strict` mode if key exists |
| `ObjectOp.Remove(key)` | Removes an existing field |
| `ObjectOp.Modify(key, patch)` | Applies `patch` to the existing field value |

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.json.JsonPatch._
import zio.blocks.chunk.Chunk

val original = Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(25))
val patch = JsonPatch.root(
  Op.ObjectEdit(Chunk(
    ObjectOp.Add("email", Json.String("alice@example.com")),
    ObjectOp.Remove("age"),
    ObjectOp.Modify("name", JsonPatch.root(Op.Set(Json.String("Bob"))))
  ))
)
// result: { "name": "Bob", "email": "alice@example.com" }
```

### `Op.Nested`

Groups a sub-patch under a shared path prefix. `JsonDiffer` emits `Nested` ops when multiple changes share the same navigation path.

```scala
final case class Nested(patch: JsonPatch) extends Op
```

## Integration

### With `Json`

`Json` exposes two extension methods that delegate to `JsonPatch`:

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.patch.PatchMode

val source = Json.Object("x" -> Json.Number(1))
val target = Json.Object("x" -> Json.Number(2))

// Compute a patch
val patch: JsonPatch = source.diff(target)

// Apply a patch
val result = source.patch(patch)               // default: Strict
val lenient = source.patch(patch, PatchMode.Lenient)
```

See [Json](./json.md) for the full `Json` API.

### With `DynamicPatch`

`JsonPatch` and `DynamicPatch` are bidirectionally convertible. Use this when interoperating with typed `Patch[S]` from the generic patching system:

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.patch.DynamicPatch

// JsonPatch → DynamicPatch (always succeeds)
val jsonPatch: JsonPatch     = JsonPatch.diff(Json.Number(1), Json.Number(2))
val dynPatch: DynamicPatch   = jsonPatch.toDynamicPatch

// DynamicPatch → JsonPatch (fails for temporal ops or non-string map keys)
val back: Either[_, JsonPatch] = JsonPatch.fromDynamicPatch(dynPatch)
```

See [Patching](./patch.md) for the typed `Patch[S]` API.

### Serialization

`JsonPatch` ships with `Schema` instances for all its nested operation types, enabling round-trip serialization via any ZIO Blocks codec (JSON, Avro, MessagePack, etc.).

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}
import zio.blocks.schema.Schema

// Schema instances are provided implicitly
val schema: Schema[JsonPatch] = implicitly[Schema[JsonPatch]]
```

See [Codec & Format](./codec.md) for how to use schemas with codecs.

## Diffing Algorithm

`JsonPatch.diff` and its alias `Json#diff` both delegate to `JsonDiffer.diff`, which selects the most compact representation for each change:

| Value type | Strategy |
|------------|----------|
| Same value | No operation emitted |
| `Json.Number` changed | `NumberDelta` (stores difference, not new value) |
| `Json.String` changed | `StringEdit` via LCS if smaller than `Set`; otherwise `Set` |
| `Json.Array` changed | `ArrayEdit` with LCS-aligned `Insert`/`Delete`/`Append`/`Modify` ops |
| `Json.Object` changed | `ObjectEdit` with field-by-field recursive diff |
| Different types | `Set` (full replacement) |

:::tip
`diff` followed by `apply` is always a roundtrip: `patch.apply(source) == Right(target)` for any `source` and `target`.
:::
