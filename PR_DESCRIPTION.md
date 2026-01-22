## Summary

**Resolves #519** - Schema Migration System for ZIO Schema 2

/claim #519

This PR implements a comprehensive schema migration system for ZIO Schema 2, addressing issue #519. The implementation provides pure, serializable migrations that enable safe, reversible transformations of data structures as schemas evolve over time.

## Key Features

- **Pure Data Migrations**: Migrations are represented as data structures rather than functions, enabling serialization, introspection, and automatic reversal
- **Type-Safe API**: Compile-time checking with schemas ensures correctness
- **Deep Nesting Support**: Navigate and transform arbitrarily nested structures up to 7+ levels deep
- **Comprehensive Action Set**: 14 migration action types covering records, variants, and collections
- **Automatic Reversal**: Every migration has a structural inverse that can be generated automatically
- **Composable**: Combine migrations using the `++` operator for complex transformations
- **Rich Error Reporting**: Detailed path-based error messages for debugging

## Implementation Details

### Core Architecture

The migration system is built on three layers:

1. **Migration[A, B]**: Type-safe wrapper with source and target schemas
2. **DynamicMigration**: Untyped core that operates on DynamicValue
3. **MigrationAction**: Individual transformation actions (14 types)

### Migration Actions

**Record Actions:**
- AddField: Add new fields with default values
- DropField: Remove existing fields
- Rename: Rename fields
- Optionalize: Make mandatory fields optional
- Mandate: Make optional fields mandatory
- ChangeType: Change field types with transformations
- TransformValue: Transform field values
- Join: Combine multiple fields into one
- Split: Split one field into multiple

**Variant Actions:**
- RenameCase: Rename variant cases
- TransformCase: Transform case values

**Collection Actions:**
- TransformElements: Transform sequence elements
- TransformKeys: Transform map keys
- TransformValues: Transform map values

### Builder API

The MigrationBuilder provides a fluent API for constructing migrations:

```scala
val migration = MigrationBuilder(sourceSchema, targetSchema)
  .renameField("name", "fullName")
  .addFieldWithDefault("email", "unknown@example.com")
  .build
```

All builder methods use only `build()` (not `buildPartial()`) as specified in the requirements.

## Testing

Comprehensive test suite with 62 tests covering:

- All 14 migration action types
- Flat structure migrations
- Nested migrations (2-3 levels)
- Deep nested migrations (4-7 levels)
- Round-trip migrations (reversibility)
- Error cases with detailed error messages
- Composition and chaining
- Property-based tests (reversibility, composition, identity, idempotence)

All tests pass successfully.

## Documentation

Complete documentation following the zio-blocks style:

- Overview and motivation
- Core concepts and architecture
- Complete API reference for all action types
- Real-world examples (user evolution, API responses, database migrations, event sourcing, configuration)
- Advanced patterns (bidirectional migrations, conditional migrations, migration chains, versioned data storage)
- Testing strategies (unit tests, property-based tests, integration tests)
- Best practices and guidelines
- Performance considerations
- Common pitfalls and solutions
- Comparison with other approaches

## Files Changed

**Core Implementation:**
- `schema/shared/src/main/scala/zio/blocks/schema/migration/Migration.scala` (122 lines)
- `schema/shared/src/main/scala/zio/blocks/schema/migration/DynamicMigration.scala` (127 lines)
- `schema/shared/src/main/scala/zio/blocks/schema/migration/MigrationAction.scala` (550 lines)
- `schema/shared/src/main/scala/zio/blocks/schema/migration/MigrationBuilder.scala` (168 lines)
- `schema/shared/src/main/scala/zio/blocks/schema/migration/SchemaExpr.scala` (153 lines)
- `schema/shared/src/main/scala-2/zio/blocks/schema/migration/MigrationBuilderMacros.scala` (95 lines)
- `schema/shared/src/main/scala-2/zio/blocks/schema/migration/MigrationBuilderSyntax.scala` (300 lines)
- `schema/shared/src/main/scala-3/zio/blocks/schema/migration/MigrationBuilderMacros.scala` (99 lines)
- `schema/shared/src/main/scala-3/zio/blocks/schema/migration/MigrationBuilderSyntax.scala` (248 lines)
- `schema/shared/src/main/scala/zio/blocks/schema/SchemaError.scala` (extended with migration errors)

**Tests (1,662 lines):**
- `schema/shared/src/test/scala/zio/blocks/schema/migration/MigrationSpec.scala` (679 lines)
- `schema/shared/src/test/scala/zio/blocks/schema/migration/MigrationActionSpec.scala` (566 lines)
- `schema/shared/src/test/scala/zio/blocks/schema/migration/MigrationPropertySpec.scala` (297 lines)
- `schema/shared/src/test/scala/zio/blocks/schema/migration/MigrationBuilderSpec.scala` (120 lines)

## Example Usage

```scala
// Define schemas
case class PersonV1(name: String, age: Int)
case class PersonV2(fullName: String, age: Int, country: String)

// Create migration
val migration = MigrationBuilder(
  Schema.derived[PersonV1],
  Schema.derived[PersonV2]
)
  .renameField("name", "fullName")
  .addFieldWithDefault("country", "USA")
  .build

// Apply migration
val person = PersonV1("Alice", 30)
val result = migration.apply(person)
// Right(PersonV2("Alice", 30, "USA"))

// Reverse migration
val reversed = migration.reverse.apply(result.toOption.get)
// Right(PersonV1("Alice", 30))
```

## Addresses Issue

Closes #519

