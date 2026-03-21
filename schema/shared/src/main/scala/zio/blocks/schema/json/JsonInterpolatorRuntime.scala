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

package zio.blocks.schema.json

import java.io.OutputStream
import java.time._
import java.util.{Currency, UUID}
import scala.annotation.tailrec

/**
 * Shared runtime utilities for JSON string interpolation. Used by both Scala 2
 * and Scala 3 macro implementations.
 */
private[schema] object JsonInterpolatorRuntime {

  /**
   * Validates a JSON literal at compile time with context-aware placeholder
   * handling. For InString contexts, we write an empty string without quotes.
   * For other contexts, we write appropriate placeholder values.
   */
  def validateJsonLiteral(sc: StringContext, contexts: Seq[InterpolationContext]): Unit = {
    val parts     = sc.parts.iterator
    val contextIt = contexts.iterator
    val str       = parts.next()
    val out       = new ByteArrayOutputStream(str.length << 1)
    out.write(str)
    while (parts.hasNext && contextIt.hasNext) {
      val ctx = contextIt.next()
      ctx match {
        case InterpolationContext.Key =>
          // For key validation, write a placeholder key string
          out.write('"')
          out.write('_')
          out.write('"')
        case InterpolationContext.Value =>
          // For value validation, write null as a valid placeholder
          out.write('n')
          out.write('u')
          out.write('l')
          out.write('l')
        case InterpolationContext.InString =>
          // For in-string validation, write nothing (placeholder is already inside quotes)
          ()
      }
      out.write(parts.next())
    }
    Json.jsonCodec.decode(out.toByteArray) match {
      case Left(error) => throw error
      case _           => () // Valid JSON
    }
  }

  /**
   * Context-aware interpolation that handles keys, values, and in-string
   * contexts differently.
   */
  def jsonWithContexts(sc: StringContext, args: Seq[Any], contexts: Seq[InterpolationContext]): Json = {
    val parts     = sc.parts.iterator
    val argsIt    = args.iterator
    val contextIt = contexts.iterator
    val str       = parts.next()
    val out       = new ByteArrayOutputStream(str.length << 1)
    out.write(str)
    while (argsIt.hasNext && contextIt.hasNext) {
      val arg = argsIt.next()
      val ctx = contextIt.next()
      ctx match {
        case InterpolationContext.Key      => writeKeyOnly(out, arg)
        case InterpolationContext.Value    => writeValue(out, arg)
        case InterpolationContext.InString => writeInString(out, arg)
      }
      out.write(parts.next())
    }
    Json.jsonCodec.decode(out.toByteArray) match {
      case Right(json) => json
      case Left(error) => throw error
    }
  }

  /**
   * Writes a value as a JSON key (quoted string without trailing colon). Unlike
   * writeKey, this does not append a colon since the colon is part of the
   * literal string context.
   */
  private[this] def writeKeyOnly(out: ByteArrayOutputStream, key: Any): Unit = key match {
    case s: String  => JsonCodec.stringCodec.encode(s, out)
    case c: Char    => JsonCodec.stringCodec.encode(c.toString, out)
    case b: Boolean =>
      out.write('"')
      JsonCodec.booleanCodec.encode(b, out)
      out.write('"')
    case b: Byte =>
      out.write('"')
      JsonCodec.byteCodec.encode(b, out)
      out.write('"')
    case sh: Short =>
      out.write('"')
      JsonCodec.shortCodec.encode(sh, out)
      out.write('"')
    case i: Int =>
      out.write('"')
      JsonCodec.intCodec.encode(i, out)
      out.write('"')
    case l: Long =>
      out.write('"')
      JsonCodec.longCodec.encode(l, out)
      out.write('"')
    case f: Float =>
      out.write('"')
      JsonCodec.floatCodec.encode(f, out)
      out.write('"')
    case d: Double =>
      out.write('"')
      JsonCodec.doubleCodec.encode(d, out)
      out.write('"')
    case bd: BigDecimal =>
      out.write('"')
      JsonCodec.bigDecimalCodec.encode(bd, out)
      out.write('"')
    case bi: BigInt =>
      out.write('"')
      JsonCodec.bigIntCodec.encode(bi, out)
      out.write('"')
    case _: Unit               => JsonCodec.stringCodec.encode("{}", out)
    case d: Duration           => JsonCodec.durationCodec.encode(d, out)
    case dow: DayOfWeek        => JsonCodec.dayOfWeekCodec.encode(dow, out)
    case i: Instant            => JsonCodec.instantCodec.encode(i, out)
    case ld: LocalDate         => JsonCodec.localDateCodec.encode(ld, out)
    case ldt: LocalDateTime    => JsonCodec.localDateTimeCodec.encode(ldt, out)
    case lt: LocalTime         => JsonCodec.localTimeCodec.encode(lt, out)
    case m: Month              => JsonCodec.monthCodec.encode(m, out)
    case md: MonthDay          => JsonCodec.monthDayCodec.encode(md, out)
    case odt: OffsetDateTime   => JsonCodec.offsetDateTimeCodec.encode(odt, out)
    case ot: OffsetTime        => JsonCodec.offsetTimeCodec.encode(ot, out)
    case p: Period             => JsonCodec.periodCodec.encode(p, out)
    case y: Year               => JsonCodec.yearCodec.encode(y, out)
    case ym: YearMonth         => JsonCodec.yearMonthCodec.encode(ym, out)
    case zo: ZoneOffset        => JsonCodec.zoneOffsetCodec.encode(zo, out)
    case zi: ZoneId            => JsonCodec.zoneIdCodec.encode(zi, out)
    case zdt: ZonedDateTime    => JsonCodec.zonedDateTimeCodec.encode(zdt, out)
    case c: java.util.Currency => JsonCodec.currencyCodec.encode(c, out)
    case uuid: java.util.UUID  => JsonCodec.uuidCodec.encode(uuid, out)
    case x                     =>
      throw new IllegalArgumentException(
        s"Unexpected type in key position: ${x.getClass.getName}. This should have been caught at compile time."
      )
  }

  /**
   * Writes a keyable value inside a JSON string context (without quotes, as the
   * quotes are part of the literal context).
   */
  private[this] def writeInString(out: ByteArrayOutputStream, value: Any): Unit = value match {
    case s: String             => writeRawString(out, s)
    case c: Char               => writeRawString(out, c.toString)
    case b: Boolean            => writeRawString(out, b.toString)
    case b: Byte               => writeRawString(out, b.toString)
    case sh: Short             => writeRawString(out, sh.toString)
    case i: Int                => writeRawString(out, i.toString)
    case l: Long               => writeRawString(out, l.toString)
    case f: Float              => writeRawString(out, JsonCodec.floatCodec.encodeToString(f))
    case d: Double             => writeRawString(out, JsonCodec.doubleCodec.encodeToString(d))
    case bd: BigDecimal        => writeRawString(out, JsonCodec.bigDecimalCodec.encodeToString(bd))
    case bi: BigInt            => writeRawString(out, JsonCodec.bigIntCodec.encodeToString(bi))
    case _: Unit               => writeRawString(out, "()")
    case dow: DayOfWeek        => writeRawString(out, dow.toString)
    case d: Duration           => writeRawString(out, Json.durationRawCodec.encodeToString(d))
    case i: Instant            => writeRawString(out, Json.instantRawCodec.encodeToString(i))
    case ld: LocalDate         => writeRawString(out, Json.localDateRawCodec.encodeToString(ld))
    case ldt: LocalDateTime    => writeRawString(out, Json.localDateTimeRawCodec.encodeToString(ldt))
    case lt: LocalTime         => writeRawString(out, Json.localTimeRawCodec.encodeToString(lt))
    case m: Month              => writeRawString(out, m.toString)
    case md: MonthDay          => writeRawString(out, Json.monthDayRawCodec.encodeToString(md))
    case odt: OffsetDateTime   => writeRawString(out, Json.offsetDateTimeRawCodec.encodeToString(odt))
    case ot: OffsetTime        => writeRawString(out, Json.offsetTimeRawCodec.encodeToString(ot))
    case p: Period             => writeRawString(out, Json.periodRawCodec.encodeToString(p))
    case y: Year               => writeRawString(out, y.toString)
    case ym: YearMonth         => writeRawString(out, ym.toString)
    case zo: ZoneOffset        => writeRawString(out, zo.getId)
    case zi: ZoneId            => writeRawString(out, zi.getId)
    case zdt: ZonedDateTime    => writeRawString(out, Json.zonedDateTimeRawCodec.encodeToString(zdt))
    case c: java.util.Currency => writeRawString(out, c.getCurrencyCode)
    case uuid: java.util.UUID  => writeRawString(out, uuid.toString)
    case x                     =>
      throw new IllegalArgumentException(
        s"Unexpected type in string literal: ${x.getClass.getName}. This should have been caught at compile time."
      )
  }

  /**
   * Writes a string inside a JSON string (escapes special characters but no
   * surrounding quotes). Properly encodes characters as UTF-8 bytes.
   */
  private[this] def writeRawString(out: ByteArrayOutputStream, s: String): Unit = {
    var i = 0
    while (i < s.length) {
      val ch1 = s.charAt(i).toInt
      ch1 match {
        case '"' =>
          out.write('\\')
          out.write('"')
        case '\\' =>
          out.write('\\')
          out.write('\\')
        case '\b' =>
          out.write('\\')
          out.write('b')
        case '\f' =>
          out.write('\\')
          out.write('f')
        case '\n' =>
          out.write('\\')
          out.write('n')
        case '\r' =>
          out.write('\\')
          out.write('r')
        case '\t' =>
          out.write('\\')
          out.write('t')
        case _ if ch1 < 0x20 =>
          out.write('\\')
          out.write('u')
          out.write(hexDigit((ch1 >> 12) & 0xf))
          out.write(hexDigit((ch1 >> 8) & 0xf))
          out.write(hexDigit((ch1 >> 4) & 0xf))
          out.write(hexDigit(ch1 & 0xf))
        case _ if ch1 < 0x80 =>
          // ASCII: single byte
          out.write(ch1)
        case _ if ch1 < 0x800 =>
          // 2-byte UTF-8: 110xxxxx 10xxxxxx
          out.write((ch1 >> 6) | 0xc0)
          out.write((ch1 & 0x3f) | 0x80)
        case _ if (ch1 & 0xf800) != 0xd800 =>
          // 3-byte UTF-8: 1110xxxx 10xxxxxx 10xxxxxx
          out.write((ch1 >> 12) | 0xe0)
          out.write(((ch1 >> 6) & 0x3f) | 0x80)
          out.write((ch1 & 0x3f) | 0x80)
        case _ =>
          // 4-byte UTF-8 (surrogate pair): 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
          if (i + 1 < s.length) {
            val ch2 = s.charAt(i + 1).toInt
            if ((ch2 & 0xfc00) == 0xdc00) {
              val cp = (ch1 << 10) + (ch2 - 56613888) // -56613888 == 0x10000 - (0xD800 << 10) - 0xDC00
              out.write((cp >> 18) | 0xf0)
              out.write(((cp >> 12) & 0x3f) | 0x80)
              out.write(((cp >> 6) & 0x3f) | 0x80)
              out.write((cp & 0x3f) | 0x80)
              i += 1 // Skip the low surrogate
            } else {
              // Invalid surrogate pair, write replacement character
              out.write(0xef)
              out.write(0xbf)
              out.write(0xbd)
            }
          } else {
            // Lone high surrogate at end, write replacement character
            out.write(0xef)
            out.write(0xbf)
            out.write(0xbd)
          }
      }
      i += 1
    }
  }

  private[this] def hexDigit(n: Int): Int = if (n < 10) '0' + n else 'a' + n - 10

  private[this] def writeValue(out: ByteArrayOutputStream, value: Any): Unit = value match {
    case s: String             => JsonCodec.stringCodec.encode(s, out)
    case b: Boolean            => JsonCodec.booleanCodec.encode(b, out)
    case b: Byte               => JsonCodec.byteCodec.encode(b, out)
    case sh: Short             => JsonCodec.shortCodec.encode(sh, out)
    case i: Int                => JsonCodec.intCodec.encode(i, out)
    case l: Long               => JsonCodec.longCodec.encode(l, out)
    case f: Float              => JsonCodec.floatCodec.encode(f, out)
    case d: Double             => JsonCodec.doubleCodec.encode(d, out)
    case c: Char               => JsonCodec.charCodec.encode(c, out)
    case bd: BigDecimal        => JsonCodec.bigDecimalCodec.encode(bd, out)
    case bi: BigInt            => JsonCodec.bigIntCodec.encode(bi, out)
    case dow: DayOfWeek        => JsonCodec.dayOfWeekCodec.encode(dow, out)
    case d: Duration           => JsonCodec.durationCodec.encode(d, out)
    case i: Instant            => JsonCodec.instantCodec.encode(i, out)
    case ld: LocalDate         => JsonCodec.localDateCodec.encode(ld, out)
    case ldt: LocalDateTime    => JsonCodec.localDateTimeCodec.encode(ldt, out)
    case lt: LocalTime         => JsonCodec.localTimeCodec.encode(lt, out)
    case m: Month              => JsonCodec.monthCodec.encode(m, out)
    case md: MonthDay          => JsonCodec.monthDayCodec.encode(md, out)
    case odt: OffsetDateTime   => JsonCodec.offsetDateTimeCodec.encode(odt, out)
    case ot: OffsetTime        => JsonCodec.offsetTimeCodec.encode(ot, out)
    case p: Period             => JsonCodec.periodCodec.encode(p, out)
    case y: Year               => JsonCodec.yearCodec.encode(y, out)
    case ym: YearMonth         => JsonCodec.yearMonthCodec.encode(ym, out)
    case zo: ZoneOffset        => JsonCodec.zoneOffsetCodec.encode(zo, out)
    case zi: ZoneId            => JsonCodec.zoneIdCodec.encode(zi, out)
    case zdt: ZonedDateTime    => JsonCodec.zonedDateTimeCodec.encode(zdt, out)
    case c: java.util.Currency => JsonCodec.currencyCodec.encode(c, out)
    case uuid: java.util.UUID  => JsonCodec.uuidCodec.encode(uuid, out)
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
      case s: String  => JsonCodec.stringCodec.encode(s, out)
      case b: Boolean =>
        out.write('"')
        JsonCodec.booleanCodec.encode(b, out)
        out.write('"')
      case b: Byte =>
        out.write('"')
        JsonCodec.byteCodec.encode(b, out)
        out.write('"')
      case sh: Short =>
        out.write('"')
        JsonCodec.shortCodec.encode(sh, out)
        out.write('"')
      case i: Int =>
        out.write('"')
        JsonCodec.intCodec.encode(i, out)
        out.write('"')
      case l: Long =>
        out.write('"')
        JsonCodec.longCodec.encode(l, out)
        out.write('"')
      case f: Float =>
        out.write('"')
        JsonCodec.floatCodec.encode(f, out)
        out.write('"')
      case d: Double =>
        out.write('"')
        JsonCodec.doubleCodec.encode(d, out)
        out.write('"')
      case bd: BigDecimal =>
        out.write('"')
        JsonCodec.bigDecimalCodec.encode(bd, out)
        out.write('"')
      case bi: BigInt =>
        out.write('"')
        JsonCodec.bigIntCodec.encode(bi, out)
        out.write('"')
      case d: Duration         => JsonCodec.durationCodec.encode(d, out)
      case dow: DayOfWeek      => JsonCodec.dayOfWeekCodec.encode(dow, out)
      case i: Instant          => JsonCodec.instantCodec.encode(i, out)
      case ld: LocalDate       => JsonCodec.localDateCodec.encode(ld, out)
      case ldt: LocalDateTime  => JsonCodec.localDateTimeCodec.encode(ldt, out)
      case lt: LocalTime       => JsonCodec.localTimeCodec.encode(lt, out)
      case m: Month            => JsonCodec.monthCodec.encode(m, out)
      case md: MonthDay        => JsonCodec.monthDayCodec.encode(md, out)
      case odt: OffsetDateTime => JsonCodec.offsetDateTimeCodec.encode(odt, out)
      case ot: OffsetTime      => JsonCodec.offsetTimeCodec.encode(ot, out)
      case p: Period           => JsonCodec.periodCodec.encode(p, out)
      case y: Year             => JsonCodec.yearCodec.encode(y, out)
      case ym: YearMonth       => JsonCodec.yearMonthCodec.encode(ym, out)
      case zo: ZoneOffset      => JsonCodec.zoneOffsetCodec.encode(zo, out)
      case zi: ZoneId          => JsonCodec.zoneIdCodec.encode(zi, out)
      case zdt: ZonedDateTime  => JsonCodec.zonedDateTimeCodec.encode(zdt, out)
      case c: Currency         => JsonCodec.currencyCodec.encode(c, out)
      case uuid: UUID          => JsonCodec.uuidCodec.encode(uuid, out)
      case x                   => JsonCodec.stringCodec.encode(x.toString, out)
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
        ) throw new JsonCodecError(Nil, "Illegal surrogate pair")
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
