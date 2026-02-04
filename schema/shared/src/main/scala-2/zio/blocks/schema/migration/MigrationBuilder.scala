package zio.blocks.schema.migration

import scala.language.experimental.macros
import zio.blocks.schema.{DynamicOptic, DynamicValue, Schema}

/**
 * Scala 2 version of MigrationBuilder.
 */
final class MigrationBuilder[A, B](
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  private[migration] val actions: Vector[MigrationAction]
) {

  /** Add a new field to the target with a default value. */
  def addField[T](target: B => T, default: T)(implicit targetFieldSchema: Schema[T]): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.addFieldImpl[A, B, T]

  /** Add a new field to the target with a DynamicValue default. */
  def addFieldDynamic(target: B => Any, default: DynamicValue): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.addFieldDynamicImpl[A, B]

  /** Drop a field from the source. */
  def dropField[T](source: A => T, defaultForReverse: T)(implicit sourceFieldSchema: Schema[T]): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.dropFieldImpl[A, B, T]

  /** Drop a field from the source with a DynamicValue for reverse. */
  def dropFieldDynamic(source: A => Any, defaultForReverse: DynamicValue): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.dropFieldDynamicImpl[A, B]

  /** Rename a field from source name to target name. */
  def renameField(from: A => Any, to: B => Any): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.renameFieldImpl[A, B]

  /** Transform a field with a literal new value. */
  def transformFieldLiteral[T](at: A => T, newValue: T)(implicit fieldSchema: Schema[T]): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.transformFieldLiteralImpl[A, B, T]

  /** Convert an optional field to required. */
  def mandateField[T](source: A => Option[T], target: B => T, default: T)(implicit fieldSchema: Schema[T]): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.mandateFieldImpl[A, B, T]

  /** Convert a required field to optional. */
  def optionalizeField[T](source: A => T, target: B => Option[T]): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.optionalizeFieldImpl[A, B, T]

  /** Rename an enum case. */
  def renameCase(from: String, to: String): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.RenameCase(DynamicOptic.root, from, to))

  /** Build the migration with validation. */
  def build: Migration[A, B] =
    new Migration(sourceSchema, targetSchema, new DynamicMigration(actions))

  /** Build the migration without validation. */
  def buildPartial: Migration[A, B] =
    new Migration(sourceSchema, targetSchema, new DynamicMigration(actions))
}

object MigrationBuilder {
  /** Create a new migration builder. */
  def apply[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, Vector.empty)
}
