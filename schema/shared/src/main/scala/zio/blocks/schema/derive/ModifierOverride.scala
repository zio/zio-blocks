package zio.blocks.schema

import zio.blocks.schema.{DynamicOptic, Modifier}

final case class ModifierOverride(optic: DynamicOptic, modifier: Modifier)
