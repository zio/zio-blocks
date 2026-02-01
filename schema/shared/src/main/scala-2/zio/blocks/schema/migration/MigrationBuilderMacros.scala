package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

object MigrationBuilderMacros {

  def fieldPath[S, A](selector: S => A): String = macro MigrationBuilderMacrosImpl.fieldPathImpl

  def lastFieldName[S, A](selector: S => A): String = macro MigrationBuilderMacrosImpl.lastFieldNameImpl

  def toDynamicOptic[S, A](selector: S => A): DynamicOptic = macro MigrationBuilderMacrosImpl.toDynamicOpticImpl

  def extractFieldNames[T]: Set[String] = macro MigrationBuilderMacrosImpl.extractFieldNamesImpl[T]

  def validateMigrationCompleteness[A, B](
    sourceFields: Set[String],
    targetFields: Set[String],
    addedFields: Set[String],
    removedFields: Set[String],
    renamedFields: Map[String, String]
  ): Unit = macro MigrationBuilderMacrosImpl.validateMigrationCompletenessImpl[A, B]

  def validateTypesCompatible[A, B]: Unit = macro MigrationBuilderMacrosImpl.validateTypesCompatibleImpl[A, B]

  def validateMigrationAtCompileTime[A, B](
    addedFields: Set[String],
    removedFields: Set[String],
    renamedFrom: Set[String],
    renamedTo: Set[String]
  ): Unit = macro MigrationBuilderMacrosImpl.validateMigrationAtCompileTimeImpl[A, B]

  def validateCompileTime[A, B](
    added: Set[String],
    removed: Set[String],
    renamedFrom: Set[String],
    renamedTo: Set[String]
  ): Unit = macro MigrationBuilderMacrosImpl.validateCompileTimeImpl[A, B]
}

