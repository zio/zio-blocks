package zio.blocks.schema.migration

import scala.quoted.*
import zio.blocks.schema._

/**
 * Macros for the MigrationBuilder DSL. Extract field names and nested paths
 * from selector functions at compile time.
 */
private[migration] object MigrationBuilderMacros {

  /** Extract a field name from a selector function like `_.fieldName`. */
  def extractFieldName(using Quotes)(selector: quotes.reflect.Term): Either[String, String] = {
    import quotes.reflect.*

    def loop(term: quotes.reflect.Term): Either[String, String] = term match {
      case Inlined(_, _, body)                                                       => loop(body)
      case Lambda(List(ValDef(_, _, _)), Select(Ident(_), fieldName))                => Right(fieldName)
      case Block(List(), Lambda(List(ValDef(_, _, _)), Select(Ident(_), fieldName))) => Right(fieldName)
      case Select(_, fieldName)                                                      => Right(fieldName)
      case other                                                                     => Left(s"Expected a field selector like '_.fieldName', got: ${other.show}")
    }

    loop(selector)
  }

  /**
   * Extract a path from nested selector like `_.address.street`. Returns the
   * path as a list of field names in order (["address", "street"]).
   */
  def extractNestedPath(using Quotes)(selector: quotes.reflect.Term): Either[String, List[String]] = {
    import quotes.reflect.*

    def loop(term: quotes.reflect.Term, acc: List[String]): Either[String, List[String]] =
      term match {
        case Inlined(_, _, body)                     => loop(body, acc)
        case Select(inner @ Select(_, _), fieldName) =>
          // Nested: continue traversing
          loop(inner, fieldName :: acc)
        case Select(Ident(_), fieldName) =>
          // Base case: _.fieldName
          Right(fieldName :: acc)
        case Ident(_) =>
          // Just the parameter itself (shouldn't happen normally)
          Right(acc)
        case other =>
          Left(s"Unsupported selector syntax: ${other.show}")
      }

    selector match {
      case Inlined(_, _, body)                 => extractNestedPath(body)
      case Lambda(List(ValDef(_, _, _)), body) =>
        loop(body, Nil)
      case Block(List(), Lambda(List(ValDef(_, _, _)), body)) =>
        loop(body, Nil)
      case other =>
        Left(s"Expected a field selector, got: ${other.show}")
    }
  }

  /** Build quoted DynamicOptic from path parts. */
  def buildOpticExpr(using Quotes)(path: List[String]): Expr[DynamicOptic] =
    path.foldLeft('{ DynamicOptic.root }) { (optic, field) =>
      '{ $optic.field(${ Expr(field) }) }
    }

  // ============================================================================
  // Rename with nested path support
  // ============================================================================

  def renameFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    fromSelector: Expr[A => Any],
    toSelector: Expr[B => Any]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*

    // Extract nested paths
    val fromPath = extractNestedPath(fromSelector.asTerm) match {
      case Right(path) if path.nonEmpty => path
      case Right(_)                     => report.errorAndAbort("Empty path in 'from' selector")
      case Left(err)                    => report.errorAndAbort(err)
    }

    val toPath = extractNestedPath(toSelector.asTerm) match {
      case Right(path) if path.nonEmpty => path
      case Right(_)                     => report.errorAndAbort("Empty path in 'to' selector")
      case Left(err)                    => report.errorAndAbort(err)
    }

    // For rename, we need the parent path and the field names
    val fromParent = fromPath.init
    val fromField  = fromPath.last
    val toField    = toPath.last

    // Validate that we are renaming within the same object (paths match)
    // Technically we could allow mismatch if we want to allow "rename" to imply "ignore target path"
    // but for correctness, we should probably warn or ensure they match.
    // For now, we align with the behavior of using fromPath's parent.

    if (fromParent.nonEmpty) {
      val opticExpr = buildOpticExpr(fromParent)
      '{ $builder.renameField(${ Expr(fromField) }, ${ Expr(toField) }, $opticExpr) }
    } else {
      '{ $builder.renameField(${ Expr(fromField) }, ${ Expr(toField) }) }
    }
  }

  // ============================================================================
  // Drop with nested path support
  // ============================================================================

  def dropFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    selector: Expr[A => Any]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*

    val path = extractNestedPath(selector.asTerm) match {
      case Right(p) if p.nonEmpty => p
      case Right(_)               => report.errorAndAbort("Empty path in selector")
      case Left(err)              => report.errorAndAbort(err)
    }

    if (path.length == 1) {
      '{ $builder.dropField(${ Expr(path.head) }) }
    } else {
      val parentOptic = buildOpticExpr(path.init)
      '{ $builder.dropField(${ Expr(path.last) }, $parentOptic) }
    }
  }

  // ============================================================================
  // Optionalize with nested path support
  // ============================================================================

  def optionalizeImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    selector: Expr[A => Any]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*

    val path = extractNestedPath(selector.asTerm) match {
      case Right(p) if p.nonEmpty => p
      case Right(_)               => report.errorAndAbort("Empty path in selector")
      case Left(err)              => report.errorAndAbort(err)
    }

    if (path.length == 1) {
      '{ $builder.optionalizeField(${ Expr(path.head) }) }
    } else {
      val parentOptic = buildOpticExpr(path.init)
      '{ $builder.optionalizeField(${ Expr(path.last) }, $parentOptic) }
    }
  }

  // ============================================================================
  // AddField with nested path support
  // ============================================================================

  def addFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    selector: Expr[B => Any],
    defaultValue: Expr[DynamicValue]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*

    val path = extractNestedPath(selector.asTerm) match {
      case Right(p) if p.nonEmpty => p
      case Right(_)               => report.errorAndAbort("Empty path in selector")
      case Left(err)              => report.errorAndAbort(err)
    }

    if (path.length == 1) {
      '{ $builder.addField(${ Expr(path.head) }, $defaultValue) }
    } else {
      val parentOptic = buildOpticExpr(path.init)
      '{ $builder.addField(${ Expr(path.last) }, $defaultValue, $parentOptic) }
    }
  }

  // ============================================================================
  // Mandate with nested path support
  // ============================================================================

  def mandateFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    sourceSelector: Expr[A => Option[?]],
    unusedTargetSelector: Expr[B => Any],
    defaultValue: Expr[DynamicValue]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*
    val _ = unusedTargetSelector

    val path = extractNestedPath(sourceSelector.asTerm) match {
      case Right(p) if p.nonEmpty => p
      case Right(_)               => report.errorAndAbort("Empty path in selector")
      case Left(err)              => report.errorAndAbort(err)
    }

    if (path.length == 1) {
      '{ $builder.mandateField(${ Expr(path.head) }, $defaultValue) }
    } else {
      val parentOptic = buildOpticExpr(path.init)
      '{ $builder.mandateField(${ Expr(path.last) }, $defaultValue, $parentOptic) }
    }
  }

  // ============================================================================
  // TransformField with nested path support
  // ============================================================================

  def transformFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    sourceSelector: Expr[A => Any],
    unusedTargetSelector: Expr[B => Any],
    transform: Expr[SchemaExpr[DynamicValue, DynamicValue]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*
    val _ = unusedTargetSelector

    val path = extractNestedPath(sourceSelector.asTerm) match {
      case Right(p) if p.nonEmpty => p
      case Right(_)               => report.errorAndAbort("Empty path in selector")
      case Left(err)              => report.errorAndAbort(err)
    }

    val optic = buildOpticExpr(path)
    '{ $builder.transformField($optic, $transform) }
  }

  // ============================================================================
  // ChangeFieldType with nested path support
  // ============================================================================

  def changeFieldTypeImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    sourceSelector: Expr[A => Any],
    unusedTargetSelector: Expr[B => Any],
    converter: Expr[SchemaExpr[DynamicValue, DynamicValue]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*
    val _ = unusedTargetSelector

    val path = extractNestedPath(sourceSelector.asTerm) match {
      case Right(p) if p.nonEmpty => p
      case Right(_)               => report.errorAndAbort("Empty path in selector")
      case Left(err)              => report.errorAndAbort(err)
    }

    if (path.length == 1) {
      '{ $builder.changeFieldType(${ Expr(path.head) }, $converter) }
    } else {
      val parentOptic = buildOpticExpr(path.init)
      '{ $builder.changeFieldType(${ Expr(path.last) }, $converter, $parentOptic) }
    }
  }

  // ============================================================================
  // TransformElements with nested path support
  // ============================================================================

  def transformElementsImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    selector: Expr[A => Seq[?]],
    transform: Expr[SchemaExpr[DynamicValue, DynamicValue]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*

    val path = extractNestedPath(selector.asTerm) match {
      case Right(p) if p.nonEmpty => p
      case Right(_)               => report.errorAndAbort("Empty path in selector")
      case Left(err)              => report.errorAndAbort(err)
    }

    val optic = buildOpticExpr(path)
    '{ $builder.transformElements($optic, $transform) }
  }

  // ============================================================================
  // TransformKeys with nested path support
  // ============================================================================

  def transformKeysImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    selector: Expr[A => Map[?, ?]],
    transform: Expr[SchemaExpr[DynamicValue, DynamicValue]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*

    val path = extractNestedPath(selector.asTerm) match {
      case Right(p) if p.nonEmpty => p
      case Right(_)               => report.errorAndAbort("Empty path in selector")
      case Left(err)              => report.errorAndAbort(err)
    }

    val optic = buildOpticExpr(path)
    '{ $builder.transformKeys($optic, $transform) }
  }

  // ============================================================================
  // TransformValues with nested path support
  // ============================================================================

  def transformValuesImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    selector: Expr[A => Map[?, ?]],
    transform: Expr[SchemaExpr[DynamicValue, DynamicValue]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*

    val path = extractNestedPath(selector.asTerm) match {
      case Right(p) if p.nonEmpty => p
      case Right(_)               => report.errorAndAbort("Empty path in selector")
      case Left(err)              => report.errorAndAbort(err)
    }

    val optic = buildOpticExpr(path)
    '{ $builder.transformValues($optic, $transform) }
  }

  // ============================================================================
  // JoinFields with selector-based paths
  // ============================================================================

  // Note: Variadic selector approach (joinFieldsImpl) was removed because selectors
  // passed as Seq cannot be inspected at compile time. Use joinFields2 for the
  // common 2-source case, or use joinFields() directly with DynamicOptic paths
  // for more than 2 sources.

  /**
   * Join exactly 2 source fields (most common case) using type-safe selectors.
   */
  def joinFields2Impl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    source1: Expr[A => Any],
    source2: Expr[A => Any],
    targetSelector: Expr[B => Any],
    combiner: Expr[SchemaExpr[DynamicValue, DynamicValue]],
    splitterForReverse: Expr[Option[SchemaExpr[DynamicValue, DynamicValue]]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*

    // Extract source paths
    val source1Path = extractNestedPath(source1.asTerm) match {
      case Right(p) if p.nonEmpty => p
      case Right(_)               => report.errorAndAbort("Empty path in source1 selector")
      case Left(err)              => report.errorAndAbort(err)
    }

    val source2Path = extractNestedPath(source2.asTerm) match {
      case Right(p) if p.nonEmpty => p
      case Right(_)               => report.errorAndAbort("Empty path in source2 selector")
      case Left(err)              => report.errorAndAbort(err)
    }

    val targetPath = extractNestedPath(targetSelector.asTerm) match {
      case Right(p) if p.nonEmpty => p
      case Right(_)               => report.errorAndAbort("Empty path in target selector")
      case Left(err)              => report.errorAndAbort(err)
    }

    val source1Optic = buildOpticExpr(source1Path)
    val source2Optic = buildOpticExpr(source2Path)
    val targetOptic  = buildOpticExpr(targetPath)

    '{
      $builder.joinFields(
        Vector($source1Optic, $source2Optic),
        $targetOptic,
        $combiner,
        $splitterForReverse
      )
    }
  }

  // ============================================================================
  // SplitField with selector-based paths
  // ============================================================================

  /**
   * Split to exactly 2 target fields (most common case) using type-safe
   * selectors.
   */
  def splitField2Impl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    sourceSelector: Expr[A => Any],
    target1: Expr[B => Any],
    target2: Expr[B => Any],
    splitter: Expr[SchemaExpr[DynamicValue, DynamicValue]],
    combinerForReverse: Expr[Option[SchemaExpr[DynamicValue, DynamicValue]]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    import quotes.reflect.*

    // Extract source path
    val sourcePath = extractNestedPath(sourceSelector.asTerm) match {
      case Right(p) if p.nonEmpty => p
      case Right(_)               => report.errorAndAbort("Empty path in source selector")
      case Left(err)              => report.errorAndAbort(err)
    }

    val target1Path = extractNestedPath(target1.asTerm) match {
      case Right(p) if p.nonEmpty => p
      case Right(_)               => report.errorAndAbort("Empty path in target1 selector")
      case Left(err)              => report.errorAndAbort(err)
    }

    val target2Path = extractNestedPath(target2.asTerm) match {
      case Right(p) if p.nonEmpty => p
      case Right(_)               => report.errorAndAbort("Empty path in target2 selector")
      case Left(err)              => report.errorAndAbort(err)
    }

    val sourceOptic  = buildOpticExpr(sourcePath)
    val target1Optic = buildOpticExpr(target1Path)
    val target2Optic = buildOpticExpr(target2Path)

    '{
      $builder.splitField(
        $sourceOptic,
        Vector($target1Optic, $target2Optic),
        $splitter,
        $combinerForReverse
      )
    }
  }
}
