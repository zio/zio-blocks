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

import zio.blocks.combinators.Tuples

private[endpoint] trait SegmentCodecPlatformSpecific {
  import SegmentCodec._

  // Scala 2.13 has no `scala.Tuple`/`*:`/`EmptyTuple`; `Tuple1`/`Unit` are the nearest concrete
  // stand-ins, matching this codebase's existing convention (combinators/Tuples.scala) of using
  // `Unit` as the neutral/empty element.
  type OnePathVar[X] = Tuple1[X]
  type NoPathVars     = Unit

  private def validateCombination(left: SegmentCodec[_], right: SegmentCodec[_]): Unit =
    (suffixBoundary(left), prefixBoundary(right)) match {
      case (Some((leftKind, _)), Some((rightKind, _))) if leftKind == Kind.Trailing || rightKind == Kind.Trailing =>
        throw new IllegalArgumentException(
          s"Cannot combine trailing path segments with `~`: ${boundaryLabel(leftKind)} ~ ${boundaryLabel(rightKind)}"
        )
      case (Some((Kind.String, leftName)), Some((Kind.String, rightName))) =>
        throw new IllegalArgumentException(
          s"Cannot combine two string segments. Their names are $leftName and $rightName"
        )
      case (Some((leftKind, leftName)), Some((rightKind, rightName))) if isNumeric(leftKind) && isNumeric(rightKind) =>
        throw new IllegalArgumentException(
          s"Cannot combine two numeric segments. Their names are $leftName and $rightName"
        )
      case _ => ()
    }

  private def prefixBoundary(codec: SegmentCodec[_]): Option[(Kind, String)] =
    codec match {
      case Empty                    => None
      case Literal(value, _, _)     => Some((Kind.Literal, value))
      case BoolSeg(name, _, _)      => Some((Kind.Bool, name))
      case IntSeg(name, _, _)       => Some((Kind.Int, name))
      case LongSeg(name, _, _)      => Some((Kind.Long, name))
      case StringSeg(name, _, _)    => Some((Kind.String, name))
      case UUIDSeg(name, _, _)      => Some((Kind.UUID, name))
      case Trailing                 => Some((Kind.Trailing, "trailing"))
      case Transform(inner, _, _)   => prefixBoundary(inner)
      case Combined(left, right, _) =>
        prefixBoundary(left).orElse(prefixBoundary(right))
    }

  private def suffixBoundary(codec: SegmentCodec[_]): Option[(Kind, String)] =
    codec match {
      case Empty                    => None
      case Literal(value, _, _)     => Some((Kind.Literal, value))
      case BoolSeg(name, _, _)      => Some((Kind.Bool, name))
      case IntSeg(name, _, _)       => Some((Kind.Int, name))
      case LongSeg(name, _, _)      => Some((Kind.Long, name))
      case StringSeg(name, _, _)    => Some((Kind.String, name))
      case UUIDSeg(name, _, _)      => Some((Kind.UUID, name))
      case Trailing                 => Some((Kind.Trailing, "trailing"))
      case Transform(inner, _, _)   => suffixBoundary(inner)
      case Combined(left, right, _) =>
        suffixBoundary(right).orElse(suffixBoundary(left))
    }

  private def boundaryLabel(kind: Kind): String =
    kind match {
      case Kind.Literal  => "literal"
      case Kind.Bool     => "bool"
      case Kind.Int      => "int"
      case Kind.Long     => "long"
      case Kind.String   => "string"
      case Kind.UUID     => "uuid"
      case Kind.Combined => "combined"
      case Kind.Trailing => "trailing"
      case Kind.Empty    => "empty"
    }

  private def isNumeric(kind: Kind): Boolean = kind == Kind.Int || kind == Kind.Long

  implicit final class SegmentCodecOps[A, P <: BoundaryTag, S <: BoundaryTag, PV](
    private val self: WithBoundaries[A, P, S] { type PathVars = PV }
  ) {
    def ~[B, P2 <: BoundaryTag, S2 <: BoundaryTag, PV2, C, PVC](
      that: WithBoundaries[B, P2, S2] { type PathVars = PV2 }
    )(implicit
      combiner: Tuples.Tuples.WithOut[A, B, C],
      canCombine: CanCombine[S, P2],
      pathVarsCombiner: PathVarTuples.Combine.WithOut[PV, PV2, PVC]
    ): WithBoundaries[C, P, S2] { type PathVars = PVC } = {
      // `pathVarsCombiner` is a pure compile-time evidence/inference parameter (it drives PVC's
      // resolution, exactly like `canCombine` drives boundary validation) - never read at
      // runtime, so it is referenced here only to satisfy `-Ywarn-unused`/`-Xfatal-warnings`.
      val _ = pathVarsCombiner
      validateCombination(self, that)
      SegmentCodec
        .combineValidated(self, that, combiner)
        .asInstanceOf[WithBoundaries[C, P, S2] { type PathVars = PVC }]
    }

    def ~[C, PVC](that: String)(implicit
      combiner: Tuples.Tuples.WithOut[A, Unit, C],
      canCombine: CanCombine[S, BoundaryTag.Literal],
      pathVarsCombiner: PathVarTuples.Combine.WithOut[PV, NoPathVars, PVC]
    ): WithBoundaries[C, P, BoundaryTag.Literal] { type PathVars = PVC } = {
      val _     = pathVarsCombiner
      val right = SegmentCodec.literal(that)
      validateCombination(self, right)
      SegmentCodec
        .combineValidated(self, right, combiner)
        .asInstanceOf[WithBoundaries[C, P, BoundaryTag.Literal] { type PathVars = PVC }]
    }

    def transform[B](decode: A => B, encode: B => A): WithBoundaries[B, P, S] { type PathVars = PV } =
      SegmentCodec
        .transformValidated[A, B](self, value => Right(decode(value)), value => Right(encode(value)))
        .asInstanceOf[WithBoundaries[B, P, S] { type PathVars = PV }]

    def transformOrFail[B](
      decode: A => Either[DecodeError, B],
      encode: B => Either[DecodeError, A]
    ): WithBoundaries[B, P, S] { type PathVars = PV } =
      SegmentCodec
        .transformValidated[A, B](self, decode, encode)
        .asInstanceOf[WithBoundaries[B, P, S] { type PathVars = PV }]
  }

  def literal(value: String): Literal = {
    SegmentCodec.validateLiteralValue(value)
    SegmentCodec.literalValidated(value)
  }
}

private[endpoint] trait CanCombinePlatformSpecific {
  import SegmentCodec.BoundaryTag

  implicit def canCombine[L <: BoundaryTag, R <: BoundaryTag]: SegmentCodec.CanCombine[L, R] =
    new SegmentCodec.CanCombine[L, R] {}
}
