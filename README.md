# zio-blocks
Powerful, joyful building blocks for modern cloud-native applications.

## Features

- **Schema System**: Type-safe schema definitions with automatic derivation
- **Migration System**: Schema migrations between data model versions (see [docs/migration.md](docs/migration.md))
- **JSON Codec**: High-performance JSON encoding/decoding
- **Avro Codec**: Avro serialization support
- **Optics**: Functional lenses and prisms for data manipulation

### Low-level API

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
