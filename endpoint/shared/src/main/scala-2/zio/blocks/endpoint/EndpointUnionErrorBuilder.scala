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
  }
}
