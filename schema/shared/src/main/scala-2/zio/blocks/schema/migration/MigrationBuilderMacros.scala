package zio.blocks.schema.migration


import scala.reflect.macros.whitebox

private[migration] class MigrationBuilderMacros(val c: whitebox.Context) {
  import c.universe._

  // Helper to extract the list of strings (path) from a selector expression
  private def extractPath(selector: Tree): List[String] = {
    def loop(tree: Tree, acc: List[String]): List[String] = tree match {
      case Select(qual, name) =>
        loop(qual, name.decodedName.toString :: acc)
      case Ident(_) =>
        // The base identifier (the argument of the function)
        acc
      case Apply(Select(qual, name), _) =>
        loop(qual, name.decodedName.toString :: acc)
      case _ =>
        c.abort(tree.pos, s"Unsupported selector expression: $tree. Expected partial function like _.field1.field2")
    }

    selector match {
      case Function(List(_), body) =>
        loop(body, Nil)
      case _ =>
        c.abort(selector.pos, "Expected a lambda function (e.g., _.field)")
    }
  }

  // Construct a DynamicOptic tree from the path list
  private def mkDynamicOptic(path: List[String]): Tree = {
    val nodes = path.map { name =>
      q"zio.blocks.schema.DynamicOptic.Node.Field($name)"
    }
    q"zio.blocks.schema.DynamicOptic(scala.collection.immutable.IndexedSeq(..$nodes))"
  }

  private def splitPath(path: List[String], pos: Position): (String, Tree) = {
    if (path.isEmpty) c.abort(pos, "Selector expression must refer to a field (cannot be empty)")
    val fieldName = path.last
    val parentPath = path.dropRight(1)
    (fieldName, mkDynamicOptic(parentPath))
  }

  def addFieldImpl[A: WeakTypeTag, B: WeakTypeTag](target: c.Expr[B => Any], default: c.Expr[zio.blocks.schema.DynamicValue]): c.Expr[MigrationBuilder[A, B]] = {
    val path = extractPath(target.tree)
    val (fieldName, optic) = splitPath(path, target.tree.pos)
    val builder = c.prefix
    c.Expr[MigrationBuilder[A, B]](q"$builder.addField($fieldName, $default, $optic)")
  }

  def dropFieldImpl[A: WeakTypeTag, B: WeakTypeTag](source: c.Expr[A => Any]): c.Expr[MigrationBuilder[A, B]] = {
    val path = extractPath(source.tree)
    val (fieldName, optic) = splitPath(path, source.tree.pos)
    val builder = c.prefix
    c.Expr[MigrationBuilder[A, B]](q"$builder.dropField($fieldName, $optic)")
  }

  def renameFieldImpl[A: WeakTypeTag, B: WeakTypeTag](from: c.Expr[A => Any], to: c.Expr[B => Any]): c.Expr[MigrationBuilder[A, B]] = {
    val fromPath = extractPath(from.tree)
    val toPath = extractPath(to.tree)
    
    val (fromName, fromOptic) = splitPath(fromPath, from.tree.pos)
    
    // For rename, 'to' is a simple String in the underlying action
    if (toPath.isEmpty) c.abort(to.tree.pos, "Target path cannot be empty")
    val toName = toPath.last
    
    val builder = c.prefix
    c.Expr[MigrationBuilder[A, B]](q"$builder.renameField($fromName, $toName, $fromOptic)")
  }

  def optionalizeFieldImpl[A: WeakTypeTag, B: WeakTypeTag](source: c.Expr[A => Any], target: c.Expr[B => Option[_]]): c.Expr[MigrationBuilder[A, B]] = {
    // target is not used for logic, only for type checking constraint (B => Option[_]) implicit in signature
    val _ = target 
    val path = extractPath(source.tree)
    val (fieldName, optic) = splitPath(path, source.tree.pos)
    val builder = c.prefix
    c.Expr[MigrationBuilder[A, B]](q"$builder.optionalizeField($fieldName, $optic)")
  }

  def mandateFieldImpl[A: WeakTypeTag, B: WeakTypeTag](source: c.Expr[A => Option[_]], target: c.Expr[B => Any], default: c.Expr[zio.blocks.schema.DynamicValue]): c.Expr[MigrationBuilder[A, B]] = {
    // target ignored
    val _ = target
    val path = extractPath(source.tree)
    val (fieldName, optic) = splitPath(path, source.tree.pos)
    val builder = c.prefix
    c.Expr[MigrationBuilder[A, B]](q"$builder.mandateField($fieldName, $default, $optic)")
  }
}
