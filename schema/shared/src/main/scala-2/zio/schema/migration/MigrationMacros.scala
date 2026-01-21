package zio.schema.migration

import scala.reflect.macros.blackbox
import zio.blocks.schema.SchemaExpr

class MigrationMacros(val c: blackbox.Context) {
  import c.universe._

  def addFieldImpl[A: WeakTypeTag, B: WeakTypeTag](target: c.Expr[B => Any], default: c.Expr[SchemaExpr[A, _]]): c.Expr[MigrationBuilder[A, B]] = {
    val builder = c.prefix
    val opticVal = extractPath(target.tree)
    c.Expr[MigrationBuilder[A, B]](q"$builder.withAction(zio.schema.migration.MigrationAction.AddField($opticVal, $default))")
  }

  def dropFieldImpl[A: WeakTypeTag, B: WeakTypeTag](source: c.Expr[A => Any], defaultForReverse: c.Expr[SchemaExpr[B, _]]): c.Expr[MigrationBuilder[A, B]] = {
    val builder = c.prefix
    val opticVal = extractPath(source.tree)
    c.Expr[MigrationBuilder[A, B]](q"$builder.withAction(zio.schema.migration.MigrationAction.DropField($opticVal, $defaultForReverse))")
  }

  def renameFieldImpl[A: WeakTypeTag, B: WeakTypeTag](from: c.Expr[A => Any], to: c.Expr[B => Any]): c.Expr[MigrationBuilder[A, B]] = {
    val builder = c.prefix
    val fromOptic = extractPath(from.tree)
    val toName = extractLeafName(to.tree)
    c.Expr[MigrationBuilder[A, B]](q"$builder.withAction(zio.schema.migration.MigrationAction.Rename($fromOptic, $toName))")
  }

  def transformValueImpl[A, B, S, T](path: c.Expr[A => S], expr: c.Expr[SchemaExpr[S, T]])(implicit aTag: WeakTypeTag[A], bTag: WeakTypeTag[B], sTag: WeakTypeTag[S], tTag: WeakTypeTag[T]): c.Expr[MigrationBuilder[A, B]] = {
    val _ = (aTag, bTag, sTag, tTag)
    val builder = c.prefix
    val opticVal = extractPath(path.tree)
    c.Expr[MigrationBuilder[A, B]](q"$builder.withAction(zio.schema.migration.MigrationAction.TransformValue($opticVal, $expr.asInstanceOf[zio.blocks.schema.SchemaExpr[Any, Any]]))")
  }

  def mandateImpl[A, B, S](path: c.Expr[A => Option[S]])(implicit aTag: WeakTypeTag[A], bTag: WeakTypeTag[B], sTag: WeakTypeTag[S]): c.Expr[MigrationBuilder[A, B]] = {
    val _ = (aTag, bTag, sTag)
    val builder = c.prefix
    val opticVal = extractPath(path.tree)
    c.Expr[MigrationBuilder[A, B]](q"$builder.withAction(zio.schema.migration.MigrationAction.Mandate($opticVal, zio.blocks.schema.SchemaExpr.Literal((), zio.blocks.schema.Schema.unit)))")
  }

  def optionalizeImpl[A, B, S](path: c.Expr[A => S])(implicit aTag: WeakTypeTag[A], bTag: WeakTypeTag[B], sTag: WeakTypeTag[S]): c.Expr[MigrationBuilder[A, B]] = {
    val _ = (aTag, bTag, sTag)
    val builder = c.prefix
    val opticVal = extractPath(path.tree)
    c.Expr[MigrationBuilder[A, B]](q"$builder.withAction(zio.schema.migration.MigrationAction.Optionalize($opticVal))")
  }

  def transformElementsImpl[A, B, S, T](path: c.Expr[A => Seq[S]], migration: c.Expr[Migration[S, T]])(implicit aTag: WeakTypeTag[A], bTag: WeakTypeTag[B], sTag: WeakTypeTag[S], tTag: WeakTypeTag[T]): c.Expr[MigrationBuilder[A, B]] = {
    val _ = (aTag, bTag, sTag, tTag)
    val builder = c.prefix
    val opticVal = extractPath(path.tree)
    c.Expr[MigrationBuilder[A, B]](q"$builder.withAction(zio.schema.migration.MigrationAction.TransformElements($opticVal, $migration.dynamicMigration))")
  }

  def transformKeysImpl[A, B, K, V, K2](path: c.Expr[A => Map[K, V]], migration: c.Expr[Migration[K, K2]])(implicit aTag: WeakTypeTag[A], bTag: WeakTypeTag[B], kTag: WeakTypeTag[K], vTag: WeakTypeTag[V], k2Tag: WeakTypeTag[K2]): c.Expr[MigrationBuilder[A, B]] = {
    val _ = (aTag, bTag, kTag, vTag, k2Tag)
    val builder = c.prefix
    val opticVal = extractPath(path.tree)
    c.Expr[MigrationBuilder[A, B]](q"$builder.withAction(zio.schema.migration.MigrationAction.TransformKeys($opticVal, $migration.dynamicMigration))")
  }

  def transformValuesImpl[A, B, K, V, V2](path: c.Expr[A => Map[K, V]], migration: c.Expr[Migration[V, V2]])(implicit aTag: WeakTypeTag[A], bTag: WeakTypeTag[B], kTag: WeakTypeTag[K], vTag: WeakTypeTag[V], v2Tag: WeakTypeTag[V2]): c.Expr[MigrationBuilder[A, B]] = {
    val _ = (aTag, bTag, kTag, vTag, v2Tag)
    val builder = c.prefix
    val opticVal = extractPath(path.tree)
    c.Expr[MigrationBuilder[A, B]](q"$builder.withAction(zio.schema.migration.MigrationAction.TransformValues($opticVal, $migration.dynamicMigration))")
  }

