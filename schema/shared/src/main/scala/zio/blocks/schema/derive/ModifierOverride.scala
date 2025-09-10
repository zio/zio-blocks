package zio.blocks.schema.derive

import zio.blocks.schema._

sealed trait ModifierOverride

case class ModifierReflectOverrideByOptic(optic: DynamicOptic, modifier: Modifier.Reflect) extends ModifierOverride

case class ModifierReflectOverrideByType[A](typeName: TypeName[A], modifier: Modifier.Reflect) extends ModifierOverride

case class ModifierTermOverride[A](typeName: TypeName[A], termName: String, modifier: Modifier.Term)
    extends ModifierOverride
