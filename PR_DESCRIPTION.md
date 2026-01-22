## Summary

Resolves #519

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

**Core Implementation (1,999 lines):**
- `schema/shared/src/main/scala/zio/blocks/schema/migration/Migration.scala` (125 lines)
- `schema/shared/src/main/scala/zio/blocks/schema/migration/DynamicMigration.scala` (127 lines)
- `schema/shared/src/main/scala/zio/blocks/schema/migration/MigrationAction.scala` (570 lines)
- `schema/shared/src/main/scala/zio/blocks/schema/migration/MigrationBuilder.scala` (168 lines)
- `schema/shared/src/main/scala/zio/blocks/schema/migration/MigrationError.scala` (105 lines)
- `schema/shared/src/main/scala/zio/blocks/schema/migration/SchemaExpr.scala` (153 lines)
- `schema/shared/src/main/scala-2/zio/blocks/schema/migration/MigrationBuilderMacros.scala` (95 lines)
- `schema/shared/src/main/scala-2/zio/blocks/schema/migration/MigrationBuilderSyntax.scala` (300 lines)
- `schema/shared/src/main/scala-3/zio/blocks/schema/migration/MigrationBuilderMacros.scala` (99 lines)
- `schema/shared/src/main/scala-3/zio/blocks/schema/migration/MigrationBuilderSyntax.scala` (248 lines)

**Tests (1,506 lines):**
- `schema/shared/src/test/scala/zio/blocks/schema/migration/MigrationSpec.scala` (594 lines)
- `schema/shared/src/test/scala/zio/blocks/schema/migration/MigrationActionSpec.scala` (515 lines)
- `schema/shared/src/test/scala/zio/blocks/schema/migration/MigrationPropertySpec.scala` (272 lines)
- `schema/shared/src/test/scala/zio/blocks/schema/migration/MigrationBuilderSpec.scala` (125 lines)

**Documentation (1,157 lines):**
- `docs/reference/migration.md` (1,157 lines)

**Total: 4,662 lines**

## Example Usage

```scala
// Define schemas
case class UserV1(name: String, age: Int)
case class UserV2(firstName: String, lastName: String, age: Int, email: String)

// Create migration
val migration = MigrationBuilder(
  Schema.derived[UserV1],
  Schema.derived[UserV2]
)
  .splitField("name", Vector("firstName", "lastName"), " ")
  .addFieldWithDefault("email", "unknown@example.com")
  .build

// Apply migration
val user = UserV1("Alice Smith", 30)
val result = migration.apply(user)
// Right(UserV2("Alice", "Smith", 30, "unknown@example.com"))

// Reverse migration
val reversed = migration.reverse.apply(result.toOption.get)
// Right(UserV1("Alice Smith", 30))
```

## Addresses Issue

Closes #519

