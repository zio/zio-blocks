package zio.blocks.schema.migration

import scala.quoted._
import zio.blocks.schema.DynamicOptic

/**
 * Scala 3 macros for extracting DynamicOptic paths from lambda selectors.
 * Converts selector expressions like `_.field.nested.each` into DynamicOptic
 * instances.
 */
object MigrationBuilderMacros {

  /**
   * Extract a DynamicOptic path from a selector lambda at compile time.
   *
   * Supports:
   *   - Field access: `_.field` -> `DynamicOptic.root.field("field")`
   *   - Nested: `_.a.b.c` ->
   *     `DynamicOptic.root.field("a").field("b").field("c")`
   *   - When: `_.x.when[Case]` -> `DynamicOptic.root.field("x").case_("Case")`
   *   - Each: `_.items.each` -> `DynamicOptic.root.field("items").elements`
   *   - Map keys: `_.map.eachKey` -> `DynamicOptic.root.field("map").mapKeys`
   *   - Map values: `_.map.eachValue` ->
   *     `DynamicOptic.root.field("map").mapValues`
   */
  inline def extractPath[A, B](inline selector: A => B): DynamicOptic =
    ${ extractPathImpl('selector) }

  private def extractPathImpl[A: Type, B: Type](selector: Expr[A => B])(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect._

    // Extract the path body from the lambda
    def toPathBody(term: Term): Term = term match {
      case Inlined(_, _, inlinedBlock)                     => toPathBody(inlinedBlock)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               => report.errorAndAbort(s"Expected a lambda expression, got '${term.show}'")
    }

    // Check if term has a specific name
    def hasName(term: Term, name: String): Boolean = term match {
      case Ident(s)     => s == name
      case Select(_, s) => s == name
      case _            => false
    }

    // Collect path segments recursively, returning nodes in order from root to leaf
    def collectNodes(term: Term): List[Expr[DynamicOptic.Node]] = term match {
      // Terminal: identity lambda body (e.g., the `x` in `x => x.field`)
      case _: Ident => Nil

      // Field selection: _.field
      case Select(parent, fieldName) =>
        collectNodes(parent) :+ '{ DynamicOptic.Node.Field(${ Expr(fieldName) }) }

      // .each - sequence elements
      case Apply(TypeApply(eachTerm, _), List(parent)) if hasName(eachTerm, "each") =>
        collectNodes(parent) :+ '{ DynamicOptic.Node.Elements }

      // .eachKey - map keys
      case Apply(TypeApply(keyTerm, _), List(parent)) if hasName(keyTerm, "eachKey") =>
        collectNodes(parent) :+ '{ DynamicOptic.Node.MapKeys }

      // .eachValue - map values
      case Apply(TypeApply(valueTerm, _), List(parent)) if hasName(valueTerm, "eachValue") =>
        collectNodes(parent) :+ '{ DynamicOptic.Node.MapValues }

      // .when[Case] - enum case
      case TypeApply(Apply(TypeApply(caseTerm, _), List(parent)), List(typeTree)) if hasName(caseTerm, "when") =>
        val caseName = typeTree.tpe.dealias.typeSymbol.name
        collectNodes(parent) :+ '{ DynamicOptic.Node.Case(${ Expr(caseName) }) }

      // .wrapped[T] - wrapped type
      case TypeApply(Apply(TypeApply(wrapperTerm, _), List(parent)), _) if hasName(wrapperTerm, "wrapped") =>
        collectNodes(parent) :+ '{ DynamicOptic.Node.Wrapped }

      // .at(index) - specific index
      case Apply(Apply(TypeApply(atTerm, _), List(parent)), List(index)) if hasName(atTerm, "at") =>
        val indexExpr = index.asExpr.asInstanceOf[Expr[Int]]
        collectNodes(parent) :+ '{ DynamicOptic.Node.AtIndex($indexExpr) }

      // Tuple element access (._1, ._2, etc.) - extract index
      case Apply(Apply(_, List(parent)), List(Literal(IntConstant(idx)))) =>
        val fieldName = s"_${idx + 1}" // Convert 0-based to 1-based
        collectNodes(parent) :+ '{ DynamicOptic.Node.Field(${ Expr(fieldName) }) }

      case _ =>
        report.errorAndAbort(
          s"Unsupported selector syntax: '${term.show}'. " +
            "Supported: _.field, _.a.b.c, _.when[Case], _.each, _.eachKey, _.eachValue, _.wrapped[T], _.at(index)"
        )
    }

    val pathBody = toPathBody(selector.asTerm)
    val nodes    = collectNodes(pathBody)

    if (nodes.isEmpty) {
      '{ DynamicOptic.root }
    } else {
      val nodeSeq = Expr.ofSeq(nodes)
      '{ DynamicOptic(Vector($nodeSeq: _*)) }
    }
  }

  /**
   * Validate that a selector lambda is valid for migration paths. This is
   * called at compile time and will emit an error if the selector is invalid.
   */
  inline def validateSelector[A, B](inline selector: A => B): Unit =
    ${ validateSelectorImpl('selector) }

  private def validateSelectorImpl[A: Type, B: Type](selector: Expr[A => B])(using Quotes): Expr[Unit] = {
    // Simply calling extractPathImpl validates the selector
    // If invalid, it will abort compilation
    extractPathImpl(selector)
    '{ () }
  }

  /**
   * Extract the simple name of an enum case type at compile time.
   *
   * Used for type-safe enum case selectors like when[C] and renameCaseTyped.
   */
  inline def extractCaseName[C]: String =
    ${ extractCaseNameImpl[C] }

  private def extractCaseNameImpl[C: Type](using Quotes): Expr[String] = {
    import quotes.reflect._
    val tpe      = TypeRepr.of[C]
    val caseName = tpe.dealias.typeSymbol.name
    Expr(caseName)
  }

  /**
   * Build the migration with compile-time schema validation.
   *
   * This macro extracts field paths and case names from source (A) and target
   * (B) schemas at compile time, then generates code that validates the
   * accumulated actions cover all required fields.
   *
   * Per Issue #519: "macro validation in .build to confirm 'old' has been
   * migrated to 'new'"
   */
  inline def buildValidated[A, B](builder: MigrationBuilder[A, B]): Migration[A, B] =
    ${ buildValidatedImpl[A, B]('builder) }

  private def buildValidatedImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]]
  )(using q: Quotes): Expr[Migration[A, B]] = {
    import q.reflect.*

    val sourceType = TypeRepr.of[A].dealias
    val targetType = TypeRepr.of[B].dealias

    val sourceFields = extractFieldPaths(sourceType)
    val targetFields = extractFieldPaths(targetType)
    val sourceCases  = extractCaseNames(sourceType)
    val targetCases  = extractCaseNames(targetType)

    // Fields that need to be handled (in source but not target)
    val fieldsToHandle = sourceFields.diff(targetFields)
    // Fields that need to be provided (in target but not source)
    val fieldsToProvide = targetFields.diff(sourceFields)
    // Cases that need to be handled
    val casesToHandle = sourceCases.diff(targetCases)
    // Cases that need to be provided
    val casesToProvide = targetCases.diff(sourceCases)

    // Convert to Expr for runtime use
    val fieldsToHandleExpr  = Expr(fieldsToHandle)
    val fieldsToProvideExpr = Expr(fieldsToProvide)
    val casesToHandleExpr   = Expr(casesToHandle)
    val casesToProvideExpr  = Expr(casesToProvide)
    val sourceNameExpr      = Expr(sourceType.typeSymbol.name)
    val targetNameExpr      = Expr(targetType.typeSymbol.name)

    '{
      MigrationBuilderMacros.validateAndBuild[A, B](
        $builder,
        $fieldsToHandleExpr,
        $fieldsToProvideExpr,
        $casesToHandleExpr,
        $casesToProvideExpr,
        $sourceNameExpr,
        $targetNameExpr
      )
    }
  }

  /**
   * Runtime validation helper called by the macro. Validates that accumulated
   * actions cover all required fields and cases.
   */
  def validateAndBuild[A, B](
    builder: MigrationBuilder[A, B],
    fieldsToHandle: List[String],
    fieldsToProvide: List[String],
    casesToHandle: List[String],
    casesToProvide: List[String],
    sourceName: String,
    targetName: String
  ): Migration[A, B] = {
    val actions = builder.currentActions

    // Extract what the actions handle/provide
    var handledFields  = Set.empty[String]
    var providedFields = Set.empty[String]
    var handledCases   = Set.empty[String]
    var providedCases  = Set.empty[String]

    actions.foreach {
      case MigrationAction.DropField(at, fieldName, _) =>
        handledFields += pathToString(at, fieldName)

      case MigrationAction.AddField(at, fieldName, _) =>
        providedFields += pathToString(at, fieldName)

      case MigrationAction.Rename(at, oldName, newName) =>
        handledFields += pathToString(at, oldName)
        providedFields += pathToString(at, newName)

      case MigrationAction.RenameCase(at, oldCase, newCase) =>
        val prefix          = at.nodes.collect { case zio.blocks.schema.DynamicOptic.Node.Field(name) => name }.mkString(".")
        val prefixedOldCase = if (prefix.isEmpty) oldCase else s"$prefix.$oldCase"
        val prefixedNewCase = if (prefix.isEmpty) newCase else s"$prefix.$newCase"
        handledCases += prefixedOldCase
        providedCases += prefixedNewCase

      case MigrationAction.TransformValue(at, fieldName, _, _) =>
        val path = pathToString(at, fieldName)
        handledFields += path
        providedFields += path

      case MigrationAction.Join(at, targetFieldName, sourcePaths, _, _) =>
        sourcePaths.foreach { sp =>
          sp.nodes.lastOption match {
            case Some(zio.blocks.schema.DynamicOptic.Node.Field(name)) =>
              handledFields += pathToString(at, name)
            case _ => ()
          }
        }
        providedFields += pathToString(at, targetFieldName)

      case MigrationAction.Split(at, sourceFieldName, targetPaths, _, _) =>
        handledFields += pathToString(at, sourceFieldName)
        targetPaths.foreach { tp =>
          tp.nodes.lastOption match {
            case Some(zio.blocks.schema.DynamicOptic.Node.Field(name)) =>
              providedFields += pathToString(at, name)
            case _ => ()
          }
        }

      case _ => ()
    }

    // Check for missing coverage
    val unhandledFields  = fieldsToHandle.filterNot(handledFields.contains)
    val unprovidedFields = fieldsToProvide.filterNot(providedFields.contains)
    val unhandledCases   = casesToHandle.filterNot(handledCases.contains)
    val unprovidedCases  = casesToProvide.filterNot(providedCases.contains)

    if (
      unhandledFields.nonEmpty || unprovidedFields.nonEmpty ||
      unhandledCases.nonEmpty || unprovidedCases.nonEmpty
    ) {
      val sb = new StringBuilder
      sb.append(s"\n[Macro Validated] Migration $sourceName => $targetName is incomplete:\n")
      sb.append("=" * 60 + "\n\n")

      if (unhandledFields.nonEmpty) {
        sb.append("UNHANDLED FIELDS (in source, not target - need dropField/renameField):\n")
        unhandledFields.foreach(f => sb.append(s"  - $f\n"))
        sb.append("\n")
      }

      if (unprovidedFields.nonEmpty) {
        sb.append("UNPROVIDED FIELDS (in target, not source - need addField/renameField):\n")
        unprovidedFields.foreach(f => sb.append(s"  - $f\n"))
        sb.append("\n")
      }

      if (unhandledCases.nonEmpty) {
        sb.append("UNHANDLED CASES (in source, not target - need renameCase):\n")
        unhandledCases.foreach(c => sb.append(s"  - $c\n"))
        sb.append("\n")
      }

      if (unprovidedCases.nonEmpty) {
        sb.append("UNPROVIDED CASES (in target, not source - need renameCase):\n")
        unprovidedCases.foreach(c => sb.append(s"  - $c\n"))
        sb.append("\n")
      }

      throw new IllegalArgumentException(sb.toString)
    }

    // All fields covered, build with path validation
    builder.buildPathsOnly
  }

  private def pathToString(at: zio.blocks.schema.DynamicOptic, fieldName: String): String = {
    val prefix = at.nodes.collect { case zio.blocks.schema.DynamicOptic.Node.Field(name) =>
      name
    }.mkString(".")
    if (prefix.isEmpty) fieldName else s"$prefix.$fieldName"
  }

  // Helper methods for compile-time schema analysis
  private def extractFieldPaths(using q: Quotes)(tpe: q.reflect.TypeRepr): List[String] = {
    import q.reflect.*

    def extract(t: TypeRepr, prefix: String, seen: Set[String]): List[String] = {
      val typeName = t.typeSymbol.fullName
      if (seen.contains(typeName)) return Nil
      val newSeen = seen + typeName

      t.dealias match {
        case ref: Refinement =>
          extractRefinement(ref, prefix)

        case t if t.typeSymbol.flags.is(Flags.Case) =>
          val fields = t.typeSymbol.caseFields
          fields.flatMap { field =>
            val fieldName = field.name
            val fieldPath = if (prefix.isEmpty) fieldName else s"$prefix.$fieldName"
            val fieldType = t.memberType(field)
            if (isPrimitive(fieldType)) List(fieldPath)
            else fieldPath :: extract(fieldType, fieldPath, newSeen)
          }.toList

        case _ => Nil
      }
    }

    def extractRefinement(ref: Refinement, prefix: String): List[String] = {
      def loop(tpe: TypeRepr, acc: List[String]): List[String] = tpe match {
        case Refinement(parent, name, _) if name != "Tag" =>
          val fieldPath = if (prefix.isEmpty) name else s"$prefix.$name"
          loop(parent, fieldPath :: acc)
        case Refinement(parent, _, _) => loop(parent, acc)
        case _                        => acc
      }
      loop(ref, Nil)
    }

    extract(tpe, "", Set.empty).sorted
  }

  private def extractCaseNames(using q: Quotes)(tpe: q.reflect.TypeRepr): List[String] = {
    import q.reflect.*

    def extract(t: TypeRepr, prefix: String, seen: Set[String]): List[String] = {
      val typeName = t.typeSymbol.fullName
      if (seen.contains(typeName)) return Nil
      val newSeen = seen + typeName

      t.dealias match {
        // If this type is a sealed trait, extract its case names with prefix
        case t if t.typeSymbol.flags.is(Flags.Sealed) =>
          t.typeSymbol.children.map { child =>
            val caseName = child.name
            if (prefix.isEmpty) caseName else s"$prefix.$caseName"
          }

        // If this is a case class, recurse into its fields to find nested sealed traits
        case t if t.typeSymbol.flags.is(Flags.Case) =>
          val fields = t.typeSymbol.caseFields
          fields.flatMap { field =>
            val fieldName = field.name
            val fieldPath = if (prefix.isEmpty) fieldName else s"$prefix.$fieldName"
            val fieldType = t.memberType(field)
            extract(fieldType, fieldPath, newSeen)
          }.toList

        case _ => Nil
      }
    }

    extract(tpe, "", Set.empty).sorted
  }

  private def isPrimitive(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    val name = tpe.typeSymbol.fullName
    Set(
      "scala.Boolean",
      "scala.Byte",
      "scala.Short",
      "scala.Int",
      "scala.Long",
      "scala.Float",
      "scala.Double",
      "scala.Char",
      "java.lang.String",
      "scala.Predef.String",
      "BigInt",
      "BigDecimal"
    ).exists(name.contains)
  }
}
