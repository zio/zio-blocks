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

package zio.blocks.projection

import java.time.Instant

/**
 * Contextual metadata associated with a projection action.
 *
 * @param entityId
 *   the identifier of the entity this action targets
 * @param timestamp
 *   the time the action was produced
 * @param seq
 *   a monotonically increasing sequence number for ordering
 * @param sourceEntityId
 *   optional identifier of the entity in the source system
 */
final case class ProjectionContext(
  entityId: String,
  timestamp: Instant,
  seq: Long,
  sourceEntityId: Option[String]
)

object ProjectionContext {

  def apply(entityId: String, timestamp: Instant, seq: Long): ProjectionContext =
    ProjectionContext(entityId, timestamp, seq, sourceEntityId = None)
}
