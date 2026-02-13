package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, Schema}

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

object TypedMigrationBuilderMacro {
  import TypeLevel._

  def from[A](implicit sourceSchema: Schema[A]): TypedFromBuilder[A] =
    new TypedFromBuilder[A](sourceSchema)

  final class TypedFromBuilder[A](sourceSchema: Schema[A]) {

    def to[B](implicit targetSchema: Schema[B]): MigrationBuilder[A, B, Empty, Empty] =
      MigrationBuilder[A, B, Empty, Empty](
        sourceSchema,
        targetSchema,
        MigrationStep.Record.empty,
        MigrationStep.Variant.empty
      )
  }

  implicit class TypedMigrationBuilderOps[A, B, HandledTree <: FieldTree, ProvidedTree <: FieldTree](
    val builder: MigrationBuilder[A, B, HandledTree, ProvidedTree]
  ) extends AnyVal {

    // format: off
    def addTyped[T](target: B => T, default: DynamicValue): MigrationBuilder[A, B, HandledTree, _ <: FieldTree] = macro TypedMigrationBuilderMacros.addTypedImpl[A, B, HandledTree, ProvidedTree, T]

    def dropTyped[T](source: A => T, defaultForReverse: DynamicValue): MigrationBuilder[A, B, _ <: FieldTree, ProvidedTree] = macro TypedMigrationBuilderMacros.dropTypedImpl[A, B, HandledTree, ProvidedTree, T]

    def renameTyped[T1, T2](from: A => T1, to: B => T2): MigrationBuilder[A, B, _ <: FieldTree, _ <: FieldTree] = macro TypedMigrationBuilderMacros.renameTypedImpl[A, B, HandledTree, ProvidedTree, T1, T2]
    // format: on

    def buildTyped: Migration[A, B] = macro TypedMigrationBuilderMacros.buildTypedImpl[A, B, HandledTree, ProvidedTree]
  }
}

