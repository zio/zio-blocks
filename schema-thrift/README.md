# schema-thrift

Apache Thrift binary codec for ZIO Schema 2.

## Key Features

- **Field ID-based decoding** - Correctly decodes fields by Thrift field ID, not position
- **Schema evolution support** - Unknown fields are gracefully skipped
- **Out-of-order field handling** - Fields can arrive in any order on the wire
- **Compatible with Apache Thrift 0.22.0**

## Usage

```scala
import zio.blocks.schema._
import zio.blocks.schema.thrift.ThriftFormat
import java.nio.ByteBuffer

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Encode
val buffer = ByteBuffer.allocate(1024)
Schema[Person].encode(ThriftFormat)(buffer)(Person("Alice", 30))
buffer.flip()

// Decode
val result: Either[SchemaError, Person] = Schema[Person].decode(ThriftFormat)(buffer)
```

## Supported Types

- **Primitives**: Unit, Boolean, Byte, Short, Int, Long, Float, Double, Char, String
- **Numeric**: BigInt, BigDecimal
- **Date/Time**: Instant, LocalDate, LocalTime, LocalDateTime, OffsetTime, OffsetDateTime, ZonedDateTime, ZoneId, ZoneOffset, DayOfWeek, Month, Year, etc.
- **Collections**: List, Vector, Set, Map
- **Other**: Option, Either, UUID, Currency
- **Composite**: Records (case classes), Variants (sealed traits), Wrappers
- **Recursive**: Self-referencing types (e.g., tree structures)

## Dependencies

```scala
libraryDependencies += "org.apache.thrift" % "libthrift" % "0.22.0"
```
