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

import zio.blocks.combinators.Unions

object EndpointUnionErrorBuilder {

  /**
   * Endpoint-specific helper for `orOutError`.
   *
   * This is intentionally narrower than the generic `combinators` typeclasses:
   * it handles the domain rule that the first `orOutError` replaces the initial
   * `Unit` error channel, while subsequent calls build fallback codecs backed
   * by Scala 3 union derivation from [[zio.blocks.combinators.Unions]].
   */
  trait ErrorBuilder[Err, E2] {
    type Out

    def add(
      existing: HttpCodec[CodecKind.Response, Err],
      next: HttpCodec[CodecKind.Response, E2]
    ): HttpCodec[CodecKind.Response, Out]
  }

  object ErrorBuilder {
    type WithOut[Err, E2, O] = ErrorBuilder[Err, E2] { type Out = O }

    given initialError[E2]: ErrorBuilder[Unit, E2] with {
      type Out = E2

      def add(
        existing: HttpCodec[CodecKind.Response, Unit],
        next: HttpCodec[CodecKind.Response, E2]
      ): HttpCodec[CodecKind.Response, E2] = next
    }

    given unionError[Err, E2, E3](using alternator: Unions.Unions.WithOut[Err, E2, E3]): ErrorBuilder[Err, E2] with {
      type Out = E3

      def add(
        existing: HttpCodec[CodecKind.Response, Err],
        next: HttpCodec[CodecKind.Response, E2]
      ): HttpCodec[CodecKind.Response, E3] =
        HttpCodec.Fallback(existing, next, Alternator.fromUnions(alternator))
    }
  }
}
