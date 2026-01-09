package zio.blocks.schema.patch

import zio.blocks.schema.DynamicValue

sealed trait MapOp

object MapOp {

  // Add a key-value pair to the map.
  final case class Add(key: DynamicValue, value: DynamicValue) extends MapOp

  // Remove a key from the map.
  final case class Remove(key: DynamicValue) extends MapOp

  // Modify the value at a key with a nested operation.
  final case class Modify(key: DynamicValue, op: Operation) extends MapOp
}
