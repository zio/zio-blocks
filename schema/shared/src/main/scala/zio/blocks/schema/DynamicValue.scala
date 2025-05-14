package zio.blocks.schema

// TODO: Implement equals and hashCode in a lawful way
sealed trait DynamicValue {
  final def toJson: String = json.dynamicValueToJson(this)
}

object DynamicValue {
  final case class Record(fields: IndexedSeq[(String, DynamicValue)]) extends DynamicValue

  final case class Variant(caseName: String, value: DynamicValue) extends DynamicValue

  final case class Sequence(elements: IndexedSeq[DynamicValue]) extends DynamicValue

  final case class Map(entries: IndexedSeq[(DynamicValue, DynamicValue)]) extends DynamicValue

  final case class Primitive(value: PrimitiveValue) extends DynamicValue

  final case class Lazy(value: () => DynamicValue) extends DynamicValue

  final def fromJson(rawJson: String): DynamicValue = json.dynamicValueFromJson(rawJson)
}
