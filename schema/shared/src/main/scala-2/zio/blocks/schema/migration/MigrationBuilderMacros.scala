package zio.blocks.schema.migration

// format: off
import scala.reflect.macros.whitebox

private[migration] class MigrationBuilderMacros(val c: whitebox.Context) {
  import c.universe._
  import MigrationMacroTypes._

  // Intermediate representation of a path node
  // Moved to MigrationMacroTypes


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
          case Ident(typeName)     => typeName.decodedName.toString
          case Select(_, typeName) => typeName.decodedName.toString
          case _                   => tpt.toString.split('.').last // Fallback
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
        c.abort(
          tree.pos,
          s"Unsupported selector expression: $tree. Expected partial function like _.field1.field2 or _.items.each"
        )
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
      case PathNode.Case(n)  => (n, path.dropRight(1))
      case PathNode.Elements =>
        c.abort(pos, "Cannot perform field operation on '.each' directly. Target must be a named field or case.")
    }

    (name, mkDynamicOptic(parentPath))
  }

  def addFieldImpl[A: WeakTypeTag, B: WeakTypeTag](
    target: c.Expr[B => Any],
    default: c.Expr[zio.blocks.schema.DynamicValue]
  ): c.Expr[MigrationBuilder[A, B]] = {
    val path               = extractPath(target.tree)
    val (fieldName, optic) = splitPath(path, target.tree.pos)
    val builder            = c.prefix
    c.Expr[MigrationBuilder[A, B]](q"$builder.addField($fieldName, $default, $optic)")
  }

  def dropFieldImpl[A: WeakTypeTag, B: WeakTypeTag](source: c.Expr[A => Any]): c.Expr[MigrationBuilder[A, B]] = {
    val path               = extractPath(source.tree)
    val (fieldName, optic) = splitPath(path, source.tree.pos)
    val builder            = c.prefix
    c.Expr[MigrationBuilder[A, B]](q"$builder.dropField($fieldName, $optic)")
  }

  def renameFieldImpl[A: WeakTypeTag, B: WeakTypeTag](
    from: c.Expr[A => Any],
    to: c.Expr[B => Any]
  ): c.Expr[MigrationBuilder[A, B]] = {
    val fromPath = extractPath(from.tree)
    val toPath   = extractPath(to.tree)

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
      case _                 => c.abort(to.tree.pos, "Target selector in rename must end in a field")
    }

    val builder = c.prefix
    c.Expr[MigrationBuilder[A, B]](q"$builder.renameField($fromName, $toName, $fromOptic)")
  }

  def optionalizeFieldImpl[A: WeakTypeTag, B: WeakTypeTag](
    source: c.Expr[A => Any]
  ): c.Expr[MigrationBuilder[A, B]] = {
    val path               = extractPath(source.tree)
    val (fieldName, optic) = splitPath(path, source.tree.pos)
    val builder            = c.prefix
    c.Expr[MigrationBuilder[A, B]](q"$builder.optionalizeField($fieldName, $optic)")
  }

  def mandateFieldImpl[A: WeakTypeTag, B: WeakTypeTag](
    source: c.Expr[A => Option[_]],
    target: c.Expr[B => Any],
    default: c.Expr[zio.blocks.schema.DynamicValue]
  ): c.Expr[MigrationBuilder[A, B]] = {
    val _                  = target
    val path               = extractPath(source.tree)
    val (fieldName, optic) = splitPath(path, source.tree.pos)
    val builder            = c.prefix
    c.Expr[MigrationBuilder[A, B]](q"$builder.mandateField($fieldName, $default, $optic)")
  }
  // ExtractedAction moved to MigrationMacroTypes


  def buildImpl[A: WeakTypeTag, B: WeakTypeTag]: c.Expr[Migration[A, B]] = {
    import c.universe._

    val builderTree = c.prefix.tree

    // 1. Extract Actions
    val actions = extractActionsFromBuilder(builderTree)

    // 2. Extract Types
    val sourceFields = extractTypeFields(weakTypeOf[A])
    val targetFields = extractTypeFields(weakTypeOf[B])

    // 3. Simulate Transformation
    // Fields existing in both are implicit
    val (handledSource, producedTarget) = simulateTransformation(actions)

    // 4. Calculate Unmapped
    val unmappedSource = sourceFields.diff(handledSource).diff(targetFields)
    val unmappedTarget = targetFields.diff(producedTarget).diff(sourceFields)

    // 5. Report Errors
     val hasOpaque = actions.contains(ExtractedAction.Opaque)

     if (hasOpaque) {
       if (unmappedSource.nonEmpty || unmappedTarget.nonEmpty) {
         c.warning(c.enclosingPosition,
           s"Migration contains opaque/runtime values. Potential issues: " +
           (if (unmappedSource.nonEmpty) s"unmapped source [${unmappedSource.mkString(", ")}] " else "") +
           (if (unmappedTarget.nonEmpty) s"unmapped target [${unmappedTarget.mkString(", ")}]" else "")
         )
       }
     } else {
       if (unmappedSource.nonEmpty) {
         c.error(c.enclosingPosition,
           s"Migration from ${weakTypeOf[A]} to ${weakTypeOf[B]} is incomplete: " +
           s"source fields [${unmappedSource.mkString(", ")}] are not handled."
         )
       }
       if (unmappedTarget.nonEmpty) {
         c.error(c.enclosingPosition,
           s"Migration from ${weakTypeOf[A]} to ${weakTypeOf[B]} is incomplete: " +
           s"target fields [${unmappedTarget.mkString(", ")}] are not produced."
         )
       }
     }

    // 6. Return runtime build call
    c.Expr[Migration[A, B]](q"$builderTree.buildValidating")
  }

  private def extractActionsFromBuilder(tree: Tree): List[ExtractedAction] = {
    def loop(t: Tree, acc: List[ExtractedAction]): List[ExtractedAction] = t match {
      // Simple method: builder.method(args)
      case Apply(Select(qual, name), args) =>
        val action = extractActionFromMethod(name.decodedName.toString, args)
        loop(qual, action :: acc)

      // Generic method: builder.method[T](args)
      case Apply(TypeApply(Select(qual, name), _), args) =>
        val action = extractActionFromMethod(name.decodedName.toString, args)
        loop(qual, action :: acc)

      // Method with two argument lists (explicit + implicit): builder.method[T](args1)(args2)
      case Apply(Apply(TypeApply(Select(qual, name), _), args1), _) =>
        val action = extractActionFromMethod(name.decodedName.toString, args1)
        loop(qual, action :: acc)

      // Method with two argument lists (non-generic): builder.method(args1)(args2)
      case Apply(Apply(Select(qual, name), args1), _) =>
        val action = extractActionFromMethod(name.decodedName.toString, args1)
        loop(qual, action :: acc)

       // Typed/Inlined wrappers
      case Typed(inner, _) => loop(inner, acc)
      case Block(_, expr) => loop(expr, acc)

      case _ => acc
    }
    loop(tree, Nil)
  }

  private def extractActionFromMethod(name: String, args: List[Tree]): ExtractedAction = {
    name match {
      case "renameField" if args.length >= 2 =>
        (extractStringOrSelector(args(0)), extractStringOrSelector(args(1))) match {
          case (Some(f), Some(t)) => ExtractedAction.Rename(f, t)
          case _ => ExtractedAction.Opaque
        }
      case "dropField" | "dropFieldWithDefault" if args.nonEmpty =>
        extractStringOrSelector(args(0)).map(ExtractedAction.Drop).getOrElse(ExtractedAction.Opaque)

      case "addField" | "addFieldWithDefault" if args.nonEmpty =>
        extractStringOrSelector(args(0)).map(ExtractedAction.Add).getOrElse(ExtractedAction.Opaque)

      case "optionalizeField" if args.nonEmpty =>
        extractStringOrSelector(args(0)).map(ExtractedAction.Optionalize).getOrElse(ExtractedAction.Opaque)

      case "mandateField" | "mandateFieldWithDefault" if args.nonEmpty =>
        extractStringOrSelector(args(0)).map(ExtractedAction.Mandate).getOrElse(ExtractedAction.Opaque)

      case "changeFieldType" if args.nonEmpty =>
        // usually checks args(0)
        extractStringOrSelector(args(0)) match {
          case Some(_) if args.size > 1 && args(1).tpe <:< typeOf[String] => // internal overload?
             ExtractedAction.Opaque // internal private overload with string usually
          case Some(f) => ExtractedAction.ChangeType(f, f) // simple assume same name
          case _ => ExtractedAction.Opaque
        }

      case "renameCase" if args.length >= 2 =>
         (extractStringLiteral(args(0)), extractStringLiteral(args(1))) match {
           case (Some(f), Some(t)) => ExtractedAction.RenameCase(f, t)
           case _ => ExtractedAction.Opaque
         }

      case _ => ExtractedAction.Opaque
    }
  }

  // Tries to extract string from literal OR from selector lambda
  private def extractStringOrSelector(tree: Tree): Option[String] = {
    extractStringLiteral(tree).orElse {
       // Check if it is a selector lambda _.field
       // The tree passed here might be the lambda logic itself or an Expr wrapping it
       // In the builder chain, arguments are trees.
       // E.g. .renameField(_.a, _.b) -> args(0) is the lambda tree for _.a
       tryExtractSelector(tree)
    }
  }

  private def extractStringLiteral(tree: Tree): Option[String] = tree match {
    case Literal(Constant(s: String)) => Some(s)
    case Typed(t, _) => extractStringLiteral(t)
    case Block(_, t) => extractStringLiteral(t)
    case _ => None
  }

  // Reuse logic from extractPath, but handle the Lambda tree structure
  private def tryExtractSelector(tree: Tree): Option[String] = {
     tree match {
       // Function(List(ValDef(...)), Select(Ident(...), name))
       case Function(_, _) =>
         // Hack: reuse extractPath which expects Function(List(_), body) but logic separates
         // extractPath implementation expects Function or just body?
         // extractPath(selector: Tree) { selector match case Function ... }
         // So we can pass tree directly.
          try {
             val p = extractPath(tree)
             if (p.nonEmpty) {
               p.last match {
                 case PathNode.Field(n) => Some(n)
                 case PathNode.Case(n) => Some(n)
                 case _ => None
               }
             } else None
          } catch {
             case _: Throwable => None
          }
       case _ => None
     }
  }

  private def extractTypeFields(tpe: Type): Set[String] = {
    tpe.decls.collect {
      case m: MethodSymbol if m.isCaseAccessor => m.name.decodedName.toString
    }.toSet
  }

  private def simulateTransformation(
    actions: List[ExtractedAction]
  ): (Set[String], Set[String]) = {
    var handledSource = Set.empty[String]
    var producedTarget = Set.empty[String]

    actions.foreach {
      case ExtractedAction.Rename(from, to) =>
        handledSource += from
        producedTarget += to
      case ExtractedAction.Drop(name) =>
        handledSource += name
      case ExtractedAction.Add(name) =>
        producedTarget += name
      case ExtractedAction.Optionalize(name) =>
        handledSource += name
        producedTarget += name
      case ExtractedAction.Mandate(name) =>
        handledSource += name
        producedTarget += name
      case ExtractedAction.ChangeType(from, to) =>
        handledSource += from
        producedTarget += to
      case _ => ()
    }
    (handledSource, producedTarget)
  }
}

private[migration] object MigrationMacroTypes {
  sealed trait PathNode
  object PathNode {
    final case class Field(name: String) extends PathNode
    final case object Elements           extends PathNode
    final case class Case(name: String)  extends PathNode
  }

  sealed trait ExtractedAction
  object ExtractedAction {
    case class Rename(from: String, to: String)           extends ExtractedAction
    case class Drop(fieldName: String)                    extends ExtractedAction
    case class Add(fieldName: String)                     extends ExtractedAction
    case class Optionalize(fieldName: String)             extends ExtractedAction
    case class Mandate(fieldName: String)                 extends ExtractedAction
    case class ChangeType(from: String, to: String)       extends ExtractedAction
    case class RenameCase(from: String, to: String)       extends ExtractedAction
    case object Opaque                                    extends ExtractedAction
  }
}
