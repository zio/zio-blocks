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

case class JwtHeader(alg: Algorithm, typ: String = "JWT", kid: Option[String] = None)

object JwtHeader {
  def parse(base64urlEncoded: String): Either[JwtError, JwtHeader] =
    parse(base64urlEncoded, JwtLimits.Default)

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
