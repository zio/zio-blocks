# Plan: JSON Schema 2020-12 Support

**Related Issue**: #462
**Date**: 2026-01-24
**Status**: Planning

## Objective

Add first-class JSON Schema 2020-12 support to ZIO Blocks, enabling:
1. A `JsonSchema` ADT that models the JSON Schema 2020-12 specification
2. Bidirectional conversion between `Schema[A]` and `JsonSchema`
3. Runtime validation of `Json` values against `JsonSchema`
4. Schema extraction from `JsonBinaryCodec[A]` instances

## Architecture Overview

```
Schema[A] ──derive──> JsonBinaryCodec[A] ──.toJsonSchema──> JsonSchema
                                                               │
                                                               ▼
                                                            Json (toJson)
                                                               │
Json ──.check(schema)──> Option[SchemaError]                   │
                                                               ▼
Schema.fromJsonSchema(jsonSchema) ───> Schema[Json] <── JsonSchema.fromJson
```

## Phases

### Phase 1: JsonSchema ADT Foundation
**Goal**: Create the core `JsonSchema` ADT with JSON serialization

### Phase 2: JsonSchema Validation Engine
**Goal**: Implement `check` method for validating JSON against schemas

### Phase 3: JsonBinaryCodec Integration
**Goal**: Add `toJsonSchema` to `JsonBinaryCodec` and implement in deriver

### Phase 4: Schema Integration
**Goal**: Add `toJsonSchema`, `fromJsonSchema`, and `Schema[Json]` to Schema

### Phase 5: Json API Updates
**Goal**: Update `Json.check` and `Json.conforms` signatures

### Phase 6: Testing
**Goal**: Comprehensive test coverage for all components

---

## Phase 1: JsonSchema ADT Foundation

### Task 1.1: Create Helper Types
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchema.scala`

Create newtypes for type safety:
- [ ] `NonNegativeInt` - for minLength, maxLength, minItems, etc. (value >= 0)
- [ ] `PositiveNumber` - for multipleOf (value > 0)
- [ ] `RegexPattern` - ECMA-262 regex pattern wrapper
- [ ] `UriReference` - RFC 3986 URI reference
- [ ] `Anchor` - JSON Schema anchor name

### Task 1.2: Create JsonType Enumeration
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchema.scala`

```scala
sealed trait JsonType {
  def toJsonString: String
}
object JsonType {
  case object Null    extends JsonType
  case object Boolean extends JsonType
  case object String  extends JsonType
  case object Number  extends JsonType
  case object Integer extends JsonType
  case object Array   extends JsonType
  case object Object  extends JsonType
  
  def fromString(s: String): Option[JsonType]
}
```

### Task 1.3: Create SchemaType (Single or Union)
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchema.scala`

```scala
sealed trait SchemaType {
  def toJson: Json
}
object SchemaType {
  case class Single(value: JsonType) extends SchemaType
  case class Union(values: ::[JsonType]) extends SchemaType
}
```

### Task 1.4: Create JsonSchema Sealed Trait
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchema.scala`

Define the main ADT:
- [ ] `JsonSchema.True` - accepts all JSON values
- [ ] `JsonSchema.False` - rejects all JSON values
- [ ] `JsonSchema.SchemaObject` - full schema with all keywords

Core methods to define (stubs initially):
```scala
def toJson: Json
def check(json: Json): Option[SchemaError]
def conforms(json: Json): Boolean = check(json).isEmpty
def &&(that: JsonSchema): JsonSchema  // allOf
def ||(that: JsonSchema): JsonSchema  // anyOf
def unary_! : JsonSchema              // not
```

