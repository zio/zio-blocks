package zio.blocks.schema.derive

import zio.blocks.schema._

sealed trait ModifierOverride

case class ModifierReflectOverrideByOptic(optic: DynamicOptic, modifier: Modifier.Reflect) extends ModifierOverride

case class ModifierReflectOverrideByType[A](typeId: zio.blocks.typeid.TypeId[A], modifier: Modifier.Reflect) extends ModifierOverride

case class ModifierTermOverrideByType[A](typeId: zio.blocks.typeid.TypeId[A], termName: String, modifier: Modifier.Term)
    extends ModifierOverride

case class ModifierTermOverrideByOptic[A](optic: DynamicOptic, termName: String, modifier: Modifier.Term)
    extends ModifierOverride
