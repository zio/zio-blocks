package zio.blocks.schema.migration

import scala.reflect.macros.whitebox
import zio.blocks.schema.SchemaExpr

/**
 * Macro implementations for MigrationBuilder methods.
 */
object MigrationBuilderMacros {

  def addFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    target: c.Expr[B => Any],
    default: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._
    locally(weakTypeOf[A])
    val targetPath = SelectorMacros.toPathImpl[B, Any](c)(target.asInstanceOf[c.Expr[B => Any]])
    q"""{
      val targetPath = $targetPath
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.AddField(
        targetPath,
        $default
      ))
    }"""
  }

  def dropFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[A => Any],
    defaultForReverse: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._
    locally(weakTypeOf[B])
    val sourcePath = SelectorMacros.toPathImpl[A, Any](c)(source.asInstanceOf[c.Expr[A => Any]])
    q"""{
      val sourcePath = $sourcePath
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.DropField(
        sourcePath,
        $defaultForReverse
      ))
    }"""
  }

  def renameFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    from: c.Expr[A => Any],
    to: c.Expr[B => Any]
  ): c.Tree = {
    import c.universe._
    val fromPath = SelectorMacros.toPathImpl[A, Any](c)(from.asInstanceOf[c.Expr[A => Any]])
    val toPath = SelectorMacros.toPathImpl[B, Any](c)(to.asInstanceOf[c.Expr[B => Any]])
    q"""{
      val fromPath = $fromPath
      val toPath = $toPath
      val toName = toPath.nodes.lastOption match {
        case _root_.scala.Some(_root_.zio.blocks.schema.DynamicOptic.Node.Field(name)) => name
        case _ => throw new IllegalArgumentException("Target selector must end with a field access")
      }
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.Rename(fromPath, toName))
    }"""
  }

  def transformFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    from: c.Expr[A => Any],
    to: c.Expr[B => Any],
    transform: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._
    locally((weakTypeOf[B], to))
    val fromPath = SelectorMacros.toPathImpl[A, Any](c)(from.asInstanceOf[c.Expr[A => Any]])
    q"""{
      val fromPath = $fromPath
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.TransformValue(
        fromPath,
        $transform
      ))
    }"""
  }

  def mandateFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[A => Option[_]],
    target: c.Expr[B => Any],
    default: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._
    locally((weakTypeOf[B], target))
    val sourcePath = SelectorMacros.toPathImpl[A, Option[_]](c)(source.asInstanceOf[c.Expr[A => Option[_]]])
    q"""{
      val sourcePath = $sourcePath
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.Mandate(
        sourcePath,
        $default
      ))
    }"""
  }

  def optionalizeFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[A => Any],
    target: c.Expr[B => Option[_]]
  ): c.Tree = {
    import c.universe._
    locally((weakTypeOf[B], target))
    val sourcePath = SelectorMacros.toPathImpl[A, Any](c)(source.asInstanceOf[c.Expr[A => Any]])
    q"""{
      val sourcePath = $sourcePath
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.Optionalize(sourcePath))
    }"""
  }

  def changeFieldTypeImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[A => Any],
    target: c.Expr[B => Any],
    converter: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._
    locally((weakTypeOf[B], target))
    val sourcePath = SelectorMacros.toPathImpl[A, Any](c)(source.asInstanceOf[c.Expr[A => Any]])
    q"""{
      val sourcePath = $sourcePath
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.ChangeType(
        sourcePath,
        $converter
      ))
    }"""
  }

  def transformElementsImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    at: c.Expr[A => Iterable[_]],
    transform: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._
    locally(weakTypeOf[B])
    val path = SelectorMacros.toPathImpl[A, Iterable[_]](c)(at.asInstanceOf[c.Expr[A => Iterable[_]]])
    q"""{
      val path = $path
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.TransformElements(
        path,
        $transform
      ))
    }"""
  }

  def transformKeysImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    at: c.Expr[A => Map[_, _]],
    transform: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._
    locally(weakTypeOf[B])
    val path = SelectorMacros.toPathImpl[A, Map[_, _]](c)(at.asInstanceOf[c.Expr[A => Map[_, _]]])
    q"""{
      val path = $path
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.TransformKeys(
        path,
        $transform
      ))
    }"""
  }

  def transformValuesImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    at: c.Expr[A => Map[_, _]],
    transform: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._
    locally(weakTypeOf[B])
    val path = SelectorMacros.toPathImpl[A, Map[_, _]](c)(at.asInstanceOf[c.Expr[A => Map[_, _]]])
    q"""{
      val path = $path
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.TransformValues(
        path,
        $transform
      ))
    }"""
  }
}
