# Schema Migration System Design

## Overview
Implement a pure, algebraic migration system for ZIO Schema 2 that represents structural transformations between schema versions as first-class, serializable data.

## Core Components

### 1. MigrationError
```scala
sealed trait MigrationError {
  def message: String
  def path: Option[DynamicOptic]
}
```

### 2. MigrationAction ADT
```scala
sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction
}

// Record Actions
case class AddField(at: DynamicOptic, default: SchemaExpr[?])
case class DropField(at: DynamicOptic, defaultForReverse: SchemaExpr[?])
case class Rename(at: DynamicOptic, to: String)
case class TransformValue(at: DynamicOptic, transform: SchemaExpr[?])
case class Mandate(at: DynamicOptic, default: SchemaExpr[?])
case class Optionalize(at: DynamicOptic)
case class Join(at: DynamicOptic, sourcePaths: Vector[DynamicOptic], combiner: SchemaExpr[?])
case class Split(at: DynamicOptic, targetPaths: Vector[DynamicOptic], splitter: SchemaExpr[?])
case class ChangeType(at: DynamicOptic, converter: SchemaExpr[?])

// Enum Actions
case class RenameCase(at: DynamicOptic, from: String, to: String)
case class TransformCase(at: DynamicOptic, actions: Vector[MigrationAction])

// Collection/Map Actions
case class TransformElements(at: DynamicOptic, transform: SchemaExpr[?])
case class TransformKeys(at: DynamicOptic, transform: SchemaExpr[?])
case class TransformValues(at: DynamicOptic, transform: SchemaExpr[?])
```

### 3. DynamicMigration
```scala
case class DynamicMigration(actions: Vector[MigrationAction]) {
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue]
  def ++(that: DynamicMigration): DynamicMigration
  def reverse: DynamicMigration
}
```

### 4. Migration[A, B]
```scala
case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {
  def apply(value: A): Either[MigrationError, B]
  def ++[C](that: Migration[B, C]): Migration[A, C]
  def reverse: Migration[B, A]
}
```

### 5. MigrationBuilder[A, B]
Builder with selector expressions (macro-based)

## Implementation Steps

1. Create MigrationError with path information
2. Create MigrationAction ADT with all action types
3. Create DynamicMigration with apply/compose/reverse
4. Create Migration[A, B] wrapper
5. Create MigrationBuilder with macro support
6. Add Schema definitions for serialization
7. Write comprehensive tests
8. Add documentation

## Laws
- Identity: Migration.identity[A].apply(a) == Right(a)
- Associativity: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)
- Structural Reverse: m.reverse.reverse == m
