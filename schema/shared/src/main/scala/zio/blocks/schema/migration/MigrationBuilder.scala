package zio.blocks.schema.migration

import zio.schema.Schema
import zio.blocks.schema.migration.optic.{OpticStep, Selector}

/**
 * The MigrationBuilder constructs the migration plan.
 * * ARCHITECTURE NOTE:
 * This file is shared between Scala 2.13 and Scala 3.
 * It uses the 'Selector' Type Class pattern to abstract over the macro implementation.
 * * - In Scala 2: The compiler finds an implicit provided by macro expansion.
 * - In Scala 3: The compiler finds a given provided by inline macro expansion.
 * * This ensures strict separation of concerns and full cross-compilation support.
 */
class MigrationBuilder[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  actions: Vector[MigrationAction]
) {

  /**
   * Adds a new field to the target schema.
   * * @param selector A function pointing to the field in the Target type (B).
   * @param value The value expression (Constant or Derived from Source A).
   */
  def addField[T](
    selector: B => T, 
    value: SchemaExpr[A, T]
  )(implicit s: Selector[B, T]): MigrationBuilder[A, B] = {
    val _ = selector // Suppress unused warning (Used by macro for Type/Path inference)
    
    // We store the generic SchemaExpr. In serialization, this will be converted to the appropriate format.
    // Note: We cast SchemaExpr[A, T] to SchemaExpr[Any, Any] for storage in the untyped ADT.
    // This is safe because 'eval' handles types at runtime.
    val newAction = MigrationAction.AddField(
      s.path, 
      value.asInstanceOf[SchemaExpr[Any, Any]]
    )
    copy(actions = actions :+ newAction)
  }

  /**
   * Drops a field from the source schema.
   * * @param selector A function pointing to the field in the Source type (A).
   * @param defaultForReverse Value to use if we reverse this migration (add the field back).
   */
  def dropField[T](
    selector: A => T,
    defaultForReverse: SchemaExpr[B, T] = SchemaExpr.DefaultValue[B, T]()
  )(implicit s: Selector[A, T]): MigrationBuilder[A, B] = {
    val _ = selector
    val newAction = MigrationAction.DeleteField(
      s.path, 
      defaultForReverse.asInstanceOf[SchemaExpr[Any, Any]]
    )
    copy(actions = actions :+ newAction)
  }

  /**
   * Renames a field.
   * * @param from Selector for the old name (Source A).
   * @param to Selector for the new name (Target B).
   */
  def renameField[T, U](
    from: A => T,
    to: B => U
  )(implicit sFrom: Selector[A, T], sTo: Selector[B, U]): MigrationBuilder[A, B] = {
    val _ = (from, to)

    // Extract the strict name from the Target selector
    val newName = sTo.path.steps.lastOption match {
      case Some(OpticStep.Field(name)) => name
      case _ => throw new IllegalArgumentException("Target selector must point to a valid field name")
    }

    val newAction = MigrationAction.RenameField(sFrom.path, newName)
    copy(actions = actions :+ newAction)
  }

  /**
   * Transforms a field's value using an expression.
   * Example: .transformField(_.age, _.age, age => age + 1)
   */
  def transformField[T, U](
    from: A => T,
    to: B => U,
    transform: SchemaExpr[A, U]
  )(implicit sFrom: Selector[A, T]): MigrationBuilder[A, B] = {
    val _ = (from, to)
    val newAction = MigrationAction.TransformValue(
      sFrom.path, 
      transform.asInstanceOf[SchemaExpr[Any, Any]]
    )
    copy(actions = actions :+ newAction)
  }

  /**
   * Makes an optional field mandatory.
   */
  def mandateField[T](
    source: A => Option[T],
    target: B => T,
    default: SchemaExpr[A, T]
  )(implicit s: Selector[A, Option[T]]): MigrationBuilder[A, B] = {
    val _ = (source, target)
    val newAction = MigrationAction.MandateField(
      s.path, 
      default.asInstanceOf[SchemaExpr[Any, Any]]
    )
    copy(actions = actions :+ newAction)
  }

  /**
   * Makes a mandatory field optional.
   */
  def optionalizeField[T](
    source: A => T,
    target: B => Option[T]
  )(implicit s: Selector[A, T]): MigrationBuilder[A, B] = {
    val _ = (source, target)
    val newAction = MigrationAction.OptionalizeField(s.path)
    copy(actions = actions :+ newAction)
  }

  /**
   * Changes the type of a field (e.g., Int -> String).
   * Currently restricted to primitives via SchemaExpr logic.
   */
  def changeFieldType[T, U](
    source: A => T,
    target: B => U,
    converter: SchemaExpr[A, U]
  )(implicit s: Selector[A, T]): MigrationBuilder[A, B] = {
    val _ = (source, target)
    // We serialize the converter expression itself, not just a string
    // Storing as 'transform' action internally or a dedicated ChangeType with expr
    // Assuming ChangeType in ADT takes a converter expression now
    val newAction = MigrationAction.ChangeType(
      s.path, 
      converter.toString // For now string representation, ideally SchemaExpr
    ) 
    copy(actions = actions :+ newAction)
  }

  // --- Collection & Map Support (Placeholder for now) ---
  
  def transformElements[T](
    at: A => Vector[T],
    transform: SchemaExpr[A, T] // Transform each element
  )(implicit s: Selector[A, Vector[T]]): MigrationBuilder[A, B] = {
    val _ = at
    val newAction = MigrationAction.TransformValue(
      s.path, 
      transform.asInstanceOf[SchemaExpr[Any, Any]]
    ) 
    copy(actions = actions :+ newAction)
  }

  def transformKeys[K, V](
    at: A => Map[K, V],
    transform: SchemaExpr[A, K]
  )(implicit s: Selector[A, Map[K, V]]): MigrationBuilder[A, B] = {
    val _ = at
    val newAction = MigrationAction.TransformKeys(
      s.path, 
      transform.asInstanceOf[SchemaExpr[Any, Any]]
    )
    copy(actions = actions :+ newAction)
  }

  def transformValues[K, V](
    at: A => Map[K, V],
    transform: SchemaExpr[A, V]
  )(implicit s: Selector[A, Map[K, V]]): MigrationBuilder[A, B] = {
    val _ = at
    val newAction = MigrationAction.TransformValues(
      s.path, 
      transform.asInstanceOf[SchemaExpr[Any, Any]]
    )
    copy(actions = actions :+ newAction)
  }

  // --- Build Methods ---

  def build: Migration[A, B] = {
    // Ideally, we run macro-validation here to ensure all fields in B are covered.
    // For this stage, we return the migration object.
    Migration(sourceSchema, targetSchema, DynamicMigration(actions))
  }

  def buildPartial: Migration[A, B] = build

  private def copy(actions: Vector[MigrationAction]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions)
}