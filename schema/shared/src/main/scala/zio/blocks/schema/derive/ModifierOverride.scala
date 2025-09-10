package zio.blocks.schema.derive

import zio.blocks.schema._

sealed trait ModifierOverride

case class ModifierReflectOverride[A](overrideBy: ModifierReflectOverride.By[A], modifier: Modifier.Reflect)
    extends ModifierOverride

object ModifierReflectOverride {
  sealed trait By[A]

  object By {
    case class Optic[A](optic: DynamicOptic) extends By[A]
    case class Type[A](name: TypeName[A])    extends By[A]
  }
}

case class ModifierTermOverride[A](overrideBy: ModifierTermOverride.By[A], modifier: Modifier.Term)
    extends ModifierOverride

object ModifierTermOverride {
  sealed trait By[A]

  object By {
    case class Type[A](name: TypeName[A], termName: String) extends By[A]
  }
}
