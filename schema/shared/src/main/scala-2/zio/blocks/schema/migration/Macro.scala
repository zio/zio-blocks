package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

object Macro {
  def toPath[S, A](selector: S => A): DynamicOptic = macro toPathImpl[S, A]

  def toPathImpl[S: c.WeakTypeTag, A: c.WeakTypeTag](c: whitebox.Context)(selector: c.Expr[S => A]): c.Expr[DynamicOptic] = {
    import c.universe._

    def processPath(tree: Tree): List[Tree] = tree match {
      case Select(qualifier, name) =>
        processPath(qualifier) :+ q"zio.blocks.schema.DynamicOptic.Node.Field(${name.toString})"
      case Apply(Select(qualifier, TermName("at")), List(Literal(Constant(idx: Int)))) =>
        processPath(qualifier) :+ q"zio.blocks.schema.DynamicOptic.Node.AtIndex($idx)"
      case Apply(TypeApply(Select(qualifier, TermName("when")), List(tpe)), _) =>
         // Simplification: extracting minimal info
         val tagName = tpe.tpe.typeSymbol.name.toString
         processPath(qualifier) :+ q"zio.blocks.schema.DynamicOptic.Node.Case($tagName)"
      case Select(qualifier, TermName("each")) =>
         processPath(qualifier) :+ q"zio.blocks.schema.DynamicOptic.Node.Elements"
      case Ident(_) =>
         Nil
      case other =>
        c.abort(c.enclosingPosition, s"Unsupported selector expression: $other")
    }
    
    val nodes = selector.tree match {
      case Function(_, body) => processPath(body)
      case Block(List(), Function(_, body)) => processPath(body)
      case _ => c.abort(c.enclosingPosition, s"Expected a lambda selector, got: ${selector.tree}")
    }

    c.Expr[DynamicOptic](q"zio.blocks.schema.DynamicOptic(Vector(..$nodes))")
  }
  
  def validateMigration[A, B](builder: MigrationBuilder[A, B]): Either[String, Migration[A, B]] = 
    Right(builder.buildPartial) // Validation stub for Scala 2
}
