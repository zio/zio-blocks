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

package zio.blocks.endpoint

/**
 * Constructors of the [[PathCodec]] companion that capture one path segment
 * under a name. The Scala 2 and Scala 3 copies differ only in how they bound
 * the name to a singleton type.
 */
private[endpoint] trait PathCodecPlatformSpecific {

  // `String & Singleton` preserves the literal singleton type of a literal
  // `name` argument (instead of widening it to plain `String`) on Scala 3.
  // (Scala 2.13 has no `&` types; it uses `with`.)
  /**
   * Captures one segment as a `Boolean`. Only `true` and `false` in lower case
   * match. `render` shows the capture as `{name}`.
   */
  def bool[N <: String & Singleton](name: N): PathCodec[Boolean] { type PathVars = SegmentCodec.HasPathVars } =
    PathCodec.apply(SegmentCodec.bool(name))

  /**
   * Captures one segment as an `Int`: an optional `-` and ASCII digits, so
   * `42`, `-1` and `007` (decoded as 7) match, while `+1`, `1.0`, an empty
   * segment and values outside the `Int` range do not. `render` shows the
   * capture as `{name}`.
   */
  def int[N <: String & Singleton](name: N): PathCodec[Int] { type PathVars = SegmentCodec.HasPathVars } =
    PathCodec.apply(SegmentCodec.int(name))

  /**
   * Captures one segment as a `Long`: an optional `-` and ASCII digits, so
   * `9999999999` matches, while `+1`, `1.0`, an empty segment and values
   * outside the `Long` range do not. `render` shows the capture as `{name}`.
   */
  def long[N <: String & Singleton](name: N): PathCodec[Long] { type PathVars = SegmentCodec.HasPathVars } =
    PathCodec.apply(SegmentCodec.long(name))

  /**
   * Captures one segment as a `String`, unchanged, including an empty segment
   * in the middle of a path (`/users//5`). A path that ends before this segment
   * does not match. `render` shows the capture as `{name}`.
   */
  def string[N <: String & Singleton](name: N): PathCodec[String] { type PathVars = SegmentCodec.HasPathVars } =
    PathCodec.apply(SegmentCodec.string(name))

  /**
   * Captures one segment as a `java.util.UUID`. The segment must be in the
   * hyphenated 8-4-4-4-12 form, in upper or lower case. `render` shows the
   * capture as `{name}`.
   */
  def uuid[N <: String & Singleton](
    name: N
  ): PathCodec[java.util.UUID] { type PathVars = SegmentCodec.HasPathVars } =
    PathCodec.apply(SegmentCodec.uuid(name))
}
