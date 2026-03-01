package zio.http.headers

import zio.blocks.chunk.Chunk
import zio.blocks.mediatype.MediaType
import zio.http._

// --- 1. Accept ---

final case class Accept(mediaRanges: Chunk[Accept.MediaRange]) extends Header {
  def headerName: String    = Accept.name
  def renderedValue: String = Accept.render(this)
}

object Accept extends Header.Typed[Accept] {
  val name: String = "accept"

  final case class MediaRange(mediaType: MediaType, quality: Double = 1.0)

  def parse(value: String): Either[String, Accept] = {
    if (value.trim.isEmpty) return Left("Accept header cannot be empty")
    val parts  = value.split(',').map(_.trim).filter(_.nonEmpty)
    val ranges = Chunk.fromArray(parts.map(parseMediaRange))
    val errors = ranges.collect { case Left(err) => err }
    if (errors.nonEmpty) Left(errors.mkString("; "))
    else Right(Accept(ranges.collect { case Right(mr) => mr }))
  }

  def render(h: Accept): String =
    h.mediaRanges.map { mr =>
      if (mr.quality == 1.0) mr.mediaType.fullType
      else s"${mr.mediaType.fullType};q=${mr.quality}"
    }.mkString(", ")

  private def parseMediaRange(s: String): Either[String, MediaRange] = {
    val semiIdx = s.indexOf(';')
    if (semiIdx < 0) {
      MediaType.parse(s.trim).map(mt => MediaRange(mt))
    } else {
      val mediaStr  = s.substring(0, semiIdx).trim
      val paramsStr = s.substring(semiIdx + 1).trim
      val quality   = parseQuality(paramsStr)
      MediaType.parse(mediaStr).map(mt => MediaRange(mt, quality))
    }
  }

  private def parseQuality(params: String): Double = {
    val qIdx = params.indexOf("q=")
    if (qIdx >= 0) {
      try params.substring(qIdx + 2).trim.toDouble
      catch { case _: NumberFormatException => 1.0 }
    } else 1.0
  }
}

// --- 2. AcceptEncoding ---

sealed trait AcceptEncoding extends Header {
  def headerName: String    = AcceptEncoding.name
  def renderedValue: String = AcceptEncoding.render(this)
}

object AcceptEncoding extends Header.Typed[AcceptEncoding] {
  val name: String = "accept-encoding"

  final case class GZip(weight: Option[Double] = None)     extends AcceptEncoding
  final case class Deflate(weight: Option[Double] = None)  extends AcceptEncoding
  final case class Br(weight: Option[Double] = None)       extends AcceptEncoding
  final case class Compress(weight: Option[Double] = None) extends AcceptEncoding
  final case class Identity(weight: Option[Double] = None) extends AcceptEncoding
  final case class Any(weight: Option[Double] = None)      extends AcceptEncoding

  final case class Multiple(values: Chunk[AcceptEncoding]) extends AcceptEncoding

  def parse(value: String): Either[String, AcceptEncoding] = {
    val parts = value.split(',').map(_.trim).filter(_.nonEmpty)
    if (parts.isEmpty) Left(s"Invalid accept-encoding: $value")
    else if (parts.length == 1) Right(parseSingle(parts(0)))
    else Right(Multiple(Chunk.fromArray(parts.map(parseSingle))))
  }

  def render(h: AcceptEncoding): String = h match {
    case GZip(w)     => renderWithWeight("gzip", w)
    case Deflate(w)  => renderWithWeight("deflate", w)
    case Br(w)       => renderWithWeight("br", w)
    case Compress(w) => renderWithWeight("compress", w)
    case Identity(w) => renderWithWeight("identity", w)
    case Any(w)      => renderWithWeight("*", w)
    case Multiple(v) => v.map(render).mkString(", ")
  }