### Task 1.5: Define SchemaObject Fields
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchema.scala`

SchemaObject fields grouped by vocabulary:

**Core Vocabulary**:
- [ ] `$id: Option[UriReference]`
- [ ] `$schema: Option[java.net.URI]`
- [ ] `$anchor: Option[Anchor]`
- [ ] `$dynamicAnchor: Option[Anchor]`
- [ ] `$ref: Option[UriReference]`
- [ ] `$dynamicRef: Option[UriReference]`
- [ ] `$vocabulary: Option[Map[java.net.URI, Boolean]]`
- [ ] `$defs: Option[Map[String, JsonSchema]]`
- [ ] `$comment: Option[String]`

**Applicator Vocabulary (Composition)**:
- [ ] `allOf: Option[::[JsonSchema]]`
- [ ] `anyOf: Option[::[JsonSchema]]`
- [ ] `oneOf: Option[::[JsonSchema]]`
- [ ] `not: Option[JsonSchema]`

**Applicator Vocabulary (Conditional)**:
- [ ] `if: Option[JsonSchema]`
- [ ] `then: Option[JsonSchema]`
- [ ] `else: Option[JsonSchema]`

**Applicator Vocabulary (Object)**:
- [ ] `properties: Option[Map[String, JsonSchema]]`
- [ ] `patternProperties: Option[Map[RegexPattern, JsonSchema]]`
- [ ] `additionalProperties: Option[JsonSchema]`
- [ ] `propertyNames: Option[JsonSchema]`
- [ ] `dependentSchemas: Option[Map[String, JsonSchema]]`

**Applicator Vocabulary (Array)**:
- [ ] `prefixItems: Option[::[JsonSchema]]`
- [ ] `items: Option[JsonSchema]`
- [ ] `contains: Option[JsonSchema]`

**Unevaluated Vocabulary**:
- [ ] `unevaluatedProperties: Option[JsonSchema]`
- [ ] `unevaluatedItems: Option[JsonSchema]`

**Validation Vocabulary (Type)**:
- [ ] `type: Option[SchemaType]`
- [ ] `enum: Option[::[Json]]`
- [ ] `const: Option[Json]`

**Validation Vocabulary (Numeric)**:
- [ ] `multipleOf: Option[PositiveNumber]`
- [ ] `maximum: Option[BigDecimal]`
- [ ] `exclusiveMaximum: Option[BigDecimal]`
- [ ] `minimum: Option[BigDecimal]`
- [ ] `exclusiveMinimum: Option[BigDecimal]`

**Validation Vocabulary (String)**:
- [ ] `minLength: Option[NonNegativeInt]`
- [ ] `maxLength: Option[NonNegativeInt]`
- [ ] `pattern: Option[RegexPattern]`

**Validation Vocabulary (Array)**:
- [ ] `minItems: Option[NonNegativeInt]`
- [ ] `maxItems: Option[NonNegativeInt]`
- [ ] `uniqueItems: Option[Boolean]`
- [ ] `minContains: Option[NonNegativeInt]`
- [ ] `maxContains: Option[NonNegativeInt]`

**Validation Vocabulary (Object)**:
- [ ] `minProperties: Option[NonNegativeInt]`
- [ ] `maxProperties: Option[NonNegativeInt]`
- [ ] `required: Option[Set[String]]`
- [ ] `dependentRequired: Option[Map[String, Set[String]]]`

**Format Vocabulary**:
- [ ] `format: Option[String]`

**Content Vocabulary**:
- [ ] `contentEncoding: Option[String]`
- [ ] `contentMediaType: Option[String]`
- [ ] `contentSchema: Option[JsonSchema]`

**Meta-Data Vocabulary**:
- [ ] `title: Option[String]`
- [ ] `description: Option[String]`
- [ ] `default: Option[Json]`
- [ ] `deprecated: Option[Boolean]`
- [ ] `readOnly: Option[Boolean]`
- [ ] `writeOnly: Option[Boolean]`
- [ ] `examples: Option[::[Json]]`

**Extensions**:
- [ ] `extensions: Map[String, Json]` - for unrecognized keywords

### Task 1.6: Implement toJson for JsonSchema
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchema.scala`

- [ ] `JsonSchema.True.toJson` returns `Json.Boolean(true)`
- [ ] `JsonSchema.False.toJson` returns `Json.Boolean(false)`
- [ ] `SchemaObject.toJson` - serialize all non-None fields to JSON object
  - Handle keyword name mapping (e.g., `type` is a reserved word in Scala, use backticks)
  - Handle `$`-prefixed keywords

