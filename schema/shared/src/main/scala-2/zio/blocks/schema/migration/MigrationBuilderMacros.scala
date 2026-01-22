package zio.blocks.schema.migration

import scala.reflect.macros.whitebox
import zio.blocks.schema.{DynamicOptic, Schema}

/**
 * Scala 2 macros for MigrationBuilder to extract field names and paths from
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
  def buildWithValidation[A, B](c: whitebox.Context)(implicit
    aTag: c.WeakTypeTag[A],
    bTag: c.WeakTypeTag[B]
  ): c.Expr[Migration[A, B]] = {
    import c.universe._

    // Get the builder instance (this)
    val builder = c.prefix.tree

    // Extract source and target field names from case class types
    val sourceFields = extractCaseClassFields(c)(aTag.tpe)
    val targetFields = extractCaseClassFields(c)(bTag.tpe)

    // Extract actions from the builder expression
    val actions = extractActionsFromBuilder(c)(builder)

    // Simulate which fields are handled by the migration
    val (handledSource, producedTarget) = simulateTransformation(actions)

    // Fields that exist in both source and target are implicitly handled
    val implicitFields = sourceFields.intersect(targetFields)

    // Calculate unmapped fields
    val unmappedSource = sourceFields.diff(handledSource).diff(implicitFields)
    val unmappedTarget = targetFields.diff(producedTarget).diff(implicitFields)

    // Report errors for unmapped fields
    if (unmappedSource.nonEmpty) {
      c.error(
        c.enclosingPosition,
        s"Migration from ${aTag.tpe} to ${bTag.tpe} is incomplete: " +
          s"source fields [${unmappedSource.mkString(", ")}] are not handled. " +
          s"Use dropField() to explicitly drop them."
      )
    }

    if (unmappedTarget.nonEmpty) {
      c.error(
        c.enclosingPosition,
        s"Migration from ${aTag.tpe} to ${bTag.tpe} is incomplete: " +
          s"target fields [${unmappedTarget.mkString(", ")}] are not produced. " +
          s"Use addField() or renameField() to provide them."
      )
    }

    // Return the build call
    c.Expr[Migration[A, B]](q"${c.prefix}.buildUnchecked")
  }

  /**
   * Extract field names from a case class type.
   */
  private def extractCaseClassFields(c: whitebox.Context)(tpe: c.Type): Set[String] = {
    import c.universe._

    tpe.decls.collect {
      case m: MethodSymbol if m.isCaseAccessor => m.name.decodedName.toString
    }.toSet
  }

  /**
   * Extract actions from the builder tree by traversing method calls.
   */
  private def extractActionsFromBuilder(c: whitebox.Context)(tree: c.Tree): List[ExtractedAction] = {
    import c.universe._

    def loop(t: Tree, acc: List[ExtractedAction]): List[ExtractedAction] = t match {
      // Method call: builder.method(args)
      case Apply(Select(qual, name), args) =>
        val action = extractActionFromMethod(c)(name.decodedName.toString, args)
        loop(qual, action :: acc)

      // Method call with type parameters: builder.method[T](args)
      case Apply(TypeApply(Select(qual, name), _), args) =>
        val action = extractActionFromMethod(c)(name.decodedName.toString, args)
        loop(qual, action :: acc)

      // Typed wrapper
      case Typed(inner, _) => loop(inner, acc)
      case Block(_, expr)  => loop(expr, acc)

      // Base case: reached the builder constructor
      case _ => acc
    }

    loop(tree, Nil)
  }

  /**
   * Extract action information from a method call.
   */
  private def extractActionFromMethod(c: whitebox.Context)(
    methodName: String,
    args: List[c.Tree]
  ): ExtractedAction =
    methodName match {
      case "renameField" if args.length >= 2 =>
        (extractStringLiteral(c)(args(0)), extractStringLiteral(c)(args(1))) match {
          case (Some(from), Some(to)) => ExtractedAction.Rename(from, to)
          case _                      => ExtractedAction.Unknown
        }

      case "dropField" | "dropFieldWithDefault" if args.nonEmpty =>
        extractStringLiteral(c)(args(0)) match {
          case Some(name) => ExtractedAction.Drop(name)
          case None       => ExtractedAction.Unknown
        }

      case "addField" | "addFieldWithDefault" if args.nonEmpty =>
        extractStringLiteral(c)(args(0)) match {
          case Some(name) => ExtractedAction.Add(name)
          case None       => ExtractedAction.Unknown
        }

      case "optionalizeField" if args.nonEmpty =>
        extractStringLiteral(c)(args(0)) match {
          case Some(name) => ExtractedAction.Optionalize(name)
          case None       => ExtractedAction.Unknown
        }

      case "mandateField" | "mandateFieldWithDefault" if args.nonEmpty =>
        extractStringLiteral(c)(args(0)) match {
          case Some(name) => ExtractedAction.Mandate(name)
          case None       => ExtractedAction.Unknown
        }

      case "changeFieldType" if args.nonEmpty =>
        extractStringLiteral(c)(args(0)) match {
          case Some(name) => ExtractedAction.ChangeType(name, name)
          case None       => ExtractedAction.Unknown
        }

      case _ => ExtractedAction.Unknown
    }

  /**
   * Extract a string literal from a tree.
   */
  private def extractStringLiteral(c: whitebox.Context)(tree: c.Tree): Option[String] = {
    import c.universe._

    tree match {
      case Literal(Constant(s: String)) => Some(s)
      case Typed(t, _)                  => extractStringLiteral(c)(t)
      case Block(_, t)                  => extractStringLiteral(c)(t)
      case _                            => None
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
  private sealed trait ExtractedAction
  private object ExtractedAction {
    case class Rename(from: String, to: String)     extends ExtractedAction
    case class Drop(name: String)                   extends ExtractedAction
    case class Add(name: String)                    extends ExtractedAction
    case class Optionalize(name: String)            extends ExtractedAction
    case class Mandate(name: String)                extends ExtractedAction
    case class ChangeType(from: String, to: String) extends ExtractedAction
    case object Unknown                             extends ExtractedAction
  }

  /**
   * Extract field name from a selector lambda like _.fieldName Returns the
   * field name as a string.
   */
  def extractFieldName[A, F](c: whitebox.Context)(selector: c.Expr[A => F]): c.Expr[String] = {
    import c.universe._

    def extractFromTree(tree: Tree): String = tree match {
      case q"($_) => $body"               => extractFromTree(body)
      case Select(_, TermName(fieldName)) => fieldName
      case Ident(TermName(name))          => name
      case _                              =>
        c.abort(c.enclosingPosition, s"Expected a field selector like _.fieldName, got: ${showRaw(tree)}")
    }

    val fieldName = extractFromTree(selector.tree)
    c.Expr[String](Literal(Constant(fieldName)))
  }

  /**
   * Extract a path from a nested selector like _.address.street Returns a
   * DynamicOptic representing the path.
   */
  def extractPath[A, F](c: whitebox.Context)(selector: c.Expr[A => F]): c.Expr[DynamicOptic] = {
    import c.universe._

    def extractFields(tree: Tree): List[String] = tree match {
      case q"($_) => $body"                       => extractFields(body)
      case Select(qualifier, TermName(fieldName)) => extractFields(qualifier) :+ fieldName
      case Ident(_)                               => Nil // Root parameter
      case _                                      =>
        c.abort(c.enclosingPosition, s"Expected a field selector like _.address.street, got: ${showRaw(tree)}")
    }

    val fields = extractFields(selector.tree)

    if (fields.isEmpty) {
      reify(DynamicOptic.root)
    } else {
      // Build path: root / "field1" / "field2" / ...
      val pathTree = fields.foldLeft[Tree](q"_root_.zio.blocks.schema.DynamicOptic.root") { (acc, field) =>
        q"$acc./.apply(${Literal(Constant(field))})"
      }
      c.Expr[DynamicOptic](pathTree)
    }
  }

  /**
   * Extract two field names from two selectors for rename operations. Returns a
   * tuple of (fromField, toField).
   */
  def extractTwoFieldNames[A, B, F1, F2](c: whitebox.Context)(
    from: c.Expr[A => F1],
    to: c.Expr[B => F2]
  ): c.Expr[(String, String)] = {
    import c.universe._

    val fromName = extractFieldName(c)(from)
    val toName   = extractFieldName(c)(to)

    reify {
      (fromName.splice, toName.splice)
    }
  }

  /**
   * Validate that a selector points to a valid field in the schema. This is a
   * compile-time check to ensure type safety.
   *
   * Note: Full validation is performed in buildWithValidation macro. This
   * method validates selector syntax only.
   */
  def validateFieldExists[A](c: whitebox.Context)(
    selector: c.Expr[A => Any],
    @scala.annotation.unused schema: c.Expr[Schema[A]]
  ): c.Expr[Unit] = {
    import c.universe._

    // Validate selector syntax by extracting field name
    val _ = extractFieldName(c)(selector)

    reify(())
  }
}

/**
 * Scala 2 extension methods for MigrationBuilder.
 */
object MigrationBuilderSyntax {
  import scala.language.experimental.macros

  implicit class MigrationBuilderOps[A, B](private val builder: MigrationBuilder[A, B]) extends AnyVal {

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
    def build: Migration[A, B] = macro MigrationBuilderMacros.buildWithValidation[A, B]
  }
}
