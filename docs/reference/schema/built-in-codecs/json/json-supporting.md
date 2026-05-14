---
id: json-supporting
title: "Supporting Infrastructure Types"
---

The JSON module includes several supporting types that provide infrastructure for validation, schema derivation, and error handling. These types are typically used indirectly through higher-level APIs but are documented here for reference.

## JsonSchemaType

`JsonSchemaType` represents the JSON Schema type constraint system. It's a sealed trait used during schema validation and generation to capture type requirements.

### Type Hierarchy

```
JsonSchemaType
├─ Null
├─ Boolean
├─ Object
├─ Array
├─ Number
├─ Integer
├─ String
└─ Combining(List[JsonSchemaType])
```

### Usage

`JsonSchemaType` is primarily used internally when deriving JSON Schema from Scala types. It's rarely used directly in application code:

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.JsonSchema

case class User(name: String, age: Int)
object User { implicit val schema: Schema[User] = Schema.derived }

// When deriving a JsonSchema, JsonSchemaType is used internally
val jsonSchema: JsonSchema = Schema[User].toJsonSchema

// The schema contains type information derived from the Scala types
```

## Validation Support Types

### NonNegativeInt

Newtype wrapper ensuring integers are ≥ 0. Used in JSON Schema for constraints like `maxProperties`, `minProperties`, `minLength`, `maxLength`.

```scala mdoc:compile-only
import zio.blocks.schema.json.NonNegativeInt

// Safe construction
val count = NonNegativeInt(5)  // Right if >= 0
val invalid = NonNegativeInt(-1)  // Left

// Used in schema constraints
import zio.blocks.schema.json.JsonSchema
val schema = JsonSchema.obj(maxProperties = count)
```

### PositiveNumber

Newtype wrapper for positive numbers (> 0), commonly used for multipliers and scales:

```scala mdoc:compile-only
import zio.blocks.schema.json.PositiveNumber

val scale = PositiveNumber(1.5)
```

### RegexPattern

Compile-time validated regex pattern for use in JSON Schema `pattern` constraints:

```scala mdoc:compile-only
import zio.blocks.schema.json.{RegexPattern, JsonSchema}

// Define a pattern constraint
val emailPattern = RegexPattern("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")

// Use in schema validation
val schema = JsonSchema.string(pattern = Some(emailPattern))
```

### UriReference

Fully qualified URI reference as a string, used for schema links and references:

```scala mdoc:compile-only
import zio.blocks.schema.json.UriReference

val ref = UriReference("https://json-schema.org/draft/2020-12/schema")
```

### Anchor

Anchor identifier for schema fragments, used in JSON Schema `$anchor` constraints:

```scala mdoc:compile-only
import zio.blocks.schema.json.Anchor

val anchor = Anchor("addressDef")

// Used in schema with $anchor
```

## JSON Schema Evaluation

### ValidationOptions

Configuration for JSON Schema validation behavior:

```scala mdoc:compile-only
import zio.blocks.schema.json.{JsonSchema, ValidationOptions}

// Default validation
val standard = ValidationOptions.default

// Custom options (example structure)
val customValidation = ValidationOptions(
  ignoreUnknownFormats = false,
  useDefaults = true
)
```

### EvaluationResult

Result of validating a JSON value against a schema, containing validation details:

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonSchema, EvaluationResult}

val json = Json.String("test")
val schema = JsonSchema.string()

// Validation produces EvaluationResult with details
```

## Error Handling

### JsonCodecError

Internal error type used during JSON encoding/decoding. Typically surfaced as `SchemaError` through the public API:

```scala mdoc:compile-only
import zio.blocks.schema._

case class User(age: Int)
object User { implicit val schema: Schema[User] = Schema.derived }

val invalid = """{"age": "not a number"}"""
val result = invalid.as[User]
// Left(SchemaError(...)) — JsonCodecError is converted internally
```

Errors include:

- **Type mismatches:** Expected type X, got Y
- **Constraint violations:** Value exceeds maxLength, minimum, etc.
- **Structural errors:** Missing required fields, extra fields
- **Path information:** Location in the JSON structure where error occurred

## Internal Type Derivation

### JsonSchemaToReflect

Converts JSON Schema constraints into Scala `Schema` validation rules. Used internally during codec derivation:

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.JsonSchema

case class StringWithMinLength(value: String)
object StringWithMinLength {
  implicit val schema: Schema[StringWithMinLength] = 
    Schema.derived[StringWithMinLength]
}

// JsonSchemaToReflect is used internally to convert
// JSON Schema constraints to Scala Validation rules
```

## Integration with Codec Derivation

These supporting types work together during automatic codec derivation:

1. **Schema derivation** creates `Schema[A]` from case class/sealed trait definition
2. **JsonCodec derivation** creates encoder/decoder from `Schema[A]`
3. **JsonSchema derivation** creates JSON Schema validation constraints
4. **Validation types** enforce constraints during encoding/decoding
5. **Error types** propagate validation failures

```scala mdoc:compile-only
import zio.blocks.schema._

case class Product(
  id: Int,
  name: String,
  price: Double
)
object Product {
  implicit val schema: Schema[Product] = Schema.derived
}

// All supporting infrastructure is used automatically
val json = Product(1, "Widget", 9.99).toJson
val result = """{"id": 1, "name": "Widget", "price": 9.99}""".as[Product]
```

## When to Use Supporting Types

| Type                | Use When                                  |
|---------------------|-------------------------------------------|
| `JsonSchemaType`    | Building custom JSON Schema derivations   |
| `NonNegativeInt`    | Defining schema size constraints          |
| `PositiveNumber`    | Defining numeric scales in schemas        |
| `RegexPattern`      | Validating string patterns in JSON Schema |
| `UriReference`      | Building schema references and links      |
| `Anchor`            | Creating schema fragment identifiers      |
| `ValidationOptions` | Custom schema validation configuration    |
| `EvaluationResult`  | Analyzing detailed validation outcomes    |
| `JsonCodecError`    | Advanced error handling and diagnostics   |

Most application code won't interact with these types directly—they're used internally by the codec and schema derivation systems. Use them when extending JSON codec functionality or implementing custom validation logic.

## Debugging and Diagnostics

When encoding/decoding fails, `SchemaError` includes path information derived from these supporting types:

```scala mdoc:compile-only
import zio.blocks.schema._

case class Address(street: String, zipCode: Int)
object Address { implicit val schema: Schema[Address] = Schema.derived }

case class Person(name: String, address: Address)
object Person { implicit val schema: Schema[Person] = Schema.derived }

val invalid = """{"name": "Alice", "address": {"street": "Main St", "zipCode": "00000"}}"""
val result = invalid.as[Person]
// Left(SchemaError(...path: [.address, .zipCode], message: "expected Int"))

// The path information comes from supporting infrastructure
```

## Thread Safety and Pooling

All supporting types are immutable and thread-safe. Infrastructure types use thread-local pools for performance:

- `JsonReader` instances pooled via `ThreadLocal`
- `JsonWriter` instances pooled via `ThreadLocal`
- Validation caches per schema (immutable)
- No contention on shared state
