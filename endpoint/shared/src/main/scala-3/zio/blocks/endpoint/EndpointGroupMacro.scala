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

package zio.blocks.endpoint

import scala.quoted.* // used for Expr/Quotes in build

object EndpointGroupMacro {
  def build(body: Expr[Any])(using Quotes): Expr[Any] = {
    import quotes.reflect.*

    def unwrap(term: Term): Term = term match {
      case Inlined(_, _, inner) => unwrap(inner)
      case Typed(inner, _)      => unwrap(inner)
      case Block(List(single), _) if single.isInstanceOf[Inlined @unchecked | Typed @unchecked] => unwrap(single.asInstanceOf[Term])
      case _ => term
    }

    val unwrapped = unwrap(body.asTerm)

    def isEndpoint(tpe: TypeRepr): Boolean =
      tpe <:< TypeRepr.of[Endpoint[?, ?, ?, ?, ?]]

    def collectMembers(stats: List[Statement]): List[(String, Term)] = stats.collect {
      case vd: ValDef if vd.rhs.nonEmpty =>
        val rhsTerm = vd.rhs.get
        if (isEndpoint(rhsTerm.tpe)) {
          (vd.name, rhsTerm)
        } else {
          report.errorAndAbort(s"endpoints { ... } only accepts `val name = Endpoint(...)` statements; found non-Endpoint val ${vd.name}")
        }
      case other if !other.isInstanceOf[ValDef @unchecked] && !other.toString.trim.isEmpty =>
        report.errorAndAbort("endpoints { ... } only accepts `val name = Endpoint(...)` statements; bare expressions are not allowed in M1")
      case _ => null
    }.filter(_ != null).asInstanceOf[List[(String, Term)]]

    unwrapped match {
      case Block(stats, _) =>
        val members = collectMembers(stats)
        if (members.isEmpty) {
          '{ NamedTuple(EmptyTuple) }
        } else {
          val (names, terms) = members.unzip
          val exprs = terms.map(_.asExprOf[Endpoint[?, ?, ?, ?, ?]])
          val namesTupleExpr: Expr[Tuple] = Expr.ofTupleFromSeq(names.map(Expr(_)))
          val namesTypeRepr: TypeRepr = namesTupleExpr.asTerm.tpe
          val valuesTuple: Expr[Tuple] = Expr.ofTupleFromSeq(exprs)
          val valuesTypeRepr: TypeRepr = valuesTuple.asTerm.tpe   // fallback; switch to terms.map(_.tpe) fold if needed for concrete
          val ntType: TypeRepr =
            AppliedType(TypeRepr.of[scala.NamedTuple.NamedTuple], List(namesTypeRepr, valuesTypeRepr))
          ntType.asType match {
            case '[nt] =>
              '{ $valuesTuple.asInstanceOf[nt] }
          }
        }

      case vd: ValDef if vd.rhs.nonEmpty => // single-val no outer Block
        val rhsTerm = vd.rhs.get
        if (isEndpoint(rhsTerm.tpe)) {
          val expr = rhsTerm.asExprOf[Endpoint[?, ?, ?, ?, ?]]
          val namesTupleExpr: Expr[Tuple] = Expr.ofTupleFromSeq(List(Expr(vd.name)))
          val namesTypeRepr: TypeRepr = namesTupleExpr.asTerm.tpe
          val valuesTuple: Expr[Tuple] = Expr.ofTupleFromSeq(Seq(expr))
          val valuesTypeRepr: TypeRepr = valuesTuple.asTerm.tpe
          val ntType: TypeRepr =
            AppliedType(TypeRepr.of[scala.NamedTuple.NamedTuple], List(namesTypeRepr, valuesTypeRepr))
          ntType.asType match {
            case '[nt] =>
              '{ $valuesTuple.asInstanceOf[nt] }
          }
        } else {
          report.errorAndAbort("endpoints { ... } only accepts `val name = Endpoint(...)` statements")
        }

      case Block(Nil, _) | _ => '{ NamedTuple(EmptyTuple) }
    }
  }
}
