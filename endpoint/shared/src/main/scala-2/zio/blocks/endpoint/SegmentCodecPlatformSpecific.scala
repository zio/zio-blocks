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

private[endpoint] trait SegmentCodecPlatformSpecific {

  def literal(value: String): SegmentCodec.Literal = {
    SegmentCodec.validateLiteralValue(value)
    SegmentCodec.literalValidated(value)
  }

  // `String with Singleton` preserves literal singletons on Scala 2.13.
  // (`with` as a type operator is an error on Scala 3.10; Scala 3 uses `&`.)
  def bool[N <: String with Singleton](name: N): SegmentCodec.BoolSeg[N]     = SegmentCodec.BoolSeg(name)
  def int[N <: String with Singleton](name: N): SegmentCodec.IntSeg[N]       = SegmentCodec.IntSeg(name)
  def long[N <: String with Singleton](name: N): SegmentCodec.LongSeg[N]     = SegmentCodec.LongSeg(name)
  def string[N <: String with Singleton](name: N): SegmentCodec.StringSeg[N] = SegmentCodec.StringSeg(name)
  def uuid[N <: String with Singleton](name: N): SegmentCodec.UUIDSeg[N]     = SegmentCodec.UUIDSeg(name)
}