### Task 1.7: Implement fromJson for JsonSchema
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchema.scala`

```scala
object JsonSchema {
  def fromJson(json: Json): Either[SchemaError, JsonSchema]
  def parse(jsonString: String): Either[SchemaError, JsonSchema]
}
```

- [ ] Parse `true` as `JsonSchema.True`
- [ ] Parse `false` as `JsonSchema.False`
- [ ] Parse objects as `SchemaObject`
- [ ] Handle all keywords with appropriate type parsing
- [ ] Collect unrecognized keywords into `extensions`

### Task 1.8: Create Smart Constructors
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchema.scala`

```scala
object JsonSchema {
  def ofType(t: JsonType): JsonSchema
  def string(minLength: Option[NonNegativeInt] = None, ...): JsonSchema
  def number(minimum: Option[BigDecimal] = None, ...): JsonSchema
  def integer(minimum: Option[BigDecimal] = None, ...): JsonSchema
  def array(items: Option[JsonSchema] = None, ...): JsonSchema
  def `object`(properties: Option[Map[String, JsonSchema]] = None, ...): JsonSchema
  def enumOf(values: ::[Json]): JsonSchema
  def constOf(value: Json): JsonSchema
  def ref(uri: UriReference): JsonSchema
  
  val `null`: JsonSchema
  val boolean: JsonSchema
}
```

### Task 1.9: Implement Combinators
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchema.scala`

- [ ] `&&` (allOf): Combine schemas, merge if both are SchemaObjects with allOf
- [ ] `||` (anyOf): Combine schemas, merge if both are SchemaObjects with anyOf
- [ ] `!` (not): Wrap in SchemaObject with `not = Some(this)`

---

## Phase 2: JsonSchema Validation Engine

### Task 2.1: Implement Type Validation
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchema.scala`

- [ ] Validate `type` keyword (single and union)
- [ ] `null` matches `Json.Null`
- [ ] `boolean` matches `Json.Boolean`
- [ ] `string` matches `Json.String`
- [ ] `number` matches `Json.Number`
- [ ] `integer` matches `Json.Number` where value is integer
- [ ] `array` matches `Json.Array`
- [ ] `object` matches `Json.Object`

### Task 2.2: Implement Numeric Validations
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchema.scala`

- [ ] `minimum` - value >= minimum
- [ ] `maximum` - value <= maximum
- [ ] `exclusiveMinimum` - value > exclusiveMinimum
- [ ] `exclusiveMaximum` - value < exclusiveMaximum
- [ ] `multipleOf` - value is multiple of multipleOf

### Task 2.3: Implement String Validations
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchema.scala`

- [ ] `minLength` - string length >= minLength
- [ ] `maxLength` - string length <= maxLength
- [ ] `pattern` - string matches ECMA-262 regex

### Task 2.4: Implement Array Validations
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchema.scala`

- [ ] `minItems` - array length >= minItems
- [ ] `maxItems` - array length <= maxItems
- [ ] `uniqueItems` - all elements are unique
- [ ] `prefixItems` - validate positional items
- [ ] `items` - validate remaining items after prefixItems
- [ ] `contains` - at least one element matches
- [ ] `minContains` / `maxContains` - control contains count

### Task 2.5: Implement Object Validations
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchema.scala`

- [ ] `properties` - validate known properties
- [ ] `required` - required properties must be present
- [ ] `additionalProperties` - validate unknown properties
- [ ] `patternProperties` - validate properties matching patterns
- [ ] `propertyNames` - validate all property names
- [ ] `minProperties` / `maxProperties` - count constraints
- [ ] `dependentRequired` - conditional required properties
- [ ] `dependentSchemas` - conditional schemas

### Task 2.6: Implement Enum and Const
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchema.scala`

- [ ] `enum` - value must equal one of the enum values
- [ ] `const` - value must equal the const value

### Task 2.7: Implement Composition Keywords
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchema.scala`

- [ ] `allOf` - all schemas must validate (accumulate errors)
- [ ] `anyOf` - at least one schema must validate
- [ ] `oneOf` - exactly one schema must validate
- [ ] `not` - schema must NOT validate

