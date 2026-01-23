package zio.blocks.schema

import zio.blocks.schema.patch.{DynamicPatch, Differ}

sealed trait DynamicValue {
  def typeIndex: Int

  def compare(that: DynamicValue): Int

  final def >(that: DynamicValue): Boolean = compare(that) > 0

  final def >=(that: DynamicValue): Boolean = compare(that) >= 0

  final def <(that: DynamicValue): Boolean = compare(that) < 0

  final def <=(that: DynamicValue): Boolean = compare(that) <= 0

  def diff(that: DynamicValue): DynamicPatch = DynamicValue.diff(this, that)

  override def toString: String = DynamicValue.toEJSON(this)
}

object DynamicValue {
  case class Primitive(value: PrimitiveValue) extends DynamicValue {
    override def equals(that: Any): Boolean = that match {
      case Primitive(thatValue) => value == thatValue
      case _                    => false
    }

    override def hashCode: Int = value.hashCode

    def typeIndex: Int = 0

    def compare(that: DynamicValue): Int = that match {
      case thatPrimitive: Primitive => value.compare(thatPrimitive.value)
      case _                        => -that.typeIndex
    }
  }

  final case class Record(fields: Vector[(String, DynamicValue)]) extends DynamicValue {
    override def equals(that: Any): Boolean = that match {
      case Record(thatFields) =>
        val len = fields.length
        if (len != thatFields.length) return false
        var idx = 0
        while (idx < len) {
          val kv1 = fields(idx)
          val kv2 = thatFields(idx)
          if (kv1._1 != kv2._1 || kv1._2 != kv2._2) return false
          idx += 1
        }
        true
      case _ => false
    }

    override def hashCode: Int = fields.hashCode

    def typeIndex: Int = 1

    def compare(that: DynamicValue): Int = that match {
      case thatRecord: Record =>
        val xs     = fields
        val ys     = thatRecord.fields
        val xLen   = xs.length
        val yLen   = ys.length
        val minLen = Math.min(xLen, yLen)
        var idx    = 0
        while (idx < minLen) {
          val kv1 = xs(idx)
          val kv2 = ys(idx)
          var cmp = kv1._1.compare(kv2._1)
          if (cmp != 0) return cmp
          cmp = kv1._2.compare(kv2._2)
          if (cmp != 0) return cmp
          idx += 1
        }
        xLen.compareTo(yLen)
      case _ => 1 - that.typeIndex
    }
  }

  final case class Variant(caseName: String, value: DynamicValue) extends DynamicValue {
    override def equals(that: Any): Boolean = that match {
      case Variant(thatCaseName, thatValue) => caseName == thatCaseName && value == thatValue
      case _                                => false
    }

    override def hashCode: Int = 31 * caseName.hashCode + value.hashCode

    def typeIndex: Int = 2

    def compare(that: DynamicValue): Int = that match {
      case thatVariant: Variant =>
        val cmp = caseName.compare(thatVariant.caseName)
        if (cmp != 0) return cmp
        value.compare(thatVariant.value)
      case _ => 2 - that.typeIndex
    }
  }

  final case class Sequence(elements: Vector[DynamicValue]) extends DynamicValue {
    override def equals(that: Any): Boolean = that match {
      case Sequence(thatElements) =>
        val len = elements.length
        if (len != thatElements.length) return false
        var idx = 0
        while (idx < len) {
          if (elements(idx) != thatElements(idx)) return false
          idx += 1
        }
        true
      case _ => false
    }

    override def hashCode: Int = elements.hashCode

    def typeIndex: Int = 3

    def compare(that: DynamicValue): Int = that match {
      case thatSequence: Sequence =>
        val xs     = elements
        val ys     = thatSequence.elements
        val xLen   = xs.length
        val yLen   = ys.length
        val minLen = Math.min(xLen, yLen)
        var idx    = 0
        while (idx < minLen) {
          val cmp = xs(idx).compare(ys(idx))
          if (cmp != 0) return cmp
          idx += 1
        }
        xLen.compareTo(yLen)
      case _ => 3 - that.typeIndex
    }
  }

  final case class Map(entries: Vector[(DynamicValue, DynamicValue)]) extends DynamicValue {
    override def equals(that: Any): Boolean = that match {
      case Map(thatEntries) =>
        val len = entries.length
        if (len != thatEntries.length) return false
        var idx = 0
        while (idx < len) {
          val kv1 = entries(idx)
          val kv2 = thatEntries(idx)
          if (kv1._1 != kv2._1 || kv1._2 != kv2._2) return false
          idx += 1
        }
        true
      case _ => false
    }

    override def hashCode: Int = entries.hashCode

    def typeIndex: Int = 4

    def compare(that: DynamicValue): Int = that match {
      case thatMap: Map =>
        val xs     = entries
        val ys     = thatMap.entries
        val xLen   = xs.length
        val yLen   = ys.length
        val minLen = Math.min(xLen, yLen)
        var idx    = 0
        while (idx < minLen) {
          val kv1 = xs(idx)
          val kv2 = ys(idx)
          var cmp = kv1._1.compare(kv2._1)
          if (cmp != 0) return cmp
          cmp = kv1._2.compare(kv2._2)
          if (cmp != 0) return cmp
          idx += 1
        }
        xLen.compareTo(yLen)
      case _ => 4 - that.typeIndex
    }
  }

  implicit val ordering: Ordering[DynamicValue] = new Ordering[DynamicValue] {
    def compare(x: DynamicValue, y: DynamicValue): Int = x.compare(y)
  }

