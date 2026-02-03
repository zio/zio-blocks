package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue}

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

trait MigrationBuilderSyntaxMacro {

  implicit class MigrationBuilderOps[A, B, Handled, Provided](
    val builder: MigrationBuilder[A, B, Handled, Provided]
  ) {

    def add[T](
      target: B => T,
      defaultValue: DynamicValue
    ): MigrationBuilder[A, B, Handled, Provided] = macro MigrationBuilderSyntaxMacros.addImpl[A, B, Handled, Provided]

    def drop[T](
      source: A => T,
      defaultForReverse: DynamicValue
    ): MigrationBuilder[A, B, Handled, Provided] = macro MigrationBuilderSyntaxMacros.dropImpl[A, B, Handled, Provided]

    // format: off
    def rename[T1, T2](from: A => T1, to: B => T2): MigrationBuilder[A, B, Handled, Provided] = macro MigrationBuilderSyntaxMacros.renameImpl[A, B, Handled, Provided]

    def transform[T](path: A => T, forward: DynamicValueTransform, backward: DynamicValueTransform): MigrationBuilder[A, B, Handled, Provided] = macro MigrationBuilderSyntaxMacros.transformImpl[A, B, Handled, Provided]

    def optionalize[T](path: A => T, defaultForReverse: DynamicValue): MigrationBuilder[A, B, Handled, Provided] = macro MigrationBuilderSyntaxMacros.optionalizeImpl[A, B, Handled, Provided]

    def mandate[T](path: A => Option[T], defaultForNone: DynamicValue): MigrationBuilder[A, B, Handled, Provided] = macro MigrationBuilderSyntaxMacros.mandateImpl[A, B, Handled, Provided]

    def changeType[T](path: A => T, forward: PrimitiveConversion, backward: PrimitiveConversion): MigrationBuilder[A, B, Handled, Provided] = macro MigrationBuilderSyntaxMacros.changeTypeImpl[A, B, Handled, Provided]

    def addExpr[T](target: B => T, default: MigrationExpr[A, T]): MigrationBuilder[A, B, Handled, Provided] = macro MigrationBuilderSyntaxMacros.addExprImpl[A, B, Handled, Provided]

    def dropExpr[T](source: A => T, defaultForReverse: MigrationExpr[B, T]): MigrationBuilder[A, B, Handled, Provided] = macro MigrationBuilderSyntaxMacros.dropExprImpl[A, B, Handled, Provided]

    def optionalizeExpr[T](path: A => T, defaultForReverse: MigrationExpr[B, T]): MigrationBuilder[A, B, Handled, Provided] = macro MigrationBuilderSyntaxMacros.optionalizeExprImpl[A, B, Handled, Provided]

    def mandateExpr[T](path: A => Option[T], defaultForNone: MigrationExpr[A, T]): MigrationBuilder[A, B, Handled, Provided] = macro MigrationBuilderSyntaxMacros.mandateExprImpl[A, B, Handled, Provided]
    // format: on

    def join[T](
      target: B => T,
      sourceNames: Vector[String],
      combiner: DynamicValueTransform,
      splitter: DynamicValueTransform
    ): MigrationBuilder[A, B, Handled, Provided] = macro MigrationBuilderSyntaxMacros.joinImpl[A, B, Handled, Provided]

    def split[T](
      source: A => T,
      targetNames: Vector[String],
      splitter: DynamicValueTransform,
      combiner: DynamicValueTransform
    ): MigrationBuilder[A, B, Handled, Provided] = macro MigrationBuilderSyntaxMacros.splitImpl[A, B, Handled, Provided]

    // format: off
    def transformElements[T](path: A => Seq[T])(buildNested: MigrationStep.Record => MigrationStep.Record): MigrationBuilder[A, B, Handled, Provided] = macro MigrationBuilderSyntaxMacros.transformElementsImpl[A, B, Handled, Provided]

    def transformKeys[K, V](path: A => scala.collection.immutable.Map[K, V])(buildNested: MigrationStep.Record => MigrationStep.Record): MigrationBuilder[A, B, Handled, Provided] = macro MigrationBuilderSyntaxMacros.transformKeysImpl[A, B, Handled, Provided]

    def transformValues[K, V](path: A => scala.collection.immutable.Map[K, V])(buildNested: MigrationStep.Record => MigrationStep.Record): MigrationBuilder[A, B, Handled, Provided] = macro MigrationBuilderSyntaxMacros.transformValuesImpl[A, B, Handled, Provided]
    // format: on

    def build: Migration[A, B] = macro MigrationBuilderSyntaxMacros.buildImpl[A, B]

    def buildChecked(
      added: Set[String],
      removed: Set[String],
      renamedFrom: Set[String] = Set.empty[String],
      renamedTo: Set[String] = Set.empty[String]
    ): Migration[A, B] = macro MigrationBuilderSyntaxMacros.buildCheckedImpl[A, B]
  }
}

