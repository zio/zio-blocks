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

/**
 * SchemaExpr is a pure, serializable expression for value-level transformations.
 *
 * This is an alias for ResolvedExpr, matching the naming convention from the
 * migration system specification.
 */
object SchemaExpr {

  /**
   * Type alias for the main expression type.
   */
  type Expr = ResolvedExpr

  // Re-export all ResolvedExpr constructors and values
  val Literal: ResolvedExpr.Literal.type         = ResolvedExpr.Literal
  val Identity: ResolvedExpr.Identity.type       = ResolvedExpr.Identity
  val FieldAccess: ResolvedExpr.FieldAccess.type = ResolvedExpr.FieldAccess
  val PathAccess: ResolvedExpr.PathAccess.type   = ResolvedExpr.PathAccess
  val Convert: ResolvedExpr.Convert.type         = ResolvedExpr.Convert
  val Concat: ResolvedExpr.Concat.type           = ResolvedExpr.Concat
  val DefaultValue: ResolvedExpr.DefaultValue.type   = ResolvedExpr.DefaultValue
  val IfThenElse: ResolvedExpr.IfThenElse.type   = ResolvedExpr.IfThenElse
  val WrapSome: ResolvedExpr.WrapSome.type       = ResolvedExpr.WrapSome
  val UnwrapSome: ResolvedExpr.UnwrapSome.type   = ResolvedExpr.UnwrapSome
  val GetNone: ResolvedExpr.GetNone.type         = ResolvedExpr.GetNone

  // Smart constructors
  def literal[A](value: A)(implicit toDV: A => zio.blocks.schema.DynamicValue): Expr =
    ResolvedExpr.literal(value)

  def literalDynamic(value: zio.blocks.schema.DynamicValue): Expr =
    ResolvedExpr.literalDynamic(value)

  def identity: Expr = ResolvedExpr.identity

  def field(name: String): Expr = ResolvedExpr.field(name)

  def path(optic: zio.blocks.schema.DynamicOptic): Expr = ResolvedExpr.path(optic)

  def convert(fromType: String, toType: String): Expr = ResolvedExpr.convert(fromType, toType)

  def concat(exprs: Expr*): Expr = ResolvedExpr.Concat(exprs.toVector, "")

  def concatWith(separator: String)(exprs: Expr*): Expr = ResolvedExpr.Concat(exprs.toVector, separator)

  def defaultValue: Expr = ResolvedExpr.defaultValue

  def ifThenElse(condition: Expr, thenExpr: Expr, elseExpr: Expr): Expr =
    ResolvedExpr.ifThenElse(condition, thenExpr, elseExpr)

  def wrapSome(inner: Expr): Expr = ResolvedExpr.wrapSome(inner)

  def unwrapSome(inner: Expr): Expr = ResolvedExpr.unwrapSome(inner)

  def none: Expr = ResolvedExpr.none
}
