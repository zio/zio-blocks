package zio.blocks.schema.json

sealed trait DiscriminatorKind

object DiscriminatorKind {
  case class Field(name: String) extends DiscriminatorKind

  case object Key extends DiscriminatorKind

  case object None extends DiscriminatorKind
}
