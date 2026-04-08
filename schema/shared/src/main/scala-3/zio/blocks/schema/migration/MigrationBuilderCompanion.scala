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

/**
 * Scala 3 macro DSL extension methods for [[MigrationBuilder]].
 *
 * Each method accepts a selector lambda (`inline path: A => ?`) that is
 * expanded at compile time into a [[DynamicOptic]] by
 * [[MigrationMacros.selectorToDynamicOptic]], then delegates to the
 * corresponding `*At` method on [[MigrationBuilder]].
 *
 * The selector lambdas support the full set of path operations defined by the
 * [[zio.blocks.schema.CompanionOptics]] DSL: `_.field`, `.when[T]`, `.each`,
 * `.eachKey`, `.eachValue`, `.wrapped[T]`, `.at(i)`, `.atIndices(i*)`,
 * `.atKey(k)`, `.atKeys(k*)`, `.searchFor[T]`.
 *
 * ==Example==
 * {{{
 * MigrationBuilder[UserV1, UserV2](v1Schema, v2Schema)
 *   .renameField(_.name, "fullName")
 *   .dropField(_.legacyId)
 *   .addField(_.age, ValueExpr.DefaultValue)
 *   .build
 * }}}
 */
object MigrationBuilderCompanion {
  import scala.quoted._
  import scala.compiletime.error

  extension [A](a: A) {
    inline def when[B <: A]: B = error("Can only be used inside migration selector macros")

    inline def wrapped[B]: B = error("Can only be used inside migration selector macros")

    inline def searchFor[B]: B = error("Can only be used inside migration selector macros")
  }

  extension [C[_], A](c: C[A]) {
    inline def at(index: Int): A = error("Can only be used inside migration selector macros")

    inline def atIndices(indices: Int*): A = error("Can only be used inside migration selector macros")

    inline def each: A = error("Can only be used inside migration selector macros")
  }

  extension [M[_, _], K, V](m: M[K, V]) {
    inline def atKey(key: K): V = error("Can only be used inside migration selector macros")

    inline def atKeys(keys: K*): V = error("Can only be used inside migration selector macros")

    inline def eachKey: K = error("Can only be used inside migration selector macros")

    inline def eachValue: V = error("Can only be used inside migration selector macros")
  }

  extension [A, B](builder: MigrationBuilder[A, B]) {

