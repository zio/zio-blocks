package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue}

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

trait MigrationBuilderSyntax {

  implicit class MigrationBuilderOps[A, B](private val builder: MigrationBuilder[A, B]) {

    def add[T](
      target: B => T,
      defaultValue: DynamicValue
    ): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.addImpl[A, B]

    def drop[T](
      source: A => T,
      defaultForReverse: DynamicValue
    ): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.dropImpl[A, B]

    def rename[T1, T2](
      from: A => T1,
      to: B => T2
    ): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.renameImpl[A, B]

    def transform[T](
      path: A => T,
      forward: DynamicValueTransform,
      backward: DynamicValueTransform
    ): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.transformImpl[A, B]

    def optionalize[T](
      path: A => T,
      defaultForReverse: DynamicValue
    ): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.optionalizeImpl[A, B]

    def mandate[T](
      path: A => Option[T],
      defaultForNone: DynamicValue
    ): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.mandateImpl[A, B]

    def changeType[T](
      path: A => T,
      forward: PrimitiveConversion,
      backward: PrimitiveConversion
    ): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.changeTypeImpl[A, B]

    def addExpr[T](
      target: B => T,
      default: MigrationExpr[A, T]
    ): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.addExprImpl[A, B]

    def dropExpr[T](
      source: A => T,
      defaultForReverse: MigrationExpr[B, T]
    ): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.dropExprImpl[A, B]

    def optionalizeExpr[T](
      path: A => T,
      defaultForReverse: MigrationExpr[B, T]
    ): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.optionalizeExprImpl[A, B]

    def mandateExpr[T](
      path: A => Option[T],
      defaultForNone: MigrationExpr[A, T]
    ): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.mandateExprImpl[A, B]

    def addCaseExpr[T](caseName: String, default: MigrationExpr[A, T]): MigrationBuilder[A, B] =
      builder.addCaseExpr(caseName, default)

    def dropCaseExpr[T](caseName: String, defaultForReverse: MigrationExpr[B, T]): MigrationBuilder[A, B] =
      builder.dropCaseExpr(caseName, defaultForReverse)

    def join[T](
      target: B => T,
      sourceNames: Vector[String],
      combiner: DynamicValueTransform,
      splitter: DynamicValueTransform
    ): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.joinImpl[A, B]

    def split[T](
      source: A => T,
      targetNames: Vector[String],
      splitter: DynamicValueTransform,
      combiner: DynamicValueTransform
    ): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.splitImpl[A, B]

    def buildValidated: Migration[A, B] = macro MigrationBuilderSyntaxMacros.buildValidatedImpl[A, B]

    def buildChecked(
      added: Set[String],
      removed: Set[String],
      renamedFrom: Set[String] = Set.empty[String],
      renamedTo: Set[String] = Set.empty[String]
    ): Migration[A, B] = macro MigrationBuilderSyntaxMacros.buildCheckedImpl[A, B]
  }
}

object MigrationBuilderSyntax extends MigrationBuilderSyntax

