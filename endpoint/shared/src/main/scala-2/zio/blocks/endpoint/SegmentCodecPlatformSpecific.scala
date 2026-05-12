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

  implicit final class SegmentCodecOps[A, P <: BoundaryTag, S <: BoundaryTag](
    private val self: WithBoundaries[A, P, S]
  ) {
    def ~[B, P2 <: BoundaryTag, S2 <: BoundaryTag, C](that: WithBoundaries[B, P2, S2])(
      implicit combiner: Tuples.Tuples.WithOut[A, B, C],
      canCombine: CanCombine[S, P2]
    ): WithBoundaries[C, P, S2] =
      SegmentCodec.combineValidated(self, that, combiner).asInstanceOf[WithBoundaries[C, P, S2]]

    def ~[C](that: String)(
      implicit combiner: Tuples.Tuples.WithOut[A, Unit, C],
      canCombine: CanCombine[S, BoundaryTag.Literal]
    ): WithBoundaries[C, P, BoundaryTag.Literal] =
      SegmentCodec.combineValidated(self, SegmentCodec.literal(that), combiner)
        .asInstanceOf[WithBoundaries[C, P, BoundaryTag.Literal]]

    def transform[B](decode: A => B, encode: B => A): WithBoundaries[B, P, S] =
      SegmentCodec.transformValidated[A, B](self, value => Right(decode(value)), value => Right(encode(value)))
        .asInstanceOf[WithBoundaries[B, P, S]]

    def transformOrFail[B](
      decode: A => Either[DecodeError, B],
      encode: B => Either[DecodeError, A]
    ): WithBoundaries[B, P, S] =
      SegmentCodec.transformValidated[A, B](self, decode, encode).asInstanceOf[WithBoundaries[B, P, S]]
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
