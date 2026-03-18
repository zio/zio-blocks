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

package zio.blocks.mediatype

final case class MediaType(
  mainType: String,
  subType: String,
  compressible: Boolean = false,
  binary: Boolean = false,
  fileExtensions: List[String] = Nil,
  extensions: Map[String, String] = Map.empty,
  parameters: Map[String, String] = Map.empty
) {
  val fullType: String = s"$mainType/$subType"

  def matches(other: MediaType, ignoreParameters: Boolean = false): Boolean = {
    val mainTypeMatches = mainType == "*" || other.mainType == "*" ||
      mainType.equalsIgnoreCase(other.mainType)
    val subTypeMatches = subType == "*" || other.subType == "*" ||
      subType.equalsIgnoreCase(other.subType)
    val parametersMatch = ignoreParameters ||
      parameters.forall { case (key, value) =>
        other.parameters.get(key).exists(_.equalsIgnoreCase(value))
      }

    mainTypeMatches && subTypeMatches && parametersMatch
  }
}

object MediaType {
  lazy val any: MediaType          = MediaTypes.any
  val application: MediaTypes.application.type   = MediaTypes.application
  val audio: MediaTypes.audio.type               = MediaTypes.audio
  val chemical: MediaTypes.chemical.type         = MediaTypes.chemical
  val font: MediaTypes.font.type                 = MediaTypes.font
  val image: MediaTypes.image.type               = MediaTypes.image
  val message: MediaTypes.message.type           = MediaTypes.message
  val model: MediaTypes.model.type               = MediaTypes.model
  val multipart: MediaTypes.multipart.type       = MediaTypes.multipart
  val text: MediaTypes.text.type                 = MediaTypes.text
  val video: MediaTypes.video.type               = MediaTypes.video
  val x_conference: MediaTypes.x_conference.type = MediaTypes.x_conference
  val x_shader: MediaTypes.x_shader.type         = MediaTypes.x_shader

  private lazy val contentTypeMap: Map[String, MediaType] =
    MediaTypes.allMediaTypes.map(m => m.fullType.toLowerCase -> m).toMap

  private lazy val extensionMap: Map[String, MediaType] = {
    val allExtensionMappings = MediaTypes.allMediaTypes
      .flatMap(m => m.fileExtensions.map(ext => ext.toLowerCase -> m))
      .toMap

    val textTypeExtensionMappings = MediaTypes.text.all
      .flatMap(m => m.fileExtensions.map(ext => ext.toLowerCase -> m))
      .toMap

    allExtensionMappings ++ textTypeExtensionMappings
  }

  def forFileExtension(ext: String): Option[MediaType] = {
    if (ext.isEmpty) return None
    val normalized = ext.stripPrefix(".").toLowerCase
    if (normalized.isEmpty) None else extensionMap.get(normalized)
  }

  def parse(s: String): Either[String, MediaType] = {
    if (s.isEmpty) return Left("Invalid media type: cannot be empty")

    val (typePart, paramsPart) = s.indexOf(';') match {
      case -1 => (s, "")
      case i  => (s.substring(0, i).trim, s.substring(i + 1))
    }

    val slashIdx = typePart.indexOf('/')
    if (slashIdx < 0) return Left("Invalid media type: must contain '/' separator")

    val mainType = typePart.substring(0, slashIdx).trim
    val subType  = typePart.substring(slashIdx + 1).trim

    if (mainType.isEmpty) return Left("Invalid media type: main type cannot be empty")
    if (subType.isEmpty) return Left("Invalid media type: subtype cannot be empty")

    val parameters    = parseParameters(paramsPart)
    val fullTypeLower = s"$mainType/$subType".toLowerCase

    contentTypeMap.get(fullTypeLower) match {
      case Some(predefined) if parameters.isEmpty => Right(predefined)
      case Some(predefined)                       => Right(predefined.copy(parameters = parameters))
      case None                                   => Right(MediaType(mainType, subType, parameters = parameters))
    }
  }

  private[this] def parseParameters(s: String): Map[String, String] = {
    if (s.isEmpty) return Map.empty

    s.split(';')
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap { param =>
        param.split("=", 2) match {
          case Array(key, value) => Some(key.trim.toLowerCase -> value.trim)
          case _                 => None
        }
      }
      .toMap
  }

  def unsafeFromString(s: String): MediaType =
    parse(s) match {
      case Right(mt) => mt
      case Left(err) => throw new IllegalArgumentException(err)
    }
}
