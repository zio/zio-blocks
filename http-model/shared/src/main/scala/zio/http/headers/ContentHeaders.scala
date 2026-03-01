package zio.http.headers

import zio.blocks.chunk.Chunk

import zio.http._

// --- 1. ContentType ---

final case class ContentType(value: zio.http.ContentType) extends Header {
  def headerName: String    = ContentType.name
  def renderedValue: String = value.render
}

object ContentType extends Header.Typed[ContentType] {
  val name: String = "content-type"

  def parse(value: String): Either[String, ContentType] =
    zio.http.ContentType.parse(value).map(ct => ContentType(ct))

  def render(h: ContentType): String = h.renderedValue
}

// --- 2. ContentLength ---

final case class ContentLength(length: Long) extends Header {
  def headerName: String    = ContentLength.name
  def renderedValue: String = length.toString
}

object ContentLength extends Header.Typed[ContentLength] {
  val name: String = "content-length"

  def parse(value: String): Either[String, ContentLength] =
    try {
      val l = value.toLong
      if (l < 0) Left(s"Invalid content-length: $l")
      else Right(ContentLength(l))
    } catch {
      case _: NumberFormatException => Left(s"Invalid content-length: $value")
    }

  def render(h: ContentLength): String = h.renderedValue
}

// --- 3. ContentEncoding ---

sealed trait ContentEncoding extends Header {
  def headerName: String    = ContentEncoding.name
  def renderedValue: String = ContentEncoding.render(this)
}

object ContentEncoding extends Header.Typed[ContentEncoding] {
  val name: String = "content-encoding"

  case object GZip     extends ContentEncoding
  case object Deflate  extends ContentEncoding
  case object Br       extends ContentEncoding
  case object Compress extends ContentEncoding
  case object Identity extends ContentEncoding

  final case class Multiple(values: Chunk[ContentEncoding]) extends ContentEncoding

  private def findEncoding(value: String): Option[ContentEncoding] =
    value.trim.toLowerCase match {
      case "gzip"     => Some(GZip)
      case "deflate"  => Some(Deflate)
      case "br"       => Some(Br)
      case "compress" => Some(Compress)
      case "identity" => Some(Identity)
      case _          => None
    }

  def parse(value: String): Either[String, ContentEncoding] = {
    val parts  = value.split(',')
    val parsed = Chunk.fromArray(parts.flatMap(s => findEncoding(s)))
    if (parsed.isEmpty) Left(s"Invalid content-encoding: $value")
    else if (parsed.length == 1) Right(parsed(0))
    else Right(Multiple(parsed))
  }

  def render(h: ContentEncoding): String = h match {
    case GZip        => "gzip"
    case Deflate     => "deflate"
    case Br          => "br"
    case Compress    => "compress"
    case Identity    => "identity"
    case Multiple(v) => v.map(render).mkString(", ")
  }
}

// --- 4. ContentDisposition ---

sealed trait ContentDisposition extends Header {
  def headerName: String    = ContentDisposition.name
  def renderedValue: String = ContentDisposition.render(this)
}

object ContentDisposition extends Header.Typed[ContentDisposition] {
  val name: String = "content-disposition"

  final case class Attachment(filename: Option[String])             extends ContentDisposition
  final case class Inline(filename: Option[String])                 extends ContentDisposition
  final case class FormData(name: String, filename: Option[String]) extends ContentDisposition

  private val AttachmentRegex         = """attachment;\s*filename="(.*)"""".r
  private val InlineRegex             = """inline;\s*filename="(.*)"""".r
  private val FormDataRegex           = """form-data;\s*name="(.*)";\s*filename="(.*)"""".r
  private val FormDataNoFileNameRegex = """form-data;\s*name="(.*)"""".r

  def parse(value: String): Either[String, ContentDisposition] = {
    val trimmed = value.trim
    if (trimmed.startsWith("attachment")) {
      Right(trimmed match {
        case AttachmentRegex(filename) => Attachment(Some(filename))
        case _                         => Attachment(None)
      })
    } else if (trimmed.startsWith("inline")) {
      Right(trimmed match {
        case InlineRegex(filename) => Inline(Some(filename))
        case _                     => Inline(None)
      })
    } else if (trimmed.startsWith("form-data")) {
      trimmed match {
        case FormDataRegex(name, filename) => Right(FormData(name, Some(filename)))
        case FormDataNoFileNameRegex(name) => Right(FormData(name, None))
        case _                             => Left("Invalid form-data content disposition")
      }
    } else {
      Left(s"Invalid content-disposition: $trimmed")
    }
  }

  def render(h: ContentDisposition): String = h match {
    case Attachment(Some(f))  => s"""attachment; filename="$f""""
    case Attachment(None)     => "attachment"
    case Inline(Some(f))      => s"""inline; filename="$f""""
    case Inline(None)         => "inline"
    case FormData(n, Some(f)) => s"""form-data; name="$n"; filename="$f""""
    case FormData(n, None)    => s"""form-data; name="$n""""
  }
}

// --- 5. ContentLanguage ---

