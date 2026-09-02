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
 * Phantom marker pairing a captured path segment's literal `Name` with its
 * decoded `Type`. `PathVar` is never instantiated - it exists purely at the
 * type level, carrying zero runtime footprint - and is used exclusively as a
 * tag inside [[SegmentCodec.PathVars]] (and, transitively, the `PathVars`
 * registries that `PathCodec`/`RoutePattern` expose) so that name+type-aware
 * consumers can recover, for each captured segment, which literal name it was
 * declared with alongside its decoded type.
 */
sealed trait PathVar[Name <: String, Type]

object PathVar {

  /**
   * Phantom marker pairing a captured path segment's literal `Name` with its
   * decoded `Type`, exactly like [[PathVar]] itself, but tagging the segment as
   * explicitly, intentionally unused - deliberately a SEPARATE, sibling type
   * (not a subtype of [[PathVar]]) so that name+type-aware consumers can tell
   * the two apart at the type level: a plain `PathVar[Name, Type]` marks a
   * segment that must be consumed by the handler (or warned about if it isn't),
   * whereas `Ignored[Name, Type]` marks a segment that is never expected to be
   * consumed and must never trigger such a warning either way. `Ignored` is
   * never instantiated - it exists purely at the type level, carrying zero
   * runtime footprint - and is produced exclusively by `SegmentCodec`'s
   * `.unused` builder method (see e.g. [[SegmentCodec.IntSeg.unused]]), which
   * relabels a leaf capturing segment's `PathVars` registry entry from
   * `PathVar[Name, Type]` to `Ignored[Name, Type]` without altering any runtime
   * behavior.
   */
  sealed trait Ignored[Name <: String, Type]
}
