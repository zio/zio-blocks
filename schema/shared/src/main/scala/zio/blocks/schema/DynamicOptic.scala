package zio.blocks.schema

case class DynamicOptic(nodes: IndexedSeq[DynamicOptic.Node])

object DynamicOptic {
  // TODO: This hierarchy should mirror the Reflect hierarchy (which implies Option, etc., specialization)
  sealed trait Node
  case class Field(name: String) extends Node
  case class Case(name: String)  extends Node
  case object MapKeys            extends Node
  case object MapValues          extends Node
  case object SeqElements        extends Node

  def lens(fieldName: String): DynamicOptic = DynamicOptic(Vector(Field(fieldName)))

  def prism(caseName: String): DynamicOptic = DynamicOptic(Vector(Case(caseName)))

  def mapKeys: DynamicOptic = DynamicOptic(Vector(MapKeys))

  def mapValues: DynamicOptic = DynamicOptic(Vector(MapValues))

  def elements: DynamicOptic = DynamicOptic(Vector(SeqElements))
}
