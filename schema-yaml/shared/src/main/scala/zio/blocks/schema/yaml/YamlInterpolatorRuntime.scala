package zio.blocks.schema.yaml

import java.time._

private[schema] object YamlInterpolatorRuntime {

  def validateYamlLiteral(sc: StringContext, contexts: Seq[YamlInterpolationContext]): Unit = {
    val parts     = sc.parts.iterator
    val contextIt = contexts.iterator
    val sb        = new StringBuilder(128)
    sb.append(parts.next())
    while (parts.hasNext && contextIt.hasNext) {
      val ctx = contextIt.next()
      ctx match {
        case YamlInterpolationContext.Key =>
          sb.append("_placeholder_key_")
        case YamlInterpolationContext.Value =>
          sb.append("null")
        case YamlInterpolationContext.InString =>
          ()
      }
      sb.append(parts.next())
    }
    YamlReader.read(sb.toString) match {
      case Right(_)    => ()
      case Left(error) => throw error
    }
  }

  def yamlWithContexts(sc: StringContext, args: Seq[Any], contexts: Seq[YamlInterpolationContext]): Yaml = {
    val parts     = sc.parts.iterator
    val argsIt    = args.iterator
    val contextIt = contexts.iterator
    val sb        = new StringBuilder(128)
    sb.append(parts.next())
    while (argsIt.hasNext && contextIt.hasNext) {
      val arg = argsIt.next()
      val ctx = contextIt.next()
      ctx match {
        case YamlInterpolationContext.Key      => writeKeyValue(sb, arg)
        case YamlInterpolationContext.Value    => writeValue(sb, arg)
        case YamlInterpolationContext.InString => writeInString(sb, arg)
      }
      sb.append(parts.next())
    }
    YamlReader.read(sb.toString) match {
      case Right(yaml) => yaml
      case Left(error) => throw error
    }
  }

  private[this] def writeKeyValue(sb: StringBuilder, key: Any): Unit = key match {
    case s: String             => appendYamlScalar(sb, s)
    case c: Char               => sb.append(c)
    case b: Boolean            => sb.append(b)
    case b: Byte               => sb.append(b.toInt)
    case sh: Short             => sb.append(sh.toInt)
    case i: Int                => sb.append(i)
    case l: Long               => sb.append(l)
    case f: Float              => sb.append(f)
    case d: Double             => sb.append(d)
    case bd: BigDecimal        => sb.append(bd)
    case bi: BigInt            => sb.append(bi)
    case _: Unit               => sb.append("unit")
    case d: Duration           => appendYamlScalar(sb, d.toString)
    case dow: DayOfWeek        => appendYamlScalar(sb, dow.toString)
    case i: Instant            => appendYamlScalar(sb, i.toString)
    case ld: LocalDate         => appendYamlScalar(sb, ld.toString)
    case ldt: LocalDateTime    => appendYamlScalar(sb, ldt.toString)
    case lt: LocalTime         => appendYamlScalar(sb, lt.toString)
    case m: Month              => appendYamlScalar(sb, m.toString)
    case md: MonthDay          => appendYamlScalar(sb, md.toString)
    case odt: OffsetDateTime   => appendYamlScalar(sb, odt.toString)
    case ot: OffsetTime        => appendYamlScalar(sb, ot.toString)
    case p: Period             => appendYamlScalar(sb, p.toString)
    case y: Year               => appendYamlScalar(sb, y.toString)
    case ym: YearMonth         => appendYamlScalar(sb, ym.toString)
    case zo: ZoneOffset        => appendYamlScalar(sb, zo.toString)
    case zi: ZoneId            => appendYamlScalar(sb, zi.toString)
    case zdt: ZonedDateTime    => appendYamlScalar(sb, zdt.toString)
    case c: java.util.Currency => sb.append(c.getCurrencyCode)
    case uuid: java.util.UUID  => appendYamlScalar(sb, uuid.toString)
    case x                     =>
      throw new IllegalArgumentException(
        s"Unexpected type in key position: ${x.getClass.getName}. This should have been caught at compile time."
      )
  }

  private[this] def writeValue(sb: StringBuilder, value: Any): Unit = value match {
    case y: Yaml               => sb.append(YamlWriter.write(y, YamlOptions.default))
    case s: String             => appendYamlScalar(sb, s)
    case b: Boolean            => sb.append(b)
    case b: Byte               => sb.append(b.toInt)
    case sh: Short             => sb.append(sh.toInt)
    case i: Int                => sb.append(i)
    case l: Long               => sb.append(l)
    case f: Float              => sb.append(f)
    case d: Double             => sb.append(d)
    case c: Char               => appendYamlScalar(sb, c.toString)
    case bd: BigDecimal        => sb.append(bd)
    case bi: BigInt            => sb.append(bi)
    case _: Unit               => sb.append("null")
    case dow: DayOfWeek        => appendYamlScalar(sb, dow.toString)
    case d: Duration           => appendYamlScalar(sb, d.toString)
    case i: Instant            => appendYamlScalar(sb, i.toString)
    case ld: LocalDate         => appendYamlScalar(sb, ld.toString)
    case ldt: LocalDateTime    => appendYamlScalar(sb, ldt.toString)
    case lt: LocalTime         => appendYamlScalar(sb, lt.toString)
    case m: Month              => appendYamlScalar(sb, m.toString)
    case md: MonthDay          => appendYamlScalar(sb, md.toString)
    case odt: OffsetDateTime   => appendYamlScalar(sb, odt.toString)
    case ot: OffsetTime        => appendYamlScalar(sb, ot.toString)
    case p: Period             => appendYamlScalar(sb, p.toString)
    case y: Year               => appendYamlScalar(sb, y.toString)
    case ym: YearMonth         => appendYamlScalar(sb, ym.toString)
    case zo: ZoneOffset        => appendYamlScalar(sb, zo.toString)
    case zi: ZoneId            => appendYamlScalar(sb, zi.toString)
    case zdt: ZonedDateTime    => appendYamlScalar(sb, zdt.toString)
    case c: java.util.Currency => appendYamlScalar(sb, c.getCurrencyCode)
    case uuid: java.util.UUID  => appendYamlScalar(sb, uuid.toString)
    case null                  => sb.append("null")
    case opt: Option[_]        =>
      opt match {
        case Some(v) => writeValue(sb, v)
        case _       => sb.append("null")
      }
    case seq: Iterable[_] =>
      sb.append('[')
      var first = true
      seq.foreach { x =>
        if (!first) sb.append(", ")
        else first = false
        writeValue(sb, x)
      }
      sb.append(']')
    case arr: Array[_] =>
      sb.append('[')
      var idx = 0
      while (idx < arr.length) {
        if (idx > 0) sb.append(", ")
        writeValue(sb, arr(idx))
        idx += 1
      }
      sb.append(']')
    case x => appendYamlScalar(sb, x.toString)
  }

  private[this] def writeInString(sb: StringBuilder, value: Any): Unit = value match {
    case s: String             => sb.append(s)
    case c: Char               => sb.append(c)
    case b: Boolean            => sb.append(b)
    case b: Byte               => sb.append(b.toInt)
    case sh: Short             => sb.append(sh.toInt)
    case i: Int                => sb.append(i)
    case l: Long               => sb.append(l)
    case f: Float              => sb.append(f)
    case d: Double             => sb.append(d)
    case bd: BigDecimal        => sb.append(bd)
    case bi: BigInt            => sb.append(bi)
    case _: Unit               => sb.append("()")
    case d: Duration           => sb.append(d.toString)
    case dow: DayOfWeek        => sb.append(dow.toString)
    case i: Instant            => sb.append(i.toString)
    case ld: LocalDate         => sb.append(ld.toString)
    case ldt: LocalDateTime    => sb.append(ldt.toString)
    case lt: LocalTime         => sb.append(lt.toString)
    case m: Month              => sb.append(m.toString)
    case md: MonthDay          => sb.append(md.toString)
    case odt: OffsetDateTime   => sb.append(odt.toString)
    case ot: OffsetTime        => sb.append(ot.toString)
    case p: Period             => sb.append(p.toString)
    case y: Year               => sb.append(y.toString)
    case ym: YearMonth         => sb.append(ym.toString)
    case zo: ZoneOffset        => sb.append(zo.toString)
    case zi: ZoneId            => sb.append(zi.toString)
    case zdt: ZonedDateTime    => sb.append(zdt.toString)
    case c: java.util.Currency => sb.append(c.getCurrencyCode)
    case uuid: java.util.UUID  => sb.append(uuid.toString)
    case x                     =>
      throw new IllegalArgumentException(
        s"Unexpected type in string literal: ${x.getClass.getName}. This should have been caught at compile time."
      )
  }

  private[this] def appendYamlScalar(sb: StringBuilder, value: String): Unit =
    if (needsQuoting(value)) {
      sb.append('"')
      var i = 0
      while (i < value.length) {
        value.charAt(i) match {
          case '"'           => sb.append("\\\"")
          case '\\'          => sb.append("\\\\")
          case '\n'          => sb.append("\\n")
          case '\t'          => sb.append("\\t")
          case '\r'          => sb.append("\\r")
          case '\b'          => sb.append("\\b")
          case c if c < 0x20 =>
            sb.append("\\u")
            sb.append(String.format("%04x", Int.box(c.toInt)))
          case c => sb.append(c)
        }
        i += 1
      }
      sb.append('"')
    } else sb.append(value)

  private[this] def needsQuoting(value: String): Boolean = {
    if (value.isEmpty) return true
    if (isSpecialValue(value)) return true
    val c0 = value.charAt(0)
    if (
      c0 == '\'' || c0 == '"' || c0 == '{' || c0 == '[' || c0 == '|' || c0 == '>' ||
      c0 == '%' || c0 == '@' || c0 == '`' || c0 == '&' || c0 == '*' || c0 == '!' || c0 == '?'
    ) return true
    if (looksNumeric(value)) return true
    var i = 0
    while (i < value.length) {
      val c = value.charAt(i)
      if (c < 0x20 && c != '\t') return true
      if (c == '\n' || c == '\r') return true
      if (c == ':') return true
      if (c == '#' && i > 0 && value.charAt(i - 1) == ' ') return true
      i += 1
    }
    false
  }

  private[this] def isSpecialValue(value: String): Boolean = value match {
    case "null" | "~" | "Null" | "NULL"                         => true
    case "true" | "false" | "True" | "False" | "TRUE" | "FALSE" => true
    case "yes" | "no" | "Yes" | "No" | "YES" | "NO"             => true
    case "on" | "off" | "On" | "Off" | "ON" | "OFF"             => true
    case ".inf" | "-.inf" | ".Inf" | "-.Inf" | ".INF" | "-.INF" => true
    case ".nan" | ".NaN" | ".NAN"                               => true
    case _                                                      => false
  }

  private[this] def looksNumeric(value: String): Boolean = {
    val c0 = value.charAt(0)
    if (c0 >= '0' && c0 <= '9') return true
    if ((c0 == '+' || c0 == '-') && value.length > 1) {
      val c1 = value.charAt(1)
      if (c1 >= '0' && c1 <= '9') return true
      if (c1 == '.') return true
    }
    if (c0 == '.' && value.length > 1) {
      val c1 = value.charAt(1)
      if (c1 >= '0' && c1 <= '9') return true
    }
    false
  }
}
