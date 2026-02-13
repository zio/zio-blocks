package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, Schema}
import scala.quoted.*

object TypedMigrationBuilderMacro {
  import TypeLevel.*

  extension [A, B, HandledTree <: FieldTree, ProvidedTree <: FieldTree](
    builder: MigrationBuilder[A, B, HandledTree, ProvidedTree]
  ) {

    transparent inline def addTyped[T](
      inline target: B => T,
      default: DynamicValue
    ): MigrationBuilder[A, B, HandledTree, ? <: FieldTree] =
      ${ TypedMigrationBuilderMacros.addTypedImpl[A, B, HandledTree, ProvidedTree, T]('builder, 'target, 'default) }

    transparent inline def dropTyped[T](
      inline source: A => T,
      defaultForReverse: DynamicValue
    ): MigrationBuilder[A, B, ? <: FieldTree, ProvidedTree] =
      ${
        TypedMigrationBuilderMacros.dropTypedImpl[A, B, HandledTree, ProvidedTree, T](
          'builder,
          'source,
          'defaultForReverse
        )
      }

    transparent inline def renameTyped[T1, T2](
      inline from: A => T1,
      inline to: B => T2
    ): MigrationBuilder[A, B, ? <: FieldTree, ? <: FieldTree] =
      ${ TypedMigrationBuilderMacros.renameTypedImpl[A, B, HandledTree, ProvidedTree, T1, T2]('builder, 'from, 'to) }

    inline def buildTyped: Migration[A, B] =
      ${ TypedMigrationBuilderMacros.buildTypedImpl[A, B, HandledTree, ProvidedTree]('builder) }
  }

  inline def from[A](using sourceSchema: Schema[A]): TypedFromBuilder[A] =
    new TypedFromBuilder[A](sourceSchema)

  final class TypedFromBuilder[A](sourceSchema: Schema[A]) {
    inline def to[B](using targetSchema: Schema[B]): MigrationBuilder[A, B, Empty, Empty] =
      MigrationBuilder[A, B, Empty, Empty](
        sourceSchema,
        targetSchema,
        MigrationStep.Record.empty,
        MigrationStep.Variant.empty
      )
  }
}

object TypedMigrationBuilderMacros {
  import TypeLevel.*

  def addTypedImpl[A: Type, B: Type, HandledTree <: FieldTree: Type, ProvidedTree <: FieldTree: Type, T: Type](
    builder: Expr[MigrationBuilder[A, B, HandledTree, ProvidedTree]],
    target: Expr[B => T],
    default: Expr[DynamicValue]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, HandledTree, ? <: FieldTree]] = {
    import q.reflect.*

    val fieldPath  = extractFieldPathFromSelector(target.asTerm)
    val pathString = fieldPath.mkString(".")

    if (fieldPath.isEmpty) {
      report.errorAndAbort("Selector must access at least one field, e.g., _.name")
    }

    val newProvidedType = buildTreeTypeForAdd(TypeRepr.of[ProvidedTree], fieldPath)

    newProvidedType.asType match {
      case '[newProv] =>
        '{
          $builder
            .addField(${ Expr(pathString) }, $default)
            .asInstanceOf[MigrationBuilder[A, B, HandledTree, newProv & FieldTree]]
        }
    }
  }

  def dropTypedImpl[A: Type, B: Type, HandledTree <: FieldTree: Type, ProvidedTree <: FieldTree: Type, T: Type](
    builder: Expr[MigrationBuilder[A, B, HandledTree, ProvidedTree]],
    source: Expr[A => T],
    defaultForReverse: Expr[DynamicValue]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ? <: FieldTree, ProvidedTree]] = {
    import q.reflect.*

    val fieldPath  = extractFieldPathFromSelector(source.asTerm)
    val pathString = fieldPath.mkString(".")

    if (fieldPath.isEmpty) {
      report.errorAndAbort("Selector must access at least one field, e.g., _.name")
    }

    val newHandledType = buildTreeTypeForDrop(TypeRepr.of[HandledTree], fieldPath)

    newHandledType.asType match {
      case '[newHandled] =>
        '{
          $builder
            .dropField(${ Expr(pathString) }, $defaultForReverse)
            .asInstanceOf[MigrationBuilder[A, B, newHandled & FieldTree, ProvidedTree]]
        }
    }
  }

  def renameTypedImpl[
    A: Type,
    B: Type,
    HandledTree <: FieldTree: Type,
    ProvidedTree <: FieldTree: Type,
    T1: Type,
    T2: Type
  ](
    builder: Expr[MigrationBuilder[A, B, HandledTree, ProvidedTree]],
    from: Expr[A => T1],
    to: Expr[B => T2]
  )(using q: Quotes): Expr[MigrationBuilder[A, B, ? <: FieldTree, ? <: FieldTree]] = {
    import q.reflect.*

    val fromPath = extractFieldPathFromSelector(from.asTerm)
    val toPath   = extractFieldPathFromSelector(to.asTerm)

    val fromPathString = fromPath.mkString(".")
    val toPathString   = toPath.mkString(".")

    if (fromPath.isEmpty || toPath.isEmpty) {
      report.errorAndAbort("Selectors must access at least one field, e.g., _.name")
    }

    val newHandledType  = buildTreeTypeForDrop(TypeRepr.of[HandledTree], fromPath)
    val newProvidedType = buildTreeTypeForAdd(TypeRepr.of[ProvidedTree], toPath)

    (newHandledType.asType, newProvidedType.asType) match {
      case ('[newHandled], '[newProv]) =>
        '{
          $builder
            .renameField(${ Expr(fromPathString) }, ${ Expr(toPathString) })
            .asInstanceOf[MigrationBuilder[A, B, newHandled & FieldTree, newProv & FieldTree]]
        }
    }
  }

  def buildTypedImpl[A: Type, B: Type, HandledTree <: FieldTree: Type, ProvidedTree <: FieldTree: Type](
    builder: Expr[MigrationBuilder[A, B, HandledTree, ProvidedTree]]
  )(using q: Quotes): Expr[Migration[A, B]] = {
    import q.reflect.*

    val requiredHandledTree = buildRequiredTreeFromSchemaDiff(
      TypeRepr.of[A],
      TypeRepr.of[B],
      isForHandled = true
    )
    val requiredProvidedTree = buildRequiredTreeFromSchemaDiff(
      TypeRepr.of[A],
      TypeRepr.of[B],
      isForHandled = false
    )

    val handledTreePaths  = extractPathsFromTreeType(TypeRepr.of[HandledTree])
    val providedTreePaths = extractPathsFromTreeType(TypeRepr.of[ProvidedTree])

    val unhandled  = requiredHandledTree.diff(handledTreePaths)
    val unprovided = requiredProvidedTree.diff(providedTreePaths)

    if (unhandled.nonEmpty || unprovided.nonEmpty) {
      val sourceTypeName = Type.show[A]
      val targetTypeName = Type.show[B]

      val sb = new StringBuilder
      sb.append(s"\n\nMigration validation failed for $sourceTypeName => $targetTypeName:\n")

      if (unhandled.nonEmpty) {
        sb.append("\n  Unhandled fields from source (need .dropTyped or .renameTyped):\n")
        formatTreePaths(unhandled).foreach(p => sb.append(s"    - $p\n"))
      }

      if (unprovided.nonEmpty) {
        sb.append("\n  Unprovided fields for target (need .addTyped or .renameTyped):\n")
        formatTreePaths(unprovided).foreach(p => sb.append(s"    - $p\n"))
      }

      sb.append("\n  Tracked handled: " + formatTreePathsInline(handledTreePaths))
      sb.append("\n  Tracked provided: " + formatTreePathsInline(providedTreePaths))

      report.errorAndAbort(sb.toString)
    }

    '{ $builder.buildPartial }
  }

  private def buildTreeTypeForAdd(using
    q: Quotes
  )(
    currentTree: q.reflect.TypeRepr,
    path: List[String]
  ): q.reflect.TypeRepr = {
    import q.reflect.*

    path match {
      case Nil              => currentTree
      case fieldName :: Nil =>
        val nameType = ConstantType(StringConstant(fieldName))
        TypeRepr
          .of[Branch]
          .appliedTo(
            List(
              nameType,
              TypeRepr.of[OpAdd],
              TypeRepr.of[Empty],
              currentTree
            )
          )
      case fieldName :: rest =>
        val nameType  = ConstantType(StringConstant(fieldName))
        val childTree = buildTreeTypeForAdd(TypeRepr.of[Empty], rest)
        TypeRepr
          .of[Branch]
          .appliedTo(
            List(
              nameType,
              TypeRepr.of[OpNested],
              childTree,
              currentTree
            )
          )
    }
  }

  private def buildTreeTypeForDrop(using
    q: Quotes
  )(
    currentTree: q.reflect.TypeRepr,
    path: List[String]
  ): q.reflect.TypeRepr = {
    import q.reflect.*

    path match {
      case Nil              => currentTree
      case fieldName :: Nil =>
        val nameType = ConstantType(StringConstant(fieldName))
        TypeRepr
          .of[Branch]
          .appliedTo(
            List(
              nameType,
              TypeRepr.of[OpDrop],
              TypeRepr.of[Empty],
              currentTree
            )
          )
      case fieldName :: rest =>
        val nameType  = ConstantType(StringConstant(fieldName))
        val childTree = buildTreeTypeForDrop(TypeRepr.of[Empty], rest)
        TypeRepr
          .of[Branch]
          .appliedTo(
            List(
              nameType,
              TypeRepr.of[OpNested],
              childTree,
              currentTree
            )
          )
    }
  }

  private case class TreePath(segments: List[String], op: String)

  private def extractPathsFromTreeType(using q: Quotes)(tpe: q.reflect.TypeRepr): Set[TreePath] = {
    import q.reflect.*

    def extract(t: TypeRepr, prefix: List[String]): Set[TreePath] = {
      val dealiased = t.dealias
      dealiased match {
        case AppliedType(tycon, List(nameType, opType, childrenType, siblingsType))
            if tycon.typeSymbol.fullName.contains("Branch") =>
          val name = nameType.dealias match {
            case ConstantType(StringConstant(s)) => s
            case _                               => "?"
          }
          val op          = opType.dealias.typeSymbol.name
          val currentPath = prefix :+ name

          val thisPath     = if (op == "OpNested") Set.empty[TreePath] else Set(TreePath(currentPath, op))
          val childPaths   = extract(childrenType, currentPath)
          val siblingPaths = extract(siblingsType, prefix)

          thisPath ++ childPaths ++ siblingPaths

        case _ if dealiased.typeSymbol.fullName.contains("Empty") =>
          Set.empty

        case _ =>
          Set.empty
      }
    }

    extract(tpe, Nil)
  }

  private def buildRequiredTreeFromSchemaDiff(using
    q: Quotes
  )(
    sourceType: q.reflect.TypeRepr,
    targetType: q.reflect.TypeRepr,
    isForHandled: Boolean
  ): Set[TreePath] = {
    val sourcePaths = MigrationHelperMacro.extractFieldPathsFromType(sourceType.dealias, "", Set.empty)
    val targetPaths = MigrationHelperMacro.extractFieldPathsFromType(targetType.dealias, "", Set.empty)

    val sourceCases = extractCaseNamesForValidation(sourceType)
    val targetCases = extractCaseNamesForValidation(targetType)

    if (isForHandled) {
      val droppedFields = sourcePaths.diff(targetPaths)
      val droppedCases  = sourceCases.diff(targetCases)
      droppedFields.map(p => TreePath(p.split('.').toList, "OpDrop")).toSet ++
        droppedCases.map(c => TreePath(List(c), "OpDrop")).toSet
    } else {
      val addedFields = targetPaths.diff(sourcePaths)
      val addedCases  = targetCases.diff(sourceCases)
      addedFields.map(p => TreePath(p.split('.').toList, "OpAdd")).toSet ++
        addedCases.map(c => TreePath(List(c), "OpAdd")).toSet
    }
  }

  private def formatTreePaths(paths: Set[TreePath]): List[String] =
    paths.toList
      .sortBy(_.segments.mkString("."))
      .map(p => p.segments.mkString("."))

  private def formatTreePathsInline(paths: Set[TreePath]): String =
    if (paths.isEmpty) "(none)"
    else paths.toList.sortBy(_.segments.mkString(".")).map(_.segments.mkString(".")).mkString(", ")

  private def extractFieldPathFromSelector(using q: Quotes)(term: q.reflect.Term): List[String] = {
    import q.reflect.*

    def toPathBody(t: Term): Term = t match {
      case Inlined(_, _, inlinedBlock)                     => toPathBody(inlinedBlock)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case Block(Nil, expr)                                => expr
      case _                                               => report.errorAndAbort(s"Expected a lambda expression, got '${t.show}'")
    }

    def extractPath(t: Term): List[String] = t match {
      case Select(parent, fieldName) =>
        extractPath(parent) :+ fieldName
      case _: Ident =>
        List.empty
      case Typed(expr, _) =>
        extractPath(expr)
      case Apply(Select(_, "apply"), _) =>
        List.empty
      case _ =>
        report.errorAndAbort(
          s"Unsupported path expression. Expected simple field access like _.field.nested, got '${t.show}'."
        )
    }

    val pathBody = toPathBody(term)
    extractPath(pathBody)
  }

  private def extractCaseNamesForValidation(using q: Quotes)(tpe: q.reflect.TypeRepr): List[String] = {
    val dealiased = tpe.dealias

    if (MigrationHelperMacro.isSealedTraitOrEnum(dealiased)) {
      val symbol   = dealiased.typeSymbol
      val children = symbol.children
      children.map { child =>
        s"case:${child.name}"
      }.sorted
    } else {
      Nil
    }
  }
}
