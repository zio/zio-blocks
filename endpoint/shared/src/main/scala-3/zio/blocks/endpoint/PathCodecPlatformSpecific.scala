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

private[endpoint] trait PathCodecPlatformSpecific {

  // `String & Singleton` preserves the literal singleton type of a literal
  // `name` argument (instead of widening it to plain `String`) on Scala 3.
  // (Scala 2.13 has no `&` types; it uses `with`.)
  def bool[N <: String & Singleton](name: N): PathCodec[Boolean] { type PathVars = SegmentCodec.HasPathVars } =
    PathCodec.apply(SegmentCodec.bool(name))
  def int[N <: String & Singleton](name: N): PathCodec[Int] { type PathVars = SegmentCodec.HasPathVars } =
    PathCodec.apply(SegmentCodec.int(name))
  def long[N <: String & Singleton](name: N): PathCodec[Long] { type PathVars = SegmentCodec.HasPathVars } =
    PathCodec.apply(SegmentCodec.long(name))
  def string[N <: String & Singleton](name: N): PathCodec[String] { type PathVars = SegmentCodec.HasPathVars } =
    PathCodec.apply(SegmentCodec.string(name))
  def uuid[N <: String & Singleton](
    name: N
  ): PathCodec[java.util.UUID] { type PathVars = SegmentCodec.HasPathVars } =
    PathCodec.apply(SegmentCodec.uuid(name))
}
