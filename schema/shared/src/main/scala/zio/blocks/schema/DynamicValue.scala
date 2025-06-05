package zio.blocks.schema

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

    def compare(that: Primitive): Int = Primitive.ordering.compare(this, that)
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

    def compare(that: Record): Int = Record.ordering.compare(this, that)
  }
  object Record {
    implicit val ordering: Ordering[Record] = new Ordering[Record] {
      def compare(x: Record, y: Record): Int = {
        val len = x.fields.length
        if (len != y.fields.length) return len.compareTo(y.fields.length)
        var idx = 0
        while (idx < len) {
          val kv1 = x.fields(idx)
          val kv2 = y.fields(idx)
          if (kv1._1 != kv2._1) return kv1._1.compare(kv2._1)
          if (kv1._2 != kv2._2) return kv1._2.compare(kv2._2)
          idx += 1
        }
        0
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

    def compare(that: Variant): Int = Variant.ordering.compare(this, that)
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
          val v1 = elements(idx)
          val v2 = thatElements(idx)
          if (v1 != v2) return false
          idx += 1
        }
        true
      case _ => false
    }

    override def hashCode: Int = elements.hashCode

    def typeIndex: Int = 3

    def compare(that: Sequence): Int = Sequence.ordering.compare(this, that)
  }

  object Sequence {
    implicit val ordering: Ordering[Sequence] = new Ordering[Sequence] {
      def compare(x: Sequence, y: Sequence): Int = {
        val len = x.elements.length
        if (len != y.elements.length) return len.compareTo(y.elements.length)
        var idx = 0
        while (idx < len) {
          val v1 = x.elements(idx)
          val v2 = y.elements(idx)
          if (v1 != v2) return v1.compare(v2)
          idx += 1
        }
        0
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

    def compare(that: Map): Int = Map.ordering.compare(this, that)
  }

  object Map {
    implicit val ordering: Ordering[Map] = new Ordering[Map] {
      def compare(x: Map, y: Map): Int = {
        val len = x.entries.length
        if (len != y.entries.length) return len.compareTo(y.entries.length)
        var idx = 0
        while (idx < len) {
          val kv1 = x.entries(idx)
          val kv2 = y.entries(idx)
          if (kv1._1 != kv2._1) return kv1._1.compare(kv2._1)
          if (kv1._2 != kv2._2) return kv1._2.compare(kv2._2)
          idx += 1
        }
        0
      }
    }
  }

  final def fromJson(rawJson: String): DynamicValue = json.dynamicValueFromJson(rawJson)

  implicit def ordering: Ordering[DynamicValue] = new Ordering[DynamicValue] {
    def compare(x: DynamicValue, y: DynamicValue): Int = (x, y) match {
      case (x @ Primitive(_), y @ Primitive(_))   => x.compare(y)
      case (x @ Record(_), y @ Record(_))         => x.compare(y)
      case (x @ Variant(_, _), y @ Variant(_, _)) => x.compare(y)
      case (x @ Sequence(_), y @ Sequence(_))     => x.compare(y)
      case (x @ Map(_), y @ Map(_))               => x.compare(y)
      case (x, y)                                 => x.typeIndex.compareTo(y.typeIndex)
    }
  }
}
