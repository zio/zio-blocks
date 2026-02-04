# Schema Migration System - Preview Video Script

## Overview
This document provides the script and instructions for recording a 5-7 minute demo video showcasing the Schema Migration System for ZIO Blocks Schema 2.

## Prerequisites
- OBS Studio or asciinema installed
- sbt and JDK 11+ configured
- Project built and ready

## Recording Setup
1. Open terminal in `c:\Users\Ahmed\Desktop\zio-blocks`
2. Set up environment:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-11.0.29.8-hotspot"
   $env:Path = "$env:JAVA_HOME\bin;C:\Program Files (x86)\sbt\bin;$env:Path"
   $env:SBT_OPTS="-Dsbt.supershell=false"
   ```

## Video Script & Timestamps

### 0:00-0:30 - Introduction
**Narration:** "Welcome to the ZIO Blocks Schema Migration System demo. This system enables pure, algebraic schema migrations between versions - fully serializable, reversible, and type-safe."

**Show:** The docs/reference/migration.md file briefly.

### 0:30-1:30 - Schema Versions Definition
**Narration:** "Let's look at a real example. We have three versions of a User schema evolving over time."

**Show:** examples/src/main/scala/MigrationDemo.scala around lines 27-38

```scala
// Version 1: Simple user schema
case class UserV1(name: String, age: Int)

// Version 2: Added email field  
case class UserV2(name: String, age: Int, email: Option[String])

// Version 3: Renamed 'name' to 'fullName', added 'verified' field
case class UserV3(fullName: String, age: Int, email: Option[String], verified: Boolean)
```

### 1:30-2:30 - Building Migrations
**Narration:** "We build migrations using MigrationBuilder with a fluent API. Each migration is a series of actions like addField, renameField, etc."

**Show:** The migration builder code (lines 51-75 in MigrationDemo.scala)

```scala
val v1ToV2 = MigrationBuilder[UserV1, UserV2]
  .addField(paths.field("email"), DynamicSchemaExpr.Literal(...))
  .buildPartial

val v2ToV3 = MigrationBuilder[UserV2, UserV3]
  .renameField(paths.field("name"), paths.field("fullName"))
  .addField(paths.field("verified"), ...)
  .buildPartial
```

### 2:30-3:30 - Running the Demo
**Narration:** "Let's run the demo and see the migration in action."

**Run:**
```powershell
sbt "examples/runMain zio.blocks.schema.migration.demo.MigrationDemo"
```

**Highlight output showing:**
- Original V1 user: `UserV1(John Doe, 30)`
- After V1 -> V2: Added email field
- After V2 -> V3: Renamed name to fullName, added verified field

### 3:30-4:30 - Migration Composition & Reversal
**Narration:** "Migrations can be composed with ++ and reversed with .reverse"

**Show terminal output:**
- `Composed migration V1 -> V3 = V1 -> V2 ++ V2 -> V3`
- `Reversed back to V1: Right(Record(...))`
- `Matches original? true`

### 4:30-5:30 - Value Transformations
**Narration:** "The system also supports value transformations using DynamicSchemaExpr - including arithmetic, string operations, and type coercion."

**Show terminal output:**
- Age doubling: `age: 25` → `age: 50`
- String concat: `Hello, Alice`
- Type coercion: Int 42 → String "42"

### 5:30-6:00 - Migration Actions Overview
**Narration:** "The system supports a comprehensive set of actions..."

**Show:** MigrationAction.scala or docs/reference/migration.md table:
- AddField, DropField, RenameField
- TransformValue, Mandate, Optionalize
- ChangeType, Join, Split
- RenameCase, TransformCase
- TransformElements, TransformKeys, TransformValues

### 6:00-6:30 - Validation & Serialization
**Narration:** "Builder validation checks structural changes like Join and Split, and migrations serialize cleanly for storage and tooling."

**Show:** MigrationValidator Join/Split handling and MigrationSchemasSerializationSpec.scala.

### 6:30-7:00 - Conclusion
**Narration:** "The Schema Migration System provides:
- Type-safe migrations between schema versions
- Pure data structures - fully serializable
- Automatic reversibility
- Path-aware error handling
- Composable and chainable operations

All migrations are just data, enabling storage, inspection, and code generation."

**Show:** The test results (1210 tests passing)

## Recording Command
For asciinema:
```bash
asciinema rec -i 2 migration-demo.cast
# Run the demo commands
# Then: asciinema upload migration-demo.cast
```

For OBS:
1. Start recording
2. Execute the commands in terminal
3. Stop recording
4. Export as MP4

## Demo Commands Summary
```powershell
# Setup (already done above)
cd c:\Users\Ahmed\Desktop\zio-blocks

# Run demo
sbt "examples/runMain zio.blocks.schema.migration.demo.MigrationDemo"

# Run tests (optional)
sbt -no-colors "schemaJVM/test"
```

## Files to Show
1. `docs/reference/migration.md` - Documentation
2. `examples/src/main/scala/MigrationDemo.scala` - Demo code
3. `schema/shared/src/main/scala/zio/blocks/schema/migration/MigrationValidator.scala` - Validation logic
4. `schema/shared/src/test/scala/zio/blocks/schema/migration/MigrationSchemasSerializationSpec.scala` - Serialization test

## Output to Capture
The complete demo output showing all features working:
- Schema versioning
- Migration building
- DynamicValue transformation
- Migration composition
- Migration reversal
- Value transformations
- Expression evaluation
