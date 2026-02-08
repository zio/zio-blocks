package zio.blocks.schema.migration

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait MigrationBuilderVersionSpecific[A, B] { self: MigrationBuilder[A, B] =>

  /**
   * Rename a field using a selector. Example:
   * `builder.renameField(_.name, "fullName")`
   */
  def renameField(from: A => Any, toName: String): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.renameFieldImpl[A, B]

  /**
   * Drop a field using a selector. Example:
   * `builder.dropField(_.email, reverseDefault)`
   */
  def dropField(from: A => Any, reverseDefault: MigrationExpr): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.dropFieldImpl[A, B]

  /**
   * Transform a field using a selector. Example:
   * `builder.transformField(_.age, expr, reverseExpr)`
   */
  def transformField(field: A => Any, expr: MigrationExpr, reverseExpr: MigrationExpr): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.transformFieldImpl[A, B]

  /** Make an optional field required using a selector. */
  def mandateField(field: A => Any, default: MigrationExpr): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.mandateFieldImpl[A, B]

  /** Make a required field optional using a selector. */
  def optionalizeField(field: A => Any, defaultForNone: MigrationExpr): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.optionalizeFieldImpl[A, B]

  /** Change the type of a field using a selector. */
  def changeFieldType(
    field: A => Any,
    coercion: MigrationExpr,
    reverseCoercion: MigrationExpr
  ): MigrationBuilder[A, B] = macro MigrationBuilderMacros.changeFieldTypeImpl[A, B]
}

private object MigrationBuilderMacros {

  private def extractFieldInfo(c: blackbox.Context)(selector: c.Tree): (c.Tree, String) = {
    import c.universe._

    def loop(tree: Tree, acc: List[String]): List[String] = tree match {
      case Select(qualifier, name) =>
        loop(qualifier, scala.reflect.NameTransformer.decode(name.toString) :: acc)
      case _: Ident => acc
      case _        =>
        c.abort(
          c.enclosingPosition,
          s"Expected a simple field selector like _.fieldName, got: ${showCode(tree)}"
        )
    }

    val body = selector match {
      case Function(_, body) => body
      case _                 =>
        c.abort(
          c.enclosingPosition,
          s"Expected a lambda expression, got: ${showCode(selector)}"
        )
    }

    val fieldNames = loop(body, Nil)
    if (fieldNames.isEmpty) {
      c.abort(c.enclosingPosition, "Selector must reference at least one field")
    }

    if (fieldNames.size == 1) {
      (q"_root_.zio.blocks.schema.DynamicOptic.root", fieldNames.head)
    } else {
      val path = fieldNames.init.foldLeft(q"_root_.zio.blocks.schema.DynamicOptic.root": Tree) { (acc, name) =>
        q"$acc.field($name)"
      }
      (path, fieldNames.last)
    }
  }

  def renameFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context)(
    from: c.Expr[A => Any],
    toName: c.Expr[String]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val (pathTree, fieldName) = extractFieldInfo(c)(from.tree)
    c.Expr[MigrationBuilder[A, B]](
      q"${c.prefix.tree}.renameFieldAt($pathTree, $fieldName, ${toName.tree})"
    )
  }

  def dropFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context)(
    from: c.Expr[A => Any],
    reverseDefault: c.Expr[MigrationExpr]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val (pathTree, fieldName) = extractFieldInfo(c)(from.tree)
    c.Expr[MigrationBuilder[A, B]](
      q"${c.prefix.tree}.dropFieldAt($pathTree, $fieldName, ${reverseDefault.tree})"
    )
  }

  def transformFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context)(
    field: c.Expr[A => Any],
    expr: c.Expr[MigrationExpr],
    reverseExpr: c.Expr[MigrationExpr]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val (pathTree, fieldName) = extractFieldInfo(c)(field.tree)
    c.Expr[MigrationBuilder[A, B]](
      q"${c.prefix.tree}.transformFieldAt($pathTree, $fieldName, ${expr.tree}, ${reverseExpr.tree})"
    )
  }

  def mandateFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context)(
    field: c.Expr[A => Any],
    default: c.Expr[MigrationExpr]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val (pathTree, fieldName) = extractFieldInfo(c)(field.tree)
    c.Expr[MigrationBuilder[A, B]](
      q"${c.prefix.tree}.mandateFieldAt($pathTree, $fieldName, ${default.tree})"
    )
  }

  def optionalizeFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context)(
    field: c.Expr[A => Any],
    defaultForNone: c.Expr[MigrationExpr]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val (pathTree, fieldName) = extractFieldInfo(c)(field.tree)
    c.Expr[MigrationBuilder[A, B]](
      q"${c.prefix.tree}.optionalizeFieldAt($pathTree, $fieldName, ${defaultForNone.tree})"
    )
  }

  def changeFieldTypeImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context)(
    field: c.Expr[A => Any],
    coercion: c.Expr[MigrationExpr],
    reverseCoercion: c.Expr[MigrationExpr]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val (pathTree, fieldName) = extractFieldInfo(c)(field.tree)
    c.Expr[MigrationBuilder[A, B]](
      q"${c.prefix.tree}.changeFieldTypeAt($pathTree, $fieldName, ${coercion.tree}, ${reverseCoercion.tree})"
    )
  }
}
