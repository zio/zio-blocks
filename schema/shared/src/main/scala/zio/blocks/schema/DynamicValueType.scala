/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema

import zio.blocks.chunk.Chunk

/**
 * The kind of a [[DynamicValue]]: one case for each of its six cases. As a
 * function, it tests whether a value is of this kind. `DynamicValue#is`, `as`,
 * `unwrap` and `select`, the filters of [[DynamicValueSelection]] and
 * [[DynamicValueMergeStrategy#recurse]] take one.
 *
 * Each case provides two type members:
 *   - `Type`: the matching [[DynamicValue]] case (for example
 *     `DynamicValue.Record`);
 *   - `Unwrap`: the type of its contents (for example
 *     `Chunk[(String, DynamicValue)]`).
 *
 * Because it extends a function type, its `toString` is `<function1>`, and so
 * are the type names in some error messages (see `docs/dev/issues.md`).
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

/**
 * The six kinds of [[DynamicValue]].
 */
object DynamicValueType {

  /**
   * The kind of [[DynamicValue.Primitive]]; `Unwrap` is [[PrimitiveValue]].
   */
  case object Primitive extends DynamicValueType {
    override final type Type   = DynamicValue.Primitive
    override final type Unwrap = PrimitiveValue
    val typeIndex = 0
  }

  /**
   * The kind of [[DynamicValue.Record]]; `Unwrap` is the fields,
   * `Chunk[(String, DynamicValue)]`.
   */
  case object Record extends DynamicValueType {
    override final type Type   = DynamicValue.Record
    override final type Unwrap = Chunk[(String, DynamicValue)]
    val typeIndex = 1
  }

  /**
   * The kind of [[DynamicValue.Variant]]; `Unwrap` is the case name and
   * payload, `(String, DynamicValue)`.
   */
  case object Variant extends DynamicValueType {
    override final type Type   = DynamicValue.Variant
    override final type Unwrap = (String, DynamicValue)
    val typeIndex = 2
  }

  /**
   * The kind of [[DynamicValue.Sequence]]; `Unwrap` is the elements,
   * `Chunk[DynamicValue]`.
   */
  case object Sequence extends DynamicValueType {
    override final type Type   = DynamicValue.Sequence
    override final type Unwrap = Chunk[DynamicValue]
    val typeIndex = 3
  }

  /**
   * The kind of [[DynamicValue.Map]]; `Unwrap` is the entries,
   * `Chunk[(DynamicValue, DynamicValue)]`.
   */
  case object Map extends DynamicValueType {
    override final type Type   = DynamicValue.Map
    override final type Unwrap = Chunk[(DynamicValue, DynamicValue)]
    val typeIndex = 4
  }

  /**
   * The kind of [[DynamicValue.Null]]; `Unwrap` is `Unit`.
   */
  case object Null extends DynamicValueType {
    override final type Type   = DynamicValue.Null.type
    override final type Unwrap = Unit
    val typeIndex = 5
  }
}
