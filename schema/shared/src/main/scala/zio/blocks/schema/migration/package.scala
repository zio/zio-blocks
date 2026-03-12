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

package object migration {

  def literal[A](value: A)(implicit schema: Schema[A]): SchemaExpr.Literal[Any, A] =
    SchemaExpr.Literal(value, schema.asInstanceOf[Schema[A]])

  def dynamicLiteral[A](value: A)(implicit schema: Schema[A]): DynamicSchemaExpr =
    DynamicSchemaExpr.Literal(schema.toDynamicValue(value))

  implicit class SchemaExprOps[A, B](private val expr: SchemaExpr[A, B]) extends AnyVal {
    def toDynamic: DynamicSchemaExpr = expr match {
      case l: SchemaExpr.Literal[_, _] =>
        DynamicSchemaExpr.Literal(l.schema.asInstanceOf[Schema[Any]].toDynamicValue(l.value))
      case _ =>
        throw new IllegalArgumentException(
          "Only SchemaExpr.Literal can be converted to DynamicSchemaExpr"
        )
    }
  }
}
