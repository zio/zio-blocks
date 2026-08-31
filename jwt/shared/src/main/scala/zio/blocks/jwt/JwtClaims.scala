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
 * The set of claims carried inside a JWT payload.
 *
 * Registered claim names follow RFC 7519 §4.1. Additional application-defined
 * claims are collected in [[extra]], preserving their original JSON value type
 * as [[JwtValue]].
 *
 * NumericDate claims (`exp`, `nbf`, `iat`) accept integer and
 * fractional/exponent JSON numbers. Fractional values are deterministically
 * truncated toward zero (floor for non-negative values) to seconds. Non-finite,
 * negative, or out-of-range values are rejected.
 *
 * Use the methods on [[JwtClaims]] to deserialise and serialise.
 */
case class JwtClaims(
  /** Token issuer (`iss`). */
  iss: Option[String] = None,
  /** Subject (`sub`). */
  sub: Option[String] = None,
  /**
   * RFC 7519 §4.1.3: single string or array of strings, canonicalized as
   * [[JwtAudience]].
   */
  aud: Option[JwtAudience] = None,
  exp: Option[Long] = None,
  nbf: Option[Long] = None,
  iat: Option[Long] = None,
  jti: Option[String] = None,
  /** Non-reserved claims, preserving their original JSON value type. */
  extra: Map[String, JwtValue] = Map.empty
)

object JwtClaims {
  private val ReservedClaims = Set("iss", "sub", "aud", "exp", "nbf", "iat", "jti")

  def parse(base64urlEncoded: String): Either[JwtError, JwtClaims] =
    parse(base64urlEncoded, JwtLimits.Default)

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