### Task 2.8: Implement Conditional Keywords
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchema.scala`

- [ ] `if`/`then`/`else` - conditional validation

### Task 2.9: Implement $ref Resolution
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchema.scala`

- [ ] Resolve `$ref` to `$defs` within same document
- [ ] Handle JSON pointer references (e.g., `#/$defs/Address`)
- [ ] Scope: Same-document references only (cross-document out of scope)

### Task 2.10: Error Accumulation
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchema.scala`

- [ ] Use `SchemaError` for accumulated errors
- [ ] Include path information via `DynamicOptic`
- [ ] Accumulate multiple validation failures

### Task 2.11: Implement Format Validation (Optional)
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchema.scala`

Common formats (annotation by default, but can validate):
- [ ] `date-time` - RFC 3339 date-time
- [ ] `date` - RFC 3339 full-date
- [ ] `time` - RFC 3339 full-time
- [ ] `email` - RFC 5321 email
- [ ] `uuid` - RFC 4122 UUID
- [ ] `uri` - RFC 3986 URI

---

## Phase 3: JsonBinaryCodec Integration

### Task 3.1: Add toJsonSchema to JsonBinaryCodec
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonBinaryCodec.scala`

Add abstract method:
```scala
abstract class JsonBinaryCodec[A](...) extends BinaryCodec[A] {
  // ... existing methods ...
  
  /** Returns the JSON Schema describing values this codec encodes/decodes. */
  def toJsonSchema: JsonSchema
}
```

### Task 3.2: Implement toJsonSchema for Primitive Codecs
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonBinaryCodec.scala`

Update companion object codecs:
- [ ] `unitCodec` -> `type: "object"` (empty object)
- [ ] `booleanCodec` -> `type: "boolean"`
- [ ] `byteCodec` -> `type: "integer"` with min/max
- [ ] `shortCodec` -> `type: "integer"` with min/max
- [ ] `intCodec` -> `type: "integer"`
- [ ] `longCodec` -> `type: "integer"`
- [ ] `floatCodec` -> `type: "number"`
- [ ] `doubleCodec` -> `type: "number"`
- [ ] `charCodec` -> `type: "string"`, `minLength: 1`, `maxLength: 1`
- [ ] `stringCodec` -> `type: "string"`
- [ ] `bigIntCodec` -> `type: "integer"`
- [ ] `bigDecimalCodec` -> `type: "number"`

Temporal types (all `type: "string"` with format):
- [ ] `dayOfWeekCodec` -> format: custom
- [ ] `durationCodec` -> format: `duration`
- [ ] `instantCodec` -> format: `date-time`
- [ ] `localDateCodec` -> format: `date`
- [ ] `localDateTimeCodec` -> format: `date-time`
- [ ] `localTimeCodec` -> format: `time`
- [ ] `monthCodec` -> format: custom
- [ ] `monthDayCodec` -> format: custom
- [ ] `offsetDateTimeCodec` -> format: `date-time`
- [ ] `offsetTimeCodec` -> format: `time`
- [ ] `periodCodec` -> format: `duration`
- [ ] `yearCodec` -> format: custom
- [ ] `yearMonthCodec` -> format: custom
- [ ] `zoneIdCodec` -> format: custom
- [ ] `zoneOffsetCodec` -> format: custom
- [ ] `zonedDateTimeCodec` -> format: `date-time`

Other types:
- [ ] `currencyCodec` -> `type: "string"`
- [ ] `uuidCodec` -> `type: "string"`, format: `uuid`
- [ ] `dynamicValueCodec` -> `JsonSchema.True` (accepts any)

### Task 3.3: Add JsonSchemaInfo to JsonBinaryCodecDeriver
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonBinaryCodecDeriver.scala`

Create a helper to track schema info during derivation:
```scala
private case class JsonSchemaInfo(
  schema: JsonSchema,
  // metadata for records/variants
)
```

### Task 3.4: Implement Primitive Schema Generation
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonBinaryCodecDeriver.scala`

