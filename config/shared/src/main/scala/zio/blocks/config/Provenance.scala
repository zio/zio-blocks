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

package zio.blocks.config

import zio.blocks.maybe.Maybe

/** Describes where a resolved configuration or flag value came from. */
sealed trait Provenance {

  /** Stable identifier for the source that produced the value. */
  def sourceId: String
}

object Provenance {

  /**
   * Value resolved from an explicit source lookup.
   *
   * @param sourceId
   *   identifier of the source that answered the lookup
   * @param key
   *   source-facing key that was resolved
   * @param rawValue
   *   original string value when the source exposes it
   */
  final case class Resolved(sourceId: String, key: String, rawValue: Maybe[String]) extends Provenance

  /** Value supplied by a schema default rather than an external source. */
  case object Default extends Provenance {
    val sourceId: String = "schema-default"
  }
}
