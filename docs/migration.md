# Migration System

The migration system allows you to define schema migrations between different versions of your data models.

## Example

```scala
import zio.blocks.schema.migration._
import zio.blocks.schema.Schema

case class PersonV1(name: String, age: Int)
case class PersonV2(fullName: String, yearsOld: Int)

implicit val schemaV1: Schema[PersonV1] = Schema.derived
implicit val schemaV2: Schema[PersonV2] = Schema.derived

val migration = Migration.newBuilder[PersonV1, PersonV2]
  .renameField(_.name, _.fullName)
  .renameField(_.age, _.yearsOld)
  .buildPartial

val personV1 = PersonV1("John", 30)
val dynamicV1 = schemaV1.toDynamicValue(personV1)
val dynamicV2 = migration.dynamicMigration.apply(dynamicV1).getOrElse(dynamicV1)
val personV2 = schemaV2.fromDynamicValue(dynamicV2).get
// personV2 == PersonV2("John", 30)
```

## Low-level API

For more control, use the low-level API:

```scala
val migration = Migration(
  DynamicMigration(Vector(
    MigrationAction.RenameField(DynamicOptic.root.field("name"), "fullName"),
    MigrationAction.RenameField(DynamicOptic.root.field("age"), "yearsOld")
  )),
  schemaV1,
  schemaV2
)
```

## Migration Actions

The system supports the following migration actions:

- **RenameField**: Renames a field from one name to another
- **Optionalize**: Makes a field optional
- **Mandate**: Makes an optional field required
- **Join**: Combines multiple fields into one
- **Split**: Splits one field into multiple

Each action has a corresponding reverse operation for bidirectional migrations.

## JSON Serialization

Migrations can be serialized to JSON for storage and transmission (Scala 3+ only):

```scala
import zio.json._

val json = migration.dynamicMigration.toJson
val deserialized = json.fromJson[DynamicMigration]
```

## Validation

The migration system validates that migrations are compatible with the source and target schemas, providing clear error messages for invalid configurations.