In `derivePrimitive`:
- [ ] Map `PrimitiveType[A]` to corresponding JSON Schema type
- [ ] Extract `Validation[A]` from `PrimitiveType` and map to constraints
- [ ] Handle `Doc` -> `description`
- [ ] Handle `Modifier.config` keys:
  - `json-schema.format` -> `format`
  - `json-schema.deprecated` -> `deprecated: true`
  - `json-schema.title` -> `title`
  - `json-schema.description` -> `description` (override Doc)

### Task 3.5: Validation to JSON Schema Mapping
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonBinaryCodecDeriver.scala`

Implement mapping:
```scala
def validationToJsonSchema(validation: Validation[_]): JsonSchema.SchemaObject
```

| Validation | JSON Schema |
|------------|-------------|
| `Validation.None` | (no constraints) |
| `Validation.Numeric.Positive` | `exclusiveMinimum: 0` |
| `Validation.Numeric.NonNegative` | `minimum: 0` |
| `Validation.Numeric.Negative` | `exclusiveMaximum: 0` |
| `Validation.Numeric.NonPositive` | `maximum: 0` |
| `Validation.Numeric.Range(min, max)` | `minimum` / `maximum` |
| `Validation.Numeric.Set(values)` | `enum: [...]` |
| `Validation.String.NonEmpty` | `minLength: 1` |
| `Validation.String.Empty` | `maxLength: 0` |
| `Validation.String.Blank` | (no direct mapping, could use pattern) |
| `Validation.String.NonBlank` | `minLength: 1` |
| `Validation.String.Length(min, max)` | `minLength` / `maxLength` |
| `Validation.String.Pattern(regex)` | `pattern` |

### Task 3.6: Implement Record Schema Generation
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonBinaryCodecDeriver.scala`

In `deriveRecord`:
- [ ] Generate `type: "object"`
- [ ] Generate `properties` from fields
- [ ] Apply `fieldNameMapper` to property names
- [ ] Handle `Modifier.rename` for field names
- [ ] Handle `Modifier.transient` - exclude from schema
- [ ] Handle `Modifier.alias` - document in extensions
- [ ] Generate `required` array for non-optional fields
- [ ] Handle Option fields based on `transientNone` / `requireOptionFields`
- [ ] Handle collection fields based on `transientEmptyCollection` / `requireCollectionFields`
- [ ] Handle default value fields based on `transientDefaultValue` / `requireDefaultValueFields`
- [ ] Set `additionalProperties: false` if `rejectExtraFields`
- [ ] Handle discriminator fields from parent variants

### Task 3.7: Implement Variant Schema Generation
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonBinaryCodecDeriver.scala`

In `deriveVariant`:

Based on `discriminatorKind`:

**`DiscriminatorKind.Key`** (default - wrapper object):
```json
{"oneOf": [{"type": "object", "properties": {"CaseName": {...}}, "required": ["CaseName"]}]}
```

**`DiscriminatorKind.Field(fieldName)`**:
```json
{
  "oneOf": [...],
  "discriminator": {"propertyName": "fieldName"}
}
```

**`DiscriminatorKind.None`** (no discriminator):
```json
{"oneOf": [...]}  // or anyOf if ambiguous
```

For enumerations (sealed trait with only case objects):
- [ ] Generate `enum` with case names

Handle:
- [ ] `caseNameMapper` for case names
- [ ] `Modifier.rename` on cases
- [ ] `Modifier.alias` on cases
- [ ] Option variant (None/Some) -> nullable schema

### Task 3.8: Implement Sequence Schema Generation
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonBinaryCodecDeriver.scala`

In `deriveSequence`:
- [ ] Generate `type: "array"`
- [ ] Generate `items` from element schema

### Task 3.9: Implement Map Schema Generation
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonBinaryCodecDeriver.scala`

In `deriveMap`:
- [ ] If key type is String: `type: "object"` with `additionalProperties`
- [ ] Otherwise: `type: "array"` with items as `[key, value]` tuples

### Task 3.10: Implement Wrapper Schema Generation
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonBinaryCodecDeriver.scala`

In `deriveWrapper`:
- [ ] Use wrapped type's schema
- [ ] Apply any metadata from wrapper (description, etc.)

### Task 3.11: Implement Dynamic Schema Generation
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonBinaryCodecDeriver.scala`

In `deriveDynamic`:
- [ ] Return `JsonSchema.True` (accepts any JSON)

### Task 3.12: Handle Recursive Types
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonBinaryCodecDeriver.scala`

