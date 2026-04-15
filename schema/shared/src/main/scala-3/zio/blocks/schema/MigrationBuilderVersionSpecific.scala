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

import scala.quoted.*

/**
 * Scala 3 selector-based API for MigrationBuilder. Mixes in inline methods that
 * accept selector lambdas (e.g. `_.name`) and convert them to DynamicOptic at
 * compile time via MigrationMacros.selectorToOpticImpl.
 *
 * `opticOf` is the single-splice macro helper; all other methods are plain
 * inline methods that delegate to the DynamicOptic-based overloads defined in
 * MigrationBuilder once the selector has been materialised.
 */
trait MigrationBuilderVersionSpecific[A, B, State <: BuilderState] {
  this: MigrationBuilder[A, B, State] =>

  /** Materialises a selector lambda into a DynamicOptic at compile time. */
  inline def opticOf[X](inline path: X => Any): DynamicOptic =
    ${ MigrationMacros.selectorToOpticImpl('path) }

  inline def renameField[C, D](inline from: A => C, inline to: B => D): MigrationBuilder[A, B, State] =
    renameField(opticOf(from), opticOf(to))

  inline def addField[C](inline at: B => C, value: MigrationExpr): MigrationBuilder[A, B, State] =
    addField(opticOf(at), value)

  inline def dropField[C](
    inline at: A => C,
    defaultForReverse: MigrationExpr
  ): MigrationBuilder[A, B, State] =
    dropField(opticOf(at), defaultForReverse)

  inline def transformValue[C](
    inline at: A => C,
    transform: MigrationExpr,
    inverseTransform: MigrationExpr
  ): MigrationBuilder[A, B, State] =
    transformValue(opticOf(at), transform, inverseTransform)

  inline def optionalize[C](
    inline at: A => C,
    defaultForReverse: MigrationExpr
  ): MigrationBuilder[A, B, State] =
    optionalize(opticOf(at), defaultForReverse)

  inline def mandate[C](inline at: A => C): MigrationBuilder[A, B, State] =
    mandate(opticOf(at))

  inline def changeFieldType[C](
    inline at: A => C,
    converter: MigrationExpr,
    inverseConverter: MigrationExpr
  ): MigrationBuilder[A, B, State] =
    changeFieldType(opticOf(at), converter, inverseConverter)
}
