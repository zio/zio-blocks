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

import scala.quoted.*
import zio.blocks.schema.DynamicOptic

object SelectorMacros {

  inline def extractPath[S](inline selector: S => Any): DynamicOptic =
    ${ extractPathImpl('selector) }

  def extractPathImpl[S: Type](selector: Expr[S => Any])(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect.*

    sealed trait PathNode
    case class FieldNode(name: String)       extends PathNode
    case object ElementsNode                 extends PathNode
    case object MapKeysNode                  extends PathNode
    case object MapValuesNode                extends PathNode
    case object WrappedNode                  extends PathNode
    case class CaseNode(caseName: String)    extends PathNode
    case class AtIndexNode(index: Expr[Int]) extends PathNode
    case class AtKeyNode(key: Expr[Any])     extends PathNode

    def extractNodes(term: Term, acc: List[PathNode]): List[PathNode] =
      term match {
        case Ident(_) =>
          acc
        case Inlined(_, _, inner) =>
          extractNodes(inner, acc)
        case Block(_, expr) =>
          extractNodes(expr, acc)

        // _.field.each -> elements
        case Select(inner, "each") =>
          extractNodes(inner, ElementsNode :: acc)
        // _.field.eachKey -> mapKeys
        case Select(inner, "eachKey") =>
          extractNodes(inner, MapKeysNode :: acc)
        // _.field.eachValue -> mapValues
        case Select(inner, "eachValue") =>
          extractNodes(inner, MapValuesNode :: acc)

        // _.field.when[T] -> caseOf("T")
        case TypeApply(Select(inner, "when"), List(typeTree)) =>
          val caseName = typeTree.tpe.typeSymbol.name
          extractNodes(inner, CaseNode(caseName) :: acc)
        // _.field.wrapped[T] -> wrapped
        case TypeApply(Select(inner, "wrapped"), _) =>
          extractNodes(inner, WrappedNode :: acc)

        // _.seq.at(index) -> at(index)
        case Apply(Select(inner, "at"), List(indexExpr)) =>
          extractNodes(inner, AtIndexNode(indexExpr.asExprOf[Int]) :: acc)
        // _.map.atKey(key) -> atKey(key)
        case Apply(Select(inner, "atKey"), List(keyExpr)) =>
          extractNodes(inner, AtKeyNode(keyExpr.asExpr) :: acc)

        // selectDynamic for structural types
        case Apply(Select(inner, "selectDynamic"), List(Literal(StringConstant(name)))) =>
          extractNodes(inner, FieldNode(name) :: acc)

        // Regular field access: _.field
        case Select(inner, name) if !name.startsWith("$") =>
          extractNodes(inner, FieldNode(name) :: acc)

        case other =>
          report.errorAndAbort(
            s"Unsupported selector expression: ${other.show}. " +
              "Use field access like _.field, _.field.nested, " +
              "_.items.each, _.status.when[T], _.map.atKey(k)"
          )
      }

    def buildOptic(nodes: List[PathNode]): Expr[DynamicOptic] =
      nodes.foldLeft('{ DynamicOptic.root }) { (acc, node) =>
        node match {
          case FieldNode(name)  => '{ $acc.field(${ Expr(name) }) }
          case ElementsNode     => '{ $acc.apply(DynamicOptic.elements) }
          case MapKeysNode      => '{ $acc.apply(DynamicOptic.mapKeys) }
          case MapValuesNode    => '{ $acc.apply(DynamicOptic.mapValues) }
          case WrappedNode      => '{ $acc.apply(DynamicOptic.wrapped) }
          case CaseNode(name)   => '{ $acc.caseOf(${ Expr(name) }) }
          case AtIndexNode(idx) => '{ $acc.at($idx) }
          case AtKeyNode(key)   =>
            report.errorAndAbort("atKey selector requires a Schema for the key type; use DynamicOptic directly")
        }
      }

    selector.asTerm match {
      case Inlined(_, _, Block(List(DefDef(_, _, _, Some(body))), _)) =>
        buildOptic(extractNodes(body, Nil))
      case Inlined(_, _, Lambda(_, body)) =>
        buildOptic(extractNodes(body, Nil))
      case other =>
        report.errorAndAbort(
          s"Expected a lambda expression like _.field, got: ${other.show}"
        )
    }
  }
}
