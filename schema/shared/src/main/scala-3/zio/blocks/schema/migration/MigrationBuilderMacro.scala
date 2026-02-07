package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue}
import scala.quoted.*

object MigrationBuilderMacros {

  inline def fieldPath[S, A](inline selector: S => A): String =
    ${ fieldPathImpl[S, A]('selector) }

  def fieldPathImpl[S: Type, A: Type](selector: Expr[S => A])(using q: Quotes): Expr[String] = {
    val pathParts = extractFieldPath(selector)
    Expr(pathParts.mkString("."))
  }

  inline def lastFieldName[S, A](inline selector: S => A): String =
    ${ lastFieldNameImpl[S, A]('selector) }

  def lastFieldNameImpl[S: Type, A: Type](selector: Expr[S => A])(using q: Quotes): Expr[String] = {
    import q.reflect.*

    def toPathBody(term: Term): Term = term match {
      case Inlined(_, _, inlinedBlock)                     => toPathBody(inlinedBlock)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               => report.errorAndAbort(s"Expected a lambda expression, got '${term.show}'")
    }

    def extractLastField(term: Term): String = term match {
      case Select(_, fieldName) =>
        fieldName
      case Apply(Apply(_, List(_)), List(Literal(IntConstant(i)))) =>
        s"_$i"
      case Apply(TypeApply(Select(_, "apply"), _), List(Literal(IntConstant(i)))) =>
        s"_$i"
      case _: Ident =>
        report.errorAndAbort("Selector must access at least one field, e.g., _.name")
      case _ =>
        report.errorAndAbort(
          s"Unsupported path expression. Expected simple field access like _.field, got '${term.show}'."
        )
    }

    val pathBody  = toPathBody(selector.asTerm)
    val fieldName = extractLastField(pathBody)

    Expr(fieldName)
  }

  inline def toDynamicOptic[S, A](inline selector: S => A): DynamicOptic =
    ${ toDynamicOpticImpl[S, A]('selector) }

  def toDynamicOpticImpl[S: Type, A: Type](selector: Expr[S => A])(using q: Quotes): Expr[DynamicOptic] = {
    val pathParts  = extractFieldPath(selector)
    val fieldExprs = pathParts.map(name => '{ DynamicOptic.Node.Field(${ Expr(name) }) })
    val nodesExpr  = Expr.ofSeq(fieldExprs)
    '{ DynamicOptic(IndexedSeq($nodesExpr*)) }
  }

  inline def extractFieldNames[T]: Set[String] =
    ${ extractFieldNamesImpl[T] }

  def extractFieldNamesImpl[T: Type](using q: Quotes): Expr[Set[String]] = {
    import q.reflect.*
    val fieldNames = MigrationHelperMacro.getFieldNamesFromType(TypeRepr.of[T].dealias)
    val fieldExprs = fieldNames.toList.map(Expr(_))
    '{ Set(${ Expr.ofSeq(fieldExprs) }*) }
  }

  inline def validateMigrationCompleteness[A, B](
    sourceFields: Set[String],
    targetFields: Set[String],
    addedFields: Set[String],
    removedFields: Set[String],
    renamedFields: Map[String, String]
  ): Unit = ${
    validateMigrationCompletenessImpl[A, B]('sourceFields, 'targetFields, 'addedFields, 'removedFields, 'renamedFields)
  }

  def validateMigrationCompletenessImpl[A: Type, B: Type](
    sourceFields: Expr[Set[String]],
    targetFields: Expr[Set[String]],
    addedFields: Expr[Set[String]],
    removedFields: Expr[Set[String]],
    renamedFields: Expr[Map[String, String]]
  )(using q: Quotes): Expr[Unit] = {
    val aTypeName = Type.show[A]
    val bTypeName = Type.show[B]

    '{
      val source  = $sourceFields
      val target  = $targetFields
      val added   = $addedFields
      val removed = $removedFields
      val renamed = $renamedFields

      val transformedSource = (source -- removed -- renamed.keySet) ++ added ++ renamed.values

      val missing = target -- transformedSource
      val extra   = transformedSource -- target

      if (missing.nonEmpty || extra.nonEmpty) {
        val missingMsg = if (missing.nonEmpty) s"Missing fields in target: ${missing.mkString(", ")}" else ""
        val extraMsg   = if (extra.nonEmpty) s"Extra fields not in target: ${extra.mkString(", ")}" else ""
        val msgs       = Seq(missingMsg, extraMsg).filter(_.nonEmpty).mkString("; ")
        throw new IllegalStateException(
          s"Migration validation failed from ${${ Expr(aTypeName) }} to ${${ Expr(bTypeName) }}: $msgs"
        )
      }
    }
  }

  inline def validateMigrationAtCompileTime[A, B](
    inline addedFields: Set[String],
    inline removedFields: Set[String],
    inline renamedFrom: Set[String],
    inline renamedTo: Set[String]
  ): Unit = ${ validateMigrationAtCompileTimeImpl[A, B]('addedFields, 'removedFields, 'renamedFrom, 'renamedTo) }

  def validateMigrationAtCompileTimeImpl[A: Type, B: Type](
    addedFieldsExpr: Expr[Set[String]],
    removedFieldsExpr: Expr[Set[String]],
    renamedFromExpr: Expr[Set[String]],
    renamedToExpr: Expr[Set[String]]
  )(using q: Quotes): Expr[Unit] = {
    import q.reflect.*

    val sourceFields = MigrationHelperMacro.extractFieldPathsFromType(TypeRepr.of[A].dealias, "", Set.empty).toSet
    val targetFields = MigrationHelperMacro.extractFieldPathsFromType(TypeRepr.of[B].dealias, "", Set.empty).toSet

    val addedOpt       = extractLiteralSetOpt(addedFieldsExpr)
    val removedOpt     = extractLiteralSetOpt(removedFieldsExpr)
    val renamedFromOpt = extractLiteralSetOpt(renamedFromExpr)
    val renamedToOpt   = extractLiteralSetOpt(renamedToExpr)

    (addedOpt, removedOpt, renamedFromOpt, renamedToOpt) match {
      case (Some(added), Some(removed), Some(renamedFrom), Some(renamedTo)) =>
        val transformedSource = (sourceFields -- removed -- renamedFrom) ++ added ++ renamedTo
        val missing           = targetFields -- transformedSource
        val extra             = transformedSource -- targetFields

        if (missing.nonEmpty || extra.nonEmpty) {
          val aTypeName  = Type.show[A]
          val bTypeName  = Type.show[B]
          val missingMsg = if (missing.nonEmpty) s"Missing fields in target: ${missing.mkString(", ")}" else ""
          val extraMsg   = if (extra.nonEmpty) s"Extra fields not in target: ${extra.mkString(", ")}" else ""
          val msgs       = Seq(missingMsg, extraMsg).filter(_.nonEmpty).mkString("; ")
          report.errorAndAbort(
            s"Migration validation failed from $aTypeName to $bTypeName: $msgs"
          )
        }
        '{ () }
      case _ =>
        '{
          val source      = ${ Expr(sourceFields) }
          val target      = ${ Expr(targetFields) }
          val added       = $addedFieldsExpr
          val removed     = $removedFieldsExpr
          val renamedFrom = $renamedFromExpr
          val renamedTo   = $renamedToExpr

          val transformedSource = (source -- removed -- renamedFrom) ++ added ++ renamedTo
          val missing           = target -- transformedSource
          val extra             = transformedSource -- target

          if (missing.nonEmpty || extra.nonEmpty) {
            val missingMsg = if (missing.nonEmpty) s"Missing fields in target: ${missing.mkString(", ")}" else ""
            val extraMsg   = if (extra.nonEmpty) s"Extra fields not in target: ${extra.mkString(", ")}" else ""
            val msgs       = Seq(missingMsg, extraMsg).filter(_.nonEmpty).mkString("; ")
            throw new IllegalStateException(
              s"Migration validation failed: $msgs"
            )
          }
        }
    }
  }

  inline def validateCompileTime[A, B](
    inline added: Set[String],
    inline removed: Set[String],
    inline renamedFrom: Set[String],
    inline renamedTo: Set[String]
  ): Unit = ${ validateCompileTimeImpl[A, B]('added, 'removed, 'renamedFrom, 'renamedTo) }

  def validateCompileTimeImpl[A: Type, B: Type](
    addedExpr: Expr[Set[String]],
    removedExpr: Expr[Set[String]],
    renamedFromExpr: Expr[Set[String]],
    renamedToExpr: Expr[Set[String]]
  )(using q: Quotes): Expr[Unit] = {
    import q.reflect.*

    val sourceFields = MigrationHelperMacro.extractFieldPathsFromType(TypeRepr.of[A].dealias, "", Set.empty).toSet
    val targetFields = MigrationHelperMacro.extractFieldPathsFromType(TypeRepr.of[B].dealias, "", Set.empty).toSet

    val added       = extractLiteralSet(addedExpr)
    val removed     = extractLiteralSet(removedExpr)
    val renamedFrom = extractLiteralSet(renamedFromExpr)
    val renamedTo   = extractLiteralSet(renamedToExpr)

    val transformedSource = (sourceFields -- removed -- renamedFrom) ++ added ++ renamedTo

    val missing = targetFields -- transformedSource
    val extra   = transformedSource -- targetFields

    if (missing.nonEmpty || extra.nonEmpty) {
      val aTypeName  = Type.show[A]
      val bTypeName  = Type.show[B]
      val missingMsg = if (missing.nonEmpty) s"Missing fields in target: ${missing.mkString(", ")}" else ""
      val extraMsg   = if (extra.nonEmpty) s"Extra fields not in target: ${extra.mkString(", ")}" else ""
      val msgs       = Seq(missingMsg, extraMsg).filter(_.nonEmpty).mkString("; ")
      report.errorAndAbort(
        s"Migration validation failed from $aTypeName to $bTypeName: $msgs"
      )
    }

    '{ () }
  }

  private def extractFieldPath[S: Type, A: Type](selector: Expr[S => A])(using q: Quotes): List[String] = {
    import q.reflect.*

    def toPathBody(term: Term): Term = term match {
      case Inlined(_, _, inlinedBlock)                     => toPathBody(inlinedBlock)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               => report.errorAndAbort(s"Expected a lambda expression, got '${term.show}'")
    }

    def extractPath(term: Term): List[String] = term match {
      case Select(parent, fieldName) =>
        extractPath(parent) :+ fieldName
      case _: Ident =>
        List.empty
      case Apply(Apply(_, List(parent)), List(Literal(IntConstant(_)))) =>
        extractPath(parent)
      case Apply(TypeApply(Select(parent, "apply"), _), List(Literal(IntConstant(_)))) =>
        extractPath(parent)
      case _ =>
        report.errorAndAbort(
          s"Unsupported path expression. Expected simple field access like _.field.nested, got '${term.show}'."
        )
    }

    val pathBody  = toPathBody(selector.asTerm)
    val pathParts = extractPath(pathBody)

    if (pathParts.isEmpty) {
      report.errorAndAbort("Selector must access at least one field, e.g., _.name")
    }

    pathParts
  }

  private def extractLiteralSetOpt(expr: Expr[Set[String]])(using q: Quotes): Option[Set[String]] = {
    import q.reflect.*
    expr.asTerm match {
      case Apply(_, List(Typed(Repeated(elems, _), _))) =>
        val strings = elems.collect { case Literal(StringConstant(s)) => s }
        if (strings.length == elems.length) Some(strings.toSet) else None
      case Apply(TypeApply(Select(Ident("Set"), "apply"), _), List(Typed(Repeated(elems, _), _))) =>
        val strings = elems.collect { case Literal(StringConstant(s)) => s }
        if (strings.length == elems.length) Some(strings.toSet) else None
      case _ => None
    }
  }

  private def extractLiteralSet(expr: Expr[Set[String]])(using q: Quotes): Set[String] = {
    import q.reflect.*

    def extractFromTerm(term: Term): Option[Set[String]] =
      term match {
        case Apply(_, List(Typed(Repeated(elems, _), _))) =>
          val strings = elems.collect { case Literal(StringConstant(s)) => s }
          if (strings.length == elems.length) Some(strings.toSet) else None

        case Apply(TypeApply(Select(Ident("Set"), "apply"), _), List(Typed(Repeated(elems, _), _))) =>
          val strings = elems.collect { case Literal(StringConstant(s)) => s }
          if (strings.length == elems.length) Some(strings.toSet) else None

        case TypeApply(Select(Ident("Set"), "empty"), _) =>
          Some(Set.empty)

        case TypeApply(
              Select(Select(Select(Select(Ident("scala"), "collection"), "immutable"), "Set"), "empty"),
              _
            ) =>
          Some(Set.empty)

        case TypeApply(Select(Select(Ident("Predef"), "Set"), "empty"), _) =>
          Some(Set.empty)

        case TypeApply(Select(Select(Select(Ident("scala"), "Predef"), "Set"), "empty"), _) =>
          Some(Set.empty)

        case Typed(inner, _) =>
          extractFromTerm(inner)

        case Inlined(_, _, inner) =>
          extractFromTerm(inner)

        case Block(_, inner) =>
          extractFromTerm(inner)

        case _ => None
      }

    extractFromTerm(expr.asTerm).getOrElse {
      report.errorAndAbort(
        s"validateCompileTime requires literal Set values (e.g., Set(\"field1\", \"field2\") or Set.empty). " +
          s"Got: ${expr.asTerm.show}"
      )
    }
  }
}

extension [A, B, Handled, Provided](builder: MigrationBuilder[A, B, Handled, Provided]) {

  inline def add[T](inline target: B => T, defaultValue: DynamicValue): MigrationBuilder[A, B, Handled, Provided] =
    builder.addField(MigrationBuilderMacros.fieldPath(target), defaultValue)

  inline def drop[T](
    inline source: A => T,
    defaultForReverse: DynamicValue
  ): MigrationBuilder[A, B, Handled, Provided] =
    builder.dropField(MigrationBuilderMacros.fieldPath(source), defaultForReverse)

  inline def rename[T1, T2](inline from: A => T1, inline to: B => T2): MigrationBuilder[A, B, Handled, Provided] =
    builder.renameField(MigrationBuilderMacros.fieldPath(from), MigrationBuilderMacros.fieldPath(to))

  inline def transform[T](
    inline path: A => T,
    forward: DynamicValueTransform,
    backward: DynamicValueTransform
  ): MigrationBuilder[A, B, Handled, Provided] =
    builder.transformFieldValue(MigrationBuilderMacros.fieldPath(path), forward, backward)

  inline def optionalize[T](
    inline path: A => T,
    defaultForReverse: DynamicValue
  ): MigrationBuilder[A, B, Handled, Provided] =
    builder.optionalizeField(MigrationBuilderMacros.fieldPath(path), defaultForReverse)

  inline def mandate[T](
    inline path: A => Option[T],
    defaultForNone: DynamicValue
  ): MigrationBuilder[A, B, Handled, Provided] =
    builder.mandateField(MigrationBuilderMacros.fieldPath(path), defaultForNone)

  inline def changeType[T](
    inline path: A => T,
    forward: PrimitiveConversion,
    backward: PrimitiveConversion
  ): MigrationBuilder[A, B, Handled, Provided] =
    builder.changeFieldType(MigrationBuilderMacros.fieldPath(path), forward, backward)

  inline def addExpr[T](
    inline target: B => T,
    default: MigrationExpr[A, T]
  ): MigrationBuilder[A, B, Handled, Provided] =
    builder.addFieldExpr(MigrationBuilderMacros.toDynamicOptic(target), default)

  inline def dropExpr[T](
    inline source: A => T,
    defaultForReverse: MigrationExpr[B, T]
  ): MigrationBuilder[A, B, Handled, Provided] =
    builder.dropFieldExpr(MigrationBuilderMacros.toDynamicOptic(source), defaultForReverse)

  inline def optionalizeExpr[T](
    inline path: A => T,
    defaultForReverse: MigrationExpr[B, T]
  ): MigrationBuilder[A, B, Handled, Provided] =
    builder.optionalizeFieldExpr(MigrationBuilderMacros.toDynamicOptic(path), defaultForReverse)

  inline def mandateExpr[T](
    inline path: A => Option[T],
    defaultForNone: MigrationExpr[A, T]
  ): MigrationBuilder[A, B, Handled, Provided] =
    builder.mandateFieldExpr(MigrationBuilderMacros.toDynamicOptic(path), defaultForNone)

  inline def join[T](
    inline target: B => T,
    sourceNames: Vector[String],
    combiner: DynamicValueTransform,
    splitter: DynamicValueTransform
  ): MigrationBuilder[A, B, Handled, Provided] =
    builder.joinFields(MigrationBuilderMacros.fieldPath(target), sourceNames, combiner, splitter)

  inline def split[T](
    inline source: A => T,
    targetNames: Vector[String],
    splitter: DynamicValueTransform,
    combiner: DynamicValueTransform
  ): MigrationBuilder[A, B, Handled, Provided] =
    builder.splitField(MigrationBuilderMacros.fieldPath(source), targetNames, splitter, combiner)

  inline def transformElements[T](
    inline path: A => Seq[T]
  )(buildNested: MigrationStep.Record => MigrationStep.Record): MigrationBuilder[A, B, Handled, Provided] =
    builder.transformElements(MigrationBuilderMacros.fieldPath(path))(buildNested)

  inline def transformKeys[K, V](
    inline path: A => scala.collection.immutable.Map[K, V]
  )(buildNested: MigrationStep.Record => MigrationStep.Record): MigrationBuilder[A, B, Handled, Provided] =
    builder.transformKeys(MigrationBuilderMacros.fieldPath(path))(buildNested)

  inline def transformValues[K, V](
    inline path: A => scala.collection.immutable.Map[K, V]
  )(buildNested: MigrationStep.Record => MigrationStep.Record): MigrationBuilder[A, B, Handled, Provided] =
    builder.transformValues(MigrationBuilderMacros.fieldPath(path))(buildNested)

  inline def build: Migration[A, B] = {
    MigrationBuilderMacros.validateMigrationAtCompileTime[A, B](
      builder.addedFieldNames,
      builder.removedFieldNames,
      builder.renamedFromNames,
      builder.renamedToNames
    )
    builder.buildPartial
  }

  inline def buildChecked(
    inline added: Set[String],
    inline removed: Set[String],
    inline renamedFrom: Set[String] = Set.empty[String],
    inline renamedTo: Set[String] = Set.empty[String]
  ): Migration[A, B] = {
    MigrationBuilderMacros.validateCompileTime[A, B](added, removed, renamedFrom, renamedTo)
    builder.buildPartial
  }
}
