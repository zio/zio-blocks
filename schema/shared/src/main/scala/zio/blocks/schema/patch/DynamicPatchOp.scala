package zio.blocks.schema.patch

import zio.blocks.schema.DynamicValue

// Note - DynamicOptic.Node.AtMapKey[K]
// has a generic type parameter `K` that makes it non-serializable without type information.
// `PatchPath` is a new serializable path type.

sealed trait PatchPath
object PatchPath {
  // Empty path - targets the root value.
  val root: Vector[PatchPath] = Vector.empty

  // Access a field in a record by name.
  final case class Field(name: String) extends PatchPath

  // Access a variant case by name. Used for prism operations on sum types.
  final case class Case(name: String) extends PatchPath

  // Access an element in a sequence by index.
  final case class AtIndex(index: Int) extends PatchPath

  // Access a value in a map by key. Uses DynamicValue for the key to ensure serializability.
  final case class AtMapKey(key: DynamicValue) extends PatchPath

  // Access all elements in a sequence (traversal). Operations using this path will be applied to every element.
  case object Elements extends PatchPath

  // Access the wrapped value in a newtype wrapper.
  case object Wrapped extends PatchPath
}

// A single patch operation paired with the path to apply it at.
final case class DynamicPatchOp(path: Vector[PatchPath], operation: Operation)
