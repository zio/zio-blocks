package zio.blocks.schema.migration.optic

final case class DynamicOptic(steps: Vector[OpticStep]) {

  def ++(that: DynamicOptic): DynamicOptic =
    DynamicOptic(this.steps ++ that.steps)

  def render: String =
    steps.map {
      case OpticStep.Field(name) => s".$name"
      case OpticStep.Index(i)    => s"[$i]"
      case OpticStep.Key(k)      => s"[\"$k\"]"
    }.mkString
}

object DynamicOptic {
  val empty: DynamicOptic = DynamicOptic(Vector.empty)

  def apply(steps: OpticStep*): DynamicOptic = DynamicOptic(steps.toVector)
}

sealed trait OpticStep

object OpticStep {
  final case class Field(name: String) extends OpticStep
  final case class Index(index: Int) extends OpticStep
  final case class Key(key: String) extends OpticStep
}