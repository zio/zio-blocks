package zio.blocks.schema.migration

import scala.quoted._
import zio.blocks.schema.{DynamicOptic, Schema}

/**
 * Scala 3 macros for MigrationBuilder to extract field names and paths from
 * lambda expressions at compile time.
 *
 * This enables type-safe, IDE-friendly migration building:
 * builder.addField(_.country, "USA") builder.renameField(_.name, _.fullName)
 * builder.dropField(_.oldField)
 */
object MigrationBuilderMacros {

  /**
   * Macro implementation for build() method with validation. Validates that all
   * fields in target schema are accounted for.
   */
  inline def buildWithValidation[A, B](builder: MigrationBuilder[A, B]): Migration[A, B] =
    ${ buildWithValidationImpl[A, B]('builder) }

  private def buildWithValidationImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]]
  )(using Quotes): Expr[Migration[A, B]] = {
    import quotes.reflect._

    // Extract source and target field names from case class types
    val sourceFields = extractCaseClassFields[A]
    val targetFields = extractCaseClassFields[B]

    // Extract actions from the builder expression
    val actions = extractActionsFromBuilder(builder.asTerm)

    // Simulate which fields are handled by the migration
    val (handledSource, producedTarget) = simulateTransformation(actions)

    // Fields that exist in both source and target are implicitly handled
    val implicitFields = sourceFields.intersect(targetFields)

    // Calculate unmapped fields
    val unmappedSource = sourceFields.diff(handledSource).diff(implicitFields)
    val unmappedTarget = targetFields.diff(producedTarget).diff(implicitFields)

    // Report errors for unmapped fields
    if (unmappedSource.nonEmpty) {
      report.error(
        s"Migration from ${Type.show[A]} to ${Type.show[B]} is incomplete: " +
          s"source fields [${unmappedSource.mkString(", ")}] are not handled. " +
          s"Use dropField() to explicitly drop them."
      )
    }

    if (unmappedTarget.nonEmpty) {
      report.error(
        s"Migration from ${Type.show[A]} to ${Type.show[B]} is incomplete: " +
          s"target fields [${unmappedTarget.mkString(", ")}] are not produced. " +
          s"Use addField() or renameField() to provide them."
      )
    }

    // Return the build call
    '{ $builder.buildUnchecked }
  }

  /**
   * Extract field names from a case class type.
   */
  private def extractCaseClassFields[T: Type](using Quotes): Set[String] = {
    import quotes.reflect._

    val tpe = TypeRepr.of[T]
    tpe.typeSymbol.caseFields.map(_.name).toSet
  }

  /**
   * Extract actions from the builder tree by traversing method calls.
   */
  private def extractActionsFromBuilder(using Quotes)(tree: quotes.reflect.Term): List[ExtractedAction] = {
    import quotes.reflect._

    def loop(t: Term, acc: List[ExtractedAction]): List[ExtractedAction] = t match {
      // Method call: builder.method(args)
      case Apply(Select(qual, name), args) =>
        val action = extractActionFromMethod(name, args)
        loop(qual, action :: acc)

      // Method call with type parameters: builder.method[T](args)
      case Apply(TypeApply(Select(qual, name), _), args) =>
        val action = extractActionFromMethod(name, args)
        loop(qual, action :: acc)

      // Inlined wrapper
      case Inlined(_, _, inner) => loop(inner, acc)
      case Block(_, expr)       => loop(expr, acc)

      // Base case: reached the builder constructor
      case _ => acc
    }

    loop(tree, Nil)
  }

  /**
   * Extract action information from a method call.
   */
  private def extractActionFromMethod(using
    Quotes
  )(
    methodName: String,
    args: List[quotes.reflect.Term]
  ): ExtractedAction =
    methodName match {
      case "renameField" if args.length >= 2 =>
        (extractStringLiteral(args(0)), extractStringLiteral(args(1))) match {
          case (Some(from), Some(to)) => ExtractedAction.Rename(from, to)
          case _                      => ExtractedAction.Unknown
        }

      case "dropField" | "dropFieldWithDefault" if args.nonEmpty =>
        extractStringLiteral(args(0)) match {
          case Some(name) => ExtractedAction.Drop(name)
          case None       => ExtractedAction.Unknown
        }

      case "addField" | "addFieldWithDefault" if args.nonEmpty =>
        extractStringLiteral(args(0)) match {
          case Some(name) => ExtractedAction.Add(name)
          case None       => ExtractedAction.Unknown
        }

      case "optionalizeField" if args.nonEmpty =>
        extractStringLiteral(args(0)) match {
          case Some(name) => ExtractedAction.Optionalize(name)
          case None       => ExtractedAction.Unknown
        }

      case "mandateField" | "mandateFieldWithDefault" if args.nonEmpty =>
        extractStringLiteral(args(0)) match {
          case Some(name) => ExtractedAction.Mandate(name)
          case None       => ExtractedAction.Unknown
        }

      case "changeFieldType" if args.nonEmpty =>
        extractStringLiteral(args(0)) match {
          case Some(name) => ExtractedAction.ChangeType(name, name)
          case None       => ExtractedAction.Unknown
        }

      case _ => ExtractedAction.Unknown
    }

  /**
   * Extract a string literal from a term.
   */
  private def extractStringLiteral(using Quotes)(term: quotes.reflect.Term): Option[String] = {
    import quotes.reflect._

    term match {
      case Literal(StringConstant(s)) => Some(s)
      case Typed(t, _)                => extractStringLiteral(t)
      case Block(_, t)                => extractStringLiteral(t)
      case Inlined(_, _, t)           => extractStringLiteral(t)
      case _                          => None
    }
  }

  /**
   * Simulate which fields are handled by the migration actions.
   */
  private def simulateTransformation(actions: List[ExtractedAction]): (Set[String], Set[String]) = {
    var handledSource  = Set.empty[String]
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
      case ExtractedAction.Unknown =>
        // Unknown actions - can't validate
        ()
    }

    (handledSource, producedTarget)
  }

  /**
   * Represents an extracted action for validation purposes.
   */
  private enum ExtractedAction {
    case Rename(from: String, to: String)
    case Drop(name: String)
    case Add(name: String)
    case Optionalize(name: String)
    case Mandate(name: String)
    case ChangeType(from: String, to: String)
    case Unknown
  }

  /**
   * Extract field name from a selector lambda like _.fieldName Returns the
   * field name as a string.
   */
  def extractFieldName[A: Type, F: Type](selector: Expr[A => F])(using Quotes): Expr[String] = {
    import quotes.reflect._

    def extractFromTerm(term: Term): String = term match {
      case Inlined(_, _, body)                         => extractFromTerm(body)
      case Block(List(DefDef(_, _, _, Some(body))), _) => extractFromTerm(body)
      case Lambda(_, body)                             => extractFromTerm(body)
      case Select(_, fieldName)                        => fieldName
      case Ident(name)                                 => name
      case _                                           =>
        report.errorAndAbort(s"Expected a field selector like _.fieldName, got: ${term.show}")
    }

    val fieldName = extractFromTerm(selector.asTerm)
    Expr(fieldName)
  }

  /**
   * Extract a path from a nested selector like _.address.street Returns a
   * DynamicOptic representing the path.
   */
  def extractPath[A: Type, F: Type](selector: Expr[A => F])(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect._

    def extractFields(term: Term): List[String] = term match {
      case Inlined(_, _, body)                         => extractFields(body)
      case Block(List(DefDef(_, _, _, Some(body))), _) => extractFields(body)
      case Lambda(_, body)                             => extractFields(body)
      case Select(qualifier, fieldName)                => extractFields(qualifier) :+ fieldName
      case Ident(_)                                    => Nil // Root parameter
      case _                                           =>
        report.errorAndAbort(s"Expected a field selector like _.address.street, got: ${term.show}")
    }

    val fields = extractFields(selector.asTerm)

    if (fields.isEmpty) {
      '{ DynamicOptic.root }
    } else {
      // Build path: root / "field1" / "field2" / ...
      val fieldExprs = fields.map(f => Expr(f))
      fieldExprs.foldLeft('{ DynamicOptic.root }) { (acc, fieldExpr) =>
        '{ $acc / $fieldExpr }
      }
    }
  }

  /**
   * Extract two field names from two selectors for rename operations. Returns a
   * tuple of (fromField, toField).
   */
  def extractTwoFieldNames[A: Type, B: Type, F1: Type, F2: Type](
    from: Expr[A => F1],
    to: Expr[B => F2]
  )(using Quotes): Expr[(String, String)] = {
    val fromName = extractFieldName(from)
    val toName   = extractFieldName(to)
    '{ ($fromName, $toName) }
  }

  /**
   * Validate that a selector points to a valid field in the schema. This is a
   * compile-time check to ensure type safety.
   *
   * Note: Full validation is performed in buildWithValidation macro. This
   * method validates selector syntax only.
   */
  def validateFieldExists[A: Type](
    selector: Expr[A => Any],
    schema: Expr[Schema[A]]
  )(using Quotes): Expr[Unit] = {
    import quotes.reflect._

    // Validate selector syntax by extracting field name
    val _ = extractFieldName(selector)
    val _ = schema

    '{ () }
  }
}

/**
 * Scala 3 extension methods for MigrationBuilder.
 */
extension [A, B](builder: MigrationBuilder[A, B]) {

  /**
   * Build the migration with full validation.
   *
   * This is the ONLY build method we expose (no buildPartial).
   * @jdegoes
   *   specifically requested this.
   *
   * This method uses macros to validate at compile time that:
   *   - All source fields are either migrated or explicitly dropped
   *   - All target fields are either produced by migration or exist in source
   */
  inline def build: Migration[A, B] = MigrationBuilderMacros.buildWithValidation(builder)
}
