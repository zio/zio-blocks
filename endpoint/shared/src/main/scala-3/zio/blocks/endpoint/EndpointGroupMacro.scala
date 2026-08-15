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
      case _ => term
    }

    val unwrapped = unwrap(body.asTerm)

    def isEndpoint(tpe: TypeRepr): Boolean =
      tpe <:< TypeRepr.of[Endpoint[?, ?, ?, ?, ?]]

    def collectMembers(stats: List[Statement]): List[(String, Term)] = {
      val raw = stats.flatMap {
        case vd: ValDef if vd.rhs.nonEmpty =>
          val rhsTerm = vd.rhs.get
          if (isEndpoint(rhsTerm.tpe)) {
            Some((vd.name, rhsTerm))
          } else {
            report.errorAndAbort(s"endpoints { ... } only accepts `val name = Endpoint(...)` statements; found non-Endpoint val ${vd.name}")
          }
        case app: Term if isEndpoint(app.tpe) =>
          val name = autoName(app)
          Some((name, app))
        case other if !other.isInstanceOf[ValDef @unchecked] && !other.toString.trim.isEmpty =>
          report.errorAndAbort("endpoints { ... } only accepts `val name = Endpoint(...)` statements or bare `Endpoint(...)` expressions")
        case _ => None
      }
      val nameToTerms = raw.groupBy(_._1)
      nameToTerms.foreach { case (n, list) =>
        if (list.size > 1) {
          val locs = list.map { case (_, t) => s"${t.pos.sourceFile.name}:${t.pos.startLine}" }.mkString(", ")
          report.error(s"duplicate endpoint name `$n` from: $locs")
        }
      }
      raw
    }

    def autoName(endpointTerm: Term): String = {
      import quotes.reflect.*
      def stripWrappers(t: Term): Term = t match {
        case TypeApply(inner, _) => stripWrappers(inner)
        case Inlined(_, _, inner) => stripWrappers(inner)
        case Typed(inner, _) => stripWrappers(inner)
        case Apply(x, List(TypeApply(Ident("leftUnit"), _))) => stripWrappers(x)
        case Apply(x, args) if args.exists { case TypeApply(Ident("leftUnit"), _) => true; case _ => false } => stripWrappers(x)
        case _ => t
      }
      def peelToRoutePattern(t: Term): Term = t match {
        case Apply(Select(qual, _), args) if Set("apply", "in", "out", "query", "header", "auth", "doc", "unauthorizedStatus", "orOutError", "outError", "outHeader").contains(qual.symbol.name) || qual.tpe <:< TypeRepr.of[Endpoint[?, ?, ?, ?, ?]] =>
          args.headOption match {
            case Some(rp) if rp.tpe <:< TypeRepr.of[RoutePattern[?]] => rp
            case _ => peelToRoutePattern(qual)
          }
        case Apply(Select(Select(_, "Endpoint"), "apply"), List(rp)) => rp
        case Apply(Select(Ident("Endpoint"), "apply"), List(rp)) => rp
        case Apply(Select(qual, "apply"), List(rp)) if qual.tpe.typeSymbol.name == "Endpoint" => rp
        case Apply(TypeApply(Select(Select(_, "Endpoint"), "apply"), _), List(rp)) => rp
        case Apply(TypeApply(Select(Ident("Endpoint"), "apply"), _), List(rp)) => rp
        case Apply(TypeApply(Apply(Ident("/"), _), _), List(rp)) => rp
        case Apply(TypeApply(Apply(Select(_, "/"), _), _), List(rp)) => rp
        case Apply(conversion, List(lit)) if conversion.tpe.toString.contains("Conversion") => lit
        case Apply(Select(Ident(givenName), "apply"), List(lit)) if givenName.startsWith("given_Conversion") => lit
        case _ => t
      }
      val rpTerm = peelToRoutePattern(stripWrappers(endpointTerm))
      renderRoutePatternName(rpTerm)
    }

    def renderRoutePatternName(rpTerm0: Term): String = {
      import quotes.reflect.*
      def stripWrappers(t: Term): Term = t match {
        case TypeApply(inner, _) => stripWrappers(inner)
        case Inlined(_, _, inner) => stripWrappers(inner)
        case Typed(inner, _) => stripWrappers(inner)
        case Apply(x, List(TypeApply(Ident("leftUnit"), _))) => stripWrappers(x)
        case Apply(x, args) if args.exists { case TypeApply(Ident("leftUnit"), _) => true; case _ => false } => stripWrappers(x)
        case _ => t
      }
      val rpTerm = stripWrappers(rpTerm0)
      def methodRender(m: Term): String = m match {
        case Select(_, name) if Set("GET","POST","PUT","DELETE","PATCH","HEAD","OPTIONS","TRACE","CONNECT").contains(name) => name
        case Select(_, "ANY") => "*"
        case Apply(Select(left, "#|"), List(right)) =>
          val ms = collectMethods(left) ++ collectMethods(right)
          ms.toList.sortBy(identity).mkString("#|")
        case _ => report.errorAndAbort(s"cannot auto-name: unsupported method tree ${Printer.TreeStructure.show(m)}")
      }
      def collectMethods(m: Term): Set[String] = m match {
        case Select(_, name) if Set("GET","POST","PUT","DELETE","PATCH","HEAD","OPTIONS","TRACE","CONNECT").contains(name) => Set(name)
        case Select(_, "ANY") => Set("*")
        case Apply(Select(l, "#|"), List(r)) => collectMethods(l) ++ collectMethods(r)
        case _ => Set()
      }
      def pathRender(p: Term): String = pathRender0(stripWrappers(p))
      def pathRender0(p: Term): String = p match {
        case Apply(Select(conv, "apply"), List(inner)) if conv.symbol.name.contains("Conversion") => pathRender0(inner)
        case Apply(Select(Ident(givenName), "apply"), List(lit)) if givenName.startsWith("given_Conversion") => pathRender0(lit)
        case Apply(conversion, List(lit)) if conversion.tpe.toString.contains("Conversion") => pathRender0(lit)
        case Literal(StringConstant(s)) => s
        case Apply(TypeApply(Select(Select(_, "PathCodec"), ctor), _), List(Literal(StringConstant(n)))) if Set("int","long","string","bool","uuid").contains(ctor) => s"{$n}"
        case Apply(Select(Select(_, "PathCodec"), ctor), List(Literal(StringConstant(n)))) if Set("int","long","string","bool","uuid").contains(ctor) => s"{$n}"
        case Apply(Select(qual, ctor), List(Literal(StringConstant(n)))) if Set("int","long","string","bool","uuid").contains(ctor) && qual.symbol.name == "PathCodec" => s"{$n}"
        case Apply(Select(Select(_, "SegmentCodec"), ctor), List(Literal(StringConstant(n)))) if Set("int","long","string","bool","uuid").contains(ctor) => s"{$n}"
        case Apply(Select(left, "/"), List(right)) => pathRender0(left) + "/" + pathRender0(right)
        case Apply(TypeApply(Select(left, "/"), _), List(right)) => pathRender0(left) + "/" + pathRender0(right)
        case Apply(Select(left, "~"), List(right)) => pathRender0(left) + pathRender0(right)
        case Apply(Select(_, "trailing"), _) => "..."
        case Apply(TypeApply(Ident("leftUnit"), _), List(inner)) => pathRender0(inner)
        case Apply(Select(qual, "apply"), args) if qual.symbol.name == "PathCodec" => pathRender0(args.headOption.getOrElse(qual))
        case _ => report.errorAndAbort(s"cannot auto-name: unsupported path tree ${Printer.TreeStructure.show(p)} ; assign to val instead")
      }
      def decompose(t0: Term): (String, List[String]) = {
        val t = stripWrappers(t0)
        t match {
          case Apply(Select(left, "/"), List(right)) =>
            val (m, segs) = decompose(left)
            (m, segs :+ pathRender(right))
          case Apply(TypeApply(Select(left, "/"), _), List(right)) =>
            val (m, segs) = decompose(left)
            (m, segs :+ pathRender(right))
          case Apply(TypeApply(Apply(Ident("/"), List(method)), _), List(seg)) =>
            (methodRender(method), List(pathRender(seg)))
          case Apply(Apply(Ident("/"), List(method)), List(seg)) =>
            (methodRender(method), List(pathRender(seg)))
          case Apply(Ident("/"), List(method)) =>
            (methodRender(method), Nil)
          case m if m.tpe <:< TypeRepr.of[zio.http.Method] =>
            (methodRender(m), Nil)
          case Select(Ident("Method"), name) =>
            (methodRender(t), Nil)
          case _ =>
            report.errorAndAbort(s"cannot auto-name this endpoint; assign it to a val. Tree: ${Printer.TreeStructure.show(t)}")
        }
      }
      val (method, segments) = decompose(rpTerm)
      val rendered = if (segments.isEmpty) "" else segments.mkString("/", "/", "")
      if (segments.isEmpty) method else s"$method $rendered"
    }

    def isEndpointTerm(t: Term): Boolean = t match {
      case vd: ValDef => vd.rhs.exists(rhs => isEndpoint(rhs.tpe))
      case other      => isEndpoint(other.tpe)
    }

    unwrapped match {
      case Block(stats, expr) =>
        val memberStats: List[Statement] =
          if (isEndpointTerm(expr) || expr.isInstanceOf[ValDef @unchecked]) stats :+ expr
          else if (expr match { case Literal(UnitConstant()) => true; case _ => false }) stats
          else stats
        if (memberStats.isEmpty) {
          '{ NamedTuple(EmptyTuple) }
        } else {
          val members = collectMembers(memberStats)
          if (members.isEmpty) {
            '{ NamedTuple(EmptyTuple) }
          } else {
            val (names, terms) = members.unzip
            val exprs = terms.map(_.asExprOf[Endpoint[?, ?, ?, ?, ?]])
            val namesTupleExpr: Expr[Tuple] = Expr.ofTupleFromSeq(names.map(Expr(_)))
            val namesTypeRepr: TypeRepr = namesTupleExpr.asTerm.tpe
            val valuesTuple: Expr[Tuple] = Expr.ofTupleFromSeq(exprs)
            val valuesTypeRepr: TypeRepr = valuesTuple.asTerm.tpe
            val ntType: TypeRepr =
              AppliedType(TypeRepr.of[scala.NamedTuple.NamedTuple], List(namesTypeRepr, valuesTypeRepr))
            ntType.asType match {
              case '[nt] =>
                '{ $valuesTuple.asInstanceOf[nt] }
            }
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
