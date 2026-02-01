/*
 * Copyright 2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema}

import scala.language.experimental.macros

/**
 * A type-safe migration builder with selector-based API.
 *
 * This builder uses selector expressions like `_.name`, `_.address.street` for
 * specifying paths, which are converted to DynamicOptic at compile time via
 * macros.
 *
 * @tparam A
 *   Source type
 * @tparam B
 *   Target type
 */
final class TypedMigrationBuilder[A, B] private[migration] (
  private[migration] val sourceSchema: Schema[A],
  private[migration] val targetSchema: Schema[B],
  private[migration] val actions: Vector[MigrationAction],
  private[migration] val handledSourcePaths: Set[String],
  private[migration] val handledTargetPaths: Set[String]
) {

  // ===========================================================================
  // Selector-based Record Operations (using macros)
  // ===========================================================================

  /**
   * Rename a field using selector expressions.
   */
  def renameField[S, T](from: A => S, to: B => T): TypedMigrationBuilder[A, B] = macro
    TypedMigrationBuilderMacros.renameFieldImpl[A, B, S, T]

  /**
   * Add a field with a default value using selector expression.
   */
  def addField[T](target: B => T, default: T)(implicit ev: Schema[T]): TypedMigrationBuilder[A, B] = macro
    TypedMigrationBuilderMacros.addFieldImpl[A, B, T]

  /**
   * Add a field with a SchemaExpr default.
   */
  def addFieldExpr[T](target: B => T, default: ResolvedExpr): TypedMigrationBuilder[A, B] = macro
    TypedMigrationBuilderMacros.addFieldExprImpl[A, B, T]

  /**
   * Drop a field using selector expression.
   */
  def dropField[S](source: A => S): TypedMigrationBuilder[A, B] = macro
    TypedMigrationBuilderMacros.dropFieldImpl[A, B, S]

  /**
   * Drop a field with a default for reverse migration.
   */
  def dropField[S](source: A => S, defaultForReverse: ResolvedExpr): TypedMigrationBuilder[A, B] = macro
    TypedMigrationBuilderMacros.dropFieldWithDefaultImpl[A, B, S]

  /**
   * Keep a field unchanged (explicit tracking for validation).
   */
  def keepField[S, T](source: A => S, target: B => T): TypedMigrationBuilder[A, B] = macro
    TypedMigrationBuilderMacros.keepFieldImpl[A, B, S, T]

  /**
   * Transform a field's value using selector expressions.
   */
  def transformField[S, T](source: A => S, target: B => T, transform: ResolvedExpr): TypedMigrationBuilder[A, B] = macro
    TypedMigrationBuilderMacros.transformFieldImpl[A, B, S, T]

  /**
   * Make an optional field mandatory using selector expressions.
   */
  def mandateField[S, T](
    source: A => Option[S],
    target: B => T,
    default: ResolvedExpr
  ): TypedMigrationBuilder[A, B] = macro
    TypedMigrationBuilderMacros.mandateFieldImpl[A, B, S, T]

  /**
   * Make a mandatory field optional using selector expressions.
   */
  def optionalizeField[S, T](source: A => S, target: B => Option[T]): TypedMigrationBuilder[A, B] = macro
    TypedMigrationBuilderMacros.optionalizeFieldImpl[A, B, S, T]

  /**
   * Change a field's type using selector expressions.
   */
  def changeFieldType[S, T](
    source: A => S,
    target: B => T,
    converter: ResolvedExpr
  ): TypedMigrationBuilder[A, B] = macro
    TypedMigrationBuilderMacros.changeFieldTypeImpl[A, B, S, T]

  // ===========================================================================
  // Nested Migration Operations
  // ===========================================================================

  /**
   * Apply a nested migration to a field.
   */
  def inField[F1, F2](sourceField: A => F1, targetField: B => F2)(
    nestedMigration: Migration[F1, F2]
  ): TypedMigrationBuilder[A, B] = macro
    TypedMigrationBuilderMacros.inFieldImpl[A, B, F1, F2]

  // ===========================================================================
  // Enum Operations
  // ===========================================================================

  /**
   * Rename a variant case.
   */
  def renameCase(from: String, to: String): TypedMigrationBuilder[A, B] =
    new TypedMigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.RenameCase(DynamicOptic.root, from, to),
      handledSourcePaths,
      handledTargetPaths
    )

  /**
   * Transform a variant case's content.
   */
  def transformCase(caseName: String)(
    nested: TypedMigrationBuilder[A, B] => TypedMigrationBuilder[A, B]
  ): TypedMigrationBuilder[A, B] = {
    val nestedBuilder = nested(
      new TypedMigrationBuilder(
        sourceSchema,
        targetSchema,
        Vector.empty,
        Set.empty,
        Set.empty
      )
    )
    new TypedMigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformCase(DynamicOptic.root, caseName, nestedBuilder.actions),
      handledSourcePaths,
      handledTargetPaths
    )
  }

  // ===========================================================================
  // Collection Operations
  // ===========================================================================

  /**
   * Transform all elements of a sequence at a path.
   */
  def transformElements[S](at: A => Seq[S], transform: ResolvedExpr): TypedMigrationBuilder[A, B] = macro
    TypedMigrationBuilderMacros.transformElementsImpl[A, B, S]

  /**
   * Transform all keys of a map at a path.
   */
  def transformKeys[K, V](at: A => Map[K, V], transform: ResolvedExpr): TypedMigrationBuilder[A, B] = macro
    TypedMigrationBuilderMacros.transformKeysImpl[A, B, K, V]

  /**
   * Transform all values of a map at a path.
   */
  def transformValues[K, V](at: A => Map[K, V], transform: ResolvedExpr): TypedMigrationBuilder[A, B] = macro
    TypedMigrationBuilderMacros.transformValuesImpl[A, B, K, V]

  // ===========================================================================
  // Build Methods
  // ===========================================================================

  /**
   * Build the migration with runtime validation.
   *
   * Note: In Scala 2, full compile-time validation is limited. This method
   * performs runtime validation to ensure all fields are handled.
   */
  def build: Migration[A, B] = {
    // Runtime validation for Scala 2
    val sourceFields = sourceSchema.reflect match {
      case r: zio.blocks.schema.Reflect.Record[zio.blocks.schema.binding.Binding, _] =>
        r.fields.map(_.name).toSet
      case _ => Set.empty[String]
    }
    val targetFields = targetSchema.reflect match {
      case r: zio.blocks.schema.Reflect.Record[zio.blocks.schema.binding.Binding, _] =>
        r.fields.map(_.name).toSet
      case _ => Set.empty[String]
    }

    val missingSrc = sourceFields -- handledSourcePaths.map(_.split("\\.").head)
    val missingTgt = targetFields -- handledTargetPaths.map(_.split("\\.").head)

    if (missingSrc.nonEmpty || missingTgt.nonEmpty) {
      val srcMsg =
        if (missingSrc.nonEmpty)
          s"source fields not handled: ${missingSrc.mkString(", ")}"
        else ""
      val tgtMsg =
        if (missingTgt.nonEmpty)
          s"target fields not provided: ${missingTgt.mkString(", ")}"
        else ""
      throw new IllegalStateException(
        s"Migration incomplete: ${Seq(srcMsg, tgtMsg).filter(_.nonEmpty).mkString("; ")}"
      )
    }

    Migration(DynamicMigration(actions), sourceSchema, targetSchema)
  }

  /**
   * Build the migration without completeness validation.
   */
  def buildPartial: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  /**
   * Build with runtime validation returning Either.
   */
  def buildValidated: Either[String, Migration[A, B]] = {
    val sourceFields = sourceSchema.reflect match {
      case r: zio.blocks.schema.Reflect.Record[zio.blocks.schema.binding.Binding, _] =>
        r.fields.map(_.name).toSet
      case _ => Set.empty[String]
    }
    val targetFields = targetSchema.reflect match {
      case r: zio.blocks.schema.Reflect.Record[zio.blocks.schema.binding.Binding, _] =>
        r.fields.map(_.name).toSet
      case _ => Set.empty[String]
    }

    val missingSrc = sourceFields -- handledSourcePaths.map(_.split("\\.").head)
    val missingTgt = targetFields -- handledTargetPaths.map(_.split("\\.").head)

    if (missingSrc.nonEmpty || missingTgt.nonEmpty) {
      val srcMsg =
        if (missingSrc.nonEmpty)
          s"source fields not handled: ${missingSrc.mkString(", ")}"
        else ""
      val tgtMsg =
        if (missingTgt.nonEmpty)
          s"target fields not provided: ${missingTgt.mkString(", ")}"
        else ""
      Left(s"Migration incomplete: ${Seq(srcMsg, tgtMsg).filter(_.nonEmpty).mkString("; ")}")
    } else {
      Right(Migration(DynamicMigration(actions), sourceSchema, targetSchema))
    }
  }

  /**
   * Build and return the underlying DynamicMigration.
   */
  def buildDynamic: DynamicMigration =
    DynamicMigration(actions)
}

