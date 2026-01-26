package zio.blocks.schema.migration

import scala.language.experimental.macros
import scala.language.implicitConversions
import zio.blocks.schema._

/**
 * Selector syntax for MigrationBuilder. Methods that accept selector functions
 * like `_.field` instead of manually constructing DynamicOptic instances.
 *
 * In Scala 2, the Handled and Provided type parameters are always Any (no compile-time tracking).
 */
final class MigrationBuilderSyntax[A, B, Handled, Provided](val builder: MigrationBuilder[A, B, Handled, Provided])
    extends AnyVal {

  /**
   * Adds a field to a record with a default value using selector syntax.
   */
  def addField(
    target: B => Any,
    default: SchemaExpr[DynamicValue, _]
  ): MigrationBuilder[A, B, Handled, Provided] = macro MigrationBuilderMacrosImpl.addFieldImpl[A, B, Handled, Provided]

  /**
   * Removes a field from a record using selector syntax.
   */
  def dropField(
    source: A => Any,
    defaultForReverse: SchemaExpr[DynamicValue, _]
  ): MigrationBuilder[A, B, Handled, Provided] = macro MigrationBuilderMacrosImpl.dropFieldImpl[A, B, Handled, Provided]

  /**
   * Renames a field in a record using selector syntax.
   */
  def renameField(
    from: A => Any,
    to: B => Any
  ): MigrationBuilder[A, B, Handled, Provided] = macro MigrationBuilderMacrosImpl.renameFieldImpl[A, B, Handled, Provided]

  /**
   * Applies a transformation expression to a field value using selector syntax.
   */
  def transformField(
    at: A => Any,
    transform: SchemaExpr[DynamicValue, _]
  ): MigrationBuilder[A, B, Handled, Provided] = macro MigrationBuilderMacrosImpl.transformFieldImpl[A, B, Handled, Provided]

  /**
   * Unwraps an Option field using selector syntax, using default for None
   * values.
   */
  def mandateField(
    at: A => Any,
    default: SchemaExpr[DynamicValue, _]
  ): MigrationBuilder[A, B, Handled, Provided] = macro MigrationBuilderMacrosImpl.mandateFieldImpl[A, B, Handled, Provided]

  /**
   * Wraps a field value in Option (as Some) using selector syntax.
   */
  def optionalizeField(
    at: A => Any,
    defaultForReverse: SchemaExpr[DynamicValue, _]
  ): MigrationBuilder[A, B, Handled, Provided] =
    macro MigrationBuilderMacrosImpl.optionalizeFieldImpl[A, B, Handled, Provided]

  /**
   * Converts a field from one primitive type to another using selector syntax.
   */
  def changeFieldType(
    at: A => Any,
    converter: PrimitiveConverter
  ): MigrationBuilder[A, B, Handled, Provided] =
    macro MigrationBuilderMacrosImpl.changeFieldTypeImpl[A, B, Handled, Provided]

  /**
   * Joins multiple source fields into a single target field using selector
   * syntax.
   */
  def joinFields(
    target: B => Any,
    sourcePaths: Seq[A => Any],
    combiner: SchemaExpr[DynamicValue, _]
  ): MigrationBuilder[A, B, Handled, Provided] = macro MigrationBuilderMacrosImpl.joinFieldsImpl[A, B, Handled, Provided]

  /**
   * Splits a single source field into multiple target fields using selector
   * syntax.
   */
  def splitField(
    source: A => Any,
    targetPaths: Seq[B => Any],
    splitter: SchemaExpr[DynamicValue, _]
  ): MigrationBuilder[A, B, Handled, Provided] = macro MigrationBuilderMacrosImpl.splitFieldImpl[A, B, Handled, Provided]

  /**
   * Applies a transformation to all elements in a sequence using selector
   * syntax.
   */
  def transformElements(
    at: A => Any,
    transform: SchemaExpr[DynamicValue, _]
  ): MigrationBuilder[A, B, Handled, Provided] =
    macro MigrationBuilderMacrosImpl.transformElementsImpl[A, B, Handled, Provided]

  /**
   * Applies a transformation to all keys in a map using selector syntax.
   */
  def transformKeys(
    at: A => Any,
    transform: SchemaExpr[DynamicValue, _]
  ): MigrationBuilder[A, B, Handled, Provided] = macro MigrationBuilderMacrosImpl.transformKeysImpl[A, B, Handled, Provided]

  /**
   * Applies a transformation to all values in a map using selector syntax.
   */
  def transformValues(
    at: A => Any,
    transform: SchemaExpr[DynamicValue, _]
  ): MigrationBuilder[A, B, Handled, Provided] =
    macro MigrationBuilderMacrosImpl.transformValuesImpl[A, B, Handled, Provided]

  /**
   * Renames a variant case using selector syntax.
   */
  def renameCase(
    from: A => Any,
    to: String
  ): MigrationBuilder[A, B, Handled, Provided] = macro MigrationBuilderMacrosImpl.renameCaseImpl[A, B, Handled, Provided]

  /**
   * Applies nested migration actions to a specific variant case using selector
   * syntax.
   */
  def transformCase(
    at: A => Any
  )(
    nestedActions: MigrationBuilder[A, A, Any, Any] => MigrationBuilder[A, A, Any, Any]
  ): MigrationBuilder[A, B, Handled, Provided] =
    macro MigrationBuilderMacrosImpl.transformCaseImpl[A, B, Handled, Provided]
}

