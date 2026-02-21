package zio.blocks.schema.json

import zio.blocks.schema.DynamicOptic

/**
 * Strategy for merging two JSON values.
 *
 * A `MergeStrategy` is a function `(DynamicOptic, Json, Json) => Json` that
 * determines how to merge two JSON values at a given path. The [[recurse]]
 * method controls whether the merge driver should descend into containers
 * (objects/arrays) or call [[apply]] directly.
 *
 * When [[recurse]] returns `true` for a container type:
 *   - Objects are merged by key (union of keys, recursive merge on shared keys)
 *   - Arrays are merged by index (union of indices, recursive merge on shared
 *     indices)
 *
 * When [[recurse]] returns `false`, [[apply]] is called with the container
 * values directly.
 */
sealed trait MergeStrategy extends ((DynamicOptic, Json, Json) => Json) {

  /**
   * Determines whether the merge driver should recurse into a container (object
   * or array) at the given path.
   *
   * If true, the driver merges by key (objects) or by index (arrays),
   * recursively calling merge on each child pair.
   *
   * If false, the driver calls [[apply]] with the container values directly.
   */
  def recurse(path: DynamicOptic, jsonType: JsonType): Boolean
}

object MergeStrategy {

  /**
   * Deep merge: recursively merge all objects and arrays. Objects merge by key,
   * arrays merge by index (not concatenation). At leaves, right value wins.
   */
  case object Auto extends MergeStrategy {
    def recurse(path: DynamicOptic, jsonType: JsonType): Boolean = true

    def apply(path: DynamicOptic, left: Json, right: Json): Json = right
  }

  /**
   * Complete replacement: no merging at any level. Right value completely
   * replaces left value.
   */
  case object Replace extends MergeStrategy {
    def recurse(path: DynamicOptic, jsonType: JsonType): Boolean = false

    def apply(path: DynamicOptic, left: Json, right: Json): Json = right
  }

  /**
   * Shallow merge: merge only at the root level, no recursion. Top-level object
   * keys or array indices are merged, but nested containers are replaced (right
   * wins).
   */
  case object Shallow extends MergeStrategy {
    def recurse(path: DynamicOptic, jsonType: JsonType): Boolean = path.nodes.isEmpty

    def apply(path: DynamicOptic, left: Json, right: Json): Json = right
  }

  /**
   * Concatenate arrays instead of merging by index. Objects are still merged by
   * key recursively.
   */
  case object Concat extends MergeStrategy {
    def recurse(path: DynamicOptic, jsonType: JsonType): Boolean = jsonType eq JsonType.Object

    def apply(path: DynamicOptic, left: Json, right: Json): Json =
      if (left.isInstanceOf[Json.Array] && right.isInstanceOf[Json.Array]) {
        new Json.Array(left.asInstanceOf[Json.Array].value ++ right.asInstanceOf[Json.Array].value)
      } else right
  }

  /**
   * Custom merge strategy with full control over recursion and merging.
   *
   * @param f
   *   A function that takes (path, left, right) and returns the merged JSON
   *   value
   * @param r
   *   A function that determines whether to recurse into containers (default:
   *   always recurse)
   */
  final case class Custom(
    f: (DynamicOptic, Json, Json) => Json,
    r: (DynamicOptic, JsonType) => Boolean = (_, _) => true
  ) extends MergeStrategy {
    def recurse(path: DynamicOptic, jsonType: JsonType): Boolean = r(path, jsonType)

    def apply(path: DynamicOptic, left: Json, right: Json): Json = f(path, left, right)
  }
}
