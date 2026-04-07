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

import scala.language.experimental.macros

/**
 * Scala 2 macro DSL extension methods for [[MigrationBuilder]].
 *
 * Each method accepts a selector lambda that is expanded at compile time into a
 * [[zio.blocks.schema.DynamicOptic]] by
 * `MigrationMacros.selectorToDynamicOptic`, then delegates to the corresponding
 * `*At` method on [[MigrationBuilder]].
 */
object MigrationBuilderCompanion {
  import scala.annotation.compileTimeOnly

  implicit class ValueExtension[A](a: A) {
    @compileTimeOnly("Can only be used inside migration selector macros")
    def when[B <: A]: B = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def wrapped[B]: B = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def searchFor[B]: B = ???
  }

  implicit class SequenceExtension[C[_], A](c: C[A]) {
    @compileTimeOnly("Can only be used inside migration selector macros")
    def at(index: Int): A = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def atIndices(indices: Int*): A = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def each: A = ???
  }

  implicit class MapExtension[M[_, _], K, V](m: M[K, V]) {
    @compileTimeOnly("Can only be used inside migration selector macros")
    def atKey(key: K): V = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def atKeys(keys: K*): V = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def eachKey: K = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def eachValue: V = ???
  }

  implicit class MigrationBuilderOps[A, B](val builder: MigrationBuilder[A, B]) extends AnyVal {

    /**
     * Adds a new field at the path described by `selector` (typed against `B`),
     * computing its initial value via `defaultValue`.
     */
    def addField[C](selector: B => C, defaultValue: ValueExpr): MigrationBuilder[A, B] =
      macro MigrationBuilderCompanionMacros.addField[A, B, C]

    /**
     * Removes the field at the path described by `selector` from the source
     * record.
     */
    def dropField[C](selector: A => C): MigrationBuilder[A, B] =
      macro MigrationBuilderCompanionMacros.dropField[A, B, C]

    /**
     * Renames the field addressed by `selector` to `newName` in place,
     * preserving field value and position.
     */
    def renameField[C](selector: A => C, newName: String): MigrationBuilder[A, B] =
      macro MigrationBuilderCompanionMacros.renameField[A, B, C]

    /**
     * Transforms the value at the path described by `selector` using `expr`.
     */
    def transformValue[C](selector: A => C, expr: ValueExpr): MigrationBuilder[A, B] =
      macro MigrationBuilderCompanionMacros.transformValue[A, B, C]

    /**
     * Mandates an optional field at the path described by `selector`,
     * unwrapping it from its `Some`/`None` encoding.
     */
    def mandate[C](selector: A => C, defaultExpr: ValueExpr): MigrationBuilder[A, B] =
      macro MigrationBuilderCompanionMacros.mandate[A, B, C]

    /**
     * Optionalizes a required field at the path described by `selector`,
     * wrapping its current value in a `Some` variant.
     */
    def optionalize[C](selector: A => C): MigrationBuilder[A, B] =
      macro MigrationBuilderCompanionMacros.optionalize[A, B, C]

    /**
     * Changes the primitive type of the value at the path described by
     * `selector` using `expr`.
     */
    def changeType[C](selector: A => C, expr: ValueExpr.PrimitiveConvert): MigrationBuilder[A, B] =
      macro MigrationBuilderCompanionMacros.changeType[A, B, C]

    /**
     * Combines the values at `leftSelector` and `rightSelector` into a single
     * value written to `targetSelector`, using `combiner`.
     */
    def join[L, R, T](
      leftSelector: A => L,
      rightSelector: A => R,
      targetSelector: B => T,
      combiner: ValueExpr
    ): MigrationBuilder[A, B] =
      macro MigrationBuilderCompanionMacros.join[A, B, L, R, T]

    /**
     * Splits the value at `fromSelector` into two values written to
     * `toLeftSelector` and `toRightSelector`, using `splitter`.
     */
    def split[F, L, R](
      fromSelector: A => F,
      toLeftSelector: B => L,
      toRightSelector: B => R,
      splitter: ValueExpr
    ): MigrationBuilder[A, B] =
      macro MigrationBuilderCompanionMacros.split[A, B, F, L, R]

    /**
     * Applies `expr` to every element of the sequence at the path described by
     * `selector`.
     */
    def transformElements[C](selector: A => C, expr: ValueExpr): MigrationBuilder[A, B] =
      macro MigrationBuilderCompanionMacros.transformElements[A, B, C]

    /**
     * Applies `expr` to every key of the map at the path described by
     * `selector`.
     */
    def transformKeys[C](selector: A => C, expr: ValueExpr): MigrationBuilder[A, B] =
      macro MigrationBuilderCompanionMacros.transformKeys[A, B, C]

    /**
     * Applies `expr` to every value of the map at the path described by
     * `selector`.
     */
    def transformValues[C](selector: A => C, expr: ValueExpr): MigrationBuilder[A, B] =
      macro MigrationBuilderCompanionMacros.transformValues[A, B, C]
  }
}

/**
 * Whitebox macro implementations backing
 * [[MigrationBuilderCompanion.MigrationBuilderOps]].
 *
 * Each method converts its selector lambda to a
 * [[zio.blocks.schema.DynamicOptic]] via
 * [[MigrationMacros.selectorToDynamicOptic]] and delegates to the corresponding
 * `*At` method on [[MigrationBuilder]].
 */
