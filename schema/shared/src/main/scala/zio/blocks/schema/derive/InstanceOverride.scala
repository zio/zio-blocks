package zio.blocks.schema.derive

import zio.blocks.schema._

final case class InstanceOverride[TC[_], A](optic: DynamicOptic, instance: Lazy[TC[A]])