object MigrationBuilderSyntaxMacro extends MigrationBuilderSyntaxMacro

private[migration] object MigrationBuilderSyntaxMacros {

  private abstract class MacroSupport[C <: whitebox.Context](val c: C) extends MigrationHelperMacro {
    import c.universe._

    def extractFieldPath(selector: Tree): List[String] = {
      def unwrapLambdaBody(tree: Tree): Tree = tree match {
        case q"($_) => $body" => body
        case _                => c.abort(c.enclosingPosition, s"Expected a lambda expression, got '$tree'")
      }

      def collectPathSegments(tree: Tree): List[String] = tree match {
        case q"$parent.$field" => collectPathSegments(parent) :+ field.toString
        case _: Ident          => Nil
        case q"$_.apply($_)"   => c.abort(c.enclosingPosition, "Index-based access is not supported in field paths")
        case _                 =>
          c.abort(
            c.enclosingPosition,
            s"Unsupported path expression. Expected simple field access like _.field.nested, got '$tree'."
          )
      }

      val body     = unwrapLambdaBody(selector)
      val segments = collectPathSegments(body)

      if (segments.isEmpty) {
        c.abort(c.enclosingPosition, "Selector must access at least one field, e.g., _.name")
      }
      segments
    }

    def extractLiteralSet(tree: Tree): Set[String] = {
      def tryExtract(t: Tree): Option[Set[String]] = t match {
        case Apply(_, List(Typed(Apply(_, elems), _))) =>
          val strings = elems.collect { case Literal(Constant(s: String)) => s }
          if (strings.length == elems.length) Some(strings.toSet) else None
        case Apply(TypeApply(Select(_, TermName("apply")), _), List(Typed(Apply(_, elems), _))) =>
          val strings = elems.collect { case Literal(Constant(s: String)) => s }
          if (strings.length == elems.length) Some(strings.toSet) else None
        case TypeApply(Select(_, TermName("empty")), _) => Some(Set.empty)
        case Select(_, TermName("empty"))               => Some(Set.empty)
        case _                                          => None
      }

      tryExtract(tree).getOrElse {
        c.abort(
          c.enclosingPosition,
          s"buildChecked requires literal Set values (e.g., Set(\"field1\", \"field2\")). Got: ${showRaw(tree)}"
        )
      }
    }
  }

  private object MacroSupport {
    def apply[C <: whitebox.Context](ctx: C): MacroSupport[ctx.type] = new MacroSupport[ctx.type](ctx) {}
  }

  private def extractFieldPathExpr(c: whitebox.Context)(selector: c.Expr[Any]): c.Expr[String] = {
    import c.universe._
    val support   = MacroSupport(c)
    val pathParts = support.extractFieldPath(selector.tree)
    c.Expr[String](q"${pathParts.mkString(".")}")
  }

  private def extractDynamicOptic(c: whitebox.Context)(selector: c.Expr[Any]): c.Expr[DynamicOptic] = {
    import c.universe._
    val support    = MacroSupport(c)
    val pathParts  = support.extractFieldPath(selector.tree)
    val fieldNodes = pathParts.map(name => q"_root_.zio.blocks.schema.DynamicOptic.Node.Field($name)")
    c.Expr[DynamicOptic](
      q"_root_.zio.blocks.schema.DynamicOptic(_root_.scala.collection.immutable.IndexedSeq(..$fieldNodes))"
    )
  }

  def addImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](c: whitebox.Context)(
    target: c.Expr[Any],
    defaultValue: c.Expr[DynamicValue]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val pathExpr   = extractFieldPathExpr(c)(target)
    val builderRef = c.prefix.tree
    c.Expr[MigrationBuilder[A, B, Handled, Provided]](q"$builderRef.builder.addField($pathExpr, $defaultValue)")
  }

  def dropImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    source: c.Expr[Any],
    defaultForReverse: c.Expr[DynamicValue]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val pathExpr   = extractFieldPathExpr(c)(source)
    val builderRef = c.prefix.tree
    c.Expr[MigrationBuilder[A, B, Handled, Provided]](q"$builderRef.builder.dropField($pathExpr, $defaultForReverse)")
  }

  def renameImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    from: c.Expr[Any],
    to: c.Expr[Any]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val fromPathExpr = extractFieldPathExpr(c)(from)
    val toPathExpr   = extractFieldPathExpr(c)(to)
    val builderRef   = c.prefix.tree
    c.Expr[MigrationBuilder[A, B, Handled, Provided]](q"$builderRef.builder.renameField($fromPathExpr, $toPathExpr)")
  }

  def transformImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    path: c.Expr[Any],
    forward: c.Expr[DynamicValueTransform],
    backward: c.Expr[DynamicValueTransform]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val pathExpr   = extractFieldPathExpr(c)(path)
    val builderRef = c.prefix.tree
    c.Expr[MigrationBuilder[A, B, Handled, Provided]](
      q"$builderRef.builder.transformFieldValue($pathExpr, $forward, $backward)"
    )
  }

  def optionalizeImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    path: c.Expr[Any],
    defaultForReverse: c.Expr[DynamicValue]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val pathExpr   = extractFieldPathExpr(c)(path)
    val builderRef = c.prefix.tree
    c.Expr[MigrationBuilder[A, B, Handled, Provided]](
      q"$builderRef.builder.optionalizeField($pathExpr, $defaultForReverse)"
    )
  }

  def mandateImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    path: c.Expr[Any],
    defaultForNone: c.Expr[DynamicValue]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val pathExpr   = extractFieldPathExpr(c)(path)
    val builderRef = c.prefix.tree
    c.Expr[MigrationBuilder[A, B, Handled, Provided]](q"$builderRef.builder.mandateField($pathExpr, $defaultForNone)")
  }

  def changeTypeImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    path: c.Expr[Any],
    forward: c.Expr[PrimitiveConversion],
    backward: c.Expr[PrimitiveConversion]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val pathExpr   = extractFieldPathExpr(c)(path)
    val builderRef = c.prefix.tree
    c.Expr[MigrationBuilder[A, B, Handled, Provided]](
      q"$builderRef.builder.changeFieldType($pathExpr, $forward, $backward)"
    )
  }

  def addExprImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    target: c.Expr[Any],
    default: c.Expr[Any]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val opticExpr  = extractDynamicOptic(c)(target)
    val builderRef = c.prefix.tree
    c.Expr[MigrationBuilder[A, B, Handled, Provided]](q"$builderRef.builder.addFieldExpr($opticExpr, $default)")
  }

  def dropExprImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    source: c.Expr[Any],
    defaultForReverse: c.Expr[Any]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val opticExpr  = extractDynamicOptic(c)(source)
    val builderRef = c.prefix.tree
    c.Expr[MigrationBuilder[A, B, Handled, Provided]](
      q"$builderRef.builder.dropFieldExpr($opticExpr, $defaultForReverse)"
    )
  }

  def optionalizeExprImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    path: c.Expr[Any],
    defaultForReverse: c.Expr[Any]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val opticExpr  = extractDynamicOptic(c)(path)
    val builderRef = c.prefix.tree
    c.Expr[MigrationBuilder[A, B, Handled, Provided]](
      q"$builderRef.builder.optionalizeFieldExpr($opticExpr, $defaultForReverse)"
    )
  }

  def mandateExprImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    path: c.Expr[Any],
    defaultForNone: c.Expr[Any]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val opticExpr  = extractDynamicOptic(c)(path)
    val builderRef = c.prefix.tree
    c.Expr[MigrationBuilder[A, B, Handled, Provided]](
      q"$builderRef.builder.mandateFieldExpr($opticExpr, $defaultForNone)"
    )
  }

  def joinImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    target: c.Expr[Any],
    sourceNames: c.Expr[Vector[String]],
    combiner: c.Expr[DynamicValueTransform],
    splitter: c.Expr[DynamicValueTransform]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val pathExpr   = extractFieldPathExpr(c)(target)
    val builderRef = c.prefix.tree
    c.Expr[MigrationBuilder[A, B, Handled, Provided]](
      q"$builderRef.builder.joinFields($pathExpr, $sourceNames, $combiner, $splitter)"
    )
  }

  def splitImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    source: c.Expr[Any],
    targetNames: c.Expr[Vector[String]],
    splitter: c.Expr[DynamicValueTransform],
    combiner: c.Expr[DynamicValueTransform]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val pathExpr   = extractFieldPathExpr(c)(source)
    val builderRef = c.prefix.tree
    c.Expr[MigrationBuilder[A, B, Handled, Provided]](
      q"$builderRef.builder.splitField($pathExpr, $targetNames, $splitter, $combiner)"
    )
  }

  def transformElementsImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    path: c.Expr[Any]
  )(
    buildNested: c.Expr[MigrationStep.Record => MigrationStep.Record]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val pathExpr   = extractFieldPathExpr(c)(path)
    val builderRef = c.prefix.tree
    c.Expr[MigrationBuilder[A, B, Handled, Provided]](
      q"$builderRef.builder.transformElements($pathExpr)($buildNested)"
    )
  }

  def transformKeysImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    path: c.Expr[Any]
  )(
    buildNested: c.Expr[MigrationStep.Record => MigrationStep.Record]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val pathExpr   = extractFieldPathExpr(c)(path)
    val builderRef = c.prefix.tree
    c.Expr[MigrationBuilder[A, B, Handled, Provided]](
      q"$builderRef.builder.transformKeys($pathExpr)($buildNested)"
    )
  }

  def transformValuesImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, Handled: c.WeakTypeTag, Provided: c.WeakTypeTag](
    c: whitebox.Context
  )(
    path: c.Expr[Any]
  )(
    buildNested: c.Expr[MigrationStep.Record => MigrationStep.Record]
  ): c.Expr[MigrationBuilder[A, B, Handled, Provided]] = {
    import c.universe._
    val pathExpr   = extractFieldPathExpr(c)(path)
    val builderRef = c.prefix.tree
    c.Expr[MigrationBuilder[A, B, Handled, Provided]](
      q"$builderRef.builder.transformValues($pathExpr)($buildNested)"
    )
  }

  def buildImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context): c.Expr[Migration[A, B]] = {
    import c.universe._

    val support        = MacroSupport(c)
    val sourceFieldSet = support.extractFieldPathsFromType(weakTypeOf[A].dealias, "", Set.empty).toSet
    val targetFieldSet = support.extractFieldPathsFromType(weakTypeOf[B].dealias, "", Set.empty).toSet
    val builderRef     = c.prefix.tree

    val sourceFieldExprs = sourceFieldSet.toList.map(name => q"$name")
    val targetFieldExprs = targetFieldSet.toList.map(name => q"$name")

    c.Expr[Migration[A, B]](q"""
      {
        val _builder = $builderRef.builder
        val source: _root_.scala.collection.immutable.Set[String] = _root_.scala.collection.immutable.Set[String](..$sourceFieldExprs)
        val target: _root_.scala.collection.immutable.Set[String] = _root_.scala.collection.immutable.Set[String](..$targetFieldExprs)
        val added: _root_.scala.collection.immutable.Set[String] = _builder.addedFieldNames
        val removed: _root_.scala.collection.immutable.Set[String] = _builder.removedFieldNames
        val renamedFrom: _root_.scala.collection.immutable.Set[String] = _builder.renamedFromNames
        val renamedTo: _root_.scala.collection.immutable.Set[String] = _builder.renamedToNames

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

    val support      = MacroSupport(c)
    val sourceFields = support.extractFieldPathsFromType(weakTypeOf[A].dealias, "", Set.empty).toSet
    val targetFields = support.extractFieldPathsFromType(weakTypeOf[B].dealias, "", Set.empty).toSet

    val addedSet       = support.extractLiteralSet(added.tree)
    val removedSet     = support.extractLiteralSet(removed.tree)
    val renamedFromSet = support.extractLiteralSet(renamedFrom.tree)
    val renamedToSet   = support.extractLiteralSet(renamedTo.tree)

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
