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

package zio.blocks.jwt

import zio.blocks.chunk.Chunk

/**
 * The `aud` claim of a token: the recipients it is meant for, as one string or
 * several.
 *
 * RFC 7519 §4.1.3 allows both forms. [[JwtAudience.Single]] renders as a JSON
 * string and [[JwtAudience.Multiple]] as a JSON array, and parsing keeps the
 * form the token used, so a one-element array reads as `Multiple`. Both forms
 * pass the `expectedAudience` check of [[JwtDecodeOptions]] when they equal or
 * contain the expected value.
 *
 * {{{
 * JwtClaims.render(JwtClaims(aud = Some(JwtAudience.multiple("notes-api", "billing-api"))))
 * // {"aud":["notes-api","billing-api"]}
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/jwt/#audience JWT]]
 */
sealed trait JwtAudience extends Product with Serializable

/** Builds the two forms of [[JwtAudience]]. */
object JwtAudience {

  /** One audience, rendered as a JSON string. */
  final case class Single(value: String) extends JwtAudience

  /**
   * Several audiences, rendered as a JSON array in the given order; it may be
   * empty or have one element.
   */
  final case class Multiple(values: Chunk[String]) extends JwtAudience

  /** Returns `Single(value)`. */
  def single(value: String): JwtAudience = Single(value)

  /** Returns `Multiple(values)`. */
  def multiple(values: Chunk[String]): JwtAudience = Multiple(values)

  /** Returns a `Multiple` of `values`, in order. */
  def multiple(values: String*): JwtAudience = Multiple(Chunk.from(values))
}
