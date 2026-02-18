# ZIO Blocks Schema Iron

Integration between ZIO Blocks Schema and [Iron](https://github.com/Iltotore/iron) for type-safe refinement types.

## Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema-iron" % "0.0.1"
```

## Usage

```scala
import zio.blocks.schema.*
import zio.blocks.schema.iron.given
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.*

case class Person(name: String, age: Int :| Positive)

object Person:
  given Schema[Person] = Schema.derived

// Now you can use Person with any format
val jsonCodec = Schema[Person].derive(JsonFormat)

// Decoding with validation
val validJson = """{"name":"Alice","age":25}"""
val invalidJson = """{"name":"Bob","age":-5}"""

jsonCodec.decode(validJson.getBytes)   // Right(Person(Alice,25))
jsonCodec.decode(invalidJson.getBytes) // Left(SchemaError: Should be strictly positive at: .age)
```

The integration automatically derives `Schema[A :| C]` from `Schema[A]` with runtime validation using Iron's constraints.

## Known Limitations

Due to how ZIO Blocks handles opaque types in derived schemas, encoding refined types may not work correctly in all cases. Decoding and validation work as expected.
