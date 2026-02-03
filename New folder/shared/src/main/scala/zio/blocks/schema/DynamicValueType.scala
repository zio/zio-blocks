package zio.blocks.schema

import zio.blocks.chunk.Chunk

/**
 * A sealed trait representing the type of a DynamicValue.
 *
 * This is used by [[DynamicValueMergeStrategy]] to make decisions about
 * recursion without needing access to the actual DynamicValue values.
 *
 * Each case provides two type members:
 *   - `Type`: The corresponding [[DynamicValue]] subtype (e.g.,
 *     `DynamicValue.Record`)
 *   - `Unwrap`: The underlying Scala type of the value (e.g.,
 *     `Chunk[(String, DynamicValue)]`)
 */
sealed trait DynamicValueType extends (DynamicValue => Boolean) {

  /** The corresponding [[DynamicValue]] subtype for this DynamicValue type. */
  type Type <: DynamicValue

  /**
   * The underlying Scala type of the value contained in this DynamicValue type.
   */
  type Unwrap

  /**
   * Returns the type index for ordering: Primitive=0, Record=1, Variant=2,
   * Sequence=3, Map=4, Null=5
   */
  def typeIndex: Int

  /** Returns true if the given DynamicValue is of this type. */
  override def apply(dv: DynamicValue): Boolean = dv.valueType == this
}

object DynamicValueType {

  case object Primitive extends DynamicValueType {
    override final type Type   = DynamicValue.Primitive
    override final type Unwrap = PrimitiveValue
    val typeIndex = 0
  }

  case object Record extends DynamicValueType {
    override final type Type   = DynamicValue.Record
    override final type Unwrap = Chunk[(String, DynamicValue)]
    val typeIndex = 1
  }

  case object Variant extends DynamicValueType {
    override final type Type   = DynamicValue.Variant
    override final type Unwrap = (String, DynamicValue)
    val typeIndex = 2
  }

  case object Sequence extends DynamicValueType {
    override final type Type   = DynamicValue.Sequence
    override final type Unwrap = Chunk[DynamicValue]
    val typeIndex = 3
  }

  case object Map extends DynamicValueType {
    override final type Type   = DynamicValue.Map
    override final type Unwrap = Chunk[(DynamicValue, DynamicValue)]
    val typeIndex = 4
  }

  case object Null extends DynamicValueType {
    override final type Type   = DynamicValue.Null.type
    override final type Unwrap = Unit
    val typeIndex = 5
  }
}
