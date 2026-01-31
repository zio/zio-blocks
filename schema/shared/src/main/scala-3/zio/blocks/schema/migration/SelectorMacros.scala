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

import scala.quoted.*
import zio.blocks.schema.DynamicOptic

/**
 * Macros for creating type-safe field selectors from accessor expressions.
 *
 * These macros extract field names from expressions like `_.name` and
 * create FieldSelector instances with the name as a singleton string type.
 *
 * Example:
 * {{{
 * case class Person(name: String, age: Int)
 *
 * // The macro extracts "name" and creates FieldSelector[Person, String, "name"]
 * val selector = select[Person](_.name)
 * }}}
 */
object SelectorMacros {

  /**
   * Entry point for field selection.
   * Usage: `select[Person](_.name)`
   */
  inline def select[S]: SelectBuilder[S] = new SelectBuilder[S]

  /**
   * Builder class that enables the `select[S](_.field)` syntax.
   */
  class SelectBuilder[S] {
    /**
     * Create a FieldSelector from a field accessor expression.
     * The field name is captured as a singleton string type.
     */
    transparent inline def apply[F](inline selector: S => F): FieldSelector[S, F, ?] =
      ${ selectImpl[S, F]('selector) }
  }

  /**
   * Implementation of the select macro.
   * Extracts the field name from the selector expression and creates a FieldSelector.
   */
  def selectImpl[S: Type, F: Type](selector: Expr[S => F])(using Quotes): Expr[FieldSelector[S, F, ?]] = {
    import quotes.reflect.*

    // Extract field names from the selector expression
    val fieldPath = extractFieldPath(selector.asTerm)

    if (fieldPath.isEmpty) {
      report.errorAndAbort(
        "Invalid field selector. Expected a simple field access like `_.name` or `_.address.street`"
      )
    }

    // For single field access, use the simple form
    if (fieldPath.length == 1) {
      val fieldName = fieldPath.head
      val nameType = ConstantType(StringConstant(fieldName))

      nameType.asType match {
        case '[name] =>
          '{
            new FieldSelector[S, F, name & String](
              ${ Expr(fieldName) }.asInstanceOf[name & String],
              DynamicOptic(Vector(DynamicOptic.Node.Field(${ Expr(fieldName) })))
            )
          }
      }
    } else {
      // For nested access, build the full path
      val firstName = fieldPath.head
      val nameType = ConstantType(StringConstant(firstName))

      nameType.asType match {
        case '[name] =>
          val nodesExpr = Expr.ofSeq(fieldPath.map(n => '{ DynamicOptic.Node.Field(${ Expr(n) }) }))
          '{
            new FieldSelector[S, F, name & String](
              ${ Expr(firstName) }.asInstanceOf[name & String],
              DynamicOptic(Vector($nodesExpr*))
            )
          }
      }
    }
  }

  /**
   * Extract the field path from a selector expression.
   * Handles both simple `_.name` and nested `_.address.street` paths.
   */
  private def extractFieldPath(using Quotes)(term: quotes.reflect.Term): List[String] = {
    import quotes.reflect.*

    def extract(t: Term, acc: List[String]): List[String] = t match {
      // Match `_.field` pattern
      case Select(inner, fieldName) =>
        extract(inner, fieldName :: acc)

      // Match the lambda parameter (identity function on it)
      case Ident(_) =>
        acc

      // Match inlined expressions
      case Inlined(_, _, inner) =>
        extract(inner, acc)

      // Match block expressions
      case Block(Nil, inner) =>
        extract(inner, acc)

      // Match lambda expressions: (x: T) => x.field
      case Lambda(List(_), body) =>
        extract(body, acc)

      // Match typed expressions
      case Typed(inner, _) =>
        extract(inner, acc)

      case other =>
        report.errorAndAbort(
          s"Unsupported selector expression: ${other.show}. " +
          s"Expected a simple field access like `_.name` or `_.address.street`. " +
          s"Term type: ${other.getClass.getSimpleName}"
        )
    }

    extract(term, Nil)
  }

  /**
   * Macro to create a PathSelector from a nested field access.
   * Usage: `path[Person](_.address.street)`
   */
  inline def path[S]: PathBuilder[S] = new PathBuilder[S]

  class PathBuilder[S] {
    transparent inline def apply[F](inline selector: S => F): PathSelector[S, F, ?] =
      ${ pathImpl[S, F]('selector) }
  }

  def pathImpl[S: Type, F: Type](selector: Expr[S => F])(using Quotes): Expr[PathSelector[S, F, ?]] = {
    import quotes.reflect.*

    val fieldPath = extractFieldPath(selector.asTerm)

    if (fieldPath.isEmpty) {
      report.errorAndAbort(
        "Invalid path selector. Expected a field access like `_.name` or `_.address.street`"
      )
    }

    // Build a tuple type of the field names
    def buildTupleType(names: List[String]): TypeRepr = names match {
      case Nil => TypeRepr.of[EmptyTuple]
      case head :: tail =>
        val headType = ConstantType(StringConstant(head))
        val tailType = buildTupleType(tail)
        TypeRepr.of[*:].appliedTo(List(headType, tailType))
    }

    val pathTupleType = buildTupleType(fieldPath)

    pathTupleType.asType match {
      case '[pathType] =>
        val nodesExpr = Expr.ofSeq(fieldPath.map(n => '{ DynamicOptic.Node.Field(${ Expr(n) }) }))

        // Build runtime tuple
        def buildTupleExpr(names: List[String]): Expr[Tuple] = names match {
          case Nil       => '{ EmptyTuple }
          case h :: tail => '{ ${ Expr(h) } *: ${ buildTupleExpr(tail) } }
        }

        val pathTupleExpr = buildTupleExpr(fieldPath)

        '{
          new PathSelector[S, F, pathType & Tuple](
            $pathTupleExpr.asInstanceOf[pathType & Tuple],
            DynamicOptic(Vector($nodesExpr*))
          )
        }
    }
  }
}
