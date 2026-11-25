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

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
 * Provides the `mediaType` string interpolator. The package object
 * `zio.blocks.mediatype` extends this trait, so importing the package (`import
 * zio.blocks.mediatype._` on Scala 2, `import zio.blocks.mediatype.*` on Scala
 * 3) brings the interpolator into scope.
 */
trait MediaTypeInterpolator {

  /**
   * Adds the `mediaType` method to `StringContext`. Scala 2 needs this implicit
   * class to attach the interpolator, whereas Scala 3 defines the method as an
   * extension.
   */
  implicit class MediaTypeStringContext(val sc: StringContext) {

    /**
     * Returns the media type written in the literal string, such as
     * `mediaType"text/plain; charset=utf-8"`. The string is checked at compile
     * time: an empty string, a missing `/`, an empty main type or subtype, or
     * an interpolated value makes the compilation fail.
     */
    def mediaType(args: Any*): MediaType = macro MediaTypeMacros.mediaTypeImpl
  }
}

private[mediatype] object MediaTypeMacros {

  def mediaTypeImpl(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[MediaType] = {
    import c.universe._

    val parts = c.prefix.tree match {
      case Apply(_, List(Apply(_, rawParts))) =>
        rawParts.map {
          case Literal(Constant(part: String)) => part
          case _                               => c.abort(c.enclosingPosition, "mediaType interpolator requires literal strings only")
        }
      case _ => c.abort(c.enclosingPosition, "mediaType interpolator requires literal strings only")
    }

    if (args.nonEmpty) {
      c.abort(c.enclosingPosition, "mediaType interpolator does not support variable interpolation")
    }

    val mediaTypeStr = parts.mkString

    if (mediaTypeStr.isEmpty) {
      c.abort(c.enclosingPosition, "Invalid media type: cannot be empty")
    }

    val (typePart, paramsPart) = mediaTypeStr.indexOf(';') match {
      case -1 => (mediaTypeStr, "")
      case i  => (mediaTypeStr.substring(0, i).trim, mediaTypeStr.substring(i + 1))
    }

    val slashIdx = typePart.indexOf('/')
    if (slashIdx < 0) {
      c.abort(c.enclosingPosition, "Invalid media type: must contain '/' separator")
    }

    val mainType = typePart.substring(0, slashIdx).trim
    val subType  = typePart.substring(slashIdx + 1).trim

    if (mainType.isEmpty) {
      c.abort(c.enclosingPosition, "Invalid media type: main type cannot be empty")
    }
    if (subType.isEmpty) {
      c.abort(c.enclosingPosition, "Invalid media type: subtype cannot be empty")
    }

    val parameters: Map[String, String] =
      if (paramsPart.isEmpty) Map.empty
      else {
        paramsPart
          .split(';')
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

    if (parameters.isEmpty) {
      c.Expr[MediaType](
        q"""_root_.zio.blocks.mediatype.MediaType.parse($mediaTypeStr).getOrElse(
              _root_.zio.blocks.mediatype.MediaType($mainType, $subType)
            )"""
      )
    } else {
      val paramEntries = parameters.toList.map { case (k, v) => q"$k -> $v" }
      c.Expr[MediaType](
        q"""_root_.zio.blocks.mediatype.MediaType.parse($typePart) match {
              case _root_.scala.Right(predefined) => predefined.copy(parameters = _root_.scala.collection.immutable.Map(..$paramEntries))
              case _root_.scala.Left(_) => _root_.zio.blocks.mediatype.MediaType($mainType, $subType, parameters = _root_.scala.collection.immutable.Map(..$paramEntries))
            }"""
      )
    }
  }
}
