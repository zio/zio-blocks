package zio.blocks.schema

/**
 * Represents an edit operation on a map.
 */
sealed trait MapOp

object MapOp {

  /**
   * Add a key-value pair to the map.
   * In Strict mode, fails if key already exists.
   * In Clobber mode, overwrites existing value.
   *
   * @param key The key to add
   * @param value The value to associate with the key
   */
  final case class Add(key: DynamicValue, value: DynamicValue) extends MapOp

  /**
   * Remove a key from the map.
   * In Strict mode, fails if key doesn't exist.
   *
   * @param key The key to remove
   */
  final case class Remove(key: DynamicValue) extends MapOp

  /**
   * Modify the value at the specified key with a nested operation.
   * In Strict mode, fails if key doesn't exist.
   *
   * @param key The key whose value should be modified
   * @param op The operation to apply to the value
   */
  final case class Modify(key: DynamicValue, op: Operation) extends MapOp
}
