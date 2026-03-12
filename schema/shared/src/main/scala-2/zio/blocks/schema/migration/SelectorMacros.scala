/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
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

import scala.annotation.nowarn
import scala.reflect.macros.whitebox

@nowarn("msg=is never used")
object SelectorMacros {

  sealed trait PathNode
  case class FieldNode(name: String) extends PathNode
  case object ElementsNode           extends PathNode
  case object MapKeysNode            extends PathNode
  case object MapValuesNode          extends PathNode
  case object WrappedNode            extends PathNode
  case class CaseNode(name: String)  extends PathNode
  case class AtIndexNode(index: Int) extends PathNode

  def extractPath(c: whitebox.Context)(selector: c.Tree): c.Tree = {
    import c.universe._

    def extractNodes(tree: c.Tree, acc: List[PathNode]): List[PathNode] =
      tree match {
        case Ident(_) =>
          acc
        case Function(_, body) =>
          extractNodes(body, acc)

        // _.field.each -> elements
        case Select(inner, TermName("each")) =>
          extractNodes(inner, ElementsNode :: acc)
        // _.field.eachKey -> mapKeys
        case Select(inner, TermName("eachKey")) =>
          extractNodes(inner, MapKeysNode :: acc)
        // _.field.eachValue -> mapValues
        case Select(inner, TermName("eachValue")) =>
          extractNodes(inner, MapValuesNode :: acc)

        // _.field.when[T] -> caseOf("T")
        case TypeApply(Select(inner, TermName("when")), List(typeTree)) =>
          val caseName = typeTree.tpe.typeSymbol.name.decodedName.toString
          extractNodes(inner, CaseNode(caseName) :: acc)
        // _.field.wrapped[T] -> wrapped
        case TypeApply(Select(inner, TermName("wrapped")), _) =>
          extractNodes(inner, WrappedNode :: acc)

        // _.seq.at(index)
        case Apply(Select(inner, TermName("at")), List(Literal(Constant(index: Int)))) =>
          extractNodes(inner, AtIndexNode(index) :: acc)

        // selectDynamic for structural types
        case Apply(Select(inner, TermName("selectDynamic")), List(Literal(Constant(name: String)))) =>
          extractNodes(inner, FieldNode(name) :: acc)

        // Regular field access
        case Select(inner, TermName(name)) if !name.startsWith("$") =>
          extractNodes(inner, FieldNode(name) :: acc)

        case _ =>
          c.abort(
            c.enclosingPosition,
            s"Unsupported selector expression. Use field access like _.field, _.items.each, _.status.when[T]"
          )
      }

    val nodes = extractNodes(selector, Nil)
    nodes.foldLeft(q"_root_.zio.blocks.schema.DynamicOptic.root": c.Tree) { (acc, node) =>
      node match {
        case FieldNode(name)    => q"$acc.field($name)"
        case ElementsNode       => q"$acc.apply(_root_.zio.blocks.schema.DynamicOptic.elements)"
        case MapKeysNode        => q"$acc.apply(_root_.zio.blocks.schema.DynamicOptic.mapKeys)"
        case MapValuesNode      => q"$acc.apply(_root_.zio.blocks.schema.DynamicOptic.mapValues)"
        case WrappedNode        => q"$acc.apply(_root_.zio.blocks.schema.DynamicOptic.wrapped)"
        case CaseNode(name)     => q"$acc.caseOf($name)"
        case AtIndexNode(index) => q"$acc.at($index)"
      }
    }
  }

  def addFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    target: c.Tree,
    default: c.Tree
  ): c.Tree = {
    import c.universe._
    val path = extractPath(c)(target)
    q"""
      new _root_.zio.blocks.schema.migration.MigrationBuilder(
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.AddField($path, $default.toDynamic)
      )
    """
  }

  def dropFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Tree,
    defaultForReverse: c.Tree
  ): c.Tree = {
    import c.universe._
    val path = extractPath(c)(source)
    q"""
      new _root_.zio.blocks.schema.migration.MigrationBuilder(
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.DropField($path, $defaultForReverse.toDynamic)
      )
    """
  }

  def renameFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    from: c.Tree,
    to: c.Tree
  ): c.Tree = {
    import c.universe._
    val fromPath = extractPath(c)(from)
    val toPath   = extractPath(c)(to)
    q"""
      {
        val _toPath = $toPath
        val _toName = _toPath.nodes.last match {
          case _root_.zio.blocks.schema.DynamicOptic.Node.Field(name) => name
          case _ => throw new IllegalArgumentException("Target selector must select a field")
        }
        new _root_.zio.blocks.schema.migration.MigrationBuilder(
          ${c.prefix}.sourceSchema,
          ${c.prefix}.targetSchema,
          ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.Rename($fromPath, _toName)
        )
      }
    """
  }

  def transformFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    from: c.Tree,
    to: c.Tree,
    transform: c.Tree
  ): c.Tree = {
    import c.universe._
    val fromPath = extractPath(c)(from)
    q"""
      new _root_.zio.blocks.schema.migration.MigrationBuilder(
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.TransformValue($fromPath, $transform.toDynamic)
      )
    """
  }

  def changeFieldTypeImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Tree,
    target: c.Tree,
    converter: c.Tree
  ): c.Tree = {
    import c.universe._
    val fromPath = extractPath(c)(source)
    q"""
      new _root_.zio.blocks.schema.migration.MigrationBuilder(
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.ChangeType($fromPath, $converter.toDynamic)
      )
    """
  }

  def mandateFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Tree,
    target: c.Tree,
    default: c.Tree
  ): c.Tree = {
    import c.universe._
    val path = extractPath(c)(source)
    q"""
      new _root_.zio.blocks.schema.migration.MigrationBuilder(
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.Mandate($path, $default.toDynamic)
      )
    """
  }

  def optionalizeFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Tree,
    target: c.Tree
  ): c.Tree = {
    import c.universe._
    val path = extractPath(c)(source)
    q"""
      new _root_.zio.blocks.schema.migration.MigrationBuilder(
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.Optionalize($path)
      )
    """
  }

  def transformElementsImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    at: c.Tree,
    transform: c.Tree
  ): c.Tree = {
    import c.universe._
    val path = extractPath(c)(at)
    q"""
      new _root_.zio.blocks.schema.migration.MigrationBuilder(
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.TransformElements($path, $transform.toDynamic)
      )
    """
  }

  def transformKeysImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    at: c.Tree,
    transform: c.Tree
  ): c.Tree = {
    import c.universe._
    val path = extractPath(c)(at)
    q"""
      new _root_.zio.blocks.schema.migration.MigrationBuilder(
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.TransformKeys($path, $transform.toDynamic)
      )
    """
  }

  def transformValuesImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: whitebox.Context)(
    at: c.Tree,
    transform: c.Tree
  ): c.Tree = {
    import c.universe._
    val path = extractPath(c)(at)
    q"""
      new _root_.zio.blocks.schema.migration.MigrationBuilder(
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.TransformValues($path, $transform.toDynamic)
      )
    """
  }

  def migrateFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, F: c.WeakTypeTag, G: c.WeakTypeTag](
    c: whitebox.Context
  )(
    selector: c.Tree,
    migration: c.Tree
  ): c.Tree = {
    import c.universe._
    val path = extractPath(c)(selector)
    q"""
      new _root_.zio.blocks.schema.migration.MigrationBuilder(
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.ApplyMigration($path, $migration.dynamicMigration)
      )
    """
  }
}
