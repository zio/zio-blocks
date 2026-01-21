# ZIO Schema MessagePack

MessagePack support for [ZIO Schema](https://github.com/zio/zio-schema). This module provides a high-performance, forward-compatible binary codec for serializing and deserializing ZIO Schema types to and from MessagePack format.

## Installation

Add the following dependency to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-schema-messagepack" % "x.x.x" // Use latest version
```

## Usage

### Basic Encoding and Decoding

You can derive a MessagePack codec for any type that has a `Schema`.

```scala
import zio.blocks.schema.Schema
import zio.blocks.schema.messagepack.MessagePackFormat

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val codec = Schema[Person].derive(MessagePackFormat.deriver)

// Encode
val person = Person("Alice", 30)
val bytes: Chunk[Byte] = codec.encode(person)

// Decode
val decoded: Either[DecodeError, Person] = codec.decode(bytes)
```

### Forward Compatibility

The Record decoder is designed for forward compatibility. It:
1.  **Skips unknown fields**: If the encoded data contains fields not present in your case class (e.g., from a newer version of the producer), they are safely ignored.
2.  **Order Independent**: Fields are decoded by name (using MessagePack map format), so the order of fields in the binary does not matter.

### Custom Validation (e.g. Email)

You can use `Schema.wrap` to enforce validation logic while staying serialization-friendly:

```scala
case class Email(value: String)
object Email {
  private val emailRegex = "^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$".r
  
  implicit val schema: Schema[Email] = Schema.derived[Email].wrap[String](
    str => if (emailRegex.matches(str)) Right(Email(str)) else Left(s"Invalid email: $str"),
    email => email.value
  )
}
// Email will be serialized as a plain String, but validated on decode.
```

## Security (DoS Protection)

This module includes built-in protection against Denial of Service attacks:
- **`maxCollectionSize`**: Limits collections (Map, List, Array settings) to 10,000,000 elements to prevent OOM.
- **`maxBinarySize`**: Limits binary blobs and strings to 100MB.

## recursive Types

Recursive data structures (like unrelated JSON trees or linked lists) are fully supported using a thread-safe caching mechanism.