  private def parseSingle(s: String): AcceptEncoding = {
    val qIdx          = s.indexOf(";q=")
    val (raw, weight) =
      if (qIdx >= 0) {
        val w =
          try Some(s.substring(qIdx + 3).trim.toDouble)
          catch { case _: NumberFormatException => None }
        (s.substring(0, qIdx).trim, w)
      } else (s.trim, None)

    raw.toLowerCase match {
      case "gzip"     => GZip(weight)
      case "deflate"  => Deflate(weight)
      case "br"       => Br(weight)
      case "compress" => Compress(weight)
      case "identity" => Identity(weight)
      case "*"        => Any(weight)
      case _          => GZip(weight) // fallback, unknown treated as gzip
    }
  }

  private def renderWithWeight(name: String, weight: Option[Double]): String =
    weight match {
      case Some(w) => s"$name;q=$w"
      case None    => name
    }
}

// --- 3. AcceptLanguage ---

final case class AcceptLanguage(languages: Chunk[AcceptLanguage.LanguageRange]) extends Header {
  def headerName: String    = AcceptLanguage.name
  def renderedValue: String = AcceptLanguage.render(this)
}

object AcceptLanguage extends Header.Typed[AcceptLanguage] {
  val name: String = "accept-language"

  final case class LanguageRange(tag: String, quality: Double = 1.0)

  def parse(value: String): Either[String, AcceptLanguage] = {
    if (value.trim.isEmpty) return Left("Accept-Language cannot be empty")
    val parts  = value.split(',').map(_.trim).filter(_.nonEmpty)
    val ranges = Chunk.fromArray(parts.map(parseLangRange))
    Right(AcceptLanguage(ranges))
  }

  def render(h: AcceptLanguage): String =
    h.languages.map { lr =>
      if (lr.quality == 1.0) lr.tag
      else s"${lr.tag};q=${lr.quality}"
    }.mkString(", ")

  private def parseLangRange(s: String): LanguageRange = {
    val qIdx = s.indexOf(";q=")
    if (qIdx >= 0) {
      val tag    = s.substring(0, qIdx).trim
      val weight =
        try s.substring(qIdx + 3).trim.toDouble
        catch { case _: NumberFormatException => 1.0 }
      LanguageRange(tag, weight)
    } else {
      LanguageRange(s.trim)
    }
  }
}

// --- 4. AcceptRanges ---

sealed trait AcceptRanges extends Header {
  def headerName: String    = AcceptRanges.name
  def renderedValue: String = AcceptRanges.render(this)
}

object AcceptRanges extends Header.Typed[AcceptRanges] {
  val name: String = "accept-ranges"

  case object Bytes extends AcceptRanges
  case object None_ extends AcceptRanges

  def parse(value: String): Either[String, AcceptRanges] =
    value.trim.toLowerCase match {
      case "bytes" => Right(Bytes)
      case "none"  => Right(None_)
      case other   => Left(s"Invalid accept-ranges: $other")
    }

  def render(h: AcceptRanges): String = h match {
    case Bytes => "bytes"
    case None_ => "none"
  }
}

// --- 5. AcceptPatch ---

final case class AcceptPatch(mediaTypes: Chunk[MediaType]) extends Header {
  def headerName: String    = AcceptPatch.name
  def renderedValue: String = AcceptPatch.render(this)
}

object AcceptPatch extends Header.Typed[AcceptPatch] {
  val name: String = "accept-patch"

  def parse(value: String): Either[String, AcceptPatch] = {
    if (value.trim.isEmpty) return Left("Accept-Patch header cannot be empty")
    val parts  = value.split(',').map(_.trim).filter(_.nonEmpty)
    val parsed = parts.flatMap(s => MediaType.parse(s).toOption)
    if (parsed.isEmpty) Left(s"Invalid accept-patch: $value")
    else Right(AcceptPatch(Chunk.fromArray(parsed)))
  }

  def render(h: AcceptPatch): String =
    h.mediaTypes.map(_.fullType).mkString(", ")
}
