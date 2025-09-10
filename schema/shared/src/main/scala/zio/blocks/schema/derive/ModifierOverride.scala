package zio.blocks.schema.derive

import zio.blocks.schema._

final case class ModifierOverride[A](overrideBy: ModifierOverride.By[A], modifier: Modifier)

object ModifierOverride {
  sealed trait By[A]

  object By {
    case class Optic[A](optic: DynamicOptic) extends By[A]
    case class Type[A](name: TypeName[A])    extends By[A]
  }
}
