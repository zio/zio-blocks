package zio.blocks.schema.migration.macros

import zio.blocks.schema.migration.ToDynamicOptic
import scala.reflect.macros.whitebox.Context

object AccessorMacros {
  
  def deriveImpl[S: c.WeakTypeTag, A: c.WeakTypeTag](c: Context)(selector: c.Expr[S => A]): c.Expr[ToDynamicOptic[S, A]] = {
    import c.universe._

    def extractNodes(tree: Tree): List[Tree] = tree match {
      case Function(_, body) => extractNodes(body)
      
      case Select(obj, TermName(name)) => 
        extractNodes(obj) :+ q"_root_.zio.blocks.schema.DynamicOptic.Node.Field($name)"
      
      case TypeApply(Select(obj, TermName("when")), List(tpt)) =>
        val tagName = tpt.tpe.typeSymbol.name.toString
        extractNodes(obj) :+ q"_root_.zio.blocks.schema.DynamicOptic.Node.Case($tagName)"

      case Ident(_) => Nil
      case _ => c.abort(c.enclosingPosition, s"Unsupported selector: $tree")
    }

    val nodeExprs = extractNodes(selector.tree)

    c.Expr[ToDynamicOptic[S, A]](q"""
      new _root_.zio.blocks.schema.migration.ToDynamicOptic[${weakTypeOf[S]}, ${weakTypeOf[A]}] {
        def apply(): _root_.zio.blocks.schema.DynamicOptic = {
          _root_.zio.blocks.schema.DynamicOptic(_root_.scala.collection.immutable.Vector(..$nodeExprs))
        }
      }
    """)
  }
}