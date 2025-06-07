package zio.blocks.schema

import scala.annotation.switch

sealed trait DynamicValue {
  final def toJson: String = json.dynamicValueToJson(this)

  def typeIndex: Int

  final def compare(that: DynamicValue): Int = DynamicValue.ordering.compare(this, that)

  final def >(that: DynamicValue): Boolean = compare(that) > 0

  final def >=(that: DynamicValue): Boolean = compare(that) >= 0

  final def <(that: DynamicValue): Boolean = compare(that) < 0

  final def <=(that: DynamicValue): Boolean = compare(that) <= 0
}

object DynamicValue {
  case class Primitive(value: PrimitiveValue) extends DynamicValue {
    override def equals(that: Any): Boolean = that match {
      case Primitive(thatValue) => value == thatValue
      case _                    => false
    }

    override def hashCode: Int = value.hashCode

    def typeIndex: Int = 0

  }
  object Primitive {
    implicit val ordering: Ordering[Primitive] = new Ordering[Primitive] {
      def compare(x: Primitive, y: Primitive): Int = x.value.compare(y.value)
    }
  }

  final case class Record(fields: IndexedSeq[(String, DynamicValue)]) extends DynamicValue {
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
  }
  object Record {
    implicit val ordering: Ordering[Record] = new Ordering[Record] {
      def compare(x: Record, y: Record): Int = {
        val xs     = x.fields
        val ys     = y.fields
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
      }
    }
  }

  final case class Variant(caseName: String, value: DynamicValue) extends DynamicValue {
    override def equals(that: Any): Boolean = that match {
      case Variant(thatCaseName, thatValue) => caseName == thatCaseName && value == thatValue
      case _                                => false
    }

    override def hashCode: Int = 31 * caseName.hashCode + value.hashCode

    def typeIndex: Int = 2
  }

  object Variant {
    implicit val ordering: Ordering[Variant] = new Ordering[Variant] {
      def compare(x: Variant, y: Variant): Int = {
        val cmp = x.caseName.compare(y.caseName)
        if (cmp != 0) return cmp
        x.value.compare(y.value)
      }
    }
  }

  final case class Sequence(elements: IndexedSeq[DynamicValue]) extends DynamicValue {
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
  }

  object Sequence {
    implicit val ordering: Ordering[Sequence] = new Ordering[Sequence] {
      def compare(x: Sequence, y: Sequence): Int = {
        val xs     = x.elements
        val ys     = y.elements
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
      }
    }
  }

  final case class Map(entries: IndexedSeq[(DynamicValue, DynamicValue)]) extends DynamicValue {
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
  }

  object Map {
    implicit val ordering: Ordering[Map] = new Ordering[Map] {
      def compare(x: Map, y: Map): Int = {
        val xs     = x.entries
        val ys     = y.entries
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
      }
    }
  }

  final def fromJson(rawJson: String): DynamicValue = json.dynamicValueFromJson(rawJson)

  implicit def ordering: Ordering[DynamicValue] = new Ordering[DynamicValue] {
    def compare(x: DynamicValue, y: DynamicValue): Int = {
      val xTypeIndex = x.typeIndex
      val cmp        = xTypeIndex.compareTo(y.typeIndex)
      if (cmp != 0) return cmp
      (xTypeIndex: @switch) match {
        case 0 => Primitive.ordering.compare(x.asInstanceOf[Primitive], y.asInstanceOf[Primitive])
        case 1 => Record.ordering.compare(x.asInstanceOf[Record], y.asInstanceOf[Record])
        case 2 => Variant.ordering.compare(x.asInstanceOf[Variant], y.asInstanceOf[Variant])
        case 3 => Sequence.ordering.compare(x.asInstanceOf[Sequence], y.asInstanceOf[Sequence])
        case 4 => Map.ordering.compare(x.asInstanceOf[Map], y.asInstanceOf[Map])
      }
    }
  }
}
