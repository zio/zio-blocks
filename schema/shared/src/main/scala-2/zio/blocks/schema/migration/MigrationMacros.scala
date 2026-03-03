package zio.blocks.schema.migration

import scala.language.experimental.macros
import zio.blocks.schema.Schema

object MigrationMacros {

  // ─────────────────────────────────────────────────────────────────────────
  // Implicit class: selector-based builder API for Scala 2
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Provides selector-based methods (`_.field` syntax) on [[MigrationBuilder]]
   * via macro expansion.
   *
   * {{{
   * import zio.blocks.schema.migration.MigrationMacros._
   *
   * Migration.newBuilder[PersonV0, PersonV1]
   *   .renameField(_.firstName, _.fullName)
   *   .addField(_.age, 0)
   *   .build
   * }}}
   */
  implicit class MigrationBuilderSelectorOps[A, B](val builder: MigrationBuilder[A, B]) {

    def addField[V](target: B => Any, default: V)(implicit schema: Schema[V]): MigrationBuilder[A, B] =
      macro MigrationMacros.addFieldMacro[A, B, V]

    def dropField(source: A => Any): MigrationBuilder[A, B] =
      macro MigrationMacros.dropFieldMacro[A, B]

    def renameField(from: A => Any, to: B => Any): MigrationBuilder[A, B] =
      macro MigrationMacros.renameFieldMacro[A, B]

    def transformField(
      from: A => Any,
      to: B => Any,
      transform: DynamicSchemaExpr
    ): MigrationBuilder[A, B] =
      macro MigrationMacros.transformFieldMacro[A, B]

    def mandateField[V](
      source: A => Any,
      target: B => Any,
      default: V
    )(implicit schema: Schema[V]): MigrationBuilder[A, B] =
      macro MigrationMacros.mandateFieldMacro[A, B, V]

    def optionalizeField(
      source: A => Any,
      target: B => Any
    ): MigrationBuilder[A, B] =
      macro MigrationMacros.optionalizeFieldMacro[A, B]

    def changeFieldType(
      source: A => Any,
      target: B => Any,
      converter: DynamicSchemaExpr
    ): MigrationBuilder[A, B] =
      macro MigrationMacros.changeFieldTypeMacro[A, B]
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Macro implementations
  //
  // Each macro extracts field names from selector lambdas and delegates to
  // the string-based builder methods.
  // ─────────────────────────────────────────────────────────────────────────

  import scala.reflect.macros.whitebox
  import scala.reflect.NameTransformer

  /**
   * Extract the last field name from a selector lambda.
   *
   * Walks the lambda body and returns the terminal field name as a string.
   */
  private def extractFieldName(c: whitebox.Context)(path: c.Tree): c.Tree = {
    import c.universe._

    def toPathBody(tree: c.Tree): c.Tree = tree match {
      case q"($_) => $pathBody" => pathBody
      case _                    =>
        c.abort(c.enclosingPosition, s"Expected a lambda expression, got '$tree'")
    }

    def lastFieldName(tree: c.Tree): String = tree match {
      case q"$_.$child" => NameTransformer.decode(child.toString)
      case _            =>
        c.abort(
          c.enclosingPosition,
          s"Unsupported path element: '$tree'. " +
            "Selector macros support field access (_.field) and nested field access (_.outer.inner)."
        )
    }

    val body = toPathBody(path)
    val name = lastFieldName(body)
    q"$name"
  }

  private def getBuilder(c: whitebox.Context): c.Tree = {
    import c.universe._
    q"${c.prefix.tree}.builder"
  }

  def addFieldMacro[A, B, V](c: whitebox.Context)(
    target: c.Expr[B => Any],
    default: c.Expr[V]
  )(schema: c.Expr[Schema[V]]): c.Tree = {
    import c.universe._
    val builder   = getBuilder(c)
    val fieldName = extractFieldName(c)(target.tree)
    q"""
    {
      val _b = $builder
      _b.addField($fieldName, $default)($schema)
    }
    """
  }

  def dropFieldMacro[A, B](c: whitebox.Context)(
    source: c.Expr[A => Any]
  ): c.Tree = {
    import c.universe._
    val builder   = getBuilder(c)
    val fieldName = extractFieldName(c)(source.tree)
    q"""
    {
      val _b = $builder
      _b.dropField($fieldName)
    }
    """
  }

  def renameFieldMacro[A, B](c: whitebox.Context)(
    from: c.Expr[A => Any],
    to: c.Expr[B => Any]
  ): c.Tree = {
    import c.universe._
    val builder  = getBuilder(c)
    val fromName = extractFieldName(c)(from.tree)
    val toName   = extractFieldName(c)(to.tree)
    q"""
    {
      val _b = $builder
      _b.renameField($fromName, $toName)
    }
    """
  }

  def transformFieldMacro[A, B](c: whitebox.Context)(
    from: c.Expr[A => Any],
    to: c.Expr[B => Any],
    transform: c.Expr[DynamicSchemaExpr]
  ): c.Tree = {
    import c.universe._
    val builder  = getBuilder(c)
    val fromName = extractFieldName(c)(from.tree)
    val toName   = extractFieldName(c)(to.tree)
    q"""
    {
      val _b = $builder
      _b.transformField($fromName, $toName, $transform)
    }
    """
  }

  def mandateFieldMacro[A, B, V](c: whitebox.Context)(
    source: c.Expr[A => Any],
    target: c.Expr[B => Any],
    default: c.Expr[V]
  )(schema: c.Expr[Schema[V]]): c.Tree = {
    import c.universe._
    val builder    = getBuilder(c)
    val sourceName = extractFieldName(c)(source.tree)
    val targetName = extractFieldName(c)(target.tree)
    q"""
    {
      val _b = $builder
      _b.mandateField($sourceName, $targetName, $default)($schema)
    }
    """
  }

  def optionalizeFieldMacro[A, B](c: whitebox.Context)(
    source: c.Expr[A => Any],
    target: c.Expr[B => Any]
  ): c.Tree = {
    import c.universe._
    val builder    = getBuilder(c)
    val sourceName = extractFieldName(c)(source.tree)
    val targetName = extractFieldName(c)(target.tree)
    q"""
    {
      val _b = $builder
      _b.optionalizeField($sourceName, $targetName)
    }
    """
  }

  def changeFieldTypeMacro[A, B](c: whitebox.Context)(
    source: c.Expr[A => Any],
    target: c.Expr[B => Any],
    converter: c.Expr[DynamicSchemaExpr]
  ): c.Tree = {
    import c.universe._
    val builder    = getBuilder(c)
    val sourceName = extractFieldName(c)(source.tree)
    val targetName = extractFieldName(c)(target.tree)
    q"""
    {
      val _b = $builder
      _b.changeFieldType($sourceName, $targetName, $converter)
    }
    """
  }
}
