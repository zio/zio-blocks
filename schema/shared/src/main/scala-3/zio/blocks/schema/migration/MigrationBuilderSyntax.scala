package zio.blocks.schema.migration

import scala.language.implicitConversions
import zio.blocks.schema._

/**
 * Selector syntax for MigrationBuilder. Methods that accept selector functions
 * like `_.field` instead of manually constructing DynamicOptic instances.
 *
 * Each method returns a builder with refined Handled/Provided type parameters
 * that track which fields have been handled (from source) or provided (for
 * target).
 */
extension [A, B, Handled <: Tuple, Provided <: Tuple](builder: MigrationBuilder[A, B, Handled, Provided]) {

  /**
   * Builds the migration with compile-time validation.
   *
   * This method only compiles when the migration is complete:
   *   - All fields removed from source (in A but not B) must be handled
   *   - All fields added to target (in B but not A) must be provided
   *
   * Fields that exist in both schemas are automatically considered
   * handled/provided.
   *
   * @return
   *   A complete, validated Migration[A, B]
   */
  inline def build: Migration[A, B] =
    ${ MigrationBuilderMacrosImpl.buildImpl[A, B, Handled, Provided]('builder) }

  /**
   * Adds a field to a record with a default value using selector syntax. Adds
   * the target field path to Provided as a structured tuple.
   */
  transparent inline def addField[FieldPath <: Tuple](
    inline target: B => Any,
    default: DynamicSchemaExpr
  ): MigrationBuilder[A, B, Handled, Tuple.Append[Provided, FieldPath]] =
    ${ MigrationBuilderMacrosImpl.addFieldImpl[A, B, Handled, Provided, FieldPath]('builder, 'target, 'default) }

  /**
   * Removes a field from a record using selector syntax. Adds the source field
   * path to Handled as a structured tuple.
   */
  transparent inline def dropField[FieldPath <: Tuple](
    inline source: A => Any,
    defaultForReverse: DynamicSchemaExpr
  ): MigrationBuilder[A, B, Tuple.Append[Handled, FieldPath], Provided] =
    ${
      MigrationBuilderMacrosImpl.dropFieldImpl[A, B, Handled, Provided, FieldPath](
        'builder,
        'source,
        'defaultForReverse
      )
    }

  /**
   * Renames a field in a record using selector syntax. Adds source path to
   * Handled and target path to Provided as structured tuples.
   */
  transparent inline def renameField[FromPath <: Tuple, ToPath <: Tuple](
    inline from: A => Any,
    inline to: B => Any
  ): MigrationBuilder[A, B, Tuple.Append[Handled, FromPath], Tuple.Append[Provided, ToPath]] =
    ${ MigrationBuilderMacrosImpl.renameFieldImpl[A, B, Handled, Provided, FromPath, ToPath]('builder, 'from, 'to) }

  /**
   * Applies a transformation expression to a field value using selector syntax.
   * Adds the field path to both Handled and Provided as a structured tuple.
   */
  transparent inline def transformField[FieldPath <: Tuple](
    inline at: A => Any,
    transform: DynamicSchemaExpr
  ): MigrationBuilder[A, B, Tuple.Append[Handled, FieldPath], Tuple.Append[Provided, FieldPath]] =
    ${ MigrationBuilderMacrosImpl.transformFieldImpl[A, B, Handled, Provided, FieldPath]('builder, 'at, 'transform) }

  /**
   * Unwraps an Option field using selector syntax, using default for None
   * values. Adds the field path to both Handled and Provided as a structured
   * tuple.
   */
  transparent inline def mandateField[FieldPath <: Tuple](
    inline at: A => Any,
    default: DynamicSchemaExpr
  ): MigrationBuilder[A, B, Tuple.Append[Handled, FieldPath], Tuple.Append[Provided, FieldPath]] =
    ${ MigrationBuilderMacrosImpl.mandateFieldImpl[A, B, Handled, Provided, FieldPath]('builder, 'at, 'default) }

  /**
   * Wraps a field value in Option (as Some) using selector syntax. Adds the
   * field path to both Handled and Provided as a structured tuple.
   */
  transparent inline def optionalizeField[FieldPath <: Tuple](
    inline at: A => Any,
    defaultForReverse: DynamicSchemaExpr
  ): MigrationBuilder[A, B, Tuple.Append[Handled, FieldPath], Tuple.Append[Provided, FieldPath]] =
    ${
      MigrationBuilderMacrosImpl.optionalizeFieldImpl[A, B, Handled, Provided, FieldPath](
        'builder,
        'at,
        'defaultForReverse
      )
    }

  /**
   * Converts a field from one primitive type to another using selector syntax.
   * Adds the field path to both Handled and Provided as a structured tuple.
   */
  transparent inline def changeFieldType[FieldPath <: Tuple](
    inline at: A => Any,
    converter: PrimitiveConverter
  ): MigrationBuilder[A, B, Tuple.Append[Handled, FieldPath], Tuple.Append[Provided, FieldPath]] =
    ${ MigrationBuilderMacrosImpl.changeFieldTypeImpl[A, B, Handled, Provided, FieldPath]('builder, 'at, 'converter) }

  /**
   * Joins multiple source fields into a single target field using selector
   * syntax. All source fields must share a common parent path.
   *
   * Adds all source field paths to Handled and the target field path to
   * Provided as structured tuples.
   */
  transparent inline def joinFields(
    inline target: B => Any,
    inline sourcePaths: Seq[A => Any],
    combiner: DynamicSchemaExpr
  ): MigrationBuilder[A, B, ? <: Tuple, ? <: Tuple] =
    ${
      MigrationBuilderMacrosImpl.joinFieldsImpl[A, B, Handled, Provided](
        'builder,
        'target,
        'sourcePaths,
        'combiner
      )
    }

  /**
   * Splits a single source field into multiple target fields using selector
   * syntax. All target fields must share a common parent path.
   *
   * Adds the source field path to Handled and all target field paths to
   * Provided as structured tuples.
   */
  transparent inline def splitField(
    inline source: A => Any,
    inline targetPaths: Seq[B => Any],
    splitter: DynamicSchemaExpr
  ): MigrationBuilder[A, B, ? <: Tuple, ? <: Tuple] =
    ${
      MigrationBuilderMacrosImpl.splitFieldImpl[A, B, Handled, Provided](
        'builder,
        'source,
        'targetPaths,
        'splitter
      )
    }

  /**
   * Applies a transformation to all elements in a sequence using selector
   * syntax. Does not affect field tracking (operates on collection contents,
   * not structure).
   */
  transparent inline def transformElements(
    inline at: A => Any,
    transform: DynamicSchemaExpr
  ): MigrationBuilder[A, B, Handled, Provided] =
    ${ MigrationBuilderMacrosImpl.transformElementsImpl[A, B, Handled, Provided]('builder, 'at, 'transform) }

  /**
   * Applies a transformation to all keys in a map using selector syntax. Does
   * not affect field tracking (operates on map contents, not structure).
   */
  transparent inline def transformKeys(
    inline at: A => Any,
    transform: DynamicSchemaExpr
  ): MigrationBuilder[A, B, Handled, Provided] =
    ${ MigrationBuilderMacrosImpl.transformKeysImpl[A, B, Handled, Provided]('builder, 'at, 'transform) }

  /**
   * Applies a transformation to all values in a map using selector syntax. Does
   * not affect field tracking (operates on map contents, not structure).
   */
  transparent inline def transformValues(
    inline at: A => Any,
    transform: DynamicSchemaExpr
  ): MigrationBuilder[A, B, Handled, Provided] =
    ${ MigrationBuilderMacrosImpl.transformValuesImpl[A, B, Handled, Provided]('builder, 'at, 'transform) }

  /**
   * Renames a variant case using selector syntax. Adds the source case path to
   * Handled and the target case path to Provided as structured tuples. Case
   * paths are single-element tuples like ((\"case\", \"OldName\"),).
   */
  transparent inline def renameCase[FromPath <: Tuple, ToPath <: Tuple](
    inline from: A => Any,
    to: String
  ): MigrationBuilder[A, B, Tuple.Append[Handled, FromPath], Tuple.Append[Provided, ToPath]] =
    ${ MigrationBuilderMacrosImpl.renameCaseImpl[A, B, Handled, Provided, FromPath, ToPath]('builder, 'from, 'to) }

  /**
   * Applies nested migration actions to a specific variant case using selector
   * syntax. Adds the case path to both Handled and Provided as a structured
   * tuple since the case is being transformed but not renamed.
   */
  transparent inline def transformCase[CasePath <: Tuple](
    inline at: A => Any
  )(
    nestedActions: MigrationBuilder[A, A, EmptyTuple, EmptyTuple] => MigrationBuilder[A, A, ?, ?]
  ): MigrationBuilder[A, B, Tuple.Append[Handled, CasePath], Tuple.Append[Provided, CasePath]] =
    ${
      MigrationBuilderMacrosImpl
        .transformCaseImpl[A, B, Handled, Provided, CasePath]('builder, 'at, 'nestedActions)
    }
}

// Macro implementations for Scala 3 selector syntax with type tracking.
private[migration] object MigrationBuilderMacrosImpl {
  import scala.quoted.*

  // Build macro implementation

  /**
   * Macro implementation for the build method that validates migration
   * completeness at compile time and produces helpful error messages.
   */
  def buildImpl[A: Type, B: Type, Handled <: Tuple: Type, Provided <: Tuple: Type](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]]
  )(using q: Quotes): Expr[Migration[A, B]] = {
    import q.reflect.*

    // 1. Extract shape trees for both types
    val tpeA  = TypeRepr.of[A].dealias
    val tpeB  = TypeRepr.of[B].dealias
    val treeA = MacroHelpers.extractShapeTree(tpeA, Set.empty, "Migration validation")
    val treeB = MacroHelpers.extractShapeTree(tpeB, Set.empty, "Migration validation")

    // 2. Compute diff - returns List[List[Segment]] (full structure)
    val (removed, added) = TreeDiff.diff(treeA, treeB)

    // 3. Extract handled/provided as List[List[Segment]]
    val handled: List[List[Segment]]  = MacroHelpers.extractTuplePaths(TypeRepr.of[Handled])
    val provided: List[List[Segment]] = MacroHelpers.extractTuplePaths(TypeRepr.of[Provided])

    // 4. Compare as List[List[Segment]] - full structural comparison
    val unhandled  = removed.filterNot(path => handled.contains(path))
    val unprovided = added.filterNot(path => provided.contains(path))

    if (unhandled.nonEmpty || unprovided.nonEmpty) {
      report.errorAndAbort(buildValidationErrorMessage[A, B](unhandled, unprovided))
    }

    // 5. Return migration
    '{ $builder.buildPartial }
  }

  /**
   * Builds a detailed error message for migration validation failures.
   */
  private def buildValidationErrorMessage[A: Type, B: Type](
    unhandled: List[List[Segment]],
    unprovided: List[List[Segment]]
  )(using q: Quotes): String = {
    import q.reflect.*

    // Flatten to strings ONLY for error display
    val unhandledStrs  = unhandled.map(Path.render).sorted
    val unprovidedStrs = unprovided.map(Path.render).sorted

    // Categorize unhandled/unprovided into fields and cases
    val unhandledFields  = unhandledStrs.filterNot(_.startsWith("case:"))
    val unhandledCases   = unhandledStrs.filter(_.startsWith("case:"))
    val unprovidedFields = unprovidedStrs.filterNot(_.startsWith("case:"))
    val unprovidedCases  = unprovidedStrs.filter(_.startsWith("case:"))

    val sourceTypeName = TypeRepr.of[A].typeSymbol.name
    val targetTypeName = TypeRepr.of[B].typeSymbol.name

    val sb = new StringBuilder
    sb.append(s"Migration validation failed for $sourceTypeName => $targetTypeName:\n")

    if (unhandledFields.nonEmpty) {
      sb.append("\nUnhandled paths from source (need dropField, renameField, or transformField):\n")
      unhandledFields.foreach(p => sb.append(s"  - $p\n"))
    }

    if (unprovidedFields.nonEmpty) {
      sb.append("\nUnprovided paths for target (need addField or renameField):\n")
      unprovidedFields.foreach(p => sb.append(s"  - $p\n"))
    }

    if (unhandledCases.nonEmpty) {
      sb.append("\nUnhandled cases from source (need renameCase or transformCase):\n")
      unhandledCases.foreach(c => sb.append(s"  - ${c.stripPrefix("case:")}\n"))
    }

    if (unprovidedCases.nonEmpty) {
      sb.append("\nUnprovided cases for target (need renameCase):\n")
      unprovidedCases.foreach(c => sb.append(s"  - ${c.stripPrefix("case:")}\n"))
    }

    // Add hints
    sb.append("\n")
    if (unhandledFields.nonEmpty) {
      val example      = unhandledFields.head
      val selectorPath = example.split("\\.").mkString(".")
      sb.append(s"Hint: Use .dropField(_.$selectorPath, default) to handle removed fields\n")
    }
    if (unprovidedFields.nonEmpty) {
      val example      = unprovidedFields.head
      val selectorPath = example.split("\\.").mkString(".")
      sb.append(s"Hint: Use .addField(_.$selectorPath, default) to provide new fields\n")
    }
    if (unhandledFields.nonEmpty && unprovidedFields.nonEmpty) {
      sb.append("Hint: Use .renameField(_.oldPath, _.newPath) when a field was renamed\n")
    }
    if (unhandledCases.nonEmpty || unprovidedCases.nonEmpty) {
      sb.append("Hint: Use .renameCase(_.when[OldCase], \"NewCase\") when a case was renamed\n")
    }

    sb.toString
  }

  // Path type construction helpers

  /**
   * Convert a list of field names to a structured path tuple type.
   *
   * Example: List("address", "city") -> (("field", "address"), ("field",
   * "city"))
   */
  private def fieldPathToTupleType(using q: Quotes)(fieldNames: List[String]): q.reflect.TypeRepr = {
    import q.reflect.*

    if (fieldNames.isEmpty) {
      TypeRepr.of[EmptyTuple]
    } else {
      fieldNames.foldRight(TypeRepr.of[EmptyTuple]) { (name, acc) =>
        // Create ("field", name) tuple type
        val fieldLit    = ConstantType(StringConstant("field"))
        val nameLit     = ConstantType(StringConstant(name))
        val segmentType = TypeRepr.of[Tuple2].appliedTo(List(fieldLit, nameLit))
        TypeRepr.of[*:].appliedTo(List(segmentType, acc))
      }
    }
  }

  /**
   * Convert a case name to a structured path tuple type.
   *
   * Example: "Success" -> (("case", "Success"),)
   */
  private def casePathToTupleType(using q: Quotes)(caseName: String): q.reflect.TypeRepr = {
    import q.reflect.*

    // Create ("case", name) tuple type
    val caseLit     = ConstantType(StringConstant("case"))
    val nameLit     = ConstantType(StringConstant(caseName))
    val segmentType = TypeRepr.of[Tuple2].appliedTo(List(caseLit, nameLit))
    // Wrap in single-element tuple: (("case", name),)
    TypeRepr.of[*:].appliedTo(List(segmentType, TypeRepr.of[EmptyTuple]))
  }

  // Field extraction helpers

  /**
   * Helper to extract field path from selector expression as a List of field
   * names.
   *
   * For nested selectors like `_.address.street`, returns List("address",
   * "street").
   */
  private def extractFieldPathFromSelector(using q: Quotes)(term: q.reflect.Term): List[String] = {
    import q.reflect.*

    def toPathBody(t: Term): Term = t match {
      case Inlined(_, _, inlinedBlock)                     => toPathBody(inlinedBlock)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               =>
        report.errorAndAbort(s"Expected a lambda expression, got '${t.show}'", t.pos)
    }

    def extractPath(t: Term, acc: List[String]): List[String] = t match {
      case Select(parent, fieldName) =>
        extractPath(parent, fieldName :: acc)
      case _: Ident =>
        acc
      case Typed(expr, _) =>
        extractPath(expr, acc)
      case _ =>
        report.errorAndAbort(
          s"Unsupported selector pattern: '${t.show}'. Only simple field access is supported",
          t.pos
        )
    }

    val pathBody = toPathBody(term)
    val path     = extractPath(pathBody, Nil)

    if (path.isEmpty) {
      report.errorAndAbort("Selector must access at least one field", term.pos)
    }

    path
  }

  /**
   * Helper to extract all field paths from a Seq of selector expressions.
   *
   * For `Seq(_.firstName, _.lastName)`, returns
   * `List(List("firstName"), List("lastName"))`. For nested selectors like
   * `Seq(_.person.firstName, _.person.lastName)`, returns
   * `List(List("person", "firstName"), List("person", "lastName"))`.
   */
  private def extractFieldPathsFromSeq(using q: Quotes)(term: q.reflect.Term): List[List[String]] = {
    import q.reflect.*

    def unwrap(t: Term): Term = t match {
      case Inlined(_, _, inner) => unwrap(inner)
      case Typed(inner, _)      => unwrap(inner)
      case other                => other
    }

    def extractSelectors(t: Term): List[Term] = unwrap(t) match {
      // Seq(a, b, c) -> Apply(_, List(Typed(Repeated(elements), _)))
      case Apply(_, List(Typed(Repeated(elements, _), _))) =>
        elements
      // Seq(a, b, c) -> Apply(_, List(Repeated(elements)))
      case Apply(_, List(Repeated(elements, _))) =>
        elements
      case other =>
        report.errorAndAbort(
          s"Expected a Seq literal (e.g., Seq(_.field1, _.field2)), got: ${other.show}",
          other.pos
        )
    }

    val selectors = extractSelectors(term)
    selectors.map(extractFieldPathFromSelector)
  }

  /**
   * Extract the case name from a selector expression like `_.when[CaseType]`.
   * Uses the same pattern as MigrationBuilderMacros.extractCaseSelector.
   * Handles both regular case classes and enum values.
   */
  private def extractCaseNameFromSelector(using q: Quotes)(term: q.reflect.Term): String = {
    import q.reflect.*
    import scala.annotation.tailrec

    @tailrec
    def toPathBody(t: Term): Term = t match {
      case Inlined(_, _, inlinedBlock)                     => toPathBody(inlinedBlock)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               =>
        report.errorAndAbort(s"Expected a lambda expression, got '${t.show}'", t.pos)
    }

    def isEnumValue(tpe: TypeRepr): Boolean =
      tpe.termSymbol.flags.is(Flags.Enum)

    def getCaseName(tpe: TypeRepr): String = {
      val dealiased = tpe.dealias
      // For enum values (simple cases like `case Red`), use termSymbol
      if (isEnumValue(dealiased)) {
        dealiased.termSymbol.name
      } else {
        dealiased.typeSymbol.name
      }
    }

    def extractCaseName(t: Term): String = t match {
      // Pattern: _.when[CaseType] or _.field.when[CaseType]
      // This is TypeApply(Apply(TypeApply(caseTerm, _), List(parent)), List(typeTree))
      case TypeApply(Apply(TypeApply(caseTerm, _), List(_)), List(typeTree)) if caseTerm match {
            case Select(_, name) => name == "when"
            case Ident(name)     => name == "when"
            case _               => false
          } =>
        getCaseName(typeTree.tpe)
      case _ =>
        report.errorAndAbort(
          s"Case selector must use .when[CaseType] pattern (e.g., _.when[MyCase] or _.field.when[MyCase]), got '${t.show}'",
          t.pos
        )
    }

    val pathBody = toPathBody(term)
    extractCaseName(pathBody)
  }

  // Shared helper for field ops that add to both Handled and Provided

  /**
   * Shared implementation for field operations that add the same field path to
   * both Handled and Provided type lists.
   *
   * @param buildCall
   *   Function that takes (builder, optic) and returns the builder method call
   *   expression
   */
  private def dualTrackingFieldOpImpl[
    A: Type,
    B: Type,
    Handled <: Tuple: Type,
    Provided <: Tuple: Type,
    FieldPath <: Tuple: Type
  ](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    at: Expr[A => Any]
  )(
    buildCall: (Expr[MigrationBuilder[A, B, Handled, Provided]], Expr[DynamicOptic]) => Expr[
      MigrationBuilder[?, ?, ?, ?]
    ]
  )(using
    q: Quotes
  ): Expr[MigrationBuilder[A, B, Tuple.Append[Handled, FieldPath], Tuple.Append[Provided, FieldPath]]] = {
    import q.reflect.*

    val optic      = MigrationBuilderMacros.extractOptic[A, Any](at)
    val fieldNames = extractFieldPathFromSelector(at.asTerm)

    // Create structured path tuple type
    val fieldPathType     = fieldPathToTupleType(fieldNames).asType.asInstanceOf[Type[FieldPath]]
    given Type[FieldPath] = fieldPathType

    '{
      ${ buildCall(builder, optic) }
        .asInstanceOf[MigrationBuilder[A, B, Tuple.Append[Handled, FieldPath], Tuple.Append[Provided, FieldPath]]]
    }
  }

  // Shared helper for passthrough ops that don't affect Handled/Provided

  /**
   * Shared implementation for operations that don't modify the Handled or
   * Provided type lists (like transformElements, transformKeys,
   * transformValues).
   */
  private def passthroughOpImpl[A: Type, B: Type, Handled <: Tuple: Type, Provided <: Tuple: Type](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    at: Expr[A => Any]
  )(
    buildCall: (Expr[MigrationBuilder[A, B, Handled, Provided]], Expr[DynamicOptic]) => Expr[
      MigrationBuilder[?, ?, ?, ?]
    ]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    val optic = MigrationBuilderMacros.extractOptic[A, Any](at)
    '{ ${ buildCall(builder, optic) }.asInstanceOf[MigrationBuilder[A, B, Handled, Provided]] }
  }

  // Single-tracking field operations (addField, dropField)

  def addFieldImpl[
    A: Type,
    B: Type,
    Handled <: Tuple: Type,
    Provided <: Tuple: Type,
    FieldPath <: Tuple: Type
  ](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    target: Expr[B => Any],
    default: Expr[DynamicSchemaExpr]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, Handled, Tuple.Append[Provided, FieldPath]]] = {
    import q.reflect.*

    val optic      = MigrationBuilderMacros.extractOptic[B, Any](target)
    val fieldNames = extractFieldPathFromSelector(target.asTerm)

    // Create structured path tuple type
    val fieldPathType     = fieldPathToTupleType(fieldNames).asType.asInstanceOf[Type[FieldPath]]
    given Type[FieldPath] = fieldPathType

    '{
      $builder
        .withAction(MigrationAction.AddField($optic, $default))
        .asInstanceOf[MigrationBuilder[A, B, Handled, Tuple.Append[Provided, FieldPath]]]
    }
  }

  def dropFieldImpl[
    A: Type,
    B: Type,
    Handled <: Tuple: Type,
    Provided <: Tuple: Type,
    FieldPath <: Tuple: Type
  ](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    source: Expr[A => Any],
    defaultForReverse: Expr[DynamicSchemaExpr]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, Tuple.Append[Handled, FieldPath], Provided]] = {
    import q.reflect.*

    val optic      = MigrationBuilderMacros.extractOptic[A, Any](source)
    val fieldNames = extractFieldPathFromSelector(source.asTerm)

    // Create structured path tuple type
    val fieldPathType     = fieldPathToTupleType(fieldNames).asType.asInstanceOf[Type[FieldPath]]
    given Type[FieldPath] = fieldPathType

    '{
      $builder
        .withAction(MigrationAction.DropField($optic, $defaultForReverse))
        .asInstanceOf[MigrationBuilder[A, B, Tuple.Append[Handled, FieldPath], Provided]]
    }
  }

  // Rename operation (handles both paths separately)

  def renameFieldImpl[
    A: Type,
    B: Type,
    Handled <: Tuple: Type,
    Provided <: Tuple: Type,
    FromPath <: Tuple: Type,
    ToPath <: Tuple: Type
  ](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    from: Expr[A => Any],
    to: Expr[B => Any]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, Tuple.Append[Handled, FromPath], Tuple.Append[Provided, ToPath]]] = {
    import q.reflect.*

    val fromOptic   = MigrationBuilderMacros.extractOptic[A, Any](from)
    val toFieldName = MigrationBuilderMacros.extractFieldName[B, Any](to)
    val fromNames   = extractFieldPathFromSelector(from.asTerm)
    val toNames     = extractFieldPathFromSelector(to.asTerm)

    // Create structured path tuple types
    val fromPathType     = fieldPathToTupleType(fromNames).asType.asInstanceOf[Type[FromPath]]
    val toPathType       = fieldPathToTupleType(toNames).asType.asInstanceOf[Type[ToPath]]
    given Type[FromPath] = fromPathType
    given Type[ToPath]   = toPathType

    '{
      $builder
        .withAction(MigrationAction.Rename($fromOptic, $toFieldName))
        .asInstanceOf[MigrationBuilder[A, B, Tuple.Append[Handled, FromPath], Tuple.Append[Provided, ToPath]]]
    }
  }

  // Dual-tracking field operations (add to both Handled and Provided)

  def transformFieldImpl[
    A: Type,
    B: Type,
    Handled <: Tuple: Type,
    Provided <: Tuple: Type,
    FieldPath <: Tuple: Type
  ](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    at: Expr[A => Any],
    transform: Expr[DynamicSchemaExpr]
  )(using
    q: Quotes
  ): Expr[MigrationBuilder[A, B, Tuple.Append[Handled, FieldPath], Tuple.Append[Provided, FieldPath]]] =
    dualTrackingFieldOpImpl[A, B, Handled, Provided, FieldPath](builder, at) { (b, o) =>
      '{ $b.withAction(MigrationAction.TransformValue($o, $transform)) }
    }

  def mandateFieldImpl[
    A: Type,
    B: Type,
    Handled <: Tuple: Type,
    Provided <: Tuple: Type,
    FieldPath <: Tuple: Type
  ](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    at: Expr[A => Any],
    default: Expr[DynamicSchemaExpr]
  )(using
    q: Quotes
  ): Expr[MigrationBuilder[A, B, Tuple.Append[Handled, FieldPath], Tuple.Append[Provided, FieldPath]]] =
    dualTrackingFieldOpImpl[A, B, Handled, Provided, FieldPath](builder, at) { (b, o) =>
      '{ $b.withAction(MigrationAction.Mandate($o, $default)) }
    }

  def optionalizeFieldImpl[
    A: Type,
    B: Type,
    Handled <: Tuple: Type,
    Provided <: Tuple: Type,
    FieldPath <: Tuple: Type
  ](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    at: Expr[A => Any],
    defaultForReverse: Expr[DynamicSchemaExpr]
  )(using
    q: Quotes
  ): Expr[MigrationBuilder[A, B, Tuple.Append[Handled, FieldPath], Tuple.Append[Provided, FieldPath]]] =
    dualTrackingFieldOpImpl[A, B, Handled, Provided, FieldPath](builder, at) { (b, o) =>
      '{ $b.withAction(MigrationAction.Optionalize($o, $defaultForReverse)) }
    }

  def changeFieldTypeImpl[
    A: Type,
    B: Type,
    Handled <: Tuple: Type,
    Provided <: Tuple: Type,
    FieldPath <: Tuple: Type
  ](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    at: Expr[A => Any],
    converter: Expr[PrimitiveConverter]
  )(using
    q: Quotes
  ): Expr[MigrationBuilder[A, B, Tuple.Append[Handled, FieldPath], Tuple.Append[Provided, FieldPath]]] =
    dualTrackingFieldOpImpl[A, B, Handled, Provided, FieldPath](builder, at) { (b, o) =>
      '{ $b.withAction(MigrationAction.ChangeType($o, $converter)) }
    }

  // Multi-field operations (joinFields, splitField)

  def joinFieldsImpl[
    A: Type,
    B: Type,
    Handled <: Tuple: Type,
    Provided <: Tuple: Type
  ](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    target: Expr[B => Any],
    sourcePaths: Expr[Seq[A => Any]],
    combiner: Expr[DynamicSchemaExpr]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ? <: Tuple, ? <: Tuple]] = {
    import q.reflect.*

    val targetOptic  = MigrationBuilderMacros.extractOptic[B, Any](target)
    val sourceOptics = MigrationBuilderMacros.extractOptics[A, Any](sourcePaths)
    val targetPath   = extractFieldPathFromSelector(target.asTerm)

    // Extract all source field paths from the Seq
    val sourcePathsList = extractFieldPathsFromSeq(sourcePaths.asTerm)

    if (sourcePathsList.isEmpty) {
      report.errorAndAbort("joinFields requires at least one source field", sourcePaths.asTerm.pos)
    }

    // Validate that all source paths share the same parent
    if (sourcePathsList.length > 1) {
      val parents = sourcePathsList.map(_.dropRight(1))
      if (!parents.forall(_ == parents.head)) {
        report.errorAndAbort(
          s"joinFields source fields must share common parent. Found paths: ${sourcePathsList.map(_.mkString(".")).mkString(", ")}",
          sourcePaths.asTerm.pos
        )
      }
    }

    // Build the new Handled type by appending all source field paths as structured tuples
    val newHandledRepr = sourcePathsList.foldLeft(TypeRepr.of[Handled]) { (acc, path) =>
      val pathType = fieldPathToTupleType(path)
      TypeRepr.of[Tuple.Append].appliedTo(List(acc, pathType))
    }

    // Build the new Provided type by appending the target field path as structured tuple
    val targetPathType  = fieldPathToTupleType(targetPath)
    val newProvidedRepr = TypeRepr.of[Tuple.Append].appliedTo(List(TypeRepr.of[Provided], targetPathType))

    // Build the result type
    val resultTypeRepr = TypeRepr
      .of[MigrationBuilder]
      .appliedTo(
        List(TypeRepr.of[A], TypeRepr.of[B], newHandledRepr, newProvidedRepr)
      )

    resultTypeRepr.asType match {
      case '[MigrationBuilder[A, B, h, p]] =>
        '{
          $builder
            .withAction(MigrationAction.Join($targetOptic, $sourceOptics, $combiner))
            .asInstanceOf[MigrationBuilder[A, B, h & Tuple, p & Tuple]]
        }
    }
  }

  def splitFieldImpl[
    A: Type,
    B: Type,
    Handled <: Tuple: Type,
    Provided <: Tuple: Type
  ](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    source: Expr[A => Any],
    targetPaths: Expr[Seq[B => Any]],
    splitter: Expr[DynamicSchemaExpr]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ? <: Tuple, ? <: Tuple]] = {
    import q.reflect.*

    val sourceOptic  = MigrationBuilderMacros.extractOptic[A, Any](source)
    val targetOptics = MigrationBuilderMacros.extractOptics[B, Any](targetPaths)
    val sourcePath   = extractFieldPathFromSelector(source.asTerm)

    // Extract all target field paths from the Seq
    val targetPathsList = extractFieldPathsFromSeq(targetPaths.asTerm)

    if (targetPathsList.isEmpty) {
      report.errorAndAbort("splitField requires at least one target field", targetPaths.asTerm.pos)
    }

    // Validate that all target paths share the same parent
    if (targetPathsList.length > 1) {
      val parents = targetPathsList.map(_.dropRight(1))
      if (!parents.forall(_ == parents.head)) {
        report.errorAndAbort(
          s"splitField target fields must share common parent. Found paths: ${targetPathsList.map(_.mkString(".")).mkString(", ")}",
          targetPaths.asTerm.pos
        )
      }
    }

    // Build the new Handled type by appending the source field path as structured tuple
    val sourcePathType = fieldPathToTupleType(sourcePath)
    val newHandledRepr = TypeRepr.of[Tuple.Append].appliedTo(List(TypeRepr.of[Handled], sourcePathType))

    // Build the new Provided type by appending all target field paths as structured tuples
    val newProvidedRepr = targetPathsList.foldLeft(TypeRepr.of[Provided]) { (acc, path) =>
      val pathType = fieldPathToTupleType(path)
      TypeRepr.of[Tuple.Append].appliedTo(List(acc, pathType))
    }

    // Build the result type
    val resultTypeRepr = TypeRepr
      .of[MigrationBuilder]
      .appliedTo(
        List(TypeRepr.of[A], TypeRepr.of[B], newHandledRepr, newProvidedRepr)
      )

    resultTypeRepr.asType match {
      case '[MigrationBuilder[A, B, h, p]] =>
        '{
          $builder
            .withAction(MigrationAction.Split($sourceOptic, $targetOptics, $splitter))
            .asInstanceOf[MigrationBuilder[A, B, h & Tuple, p & Tuple]]
        }
    }
  }

  // Passthrough operations (don't affect Handled/Provided)

  def transformElementsImpl[A: Type, B: Type, Handled <: Tuple: Type, Provided <: Tuple: Type](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    at: Expr[A => Any],
    transform: Expr[DynamicSchemaExpr]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, Handled, Provided]] =
    passthroughOpImpl(builder, at)((b, o) => '{ $b.withAction(MigrationAction.TransformElements($o, $transform)) })

  def transformKeysImpl[A: Type, B: Type, Handled <: Tuple: Type, Provided <: Tuple: Type](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    at: Expr[A => Any],
    transform: Expr[DynamicSchemaExpr]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, Handled, Provided]] =
    passthroughOpImpl(builder, at)((b, o) => '{ $b.withAction(MigrationAction.TransformKeys($o, $transform)) })

  def transformValuesImpl[A: Type, B: Type, Handled <: Tuple: Type, Provided <: Tuple: Type](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    at: Expr[A => Any],
    transform: Expr[DynamicSchemaExpr]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, Handled, Provided]] =
    passthroughOpImpl(builder, at)((b, o) => '{ $b.withAction(MigrationAction.TransformValues($o, $transform)) })

  // Case operations (renameCase, transformCase)

  def renameCaseImpl[
    A: Type,
    B: Type,
    Handled <: Tuple: Type,
    Provided <: Tuple: Type,
    FromPath <: Tuple: Type,
    ToPath <: Tuple: Type
  ](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    from: Expr[A => Any],
    to: Expr[String]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, Tuple.Append[Handled, FromPath], Tuple.Append[Provided, ToPath]]] = {
    import q.reflect.*

    val (fromOptic, fromCaseName) = MigrationBuilderMacros.extractCaseSelector[A, Any](from)

    // Extract the case name as a string literal
    val caseNameStr = extractCaseNameFromSelector(from.asTerm)

    // Extract the target case name from the string expression
    val toCaseStr = to.asTerm match {
      case Literal(StringConstant(s))                => s
      case Inlined(_, _, Literal(StringConstant(s))) => s
      case _                                         =>
        report.errorAndAbort(
          "renameCase target must be a string literal (e.g., \"NewCaseName\")",
          to.asTerm.pos
        )
    }

    // Create structured path tuple types for case names: (("case", "CaseName"),)
    val fromPathType     = casePathToTupleType(caseNameStr).asType.asInstanceOf[Type[FromPath]]
    val toPathType       = casePathToTupleType(toCaseStr).asType.asInstanceOf[Type[ToPath]]
    given Type[FromPath] = fromPathType
    given Type[ToPath]   = toPathType

    '{
      $builder
        .withAction(MigrationAction.RenameCase($fromOptic, $fromCaseName, $to))
        .asInstanceOf[MigrationBuilder[A, B, Tuple.Append[Handled, FromPath], Tuple.Append[Provided, ToPath]]]
    }
  }

  def transformCaseImpl[
    A: Type,
    B: Type,
    Handled <: Tuple: Type,
    Provided <: Tuple: Type,
    CasePath <: Tuple: Type
  ](
    builder: Expr[MigrationBuilder[A, B, Handled, Provided]],
    at: Expr[A => Any],
    nestedActions: Expr[MigrationBuilder[A, A, EmptyTuple, EmptyTuple] => MigrationBuilder[A, A, ?, ?]]
  )(using
    q: Quotes
  ): Expr[MigrationBuilder[A, B, Tuple.Append[Handled, CasePath], Tuple.Append[Provided, CasePath]]] = {
    import q.reflect.*

    val (atOptic, caseName) = MigrationBuilderMacros.extractCaseSelector[A, Any](at)

    // Extract the case name from the selector
    val caseNameStr = extractCaseNameFromSelector(at.asTerm)

    // Create structured path tuple type for case name: (("case", "CaseName"),)
    val casePathType     = casePathToTupleType(caseNameStr).asType.asInstanceOf[Type[CasePath]]
    given Type[CasePath] = casePathType

    '{
      val sourceSchema                                                 = $builder.sourceSchema
      val emptyBuilder: MigrationBuilder[A, A, EmptyTuple, EmptyTuple] =
        MigrationBuilder(sourceSchema, sourceSchema, Vector.empty)
      val transformedBuilder = $nestedActions.apply(emptyBuilder)
      $builder
        .withAction(MigrationAction.TransformCase($atOptic, $caseName, transformedBuilder.actions))
        .asInstanceOf[MigrationBuilder[A, B, Tuple.Append[Handled, CasePath], Tuple.Append[Provided, CasePath]]]
    }
  }
}

// Empty companion object for Scala 2 import compatibility in shared tests.
// Allows `import MigrationBuilderSyntax._` to compile in both Scala 2 and 3.
object MigrationBuilderSyntax