private[migration] object MigrationBuilderMacrosImpl {

  def fieldPathImpl(c: whitebox.Context)(selector: c.Expr[Any]): c.Expr[String] = {
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

  def lastFieldNameImpl(c: whitebox.Context)(selector: c.Expr[Any]): c.Expr[String] = {
    import c.universe._

    def toPathBody(tree: Tree): Tree = tree match {
      case q"($_) => $pathBody" => pathBody
      case _                    => c.abort(c.enclosingPosition, s"Expected a lambda expression, got '$tree'")
    }

    def extractLastField(tree: Tree): String = tree match {
      case q"$_.$child" =>
        child.toString
      case q"$_.apply($i)" =>
        s"_$i"
      case _: Ident =>
        c.abort(c.enclosingPosition, "Selector must access at least one field, e.g., _.name")
      case _ =>
        c.abort(
          c.enclosingPosition,
          s"Unsupported path expression. Expected simple field access like _.field, got '$tree'."
        )
    }

    val pathBody  = toPathBody(selector.tree)
    val fieldName = extractLastField(pathBody)

    c.Expr[String](q"$fieldName")
  }

  def toDynamicOpticImpl(c: whitebox.Context)(selector: c.Expr[Any]): c.Expr[DynamicOptic] = {
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

  def extractFieldNamesImpl[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[Set[String]] = {
    import c.universe._

    val tpe = weakTypeOf[T].dealias

    def getFieldNames(t: Type): List[String] = {
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
          case None =>
            Nil
        }
      } else {
        t match {
          case RefinedType(parents, scope) =>
            val refinementNames = scope.collect {
              case m: TermSymbol if m.isAbstract => m.name.toString.trim
            }.toList
            refinementNames ++ parents.flatMap(getFieldNames)
          case _ =>
            Nil
        }
      }
    }

    val fieldNames = getFieldNames(tpe)
    val fieldExprs = fieldNames.map(name => q"$name")
    c.Expr[Set[String]](q"_root_.scala.collection.immutable.Set(..$fieldExprs)")
  }

  def validateMigrationCompletenessImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    sourceFields: c.Expr[Set[String]],
    targetFields: c.Expr[Set[String]],
    addedFields: c.Expr[Set[String]],
    removedFields: c.Expr[Set[String]],
    renamedFields: c.Expr[Map[String, String]]
  ): c.Expr[Unit] = {
    import c.universe._

    val aTypeName = weakTypeOf[A].toString
    val bTypeName = weakTypeOf[B].toString

    c.Expr[Unit](q"""
      val source = $sourceFields
      val target = $targetFields
      val added = $addedFields
      val removed = $removedFields
      val renamed = $renamedFields

      val transformedSource = (source -- removed -- renamed.keySet) ++ added ++ renamed.values

      val missing = target -- transformedSource
      val extra = transformedSource -- target

      if (missing.nonEmpty || extra.nonEmpty) {
        val missingMsg = if (missing.nonEmpty) "Missing fields in target: " + missing.mkString(", ") else ""
        val extraMsg = if (extra.nonEmpty) "Extra fields not in target: " + extra.mkString(", ") else ""
        val msgs = _root_.scala.Seq(missingMsg, extraMsg).filter(_.nonEmpty).mkString("; ")
        throw new IllegalStateException(
          "Migration validation failed from " + $aTypeName + " to " + $bTypeName + ": " + msgs
        )
      }
    """)
  }

  def validateTypesCompatibleImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context): c.Expr[Unit] = {
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

    val sourceFields = getFieldNames(weakTypeOf[A].dealias)
    val targetFields = getFieldNames(weakTypeOf[B].dealias)

    if (sourceFields == targetFields) {
      c.Expr[Unit](q"()")
    } else {
      val added   = targetFields -- sourceFields
      val removed = sourceFields -- targetFields

      if (added.nonEmpty || removed.nonEmpty) {
        val aTypeName  = weakTypeOf[A].toString
        val bTypeName  = weakTypeOf[B].toString
        val addedMsg   = if (added.nonEmpty) s"Fields added in target: ${added.mkString(", ")}" else ""
        val removedMsg = if (removed.nonEmpty) s"Fields removed from source: ${removed.mkString(", ")}" else ""
        val msg        = s"Types $aTypeName and $bTypeName have different structures. $addedMsg $removedMsg".trim
        c.info(c.enclosingPosition, msg, force = false)
      }
      c.Expr[Unit](q"()")
    }
  }

  def validateMigrationAtCompileTimeImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    addedFields: c.Expr[Set[String]],
    removedFields: c.Expr[Set[String]],
    renamedFrom: c.Expr[Set[String]],
    renamedTo: c.Expr[Set[String]]
  ): c.Expr[Unit] = {
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

    def extractLiteralSet(tree: Tree): Option[Set[String]] =
      tree match {
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
        case _ => None
      }

    val sourceFieldSet = getFieldNames(weakTypeOf[A].dealias)
    val targetFieldSet = getFieldNames(weakTypeOf[B].dealias)

    val addedOpt       = extractLiteralSet(addedFields.tree)
    val removedOpt     = extractLiteralSet(removedFields.tree)
    val renamedFromOpt = extractLiteralSet(renamedFrom.tree)
    val renamedToOpt   = extractLiteralSet(renamedTo.tree)

    (addedOpt, removedOpt, renamedFromOpt, renamedToOpt) match {
      case (Some(added), Some(removed), Some(rFrom), Some(rTo)) =>
        val transformedSource = (sourceFieldSet -- removed -- rFrom) ++ added ++ rTo
        val missing           = targetFieldSet -- transformedSource
        val extra             = transformedSource -- targetFieldSet

        if (missing.nonEmpty || extra.nonEmpty) {
          val aTypeName  = weakTypeOf[A].toString
          val bTypeName  = weakTypeOf[B].toString
          val missingMsg = if (missing.nonEmpty) s"Missing fields in target: ${missing.mkString(", ")}" else ""
          val extraMsg   = if (extra.nonEmpty) s"Extra fields not in target: ${extra.mkString(", ")}" else ""
          val msgs       = Seq(missingMsg, extraMsg).filter(_.nonEmpty).mkString("; ")
          c.abort(c.enclosingPosition, s"Migration validation failed from $aTypeName to $bTypeName: $msgs")
        }
        c.Expr[Unit](q"()")

      case _ =>
        val sourceFieldsExpr = {
          val fieldExprs = sourceFieldSet.map(name => q"$name")
          q"_root_.scala.collection.immutable.Set(..$fieldExprs)"
        }
        val targetFieldsExpr = {
          val fieldExprs = targetFieldSet.map(name => q"$name")
          q"_root_.scala.collection.immutable.Set(..$fieldExprs)"
        }

        c.Expr[Unit](q"""
          val source = $sourceFieldsExpr
          val target = $targetFieldsExpr
          val added = $addedFields
          val removed = $removedFields
          val renamedFrom = $renamedFrom
          val renamedTo = $renamedTo

          val transformedSource = (source -- removed -- renamedFrom) ++ added ++ renamedTo
          val missing = target -- transformedSource
          val extra = transformedSource -- target

          if (missing.nonEmpty || extra.nonEmpty) {
            val missingMsg = if (missing.nonEmpty) "Missing fields in target: " + missing.mkString(", ") else ""
            val extraMsg = if (extra.nonEmpty) "Extra fields not in target: " + extra.mkString(", ") else ""
            val msgs = _root_.scala.Seq(missingMsg, extraMsg).filter(_.nonEmpty).mkString("; ")
            throw new IllegalStateException("Migration validation failed: " + msgs)
          }
        """)
    }
  }

  def validateCompileTimeImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    added: c.Expr[Set[String]],
    removed: c.Expr[Set[String]],
    renamedFrom: c.Expr[Set[String]],
    renamedTo: c.Expr[Set[String]]
  ): c.Expr[Unit] = {
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
          s"validateCompileTime requires literal Set values (e.g., Set(\"field1\", \"field2\") or Set.empty). Got: ${showRaw(tree)}"
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

    c.Expr[Unit](q"()")
  }
}
