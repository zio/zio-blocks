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

package zio.blocks.schema

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

trait MigrationBuilderVersionSpecific[A, B] {
  self: MigrationBuilder[A, B] =>

  def addField(target: B => Any, default: SchemaExpr[DynamicValue, Any]): MigrationBuilder[A, B] = macro MigrationBuilderMacros.addFieldImpl[A, B]

  def dropField(source: A => Any): MigrationBuilder[A, B] = macro MigrationBuilderMacros.dropFieldImpl[A, B]

  def renameField(from: A => Any, to: B => Any): MigrationBuilder[A, B] = macro MigrationBuilderMacros.renameFieldImpl[A, B]
}

object MigrationBuilderMacros {
  def addFieldImpl[A, B](c: whitebox.Context)(target: c.Tree, default: c.Tree)(implicit tagA: c.WeakTypeTag[A], tagB: c.WeakTypeTag[B]): c.Tree = {
    import c.universe._
    val fieldName = extractFieldName(c)(target)
    q"new MigrationBuilder(${c.prefix}.sourceSchema, ${c.prefix}.targetSchema, ${c.prefix}.actions :+ MigrationAction.AddField(DynamicOptic.root.field($fieldName), $default))"
  }

  def dropFieldImpl[A, B](c: whitebox.Context)(source: c.Tree)(implicit tagA: c.WeakTypeTag[A], tagB: c.WeakTypeTag[B]): c.Tree = {
    import c.universe._
    val fieldName = extractFieldName(c)(source)
    q"new MigrationBuilder(${c.prefix}.sourceSchema, ${c.prefix}.targetSchema, ${c.prefix}.actions :+ MigrationAction.DropField(DynamicOptic.root.field($fieldName), SchemaExpr.Literal(DynamicValue.Null, Schema.dynamic)))"
  }

  def renameFieldImpl[A, B](c: whitebox.Context)(from: c.Tree, to: c.Tree)(implicit tagA: c.WeakTypeTag[A], tagB: c.WeakTypeTag[B]): c.Tree = {
    import c.universe._
    val fromName = extractFieldName(c)(from)
    val toName = extractFieldName(c)(to)
    q"new MigrationBuilder(${c.prefix}.sourceSchema, ${c.prefix}.targetSchema, ${c.prefix}.actions :+ MigrationAction.RenameField(DynamicOptic.root.field($fromName), $toName))"
  }

  private def extractFieldName(c: whitebox.Context)(tree: c.Tree): String = {
    import c.universe._
    
    def unblock(t: Tree): Tree = t match {
      case Block(Nil, expr) => unblock(expr)
      case _ => t
    }

    unblock(tree) match {
      case Function(_, Select(_, field)) => field.decodedName.toString
      case Function(_, Block(_, Select(_, field))) => field.decodedName.toString
      case _ => c.abort(c.enclosingPosition, s"Could not extract field name from selector: $tree")
    }
  }
}
