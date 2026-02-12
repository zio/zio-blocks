package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema}
import scala.language.experimental.macros
import scala.language.implicitConversions
import scala.reflect.macros.blackbox

/**
 * Scala 2 macros for converting selector lambdas to DynamicOptic instances, and
 * an implicit class that provides the selector-based MigrationBuilder API.
 *
 * Selectors like `_.firstName` or `_.address.street` are parsed at compile time
 * into `DynamicOptic.root.field("firstName")` or
 * `DynamicOptic.root.field("address").field("street")`.
 */
object MigrationBuilderMacros {

  def selectorToOpticImpl(c: blackbox.Context)(f: c.Expr[Any]): c.Expr[DynamicOptic] = {
    import c.universe._

    def extractNodes(tree: Tree, paramName: Name): List[String] =
      tree match {
        case Select(qualifier, name) =>
          extractNodes(qualifier, paramName) :+ name.decodedName.toString
        case Ident(name) if name == paramName =>
          Nil
        case _ =>
          c.abort(
            tree.pos,
            s"Unsupported selector expression. Expected simple field access like _.field or _.field.subfield, " +
              s"got: ${showCode(tree)}"
          )
      }

    val body = f.tree match {
      case Function(List(param), body) =>
        val fieldNames = extractNodes(body, param.name)
        if (fieldNames.isEmpty) {
          c.abort(c.enclosingPosition, "Selector must access at least one field, e.g. _.firstName")
        }
        fieldNames
      case _ =>
        c.abort(c.enclosingPosition, s"Expected a lambda expression like (x: A) => x.field, got: ${showCode(f.tree)}")
    }

    val opticTree = body.foldLeft[Tree](q"_root_.zio.blocks.schema.DynamicOptic.root") { (optic, name) =>
      q"$optic.field($name)"
    }
    c.Expr[DynamicOptic](opticTree)
  }
}

/**
 * Implicit class providing selector-based methods for MigrationBuilder in Scala 2.
 * These macros convert `_.field` selectors to `DynamicOptic` and delegate to
 * the corresponding `*At` methods.
 */
final class MigrationBuilderSelectorOps[A, B](private val builder: MigrationBuilder[A, B]) extends AnyVal {
  // format: off
  def addField[T](selector: B => T)(default: T)(implicit s: Schema[T]): MigrationBuilder[A, B] =
    macro MigrationBuilderSelectorOps.addFieldImpl[A, B, T]

  def dropField(selector: A => Any): MigrationBuilder[A, B] =
    macro MigrationBuilderSelectorOps.dropFieldImpl[A, B]

  def renameField(from: A => Any, toName: String): MigrationBuilder[A, B] =
    macro MigrationBuilderSelectorOps.renameFieldImpl[A, B]

  def mandate[T](selector: A => Option[T])(default: T)(implicit s: Schema[T]): MigrationBuilder[A, B] =
    macro MigrationBuilderSelectorOps.mandateImpl[A, B, T]

  def optionalize(selector: A => Any): MigrationBuilder[A, B] =
    macro MigrationBuilderSelectorOps.optionalizeImpl[A, B]
  // format: on
}

object MigrationBuilderSelectorOps {

  implicit def toSelectorOps[A, B](builder: MigrationBuilder[A, B]): MigrationBuilderSelectorOps[A, B] =
    new MigrationBuilderSelectorOps[A, B](builder)

  def addFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](
    c: blackbox.Context
  )(selector: c.Expr[B => T])(default: c.Expr[T])(s: c.Expr[Schema[T]]): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val optic = MigrationBuilderMacros.selectorToOpticImpl(c)(selector.asInstanceOf[c.Expr[Any]])
    c.Expr[MigrationBuilder[A, B]](
      q"${c.prefix}.builder.addFieldAt[${weakTypeOf[T]}]($optic, $default)($s)"
    )
  }

  def dropFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](
    c: blackbox.Context
  )(selector: c.Expr[A => Any]): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val optic = MigrationBuilderMacros.selectorToOpticImpl(c)(selector.asInstanceOf[c.Expr[Any]])
    c.Expr[MigrationBuilder[A, B]](
      q"${c.prefix}.builder.dropFieldAt($optic)"
    )
  }

  def renameFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](
    c: blackbox.Context
  )(from: c.Expr[A => Any], toName: c.Expr[String]): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val optic = MigrationBuilderMacros.selectorToOpticImpl(c)(from.asInstanceOf[c.Expr[Any]])
    c.Expr[MigrationBuilder[A, B]](
      q"${c.prefix}.builder.renameFieldAt($optic, $toName)"
    )
  }

  def mandateImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](
    c: blackbox.Context
  )(selector: c.Expr[A => Option[T]])(default: c.Expr[T])(s: c.Expr[Schema[T]]): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val optic = MigrationBuilderMacros.selectorToOpticImpl(c)(selector.asInstanceOf[c.Expr[Any]])
    c.Expr[MigrationBuilder[A, B]](
      q"${c.prefix}.builder.mandateAt[${weakTypeOf[T]}]($optic, $default)($s)"
    )
  }

  def optionalizeImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](
    c: blackbox.Context
  )(selector: c.Expr[A => Any]): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val optic = MigrationBuilderMacros.selectorToOpticImpl(c)(selector.asInstanceOf[c.Expr[Any]])
    c.Expr[MigrationBuilder[A, B]](
      q"${c.prefix}.builder.optionalizeAt($optic)"
    )
  }
}
