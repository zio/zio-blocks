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

package zio.http

import zio.blocks.mediatype.{MediaType, MediaTypes}

final case class ContentType(
  mediaType: MediaType,
  boundary: Option[Boundary] = None,
  charset: Option[Charset] = None
) {
  def render: String = {
    val sb = new StringBuilder(mediaType.fullType)
    charset.foreach(c => sb.append("; charset=").append(c.name))
    boundary.foreach(b => sb.append("; boundary=").append(b.value))
    sb.toString
  }
}

object ContentType {

  def parse(s: String): Either[String, ContentType] = {
    if (s.isEmpty) return Left("Invalid content type: cannot be empty")

    val semiIdx                     = s.indexOf(';')
    val (mediaTypePart, paramsPart) =
      if (semiIdx < 0) (s.trim, "")
      else (s.substring(0, semiIdx).trim, s.substring(semiIdx + 1))

    MediaType.parse(mediaTypePart) match {
      case Left(err) => Left(err)
      case Right(mt) =>
        var charset: Option[Charset]   = None
        var boundary: Option[Boundary] = None

        if (paramsPart.nonEmpty) {
          val params = paramsPart.split(';')
          var i      = 0
          while (i < params.length) {
            val param = params(i).trim
            if (param.nonEmpty) {
              val eqIdx = param.indexOf('=')
              if (eqIdx >= 0) {
                val key   = param.substring(0, eqIdx).trim.toLowerCase
                val value = param.substring(eqIdx + 1).trim
                if (key == "charset") {
                  charset = Charset.fromString(value)
                } else if (key == "boundary") {
                  boundary = Some(Boundary(value))
                }
              }
            }
            i += 1
          }
        }

        Right(ContentType(mt, boundary = boundary, charset = charset))
    }
  }

  val `application/json`: ContentType         = ContentType(MediaTypes.application.`json`)
  val `text/plain`: ContentType               = ContentType(MediaTypes.text.`plain`)
  val `text/html`: ContentType                = ContentType(MediaTypes.text.`html`)
  val `application/octet-stream`: ContentType = ContentType(MediaTypes.application.`octet-stream`)
}
