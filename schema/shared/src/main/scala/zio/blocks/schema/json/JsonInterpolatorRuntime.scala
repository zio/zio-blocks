package zio.blocks.schema.json

import java.io.OutputStream
import java.time._
import java.util.{Currency, UUID}
import scala.annotation.tailrec

/**
 * Shared runtime utilities for JSON string interpolation. Used by both Scala 2
 * and Scala 3 macro implementations.
 */
object JsonInterpolatorRuntime {
  final case class Raw(s: String) extends AnyVal

  def jsonWithInterpolation(sc: StringContext, args: Seq[Any]): Json = {
    val parts  = sc.parts.iterator
    val argsIt = args.iterator
    val str    = parts.next()
    val out    = new ByteArrayOutputStream(str.length << 1)
    out.write(str)
    while (argsIt.hasNext) {
      writeValue(out, argsIt.next())
      out.write(parts.next())
    }
    Json.jsonCodec.decode(out.toByteArray) match {
      case Right(json) => json
      case Left(error) => throw error
    }
  }

  private[this] def writeValue(out: ByteArrayOutputStream, value: Any): Unit = value match {
    case Raw(s)                => writeEscapedString(out, s)
    case s: String             => JsonBinaryCodec.stringCodec.encode(s, out)
    case b: Boolean            => JsonBinaryCodec.booleanCodec.encode(b, out)
    case b: Byte               => JsonBinaryCodec.byteCodec.encode(b, out)
    case sh: Short             => JsonBinaryCodec.shortCodec.encode(sh, out)
    case i: Int                => JsonBinaryCodec.intCodec.encode(i, out)
    case l: Long               => JsonBinaryCodec.longCodec.encode(l, out)
    case f: Float              => JsonBinaryCodec.floatCodec.encode(f, out)
    case d: Double             => JsonBinaryCodec.doubleCodec.encode(d, out)
    case c: Char               => JsonBinaryCodec.charCodec.encode(c, out)
    case bd: BigDecimal        => JsonBinaryCodec.bigDecimalCodec.encode(bd, out)
    case bi: BigInt            => JsonBinaryCodec.bigIntCodec.encode(bi, out)
    case dow: DayOfWeek        => JsonBinaryCodec.dayOfWeekCodec.encode(dow, out)
    case d: Duration           => JsonBinaryCodec.durationCodec.encode(d, out)
    case i: Instant            => JsonBinaryCodec.instantCodec.encode(i, out)
    case ld: LocalDate         => JsonBinaryCodec.localDateCodec.encode(ld, out)
    case ldt: LocalDateTime    => JsonBinaryCodec.localDateTimeCodec.encode(ldt, out)
    case lt: LocalTime         => JsonBinaryCodec.localTimeCodec.encode(lt, out)
    case m: Month              => JsonBinaryCodec.monthCodec.encode(m, out)
    case md: MonthDay          => JsonBinaryCodec.monthDayCodec.encode(md, out)
    case odt: OffsetDateTime   => JsonBinaryCodec.offsetDateTimeCodec.encode(odt, out)
    case ot: OffsetTime        => JsonBinaryCodec.offsetTimeCodec.encode(ot, out)
    case p: Period             => JsonBinaryCodec.periodCodec.encode(p, out)
    case y: Year               => JsonBinaryCodec.yearCodec.encode(y, out)
    case ym: YearMonth         => JsonBinaryCodec.yearMonthCodec.encode(ym, out)
    case zo: ZoneOffset        => JsonBinaryCodec.zoneOffsetCodec.encode(zo, out)
    case zi: ZoneId            => JsonBinaryCodec.zoneIdCodec.encode(zi, out)
    case zdt: ZonedDateTime    => JsonBinaryCodec.zonedDateTimeCodec.encode(zdt, out)
    case c: java.util.Currency => JsonBinaryCodec.currencyCodec.encode(c, out)
    case uuid: java.util.UUID  => JsonBinaryCodec.uuidCodec.encode(uuid, out)
    case opt: Option[_]        =>
      opt match {
        case Some(value) => writeValue(out, value)
        case _           =>
          out.write('n')
          out.write('u')
          out.write('l')
          out.write('l')
      }
    case null =>
      out.write('n')
      out.write('u')
      out.write('l')
      out.write('l')
    case _: Unit =>
      out.write('{')
      out.write('}')
    case j: Json                         => Json.jsonCodec.encode(j, out)
    case map: scala.collection.Map[_, _] =>
      out.write('{')
      map.foreach {
        var comma = false
        kv =>
          if (comma) out.write(',')
          else comma = true
          writeKey(out, kv._1)
          writeValue(out, kv._2)
      }
      out.write('}')
    case seq: Iterable[_] =>
      out.write('[')
      seq.foreach {
        var comma = false
        x =>
          if (comma) out.write(',')
          else comma = true
          writeValue(out, x)
      }
      out.write(']')
    case arr: Array[_] =>
      out.write('[')
      val len = arr.length
      var idx = 0
      while (idx < len) {
        if (idx > 0) out.write(',')
        writeValue(out, arr(idx))
        idx += 1
      }
      out.write(']')
    case x => out.write(x.toString)
  }

