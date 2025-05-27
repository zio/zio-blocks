package zio.blocks.schema

sealed trait DynamicValue {
  final def toJson: String = json.dynamicValueToJson(this)
}

object DynamicValue {
  case class Record(fields: IndexedSeq[(String, DynamicValue)]) extends DynamicValue {
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
  }

  case class Variant(caseName: String, value: DynamicValue) extends DynamicValue {
    override def equals(that: Any): Boolean = that match {
      case Variant(thatCaseName, thatValue) => caseName == thatCaseName && value == thatValue
      case _                                => false
    }

    override def hashCode: Int = 31 * caseName.hashCode + value.hashCode
  }

  case class Sequence(elements: IndexedSeq[DynamicValue]) extends DynamicValue {
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
  }

  case class Map(entries: IndexedSeq[(DynamicValue, DynamicValue)]) extends DynamicValue {
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
  }

  case class Primitive(value: PrimitiveValue) extends DynamicValue {
    override def equals(that: Any): Boolean = that match {
      case Primitive(thatValue) => value == thatValue
      case _                    => false
    }

    override def hashCode: Int = value.hashCode
  }

  case class Lazy(value: () => DynamicValue) extends DynamicValue {
    override def equals(that: Any): Boolean = that match {
      case other: Lazy => this eq other // Pure identity comparison
      case _           => false         // Lazy values are never equal to non-lazy values
    }

    override def hashCode: Int = System.identityHashCode(this)

    override def toString: String = s"Lazy(value = $value)"
  }

  final def fromJson(rawJson: String): DynamicValue = json.dynamicValueFromJson(rawJson)
}
