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
 * consumers can recover, for each captured segment, which literal name it
 * was declared with alongside its decoded type.
 */
sealed trait PathVar[Name <: String, Type]
