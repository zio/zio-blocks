package zio.blocks.schema.migration

import scala.language.experimental.macros
import zio.blocks.schema.{DynamicOptic, DynamicValue, Schema}

/**
 * Scala 2 version of MigrationBuilder.
 */
// format: off
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

  /**
   * Transform a field's value using a serializable expression.
   *
   * @param at The path to the field to transform
   * @param expr The expression that computes the new value
   * @param reverseExpr Optional expression for reverse migration
   */
  def transformFieldExpr(at: DynamicOptic, expr: MigrationExpr, reverseExpr: Option[MigrationExpr] = None): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformValueExpr(at, expr, reverseExpr))

  /**
   * Change the type of a field using a serializable expression.
   *
   * @param at The path to the field to convert
   * @param convertExpr Expression that converts the value
   * @param reverseExpr Optional expression for reverse migration
   */
  def changeFieldTypeExpr(at: DynamicOptic, convertExpr: MigrationExpr, reverseExpr: Option[MigrationExpr] = None): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.ChangeTypeExpr(at, convertExpr, reverseExpr))

  /**
   * Join multiple source fields into a single target field using an expression.
   *
   * @param target The path to the target field
   * @param sourcePaths Paths to the source fields to join
   * @param combineExpr Expression that computes the combined value
   * @param splitExprs Optional expressions for reverse migration
   */
  def joinFields(target: DynamicOptic, sourcePaths: Vector[DynamicOptic], combineExpr: MigrationExpr, splitExprs: Option[Vector[MigrationExpr]] = None): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.JoinExpr(target, sourcePaths, combineExpr, splitExprs))

  /**
   * Split a source field into multiple target fields using expressions.
   *
   * @param source The path to the source field
   * @param targetPaths Paths to the target fields
   * @param splitExprs Expressions that compute each target value
   * @param combineExpr Optional expression for reverse migration
   */
  def splitField(source: DynamicOptic, targetPaths: Vector[DynamicOptic], splitExprs: Vector[MigrationExpr], combineExpr: Option[MigrationExpr] = None): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.SplitExpr(source, targetPaths, splitExprs, combineExpr))

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
// format: on

object MigrationBuilder {

  /** Create a new migration builder. */
  def apply[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, Vector.empty)
}
