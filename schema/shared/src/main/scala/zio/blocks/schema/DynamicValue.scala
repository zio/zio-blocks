package zio.blocks.schema

import zio.blocks.schema.patch.{DynamicPatch, Differ}

sealed trait DynamicValue {
  def typeIndex: Int

  def compare(that: DynamicValue): Int

  override def toString: String = DynamicValue.render(this, 0)

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

  private[schema] def render(dv: DynamicValue, indent: Int): String = {
    val pad      = "  " * indent
    val padInner = "  " * (indent + 1)
    dv match {
      case Primitive(pv)  => renderPrimitive(pv)
      case Record(fields) =>
        if (fields.isEmpty) "{}"
        else {
          val sb    = new StringBuilder("{\n")
          var first = true
          fields.foreach { case (k, v) =>
            if (!first) sb.append(",\n")
            first = false
            sb.append(padInner).append(k).append(": ").append(render(v, indent + 1))
          }
          sb.append("\n").append(pad).append("}").toString
        }
      case Variant(caseName, value) =>
        val inner = value match {
          case Record(Vector()) => "{}"
          case _                => render(value, indent)
        }
        s"$inner @ {tag: \"$caseName\"}"
      case Sequence(elements) =>
        if (elements.isEmpty) "[]"
        else if (elements.forall(isSimple)) {
          "[" + elements.map(render(_, 0)).mkString(", ") + "]"
        } else {
          val sb    = new StringBuilder("[\n")
          var first = true
          elements.foreach { e =>
            if (!first) sb.append(",\n")
            first = false
            sb.append(padInner).append(render(e, indent + 1))
          }
          sb.append("\n").append(pad).append("]").toString
        }
      case Map(entries) =>
        if (entries.isEmpty) "{}"
        else {
          val sb    = new StringBuilder("{\n")
          var first = true
          entries.foreach { case (k, v) =>
            if (!first) sb.append(",\n")
            first = false
            sb.append(padInner).append(renderMapKey(k)).append(": ").append(render(v, indent + 1))
          }
          sb.append("\n").append(pad).append("}").toString
        }
    }
  }

  private def isSimple(dv: DynamicValue): Boolean = dv match {
    case Primitive(_) => true
    case _            => false
  }

  private def renderPrimitive(pv: PrimitiveValue): String = pv match {
    case PrimitiveValue.Unit           => "null"
    case PrimitiveValue.Boolean(b)     => b.toString
    case PrimitiveValue.Byte(b)        => b.toString
    case PrimitiveValue.Short(s)       => s.toString
    case PrimitiveValue.Int(i)         => i.toString
    case PrimitiveValue.Long(l)        => l.toString
    case PrimitiveValue.Float(f)       => f.toString
    case PrimitiveValue.Double(d)      => d.toString
    case PrimitiveValue.Char(c)        => "\"" + c + "\""
    case PrimitiveValue.String(s)      => "\"" + escapeString(s) + "\""
    case PrimitiveValue.BigInt(bi)     => bi.toString
    case PrimitiveValue.BigDecimal(bd) => bd.toString
    case other                         => other.toString + " @ {type: \"" + other.primitiveType.typeName.name + "\"}"
  }

  private def renderMapKey(dv: DynamicValue): String = dv match {
    case Primitive(PrimitiveValue.String(s)) => "\"" + escapeString(s) + "\""
    case Primitive(pv)                       => renderPrimitive(pv)
    case other                               => render(other, 0)
  }

  private def escapeString(s: String): String = {
    val sb = new StringBuilder
    var i  = 0
    while (i < s.length) {
      s.charAt(i) match {
        case '"'  => sb.append("\\\"")
        case '\\' => sb.append("\\\\")
        case '\n' => sb.append("\\n")
        case '\r' => sb.append("\\r")
        case '\t' => sb.append("\\t")
        case c    => sb.append(c)
      }
      i += 1
    }
    sb.toString
  }
}
