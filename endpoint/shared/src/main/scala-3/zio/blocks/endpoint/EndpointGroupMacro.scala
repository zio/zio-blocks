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
import zio.blocks.endpoint.{Endpoint, PathCodec, RoutePattern}

object EndpointGroupMacro {
  def build(body: Expr[Any])(using Quotes): Expr[Any] = buildGroup(body, None)

  def prefixGroup(codecExpr: Expr[PathCodec[?]], ntExpr: Expr[Any])(using Quotes): Expr[Any] = {
    import quotes.reflect.*

    def recoverBlock(t: Term): Term = t match {
      case Inlined(Some(Apply(Ident("endpoints"), List(b))), _, _)                  => b
      case Inlined(Some(Apply(Select(_, "endpoints"), List(b))), _, _)              => b
      case Inlined(_, _, inner)                                                     => recoverBlock(inner)
      case Typed(inner, _)                                                          => recoverBlock(inner)
      case Apply(Select(Ident("NamedTuple"), "toTuple"), List(inner))               => recoverBlock(inner)
      case Apply(Select(_, "toTuple"), List(inner))                                 => recoverBlock(inner)
      case Apply(TypeApply(Select(Ident("NamedTuple"), "toTuple"), _), List(inner)) => recoverBlock(inner)
      case Apply(TypeApply(Select(_, "toTuple"), _), List(inner))                   => recoverBlock(inner)
      case Apply(Ident("endpoints"), List(b))                                       => b
      case Apply(Select(_, "endpoints"), List(b))                                   => b
      case other                                                                    => other
    }
    val block = recoverBlock(ntExpr.asTerm)
    buildGroup(block.asExpr, Some(codecExpr))
  }

