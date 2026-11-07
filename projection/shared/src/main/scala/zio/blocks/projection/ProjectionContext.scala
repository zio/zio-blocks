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
 * The metadata of an event that a handler receives next to the event itself.
 * [[ProjectionEngine]] builds it from the event's [[EventEnvelope]].
 *
 * @param entityId
 *   the entity the event was recorded under. Insert, update and delete actions
 *   change the row with this ID
 * @param timestamp
 *   when the event was appended to its store
 * @param seq
 *   the event's sequence number in its store
 * @param sourceEntityId
 *   the entity ID again, as `Some(entityId)`, when the engine builds the
 *   context; `None` from the three-argument `apply`
 */
final case class ProjectionContext(
  entityId: String,
  timestamp: Instant,
  seq: Long,
  sourceEntityId: Option[String]
)

/** Builds [[ProjectionContext]]s. */
object ProjectionContext {

  /** Builds a context with no `sourceEntityId`, for example in tests. */
  def apply(entityId: String, timestamp: Instant, seq: Long): ProjectionContext =
    ProjectionContext(entityId, timestamp, seq, sourceEntityId = None)
}
