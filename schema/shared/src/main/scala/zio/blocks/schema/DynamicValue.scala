package zio.blocks.schema

import zio.blocks.schema.patch.{DynamicPatch, Differ}

sealed trait DynamicValue {
  override def toString: String = DynamicValue.renderToString(this)

  def typeIndex: Int

  def compare(that: DynamicValue): Int

  final def >(that: DynamicValue): Boolean = compare(that) > 0

  final def >=(that: DynamicValue): Boolean = compare(that) >= 0

  final def <(that: DynamicValue): Boolean = compare(that) < 0

  final def <=(that: DynamicValue): Boolean = compare(that) <= 0

  def diff(that: DynamicValue): DynamicPatch = DynamicValue.diff(this, that)
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

  private[schema] def renderToString(value: DynamicValue): String = {
    val sb = new StringBuilder
    renderToStringBuilder(value, sb)
    sb.toString
  }

  private def renderToStringBuilder(value: DynamicValue, sb: StringBuilder): Unit = value match {
    case Primitive(pv) =>
      renderPrimitiveValue(pv, sb)

    case Record(fields) =>
      sb.append("{ ")
      var first = true
      val iter  = fields.iterator
      while (iter.hasNext) {
        val (name, v) = iter.next()
        if (!first) sb.append(", ")
        first = false
        sb.append(name).append(": ")
        renderToStringBuilder(v, sb)
      }
      sb.append(" }")

    case Variant(caseName, v) =>
      renderToStringBuilder(v, sb)
      sb.append(" @ {tag: \"").append(escapeString(caseName)).append("\"}")

    case Sequence(elements) =>
      sb.append('[')
      var first = true
      val iter  = elements.iterator
      while (iter.hasNext) {
        if (!first) sb.append(", ")
        first = false
        renderToStringBuilder(iter.next(), sb)
      }
      sb.append(']')

    case Map(entries) =>
      sb.append("{ ")
      var first = true
      val iter  = entries.iterator
      while (iter.hasNext) {
        val (k, v) = iter.next()
        if (!first) sb.append(", ")
        first = false
        renderDynamicValueKey(k, sb)
        sb.append(": ")
        renderToStringBuilder(v, sb)
      }
      sb.append(" }")
  }

  private def renderDynamicValueKey(key: DynamicValue, sb: StringBuilder): Unit = key match {
    case Primitive(PrimitiveValue.String(s)) =>
      sb.append('"').append(escapeString(s)).append('"')
    case other =>
      renderToStringBuilder(other, sb)
  }

  private def renderPrimitiveValue(pv: PrimitiveValue, sb: StringBuilder): Unit = pv match {
    case PrimitiveValue.Unit              => sb.append("()")
    case PrimitiveValue.Boolean(v)        => sb.append(v)
    case PrimitiveValue.Byte(v)           => sb.append(v)
    case PrimitiveValue.Short(v)          => sb.append(v)
    case PrimitiveValue.Int(v)            => sb.append(v)
    case PrimitiveValue.Long(v)           => sb.append(v)
    case PrimitiveValue.Float(v)          => sb.append(v)
    case PrimitiveValue.Double(v)         => sb.append(v)
    case PrimitiveValue.Char(v)           => sb.append('\'').append(escapeChar(v)).append('\'')
    case PrimitiveValue.String(v)         => sb.append('"').append(escapeString(v)).append('"')
    case PrimitiveValue.BigInt(v)         => sb.append(v)
    case PrimitiveValue.BigDecimal(v)     => sb.append(v)
    case v: PrimitiveValue.DayOfWeek      => appendTypedPrimitive(sb, v.value.toString, "dayOfWeek")
    case v: PrimitiveValue.Duration       => appendTypedPrimitive(sb, v.value.toString, "duration")
    case v: PrimitiveValue.Instant        => appendTypedPrimitive(sb, v.value.toString, "instant")
    case v: PrimitiveValue.LocalDate      => appendTypedPrimitive(sb, v.value.toString, "localDate")
    case v: PrimitiveValue.LocalDateTime  => appendTypedPrimitive(sb, v.value.toString, "localDateTime")
    case v: PrimitiveValue.LocalTime      => appendTypedPrimitive(sb, v.value.toString, "localTime")
    case v: PrimitiveValue.Month          => appendTypedPrimitive(sb, v.value.toString, "month")
    case v: PrimitiveValue.MonthDay       => appendTypedPrimitive(sb, v.value.toString, "monthDay")
    case v: PrimitiveValue.OffsetDateTime => appendTypedPrimitive(sb, v.value.toString, "offsetDateTime")
    case v: PrimitiveValue.OffsetTime     => appendTypedPrimitive(sb, v.value.toString, "offsetTime")
    case v: PrimitiveValue.Period         => appendTypedPrimitive(sb, v.value.toString, "period")
    case v: PrimitiveValue.Year           => appendTypedPrimitive(sb, v.value.toString, "year")
    case v: PrimitiveValue.YearMonth      => appendTypedPrimitive(sb, v.value.toString, "yearMonth")
    case v: PrimitiveValue.ZoneId         => appendTypedPrimitive(sb, v.value.toString, "zoneId")
    case v: PrimitiveValue.ZoneOffset     => appendTypedPrimitive(sb, v.value.toString, "zoneOffset")
    case v: PrimitiveValue.ZonedDateTime  => appendTypedPrimitive(sb, v.value.toString, "zonedDateTime")
    case v: PrimitiveValue.Currency       => appendTypedPrimitive(sb, v.value.toString, "currency")
    case v: PrimitiveValue.UUID           => appendTypedPrimitive(sb, v.value.toString, "uuid")
  }

  private def appendTypedPrimitive(sb: StringBuilder, value: String, typeName: String): Unit =
    sb.append('"').append(value).append("\" @ {type: \"").append(typeName).append("\"}")

  private def escapeString(s: String): String = {
    val sb  = new StringBuilder
    var idx = 0
    while (idx < s.length) {
      sb.append(escapeChar(s.charAt(idx)))
      idx += 1
    }
    sb.toString
  }

  private def escapeChar(c: Char): String = c match {
    case '"'          => "\\\""
    case '\\'         => "\\\\"
    case '\b'         => "\\b"
    case '\f'         => "\\f"
    case '\n'         => "\\n"
    case '\r'         => "\\r"
    case '\t'         => "\\t"
    case _ if c < ' ' =>
      val hex = Integer.toHexString(c.toInt)
      "\\u" + ("0" * (4 - hex.length)) + hex
    case _ => c.toString
  }
}
