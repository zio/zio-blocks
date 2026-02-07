package zio.blocks.schema.migration.macros

import scala.reflect.macros.whitebox
import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.migration.ToDynamicOptic

object AccessorMacros {

  def deriveImpl[S: c.WeakTypeTag, A: c.WeakTypeTag](
    c: whitebox.Context
  )(selector: c.Expr[S => A]): c.Expr[ToDynamicOptic[S, A]] = {
    import c.universe._

    // Helper to unwrap implicit conversion applications
    // e.g. Wrapper(qual).method -> qual
    def unwrapImplicit(t: Tree): Tree = t match {
      case Apply(_, List(qual)) => qual // Assuming simple wrapper
      case _                    => t
    }

    def extractPath(tree: Tree): List[Tree] = tree match {
      case Function(_, body) => extractPath(body)
      case Block(_, expr)    => extractPath(expr)

      // [FIX] Handle Collection Traversal (.each)
      // Matches: Wrapper(qual).each OR qual.each
      case Select(qual, TermName("each")) =>
        extractPath(unwrapImplicit(qual)) :+ q"_root_.zio.blocks.schema.DynamicOptic.Node.Elements"

      // [FIX] Handle Sum Type / Case Selection (.when[T])
      // Matches: Wrapper(qual).when[T] OR qual.when[T]
      // Note: .when[T] is a TypeApply, it might NOT be wrapped in an Apply if it takes no value args
      case TypeApply(Select(qual, TermName("when")), List(tpe)) =>
        val tagName = tpe.tpe.typeSymbol.name.decodedName.toString
        extractPath(unwrapImplicit(qual)) :+ q"_root_.zio.blocks.schema.DynamicOptic.Node.Case($tagName)"

      // Also match if it IS wrapped in Apply (e.g. if it had implicit args)
      case Apply(TypeApply(Select(qual, TermName("when")), List(tpe)), _) =>
        val tagName = tpe.tpe.typeSymbol.name.decodedName.toString
        extractPath(unwrapImplicit(qual)) :+ q"_root_.zio.blocks.schema.DynamicOptic.Node.Case($tagName)"

      // Standard Field Access
      case Select(qual, TermName(name)) =>
        extractPath(qual) :+ q"_root_.zio.blocks.schema.DynamicOptic.Node.Field($name)"

      case Ident(_) => Nil

      case _ =>
        c.abort(
          c.enclosingPosition,
          s"Unsupported selector expression: $tree. Ensure only field access, .each, or .when is used."
        )
    }

    val nodes     = extractPath(selector.tree)
    val nodesExpr = c.Expr[List[DynamicOptic.Node]](q"List(..$nodes)")

    c.Expr[ToDynamicOptic[S, A]](q"""
      _root_.zio.blocks.schema.migration.ToDynamicOptic(
        _root_.zio.blocks.schema.DynamicOptic(
          _root_.zio.blocks.chunk.Chunk.fromIterable($nodesExpr)
        )
      )
    """)
  }
}
