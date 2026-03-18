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

import scala.reflect.NameTransformer
import scala.reflect.macros.blackbox

object MigrationMacros {

  /**
   * Converts a selector lambda like `_.field` or `_.a.b` into a DynamicOptic
   * tree by walking the lambda body and collecting each field-select step.
   */
  def selectorBodyToOptic(c: blackbox.Context)(selectorTree: c.Tree): c.Tree = {
    import c.universe._

    def extractFields(tree: c.Tree): List[String] = tree match {
      case q"$parent.$field" => extractFields(parent) :+ NameTransformer.decode(field.toString)
      case _: Ident          => Nil
      case _                 =>
        c.abort(
          c.enclosingPosition,
          s"Migration selector must be a chain of field accesses (e.g. _.field or _.a.b), got '$tree'"
        )
    }

    val body = selectorTree match {
      case Function(_, body) => body
      case _                 => c.abort(c.enclosingPosition, s"Expected a lambda expression, got '$selectorTree'")
    }

    val fields = extractFields(body)
    fields.foldLeft[c.Tree](q"_root_.zio.blocks.schema.DynamicOptic.root") { (acc, name) =>
      q"$acc.field($name)"
    }
  }

  def renameFieldImpl(c: blackbox.Context)(from: c.Expr[Any], to: c.Expr[Any]): c.Tree = {
    import c.universe._
    val fromOptic = selectorBodyToOptic(c)(from.tree)
    val toOptic   = selectorBodyToOptic(c)(to.tree)
    q"${c.prefix}.renameField($fromOptic, $toOptic)"
  }

  def addFieldImpl(c: blackbox.Context)(at: c.Expr[Any], value: c.Expr[Any]): c.Tree = {
    import c.universe._
    val atOptic = selectorBodyToOptic(c)(at.tree)
    q"${c.prefix}.addField($atOptic, $value)"
  }

  def dropFieldImpl(c: blackbox.Context)(at: c.Expr[Any], defaultForReverse: c.Expr[Any]): c.Tree = {
    import c.universe._
    val atOptic = selectorBodyToOptic(c)(at.tree)
    q"${c.prefix}.dropField($atOptic, $defaultForReverse)"
  }

  def transformValueImpl(
    c: blackbox.Context
  )(at: c.Expr[Any], transform: c.Expr[Any], inverseTransform: c.Expr[Any]): c.Tree = {
    import c.universe._
    val atOptic = selectorBodyToOptic(c)(at.tree)
    q"${c.prefix}.transformValue($atOptic, $transform, $inverseTransform)"
  }

  def optionalizeImpl(c: blackbox.Context)(at: c.Expr[Any], defaultForReverse: c.Expr[Any]): c.Tree = {
    import c.universe._
    val atOptic = selectorBodyToOptic(c)(at.tree)
    q"${c.prefix}.optionalize($atOptic, $defaultForReverse)"
  }

  def mandateImpl(c: blackbox.Context)(at: c.Expr[Any]): c.Tree = {
    import c.universe._
    val atOptic = selectorBodyToOptic(c)(at.tree)
    q"${c.prefix}.mandate($atOptic)"
  }

  def changeFieldTypeImpl(
    c: blackbox.Context
  )(at: c.Expr[Any], converter: c.Expr[Any], inverseConverter: c.Expr[Any]): c.Tree = {
    import c.universe._
    val atOptic = selectorBodyToOptic(c)(at.tree)
    q"${c.prefix}.changeFieldType($atOptic, $converter, $inverseConverter)"
  }
}
