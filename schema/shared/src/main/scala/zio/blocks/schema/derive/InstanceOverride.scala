package zio.blocks.schema.derive

import zio.blocks.schema._

sealed trait InstanceOverride

case class InstanceOverrideByOptic[TC[_], A](optic: DynamicOptic, instance: Lazy[TC[A]]) extends InstanceOverride

case class InstanceOverrideByType[TC[_], A](name: TypeName[A], instance: Lazy[TC[A]]) extends InstanceOverride
