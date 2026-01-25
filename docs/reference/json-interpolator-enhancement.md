# Enhanced JSON String Interpolator

Closes #801

## Overview

The `json""` interpolator now supports type-safe interpolation across three contexts — key position, value position, and string literals. It validates types at compile time and provides clear error messages.

## Interpolation Contexts

### Key Position
Values used as JSON object keys must be "stringable":
```scala
val userId = 42
json"""{ $userId: "data" }"""  // → {"42": "data"}
```

### Value Position
Any type can be used as a JSON value:
```scala
val count = 42
json"""{"count": $count}"""  // → {"count": 42}
```

### String Literal
Stringable values can be embedded inside JSON strings:
```scala
val name = "Alice"
json"""{"greeting": "Hello, $name!"}"""  // → {"greeting": "Hello, Alice!"}
```

## Stringable Types

| Category | Types |
|----------|-------|
| Primitives | `Unit`, `Boolean`, `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `Char`, `String` |
| Big Numbers | `BigInt`, `BigDecimal` |
| Java Time | `DayOfWeek`, `Duration`, `Instant`, `LocalDate`, `LocalDateTime`, `LocalTime`, `Month`, `MonthDay`, `OffsetDateTime`, `OffsetTime`, `Period`, `Year`, `YearMonth`, `ZoneId`, `ZoneOffset`, `ZonedDateTime` |
| Java Util | `Currency`, `UUID` |

## Examples

### Dynamic Keys
```scala
val timestamp = Instant.now()
json"""{ $timestamp: "event occurred" }"""
// {"2024-01-15T10:30:00Z": "event occurred"}
```

### String Building
```scala
val version = 3
val env = "prod"
json"""{"path": "/api/$env/v$version/users"}"""
// {"path": "/api/prod/v3/users"}
```

### Mixed Contexts
```scala
val id = UUID.randomUUID()
val data = Json.obj("value" -> Json.number(42))
val timestamp = Instant.now()

json"""{
  $id: {
    "data": $data,
    "note": "Created at $timestamp"
  }
}"""
```

## Compile-Time Safety

```scala
case class Point(x: Int, y: Int)
val p = Point(1, 2)

json"""{ $p: "value" }"""       // ❌ Error: Point cannot be used in key position
json"""{"msg": "Point is $p"}"""  // ❌ Error: Point cannot be in string literal
json"""{"point": $p}"""          // ✅ Works in value position
```

## Changes

| File | Description |
|------|-------------|
| `Stringable.scala` | Type class with 28 implicit instances |
| `JsonInterpolatorRuntime.scala` | Context-aware value writing |
| `json/package.scala` (×4) | Macro updates for all platforms |
| `JsonInterpolatorSpec.scala` | 29 new tests |

## Notes

- Backward compatible with existing code
- Works on JVM, JS, and Native
- Supports Scala 2.13 and 3.3+
- Special characters are properly escaped
