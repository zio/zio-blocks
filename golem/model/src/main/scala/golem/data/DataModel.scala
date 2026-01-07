package golem.data

sealed trait DataType extends Product with Serializable

object DataType {
  final case class Optional(of: DataType) extends DataType

  final case class ListType(of: DataType) extends DataType

  final case class SetType(of: DataType) extends DataType

  final case class MapType(valueType: DataType) extends DataType // string keys

  final case class TupleType(elements: List[DataType]) extends DataType

  final case class StructType(fields: List[Field]) extends DataType

  final case class EnumType(cases: List[EnumCase]) extends DataType

  final case class Field(name: String, dataType: DataType, optional: Boolean)

  final case class EnumCase(name: String, payload: Option[DataType])

  case object StringType extends DataType

  case object BoolType extends DataType

  case object IntType extends DataType

  case object LongType extends DataType

  case object DoubleType extends DataType

  case object BigDecimalType extends DataType

  case object UUIDType extends DataType

  case object BytesType extends DataType

  case object UnitType extends DataType
}

sealed trait DataValue extends Product with Serializable

object DataValue {
  final case class StringValue(value: String) extends DataValue

  final case class BoolValue(value: Boolean) extends DataValue

  final case class IntValue(value: Int) extends DataValue

  final case class LongValue(value: Long) extends DataValue

  final case class DoubleValue(value: Double) extends DataValue

  final case class BigDecimalValue(value: BigDecimal) extends DataValue

  final case class UUIDValue(value: java.util.UUID) extends DataValue

  final case class BytesValue(value: Array[Byte]) extends DataValue

  final case class OptionalValue(value: Option[DataValue]) extends DataValue

  final case class ListValue(values: List[DataValue]) extends DataValue

  final case class SetValue(values: Set[DataValue]) extends DataValue

  final case class MapValue(entries: Map[String, DataValue]) extends DataValue

  final case class TupleValue(values: List[DataValue]) extends DataValue

  final case class StructValue(fields: Map[String, DataValue]) extends DataValue

  final case class EnumValue(caseName: String, payload: Option[DataValue]) extends DataValue

  case object NullValue extends DataValue
}