private[migration] object MigrationBuilderCompanionMacros {
  import scala.reflect.macros.whitebox

  def addField[A, B, C](c: whitebox.Context)(
    selector: c.Expr[B => C],
    defaultValue: c.Expr[ValueExpr]
  ): c.Tree = {
    import c.universe._
    val path = MigrationMacros.selectorToDynamicOptic[B, C](c)(selector)
    q"${c.prefix}.builder.addFieldAt($path, $defaultValue)"
  }

  def dropField[A, B, C](c: whitebox.Context)(
    selector: c.Expr[A => C]
  ): c.Tree = {
    import c.universe._
    val path = MigrationMacros.selectorToDynamicOptic[A, C](c)(selector)
    q"${c.prefix}.builder.dropFieldAt($path)"
  }

  def renameField[A, B, C](c: whitebox.Context)(
    selector: c.Expr[A => C],
    newName: c.Expr[String]
  ): c.Tree = {
    import c.universe._
    val path = MigrationMacros.selectorToDynamicOptic[A, C](c)(selector)
    q"${c.prefix}.builder.renameFieldAt($path, $newName)"
  }

  def transformValue[A, B, C](c: whitebox.Context)(
    selector: c.Expr[A => C],
    expr: c.Expr[ValueExpr]
  ): c.Tree = {
    import c.universe._
    val path = MigrationMacros.selectorToDynamicOptic[A, C](c)(selector)
    q"${c.prefix}.builder.transformValueAt($path, $expr)"
  }

  def mandate[A, B, C](c: whitebox.Context)(
    selector: c.Expr[A => C],
    defaultExpr: c.Expr[ValueExpr]
  ): c.Tree = {
    import c.universe._
    val path = MigrationMacros.selectorToDynamicOptic[A, C](c)(selector)
    q"${c.prefix}.builder.mandateAt($path, $defaultExpr)"
  }

  def optionalize[A, B, C](c: whitebox.Context)(
    selector: c.Expr[A => C]
  ): c.Tree = {
    import c.universe._
    val path = MigrationMacros.selectorToDynamicOptic[A, C](c)(selector)
    q"${c.prefix}.builder.optionalizeAt($path)"
  }

  def changeType[A, B, C](c: whitebox.Context)(
    selector: c.Expr[A => C],
    expr: c.Expr[ValueExpr.PrimitiveConvert]
  ): c.Tree = {
    import c.universe._
    val path = MigrationMacros.selectorToDynamicOptic[A, C](c)(selector)
    q"${c.prefix}.builder.changeTypeAt($path, $expr)"
  }

  def join[A, B, L, R, T](c: whitebox.Context)(
    leftSelector: c.Expr[A => L],
    rightSelector: c.Expr[A => R],
    targetSelector: c.Expr[B => T],
    combiner: c.Expr[ValueExpr]
  ): c.Tree = {
    import c.universe._
    val leftPath   = MigrationMacros.selectorToDynamicOptic[A, L](c)(leftSelector)
    val rightPath  = MigrationMacros.selectorToDynamicOptic[A, R](c)(rightSelector)
    val targetPath = MigrationMacros.selectorToDynamicOptic[B, T](c)(targetSelector)
    q"${c.prefix}.builder.joinAt($leftPath, $rightPath, $targetPath, $combiner)"
  }

  def split[A, B, F, L, R](c: whitebox.Context)(
    fromSelector: c.Expr[A => F],
    toLeftSelector: c.Expr[B => L],
    toRightSelector: c.Expr[B => R],
    splitter: c.Expr[ValueExpr]
  ): c.Tree = {
    import c.universe._
    val fromPath    = MigrationMacros.selectorToDynamicOptic[A, F](c)(fromSelector)
    val toLeftPath  = MigrationMacros.selectorToDynamicOptic[B, L](c)(toLeftSelector)
    val toRightPath = MigrationMacros.selectorToDynamicOptic[B, R](c)(toRightSelector)
    q"${c.prefix}.builder.splitAt($fromPath, $toLeftPath, $toRightPath, $splitter)"
  }

  def transformElements[A, B, C](c: whitebox.Context)(
    selector: c.Expr[A => C],
    expr: c.Expr[ValueExpr]
  ): c.Tree = {
    import c.universe._
    val path = MigrationMacros.selectorToDynamicOptic[A, C](c)(selector)
    q"${c.prefix}.builder.transformElementsAt($path, $expr)"
  }

  def transformKeys[A, B, C](c: whitebox.Context)(
    selector: c.Expr[A => C],
    expr: c.Expr[ValueExpr]
  ): c.Tree = {
    import c.universe._
    val path = MigrationMacros.selectorToDynamicOptic[A, C](c)(selector)
    q"${c.prefix}.builder.transformKeysAt($path, $expr)"
  }

  def transformValues[A, B, C](c: whitebox.Context)(
    selector: c.Expr[A => C],
    expr: c.Expr[ValueExpr]
  ): c.Tree = {
    import c.universe._
    val path = MigrationMacros.selectorToDynamicOptic[A, C](c)(selector)
    q"${c.prefix}.builder.transformValuesAt($path, $expr)"
  }
}
