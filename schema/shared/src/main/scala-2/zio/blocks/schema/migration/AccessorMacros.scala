package zio.blocks.schema.migration.macros

import zio.blocks.schema.migration.ToDynamicOptic
import scala.reflect.macros.whitebox.Context

object AccessorMacros {
  
  /**
   * ম্যাক্রো ইমপ্লিমেন্টেশন। এখানে ফুললি কোয়ালিফাইড পাথ ব্যবহার করা হয়েছে।
   */
  def deriveImpl[S: c.WeakTypeTag, A: c.WeakTypeTag](c: Context)(selector: c.Expr[S => A]): c.Expr[ToDynamicOptic[S, A]] = {
    import c.universe._

    def extractPath(tree: Tree): List[String] = tree match {
      case Function(_, body) => extractPath(body)
      case Select(obj, TermName(name)) => extractPath(obj) :+ name
      case Ident(_) => Nil
      case _ => c.abort(c.enclosingPosition, s"Unsupported selector expression: $tree")
    }

    val paths = extractPath(selector.tree)
    val nodeExprs = paths.map(name => q"_root_.zio.blocks.schema.DynamicOptic.Node.Field($name)")

    c.Expr[ToDynamicOptic[S, A]](q"""
      new _root_.zio.blocks.schema.migration.ToDynamicOptic[${weakTypeOf[S]}, ${weakTypeOf[A]}] {
        def apply(): _root_.zio.blocks.schema.DynamicOptic = {
          _root_.zio.blocks.schema.DynamicOptic(_root_.scala.collection.immutable.Vector(..$nodeExprs))
        }
      }
    """)
  }
}