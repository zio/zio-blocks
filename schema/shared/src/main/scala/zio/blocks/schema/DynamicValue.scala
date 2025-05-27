package zio.blocks.schema

import scala.util.control.NonFatal


sealed trait DynamicValue {
  final def toJson: String = json.dynamicValueToJson(this)

  final override def equals(that: Any): Boolean = that match {
    case dv: DynamicValue => this.internalEquals(dv)
    case _ => false
  }

  protected def internalEquals(that: DynamicValue): Boolean
}

object DynamicValue {

  final case class Record(fields: IndexedSeq[(String, DynamicValue)]) extends DynamicValue {
    protected def internalEquals(that: DynamicValue): Boolean = that match {
      case Record(thatFields) => 
        fields.length == thatFields.length && 
        fields.zip(thatFields).forall { case ((k1, v1), (k2, v2)) =>
          k1 == k2 && v1.internalEquals(v2)
        }
      case _ => false
    }
    override def hashCode: Int = fields.map { case (k, v) => 
      31 * k.hashCode + v.hashCode
    }.hashCode
  }

  final case class Variant(caseName: String, value: DynamicValue) extends DynamicValue {
    protected def internalEquals(that: DynamicValue): Boolean = that match {
      case Variant(thatCaseName, thatValue) =>
        caseName == thatCaseName && value.internalEquals(thatValue)
      case _ => false
    }
    override def hashCode: Int = 31 * caseName.hashCode + value.hashCode
  }

  final case class Sequence(elements: IndexedSeq[DynamicValue]) extends DynamicValue {
    protected def internalEquals(that: DynamicValue): Boolean = that match {
      case Sequence(thatElements) => 
        elements.length == thatElements.length &&
        elements.zip(thatElements).forall { case (e1, e2) =>
          e1.internalEquals(e2)
        }
      case _ => false
    }
    override def hashCode: Int = elements.map(_.hashCode).hashCode
  }

  final case class Map(entries: IndexedSeq[(DynamicValue, DynamicValue)]) extends DynamicValue {
    protected def internalEquals(that: DynamicValue): Boolean = that match {
      case Map(thatEntries) => 
        entries.length == thatEntries.length &&
        entries.zip(thatEntries).forall { case ((k1, v1), (k2, v2)) =>
          k1.internalEquals(k2) && v1.internalEquals(v2)
        }
      case _ => false
    }
    override def hashCode: Int = entries.map { case (k, v) => 
      31 * k.hashCode + v.hashCode
    }.hashCode
  }

  final case class Primitive(value: PrimitiveValue) extends DynamicValue {
    protected def internalEquals(that: DynamicValue): Boolean = that match {
      case Primitive(thatValue) => value == thatValue
      case _ => false
    }
    override def hashCode: Int = value.hashCode
  }

  final case class Lazy(value: () => DynamicValue) extends DynamicValue {
    protected def internalEquals(that: DynamicValue): Boolean = that match {
      case other: Lazy => this eq other  // Pure identity comparison
      case _ => false  // Lazy values are never equal to non-lazy values
    }

    override def hashCode: Int = System.identityHashCode(this)

    override def toString: String = s"Lazy(value = $value)"
  }

  final def fromJson(rawJson: String): DynamicValue = json.dynamicValueFromJson(rawJson)
}