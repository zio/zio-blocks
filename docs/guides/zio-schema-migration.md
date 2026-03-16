# ZIO Schema Migration Guide

## Overview

ZIO Schema Migration provides a powerful system for migrating between different versions of schemas. This is particularly useful for:

- **Data Evolution**: Handling changes in data structures over time
- **Version Compatibility**: Ensuring backward and forward compatibility
- **Transformation Pipelines**: Converting data between different schema versions

## Installation

Add the following dependency to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-schema-migration" % "<version>"
```

## Basic Concepts

### Schema Version

A `SchemaVersion` represents a specific version of a schema:

```scala
import zio.blocks.schema._

case class SchemaVersion(major: Int, minor: Int, patch: Int) {
  def >=(other: SchemaVersion): Boolean =
    major > other.major ||
    (major == other.major && minor > other.minor) ||
    (major == other.major && minor == other.minor && patch >= other.patch)
}
```

### Migration

A `Migration` defines how to transform data from one schema version to another:

```scala
trait Migration[From, To] {
  def fromVersion: SchemaVersion
  def toVersion: SchemaVersion
  def migrate(value: From): Either[MigrationError, To]
}
```

### MigrationPath

A `MigrationPath` represents a sequence of migrations to reach a target version:

```scala
case class MigrationPath[From, To](migrations: List[Migration[?, ?]])
```

## Defining Migrations

### Simple Field Migration

```scala
import zio.blocks.schema.migration._

case class UserV1(name: String, age: Int)
case class UserV2(name: String, age: Int, email: Option[String])

val userMigration: Migration[UserV1, UserV2] = Migration
  .from[UserV1]
  .to[UserV2]
  .field(_.email, _.withDefault(None))
  .build
```

### Complex Migration with Transformation

```scala
case class AddressV1(street: String, city: String)
case class AddressV2(street: String, city: String, country: String, postalCode: String)

val addressMigration: Migration[AddressV1, AddressV2] = Migration
  .from[AddressV1]
  .to[AddressV2]
  .field(_.country, _.withDefault("Unknown"))
  .field(_.postalCode, _.withDefault(""))
  .build
```

## Migration Registry

Register migrations in a `MigrationRegistry`:

```scala
import zio.blocks.schema.migration.MigrationRegistry

val registry = MigrationRegistry.empty
  .register(userMigration)
  .register(addressMigration)
```

## Running Migrations

### Direct Migration

```scala
val result: Either[MigrationError, UserV2] = 
  userMigration.migrate(UserV1("John", 30))
```

### Registry-Based Migration

```scala
val result: Either[MigrationError, UserV2] = 
  registry.migrate[UserV1, UserV2](UserV1("John", 30))
```

## Automatic Schema Diff

ZIO Schema Migration can automatically detect differences between schema versions:

```scala
import zio.blocks.schema.migration.SchemaDiff

val diff: SchemaDiff = SchemaDiff.between[UserV1, UserV2]
println(diff.changes)
// Output: List(AddedField(email), RenamedField(...), ...)
```

## Best Practices

1. **Semantic Versioning**: Use semantic versioning for schema versions
2. **Backward Compatibility**: Design migrations to be backward compatible when possible
3. **Default Values**: Provide sensible defaults for new fields
4. **Testing**: Write comprehensive tests for all migrations
5. **Documentation**: Document all schema changes and migration strategies

## Error Handling

```scala
sealed trait MigrationError
object MigrationError {
  case class MissingField(name: String) extends MigrationError
  case class TypeMismatch(expected: String, actual: String) extends MigrationError
  case class InvalidValue(field: String, value: Any) extends MigrationError
  case class MigrationNotFound(from: SchemaVersion, to: SchemaVersion) extends MigrationError
}
```

## Integration with ZIO Schema

The migration system integrates seamlessly with ZIO Schema:

```scala
import zio.blocks.schema._
import zio.blocks.schema.codec._

// Define schemas
implicit val userV1Schema: Schema[UserV1] = DeriveSchema.gen[UserV1]
implicit val userV2Schema: Schema[UserV2] = DeriveSchema.gen[UserV2]

// Use with codecs
val jsonCodecV1 = JsonCodec.schemaBased(userV1Schema)
val jsonCodecV2 = JsonCodec.schemaBased(userV2Schema)
```

## Complete Example

```scala
import zio._
import zio.blocks.schema._
import zio.blocks.schema.migration._

object MigrationExample extends ZIOAppDefault {
  
  // Define schema versions
  case class OrderV1(id: String, items: List[String], total: Double)
  case class OrderV2(
    id: String, 
    items: List[OrderV2.Item], 
    total: Double,
    currency: String,
    createdAt: java.time.Instant
  )
  object OrderV2 {
    case class Item(name: String, quantity: Int, price: Double)
  }

  // Define migration
  val orderMigration: Migration[OrderV1, OrderV2] = Migration
    .from[OrderV1]
    .to[OrderV2]
    .transformField(_.items, oldItems => oldItems.map(name => OrderV2.Item(name, 1, 0.0)))
    .field(_.currency, _.withDefault("USD"))
    .field(_.createdAt, _.withDefault(java.time.Instant.now()))
    .build

  def run = for {
    orderV1 <- ZIO.succeed(OrderV1("order-1", List("Apple", "Banana"), 10.0))
    result <- ZIO.fromEither(orderMigration.migrate(orderV1))
    _ <- Console.printLine(s"Migrated order: $result")
  } yield ()
}
```

## See Also

- [Schema Reference](../reference/schema.md)
- [Schema Derivation](./schema-derivation.md)
- [Codec Guide](./codecs.md)
