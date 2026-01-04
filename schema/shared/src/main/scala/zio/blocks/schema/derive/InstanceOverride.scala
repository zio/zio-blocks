package zio.blocks.schema.derive

import zio.blocks.schema._
import zio.blocks.typeid.TypeId

sealed trait InstanceOverride

case class InstanceOverrideByOptic[TC[_], A](optic: DynamicOptic, instance: Lazy[TC[A]]) extends InstanceOverride

case class InstanceOverrideByType[TC[_], A](typeId: TypeId[A], instance: Lazy[TC[A]]) extends InstanceOverride