  private def buildGroup(bodyExpr: Expr[Any], captureCodecExpr: Option[Expr[Any]])(using Quotes): Expr[Any] = {
    import quotes.reflect.*

    val bodyTerm: Term             = bodyExpr.asTerm
    val captureCodec: Option[Term] = captureCodecExpr.map(_.asTerm)

    def unwrap(term: Term): Term = term match {
      case Inlined(Some(Apply(Ident("endpoints"), List(b))), _, _)     => unwrap(b)
      case Inlined(Some(Apply(Select(_, "endpoints"), List(b))), _, _) => unwrap(b)
      case Inlined(_, _, inner)                                        => unwrap(inner)
      case Typed(inner, _)                                             => unwrap(inner)
      case Apply(Ident("endpoints"), List(b))                          => unwrap(b)
      case Apply(Select(_, "endpoints"), List(b))                      => unwrap(b)
      case _                                                           => term
    }

    val unwrapped = unwrap(bodyTerm)

    def isEndpoint(tpe: TypeRepr): Boolean =
      tpe <:< TypeRepr.of[Endpoint[?, ?, ?, ?, ?]]

    def isNestedSubgroupStmt(t: Term): Option[(String, Term)] = t match {
      case Inlined(Some(call: Term), _, _) =>
        slashPrefix(call) match {
          case Some(p) => Some((p, t))
          case None    => isNestedSubgroupStmt(call)
        }
      case Inlined(_, _, inner)                                                                        => isNestedSubgroupStmt(inner)
      case Typed(inner, _)                                                                             => isNestedSubgroupStmt(inner)
      case Apply(Apply(TypeApply(Apply(Ident("/"), List(Literal(StringConstant(p)))), _), _), List(_)) => Some((p, t))
      case Apply(TypeApply(Apply(Ident("/"), List(Literal(StringConstant(p)))), _), List(_))           => Some((p, t))
      case Apply(Apply(Ident("/"), List(Literal(StringConstant(p)))), List(_))                         => Some((p, t))
      case Apply(Select(Literal(StringConstant(p)), "/"), List(_))                                     => Some((p, t))
      case Apply(TypeApply(Select(Literal(StringConstant(p)), "/"), _), List(_))                       => Some((p, t))
      case _                                                                                           => None
    }

    def slashPrefix(t: Term): Option[String] = t match {
      case Apply(Apply(TypeApply(Apply(Ident("/"), List(Literal(StringConstant(p)))), _), _), List(_)) => Some(p)
      case Apply(TypeApply(Apply(Ident("/"), List(Literal(StringConstant(p)))), _), List(_))           => Some(p)
      case Apply(Apply(Ident("/"), List(Literal(StringConstant(p)))), List(_))                         => Some(p)
      case Apply(Ident("/"), List(Literal(StringConstant(p))))                                         => Some(p)
      case Apply(Select(Literal(StringConstant(p)), "/"), List(_))                                     => Some(p)
      case Apply(TypeApply(Select(Literal(StringConstant(p)), "/"), _), List(_))                       => Some(p)
      case _                                                                                           => None
    }

    def collectMembers(stats: List[Statement]): List[(String, Term, Boolean)] = {
      val raw = stats.flatMap {
        case vd: ValDef if vd.rhs.nonEmpty =>
          val rhs = vd.rhs.get
          if (isEndpoint(rhs.tpe)) {
            Some((vd.name, rhs, false))
          } else {
            report.errorAndAbort(
              s"endpoints { ... } only accepts `val name = Endpoint(...)` statements; found non-Endpoint val ${vd.name} of type ${rhs.tpe.show}"
            )
          }
        case app: Term if isEndpoint(app.tpe) =>
          val name = autoName(app)
          Some((name, app, false))
        case other: Term =>
          isNestedSubgroupStmt(other) match {
            case Some((name, wholeSlashTerm)) =>
              Some((name, wholeSlashTerm, true))
            case None if !other.isInstanceOf[ValDef @unchecked] && !other.toString.trim.isEmpty =>
              report.errorAndAbort(
                "endpoints { ... } only accepts `val name = Endpoint(...)` statements, bare `Endpoint(...)` or `prefix / endpoints { ... }`"
              )
            case _ => None
          }
        case _ => None
      }
      val nameToTerms = raw.groupBy(_._1)
      nameToTerms.foreach { case (n, list) =>
        if (list.size > 1) {
          val locs = list.map { case (_, t, _) => s"${t.pos.sourceFile.name}:${t.pos.startLine}" }.mkString(", ")
          report.error(s"duplicate endpoint name `$n` from: $locs; rename one or assign to an explicit `val`")
        }
      }
      raw
    }

    def autoName(endpointTerm: Term): String = {
      import quotes.reflect.*
      def stripWrappers(t: Term): Term = t match {
        case TypeApply(inner, _)                                                                             => stripWrappers(inner)
        case Inlined(_, _, inner)                                                                            => stripWrappers(inner)
        case Typed(inner, _)                                                                                 => stripWrappers(inner)
        case Apply(x, List(TypeApply(Ident("leftUnit"), _)))                                                 => stripWrappers(x)
        case Apply(x, args) if args.exists { case TypeApply(Ident("leftUnit"), _) => true; case _ => false } =>
          stripWrappers(x)
        case _ => t
      }
      def peelToRoutePattern(t: Term): Term = t match {
        case Apply(Select(qual, _), args)
            if Set(
              "apply",
              "in",
              "out",
              "query",
              "header",
              "auth",
              "doc",
              "unauthorizedStatus",
              "orOutError",
              "outError",
              "outHeader"
            ).contains(qual.symbol.name) || qual.tpe <:< TypeRepr.of[Endpoint[?, ?, ?, ?, ?]] =>
          args.headOption match {
            case Some(rp) if rp.tpe <:< TypeRepr.of[RoutePattern[?]] => rp
            case _                                                   => peelToRoutePattern(qual)
          }
        case Apply(Select(Select(_, "Endpoint"), "apply"), List(rp))                                         => rp
        case Apply(Select(Ident("Endpoint"), "apply"), List(rp))                                             => rp
        case Apply(Select(qual, "apply"), List(rp)) if qual.tpe.typeSymbol.name == "Endpoint"                => rp
        case Apply(TypeApply(Select(Select(_, "Endpoint"), "apply"), _), List(rp))                           => rp
        case Apply(TypeApply(Select(Ident("Endpoint"), "apply"), _), List(rp))                               => rp
        case Apply(TypeApply(Apply(Ident("/"), _), _), List(rp))                                             => rp
        case Apply(TypeApply(Apply(Select(_, "/"), _), _), List(rp))                                         => rp
        case Apply(conversion, List(lit)) if conversion.tpe.toString.contains("Conversion")                  => lit
        case Apply(Select(Ident(givenName), "apply"), List(lit)) if givenName.startsWith("given_Conversion") => lit
        case _                                                                                               => t
      }
      val rpTerm = peelToRoutePattern(stripWrappers(endpointTerm))
      renderRoutePatternName(rpTerm)
    }

    def renderRoutePatternName(rpTerm0: Term): String = {
      import quotes.reflect.*
      def stripWrappers(t: Term): Term = t match {
        case TypeApply(inner, _)                                                                             => stripWrappers(inner)
        case Inlined(_, _, inner)                                                                            => stripWrappers(inner)
        case Typed(inner, _)                                                                                 => stripWrappers(inner)
        case Apply(x, List(TypeApply(Ident("leftUnit"), _)))                                                 => stripWrappers(x)
        case Apply(x, args) if args.exists { case TypeApply(Ident("leftUnit"), _) => true; case _ => false } =>
          stripWrappers(x)
        case _ => t
      }
      val rpTerm                        = stripWrappers(rpTerm0)
      def methodRender(m: Term): String = m match {
        case Select(_, name)
            if Set("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "TRACE", "CONNECT").contains(name) =>
          name
        case Select(_, "ANY")                       => "*"
        case Apply(Select(left, "#|"), List(right)) =>
          val ms = collectMethods(left) ++ collectMethods(right)
          ms.toList.sortBy(identity).mkString("#|")
        case _ =>
          report.errorAndAbort(
            s"cannot auto-name: unsupported method tree ${Printer.TreeStructure.show(m)} ; assign to val instead"
          )
      }
      def collectMethods(m: Term): Set[String] = m match {
        case Select(_, name)
            if Set("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "TRACE", "CONNECT").contains(name) =>
          Set(name)
        case Select(_, "ANY")                => Set("*")
        case Apply(Select(l, "#|"), List(r)) => collectMethods(l) ++ collectMethods(r)
        case _                               => Set()
      }
      def pathRender(p: Term): String  = pathRender0(stripWrappers(p))
      def pathRender0(p: Term): String = p match {
        case Apply(Select(conv, "apply"), List(inner)) if conv.symbol.name.contains("Conversion")            => pathRender0(inner)
        case Apply(Select(Ident(givenName), "apply"), List(lit)) if givenName.startsWith("given_Conversion") =>
          pathRender0(lit)
        case Apply(conversion, List(lit)) if conversion.tpe.toString.contains("Conversion") => pathRender0(lit)
        case Literal(StringConstant(s))                                                     => s
        case Apply(TypeApply(Select(Select(_, "PathCodec"), ctor), _), List(Literal(StringConstant(n))))
            if Set("int", "long", "string", "bool", "uuid").contains(ctor) =>
          s"{$n}"
        case Apply(TypeApply(Select(qual, ctor), _), List(Literal(StringConstant(n))))
            if Set("int", "long", "string", "bool", "uuid").contains(ctor) && qual.symbol.name == "PathCodec" =>
          s"{$n}"
        case Apply(Select(Select(_, "PathCodec"), ctor), List(Literal(StringConstant(n))))
            if Set("int", "long", "string", "bool", "uuid").contains(ctor) =>
          s"{$n}"
        case Apply(Select(qual, ctor), List(Literal(StringConstant(n))))
            if Set("int", "long", "string", "bool", "uuid").contains(ctor) && qual.symbol.name == "PathCodec" =>
          s"{$n}"
        case Apply(Select(Select(_, "SegmentCodec"), ctor), List(Literal(StringConstant(n))))
            if Set("int", "long", "string", "bool", "uuid").contains(ctor) =>
          s"{$n}"
        case Apply(Ident("stringToPathCodec"), List(Literal(StringConstant(s))))   => s
        case Apply(Select(left, "/"), List(right))                                 => pathRender0(left) + "/" + pathRender0(right)
        case Apply(TypeApply(Select(left, "/"), _), List(right))                   => pathRender0(left) + "/" + pathRender0(right)
        case Apply(Select(left, "~"), List(right))                                 => pathRender0(left) + pathRender0(right)
        case Apply(Select(_, "trailing"), _)                                       => "..."
        case Apply(TypeApply(Ident("leftUnit"), _), List(inner))                   => pathRender0(inner)
        case Apply(Select(qual, "apply"), args) if qual.symbol.name == "PathCodec" =>
          pathRender0(args.headOption.getOrElse(qual))
        case _ =>
          report.errorAndAbort(
            s"cannot auto-name: unsupported path tree ${Printer.TreeStructure.show(p)} ; assign to val instead"
          )
      }
      def decompose(t0: Term): (String, List[String]) = {
        val t = stripWrappers(t0)
        t match {
          case Apply(Ident("MethodSyntax"), List(method)) =>
            (methodRender(method), Nil)
          case Apply(TypeApply(Ident("RoutePatternOps"), _), List(inner)) =>
            decompose(inner)
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
            report.errorAndAbort(
              s"cannot auto-name this endpoint; assign it to a val. Tree: ${Printer.TreeStructure.show(t)}"
            )
        }
      }
      val (method, segments) = decompose(rpTerm)
      val rendered           = if (segments.isEmpty) "" else segments.mkString("/", "/", "")
      if (segments.isEmpty) method else s"$method $rendered"
    }

    def isEndpointTerm(t: Term): Boolean = t match {
      case vd: ValDef => vd.rhs.exists(rhs => isEndpoint(rhs.tpe))
      case other      => isEndpoint(other.tpe)
    }

    def wrapLeaf(term: Term): Expr[Any] = captureCodec match {
      case Some(codecTerm) =>
        val epTpe            = term.tpe.dealias
        val (pi, i, e, o, a) = epTpe match {
          case AppliedType(_, List(pi, i, e, o, a)) => (pi, i, e, o, a)
          case _                                    => report.errorAndAbort(s"cannot read Endpoint type: ${epTpe.show}")
        }
        val codecA = codecTerm.tpe.baseType(TypeRepr.of[PathCodec].typeSymbol) match {
          case AppliedType(_, List(ca)) => ca
          case _                        => report.errorAndAbort(s"cannot read PathCodec A: ${codecTerm.tpe.show}")
        }
        (codecA.asType, pi.asType) match {
          case ('[ca], '[p]) =>
            // Upstream's Tuples givens infer C; read the precise Out off the
            // summoned instance's refined static type - never hand-construct C.
            Expr.summon[zio.blocks.combinators.Tuples.Tuples[ca, p]] match {
              case Some(withOut) =>
                val outTpe = {
                  val tuplesSym                    = TypeRepr.of[zio.blocks.combinators.Tuples.Tuples].typeSymbol
                  def walk(t0: TypeRepr): TypeRepr = t0.dealias match {
                    case Refinement(_, "Out", TypeBounds(_, hi)) => hi.dealias
                    case Refinement(parent, _, _)                => walk(parent)
                    case other                                   => other.memberType(tuplesSym.typeMember("Out")).dealias
                  }
                  walk(withOut.asTerm.tpe)
                }
                val composedEndpoint: TypeRepr = TypeRepr.of[Endpoint].appliedTo(List(outTpe, i, e, o, a))
                composedEndpoint.asType match {
                  case '[ce] =>
                    '{
                      val ep       = ${ term.asExprOf[Endpoint[?, ?, ?, ?, ?]] }
                      val codec    = ${ codecTerm.asExprOf[PathCodec[?]] }
                      val pc       = ep.route.pathCodec
                      val combiner =
                        ${ withOut }.asInstanceOf[zio.blocks.combinators.Tuples.Tuples.WithOut[Any, Any, Any]]
                      val combined =
                        PathCodec.combineUnrefined(codec.asInstanceOf[PathCodec[Any]], pc.asInstanceOf[PathCodec[Any]])(
                          combiner
                        )
                      ep.copy(route =
                        ep.route.copy(pathCodec = combined.asInstanceOf[PathCodec[Any]]).asInstanceOf[ep.route.type]
                      ).asInstanceOf[ce]
                    }
                }
              case None =>
                report.errorAndAbort(s"cannot find Tuples[${codecA.show}, ${pi.show}] combiner for prefix composition")
            }
        }
      case None => term.asExprOf[Endpoint[?, ?, ?, ?, ?]]
    }

    def wrapSubgroupError(): Nothing =
      if (captureCodec.isDefined)
        report.errorAndAbort(
          "nested constant groups under a path-variable prefix are not yet supported; use a flat block"
        )
      else sys.error("internal: subgroup under capture without error")

    unwrapped match {
      case Block(stats, expr) =>
        val memberStats: List[Statement] =
          if (isEndpointTerm(expr) || expr.isInstanceOf[ValDef @unchecked] || isNestedSubgroupStmt(expr).isDefined)
            stats :+ expr
          else if (expr match { case Literal(UnitConstant()) => true; case _ => false }) stats
          else stats
        if (memberStats.isEmpty) {
          '{ NamedTuple(EmptyTuple) }
        } else {
          val members = collectMembers(memberStats)
          if (members.isEmpty) {
            '{ NamedTuple(EmptyTuple) }
          } else {
            val (names, terms, isSubgroups) = members.unzip3
            val exprs: List[Expr[Any]]      = terms.zip(isSubgroups).map { case (t, isSub) =>
              if (isSub) {
                if (captureCodec.isDefined) wrapSubgroupError() else t.asExpr
              } else {
                wrapLeaf(t)
              }
            }
            val namesTupleExpr: Expr[Tuple] = Expr.ofTupleFromSeq(names.map(Expr(_)))
            val namesTypeRepr: TypeRepr     = namesTupleExpr.asTerm.tpe
            val valuesTuple: Expr[Tuple]    = Expr.ofTupleFromSeq(exprs)
            val valuesTypeRepr: TypeRepr    = valuesTuple.asTerm.tpe
            val ntType: TypeRepr            =
              AppliedType(TypeRepr.of[scala.NamedTuple.NamedTuple], List(namesTypeRepr, valuesTypeRepr))
            ntType.asType match {
              case '[nt] =>
                '{ $valuesTuple.asInstanceOf[nt] }
              case _ =>
                report.errorAndAbort("failed to construct NamedTuple type for group")
            }
          }
        }

      case vd: ValDef if vd.rhs.nonEmpty =>
        val rhsTerm = vd.rhs.get
        if (isEndpoint(rhsTerm.tpe)) {
          val expr                        = wrapLeaf(rhsTerm)
          val namesTupleExpr: Expr[Tuple] = Expr.ofTupleFromSeq(List(Expr(vd.name)))
          val namesTypeRepr: TypeRepr     = namesTupleExpr.asTerm.tpe
          val valuesTuple: Expr[Tuple]    = Expr.ofTupleFromSeq(Seq(expr))
          val valuesTypeRepr: TypeRepr    = valuesTuple.asTerm.tpe
          val ntType: TypeRepr            =
            AppliedType(TypeRepr.of[scala.NamedTuple.NamedTuple], List(namesTypeRepr, valuesTypeRepr))
          ntType.asType match {
            case '[nt] =>
              '{ $valuesTuple.asInstanceOf[nt] }
            case _ =>
              report.errorAndAbort("failed to construct NamedTuple type for group")
          }
        } else {
          report.errorAndAbort("endpoints { ... } only accepts `val name = Endpoint(...)` statements")
        }

      case t: Term =>
        isNestedSubgroupStmt(t) match {
          case Some((name, whole)) =>
            if (captureCodec.isDefined) wrapSubgroupError()
            val members                     = List((name, whole, true))
            val (names, terms, isSubgroups) = members.unzip3
            val exprs: List[Expr[Any]]      =
              terms.zip(isSubgroups).map { case (tt, isSub) => if (isSub) tt.asExpr else wrapLeaf(tt) }
            val namesTupleExpr: Expr[Tuple] = Expr.ofTupleFromSeq(names.map(Expr(_)))
            val namesTypeRepr: TypeRepr     = namesTupleExpr.asTerm.tpe
            val valuesTuple: Expr[Tuple]    = Expr.ofTupleFromSeq(exprs)
            val valuesTypeRepr: TypeRepr    = valuesTuple.asTerm.tpe
            val ntType: TypeRepr            =
              AppliedType(TypeRepr.of[scala.NamedTuple.NamedTuple], List(namesTypeRepr, valuesTypeRepr))
            ntType.asType match {
              case '[nt] => '{ $valuesTuple.asInstanceOf[nt] }
              case _     => report.errorAndAbort("failed to construct NamedTuple type for single-subgroup group")
            }
          case None =>
            '{ NamedTuple(EmptyTuple) }
        }
    }
  }
}
