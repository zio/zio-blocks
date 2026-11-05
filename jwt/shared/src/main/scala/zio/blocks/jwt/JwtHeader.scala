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

import zio.blocks.chunk.ChunkBuilder

/**
 * The JOSE header of a token, its first segment: the signing algorithm, the
 * media type `typ` and an optional key id `kid`.
 *
 * `Jwt.sign` writes `JwtHeader(alg)` unless it is given a header, and
 * `Jwt.decode` compares the header's `alg` with the expected algorithm; it does
 * not check the value of `typ` and does not use `kid` to pick a key. Parsing
 * reads `alg`, `typ` and `kid` and ignores the other header fields.
 *
 * {{{
 * JwtHeader.render(JwtHeader(Algorithm.ES256, kid = Some("key-2026-01")))
 * // {"alg":"ES256","typ":"JWT","kid":"key-2026-01"}
 * }}}
 *
 * @param alg
 *   the signing algorithm, written as the `alg` field
 * @param typ
 *   the media type of the token, written as the `typ` field (default `"JWT"`);
 *   a parsed header without `typ`, or with `"typ":null`, gets `"JWT"`
 * @param kid
 *   the key id, written as the `kid` field when set, for example to tell a
 *   verifier which of its keys to use
 *
 * @see
 *   [[https://zioblocks.com/reference/jwt/ JWT]]
 */
case class JwtHeader(alg: Algorithm, typ: String = "JWT", kid: Option[String] = None)

/** Reads and writes [[JwtHeader]] as a token's header JSON. */
object JwtHeader {

  /**
   * Parses a token's header segment (Base64URL, not raw JSON), with
   * [[JwtLimits.Default]].
   *
   * Returns `MissingClaim("alg")` when there is no `alg`,
   * `InvalidClaim("alg", "must be a string")` when it is not a string, and
   * `UnsupportedAlgorithm` with the raw value for a name that
   * `Algorithm.fromString` does not know, such as `"none"`. A `typ` or `kid`
   * that is not a string gives `InvalidClaim` with "must be a string", and a
   * segment or JSON over the limits gives a size or shape error such as
   * `SegmentTooLarge` or `TooDeep`. Invalid Base64URL or JSON gives
   * `InvalidToken`.
   */
  def parse(base64urlEncoded: String): Either[JwtError, JwtHeader] =
    parse(base64urlEncoded, JwtLimits.Default)

  /**
   * Parses a token's header segment, as the one-argument `parse` does, with
   * `limits` (all of them but `maxTokenChars`).
   */
  def parse(base64urlEncoded: String, limits: JwtLimits): Either[JwtError, JwtHeader] =
    for {
      _      <- checkSegmentSize(base64urlEncoded, limits)
      bytes  <- Base64Url.decode(base64urlEncoded)
      jsonStr = JwtText.decodeUtf8(bytes)
      _      <- checkJsonSize(jsonStr, limits)
      fields <- JwtJson.parseObject(jsonStr, limits)
      algRaw <- JwtJson.requiredString(fields, "alg")
      alg    <- Algorithm.fromString(algRaw).toRight[JwtError](JwtError.UnsupportedAlgorithm(algRaw))
      typ    <- JwtJson.optionalString(fields, "typ")
      kid    <- JwtJson.optionalString(fields, "kid")
    } yield JwtHeader(alg = alg, typ = typ.getOrElse("JWT"), kid = kid)

  /**
   * Renders `h` as compact JSON in the order `alg`, `typ`, `kid`, leaving out
   * `kid` when it is `None`.
   */
  def render(h: JwtHeader): String = {
    val fields = ChunkBuilder.make[String]()
    fields += JwtJson.renderField("alg", JwtValue.Str(h.alg.name))
    fields += JwtJson.renderField("typ", JwtValue.Str(h.typ))
    h.kid.foreach(value => fields += JwtJson.renderField("kid", JwtValue.Str(value)))
    fields.result().mkString("{", ",", "}")
  }

  private def checkSegmentSize(s: String, limits: JwtLimits): Either[JwtError, Unit] =
    if (s.length > limits.maxSegmentChars) Left(JwtError.SegmentTooLarge(s.length, limits.maxSegmentChars))
    else Right(())

  private def checkJsonSize(s: String, limits: JwtLimits): Either[JwtError, Unit] =
    if (s.length > limits.maxJsonChars) Left(JwtError.JsonTooLarge(s.length, limits.maxJsonChars))
    else Right(())
}