  /**
   * Compute the difference between two DynamicValues. Returns a DynamicPatch
   * that transforms oldValue into newValue.
   */
  def diff(oldValue: DynamicValue, newValue: DynamicValue): DynamicPatch =
    Differ.diff(oldValue, newValue)

  private[schema] def toEJSON(dv: DynamicValue): String = {
    val sb = new StringBuilder
    appendEJSON(sb, dv)
    sb.toString
  }

  private def appendEJSON(sb: StringBuilder, dv: DynamicValue): Unit = dv match {
    case Primitive(pv) =>
      pv match {
        case PrimitiveValue.String(s) =>
          sb.append('"')
          escapeString(sb, s)
          sb.append('"')
        case PrimitiveValue.Boolean(b) => sb.append(b)
        case PrimitiveValue.Int(i) => sb.append(i)
        case PrimitiveValue.Long(l) => sb.append(l)
        case PrimitiveValue.Double(d) => sb.append(d)
        case PrimitiveValue.Float(f) => sb.append(f)
        case PrimitiveValue.Short(s) => sb.append(s)
        case PrimitiveValue.Byte(b) => sb.append(b)
        case PrimitiveValue.Char(c) => sb.append('\'').append(c).append('\'')
        case PrimitiveValue.BigInt(bi) => sb.append(bi).append(" @ {type: \"bigint\"}")
        case PrimitiveValue.BigDecimal(bd) => sb.append(bd).append(" @ {type: \"bigdecimal\"}")
        case PrimitiveValue.Instant(i) => sb.append(i.toEpochMilli).append(" @ {type: \"instant\"}")
        case PrimitiveValue.LocalDate(ld) => sb.append('"').append(ld).append("\" @ {type: \"localdate\"}")
        case PrimitiveValue.LocalDateTime(ldt) => sb.append('"').append(ldt).append("\" @ {type: \"localdatetime\"}")
        case PrimitiveValue.LocalTime(lt) => sb.append('"').append(lt).append("\" @ {type: \"localtime\"}")
        case PrimitiveValue.OffsetDateTime(odt) => sb.append('"').append(odt).append("\" @ {type: \"offsetdatetime\"}")
        case PrimitiveValue.OffsetTime(ot) => sb.append('"').append(ot).append("\" @ {type: \"offsettime\"}")
        case PrimitiveValue.ZonedDateTime(zdt) => sb.append('"').append(zdt).append("\" @ {type: \"zoneddatetime\"}")
        case PrimitiveValue.Duration(d) => sb.append('"').append(d).append("\" @ {type: \"duration\"}")
        case PrimitiveValue.Period(p) => sb.append('"').append(p).append("\" @ {type: \"period\"}")
        case PrimitiveValue.DayOfWeek(dow) => sb.append('"').append(dow).append("\" @ {type: \"dayofweek\"}")
        case PrimitiveValue.Month(m) => sb.append('"').append(m).append("\" @ {type: \"month\"}")
        case PrimitiveValue.MonthDay(md) => sb.append('"').append(md).append("\" @ {type: \"monthday\"}")
        case PrimitiveValue.Year(y) => sb.append(y.getValue).append(" @ {type: \"year\"}")
        case PrimitiveValue.YearMonth(ym) => sb.append('"').append(ym).append("\" @ {type: \"yearmonth\"}")
        case PrimitiveValue.ZoneId(zi) => sb.append('"').append(zi).append("\" @ {type: \"zoneid\"}")
        case PrimitiveValue.ZoneOffset(zo) => sb.append('"').append(zo).append("\" @ {type: \"zoneoffset\"}")
        case PrimitiveValue.Currency(c) => sb.append('"').append(c.getCurrencyCode).append("\" @ {type: \"currency\"}")
        case PrimitiveValue.UUID(u) => sb.append('"').append(u).append("\" @ {type: \"uuid\"}")
        case PrimitiveValue.Unit => sb.append("null")
      }

    case Record(fields) =>
      sb.append("{ ")
      var first = true
      fields.foreach { case (k, v) =>
        if (!first) sb.append(", ")
        first = false
        sb.append(k).append(": ")
        appendEJSON(sb, v)
      }
      sb.append(" }")

    case Variant(caseName, value) =>
      appendEJSON(sb, value)
      sb.append(" @ {tag: \"").append(caseName).append("\"}")

    case Sequence(elements) =>
      sb.append('[')
      var first = true
      elements.foreach { e =>
        if (!first) sb.append(", ")
        first = false
        appendEJSON(sb, e)
      }
      sb.append(']')

    case Map(entries) =>
      sb.append("{ ")
      var first = true
      entries.foreach { case (k, v) =>
        if (!first) sb.append(", ")
        first = false
        appendEJSON(sb, k)
        sb.append(": ")
        appendEJSON(sb, v)
      }
      sb.append(" }")
  }

  private def escapeString(sb: StringBuilder, s: String): Unit = {
    var i = 0
    while (i < s.length) {
      val c = s.charAt(i)
      c match {
        case '"'  => sb.append("\\\"")
        case '\\' => sb.append("\\\\")
        case '\b' => sb.append("\\b")
        case '\f' => sb.append("\\f")
        case '\n' => sb.append("\\n")
        case '\r' => sb.append("\\r")
        case '\t' => sb.append("\\t")
        case _ if c < 32 => sb.append("\\u%04x".format(c.toInt))
        case _ => sb.append(c)
      }
      i += 1
    }
  }
}