final case class ContentLanguage(language: String) extends Header {
  def headerName: String    = ContentLanguage.name
  def renderedValue: String = language
}

object ContentLanguage extends Header.Typed[ContentLanguage] {
  val name: String                                          = "content-language"
  def parse(value: String): Either[String, ContentLanguage] = Right(ContentLanguage(value))
  def render(h: ContentLanguage): String                    = h.renderedValue
}

// --- 6. ContentLocation ---

final case class ContentLocation(location: String) extends Header {
  def headerName: String    = ContentLocation.name
  def renderedValue: String = location
}

object ContentLocation extends Header.Typed[ContentLocation] {
  val name: String                                          = "content-location"
  def parse(value: String): Either[String, ContentLocation] = Right(ContentLocation(value))
  def render(h: ContentLocation): String                    = h.renderedValue
}

// --- 7. ContentRange ---

final case class ContentRange(unit: String, range: Option[(Long, Long)], size: Option[Long]) extends Header {
  def headerName: String    = ContentRange.name
  def renderedValue: String = ContentRange.render(this)
}

object ContentRange extends Header.Typed[ContentRange] {
  val name: String = "content-range"

  private val StartEndTotalRegex = """(\w+)\s+(\d+)-(\d+)/(\d+)""".r
  private val StartEndRegex      = """(\w+)\s+(\d+)-(\d+)/\*""".r
  private val TotalOnlyRegex     = """(\w+)\s+\*/(\d+)""".r

  def parse(value: String): Either[String, ContentRange] =
    value.trim match {
      case StartEndTotalRegex(unit, start, end, total) =>
        Right(ContentRange(unit, Some((start.toLong, end.toLong)), Some(total.toLong)))
      case StartEndRegex(unit, start, end) =>
        Right(ContentRange(unit, Some((start.toLong, end.toLong)), None))
      case TotalOnlyRegex(unit, total) =>
        Right(ContentRange(unit, None, Some(total.toLong)))
      case _ =>
        Left(s"Invalid content-range: $value")
    }

  def render(h: ContentRange): String = (h.range, h.size) match {
    case (Some((start, end)), Some(total)) => s"${h.unit} $start-$end/$total"
    case (Some((start, end)), None)        => s"${h.unit} $start-$end/*"
    case (None, Some(total))               => s"${h.unit} */$total"
    case (None, None)                      => s"${h.unit} */*"
  }
}

// --- 8. ContentSecurityPolicy ---

final case class ContentSecurityPolicy(directives: String) extends Header {
  def headerName: String    = ContentSecurityPolicy.name
  def renderedValue: String = directives
}

object ContentSecurityPolicy extends Header.Typed[ContentSecurityPolicy] {
  val name: String                                                = "content-security-policy"
  def parse(value: String): Either[String, ContentSecurityPolicy] = Right(ContentSecurityPolicy(value))
  def render(h: ContentSecurityPolicy): String                    = h.renderedValue
}

// --- 9. ContentTransferEncoding ---

sealed trait ContentTransferEncoding extends Header {
  def headerName: String    = ContentTransferEncoding.name
  def renderedValue: String = ContentTransferEncoding.render(this)
}

object ContentTransferEncoding extends Header.Typed[ContentTransferEncoding] {
  val name: String = "content-transfer-encoding"

  case object SevenBit        extends ContentTransferEncoding
  case object EightBit        extends ContentTransferEncoding
  case object Binary          extends ContentTransferEncoding
  case object QuotedPrintable extends ContentTransferEncoding
  case object Base64          extends ContentTransferEncoding

  def parse(value: String): Either[String, ContentTransferEncoding] =
    value.trim.toLowerCase match {
      case "7bit"             => Right(SevenBit)
      case "8bit"             => Right(EightBit)
      case "binary"           => Right(Binary)
      case "quoted-printable" => Right(QuotedPrintable)
      case "base64"           => Right(Base64)
      case other              => Left(s"Invalid content-transfer-encoding: $other")
    }

  def render(h: ContentTransferEncoding): String = h match {
    case SevenBit        => "7bit"
    case EightBit        => "8bit"
    case Binary          => "binary"
    case QuotedPrintable => "quoted-printable"
    case Base64          => "base64"
  }
}

// --- 10. ContentMd5 ---

final case class ContentMd5(value: String) extends Header {
  def headerName: String    = ContentMd5.name
  def renderedValue: String = value
}

object ContentMd5 extends Header.Typed[ContentMd5] {
  val name: String                                     = "content-md5"
  def parse(value: String): Either[String, ContentMd5] = Right(ContentMd5(value))
  def render(h: ContentMd5): String                    = h.renderedValue
}

// --- 11. ContentBase ---

final case class ContentBase(uri: String) extends Header {
  def headerName: String    = ContentBase.name
  def renderedValue: String = uri
}

object ContentBase extends Header.Typed[ContentBase] {
  val name: String                                      = "content-base"
  def parse(value: String): Either[String, ContentBase] = Right(ContentBase(value))
  def render(h: ContentBase): String                    = h.renderedValue
}
