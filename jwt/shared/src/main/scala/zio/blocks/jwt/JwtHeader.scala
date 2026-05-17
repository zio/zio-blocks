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

import scala.collection.mutable.ListBuffer

case class JwtHeader(alg: Algorithm, typ: String = "JWT", kid: Option[String] = None)

object JwtHeader {
  def parse(base64urlEncoded: String): Either[JwtError, JwtHeader] =
    for {
      bytes  <- Base64Url.decode(base64urlEncoded)
      fields <- JwtJson.parseObject(JwtText.decodeUtf8(bytes))
      algRaw <- JwtJson.requiredString(fields, "alg")
      alg    <- Algorithm.fromString(algRaw).toRight[JwtError](JwtError.UnsupportedAlgorithm(algRaw))
      typ    <- JwtJson.optionalString(fields, "typ")
      kid    <- JwtJson.optionalString(fields, "kid")
    } yield JwtHeader(alg = alg, typ = typ.getOrElse("JWT"), kid = kid)

  def render(h: JwtHeader): String = {
    val fields = ListBuffer.empty[String]
    fields += JwtJson.renderField("alg", JwtJson.StringValue(h.alg.name))
    fields += JwtJson.renderField("typ", JwtJson.StringValue(h.typ))
    h.kid.foreach(value => fields += JwtJson.renderField("kid", JwtJson.StringValue(value)))
    fields.mkString("{", ",", "}")
  }
}
