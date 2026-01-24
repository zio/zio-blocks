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
  def jsonWithInterpolation(sc: StringContext, args: Seq[Any]): Json = {
    val parts  = sc.parts
    val out    = new ByteArrayOutputStream(parts.head.length << 1)
    out.write(parts.head)
    
    // Track whether we're inside a string literal across multiple interpolations
    var inStringLiteral = isInStringLiteral(parts.head)
    // Maintain accumulated text for O(n) context detection
    val accumulatedText = new java.lang.StringBuilder(parts.head)
    
    var i = 0
    while (i < args.length) {
      val context = if (inStringLiteral) {
        Context.StringLiteral
      } else {
        val after = if (i + 1 < parts.length) parts(i + 1) else ""
        detectContextFromBeforeAfter(accumulatedText.toString, after)
      }
      
      context match {
        case Context.Key =>
          writeKeyOnly(out, args(i))
        case Context.StringLiteral =>
          writeStringLiteralValue(out, args(i))
        case Context.Value =>
          writeValue(out, args(i))
      }
      
      val nextPart = parts(i + 1)
      out.write(nextPart)
      
      // Update string literal tracking
      if (inStringLiteral) {
        // We were in a string, check if this part closes it
        inStringLiteral = isInStringLiteral(nextPart) != inStringLiteral
      } else {
        // We weren't in a string, check if this part opens one
        inStringLiteral = isInStringLiteral(nextPart)
      }
      
      // Update accumulated text with placeholder for this arg and the next part
      accumulatedText.append("x").append(nextPart)
      
      i += 1
    }
    
    Json.jsonCodec.decode(out.toByteArray) match {
      case Right(json) => json
      case Left(error) => throw error
    }
  }

  private sealed trait Context
  private object Context {
    case object Key extends Context
    case object Value extends Context
    case object StringLiteral extends Context
  }

  private def detectContextFromBeforeAfter(before: String, after: String): Context = {
    // Check if we're inside a string literal (odd number of unescaped quotes before)
    if (isInStringLiteral(before)) {
      Context.StringLiteral
    }
    // Check if this is a key position (after '{' or ',' and before ':')
    else if (isKeyPosition(before, after)) {
      Context.Key
    }
    // Otherwise it's a value position
    else {
      Context.Value
    }
  }

  private def isInStringLiteral(text: String): Boolean = {
    var inQuote = false
    var i = 0
    while (i < text.length) {
      val c = text.charAt(i)
      if (c == '"') {
        // Count consecutive backslashes before this quote
        var backslashCount = 0
        var j = i - 1
        while (j >= 0 && text.charAt(j) == '\\') {
          backslashCount += 1
          j -= 1
        }
        // If there's an even number of backslashes (including 0), the quote is not escaped
        if (backslashCount % 2 == 0) {
          inQuote = !inQuote
        }
      }
      i += 1
    }
    inQuote
  }

  private def isKeyPosition(before: String, after: String): Boolean = {
    val trimmedBefore = before.reverse.dropWhile(c => c.isWhitespace).reverse
    val trimmedAfter = after.dropWhile(c => c.isWhitespace)
    
    (trimmedBefore.endsWith("{") || trimmedBefore.endsWith(",")) && trimmedAfter.startsWith(":")
  }

  /**
   * Writes a value into an existing JSON string literal, escaping according to
   * JSON string rules but without adding surrounding quotes (the literal already
   * provides them).
   */
  private def writeJsonEscapedString(out: ByteArrayOutputStream, s: String): Unit = {
    val sb = new java.lang.StringBuilder(s.length + 16)
    var i  = 0
    while (i < s.length) {
      s.charAt(i) match {
        case '"'  => sb.append("\\\"")
        case '\\' => sb.append("\\\\")
        case '\b' => sb.append("\\b")
        case '\f' => sb.append("\\f")
        case '\n' => sb.append("\\n")
        case '\r' => sb.append("\\r")
        case '\t' => sb.append("\\t")
        case c if c < ' ' =>
          val hex = Integer.toHexString(c.toInt)
          sb.append("\\u")
          var j = hex.length
          while (j < 4) {
            sb.append('0')
            j += 1
          }
          sb.append(hex)
        case c =>
          sb.append(c)
      }
      i += 1
    }
    out.write(sb.toString)
  }

  private def writeStringLiteralValue(out: ByteArrayOutputStream, value: Any): Unit = value match {
    case s: String     => writeJsonEscapedString(out, s) // Escape JSON special chars
    case b: Boolean    => out.write(b.toString)
    case b: Byte       => out.write(b.toString)
    case sh: Short     => out.write(sh.toString)
    case i: Int        => out.write(i.toString)
    case l: Long       => out.write(l.toString)
    case f: Float      => out.write(JsonBinaryCodec.floatCodec.encodeToString(f))
    case d: Double     => out.write(JsonBinaryCodec.doubleCodec.encodeToString(d))
    case c: Char       => writeJsonEscapedString(out, c.toString) // Escape special chars
    case bd: BigDecimal => out.write(bd.toString)
    case bi: BigInt    => out.write(bi.toString)
    case dow: DayOfWeek => out.write(dow.toString)
    case d: Duration    => out.write(d.toString)
    case i: Instant     => out.write(i.toString)
    case ld: LocalDate  => out.write(ld.toString)
    case ldt: LocalDateTime => out.write(ldt.toString)
    case lt: LocalTime  => out.write(lt.toString)
    case m: Month       => out.write(m.toString)
    case md: MonthDay   => out.write(md.toString)
    case odt: OffsetDateTime => out.write(odt.toString)
    case ot: OffsetTime => out.write(ot.toString)
    case p: Period      => out.write(p.toString)
    case y: Year        => out.write(y.toString)
    case ym: YearMonth  => out.write(ym.toString)
    case zo: ZoneOffset => out.write(zo.toString)
    case zi: ZoneId     => out.write(zi.toString)
    case zdt: ZonedDateTime => out.write(zdt.toString)
    case c: Currency    => out.write(c.toString)
    case uuid: UUID     => out.write(uuid.toString)
    case x              => writeJsonEscapedString(out, if (x == null) "null" else x.toString) // Escape fallback
  }

  private def writeKeyOnly(out: ByteArrayOutputStream, key: Any): Unit = {
    key match {
      case s: String  => JsonBinaryCodec.stringCodec.encode(s, out)
      case b: Boolean =>
        out.write('"')
        JsonBinaryCodec.booleanCodec.encode(b, out)
        out.write('"')
      case b: Byte =>
        out.write('"')
        JsonBinaryCodec.byteCodec.encode(b, out)
        out.write('"')
      case sh: Short =>
        out.write('"')
        JsonBinaryCodec.shortCodec.encode(sh, out)
        out.write('"')
      case i: Int =>
        out.write('"')
        JsonBinaryCodec.intCodec.encode(i, out)
        out.write('"')
      case l: Long =>
        out.write('"')
        JsonBinaryCodec.longCodec.encode(l, out)
        out.write('"')
      case f: Float =>
        out.write('"')
        JsonBinaryCodec.floatCodec.encode(f, out)
        out.write('"')
      case d: Double =>
        out.write('"')
        JsonBinaryCodec.doubleCodec.encode(d, out)
        out.write('"')
      case bd: BigDecimal =>
        out.write('"')
        JsonBinaryCodec.bigDecimalCodec.encode(bd, out)
        out.write('"')
      case bi: BigInt =>
        out.write('"')
        JsonBinaryCodec.bigIntCodec.encode(bi, out)
        out.write('"')
      case d: Duration         => JsonBinaryCodec.durationCodec.encode(d, out)
      case dow: DayOfWeek      => JsonBinaryCodec.dayOfWeekCodec.encode(dow, out)
      case i: Instant          => JsonBinaryCodec.instantCodec.encode(i, out)
      case ld: LocalDate       => JsonBinaryCodec.localDateCodec.encode(ld, out)
      case ldt: LocalDateTime  => JsonBinaryCodec.localDateTimeCodec.encode(ldt, out)
      case lt: LocalTime       => JsonBinaryCodec.localTimeCodec.encode(lt, out)
      case m: Month            => JsonBinaryCodec.monthCodec.encode(m, out)
      case md: MonthDay        => JsonBinaryCodec.monthDayCodec.encode(md, out)
      case odt: OffsetDateTime => JsonBinaryCodec.offsetDateTimeCodec.encode(odt, out)
      case ot: OffsetTime      => JsonBinaryCodec.offsetTimeCodec.encode(ot, out)
      case p: Period           => JsonBinaryCodec.periodCodec.encode(p, out)
      case y: Year             => JsonBinaryCodec.yearCodec.encode(y, out)
      case ym: YearMonth       => JsonBinaryCodec.yearMonthCodec.encode(ym, out)
      case zo: ZoneOffset      => JsonBinaryCodec.zoneOffsetCodec.encode(zo, out)
      case zi: ZoneId          => JsonBinaryCodec.zoneIdCodec.encode(zi, out)
      case zdt: ZonedDateTime  => JsonBinaryCodec.zonedDateTimeCodec.encode(zdt, out)
      case c: Currency         => JsonBinaryCodec.currencyCodec.encode(c, out)
      case uuid: UUID          => JsonBinaryCodec.uuidCodec.encode(uuid, out)
      case x                   => JsonBinaryCodec.stringCodec.encode(x.toString, out)
    }
    // Don't write colon - it's in the next part
  }

  private[this] def writeValue(out: ByteArrayOutputStream, value: Any): Unit = value match {
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
      var comma = false
      map.foreach { kv =>
        if (comma) out.write(',')
        else comma = true
        writeKey(out, kv._1)
        writeValue(out, kv._2)
      }
      out.write('}')
    case seq: Iterable[_] =>
      out.write('[')
      var comma = false
      seq.foreach { x =>
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
      case s: String  => JsonBinaryCodec.stringCodec.encode(s, out)
      case b: Boolean =>
        out.write('"')
        JsonBinaryCodec.booleanCodec.encode(b, out)
        out.write('"')
      case b: Byte =>
        out.write('"')
        JsonBinaryCodec.byteCodec.encode(b, out)
        out.write('"')
      case sh: Short =>
        out.write('"')
        JsonBinaryCodec.shortCodec.encode(sh, out)
        out.write('"')
      case i: Int =>
        out.write('"')
        JsonBinaryCodec.intCodec.encode(i, out)
        out.write('"')
      case l: Long =>
        out.write('"')
        JsonBinaryCodec.longCodec.encode(l, out)
        out.write('"')
      case f: Float =>
        out.write('"')
        JsonBinaryCodec.floatCodec.encode(f, out)
        out.write('"')
      case d: Double =>
        out.write('"')
        JsonBinaryCodec.doubleCodec.encode(d, out)
        out.write('"')
      case bd: BigDecimal =>
        out.write('"')
        JsonBinaryCodec.bigDecimalCodec.encode(bd, out)
        out.write('"')
      case bi: BigInt =>
        out.write('"')
        JsonBinaryCodec.bigIntCodec.encode(bi, out)
        out.write('"')
      case d: Duration         => JsonBinaryCodec.durationCodec.encode(d, out)
      case dow: DayOfWeek      => JsonBinaryCodec.dayOfWeekCodec.encode(dow, out)
      case i: Instant          => JsonBinaryCodec.instantCodec.encode(i, out)
      case ld: LocalDate       => JsonBinaryCodec.localDateCodec.encode(ld, out)
      case ldt: LocalDateTime  => JsonBinaryCodec.localDateTimeCodec.encode(ldt, out)
      case lt: LocalTime       => JsonBinaryCodec.localTimeCodec.encode(lt, out)
      case m: Month            => JsonBinaryCodec.monthCodec.encode(m, out)
      case md: MonthDay        => JsonBinaryCodec.monthDayCodec.encode(md, out)
      case odt: OffsetDateTime => JsonBinaryCodec.offsetDateTimeCodec.encode(odt, out)
      case ot: OffsetTime      => JsonBinaryCodec.offsetTimeCodec.encode(ot, out)
      case p: Period           => JsonBinaryCodec.periodCodec.encode(p, out)
      case y: Year             => JsonBinaryCodec.yearCodec.encode(y, out)
      case ym: YearMonth       => JsonBinaryCodec.yearMonthCodec.encode(ym, out)
      case zo: ZoneOffset      => JsonBinaryCodec.zoneOffsetCodec.encode(zo, out)
      case zi: ZoneId          => JsonBinaryCodec.zoneIdCodec.encode(zi, out)
      case zdt: ZonedDateTime  => JsonBinaryCodec.zonedDateTimeCodec.encode(zdt, out)
      case c: Currency         => JsonBinaryCodec.currencyCodec.encode(c, out)
      case uuid: UUID          => JsonBinaryCodec.uuidCodec.encode(uuid, out)
      case x                   => JsonBinaryCodec.stringCodec.encode(x.toString, out)
    }
    out.write(':')
  }
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
