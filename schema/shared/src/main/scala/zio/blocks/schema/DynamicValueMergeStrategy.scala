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

/**
 * Decides how `DynamicValue#merge` combines two values. A strategy is a
 * function `(path, left, right) => merged` plus [[recurse]], which says whether
 * the merge goes inside two containers of the same kind at a path instead of
 * calling [[apply]] on them.
 *
 * When [[recurse]] returns `true` for the kind of the two values:
 *   - records are merged by field name, keeping the fields of both (left first)
 *     and merging the values of shared fields;
 *   - sequences are merged by index, merging the elements at shared indices and
 *     keeping the extra elements of the longer one;
 *   - maps are merged by key, like records;
 *   - variants of the same case are merged by payload.
 *
 * Otherwise, and for two variants of different cases, two values of different
 * kinds, two primitives or two `Null`s, [[apply]] returns the merged value. The
 * trait is sealed: use [[DynamicValueMergeStrategy.Custom]] for your own rules.
 * Its `toString` is `<function3>` (see `docs/dev/issues.md`).
 */
sealed trait DynamicValueMergeStrategy extends ((DynamicOptic, DynamicValue, DynamicValue) => DynamicValue) {

  /**
   * Determines whether the merge driver should recurse into a container at the
   * given path.
   *
   * If true, the driver merges by key (records/maps), by index (sequences), or
   * by case (variants), recursively calling merge on each child pair.
   *
   * If false, the driver calls [[apply]] with the container values directly.
   */
  def recurse(path: DynamicOptic, valueType: DynamicValueType): Boolean
}

/**
 * The built-in merge strategies.
 */
object DynamicValueMergeStrategy {

  /**
   * Deep merge, the default: goes inside records, sequences, maps and variants
   * of the same case at every depth. At leaves, and for values of different
   * kinds, the right value wins.
   */
  case object Auto extends DynamicValueMergeStrategy {

    /** Returns `true` for records, sequences, maps and variants. */
    def recurse(path: DynamicOptic, valueType: DynamicValueType): Boolean =
      valueType == DynamicValueType.Record ||
        valueType == DynamicValueType.Sequence ||
        valueType == DynamicValueType.Map ||
        valueType == DynamicValueType.Variant

    /** Returns `right`. */
    def apply(path: DynamicOptic, left: DynamicValue, right: DynamicValue): DynamicValue = right
  }

  /**
   * Complete replacement: no merging at any level. The right value replaces the
   * left value.
   */
  case object Replace extends DynamicValueMergeStrategy {

    /** Returns `false`. */
    def recurse(path: DynamicOptic, valueType: DynamicValueType): Boolean = false

    /** Returns `right`. */
    def apply(path: DynamicOptic, left: DynamicValue, right: DynamicValue): DynamicValue = right
  }

  /**
   * Keeps the left value and never goes inside it, so merging with it returns
   * the left value unchanged.
   */
  case object KeepLeft extends DynamicValueMergeStrategy {

    /** Returns `false`. */
    def recurse(path: DynamicOptic, valueType: DynamicValueType): Boolean = false

    /** Returns `left`. */
    def apply(path: DynamicOptic, left: DynamicValue, right: DynamicValue): DynamicValue = left
  }

  /**
   * Shallow merge: goes inside the two values only at the root. The top-level
   * record fields, sequence indices and map keys of both are kept, and where
   * both have one, the right value wins without merging further.
   */
  case object Shallow extends DynamicValueMergeStrategy {

    /** Returns `true` only at the root path. */
    def recurse(path: DynamicOptic, valueType: DynamicValueType): Boolean =
      path.nodes.isEmpty

    /** Returns `right`. */
    def apply(path: DynamicOptic, left: DynamicValue, right: DynamicValue): DynamicValue = right
  }

  /**
   * Like [[Auto]] for records and maps, but concatenates two sequences (left
   * elements first) instead of merging them by index. It does not go inside
   * variants: the right variant wins, even for the same case. At leaves, and
   * for values of different kinds, the right value wins.
   */
  case object Concat extends DynamicValueMergeStrategy {

    /** Returns `true` for records and maps. */
    def recurse(path: DynamicOptic, valueType: DynamicValueType): Boolean =
      valueType == DynamicValueType.Record || valueType == DynamicValueType.Map

    /**
     * Returns the two sequences concatenated when both are sequences, and
     * `right` otherwise.
     */
    def apply(path: DynamicOptic, left: DynamicValue, right: DynamicValue): DynamicValue =
      (left, right) match {
        case (ls: DynamicValue.Sequence, rs: DynamicValue.Sequence) =>
          DynamicValue.Sequence(ls.elements ++ rs.elements)
        case _ => right
      }
  }

  /**
   * A strategy built from two functions.
   *
   * @param f
   *   returns the merged value for `(path, left, right)` wherever the merge
   *   does not go inside the two values
   * @param r
   *   says whether to go inside two values of the given kind at the given path
   *   (default: always, for records, sequences, maps and variants)
   */
  final case class Custom(
    f: (DynamicOptic, DynamicValue, DynamicValue) => DynamicValue,
    r: (DynamicOptic, DynamicValueType) => Boolean = (_, t) =>
      t == DynamicValueType.Record ||
        t == DynamicValueType.Sequence ||
        t == DynamicValueType.Map ||
        t == DynamicValueType.Variant
  ) extends DynamicValueMergeStrategy {

    /** Returns `r(path, valueType)`. */
    def recurse(path: DynamicOptic, valueType: DynamicValueType): Boolean = r(path, valueType)

    /** Returns `f(path, left, right)`. */
    def apply(path: DynamicOptic, left: DynamicValue, right: DynamicValue): DynamicValue = f(path, left, right)
  }
}
