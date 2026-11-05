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

import scala.collection.mutable
import zio.blocks.chunk.ChunkBuilder

/**
 * The claims of a token: its JSON payload, with a typed field for each claim
 * registered by RFC 7519 §4.1 and a map for the others.
 *
 * Build one to pass to `Jwt.sign`; `Jwt.decode` returns one after the signature
 * and claim checks pass. Every field is optional. `Jwt.decode` checks `exp` and
 * `nbf` whenever they are present, against the clock and skew of
 * [[JwtDecodeOptions]], and checks `iss` and `aud` only when the options name
 * an expected issuer or audience.
 *
 * The NumericDate claims (`exp`, `nbf`, `iat`) are whole seconds since the Unix
 * epoch. Parsing accepts integer, fractional and exponent JSON numbers and
 * rounds them down to whole seconds (`1767225600.9` and `1.7672256e9` read as
 * `1767225600`); a negative value or one beyond `Long.MaxValue` is rejected
 * with `InvalidClaim`.
 *
 * {{{
 * val claims = JwtClaims(
 *   iss = Some("https://auth.example.com"),
 *   sub = Some("user-42"),
 *   aud = Some(JwtAudience.single("notes-api")),
 *   exp = Some(1767225600L),
 *   extra = Map("scope" -> JwtValue.Str("notes:read"))
 * )
 *
 * JwtClaims.render(claims)
 * // {"iss":"https://auth.example.com","sub":"user-42","aud":"notes-api","exp":1767225600,"scope":"notes:read"}
 * }}}
 *
 * @param iss
 *   the issuer (`iss`), such as `"https://auth.example.com"`
 * @param sub
 *   the subject (`sub`), usually the user or client the token is about
 * @param aud
 *   the audience (`aud`): one recipient or several, as [[JwtAudience]]
 * @param exp
 *   the expiration time (`exp`), in seconds since the Unix epoch
 * @param nbf
 *   the time before which the token is not valid (`nbf`), in seconds since the
 *   Unix epoch
 * @param iat
 *   the time the token was issued (`iat`), in seconds since the Unix epoch;
 *   `Jwt.decode` does not check it
 * @param jti
 *   the token's unique id (`jti`)
 * @param extra
 *   every other claim, by name, with its JSON value as a [[JwtValue]]. Do not
 *   put a registered name (`iss`, `sub`, `aud`, `exp`, `nbf`, `iat`, `jti`)
 *   here: `Jwt.sign` writes it after the typed fields, so when the typed field
 *   is also set the claim appears twice and this module's decoder reads the
 *   later value, a known bug.
 *
 * @see
 *   [[https://zioblocks.com/reference/jwt/#claims JWT]]
 */
case class JwtClaims(
  iss: Option[String] = None,
  sub: Option[String] = None,
  aud: Option[JwtAudience] = None,
  exp: Option[Long] = None,
  nbf: Option[Long] = None,
  iat: Option[Long] = None,
  jti: Option[String] = None,
  extra: Map[String, JwtValue] = Map.empty
)

/**
 * Reads and writes [[JwtClaims]] as a token's payload JSON.
 *
 * @see
 *   [[https://zioblocks.com/reference/jwt/#claims JWT]]
 */
object JwtClaims {
  private val ReservedClaims = Set("iss", "sub", "aud", "exp", "nbf", "iat", "jti")

  /**
   * Parses a token's payload segment (Base64URL, not raw JSON) into claims,
   * with [[JwtLimits.Default]]. It checks nothing but the format: no signature,
   * no `exp` or `nbf`.
   *
   * Returns `InvalidToken` for invalid Base64URL or JSON, or a payload that is
   * not a JSON object, and `InvalidClaim` for a registered claim of the wrong
   * type or a NumericDate out of range. A segment or JSON over the limits gives
   * a size or shape error such as `SegmentTooLarge`, `JsonTooLarge`, `TooDeep`,
   * `TooManyFields` or `TooManyElements`. A registered claim that is JSON
   * `null` reads as absent, and when a name appears twice the later value wins.
   */
  def parse(base64urlEncoded: String): Either[JwtError, JwtClaims] =
    parse(base64urlEncoded, JwtLimits.Default)

