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
    val unmappedSource = sourceFields.diff(handledSourceFields).diff(targetFields)  // Source fields not in target that weren't handled
    val unmappedTarget = targetFields.diff(producedTargetFields).diff(sourceFields) // Target fields not in source that weren't added

    // Report compile-time errors (or warnings if opaque)
    if (hasOpaqueActions) {
      // Best effort: we couldn't fully analyze, so warn
      if (unmappedSource.nonEmpty || unmappedTarget.nonEmpty) {
        report.warning(
          s"Migration contains runtime values that prevent full analysis. " +
            s"Potential issues: " +
            (if (unmappedSource.nonEmpty) s"unmapped source fields [${unmappedSource.mkString(", ")}]" else "") +
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
   */
  private def extractActionsFromBuilder(using Quotes)(term: quotes.reflect.Term): List[ExtractedAction] = {
    import quotes.reflect.*

    def walk(t: Term, acc: List[ExtractedAction]): List[ExtractedAction] = t match {
      // Method call: builder.methodName(args)
      case Apply(Select(receiver, methodName), args) =>
        val action = extractActionFromMethod(methodName, args)
        walk(receiver, action :: acc)

      // Generic method call: builder.methodName[T](args)
      case Apply(TypeApply(Select(receiver, methodName), _), args) =>
        val action = extractActionFromMethod(methodName, args)
        walk(receiver, action :: acc)

      // Multiple argument lists: builder.methodName(args1)(args2)
      case Apply(Apply(Select(receiver, methodName), args1), args2) =>
        val action = extractActionFromMethod(methodName, args1 ++ args2)
        walk(receiver, action :: acc)

      // Generic multiple argument lists: builder.methodName[T](args1)(args2)
      case Apply(Apply(TypeApply(Select(receiver, methodName), _), args1), args2) =>
        val action = extractActionFromMethod(methodName, args1 ++ args2)
        walk(receiver, action :: acc)

      // Inlined method call
      case Inlined(_, _, inner) =>
        walk(inner, acc)

      // Block with expression
      case Block(_, expr) =>
        walk(expr, acc)

      // Typed wrapper
      case Typed(inner, _) =>
        walk(inner, acc)

      case _ =>
        acc
    }

    walk(term, Nil)
  }

  /**
   * Extract an action from a method call.
   */
  private def extractActionFromMethod(using Quotes)(
    methodName: String,
    args: List[quotes.reflect.Term]
  ): ExtractedAction = {

    methodName match {
      case "renameField" if args.length >= 2 =>
        val from = extractFieldName(args(0)).orElse(extractStringLiteral(args(0)))
        val to   = extractFieldName(args(1)).orElse(extractStringLiteral(args(1)))
        (from, to) match {
          case (Some(f), Some(t)) => ExtractedAction.Rename(f, t)
          case _                      => ExtractedAction.Opaque
        }

      case "dropField" if args.nonEmpty =>
        extractFieldName(args(0)) match {
          case Some(name) => ExtractedAction.Drop(name)
          case _          => ExtractedAction.Opaque
        }

      case "addField" if args.nonEmpty =>
        extractFieldName(args(0)) match {
          case Some(name) => ExtractedAction.Add(name)
          case _          => ExtractedAction.Opaque
        }

      case "addFieldWithDefault" if args.nonEmpty =>
        extractStringLiteral(args(0)) match {
          case Some(name) => ExtractedAction.Add(name)
          case _          => ExtractedAction.Opaque
        }

      case "optionalizeField" if args.nonEmpty =>
        extractFieldName(args(0)) match {
          case Some(name) => ExtractedAction.Optionalize(name)
          case _          => ExtractedAction.Opaque
        }

      case "mandateField" if args.nonEmpty =>
        extractFieldName(args(0)) match {
          case Some(name) => ExtractedAction.Mandate(name)
          case _          => ExtractedAction.Opaque
        }

      case "changeFieldType" if args.length >= 2 =>
        (extractFieldName(args(0)), extractFieldName(args(1))) match {
          case (Some(from), Some(to)) => ExtractedAction.ChangeType(from, to)
          case _                      => ExtractedAction.Opaque
        }

      case "renameCase" if args.length >= 2 =>
        (extractStringLiteral(args(0)), extractStringLiteral(args(1))) match {
          case (Some(from), Some(to)) => ExtractedAction.RenameCase(from, to)
          case _                      => ExtractedAction.Opaque
        }

      case "mandateFieldWithDefault" if args.nonEmpty =>
        extractStringLiteral(args(0)) match {
          case Some(name) => ExtractedAction.Mandate(name)
          case _          => ExtractedAction.Opaque
        }

      case _ => ExtractedAction.Opaque
    }
  }

  /**
   * Extract field name from a selector expression like `_.fieldName` or `_.nested.field`.
   */
  private def extractFieldName(using Quotes)(term: quotes.reflect.Term): Option[String] = {
    import quotes.reflect.*

    def extractFromSelector(t: Term): Option[String] = t match {
      // _.fieldName
      case Select(_, name) => Some(name)
      // Lambda: (x: A) => x.fieldName
      case Lambda(_, body) => extractFromSelector(body)
      // Block containing lambda
      case Block(_, expr) => extractFromSelector(expr)
      // Inlined expression
      case Inlined(_, _, inner) => extractFromSelector(inner)
      // Typed expression
      case Typed(inner, _) => extractFromSelector(inner)
      case _ => None
    }
    extractFromSelector(term)
  }

  /**
   * Extract a string literal from a term.
   */
  private def extractStringLiteral(using Quotes)(term: quotes.reflect.Term): Option[String] = {
    import quotes.reflect.*
    term match {
      case Literal(StringConstant(s)) => Some(s)
      case Inlined(_, _, inner)       => extractStringLiteral(inner)
      case Typed(inner, _)            => extractStringLiteral(inner) // Handle typed literals
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
   */
  private def extractTypeFields[T: Type](using Quotes): Set[String] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[T]
    extractFieldsFromTypeRepr(tpe)
  }

  private def extractFieldsFromTypeRepr(using Quotes)(tpe: quotes.reflect.TypeRepr): Set[String] = {
    import quotes.reflect.*

    def extractFromRefinement(t: TypeRepr, acc: Set[String]): Set[String] = t match {
      case Refinement(parent, name, info) =>
        val isField = info match {
          case _: ByNameType           => true
          case MethodType(Nil, Nil, _) => true
          case PolyType(_, _, _)       => false
          case _: MethodType           => false
          case _                       => true
        }
        val newAcc = if (isField && !name.startsWith("$")) acc + name else acc
        extractFromRefinement(parent, newAcc)
      case _ => acc
    }

    // Try refinement extraction first
    val refinementFields = extractFromRefinement(tpe, Set.empty)
    if (refinementFields.nonEmpty) {
      return refinementFields
    }

    // For case classes, extract from type symbol
    tpe.classSymbol match {
      case Some(sym) =>
        sym.caseFields.map(_.name).toSet
      case None =>
        Set.empty
    }
  }
}

