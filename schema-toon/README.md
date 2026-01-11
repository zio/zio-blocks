# ZIO Blocks Schema TOON

TOON (Textual Object-Oriented Notation) is a human-readable data serialization format designed for clarity and editability. It uses indentation for structure and colons for key-value pairs, making it more readable than JSON for complex nested data.

## Installation

Add the following to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema-toon" % "version"
```

## Quick Start

```scala
import zio.blocks.schema.Schema
import zio.blocks.schema.toon.ToonFormat

// Define your data model
case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

// Derive a codec
val codec = Person.schema.derive(ToonFormat.deriver)

// Encode to TOON
val toon = codec.encodeToString(Person("Alice", 30))
// Output:
// name: Alice
// age: 30
```

## TOON Format Syntax

### Key-Value Pairs

```
fieldName: value
```

### Strings

Simple strings are unquoted:
```
name: Alice
city: Springfield
```

Strings with special characters are quoted:
```
message: "Hello, World!"
path: "C:\\Users\\Alice"
```

### Numbers

```
count: 42
price: 19.99
big: 123456789012345678901234567890
```

### Booleans

```
active: true
deleted: false
```

### Arrays (Inline Format)

For simple primitive arrays:
```
[3]: 1,2,3
```

### Arrays (List Format)

For complex objects:
```
[2]:
  - name: Alice
    age: 30
  - name: Bob
    age: 25
```

### Nested Objects

```
name: Alice
age: 30
address:
  street: 123 Main St
  city: Springfield
  zip: 12345
```

### Variants (Sealed Traits)

```
Cat:
  name: Whiskers
  lives: 9
```

### Maps

```
a: 1
b: 2
c: 3
```

## Configuration

### Customizing the Deriver

```scala
import zio.blocks.schema.toon.{ToonBinaryCodecDeriver, ArrayFormat}
import zio.blocks.schema.json.NameMapper

val customDeriver = ToonBinaryCodecDeriver
  .withFieldNameMapper(NameMapper.SnakeCase)     // user_name instead of userName
  .withCaseNameMapper(NameMapper.KebabCase)      // my-case instead of MyCase
  .withArrayFormat(ArrayFormat.List)              // Force list format for arrays
  .withTransientNone(true)                        // Omit None values
  .withTransientEmptyCollection(true)             // Omit empty collections
  .withTransientDefaultValue(true)                // Omit default values

val codec = Person.schema.derive(customDeriver)
```

### Configuration Options

| Option | Default | Description |
|--------|---------|-------------|
| `fieldNameMapper` | `Identity` | Transform field names (e.g., `SnakeCase`, `CamelCase`) |
| `caseNameMapper` | `Identity` | Transform variant case names |
| `arrayFormat` | `Auto` | Array format (`Auto`, `Inline`, `List`, `Tabular`) |
| `transientNone` | `true` | Omit `None` values when encoding |
| `transientEmptyCollection` | `true` | Omit empty collections when encoding |
| `transientDefaultValue` | `true` | Omit fields with default values |
| `rejectExtraFields` | `false` | Error on unknown fields during decoding |
| `discriminatorKind` | `Key` | How to encode variant type discriminators |

### Array Formats

```scala
// Auto (default) - primitives inline, complex as list
ArrayFormat.Auto

// Inline - comma-separated on single line
// [3]: 1,2,3
ArrayFormat.Inline

// List - each element on new line with -
// [2]:
//   - item1
//   - item2
ArrayFormat.List

// Tabular - records as rows
// [2]:
//   field1,field2
//   field1,field2
ArrayFormat.Tabular
```

## Comparison with JSON

| Feature | TOON | JSON |
|---------|------|------|
| Readability | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| Compactness | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| Human Editing | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| Machine Parsing | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| Nested Data | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| Comments | Planned | ❌ |

### Example Comparison

**JSON:**
```json
{
  "name": "Alice",
  "age": 30,
  "address": {
    "street": "123 Main St",
    "city": "Springfield"
  },
  "tags": ["developer", "designer"]
}
```

**TOON:**
```
name: Alice
age: 30
address:
  street: 123 Main St
  city: Springfield
tags[2]: developer,designer
```

## Type Support

### Primitives
- `Unit`, `Boolean`
- `Int`, `Long`, `Float`, `Double`
- `BigInt`, `BigDecimal`
- `String`

### Java Time
- `Instant`, `Duration`, `Period`
- `LocalDate`, `LocalTime`, `LocalDateTime`
- `OffsetDateTime`, `ZonedDateTime`

### Collections
- `List`, `Vector`, `Set`, `Seq`
- `Map[String, V]`

### ADTs
- Case classes (records)
- Sealed traits (variants)
- Case objects

## Advanced Usage

### Working with Writer/Reader Directly

```scala
import zio.blocks.schema.toon.{ToonWriter, ToonReader, ToonWriterConfig}

// Writing
val writer = ToonWriter(ToonWriterConfig.default)
writer.writeRaw("name: ")
writer.writeRaw("Alice")
writer.newLine()
writer.writeRaw("age: ")
writer.writeRaw("30")
val result = writer.toString
// name: Alice
// age: 30

// Reading
val reader = ToonReader("name: Alice\nage: 30")
val name = reader.readKey()  // "name"
reader.expectColon()
val value = reader.readString()  // "Alice"
```

### Custom Codecs

```scala
import zio.blocks.schema.toon.ToonBinaryCodec

// Define a custom codec
val myCodec = new ToonBinaryCodec[MyType] {
  def encodeValue(value: MyType, writer: ToonWriter): Unit = {
    writer.writeRaw(value.toString)
  }
  
  def decodeValue(reader: ToonReader, default: MyType): MyType = {
    val str = reader.readString()
    MyType.parse(str)
  }
}
```

## Running Tests

```bash
sbt schema-toon/test
```

## Current Limitations

- Decoding is not yet fully implemented (encoding-only for now)
- Comments are not yet supported
- Multi-line strings use escape sequences

## License

Apache 2.0

## Contributing

Contributions are welcome! Please see the main ZIO Blocks repository for contribution guidelines.
