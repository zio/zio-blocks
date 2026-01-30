package zio.blocks.schema

import scala.reflect.macros.whitebox

object MigrationMacros {

  // Extract a DynamicOptic from a lambda selector expression.
  def extractPath(c: whitebox.Context)(path: c.Tree): c.Tree = {
    import c.universe._

    def toPathBody(tree: c.Tree): c.Tree = tree match {
      case q"($_) => $pathBody" => pathBody
      case _                    => c.abort(c.enclosingPosition, s"Expected a lambda expression, got '$tree'")
    }

    def extractNodes(tree: c.Tree): List[c.Tree] = tree match {
      case q"$parent.$child" =>
        val fieldName = scala.reflect.NameTransformer.decode(child.toString)
        extractNodes(parent) :+ q"_root_.zio.blocks.schema.DynamicOptic.Node.Field($fieldName)"
      case _: Ident =>
        Nil
      case _ =>
        c.abort(
          c.enclosingPosition,
          s"Migration selectors support field access only (_.field or _.a.b.c), got '$tree'"
        )
    }

    val nodes = extractNodes(toPathBody(path))
    q"_root_.zio.blocks.schema.DynamicOptic(_root_.scala.Vector(..$nodes))"
  }

  // Extract the last field name from a lambda selector expression.
  def extractLastFieldName(c: whitebox.Context)(path: c.Tree): c.Tree = {
    import c.universe._

    def toPathBody(tree: c.Tree): c.Tree = tree match {
      case q"($_) => $pathBody" => pathBody
      case _                    => c.abort(c.enclosingPosition, s"Expected a lambda expression, got '$tree'")
    }

    def lastField(tree: c.Tree): String = tree match {
      case q"$_.$child" => scala.reflect.NameTransformer.decode(child.toString)
      case _            => c.abort(c.enclosingPosition, s"Expected a field selector like _.fieldName, got '$tree'")
    }

    val name = lastField(toPathBody(path))
    q"$name"
  }

  // Extract the parent path (all nodes except the last) from a lambda selector.
  def extractParentPath(c: whitebox.Context)(path: c.Tree): c.Tree = {
    import c.universe._

    def toPathBody(tree: c.Tree): c.Tree = tree match {
      case q"($_) => $pathBody" => pathBody
      case _                    => c.abort(c.enclosingPosition, s"Expected a lambda expression, got '$tree'")
    }

    def extractNodes(tree: c.Tree): List[c.Tree] = tree match {
      case q"$parent.$child" =>
        val fieldName = scala.reflect.NameTransformer.decode(child.toString)
        extractNodes(parent) :+ q"_root_.zio.blocks.schema.DynamicOptic.Node.Field($fieldName)"
      case _: Ident =>
        Nil
      case _ =>
        c.abort(
          c.enclosingPosition,
          s"Migration selectors support field access only (_.field or _.a.b.c), got '$tree'"
        )
    }

    val allNodes    = extractNodes(toPathBody(path))
    val parentNodes = if (allNodes.nonEmpty) allNodes.init else allNodes
    q"_root_.zio.blocks.schema.DynamicOptic(_root_.scala.Vector(..$parentNodes))"
  }

  // ─── Builder method implementations (original 5) ────────────────────

  def addFieldImpl[A, B](c: whitebox.Context)(
    target: c.Tree,
    default: c.Tree
  ): c.Tree = {
    import c.universe._
    val path     = extractParentPath(c)(target)
    val nameTree = extractLastFieldName(c)(target)
    q"${c.prefix.tree}.addField($path, $nameTree, $default)"
  }

  def dropFieldImpl[A, B](c: whitebox.Context)(
    source: c.Tree,
    defaultForReverse: c.Tree
  ): c.Tree = {
    import c.universe._
    val path     = extractParentPath(c)(source)
    val nameTree = extractLastFieldName(c)(source)
    q"${c.prefix.tree}.dropField($path, $nameTree, $defaultForReverse)"
  }

  def renameFieldImpl[A, B](c: whitebox.Context)(
    from: c.Tree,
    to: c.Tree
  ): c.Tree = {
    import c.universe._
    val fromName = extractLastFieldName(c)(from)
    val toName   = extractLastFieldName(c)(to)
    val atPath   = extractParentPath(c)(from)
    q"${c.prefix.tree}.renameField($atPath, $fromName, $toName)"
  }

  def mandateFieldImpl[A, B](c: whitebox.Context)(
    source: c.Tree,
    default: c.Tree
  ): c.Tree = {
    import c.universe._
    val path = extractPath(c)(source)
    q"${c.prefix.tree}.mandate($path, $default)"
  }

  def optionalizeFieldImpl[A, B](c: whitebox.Context)(
    source: c.Tree
  ): c.Tree = {
    import c.universe._
    val path = extractPath(c)(source)
    q"${c.prefix.tree}.optionalize($path)"
  }

  // ─── Builder method implementations (new 5) ────────────────────────

  def transformFieldImpl[A, B](c: whitebox.Context)(
    source: c.Tree,
    transform: c.Tree,
    reverseTransform: c.Tree
  ): c.Tree = {
    import c.universe._
    val path = extractPath(c)(source)
    q"${c.prefix.tree}.transformValue($path, $transform, $reverseTransform)"
  }

  def changeFieldTypeImpl[A, B](c: whitebox.Context)(
    source: c.Tree,
    converter: c.Tree,
    reverseConverter: c.Tree
  ): c.Tree = {
    import c.universe._
    val path = extractPath(c)(source)
    q"${c.prefix.tree}.changeType($path, $converter, $reverseConverter)"
  }

  def transformElementsImpl[A, B](c: whitebox.Context)(
    source: c.Tree,
    transform: c.Tree,
    reverseTransform: c.Tree
  ): c.Tree = {
    import c.universe._
    val path = extractPath(c)(source)
    q"${c.prefix.tree}.transformElements($path, $transform, $reverseTransform)"
  }

  def transformKeysImpl[A, B](c: whitebox.Context)(
    source: c.Tree,
    transform: c.Tree,
    reverseTransform: c.Tree
  ): c.Tree = {
    import c.universe._
    val path = extractPath(c)(source)
    q"${c.prefix.tree}.transformKeys($path, $transform, $reverseTransform)"
  }

  def transformValuesImpl[A, B](c: whitebox.Context)(
    source: c.Tree,
    transform: c.Tree,
    reverseTransform: c.Tree
  ): c.Tree = {
    import c.universe._
    val path = extractPath(c)(source)
    q"${c.prefix.tree}.transformValues($path, $transform, $reverseTransform)"
  }
}
