package zio.blocks.schema

import zio.blocks.schema.patch.{DynamicPatch, Differ}

sealed trait DynamicValue {
  def typeIndex: Int

  def compare(that: DynamicValue): Int

  final def >(that: DynamicValue): Boolean = compare(that) > 0

  final def >=(that: DynamicValue): Boolean = compare(that) >= 0

  final def <(that: DynamicValue): Boolean = compare(that) < 0

  final def <=(that: DynamicValue): Boolean = compare(that) <= 0

  override def toString: String = DynamicValue.render(this)

  def diff(that: DynamicValue): DynamicPatch = DynamicValue.diff(this, that)
}

object DynamicValue {
  private def indent(sb: StringBuilder, depth: Int): Unit = {
    var i = 0
    while (i < depth) {
      sb.append("  ")
      i += 1
    }
  }

  def render(value: DynamicValue): String = {
    val sb = new StringBuilder
    render(sb, value, 0)
    sb.toString()
  }

  private def render(sb: StringBuilder, value: DynamicValue, depth: Int): Unit = value match {
    case Primitive(p)     => renderPrimitive(sb, p)
    case Record(fields)   => renderRecord(sb, fields, depth)
    case Variant(n, v)    => renderVariant(sb, n, v, depth)
    case Sequence(elems)  => renderSequence(sb, elems, depth)
    case Map(entries)     => renderMap(sb, entries, depth)
  }

  private def renderPrimitive(sb: StringBuilder, value: PrimitiveValue): Unit = value match {
    case PrimitiveValue.Unit => sb.append("null")
    case PrimitiveValue.Boolean(b) => sb.append(b)
    case PrimitiveValue.Byte(b) => sb.append(b)
    case PrimitiveValue.Short(s) => sb.append(s)
    case PrimitiveValue.Int(i) => sb.append(i)
    case PrimitiveValue.Long(l) => sb.append(l)
    case PrimitiveValue.Float(f) => sb.append(f)
    case PrimitiveValue.Double(d) => sb.append(d)
    case PrimitiveValue.String(s) => sb.append('"').append(s).append('"')
    case PrimitiveValue.Char(c) => sb.append('"').append(c).append('"')
    case PrimitiveValue.Instant(i) =>
      sb.append(i.getEpochSecond).append(" @ {type: \"instant\"}")
    case PrimitiveValue.LocalDate(d) =>
      sb.append('"').append(d.toString).append("\" @ {type: \"localDate\"}")
    case PrimitiveValue.LocalTime(t) =>
      sb.append('"').append(t.toString).append("\" @ {type: \"localTime\"}")
    case PrimitiveValue.LocalDateTime(dt) =>
      sb.append('"').append(dt.toString).append("\" @ {type: \"localDateTime\"}")
    case PrimitiveValue.OffsetTime(ot) =>
      sb.append('"').append(ot.toString).append("\" @ {type: \"offsetTime\"}")
    case PrimitiveValue.OffsetDateTime(odt) =>
      sb.append('"').append(odt.toString).append("\" @ {type: \"offsetDateTime\"}")
    case PrimitiveValue.ZonedDateTime(zdt) =>
      sb.append('"').append(zdt.toString).append("\" @ {type: \"zonedDateTime\"}")
    case PrimitiveValue.Duration(d) =>
      sb.append('"').append(d.toString).append("\" @ {type: \"duration\"}")
    case PrimitiveValue.Period(p) =>
      sb.append('"').append(p.toString).append("\" @ {type: \"period\"}")
    case PrimitiveValue.Year(y) =>
      sb.append(y.getValue).append(" @ {type: \"year\"}")
    case PrimitiveValue.YearMonth(ym) =>
      sb.append('"').append(ym.toString).append("\" @ {type: \"yearMonth\"}")
    case PrimitiveValue.Month(m) =>
      sb.append('"').append(m.toString).append("\" @ {type: \"month\"}")
    case PrimitiveValue.MonthDay(md) =>
      sb.append('"').append(md.toString).append("\" @ {type: \"monthDay\"}")
    case PrimitiveValue.ZoneId(z) =>
      sb.append('"').append(z.toString).append("\" @ {type: \"zoneId\"}")
    case PrimitiveValue.ZoneOffset(z) =>
      sb.append('"').append(z.toString).append("\" @ {type: \"zoneOffset\"}")
    case PrimitiveValue.BigInt(bi) => sb.append(bi)
    case PrimitiveValue.BigDecimal(bd) => sb.append(bd)
    case PrimitiveValue.Currency(c) =>
      sb.append('"').append(c.toString).append("\" @ {type: \"currency\"}")
    case PrimitiveValue.DayOfWeek(d) =>
      sb.append('"').append(d.toString).append("\" @ {type: \"dayOfWeek\"}")
    case PrimitiveValue.UUID(u) =>
      sb.append('"').append(u.toString).append("\" @ {type: \"uuid\"}")
  }

  private def renderRecord(sb: StringBuilder, fields: Vector[(Predef.String, DynamicValue)], depth: Int): Unit = {
    if (fields.isEmpty) sb.append("{}")
    else {
      sb.append("{\n")
      fields.zipWithIndex.foreach { case ((k, v), idx) =>
        indent(sb, depth + 1)
        sb.append(k).append(": ")
        render(sb, v, depth + 1)
        if (idx < fields.length - 1) sb.append(",")
        sb.append("\n")
      }
      indent(sb, depth)
      sb.append("}")
    }
  }

  private def renderVariant(sb: StringBuilder, name: Predef.String, value: DynamicValue, depth: Int): Unit = {
    value match {
      case Primitive(PrimitiveValue.Unit) => sb.append("{}")
      case _ =>
        sb.append("{ value: ")
        render(sb, value, depth)
        sb.append(" }")
    }
    sb.append(" @ {tag: \"").append(name).append("\"}")
  }

  private def renderSequence(sb: StringBuilder, elements: Vector[DynamicValue], depth: Int): Unit = {
    if (elements.isEmpty) sb.append("[]")
    else {
      sb.append("[")
      elements.zipWithIndex.foreach { case (v, idx) =>
        if (idx > 0) sb.append(", ")
        render(sb, v, depth)
      }
      sb.append("]")
    }
  }

  private def renderMap(sb: StringBuilder, entries: Vector[(DynamicValue, DynamicValue)], depth: Int): Unit = {
    if (entries.isEmpty) sb.append("{}")
    else {
      sb.append("{\n")
      entries.zipWithIndex.foreach { case ((k, v), idx) =>
        indent(sb, depth + 1)
        k match {
          case Primitive(PrimitiveValue.String(s)) => sb.append('"').append(s).append('"')
          case _ => render(sb, k, depth + 1)
        }
        sb.append(": ")
        render(sb, v, depth + 1)
        if (idx < entries.length - 1) sb.append(",")
        sb.append("\n")
      }
      indent(sb, depth)
      sb.append("}")
    }
  }

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
}
