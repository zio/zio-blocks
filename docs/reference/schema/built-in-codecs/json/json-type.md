---
id: json-type
title: "JsonType"
---

`JsonType` is a sealed trait representing the runtime type of a `Json` value. It provides six case objects corresponding to the six JSON types: `ObjectType`, `ArrayType`, `StringType`, `NumberType`, `BooleanType`, and `NullType`. Use `JsonType` for pattern matching, type discrimination, and runtime type inspection.

## Overview

The six JSON types form a complete algebraic description of all possible JSON values. `JsonType` enables you to:

- **Pattern match** on JSON values to handle each type's logic
- **Discriminate at runtime** without nested `match` expressions
- **Build type-safe queries** that filter by JSON type
- **Generate schemas** that respect type information

Unlike `Json`'s runtime representation (`Object`, `Array`, `String`, etc.), `JsonType` is lightweight and designed for type inspection without carrying data.

## Type Enumeration

| Type | Represents | Example |
|------|-----------|---------|
| `ObjectType` | JSON object | `{"key": "value"}` |
| `ArrayType` | JSON array | `[1, 2, 3]` |
| `StringType` | JSON string | `"hello"` |
| `NumberType` | JSON number | `42`, `3.14`, `-1e10` |
| `BooleanType` | JSON boolean | `true`, `false` |
| `NullType` | JSON null | `null` |

## Pattern Matching

### Direct Type Testing

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonType}

val values = Seq(
  Json.Object.empty,
  Json.Array.empty,
  Json.String("text"),
  Json.Number(42),
  Json.Boolean(true),
  Json.Null
)

values.foreach { json =>
  json.jsonType match {
    case JsonType.ObjectType  => println("object")
    case JsonType.ArrayType   => println("array")
    case JsonType.StringType  => println("string")
    case JsonType.NumberType  => println("number")
    case JsonType.BooleanType => println("boolean")
    case JsonType.NullType    => println("null")
  }
}
```

### Handler Functions

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonType}

val json = Json.String("hello")

// Dispatch based on type
val handler: JsonType => String = {
  case JsonType.ObjectType  => "handle object"
  case JsonType.ArrayType   => "handle array"
  case JsonType.StringType  => "handle string"
  case JsonType.NumberType  => "handle number"
  case JsonType.BooleanType => "handle boolean"
  case JsonType.NullType    => "handle null"
}

val result = handler(json.jsonType)
```

## Type Checking and Filtering

### Direct Type Checking

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonType}

val json = Json.Object("name" -> Json.String("Alice"))

// Check single type
val isObject = json.is(JsonType.ObjectType)   // true
val isArray = json.is(JsonType.ArrayType)     // false
val isString = json.is(JsonType.StringType)   // false

// Check any of multiple types
val isComposite = json.jsonType match {
  case JsonType.ObjectType | JsonType.ArrayType => true
  case _ => false
}
```

### Filtering by Type

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonType}

val mixed = Seq(
  Json.String("text"),
  Json.Number(42),
  Json.Boolean(true),
  Json.String("more")
)

// Filter to one type
val onlyStrings = mixed.filter(_.is(JsonType.StringType))
val onlyNumbers = mixed.filter(_.is(JsonType.NumberType))

// Use JsonSelection type filters (simpler)
val arr = Json.Array.from(mixed)
val strings = arr.strings   // Already filtered
val numbers = arr.numbers
```

## Type-Based Extraction

### Safe Extraction with Pattern Matching

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonType}

val json = Json.Object(
  "items" -> Json.Array(
    Json.Number(1),
    Json.Number(2)
  )
)