- [ ] Use `$defs` for recursive schemas
- [ ] Generate `$ref` to definition
- [ ] Similar pattern to `recursiveRecordCache` ThreadLocal

---

## Phase 4: Schema Integration

### Task 4.1: Add toJsonSchema to Schema[A]
**File**: `schema/shared/src/main/scala/zio/blocks/schema/Schema.scala`

```scala
final case class Schema[A](reflect: Reflect.Bound[A]) {
  // ... existing methods ...
  
  def toJsonSchema: JsonSchema = 
    this.derive(JsonBinaryCodecDeriver).toJsonSchema
}
```

### Task 4.2: Add Schema.fromJsonSchema
**File**: `schema/shared/src/main/scala/zio/blocks/schema/Schema.scala`

```scala
object Schema extends SchemaCompanionVersionSpecific {
  def fromJsonSchema(jsonSchema: JsonSchema): Schema[Json] = {
    Schema[DynamicValue].wrap[Json](
      wrap = { dv =>
        val json = Json.fromDynamicValue(dv)
        jsonSchema.check(json) match {
          case None        => Right(json)
          case Some(error) => Left(error.message)
        }
      },
      unwrap = _.toDynamicValue
    )
  }
}
```

### Task 4.3: Add implicit Schema[Json]
**File**: `schema/shared/src/main/scala/zio/blocks/schema/Schema.scala`

```scala
object Schema extends SchemaCompanionVersionSpecific {
  implicit val json: Schema[Json] = Schema[DynamicValue].wrapTotal[Json](
    wrap = Json.fromDynamicValue,
    unwrap = _.toDynamicValue
  )
}
```

### Task 4.4: Add Schema[JsonSchema]
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/JsonSchema.scala`

```scala
object JsonSchema {
  implicit val schema: Schema[JsonSchema] = ???
}
```

This requires careful handling of the recursive ADT. Options:
- Manual construction using `Reflect` primitives
- Use `Schema.derived` if the structure supports it
- Define via DynamicValue conversion

---

## Phase 5: Json API Updates

### Task 5.1: Update Json.check Signature
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/Json.scala`

Change from:
```scala
def check(schema: Json): Either[JsonError, Unit] = ???
```

To:
```scala
def check(schema: JsonSchema): Option[SchemaError] = schema.check(this)
```

### Task 5.2: Update Json.conforms Signature
**File**: `schema/shared/src/main/scala/zio/blocks/schema/json/Json.scala`

Change from:
```scala
def conforms(schema: Json): Boolean = ???
```

To:
```scala
def conforms(schema: JsonSchema): Boolean = check(schema).isEmpty
```

---

## Phase 6: Testing

### Task 6.1: Create JsonSchemaSpec
**File**: `schema/shared/src/test/scala/zio/blocks/schema/json/JsonSchemaSpec.scala`

Test core ADT:
- [ ] Boolean schemas: `True` accepts all, `False` rejects all
- [ ] Type validation for each JsonType
- [ ] Type unions
- [ ] Numeric constraints (min, max, exclusiveMin, exclusiveMax, multipleOf)
- [ ] String constraints (minLength, maxLength, pattern)
- [ ] Array constraints (minItems, maxItems, uniqueItems, prefixItems, items, contains)
- [ ] Object constraints (properties, required, additionalProperties, patternProperties, etc.)
- [ ] Composition (allOf, anyOf, oneOf, not)
- [ ] Conditional (if/then/else)
- [ ] Enum and const
- [ ] Error accumulation (multiple failures collected)

### Task 6.2: Create JsonSchemaRoundTripSpec
**File**: `schema/shared/src/test/scala/zio/blocks/schema/json/JsonSchemaRoundTripSpec.scala`

Test serialization:
- [ ] `toJson` / `fromJson` round-trip for all schema types
- [ ] Boolean schema serialization
- [ ] Empty object equivalence (`{}` == `True`)
- [ ] Extension preservation (unknown keywords survive round-trip)
- [ ] All keyword serialization

