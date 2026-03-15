/*
  * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *   http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */

package zio.blocks.schema

import scala.quoted.*

trait MigrationBuilderVersionSpecific[A, B] {
  self: MigrationBuilder[A, B] =>

  inline def addField(inline target: B => Any, default: SchemaExpr[DynamicValue, ?]): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.addFieldImpl[A, B]('this, 'target, 'default) }

  inline def dropField(inline source: A => Any): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.dropFieldImpl[A, B]('this, 'source) }

  inline def renameField(inline from: A => Any, inline to: B => Any): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.renameFieldImpl[A, B]('this, 'from, 'to) }
}

object MigrationBuilderMacros {
  def addFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    target: Expr[B => Any],
    default: Expr[SchemaExpr[DynamicValue, ?]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val fieldName = extractFieldName(target)
    '{
      new MigrationBuilder(
        ${builder}.sourceSchema,
        ${builder}.targetSchema,
        ${builder}.actions :+ MigrationAction.AddField(DynamicOptic.root.field(${Expr(fieldName)}), $default)
      )
    }
  }

  def dropFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    source: Expr[A => Any]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val fieldName = extractFieldName(source)
    '{
      new MigrationBuilder(
        ${builder}.sourceSchema,
        ${builder}.targetSchema,
        ${builder}.actions :+ MigrationAction.DropField(DynamicOptic.root.field(${Expr(fieldName)}), SchemaExpr.Literal(DynamicValue.Null, Schema.dynamic))
      )
    }
  }

  def renameFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    from: Expr[A => Any],
    to: Expr[B => Any]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val fromName = extractFieldName(from)
    val toName = extractFieldName(to)
    '{
      new MigrationBuilder(
        ${builder}.sourceSchema,
        ${builder}.targetSchema,
        ${builder}.actions :+ MigrationAction.RenameField(DynamicOptic.root.field(${Expr(fromName)}), ${Expr(toName)})
      )
    }
  }

  private def extractFieldName(expr: Expr[? => Any])(using Quotes): String = {
    import quotes.reflect.*
    expr.asTerm match {
      case Inlined(_, _, Lambda(_, Select(_, name))) => name
      case Inlined(_, _, Lambda(_, Block(List(), Select(_, name)))) => name
      case _ => report.errorAndAbort(s"Could not extract field name from selector: ${expr.show}")
    }
  }
}
