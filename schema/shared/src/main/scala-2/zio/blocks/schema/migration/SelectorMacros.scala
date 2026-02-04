package zio.blocks.schema.migration

import scala.annotation.nowarn
import scala.reflect.macros.blackbox
import zio.blocks.schema.DynamicOptic

/**
 * Scala 2 macros for converting selector expressions into DynamicOptic paths.
 */
object SelectorMacros {
  import scala.language.experimental.macros

  def toPath[S, A](selector: S => A): DynamicOptic = macro toPathImpl[S, A]

  def extractFieldName[S, A](selector: S => A): String = macro extractFieldNameImpl[S, A]

  @nowarn("msg=never used")
  def extractFieldNameImpl[S: c.WeakTypeTag, A: c.WeakTypeTag](c: blackbox.Context)(
    selector: c.Expr[S => A]
  ): c.Expr[String] = {
    import c.universe._

    def extractLastFieldName(tree: c.Tree): String = tree match {
      case Select(_, fieldName) => fieldName.decodedName.toString
      case Ident(_)             => c.abort(tree.pos, "Selector must access at least one field")
      case _                    => c.abort(tree.pos, s"Cannot extract field name from: $tree")
    }

    val pathBody = selector.tree match {
      case q"($_) => $body" => body
      case _                => c.abort(selector.tree.pos, s"Expected a lambda expression")
    }

    val fieldName = extractLastFieldName(pathBody)
    c.Expr[String](q"$fieldName")
  }

  @nowarn("msg=never used")
  def toPathImpl[S: c.WeakTypeTag, A: c.WeakTypeTag](c: blackbox.Context)(
    selector: c.Expr[S => A]
  ): c.Expr[DynamicOptic] = {
    import c.universe._

    def fail(msg: String): Nothing =
      c.abort(c.enclosingPosition, msg)

    def toPathBody(tree: c.Tree): c.Tree = tree match {
      case q"($_) => $body" => body
      case _                => fail(s"Expected a lambda expression, got '$tree'")
    }

    def toDynamicOptic(tree: c.Tree): c.Tree = tree match {
      // Identity - just the parameter reference
      case Ident(_) =>
        q"_root_.zio.blocks.schema.DynamicOptic.root"

      // Field access: _.field or _.a.b.c
      case Select(parent, fieldName) =>
        val parentOptic = toDynamicOptic(parent)
        val fieldNameStr = fieldName.decodedName.toString
        q"$parentOptic.field($fieldNameStr)"

      // Collection traversal: _.items.each
      case q"$_[..$_]($parent).each" =>
        val parentOptic = toDynamicOptic(parent)
        q"$parentOptic.elements"

      // Map key traversal: _.map.eachKey
      case q"$_[..$_]($parent).eachKey" =>
        val parentOptic = toDynamicOptic(parent)
        q"$parentOptic.mapKeys"

      // Map value traversal: _.map.eachValue
      case q"$_[..$_]($parent).eachValue" =>
        val parentOptic = toDynamicOptic(parent)
        q"$parentOptic.mapValues"

      // Case selection: _.variant.when[CaseType]
      case q"$_[..$_]($parent).when[$caseTree]" =>
        val parentOptic = toDynamicOptic(parent)
        val caseName = caseTree.tpe.dealias.typeSymbol.name.decodedName.toString
        q"$parentOptic.caseOf($caseName)"

      // Wrapper unwrap: _.wrapper.wrapped[Inner]
      case q"$_[..$_]($parent).wrapped[$_]" =>
        val parentOptic = toDynamicOptic(parent)
        q"$parentOptic.wrapped"

      // Index access: _.seq.at(0)
      case q"$_[..$_]($parent).at($index)" =>
        val parentOptic = toDynamicOptic(parent)
        q"$parentOptic.at($index)"

      // Map key access: _.map.atKey(key)
      case q"$_[..$_]($parent).atKey($key)($_)" =>
        val parentOptic = toDynamicOptic(parent)
        q"$parentOptic.atKey($key)"

      case other =>
        fail(s"Unsupported selector expression: $other")
    }

    val pathBody = toPathBody(selector.tree)
    val result = toDynamicOptic(pathBody)
    c.Expr[DynamicOptic](result)
  }
}