### Task 6.3: Create JsonBinaryCodecToJsonSchemaSpec
**File**: `schema/shared/src/test/scala/zio/blocks/schema/json/JsonBinaryCodecToJsonSchemaSpec.scala`

Test codec schema extraction:
- [ ] Primitives produce correct schemas
- [ ] Temporal types have correct formats
- [ ] Records produce object schemas with properties and required
- [ ] Variants produce oneOf with discriminator
- [ ] Enumerations produce enum schemas
- [ ] Option fields are non-required or nullable
- [ ] Collections produce array schemas
- [ ] Maps produce object or array schemas
- [ ] Nested structures work correctly
- [ ] Field renaming affects property names
- [ ] Transient fields are excluded
- [ ] Doc maps to description
- [ ] Validation constraints map to keywords
- [ ] Config modifiers are respected

### Task 6.4: Create SchemaToJsonSchemaSpec
**File**: `schema/shared/src/test/scala/zio/blocks/schema/json/SchemaToJsonSchemaSpec.scala`

Test Schema conversion:
- [ ] All built-in schemas work
- [ ] Derived schemas work
- [ ] Consistency with codec approach
- [ ] Recursive types produce valid schemas with $ref

### Task 6.5: Create SchemaFromJsonSchemaSpec
**File**: `schema/shared/src/test/scala/zio/blocks/schema/json/SchemaFromJsonSchemaSpec.scala`

Test Schema construction:
- [ ] Valid JSON passes validation
- [ ] Invalid JSON fails with errors
- [ ] Error messages include paths
- [ ] Round-trip through DynamicValue works
- [ ] Encode/decode with resulting Schema[Json] works

### Task 6.6: Create JsonCheckSpec
**File**: `schema/shared/src/test/scala/zio/blocks/schema/json/JsonCheckSpec.scala`

Test Json.check/conforms:
- [ ] Method signature changes work
- [ ] Delegation to JsonSchema.check works
- [ ] All validation scenarios from JsonSchemaSpec work via Json methods

### Task 6.7: Create JsonSchemaCombinatorSpec
**File**: `schema/shared/src/test/scala/zio/blocks/schema/json/JsonSchemaCombinatorSpec.scala`

Test combinators:
- [ ] `&&` (allOf) validates if both pass
- [ ] `||` (anyOf) validates if either passes
- [ ] `!` (not) validates if schema fails
- [ ] Associativity
- [ ] Commutativity (for validation purposes)
- [ ] De Morgan's laws

---

## Dependencies Between Tasks

```
Phase 1 (ADT Foundation)
├── Task 1.1-1.5: Core types and SchemaObject (no deps)
├── Task 1.6: toJson (depends on 1.1-1.5)
├── Task 1.7: fromJson (depends on 1.1-1.5)
├── Task 1.8: Smart constructors (depends on 1.1-1.5)
└── Task 1.9: Combinators (depends on 1.1-1.5)

Phase 2 (Validation Engine)
├── All tasks depend on Phase 1
├── Tasks 2.1-2.6: Independent of each other
├── Tasks 2.7-2.8: Depend on 2.1-2.6 for subschema validation
├── Task 2.9: $ref resolution (depends on 2.1-2.8)
└── Task 2.10-2.11: Error accumulation and format (parallel)

Phase 3 (Codec Integration)
├── Task 3.1: Add abstract method (no deps)
├── Task 3.2: Primitive codecs (depends on Phase 1)
├── Tasks 3.3-3.12: All depend on Phase 1-2 and 3.1
└── Tasks 3.6-3.7: Record/Variant (most complex, depends on 3.4-3.5)

Phase 4 (Schema Integration)
├── Task 4.1: toJsonSchema (depends on Phase 3)
├── Task 4.2: fromJsonSchema (depends on Phase 2)
├── Task 4.3: Schema[Json] (no deps beyond existing code)
└── Task 4.4: Schema[JsonSchema] (depends on Phase 1)

Phase 5 (Json API)
├── Tasks 5.1-5.2: Depend on Phase 2

Phase 6 (Testing)
├── Task 6.1: JsonSchemaSpec (depends on Phase 1-2)
├── Task 6.2: Round-trip spec (depends on 6.1)
├── Task 6.3: Codec spec (depends on Phase 3)
├── Tasks 6.4-6.5: Schema specs (depends on Phase 4)
├── Task 6.6: Json.check spec (depends on Phase 5)
└── Task 6.7: Combinator spec (depends on Phase 1-2)
```

