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

import scala.quoted.*
import zio.blocks.endpoint.{Endpoint, PathCodec, RoutePattern}

private[endpoint] object EndpointGroupMacro {
  def build(body: Expr[Any])(using Quotes): Expr[Any] =
    buildGroupTree(body, Nil)

  def prefixGroupCodec(codecExpr: Expr[PathCodec[?]], ntExpr: Expr[Any])(using Quotes): Expr[Any] = {
    import quotes.reflect.*

    def recoverBlock(t: Term): Option[Term] = t match {
      case Inlined(Some(Apply(Ident("endpoints"), List(b))), _, _)                  => Some(b)
      case Inlined(Some(Apply(Select(_, "endpoints"), List(b))), _, _)              => Some(b)
      case Inlined(_, _, inner)                                                     => recoverBlock(inner)
      case Typed(inner, _)                                                          => recoverBlock(inner)
      case Apply(Select(Ident("NamedTuple"), "toTuple"), List(inner))               => recoverBlock(inner)
      case Apply(Select(_, "toTuple"), List(inner))                                 => recoverBlock(inner)
      case Apply(TypeApply(Select(Ident("NamedTuple"), "toTuple"), _), List(inner)) => recoverBlock(inner)
      case Apply(TypeApply(Select(_, "toTuple"), _), List(inner))                   => recoverBlock(inner)
      case Apply(Ident("endpoints"), List(b))                                       => Some(b)
      case Apply(Select(_, "endpoints"), List(b))                                   => Some(b)
      case _                                                                        => None
    }
    val block = recoverBlock(ntExpr.asTerm).getOrElse(
      report.errorAndAbort(
        "prefix / <group> requires an inline `endpoints { ... }` block; binding the group to a value first (val g = endpoints { ... }) is not supported"
      )
    )
    buildGroupTree(block.asExpr, List(codecExpr))
  }

  private def buildGroupTree(bodyExpr: Expr[Any], codecs: List[Expr[PathCodec[?]]])(using Quotes): Expr[Any] = {
    import quotes.reflect.*

    val bodyTerm: Term = bodyExpr.asTerm

    def unwrap(term: Term): Term = term match {
      case Inlined(Some(Apply(Ident("endpoints"), List(b))), _, _)     => unwrap(b)
      case Inlined(Some(Apply(Select(_, "endpoints"), List(b))), _, _) => unwrap(b)
      case Inlined(_, _, inner)                                        => unwrap(inner)
      case Typed(inner, _)                                             => unwrap(inner)
      case Apply(Ident("endpoints"), List(b))                          => unwrap(b)
      case Apply(Select(_, "endpoints"), List(b))                      => unwrap(b)
      case _                                                           => term
    }

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

    def isPathCodecType(tpe: TypeRepr): Boolean = {
      val sym = TypeRepr.of[PathCodec].typeSymbol
      tpe.dealias.baseType(sym) match {
        case AppliedType(_, _) => true
        case _                 => false
      }
    }

    def isPathCodecSubgroupStmt(t: Term): Option[(Term, Term)] = t match {
      case Inlined(Some(call: Term), _, expansion) =>
        // Avoid self-recursion: check call's direct patterns without re-entering Inlined(Some(...)) case
        isPathCodecSubgroupStmtDirect(call).orElse(isPathCodecSubgroupStmt(expansion))
      case Inlined(_, _, inner)                                                           => isPathCodecSubgroupStmt(inner)
      case Typed(inner, _)                                                                => isPathCodecSubgroupStmt(inner)
      case Apply(TypeApply(Select(codec, "/"), _), List(_)) if isPathCodecType(codec.tpe) =>
        Some((codec, t))
      case Apply(Apply(TypeApply(Select(codec, "/"), _), _), List(_)) if isPathCodecType(codec.tpe) =>
        Some((codec, t))
      case Apply(Select(codec, "/"), List(_)) if isPathCodecType(codec.tpe) =>
        Some((codec, t))
      // Ident("/") with PathCodec first arg (extension method form)
      case Apply(Apply(Ident("/"), List(codec)), List(_)) if isPathCodecType(codec.tpe) =>
        Some((codec, t))
      case Apply(TypeApply(Apply(Ident("/"), List(codec)), _), List(_)) if isPathCodecType(codec.tpe) =>
        Some((codec, t))
      case Apply(Apply(TypeApply(Ident("/"), _), List(codec)), List(_)) if isPathCodecType(codec.tpe) =>
        Some((codec, t))
      case Apply(TypeApply(Apply(TypeApply(Ident("/"), _), List(codec)), _), List(_)) if isPathCodecType(codec.tpe) =>
        Some((codec, t))
      // prefixGroupCodec calls (expanded from PathCodec / endpoints { ... })
      case Apply(Select(_, "prefixGroupCodec"), List(codecArg, _)) =>
        Some((codecArg, t))
      case Apply(Ident("prefixGroupCodec"), List(codecArg, _)) =>
        Some((codecArg, t))
      case Apply(TypeApply(Select(_, "prefixGroupCodec"), _), List(codecArg, _)) =>
        Some((codecArg, t))
      case Apply(TypeApply(Ident("prefixGroupCodec"), _), List(codecArg, _)) =>
        Some((codecArg, t))
      case _ => None
    }

    // Direct check without Inlined(Some(...)) recursion to avoid infinite loop when inner call does not match
    def isPathCodecSubgroupStmtDirect(t: Term): Option[(Term, Term)] = t match {
      case Apply(TypeApply(Select(codec, "/"), _), List(_)) if isPathCodecType(codec.tpe) =>
        Some((codec, t))
      case Apply(Apply(TypeApply(Select(codec, "/"), _), _), List(_)) if isPathCodecType(codec.tpe) =>
        Some((codec, t))
      case Apply(Select(codec, "/"), List(_)) if isPathCodecType(codec.tpe) =>
        Some((codec, t))
      case Apply(Apply(Ident("/"), List(codec)), List(_)) if isPathCodecType(codec.tpe) =>
        Some((codec, t))
      case Apply(TypeApply(Apply(Ident("/"), List(codec)), _), List(_)) if isPathCodecType(codec.tpe) =>
        Some((codec, t))
      case Apply(Apply(TypeApply(Ident("/"), _), List(codec)), List(_)) if isPathCodecType(codec.tpe) =>
        Some((codec, t))
      case Apply(TypeApply(Apply(TypeApply(Ident("/"), _), List(codec)), _), List(_)) if isPathCodecType(codec.tpe) =>
        Some((codec, t))
      case Apply(Select(_, "prefixGroupCodec"), List(codecArg, _)) =>
        Some((codecArg, t))
      case Apply(Ident("prefixGroupCodec"), List(codecArg, _)) =>
        Some((codecArg, t))
      case Apply(TypeApply(Select(_, "prefixGroupCodec"), _), List(codecArg, _)) =>
        Some((codecArg, t))
      case Apply(TypeApply(Ident("prefixGroupCodec"), _), List(codecArg, _)) =>
        Some((codecArg, t))
      case Inlined(_, _, inner) => isPathCodecSubgroupStmtDirect(inner)
      case Typed(inner, _)      => isPathCodecSubgroupStmtDirect(inner)
      case _                    => None
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

    /**
     * Extract the inner block from a nested subgroup term like "prefix" /
     * endpoints { ... }
     */
    def extractEndpointsBlock(t: Term): Term = {
      def go(inner: Term): Term = inner match {
        case Inlined(Some(call: Term), _, _)
            if isNestedSubgroupStmt(call).isDefined || isPathCodecSubgroupStmt(call).isDefined =>
          go(call)
        case Apply(Select(_, "/"), List(right))                                         => unwrap(right)
        case Apply(TypeApply(Select(_, "/"), _), List(right))                           => unwrap(right)
        case Apply(Apply(TypeApply(Select(_, "/"), _), _), List(right))                 => unwrap(right)
        case Apply(Apply(Ident("/"), List(_)), List(right))                             => unwrap(right)
        case Apply(TypeApply(Apply(Ident("/"), List(_)), _), List(right))               => unwrap(right)
        case Apply(Apply(TypeApply(Apply(Ident("/"), List(_)), _), _), List(right))     => unwrap(right)
        case Apply(TypeApply(Apply(TypeApply(Ident("/"), _), List(_)), _), List(right)) => unwrap(right)
        case Apply(Select(_, "~"), List(right))                                         => go(right)
        // prefixGroupCodec calls (expanded from PathCodec / endpoints { ... })
        case Apply(Select(_, "prefixGroupCodec"), List(_, ntArg))               => unwrap(ntArg)
        case Apply(Ident("prefixGroupCodec"), List(_, ntArg))                   => unwrap(ntArg)
        case Apply(TypeApply(Select(_, "prefixGroupCodec"), _), List(_, ntArg)) => unwrap(ntArg)
        case Apply(TypeApply(Ident("prefixGroupCodec"), _), List(_, ntArg))     => unwrap(ntArg)
        case Inlined(_, _, inner2)                                              => go(inner2)
        case Typed(inner2, _)                                                   => go(inner2)
        case _                                                                  => unwrap(inner)
      }
      go(t)
    }

    /**
     * Extract a readable name from a PathCodec term like PathCodec.int("id")
     */
    def extractCodecName(codec: Term): String = {
      def go(t: Term): String = t match {
        case Apply(TypeApply(Select(Select(_, "PathCodec"), ctor), _), List(Literal(StringConstant(n))))
            if Set("int", "long", "string", "bool", "uuid").contains(ctor) =>
          n
        case Apply(TypeApply(Select(qual, ctor), _), List(Literal(StringConstant(n))))
            if Set("int", "long", "string", "bool", "uuid").contains(ctor) && qual.symbol.name == "PathCodec" =>
          n
        case Apply(Select(Select(_, "PathCodec"), ctor), List(Literal(StringConstant(n))))
            if Set("int", "long", "string", "bool", "uuid").contains(ctor) =>
          n
        case Apply(Select(qual, ctor), List(Literal(StringConstant(n))))
            if Set("int", "long", "string", "bool", "uuid").contains(ctor) && qual.symbol.name == "PathCodec" =>
          n
        case Apply(Select(Select(_, "PathCodec"), "literal"), List(Literal(StringConstant(n))))                  => n
        case Apply(Select(qual, "literal"), List(Literal(StringConstant(n)))) if qual.symbol.name == "PathCodec" => n
        case Apply(Select(_, "literal"), List(Literal(StringConstant(n))))                                       => n
        case Inlined(_, _, inner)                                                                                => go(inner)
        case Typed(inner, _)                                                                                     => go(inner)
        case Block(List(_), inner)                                                                               => go(inner)
        case _                                                                                                   => "group"
      }
      val res = go(codec)
      if (res != "group") res
      else {
        val s   = codec.show
        val lit = "\"([^\"]+)\"".r.findFirstMatchIn(s).map(_.group(1)).getOrElse("group")
        if (lit.matches("[A-Za-z0-9_\\-\\.]+") && lit != "group") lit else "group"
      }
    }

    def extractExternalName(term: Term): Option[String] = {
      def strip(t: Term): Term = t match {
        case Inlined(_, _, inner) => strip(inner)
        case Typed(inner, _)      => strip(inner)
        case Block(_, inner)      => strip(inner)
        case TypeApply(inner, _)  => strip(inner)
        case _                    => t
      }
      strip(term) match {
        case Ident(name)     => Some(name)
        case Select(_, name) => Some(name)
        case _               => None
      }
    }

    def endpointName(term: Term): String =
      extractExternalName(term).getOrElse(autoName(term))

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
          val name = endpointName(app)
          Some((name, app, false))
        case other: Term =>
          isNestedSubgroupStmt(other) match {
            case Some((name, wholeSlashTerm)) =>
              Some((name, wholeSlashTerm, true))
            case None =>
              isPathCodecSubgroupStmt(other) match {
                case Some((codec, whole)) =>
                  val name = extractCodecName(codec)
                  Some((name, whole, true))
                case None if !other.isInstanceOf[ValDef @unchecked] && !other.toString.trim.isEmpty =>
                  report.errorAndAbort(
                    "endpoints { ... } only accepts `val name = Endpoint(...)` statements, bare `Endpoint(...)` or `prefix / endpoints { ... }`"
                  )
                case _ => None
              }
          }
        case _ => None
      }
      val nameToTerms = raw.groupBy(_._1)
      nameToTerms.foreach { case (n, list) =>
        if (list.size > 1) {
          val locs = list.map { case (_, t, _) => s"${t.pos.sourceFile.name}:${t.pos.startLine + 1}" }.mkString(", ")
          report.error(s"duplicate endpoint name `$n` from: $locs; rename one or assign to an explicit `val`")
        }
      }
      raw
    }

    def autoName(endpointTerm: Term): String = {
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
        case Apply(func, List(TypeApply(Ident("leftUnit"), _)))                                                 => peelToRoutePattern(func)
        case Apply(func, args) if args.exists { case TypeApply(Ident("leftUnit"), _) => true; case _ => false } =>
          peelToRoutePattern(func)
        case Apply(func, List(TypeApply(Ident("eithers"), _))) => peelToRoutePattern(func)
        case Apply(TypeApply(Select(qual, mname), _), args)
            if Set(
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
            )
              .contains(mname) =>
          args.headOption match {
            case Some(rp) if rp.tpe <:< TypeRepr.of[RoutePattern[?]] => rp
            case _                                                   => peelToRoutePattern(qual)
          }
        case Apply(Select(qual, mname), args)
            if Set(
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
            )
              .contains(mname) =>
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
        case Inlined(_, _, inner)                                                                            => pathRender0(inner)
        case Typed(inner, _)                                                                                 => pathRender0(inner)
        case Block(List(_), inner)                                                                           => pathRender0(inner)
        case Apply(Select(conv, "apply"), List(inner)) if conv.symbol.name.contains("Conversion")            => pathRender0(inner)
        case Apply(Select(Ident(givenName), "apply"), List(lit)) if givenName.startsWith("given_Conversion") =>
          pathRender0(lit)
        case Apply(conversion, List(lit)) if conversion.tpe.toString.contains("Conversion") => pathRender0(lit)
        case Apply(Select(inner, "unused"), _)                                              => pathRender0(inner)
        case Apply(TypeApply(Select(inner, "unused"), _), _)                                => pathRender0(inner)
        case Select(inner, "unused")                                                        => pathRender0(inner)
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
        case Select(_, "trailing")                                                 => "..."
        case Apply(TypeApply(Select(Select(_, "PathCodec"), "trailing"), _), _)    => "..."
        case Apply(TypeApply(Select(_, "trailing"), _), _)                         => "..."
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
          case Apply(Apply(TypeApply(Ident("/"), _), List(left)), List(right)) =>
            val (m, segs) = decompose(left)
            (m, segs :+ pathRender(right))
          case Apply(TypeApply(Apply(TypeApply(Ident("/"), _), List(left)), _), List(right)) =>
            val (m, segs) = decompose(left)
            (m, segs :+ pathRender(right))
          case Apply(Apply(Ident("/"), List(left)), List(right)) =>
            val (m, segs) = decompose(left)
            (m, segs :+ pathRender(right))
          case Apply(TypeApply(Apply(TypeApply(Apply(Ident("/"), List(method)), _), List(seg)), _), List(_)) =>
            (methodRender(method), List(pathRender(seg)))
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

    def hasIntraGroupRef(term: Term, localSymbols: Set[Symbol]): Option[String] = {
      var found: Option[String] = None
      val traverser             = new TreeTraverser {
        override def traverseTree(tree: Tree)(owner: Symbol): Unit =
          if (found.isEmpty) {
            tree match {
              case ident: Ident if localSymbols.contains(ident.symbol) =>
                found = Some(ident.name)
              case _ =>
                super.traverseTree(tree)(owner)
            }
          }
      }
      traverser.traverseTree(term)(Symbol.spliceOwner)
      found
    }

    def wrapLeaf(term: Term, prefixRefs: List[Expr[PathCodec[?]]]): Expr[Any] =
      if (prefixRefs.isEmpty) term.asExprOf[Endpoint[?, ?, ?, ?, ?]]
      else {
        val epTpe            = term.tpe.dealias
        val (pi, i, e, o, a) = epTpe match {
          case AppliedType(_, List(pi0, i0, e0, o0, a0)) => (pi0, i0, e0, o0, a0)
          case _                                         => report.errorAndAbort(s"cannot read Endpoint type: ${epTpe.show}")
        }

        val epSym                      = Symbol.newVal(Symbol.spliceOwner, "ep", epTpe, Flags.EmptyFlags, Symbol.noSymbol)
        val epDef                      = ValDef(epSym, Some(term))
        val epRefExpr                  = Ref(epSym).asExprOf[Endpoint[?, ?, ?, ?, ?]]
        val basePC: Expr[PathCodec[?]] = '{ $epRefExpr.route.pathCodec }

        // Helper to extract Out from a summoned Tuples instance
        def extractOut(withOutTerm: Term): TypeRepr = {
          val tuplesSym                    = TypeRepr.of[zio.blocks.combinators.Tuples.Tuples].typeSymbol
          def walk(t0: TypeRepr): TypeRepr = t0.dealias match {
            case Refinement(_, "Out", tb @ TypeBounds(lo, hi)) =>
              if (lo =:= TypeRepr.of[Nothing] && hi =:= TypeRepr.of[Any]) tb else hi.dealias
            case Refinement(parent, _, _) => walk(parent)
            case other                    =>
              other.memberType(tuplesSym.typeMember("Out")).dealias match {
                case tb @ TypeBounds(lo, hi) if lo =:= TypeRepr.of[Nothing] && hi =:= TypeRepr.of[Any] => tb
                case t                                                                                 => t
              }
          }
          walk(withOutTerm.tpe)
        }

        // Fold codecs left-to-right building precise PC chain; track precise A type via TypeRepr
        val composedPCWithType: (Expr[PathCodec[?]], TypeRepr) =
          prefixRefs.reverse.foldLeft[(Expr[PathCodec[?]], TypeRepr)]((basePC, pi)) {
            case ((acc, accATpe), codecExpr) =>
              val cA = codecExpr.asTerm.tpe.baseType(TypeRepr.of[PathCodec].typeSymbol) match {
                case AppliedType(_, List(ca)) => ca
                case _                        => report.errorAndAbort(s"cannot read PathCodec A: ${codecExpr.asTerm.tpe.show}")
              }
              val accATpeNorm = accATpe.dealias
              (cA.asType, accATpeNorm.asType) match {
                case ('[ca], '[ac]) =>
                  Expr.summon[zio.blocks.combinators.Tuples.Tuples[ca, ac]] match {
                    case Some(withOut) =>
                      var outTpe0          = extractOut(withOut.asTerm)
                      val isAbstractBounds = outTpe0 match {
                        case TypeBounds(lo, hi) => lo =:= TypeRepr.of[Nothing] && hi =:= TypeRepr.of[Any]
                        case _                  => false
                      }
                      if (isAbstractBounds) {
                        val caNorm    = cA.dealias
                        val acNorm    = accATpeNorm.dealias
                        val caIsUnit  = caNorm =:= TypeRepr.of[Unit]
                        val acIsUnit  = acNorm =:= TypeRepr.of[Unit]
                        val caIsEmpty = caNorm =:= TypeRepr.of[EmptyTuple]
                        val acIsEmpty = acNorm =:= TypeRepr.of[EmptyTuple]
                        outTpe0 = (caIsUnit, acIsUnit, caIsEmpty, acIsEmpty) match {
                          case (true, _, _, _) => acNorm
                          case (_, true, _, _) => caNorm
                          case (_, _, true, _) => acNorm
                          case (_, _, _, true) => caNorm
                          case _               => TypeRepr.of[(ca, ac)]
                        }
                      }
                      val outTpe                      = outTpe0.dealias
                      val outExpr: Expr[PathCodec[?]] = outTpe.asType match {
                        case '[out] =>
                          '{
                            PathCodec.combineUnrefined(
                              $codecExpr.asInstanceOf[PathCodec[ca]],
                              $acc.asInstanceOf[PathCodec[ac]]
                            )(${ withOut }.asInstanceOf[zio.blocks.combinators.Tuples.Tuples.WithOut[ca, ac, out]])
                          }.asInstanceOf[Expr[PathCodec[?]]]
                      }
                      (outExpr, outTpe)
                    case None =>
                      report.errorAndAbort(
                        s"cannot find Tuples combiner for prefix composition: ${cA.show} / ${accATpeNorm.show}"
                      )
                  }
                case _ => report.errorAndAbort("internal: failed to decompose types for prefix composition")
              }
          }

        val (composedPC, finalOutTpe) = composedPCWithType
        // Build precise Endpoint type with finalOut as PathInput
        val composedEndpointTpe: TypeRepr = TypeRepr.of[Endpoint].appliedTo(List(finalOutTpe, i, e, o, a))
        composedEndpointTpe.asType match {
          case '[ce] =>
            Block(
              List(epDef),
              '{
                val pc = $composedPC.asInstanceOf[PathCodec[Any]]
                $epRefExpr
                  .copy(route = $epRefExpr.route.copy(pathCodec = pc.asInstanceOf[PathCodec[Any]]))
                  .asInstanceOf[ce]
              }.asTerm
            ).asExpr
        }
      }

    def buildNamedTuple(names: List[String], exprs: List[Expr[Any]]): Expr[Any] = {
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

    // — Global hoist: one val per distinct prefix across the entire tree —
    val globalSeen = scala.collection.mutable.LinkedHashMap[String, (ValDef, Expr[PathCodec[?]])]()

    def ensureGlobal(expr: Expr[PathCodec[?]]): Expr[PathCodec[?]] = {
      val key = expr.asTerm.show
      globalSeen.get(key) match {
        case Some((_, ref)) => ref
        case None           =>
          val tpe = expr.asTerm.tpe
          val idx = globalSeen.size
          val sym = Symbol.newVal(Symbol.spliceOwner, s"prefix_$idx", tpe, Flags.EmptyFlags, Symbol.noSymbol)
          val vd  = ValDef(sym, Some(expr.asTerm))
          val ref = Ref(sym).asExprOf[PathCodec[?]]
          globalSeen(key) = (vd, ref)
          ref
      }
    }

    def resolveGlobal(prefixes: List[Expr[PathCodec[?]]]): List[Expr[PathCodec[?]]] =
      prefixes.map(ensureGlobal)

    // Seed global with outer codecs already
    codecs.foreach(ensureGlobal)

    def extractPrefixExpr(t: Term): Expr[PathCodec[?]] =
      isNestedSubgroupStmt(t) match {
        case Some((p, _)) =>
          '{ PathCodec.literal(${ Expr(p) }) }.asInstanceOf[Expr[PathCodec[?]]]
        case None =>
          isPathCodecSubgroupStmt(t) match {
            case Some((codec, _)) => codec.asExprOf[PathCodec[?]]
            case None             => report.errorAndAbort("internal: extractPrefixExpr called with non-subgroup term")
          }
      }

    def rec(term: Term, codecsAcc: List[Expr[PathCodec[?]]]): Expr[Any] = {
      // Ensure accumulated prefixes are registered globally (idempotent)
      codecsAcc.foreach(ensureGlobal)
      val unwrapped = unwrap(term)
      unwrapped match {
        case Block(stats, expr) =>
          val memberStats: List[Statement] =
            if (
              isEndpointTerm(expr) || expr.isInstanceOf[ValDef @unchecked] ||
              isNestedSubgroupStmt(expr).isDefined || isPathCodecSubgroupStmt(expr).isDefined
            )
              stats :+ expr
            else if (expr match { case Literal(UnitConstant()) => true; case _ => false }) stats
            else
              report.errorAndAbort(
                "endpoints { ... } only accepts `val name = Endpoint(...)` statements, bare `Endpoint(...)`, or `prefix / endpoints { ... }`"
              )
          if (memberStats.isEmpty) {
            '{ NamedTuple(EmptyTuple) }
          } else {
            val members = collectMembers(memberStats)
            if (members.isEmpty) {
              '{ NamedTuple(EmptyTuple) }
            } else {
              val localSymbols: Set[Symbol] = memberStats.collect { case vd: ValDef => vd.symbol }.toSet
              members.foreach { case (name, t, isSub) =>
                if (!isSub) {
                  hasIntraGroupRef(t, localSymbols) match {
                    case Some(dep) =>
                      report.errorAndAbort(
                        s"endpoint `$name` has intra-group dependency on `$dep`: endpoints { ... } does not support dependencies between members; extract to external val outside block or make independent (move `$dep` outside)"
                      )
                    case None => ()
                  }
                }
              }
              val (names, terms, isSubgroups) = members.unzip3
              val exprs: List[Expr[Any]]      = terms.zip(isSubgroups).map { case (t, isSub) =>
                if (isSub) {
                  val prefixExpr = extractPrefixExpr(t)
                  ensureGlobal(prefixExpr)
                  val innerBlock = extractEndpointsBlock(t)
                  rec(innerBlock, codecsAcc :+ prefixExpr)
                } else wrapLeaf(t, resolveGlobal(codecsAcc))
              }
              buildNamedTuple(names, exprs)
            }
          }

        case vd: ValDef if vd.rhs.nonEmpty =>
          val rhsTerm = vd.rhs.get
          if (isEndpoint(rhsTerm.tpe)) {
            val expr = wrapLeaf(rhsTerm, resolveGlobal(codecsAcc))
            buildNamedTuple(List(vd.name), List(expr))
          } else {
            report.errorAndAbort("endpoints { ... } only accepts `val name = Endpoint(...)` statements")
          }

        case t: Term =>
          isNestedSubgroupStmt(t) match {
            case Some((_, whole)) =>
              val prefixExpr = extractPrefixExpr(whole)
              ensureGlobal(prefixExpr)
              val innerBlock = extractEndpointsBlock(whole)
              val expr       = rec(innerBlock, codecsAcc :+ prefixExpr)
              isNestedSubgroupStmt(t) match {
                case Some((name, _)) => buildNamedTuple(List(name), List(expr))
                case None            => buildNamedTuple(List(extractCodecName(t.asInstanceOf[Term])), List(expr))
              }
            case None =>
              isPathCodecSubgroupStmt(t) match {
                case Some((codec, whole)) =>
                  val prefixExpr = codec.asExprOf[PathCodec[?]]
                  ensureGlobal(prefixExpr)
                  val innerBlock = extractEndpointsBlock(whole)
                  val expr       = rec(innerBlock, codecsAcc :+ prefixExpr)
                  val name       = extractCodecName(codec)
                  buildNamedTuple(List(name), List(expr))
                case None =>
                  if (isEndpoint(t.tpe)) {
                    val expr = wrapLeaf(t, resolveGlobal(codecsAcc))
                    buildNamedTuple(List(endpointName(t)), List(expr))
                  } else
                    report.errorAndAbort(
                      s"endpoints { ... } only accepts `val name = Endpoint(...)` statements, bare `Endpoint(...)` or `prefix / endpoints { ... }`; found unsupported expression of type ${t.tpe.show}"
                    )
              }
          }
      }
    }

    val result = rec(bodyTerm, codecs)
    if (globalSeen.nonEmpty) Block(globalSeen.values.map(_._1).toList, result.asTerm).asExpr
    else result
  }
}
