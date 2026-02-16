package zio.blocks.schema

/**
 * Strategy for merging two DynamicValue structures.
 *
 * A `DynamicValueMergeStrategy` is a function
 * `(DynamicOptic, DynamicValue, DynamicValue) => DynamicValue` that determines
 * how to merge two DynamicValue values at a given path. The [[recurse]] method
 * controls whether the merge driver should descend into containers
 * (Record/Sequence/Map) or call [[apply]] directly.
 *
 * When [[recurse]] returns `true` for a container type:
 *   - Records are merged by field name (union of fields, recursive merge on
 *     shared fields)
 *   - Sequences are merged by index (union of indices, recursive merge on
 *     shared indices)
 *   - Maps are merged by key (union of keys, recursive merge on shared keys)
 *   - Variants with same case name merge inner values; different cases: right
 *     wins
 *
 * When [[recurse]] returns `false`, [[apply]] is called with the container
 * values directly.
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

object DynamicValueMergeStrategy {

  /**
   * Deep merge: recursively merge all containers. Records merge by field name,
   * Sequences merge by index, Maps merge by key. At leaves, right value wins.
   */
  case object Auto extends DynamicValueMergeStrategy {
    def recurse(path: DynamicOptic, valueType: DynamicValueType): Boolean =
      valueType == DynamicValueType.Record ||
        valueType == DynamicValueType.Sequence ||
        valueType == DynamicValueType.Map ||
        valueType == DynamicValueType.Variant

    def apply(path: DynamicOptic, left: DynamicValue, right: DynamicValue): DynamicValue = right
  }

  /**
   * Complete replacement: no merging at any level. Right value completely
   * replaces left value.
   */
  case object Replace extends DynamicValueMergeStrategy {
    def recurse(path: DynamicOptic, valueType: DynamicValueType): Boolean = false

    def apply(path: DynamicOptic, left: DynamicValue, right: DynamicValue): DynamicValue = right
  }

  /**
   * Always take left value, never recurse.
   */
  case object KeepLeft extends DynamicValueMergeStrategy {
    def recurse(path: DynamicOptic, valueType: DynamicValueType): Boolean = false

    def apply(path: DynamicOptic, left: DynamicValue, right: DynamicValue): DynamicValue = left
  }

  /**
   * Shallow merge: merge only at the root level, no recursion. Top-level record
   * fields, sequence indices, or map keys are merged, but nested containers are
   * replaced (right wins).
   */
  case object Shallow extends DynamicValueMergeStrategy {
    def recurse(path: DynamicOptic, valueType: DynamicValueType): Boolean =
      path.nodes.isEmpty

    def apply(path: DynamicOptic, left: DynamicValue, right: DynamicValue): DynamicValue = right
  }

  /**
   * Concatenate sequences instead of merging by index. Records and Maps are
   * still merged by key recursively.
   */
  case object Concat extends DynamicValueMergeStrategy {
    def recurse(path: DynamicOptic, valueType: DynamicValueType): Boolean =
      valueType == DynamicValueType.Record || valueType == DynamicValueType.Map

    def apply(path: DynamicOptic, left: DynamicValue, right: DynamicValue): DynamicValue =
      (left, right) match {
        case (ls: DynamicValue.Sequence, rs: DynamicValue.Sequence) =>
          DynamicValue.Sequence(ls.elements ++ rs.elements)
        case _ => right
      }
  }

  /**
   * Custom merge strategy with full control over recursion and merging.
   *
   * @param f
   *   A function that takes (path, left, right) and returns the merged
   *   DynamicValue
   * @param r
   *   A function that determines whether to recurse into containers (default:
   *   always recurse for containers)
   */
  final case class Custom(
    f: (DynamicOptic, DynamicValue, DynamicValue) => DynamicValue,
    r: (DynamicOptic, DynamicValueType) => Boolean = (_, t) =>
      t == DynamicValueType.Record ||
        t == DynamicValueType.Sequence ||
        t == DynamicValueType.Map ||
        t == DynamicValueType.Variant
  ) extends DynamicValueMergeStrategy {
    def recurse(path: DynamicOptic, valueType: DynamicValueType): Boolean = r(path, valueType)

    def apply(path: DynamicOptic, left: DynamicValue, right: DynamicValue): DynamicValue = f(path, left, right)
  }
}
