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
import scala.collection.mutable.ListBuffer

/** The set of claims carried inside a JWT payload.
 *
 *  Registered claim names follow RFC 7519 §4.1. Additional application-defined claims
 *  are collected in [[extra]], preserving their original JSON value type.
 *
 *  Use [[JwtClaims$.parse]] to deserialise and [[JwtClaims$.render]] to serialise.
 */
case class JwtClaims(
  /** Token issuer (`iss`). */
  iss: Option[String] = None,
  /** Subject (`sub`). */
  sub: Option[String] = None,
  /** RFC 7519 §4.1.3: single string or array of strings. */
  aud: Option[Either[String, List[String]]] = None,
  exp: Option[Long] = None,
  nbf: Option[Long] = None,
  iat: Option[Long] = None,
  jti: Option[String] = None,
  /** Non-reserved claims, preserving their original JSON value type. */
  extra: Map[String, JwtJson.Value] = Map.empty
)

object JwtClaims {
  private[this] val ReservedClaims = Set("iss", "sub", "aud", "exp", "nbf", "iat", "jti")

  def parse(base64urlEncoded: String): Either[JwtError, JwtClaims] =
    for {
      bytes  <- Base64Url.decode(base64urlEncoded)
      fields <- JwtJson.parseObject(JwtText.decodeUtf8(bytes))
      iss    <- JwtJson.optionalString(fields, "iss")
      sub    <- JwtJson.optionalString(fields, "sub")
      aud    <- JwtJson.optionalAud(fields, "aud")
      exp    <- JwtJson.optionalLong(fields, "exp")
      nbf    <- JwtJson.optionalLong(fields, "nbf")
      iat    <- JwtJson.optionalLong(fields, "iat")
      jti    <- JwtJson.optionalString(fields, "jti")
      extra  <- parseExtra(fields)
    } yield JwtClaims(iss = iss, sub = sub, aud = aud, exp = exp, nbf = nbf, iat = iat, jti = jti, extra = extra)

  def render(c: JwtClaims): String = {
    val fields = ListBuffer.empty[String]

    c.iss.foreach(value => fields += JwtJson.renderField("iss", JwtJson.StringValue(value)))
    c.sub.foreach(value => fields += JwtJson.renderField("sub", JwtJson.StringValue(value)))
    c.aud.foreach {
      case Left(single)   => fields += JwtJson.renderField("aud", JwtJson.StringValue(single))
      case Right(strings) => fields += JwtJson.renderField("aud", JwtJson.ArrayValue(strings.map(JwtJson.StringValue(_))))
    }
    c.exp.foreach(value => fields += JwtJson.renderField("exp", JwtJson.NumberValue(value.toString)))
    c.nbf.foreach(value => fields += JwtJson.renderField("nbf", JwtJson.NumberValue(value.toString)))
    c.iat.foreach(value => fields += JwtJson.renderField("iat", JwtJson.NumberValue(value.toString)))
    c.jti.foreach(value => fields += JwtJson.renderField("jti", JwtJson.StringValue(value)))
    c.extra.toList.sortBy(_._1).foreach { case (key, value) =>
      fields += JwtJson.renderField(key, value)
    }

    fields.mkString("{", ",", "}")
  }

  private[this] def parseExtra(fields: Map[String, JwtJson.Value]): Either[JwtError, Map[String, JwtJson.Value]] = {
    val builder  = mutable.Map.empty[String, JwtJson.Value]
    val iterator = fields.iterator

    while (iterator.hasNext) {
      val next = iterator.next()
      if (!ReservedClaims.contains(next._1)) builder += next._1 -> next._2
    }

    Right(builder.toMap)
  }
}