object MigrationBuilderSyntax {
  implicit def toSyntax[A, B, Handled, Provided](
    builder: MigrationBuilder[A, B, Handled, Provided]
  ): MigrationBuilderSyntax[A, B, Handled, Provided] =
    new MigrationBuilderSyntax[A, B, Handled, Provided](builder)
}

// Macro implementations for Scala 2 selector syntax.
private[migration] object MigrationBuilderMacrosImpl {
  import scala.reflect.macros.whitebox

  def addFieldImpl[A, B, Handled, Provided](c: whitebox.Context)(
    target: c.Expr[B => Any],
    default: c.Expr[SchemaExpr[DynamicValue, _]]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
      case b                 => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
    }
    val optic = MigrationBuilderMacros.extractOptic[B, Any](c)(target)
    c.universe.reify {
      builder.splice.addField(optic.splice, default.splice)
    }
  }

  def dropFieldImpl[A, B, Handled, Provided](c: whitebox.Context)(
    source: c.Expr[A => Any],
    defaultForReverse: c.Expr[SchemaExpr[DynamicValue, _]]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
      case b                 => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
    }
    val optic = MigrationBuilderMacros.extractOptic[A, Any](c)(source)
    c.universe.reify {
      builder.splice.dropField(optic.splice, defaultForReverse.splice)
    }
  }

  def renameFieldImpl[A, B, Handled, Provided](c: whitebox.Context)(
    from: c.Expr[A => Any],
    to: c.Expr[B => Any]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
      case b                 => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
    }
    val fromOptic   = MigrationBuilderMacros.extractOptic[A, Any](c)(from)
    val toFieldName = MigrationBuilderMacros.extractFieldName[B, Any](c)(to)
    c.universe.reify {
      builder.splice.renameField(fromOptic.splice, toFieldName.splice)
    }
  }

  def transformFieldImpl[A, B, Handled, Provided](c: whitebox.Context)(
    at: c.Expr[A => Any],
    transform: c.Expr[SchemaExpr[DynamicValue, _]]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
      case b                 => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
    }
    val optic = MigrationBuilderMacros.extractOptic[A, Any](c)(at)
    c.universe.reify {
      builder.splice.transformField(optic.splice, transform.splice)
    }
  }

  def mandateFieldImpl[A, B, Handled, Provided](c: whitebox.Context)(
    at: c.Expr[A => Any],
    default: c.Expr[SchemaExpr[DynamicValue, _]]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
      case b                 => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
    }
    val optic = MigrationBuilderMacros.extractOptic[A, Any](c)(at)
    c.universe.reify {
      builder.splice.mandateField(optic.splice, default.splice)
    }
  }

  def optionalizeFieldImpl[A, B, Handled, Provided](c: whitebox.Context)(
    at: c.Expr[A => Any],
    defaultForReverse: c.Expr[SchemaExpr[DynamicValue, _]]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
      case b                 => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
    }
    val optic = MigrationBuilderMacros.extractOptic[A, Any](c)(at)
    c.universe.reify {
      builder.splice.optionalizeField(optic.splice, defaultForReverse.splice)
    }
  }

  def changeFieldTypeImpl[A, B, Handled, Provided](c: whitebox.Context)(
    at: c.Expr[A => Any],
    converter: c.Expr[PrimitiveConverter]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
      case b                 => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
    }
    val optic = MigrationBuilderMacros.extractOptic[A, Any](c)(at)
    c.universe.reify {
      builder.splice.changeFieldType(optic.splice, converter.splice)
    }
  }

  def joinFieldsImpl[A, B, Handled, Provided](c: whitebox.Context)(
    target: c.Expr[B => Any],
    sourcePaths: c.Expr[Seq[A => Any]],
    combiner: c.Expr[SchemaExpr[DynamicValue, _]]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
      case b                 => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
    }
    val targetOptic  = MigrationBuilderMacros.extractOptic[B, Any](c)(target)
    val sourceOptics = MigrationBuilderMacros.extractOptics[A, Any](c)(sourcePaths)
    c.universe.reify {
      builder.splice.joinFields(targetOptic.splice, sourceOptics.splice, combiner.splice)
    }
  }

  def splitFieldImpl[A, B, Handled, Provided](c: whitebox.Context)(
    source: c.Expr[A => Any],
    targetPaths: c.Expr[Seq[B => Any]],
    splitter: c.Expr[SchemaExpr[DynamicValue, _]]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
      case b                 => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
    }
    val sourceOptic  = MigrationBuilderMacros.extractOptic[A, Any](c)(source)
    val targetOptics = MigrationBuilderMacros.extractOptics[B, Any](c)(targetPaths)
    c.universe.reify {
      builder.splice.splitField(sourceOptic.splice, targetOptics.splice, splitter.splice)
    }
  }

  def transformElementsImpl[A, B, Handled, Provided](c: whitebox.Context)(
    at: c.Expr[A => Any],
    transform: c.Expr[SchemaExpr[DynamicValue, _]]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
      case b                 => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
    }
    val optic = MigrationBuilderMacros.extractOptic[A, Any](c)(at)
    c.universe.reify {
      builder.splice.transformElements(optic.splice, transform.splice)
    }
  }

  def transformKeysImpl[A, B, Handled, Provided](c: whitebox.Context)(
    at: c.Expr[A => Any],
    transform: c.Expr[SchemaExpr[DynamicValue, _]]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
      case b                 => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
    }
    val optic = MigrationBuilderMacros.extractOptic[A, Any](c)(at)
    c.universe.reify {
      builder.splice.transformKeys(optic.splice, transform.splice)
    }
  }

  def transformValuesImpl[A, B, Handled, Provided](c: whitebox.Context)(
    at: c.Expr[A => Any],
    transform: c.Expr[SchemaExpr[DynamicValue, _]]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
      case b                 => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
    }
    val optic = MigrationBuilderMacros.extractOptic[A, Any](c)(at)
    c.universe.reify {
      builder.splice.transformValues(optic.splice, transform.splice)
    }
  }

  def renameCaseImpl[A, B, Handled, Provided](c: whitebox.Context)(
    from: c.Expr[A => Any],
    to: c.Expr[String]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
      case b                 => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
    }
    val (fromOptic, fromCaseName) = MigrationBuilderMacros.extractCaseSelector[A, Any](c)(from)
    c.universe.reify {
      builder.splice.renameCase(fromOptic.splice, fromCaseName.splice, to.splice)
    }
  }

  def transformCaseImpl[A, B, Handled, Provided](c: whitebox.Context)(
    at: c.Expr[A => Any]
  )(
    nestedActions: c.Expr[MigrationBuilder[A, A, Any, Any] => MigrationBuilder[A, A, Any, Any]]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val builder = c.prefix.tree match {
      case Apply(_, List(b)) => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
      case b                 => c.Expr[MigrationBuilder[A, B, Handled, Provided]](b)
    }
    val (atOptic, caseName) = MigrationBuilderMacros.extractCaseSelector[A, Any](c)(at)
    c.universe.reify {
      val sourceSchema = builder.splice.sourceSchema
      val emptyBuilder: MigrationBuilder[A, A, Any, Any] =
        MigrationBuilder[A, A, Any, Any](sourceSchema, sourceSchema, Vector.empty)
      val transformedBuilder = nestedActions.splice.apply(emptyBuilder)
      builder.splice.transformCase(atOptic.splice, caseName.splice, transformedBuilder.actions)
    }
  }
}