private[migration] object TypedMigrationBuilderMacros {
  import TypeLevel._

  def addTypedImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, HandledTree: c.WeakTypeTag, ProvidedTree: c.WeakTypeTag, T](
    c: whitebox.Context
  )(
    target: c.Expr[B => T],
    default: c.Expr[DynamicValue]
  )(implicit ttag: c.WeakTypeTag[T]): c.Expr[MigrationBuilder[A, B, HandledTree, _ <: FieldTree]] = {
    val _ = ttag
    import c.universe._

    val typeBuilder = new TypedMigrationBuilderMacroHelpers[c.type](c)
    val pathParts   = typeBuilder.extractFieldPathParts(target.tree)
    val pathString  = pathParts.mkString(".")

    val newProvidedType = typeBuilder.buildTreeTypeForAdd(weakTypeOf[ProvidedTree], pathParts)
    val builderRef      = c.prefix.tree

    val result = q"""
      $builderRef.builder.addField($pathString, $default)
        .asInstanceOf[_root_.zio.blocks.schema.migration.MigrationBuilder[
          ${weakTypeOf[A]},
          ${weakTypeOf[B]},
          ${weakTypeOf[HandledTree]},
          $newProvidedType
        ]]
    """

    c.Expr[MigrationBuilder[A, B, HandledTree, _ <: FieldTree]](result)
  }

  def dropTypedImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, HandledTree: c.WeakTypeTag, ProvidedTree: c.WeakTypeTag, T](
    c: whitebox.Context
  )(
    source: c.Expr[A => T],
    defaultForReverse: c.Expr[DynamicValue]
  )(implicit ttag: c.WeakTypeTag[T]): c.Expr[MigrationBuilder[A, B, _ <: FieldTree, ProvidedTree]] = {
    val _ = ttag
    import c.universe._

    val typeBuilder = new TypedMigrationBuilderMacroHelpers[c.type](c)
    val pathParts   = typeBuilder.extractFieldPathParts(source.tree)
    val pathString  = pathParts.mkString(".")

    val newHandledType = typeBuilder.buildTreeTypeForDrop(weakTypeOf[HandledTree], pathParts)
    val builderRef     = c.prefix.tree

    val result = q"""
      $builderRef.builder.dropField($pathString, $defaultForReverse)
        .asInstanceOf[_root_.zio.blocks.schema.migration.MigrationBuilder[
          ${weakTypeOf[A]},
          ${weakTypeOf[B]},
          $newHandledType,
          ${weakTypeOf[ProvidedTree]}
        ]]
    """

    c.Expr[MigrationBuilder[A, B, _ <: FieldTree, ProvidedTree]](result)
  }

  def renameTypedImpl[
    A: c.WeakTypeTag,
    B: c.WeakTypeTag,
    HandledTree: c.WeakTypeTag,
    ProvidedTree: c.WeakTypeTag,
    T1,
    T2
  ](
    c: whitebox.Context
  )(
    from: c.Expr[A => T1],
    to: c.Expr[B => T2]
  )(implicit
    t1tag: c.WeakTypeTag[T1],
    t2tag: c.WeakTypeTag[T2]
  ): c.Expr[MigrationBuilder[A, B, _ <: FieldTree, _ <: FieldTree]] = {
    val _ = (t1tag, t2tag)
    import c.universe._

    val typeBuilder    = new TypedMigrationBuilderMacroHelpers[c.type](c)
    val fromPathParts  = typeBuilder.extractFieldPathParts(from.tree)
    val toPathParts    = typeBuilder.extractFieldPathParts(to.tree)
    val fromPathString = fromPathParts.mkString(".")
    val toPathString   = toPathParts.mkString(".")

    val newHandledType  = typeBuilder.buildTreeTypeForDrop(weakTypeOf[HandledTree], fromPathParts)
    val newProvidedType = typeBuilder.buildTreeTypeForAdd(weakTypeOf[ProvidedTree], toPathParts)
    val builderRef      = c.prefix.tree

    val result = q"""
      $builderRef.builder.renameField($fromPathString, $toPathString)
        .asInstanceOf[_root_.zio.blocks.schema.migration.MigrationBuilder[
          ${weakTypeOf[A]},
          ${weakTypeOf[B]},
          $newHandledType,
          $newProvidedType
        ]]
    """

    c.Expr[MigrationBuilder[A, B, _ <: FieldTree, _ <: FieldTree]](result)
  }

  def buildTypedImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, HandledTree: c.WeakTypeTag, ProvidedTree: c.WeakTypeTag](
    c: whitebox.Context
  ): c.Expr[Migration[A, B]] = {
    import c.universe._

    val typeBuilder = new TypedMigrationBuilderMacroHelpers[c.type](c)

    val typeA = weakTypeOf[A].dealias
    val typeB = weakTypeOf[B].dealias

    val pathsA = typeBuilder.extractFieldPathsFromType(typeA, "", Set.empty).sorted
    val pathsB = typeBuilder.extractFieldPathsFromType(typeB, "", Set.empty).sorted

    val casesA = typeBuilder.extractCaseNamesFromType(typeA).map(n => s"case:$n")
    val casesB = typeBuilder.extractCaseNamesFromType(typeB).map(n => s"case:$n")

    val handled  = typeBuilder.extractTreePaths(weakTypeOf[HandledTree])
    val provided = typeBuilder.extractTreePaths(weakTypeOf[ProvidedTree])

    val requiredHandled       = pathsA.diff(pathsB)
    val requiredProvided      = pathsB.diff(pathsA)
    val requiredHandledCases  = casesA.diff(casesB)
    val requiredProvidedCases = casesB.diff(casesA)

    val unhandled       = requiredHandled.diff(handled)
    val unprovided      = requiredProvided.diff(provided)
    val unhandledCases  = requiredHandledCases.diff(handled)
    val unprovidedCases = requiredProvidedCases.diff(provided)

    if (unhandled.nonEmpty || unprovided.nonEmpty || unhandledCases.nonEmpty || unprovidedCases.nonEmpty) {
      val sourceTypeName = typeA.toString
      val targetTypeName = typeB.toString

      val sb = new StringBuilder
      sb.append(s"\n\nMigration validation failed for $sourceTypeName => $targetTypeName:\n")

      if (unhandled.nonEmpty) {
        sb.append("\n  Unhandled fields from source (need .dropTyped or .renameTyped):\n")
        unhandled.sorted.foreach(p => sb.append(s"    - $p\n"))
      }

      if (unprovided.nonEmpty) {
        sb.append("\n  Unprovided fields for target (need .addTyped or .renameTyped):\n")
        unprovided.sorted.foreach(p => sb.append(s"    - $p\n"))
      }

      if (unhandledCases.nonEmpty) {
        sb.append("\n  Unhandled cases from source:\n")
        unhandledCases.sorted.foreach(cs => sb.append(s"    - ${cs.stripPrefix("case:")}\n"))
      }

      if (unprovidedCases.nonEmpty) {
        sb.append("\n  Unprovided cases for target:\n")
        unprovidedCases.sorted.foreach(cs => sb.append(s"    - ${cs.stripPrefix("case:")}\n"))
      }

      sb.append("\n  Tracked handled: " + (if (handled.isEmpty) "(none)" else handled.mkString(", ")))
      sb.append("\n  Tracked provided: " + (if (provided.isEmpty) "(none)" else provided.mkString(", ")))

      c.abort(c.enclosingPosition, sb.toString)
    }

    val builderRef = c.prefix.tree
    c.Expr[Migration[A, B]](q"$builderRef.builder.buildPartial")
  }
}