  private[this] def writeKey(out: ByteArrayOutputStream, key: Any): Unit = {
    key match {
      case s: String          => JsonBinaryCodec.stringCodec.encode(s, out)
      case b: Boolean         => writeQuoted(out)(JsonBinaryCodec.booleanCodec.encode(b, out))
      case b: Byte            => writeQuoted(out)(JsonBinaryCodec.byteCodec.encode(b, out))
      case sh: Short          => writeQuoted(out)(JsonBinaryCodec.shortCodec.encode(sh, out))
      case i: Int             => writeQuoted(out)(JsonBinaryCodec.intCodec.encode(i, out))
      case l: Long            => writeQuoted(out)(JsonBinaryCodec.longCodec.encode(l, out))
      case f: Float           => writeQuoted(out)(JsonBinaryCodec.floatCodec.encode(f, out))
      case d: Double          => writeQuoted(out)(JsonBinaryCodec.doubleCodec.encode(d, out))
      case c: Char            => writeQuoted(out)(JsonBinaryCodec.charCodec.encode(c, out))
      case bd: BigDecimal     => writeQuoted(out)(JsonBinaryCodec.bigDecimalCodec.encode(bd, out))
      case bi: BigInt         => writeQuoted(out)(JsonBinaryCodec.bigIntCodec.encode(bi, out))
      case d: Duration        => writeQuoted(out)(JsonBinaryCodec.durationCodec.encode(d, out))
      case dow: DayOfWeek     => writeQuoted(out)(JsonBinaryCodec.dayOfWeekCodec.encode(dow, out))
      case i: Instant         => writeQuoted(out)(JsonBinaryCodec.instantCodec.encode(i, out))
      case ld: LocalDate      => writeQuoted(out)(JsonBinaryCodec.localDateCodec.encode(ld, out))
      case ldt: LocalDateTime =>
        writeQuoted(out)(JsonBinaryCodec.localDateTimeCodec.encode(ldt, out))
      case lt: LocalTime       => writeQuoted(out)(JsonBinaryCodec.localTimeCodec.encode(lt, out))
      case m: Month            => writeQuoted(out)(JsonBinaryCodec.monthCodec.encode(m, out))
      case md: MonthDay        => writeQuoted(out)(JsonBinaryCodec.monthDayCodec.encode(md, out))
      case odt: OffsetDateTime =>
        writeQuoted(out)(JsonBinaryCodec.offsetDateTimeCodec.encode(odt, out))
      case ot: OffsetTime     => writeQuoted(out)(JsonBinaryCodec.offsetTimeCodec.encode(ot, out))
      case p: Period          => writeQuoted(out)(JsonBinaryCodec.periodCodec.encode(p, out))
      case y: Year            => writeQuoted(out)(JsonBinaryCodec.yearCodec.encode(y, out))
      case ym: YearMonth      => writeQuoted(out)(JsonBinaryCodec.yearMonthCodec.encode(ym, out))
      case zo: ZoneOffset     => writeQuoted(out)(JsonBinaryCodec.zoneOffsetCodec.encode(zo, out))
      case zi: ZoneId         => writeQuoted(out)(JsonBinaryCodec.zoneIdCodec.encode(zi, out))
      case zdt: ZonedDateTime =>
        writeQuoted(out)(JsonBinaryCodec.zonedDateTimeCodec.encode(zdt, out))
      case c: Currency => writeQuoted(out)(JsonBinaryCodec.currencyCodec.encode(c, out))
      case uuid: UUID  => writeQuoted(out)(JsonBinaryCodec.uuidCodec.encode(uuid, out))
      case x           => JsonBinaryCodec.stringCodec.encode(x.toString, out)
    }
    out.write(':')
  }

