package zio.blocks.schema.migration

import scala.quoted._
import zio.blocks.schema.Schema

/**
 * Macros for [[TrackedMigrationBuilder]] that track field names at the type
 * level using Tuple types.
 *
 * Each method extracts the field name from the selector lambda at compile time,
 * then returns a builder with the field name appended to the appropriate tuple
 * type parameter.
 */
object TrackedMigrationBuilderMacros {

  def addFieldImpl[A: Type, B: Type, SH <: Tuple: Type, TP <: Tuple: Type, T: Type](
    builder: Expr[TrackedMigrationBuilder[A, B, SH, TP]],
    selector: Expr[B => T],
    default: Expr[T],
    schema: Expr[Schema[T]]
  )(using q: Quotes): Expr[TrackedMigrationBuilder[A, B, SH, ?]] = {
    import q.reflect._

    val fieldName     = MigrationValidationMacros.extractFieldNameFromSelector(selector)
    val fieldNameType = ConstantType(StringConstant(fieldName))

    fieldNameType.asType match {
      case '[fn] =>
        '{
          val optic = SelectorMacros.toOptic($selector)
          new TrackedMigrationBuilder[A, B, SH, Tuple.Concat[TP, fn *: EmptyTuple]](
            $builder.inner.addField(optic, $default)(using $schema)
          )
        }
    }
  }

  def addFieldExprImpl[A: Type, B: Type, SH <: Tuple: Type, TP <: Tuple: Type, T: Type](
    builder: Expr[TrackedMigrationBuilder[A, B, SH, TP]],
    selector: Expr[B => T],
    default: Expr[DynamicSchemaExpr]
  )(using q: Quotes): Expr[TrackedMigrationBuilder[A, B, SH, ?]] = {
    import q.reflect._

    val fieldName     = MigrationValidationMacros.extractFieldNameFromSelector(selector)
    val fieldNameType = ConstantType(StringConstant(fieldName))

    fieldNameType.asType match {
      case '[fn] =>
        '{
          val optic = SelectorMacros.toOptic($selector)
          new TrackedMigrationBuilder[A, B, SH, Tuple.Concat[TP, fn *: EmptyTuple]](
            $builder.inner.addField(optic, $default)
          )
        }
    }
  }

  def dropFieldImpl[A: Type, B: Type, SH <: Tuple: Type, TP <: Tuple: Type, T: Type](
    builder: Expr[TrackedMigrationBuilder[A, B, SH, TP]],
    selector: Expr[A => T]
  )(using q: Quotes): Expr[TrackedMigrationBuilder[A, B, ?, TP]] = {
    import q.reflect._

    val fieldName     = MigrationValidationMacros.extractFieldNameFromSelector(selector)
    val fieldNameType = ConstantType(StringConstant(fieldName))

    fieldNameType.asType match {
      case '[fn] =>
        '{
          val optic = SelectorMacros.toOptic($selector)
          new TrackedMigrationBuilder[A, B, Tuple.Concat[SH, fn *: EmptyTuple], TP](
            $builder.inner.dropField(optic)
          )
        }
    }
  }

  def dropFieldWithReverseImpl[A: Type, B: Type, SH <: Tuple: Type, TP <: Tuple: Type, T: Type](
    builder: Expr[TrackedMigrationBuilder[A, B, SH, TP]],
    selector: Expr[A => T],
    defaultForReverse: Expr[DynamicSchemaExpr]
  )(using q: Quotes): Expr[TrackedMigrationBuilder[A, B, ?, TP]] = {
    import q.reflect._

    val fieldName     = MigrationValidationMacros.extractFieldNameFromSelector(selector)
    val fieldNameType = ConstantType(StringConstant(fieldName))

    fieldNameType.asType match {
      case '[fn] =>
        '{
          val optic = SelectorMacros.toOptic($selector)
          new TrackedMigrationBuilder[A, B, Tuple.Concat[SH, fn *: EmptyTuple], TP](
            $builder.inner.dropField(optic, $defaultForReverse)
          )
        }
    }
  }

  def renameFieldImpl[A: Type, B: Type, SH <: Tuple: Type, TP <: Tuple: Type](
    builder: Expr[TrackedMigrationBuilder[A, B, SH, TP]],
    from: Expr[A => Any],
    to: Expr[B => Any]
  )(using q: Quotes): Expr[TrackedMigrationBuilder[A, B, ?, ?]] = {
    import q.reflect._

    val fromFieldName     = MigrationValidationMacros.extractFieldNameFromSelector(from)
    val toFieldName       = MigrationValidationMacros.extractFieldNameFromSelector(to)
    val fromFieldNameType = ConstantType(StringConstant(fromFieldName))
    val toFieldNameType   = ConstantType(StringConstant(toFieldName))

    (fromFieldNameType.asType, toFieldNameType.asType) match {
      case ('[fnFrom], '[fnTo]) =>
        '{
          val fromOptic = SelectorMacros.toOptic($from)
          val toOptic   = SelectorMacros.toOptic($to)
          new TrackedMigrationBuilder[
            A,
            B,
            Tuple.Concat[SH, fnFrom *: EmptyTuple],
            Tuple.Concat[TP, fnTo *: EmptyTuple]
          ](
            $builder.inner.renameField(fromOptic, toOptic)
          )
        }
    }
  }
}
