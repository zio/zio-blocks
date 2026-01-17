# ZIO Blocks Schema BSON

BSON (Binary JSON) format support for ZIO Blocks Schema.

## Overview

This module provides BSON encoding and decoding capabilities for ZIO Blocks. BSON is a binary serialization format used by MongoDB and other systems. It extends JSON with additional data types and is designed to be efficient in both storage space and scan speed.

## Features

- **Complete Type Support**: All Scala primitive types, collections, case classes, sealed traits, and Java time types
- **MongoDB Compatible**: Full compatibility with MongoDB's BSON implementation
- **Type-Safe**: Leverages ZIO Blocks' type-safe schema derivation
- **Efficient**: Binary format optimized for performance
- **Automatic Derivation**: Codecs are automatically derived from your data types

## Supported Types

### Primitive Types
- `Unit`, `Boolean`, `Byte`, `Short`, `Int`, `Long`
- `Float`, `Double`, `Char`, `String`
- `BigInt`, `BigDecimal`
- `UUID`, `Currency`

### Java Time Types
- `Instant`, `Duration`, `Period`
- `LocalDate`, `LocalTime`, `LocalDateTime`
- `OffsetDateTime`, `OffsetTime`
- `ZonedDateTime`, `ZoneId`, `ZoneOffset`
- `Year`, `YearMonth`, `Month`, `MonthDay`, `DayOfWeek`

### Collections
- `List`, `Vector`, `Set`, `Map`
- `Option`, `Either`

### Complex Types
- Case classes (records)
- Sealed traits (variants/enums)
- Nested structures
- Recursive types

### MongoDB-Specific Types
- `ObjectId`
- `Decimal128` (via BigDecimal)
- Binary data

## Usage

### Basic Example

```scala
import zio.blocks.schema._
import zio.blocks.schema.bson.BsonFormat

case class Person(name: String, age: Int)
object Person {
  given Reflect[BsonFormat.type, Person] = Reflect.derive[BsonFormat.type, Person]
}

val person = Person("Alice", 30)

// Get the codec
val codec = summon[BsonFormat.TypeClass[Person]]

// Encode to BSON
val encoded: Array[Byte] = codec.encode(person)

// Decode from BSON
val decoded: Either[SchemaError, Person] = codec.decode(java.nio.ByteBuffer.wrap(encoded))
```

### Nested Structures

```scala
case class Address(street: String, city: String, zipCode: Int)
object Address {
  given Reflect[BsonFormat.type, Address] = Reflect.derive[BsonFormat.type, Address]
}

case class Employee(id: Long, person: Person, address: Address, salary: Double)
object Employee {
  given Reflect[BsonFormat.type, Employee] = Reflect.derive[BsonFormat.type, Employee]
}

val employee = Employee(
  id = 1001L,
  person = Person("Bob", 25),
  address = Address("123 Main St", "Springfield", 12345),
  salary = 75000.50
)

val codec = summon[BsonFormat.TypeClass[Employee]]
val encoded = codec.encode(employee)
val decoded = codec.decode(java.nio.ByteBuffer.wrap(encoded))
```

### Sealed Traits (Variants)

```scala
sealed trait Shape
object Shape {
  case class Circle(radius: Double) extends Shape
  case class Rectangle(width: Double, height: Double) extends Shape
  case class Triangle(base: Double, height: Double) extends Shape

  given Reflect[BsonFormat.type, Shape] = Reflect.derive[BsonFormat.type, Shape]
}

val shape: Shape = Shape.Circle(5.0)

val codec = summon[BsonFormat.TypeClass[Shape]]
val encoded = codec.encode(shape)
val decoded = codec.decode(java.nio.ByteBuffer.wrap(encoded))
```

### Collections

```scala
case class Library(name: String, books: List[String])
object Library {
  given Reflect[BsonFormat.type, Library] = Reflect.derive[BsonFormat.type, Library]
}

val library = Library("City Library", List("Book1", "Book2", "Book3"))

val codec = summon[BsonFormat.TypeClass[Library]]
val encoded = codec.encode(library)
val decoded = codec.decode(java.nio.ByteBuffer.wrap(encoded))
```

