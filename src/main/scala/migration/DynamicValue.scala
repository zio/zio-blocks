package migration

sealed trait DynamicValue

object DynamicValue {

  case class Obj(fields: Map[String, DynamicValue]) extends DynamicValue

  case class Str(value: String) extends DynamicValue

  case class Num(value: Double) extends DynamicValue

  case object Null extends DynamicValue
}