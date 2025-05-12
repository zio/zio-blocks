package zio.blocks.schema

// TODO: Implement equals and hashCode in a lawful way
sealed trait DynamicValue

object DynamicValue {
  case class Record(fields: IndexedSeq[(String, DynamicValue)]) extends DynamicValue

  case class Variant(caseName: String, value: DynamicValue) extends DynamicValue

  case class Sequence(elements: IndexedSeq[DynamicValue]) extends DynamicValue

  case class Map(entries: IndexedSeq[(DynamicValue, DynamicValue)]) extends DynamicValue

  case class Primitive(value: PrimitiveValue) extends DynamicValue

  case class Lazy(value: () => DynamicValue) extends DynamicValue
}