object TypedMigrationBuilder {

  /**
   * Create a new typed builder for migrating from A to B.
   */
  def apply[A, B](implicit sourceSchema: Schema[A], targetSchema: Schema[B]): TypedMigrationBuilder[A, B] =
    new TypedMigrationBuilder(sourceSchema, targetSchema, Vector.empty, Set.empty, Set.empty)

  /**
   * Wrap an action in nested AtField calls based on the path nodes. This
   * converts a path like [Field("address"), Field("city")] into
   * AtField("address", Vector(AtField("city", Vector(action))))
   */
  private[migration] def wrapInAtField(
    nodes: IndexedSeq[DynamicOptic.Node],
    action: MigrationAction
  ): MigrationAction = {
    def wrapList(nodeList: List[DynamicOptic.Node], action: MigrationAction): MigrationAction =
      nodeList match {
        case Nil                                   => action
        case DynamicOptic.Node.Field(name) :: tail =>
          MigrationAction.AtField(name, Vector(wrapList(tail, action)))
        case _ :: tail =>
          // Skip non-field nodes (Case, Elements, etc.) - not supported for simple field ops
          wrapList(tail, action)
      }
    wrapList(nodes.toList, action)
  }
}

/**
 * Macros for TypedMigrationBuilder methods.
 */