  /**
   * Parses a token's payload segment into claims, as the one-argument `parse`
   * does, with `limits` (all of them but `maxTokenChars`).
   */
  def parse(base64urlEncoded: String, limits: JwtLimits): Either[JwtError, JwtClaims] =
    for {
      _      <- checkSegmentSize(base64urlEncoded, limits)
      bytes  <- Base64Url.decode(base64urlEncoded)
      jsonStr = JwtText.decodeUtf8(bytes)
      _      <- checkJsonSize(jsonStr, limits)
      fields <- JwtJson.parseObject(jsonStr, limits)
      iss    <- JwtJson.optionalString(fields, "iss")
      sub    <- JwtJson.optionalString(fields, "sub")
      aud    <- JwtJson.optionalAud(fields, "aud")
      exp    <- JwtJson.optionalNumericDate(fields, "exp")
      nbf    <- JwtJson.optionalNumericDate(fields, "nbf")
      iat    <- JwtJson.optionalNumericDate(fields, "iat")
      jti    <- JwtJson.optionalString(fields, "jti")
      extra  <- parseExtra(fields)
    } yield JwtClaims(iss = iss, sub = sub, aud = aud, exp = exp, nbf = nbf, iat = iat, jti = jti, extra = extra)

  /**
   * Renders `c` as compact JSON, the payload `Jwt.sign` encodes. The registered
   * claims come first, in the order `iss`, `sub`, `aud`, `exp`, `nbf`, `iat`,
   * `jti`, absent ones left out, then the `extra` claims sorted by name. It
   * validates nothing: a registered name in `extra` is written after the typed
   * fields, and twice when its typed field is also set (a known bug), and the
   * text of a `JwtValue.Num` is written as it is.
   */
  def render(c: JwtClaims): String = {
    val fields = ChunkBuilder.make[String]()

    c.iss.foreach(value => fields += JwtJson.renderField("iss", JwtValue.Str(value)))
    c.sub.foreach(value => fields += JwtJson.renderField("sub", JwtValue.Str(value)))
    c.aud.foreach {
      case JwtAudience.Single(single)   => fields += JwtJson.renderField("aud", JwtValue.Str(single))
      case JwtAudience.Multiple(values) =>
        fields += JwtJson.renderField("aud", JwtValue.Arr(values.map(s => JwtValue.Str(s): JwtValue)))
    }
    c.exp.foreach(value => fields += JwtJson.renderField("exp", JwtValue.Num(value.toString)))
    c.nbf.foreach(value => fields += JwtJson.renderField("nbf", JwtValue.Num(value.toString)))
    c.iat.foreach(value => fields += JwtJson.renderField("iat", JwtValue.Num(value.toString)))
    c.jti.foreach(value => fields += JwtJson.renderField("jti", JwtValue.Str(value)))
    c.extra.toList.sortBy(_._1).foreach { case (key, value) =>
      fields += JwtJson.renderField(key, value)
    }

    fields.result().mkString("{", ",", "}")
  }

  private def parseExtra(fields: Map[String, JwtValue]): Either[JwtError, Map[String, JwtValue]] = {
    val builder  = mutable.Map.empty[String, JwtValue]
    val iterator = fields.iterator

    while (iterator.hasNext) {
      val next = iterator.next()
      if (!ReservedClaims.contains(next._1)) builder += next._1 -> next._2
    }

    Right(builder.toMap)
  }

  private def checkSegmentSize(s: String, limits: JwtLimits): Either[JwtError, Unit] =
    if (s.length > limits.maxSegmentChars) Left(JwtError.SegmentTooLarge(s.length, limits.maxSegmentChars))
    else Right(())

  private def checkJsonSize(s: String, limits: JwtLimits): Either[JwtError, Unit] =
    if (s.length > limits.maxJsonChars) Left(JwtError.JsonTooLarge(s.length, limits.maxJsonChars))
    else Right(())
}
