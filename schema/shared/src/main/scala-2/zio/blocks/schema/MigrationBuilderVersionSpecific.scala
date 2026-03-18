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

/**
 * Scala 2 selector-based API for MigrationBuilder. Mixes in methods that accept
 * selector lambdas (e.g. `_.name`) and convert them to DynamicOptic at compile
 * time via blackbox macros.
 *
 * Each method here overloads the corresponding DynamicOptic-based method
 * defined in MigrationBuilder itself. Overload resolution picks the selector
 * variant when the argument is a lambda and the optic variant when a
 * DynamicOptic is supplied directly.
 */
trait MigrationBuilderVersionSpecific[A, B, State <: BuilderState] {
  this: MigrationBuilder[A, B, State] =>

  def renameField[C, D](from: A => C, to: B => D): MigrationBuilder[A, B, State] =
    macro MigrationMacros.renameFieldImpl

  def addField[C](at: B => C, value: MigrationExpr): MigrationBuilder[A, B, State] =
    macro MigrationMacros.addFieldImpl

  def dropField[C](at: A => C, defaultForReverse: MigrationExpr): MigrationBuilder[A, B, State] =
    macro MigrationMacros.dropFieldImpl

  def transformValue[C](
    at: A => C,
    transform: MigrationExpr,
    inverseTransform: MigrationExpr
  ): MigrationBuilder[A, B, State] = macro MigrationMacros.transformValueImpl

  def optionalize[C](at: A => C, defaultForReverse: MigrationExpr): MigrationBuilder[A, B, State] =
    macro MigrationMacros.optionalizeImpl

  def mandate[C](at: A => C): MigrationBuilder[A, B, State] =
    macro MigrationMacros.mandateImpl

  def changeFieldType[C](
    at: A => C,
    converter: MigrationExpr,
    inverseConverter: MigrationExpr
  ): MigrationBuilder[A, B, State] = macro MigrationMacros.changeFieldTypeImpl
}
