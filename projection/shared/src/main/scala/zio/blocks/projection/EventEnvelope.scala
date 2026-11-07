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
 * An event together with the metadata an [[EventStore]] records about it.
 *
 * Envelopes are what an `EventStore` returns from `readFrom` and `readAll`, and
 * what it publishes on its `subscribe` hub when a new event is appended. The
 * [[ProjectionEngine]] reads them to run a projection's handlers: it passes
 * `event` to the handler, along with a [[ProjectionContext]] built from
 * `entityId`, `timestamp` and `seq`.
 *
 * The type is covariant in `E`, so an `EventEnvelope[UserCreated]` can be used
 * where an `EventEnvelope[Any]` is expected.
 *
 * @param seq
 *   the position of the event in its store, increasing with each append. The
 *   engine saves the last `seq` it processed and resumes after it on restart
 * @param tag
 *   the name that identifies the kind of event. The SQLite store uses the case
 *   name or the number from `@eventTag`, and replaces an old tag with its
 *   current one when tag aliases are configured. The in-memory test store uses
 *   the event class's simple name
 * @param event
 *   the event itself
 * @param timestamp
 *   when the event was recorded
 * @param entityId
 *   the entity the event was recorded under, which a handler sees as
 *   `ctx.entityId`
 * @tparam E
 *   the event type
 */
final case class EventEnvelope[+E](
  seq: Long,
  tag: String,
  event: E,
  timestamp: Instant,
  entityId: String
)
