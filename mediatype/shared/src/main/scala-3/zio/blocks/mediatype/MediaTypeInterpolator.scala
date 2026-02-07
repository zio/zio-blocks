package zio.blocks.mediatype

import scala.quoted.*

trait MediaTypeInterpolator {

  extension (inline ctx: StringContext) {
    inline def mediaType(inline args: Any*): MediaType =
      ${ MediaTypeInterpolatorMacros.apply('ctx, 'args) }
  }
}

private[mediatype] object MediaTypeInterpolatorMacros {
  def apply(ctx: Expr[StringContext], args: Expr[Seq[Any]])(using Quotes): Expr[MediaType] = {
    import quotes.reflect.*

    val parts = ctx match {
      case '{ StringContext(${ Varargs(parts) }: _*) } =>
        parts.map {
          case Expr(s: String) => s
          case _               => report.errorAndAbort("mediaType interpolator requires literal strings only")
        }
      case _ =>
        report.errorAndAbort("mediaType interpolator requires literal strings only")
    }

    args match {
      case Varargs(Nil) => ()
      case Varargs(_)   =>
        report.errorAndAbort("mediaType interpolator does not support variable interpolation")
      case _ => ()
    }

    val mediaTypeStr = parts.mkString

    if (mediaTypeStr.isEmpty) {
      report.errorAndAbort("Invalid media type: cannot be empty")
    }

    val (typePart, paramsPart) = mediaTypeStr.indexOf(';') match {
      case -1 => (mediaTypeStr, "")
      case i  => (mediaTypeStr.substring(0, i).trim, mediaTypeStr.substring(i + 1))
    }

    val slashIdx = typePart.indexOf('/')
    if (slashIdx < 0) {
      report.errorAndAbort("Invalid media type: must contain '/' separator")
    }

    val mainType = typePart.substring(0, slashIdx).trim
    val subType  = typePart.substring(slashIdx + 1).trim

    if (mainType.isEmpty) {
      report.errorAndAbort("Invalid media type: main type cannot be empty")
    }
    if (subType.isEmpty) {
      report.errorAndAbort("Invalid media type: subtype cannot be empty")
    }

    val parameters =
      if (paramsPart.isEmpty) Map.empty[String, String]
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
      '{
        MediaType.parse(${ Expr(mediaTypeStr) }).getOrElse(MediaType(${ Expr(mainType) }, ${ Expr(subType) }))
      }
    } else {
      val paramExprs = parameters.toList.map { case (k, v) => '{ ${ Expr(k) } -> ${ Expr(v) } } }
      val paramsExpr = '{ Map(${ Varargs(paramExprs) }: _*) }
      '{
        MediaType.parse(${ Expr(typePart) }) match {
          case Right(predefined) => predefined.copy(parameters = $paramsExpr)
          case Left(_)           => MediaType(${ Expr(mainType) }, ${ Expr(subType) }, parameters = $paramsExpr)
        }
      }
    }
  }
}
