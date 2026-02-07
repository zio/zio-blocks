
---

# ZIO Blocks Schema Migration

A **pure, algebraic, and fully serializable** migration system for ZIO Schema.

## 📖 Overview

This library provides a mechanism to structurally transform data between schema versions **without requiring runtime representations** of older data types. It treats migrations as **first-class data**, enabling:

* **Schema Evolution:** Seamlessly move data from V1 → V2.
* **Zero Runtime Overhead:** Uses Scala's **Structural Types** for past versions, ensuring no dead code or old case classes pollute your runtime.
* **Offline Capability:** Migrations can be serialized (to JSON/Binary) and stored in registries or databases.
* **Macro-Validated:** Transformations are checked at compile-time for type safety.

---

## 📦 Installation


## 🚀 Usage

### 1. Define Versions (The "Zero Overhead" Pattern)

Unlike traditional approaches, you **do not** keep old Case Classes. Instead, define past versions using **Structural Types** and the current version as a real Case Class.

```scala
import zio.blocks.schema._
import zio.blocks.schema.migration._
import zio.schema.Schema

// --- 1. Past Version (Structural Type) ---
// No runtime class needed. Just a type alias describing the shape.
type UserV1 = { def name: String; def age: Int }

implicit val schemaV1: Schema[UserV1] = Schema.structural[UserV1]

// --- 2. Current Version (Case Class) ---
case class UserV2(fullName: String, age: Int, active: Boolean)

object UserV2 {
  implicit val schema: Schema[UserV2] = Schema.derived
}

```

### 2. Define the Migration

Use the type-safe DSL to describe how `UserV1` transforms into `UserV2`. The compiler ensures all fields in `UserV2` are satisfied.

```scala
val migration = Migration.newBuilder[UserV1, UserV2]
  .renameField(_.name, _.fullName)              // Rename field
  .addField(_.active, SchemaExpr.Constant(true)) // Add new field with default value
  .build

```

### 3. Run the Migration

```scala
// Simulate loading old data (e.g., from JSON or DB)
val oldData = DynamicValue.fromSchemaAndValue(schemaV1, new { val name = "Alice"; val age = 30 })

// Execute transformation
val result = migration(oldData)

// Output: Right(UserV2("Alice", 30, true))
println(result)

```

---

## 🛠 Supported Operations

The migration algebra supports a comprehensive set of structural transformations:

| Operation | Description |
| --- | --- |
| **`addField`** | Adds a new field to the target schema with a default value expression. |
| **`dropField`** | Removes a field that exists in the source but not the target. |
| **`renameField`** | Renames a field path (e.g., `address.street` -> `addr.road`). |
| **`transformField`** | Applies a purely functional transformation to a field's value. |
| **`optionalizeField`** | Safely converts a required field `T` to `Option[T]`. |
| **`mandateField`** | Converts `Option[T]` to `T` by providing a fallback default. |
| **`changeType`** | Converts primitive types (e.g., `Int` to `String`) using defined conversion ops. |

---

## 💾 Pure Data & Serialization

A key requirement of this system is that **migrations are data, not code**. They do not contain arbitrary closures or compiled lambdas, making them fully serializable.

This allows you to compute a migration plan, serialize it to JSON, and execute it on a remote machine or save it for audit logs.

```scala
import zio.blocks.schema.migration.json.MigrationJsonCodec._

// 1. Serialize migration to JSON
val json = migration.dynamicMigration.toJson
// Result: { "actions": [ { "op": "Rename", "details": { ... } } ] }

// 2. Deserialize later (e.g., on a Spark worker or separate service)
val loadedMigration = migrationDecoder.decode(json)

// 3. Apply
val migratedData = MigrationInterpreter.run(inputData, loadedMigration)

```

---

## 🧩 Advanced Features

### Enum & ADT Support

Rename or restructure Sum Types (Enums) seamlessly.

```scala
.renameCase(from = "User", to = "Member")
.transformCase(_.asUser, actions => actions.addField(...))

```

### Collection Support

Apply migrations deep inside nested collections (`List`, `Map`, `Set`).

```scala
.transformElements(
  at = _.tags, 
  transform = SchemaExpr.Converted(SchemaExpr.Identity(), ConversionOp.ToUpper)
)

```

---

## ⚖️ Design Philosophy

1. **Algebraic:** Migrations are composed of small, atomic operations.
2. **Introspectable:** Since migrations are data, we can query them (e.g., "Does this migration drop any fields?").
3. **Forward/Backward:** The structure allows generating reverse migrations (best-effort) automatically.

---
