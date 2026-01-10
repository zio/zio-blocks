package zio.blocks.schema.migration


import scala.reflect.macros.whitebox

private[migration] class MigrationBuilderMacros(val c: whitebox.Context) {
  import c.universe._

  // Intermediate representation of a path node
  private sealed trait PathNode
  private object PathNode {
    final case class Field(name: String) extends PathNode
    final case object Elements extends PathNode
    final case class Case(name: String) extends PathNode
  }

  // Helper to extract the list of nodes from a selector expression
  private def extractPath(selector: Tree): List[PathNode] = {
    def loop(tree: Tree, acc: List[PathNode]): List[PathNode] = tree match {
      // _.field
      case Select(qual, name) =>
        val node = name.decodedName.toString match {
          case "each" => PathNode.Elements
          case n      => PathNode.Field(n)
        }
        loop(qual, node :: acc)

      // _.when[Subject]
      case Apply(TypeApply(Select(qual, name), List(tpt)), _) if name.decodedName.toString == "when" =>
        // Extract case name from type `tpt`
        // tpt.tpe might not be available if un-typed, but tpt usually is a TypeTree.
        // We might need to use tpt.toString() or similar if type checking hasn't fully run, 
        // but macros run after typer usually? Whitebox macros run during typing.
        // For simple usage, we can try to get the simple name.
        val caseName = tpt match {
          case Ident(typeName) => typeName.decodedName.toString
          case Select(_, typeName) => typeName.decodedName.toString
          case _ => tpt.toString.split('.').last // Fallback
        }
        loop(qual, PathNode.Case(caseName) :: acc)

      case Ident(_) =>
        // The base identifier
        acc
      case Apply(Select(qual, name), _) =>
         // Handle methods that look like fields (e.g. getters)
         val node = name.decodedName.toString match {
          case "each" => PathNode.Elements
          case n      => PathNode.Field(n)
        }
        loop(qual, node :: acc)
      
      case _ =>
        c.abort(tree.pos, s"Unsupported selector expression: $tree. Expected partial function like _.field1.field2 or _.items.each")
    }

    selector match {
      case Function(List(_), body) =>
        loop(body, Nil)
      case _ =>
        c.abort(selector.pos, "Expected a lambda function (e.g., _.field)")
    }
  }

  // Construct a DynamicOptic tree from the path list
  private def mkDynamicOptic(path: List[PathNode]): Tree = {
    val nodes = path.map {
      case PathNode.Field(name) => q"zio.blocks.schema.DynamicOptic.Node.Field($name)"
      case PathNode.Elements    => q"zio.blocks.schema.DynamicOptic.Node.Elements"
      case PathNode.Case(name)  => q"zio.blocks.schema.DynamicOptic.Node.Case($name)"
    }
    q"zio.blocks.schema.DynamicOptic(scala.collection.immutable.IndexedSeq(..$nodes))"
  }

  private def splitPath(path: List[PathNode], pos: Position): (String, Tree) = {
    if (path.isEmpty) c.abort(pos, "Selector expression must refer to a field (cannot be empty)")
    
    // The last node should be the field/case being operated on.
    // For MigrationBuilder actions (rename/drop/add), we typically operate on a Field or Case Name.
    // However, the action usually takes the parent optic and the name.
    
    val (name, parentPath) = path.last match {
      case PathNode.Field(n) => (n, path.dropRight(1))
      case PathNode.Case(n) => (n, path.dropRight(1))
      case PathNode.Elements => c.abort(pos, "Cannot perform field operation on '.each' directly. Target must be a named field or case.")
    }
    
    (name, mkDynamicOptic(parentPath))
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
    
    if (toPath.isEmpty) c.abort(to.tree.pos, "Target path cannot be empty")
    
    // For rename, 'to' implies the NEW name. If 'to' is selector _.address.newField,
    // we assume the parent path matches 'from' or is ignored?
    // MigrationBuilder.renameField takes (from: String, to: String, at: DynamicOptic).
    // So we invoke renameField(fromName, toName, fromOptic).
    // The user API implies: .renameField(_.address.street, _.address.streetName)
    // We trust fromOptic.
    
    val toName = toPath.last match {
      case PathNode.Field(n) => n
      case _ => c.abort(to.tree.pos, "Target selector in rename must end in a field")
    }
    
    val builder = c.prefix
    c.Expr[MigrationBuilder[A, B]](q"$builder.renameField($fromName, $toName, $fromOptic)")
  }

  def optionalizeFieldImpl[A: WeakTypeTag, B: WeakTypeTag](source: c.Expr[A => Any], target: c.Expr[B => Option[_]]): c.Expr[MigrationBuilder[A, B]] = {
    val _ = target 
    val path = extractPath(source.tree)
    val (fieldName, optic) = splitPath(path, source.tree.pos)
    val builder = c.prefix
    c.Expr[MigrationBuilder[A, B]](q"$builder.optionalizeField($fieldName, $optic)")
  }

  def mandateFieldImpl[A: WeakTypeTag, B: WeakTypeTag](source: c.Expr[A => Option[_]], target: c.Expr[B => Any], default: c.Expr[zio.blocks.schema.DynamicValue]): c.Expr[MigrationBuilder[A, B]] = {
    val _ = target
    val path = extractPath(source.tree)
    val (fieldName, optic) = splitPath(path, source.tree.pos)
    val builder = c.prefix
    c.Expr[MigrationBuilder[A, B]](q"$builder.mandateField($fieldName, $default, $optic)")
  }
}
