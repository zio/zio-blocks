package zio.blocks.schema.derive

import zio.blocks.typeid.TypeId
import zio.blocks.schema.Modifier
import zio.blocks.schema.DynamicOptic

sealed trait ModifierOverride

case class ModifierReflectOverrideByOptic(optic: DynamicOptic, modifier: Modifier.Reflect) extends ModifierOverride

case class ModifierReflectOverrideByType[A](typeId: TypeId[A], modifier: Modifier.Reflect) extends ModifierOverride

case class ModifierTermOverrideByType[A](typeId: TypeId[A], termName: String, modifier: Modifier.Term)
    extends ModifierOverride

case class ModifierTermOverrideByOptic[A](optic: DynamicOptic, termName: String, modifier: Modifier.Term)
    extends ModifierOverride
