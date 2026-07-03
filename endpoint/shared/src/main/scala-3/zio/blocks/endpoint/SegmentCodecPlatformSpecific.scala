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

import zio.blocks.combinators.Tuples

private[endpoint] trait SegmentCodecPlatformSpecific {
  import SegmentCodec._

  // Scala 3's real generic-tuple types, used directly as the leaf `PathVars` encoding.
  type OnePathVar[X] = X *: EmptyTuple
  type NoPathVars    = EmptyTuple

  extension [A, P <: BoundaryTag, S <: BoundaryTag](self: WithBoundaries[A, P, S]) {
    inline def ~[B, P2 <: BoundaryTag, S2 <: BoundaryTag, C](that: WithBoundaries[B, P2, S2])(using
      combiner: Tuples.Tuples.WithOut[A, B, C],
      canCombine: CanCombine[S, P2]
    ): WithBoundaries[C, P, S2] =
      ${ SegmentCodecPlatformSpecificMacros.combineImpl[A, B, C, P, S, P2, S2]('self, 'that, 'combiner) }

    inline def ~[C](inline that: String)(using
      combiner: Tuples.Tuples.WithOut[A, Unit, C],
      canCombine: CanCombine[S, BoundaryTag.Literal]
    ): WithBoundaries[C, P, BoundaryTag.Literal] =
      ${ SegmentCodecPlatformSpecificMacros.combineLiteralImpl[A, C, P, S]('self, 'that, 'combiner) }

    inline def transform[B](decode: A => B, encode: B => A): WithBoundaries[B, P, S] =
      ${ SegmentCodecPlatformSpecificMacros.transformImpl[A, B, P, S]('self, 'decode, 'encode) }

    inline def transformOrFail[B](
      decode: A => Either[DecodeError, B],
      encode: B => Either[DecodeError, A]
    ): WithBoundaries[B, P, S] =
      ${ SegmentCodecPlatformSpecificMacros.transformOrFailImpl[A, B, P, S]('self, 'decode, 'encode) }
  }

  inline def literal(inline value: String): Literal =
    ${ SegmentCodecPlatformSpecificMacros.literalImpl('value) }
}

private[endpoint] trait CanCombinePlatformSpecific {
  import SegmentCodec.BoundaryTag

  transparent inline given [L <: BoundaryTag, R <: BoundaryTag]: SegmentCodec.CanCombine[L, R] =
    ${ SegmentCodecPlatformSpecificMacros.canCombineImpl[L, R] }
}

private[endpoint] object SegmentCodecPlatformSpecificMacros {
  import SegmentCodec.*

  private sealed trait SegmentInfoKind
  private object SegmentInfoKind {
    case object Literal  extends SegmentInfoKind
    case object Bool     extends SegmentInfoKind
    case object Int      extends SegmentInfoKind
    case object Long     extends SegmentInfoKind
    case object String   extends SegmentInfoKind
    case object UUID     extends SegmentInfoKind
    case object Trailing extends SegmentInfoKind
  }

  private final case class SegmentInfo(kind: SegmentInfoKind, name: String)
  private final case class BoundaryInfo(prefix: Option[SegmentInfo], suffix: Option[SegmentInfo]) {
    def ++(that: BoundaryInfo): BoundaryInfo =
      BoundaryInfo(prefix.orElse(that.prefix), that.suffix.orElse(suffix))
  }

  def combineImpl[
    A: Type,
    B: Type,
    C: Type,
    P <: BoundaryTag: Type,
    S <: BoundaryTag: Type,
    P2 <: BoundaryTag: Type,
    S2 <: BoundaryTag: Type
  ](
    leftExpr: Expr[WithBoundaries[A, P, S]],
    rightExpr: Expr[WithBoundaries[B, P2, S2]],
    combinerExpr: Expr[Tuples.Tuples.WithOut[A, B, C]]
  )(using Quotes): Expr[WithBoundaries[C, P, S2]] = {
    import quotes.reflect.*

    val leftInfo  = boundaryInfo(leftExpr.asTerm)
    val rightInfo = boundaryInfo(rightExpr.asTerm)

    (leftInfo, rightInfo) match {
      case (Some(left), Some(right)) =>
        validateBoundary(left.suffix, right.prefix)
        '{
          SegmentCodec.combineValidated($leftExpr, $rightExpr, $combinerExpr).asInstanceOf[WithBoundaries[C, P, S2]]
        }
      case _ =>
        '{
          SegmentCodec.combineValidated($leftExpr, $rightExpr, $combinerExpr).asInstanceOf[WithBoundaries[C, P, S2]]
        }
    }
  }

  def combineLiteralImpl[A: Type, C: Type, P <: BoundaryTag: Type, S <: BoundaryTag: Type](
    leftExpr: Expr[WithBoundaries[A, P, S]],
    rightExpr: Expr[String],
    combinerExpr: Expr[Tuples.Tuples.WithOut[A, Unit, C]]
  )(using Quotes): Expr[WithBoundaries[C, P, BoundaryTag.Literal]] =
    '{
      SegmentCodec
        .combineValidated($leftExpr, SegmentCodec.literal($rightExpr), $combinerExpr)
        .asInstanceOf[WithBoundaries[C, P, BoundaryTag.Literal]]
    }

  def canCombineImpl[L <: BoundaryTag: Type, R <: BoundaryTag: Type](using Quotes): Expr[CanCombine[L, R]] = {
    import quotes.reflect.*

    val left  = TypeRepr.of[L]
    val right = TypeRepr.of[R]

    def is(boundary: TypeRepr, target: TypeRepr): Boolean = boundary <:< target

    def label(boundary: TypeRepr): String =
      if is(boundary, TypeRepr.of[BoundaryTag.Literal]) then "literal"
      else if is(boundary, TypeRepr.of[BoundaryTag.Bool]) then "bool"
      else if is(boundary, TypeRepr.of[BoundaryTag.Int]) then "int"
      else if is(boundary, TypeRepr.of[BoundaryTag.Long]) then "long"
      else if is(boundary, TypeRepr.of[BoundaryTag.String]) then "string"
      else if is(boundary, TypeRepr.of[BoundaryTag.UUID]) then "uuid"
      else if is(boundary, TypeRepr.of[BoundaryTag.Trailing]) then "trailing"
      else if is(boundary, TypeRepr.of[BoundaryTag.Empty]) then "empty"
      else boundary.show

    val leftTrailing  = is(left, TypeRepr.of[BoundaryTag.Trailing])
    val rightTrailing = is(right, TypeRepr.of[BoundaryTag.Trailing])
    val leftString    = is(left, TypeRepr.of[BoundaryTag.String])
    val rightString   = is(right, TypeRepr.of[BoundaryTag.String])
    val leftNumeric   = is(left, TypeRepr.of[BoundaryTag.Int]) || is(left, TypeRepr.of[BoundaryTag.Long])
    val rightNumeric  = is(right, TypeRepr.of[BoundaryTag.Int]) || is(right, TypeRepr.of[BoundaryTag.Long])
    val leftKnown     =
      is(left, TypeRepr.of[BoundaryTag.Empty]) || is(left, TypeRepr.of[BoundaryTag.Literal]) ||
        is(left, TypeRepr.of[BoundaryTag.Bool]) || leftNumeric || leftString ||
        is(left, TypeRepr.of[BoundaryTag.UUID]) || leftTrailing
    val rightKnown =
      is(right, TypeRepr.of[BoundaryTag.Empty]) || is(right, TypeRepr.of[BoundaryTag.Literal]) ||
        is(right, TypeRepr.of[BoundaryTag.Bool]) || rightNumeric || rightString ||
        is(right, TypeRepr.of[BoundaryTag.UUID]) || rightTrailing

    if leftTrailing || rightTrailing then
      report.errorAndAbort(s"Cannot combine trailing path segments with `~`: ${label(left)} ~ ${label(right)}")
    else if leftString && rightString then
      report.errorAndAbort(s"Cannot combine two string segments with `~`: ${label(left)} ~ ${label(right)}")
    else if leftNumeric && rightNumeric then
      report.errorAndAbort(s"Cannot combine two numeric segments with `~`: ${label(left)} ~ ${label(right)}")
    else if !leftKnown || !rightKnown then
      report.errorAndAbort(
        s"Cannot prove segment combination at compile time: ${label(left)} ~ ${label(right)}"
      )
    else '{ new CanCombine[L, R] {} }
  }

  def literalImpl(valueExpr: Expr[String])(using Quotes): Expr[Literal] =
    valueExpr.value match {
      case Some(value) =>
        try {
          SegmentCodec.validateLiteralValue(value)
          '{ SegmentCodec.literalValidated(${ Expr(value) }) }
        } catch {
          case error: IllegalArgumentException =>
            quotes.reflect.report.errorAndAbort(error.getMessage)
        }
      case None =>
        quotes.reflect.report.errorAndAbort("SegmentCodec.literal requires a string literal known at compile time")
    }

  def transformImpl[A: Type, B: Type, P <: BoundaryTag: Type, S <: BoundaryTag: Type](
    codecExpr: Expr[WithBoundaries[A, P, S]],
    decodeExpr: Expr[A => B],
    encodeExpr: Expr[B => A]
  )(using Quotes): Expr[WithBoundaries[B, P, S]] =
    '{
      SegmentCodec
        .transformValidated[A, B](
          $codecExpr,
          value => Right($decodeExpr(value)),
          value => Right($encodeExpr(value))
        )
        .asInstanceOf[WithBoundaries[B, P, S]]
    }

  def transformOrFailImpl[A: Type, B: Type, P <: BoundaryTag: Type, S <: BoundaryTag: Type](
    codecExpr: Expr[WithBoundaries[A, P, S]],
    decodeExpr: Expr[A => Either[DecodeError, B]],
    encodeExpr: Expr[B => Either[DecodeError, A]]
  )(using Quotes): Expr[WithBoundaries[B, P, S]] =
    '{
      SegmentCodec
        .transformValidated[A, B]($codecExpr, $decodeExpr, $encodeExpr)
        .asInstanceOf[WithBoundaries[B, P, S]]
    }

  private def validateBoundary(using Quotes)(left: Option[SegmentInfo], right: Option[SegmentInfo]): Unit = {
    import quotes.reflect.*

    (left, right) match {
      case (Some(SegmentInfo(SegmentInfoKind.Trailing, _)), _) | (_, Some(SegmentInfo(SegmentInfoKind.Trailing, _))) =>
        report.errorAndAbort("Cannot combine trailing path segments with `~`")
      case (
            Some(SegmentInfo(SegmentInfoKind.String, leftName)),
            Some(SegmentInfo(SegmentInfoKind.String, rightName))
          ) =>
        report.errorAndAbort(s"Cannot combine two string segments. Their names are $leftName and $rightName")
      case (
            Some(SegmentInfo(SegmentInfoKind.Int | SegmentInfoKind.Long, leftName)),
            Some(SegmentInfo(SegmentInfoKind.Int | SegmentInfoKind.Long, rightName))
          ) =>
        report.errorAndAbort(s"Cannot combine two numeric segments. Their names are $leftName and $rightName")
      case _ => ()
    }
  }

  private def boundaryInfo(using Quotes)(term: quotes.reflect.Term): Option[BoundaryInfo] = {
    import quotes.reflect.*

    val underlying = term.underlyingArgument
    if (underlying ne term) return boundaryInfo(underlying)

    def stringArg(args: List[Term], default: String): String =
      args.collectFirst { case quotes.reflect.Literal(StringConstant(value)) => value }.getOrElse(default)

    def singleInfo(kind: SegmentInfoKind, name: String): BoundaryInfo = {
      val info = SegmentInfo(kind, name)
      BoundaryInfo(Some(info), Some(info))
    }

    def applyInfo(name: String, args: List[Term]): Option[BoundaryInfo] = name match {
      case "literal" | "Literal"  => Some(singleInfo(SegmentInfoKind.Literal, stringArg(args, "literal")))
      case "bool" | "BoolSeg"     => Some(singleInfo(SegmentInfoKind.Bool, stringArg(args, "bool")))
      case "string" | "StringSeg" => Some(singleInfo(SegmentInfoKind.String, stringArg(args, "string")))
      case "int" | "IntSeg"       => Some(singleInfo(SegmentInfoKind.Int, stringArg(args, "int")))
      case "long" | "LongSeg"     => Some(singleInfo(SegmentInfoKind.Long, stringArg(args, "long")))
      case "uuid" | "UUIDSeg"     => Some(singleInfo(SegmentInfoKind.UUID, stringArg(args, "uuid")))
      case "Trailing"             => Some(singleInfo(SegmentInfoKind.Trailing, "trailing"))
      case "Empty"                => Some(BoundaryInfo(None, None))
      case _                      => None
    }

    def typeInfo(tpe: TypeRepr): Option[BoundaryInfo] = {
      val segmentCodecSym = Symbol.requiredClass("zio.blocks.endpoint.SegmentCodec")
      val base            = tpe.widenTermRefByName.dealias.baseType(segmentCodecSym)

      if base =:= TypeRepr.of[Any] then None
      else {
        val prefixTpe = base.memberType(segmentCodecSym.typeMember("Prefix"))
        val suffixTpe = base.memberType(segmentCodecSym.typeMember("Suffix"))

        def kindOf(boundary: TypeRepr): Option[SegmentInfoKind] =
          if boundary <:< TypeRepr.of[BoundaryTag.Literal] then Some(SegmentInfoKind.Literal)
          else if boundary <:< TypeRepr.of[BoundaryTag.Bool] then Some(SegmentInfoKind.Bool)
          else if boundary <:< TypeRepr.of[BoundaryTag.Int] then Some(SegmentInfoKind.Int)
          else if boundary <:< TypeRepr.of[BoundaryTag.Long] then Some(SegmentInfoKind.Long)
          else if boundary <:< TypeRepr.of[BoundaryTag.String] then Some(SegmentInfoKind.String)
          else if boundary <:< TypeRepr.of[BoundaryTag.UUID] then Some(SegmentInfoKind.UUID)
          else if boundary <:< TypeRepr.of[BoundaryTag.Trailing] then Some(SegmentInfoKind.Trailing)
          else None

        val prefix = kindOf(prefixTpe).map(kind => SegmentInfo(kind, "transformed"))
        val suffix = kindOf(suffixTpe).map(kind => SegmentInfo(kind, "transformed"))
        if (prefix.isEmpty && suffix.isEmpty) None else Some(BoundaryInfo(prefix, suffix))
      }
    }

    def appliedBoundary(fun: Term, args: List[Term]): Option[BoundaryInfo] = {
      val fullName = fun.symbol.fullName
      if (
        fullName == "zio.blocks.endpoint.SegmentCodec.literal" || fullName == "zio.blocks.endpoint.SegmentCodec.string" ||
        fullName == "zio.blocks.endpoint.SegmentCodec.int" || fullName == "zio.blocks.endpoint.SegmentCodec.long" ||
        fullName == "zio.blocks.endpoint.SegmentCodec.bool" || fullName == "zio.blocks.endpoint.SegmentCodec.uuid"
      )
        applyInfo(fun.symbol.name, args)
      else if (
        args.nonEmpty &&
        (fun.symbol.owner.name == "Transform" || fullName.contains("SegmentCodec.Transform"))
      )
        boundaryInfo(args.head)
      else if (
        (fun.symbol.name == "transform" || fun.symbol.name == "transformOrFail" ||
          fun.symbol.name == "transformValidated" ||
          fun.symbol.name == "transform$extension" || fun.symbol.name == "transformOrFail$extension") &&
        args.nonEmpty
      )
        boundaryInfo(args.head)
      else if (
        fullName.endsWith("SegmentCodec.Literal.apply") || fullName.endsWith("SegmentCodec.StringSeg.apply") ||
        fullName.endsWith("SegmentCodec.IntSeg.apply") || fullName.endsWith("SegmentCodec.LongSeg.apply") ||
        fullName.endsWith("SegmentCodec.BoolSeg.apply") || fullName.endsWith("SegmentCodec.UUIDSeg.apply")
      )
        applyInfo(fun.symbol.owner.name, args)
      else if (fullName.endsWith("SegmentCodec.Transform.apply"))
        args.headOption.flatMap(boundaryInfo)
      else None
    }

    term match {
      case Inlined(_, _, inner) => boundaryInfo(inner)
      case Typed(inner, _)      => boundaryInfo(inner)
      case Block(_, expr)       => boundaryInfo(expr)
      case Ident(_)             =>
        term.symbol.tree match {
          case valDef: ValDef => valDef.rhs.flatMap(boundaryInfo)
          case _              => None
        }
      case Apply(TypeApply(Select(_, "combineValidated"), _), List(left, right, _)) =>
        for {
          leftInfo  <- boundaryInfo(left)
          rightInfo <- boundaryInfo(right)
        } yield leftInfo ++ rightInfo
      case Apply(TypeApply(Select(_, "transformValidated"), _), codec :: _) =>
        boundaryInfo(codec)
      case Apply(Select(_, "transformValidated"), codec :: _) =>
        boundaryInfo(codec)
      case Apply(TypeApply(fun, _), args) =>
        appliedBoundary(fun, args)
      case Apply(Select(_, "combineValidated"), List(left, right, _)) =>
        for {
          leftInfo  <- boundaryInfo(left)
          rightInfo <- boundaryInfo(right)
        } yield leftInfo ++ rightInfo
      case Apply(Apply(TypeApply(Select(_, "transform"), _), List(left)), _) =>
        boundaryInfo(left)
      case Apply(Apply(Select(_, "transform"), List(left)), _) =>
        boundaryInfo(left)
      case Apply(TypeApply(Select(left, "transform"), _), _) =>
        boundaryInfo(left)
      case Apply(Select(left, "transform"), _) =>
        boundaryInfo(left)
      case Apply(Apply(TypeApply(Select(_, "transformOrFail"), _), List(left)), _) =>
        boundaryInfo(left)
      case Apply(Apply(Select(_, "transformOrFail"), List(left)), _) =>
        boundaryInfo(left)
      case Apply(TypeApply(Select(left, "transformOrFail"), _), _) =>
        boundaryInfo(left)
      case Apply(Select(left, "transformOrFail"), _) =>
        boundaryInfo(left)
      case Apply(fun, args) =>
        appliedBoundary(fun, args)
      case Select(_, "Empty") => Some(BoundaryInfo(None, None))
      case _                  => typeInfo(term.tpe)
    }
  }
}
