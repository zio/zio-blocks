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

  /**
   * Interpolation context for each hole in the StringContext.
   *
   * Encoding rules:
   *   - Key: arg is encoded as a JSON object key (a JSON string, without
   *     writing the ':')
   *   - Value: arg is encoded as a full JSON value
   *   - String: arg is encoded as JSON string *content* (escaped, without
   *     surrounding quotes)
   */
  final val CtxKey: Byte    = 0
  final val CtxValue: Byte  = 1
  final val CtxString: Byte = 2

  sealed trait Arg {
    def writeAsValue(out: ByteArrayOutputStream): Unit
    def writeAsKey(out: ByteArrayOutputStream): Unit
    def writeInString(out: ByteArrayOutputStream): Unit
  }

  final case class RuntimeValueArg(value: Any) extends Arg {
    override def writeAsValue(out: ByteArrayOutputStream): Unit  = writeAnyValue(out, value)
    override def writeAsKey(out: ByteArrayOutputStream): Unit    = writeJsonString(out, stringableToCanonicalString(value))
    override def writeInString(out: ByteArrayOutputStream): Unit =
      writeEscapedStringFragment(out, stringableToCanonicalString(value))
  }

  final case class StringableArg(value: Any) extends Arg {
    override def writeAsValue(out: ByteArrayOutputStream): Unit  = writeAnyValue(out, value)
    override def writeAsKey(out: ByteArrayOutputStream): Unit    = writeJsonString(out, stringableToCanonicalString(value))
    override def writeInString(out: ByteArrayOutputStream): Unit =
      writeEscapedStringFragment(out, stringableToCanonicalString(value))
  }

  final case class EncodedValueArg[A](value: A, encoder: JsonEncoder[A]) extends Arg {
    override def writeAsValue(out: ByteArrayOutputStream): Unit =
      // In Scala (w/out explicit nulls), a value typed as A can still be null at runtime.
      // For the interpolator we treat null as JSON null (rather than throwing in user encoders).
      if (value == null) {
        out.write('n')
        out.write('u')
        out.write('l')
        out.write('l')
      } else if (value == (())) {
        // Preserve interpolator semantics for Unit (historically encoded as an empty object).
        out.write('{')
        out.write('}')
      } else {
        Json.jsonCodec.encode(encoder.encode(value), out)
      }
    override def writeAsKey(out: ByteArrayOutputStream): Unit    = writeJsonString(out, stringableToCanonicalString(value))
    override def writeInString(out: ByteArrayOutputStream): Unit =
      writeEscapedStringFragment(out, stringableToCanonicalString(value))
  }

  def jsonWithInterpolation(sc: StringContext, args: Seq[Any]): Json = {
    val wrapped = args.map(RuntimeValueArg(_))
    val ctxs    = Array.fill[Byte](args.length)(CtxValue)
    jsonWithInterpolation(sc, wrapped, ctxs)
  }

  def jsonWithInterpolation(sc: StringContext, args: Seq[Arg], contexts: Array[Byte]): Json = {
    val parts = sc.parts
    val out   = new ByteArrayOutputStream(parts.headOption.map(_.length).getOrElse(0) << 1)
    out.write(parts.headOption.getOrElse(""))

    val holeCount = parts.length - 1
    var i         = 0
    while (i < holeCount) {
      val ctx = if (i < contexts.length) contexts(i) else CtxValue
      val arg = if (i < args.length) args(i) else RuntimeValueArg("")
      ctx match {
        case CtxKey    => arg.writeAsKey(out)
        case CtxString => arg.writeInString(out)
        case _         => arg.writeAsValue(out)
      }
      out.write(parts(i + 1))
      i += 1
    }

    Json.jsonCodec.decode(out.toByteArray) match {
      case Right(json) => json
      case Left(error) => throw error
    }
  }

  private[this] def writeAnyValue(out: ByteArrayOutputStream, value: Any): Unit = value match {
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
        case Some(value) => writeAnyValue(out, value)
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
          writeObjectEntryKey(out, kv._1)
          writeAnyValue(out, kv._2)
      }
      out.write('}')
    case seq: Iterable[_] =>
      out.write('[')
      seq.foreach {
        var comma = false
        x =>
          if (comma) out.write(',')
          else comma = true
          writeAnyValue(out, x)
      }
      out.write(']')
    case arr: Array[_] =>
      out.write('[')
      val len = arr.length
      var idx = 0
      while (idx < len) {
        if (idx > 0) out.write(',')
        writeAnyValue(out, arr(idx))
        idx += 1
      }
      out.write(']')
    case x => out.write(x.toString)
  }

  private[this] def writeJsonString(out: ByteArrayOutputStream, s: String): Unit =
    JsonBinaryCodec.stringCodec.encode(s, out)

  private[this] def writeObjectEntryKey(out: ByteArrayOutputStream, key: Any): Unit = {
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

  private[this] def stringableToCanonicalString(value: Any): String = value match {
    case null                => "null"
    case s: String           => s
    case c: Char             => c.toString
    case u: Unit             => u.toString
    case b: Boolean          => if (b) "true" else "false"
    case b: Byte             => b.toString
    case s: Short            => s.toString
    case i: Int              => i.toString
    case l: Long             => l.toString
    case f: Float            => JsonBinaryCodec.floatCodec.encodeToString(f)
    case d: Double           => JsonBinaryCodec.doubleCodec.encodeToString(d)
    case bi: BigInt          => bi.toString
    case bd: BigDecimal      => bd.toString
    case dow: DayOfWeek      => dow.toString
    case d: Duration         => d.toString
    case i: Instant          => i.toString
    case ld: LocalDate       => ld.toString
    case ldt: LocalDateTime  => ldt.toString
    case lt: LocalTime       => lt.toString
    case m: Month            => m.toString
    case md: MonthDay        => md.toString
    case odt: OffsetDateTime => odt.toString
    case ot: OffsetTime      => ot.toString
    case p: Period           => p.toString
    case y: Year             => y.toString
    case ym: YearMonth       => ym.toString
    case zo: ZoneOffset      => zo.toString
    case zi: ZoneId          => zi.toString
    case zdt: ZonedDateTime  => zdt.toString
    case c: Currency         => c.toString
    case uuid: UUID          => uuid.toString
    case x                   => x.toString
  }

  private[this] def writeEscapedStringFragment(out: ByteArrayOutputStream, s: String): Unit = {
    val len = s.length
    var i   = 0
    while (i < len) {
      val ch = s.charAt(i)
      ch match {
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
        case c if c < ' ' =>
          // Control char -> \u00XX
          out.write('\\')
          out.write('u')
          out.write('0')
          out.write('0')
          val hex = Integer.toHexString(c.toInt)
          if (hex.length == 1) {
            out.write('0')
            out.write(hex.charAt(0))
          } else {
            out.write(hex.charAt(hex.length - 2))
            out.write(hex.charAt(hex.length - 1))
          }
        case other =>
          out.write(other.toString)
      }
      i += 1
    }
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