---

## Recommended Implementation Order

### Milestone 1: Minimal JsonSchema ADT
1. Task 1.1: Helper types
2. Task 1.2: JsonType enumeration
3. Task 1.3: SchemaType
4. Task 1.4: JsonSchema trait with True/False/SchemaObject (minimal fields)
5. Task 1.6: toJson (partial - just type, properties, required)
6. Task 1.7: fromJson (partial)
7. Task 6.1: Basic tests for type validation

### Milestone 2: Core Validation
1. Task 2.1: Type validation
2. Task 2.2-2.5: Numeric, string, array, object validations
3. Task 2.6: Enum and const
4. Task 2.10: Error accumulation
5. Expand Task 6.1 tests

### Milestone 3: Composition
1. Task 2.7: allOf, anyOf, oneOf, not
2. Task 2.8: if/then/else
3. Task 1.9: Combinators
4. Task 6.7: Combinator tests

### Milestone 4: Codec Integration
1. Task 3.1: Add abstract method to JsonBinaryCodec
2. Task 3.2: Primitive codec schemas
3. Task 3.4-3.5: Primitive derivation with validation mapping
4. Task 3.6: Record derivation
5. Task 3.7: Variant derivation
6. Task 3.8-3.11: Sequence, Map, Wrapper, Dynamic
7. Task 6.3: Codec tests

### Milestone 5: Schema Integration
1. Task 4.1: Schema.toJsonSchema
2. Task 4.2: Schema.fromJsonSchema
3. Task 4.3: Schema[Json]
4. Task 6.4-6.5: Schema tests

### Milestone 6: Json API and Polish
1. Task 5.1-5.2: Json.check/conforms
2. Task 6.6: Json.check tests
3. Task 6.2: Round-trip tests
4. Task 2.9: $ref resolution
5. Task 2.11: Format validation
6. Task 3.12: Recursive types
7. Task 4.4: Schema[JsonSchema]
8. Complete remaining SchemaObject fields (Task 1.5)

---

## Success Criteria

- [ ] All 7 test suites pass
- [ ] `JsonSchema` ADT covers JSON Schema 2020-12 vocabularies
- [ ] `JsonSchema#toJson` produces valid JSON Schema documents
- [ ] `JsonSchema.fromJson` parses valid JSON Schema documents
- [ ] `JsonSchema#check` validates with accumulated errors
- [ ] `JsonBinaryCodec[A].toJsonSchema` reflects codec configuration
- [ ] `Schema[A].toJsonSchema` works for all derivable types
- [ ] `Schema.fromJsonSchema` produces working `Schema[Json]`
- [ ] `Json#check` and `Json#conforms` use `JsonSchema` parameter
- [ ] `Validation[A]` maps to JSON Schema keywords
- [ ] `Doc` maps to `description`
- [ ] `Modifier.rename`, `Modifier.transient`, `Modifier.config` are respected
- [ ] Code follows existing project style
- [ ] No new dependencies introduced
- [ ] Code formatted with `sbt fmt`

---

## Notes

1. **Start with the ADT**: Get `JsonSchema` working before integrating with codecs.

2. **Validation is complex**: The `check` method is the hardest part. Consider `unevaluatedProperties`/`unevaluatedItems` carefully.

3. **Error accumulation**: Use existing `SchemaError` pattern with `DynamicOptic` paths.

4. **$ref scope**: Only same-document `$ref` to `$defs` is required initially.

5. **Format validation**: Make it non-fatal or configurable per JSON Schema 2020-12 spec.

6. **Test against JSON Schema Test Suite**: Consider using the [official test suite](https://github.com/json-schema-org/JSON-Schema-Test-Suite).

7. **Performance**: The deriver already uses ThreadLocal caches for recursive types. Follow the same pattern for schema generation.
