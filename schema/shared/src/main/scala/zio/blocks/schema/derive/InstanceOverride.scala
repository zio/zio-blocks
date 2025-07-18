package zio.blocks.schema.derive

import zio.blocks.schema._

final case class InstanceOverride[TC[_], A](overrideBy: InstanceOverride.By[A], instance: Lazy[TC[A]])

object InstanceOverride {
  sealed trait By[A]

  object By {
    case class Optic[A](optic: DynamicOptic) extends By[A]
    case class Type[A](name: TypeName[A])    extends By[A]
  }
}
