---
id: index
title: "JSON Codec"
---

The JSON codec module provides complete support for working with JSON data in ZIO Blocks. It includes a type-safe ADT for representing JSON values, composable patches for transforming them, a diff algorithm for computing minimal changes, and full JSON Schema 2020-12 support for validation and code generation.

Core types: `Json`, `JsonPatch`, `JsonDiffer`, `JsonSchema`.

```scala
// Represent any JSON value
val person = Json.Object("name" -> Json.String("Alice"), "age" -> Json.Number(30))

// Navigate and transform
val updated = person.set(p".age", Json.Number(31))

// Compute the diff
val patch = JsonPatch.diff(person, updated)

// Validate against a schema
val schema: JsonSchema = Schema[Person].toJsonSchema
schema.conforms(person)  // true
```

## How They Work Together

The JSON module revolves around transforming, validating, and understanding JSON data through four complementary operations:

```
1. Represent JSON                  →  Json (ADT with 6 cases)
   ├─ Create from literals, parse, interpolate
   └─ Navigate, transform, merge, query

2. Compute differences             →  JsonDiffer (diff algorithm)
   ├─ Handles all JSON types (numbers, strings, arrays, objects)
   └─ Returns minimal JsonPatch

3. Apply transformations           →  JsonPatch (composable patches)
   ├─ Create, compose, apply with multiple modes
   └─ Convert to/from DynamicPatch

4. Validate & generate schemas     →  JsonSchema (JSON Schema 2020-12)
   ├─ Derive from Scala types
   ├─ Validate JSON values
   └─ Generate schemas for documentation
```

## Common Patterns

### Pattern 1: Parse, Transform, Serialize

Read JSON, modify it, and write it back:

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.{Json, WriterConfig}

val jsonString = """{"name": "Alice", "age": 30}"""
val json = Json.parseUnsafe(jsonString)

// Transform
val updated = json.modify(p".age") {
  case Json.Number(n) => Json.Number(n + 1)
  case other => other
}

// Serialize
val output = updated.print(WriterConfig.withIndentionStep2)
```

### Pattern 2: Diff and Apply

Compute changes and apply them:

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonPatch}

val original = Json.Object("count" -> Json.Number(0))
val target = Json.Object("count" -> Json.Number(1), "active" -> Json.Boolean(true))

// Compute the diff
val patch = JsonPatch.diff(original, target)

// Apply to reconstruct
val result = patch.apply(original)
// Right({"count": 1, "active": true})
```

### Pattern 3: Validate with Schema

Generate and validate using schemas:

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.{Json, JsonSchema}

case class User(name: String, email: String, age: Int)
object User {
  implicit val schema: Schema[User] = Schema.derived
}

val jsonSchema: JsonSchema = Schema[User].toJsonSchema
val validJson = Json.Object(
  "name" -> Json.String("Alice"),
  "email" -> Json.String("alice@example.com"),
  "age" -> Json.Number(30)
)

jsonSchema.conforms(validJson)  // true
```

### Pattern 4: Compose Multiple Patches

Chain multiple transformations into a single patch:

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.{Json, JsonPatch}

val original = Json.Object("x" -> Json.Number(1), "y" -> Json.Number(2))

// Build patches independently
val patchX = JsonPatch.diff(
  original,
  original.set(p".x", Json.Number(10))
)

val patchY = JsonPatch.diff(
  original.set(p".x", Json.Number(10)),
  original.set(p".x", Json.Number(10)).set(p".y", Json.Number(20))
)

// Compose them
val combined = patchX ++ patchY
combined.apply(original)
// Right({"x": 10, "y": 20})
```

## Integration Points

**With other codecs:** `Json` values can be converted to and from other formats (Avro, TOON, etc.) via `DynamicValue`:

```scala mdoc:compile-only
import zio.blocks.schema.json.Json
import zio.blocks.schema.DynamicValue

val json = Json.parseUnsafe("""{"name": "Alice"}""")
val dynamic: DynamicValue = json.toDynamicValue

// Now use with other codecs...
```

**With Schema system:** `Json` values can be encoded/decoded using `Schema`-based derivation:

```scala mdoc:compile-only
import zio.blocks.schema._

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Encode to JSON
val person = Person("Alice", 30)
val json = person.toJson

// Decode from JSON
val decoded: Either[SchemaError, Person] = json.as[Person]
```

**With patching infrastructure:** `JsonPatch` converts to/from `DynamicPatch` for use in generic patching pipelines:

```scala mdoc:compile-only
import zio.blocks.schema.json.JsonPatch
import zio.blocks.schema.patch.DynamicPatch

val jsonPatch: JsonPatch = ???
val dynamicPatch: DynamicPatch = jsonPatch.toDynamicPatch
```

## Type Pages

- **[Json](./json.md)** — The core ADT representing JSON values with six cases (Object, Array, String, Number, Boolean, Null). Covers construction, navigation, modification, transformation, filtering, merging, and encoding.

- **[JsonPatch](./json-patch.md)** — Composable patches for transforming JSON values. Create patches via diff or manually, compose them with `++`, and apply with different failure modes (Strict, Lenient, Clobber).

- **[JsonDiffer](./json-differ.md)** — Diff algorithm computing minimal patches. Uses smart strategies per type: NumberDelta for numbers, LCS-based StringEdit for strings, ArrayEdit with insertion/deletion for arrays, and ObjectEdit for fields.

- **[JSON Schema](./json-schema.md)** — Full JSON Schema 2020-12 support for validation and code generation. Derive schemas from Scala types, validate JSON, construct schemas manually with builders, and combine with logical operators.