private[migration] object MigrationBuilderSyntaxMacros {

  private def extractFieldPath(c: whitebox.Context)(selector: c.Expr[Any]): c.Expr[String] = {
    import c.universe._

    def toPathBody(tree: Tree): Tree = tree match {
      case q"($_) => $pathBody" => pathBody
      case _                    => c.abort(c.enclosingPosition, s"Expected a lambda expression, got '$tree'")
    }

    def extractPath(tree: Tree): List[String] = tree match {
      case q"$parent.$child" =>
        extractPath(parent) :+ child.toString
      case _: Ident =>
        List.empty
      case q"$_.apply($_)" =>
        c.abort(c.enclosingPosition, "Index-based access is not supported in field paths")
      case _ =>
        c.abort(
          c.enclosingPosition,
          s"Unsupported path expression. Expected simple field access like _.field.nested, got '$tree'."
        )
    }

    val pathBody  = toPathBody(selector.tree)
    val pathParts = extractPath(pathBody)

    if (pathParts.isEmpty) {
      c.abort(c.enclosingPosition, "Selector must access at least one field, e.g., _.name")
    }

    val path = pathParts.mkString(".")
    c.Expr[String](q"$path")
  }

  private def extractDynamicOptic(c: whitebox.Context)(selector: c.Expr[Any]): c.Expr[DynamicOptic] = {
    import c.universe._

    def toPathBody(tree: Tree): Tree = tree match {
      case q"($_) => $pathBody" => pathBody
      case _                    => c.abort(c.enclosingPosition, s"Expected a lambda expression, got '$tree'")
    }

    def extractPath(tree: Tree): List[String] = tree match {
      case q"$parent.$child" =>
        extractPath(parent) :+ child.toString
      case _: Ident =>
        List.empty
      case q"$_.apply($_)" =>
        c.abort(c.enclosingPosition, "Index-based access is not supported in field paths")
      case _ =>
        c.abort(
          c.enclosingPosition,
          s"Unsupported path expression. Expected simple field access like _.field.nested, got '$tree'."
        )
    }

    val pathBody  = toPathBody(selector.tree)
    val pathParts = extractPath(pathBody)

    if (pathParts.isEmpty) {
      c.abort(c.enclosingPosition, "Selector must access at least one field, e.g., _.name")
    }

    val fieldNodes = pathParts.map { name =>
      q"_root_.zio.blocks.schema.DynamicOptic.Node.Field($name)"
    }

    c.Expr[DynamicOptic](
      q"_root_.zio.blocks.schema.DynamicOptic(_root_.scala.collection.immutable.IndexedSeq(..$fieldNodes))"
    )
  }

  def addImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    target: c.Expr[Any],
    defaultValue: c.Expr[DynamicValue]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val pathExpr   = extractFieldPath(c)(target)
    val builderRef = c.prefix.tree
    c.Expr[MigrationBuilder[A, B]](q"$builderRef.builder.addField($pathExpr, $defaultValue)")
  }

  def dropImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[Any],
    defaultForReverse: c.Expr[DynamicValue]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val pathExpr   = extractFieldPath(c)(source)
    val builderRef = c.prefix.tree
    c.Expr[MigrationBuilder[A, B]](q"$builderRef.builder.dropField($pathExpr, $defaultForReverse)")
  }

  def renameImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    from: c.Expr[Any],
    to: c.Expr[Any]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val fromPathExpr = extractFieldPath(c)(from)
    val toPathExpr   = extractFieldPath(c)(to)
    val builderRef   = c.prefix.tree
    c.Expr[MigrationBuilder[A, B]](q"$builderRef.builder.renameField($fromPathExpr, $toPathExpr)")
  }

  def transformImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    path: c.Expr[Any],
    forward: c.Expr[DynamicValueTransform],
    backward: c.Expr[DynamicValueTransform]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val pathExpr   = extractFieldPath(c)(path)
    val builderRef = c.prefix.tree
    c.Expr[MigrationBuilder[A, B]](q"$builderRef.builder.transformField($pathExpr, $forward, $backward)")
  }

  def optionalizeImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    path: c.Expr[Any],
    defaultForReverse: c.Expr[DynamicValue]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val pathExpr   = extractFieldPath(c)(path)
    val builderRef = c.prefix.tree
    c.Expr[MigrationBuilder[A, B]](q"$builderRef.builder.optionalizeField($pathExpr, $defaultForReverse)")
  }

  def mandateImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    path: c.Expr[Any],
    defaultForNone: c.Expr[DynamicValue]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val pathExpr   = extractFieldPath(c)(path)
    val builderRef = c.prefix.tree
    c.Expr[MigrationBuilder[A, B]](q"$builderRef.builder.mandateField($pathExpr, $defaultForNone)")
  }

  def changeTypeImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    path: c.Expr[Any],
    forward: c.Expr[PrimitiveConversion],
    backward: c.Expr[PrimitiveConversion]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val pathExpr   = extractFieldPath(c)(path)
    val builderRef = c.prefix.tree
    c.Expr[MigrationBuilder[A, B]](q"$builderRef.builder.changeFieldType($pathExpr, $forward, $backward)")
  }

  def addExprImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    target: c.Expr[Any],
    default: c.Expr[Any]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val opticExpr  = extractDynamicOptic(c)(target)
    val builderRef = c.prefix.tree
    c.Expr[MigrationBuilder[A, B]](q"$builderRef.builder.addFieldExpr($opticExpr, $default)")
  }

  def dropExprImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[Any],
    defaultForReverse: c.Expr[Any]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val opticExpr  = extractDynamicOptic(c)(source)
    val builderRef = c.prefix.tree
    c.Expr[MigrationBuilder[A, B]](q"$builderRef.builder.dropFieldExpr($opticExpr, $defaultForReverse)")
  }

  def optionalizeExprImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    path: c.Expr[Any],
    defaultForReverse: c.Expr[Any]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val opticExpr  = extractDynamicOptic(c)(path)
    val builderRef = c.prefix.tree
    c.Expr[MigrationBuilder[A, B]](q"$builderRef.builder.optionalizeFieldExpr($opticExpr, $defaultForReverse)")
  }

  def mandateExprImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    path: c.Expr[Any],
    defaultForNone: c.Expr[Any]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val opticExpr  = extractDynamicOptic(c)(path)
    val builderRef = c.prefix.tree
    c.Expr[MigrationBuilder[A, B]](q"$builderRef.builder.mandateFieldExpr($opticExpr, $defaultForNone)")
  }

  def joinImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    target: c.Expr[Any],
    sourceNames: c.Expr[Vector[String]],
    combiner: c.Expr[DynamicValueTransform],
    splitter: c.Expr[DynamicValueTransform]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val pathExpr   = extractFieldPath(c)(target)
    val builderRef = c.prefix.tree
    c.Expr[MigrationBuilder[A, B]](q"$builderRef.builder.joinFields($pathExpr, $sourceNames, $combiner, $splitter)")
  }

  def splitImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[Any],
    targetNames: c.Expr[Vector[String]],
    splitter: c.Expr[DynamicValueTransform],
    combiner: c.Expr[DynamicValueTransform]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val pathExpr   = extractFieldPath(c)(source)
    val builderRef = c.prefix.tree
    c.Expr[MigrationBuilder[A, B]](q"$builderRef.builder.splitField($pathExpr, $targetNames, $splitter, $combiner)")
  }

  def buildValidatedImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context): c.Expr[Migration[A, B]] = {
    import c.universe._

    def getFieldNames(t: Type): Set[String] = {
      val sym = t.typeSymbol
      if (sym.isClass && sym.asClass.isCaseClass) {
        val primaryConstructor = t.decls.collectFirst {
          case m: MethodSymbol if m.isPrimaryConstructor => m
        }
        primaryConstructor match {
          case Some(ctor) =>
            ctor.paramLists.flatten
              .filterNot(_.isImplicit)
              .map(_.name.toString.trim)
              .toSet
          case None =>
            Set.empty[String]
        }
      } else {
        t match {
          case RefinedType(parents, scope) =>
            val refinementNames = scope.collect {
              case m: TermSymbol if m.isAbstract => m.name.toString.trim
            }.toSet
            refinementNames ++ parents.flatMap(p => getFieldNames(p))
          case _ =>
            Set.empty[String]
        }
      }
    }

    val sourceFieldSet = getFieldNames(weakTypeOf[A].dealias)
    val targetFieldSet = getFieldNames(weakTypeOf[B].dealias)
    val builderRef     = c.prefix.tree

    c.Expr[Migration[A, B]](q"""
      {
        val _builder = $builderRef.builder
        val source = ${
        val fieldExprs = sourceFieldSet.map(name => q"$name")
        q"_root_.scala.collection.immutable.Set(..$fieldExprs)"
      }
        val target = ${
        val fieldExprs = targetFieldSet.map(name => q"$name")
        q"_root_.scala.collection.immutable.Set(..$fieldExprs)"
      }
        val added = _builder.addedFieldNames
        val removed = _builder.removedFieldNames
        val renamedFrom = _builder.renamedFromNames
        val renamedTo = _builder.renamedToNames

        val transformedSource = (source -- removed -- renamedFrom) ++ added ++ renamedTo
        val missing = target -- transformedSource
        val extra = transformedSource -- target

        if (missing.nonEmpty || extra.nonEmpty) {
          val missingMsg = if (missing.nonEmpty) "Missing fields in target: " + missing.mkString(", ") else ""
          val extraMsg = if (extra.nonEmpty) "Extra fields not in target: " + extra.mkString(", ") else ""
          val msgs = _root_.scala.Seq(missingMsg, extraMsg).filter(_.nonEmpty).mkString("; ")
          throw new IllegalStateException(
            "Migration validation failed from " + ${weakTypeOf[A].toString} + " to " + ${weakTypeOf[
        B
      ].toString} + ": " + msgs
          )
        }
        _builder.buildPartial
      }
    """)
  }

  def buildCheckedImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    added: c.Expr[Set[String]],
    removed: c.Expr[Set[String]],
    renamedFrom: c.Expr[Set[String]],
    renamedTo: c.Expr[Set[String]]
  ): c.Expr[Migration[A, B]] = {
    import c.universe._

    def getFieldNames(t: Type): Set[String] = {
      val sym = t.typeSymbol
      if (sym.isClass && sym.asClass.isCaseClass) {
        val primaryConstructor = t.decls.collectFirst {
          case m: MethodSymbol if m.isPrimaryConstructor => m
        }
        primaryConstructor match {
          case Some(ctor) =>
            ctor.paramLists.flatten
              .filterNot(_.isImplicit)
              .map(_.name.toString.trim)
              .toSet
          case None =>
            Set.empty[String]
        }
      } else {
        t match {
          case RefinedType(parents, scope) =>
            val refinementNames = scope.collect {
              case m: TermSymbol if m.isAbstract => m.name.toString.trim
            }.toSet
            refinementNames ++ parents.flatMap(p => getFieldNames(p))
          case _ =>
            Set.empty[String]
        }
      }
    }

    def extractLiteralSet(tree: Tree): Set[String] = {
      def extractFromTree(t: Tree): Option[Set[String]] = t match {
        case Apply(_, List(arg)) =>
          arg match {
            case Typed(Apply(_, elems), _) =>
              val strings = elems.collect { case Literal(Constant(s: String)) =>
                s
              }
              if (strings.length == elems.length) Some(strings.toSet) else None
            case _ => None
          }
        case Apply(TypeApply(Select(_, TermName("apply")), _), List(Typed(Apply(_, elems), _))) =>
          val strings = elems.collect { case Literal(Constant(s: String)) =>
            s
          }
          if (strings.length == elems.length) Some(strings.toSet) else None
        case TypeApply(Select(_, TermName("empty")), _) =>
          Some(Set.empty)
        case Select(_, TermName("empty")) =>
          Some(Set.empty)
        case _ => None
      }

      extractFromTree(tree).getOrElse {
        c.abort(
          c.enclosingPosition,
          s"buildChecked requires literal Set values (e.g., Set(\"field1\", \"field2\")). Got: ${showRaw(tree)}"
        )
      }
    }

    val sourceFields = getFieldNames(weakTypeOf[A].dealias)
    val targetFields = getFieldNames(weakTypeOf[B].dealias)

    val addedSet       = extractLiteralSet(added.tree)
    val removedSet     = extractLiteralSet(removed.tree)
    val renamedFromSet = extractLiteralSet(renamedFrom.tree)
    val renamedToSet   = extractLiteralSet(renamedTo.tree)

    val transformedSource = (sourceFields -- removedSet -- renamedFromSet) ++ addedSet ++ renamedToSet
    val missing           = targetFields -- transformedSource
    val extra             = transformedSource -- targetFields

    if (missing.nonEmpty || extra.nonEmpty) {
      val aTypeName  = weakTypeOf[A].toString
      val bTypeName  = weakTypeOf[B].toString
      val missingMsg = if (missing.nonEmpty) s"Missing fields in target: ${missing.mkString(", ")}" else ""
      val extraMsg   = if (extra.nonEmpty) s"Extra fields not in target: ${extra.mkString(", ")}" else ""
      val msgs       = Seq(missingMsg, extraMsg).filter(_.nonEmpty).mkString("; ")
      c.abort(c.enclosingPosition, s"Migration validation failed from $aTypeName to $bTypeName: $msgs")
    }

    val builderRef = c.prefix.tree
    c.Expr[Migration[A, B]](q"$builderRef.builder.buildPartial")
  }
}
