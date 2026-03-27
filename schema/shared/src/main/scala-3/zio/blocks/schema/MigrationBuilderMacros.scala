package zio.blocks.schema

import scala.quoted._

trait MigrationBuilderMacros[A, B]

extension [A, B](inline self: MigrationBuilder[A, B]) {
  inline def addField[T](inline targetPath: B => T, default: SchemaExpr[_, _]): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacroImpls.addFieldImpl[A, B, T]('self, 'targetPath, 'default) }

  inline def dropField[T](inline oldPath: A => T, defaultForReverse: SchemaExpr[_, _]): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacroImpls.dropFieldImpl[A, B, T]('self, 'oldPath, 'defaultForReverse) }

  inline def renameField[T](inline oldPath: A => T, inline newPath: B => T): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacroImpls.renameFieldImpl[A, B, T]('self, 'oldPath, 'newPath) }

  inline def transformField[T](inline path: B => T, transform: SchemaExpr[_, _]): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacroImpls.transformFieldImpl[A, B, T]('self, 'path, 'transform) }

  inline def mandateField[T](inline path: B => T, default: SchemaExpr[_, _]): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacroImpls.mandateFieldImpl[A, B, T]('self, 'path, 'default) }

  inline def optionalizeField[T](inline path: B => T, defaultForReverse: SchemaExpr[_, _]): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacroImpls.optionalizeFieldImpl[A, B, T]('self, 'path, 'defaultForReverse) }

  inline def changeFieldType[T](inline path: B => T, converter: SchemaExpr[_, _]): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacroImpls.changeFieldTypeImpl[A, B, T]('self, 'path, 'converter) }

  inline def renameCase[SumA, SumB](from: String, to: String): MigrationBuilder[A, B] =
    self.renameCaseCore(from, to)

  inline def transformCase[SumA, CaseA, SumB, CaseB](
    caseMigration: MigrationBuilder[CaseA, CaseB] => MigrationBuilder[CaseA, CaseB]
  )(implicit schemaCaseA: Schema[CaseA], schemaCaseB: Schema[CaseB]): MigrationBuilder[A, B] =
    ${
      MigrationBuilderMacroImpls.transformCaseImpl[A, B, SumA, CaseA, SumB, CaseB](
        'self,
        'caseMigration,
        'schemaCaseA,
        'schemaCaseB
      )
    }

  inline def transformElements[T](inline at: A => Iterable[T], transform: SchemaExpr[_, _]): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacroImpls.transformElementsImpl[A, B, T]('self, 'at, 'transform) }

  inline def transformKeys[K, V](inline at: A => Map[K, V], transform: SchemaExpr[_, _]): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacroImpls.transformKeysImpl[A, B, K, V]('self, 'at, 'transform) }

  inline def transformValues[K, V](inline at: A => Map[K, V], transform: SchemaExpr[_, _]): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacroImpls.transformValuesImpl[A, B, K, V]('self, 'at, 'transform) }

  inline def build: Migration[A, B] =
    ${ MigrationBuilderMacroImpls.buildImpl[A, B]('self) }
}

object MigrationBuilderMacroImpls {
  def extractOpticNodesForTerm(using q: Quotes)(term: q.reflect.Term): List[q.reflect.Term] = {
    import q.reflect.*
    def toPathBody(t: Term): Term = t match {
      case Inlined(_, _, inlinedBlock)                     => toPathBody(inlinedBlock)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               => report.errorAndAbort(s"Expected a lambda expression, got ${term.show}", term.asExpr)
    }

    def collectNodes(t: Term): List[Term] = t match {
      case Select(parent, _) =>
        collectNodes(parent) :+ t
      case Apply(TypeApply(Ident("when"), _), List(parent)) =>
        collectNodes(parent) :+ t
      case Ident(_)                             => Nil
      case Typed(expr, _)                       => collectNodes(expr)
      case Inlined(_, _, expr)                  => collectNodes(expr)
      case Block(_, expr)                       => collectNodes(expr)
      case Apply(Select(parent, "apply"), args) => collectNodes(parent)
      case _                                    => report.errorAndAbort(s"Unsupported path element: ${t.show}", t.asExpr)
    }

    collectNodes(toPathBody(term))
  }

  def extractOptic(path: Expr[Any])(using q: Quotes): Expr[DynamicOptic] = {
    import q.reflect.*
    val nodes     = extractOpticNodesForTerm(path.asTerm)
    val nodeExprs = nodes.map {
      case Select(_, fieldName)                          => '{ DynamicOptic.Node.Field(${ Expr(fieldName) }) }
      case Apply(TypeApply(Ident("when"), List(tpt)), _) =>
        val caseName = tpt.tpe.typeSymbol.name
        '{ DynamicOptic.Node.Case(${ Expr(caseName) }) }
      case _ => report.errorAndAbort("unsupported element extractor")
    }
    '{ DynamicOptic(Vector(${ Varargs(nodeExprs) }*)) }
  }

  def extractParentOptic(path: Expr[Any])(using q: Quotes): Expr[DynamicOptic] = {
    import q.reflect.*
    val nodes = extractOpticNodesForTerm(path.asTerm)
    if (nodes.isEmpty) report.errorAndAbort("Path empty")
    val parentNodes = nodes.init
    val nodeExprs   = parentNodes.map {
      case Select(_, fieldName) => '{ DynamicOptic.Node.Field(${ Expr(fieldName) }) }
      case _                    => report.errorAndAbort("unsupported parent element extractor")
    }
    '{ DynamicOptic(Vector(${ Varargs(nodeExprs) }*)) }
  }

  def extractLastNodeName(path: Expr[Any])(using q: Quotes): Expr[String] = {
    import q.reflect.*
    val nodes = extractOpticNodesForTerm(path.asTerm)
    if (nodes.isEmpty) report.errorAndAbort("Path empty")
    nodes.last match {
      case Select(_, fname) => Expr(fname)
      case _                => report.errorAndAbort("Expected a field select")
    }
  }

  def addFieldImpl[A: Type, B: Type, T: Type](
    self: Expr[MigrationBuilder[A, B]],
    targetPath: Expr[B => T],
    default: Expr[SchemaExpr[_, _]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = extractOptic(targetPath)
    '{ $self.addFieldCore($optic, $default) }
  }

  def dropFieldImpl[A: Type, B: Type, T: Type](
    self: Expr[MigrationBuilder[A, B]],
    oldPath: Expr[A => T],
    defaultForReverse: Expr[SchemaExpr[_, _]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = extractOptic(oldPath)
    '{ $self.dropFieldCore($optic, $defaultForReverse) }
  }

  def renameFieldImpl[A: Type, B: Type, T: Type](
    self: Expr[MigrationBuilder[A, B]],
    oldPath: Expr[A => T],
    newPath: Expr[B => T]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val parentOptic = extractParentOptic(oldPath)
    val oldName     = extractLastNodeName(oldPath)
    val newName     = extractLastNodeName(newPath)
    '{ $self.renameFieldCore($parentOptic, $oldName, $newName) }
  }

  def transformFieldImpl[A: Type, B: Type, T: Type](
    self: Expr[MigrationBuilder[A, B]],
    path: Expr[B => T],
    transform: Expr[SchemaExpr[_, _]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = extractOptic(path)
    '{ $self.transformFieldCore($optic, $transform) }
  }

  def mandateFieldImpl[A: Type, B: Type, T: Type](
    self: Expr[MigrationBuilder[A, B]],
    path: Expr[B => T],
    default: Expr[SchemaExpr[_, _]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = extractOptic(path)
    '{ $self.mandateFieldCore($optic, $default) }
  }

  def optionalizeFieldImpl[A: Type, B: Type, T: Type](
    self: Expr[MigrationBuilder[A, B]],
    path: Expr[B => T],
    defaultForReverse: Expr[SchemaExpr[_, _]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = extractOptic(path)
    '{ $self.optionalizeFieldCore($optic, $defaultForReverse) }
  }

  def changeFieldTypeImpl[A: Type, B: Type, T: Type](
    self: Expr[MigrationBuilder[A, B]],
    path: Expr[B => T],
    converter: Expr[SchemaExpr[_, _]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = extractOptic(path)
    '{ $self.changeFieldTypeCore($optic, $converter) }
  }

  def transformCaseImpl[A: Type, B: Type, SumA: Type, CaseA: Type, SumB: Type, CaseB: Type](
    self: Expr[MigrationBuilder[A, B]],
    caseMigration: Expr[MigrationBuilder[CaseA, CaseB] => MigrationBuilder[CaseA, CaseB]],
    schemaCaseA: Expr[Schema[CaseA]],
    schemaCaseB: Expr[Schema[CaseB]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    import q.reflect.*
    val caseName = TypeRepr.of[CaseA].typeSymbol.name
    '{
      val builtMigration = $caseMigration(MigrationBuilder.make[CaseA, CaseB](using $schemaCaseA, $schemaCaseB))
      $self.transformCaseCore(${ Expr(caseName) }, builtMigration)
    }
  }

  def transformElementsImpl[A: Type, B: Type, T: Type](
    self: Expr[MigrationBuilder[A, B]],
    at: Expr[A => Iterable[T]],
    transform: Expr[SchemaExpr[_, _]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = extractOptic(at)
    '{ $self.transformElementsCore($optic, $transform) }
  }

  def transformKeysImpl[A: Type, B: Type, K: Type, V: Type](
    self: Expr[MigrationBuilder[A, B]],
    at: Expr[A => Map[K, V]],
    transform: Expr[SchemaExpr[_, _]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = extractOptic(at)
    '{ $self.transformKeysCore($optic, $transform) }
  }

  def transformValuesImpl[A: Type, B: Type, K: Type, V: Type](
    self: Expr[MigrationBuilder[A, B]],
    at: Expr[A => Map[K, V]],
    transform: Expr[SchemaExpr[_, _]]
  )(using q: Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = extractOptic(at)
    '{ $self.transformValuesCore($optic, $transform) }
  }

  def buildImpl[A: Type, B: Type](self: Expr[MigrationBuilder[A, B]])(using q: Quotes): Expr[Migration[A, B]] = {
    import q.reflect.*

    def getFields(tr: TypeRepr): Set[String] = {
      val sym = tr.typeSymbol
      val caseF = scala.util.Try(if (sym.isClassDef) sym.caseFields.map(_.name.trim).toSet else Set.empty[String]).getOrElse(Set.empty[String])
      def refinements(t: TypeRepr): Set[String] = t.dealias match {
        case Refinement(parent, name, _) => refinements(parent) + name.trim
        case AndType(left, right) => refinements(left) ++ refinements(right)
        case _ => Set.empty
      }
      caseF ++ refinements(tr)
    }

    val targetFields = getFields(TypeRepr.of[B])
    val sourceFields = getFields(TypeRepr.of[A])

    val fieldMethods = Set("addField", "transformField", "mandateField", "changeFieldType", "optionalizeField")

    def extractFieldFromFirstArg(args: List[Term]): Option[String] =
      if (args.nonEmpty) {
        val nodes = extractOpticNodesForTerm(args(0))
        nodes.lastOption match {
          case Some(Select(_, fname)) => Some(fname)
          case _                      => None
        }
      } else None

    def extractFieldFromSecondArg(args: List[Term]): Option[String] =
      if (args.size > 1) {
        val nodes = extractOpticNodesForTerm(args(1))
        nodes.lastOption match {
          case Some(Select(_, fname)) => Some(fname)
          case _                      => None
        }
      } else None

    def extractUserFields(term: Term): Set[String] = term match {
      case Inlined(_, _, body) => extractUserFields(body)
      case Block(_, expr)      => extractUserFields(expr)

      // Extension method pattern: Apply(Apply(TypeApply(Ident(methodName), _), List(qualifier)), args)
      case Apply(Apply(TypeApply(Ident(methodName), _), List(qualifier)), args) =>
        val fromParent = extractUserFields(qualifier)
        if (fieldMethods.contains(methodName))
          extractFieldFromFirstArg(args).fold(fromParent)(fromParent + _)
        else if (methodName == "renameField")
          extractFieldFromSecondArg(args).orElse(extractFieldFromFirstArg(args)).fold(fromParent)(fromParent + _)
        else fromParent

      // Extension method without type params: Apply(Apply(Ident(methodName), List(qualifier)), args)
      case Apply(Apply(Ident(methodName), List(qualifier)), args) =>
        val fromParent = extractUserFields(qualifier)
        if (fieldMethods.contains(methodName))
          extractFieldFromFirstArg(args).fold(fromParent)(fromParent + _)
        else if (methodName == "renameField")
          extractFieldFromSecondArg(args).orElse(extractFieldFromFirstArg(args)).fold(fromParent)(fromParent + _)
        else fromParent

      // Regular method call: Apply(TypeApply(Select(qualifier, methodName), _), args)
      case Apply(TypeApply(Select(qualifier, methodName), _), args) =>
        val fromParent = extractUserFields(qualifier)
        if (fieldMethods.contains(methodName))
          extractFieldFromFirstArg(args).fold(fromParent)(fromParent + _)
        else if (methodName == "renameField")
          extractFieldFromSecondArg(args).orElse(extractFieldFromFirstArg(args)).fold(fromParent)(fromParent + _)
        else fromParent

      // Regular method call without type params: Apply(Select(qualifier, methodName), args)
      case Apply(Select(qualifier, methodName), args) =>
        val fromParent = extractUserFields(qualifier)
        if (fieldMethods.contains(methodName))
          extractFieldFromFirstArg(args).fold(fromParent)(fromParent + _)
        else if (methodName == "renameField")
          extractFieldFromSecondArg(args).orElse(extractFieldFromFirstArg(args)).fold(fromParent)(fromParent + _)
        else fromParent

      case TypeApply(inner, _) => extractUserFields(inner)
      case Select(qualifier, _) => extractUserFields(qualifier)
      case _ => Set.empty
    }

    val handledFields   = extractUserFields(self.asTerm)
    val uncoveredFields = targetFields -- sourceFields -- handledFields

    if (uncoveredFields.nonEmpty) {
      report.errorAndAbort(
        s"Field(s) [${uncoveredFields.mkString(", ")}] in target schema are missing from source and have no default value provided."
      )
    }

    '{ $self.buildPartial }
  }
}
