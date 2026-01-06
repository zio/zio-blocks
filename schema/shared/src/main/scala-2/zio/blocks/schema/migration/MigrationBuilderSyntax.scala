package zio.blocks.schema.migration

import scala.language.experimental.macros
import scala.reflect.macros.whitebox
import zio.blocks.schema.{DynamicOptic, DynamicValue}

/**
 * Macro implementations for MigrationBuilder methods.
 */
object MigrationBuilderMacros {

  def addFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    target: c.Expr[B => Any],
    default: c.Expr[DynamicValue]
  ): c.Tree = {
    import c.universe._
    val targetPath = SelectorMacros.toPathImpl[B, Any](c)(target.asInstanceOf[c.Expr[B => Any]])
    q"""{
      val targetPath = $targetPath
      val fieldName = targetPath.nodes.lastOption match {
        case _root_.scala.Some(_root_.zio.blocks.schema.DynamicOptic.Node.Field(name)) => name
        case _ => throw new IllegalArgumentException("Target selector must end with a field access")
      }
      val parentPath = _root_.zio.blocks.schema.DynamicOptic(targetPath.nodes.dropRight(1))
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.AddField(parentPath, fieldName, $default))
    }"""
  }

  def dropFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[A => Any],
    defaultForReverse: c.Expr[DynamicValue]
  ): c.Tree = {
    import c.universe._
    val sourcePath = SelectorMacros.toPathImpl[A, Any](c)(source.asInstanceOf[c.Expr[A => Any]])
    q"""{
      val sourcePath = $sourcePath
      val fieldName = sourcePath.nodes.lastOption match {
        case _root_.scala.Some(_root_.zio.blocks.schema.DynamicOptic.Node.Field(name)) => name
        case _ => throw new IllegalArgumentException("Source selector must end with a field access")
      }
      val parentPath = _root_.zio.blocks.schema.DynamicOptic(sourcePath.nodes.dropRight(1))
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.DropField(parentPath, fieldName, _root_.scala.Some($defaultForReverse)))
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
    transform: c.Expr[DynamicTransform]
  ): c.Tree = {
    import c.universe._
    val fromPath = SelectorMacros.toPathImpl[A, Any](c)(from.asInstanceOf[c.Expr[A => Any]])
    q"""{
      val fromPath = $fromPath
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.TransformValue(fromPath, $transform, _root_.zio.blocks.schema.migration.DynamicTransform.Identity))
    }"""
  }

  def mandateFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[A => Option[_]],
    target: c.Expr[B => Any],
    default: c.Expr[DynamicValue]
  ): c.Tree = {
    import c.universe._
    val sourcePath = SelectorMacros.toPathImpl[A, Option[_]](c)(source.asInstanceOf[c.Expr[A => Option[_]]])
    q"""{
      val sourcePath = $sourcePath
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.Mandate(sourcePath, $default))
    }"""
  }

  def optionalizeFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[A => Any],
    target: c.Expr[B => Option[_]]
  ): c.Tree = {
    import c.universe._
    val sourcePath = SelectorMacros.toPathImpl[A, Any](c)(source.asInstanceOf[c.Expr[A => Any]])
    q"""{
      val sourcePath = $sourcePath
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.Optionalize(sourcePath))
    }"""
  }

  def changeFieldTypeImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[A => Any],
    target: c.Expr[B => Any],
    converter: c.Expr[DynamicTransform]
  ): c.Tree = {
    import c.universe._
    val sourcePath = SelectorMacros.toPathImpl[A, Any](c)(source.asInstanceOf[c.Expr[A => Any]])
    q"""{
      val sourcePath = $sourcePath
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.ChangeType(sourcePath, $converter, _root_.zio.blocks.schema.migration.DynamicTransform.Identity))
    }"""
  }

  def transformElementsImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    at: c.Expr[A => Iterable[_]],
    transform: c.Expr[DynamicTransform]
  ): c.Tree = {
    import c.universe._
    val path = SelectorMacros.toPathImpl[A, Iterable[_]](c)(at.asInstanceOf[c.Expr[A => Iterable[_]]])
    q"""{
      val path = $path
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.TransformElements(path, $transform, _root_.zio.blocks.schema.migration.DynamicTransform.Identity))
    }"""
  }

  def transformKeysImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    at: c.Expr[A => Map[_, _]],
    transform: c.Expr[DynamicTransform]
  ): c.Tree = {
    import c.universe._
    val path = SelectorMacros.toPathImpl[A, Map[_, _]](c)(at.asInstanceOf[c.Expr[A => Map[_, _]]])
    q"""{
      val path = $path
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.TransformKeys(path, $transform, _root_.zio.blocks.schema.migration.DynamicTransform.Identity))
    }"""
  }

  def transformValuesImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    at: c.Expr[A => Map[_, _]],
    transform: c.Expr[DynamicTransform]
  ): c.Tree = {
    import c.universe._
    val path = SelectorMacros.toPathImpl[A, Map[_, _]](c)(at.asInstanceOf[c.Expr[A => Map[_, _]]])
    q"""{
      val path = $path
      ${c.prefix}.appendAction(_root_.zio.blocks.schema.migration.MigrationAction.TransformValues(path, $transform, _root_.zio.blocks.schema.migration.DynamicTransform.Identity))
    }"""
  }
}