// Extract with type checking
val count = json.get("items") match {
  case sel if sel.values.exists(_.exists(_.is(JsonType.ArrayType))) =>
    sel.values.map(_.size).getOrElse(0)
  case _ => 0
}
```

### Conditional Processing

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonType}

def process(json: Json): String = json.jsonType match {
  case JsonType.ObjectType =>
    json match {
      case obj: Json.Object => s"Object with ${obj.value.length} fields"
      case _ => "Unknown object"
    }
  case JsonType.ArrayType =>
    json match {
      case arr: Json.Array => s"Array with ${arr.value.length} elements"
      case _ => "Unknown array"
    }
  case JsonType.StringType =>
    json match {
      case Json.String(s) => s"String: $s"
      case _ => "Unknown string"
    }
  case JsonType.NumberType =>
    json match {
      case Json.Number(n) => s"Number: $n"
      case _ => "Unknown number"
    }
  case JsonType.BooleanType =>
    json match {
      case Json.Boolean(b) => s"Boolean: $b"
      case _ => "Unknown boolean"
    }
  case JsonType.NullType => "Null"
}
```

## Type Introspection

### Building Type-Safe Handlers

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonType}

// Handler registry by type
val handlers: Map[JsonType, Json => String] = Map(
  JsonType.ObjectType  -> (json => s"Processing object"),
  JsonType.ArrayType   -> (json => s"Processing array"),
  JsonType.StringType  -> (json => s"Processing string"),
  JsonType.NumberType  -> (json => s"Processing number"),
  JsonType.BooleanType -> (json => s"Processing boolean"),
  JsonType.NullType    -> (json => s"Processing null")
)

val json = Json.Number(42)
val action = handlers(json.jsonType)(json)  // "Processing number"
```

### Recursive Type Inspection

```scala mdoc:compile-only
import zio.blocks.schema.json.{Json, JsonType}

def summarize(json: Json): String = json.jsonType match {
  case JsonType.ObjectType =>
    json match {
      case obj: Json.Object =>
        val fields = obj.value.map(_._1).take(3)
        s"Object(${fields.mkString(", ")}${if obj.value.size > 3 then "..." else ""})"
      case _ => "Object"
    }
  case JsonType.ArrayType =>
    json match {
      case arr: Json.Array =>
        if arr.value.isEmpty then "Array()"
        else {
          val types = arr.value.map(_.jsonType).distinct
          s"Array(${types.map(_.toString).mkString("|")})"
        }
      case _ => "Array"
    }
  case JsonType.StringType => "String"
  case JsonType.NumberType => "Number"
  case JsonType.BooleanType => "Boolean"
  case JsonType.NullType => "Null"
}

println(summarize(Json.Object("a" -> Json.String("x"), "b" -> Json.Number(1))))
// "Object(a, b)"

println(summarize(Json.Array(Json.String("x"), Json.Number(1), Json.String("y"))))
// "Array(String|Number)"
```

## Conversion to/from Strings

### Type Name Representation

```scala mdoc:compile-only
import zio.blocks.schema.json.JsonType

// Get string representation
val typeName = JsonType.StringType.toString  // "StringType"

// Types can be identified by name
val allTypes = Seq(
  JsonType.ObjectType,
  JsonType.ArrayType,
  JsonType.StringType,
  JsonType.NumberType,
  JsonType.BooleanType,
  JsonType.NullType
)

val typeNames = allTypes.map(_.toString)
```

## Integration with Schema

`JsonType` integrates with the schema system for type-safe validation:

```scala mdoc:compile-only
import zio.blocks.schema._
import zio.blocks.schema.json.JsonType

case class User(name: String, age: Int)
object User { implicit val schema: Schema[User] = Schema.derived }

// Schema-based derivation handles type verification
val json = Json.Object(
  "name" -> Json.String("Alice"),
  "age" -> Json.Number(30)
)

// JsonType helps validate structure before decoding
val isValidShape = json.jsonType == JsonType.ObjectType
val decoded: Either[SchemaError, User] = json.as[User]
```

## Performance Characteristics

- **Zero allocation:** `JsonType` instances are singletons (case objects)
- **O(1) comparison:** Type checking via object equality
- **No pattern matching overhead:** Dispatching on type is extremely fast
- **Suitable for hot paths:** Use for critical filtering or routing logic

## Limitations and Notes

- `JsonType` only represents the type, not the value structure
- For complex type-dependent logic, combine with pattern matching on `Json` subtypes
- `JsonType` instances don't carry metadata (e.g., array length, object key count)
- For schema validation, use `Schema` and `Validation` instead of raw type checking
