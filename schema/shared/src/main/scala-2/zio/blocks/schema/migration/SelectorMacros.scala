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

import zio.blocks.schema.{CommonMacroOps, DynamicOptic}

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/**
 * Macros for converting selector expressions to DynamicOptic.
 *
 * Selector expressions like `_.name`, `_.address.street`, `_.items.each` are
 * converted to DynamicOptic at compile time.
 */
object SelectorMacros {

  /**
   * Convert a selector expression to DynamicOptic.
   */
  def toDynamicOptic[S, A](path: S => A): DynamicOptic = macro toDynamicOpticImpl[S, A]

  def toDynamicOpticImpl[S, A](c: whitebox.Context)(path: c.Expr[S => A]): c.Tree = {
    import c.universe._

    def fail(msg: String): Nothing = CommonMacroOps.fail(c)(msg)

    def toPathBody(tree: c.Tree): c.Tree = tree match {
      case q"($_) => $pathBody" => pathBody
      case _                    => fail(s"Expected a lambda expression, got '$tree'")
    }

    def toOptic(tree: c.Tree): c.Tree = tree match {
      // Handle .each for sequences
      case q"$_[..$_]($parent).each" =>
        val parentOptic = toOptic(parent)
        q"$parentOptic.elements"

      // Handle .eachKey for maps
      case q"$_[..$_]($parent).eachKey" =>
        val parentOptic = toOptic(parent)
        q"$parentOptic.mapKeys"

      // Handle .eachValue for maps
      case q"$_[..$_]($parent).eachValue" =>
        val parentOptic = toOptic(parent)
        q"$parentOptic.mapValues"

      // Handle .when[Case]
      case q"$_[..$_]($parent).when[$caseTree]" =>
        val caseName    = caseTree.tpe.dealias.typeSymbol.name.toString
        val parentOptic = toOptic(parent)
        q"$parentOptic.caseOf($caseName)"

      // Handle .wrapped[T]
      case q"$_[..$_]($parent).wrapped[$_]" =>
        val parentOptic = toOptic(parent)
        q"$parentOptic.wrapped"

      // Handle .at(index)
      case q"$_[..$_]($parent).at(..$args)" if args.size == 1 && args.head.tpe.widen.dealias <:< definitions.IntTpe =>
        val parentOptic = toOptic(parent)
        q"$parentOptic.at(${args.head})"

      // Handle .atKey(key)
      case q"$_[..$_]($parent).atKey(..$args)" if args.size == 1 =>
        val parentOptic = toOptic(parent)
        // Simplified - only handle string keys
        q"$parentOptic.field(${args.head}.toString)"

      // Handle field access _.foo.bar
      case q"$parent.$child" =>
        val fieldName   = scala.reflect.NameTransformer.decode(child.toString)
        val parentOptic = toOptic(parent)
        q"$parentOptic.field($fieldName)"

      // Handle the root identifier (the _ in _.foo)
      case _: Ident =>
        q"_root_.zio.blocks.schema.DynamicOptic.root"

      case _ =>
        fail(
          s"Expected path elements: .<field>, .when[<T>], .at(<index>), .each, .eachKey, .eachValue, or .wrapped[<T>], got '$tree'."
        )
    }

    toOptic(toPathBody(path.tree))
  }

  /**
   * Extract the field name from the last segment of a selector.
   */
  def extractFieldName[S, A](path: S => A): String = macro extractFieldNameImpl[S, A]

  def extractFieldNameImpl[S, A](c: whitebox.Context)(path: c.Expr[S => A]): c.Tree = {
    import c.universe._

    def fail(msg: String): Nothing = CommonMacroOps.fail(c)(msg)

    def toPathBody(tree: c.Tree): c.Tree = tree match {
      case q"($_) => $pathBody" => pathBody
      case _                    => fail(s"Expected a lambda expression, got '$tree'")
    }

    def getLastFieldName(tree: c.Tree): String = tree match {
      case q"$_.$child" => scala.reflect.NameTransformer.decode(child.toString)
      case _            => fail(s"Expected field access, got '$tree'")
    }

    val fieldName = getLastFieldName(toPathBody(path.tree))
    q"$fieldName"
  }

  /**
   * Extract the full path string from a selector expression.
   *
   * Example: `_.address.street` => "address.street"
   */
  def extractPath[S, A](path: S => A): String = macro extractPathImpl[S, A]

  def extractPathImpl[S, A](c: whitebox.Context)(path: c.Expr[S => A]): c.Tree = {
    import c.universe._

    def fail(msg: String): Nothing = CommonMacroOps.fail(c)(msg)

    def toPathBody(tree: c.Tree): c.Tree = tree match {
      case q"($_) => $pathBody" => pathBody
      case _                    => fail(s"Expected a lambda expression, got '$tree'")
    }

    def extractPathParts(tree: c.Tree): List[String] = tree match {
      case q"$parent.$child" =>
        extractPathParts(parent) :+ scala.reflect.NameTransformer.decode(child.toString)
      case _: Ident =>
        Nil // Root, no path parts
      case _ =>
        fail(s"Unsupported selector expression: $tree")
    }

    val parts   = extractPathParts(toPathBody(path.tree))
    val pathStr = parts.mkString(".")
    q"$pathStr"
  }
}
