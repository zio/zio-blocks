# ZIO Schema BSON

BSON support for ZIO Schema, enabling seamless serialization and deserialization of Scala types to and from BSON format.

## Installation

Add the following dependency to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-schema-bson" % "0.4.x" // Check for latest version
```

## Usage

### Basic Usage

You can derive a `BsonCodec` for any type that has a `Schema`.

```scala
import zio.schema.Schema
import zio.schema.bson.BsonSchemaCodec
import zio.bson._
import org.mongodb.scala.bson.BsonDocument

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived
  implicit val codec: BsonCodec[Person] = BsonSchemaCodec.bsonCodec(schema)
}

val person = Person("Alice", 30)
val doc: BsonDocument = person.toBsonDocument
val decoded: Either[String, Person] = doc.as[Person]
```

### Configuration

You can configure how the schema is mapped to BSON using `Modifier` annotations or by passing a `Config` object.

#### Discriminators for Sum Types

By default, sum types (sealed traits/enums) are encoded as a wrapper object: `{"ClassName": { ... fields ... }}`.
You can change this to use a discriminator field (e.g., `{"$type": "ClassName", ... fields ...}`) using `@Modifier.config`.

```scala
import zio.blocks.schema.Modifier

@Modifier.config("bson.discriminator", "$type")
sealed trait Pet
case class Dog(name: String) extends Pet
case class Cat(name: String) extends Pet
```

Or disable discriminators entirely (only for leaf nodes with unique fields):

```scala
@Modifier.config("bson.noDiscriminator", "true")
sealed trait Shape. // Encodes as just the fields
```

#### Field Renaming and Aliases

```scala
case class Legacy(
  @Modifier.rename("new_name") 
  @Modifier.alias("old_name")
  field: String
)
```

#### Transient Fields

```scala
case class User(
  name: String,
  @Modifier.transient
  cachedValue: Int = 0 // Will not be encoded/decoded
)
```

## Features

- **Full ZIO Schema Support**: specific support for Records, Enums, Sealed Traits, Collections (List, Set, Map), and Primitives.
- **BSON Types**: Correctly maps Scala types to BSON types (e.g., `Instant` to `BsonDateTime`, `UUID` to `BsonBinary`).
- **Customization**: Supports `classNameMapping`, `allowExtraFields`, and standard ZIO Schema modifiers.
- **Recursive Types**: Handles recursive data structures.

## Contributing

Please follow the ZIO standard contributing guidelines.
