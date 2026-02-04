package zio.blocks.schema.migration

import scala.language.experimental.macros
import scala.reflect.macros.whitebox
import zio.blocks.schema.DynamicOptic

/**
 * Scala 2 macros for converting selector expressions into DynamicOptic paths.
 *
 * These macros parse selector expressions like:
 *   - `_.field` → `DynamicOptic.root.field("field")`
 *   - `_.a.b.c` → `DynamicOptic.root.field("a").field("b").field("c")`
 *   - `_.items.each` → `DynamicOptic.root.field("items").elements`
 *   - `_.country.when[UK]` → `DynamicOptic.root.field("country").caseOf("UK")`
 */
object SelectorMacros {

  /**
   * Convert a selector expression to a DynamicOptic.
   */
  def toPath[S, A](selector: S => A): DynamicOptic = macro toPathImpl[S, A]

  def toPathImpl[S: c.WeakTypeTag, A: c.WeakTypeTag](c: whitebox.Context)(selector: c.Expr[S => A]): c.Tree = {
    import c.universe._

    // Use type tags for better error messages
    val sourceType = weakTypeOf[S]
    val targetType = weakTypeOf[A]

    def fail(msg: String): Nothing =
      c.abort(c.enclosingPosition, s"$msg (selector: $sourceType => $targetType)")

    def toPathBody(tree: c.Tree): c.Tree = tree match {
      case q"($_) => $pathBody" => pathBody
      case _                    => fail(s"Expected a lambda expression, got '$tree'")
    }

    def getTypeName(tpe: Type): String =
      tpe.dealias.typeSymbol.name.toString

    def toDynamicOptic(tree: c.Tree): c.Tree = tree match {
      // Identity - just the parameter reference
      case Ident(_) =>
        q"_root_.zio.blocks.schema.DynamicOptic.root"

      // Field access: _.field or _.a.b.c
      case q"$parent.$fieldName" =>
        val parentOptic = toDynamicOptic(parent)
        val fieldStr    = fieldName.toString
        q"$parentOptic.field($fieldStr)"

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
      case q"$_[..$_]($parent).when[$caseType]" =>
        val parentOptic = toDynamicOptic(parent)
        val caseName    = getTypeName(caseType.tpe)
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
      case q"$_[..$_]($parent).atKey($key)" =>
        val parentOptic = toDynamicOptic(parent)
        q"$parentOptic.atKey($key)"

      case other =>
        fail(s"Unsupported selector expression: ${showRaw(other)}")
    }

    val pathBody = toPathBody(selector.tree)
    toDynamicOptic(pathBody)
  }
}
