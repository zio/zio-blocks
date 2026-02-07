package zio.blocks.schema.migration

import scala.quoted._
import zio.blocks.schema.{DynamicValue, Schema}

/**
 * Macros for MigrationBuilder that track field names at the type level.
 *
 * These macros enable compile-time validation by tracking:
 *   - Source fields that have been handled (renamed, dropped, transformed)
 *   - Target fields that have been provided (added, renamed to)
 */
object MigrationBuilderMacros {

  /**
   * Add a field to the target, tracking the field name at type level.
   */
  def addFieldImpl[A: Type, B: Type, SH <: Tuple: Type, TP <: Tuple: Type, T: Type](
    builder: Expr[MigrationBuilder[A, B, SH, TP]],
    target: Expr[B => T],
    default: Expr[T],
    targetFieldSchema: Expr[Schema[T]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, SH, ?]] = {
    import q.reflect._

    val fieldName = MigrationValidationMacros.extractFieldNameFromSelector(target)

    // Create a literal type for the field name
    val fieldNameType = ConstantType(StringConstant(fieldName))

    fieldNameType.asType match {
      case '[fn] =>
        '{
          val path           = SelectorMacros.toPath[B, T]($target)
          val dynamicDefault = $targetFieldSchema.toDynamicValue($default)
          new MigrationBuilder[A, B, SH, Tuple.Concat[TP, fn *: EmptyTuple]](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.AddField(path, dynamicDefault)
          )
        }
    }
  }

  /**
   * Add a field with a DynamicValue default, tracking the field name at type
   * level.
   */
  def addFieldDynamicImpl[A: Type, B: Type, SH <: Tuple: Type, TP <: Tuple: Type](
    builder: Expr[MigrationBuilder[A, B, SH, TP]],
    target: Expr[B => Any],
    default: Expr[DynamicValue]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, SH, ?]] = {
    import q.reflect._

    val fieldName     = MigrationValidationMacros.extractFieldNameFromSelector(target)
    val fieldNameType = ConstantType(StringConstant(fieldName))

    fieldNameType.asType match {
      case '[fn] =>
        '{
          val path = SelectorMacros.toPath[B, Any]($target)
          new MigrationBuilder[A, B, SH, Tuple.Concat[TP, fn *: EmptyTuple]](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.AddField(path, $default)
          )
        }
    }
  }

  /**
   * Drop a field from the source, tracking the field name at type level.
   */
  def dropFieldImpl[A: Type, B: Type, SH <: Tuple: Type, TP <: Tuple: Type, T: Type](
    builder: Expr[MigrationBuilder[A, B, SH, TP]],
    source: Expr[A => T],
    defaultForReverse: Expr[T],
    sourceFieldSchema: Expr[Schema[T]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?, TP]] = {
    import q.reflect._

    val fieldName     = MigrationValidationMacros.extractFieldNameFromSelector(source)
    val fieldNameType = ConstantType(StringConstant(fieldName))

    fieldNameType.asType match {
      case '[fn] =>
        '{
          val path           = SelectorMacros.toPath[A, T]($source)
          val dynamicDefault = $sourceFieldSchema.toDynamicValue($defaultForReverse)
          new MigrationBuilder[A, B, Tuple.Concat[SH, fn *: EmptyTuple], TP](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.DropField(path, dynamicDefault)
          )
        }
    }
  }

  /**
   * Drop a field with a DynamicValue for reverse, tracking the field name at
   * type level.
   */
  def dropFieldDynamicImpl[A: Type, B: Type, SH <: Tuple: Type, TP <: Tuple: Type](
    builder: Expr[MigrationBuilder[A, B, SH, TP]],
    source: Expr[A => Any],
    defaultForReverse: Expr[DynamicValue]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?, TP]] = {
    import q.reflect._

    val fieldName     = MigrationValidationMacros.extractFieldNameFromSelector(source)
    val fieldNameType = ConstantType(StringConstant(fieldName))

    fieldNameType.asType match {
      case '[fn] =>
        '{
          val path = SelectorMacros.toPath[A, Any]($source)
          new MigrationBuilder[A, B, Tuple.Concat[SH, fn *: EmptyTuple], TP](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.DropField(path, $defaultForReverse)
          )
        }
    }
  }

  /**
   * Rename a field, tracking both source (handled) and target (provided) at
   * type level.
   */
  def renameFieldImpl[A: Type, B: Type, SH <: Tuple: Type, TP <: Tuple: Type](
    builder: Expr[MigrationBuilder[A, B, SH, TP]],
    from: Expr[A => Any],
    to: Expr[B => Any]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?, ?]] = {
    import q.reflect._

    val fromFieldName = MigrationValidationMacros.extractFieldNameFromSelector(from)
    val toFieldName   = MigrationValidationMacros.extractFieldNameFromSelector(to)

    val fromFieldNameType = ConstantType(StringConstant(fromFieldName))
    val toFieldNameType   = ConstantType(StringConstant(toFieldName))

    (fromFieldNameType.asType, toFieldNameType.asType) match {
      case ('[fnFrom], '[fnTo]) =>
        '{
          val fromPath = SelectorMacros.toPath[A, Any]($from)
          val toName   = SelectorMacros.extractFieldName[B, Any]($to)
          new MigrationBuilder[A, B, Tuple.Concat[SH, fnFrom *: EmptyTuple], Tuple.Concat[TP, fnTo *: EmptyTuple]](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.Rename(fromPath, toName)
          )
        }
    }
  }

  /**
   * Transform a field's value, tracking the field as handled.
   */
  def transformFieldLiteralImpl[A: Type, B: Type, SH <: Tuple: Type, TP <: Tuple: Type, T: Type](
    builder: Expr[MigrationBuilder[A, B, SH, TP]],
    at: Expr[A => T],
    newValue: Expr[T],
    fieldSchema: Expr[Schema[T]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?, TP]] = {
    import q.reflect._

    val fieldName     = MigrationValidationMacros.extractFieldNameFromSelector(at)
    val fieldNameType = ConstantType(StringConstant(fieldName))

    fieldNameType.asType match {
      case '[fn] =>
        '{
          val path         = SelectorMacros.toPath[A, T]($at)
          val dynamicValue = $fieldSchema.toDynamicValue($newValue)
          new MigrationBuilder[A, B, Tuple.Concat[SH, fn *: EmptyTuple], TP](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.TransformValue(path, dynamicValue)
          )
        }
    }
  }

  /**
   * Mandate a field (Option[T] -> T), tracking field as handled on source.
   */
  def mandateFieldImpl[A: Type, B: Type, SH <: Tuple: Type, TP <: Tuple: Type, T: Type](
    builder: Expr[MigrationBuilder[A, B, SH, TP]],
    source: Expr[A => Option[T]],
    @scala.annotation.unused target: Expr[B => T],
    default: Expr[T],
    fieldSchema: Expr[Schema[T]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?, TP]] = {
    import q.reflect._

    val fieldName     = MigrationValidationMacros.extractFieldNameFromSelector(source)
    val fieldNameType = ConstantType(StringConstant(fieldName))

    fieldNameType.asType match {
      case '[fn] =>
        '{
          val path           = SelectorMacros.toPath[A, Option[T]]($source)
          val dynamicDefault = $fieldSchema.toDynamicValue($default)
          new MigrationBuilder[A, B, Tuple.Concat[SH, fn *: EmptyTuple], TP](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.Mandate(path, dynamicDefault)
          )
        }
    }
  }

  /**
   * Optionalize a field (T -> Option[T]), tracking field as handled on source.
   */
  def optionalizeFieldImpl[A: Type, B: Type, SH <: Tuple: Type, TP <: Tuple: Type, T: Type](
    builder: Expr[MigrationBuilder[A, B, SH, TP]],
    source: Expr[A => T],
    @scala.annotation.unused target: Expr[B => Option[T]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ?, TP]] = {
    import q.reflect._

    val fieldName     = MigrationValidationMacros.extractFieldNameFromSelector(source)
    val fieldNameType = ConstantType(StringConstant(fieldName))

    fieldNameType.asType match {
      case '[fn] =>
        '{
          val path = SelectorMacros.toPath[A, T]($source)
          new MigrationBuilder[A, B, Tuple.Concat[SH, fn *: EmptyTuple], TP](
            $builder.sourceSchema,
            $builder.targetSchema,
            $builder.actions :+ MigrationAction.Optionalize(path)
          )
        }
    }
  }
}
