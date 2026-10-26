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

package zio.blocks.schema.json

import zio.blocks.schema.DynamicOptic

/**
 * How [[Json#merge]] combines two values. At each path where both sides are
 * objects or both are arrays, [[recurse]] decides whether to descend; every
 * other pair, including two values of different kinds, goes to [[apply]], with
 * the left value, the right value and the path.
 *
 * When the merge descends:
 *   - two objects merge by key: the fields of the left, in order, then the new
 *     fields of the right; a key on both sides merges recursively, and a key
 *     repeated on one side keeps its last value;
 *   - two arrays merge by index: shared indices merge recursively, and the
 *     longer array's extra elements are kept.
 *
 * {{{
 * import zio.blocks.schema.json._
 *
 * val left  = Json.parseUnsafe("""{"tags":["fp"],"info":{"year":2021,"lang":"en"}}""")
 * val right = Json.parseUnsafe("""{"tags":["scala","zio"],"info":{"year":2022}}""")
 *
 * left.merge(right, MergeStrategy.Auto).print    // {"tags":["scala","zio"],"info":{"year":2022,"lang":"en"}}
 * left.merge(right, MergeStrategy.Shallow).print // {"tags":["scala","zio"],"info":{"year":2022}}
 * }}}
 *
 * The built-in strategies print as `<function3>` (a known bug).
 */
sealed trait MergeStrategy extends ((DynamicOptic, Json, Json) => Json) {

  /**
   * Returns `true` to merge two objects (`jsonType` is `JsonType.Object`) or
   * two arrays (`JsonType.Array`) at `path` child by child, or `false` to pass
   * them to [[apply]] whole.
   */
  def recurse(path: DynamicOptic, jsonType: JsonType): Boolean
}

/** The built-in merge strategies and [[MergeStrategy.Custom]]. */
object MergeStrategy {

  /**
   * The default deep merge: objects merge by key and arrays by index at every
   * depth, and the right value wins everywhere else, so `[1,2,3]` merged with
   * `[9]` gives `[9,2,3]`.
   */
  case object Auto extends MergeStrategy {

    /** Always `true`. */
    def recurse(path: DynamicOptic, jsonType: JsonType): Boolean = true

    /** Returns `right`. */
    def apply(path: DynamicOptic, left: Json, right: Json): Json = right
  }

  /** No merging: the right value replaces the left one. */
  case object Replace extends MergeStrategy {

    /** Always `false`. */
    def recurse(path: DynamicOptic, jsonType: JsonType): Boolean = false

    /** Returns `right`. */
    def apply(path: DynamicOptic, left: Json, right: Json): Json = right
  }

  /**
   * Merges the two root objects (or arrays) child by child, and the right value
   * wins for each child, so nested objects are replaced, not merged.
   */
  case object Shallow extends MergeStrategy {

    /** Returns `true` at the root path only. */
    def recurse(path: DynamicOptic, jsonType: JsonType): Boolean = path.nodes.isEmpty

    /** Returns `right`. */
    def apply(path: DynamicOptic, left: Json, right: Json): Json = right
  }

  /**
   * Merges objects by key at every depth, appends the right array to the left
   * one, and the right value wins everywhere else.
   */
  case object Concat extends MergeStrategy {

    /** Returns `true` for objects only. */
    def recurse(path: DynamicOptic, jsonType: JsonType): Boolean = jsonType eq JsonType.Object

    /** Returns the two arrays joined when both are arrays, or else `right`. */
    def apply(path: DynamicOptic, left: Json, right: Json): Json =
      if (left.isInstanceOf[Json.Array] && right.isInstanceOf[Json.Array]) {
        new Json.Array(left.asInstanceOf[Json.Array].value ++ right.asInstanceOf[Json.Array].value)
      } else right
  }

  /**
   * A strategy built from two functions.
   *
   * @param f
   *   merges the pairs the driver does not descend into: `(path, left, right)`
   * @param r
   *   decides whether to descend into two objects or two arrays at a path
   *   (default: always)
   */
  final case class Custom(
    f: (DynamicOptic, Json, Json) => Json,
    r: (DynamicOptic, JsonType) => Boolean = (_, _) => true
  ) extends MergeStrategy {

    /** Returns `r(path, jsonType)`. */
    def recurse(path: DynamicOptic, jsonType: JsonType): Boolean = r(path, jsonType)

    /** Returns `f(path, left, right)`. */
    def apply(path: DynamicOptic, left: Json, right: Json): Json = f(path, left, right)
  }
}
