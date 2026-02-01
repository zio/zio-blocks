package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue}
import scala.quoted.*

object MigrationBuilderMacros {

  inline def fieldPath[S, A](inline selector: S => A): String =
    ${ fieldPathImpl[S, A]('selector) }

  def fieldPathImpl[S: Type, A: Type](selector: Expr[S => A])(using q: Quotes): Expr[String] = {
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

    val path = pathParts.mkString(".")
    Expr(path)
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

    val fieldExprs = pathParts.map(name => '{ DynamicOptic.Node.Field(${ Expr(name) }) })
    val nodesExpr  = Expr.ofSeq(fieldExprs)
    '{ DynamicOptic(IndexedSeq($nodesExpr*)) }
  }

  inline def extractFieldNames[T]: Set[String] =
    ${ extractFieldNamesImpl[T] }

  def extractFieldNamesImpl[T: Type](using q: Quotes): Expr[Set[String]] = {
    import q.reflect.*

    val tpe = TypeRepr.of[T].dealias

    def getFieldNames(tpe: TypeRepr): List[String] =
      tpe.classSymbol match {
        case Some(sym) =>
          sym.primaryConstructor.paramSymss.flatten
            .filter(!_.isTypeParam)
            .map(_.name)
        case None =>
          tpe match {
            case Refinement(parent, name, _) =>
              name :: getFieldNames(parent)
            case _ =>
              Nil
          }
      }

    val fieldNames = getFieldNames(tpe)
    val fieldExprs = fieldNames.map(Expr(_))
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

  inline def validateTypesCompatible[A, B]: Unit =
    ${ validateTypesCompatibleImpl[A, B] }

  def validateTypesCompatibleImpl[A: Type, B: Type](using q: Quotes): Expr[Unit] = {
    import q.reflect.*

    def getFieldNames(tpe: TypeRepr): List[String] =
      tpe.classSymbol match {
        case Some(sym) =>
          sym.primaryConstructor.paramSymss.flatten
            .filter(!_.isTypeParam)
            .map(_.name)
        case None =>
          tpe match {
            case Refinement(parent, name, _) =>
              name :: getFieldNames(parent)
            case _ =>
              Nil
          }
      }

    val sourceFields = getFieldNames(TypeRepr.of[A].dealias).toSet
    val targetFields = getFieldNames(TypeRepr.of[B].dealias).toSet

    if (sourceFields == targetFields) {
      '{ () }
    } else {
      val added   = targetFields -- sourceFields
      val removed = sourceFields -- targetFields

      if (added.nonEmpty || removed.nonEmpty) {
        val aTypeName  = Type.show[A]
        val bTypeName  = Type.show[B]
        val addedMsg   = if (added.nonEmpty) s"Fields added in target: ${added.mkString(", ")}" else ""
        val removedMsg = if (removed.nonEmpty) s"Fields removed from source: ${removed.mkString(", ")}" else ""
        val msg        = s"Types $aTypeName and $bTypeName have different structures. $addedMsg $removedMsg".trim
        report.info(msg)
      }
      '{ () }
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

    def getFieldNames(tpe: TypeRepr): Set[String] =
      tpe.classSymbol match {
        case Some(sym) =>
          sym.primaryConstructor.paramSymss.flatten
            .filter(!_.isTypeParam)
            .map(_.name)
            .toSet
        case None =>
          tpe match {
            case Refinement(parent, name, _) =>
              Set(name) ++ getFieldNames(parent)
            case _ =>
              Set.empty
          }
      }

    def extractLiteralSet(expr: Expr[Set[String]]): Option[Set[String]] =
      expr.asTerm match {
        case Apply(_, List(Typed(Repeated(elems, _), _))) =>
          val strings = elems.collect { case Literal(StringConstant(s)) =>
            s
          }
          if (strings.length == elems.length) Some(strings.toSet) else None
        case Apply(TypeApply(Select(Ident("Set"), "apply"), _), List(Typed(Repeated(elems, _), _))) =>
          val strings = elems.collect { case Literal(StringConstant(s)) =>
            s
          }
          if (strings.length == elems.length) Some(strings.toSet) else None
        case _ => None
      }

    val sourceFields = getFieldNames(TypeRepr.of[A].dealias)
    val targetFields = getFieldNames(TypeRepr.of[B].dealias)

    val addedOpt       = extractLiteralSet(addedFieldsExpr)
    val removedOpt     = extractLiteralSet(removedFieldsExpr)
    val renamedFromOpt = extractLiteralSet(renamedFromExpr)
    val renamedToOpt   = extractLiteralSet(renamedToExpr)

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

    def getFieldNames(tpe: TypeRepr): Set[String] =
      tpe.classSymbol match {
        case Some(sym) =>
          sym.primaryConstructor.paramSymss.flatten
            .filter(!_.isTypeParam)
            .map(_.name)
            .toSet
        case None =>
          tpe match {
            case Refinement(parent, name, _) =>
              Set(name) ++ getFieldNames(parent)
            case _ =>
              Set.empty
          }
      }

    def extractLiteralSet(expr: Expr[Set[String]]): Set[String] = {
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

    val sourceFields = getFieldNames(TypeRepr.of[A].dealias)
    val targetFields = getFieldNames(TypeRepr.of[B].dealias)

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

  inline def buildChecked[A, B](
    builder: MigrationBuilder[A, B],
    inline added: Set[String],
    inline removed: Set[String],
    inline renamedFrom: Set[String],
    inline renamedTo: Set[String]
  ): Migration[A, B] = {
    validateCompileTime[A, B](added, removed, renamedFrom, renamedTo)
    builder.build
  }
}

extension [A, B](builder: MigrationBuilder[A, B]) {

  inline def add[T](inline target: B => T, defaultValue: DynamicValue): MigrationBuilder[A, B] =
    builder.addField(MigrationBuilderMacros.fieldPath(target), defaultValue)

  inline def drop[T](inline source: A => T, defaultForReverse: DynamicValue): MigrationBuilder[A, B] =
    builder.dropField(MigrationBuilderMacros.fieldPath(source), defaultForReverse)

  inline def rename[T1, T2](inline from: A => T1, inline to: B => T2): MigrationBuilder[A, B] =
    builder.renameField(MigrationBuilderMacros.fieldPath(from), MigrationBuilderMacros.fieldPath(to))

  inline def transform[T](
    inline path: A => T,
    forward: DynamicValueTransform,
    backward: DynamicValueTransform
  ): MigrationBuilder[A, B] =
    builder.transformField(MigrationBuilderMacros.fieldPath(path), forward, backward)

  inline def optionalize[T](inline path: A => T, defaultForReverse: DynamicValue): MigrationBuilder[A, B] =
    builder.optionalizeField(MigrationBuilderMacros.fieldPath(path), defaultForReverse)

  inline def mandate[T](inline path: A => Option[T], defaultForNone: DynamicValue): MigrationBuilder[A, B] =
    builder.mandateField(MigrationBuilderMacros.fieldPath(path), defaultForNone)

  inline def changeType[T](
    inline path: A => T,
    forward: PrimitiveConversion,
    backward: PrimitiveConversion
  ): MigrationBuilder[A, B] =
    builder.changeFieldType(MigrationBuilderMacros.fieldPath(path), forward, backward)

  inline def addExpr[T](inline target: B => T, default: MigrationExpr[A, T]): MigrationBuilder[A, B] =
    builder.addFieldExpr(MigrationBuilderMacros.toDynamicOptic(target), default)

  inline def dropExpr[T](inline source: A => T, defaultForReverse: MigrationExpr[B, T]): MigrationBuilder[A, B] =
    builder.dropFieldExpr(MigrationBuilderMacros.toDynamicOptic(source), defaultForReverse)

  inline def optionalizeExpr[T](inline path: A => T, defaultForReverse: MigrationExpr[B, T]): MigrationBuilder[A, B] =
    builder.optionalizeFieldExpr(MigrationBuilderMacros.toDynamicOptic(path), defaultForReverse)

  inline def mandateExpr[T](inline path: A => Option[T], defaultForNone: MigrationExpr[A, T]): MigrationBuilder[A, B] =
    builder.mandateFieldExpr(MigrationBuilderMacros.toDynamicOptic(path), defaultForNone)

  inline def addCaseExpr[T](caseName: String, default: MigrationExpr[A, T]): MigrationBuilder[A, B] =
    builder.addCaseExpr(caseName, default)

  inline def dropCaseExpr[T](caseName: String, defaultForReverse: MigrationExpr[B, T]): MigrationBuilder[A, B] =
    builder.dropCaseExpr(caseName, defaultForReverse)

  inline def join[T](
    inline target: B => T,
    sourceNames: Vector[String],
    combiner: DynamicValueTransform,
    splitter: DynamicValueTransform
  ): MigrationBuilder[A, B] =
    builder.joinFields(MigrationBuilderMacros.fieldPath(target), sourceNames, combiner, splitter)

  inline def split[T](
    inline source: A => T,
    targetNames: Vector[String],
    splitter: DynamicValueTransform,
    combiner: DynamicValueTransform
  ): MigrationBuilder[A, B] =
    builder.splitField(MigrationBuilderMacros.fieldPath(source), targetNames, splitter, combiner)

  inline def buildValidated: Migration[A, B] = {
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
