package zio.blocks.schema.migration

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

object MigrationMacros {

  def select[A](f: A => Any): DynamicOptic = macro selectImpl

  def selectImpl(c: whitebox.Context)(f: c.Tree): c.Tree = {
    import c.universe._
    def extractPath(tree: Tree): List[String] = tree match {
      case Function(_, body)       => extractPath(body)
      case Select(qualifier, name) => extractPath(qualifier) :+ name.toString
      case _                       => Nil
    }
    val fields = extractPath(f)
    if (fields.isEmpty)
      c.abort(c.enclosingPosition, "select: expected a field selector like _.name")
    val root = q"_root_.zio.blocks.schema.DynamicOptic.root"
    fields.foldLeft(root: Tree) { (acc, name) =>
      q"$acc.field($name)"
    }
  }
}