@scala.annotation.nowarn("msg=never used")
private object TypedMigrationBuilderMacros {
  import scala.reflect.macros.whitebox
  import zio.blocks.schema.CommonMacroOps

  def renameFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, S: c.WeakTypeTag, T: c.WeakTypeTag](
    c: whitebox.Context
  )(from: c.Expr[A => S], to: c.Expr[B => T]): c.Tree = {
    import c.universe._
    val fromOptic = extractDynamicOptic(c)(from.tree)
    val fromName  = extractFieldName(c)(from.tree)
    val toName    = extractFieldName(c)(to.tree)
    val fromPath  = extractPath(c)(from.tree)
    val toPath    = extractPath(c)(to.tree)
    q"""
      {
        val optic = $fromOptic
        val renameAction = _root_.zio.blocks.schema.migration.MigrationAction.RenameField($fromName, $toName)
        val wrappedAction = _root_.zio.blocks.schema.migration.TypedMigrationBuilder.wrapInAtField(optic.nodes.dropRight(1), renameAction)
        new _root_.zio.blocks.schema.migration.TypedMigrationBuilder(
          ${c.prefix}.sourceSchema,
          ${c.prefix}.targetSchema,
          ${c.prefix}.actions :+ wrappedAction,
          ${c.prefix}.handledSourcePaths + $fromPath,
          ${c.prefix}.handledTargetPaths + $toPath
        )
      }
    """
  }

  def addFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](
    c: whitebox.Context
  )(target: c.Expr[B => T], default: c.Expr[T])(ev: c.Expr[Schema[T]]): c.Tree = {
    import c.universe._
    val targetOptic = extractDynamicOptic(c)(target.tree)
    val fieldName   = extractFieldName(c)(target.tree)
    val targetPath  = extractPath(c)(target.tree)
    q"""
      {
        val optic = $targetOptic
        val defaultValue = _root_.zio.blocks.schema.migration.ResolvedExpr.Literal($ev.toDynamicValue($default))
        val addAction = _root_.zio.blocks.schema.migration.MigrationAction.AddField(_root_.zio.blocks.schema.DynamicOptic.root, $fieldName, defaultValue)
        val wrappedAction = _root_.zio.blocks.schema.migration.TypedMigrationBuilder.wrapInAtField(optic.nodes.dropRight(1), addAction)
        new _root_.zio.blocks.schema.migration.TypedMigrationBuilder(
          ${c.prefix}.sourceSchema,
          ${c.prefix}.targetSchema,
          ${c.prefix}.actions :+ wrappedAction,
          ${c.prefix}.handledSourcePaths,
          ${c.prefix}.handledTargetPaths + $targetPath
        )
      }
    """
  }

  def addFieldExprImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](
    c: whitebox.Context
  )(target: c.Expr[B => T], default: c.Expr[ResolvedExpr]): c.Tree = {
    import c.universe._
    val targetOptic = extractDynamicOptic(c)(target.tree)
    val fieldName   = extractFieldName(c)(target.tree)
    val targetPath  = extractPath(c)(target.tree)
    q"""
      {
        val optic = $targetOptic
        val addAction = _root_.zio.blocks.schema.migration.MigrationAction.AddField(_root_.zio.blocks.schema.DynamicOptic.root, $fieldName, $default)
        val wrappedAction = _root_.zio.blocks.schema.migration.TypedMigrationBuilder.wrapInAtField(optic.nodes.dropRight(1), addAction)
        new _root_.zio.blocks.schema.migration.TypedMigrationBuilder(
          ${c.prefix}.sourceSchema,
          ${c.prefix}.targetSchema,
          ${c.prefix}.actions :+ wrappedAction,
          ${c.prefix}.handledSourcePaths,
          ${c.prefix}.handledTargetPaths + $targetPath
        )
      }
    """
  }

  def dropFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, S: c.WeakTypeTag](
    c: whitebox.Context
  )(source: c.Expr[A => S]): c.Tree = {
    import c.universe._
    val sourceOptic = extractDynamicOptic(c)(source.tree)
    val fieldName   = extractFieldName(c)(source.tree)
    val sourcePath  = extractPath(c)(source.tree)
    q"""
      {
        val optic = $sourceOptic
        val dropAction = _root_.zio.blocks.schema.migration.MigrationAction.DropField(_root_.zio.blocks.schema.DynamicOptic.root, $fieldName, None)
        val wrappedAction = _root_.zio.blocks.schema.migration.TypedMigrationBuilder.wrapInAtField(optic.nodes.dropRight(1), dropAction)
        new _root_.zio.blocks.schema.migration.TypedMigrationBuilder(
          ${c.prefix}.sourceSchema,
          ${c.prefix}.targetSchema,
          ${c.prefix}.actions :+ wrappedAction,
          ${c.prefix}.handledSourcePaths + $sourcePath,
          ${c.prefix}.handledTargetPaths
        )
      }
    """
  }

  def dropFieldWithDefaultImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, S: c.WeakTypeTag](
    c: whitebox.Context
  )(source: c.Expr[A => S], defaultForReverse: c.Expr[ResolvedExpr]): c.Tree = {
    import c.universe._
    val sourceOptic = extractDynamicOptic(c)(source.tree)
    val fieldName   = extractFieldName(c)(source.tree)
    val sourcePath  = extractPath(c)(source.tree)
    q"""
      {
        val optic = $sourceOptic
        val dropAction = _root_.zio.blocks.schema.migration.MigrationAction.DropField(_root_.zio.blocks.schema.DynamicOptic.root, $fieldName, Some($defaultForReverse))
        val wrappedAction = _root_.zio.blocks.schema.migration.TypedMigrationBuilder.wrapInAtField(optic.nodes.dropRight(1), dropAction)
        new _root_.zio.blocks.schema.migration.TypedMigrationBuilder(
          ${c.prefix}.sourceSchema,
          ${c.prefix}.targetSchema,
          ${c.prefix}.actions :+ wrappedAction,
          ${c.prefix}.handledSourcePaths + $sourcePath,
          ${c.prefix}.handledTargetPaths
        )
      }
    """
  }

  def keepFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, S: c.WeakTypeTag, T: c.WeakTypeTag](
    c: whitebox.Context
  )(source: c.Expr[A => S], target: c.Expr[B => T]): c.Tree = {
    import c.universe._
    val sourcePath = extractPath(c)(source.tree)
    val targetPath = extractPath(c)(target.tree)
    q"""
      new _root_.zio.blocks.schema.migration.TypedMigrationBuilder(
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions,
        ${c.prefix}.handledSourcePaths + $sourcePath,
        ${c.prefix}.handledTargetPaths + $targetPath
      )
    """
  }

  def transformFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, S: c.WeakTypeTag, T: c.WeakTypeTag](
    c: whitebox.Context
  )(source: c.Expr[A => S], target: c.Expr[B => T], transform: c.Expr[ResolvedExpr]): c.Tree = {
    import c.universe._
    val sourceOptic = extractDynamicOptic(c)(source.tree)
    val sourcePath  = extractPath(c)(source.tree)
    val targetPath  = extractPath(c)(target.tree)
    q"""
      new _root_.zio.blocks.schema.migration.TypedMigrationBuilder(
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.TransformValue($sourceOptic, $transform, None),
        ${c.prefix}.handledSourcePaths + $sourcePath,
        ${c.prefix}.handledTargetPaths + $targetPath
      )
    """
  }

  def mandateFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, S: c.WeakTypeTag, T: c.WeakTypeTag](
    c: whitebox.Context
  )(source: c.Expr[A => Option[S]], target: c.Expr[B => T], default: c.Expr[ResolvedExpr]): c.Tree = {
    import c.universe._
    val sourceOptic = extractDynamicOptic(c)(source.tree)
    val sourcePath  = extractPath(c)(source.tree)
    val targetPath  = extractPath(c)(target.tree)
    q"""
      new _root_.zio.blocks.schema.migration.TypedMigrationBuilder(
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.Mandate($sourceOptic, $default),
        ${c.prefix}.handledSourcePaths + $sourcePath,
        ${c.prefix}.handledTargetPaths + $targetPath
      )
    """
  }

  def optionalizeFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, S: c.WeakTypeTag, T: c.WeakTypeTag](
    c: whitebox.Context
  )(source: c.Expr[A => S], target: c.Expr[B => Option[T]]): c.Tree = {
    import c.universe._
    val sourceOptic = extractDynamicOptic(c)(source.tree)
    val sourcePath  = extractPath(c)(source.tree)
    val targetPath  = extractPath(c)(target.tree)
    q"""
      new _root_.zio.blocks.schema.migration.TypedMigrationBuilder(
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.Optionalize($sourceOptic),
        ${c.prefix}.handledSourcePaths + $sourcePath,
        ${c.prefix}.handledTargetPaths + $targetPath
      )
    """
  }

  def changeFieldTypeImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, S: c.WeakTypeTag, T: c.WeakTypeTag](
    c: whitebox.Context
  )(source: c.Expr[A => S], target: c.Expr[B => T], converter: c.Expr[ResolvedExpr]): c.Tree = {
    import c.universe._
    val sourceOptic = extractDynamicOptic(c)(source.tree)
    val sourcePath  = extractPath(c)(source.tree)
    val targetPath  = extractPath(c)(target.tree)
    q"""
      new _root_.zio.blocks.schema.migration.TypedMigrationBuilder(
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.ChangeType($sourceOptic, $converter),
        ${c.prefix}.handledSourcePaths + $sourcePath,
        ${c.prefix}.handledTargetPaths + $targetPath
      )
    """
  }

  def inFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, F1: c.WeakTypeTag, F2: c.WeakTypeTag](
    c: whitebox.Context
  )(sourceField: c.Expr[A => F1], targetField: c.Expr[B => F2])(
    nestedMigration: c.Expr[Migration[F1, F2]]
  ): c.Tree = {
    import c.universe._
    val sourceFieldOptic = extractDynamicOptic(c)(sourceField.tree)
    val targetFieldName  = extractFieldName(c)(targetField.tree)
    val sourceFieldName  = extractFieldName(c)(sourceField.tree)
    val sourcePath       = extractPath(c)(sourceField.tree)
    val targetPath       = extractPath(c)(targetField.tree)
    q"""
      {
        val srcOptic = $sourceFieldOptic
        val srcName = $sourceFieldName
        val tgtName = $targetFieldName
        val nested = $nestedMigration

        def prefixAction(action: _root_.zio.blocks.schema.migration.MigrationAction, prefix: _root_.zio.blocks.schema.DynamicOptic): _root_.zio.blocks.schema.migration.MigrationAction = {
          def prefixOptic(optic: _root_.zio.blocks.schema.DynamicOptic): _root_.zio.blocks.schema.DynamicOptic =
            _root_.zio.blocks.schema.DynamicOptic(prefix.nodes ++ optic.nodes)

          action match {
            case a: _root_.zio.blocks.schema.migration.MigrationAction.AddField =>
              _root_.zio.blocks.schema.migration.MigrationAction.AddField(prefixOptic(a.at), a.fieldName, a.default)
            case a: _root_.zio.blocks.schema.migration.MigrationAction.DropField =>
              _root_.zio.blocks.schema.migration.MigrationAction.DropField(prefixOptic(a.at), a.fieldName, a.defaultForReverse)
            case a: _root_.zio.blocks.schema.migration.MigrationAction.Rename =>
              _root_.zio.blocks.schema.migration.MigrationAction.Rename(prefixOptic(a.at), a.to)
            case a: _root_.zio.blocks.schema.migration.MigrationAction.TransformValue =>
              _root_.zio.blocks.schema.migration.MigrationAction.TransformValue(prefixOptic(a.at), a.transform, a.reverseTransform)
            case a: _root_.zio.blocks.schema.migration.MigrationAction.Mandate =>
              _root_.zio.blocks.schema.migration.MigrationAction.Mandate(prefixOptic(a.at), a.default)
            case a: _root_.zio.blocks.schema.migration.MigrationAction.Optionalize =>
              _root_.zio.blocks.schema.migration.MigrationAction.Optionalize(prefixOptic(a.at))
            case a: _root_.zio.blocks.schema.migration.MigrationAction.ChangeType =>
              _root_.zio.blocks.schema.migration.MigrationAction.ChangeType(prefixOptic(a.at), a.converter)
            case a: _root_.zio.blocks.schema.migration.MigrationAction.Keep =>
              _root_.zio.blocks.schema.migration.MigrationAction.Keep(prefixOptic(a.at))
            case other => other
          }
        }

        val prefixedActions = nested.dynamicMigration.actions.map(a => prefixAction(a, srcOptic))
        val renameAction = if (srcName != tgtName) {
          Vector(_root_.zio.blocks.schema.migration.MigrationAction.Rename(
            _root_.zio.blocks.schema.DynamicOptic(srcOptic.nodes.dropRight(1)),
            tgtName
          ))
        } else Vector.empty

        new _root_.zio.blocks.schema.migration.TypedMigrationBuilder(
          ${c.prefix}.sourceSchema,
          ${c.prefix}.targetSchema,
          ${c.prefix}.actions ++ renameAction ++ prefixedActions,
          ${c.prefix}.handledSourcePaths + $sourcePath,
          ${c.prefix}.handledTargetPaths + $targetPath
        )
      }
    """
  }

  def transformElementsImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, S: c.WeakTypeTag](
    c: whitebox.Context
  )(at: c.Expr[A => Seq[S]], transform: c.Expr[ResolvedExpr]): c.Tree = {
    import c.universe._
    val atOptic = extractDynamicOptic(c)(at.tree)
    val atPath  = extractPath(c)(at.tree)
    q"""
      new _root_.zio.blocks.schema.migration.TypedMigrationBuilder(
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.TransformElements($atOptic, $transform, None),
        ${c.prefix}.handledSourcePaths + $atPath,
        ${c.prefix}.handledTargetPaths + $atPath
      )
    """
  }

  def transformKeysImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, K: c.WeakTypeTag, V: c.WeakTypeTag](
    c: whitebox.Context
  )(at: c.Expr[A => Map[K, V]], transform: c.Expr[ResolvedExpr]): c.Tree = {
    import c.universe._
    val atOptic = extractDynamicOptic(c)(at.tree)
    val atPath  = extractPath(c)(at.tree)
    q"""
      new _root_.zio.blocks.schema.migration.TypedMigrationBuilder(
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.TransformKeys($atOptic, $transform, None),
        ${c.prefix}.handledSourcePaths + $atPath,
        ${c.prefix}.handledTargetPaths + $atPath
      )
    """
  }

  def transformValuesImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, K: c.WeakTypeTag, V: c.WeakTypeTag](
    c: whitebox.Context
  )(at: c.Expr[A => Map[K, V]], transform: c.Expr[ResolvedExpr]): c.Tree = {
    import c.universe._
    val atOptic = extractDynamicOptic(c)(at.tree)
    val atPath  = extractPath(c)(at.tree)
    q"""
      new _root_.zio.blocks.schema.migration.TypedMigrationBuilder(
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.TransformValues($atOptic, $transform, None),
        ${c.prefix}.handledSourcePaths + $atPath,
        ${c.prefix}.handledTargetPaths + $atPath
      )
    """
  }

  // Helper to extract DynamicOptic from selector expression
  private def extractDynamicOptic(c: whitebox.Context)(tree: c.Tree): c.Tree = {
    import c.universe._

    def toPathBody(tree: c.Tree): c.Tree = tree match {
      case q"($_) => $pathBody" => pathBody
      case _                    => CommonMacroOps.fail(c)(s"Expected a lambda expression, got '$tree'")
    }

    def toOptic(tree: c.Tree): c.Tree = tree match {
      case q"$_[..$_]($parent).each" =>
        val parentOptic = toOptic(parent)
        q"$parentOptic.elements"
      case q"$_[..$_]($parent).eachKey" =>
        val parentOptic = toOptic(parent)
        q"$parentOptic.mapKeys"
      case q"$_[..$_]($parent).eachValue" =>
        val parentOptic = toOptic(parent)
        q"$parentOptic.mapValues"
      case q"$_[..$_]($parent).when[$caseTree]" =>
        val caseName    = caseTree.tpe.dealias.typeSymbol.name.toString
        val parentOptic = toOptic(parent)
        q"$parentOptic.caseOf($caseName)"
      case q"$_[..$_]($parent).wrapped[$_]" =>
        val parentOptic = toOptic(parent)
        q"$parentOptic.wrapped"
      case q"$_[..$_]($parent).at(..$args)" if args.size == 1 =>
        val parentOptic = toOptic(parent)
        q"$parentOptic.at(${args.head})"
      case q"$parent.$child" =>
        val fieldName   = scala.reflect.NameTransformer.decode(child.toString)
        val parentOptic = toOptic(parent)
        q"$parentOptic.field($fieldName)"
      case _: Ident =>
        q"_root_.zio.blocks.schema.DynamicOptic.root"
      case _ =>
        CommonMacroOps.fail(c)(s"Expected path elements, got '$tree'")
    }

    toOptic(toPathBody(tree))
  }

  // Helper to extract field name from selector
  private def extractFieldName(c: whitebox.Context)(tree: c.Tree): c.Tree = {
    import c.universe._

    def toPathBody(tree: c.Tree): c.Tree = tree match {
      case q"($_) => $pathBody" => pathBody
      case _                    => CommonMacroOps.fail(c)(s"Expected a lambda expression, got '$tree'")
    }

    def getLastFieldName(tree: c.Tree): String = tree match {
      case q"$_.$child" => scala.reflect.NameTransformer.decode(child.toString)
      case _            => CommonMacroOps.fail(c)(s"Expected field access, got '$tree'")
    }

    val fieldName = getLastFieldName(toPathBody(tree))
    q"$fieldName"
  }

  // Helper to extract full path string from selector
  private def extractPath(c: whitebox.Context)(tree: c.Tree): c.Tree = {
    import c.universe._

    def toPathBody(tree: c.Tree): c.Tree = tree match {
      case q"($_) => $pathBody" => pathBody
      case _                    => CommonMacroOps.fail(c)(s"Expected a lambda expression, got '$tree'")
    }

    def extractPathParts(tree: c.Tree): List[String] = tree match {
      case q"$parent.$child" =>
        extractPathParts(parent) :+ scala.reflect.NameTransformer.decode(child.toString)
      case _: Ident =>
        Nil
      case _ =>
        CommonMacroOps.fail(c)(s"Unsupported selector expression: $tree")
    }

    val parts   = extractPathParts(toPathBody(tree))
    val pathStr = parts.mkString(".")
    q"$pathStr"
  }
}