    /**
     * Adds a new field at the path described by `selector`, computing its
     * initial value via `defaultValue`.
     *
     * The selector must address a field that does not yet exist in `A` but
     * exists in `B`. The final path segment becomes the new field name.
     */
    transparent inline def addField[C](
      inline selector: B => C,
      inline defaultValue: ValueExpr
    ): MigrationBuilder[A, B] =
      ${ MigrationBuilderCompanionMacros.addFieldImpl[A, B, C]('builder, 'selector, 'defaultValue) }

    /**
     * Removes the field at the path described by `selector` from the source
     * record.
     */
    transparent inline def dropField[C](inline selector: A => C): MigrationBuilder[A, B] =
      ${ MigrationBuilderCompanionMacros.dropFieldImpl[A, B, C]('builder, 'selector) }

    /**
     * Renames the field addressed by `selector` to `newName` in place,
     * preserving the field's value and position.
     *
     * The selector must end in a field access on `A` (the source type). The
     * `newName` is the field name as it will appear in `B`.
     */
    transparent inline def renameField[C](inline selector: A => C, inline newName: String): MigrationBuilder[A, B] =
      ${ MigrationBuilderCompanionMacros.renameFieldImpl[A, B, C]('builder, 'selector, 'newName) }

    /**
     * Transforms the value at the path described by `selector` using `expr`.
     *
     * Suitable for in-place value changes that do not alter structural position
     * (e.g. [[ValueExpr.PrimitiveConvert]], [[ValueExpr.Constant]]).
     */
    transparent inline def transformValue[C](inline selector: A => C, inline expr: ValueExpr): MigrationBuilder[A, B] =
      ${ MigrationBuilderCompanionMacros.transformValueImpl[A, B, C]('builder, 'selector, 'expr) }

    /**
     * Mandates an optional field at the path described by `selector`,
     * unwrapping it from its `Some`/`None` encoding.
     *
     * If the field is `None`, `defaultExpr` is evaluated to supply the required
     * value.
     */
    transparent inline def mandate[C](inline selector: A => C, inline defaultExpr: ValueExpr): MigrationBuilder[A, B] =
      ${ MigrationBuilderCompanionMacros.mandateImpl[A, B, C]('builder, 'selector, 'defaultExpr) }

    /**
     * Optionalizes a required field at the path described by `selector`,
     * wrapping its current value in a `Some` variant.
     */
    transparent inline def optionalize[C](inline selector: A => C): MigrationBuilder[A, B] =
      ${ MigrationBuilderCompanionMacros.optionalizeImpl[A, B, C]('builder, 'selector) }

    /**
     * Changes the primitive type of the value at the path described by
     * `selector` using `expr`.
     */
    transparent inline def changeType[C](
      inline selector: A => C,
      inline expr: ValueExpr.PrimitiveConvert
    ): MigrationBuilder[A, B] =
      ${ MigrationBuilderCompanionMacros.changeTypeImpl[A, B, C]('builder, 'selector, 'expr) }

    /**
     * Combines the values at `leftSelector` and `rightSelector` into a single
     * value written to `targetSelector`, using `combiner`.
     *
     * Both source fields are read before either is removed.
     */
    transparent inline def join[L, R, T](
      inline leftSelector: A => L,
      inline rightSelector: A => R,
      inline targetSelector: B => T,
      inline combiner: ValueExpr
    ): MigrationBuilder[A, B] =
      ${
        MigrationBuilderCompanionMacros.joinImpl[A, B, L, R, T](
          'builder,
          'leftSelector,
          'rightSelector,
          'targetSelector,
          'combiner
        )
      }

    /**
     * Splits the value at `fromSelector` into two values written to
     * `toLeftSelector` and `toRightSelector`, using `splitter`.
     */
    transparent inline def split[F, L, R](
      inline fromSelector: A => F,
      inline toLeftSelector: B => L,
      inline toRightSelector: B => R,
      inline splitter: ValueExpr
    ): MigrationBuilder[A, B] =
      ${
        MigrationBuilderCompanionMacros.splitImpl[A, B, F, L, R](
          'builder,
          'fromSelector,
          'toLeftSelector,
          'toRightSelector,
          'splitter
        )
      }

    /**
     * Applies `expr` to every element of the sequence at the path described by
     * `selector`.
     */
    transparent inline def transformElements[C](
      inline selector: A => C,
      inline expr: ValueExpr
    ): MigrationBuilder[A, B] =
      ${ MigrationBuilderCompanionMacros.transformElementsImpl[A, B, C]('builder, 'selector, 'expr) }

    /**
     * Applies `expr` to every key of the map at the path described by
     * `selector`.
     */
    transparent inline def transformKeys[C](inline selector: A => C, inline expr: ValueExpr): MigrationBuilder[A, B] =
      ${ MigrationBuilderCompanionMacros.transformKeysImpl[A, B, C]('builder, 'selector, 'expr) }

    /**
     * Applies `expr` to every value of the map at the path described by
     * `selector`.
     */
    transparent inline def transformValues[C](inline selector: A => C, inline expr: ValueExpr): MigrationBuilder[A, B] =
      ${ MigrationBuilderCompanionMacros.transformValuesImpl[A, B, C]('builder, 'selector, 'expr) }

    /**
     * Applies a nested [[Migration]] to the value at the path described by
     * `selector`.
     *
     * The value at the selector path is extracted, passed through `migration`,
     * and the result is written back. Enables composing typed migrations for
     * nested types within a larger record migration.
     *
     * ==Example==
     * {{{
     * val addressMigration: Migration[AddressV1, AddressV2] = ...
     * MigrationBuilder[PersonV1, PersonV2](v1Schema, v2Schema)
     *   .migrateField(_.address, addressMigration)
     *   .build
     * }}}
     */
    transparent inline def migrateField[C, D](
      inline selector: A => C,
      migration: Migration[C, D]
    ): MigrationBuilder[A, B] =
      ${ MigrationBuilderCompanionMacros.migrateFieldImpl[A, B, C, D]('builder, 'selector, 'migration) }

    /**
     * Copies the value at the path described by `fromSelector` and inserts it
     * at the path described by `toSelector`, leaving the source field intact.
     *
     * Useful when a field needs to appear in two places during a schema
     * transition (e.g. rolling deployments) or when duplicating a value into
     * a newly-introduced location.
     */
    transparent inline def copyField[C](
      inline fromSelector: A => C,
      inline toSelector: B => C
    ): MigrationBuilder[A, B] =
      ${ MigrationBuilderCompanionMacros.copyFieldImpl[A, B, C]('builder, 'fromSelector, 'toSelector) }

    /**
     * Moves the value at the path described by `fromSelector` to the path
     * described by `toSelector`, removing the source field.
     *
     * Use `moveField` instead of `renameField` when the target field occupies
     * a different structural location (different parent record), not just a
     * different name at the same level.
     */
    transparent inline def moveField[C](
      inline fromSelector: A => C,
      inline toSelector: B => C
    ): MigrationBuilder[A, B] =
      ${ MigrationBuilderCompanionMacros.moveFieldImpl[A, B, C]('builder, 'fromSelector, 'toSelector) }
  }
}

private[migration] object MigrationBuilderCompanionMacros {
  import scala.quoted._

  def addFieldImpl[A: Type, B: Type, C: Type](
    builder: Expr[MigrationBuilder[A, B]],
    selector: Expr[B => C],
    defaultValue: Expr[ValueExpr]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val path = MigrationMacros.selectorToDynamicOptic[B, C](selector)
    '{ $builder.addFieldAt($path, $defaultValue) }
  }

  def dropFieldImpl[A: Type, B: Type, C: Type](
    builder: Expr[MigrationBuilder[A, B]],
    selector: Expr[A => C]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val path = MigrationMacros.selectorToDynamicOptic[A, C](selector)
    '{ $builder.dropFieldAt($path) }
  }

  def renameFieldImpl[A: Type, B: Type, C: Type](
    builder: Expr[MigrationBuilder[A, B]],
    selector: Expr[A => C],
    newName: Expr[String]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val path = MigrationMacros.selectorToDynamicOptic[A, C](selector)
    '{ $builder.renameFieldAt($path, $newName) }
  }

  def transformValueImpl[A: Type, B: Type, C: Type](
    builder: Expr[MigrationBuilder[A, B]],
    selector: Expr[A => C],
    expr: Expr[ValueExpr]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val path = MigrationMacros.selectorToDynamicOptic[A, C](selector)
    '{ $builder.transformValueAt($path, $expr) }
  }

  def mandateImpl[A: Type, B: Type, C: Type](
    builder: Expr[MigrationBuilder[A, B]],
    selector: Expr[A => C],
    defaultExpr: Expr[ValueExpr]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val path = MigrationMacros.selectorToDynamicOptic[A, C](selector)
    '{ $builder.mandateAt($path, $defaultExpr) }
  }

  def optionalizeImpl[A: Type, B: Type, C: Type](
    builder: Expr[MigrationBuilder[A, B]],
    selector: Expr[A => C]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val path = MigrationMacros.selectorToDynamicOptic[A, C](selector)
    '{ $builder.optionalizeAt($path) }
  }

  def changeTypeImpl[A: Type, B: Type, C: Type](
    builder: Expr[MigrationBuilder[A, B]],
    selector: Expr[A => C],
    expr: Expr[ValueExpr.PrimitiveConvert]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val path = MigrationMacros.selectorToDynamicOptic[A, C](selector)
    '{ $builder.changeTypeAt($path, $expr) }
  }

  def joinImpl[A: Type, B: Type, L: Type, R: Type, T: Type](
    builder: Expr[MigrationBuilder[A, B]],
    leftSelector: Expr[A => L],
    rightSelector: Expr[A => R],
    targetSelector: Expr[B => T],
    combiner: Expr[ValueExpr]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val leftPath   = MigrationMacros.selectorToDynamicOptic[A, L](leftSelector)
    val rightPath  = MigrationMacros.selectorToDynamicOptic[A, R](rightSelector)
    val targetPath = MigrationMacros.selectorToDynamicOptic[B, T](targetSelector)
    '{ $builder.joinAt($leftPath, $rightPath, $targetPath, $combiner) }
  }

  def splitImpl[A: Type, B: Type, F: Type, L: Type, R: Type](
    builder: Expr[MigrationBuilder[A, B]],
    fromSelector: Expr[A => F],
    toLeftSelector: Expr[B => L],
    toRightSelector: Expr[B => R],
    splitter: Expr[ValueExpr]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val fromPath    = MigrationMacros.selectorToDynamicOptic[A, F](fromSelector)
    val toLeftPath  = MigrationMacros.selectorToDynamicOptic[B, L](toLeftSelector)
    val toRightPath = MigrationMacros.selectorToDynamicOptic[B, R](toRightSelector)
    '{ $builder.splitAt($fromPath, $toLeftPath, $toRightPath, $splitter) }
  }

  def transformElementsImpl[A: Type, B: Type, C: Type](
    builder: Expr[MigrationBuilder[A, B]],
    selector: Expr[A => C],
    expr: Expr[ValueExpr]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val path = MigrationMacros.selectorToDynamicOptic[A, C](selector)
    '{ $builder.transformElementsAt($path, $expr) }
  }

  def transformKeysImpl[A: Type, B: Type, C: Type](
    builder: Expr[MigrationBuilder[A, B]],
    selector: Expr[A => C],
    expr: Expr[ValueExpr]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val path = MigrationMacros.selectorToDynamicOptic[A, C](selector)
    '{ $builder.transformKeysAt($path, $expr) }
  }

  def transformValuesImpl[A: Type, B: Type, C: Type](
    builder: Expr[MigrationBuilder[A, B]],
    selector: Expr[A => C],
    expr: Expr[ValueExpr]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val path = MigrationMacros.selectorToDynamicOptic[A, C](selector)
    '{ $builder.transformValuesAt($path, $expr) }
  }

  def migrateFieldImpl[A: Type, B: Type, C: Type, D: Type](
    builder: Expr[MigrationBuilder[A, B]],
    selector: Expr[A => C],
    migration: Expr[Migration[C, D]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val path = MigrationMacros.selectorToDynamicOptic[A, C](selector)
    '{ $builder.migrateFieldAt($path, $migration.migration) }
  }

  def copyFieldImpl[A: Type, B: Type, C: Type](
    builder: Expr[MigrationBuilder[A, B]],
    fromSelector: Expr[A => C],
    toSelector: Expr[B => C]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val fromPath = MigrationMacros.selectorToDynamicOptic[A, C](fromSelector)
    val toPath   = MigrationMacros.selectorToDynamicOptic[B, C](toSelector)
    '{ $builder.copyFieldAt($fromPath, $toPath) }
  }

  def moveFieldImpl[A: Type, B: Type, C: Type](
    builder: Expr[MigrationBuilder[A, B]],
    fromSelector: Expr[A => C],
    toSelector: Expr[B => C]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val fromPath = MigrationMacros.selectorToDynamicOptic[A, C](fromSelector)
    val toPath   = MigrationMacros.selectorToDynamicOptic[B, C](toSelector)
    '{ $builder.moveFieldAt($fromPath, $toPath) }
  }
}