  private[this] def writeQuoted(out: ByteArrayOutputStream)(f: => Unit): Unit = {
    out.write('"')
    f
    out.write('"')
  }

  private[this] def writeEscapedString(out: ByteArrayOutputStream, s: String): Unit = {
    var i     = 0
    var start = 0
    val len   = s.length
    while (i < len) {
      val c       = s.charAt(i)
      val escaped =
        if (c == '"') "\\\""
        else if (c == '\\') "\\\\"
        else if (c < 0x20) {
          c match {
            case '\b' => "\\b"
            case '\f' => "\\f"
            case '\n' => "\\n"
            case '\r' => "\\r"
            case '\t' => "\\t"
            case _    => f"\\u$c%04x"
          }
        } else null

      if (escaped != null) {
        if (i > start) out.write(s, start, i)
        out.write(escaped)
        start = i + 1
      }
      i += 1
    }
    if (i > start) out.write(s, start, i)
  }

  private class ByteArrayOutputStream(initCapacity: Int) extends OutputStream {
    private[this] var buf   = new Array[Byte](initCapacity)
    private[this] var count = 0

    override def write(bytes: Array[Byte], off: Int, len: Int): Unit = {
      val limit = count + len
      if (limit > buf.length) buf = java.util.Arrays.copyOf(buf, math.max(buf.length << 1, limit))
      System.arraycopy(bytes, off, buf, count, len)
      count = limit
    }

    override def write(b: Int): Unit = {
      val pos = count
      if (pos >= buf.length) buf = java.util.Arrays.copyOf(buf, buf.length << 1)
      buf(pos) = b.toByte
      count = pos + 1
    }

    def write(s: String): Unit = count = write(s, 0, s.length, count, buf.length - 4)

    def write(s: String, from: Int, to: Int): Unit = count = write(s, from, to, count, buf.length - 4)

    @tailrec
    private[this] def write(s: String, from: Int, to: Int, pos: Int, posLim: Int): Int =
      if (from >= to) pos
      else if (pos >= posLim) {
        buf = java.util.Arrays.copyOf(buf, Math.max(buf.length << 1, count + (to - from) * 3))
        write(s, from, to, pos, buf.length - 4)
      } else {
        val ch1 = s.charAt(from).toInt
        if (ch1 < 0x80) {
          buf(pos) = ch1.toByte
          write(s, from + 1, to, pos + 1, posLim)
        } else if (ch1 < 0x800) { // 00000bbbbbaaaaaa (UTF-16 char) -> 110bbbbb 10aaaaaa (UTF-8 bytes)
          buf(pos) = (ch1 >> 6 | 0xc0).toByte
          buf(pos + 1) = (ch1 & 0x3f | 0x80).toByte
          write(s, from + 1, to, pos + 2, posLim)
        } else if ((ch1 & 0xf800) != 0xd800) { // ccccbbbbbbaaaaaa (UTF-16 char) -> 1110cccc 10bbbbbb 10aaaaaa (UTF-8 bytes)
          buf(pos) = (ch1 >> 12 | 0xe0).toByte
          buf(pos + 1) = (ch1 >> 6 & 0x3f | 0x80).toByte
          buf(pos + 2) = (ch1 & 0x3f | 0x80).toByte
          write(s, from + 1, to, pos + 3, posLim)
        } else { // 110110uuuuccccbb 110111bbbbaaaaaa (UTF-16 chars) -> 11110ddd 10ddcccc 10bbbbbb 10aaaaaa (UTF-8 bytes), where ddddd = uuuu + 1
          var ch2 = 0
          if (
            ch1 >= 0xdc00 || from + 1 >= to || {
              ch2 = s.charAt(from + 1).toInt
              (ch2 & 0xfc00) != 0xdc00
            }
          ) throw new JsonBinaryCodecError(Nil, "Illegal surrogate pair")
          val cp = (ch1 << 10) + (ch2 - 56613888) // -56613888 == 0x10000 - (0xD800 << 10) - 0xDC00
          buf(pos) = (cp >> 18 | 0xf0).toByte
          buf(pos + 1) = (cp >> 12 & 0x3f | 0x80).toByte
          buf(pos + 2) = (cp >> 6 & 0x3f | 0x80).toByte
          buf(pos + 3) = (cp & 0x3f | 0x80).toByte
          write(s, from + 2, to, pos + 4, posLim)
        }
      }

    def toByteArray: Array[Byte] = java.util.Arrays.copyOf(buf, count)
  }
}
