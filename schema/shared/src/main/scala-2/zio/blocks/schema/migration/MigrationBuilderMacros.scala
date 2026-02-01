package zio.blocks.schema.migration

import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import zio.blocks.schema.DynamicOptic

/**
 * Scala 2 macros for extracting DynamicOptic paths from lambda selectors.
 * Converts selector expressions like `_.field.nested.each` into DynamicOptic
 * instances.
 */
object MigrationBuilderMacros {

  /**
   * Extract a DynamicOptic path from a selector lambda at compile time.
   *
   * Supports:
   *   - Field access: `_.field` -> `DynamicOptic.root.field("field")`
   *   - Nested: `_.a.b.c` ->
   *     `DynamicOptic.root.field("a").field("b").field("c")`
   *   - When: `_.x.when[Case]` -> `DynamicOptic.root.field("x").case_("Case")`
   *   - Each: `_.items.each` -> `DynamicOptic.root.field("items").elements`
   *   - Map keys: `_.map.eachKey` -> `DynamicOptic.root.field("map").mapKeys`
   *   - Map values: `_.map.eachValue` ->
   *     `DynamicOptic.root.field("map").mapValues`
   */
  def extractPath[A, B](selector: A => B): DynamicOptic = macro extractPathImpl[A, B]

  def extractPathImpl[A, B](c: blackbox.Context)(
    selector: c.Expr[A => B]
  )(implicit evA: c.WeakTypeTag[A], evB: c.WeakTypeTag[B]): c.Expr[DynamicOptic] = {
    import c.universe._
    // Reference type tags to avoid unused warning (needed for macro signature)
    val _ = (evA, evB)

    // Extract the body from the lambda function
    def extractBody(tree: Tree): Tree = tree match {
      case Function(_, body) => body
      case Block(_, expr)    => extractBody(expr)
      case _                 => c.abort(c.enclosingPosition, s"Expected lambda expression, got: ${tree.getClass}")
    }

    // Collect path segments from selector tree
    def collectNodes(tree: Tree): List[Tree] = tree match {
      // Terminal: the lambda parameter (identity)
      case Ident(_) => Nil

      // Field selection: _.field
      case Select(parent, TermName(fieldName)) if !isSpecialMethod(fieldName) =>
        collectNodes(parent) :+ q"_root_.zio.blocks.schema.DynamicOptic.Node.Field($fieldName)"

      // .each for sequences
      case Apply(TypeApply(Select(parent, TermName("each")), _), _) =>
        collectNodes(parent) :+ q"_root_.zio.blocks.schema.DynamicOptic.Node.Elements"

      case Select(parent, TermName("each")) =>
        collectNodes(parent) :+ q"_root_.zio.blocks.schema.DynamicOptic.Node.Elements"

      // .eachKey for map keys
      case Apply(TypeApply(Select(parent, TermName("eachKey")), _), _) =>
        collectNodes(parent) :+ q"_root_.zio.blocks.schema.DynamicOptic.Node.MapKeys"

      case Select(parent, TermName("eachKey")) =>
        collectNodes(parent) :+ q"_root_.zio.blocks.schema.DynamicOptic.Node.MapKeys"

      // .eachValue for map values
      case Apply(TypeApply(Select(parent, TermName("eachValue")), _), _) =>
        collectNodes(parent) :+ q"_root_.zio.blocks.schema.DynamicOptic.Node.MapValues"

      case Select(parent, TermName("eachValue")) =>
        collectNodes(parent) :+ q"_root_.zio.blocks.schema.DynamicOptic.Node.MapValues"

      // .when[Case] for enum cases
      case TypeApply(Select(parent, TermName("when")), List(typeTree)) =>
        val caseName = typeTree.tpe.typeSymbol.name.toString
        collectNodes(parent) :+ q"_root_.zio.blocks.schema.DynamicOptic.Node.Case($caseName)"

      // .wrapped[T] for wrapped types
      case TypeApply(Select(parent, TermName("wrapped")), _) =>
        collectNodes(parent) :+ q"_root_.zio.blocks.schema.DynamicOptic.Node.Wrapped"

      // .at(index) for specific index
      case Apply(Select(parent, TermName("at")), List(indexArg)) =>
        collectNodes(parent) :+ q"_root_.zio.blocks.schema.DynamicOptic.Node.AtIndex($indexArg)"

      case _ =>
        c.abort(
          c.enclosingPosition,
          s"Unsupported selector syntax: '${showRaw(tree)}'. " +
            "Supported: _.field, _.a.b.c, _.when[Case], _.each, _.eachKey, _.eachValue, _.wrapped[T], _.at(index)"
        )
    }

    def isSpecialMethod(name: String): Boolean =
      Set("each", "eachKey", "eachValue", "when", "wrapped", "at").contains(name)

    val body  = extractBody(selector.tree)
    val nodes = collectNodes(body)

    if (nodes.isEmpty) {
      c.Expr[DynamicOptic](q"_root_.zio.blocks.schema.DynamicOptic.root")
    } else {
      c.Expr[DynamicOptic](q"_root_.zio.blocks.schema.DynamicOptic(_root_.scala.Vector(..$nodes))")
    }
  }

  /**
   * Validate that a selector lambda is valid for migration paths. Called at
   * compile time; emits an error if the selector is invalid.
   */
  def validateSelector[A, B](selector: A => B): Unit = macro validateSelectorImpl[A, B]

  def validateSelectorImpl[A, B](c: blackbox.Context)(
    selector: c.Expr[A => B]
  )(implicit evA: c.WeakTypeTag[A], evB: c.WeakTypeTag[B]): c.Expr[Unit] = {
    // Just call extractPathImpl to validate; if invalid, it aborts
    extractPathImpl[A, B](c)(selector)(evA, evB)
    c.Expr[Unit](c.universe.reify(()).tree)
  }
}