private[migration] class TypedMigrationBuilderMacroHelpers[C <: whitebox.Context](val c: C)
    extends MigrationHelperMacro {
  import c.universe._

  private val fieldTreeSym = typeOf[TypeLevel.FieldTree].typeSymbol
  private val emptySym     = typeOf[TypeLevel.Empty].typeSymbol
  private val branchSym    = typeOf[TypeLevel.Branch[_, _, _, _]].typeSymbol
  private val opNestedSym  = typeOf[TypeLevel.OpNested].typeSymbol

  def extractFieldPathParts(tree: Tree): List[String] = {
    def toPathBody(t: Tree): Tree = t match {
      case q"($_) => $pathBody" => pathBody
      case _                    => c.abort(c.enclosingPosition, s"Expected a lambda expression, got '$t'")
    }

    def extractPath(t: Tree): List[String] = t match {
      case q"$parent.$child" =>
        extractPath(parent) :+ child.toString
      case _: Ident =>
        List.empty
      case q"$_.apply($_)" =>
        c.abort(c.enclosingPosition, "Index-based access is not supported in field paths")
      case _ =>
        c.abort(
          c.enclosingPosition,
          s"Unsupported path expression. Expected simple field access like _.field.nested, got '$t'."
        )
    }

    val pathBody  = toPathBody(tree)
    val pathParts = extractPath(pathBody)

    if (pathParts.isEmpty) {
      c.abort(c.enclosingPosition, "Selector must access at least one field, e.g., _.name")
    }

    pathParts
  }

  def buildTreeTypeForAdd(currentTree: Type, path: List[String]): Type = {
    val branchType   = typeOf[TypeLevel.Branch[_, _, _, _]].typeConstructor
    val emptyType    = typeOf[TypeLevel.Empty]
    val opAddType    = typeOf[TypeLevel.OpAdd]
    val opNestedType = typeOf[TypeLevel.OpNested]

    path match {
      case Nil              => currentTree
      case fieldName :: Nil =>
        val nameType = c.internal.constantType(Constant(fieldName))
        appliedType(branchType, List(nameType, opAddType, emptyType, currentTree))
      case fieldName :: rest =>
        val nameType  = c.internal.constantType(Constant(fieldName))
        val childTree = buildTreeTypeForAdd(emptyType, rest)
        appliedType(branchType, List(nameType, opNestedType, childTree, currentTree))
    }
  }

  def buildTreeTypeForDrop(currentTree: Type, path: List[String]): Type = {
    val branchType   = typeOf[TypeLevel.Branch[_, _, _, _]].typeConstructor
    val emptyType    = typeOf[TypeLevel.Empty]
    val opDropType   = typeOf[TypeLevel.OpDrop]
    val opNestedType = typeOf[TypeLevel.OpNested]

    path match {
      case Nil              => currentTree
      case fieldName :: Nil =>
        val nameType = c.internal.constantType(Constant(fieldName))
        appliedType(branchType, List(nameType, opDropType, emptyType, currentTree))
      case fieldName :: rest =>
        val nameType  = c.internal.constantType(Constant(fieldName))
        val childTree = buildTreeTypeForDrop(emptyType, rest)
        appliedType(branchType, List(nameType, opNestedType, childTree, currentTree))
    }
  }

  def extractTreePaths(tpe: Type): List[String] = {
    def extract(t: Type, prefix: List[String]): List[String] = {
      val dealiased = t.dealias
      val sym       = dealiased.typeSymbol

      if (sym == emptySym || sym == fieldTreeSym) {
        Nil
      } else if (sym == branchSym) {
        val args = dealiased.typeArgs
        if (args.size != 4) {
          c.abort(c.enclosingPosition, s"Invalid Branch type: $dealiased")
        }
        val nameType     = args(0)
        val opType       = args(1)
        val childrenType = args(2)
        val siblingsType = args(3)

        val name = nameType.dealias match {
          case ConstantType(Constant(s: String)) => s
          case other                             => c.abort(c.enclosingPosition, s"Expected string literal type in Branch, got: $other")
        }

        val opSym       = opType.dealias.typeSymbol
        val currentPath = prefix :+ name

        val thisPath     = if (opSym == opNestedSym) Nil else List(currentPath.mkString("."))
        val childPaths   = extract(childrenType, currentPath)
        val siblingPaths = extract(siblingsType, prefix)

        thisPath ++ childPaths ++ siblingPaths
      } else {
        Nil
      }
    }

    extract(tpe, Nil)
  }
}
