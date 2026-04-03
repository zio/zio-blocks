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

/**
 * Integration between SchemaExpr and DynamicTransform for migrations.
 *
 * SchemaExpr provides a rich expression language for value transformations that
 * can be used within migrations. This module provides utilities to convert
 * SchemaExpr to DynamicTransform for use in migration actions.
 */
object SchemaExprTransform {

  /**
   * Creates a DynamicTransform that always returns the specified literal value.
   */
  def literal[A](value: A)(implicit schema: Schema[A]): DynamicTransform =
    DynamicTransform.Constant(schema.toDynamicValue(value))

  /**
   * Creates a DynamicTransform that returns the default value for a type.
   *
   * Uses DefaultValue transform which requires schema context at evaluation
   * time.
   */
  def defaultValue[A](implicit schema: Schema[A]): DynamicTransform =
    DynamicTransform.DefaultValue

  /**
   * Converts a typed Transform[A, B] to a DynamicTransform.
   */
  def fromTransform[A, B](transform: Transform[A, B]): DynamicTransform =
    transform.toDynamic

  /**
   * Creates a SchemaExpr that represents a constant value.
   */
  def constantExpr[S, A](value: A)(implicit schema: Schema[A]): SchemaExpr[S, A] =
    SchemaExpr.Literal(value, schema)

  /**
   * Creates a SchemaExpr that accesses a field via an Optic.
   */
  def accessExpr[A, B](optic: Optic[A, B]): SchemaExpr[A, B] =
    SchemaExpr.Optic(optic)

  /**
   * Evaluates a SchemaExpr on a DynamicValue.
   *
   * This is useful for applying SchemaExpr transformations during migrations.
   */
  def evalDynamic[A, B](
    expr: SchemaExpr[A, B],
    input: DynamicValue,
    schema: Schema[A]
  ): Either[MigrationError, DynamicValue] =
    schema.fromDynamicValue(input) match {
      case Right(a) =>
        expr.evalDynamic(a) match {
          case Right(values) =>
            if (values.isEmpty) Left(MigrationError.notFound("Expression produced no results"))
            else Right(values.head)
          case Left(opticCheck) =>
            Left(MigrationError.transformFailed(opticCheck.toString))
        }
      case Left(schemaError) =>
        Left(MigrationError.fromSchemaError(schemaError))
    }
}
