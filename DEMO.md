# Schema Migration System Demo

## Video Script for PR #519

### 1. Introduction (10 seconds)
"This PR implements a pure algebraic migration system for ZIO Schema 2, as specified in issue #519."

### 2. Show the Implementation (20 seconds)
```
- Open Migration.scala
- Highlight MigrationAction sealed trait
- Show the 5 action types: AddField, DropField, Rename, SetValue, Optionalize
- Point out that all actions are reversible
```

### 3. Live Demo in SBT Console (60 seconds)

```scala
// Start sbt console
sbt schemaJVM/console

// Import the migration package
import zio.blocks.schema._
import zio.blocks.schema.migration._

// Create a sample record (simulating old schema)
val oldPerson = DynamicValue.Record(Vector(
  "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
  "lastName" -> DynamicValue.Primitive(PrimitiveValue.String("Doe"))
))

// Create a migration: rename firstName -> name, add age field
val migration = DynamicMigration.renameField("firstName", "name") ++
  DynamicMigration.dropField("lastName", DynamicValue.Primitive(PrimitiveValue.String(""))) ++
  DynamicMigration.addField("age", DynamicValue.Primitive(PrimitiveValue.Int(0)))

// Apply the migration
val newPerson = migration(oldPerson)
// Result: Right(Record(Vector(name -> "John", age -> 0)))

// Reverse the migration
val reversed = migration.reverse(newPerson.toOption.get)
// Result: Right(Record(Vector(firstName -> "John", lastName -> "")))

// Nested migration example
val company = DynamicValue.Record(Vector(
  "name" -> DynamicValue.Primitive(PrimitiveValue.String("Acme")),
  "ceo" -> DynamicValue.Record(Vector(
    "name" -> DynamicValue.Primitive(PrimitiveValue.String("Jane"))
  ))
))

// Add field to nested record
val nestedMigration = DynamicMigration.addFieldAt(
  DynamicOptic.root.field("ceo"),
  "title",
  DynamicValue.Primitive(PrimitiveValue.String("CEO"))
)

nestedMigration(company)
// Result: Right(Record with ceo.title = "CEO")
```

### 4. Run Tests (15 seconds)
```bash
sbt "schemaJVM/testOnly *MigrationSpec*"
# Show: 10 tests passed. 0 tests failed.
```

### 5. Show Full Test Suite Still Passes (10 seconds)
```bash
sbt schemaJVM/test
# Show: 2180 tests passed. 0 tests failed.
```

### 6. Key Features Summary (15 seconds)
- ✅ Pure data migrations (no closures)
- ✅ Fully serializable (can be stored in registries)
- ✅ Path-based actions via DynamicOptic
- ✅ Reversible migrations
- ✅ Composable with ++ operator
- ✅ Identity and associativity laws verified

---

## Quick Demo Commands

```bash
# Run in /tmp/zio-blocks

# 1. Compile
sbt schemaJVM/compile

# 2. Run migration tests
sbt "schemaJVM/testOnly *MigrationSpec*"

# 3. Run full test suite
sbt schemaJVM/test

# 4. Interactive demo
sbt schemaJVM/console
```

Then paste:
```scala
import zio.blocks.schema._
import zio.blocks.schema.migration._

val record = DynamicValue.Record(Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))))
val migration = DynamicMigration.addField("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
migration(record)
```
