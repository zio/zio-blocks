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

/**
 * Holder of the [[EndpointUnionErrorBuilder.ErrorBuilder]] type class that
 * `Endpoint#orOutError` uses to extend an endpoint's error channel.
 */
object EndpointUnionErrorBuilder {

  /**
   * Builds the error codec that `Endpoint#orOutError` stores.
   *
   * `add` receives the endpoint's current error codec (`existing`) and the new
   * one (`next`) and returns the codec that becomes the endpoint's error codec;
   * its error type is `Out`.
   *
   * The Scala 3 companion provides two instances. `initialError` applies while
   * the error type is `Unit` and returns `next` as it is. `unionError` returns
   * a [[HttpCodec.Fallback]] of `existing` and `next` whose error type is the
   * union of the two types. The Scala 2 companion provides none, so
   * `Endpoint#orOutError` does not compile there; use `Endpoint#outError`.
   */
  trait ErrorBuilder[Err, E2] {

    /** The error type of the codec that `add` returns. */
    type Out

    /**
     * Returns the new error codec for an endpoint whose current error codec is
     * `existing` and which gains `next`.
     */
    def add(
      existing: HttpCodec[CodecKind.Response, Err],
      next: HttpCodec[CodecKind.Response, E2]
    ): HttpCodec[CodecKind.Response, Out]
  }

  /** The [[ErrorBuilder]] type and its instances. */
  object ErrorBuilder {

    /** An [[ErrorBuilder]] whose `Out` type is fixed to `O`. */
    type WithOut[Err, E2, O] = ErrorBuilder[Err, E2] { type Out = O }

    /**
     * Applies while the existing error type is `Unit`: returns `next`
     * unchanged, so the first `orOutError` replaces the initial error codec.
     * Because the test is on the type only, a later `orOutError` after one
     * whose error type is `Unit` replaces that codec too.
     */
    given initialError[E2]: ErrorBuilder[Unit, E2] with {
      type Out = E2

      def add(
        existing: HttpCodec[CodecKind.Response, Unit],
        next: HttpCodec[CodecKind.Response, E2]
      ): HttpCodec[CodecKind.Response, E2] = next
    }

    /**
     * Returns a [[HttpCodec.Fallback]] with `existing` on the left and `next`
     * on the right, and the union `Err | E2` as the error type, using
     * [[zio.blocks.combinators.Unions]]. It does not compile when `Err` and
     * `E2` share a type. To split a union value into its sides, see
     * [[Alternator#separate]].
     */
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
