package zio.blocks.mediatype

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait MediaTypeInterpolator {

  implicit class MediaTypeStringContext(val sc: StringContext) {
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