### Optional Fields

```scala
case class OptionalData(required: String, optional: Option[Int])
object OptionalData {
  given Reflect[BsonFormat.type, OptionalData] = Reflect.derive[BsonFormat.type, OptionalData]
}

val withValue = OptionalData("required", Some(42))
val withoutValue = OptionalData("required", None)

val codec = summon[BsonFormat.TypeClass[OptionalData]]
```

### Time Types

```scala
import java.time._

case class TimeData(
  instant: Instant,
  localDate: LocalDate,
  localTime: LocalTime,
  localDateTime: LocalDateTime
)
object TimeData {
  given Reflect[BsonFormat.type, TimeData] = Reflect.derive[BsonFormat.type, TimeData]
}

val timeData = TimeData(
  instant = Instant.now(),
  localDate = LocalDate.now(),
  localTime = LocalTime.now(),
  localDateTime = LocalDateTime.now()
)

val codec = summon[BsonFormat.TypeClass[TimeData]]
```

## BSON Format Details

### Variant Encoding

Sealed traits (variants) are encoded as BSON documents with two fields:
- `$type`: The name of the case (e.g., "Circle", "Rectangle")
- `$value`: The encoded value of that case

Example BSON for `Shape.Circle(5.0)`:
```json
{
  "$type": "Circle",
  "$value": {
    "radius": 5.0
  }
}
```

### Time Type Encoding

- `Instant`: Encoded as BSON DateTime (milliseconds since epoch)
- `LocalDate`, `LocalTime`, `LocalDateTime`: Encoded as BSON documents with component fields
- `Duration`, `Period`: Encoded as BSON documents with component fields
- `ZonedDateTime`: Encoded with both datetime and zone ID
- Other time types: Encoded appropriately based on their structure

### Collection Encoding

- Lists, Vectors, Sets: Encoded as BSON arrays
- Maps: Encoded as BSON documents (keys must be strings or convertible to strings)
- Options: `Some(value)` encoded as the value, `None` encoded as BSON null

## Error Handling

Decoding returns `Either[SchemaError, A]`:
- `Right(value)`: Successful decoding
- `Left(error)`: Decoding failed with detailed error information

```scala
val result: Either[SchemaError, Person] = codec.decode(buffer)
result match {
  case Right(person) => println(s"Decoded: $person")
  case Left(error) => println(s"Error: ${error.message}")
}
```

## Integration with MongoDB

This BSON implementation is fully compatible with MongoDB's BSON format. You can use it to:
- Serialize Scala types for MongoDB storage
- Deserialize MongoDB documents into Scala types
- Work with MongoDB drivers that use BSON

## Performance

BSON is a binary format designed for:
- **Efficient encoding/decoding**: Faster than text-based JSON
- **Compact storage**: Binary representation is more space-efficient
- **Fast traversal**: BSON documents include length prefixes for quick navigation

## Dependencies

```scala
libraryDependencies += "org.mongodb" % "bson" % "5.3.0"
```

## Testing

The module includes comprehensive tests covering:
- All primitive types
- Case classes and nested structures
- Sealed traits and variants
- Collections and optional values
- Java time types
- Edge cases and error handling

Run tests with:
```bash
sbt schema-bson/test
```

## Comparison with Other Formats

| Feature | BSON | JSON | Avro |
|---------|------|------|------|
| Binary | ✅ | ❌ | ✅ |
| Human Readable | ❌ | ✅ | ❌ |
| Schema Required | ❌ | ❌ | ✅ |
| MongoDB Compatible | ✅ | ⚠️ | ❌ |
| Rich Type Support | ✅ | ⚠️ | ✅ |
| Compact | ✅ | ❌ | ✅ |

## Contributing

Contributions are welcome! Please ensure:
- All tests pass
- New features include tests
- Code follows the project style
- Documentation is updated

## License

Apache License 2.0

## Related

- [ZIO Blocks](https://github.com/zio/zio-blocks)
- [ZIO Schema](https://github.com/zio/zio-schema)
- [BSON Specification](http://bsonspec.org/)
- [MongoDB BSON](https://www.mongodb.com/docs/manual/reference/bson-types/)
