package zio.blocks.schema.migration

import scala.annotation.nowarn
import scala.reflect.macros.blackbox

object MigrationBuilderMacros {

  @nowarn("msg=never used")
  def addFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](c: blackbox.Context)(
    target: c.Expr[B => T],
    default: c.Expr[T]
  )(targetFieldSchema: c.Expr[zio.blocks.schema.Schema[T]]): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val self = c.prefix
    c.Expr[MigrationBuilder[A, B]](q"""
      {
        val path = _root_.zio.blocks.schema.migration.SelectorMacros.toPath($target)
        val dynamicDefault = $targetFieldSchema.toDynamicValue($default)
        new _root_.zio.blocks.schema.migration.MigrationBuilder(
          $self.sourceSchema,
          $self.targetSchema,
          $self.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.AddField(path, dynamicDefault)
        )
      }
    """)
  }

  def addFieldDynamicImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context)(
    target: c.Expr[B => Any],
    default: c.Expr[zio.blocks.schema.DynamicValue]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val self = c.prefix
    c.Expr[MigrationBuilder[A, B]](q"""
      {
        val path = _root_.zio.blocks.schema.migration.SelectorMacros.toPath($target)
        new _root_.zio.blocks.schema.migration.MigrationBuilder(
          $self.sourceSchema,
          $self.targetSchema,
          $self.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.AddField(path, $default)
        )
      }
    """)
  }

  @nowarn("msg=never used")
  def dropFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](c: blackbox.Context)(
    source: c.Expr[A => T],
    defaultForReverse: c.Expr[T]
  )(sourceFieldSchema: c.Expr[zio.blocks.schema.Schema[T]]): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val self = c.prefix
    c.Expr[MigrationBuilder[A, B]](q"""
      {
        val path = _root_.zio.blocks.schema.migration.SelectorMacros.toPath($source)
        val dynamicDefault = $sourceFieldSchema.toDynamicValue($defaultForReverse)
        new _root_.zio.blocks.schema.migration.MigrationBuilder(
          $self.sourceSchema,
          $self.targetSchema,
          $self.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.DropField(path, dynamicDefault)
        )
      }
    """)
  }

  def dropFieldDynamicImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context)(
    source: c.Expr[A => Any],
    defaultForReverse: c.Expr[zio.blocks.schema.DynamicValue]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val self = c.prefix
    c.Expr[MigrationBuilder[A, B]](q"""
      {
        val path = _root_.zio.blocks.schema.migration.SelectorMacros.toPath($source)
        new _root_.zio.blocks.schema.migration.MigrationBuilder(
          $self.sourceSchema,
          $self.targetSchema,
          $self.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.DropField(path, $defaultForReverse)
        )
      }
    """)
  }

  def renameFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context)(
    from: c.Expr[A => Any],
    to: c.Expr[B => Any]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val self = c.prefix
    c.Expr[MigrationBuilder[A, B]](q"""
      {
        val fromPath = _root_.zio.blocks.schema.migration.SelectorMacros.toPath($from)
        val toFieldName = _root_.zio.blocks.schema.migration.SelectorMacros.extractFieldName($to)
        new _root_.zio.blocks.schema.migration.MigrationBuilder(
          $self.sourceSchema,
          $self.targetSchema,
          $self.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.Rename(fromPath, toFieldName)
        )
      }
    """)
  }

  @nowarn("msg=never used")
  def transformFieldLiteralImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](c: blackbox.Context)(
    at: c.Expr[A => T],
    newValue: c.Expr[T]
  )(fieldSchema: c.Expr[zio.blocks.schema.Schema[T]]): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val self = c.prefix
    c.Expr[MigrationBuilder[A, B]](q"""
      {
        val path = _root_.zio.blocks.schema.migration.SelectorMacros.toPath($at)
        val dynamicValue = $fieldSchema.toDynamicValue($newValue)
        new _root_.zio.blocks.schema.migration.MigrationBuilder(
          $self.sourceSchema,
          $self.targetSchema,
          $self.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.TransformValue(path, dynamicValue)
        )
      }
    """)
  }

  @nowarn("msg=never used")
  def mandateFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](c: blackbox.Context)(
    source: c.Expr[A => Option[T]],
    @nowarn("msg=never used") target: c.Expr[B => T],
    default: c.Expr[T]
  )(fieldSchema: c.Expr[zio.blocks.schema.Schema[T]]): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val self = c.prefix
    c.Expr[MigrationBuilder[A, B]](q"""
      {
        val path = _root_.zio.blocks.schema.migration.SelectorMacros.toPath($source)
        val dynamicDefault = $fieldSchema.toDynamicValue($default)
        new _root_.zio.blocks.schema.migration.MigrationBuilder(
          $self.sourceSchema,
          $self.targetSchema,
          $self.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.Mandate(path, dynamicDefault)
        )
      }
    """)
  }

  @nowarn("msg=never used")
  def optionalizeFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](c: blackbox.Context)(
    source: c.Expr[A => T],
    @nowarn("msg=never used") target: c.Expr[B => Option[T]]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val self = c.prefix
    c.Expr[MigrationBuilder[A, B]](q"""
      {
        val path = _root_.zio.blocks.schema.migration.SelectorMacros.toPath($source)
        new _root_.zio.blocks.schema.migration.MigrationBuilder(
          $self.sourceSchema,
          $self.targetSchema,
          $self.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.Optionalize(path)
        )
      }
    """)
  }
}
