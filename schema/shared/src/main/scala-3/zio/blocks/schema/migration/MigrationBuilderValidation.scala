package zio.blocks.schema.migration

// format: off
import scala.quoted.*
import zio.blocks.schema._

/**
 * Compile-time validation for MigrationBuilder.build.
 *
 * This macro walks the builder chain AST to extract actions and validates that
 * all source fields are handled and all target fields are produced.
 */
private[migration] object MigrationBuilderValidation {

  /**
   * Represents an action extracted from the builder chain at compile time.
   */
  sealed trait ExtractedAction
  object ExtractedAction {
    case class Rename(from: String, to: String)           extends ExtractedAction
    case class Drop(fieldName: String)                    extends ExtractedAction
    case class Add(fieldName: String)                     extends ExtractedAction
    case class Optionalize(fieldName: String)             extends ExtractedAction
    case class Mandate(fieldName: String)                 extends ExtractedAction
    case class ChangeType(from: String, to: String)       extends ExtractedAction
    case class RenameCase(from: String, to: String)       extends ExtractedAction
    case object Opaque                                    extends ExtractedAction // Runtime value, can't analyze
  }

  /**
   * Main entry point: validates the builder chain and builds the migration.
   *
   * Walks the AST of the builder expression to extract all actions, then
   * validates that the transformation from source to target is complete.
   */
  def validateBuilderImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]]
  )(using Quotes): Expr[Migration[A, B]] = {
    import quotes.reflect.*

    // Extract field names from source and target types
    val sourceFields = extractTypeFields[A]
    val targetFields = extractTypeFields[B]

    // Walk the builder chain to extract actions
    val actions = extractActionsFromBuilder(builder.asTerm)

    // Check if we hit any opaque values
    val hasOpaqueActions = actions.contains(ExtractedAction.Opaque)

    // Simulate the field transformation
    val (handledSourceFields, producedTargetFields) = simulateTransformation(sourceFields, actions)

    // Calculate unmapped fields
    val unmappedSource = sourceFields.diff(handledSourceFields).diff(targetFields)
    val unmappedTarget = targetFields.diff(producedTargetFields).diff(sourceFields)

    // Report compile-time errors (or warnings if opaque)
    if (hasOpaqueActions) {
      // Best effort: we couldn't fully analyze, so warn
      if (unmappedSource.nonEmpty || unmappedTarget.nonEmpty) {
        report.warning(
          s"Migration contains runtime values that prevent full analysis. " +
            s"Potential issues: " +
            (if (unmappedSource.nonEmpty) s"unmapped source fields [${unmappedSource.mkString(", ")}] " else "") +
            (if (unmappedTarget.nonEmpty) s"unmapped target fields [${unmappedTarget.mkString(", ")}]" else "")
        )
      }
    } else {
      // Strict mode: we fully analyzed, so error on issues
      if (unmappedSource.nonEmpty) {
        report.error(
          s"Migration from ${Type.show[A]} to ${Type.show[B]} is incomplete: " +
            s"source fields [${unmappedSource.mkString(", ")}] are not handled. " +
            "Add dropField() or renameField() for these fields."
        )
      }
      if (unmappedTarget.nonEmpty) {
        report.error(
          s"Migration from ${Type.show[A]} to ${Type.show[B]} is incomplete: " +
            s"target fields [${unmappedTarget.mkString(", ")}] are not produced. " +
            "Add addField() or renameField() for these fields."
        )
      }
    }

    // Build the migration (will also do runtime validation as backup)
    '{ $builder.buildValidating }
  }

  /**
   * Walk the builder chain AST and extract all actions.
   * 
   * Handles Scala 3 inline expansion patterns where:
   * - Method names become `inline$methodName$suffix`
   * - Methods are called via Ident (not Select on receiver)
   * - Builder is passed as first arg, bound to ValDef
   * - String literals wrapped in Inlined(Literal(...))
   */
  private def extractActionsFromBuilder(using Quotes)(term: quotes.reflect.Term): List[ExtractedAction] = {
    import quotes.reflect.*

    // Extract the base method name from inline-expanded names like "inline$renameField$i2"
    def normalizeMethodName(name: String): String = {
      if (name.startsWith("inline$")) {
        val withoutPrefix = name.stripPrefix("inline$")
        // Find the pattern: inline$methodName$suffix -> methodName
        val endIdx = withoutPrefix.lastIndexOf('$')
        if (endIdx > 0) withoutPrefix.substring(0, endIdx) else withoutPrefix
      } else name
    }

    def walk(t: Term, acc: List[ExtractedAction]): List[ExtractedAction] = t match {
      // Standard method call: builder.methodName(args)
      case Apply(Select(receiver, methodName), args) =>
        val actionOpt = extractActionFromMethod(methodName, args)
        walk(receiver, actionOpt.toList ++ acc)

      // Inline-expanded pattern: methodName[T](builder)(stringArg1, stringArg2, opticArg)
      case Apply(Apply(TypeApply(Ident(inlineName), _), List(builderArg)), args) =>
        val methodName = normalizeMethodName(inlineName)
        val actionOpt = extractActionFromMethod(methodName, args)
        walk(builderArg, actionOpt.toList ++ acc)

      // Inline-expanded pattern without TypeApply
      case Apply(Apply(Ident(inlineName), List(builderArg)), args) =>
        val methodName = normalizeMethodName(inlineName)
        val actionOpt = extractActionFromMethod(methodName, args)
        walk(builderArg, actionOpt.toList ++ acc)

      // Generic method call: builder.methodName[T](args)
      case Apply(TypeApply(Select(receiver, methodName), _), args) =>
        val actionOpt = extractActionFromMethod(methodName, args)
        walk(receiver, actionOpt.toList ++ acc)

      // Multiple argument lists: builder.methodName(args1)(args2)
      case Apply(Apply(Select(receiver, methodName), args1), args2) =>
        val actionOpt = extractActionFromMethod(methodName, args1 ++ args2)
        walk(receiver, actionOpt.toList ++ acc)

      // Generic multiple argument lists: builder.methodName[T](args1)(args2)
      case Apply(Apply(TypeApply(Select(receiver, methodName), _), args1), args2) =>
        val actionOpt = extractActionFromMethod(methodName, args1 ++ args2)
        walk(receiver, actionOpt.toList ++ acc)
      
      // Three argument lists
      case Apply(Apply(Apply(Select(receiver, methodName), args1), args2), args3) =>
        val actionOpt = extractActionFromMethod(methodName, args1 ++ args2 ++ args3)
        walk(receiver, actionOpt.toList ++ acc)

      // Inlined wrapper - recurse into body
      case Inlined(_, bindings, inner) =>
        // Also process bindings - they may contain ValDefs we need to follow
        val fromBindings = bindings.flatMap {
          case vd: ValDef => vd.rhs match {
            case Some(rhs: Term) => walk(rhs, Nil)
            case _ => Nil
          }
          case _ => Nil
        }
        walk(inner, fromBindings.toList ++ acc)

      // Block with statements and expression
      case Block(stats, expr) =>
        // Walk through ValDefs in stats to find builder chains
        val fromStats = stats.flatMap {
          case vd: ValDef => vd.rhs match {
            case Some(rhs: Term) => walk(rhs, Nil)
            case _ => Nil
          }
          case t: Term => walk(t, Nil)
          case _ => Nil
        }
        walk(expr, fromStats.toList ++ acc)

      // Typed wrapper
      case Typed(inner, _) =>
        walk(inner, acc)
        
      // Ident - could be a reference to a bound builder variable
      case Ident(_) =>
        acc

      case _ =>
        acc
    }

    walk(term, Nil)
  }

  /**
   * Extract an action from a method call.
   * Returns None for non-migration methods (builder, create, etc.)
   * Returns Some(Opaque) for migration methods where we can't extract the field names
   */
  /**
   * Extract an action from a method call.
   * Returns None for non-migration methods (builder, create, etc.)
   * Returns Some(Opaque) for migration methods where we can't extract the field names
   */
  private def extractActionFromMethod(using Quotes)(
    methodName: String,
    args: List[quotes.reflect.Term]
  ): Option[ExtractedAction] = {

    // Helper to apply optic prefix if present (3rd argument)
    def withPrefix(name: Option[String]): Option[String] = {
      if (args.length >= 3) {
        extractPathFromOptic(args(2)) match {
          case Some(prefix) => name.map(n => s"$prefix.$n")
          case None         => name // If we can't extract path, we can't validate correctly -> Opaque? Or just assume root?
        }
      } else {
        name
      }
    }

    methodName match {
      case "renameField" if args.length >= 2 =>
        // Args: (fromName, toName, [optic])
        // If optic is present, it applies to BOTH from and to?
        // Usually renameField(from, to, optic) implies both are under the same parent.
        val from = withPrefix(extractFieldName(args(0)).orElse(extractStringLiteral(args(0))))
        val to   = withPrefix(extractFieldName(args(1)).orElse(extractStringLiteral(args(1))))
        (from, to) match {
          case (Some(f), Some(t)) => Some(ExtractedAction.Rename(f, t))
          case _                  => Some(ExtractedAction.Opaque)
        }

      case "dropField" if args.nonEmpty =>
        // Args: (name, [optic])
        val name = withPrefix(extractFieldName(args(0)).orElse(extractStringLiteral(args(0))))
        name match {
          case Some(n) => Some(ExtractedAction.Drop(n))
          case _       => Some(ExtractedAction.Opaque)
        }

      case "addField" if args.nonEmpty =>
        // Args: (name, [optic]) or (name, default, [optic])?
        // addField(name, default, optic)
        // If default is passed, it might be the 2nd arg.
        // Need to check signature. But usually addField takes (name, default).
        // If optic is used, it might be (name, default, optic).
        // Let's assume optic is last.
        val prefix = if (args.length == 3) extractPathFromOptic(args(2)) else None
        
        val rawName = extractFieldName(args(0)).orElse(extractStringLiteral(args(0)))
        val name = prefix match {
             case Some(p) => rawName.map(n => s"$p.$n")
             case None => rawName
        }

        name match {
          case Some(n) => Some(ExtractedAction.Add(n))
          case _       => Some(ExtractedAction.Opaque)
        }

      case "addFieldWithDefault" if args.nonEmpty =>
        val name = extractStringLiteral(args(0))
        name match {
          case Some(n) => Some(ExtractedAction.Add(n))
          case _       => Some(ExtractedAction.Opaque)
        }

      case "optionalizeField" if args.nonEmpty =>
        val name = withPrefix(extractFieldName(args(0)).orElse(extractStringLiteral(args(0))))
        name match {
          case Some(n) => Some(ExtractedAction.Optionalize(n))
          case _       => Some(ExtractedAction.Opaque)
        }

      case "mandateField" if args.nonEmpty =>
        val name = withPrefix(extractFieldName(args(0)).orElse(extractStringLiteral(args(0))))
        name match {
          case Some(n) => Some(ExtractedAction.Mandate(n))
          case _       => Some(ExtractedAction.Opaque)
        }

      case "changeFieldType" if args.length >= 2 =>
        val from = extractFieldName(args(0)).orElse(extractStringLiteral(args(0)))
        val to   = extractFieldName(args(1)).orElse(extractStringLiteral(args(1)))
        (from, to) match {
          case (Some(f), Some(t)) => Some(ExtractedAction.ChangeType(f, t))
          case _                  => Some(ExtractedAction.Opaque)
        }

      case "renameCase" if args.length >= 2 =>
        (extractStringLiteral(args(0)), extractStringLiteral(args(1))) match {
          case (Some(from), Some(to)) => Some(ExtractedAction.RenameCase(from, to))
          case _                      => Some(ExtractedAction.Opaque)
        }

      case "mandateFieldWithDefault" if args.nonEmpty =>
        extractStringLiteral(args(0)) match {
          case Some(name) => Some(ExtractedAction.Mandate(name))
          case _          => Some(ExtractedAction.Opaque)
        }

      // Not a migration method - return None (ignore this call, e.g. builder())
      case _ => None
    }
  }

  /**
   * Extract path from a DynamicOptic expression.
   * e.g. DynamicOptic.root.field("address").field("street") -> "address.street"
   */
  private def extractPathFromOptic(using Quotes)(term: quotes.reflect.Term): Option[String] = {
    import quotes.reflect.*

    def loop(t: Term, acc: List[String]): Option[String] = t match {
      // Unwrap wrappers including TypeApply (generics)
      case Inlined(_, _, inner) => loop(inner, acc)
      case Typed(inner, _)      => loop(inner, acc)
      case Block(_, expr)       => loop(expr, acc)
      case TypeApply(inner, _)  => loop(inner, acc)

      // .field("name") call
      case Apply(Select(inner, "field"), args) if args.nonEmpty =>
        extractStringLiteral(args.head).flatMap(name => loop(inner, name :: acc))

      // .field[T]("name") call
      case Apply(TypeApply(Select(inner, "field"), _), args) if args.nonEmpty =>
        extractStringLiteral(args.head).flatMap(name => loop(inner, name :: acc))

      // DynamicOptic.root (any qualifier)
      case Select(_, "root") => Some(acc.mkString("."))
      
      case _ => None
    }

    loop(term, Nil)
  }

  /**
   * Extract field name from a selector expression like `_.fieldName` or `_.nested.field`.
   */
  private def extractFieldName(using Quotes)(term: quotes.reflect.Term): Option[String] = {
    import quotes.reflect.*

    def extractFromSelector(t: Term): Option[String] = {
      t match {
      // _.fieldName
      case Select(Ident(_), name) => Some(name)
      
      // _.nested.fieldName
      case Select(inner, name) =>
        extractFromSelector(inner).map(prefix => s"$prefix.$name")

      // Block containing lambda
      case Block(_, expr) => extractFromSelector(expr)
      // Inlined expression
      case Inlined(_, _, inner) => extractFromSelector(inner)
      // Typed expression
      case Typed(inner, _) => extractFromSelector(inner)
      
      // Lambda function x => x.field
      case Lambda(_, body) => extractFromSelector(body)

      case _ => None
      }
    }
    extractFromSelector(term)
  }

  /**
   * Extract a string literal from a term.
   * Handles various AST patterns including those from Scala 3 macro expansion.
   */
  private def extractStringLiteral(using Quotes)(term: quotes.reflect.Term): Option[String] = {
    import quotes.reflect.*
    term match {
      case Literal(StringConstant(s)) => Some(s)
      case Inlined(_, _, inner)       => extractStringLiteral(inner)
      case Typed(inner, _)            => extractStringLiteral(inner)
      case Block(_, expr)             => extractStringLiteral(expr)
      // Handle Expr.apply from macro expansion: Expr("string")
      case Apply(_, List(Literal(StringConstant(s)))) => Some(s)
      case _                          => None
    }
  }


  /**
   * Simulate the field transformation to determine what fields are handled/produced.
   */
  private def simulateTransformation(
    sourceFields: Set[String],
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
      case ExtractedAction.RenameCase(_, _) =>
        // Case renames don't affect field coverage
        ()
      case ExtractedAction.Opaque =>
        // Can't determine, handled separately
        ()
    }

    // Fields that exist in both source and target are implicitly handled
    (handledSource, producedTarget)
  }

  /**
   * Extract field names from a type at compile time.
   * Recursively extracts nested fields as dot-separated paths (e.g. "address.street").
   */
  private def extractTypeFields[T: Type](using Quotes): Set[String] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    extractFieldsFromTypeRepr(tpe, prefix = "")
  }

  private def extractFieldsFromTypeRepr(using Quotes)(tpe: quotes.reflect.TypeRepr, prefix: String): Set[String] = {

    // Helper to format path
    def makePath(name: String): String =
      if (prefix.isEmpty) name else s"$prefix.$name"

    // Stop recursion for standard types
    if (isStandardType(tpe)) {
      return Set.empty
    }

    tpe.classSymbol match {
      case Some(sym) if sym.caseFields.nonEmpty =>
        sym.caseFields.flatMap { field =>
          val fieldPath = makePath(field.name)
          val fieldType = tpe.memberType(field)
          
          // If it's a nested case class (and not a standard type), recurse
          if (!isStandardType(fieldType) && fieldType.classSymbol.exists(_.caseFields.nonEmpty)) {
             extractFieldsFromTypeRepr(fieldType, fieldPath)
          } else {
             Set(fieldPath)
          }
        }.toSet
      case _ =>
        Set.empty
    }
  }

  private def isStandardType(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    val name = tpe.typeSymbol.fullName
    name.startsWith("java.") ||
    name.startsWith("scala.") ||
    name == "zio.blocks.schema.DynamicValue"
  }
}

