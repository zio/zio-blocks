package zio.blocks.schema

// TODO: Implement equals and hashCode in a lawful way
sealed trait DynamicValue

object DynamicValue {
  final case class Record(fields: IndexedSeq[(String, DynamicValue)])     extends DynamicValue
  final case class Variant(caseName: String, value: DynamicValue)         extends DynamicValue
  final case class Sequence(elements: IndexedSeq[DynamicValue])           extends DynamicValue
  final case class Map(entries: IndexedSeq[(DynamicValue, DynamicValue)]) extends DynamicValue
  final case class Primitive(value: PrimitiveValue)                       extends DynamicValue
  final case class Lazy(value: () => DynamicValue)                        extends DynamicValue
}
