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

/**
 * A media type such as `text/plain`, with its main type, subtype and
 * parameters.
 *
 * `compressible` and `binary` describe the content, and `fileExtensions` lists
 * the registered file extensions, without dots. `extensions` holds extra
 * key/value pairs, and no predefined type sets them.
 */
final case class MediaType(
  mainType: String,
  subType: String,
  compressible: Boolean = false,
  binary: Boolean = false,
  fileExtensions: List[String] = Nil,
  extensions: Map[String, String] = Map.empty,
  parameters: Map[String, String] = Map.empty
) {

  /**
   * The main type and subtype joined by `/`, such as `text/plain`. Parameters
   * are left out, and the names keep the case they were given in.
   */
  val fullType: String = s"$mainType/$subType"

  /**
   * Returns `true` when this media type matches `other`. Either side may use
   * `*` for its main type or subtype, and the names compare ignoring case.
   * Every parameter of this media type must appear in `other` with the same
   * value (ignoring case), unless `ignoreParameters` is `true`; `other` may
   * have extra parameters, and parameter names compare exactly.
   */
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

/**
 * Parsing and lookup for `MediaType`. The predefined media types are listed in
 * `MediaTypes`.
 */
object MediaType {
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

  /**
   * Returns the predefined media type that lists the file extension `ext`, or
   * `None` when none does. When several predefined types list `ext`, a `text`
   * type wins, and otherwise the last one in `MediaTypes.allMediaTypes` wins,
   * so `xml` gives `text/xml` and `mp4` gives `video/mp4`. A leading `.` and
   * letter case are ignored (`.PNG` and `png` both give `image/png`),
   * surrounding spaces are not trimmed, and an empty string or a lone `.` gives
   * `None`.
   */
  def forFileExtension(ext: String): Option[MediaType] = {
    if (ext.isEmpty) return None
    val normalized = ext.stripPrefix(".").toLowerCase
    if (normalized.isEmpty) None else extensionMap.get(normalized)
  }

  /**
   * Parses a string such as `text/plain; charset=utf-8` into a `MediaType`, or
   * returns `Left` with a message when the string is empty, has no `/`, or has
   * an empty main type or subtype. When the type matches a predefined one,
   * ignoring case, the result is that predefined media type, with its own
   * lowercase names, plus the parsed parameters. Any other type gives a new
   * `MediaType` with the names as written and `compressible` and `binary` set
   * to `false`. Parameter names are lowercased, values keep their case, and
   * entries without `=` are dropped.
   */
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

  /**
   * Parses `s` as `parse` does and returns the media type. Throws
   * `java.lang.IllegalArgumentException`, with the message from the `Left` that
   * `parse` would return, when `s` is invalid.
   */
  def unsafeFromString(s: String): MediaType =
    parse(s) match {
      case Right(mt) => mt
      case Left(err) => throw new IllegalArgumentException(err)
    }
}