  def renameCaseImpl[A: WeakTypeTag, B: WeakTypeTag](path: c.Expr[A => Any], from: c.Expr[String], to: c.Expr[String]): c.Expr[MigrationBuilder[A, B]] = {
    val builder = c.prefix
    val opticVal = extractPath(path.tree)
    c.Expr[MigrationBuilder[A, B]](q"$builder.withAction(zio.schema.migration.MigrationAction.RenameCase($opticVal, $from, $to))")
  }

  def transformCaseImpl[A, B, S, T](path: c.Expr[A => S], migration: c.Expr[Migration[S, T]])(implicit aTag: WeakTypeTag[A], bTag: WeakTypeTag[B], sTag: WeakTypeTag[S], tTag: WeakTypeTag[T]): c.Expr[MigrationBuilder[A, B]] = {
    val _ = (aTag, bTag, sTag, tTag)
    val builder = c.prefix
    val opticVal = extractPath(path.tree)
    c.Expr[MigrationBuilder[A, B]](q"$builder.withAction(zio.schema.migration.MigrationAction.TransformCase($opticVal, $migration.dynamicMigration))")
  }

  def changeTypeImpl[A, B, S](path: c.Expr[A => S], converter: c.Expr[SchemaExpr[S, _]])(implicit aTag: WeakTypeTag[A], bTag: WeakTypeTag[B], sTag: WeakTypeTag[S]): c.Expr[MigrationBuilder[A, B]] = {
    val _ = (aTag, bTag, sTag)
    val builder = c.prefix
    val opticVal = extractPath(path.tree)
    c.Expr[MigrationBuilder[A, B]](q"$builder.withAction(zio.schema.migration.MigrationAction.ChangeType($opticVal, $converter.asInstanceOf[zio.blocks.schema.SchemaExpr[Any, Any]]))")
  }

  def joinImpl[A, B](at: c.Expr[B => Any], combiner: c.Expr[SchemaExpr[_, _]], sources: c.Expr[A => Any]*)(implicit aTag: WeakTypeTag[A], bTag: WeakTypeTag[B]): c.Expr[MigrationBuilder[A, B]] = {
    val builder = c.prefix
    val atOptic = extractPath(at.tree)
    val sourceOptics = sources.map(s => extractPath(s.tree))
    c.Expr[MigrationBuilder[A, B]](q"$builder.withAction(zio.schema.migration.MigrationAction.Join($atOptic, Vector(..$sourceOptics), $combiner.asInstanceOf[zio.blocks.schema.SchemaExpr[Any, Any]]))")
  }

  def splitImpl[A, B](at: c.Expr[A => Any], splitter: c.Expr[SchemaExpr[_, _]], targets: c.Expr[B => Any]*)(implicit aTag: WeakTypeTag[A], bTag: WeakTypeTag[B]): c.Expr[MigrationBuilder[A, B]] = {
    val builder = c.prefix
    val atOptic = extractPath(at.tree)
    val targetOptics = targets.map(t => extractPath(t.tree))
    c.Expr[MigrationBuilder[A, B]](q"$builder.withAction(zio.schema.migration.MigrationAction.Split($atOptic, Vector(..$targetOptics), $splitter.asInstanceOf[zio.blocks.schema.SchemaExpr[Any, Any]]))")
  }

  private def extractPath(tree: Tree): Tree = {
    // Removed unused import: import zio.blocks.schema.DynamicOptic
    
    def loop(t: Tree, acc: List[Tree]): List[Tree] = t match {
      // Handle "each": x.items.each
      case Select(qual, TermName("each")) =>
        val node = q"zio.blocks.schema.DynamicOptic.Node.Elements"
        loop(qual, node :: acc)

      // Handle standard Select: x.name
      case Select(qual, name) =>
        val node = q"zio.blocks.schema.DynamicOptic.Node.Field(${name.toString})"
        loop(qual, node :: acc)
      
      // Handle "when[T]"
      case Apply(TypeApply(Select(qual, TermName("when")), List(tpe)), _) =>
         val typeName = tpe.toString // Simplistic extraction
         val node = q"zio.blocks.schema.DynamicOptic.Node.Case($typeName)"
         loop(qual, node :: acc)

      // Handle implicit wrappers (e.g. extension methods converted to implicit class calls)
      case Apply(_, List(arg)) => loop(arg, acc)

      case Ident(_) => acc // Param
      case Annotated(q"new scala.unchecked()", expr) => loop(expr, acc)
      case Block(_, expr) => loop(expr, acc)
      
      case _ => c.abort(c.enclosingPosition, s"Unsupported selector: $t")
    }

    tree match {
      case Function(_, body) =>
        val nodes = loop(body, Nil)
        q"zio.blocks.schema.DynamicOptic(Vector(..$nodes))"
      case _ => c.abort(c.enclosingPosition, "Selector must be a lambda")
    }
  }

  private def extractLeafName(tree: Tree): Tree = {
    tree match {
      case Function(_, body) => 
         body match {
           case Select(_, name) => q"${name.toString}"
           case _ => c.abort(c.enclosingPosition, "Selector must end in a field")
         }
      case _ => c.abort(c.enclosingPosition, "Selector must be a lambda")
    }
  }
}
